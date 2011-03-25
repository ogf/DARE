(ns backend.core
  (:require [somnium.congomongo :as mongo]
            [somnium.congomongo.config :as mongo-config])
  (:import [es.uvigo.ei.sing.dare.domain IBackend Maybe IBackendBuilder]
           [es.uvigo.ei.sing.dare.entities
            Robot PeriodicalExecution ExecutionPeriod ExecutionPeriod$Unit
            ExecutionResult ExecutionResult$Type]
           [java.util List UUID]
           [org.joda.time DateTime]
           [com.mongodb DBApiLayer]))

(defn code-to-mongo-id [map]
  (if-let [code (get map :code nil)]
    (-> map (dissoc :code) (assoc :_id code))
    map))

(defn update-in-if-exists [map [& ks] fn & args]
  (if-let [previous-value (get-in map ks nil)]
    (apply update-in map ks fn args)
    map))

(defn class-to-string [map]
  (update-in-if-exists map [:class] #(.getName %)))

(defn date-time-to-millis [map-]
  (zipmap (keys map-)
          (map #(if (instance? DateTime %) (.getMillis %) %) (vals map-))))

(def standard-transformations
  (comp code-to-mongo-id class-to-string date-time-to-millis))

(defn unit-as-string [map]
  (update-in-if-exists map [:unitType] #(.asString %)))

(defn execution-period-to-map [map]
  (update-in-if-exists map [:executionPeriod] #(-> (bean %)
                                                  standard-transformations
                                                  unit-as-string)))
(defprotocol Mongoable
  (to-mongo [this]))

(extend-protocol Mongoable
  Robot
  (to-mongo [robot] (-> (bean robot) standard-transformations))

  PeriodicalExecution
  (to-mongo [periodical-execution] (-> (bean periodical-execution)
                                       execution-period-to-map
                                       standard-transformations))
  clojure.lang.PersistentArrayMap
  (to-mongo [map] map))

(defn save! [col entity]
  (mongo/insert! col (to-mongo entity)))

(defn get-db [{:keys [conn]}]
  (:db conn))

(defn find-unique [col id & options]
  (apply mongo/fetch col (concat options [:one? true :where {:_id id}])))

(defn ensure-consistent-request [^DBApiLayer db f]
  (.requestStart db)
  (try
    (f)
    (finally (.requestDone db))))

(defmacro on [backend & body]
  `(if (not= (:conn ~backend) mongo-config/*mongo-config*)
     (mongo/with-mongo (:conn ~backend)
       (ensure-consistent-request (get-db ~backend) #(do ~@body)))
     (do ~@body)))

(defn create-robot
  [code transformerInMinilanguage transformerInXML creationTime description]
  (Robot. code transformerInMinilanguage transformerInXML creationTime description))

(defn create-periodical
  [code creationTime robotCode executionPeriod inputs]
  (PeriodicalExecution. code creationTime robotCode executionPeriod inputs))

(defn create-execution-result
  [code type createdFromCode creationTime executionTimeMilliseconds resultLines]
  (ExecutionResult. code
                    creationTime
                    type
                    createdFromCode
                    executionTimeMilliseconds
                    (into-array String resultLines)))

(defn at-key [key f]
  (fn [map] (f (key map))))

(defn date-time-at [key]
  (at-key key #(DateTime. %)))

(defn to-robot [map-from-mongo]
  (->> map-from-mongo
       ((juxt :_id
              :transformerInMinilanguage
              :transformerInXML
              (date-time-at :creationTime)
              :description))
       (apply create-robot)))

(defn from-robot-code [key]
  (at-key key (comp to-robot (partial find-unique :robots))))

(defn from-execution-period [key]
  (at-key key #(ExecutionPeriod.
                (:amount %)
                (ExecutionPeriod$Unit/parseUnit (:unitType %)))))

(defn to-periodical [map-from-mongo]
  (->> map-from-mongo
       ((juxt :_id
              (date-time-at :creationTime)
              :robotCode
              (from-execution-period :executionPeriod)
              :inputs))
       (apply create-periodical)))

(defn to-execution-result [map-from-mongo]
  (->> map-from-mongo
       ((juxt :_id
              (at-key :type #(ExecutionResult$Type/valueOf %))
              :createdFromCode
              (date-time-at :creationTime)
              (at-key :executionTimeMilliseconds #(.longValue %))
              :resultLines))
       (apply create-execution-result)))

(defn new-unique-code []
  (.toString (UUID/randomUUID)))

(defn insert-execution-at-initial-state [^Robot robot ^List inputs]
  (let [code (new-unique-code)]
    (save! :executions {:_id code
                        :inputs inputs
                        :type "ROBOT"
                        :creationTime (System/currentTimeMillis)
                        :createdFromCode (.getCode robot)})
    code))

(defn submit-execution! [^Robot robot ^List inputs]
  (let [code (insert-execution-at-initial-state robot inputs)]
    ;;TODO send execution to workers
    code))

(defrecord Backend [conn]
  IBackend

  (^void
   save [this ^Robot robot]
   (on this
       (save! :robots robot)))

  (^Robot
   find [this ^String robotCode]
   (on this
       (when-let [from-mongo-map (find-unique :robots robotCode)]
         (to-robot from-mongo-map))))

  (^String
   submitExecution [this ^Robot robot ^List inputs]
   (on this
       (save! :robots robot)
       (submit-execution! robot inputs)))

  (^String
   submitExecutionForExistentRobot [this ^String existentRobotCode ^List inputs]
   (on this
       (let [robot (.find this existentRobotCode)]
         (assert ((complement nil?) robot))
         (submit-execution! robot inputs))))

  (^Maybe
   retrieveExecution [this ^String executionCode]
   (when-let [found (find-unique :executions executionCode)]
     (if (:resultLines found)
       (Maybe/value (to-execution-result found))
       (Maybe/none))))

  (^void
   save [this ^PeriodicalExecution periodicalExecution]
   (on this
       (assert (find-unique :robots
                            (.getRobotCode periodicalExecution)))
       (save! :periodical-executions periodicalExecution)))

  (^PeriodicalExecution
   findPeriodicalExecution [this ^String code]
   (on this
       (when-let [map (find-unique :periodical-executions code)]
         (to-periodical map)))))

(defn create-backend  [& {:keys [host port db]}]
  (let [mongo-connection (mongo/make-connection db :host host :port port)
        _ (mongo/set-write-concern mongo-connection :strict)]
    (Backend. mongo-connection)))

(defrecord BackendBuilder [^String host ^String port ^String db]
  IBackendBuilder
  (^IBackend build [this]
             (create-backend :host host :port port :db db)))
