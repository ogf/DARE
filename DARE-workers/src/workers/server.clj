(ns workers.server
  (:use gloss.core gloss.io lamina.core)
  (:require [aleph.tcp :as tcp]
            [lamina.connections :as c]
            [clojure.contrib.json :as json]
            [somnium.congomongo :as mongo]
            [clojure.contrib.logging :as log])
  (:import [java.util concurrent.Executors UUID]
           [java.net InetSocketAddress InetAddress NetworkInterface]
           [es.uvigo.ei.sing.dare.util XMLUtil]
           [es.uvigo.ei.sing.stringeditor Util XMLInputOutput])
  (:gen-class))


(defn adapt-updated-fields [collection next-execution-ms fields]
  (case collection
    :periodical-executions (assoc {:lastExecution
                                   (assoc fields :creationTime
                                          (System/currentTimeMillis))}
                             :next-execution-ms next-execution-ms
                             :scheduled false
                             :execution-sent-at nil)
    fields))

(defn db-update-execution! [collection code next-execution-ms & {:as updated-fields}]
  (mongo/update! collection
                 {:_id code}
                 {:$set (adapt-updated-fields
                         collection next-execution-ms updated-fields)}))

(defn millis-elapsed-since [& times]
  (let [now (System/currentTimeMillis)]
    (map #(- now %) times)))

(defn with-error-handling [f on-error]
  (fn [& args]
    (try
      (apply f args)
      (catch Throwable e
        (on-error e)))))

(defn callable-execution
  [{:keys [inputs robotXML result-code periodical-code next-execution-ms]}]
  {:pre [(sequential? inputs) (string? robotXML)
         (or result-code (and periodical-code next-execution-ms))
         (not (and result-code periodical-code))]}
  (let [submit-time (System/currentTimeMillis)
        is-periodical periodical-code
        code (or result-code periodical-code)
        collection-to-update (if is-periodical
                               :periodical-executions
                               :executions)
        name (str "["(when periodical-code "periodical") "execution " code "]")
        on-exception (fn [ex] (log/error  (str "Error executing " name) ex))]
    [name
     (->
      (fn []
        (let [start-execution-time (System/currentTimeMillis)
              result-array (-> (XMLUtil/toDocument robotXML)
                               (XMLInputOutput/loadTransformer)
                               (Util/runRobot (into-array String inputs)))
              [all-time real-execution-time] (millis-elapsed-since
                                              submit-time start-execution-time)
              next-execution-ms (+ all-time (or next-execution-ms 0))]
          (db-update-execution! collection-to-update
                                code
                                next-execution-ms
                                :resultLines (seq result-array)
                                :executionTimeMilliseconds all-time
                                :realExecutionTime real-execution-time)
          (log/info (str "execution completed for: " name))))
      (with-error-handling on-exception))]))

(def automator-executor)

(defn accept-request [execution-wrapper request]
  (let [[name execution] (callable-execution request)]
    (.submit automator-executor (execution-wrapper execution))
    (log/info (str name " accepted"))))

(def query-alive-str "ping")

(defn accept-request-and-respond [executor response raw-request]
  (try
    (let [request (json/read-json (.toString raw-request))]
      (when-not (= request query-alive-str)
        (executor request))
      (enqueue response "ACCEPTED"))
    (catch Throwable e
      (log/error (str "Error processing: " raw-request) e)
      (enqueue response "ERROR"))))

(defn wrap-execution-with [mongo-connection f]
  (fn [& args]
    (mongo/with-mongo mongo-connection
      (apply f args))))

(defn wrap-executions-with [mongo-connection]
  (partial wrap-execution-with mongo-connection))

(defn petition-handler [mongo-connection channel connection-info]
  (c/pipelined-server channel
                      (partial
                       (var accept-request-and-respond)
                       (partial accept-request (wrap-executions-with mongo-connection)))))

(defcodec protocol-frame
  (finite-frame :int32 (string :utf-8)))

(defn server [mongo-connection port]
  (let [result (tcp/start-tcp-server (partial petition-handler mongo-connection)
                                     {:frame protocol-frame
                                      :port port})]
    (log/info (str "Worker listening on " port))
    result))

(def server-id (.toString (UUID/randomUUID)))

(defn- guess-external-local-ip []
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

(defn -main [dbhost db-port db port threads-number]
  (def automator-executor (Executors/newFixedThreadPool (or threads-number 20)))
  (let [conn (mongo/make-connection db :host dbhost :port db-port)
        result (server conn port)]
    (mongo/with-mongo conn
      (mongo/add-index! :workers [:server-id])
      (mongo/set-write-concern conn :strict)
      (if-let [ip (guess-external-local-ip)]
        (mongo/update! :workers
                       {:host ip :port port}
                       {:$set {:server-id server-id}})))
    (add-connection-info result conn)))

(defn local-setup
  ([db port threads-number]
     (-main "127.0.0.1" 27017 db port threads-number))
  ([db port] (local-setup db port 10)))

(defn shutdown [server]
  (server)
  (let [connection (get-connection-info server)]
    (mongo/with-mongo connection
      (mongo/destroy! :workers {:server-id server-id}))
    (mongo/close-connection connection))
  (.shutdownNow automator-executor))
