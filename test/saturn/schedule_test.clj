(ns saturn.schedule-test
  (:require [clojure.test :as test :refer [deftest testing is are]]
            [saturn.schedule :as s])
  (:import [java.time OffsetDateTime]))


(defn parse [s]
  (.toInstant (OffsetDateTime/parse s)))


(defn today-at [h m]
  (OffsetDateTime/parse (format "2020-11-30T%02d:%02d:00+02:00" h m)))


(deftest generating-schedules
  (with-redefs [s/today-at today-at]
    (are [expr expected] (= expected (->> (s/parse expr)
                                          (s/generate)
                                          (take 5)
                                          (map s/as-local)
                                          (map str)))

      [:every 1 :minute]
      ["2020-11-30T00:00+02:00[Europe/Kiev]"
       "2020-11-30T00:01+02:00[Europe/Kiev]"
       "2020-11-30T00:02+02:00[Europe/Kiev]"
       "2020-11-30T00:03+02:00[Europe/Kiev]"
       "2020-11-30T00:04+02:00[Europe/Kiev]"]

      [:every :hour :at 13]
      ["2020-11-30T00:13+02:00[Europe/Kiev]"
       "2020-11-30T01:13+02:00[Europe/Kiev]"
       "2020-11-30T02:13+02:00[Europe/Kiev]"
       "2020-11-30T03:13+02:00[Europe/Kiev]"
       "2020-11-30T04:13+02:00[Europe/Kiev]"]

      [:every 2 :hours :at 30]
      ["2020-11-30T00:30+02:00[Europe/Kiev]"
       "2020-11-30T02:30+02:00[Europe/Kiev]"
       "2020-11-30T04:30+02:00[Europe/Kiev]"
       "2020-11-30T06:30+02:00[Europe/Kiev]"
       "2020-11-30T08:30+02:00[Europe/Kiev]"]

      [:every 1 :day :at 8 0]
      ["2020-11-30T08:00+02:00[Europe/Kiev]"
       "2020-12-01T08:00+02:00[Europe/Kiev]"
       "2020-12-02T08:00+02:00[Europe/Kiev]"
       "2020-12-03T08:00+02:00[Europe/Kiev]"
       "2020-12-04T08:00+02:00[Europe/Kiev]"]

      [:every 1 :day :at 8 0, 13 0, 18 0]
      ["2020-11-30T08:00+02:00[Europe/Kiev]"
       "2020-11-30T13:00+02:00[Europe/Kiev]"
       "2020-11-30T18:00+02:00[Europe/Kiev]"
       "2020-12-01T08:00+02:00[Europe/Kiev]"
       "2020-12-01T13:00+02:00[Europe/Kiev]"]

      [:every :tuesday :at 9 0]
      ["2020-12-01T09:00+02:00[Europe/Kiev]"
       "2020-12-08T09:00+02:00[Europe/Kiev]"
       "2020-12-15T09:00+02:00[Europe/Kiev]"
       "2020-12-22T09:00+02:00[Europe/Kiev]"
       "2020-12-29T09:00+02:00[Europe/Kiev]"]

      [:every 1 :day :at 14 45 (s/filter-month 12) (s/filter-day 5)]
      ["2020-12-05T14:45+02:00[Europe/Kiev]"
       "2021-12-05T14:45+02:00[Europe/Kiev]"
       "2022-12-05T14:45+02:00[Europe/Kiev]"
       "2023-12-05T14:45+02:00[Europe/Kiev]"
       "2024-12-05T14:45+02:00[Europe/Kiev]"])))



(deftest adjusting-schedules
  (with-redefs [s/today-at today-at
                s/now      (constantly (parse "2020-11-30T12:30:00+02:00"))]

    (let [s                   (s/generate (s/parse [:every 1 :day :at 8 0, 13 0, 18 0]))
          old-last-run        (parse "2020-11-29T18:00:00+02:00")
          up-to-date-last-run (parse "2020-11-30T08:00:00+02:00")
          serialize           #(->> % (take 3) (map s/as-local) (map str))]

      (testing "inserts immediate re-run"
        (is (= ["2020-11-30T12:30+02:00[Europe/Kiev]"
                "2020-11-30T13:00+02:00[Europe/Kiev]"
                "2020-11-30T18:00+02:00[Europe/Kiev]"]
               (->> (s/adjust old-last-run s) serialize))))

      (testing "no immediate re-run"
        (is (= ["2020-11-30T13:00+02:00[Europe/Kiev]"
                "2020-11-30T18:00+02:00[Europe/Kiev]"
                "2020-12-01T08:00+02:00[Europe/Kiev]"]
               (->> (s/adjust up-to-date-last-run s) serialize)))))))
