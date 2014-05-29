;; Client part of DARE-workers. It distributes the execution requests
;; among the healthy workers.
(ns workers.client
  (:use gloss.core gloss.io lamina.core [workers.server :exclude [shutdown]])
  (:require [clojure.set :as set]
            [aleph.tcp :as tcp]
            [lamina.connections :as c]
            [clojure.contrib.logging :as log]))


;; ### Sending functions

;; Functions for sending requests and receiving their responses.

(defn- async-send
  "It sends the given request using the given client. If sending and receiving a response takes more than `timeout` ms it sends an exception.

The response is a Clojure data structure inside a lamina's
`result-channel`."
  [request timeout client]
  (run-pipeline (client (pr-str request) timeout)
    (fn [response] (read-string response))))

(defn send-and-wait
  "It has the same behavior as `async-send` but the caller is blocked
until the response is received or times out."
  [request timeout client]
  (wait-for-result (async-send request timeout client) timeout))

(defn- client
  "It creates a new Aleph tcp-client."
  [host port]
  (c/client
   #(tcp/tcp-client {:host host
                     :port port
                     :frame protocol-frame})))
;; ### Check health

;; We have to check the status of the registered workers
;; periodically. So if there is a partition in the system or some of
;; the workers go down, requests are no longer sent to them. Once they
;; are reachable again they're considered healthy.

;; Each four seconds we check if we can connect to the registered
;; workers.
(def ^{:dynamic true} *check-healthy-interval-ms* 4000)

(defn- wrap
  "It wraps the execution of the function `f`. If an error happens the
`on-error` parameter is called with the exception. If not, the value
of the on-success function is used."
  [f & {:keys [on-success on-error] :or {on-success identity}}]
  {:pre [((complement nil?) on-error)]}
  (fn [& args]
    (try
      (on-success (apply f args))
      (catch Throwable e
        (on-error e)))))


(defn- loop-while-healthy
  "It checks periodically that the given client can reach its
associated worker. It uses a Lamina pipeline so each stage is executed
when the data of the previous stage is available, i.e. no thread is
blocked.

`alive-atom` is a updated according to the reachability status of the
worker.

`spec` is a tuple composed of the host and the port on which the
worker is listening. The `alive-atom` is keyed by this kind of
object.

`udpate-current-petitions!` is called when receiving the ping response
of a worker. The response contains the number of the petitions that is
handling right now. This information can be used to send the petitions
to the least used worker."

  [client alive-atom spec update-current-petitions! polling-interval]

  (run-pipeline client
    :error-handler (fn [ex]
                     (swap! alive-atom dissoc spec)
                     (log/warn (str "Couldn't connect to " spec) ex))
    (fn [_]
      (async-send query-alive-str polling-interval client))
    (fn [{:keys [accepted current-petitions] :as result}]
      (when-not (contains? @alive-atom spec)
        (swap! alive-atom assoc spec client)
        (update-current-petitions! spec current-petitions)
        (log/info (str "[Re]Connected to " spec))))
     (wait-stage polling-interval)
     restart))

(defn- check-alive-connection
  "It delegates to `loop-while-healthy`. If it can't connect there,
the error pops out to the error-handler defined here and waits some
time before trying again.

Otherwise given a not reachable worker it would be trying to connect
continuously."
  [alive-atom update-current-petitions! polling-interval [host port :as spec]]
  (let [client (client host port)]
    (run-pipeline 0
      :error-handler (fn [ex]
                       (restart polling-interval))
      (fn [wait-interval]
        ((wait-stage wait-interval) nil))
      (fn [_]
        (loop-while-healthy client alive-atom
                            spec update-current-petitions!
                            polling-interval))
      restart)))

;; ### Worker handler creation

;; Now we define the functions needed to create a new workers-handler
;; to be used by DARE-backend. The details of the handler are not
;; intended to be used from outside of this namespace.

(defn- as-set [workers-data]
  (apply hash-set workers-data))

;; Internally some local variables intended to not be accessible from
;;outside this namespace are defined:
;;
;; 1. specified-workers: An atom that contains a set with the workers
;; specified.
;; 2. alive: It's an atom that contains a map from the specs of the
;; workers that are considered healthy to their client objects.
;; 3. load-by-worker: It's an agent that keeps a map from worker specs
;; to the number of petitions they are handling.
;; 4. update-current-petitions!: It's a function that will be called
;; when receiving a response from a worker. A worker response contains
;; a current-petitions field with the number of executions that it's
;; handling at the moment.
;; 5. check-alive-several: A function that is called whenever new
;; workers specs are added. It calls `check-alive-connection`, so each
;; added worker status is periodically checked.
;; 6. get-healthy: A function that returns only the workers that are
;; reachable with the least loaded first.
(defn workers-handler!
  "It creates a new workers handler from the given workers specs. A
worker spec is a tuple of the host and the port on which a worker is
listening to.

The returned value can be used with `add-new-workers!`,
`count-workers`, `send-request!` and `shutdown!`."

  [& workers-specs]
  (let [specified-workers (atom (as-set workers-specs))
        alive (atom {})
        load-by-worker (agent {} :mode :continue)
        update-current-petitions! (fn [worker-spec current-petitions]
                                    (log/info
                                     (str "current petitions being handled for "
                                          worker-spec
                                          " are " current-petitions))
                                    (send load-by-worker
                                          update-in [worker-spec]
                                          (constantly current-petitions)))
        check-alive (partial check-alive-connection alive update-current-petitions!
                             *check-healthy-interval-ms*)
        check-alive-several #(doall (map check-alive %))
        high-load-if-unknown 30
        get-healthy (fn []
                      (let [load-by-worker @load-by-worker]
                        (sort-by #(get load-by-worker (first %)
                                       high-load-if-unknown)
                                 @alive)))
        _ (check-alive-several @specified-workers)]
    (log/info (str "created workers handler for: " workers-specs))
    {:specified-workers specified-workers
     :alive alive
     :update-current-petitions! update-current-petitions!
     :get-healthy get-healthy
     :check-alive check-alive-several}))

(defn count-alive-workers
  "It counts the number of workers that are considered alive and ready
to receive requests."
  [workers-handler]
  (let [{:keys [alive]} workers-handler]
    (count @alive)))

(defn add-new-workers!
  "Adds new workers. a new worker spec must be of the form [`host` `port`]."
  [workers-handler & new-workers-specs]
  (let [{:keys [specified-workers check-alive]} workers-handler
        new-workers (as-set new-workers-specs)
        truly-added (set/difference new-workers @specified-workers)]
    (swap! specified-workers set/union new-workers)
    (when (seq truly-added)
      (log/info (str "adding new workers " truly-added))
      (check-alive truly-added))))

(def attempt-send (wrap send-and-wait
                        :on-error (constantly nil)))

(defn send-request!
  "It sends a request to the least busy worker. If no worker is
available an exception is thrown."
  [{:keys [get-healthy update-current-petitions!]} request]
  (let [healthy (get-healthy)
        is-accepted!? (fn [[spec client]]
                       (when-let [result (attempt-send request 500 client)]
                         [spec result]))]
    (when (empty? healthy)
      (throw (RuntimeException. "No healthy workers")))
    (if-let [[worker-spec {:keys [accepted error current-petitions] :as result}]
             (some is-accepted!? healthy)]
      (do
        (when-not accepted
          (throw (RuntimeException. (str "Request " request
                                         " not accepted: " error))))
        (update-current-petitions! worker-spec current-petitions)
        result)
      (throw (RuntimeException. "No healthy worker found!")))))

;; ### Shutdown

(defn- close-connections!
  "It closes the connections to the workers."
  [connections]
  (doseq [conn connections] (c/close-connection conn)))

(defn shutdown!
  "It frees resources associated to the provided `workers-handler`. It
cannot be used any longer after this."
  [workers-handler]
  (close-connections! (vals @(:alive workers-handler)))
  (log/info "workers handler has been shutdown"))
