(ns jj.saola.protocols)

(defprotocol Job
  (start-job [this config]))


(defprotocol Service
  (start [this config])
  (stop [this config]))