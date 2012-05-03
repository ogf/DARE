;; Server part of DARE-workers. It listens in a port waiting for
;; execution requests. Each time it receives a requests sends a
;; response acknowledging that has accepted it.
;; The accepted request is eventually executed and its response
;; written to the appropiate MongoDB collection.
(ns workers.server
  (:use gloss.core gloss.io lamina.core clojure.tools.cli)
  (:require [aleph.tcp :as tcp]
            [lamina.connections :as c]
            [somnium.congomongo :as mongo]
            [clojure.contrib.logging :as log]
            [clj-stacktrace.repl :as stacktrace])
  (:import [java.util concurrent.Executors UUID]
           [java.net InetSocketAddress InetAddress NetworkInterface]
           [es.uvigo.ei.sing.dare.util XMLUtil]
           [es.uvigo.ei.sing.stringeditor Util XMLInputOutput])
  (:gen-class))


;; ### Storing result

;; The result of the execution must be stored either on the
;; periodical-executions or executions collection.

(defn db-update!
  "It updates the document of _id `code` in `collection` with the
provided mongo-updates`"
  [collection code mongo-updates]
  (mongo/update! collection {:_id code} mongo-updates :upsert false))

(defn adapt-updated-fields
  "In the case of periodical-executions collection, the result must be
stored at the lastExecution field. Besides the scheduled flag must be
set to false and the next-execution-ms to the value provided. So the
periodical execution is left in a state on which it's not rescheduled
immediately and can be scheduled again when its time comes.

 In the case of an executions collection the updates are applied
directly to the topmost level of the document."
  [collection next-execution-ms fields]
  (case collection
    :periodical-executions (assoc {:lastExecution
                                   (assoc fields :creationTime
                                          (System/currentTimeMillis))}
                             :next-execution-ms next-execution-ms
                             :scheduled false)
    fields))

(defn db-execution-completed!
  "Updating the MongoDB database with the produced result."
  [collection code next-execution-ms & {:as updated-fields}]
  (db-update! collection code
              {:$set (adapt-updated-fields
                      collection next-execution-ms updated-fields)
               :$unset {:error 1}}))

(defn db-execution-error!
  "Updating the MongoDB with the produced error."
  [collection code fields-to-update]
  (db-update! collection code {:$set fields-to-update}))

;; ### Execution

(defn millis-elapsed-since
  "Returns a seq with the time ellapsed from each of the provided
times in milliseconds to right now."
  [& times]
  (let [now (System/currentTimeMillis)]
    (map #(- now %) times)))

(defn check-for-interruption
  "Checks if the thread has been interrupted."
  [x]
  (when (Thread/interrupted)
    (throw (InterruptedException.)))
  x)

(defn execute-robot
  "Do the robot execution. For that we create a transformer and run it
using an utility class from aAUTOMATOR."
  [robotXML inputs]
  (-> (XMLUtil/toDocument robotXML)
      (check-for-interruption)
      (XMLInputOutput/loadTransformer)
      (check-for-interruption)
      (Util/runRobot (into-array String inputs))))

(defn exception-message
  "Extract a properly formatted string from the stack trace."
  [ex]
  (stacktrace/pst-str ex))

(defn get-collection-to-update
  "Depending if the request of execution is for a periodical execution
we choose the collection to update."
  [is-periodical]
  (if is-periodical
    :periodical-executions
    :executions))

(defn build-on-error-fn
  "Builds a function that updates the corresponding collection when an
  error happens. If it's a periodical execution we ensure that it's no
  longer marked as scheduled and we delay the next execution to one
  hour later."
  [code is-periodical next-execution-ms]
  (fn [type message]
    (let [mark-as-not-scheduled (when is-periodical
                                  {:scheduled false})
          delay-next-execution (when is-periodical
                                 {:next-execution-ms
                                  (+ next-execution-ms 3600)})]
      (db-execution-error! (get-collection-to-update is-periodical) code
                           (merge {:error {:type type
                                           :message message}}
                                  mark-as-not-scheduled
                                  delay-next-execution)))))

(declare with-timeout-handling with-error-handling with-decrease-petitions-number)

;; First we check if the execution is for a periodical execution or a
;; normal execution. Depending on that the collection to update will
;; be different.
;;
;; We create a function `on-error` using `build-on-error-fn` that will
;; be called when an error happens. There will be two types of errors:
;; :error and :timeout. In any case the database is updated with the
;; produced error. The `on-exception` function is called when an
;; exception happens and it just delegates on `on-error`.
;;
;; Then we define the function that we're going to return. It
;; keeps track of the time the execution takes. This function returns
;; another function that represents what to do in the case the
;; execution has succeeded.
;;
;; We wrap this original function with some decorators:
;;
;; 1. `with-timeout-handling`: it checks that the execution doesn't
;; take more than the maximum allowed time.
;; 2. `with-error-handling`: it reports any produced error via the
;; `on-exception` function.
;; 3. `with-decrease-petitions-number`: Once the execution has
;; finished it decreases the number of petitions being handled.
;;
(defn callable-execution
  "It returns a tuple with a name for the execution and a function
that will do the execution. It needs besides the robotXML and the
inputs to be executed with, either a `result-code` or a
`periodical-code`. In the latter case a `next-execution-ms` value is
required too."
  [{:keys [inputs robotXML result-code periodical-code next-execution-ms]}]
  {:pre [(sequential? inputs) (string? robotXML)
         (or result-code (and periodical-code next-execution-ms))
         (not (and result-code periodical-code))]}
  (let [submit-time (System/currentTimeMillis)
        is-periodical periodical-code
        code (or result-code periodical-code)
        collection-to-update (get-collection-to-update is-periodical)
        name (str "["(when periodical-code "periodical") "execution " code "]")

        on-error (build-on-error-fn code is-periodical next-execution-ms)
        on-exception (fn [ex]
                       (log/error (str "Error executing " name) ex)
                       (on-error :error (exception-message ex)))]
    [name
     (->
      (fn []
        (let [start-execution-time (System/currentTimeMillis)
              result-array (execute-robot robotXML inputs)
              [all-time real-execution-time] (millis-elapsed-since
                                              submit-time start-execution-time)
              next-execution-ms (+ all-time (or next-execution-ms 0))]
          (fn []
            (db-execution-completed! collection-to-update
                                     code
                                     next-execution-ms
                                     :resultLines (seq result-array)
                                     :executionTimeMilliseconds all-time
                                     :realExecutionTime real-execution-time)
            (log/info (str "execution completed for: " name)))))
      (with-timeout-handling (partial on-error :timeout))
      (with-error-handling on-exception)
      (with-decrease-petitions-number))]))

;; An `ExecutorService` that will be defined when starting the
;; server. The number of threads used depends on the parameters.
(def automator-executor)

(defn accept-request
  "It receives a `request` and submits it to the `automator-executor`
so it's executed on the background. The `request` must conform to the
expected keys by `callable-execution`.  An `execution-wrapper` is used
to provide an environment on which MongoDB functions can be used."
  [execution-wrapper request]
  (let [[name execution] (callable-execution request)]
    (.submit automator-executor (execution-wrapper execution))
    (log/info (str name " accepted"))))

;; We now expose the execution decorators. They wrap the receiving
;; function adding extra functionality.

(def current-petitions (agent 0 :error-mode :continue))

(defn with-decrease-petitions-number
  "It wraps the receiving function. After the receiving function is
executed, it decreases the number of petitions being handled."
  [f]
  (fn [& args]
    (try
      (apply f args)
      (finally
       (send current-petitions dec)))))

(defn with-error-handling
  "It executes the function `f` and if an exception happens the
provided `on-error`function is called with the produced exception."
  [f on-error]
  (fn [& args]
    (try
      (apply f args)
      (catch Throwable e
        (on-error e)))))

(def ^{:dynamic true} *time-allowed-for-execution-ms* (* 1 60 1000))
;; For implementing this we launch a pipeline. It does a poll
;; operation on a newly created constant channel, named success. This
;; poll operation either returns the a tuple or a nil value if it has
;; timed out. In the next stage of the pipeline we check for that and
;; if it has timed out we interrupt the thread on which the execution
;; is being done.
;;
;; As soon as we finish executing the provided function we enqueue in
;; the success channel. This causes the pipeline to wake up and the
;; thread is not interrupted. Once done that we can call the
;; `on-success` function.
;;
;; If the execution operation took too much time and the .interrupt
;; call is reached an InterruptedException is thrown. In that case we
;; call the provided `on-timeout` function.
;;
;; Last we have to clean up in the finally clause. We must avoid
;; spurious interruptions of the thread. For example, if an exception
;; that is not an InterruptedException happens the success channel is
;; not enqueued and eventually the thread is interrupted. So we
;; enqueue it in the finally clause. But this might not be enough: a
;; race condition can happen. So we wait for the pipeline to finish
;; and clear the interrupted flag so the spurious exception is not
;; produced.
(defn with-timeout-handling
  "It handles timeouts. If a execution takes more than the configured
time in *time-allowed-for-execution-ms* the execution must be
interrupted. It receives:

1. a function `f` which must be return another function that
represents what to do in the case that `f` has been completed without
timing out.
2. a function `on-timeout` which must be called in the case that the
timeout has been produced. This function must receive a string
describing the problem."
  [f on-timeout]
  (fn [& args]
    (let [start (System/currentTimeMillis)
          success (constant-channel)
          timeout-ms *time-allowed-for-execution-ms*
          thread (Thread/currentThread)
          timeout-pipeline (run-pipeline nil
                             (fn [_]
                               (poll {:success success} timeout-ms))
                             read-channel
                             (fn [result]
                               (when-not result
                                 (.interrupt thread))))]
      (try
        (let [on-success (apply f args)]
          (enqueue success true)
          (on-success))
        (catch InterruptedException e
          (if (< (first (millis-elapsed-since start)) timeout-ms)
            (throw e)
            (on-timeout
             (str "The execution took more than the maximum allowed: "
                  timeout-ms " ms"))))
        (finally
         (enqueue success true)
         (try
           (wait-for-result timeout-pipeline)
           (catch InterruptedException e
             ;; it can happen if the thread has been interrupted:
             ;; .interrupt has already been reached.
             )
           (finally
            ;; now we are sure that the pipeline has finished, we
            ;; clear the possible interrupted flag.
            (Thread/interrupted))))))))

;; ### Receiving requests

(def ^{:doc "The lightweight request done to check if the worker is up."}
  query-alive-str "ping")

(defn read-request
  "It reads a request that is printed Clojure data. It's read with
`*read-eval*` to false to avoid malicious attacks."
  [request-str]
  (binding [*read-eval* false]
    (read-string request-str)))

(defmacro continue-on-error
  "If an error happens inside `body`, the error is reported but the
execution goes on."
  [& body]
  `(try
     ~@body
     (catch Throwable ~'e
       (log/error "Execution continues in spite of:" ~'e))))

(defn accept-request-and-respond
  "It registers an execution to be done for the given request, unless
the request is a `query-alive-str`. If returns to the client a
response showing that the request has been accepted an the number of
current petitions being handled. If an error happens the message of
the error is returned instead."

  [executor-receiver response raw-request]
  (try
    (let [request (read-request (.toString raw-request))]
      (when-not (= request query-alive-str)
        (executor-receiver request)
        (send current-petitions inc))
      (enqueue response (pr-str {:accepted true
                                 :current-petitions @current-petitions})))
    (catch Throwable e
      (log/error (str "Error processing: " raw-request) e)
      (enqueue response (pr-str {:accepted false
                                 :error (or
                                         (continue-on-error (stacktrace/pst-str e))
                                         (.getMessage e))})))))

;; Any function that could access the database must be executed in an
;; environment with a mongo connection defined.

(defn wrap-execution-with [mongo-connection f]
  (fn [& args]
    (mongo/with-mongo mongo-connection
      (apply f args))))

;; It will be used to wrap the execution that is submitted to
;; `automator-executor`.
(defn wrap-executions-with [mongo-connection]
  (partial wrap-execution-with mongo-connection))

;; Basically it says that for each request
;; `accept-request-and-respond` will be called and that
;; `accept-request` will be used for accepting the request. The
;; `accept-request` function will receive a function that will ensure
;; that a proper environment for accessing to the MongoDB is up.
(defn petition-handler
  "Defines what to do for the received requests."
  [mongo-connection channel connection-info]
  (c/pipelined-server channel
                      (partial
                       (var accept-request-and-respond)
                       (partial accept-request (wrap-executions-with mongo-connection)))))

;; We define a gloss codec. From TCP you receive a stream of
;; bytes. Gloss allows to extract discrete requests within this stream
;; of bytes. In this case the request is composed of a 32 bits integer
;; that tells the number of bytes that come after.
(defcodec protocol-frame
  (finite-frame :int32 (string :utf-8)))

(defn server
  "It starts the server on the given `port`. The executions performed
will work with the given `mongo-connection`."
  [mongo-connection port]
  (let [result (tcp/start-tcp-server (partial petition-handler mongo-connection)
                                     {:frame protocol-frame
                                      :port port})]
    (log/info (str "Worker listening on " port))
    result))

;; ### Starting the worker.


(defn- guess-external-local-ip
  "It tries to find an IP direction that is accessible from other
  hosts."
  []
  (if-let [candidate (->> (NetworkInterface/getNetworkInterfaces)
                          (enumeration-seq)
                          (remove #(.isLoopback %))
                          (mapcat #(enumeration-seq (.getInetAddresses %)))
                          (filter #(.isSiteLocalAddress %))
                          (first))]
    (.getHostAddress candidate)))

(defn- add-connection-info [server conn]
  (with-meta server (-> (meta server) (merge {:conn conn}))))

(defn- get-connection-info [server]
  (:conn (meta server)))

(defn other-not-daemon-threads
  "Get all threads that are not daemon and different to the current
thread."
  []
  (->> (Thread/getAllStackTraces)
       keys
       (filter #(not (.isDaemon %)))
       (remove (partial identical? (Thread/currentThread)))))

(defn exit-with-error-code
  "Waits for the non daemon threads to finish and exists with the
  provided exit code."  [exit-code]
  (doseq [t (other-not-daemon-threads)]
    (.join t))
  (System/exit (- exit-code 256)))

;; An unique identifier for this worker.
(def server-id (.toString (UUID/randomUUID)))

(defn remove-registered-workers
  "All the workers registered with the `server-id` of this server are
  removed."
  [server]
  (mongo/with-mongo (get-connection-info server)
    (mongo/destroy! :workers {:server-id server-id})))

(def registered-workers-removed (atom false))

(defn shutdown
  "It tries to do a clean shutdown:
 1. Stops listening to the port.
 2. Removes the registered workers on DB so they are no longer used.
 3. Closes the connection to the server.
 4. Shutdowns `automator-executor`."
  [server & {:keys [complete-exit] :or {complete-exit true}}]
  (continue-on-error
   (server))
  (continue-on-error
   (remove-registered-workers server)
   (swap! registered-workers-removed (constantly true)))
  (continue-on-error
   (mongo/close-connection (get-connection-info server)))
  (when complete-exit
    (continue-on-error
     (.shutdownNow automator-executor)
     (shutdown-agents))))

(defn run
  "It starts this worker based on the given parameters.

   * It creates the connection to the MongoDB that will be used from
     now on.
   * Starts a TCP server on the given port.
   * It registers this worker at the database so this worker can be
   discovered.
   * It also adds a shutdown hook so if the program is
   terminated via a kill signal instead of the shutdown method the
   registered workers are removed too."
  [dbhost db-port db port ip-to-register-on threads-number]
  (defonce automator-executor (Executors/newFixedThreadPool (or threads-number 20)))
  (let [conn (mongo/make-connection db :host dbhost :port db-port)
        tcp-server (server conn port)
        tcp-server (add-connection-info tcp-server conn)]
    (try
      (mongo/with-mongo conn
        (mongo/add-index! :workers [:server-id])
        (mongo/set-write-concern conn :strict)
        (if-let [ip (or ip-to-register-on (guess-external-local-ip))]
          (mongo/update! :workers
                         {:host ip :port port}
                         {:$set {:server-id server-id}})))
      (.addShutdownHook (Runtime/getRuntime)
                        (Thread. #(when-not @registered-workers-removed
                                    (remove-registered-workers tcp-server))))
      tcp-server
      (catch Throwable e
        (log/warn "Error connecting to mongo" e)
        (shutdown tcp-server)
        ;; EX_UNAVAILABLE
        (exit-with-error-code 69)))))

(defn parse-args
  "It defines the flags for the worker Command Line Interface."
  [args]
  (cli args
       ["--mongo-host" "The hostname on which mongoDB is" :default "127.0.0.1"]
       ["--mongo-port" "The port on which the mongoDB to be used is listening to"
        :default 27017 :parse-fn #(Integer. %)]
       ["--mongo-db" "The name of the database to use within the mongoDB instance"
        :default "test"]
       ["-p" "--port" "The port this worker will listen to for requests"
        :default 40100 :parse-fn #(Integer. %)]
       ["--threads-number" :default 20 :parse-fn #(Integer. %)]
       ["--ip-to-run-on" "The ip on which to register the worker" :default nil]
       ["-h" "--help" "Print this help" :flag true :default false]))

(defn -main
  "Main method"
  [& args]
  (let [[{:keys [mongo-host mongo-port mongo-db port threads-number ip-to-run-on
                 help]} _ help-banner] (parse-args args)]
    (cond
     help (println help-banner)
     :else
     (run mongo-host mongo-port mongo-db port ip-to-run-on threads-number))))

(defn local-setup
  "Used to launch a worker inside the same process."
  ([db port threads-number]
     (run "127.0.0.1" 27017 db port "127.0.0.1" threads-number))
  ([db port] (local-setup db port 10)))
