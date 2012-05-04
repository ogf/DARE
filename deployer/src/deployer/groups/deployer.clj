(ns deployer.groups.deployer
  "Node defintions for deployer"
  (:require pallet.utils [clojure.set :as set])
  (:use
   [pallet.core :only [group-spec server-spec node-spec lift converge]]
   [pallet.configure :only [compute-service pallet-config]]
   [pallet.crate.automated-admin-user :only [automated-admin-user]]
   [pallet.phase :only [phase-fn schedule-in-pre-phase schedule-in-post-phase]]
   [pallet.session :only [target-ip target-node]]
   [pallet.node :only [private-ip]]
   [pallet.action :only [def-clj-action def-bash-action def-aggregated-action]]
   [pallet.action.package :only [package package-source package-manager]]
   [pallet.action.user :only [user group]]
   [pallet.action.directory :only [directory adjust-directory]]
   [pallet.stevedore :only [script with-script-language checked-script]]
   [pallet.action.rsync :only [rsync-directory rsync]]
   [pallet.action.exec-script :only [exec-checked-script exec-script]]
   [pallet.execute :only [sh-script]]
   [pallet.action.remote-file :only [remote-file]]
   [pallet.action.remote-directory :only [remote-directory]]
   [pallet.action.file :only [symbolic-link file]]
   [pallet.crate.java :only [java]])
  (:import java.util.UUID))

(def medium-node
  (node-spec
   :image {:image-id "eu-west-1/ami-d5c5fda1"} ;; ubuntu 11.10 32 bits
   :location {:location-id "eu-west-1"}
   :hardware {:hardware-id "m1.small"}
   :network {:inbound-ports `[22 80 27017 ~@(range 5000 5200)]}))

(def micro-node
  (node-spec
   :image {:image-id "eu-west-1/ami-d5c5fda1"} ;; ubuntu 11.10 32 bits
   :location {:location-id "eu-west-1"}
   :hardware {:hardware-id "t1.micro"}
   :network {:inbound-ports `[22 80]}))


(def service (compute-service :dare))

(def
  ^{:doc "Defines the spec common for all"}
  base-server
  (server-spec
   :phases
   {:bootstrap automated-admin-user}))

(def path-to-mongo-host [:parameters :mongo-host])

(defn get-target-private-ip [session]
  (-> session target-node private-ip))

(defn register-mongo-host [session]
  (let [ip (get-target-private-ip session)]
    (update-in session path-to-mongo-host (constantly ip))))

(defn get-mongo-host [session default-value]
  (get-in session path-to-mongo-host default-value))

(def mongo-repository "http://downloads-distro.mongodb.org/repo/debian-sysvinit")

(defn install-mongo [session]
  (-> session
      (package-source "10gen" :aptitude {:url mongo-repository
                                         :release ""
                                         :scopes ["dist" "10gen"]
                                         :key-url "http://keyserver.ubuntu.com:11371/pks/lookup?op=get&search=0x9ECBEC467F0CEB10"})
      (package-manager :update)
      (package "mongodb-10gen")))

(def
  ^{:doc "Defines the spec for the mongodb"}
  mongo-spec
  (server-spec
   :phases {:configure (phase-fn
                        install-mongo
                        (schedule-in-pre-phase
                         register-mongo-host))
            :register register-mongo-host}))

(defn create-dare-user [session]
  (-> session
      (group "dare" :system true)
      (user "dare" :system true :create-home false :shell :bash :group "dare")))

(defn deploy-dare-contents
  ([session]
     (deploy-dare-contents session (:dare-dist-location (pallet-config))))
  ([session dare-location]
     (-> session
         (directory "/opt/dare" :action :touch
                    :owner "oscar" :group "oscar"
                    :recursive true)
         (rsync dare-location "/opt/dare/" {})
         (directory "/opt/dare" :action :touch
                             :owner "dare" :group "dare"
                             :recursive true :mode "0660")
         (directory "/opt/dare" :action :touch
                             :owner "dare" :group "dare"
                             :recursive false :mode "0770"))))

(defn generate-upstart-files [session base-port concurrency]
  (let [location (:dare-dist-location (pallet-config))
        random (-> (UUID/randomUUID) (.toString) (.substring 0 6))
        files-dir (str location (str "../upstart/export-" random))
        mongo-host (get-mongo-host session "127.0.0.1")
        command (format "cd %s;./export.sh %s %d '%s' '%s'" location
                        files-dir
                        base-port
                        concurrency
                        mongo-host)
        files-tar (str files-dir ".tar.gz")]
    (sh-script command)
    (-> session
        (remote-directory "/etc/init" :local-file files-tar
                          :strip-components 0
                          :owner "root" :group "root"))))

(def
  ^{:doc "Defines the spec for the mongodb"}
  with-dare-dist
  (server-spec
   :phases {:configure (phase-fn (java :openjdk)
                                 (create-dare-user)
                                 (deploy-dare-contents))}))

(def path-to-proxied [:parameters :nginx-proxied])

(defn register-nodes [session ports]
  (let [ip (get-target-private-ip session)
        servers (map str (repeat ip) (repeat ":") ports)]
    (update-in session path-to-proxied (fnil set/union #{}) (set servers))))

(defn get-servers-to-proxy [session]
  (get-in session path-to-proxied #{}))

(defn dare [& {:keys [web worker] :or {:web 1 :worker 1}}]
  (let [base-port 5000
        concurrency (str "'web=" web ",worker=" worker "'")
        web-ports (take web (iterate inc base-port))]
    (server-spec
     :extends [with-dare-dist]
     :phases {:configure (phase-fn
                          (generate-upstart-files base-port concurrency)
                          (register-nodes web-ports))
              :register (phase-fn
                         (register-nodes web-ports))
              :start (phase-fn
                      (exec-script
                       (sudo start dare || echo "already running")))
              :stop (phase-fn
                     (exec-script
                       (sudo stop dare || echo "not running")))})))

(def nginx-template "deployer/nginx-dare.conf")

(defn configure-nginx [session server-name]
  (-> session
      (package "nginx")
      (remote-file "/etc/nginx/sites-available/dare"
                   :template nginx-template
                   :owner "root" :group "root"
                   :values {:server-name server-name
                            :servers (get-servers-to-proxy session)})
      (symbolic-link  "/etc/nginx/sites-available/dare"
                      "/etc/nginx/sites-enabled/dare")
      (file "/etc/nginx/sites-enabled/default" :action :delete)))

(defn nginx-spec
  ([server-name]
     (server-spec
      :phases {:configure (phase-fn (schedule-in-post-phase
                                     (configure-nginx server-name)))
               :register (phase-fn (schedule-in-post-phase
                                    (configure-nginx server-name))
                                   (schedule-in-post-phase
                                    (exec-script
                                     (sudo service nginx reload))))
               :start (phase-fn
                       (schedule-in-post-phase
                        (exec-script
                         (sudo start nginx)))
                       (schedule-in-post-phase
                         (exec-script
                          (sudo service nginx reload))))}))
  ([]
     (nginx-spec "\"\"")))

(defn only-webs [n-web]
  (dare :web n-web :worker 0))

(defn only-workers [n-worker]
  (dare :web 0 :worker n-worker))

(def entry-group (group-spec "entry"
                             :extends [base-server mongo-spec
                                       (dare :web 1 :worker 3) (nginx-spec)]
                             :node-spec medium-node))

(def bench-group (group-spec "benchmark" :extends [base-server]
                              :node-spec medium-node))

(def mixed-group (group-spec "mixed" :extends [base-server (dare :web 2 :worker 8)]
                              :node-spec medium-node))

(def worker-group (group-spec "worker" :extends [base-server (only-workers 8)]
                              :node-spec medium-node))

(def web-group (group-spec "web" :extends [base-server (only-webs 2)]
                             :node-spec medium-node))
