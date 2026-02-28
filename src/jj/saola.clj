(ns jj.saola
  (:require [clojure.tools.logging :as logger]
            [clojure.pprint :as pprint]
            [jj.saola.protocols :refer [Job Service start start-job stop]]
            [jj.saola.topsort :refer [topsort]]))

(defn- register-component
  [registry component]
  (let [component-id (:id component)]
    (when-not component-id
      (throw (ex-info "Component must have an :id field" {:component component})))
    (assoc registry component-id component)))

(defn- register-components
  [components]
  (reduce register-component {} components))

(defn- build-dependency-graph
  [registry]
  (map (fn [[component-id component]]
         (vec (cons component-id (:dependencies component []))))
       registry))

(defn- component-type
  [component]
  (cond
    (satisfies? Service component) :service
    (satisfies? Job component) :job
    :else (throw (ex-info "Component must implement Job or Service protocol"
                          {:component component}))))

(defn- build-dependency-results
  [registry component-id all-results]
  (let [component (get registry component-id)
        deps (:dependencies component [])]
    (into {} (map (fn [dep-id] [dep-id (get all-results dep-id)]) deps))))

(defn start-system
  "Start all components in dependency order.
   `components` is a plain sequence (list, vector, …) of component records —
   no pre-registration needed.
   Returns a system map that can be passed directly to `stop-system`."
  [components config]
  (let [registry (register-components components)
        dep-graph (build-dependency-graph registry)
        sorted-layers (topsort dep-graph)]

    (logger/info "STARTING SYSTEM")
    (when (logger/enabled? :debug)
      (logger/debug "Dependency graph:" dep-graph)
      (logger/debug "Sorted layers:" sorted-layers))

    (let [all-results
          (reduce
            (fn [results layer]
              (let [layer-promises
                    (mapv (fn [component-id]
                            (future
                              (let [component (get registry component-id)
                                    dependency-results (build-dependency-results registry component-id results)
                                    comp-type (component-type component)
                                    result (if (= comp-type :service)
                                             (do (logger/info "Starting service" (name component-id))
                                                 (start component (merge config dependency-results)))
                                             (do (logger/info "Starting job" (name component-id))
                                                 (start-job component (merge config dependency-results))))]
                                [component-id result])))
                          layer)
                    layer-results (mapv deref layer-promises)]
                (into results layer-results)))
            {}
            sorted-layers)]

      (logger/info "SYSTEM STARTED")

      (let [services (into {} (filter (fn [[k _]] (= :service (component-type (get registry k)))) all-results))
            jobs (into {} (filter (fn [[k _]] (= :job (component-type (get registry k)))) all-results))
            service-keys (set (keys services))
            shut-down-graph (->> (build-dependency-graph registry)
                                 topsort
                                 reverse
                                 (apply concat)
                                 (filter service-keys)
                                 (map (fn [k]
                                        {:service-id (get registry k)
                                         :key        (get services k)})))]

        {:shut-down-graph shut-down-graph
         :services        services
         :job-results     jobs
         :all-results     all-results}))))

(defn stop-system
  "Stop all services in the system, in reverse dependency order.
   Only the system map returned by `start-system` is required — no registry."
  [system]
  (logger/info "STOPPING SERVICES")
  (doseq [{:keys [service-id key]} (:shut-down-graph system)]
    (let [label (or (:id service-id) service-id)]
      (logger/info "Stopping service:" label)
      (stop service-id key)
      (logger/info "Stopped service:" label)))

  (logger/info "ALL SERVICES STOPPED"))