(ns backend.core
  (:require [somnium.congomongo :as mongo]
            [somnium.congomongo.config :as mongo-config]
            [workers.client :as workers]
            [clojure.contrib.logging :as log]
            [lamina.core :as l])
  (:import [es.uvigo.ei.sing.dare.domain IBackend Maybe IBackendBuilder]
           [es.uvigo.ei.sing.dare.entities
            Robot PeriodicalExecution ExecutionPeriod ExecutionPeriod$Unit ExecutionResult]
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
  [code creationTime robotCode executionPeriod inputs lastExecution]
  (PeriodicalExecution. code creationTime robotCode executionPeriod inputs lastExecution))

(defn create-execution-result
  [code optionalRobotCode creationTime executionTimeMilliseconds resultLines]
  (ExecutionResult. code
                    creationTime
                    optionalRobotCode
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

(defn to-execution-result [map-from-mongo]
  (when map-from-mongo
    (->> map-from-mongo
         ((juxt :_id
                :optionalRobotCode
                (date-time-at :creationTime)
                (at-key :executionTimeMilliseconds #(.longValue %))
                :resultLines))
         (apply create-execution-result))))

(defn to-periodical [map-from-mongo]
  (->> map-from-mongo
       ((juxt :_id
              (date-time-at :creationTime)
              :robotCode
              (from-execution-period :executionPeriod)
              :inputs
              (at-key :lastExecution to-execution-result)))
       (apply create-periodical)))

(defn new-unique-code []
  (.toString (UUID/randomUUID)))

(defn insert-execution-at-initial-state [^Robot robot ^List inputs]
  (let [code (new-unique-code)]
    (save! :executions {:_id code
                        :inputs inputs
                        :creationTime (System/currentTimeMillis)
                        :optionalRobotCode (.getCode robot)})
    code))

(defn submit-execution! [^Robot robot ^List inputs]
  (let [code (insert-execution-at-initial-state robot inputs)]
    ;;TODO send execution to workers
    code))

(defrecord Backend [conn workers closed]
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
         (to-periodical map))))

  (^void
   close [this]
   (reset! (:closed this) true)
   (workers/shutdown! (:workers this))
   (on this
       (mongo/close-connection mongo-config/*mongo-config*))))


(defn- only-defined [map]
  (->> (filter second map)
       (apply concat)
       (apply hash-map)))

(defn- query-workers [backend]
  (on backend
      (->> (mongo/fetch :workers :only [:host :port])
           (map (fn [m] [(:host m) (:port m)]))
           (into []))))

(defn- create-workers-handler [mongo-connection]
  (apply workers/workers-handler!
         (query-workers {:conn mongo-connection})))

(defn- poll-new-workers [backend]
  (l/run-pipeline nil
                  :error-handler (fn [ex]
                                   (log/error "exception polling new workers" ex)
                                   (l/restart))
                  (l/wait-stage 10000)
                  (fn [_]
                    (when @(:closed backend)
                      (l/task (workers/add-new-workers! (:workers backend)
                                                        (query-workers backend)))))
                  (fn [_]
                    (if-not @(:closed backend)
                      (l/restart)))))

(defn create-backend  [& {:keys [host port db]}]
  (let [mongo-connection (mongo/make-connection db
                                                (only-defined {:host host :port port}))
        _ (mongo/set-write-concern mongo-connection :strict)]
    (let [workers-handler (create-workers-handler mongo-connection)
          backend (Backend. mongo-connection workers-handler (atom false))
          poller (poll-new-workers backend)]
      (log/info (str "Backend started. Connected to " host
                     " on " port + " with database " db))
      backend)))

(defrecord BackendBuilder [^String host ^int port ^String db]
  IBackendBuilder
  (^IBackend build [this]
             (create-backend :host host :port port :db db)))
