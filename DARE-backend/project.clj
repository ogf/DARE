(defproject DARE-backend "1.0.0-SNAPSHOT"
  :description ""
  :aot [backend.core]
  :repositories {"codehaus repository" "http://dist.wso2.org/maven2/"}
  :dependencies [[org.clojure/clojure "1.2.0"]
                 [org.clojure/clojure-contrib "1.2.0"]
                 [es.uvigo.ei.sing/DARE-domain "0.1"]
                 [DARE-workers "0.1-SNAPSHOT"]
                 [congomongo "0.1.7-SNAPSHOT"]
                 [robert/hooke "1.1.2"]])
