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

(defn- send-and-wait [request timeout {client :connection}]
  (wait-for-result (client (json/json-str request) timeout) timeout))

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

(defn- connect-to! [workers-specs]
  (log/info (str "Creating connections for: " workers-specs))
  (letfn [(connect [[host port :as spec]]
                   {:spec spec
                    :connection (client host port)})]
    (doall (map connect workers-specs))))

(defn- close-connections! [connections]
  (doseq [conn connections] (c/close-connection conn)))

(def ^{:dynamic true} *check-healthy-interval-ms* 4000)

(defn- check-currently-alive [alive specified-workers]
  (let [not-alive (connect-to!
                   (set/difference specified-workers (set (map :spec alive))))]
    (group-by check-alive-fn (concat alive not-alive))))

(defn- update-currently-alive [specified-workers-ref alive-ref]
  (let [as-string #(into [] (map :spec %))
        {:keys [successes errors]}
            (check-currently-alive @alive-ref @specified-workers-ref)]
        (reset! alive-ref (into [] successes))
        (close-connections! (map :connection errors))
        (log/info (str "healthy: " (as-string successes)
                       " errors: " (as-string errors)))))

(def end-check-healthy ::end)

(def is-end-check-healthy? (partial = end-check-healthy))

(defn- consume-all [channel]
  (channel-seq channel))

(defn- wait-for-next-iteration [new-workers-channel polling-interval]
  (if (some is-end-check-healthy? (consume-all new-workers-channel))
      end-check-healthy
      (let [[_ message] (-> {:new new-workers-channel}
                            (poll polling-interval)
                            (wait-for-message)
                            (or [nil (str polling-interval " ms elapsed")]))]
        message)))

(defn- check-healthy [wait-for-next-iteration-fn update-currently-alive-fn]
  (let [report-errors (fn [fn message]
                        (wrap fn :on-error #(do (log/error message %)
                                                "After error")))
        wait-for-next-iteration-fn (report-errors wait-for-next-iteration-fn
                                                  "Error waiting")
        update-currently-alive-fn (report-errors update-currently-alive-fn
                                                 "Error updating")]
    (loop [message "Starting"]
      (log/info (str message ": checking healthy status of connections"))
      (update-currently-alive-fn)
      (let [next (wait-for-next-iteration-fn)]
        (when-not (is-end-check-healthy? next)
          (recur next))))))

(defn- as-workers [workers-data]
  (apply hash-set workers-data))

(defn workers-handler! [& workers-specs]
  (let [specified-workers (atom (as-workers workers-specs))
        alive (atom [])
        new-workers-channel (channel)
        wait-for-next (partial wait-for-next-iteration
                      new-workers-channel *check-healthy-interval-ms*)
        update-alive (partial update-currently-alive specified-workers alive)
        checker (future (check-healthy wait-for-next update-alive))]
    (log/info (str "created workers handler for: " workers-specs))
    {:specified-workers specified-workers
     :alive alive
     :checker checker
     :new-workers-channel new-workers-channel}))

(defn count-alive-workers [workers-handler]
  (let [{:keys [alive]} workers-handler]
    (count @alive)))

(defn add-new-workers! [workers-handler & new-workers-specs]
  (let [{:keys [specified-workers new-workers-channel]} workers-handler
        new-workers (as-workers new-workers-specs)
        truly-added (set/difference new-workers @specified-workers)]
    (swap! specified-workers set/union new-workers)
    (when (seq truly-added)
      (log/info (str "adding new workers " truly-added))
      (enqueue new-workers-channel "New workers added"))))

(defn shutdown! [workers-handler]
  (let [channel (:new-workers-channel workers-handler)]
    (enqueue-and-close channel end-check-healthy))
  (future-cancel (:checker workers-handler))
  (close-connections! @(:alive workers-handler))
  (log/info "workers handler has been shutdown"))

(def attempt-send (wrap send-and-wait
                        :on-error (constantly nil)))

(defn send-request! [workers-handler request]
  (let [alive (shuffle @(:alive workers-handler))]
    (when (empty? alive)
      (throw (RuntimeException. "No alive workers")))
    (if-let [result (some (partial attempt-send request 500) alive)]
      result
      (throw (RuntimeException. "No worker alive found!")))))
