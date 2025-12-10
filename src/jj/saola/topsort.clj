(ns jj.saola.topsort)

(defn add-dependency
  ([]
   (vector))
  ([dependency-list dependency]
   (conj dependency-list (vector dependency)))

  ([dependency-list dependency dependee]
   (conj dependency-list (vector dependency dependee))))

(defn topsort
  [items]
  (let [deps (into {} (map (fn [[k & ds]] [k (set ds)]) items))
        all-keys (into (set (keys deps))
                       (mapcat rest items))]

    (loop [result []
           remaining (set all-keys)
           deps deps]
      (if (empty? remaining)
        result
        (let [ready (set (filter (fn [k]
                                   (empty? (get deps k #{})))
                                 remaining))]
          (if (empty? ready)
            (throw (ex-info "Cycle detected in dependencies"
                            {:remaining remaining :deps deps}))
            (let [new-result (conj result ready)
                  new-remaining (apply disj remaining ready)
                  new-deps (reduce (fn [m k]
                                     (into {} (map (fn [[key val]]
                                                     [key (disj val k)])
                                                   m)))
                                   deps
                                   ready)]
              (recur new-result new-remaining new-deps))))))))

