(ns jj.saola.topsort-test
  (:require [clojure.test :refer [deftest are]]
            [jj.saola.topsort :as deps]))

(deftest single-test
  (are [expected input-vector input] (= expected (deps/add-dependency input-vector input))
                                     [[:key]] [] :key
                                     [[:key1] [:key]] [[:key1]] :key))

(deftest empty-returns-emtpy-vector
  (are [expected] (= expected (deps/add-dependency)) []))


(deftest multiple-test
  (are [expected] (= expected (deps/add-dependency)) []))

(deftest multiple-arity-test
  (are [expected input-vector input1 input2] (= expected (deps/add-dependency input-vector input1 input2))
                                             [[:key :dep]] [] :key :dep
                                             [[:key1] [:key :dep]] [[:key1]] :key :dep))

(deftest
  topsort-test
  (are [expected input] (= expected (deps/topsort input))
                        [#{:dep
                           :key1}
                         #{:key}] [[:key1] [:key :dep]]
                        ))



