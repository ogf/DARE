(ns DARE-web.web
  (:use clojure.tools.cli)
  (:import org.eclipse.jetty.util.resource.Resource
           org.eclipse.jetty.xml.XmlConfiguration
           org.eclipse.jetty.server.Server
           org.eclipse.jetty.webapp.WebAppContext
           org.eclipse.jetty.plus.jndi.EnvEntry
           (java.net ServerSocket Socket InetAddress)
           (java.io InputStreamReader BufferedReader
                    OutputStreamWriter BufferedWriter))
  (:gen-class))


(defn unkeywordize [keyword]
  (->> keyword
       str
       (re-matches #"(:)?(.*)")
       rest
       second))

(defn add-env-to-webapp [^WebAppContext context env-options]
  (doseq [[key value] env-options]
    (EnvEntry. context (unkeywordize key) value true)))

(defn is-production-option [[key value]]
  (let [as-string (unkeywordize key)]
    (or (.startsWith as-string "mongo-")
        (and (.startsWith as-string "max-queue") value))))

(defn get-production-options [options]
  (->> options
       (filter is-production-option)
       (into {})
       (merge {:backend-type "production"})))

(def stop-port 60000)

(defn wait-for-stop [^Server server]
  (let [server-socket (ServerSocket. stop-port)]
    (future
      (with-open [server-socket server-socket
                  socket (.accept server-socket)
                  input (-> socket
                            .getInputStream
                            InputStreamReader.
                            BufferedReader.)]
        (.readLine input))
      (.stop server)
      (System/exit 0))))

(defn notify-stop []
  (with-open [socket (Socket. (InetAddress/getLocalHost) stop-port)
              output (-> socket
                         .getOutputStream
                         OutputStreamWriter.
                         BufferedWriter.)]
    (.newLine output)
    (.flush output)))

(defn attempt [f]
  (fn [& args]
    (try
      (apply f args)
      (catch Throwable e))))

(def try-notify-stop (attempt notify-stop))

(defn ^Server create-server [^Resource server-configuration port]
  (let [configuration (-> server-configuration .getInputStream XmlConfiguration.)]
    (.put (.getProperties configuration) "jetty.port" port)
    (.configure configuration)))

(defn parse-args [args]
  (cli args
       ["--stop" "stop the jetty server. Only if it has been launch in stub mode"
        :flag true :default false]
       ["-w" "--war" "The path to the war to be used"]
       ["-p" "--port" "The port this web server will listen to"
        :default "8080"]
       ["--config" "The custom configuration to use" :default "jetty.xml"]
       ["--stub" "Run in stub mode. It's for running tests" :flag true
        :default false]
       ["--mongo-host" "The host on which the mongoDB to be used is listening on"
        :default "127.0.0.1"]
       ["--mongo-port" "The port on which the mongoDB to be used is listening to"
        :default 27017 :parse-fn #(Integer. %)]
       ["--mongo-db" "The name of the database to use within the mongoDB instance"
        :default "test"]
       ["--max-queue-minilanguage-parsing" "Not required. The number of robots
       creation requests than can be waiting for being parsed"]
       ["-h" "--help" "Print this help" :flag true :default false]))

(defn -main [& args]
  (let [[{:keys [war port config stub stop help] :as options} _ help-banner] (parse-args args)
        production-options (if stub
                             {}
                             (get-production-options options))
        wait-for-stop (if stub
                        wait-for-stop
                        (constantly nil))]
    (cond
     help (println help-banner)
     stop (try-notify-stop)
     (not war) (do
                 (println "--war is required")
                 (println help-banner)
                 (System/exit (- 64 256))) ;; EX_USAGE
     :else (do (try-notify-stop)
               (doto (create-server (Resource/newSystemResource config) port)
                 wait-for-stop
                 (.setHandler
                  (doto (WebAppContext.)
                    (.setContextPath "/")
                    (.setWar war)
                    (add-env-to-webapp production-options)))
                 .start
                 .join)))))
