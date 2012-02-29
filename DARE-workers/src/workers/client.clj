(ns workers.client
  (:use gloss.core gloss.io lamina.core [workers.server :exclude [shutdown]])
  (:require [clojure.set :as set]
            [aleph.tcp :as tcp]
            [lamina.connections :as c]
            [clojure.contrib.json :as json]
            [clojure.contrib.logging :as log]))


(defn- wrap [f & {:keys [on-success on-error] :or {on-success identity}}]
  {:pre [((complement nil?) on-error)]}
  (fn [& args]
    (try
      (on-success (apply f args))
      (catch Throwable e
        (on-error e)))))

(defn- async-send [request timeout client]
  (run-pipeline (client (json/json-str request) timeout)
    (fn [response] (read-string response))))

(defn send-and-wait [request timeout client]
  (wait-for-result (async-send request timeout client) timeout))

(def check-alive-fn (partial
                     (wrap send-and-wait
                                   :on-success (constantly :successes)
                                   :on-error (constantly :errors))
                     query-alive-str
                     200))

(defn- client [host port]
  (c/client
   #(tcp/tcp-client {:host host
                     :port port
                     :frame protocol-frame})))

(def ^{:dynamic true} *check-healthy-interval-ms* 4000)

(defn- loop-while-healthy [client alive-ref spec update-current-petitions!
                           polling-interval]
  (run-pipeline client
    :error-handler (fn [ex]
                     (swap! alive-ref dissoc spec)
                     (log/warn (str "Couldn't connect to " spec) ex))
    (fn [_]
      (async-send query-alive-str polling-interval client))
    (fn [{:keys [accepted current-petitions] :as result}]
      (when-not (contains? @alive-ref spec)
        (swap! alive-ref assoc spec client)
        (update-current-petitions! spec current-petitions)
        (log/info (str "[Re]Connected to " spec))))
     (wait-stage polling-interval)
     restart))

(defn- check-alive-connection [alive-ref update-current-petitions! polling-interval [host port :as spec]]
  (let [client (client host port)]
    (run-pipeline 0
      :error-handler (fn [ex]
                       (restart polling-interval))
      (fn [wait-interval]
        ((wait-stage wait-interval) nil))
      (fn [_]
        (loop-while-healthy client alive-ref spec update-current-petitions! polling-interval))
      restart)))

(defn- as-workers [workers-data]
  (apply hash-set workers-data))

(defn workers-handler! [& workers-specs]
  (let [specified-workers (atom (as-workers workers-specs))
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
        get-healthy (fn [] (sort-by #(get load-by-worker (first %)
                                         high-load-if-unknown)
                                   @alive))
        _ (check-alive-several @specified-workers)]
    (log/info (str "created workers handler for: " workers-specs))
    {:specified-workers specified-workers
     :alive alive
     :update-current-petitions! update-current-petitions!
     :get-healthy get-healthy
     :check-alive check-alive-several}))

(defn count-alive-workers [workers-handler]
  (let [{:keys [alive]} workers-handler]
    (count @alive)))

(defn add-new-workers! [workers-handler & new-workers-specs]
  (let [{:keys [specified-workers check-alive]} workers-handler
        new-workers (as-workers new-workers-specs)
        truly-added (set/difference new-workers @specified-workers)]
    (swap! specified-workers set/union new-workers)
    (when (seq truly-added)
      (log/info (str "adding new workers " truly-added))
      (check-alive truly-added))))

(defn- close-connections! [connections]
  (doseq [conn connections] (c/close-connection conn)))

(defn shutdown! [workers-handler]
  (close-connections! (vals @(:alive workers-handler)))
  (log/info "workers handler has been shutdown"))

(def attempt-send (wrap send-and-wait
                        :on-error (constantly nil)))

(defn send-request! [{:keys [get-healthy update-current-petitions!]} request]
  (let [healthy (get-healthy)
        is-accepted? (fn [[spec client]]
                       (when-let [result (attempt-send request 500 client)]
                         [spec result]))]
    (when (empty? healthy)
      (throw (RuntimeException. "No healthy workers")))
    (if-let [[worker-spec {:keys [accepted error current-petitions] :as result}]
             (some is-accepted? healthy)]
      (do
        (when-not accepted
          (throw (RuntimeException. (str "Request " request
                                         " not accepted: " error))))
        (update-current-petitions! worker-spec current-petitions)
        result)
      (throw (RuntimeException. "No healthy worker found!")))))
