(defproject DARE-backend "1.0.0-SNAPSHOT"
  :description ""
  :repositories {"codehaus repository" "http://dist.wso2.org/maven2/"}
  :dependencies [[org.clojure/clojure "1.2.0"]
                 [org.clojure/clojure-contrib "1.2.0"]
                 [es.uvigo.ei.sing/DARE-domain "0.1"]
                 [congomongo "0.1.3-SNAPSHOT"]]
  :dev-dependencies [[swank-clojure/swank-clojure "1.2.1"]])
