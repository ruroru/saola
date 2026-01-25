(defproject org.clojars.jj/saola "1.0.0-SNAPSHOT"
  :description "Soala is a dependency injection and lifecycle management library for clojure"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url  "https://www.eclipse.org/legal/epl-2.0/"}


  :deploy-repositories [["clojars" {:url      "https://repo.clojars.org"
                                    :username :env/clojars_user
                                    :password :env/clojars_pass}]]

  :profiles {:test
             {:dependencies [
                             [ch.qos.logback/logback-classic "1.5.26"]
                             [mock-clj "0.2.1"]]}
             }


  :dependencies [[org.clojure/clojure "1.11.1"]
                 [org.clojure/tools.logging "1.3.0"]]


  :plugins [[org.clojars.jj/bump "1.0.4"]
            [org.clojars.jj/bump-md "1.1.0"]
            [org.clojars.jj/lein-git-tag "1.0.0"]
            [org.clojars.jj/strict-check "1.1.0"]]

  )
