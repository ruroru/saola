(ns jj.saola-test
  (:require [clojure.test :refer :all]
            [mock-clj.core :as mock]
            [jj.saola :as saola])
  (:import (jj.saola.protocols Job Service)))

(def execution-log (atom []))

(defn log-execution [component-id phase]
  (swap! execution-log conj {:component component-id
                             :phase     phase
                             :timestamp (System/currentTimeMillis)}))

(declare extract-data-job)
(declare pulish-events-job)
(declare http-server-start)
(declare http-server-stop)
(declare health-check-start)
(declare health-check-stop)
(declare load-data-job)
(declare transform-data-job)
(declare cache-service-start)
(declare cache-service-stop)
(declare database-service-start)
(declare database-service-stop)

(defrecord DatabaseService [id]
  Service
  (start [this config]
    (log-execution id :start)
    (println "Starting database connection pool...")
    (database-service-start config)
    (Thread/sleep 100)
    {:connection "jdbc:postgresql://localhost:5432/warehouse"})
  (stop [this config]
    (log-execution id :stop)
    (database-service-stop config)
    (println "Closing database connections...")
    (Thread/sleep 50)))

(defrecord HttpServerService [id dependencies]
  Service
  (start [this config]
    (log-execution id :start)
    (println "Starting httpserver")
    (http-server-start config)
    (Thread/sleep 100)
    {:connection :server})
  (stop [this config]
    (log-execution id :stop)
    (http-server-stop config)
    (println "Closing httpserver")
    (Thread/sleep 50)))

(defrecord HealthCheckService [id dependencies]
  Service
  (start [this config]
    (log-execution id :start)
    (println "Starting httpserver")
    (http-server-start config)
    (Thread/sleep 100)
    {:connection :server})
  (stop [this config]
    (log-execution id :stop)
    (http-server-stop config)
    (println "Closing httpserver")
    (Thread/sleep 50)))


(defrecord ExtractDataJob [id dependencies]
  Job
  (start-job [this config]
    (log-execution id :execute)
    (let [db-spec (:database config)]
      (extract-data-job config)
      (println "Extracting data using db-spec:" db-spec)
      (Thread/sleep 100)

      {:exttract-return -1})))

(defrecord TransformDataJob [id dependencies]
  Job
  (start-job [this config]
    (log-execution id :execute)
    (let [db-spec (:database config)
          cache (:cache config)
          extract-result (:extract config)]
      (transform-data-job config)
      (println "Transforming with:")
      (println "  - database:" db-spec)
      (println "  - cache:" cache)
      (println "  - extracted rows:" (:rows extract-result))
      (Thread/sleep 120)
      :transform-data-result
      )))

(defrecord LoadDataJob [id dependencies]
  Job
  (start-job [this config]
    (log-execution id :execute)
    (let [db-spec (:database config)
          transform-result (:transform config)]
      (println "Loading" (:transformed-rows transform-result) "rows")
      (load-data-job config)
      (println "  - using database:" db-spec)
      (Thread/sleep 100)
      :load-result
      )))




(deftest successful-execution
  (testing "Verify execution happens in correct temporal order"
    (reset! execution-log [])
    (let [registry (list
                     (->DatabaseService :database)
                     (->ExtractDataJob :extract [:database])
                     (->TransformDataJob :transform [:extract :database])
                     (->LoadDataJob :load [:transform :database])
                     (->HttpServerService :http [:database])
                     )]
      (mock/with-mock [database-service-stop nil
                       database-service-start nil
                       extract-data-job nil
                       transform-data-job nil
                       http-server-start nil
                       http-server-stop nil
                       load-data-job nil]
                      (let [system (saola/start-system registry {:initial-config :value})]
                        (is (= (mock/calls database-service-start) [[{:initial-config :value}]]))
                        (is (= (mock/call-count database-service-start) 1))

                        (is (= (mock/calls extract-data-job) [[{:initial-config :value
                                                                :database       {:connection "jdbc:postgresql://localhost:5432/warehouse"}
                                                                }]]))
                        (is (= (mock/call-count extract-data-job) 1))

                        (is (= (mock/calls transform-data-job) [[{:initial-config :value
                                                                  :extract        {:exttract-return -1}
                                                                  :database       {:connection "jdbc:postgresql://localhost:5432/warehouse"}
                                                                  }]]))
                        (is (= (mock/call-count transform-data-job) 1))

                        (is (= (mock/calls http-server-start) [[{:initial-config :value
                                                                 :database       {:connection "jdbc:postgresql://localhost:5432/warehouse"}
                                                                 }]]))
                        (is (= (mock/call-count http-server-start) 1))

                        (let [log @execution-log
                              get-timestamp (fn [comp-id]
                                              (->> log
                                                   (filter #(= comp-id (:component %)))
                                                   first
                                                   :timestamp))]

                          (is (< (get-timestamp :database) (get-timestamp :extract))
                              "Database should start before extract (by timestamp)")
                          (is (< (get-timestamp :extract) (get-timestamp :transform))
                              "Extract should complete before transform (by timestamp)")
                          (is (< (get-timestamp :transform) (get-timestamp :load))
                              "Transform should complete before load (by timestamp)"))

                        (is (= {:all-results     {:database  {:connection "jdbc:postgresql://localhost:5432/warehouse"}
                                                  :extract   {:exttract-return -1}
                                                  :http      {:connection :server}
                                                  :load      :load-result
                                                  :transform :transform-data-result}
                                :job-results     {:extract   {:exttract-return -1}
                                                  :load      :load-result
                                                  :transform :transform-data-result}
                                :services        {:database {:connection "jdbc:postgresql://localhost:5432/warehouse"}
                                                  :http     {:connection :server}}
                                :shut-down-graph (list {:key        {:connection :server}
                                                        :service-id #jj.saola_test.HttpServerService{:dependencies [:database]
                                                                                                     :id           :http}}
                                                       {:key        {:connection "jdbc:postgresql://localhost:5432/warehouse"}
                                                        :service-id #jj.saola_test.DatabaseService{:id :database}})}
                               system))
                        (saola/stop-system system)
                        (is (= (mock/calls database-service-stop) [[{:connection "jdbc:postgresql://localhost:5432/warehouse"}]]))
                        (is (= (mock/call-count database-service-stop) 1)))))))


(declare service-a-dep-start)
(declare service-a-dep-stop)
(declare service-a-start)
(declare service-a-stop)
(declare service-a-parent-start)
(declare service-a-parent-stop)
(declare failing-service-b-start)

(defrecord ServiceADep [id]
  Service
  (start [this config]
    (log-execution id :start)
    (service-a-dep-start config)
    {:dep-resource :open})
  (stop [this config]
    (log-execution id :stop)
    (service-a-dep-stop config)))

(defrecord ServiceA [id dependencies]
  Service
  (start [this config]
    (log-execution id :start)
    (service-a-start config)
    {:a-resource :open})
  (stop [this config]
    (log-execution id :stop)
    (service-a-stop config)))

(defrecord ServiceAParent [id dependencies]
  Service
  (start [this config]
    (log-execution id :start)
    (service-a-parent-start config)
    {:parent-resource :open})
  (stop [this config]
    (log-execution id :stop)
    (service-a-parent-stop config)))

(defrecord FailingServiceB [id dependencies]
  Service
  (start [this config]
    (log-execution id :start)
    (failing-service-b-start config)
    (throw (ex-info "ServiceB failed to start" {:reason :boom})))
  (stop [this _]
    (log-execution id :stop)))

(deftest partial-start-failure-stops-already-started-services
  (testing "When a dependent service throws on start, every already-started service is stopped in reverse dependency order"
    (reset! execution-log [])
    (let [registry (list
                     (->ServiceADep     :a-dep)
                     (->ServiceA        :a        [:a-dep])
                     (->ServiceAParent  :a-parent [:a])
                     (->FailingServiceB :b        [:a]))]
      (mock/with-mock [service-a-dep-start    nil
                       service-a-dep-stop     nil
                       service-a-start        nil
                       service-a-stop         nil
                       service-a-parent-start nil
                       service-a-parent-stop  nil
                       failing-service-b-start nil]
        (is (thrown? Throwable
                     (saola/start-system registry {:initial-config :value})))

        (is (= 1 (mock/call-count service-a-dep-start))     "a-dep should start once")
        (is (= 1 (mock/call-count service-a-start))         "a should start once")
        (is (= 1 (mock/call-count service-a-parent-start))  "a-parent should start once")
        (is (= 1 (mock/call-count failing-service-b-start)) "b's start should be attempted once")

        (is (= 1 (mock/call-count service-a-dep-stop))    "a-dep should be stopped")
        (is (= 1 (mock/call-count service-a-stop))        "a should be stopped")
        (is (= 1 (mock/call-count service-a-parent-stop)) "a-parent should be stopped")

        (is (= (mock/calls service-a-dep-stop)    [[{:dep-resource :open}]])
            "a-dep/stop should receive the result returned by a-dep/start")
        (is (= (mock/calls service-a-stop)        [[{:a-resource :open}]])
            "a/stop should receive the result returned by a/start")
        (is (= (mock/calls service-a-parent-stop) [[{:parent-resource :open}]])
            "a-parent/stop should receive the result returned by a-parent/start")

        (let [events    @execution-log
              starts    (->> events (filter #(= :start (:phase %))) (mapv :component))
              stops     (->> events (filter #(= :stop  (:phase %))) (mapv :component))
              start-idx (fn [c] (.indexOf starts c))]
          (is (< (start-idx :a-dep) (start-idx :a))
              "a-dep starts before a")
          (is (< (start-idx :a) (start-idx :a-parent))
              "a starts before a-parent")
          (is (< (start-idx :a) (start-idx :b))
              "a starts before b")
          (is (= [:a-parent :a :a-dep] stops)
              "Services stop in reverse dependency order"))))))

