(ns saturn.schedule-test
  (:require [clojure.test :as test :refer [deftest testing is are]]
            [saturn.schedule :as s])
  (:import [java.time OffsetDateTime ZonedDateTime ZoneId LocalTime LocalDate]))


(defn parse [s]
  (.toInstant (OffsetDateTime/parse s)))


(defn today-at [h m]
  (ZonedDateTime/of (LocalDate/of 2020 11 30) (LocalTime/of h m) s/tz))


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
       "2020-12-29T09:00+02:00[Europe/Kiev]"])))


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


(defn today-at-spring-dst [h m]
  "Mock today-at for spring DST transition testing (March 29, 2025)"
  (ZonedDateTime/of (LocalDate/of 2025 3 29) (LocalTime/of h m) s/tz))


(defn today-at-fall-dst [h m]
  "Mock today-at for fall DST transition testing (October 25, 2025)"
  (ZonedDateTime/of (LocalDate/of 2025 10 25) (LocalTime/of h m) s/tz))


(deftest dst-spring-forward-daily-schedule
  (testing "Daily schedule at 00:30 maintains local time across spring DST transition"
    (with-redefs [s/today-at today-at-spring-dst]
      (is (= ["2025-03-29T00:30+02:00[Europe/Kiev]"
              "2025-03-30T00:30+02:00[Europe/Kiev]"  ; 00:30 is before DST at 3:00, so still +02:00
              "2025-03-31T00:30+03:00[Europe/Kiev]"  ; After DST, now +03:00
              "2025-04-01T00:30+03:00[Europe/Kiev]"
              "2025-04-02T00:30+03:00[Europe/Kiev]"]
             (->> (s/parse [:every 1 :day :at 0 30])
                  (s/generate)
                  (take 5)
                  (map s/as-local)
                  (map str)))))))


(deftest dst-spring-forward-multiple-times
  (testing "Multiple daily times maintain local time across spring DST transition"
    (with-redefs [s/today-at today-at-spring-dst]
      (is (= ["2025-03-29T08:00+02:00[Europe/Kiev]"
              "2025-03-29T13:00+02:00[Europe/Kiev]"
              "2025-03-29T18:00+02:00[Europe/Kiev]"
              "2025-03-30T08:00+03:00[Europe/Kiev]"  ; After DST at 3:00, these are +03:00
              "2025-03-30T13:00+03:00[Europe/Kiev]"
              "2025-03-30T18:00+03:00[Europe/Kiev]"
              "2025-03-31T08:00+03:00[Europe/Kiev]"]
             (->> (s/parse [:every 1 :day :at 8 0, 13 0, 18 0])
                  (s/generate)
                  (take 7)
                  (map s/as-local)
                  (map str)))))))


(defn today-at-dst-crossing-spring [h m]
  "Mock today-at for testing across spring DST (March 30, 2025 at 00:30)"
  (ZonedDateTime/of (LocalDate/of 2025 3 30) (LocalTime/of h m) s/tz))


(deftest dst-spring-forward-hourly-schedule
  (testing "Hourly schedule maintains local hour across spring DST transition"
    (with-redefs [s/today-at today-at-dst-crossing-spring]
      ;; Starting at 00:30 on March 30, crossing the 3:00 AM DST transition
      ;; At 3:00 AM, clocks jump to 4:00 AM (UTC+2 → UTC+3)
      (is (= ["2025-03-30T00:30+02:00[Europe/Kiev]"  ; Before DST
              "2025-03-30T01:30+02:00[Europe/Kiev]"  ; Before DST
              "2025-03-30T02:30+02:00[Europe/Kiev]"  ; Before DST
              "2025-03-30T04:30+03:00[Europe/Kiev]"  ; After DST (3:30 doesn't exist!)
              "2025-03-30T05:30+03:00[Europe/Kiev]"] ; After DST
             (->> (s/parse [:every 1 :hour :at 30])
                  (s/generate)
                  (take 5)
                  (map s/as-local)
                  (map str)))))))


(defn today-at-before-dst-spring [h m]
  "Mock today-at for week before spring DST (March 22, 2025)"
  (ZonedDateTime/of (LocalDate/of 2025 3 22) (LocalTime/of h m) s/tz))


(deftest dst-spring-forward-weekday-schedule
  (testing "Weekday schedule maintains local time across spring DST transition"
    (with-redefs [s/today-at today-at-before-dst-spring]
      ;; March 22, 2025 is Saturday
      ;; Next Sunday is March 23 (before DST, +02:00)
      ;; Then March 30 (DST day - 09:00 is after 3:00 AM transition, so +03:00)
      (is (= ["2025-03-23T09:00+02:00[Europe/Kiev]"  ; Sunday before DST
              "2025-03-30T09:00+03:00[Europe/Kiev]"  ; Sunday AFTER DST transition
              "2025-04-06T09:00+03:00[Europe/Kiev]"  ; After DST
              "2025-04-13T09:00+03:00[Europe/Kiev]"
              "2025-04-20T09:00+03:00[Europe/Kiev]"]
             (->> (s/parse [:every :sunday :at 9 0])
                  (s/generate)
                  (take 5)
                  (map s/as-local)
                  (map str)))))))


(deftest dst-fall-back-daily-schedule
  (testing "Daily schedule at 00:30 maintains local time across fall DST transition"
    (with-redefs [s/today-at today-at-fall-dst]
      (is (= ["2025-10-25T00:30+03:00[Europe/Kiev]"
              "2025-10-26T00:30+03:00[Europe/Kiev]"  ; 00:30 is before fall DST at 4:00, so still +03:00
              "2025-10-27T00:30+02:00[Europe/Kiev]"  ; After fall DST, now +02:00
              "2025-10-28T00:30+02:00[Europe/Kiev]"
              "2025-10-29T00:30+02:00[Europe/Kiev]"]
             (->> (s/parse [:every 1 :day :at 0 30])
                  (s/generate)
                  (take 5)
                  (map s/as-local)
                  (map str)))))))


(deftest dst-fall-back-multiple-times
  (testing "Multiple daily times maintain local time across fall DST transition"
    (with-redefs [s/today-at today-at-fall-dst]
      (is (= ["2025-10-25T08:00+03:00[Europe/Kiev]"
              "2025-10-25T13:00+03:00[Europe/Kiev]"
              "2025-10-25T18:00+03:00[Europe/Kiev]"
              "2025-10-26T08:00+02:00[Europe/Kiev]"  ; After fall DST at 4:00 AM, these are +02:00
              "2025-10-26T13:00+02:00[Europe/Kiev]"
              "2025-10-26T18:00+02:00[Europe/Kiev]"
              "2025-10-27T08:00+02:00[Europe/Kiev]"]
             (->> (s/parse [:every 1 :day :at 8 0, 13 0, 18 0])
                  (s/generate)
                  (take 7)
                  (map s/as-local)
                  (map str)))))))


(defn today-at-dst-crossing-fall [h m]
  "Mock today-at for testing across fall DST (October 26, 2025 at 00:30)"
  (ZonedDateTime/of (LocalDate/of 2025 10 26) (LocalTime/of h m) s/tz))


(deftest dst-fall-back-hourly-schedule
  (testing "Hourly schedule maintains local hour across fall DST transition"
    (with-redefs [s/today-at today-at-dst-crossing-fall]
      ;; Starting at 00:30 on October 26, crossing the 4:00 AM DST transition
      ;; At 4:00 AM, clocks fall back to 3:00 AM (UTC+3 → UTC+2)
      ;; Note: 03:30 happens twice! Our schedule should hit the second occurrence
      (is (= ["2025-10-26T00:30+03:00[Europe/Kiev]"  ; Before DST fall back
              "2025-10-26T01:30+03:00[Europe/Kiev]"  ; Before DST fall back
              "2025-10-26T02:30+03:00[Europe/Kiev]"  ; Before DST fall back
              "2025-10-26T03:30+03:00[Europe/Kiev]"  ; Before DST fall back (first 03:30)
              "2025-10-26T03:30+02:00[Europe/Kiev]"] ; After DST fall back (second 03:30!)
             (->> (s/parse [:every 1 :hour :at 30])
                  (s/generate)
                  (take 5)
                  (map s/as-local)
                  (map str)))))))


(defn today-at-before-dst-fall [h m]
  "Mock today-at for week before fall DST (October 18, 2025)"
  (ZonedDateTime/of (LocalDate/of 2025 10 18) (LocalTime/of h m) s/tz))


(deftest dst-fall-back-weekday-schedule
  (testing "Weekday schedule maintains local time across fall DST transition"
    (with-redefs [s/today-at today-at-before-dst-fall]
      ;; October 18, 2025 is Saturday
      ;; Next Sunday is October 19 (before DST fall back, +03:00)
      ;; Then October 26 (DST fall back at 4:00 AM - 09:00 is after, so +02:00)
      (is (= ["2025-10-19T09:00+03:00[Europe/Kiev]"  ; Sunday before DST fall back
              "2025-10-26T09:00+02:00[Europe/Kiev]"  ; Sunday AFTER DST fall back
              "2025-11-02T09:00+02:00[Europe/Kiev]"  ; After DST
              "2025-11-09T09:00+02:00[Europe/Kiev]"
              "2025-11-16T09:00+02:00[Europe/Kiev]"]
             (->> (s/parse [:every :sunday :at 9 0])
                  (s/generate)
                  (take 5)
                  (map s/as-local)
                  (map str)))))))


(deftest dst-minute-schedules-cross-dst
  (testing "Minute schedules use fixed duration across DST (wall-clock skips during spring forward)"
    (with-redefs [s/today-at (fn [h m] (ZonedDateTime/of (LocalDate/of 2025 3 30) (LocalTime/of 2 58) s/tz))]
      ;; Starting at 02:58, crossing the 3:00 AM spring forward (clocks jump to 4:00 AM)
      ;; Minute schedules maintain FIXED 60-second intervals, so wall-clock appears to skip
      (is (= ["2025-03-30T02:58+02:00[Europe/Kiev]"
              "2025-03-30T02:59+02:00[Europe/Kiev]"
              "2025-03-30T04:00+03:00[Europe/Kiev]"  ; Skips 3:00-3:59 (doesn't exist!)
              "2025-03-30T04:01+03:00[Europe/Kiev]"
              "2025-03-30T04:02+03:00[Europe/Kiev]"]
             (->> (s/parse [:every 1 :minute])
                  (s/generate)
                  (take 5)
                  (map s/as-local)
                  (map str)))))))


(deftest dst-minute-schedules-cross-dst-fall
  (testing "Minute schedules use fixed duration across fall DST (wall-clock appears to go backwards)"
    (with-redefs [s/today-at (fn [h m] (ZonedDateTime/of (LocalDate/of 2025 10 26) (LocalTime/of 3 58) s/tz))]
      ;; Starting at 03:58 (before fall back), crossing the 4:00 AM fall back (clocks go to 3:00 AM)
      ;; Fixed intervals mean we see times that appear to go backwards in wall-clock
      (is (= ["2025-10-26T03:58+03:00[Europe/Kiev]"
              "2025-10-26T03:59+03:00[Europe/Kiev]"
              "2025-10-26T03:00+02:00[Europe/Kiev]"  ; Wall-clock goes back!
              "2025-10-26T03:01+02:00[Europe/Kiev]"
              "2025-10-26T03:02+02:00[Europe/Kiev]"]
             (->> (s/parse [:every 1 :minute])
                  (s/generate)
                  (take 5)
                  (map s/as-local)
                  (map str)))))))


(deftest dst-multi-day-schedule
  (testing "Multi-day schedule maintains local time across DST transitions"
    (with-redefs [s/today-at today-at-spring-dst]
      (is (= ["2025-03-29T10:00+02:00[Europe/Kiev]"
              "2025-03-31T10:00+03:00[Europe/Kiev]"  ; 2 days later, after DST on March 30
              "2025-04-02T10:00+03:00[Europe/Kiev]"
              "2025-04-04T10:00+03:00[Europe/Kiev]"
              "2025-04-06T10:00+03:00[Europe/Kiev]"]
             (->> (s/parse [:every 2 :days :at 10 0])
                  (s/generate)
                  (take 5)
                  (map s/as-local)
                  (map str)))))))
