(defproject cljdedup "0.1.0-SNAPSHOT"
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [io.pedestal/pedestal.jetty "0.5.5"]
                 [io.pedestal/pedestal.service "0.5.5"]
                 [org.clojure/data.codec "0.1.1"]
                 [com.google.guava/guava "27.0.1-jre"]
                 [datascript/datascript "0.17.1"]
                 [hiccup/hiccup "2.0.0-alpha1"]]
  :profiles {:dev {:dependencies [[midje/midje "1.9.4"]]}})
