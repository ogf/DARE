(ns workers.server
  (:use gloss.core gloss.io lamina.core)
  (:require [aleph.tcp :as tcp]
            [lamina.connections :as c]
            [clojure.contrib.json :as json]
            [somnium.congomongo :as mongo]
            [somnium.congomongo.config]
            [clojure.contrib.logging :as log])
  (:import [java.util concurrent.Executors UUID]
           [java.net InetSocketAddress InetAddress NetworkInterface]
           [es.uvigo.ei.sing.dare.util XMLUtil]
           [es.uvigo.ei.sing.stringeditor Util XMLInputOutput])
  (:gen-class))


(defn adapt-updated-fields [collection fields]
  (case collection
    :periodical-executions {:lastExecution
                            (assoc fields :creationTime (System/currentTimeMillis))}
    fields))

(defn db-update-execution! [collection code & {:as updated-fields}]
  (mongo/update! collection
                 {:_id code}
                 {:$set (adapt-updated-fields collection updated-fields)}))

(defn millis-elapsed-since [& times]
  (let [now (System/currentTimeMillis)]
    (map #(- now %) times)))

(defn with-error-handling [f on-error]
  (fn [& args]
    (try
      (apply f args)
      (catch Throwable e
        (on-error e)))))

(defn callable-execution [{:keys [inputs robotXML result-code periodical-code]}]
  {:pre [(sequential? inputs) (string? robotXML)
         (or result-code periodical-code) (not (and result-code periodical-code))]}
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
                                              submit-time start-execution-time)]
          (db-update-execution! collection-to-update
                                code
                                :resultLines (seq result-array)
                                :executionTimeMilliseconds all-time
                                :realExecutionTime real-execution-time)
          (log/info (str "execution completed for: " name))))
      (with-error-handling on-exception))]))

(def automator-executor)

(defn accept-request [request]
  (let [[name execution] (callable-execution request)]
    (.submit automator-executor execution)
    (log/info (str name " accepted"))))

(def query-alive-str "ping")

(defn accept-request-and-respond [response raw-request]
  (try
    (let [request (json/read-json (.toString raw-request))]
      (when-not (= request query-alive-str)
        (accept-request request))
      (enqueue response "ACCEPTED"))
    (catch Throwable e
      (log/error (str "Error processing: " raw-request) e)
      (enqueue response "ERROR"))))

(defn petition-handler [channel connection-info]
  (c/pipelined-server channel (var accept-request-and-respond)))

(defcodec protocol-frame
  (finite-frame :int32 (string :utf-8)))

(defn server [port]
  (let [result (tcp/start-tcp-server petition-handler {:frame protocol-frame
                                                       :port port})]
    (log/info (str "Worker listening on " port))
    result))

(def server-id (.toString (UUID/randomUUID)))

(defn mongo-instance []
  somnium.congomongo.config/*mongo-config*)

(defn- guess-external-local-ip []
  (if-let [candidate (->> (NetworkInterface/getNetworkInterfaces)
                          (enumeration-seq)
                          (remove #(.isLoopback %))
                          (mapcat #(enumeration-seq (.getInetAddresses %)))
                          (filter #(.isSiteLocalAddress %))
                          (first))]
    (.getHostAddress candidate)))

(defn -main [dbhost db-port db port threads-number]
  (def automator-executor (Executors/newFixedThreadPool (or threads-number 20)))
  (let [result (server port)]
    (mongo/mongo! :db db :host dbhost :port db-port)
    (mongo/add-index! :workers [:server-id])
    (mongo/set-write-concern (mongo-instance) :strict)
    (if-let [ip (guess-external-local-ip)]
      (mongo/update! :workers
                     {:host ip :port port}
                     {:$set {:server-id server-id}}))
    result))

(defn local-setup
  ([db port threads-number]
     (-main "127.0.0.1" 27017 db port threads-number))
  ([db port] (local-setup db port 10)))

(defn shutdown [server]
  (server)
  (mongo/destroy! :workers {:server-id server-id})
  (mongo/close-connection (mongo-instance))
  (.shutdownNow automator-executor))
