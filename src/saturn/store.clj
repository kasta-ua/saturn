(ns saturn.store)


(defprotocol State
  (running [this command args time])
  (completed [this command])
  (failed [this command]))


(defprotocol History
  (history [this]))


(defprotocol Cleanup
  (cleanup [this]))


(defrecord DummyStore []
  State
  (running [_ _ _ _] nil)
  (completed [_ _] nil)
  (failed [_ _] nil))


(defn dummy []
  (->DummyStore))
