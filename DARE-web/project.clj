(defproject DARE-web "1.0.0-SNAPSHOT"
  :description "Responsible of launching a web worker on the specified port"
  :dependencies [[org.clojure/clojure "1.2.1"]
                 [org.clojure/tools.cli "0.2.1"]
                 [org.eclipse.jetty/jetty-annotations "8.0.4.v20111024"]]
  :aot [DARE-web.web]
  :main DARE-web.web)
