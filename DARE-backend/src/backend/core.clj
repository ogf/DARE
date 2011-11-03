(ns backend.core
  (:require [somnium.congomongo :as mongo]
            [somnium.congomongo.config :as mongo-config]
            [workers.client :as workers]
            [clojure.contrib.logging :as log]
            [lamina.core :as l])
  (:import [es.uvigo.ei.sing.dare.domain IBackend Maybe IBackendBuilder
            ExecutionTimeExceededException ExecutionFailedException]
           [es.uvigo.ei.sing.dare.entities
            Robot PeriodicalExecution ExecutionPeriod ExecutionPeriod$Unit ExecutionResult]
           [java.util UUID List ArrayList]
           [org.joda.time DateTime]
           [com.mongodb DBApiLayer]))

(def ^{:dynamic true} *print-background-task* true)

(def ^{:dynamic true} *fast-testing-polling-mode* false)

(def ^{:dynamic true} *polling-interval-for-new-workers* (* 1 60 1000))

(def ^{:dynamic true} *polling-interval-for-periodical* (* 5 60 1000))

(def ^{:dynamic true} *minimal-allowed-execution-period-ms* (* 60 60 1000))

(def ^{:dynamic true} *polling-interval-for-cleaning-expired-executions*
  (* 2 60 1000))

(def ^{:dynamic true} *time-allowed-for-periodical-execution-ms* (* 4 60 1000))

(def ^{:dynamic true} *time-allowed-for-execution-ms* (* 2 60 1000))

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
(defn now-ms []
  (System/currentTimeMillis))

(defn- next-execution [^ExecutionPeriod execution-period]
  (max
   (+ (now-ms) *minimal-allowed-execution-period-ms*)
   (.getMillis (.calculateNextExecution execution-period (DateTime.)))))

(defn add-scheduling-fields [map]
  (assoc map
    :next-execution-ms (now-ms)
    :scheduled false
    :execution-sent-at nil))

(defprotocol Mongoable
  (to-mongo [this]))

(extend-protocol Mongoable
  Robot
  (to-mongo [robot] (-> (bean robot) standard-transformations))

  PeriodicalExecution
  (to-mongo [periodical-execution] (-> (bean periodical-execution)
                                       add-scheduling-fields
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
              (at-key :lastExecution
                      (comp to-execution-result
                            #(when % (assoc % :_id (:_id map-from-mongo)))))))
       (apply create-periodical)))

(defn new-unique-code []
  (.toString (UUID/randomUUID)))

(defn insert-execution-at-initial-state [^Robot robot ^List inputs]
  (let [code (new-unique-code)]
    (save! :executions {:_id code
                        :inputs inputs
                        :creationTime (now-ms)
                        :optionalRobotCode (.getCode robot)})
    code))

(defn common-request-part [^Robot robot ^List inputs]
  {:inputs inputs
   :robotXML (.getTransformerInXML robot)})

(defn submit-execution-for-robot! [workers-handler ^Robot robot ^List inputs]
  (let [code (insert-execution-at-initial-state robot inputs)]
    (workers/send-request! workers-handler (assoc (common-request-part robot inputs)
                                             :result-code code))
    code))

(defn find-robot [robot-code]
  (when-let [from-mongo-map (find-unique :robots robot-code)]
    (to-robot from-mongo-map)))

(defn mark-as-execution-sent [periodical-code time-execution-was-sent]
  (mongo/update! :periodical-executions
                 {:_id periodical-code}
                 {:$set {:execution-sent-at time-execution-was-sent}}
                 :upsert false))

(defn submit-execution-for-periodical-execution!
  [workers-handler
   {:keys [periodical-code robot-code inputs execution-period next-execution-ms] :as request}]
  (l/run-pipeline (find-robot robot-code)
    :error-handler (fn [ex]
                     (log/error
                      (str "Error submitting execution for periodical with code "
                           periodical-code) ex))
    (l/wait-stage (- next-execution-ms (now-ms)))
    (fn [robot]
      (log/info (str "sending execution for periodical with code: " periodical-code))
      (workers/send-request! workers-handler
                             (assoc (common-request-part robot (ArrayList. inputs))
                               :periodical-code periodical-code
                               :next-execution-ms (next-execution execution-period)))
      (mark-as-execution-sent periodical-code (now-ms)))))

(defn- check-no-execution-time-exceeded [start-time-fn max-time-allowed-ms]
  (when (> (now-ms) (+ (start-time-fn) max-time-allowed-ms))
            (throw (ExecutionTimeExceededException. (-> max-time-allowed-ms
                                                        (/ 1000)
                                                        (int))))))

(defrecord Backend [conn workers closed]
  IBackend

  (^void
   save [this ^Robot robot]
   (on this
       (save! :robots robot)))

  (^Robot
   find [this ^String robotCode]
    (on this
        (find-robot robotCode)))

  (^String
   submitExecution [this ^Robot robot ^List inputs]
   (on this
       (save! :robots robot)
       (submit-execution-for-robot! (:workers this) robot inputs)))

  (^String
   submitExecutionForExistentRobot [this ^String existentRobotCode ^List inputs]
   (on this
       (let [robot (.find this existentRobotCode)]
         (assert ((complement nil?) robot))
         (submit-execution-for-robot! (:workers this) robot inputs))))

  (^Maybe
    retrieveExecution [this ^String executionCode]
    (on this
        (when-let [found (find-unique :executions executionCode)]
          (check-no-execution-time-exceeded #(:creationTime found)
                                            *time-allowed-for-execution-ms*)
          (when-let [{:keys [type message] :as error} (:error found)]
            (throw (case (keyword type)
                     :error (ExecutionFailedException. message)
                     :timeout (ExecutionTimeExceededException. message))))
          (if (:resultLines found)
            (Maybe/value (to-execution-result found))
            (Maybe/none)))))

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

(defn poll-for-execution-result [backend code]
  (l/run-pipeline nil
   (fn [_]
     (l/task
      (.retrieveExecution backend code)))
   (fn [^Maybe result]
     (when (.hasValue result)
       (l/complete (.getValue result))))
   (l/wait-stage 500)
   (fn [_]
     (l/restart))))


(defn poll-for-next-periodical-execution-result [backend code]
  (let [retrieve-current #(.findPeriodicalExecution backend code)
        original-execution (.getLastExecutionResult (retrieve-current))
        creation-time-fn #(when % (.. % getCreationTime getMillis))]
    (l/run-pipeline nil
      :error-handler (fn [ex]
                       (log/error
                        (str "error polling for periodical execution with code "
                             code) ex))
      (fn [_] (l/task (.getLastExecutionResult (retrieve-current))))
      (fn [current]
        (when (not= (creation-time-fn current) (creation-time-fn original-execution))
          (l/complete current)))
      (l/wait-stage 500)
      (fn [_]
        (l/restart)))))

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


(defn convert-wait [time-ms]
  (if *fast-testing-polling-mode*
    (-> time-ms (/ 1000) (int))
    time-ms))

(defmacro while-backend-not-closed [{:keys [task-name backend period]} & body]
  `(l/run-pipeline nil
                   :error-handler (fn [~'ex]
                                    (when-not @(:closed ~backend)
                                      (log/error (str "exception " ~task-name) ~'ex)
                                      (l/restart)))
                   (l/wait-stage (convert-wait ~period))
                   (fn [~'_]
                     (when-not @(:closed ~backend)
                       (when *print-background-task*
                         (log/info (str "Executing " ~task-name)))
                       ~@body))
                   (fn [~'_]
                     (if-not @(:closed ~backend)
                       (l/restart)))))

(defn- poll-new-workers [backend]
  (while-backend-not-closed {:task-name "polling new workers" :backend backend
                             :period *polling-interval-for-new-workers*}
    (apply workers/add-new-workers! (:workers backend) (query-workers backend))))


(defn mark-as-scheduled [periodical-code]
  (mongo/update! :periodical-executions
                 {:_id periodical-code}
                 {:$set {:scheduled true}} :upsert false))

(defmacro continue-on-error [name & body]
  `(try ~@body
        (catch Throwable ~'e
          (log/error (str "error while " ~name) ~'e))))

(defn submit-periodical [workers expiring-on-next-ms]
  (let [before-time (+ (now-ms) expiring-on-next-ms)
        where {:scheduled false
               :next-execution-ms {:$lte before-time}}
        select (zipmap [:_id :robotCode :inputs :executionPeriod :next-execution-ms]
                       (repeat 1))
        to-submit-map (fn [{:keys [_id robotCode inputs next-execution-ms] :as each}]
                        {:periodical-code _id
                         :robot-code robotCode
                         :inputs inputs
                         :next-execution-ms next-execution-ms
                         :execution-period
                         ((from-execution-period :executionPeriod) each)})
        found (mongo/fetch :periodical-executions :where where :only select)]
    (log/info (str "scheduling " (count found) " periodical executions"))
    (doseq [each found]
      (continue-on-error (str "submitting periodical execution: " (:_id each))
        (mark-as-scheduled (:_id each))
        (submit-execution-for-periodical-execution! workers (to-submit-map each))))))

(defn submit-periodical-executions [backend]
  (while-backend-not-closed {:task-name "submitting periodical executions"
                             :backend backend
                             :period *polling-interval-for-periodical*}
    (on backend
        (submit-periodical (:workers backend)
                           *minimal-allowed-execution-period-ms*))))

(defn clean-not-completed [time-allowed-to-execute-ms]
  (mongo/update! :periodical-executions
                 {:execution-sent-at {:$lte (- (now-ms) time-allowed-to-execute-ms)}}
                 {:$set {:execution-sent-at nil
                         :scheduled false}}
                 :upsert false :multiple true))

(defn clean-scheduled-but-not-completed [backend]
  (while-backend-not-closed {:task-name "cleaning scheduled but not completed"
                             :backend backend
                             :period *polling-interval-for-cleaning-expired-executions*}
    (on backend
      (clean-not-completed *time-allowed-for-periodical-execution-ms*))))

(defn add-indexes [backend]
  (on backend
    (mongo/add-index! :periodical-executions
                      {:scheduled 1 :next-execution-ms 1})
    (mongo/add-index! :periodical-executions
                      {:execution-sent-at 1})))

(defn create-backend  [& {:keys [host port db]}]
  (let [mongo-connection (mongo/make-connection db
                                                (only-defined {:host host :port port}))
        _ (mongo/set-write-concern mongo-connection :strict)]
    (let [workers-handler (create-workers-handler mongo-connection)
          backend (Backend. mongo-connection workers-handler (atom false))
          submitter (submit-periodical-executions backend)
          cleaner (clean-scheduled-but-not-completed backend)
          poller (poll-new-workers backend)]
      (add-indexes backend)
      (log/info (str "Backend started. Connected to " host
                     " on " port + " with database " db))
      backend)))

(defrecord BackendBuilder [^String host ^int port ^String db]
  IBackendBuilder
  (^IBackend build [this]
             (create-backend :host host :port port :db db)))
