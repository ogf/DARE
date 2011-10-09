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

(defn- send-and-wait [request {client :connection}]
  (wait-for-result (client (json/json-str request) 1000) 1000))

(def check-alive-fn (partial
                     (wrap send-and-wait
                                   :on-success (constantly :successes)
                                   :on-error (constantly :errors))
                     query-alive-str))

(defn- client [host port]
  (c/client
   #(tcp/tcp-client {:host host
                     :port port
                     :frame protocol-frame})))

(defn- connect-to! [workers-specs]
  (letfn [(connect [[host port :as spec]]
                   {:spec spec
                    :connection (client host port)})]
    (doall (map connect workers-specs))))

(defn- close-connections! [connections]
  (doseq [conn connections] (c/close-connection conn)))

(defn- check-healthy [specified-workers-ref alive-ref]
  (while true
    (try
      (let [alive @alive-ref
            not-alive (connect-to!
                       (set/difference @specified-workers-ref (set (map :spec alive))))
            results-map (group-by check-alive-fn (concat alive not-alive))]
        (log/info (str "healthy status: " results-map))
        (reset! alive-ref (:successes results-map))
        (close-connections! (map :connection (:errors results-map))))
      (catch Throwable e
        (log/error "error in check-healthy" e)))
    (Thread/sleep 4000)))


(defn- as-workers [workers-data]
  (apply hash-set workers-data))

(defn workers-handler! [& [host port :as workers-specs]]
  (let [specified-workers (atom (as-workers workers-specs))
        alive (atom [])
        checker (future (check-healthy specified-workers alive))]
    (log/info (str "created workers handler for: " workers-specs))
    {:specified-workers specified-workers
     :alive alive
     :checker checker}))

(defn add-new-workers! [workers-handler & [host port :as new-workers-specs]]
  (let [{:keys [specified-workers]} workers-handler]
    (log/info (str "adding new workers " new-workers-specs))
    (swap! specified-workers set/union (as-workers new-workers-specs))))

(defn shutdown! [workers-handler]
  (future-cancel (:checker workers-handler))
  (close-connections! @(:alive workers-handler))
  (log/info "workers handler has been shutdown"))

(def attempt-send (wrap send-and-wait
                        :on-error (constantly nil)))

(defn send-request [workers-handler request]
  (let [alive (shuffle (seq @(:alive workers-handler)))]
    (when (empty? alive)
      (throw (RuntimeException. "No alive workers")))
    (if-let [result (some (partial attempt-send request) alive)]
      result
      (throw (RuntimeException. "No worker alive found!")))))
