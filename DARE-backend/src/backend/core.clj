;; ### Introduction

;; Backend is responsible of implementing the functionality specified
;; by `es.uvigo.ei.sing.dare.domain.IBackend`. It stores and retrieves
;; domain objects to and from a MongoDB database.
;;
;; Besides it uses DARE-workers client part to send the executions.
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
           [java.util UUID List ArrayList Map Collection]
           [org.joda.time DateTime]
           [com.mongodb DB]))


;; A `PeriodicalExecution` can only be executed each hour at most.
(def ^{:dynamic true} *minimal-allowed-execution-period-ms* (* 60 60 1000))

;; The time allowed for a `PeriodicalExecution` to elapse executing
;; once it's been sent to the workers: 4 minutes.
(def ^{:dynamic true} *time-allowed-for-periodical-execution-ms* (* 4 60 1000))

;; The time allowed for a `Execution` to elapse executing: 2 minutes.
(def ^{:dynamic true} *time-allowed-for-execution-ms* (* 2 60 1000))

;; ### MongoDB utilities

(defn ensure-consistent-request
  [^DB db f]
  (.requestStart db)
  (try
    (f)
    (finally (.requestDone db))))

(defn get-db [{:keys [conn]}]
  (:db conn))

(defmacro on
  "Ensure that the provided `body` is executed using the same
  connection for each operation. Otherwise the writes done may not be
  seen, since they could have been written to a different slave and
  the replication is asynchronous."
  [backend & body]
  `(if (not= (:conn ~backend) mongo-config/*mongo-config*)
     (mongo/with-mongo (:conn ~backend)
       (ensure-consistent-request (get-db ~backend) #(do ~@body)))
     (do ~@body)))

;; ### Collections Names

;; Defining the collections names as variables, we avoid typos.

(def robots-coll :robots)

(def executions-coll :executions)

(def periodicals-coll :periodical-executions)

;; ### Domain objects => MongoDB

;; We have to transform the domain objects into documents (associative
;; maps). For that we transform the domain object into a map using the
;; builtin function `bean`. This gives us a map with the properties of
;; the domain object. To this map we have to apply some further
;; transformations and we have a map that can be saved as a document
;; for the proper collection.

(defn code-to-mongo-id
  "It converts the code property to the `_id` property. MongoDB uses
the `_id` property as primary identifier of a document"
  [map]
  (if-let [code (get map :code nil)]
    (-> map (dissoc :code) (assoc :_id code))
    map))

(defn update-in-if-exists
  "Updates the value found under the provided keys: `ks`. If there is
no value associated, it does nothing."
  [map [& ks] fn & args]
  (if-let [previous-value (get-in map ks nil)]
    (apply update-in map ks fn args)
    map))

(defn class-to-string
  "MongoDB wouldn't know how to handle a Class object, the name is
stored instead."
  [map]
  (update-in-if-exists map [:class] #(.getName %)))

(defn date-time-to-millis
  "MongoDB wouldn't know how to handle a DateTime object, so we store
the milliseconds number since epoch."
  [map-]
  (zipmap (keys map-)
          (map #(if (instance? DateTime %) (.getMillis %) %) (vals map-))))

;; Transformations needed by all domain entities.
(def standard-transformations
  (comp code-to-mongo-id class-to-string date-time-to-millis))

(defn unit-as-string [map]
  (update-in-if-exists map [:unitType] #(.asString %)))

(defn execution-period-to-map
  "An ExecutionPeriod must be transformed into something that can be
 stored in a MongoDB too."
  [map]
  (update-in-if-exists map [:executionPeriod] #(-> (bean %)
                                                  standard-transformations
                                                  unit-as-string)))
(defn now-ms
  "The milliseconds since epoch for right now"
  []
  (System/currentTimeMillis))

(defn add-scheduling-fields
  "Adds the scheduling fields to a document to be stored as a
  periodical execution. The `:next-execution-ms` is now so the
  PeriodicalExecution can be immediately scheduled."
  [map]
  (assoc map
    :next-execution-ms (now-ms)
    :scheduled false))

;; We define a Mongoable protocol that will be implemeted for all
;; DARE-domain entities that can be saved into the MongoDB database.
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

(defn save!
  "It saves the provided entity:

   1. It converts it to a map suitable to be stored.
   2. It inserts it."
  [col entity]
  (mongo/insert! col (to-mongo entity)))

;; ### MongoDB => Domain Objects

;; In order to retrieve a DARE-domain object from MongoDB we have to
;; retrieve the document with the provided code and convert such
;; document to the domain object.

;; For that we define `create-*` functions that allow us to create the
;; corresponding domain object. The parameters for that function are
;; calculated via the `juxt` function.

(defn at-key
  "It creates a function that given a map will return the result of
applying the function `f` to the value of the map associated to the
key `key`."
  [key f]
  (fn [map] (f (get map key))))

(defn date-time-at
  "It creates a function that given a map will return the result of
  creating a DateTime from the milliseconds associated to the key
  `key`."
  [key]
  (at-key key #(DateTime. %)))

(defn from-execution-period
  "It creates a function that given a map will return the result of
  creating a ExecutionPeriod from the map storing it."
  [key]
  (at-key key #(ExecutionPeriod.
                (:amount %)
                (ExecutionPeriod$Unit/parseUnit (:unitType %)))))

(defn create-robot
  "Build a DARE-domain `Robot` from params."
  ^Robot
  [code transformerInMinilanguage transformerInXML creationTime description]
  (Robot. code transformerInMinilanguage transformerInXML creationTime description))

(defn to-robot
  "Build a DARE-domain `Robot` from a suitable mongo document."
  ^Robot
  [map-from-mongo]
  (->> map-from-mongo
       ((juxt :_id
              :transformerInMinilanguage
              :transformerInXML
              (date-time-at :creationTime)
              :description))
       (apply create-robot)))

(defn create-execution-result
  "Build a DARE-domain `ExecutionResult` from params."
  ^ExecutionResult
  [code optionalRobotCode creationTime executionTimeMilliseconds inputs resultLines]
  (ExecutionResult. code
                    creationTime
                    optionalRobotCode
                    executionTimeMilliseconds
                    inputs
                    (into-array String resultLines)))

(defn to-execution-result
  "Build a DARE-domain `ExecutionResult` from a suitable mongo
document."
  [map-from-mongo]
  (when map-from-mongo
    (->> map-from-mongo
         ((juxt :_id
                :optionalRobotCode
                (date-time-at :creationTime)
                (at-key :executionTimeMilliseconds #(.longValue %))
                :inputs
                :resultLines))
         (apply create-execution-result))))

(defn create-periodical
  "Build a DARE-domain `PeriodicalExecution` from params."
  ^PeriodicalExecution
  [code creationTime robotCode executionPeriod inputs lastExecution]
  (PeriodicalExecution. code creationTime robotCode executionPeriod inputs lastExecution))

(defn to-periodical
  "Build a DARE-domain `PeriodicalExecution` from a suitable mongo
  document."
  ^PeriodicalExecution
  [map-from-mongo]
    (->> map-from-mongo
       ((juxt :_id
              (date-time-at :creationTime)
              :robotCode
              (from-execution-period :executionPeriod)
              :inputs
              (at-key :lastExecution
                      (comp to-execution-result
                            #(when % (assoc %
                                       :_id (:_id map-from-mongo)
                                       :inputs (:inputs map-from-mongo)))))))
       (apply create-periodical)))

(defn to-domain-fn
  "Returns a fuction that would convert a document of the given
collection to the corresponding domain entity"
  [coll]
  (condp = coll
    robots-coll to-robot
    executions-coll to-execution-result
    periodicals-coll to-periodical))

(defn mongo->domain [coll map-from-mongo]
  (when map-from-mongo
    ((to-domain-fn coll) map-from-mongo)))

(defn find-unique
  "Finds the document of _id `id` at collection `coll`"
  [coll id & options]
  (apply mongo/fetch coll (concat options [:one? true :where {:_id id}])))

(defn find-unique->domain
  "Finds the document of `code` at collection `coll` and converts it
  to a domain object, a Java entity."
  [coll code & options]
  (->> (apply find-unique coll code options) (mongo->domain coll)))

;; ### Submit Execution for Robot

(defn new-unique-code
  "A new unique code following DARE format for identifiers: an UUID
  string."
  []
  (.toString (UUID/randomUUID)))

(defn insert-execution-at-initial-state!
  "An execution is inserted at its initial state, i.e. it still
  doesn't have its results."
  [^Robot robot ^List inputs]
  (let [code (new-unique-code)]
    (save! executions-coll {:_id code
                            :inputs inputs
                            :creationTime (now-ms)
                            :optionalRobotCode (.getCode robot)})
    code))

(defn common-request-part
  "It defines the part that is common for requests to both periodical
  executions and standard ones"
  [^Robot robot ^List inputs]
  {:inputs inputs
   :robotXML (.getTransformerInXML robot)})

(defn submit-execution-for-robot!
  "It submits an execution for the given `robot`"
  [workers-handler ^Robot robot ^List inputs]
  (let [code (insert-execution-at-initial-state! robot inputs)]
    (workers/send-request! workers-handler (assoc (common-request-part robot inputs)
                                             :result-code code))
    code))

;; ### Submit Executions for Periodical Executions

(defn- next-execution
  "Given the `execution-period` return when it should be executed again."
  [^ExecutionPeriod execution-period]
  (max
   (+ (now-ms) *minimal-allowed-execution-period-ms*)
   (.getMillis (.calculateNextExecution execution-period (DateTime.)))))

(defn schedule-periodical!
  "Schedules an execution for a periodical execution. It waits until the very moment it has to be executed to submit the execution to the workers.

  This method is asynchronous, it returns the control to the caller
  almost immediately. It's not blocked waiting for the execution time
  to come. Instead a Lamina `wait-stage` is used, so that when the
  execution time comes it's executed on a new thread.

  Once the time has come the request is sent to the workers. A field
  `:next-execution-ms` is specified so the worker stores the next
  execution time, once the execution is finished. This avoids a
  dependecy to DARE-domain in DARE-workers."

  [workers-handler {:keys [periodical-code robot-code inputs execution-period
                           next-execution-ms] :as request}]
  (let [robot (find-unique->domain robots-coll robot-code)]
    (when-not robot (throw (RuntimeException.
                            (str "Robot with code " robot-code " not found "
                                 "for periodical with code " periodical-code))))
    (l/run-pipeline robot
      :error-handler (fn [ex]
                       (log/error
                        (str "Error submitting execution for periodical with code: "
                             periodical-code) ex))
      (l/wait-stage (- next-execution-ms (now-ms)))
      (fn [robot]
        (log/info (str "sending execution for periodical with code: "
                       periodical-code))
        (workers/send-request! workers-handler
                               (assoc (common-request-part robot (ArrayList. inputs))
                                 :periodical-code periodical-code
                                 :next-execution-ms (next-execution execution-period)))))))

(defn mark-as-scheduled!
  "It marks the periodical execution as scheduled. A scheduled
  periodical execution is not sent again, unless its time to be
  executed times out.

  See `clean-scheduled-but-not-completed`."
  [periodical-code]
  (mongo/update! periodicals-coll
                 {:_id periodical-code}
                 {:$set {:scheduled true}} :upsert false))

(defmacro continue-on-error
  "If the wrapped body fails, the exeption is logged but the execution
continues."
  [name & body]
  `(try ~@body
        (catch Throwable ~'e
          (log/error (str "error while " ~name) ~'e))))

(defn to-submit-map
  [{:keys [_id robotCode inputs next-execution-ms] :as  each}]
  {:periodical-code _id
   :robot-code robotCode
   :inputs inputs
   :next-execution-ms next-execution-ms
   :execution-period
   ((from-execution-period :executionPeriod) each)})

(defn schedule-periodicals!
  "Submits the periodicals found with the provided `where` clause.
   It uses `to-submit-map` to adapt the results of the query to the
   fields required by `schedule-periodical!` For each
   periodical document found we mark it as scheduled and send it."
  [workers where]
  (let [select (zipmap [:_id :robotCode :inputs :executionPeriod :next-execution-ms]
                       (repeat 1))
        found (mongo/fetch periodicals-coll :where where :only select)]
    (log/info (str "scheduling " (count found) " periodical executions"))
    (doseq [each found]
      (continue-on-error (str "submitting periodical execution: " (:_id each))
        (mark-as-scheduled! (:_id each))
        (schedule-periodical! workers (to-submit-map each))))))

(defn unscheduled-on-next
  "where clause for `schedule-periodicals!` that retrieves not
scheduled periodical executions which must be scheduled within the
next `expiring-on-next-ms` milliseconds."
  [expiring-on-next-ms]
  (let [before-time (+ (now-ms) expiring-on-next-ms)]
    {:scheduled false
     :next-execution-ms {:$lte before-time}}))

(defn with-id
  "where clause for `schedule-periodicals!` that retrieves the
  execution such _id is equal to`code`."
  [code]
  {:_id code})

(defn unschedule-timedout!
  "The ones scheduled but that are taking much time executing are
  marked as unscheduled, so they can be scheduled again.

  It's called periodically from the background operation
  `clean-scheduled-but-not-completed`."
  [time-allowed-to-execute-ms]
  (mongo/update! periodicals-coll
                 {:scheduled true
                  :next-execution-ms {:$lte (- (now-ms)
                                               time-allowed-to-execute-ms)}}
                 {:$set {:scheduled false}}
                 :upsert false :multiple true))

;; ### Backend implementation.

(defn check-no-execution-time-exceeded
  "Throws exception if the time from the start exceeds
`max-time-allowed-ms`."
  [start-time-fn max-time-allowed-ms]
  (when (> (now-ms) (+ (start-time-fn) max-time-allowed-ms))
    (throw (ExecutionTimeExceededException. (-> max-time-allowed-ms
                                                (/ 1000)
                                                (long))))))

(defn check-no-error-on-execution
  "It throws an exception if there is error on execution."
  [execution]
  (when-let [{:keys [type message] :as error} (:error execution)]
         (throw (case (keyword type)
                  :error (ExecutionFailedException. message)
                  :timeout (ExecutionTimeExceededException. message)))))

(defn execution-or-none
  "It returns a value if the the execution has resultLines (it has
  completed). Otherwise returns none, unless it has timed out."
  [execution]
  (if (:resultLines execution)
    (Maybe/value (to-execution-result execution))
    (do
      (check-no-execution-time-exceeded #(:creationTime execution)
                                        *time-allowed-for-execution-ms*)
      (Maybe/none))))

(defn new-periodical!
  "It saves the given periodical and schedules its first execution."
  [backend ^PeriodicalExecution periodicalExecution]
  (assert (find-unique robots-coll
                       (.getRobotCode periodicalExecution)))
  (save! periodicals-coll periodicalExecution)
  (future
    (on backend
     (schedule-periodicals! (:workers backend)
                            (with-id (.getCode periodicalExecution))))))

(defn close-backend
  "After calling this the Backend is no longer usable. It's marked as
closed, the workers are shutdown and the connection to MongoDB is
closed too."
  [backend]
  (reset! (:closed backend) true)
  (workers/shutdown! (:workers backend))
  (on backend
    (mongo/close-connection mongo-config/*mongo-config*)))

;; Backend implements the IBackend interface. It receives:
;;
;; 1. `conn`: the result of `mongo/make-connection`.
;; 2. `workers`: used to communicate with the workers.
;; 3. `closed`: an atom that is put to true when closing this object.
(defrecord Backend [conn workers closed]
  IBackend
  (^void
    save [this ^Robot robot]
    (on this
      (save! robots-coll robot)))

  (^Robot
   find [this ^String robotCode]
    (on this
      (find-unique->domain robots-coll robotCode)))

  (^String
   submitExecution [this ^Robot robot ^List inputs]
   (on this
     (save! robots-coll robot)
     (submit-execution-for-robot! (:workers this) robot inputs)))

  (^String
   submitExecutionForExistentRobot [this ^String existentRobotCode ^List inputs]
   (on this
     (let [robot (.find this existentRobotCode)]
       (assert ((complement nil?) robot))
       (submit-execution-for-robot! (:workers this) robot inputs))))

  (^Maybe
    retrieveExecution
    [this ^String executionCode]
    (on this
     (when-let [found (find-unique executions-coll executionCode)]
       (check-no-error-on-execution found)
       (execution-or-none found))))

  (^void
    save [this ^PeriodicalExecution periodicalExecution]
    (on this
      (new-periodical! this periodicalExecution)))

  (^PeriodicalExecution
   findPeriodicalExecution [this ^String code]
    (on this
      (find-unique->domain periodicals-coll code)))

  (^void
    deleteExecution [this ^String code]
    (on this
      (mongo/destroy! executions-coll {:_id code})))

  (^void
    deleteRobot [this ^String code]
    (on this
      (mongo/destroy! robots-coll {:_id code})
      (mongo/destroy! executions-coll {:optionalRobotCode code})
      (mongo/destroy! periodicals-coll {:robotCode code})))

  (^void
    deletePeriodical [this ^String code]
    (on this
      (mongo/destroy! periodicals-coll {:_id code})))

  (^void
    close [this]
    (close-backend this)))

;; ### Functions for waiting for a result

;; They are used from unit tests.

(defn poll-for-execution-result
  "Checks each 500 ms if the ExecutionResult with the given code has
completed."
  [backend code]
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

(defn poll-for-next-periodical-execution-result
  "Checks each 500 ms if there is a new result for the
PeriodicalExecution with the given code"
  [backend code]
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
        (when (not= (creation-time-fn current)
                    (creation-time-fn original-execution))
          (l/complete current)))
      (l/wait-stage 500)
      (fn [_]
        (l/restart)))))

;; ### Workers handler creation

;; The workers handler is responsible of sending an execution to a
;; worker if possible.

(defn- query-workers
  "Query the workers that are registered on the MongoDB."
  [backend]
  (on backend
      (->> (mongo/fetch :workers :only [:host :port])
           (map (fn [m] [(:host m) (-> m :port int)]))
           (into []))))

(defn- create-workers-handler
  "Create a workers handler with the workers registered."
  [mongo-connection]
  (apply workers/workers-handler!
         (query-workers {:conn mongo-connection})))

;; ### Periodical background operations.

(def ^{:dynamic true} *fast-testing-polling-mode* false)

(defn convert-wait
  "When testing we want to reduce the period of time between executing
a background operation. For example, instead of executing a background
operation each minute, it's executed each 60 milliseconds."
  [time-ms]
  (if *fast-testing-polling-mode*
    (-> time-ms (/ 1000) (int))
    time-ms))

(def ^{:dynamic true} *print-background-task* true)

(defmacro while-backend-not-closed
  "Defines all the boilerplate needed to execute some operation
periodically until the backend is closed."
  [{:keys [task-name backend period]} & body]
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

;; Each minute check if there are new workers.

(def ^{:dynamic true} *polling-interval-for-new-workers* (* 1 60 1000))

(defn- poll-new-workers
  [backend]
  (while-backend-not-closed {:task-name "polling new workers" :backend backend
                             :period *polling-interval-for-new-workers*}
    (apply workers/add-new-workers! (:workers backend)
           (query-workers backend))))

;; Submit periodical executions

;; Each five minutes checks if there are new periodical executions
;; that must be scheduled.
(def ^{:dynamic true} *polling-interval-for-periodical* (* 5 60 1000))

(defn submit-periodical-executions
  "It schedules the periodical executions that need to be executed on
the next `*minimal-allowed-execution-period-ms*`. These periodical
executions are not sent immediately, only marked as scheduled and sent
when their time comes."
  [backend]
  (while-backend-not-closed {:task-name "submitting periodical executions"
                             :backend backend
                             :period *polling-interval-for-periodical*}
    (on backend
        (schedule-periodicals! (:workers backend)
                             (unscheduled-on-next
                              *minimal-allowed-execution-period-ms*)))))

;; Each two minutes, it's checked if there are periodical executions
;; that have been sent but not executed in a reasonable time. These
;; must be marked as unscheduled to be sent again.
(def ^{:dynamic true} *polling-interval-for-cleaning-expired-executions*
  (* 2 60 1000))

(defn clean-scheduled-but-not-completed
  [backend]
  (while-backend-not-closed {:task-name "cleaning scheduled but not completed"
                             :backend backend
                             :period *polling-interval-for-cleaning-expired-executions*}
    (on backend
        (unschedule-timedout! *time-allowed-for-periodical-execution-ms*))))

;; ### Backend Creation

(defn add-indexes!
  "Ensure the existence of indexes to speed up queries."
  [backend]
  (on backend
    (mongo/add-index! periodicals-coll
                      {:scheduled 1 :next-execution-ms 1 :robotCode 1})
    (mongo/add-index! executions-coll {:optionalRobotCode 1})))

(defn- only-defined [map]
  (->> (filter second map)
       (apply concat)
       (apply hash-map)))

(defn create-backend
  "Create the backend and launch the background operations.

  The write concern is set to strict so write operations at MongoDB
  are at least written to memory."
  [& {:keys [host port db]}]
  (let [mongo-connection
        (mongo/make-connection db
                               (only-defined {:host host :port port}))
        _ (mongo/set-write-concern mongo-connection :strict)]
    (let [workers-handler (create-workers-handler mongo-connection)
          backend (Backend. mongo-connection workers-handler (atom false))
          submitter (submit-periodical-executions backend)
          cleaner (clean-scheduled-but-not-completed backend)
          poller (poll-new-workers backend)]
      (add-indexes! backend)
      (log/info (str "Backend started. Connected to " host
                     " on " port + " with database " db))
      backend)))

(defn keywordize
  "Ensure that the keys of the provided map are keywords."
  [m]
  (zipmap (->> (keys m)
               (map keyword))
          (vals m)))

;; An implementation of IBackendBuilder that provides parameters to
;; create-backend.
(defrecord BackendBuilder []
  IBackendBuilder
  (^IBackend build [this ^Map parameters]
    (let [{:keys [mongo-host mongo-port mongo-db]}
          (keywordize parameters)]
      (create-backend :host mongo-host
                      :port (Integer/parseInt (str mongo-port))
                      :db mongo-db)))
  (^Collection getParametersNeeded [this]
    ["mongo-host" "mongo-port" "mongo-db"]))
