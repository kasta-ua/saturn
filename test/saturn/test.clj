(ns saturn.test
  (:require [clojure.test :as test]
            [saturn schedule-test]))


(defn -main [& _]
  (test/run-all-tests #"saturn.*test"))
