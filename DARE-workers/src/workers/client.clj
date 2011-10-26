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
  (client (json/json-str request) timeout))

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

(defn- loop-while-healthy [client alive-ref spec polling-interval]
  (run-pipeline client
    :error-handler (fn [ex]
                     (try
                       (swap! alive-ref dissoc spec)
                       (log/warn (str "Couldn't connect to " spec) ex)
                       (c/close-connection client)
                       (finally
                       (complete nil))))
    (fn [_]
      (async-send query-alive-str polling-interval client))
    (fn [_]
      (swap! alive-ref assoc spec client)
      client)
     (wait-stage polling-interval)
     restart))

(defn- check-alive-connection [alive-ref polling-interval spec]
  (run-pipeline spec
    :error-handler (fn [ex]
                     (log/error (str "Unexpected error for " spec) ex)
                     (restart spec))
    (fn [[host port]]
      (client host port))
    (fn [client]
      (log/info (str  "Client for " spec " created"))
      client)
    (fn [client]
      (loop-while-healthy client alive-ref spec polling-interval))
    (fn [_]
      (restart spec))))

(defn- as-workers [workers-data]
  (apply hash-set workers-data))

(defn workers-handler! [& workers-specs]
  (let [specified-workers (atom (as-workers workers-specs))
        alive (atom {})
        check-alive (partial check-alive-connection alive *check-healthy-interval-ms*)
        check-alive-several #(doall (map check-alive %))
        _ (check-alive-several @specified-workers)]
    (log/info (str "created workers handler for: " workers-specs))
    {:specified-workers specified-workers
     :alive alive
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

(defn send-request! [workers-handler request]
  (let [alive (shuffle (or (vals @(:alive workers-handler)) []))]
    (when (empty? alive)
      (throw (RuntimeException. "No alive workers")))
    (if-let [result (some (partial attempt-send request 500) alive)]
      result
      (throw (RuntimeException. "No worker alive found!")))))
