(defproject DARE-workers "0.1-SNAPSHOT"
  :description "Responsible of receiving executions requests and executing them"
  :dependencies [[org.clojure/clojure "1.2.1"]
                 [org.clojure/clojure-contrib "1.2.0"]
                 [aleph "[0.2.0-alpha,0.2.0)"]
                 [es.uvigo.ei.sing/minilanguage "0.1"]
                 [es.uvigo.ei.sing/DARE-util "0.1"]
                 [es.uvigo.ei.sing/stringeditor "1.0"]
                 [congomongo "0.1.7-SNAPSHOT"]
                 [org.clojure/tools.cli "0.2.1"]
                 [clj-stacktrace "0.2.3"]]
  :repositories {"codehaus repository" "http://dist.wso2.org/maven2/"}
  :aot [workers.server]
  :main workers.server)
