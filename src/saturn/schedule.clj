(ns saturn.schedule
  (:require [clojure.spec.alpha :as s]
            [chime.core :as chime])
  (:import [java.time Instant Duration LocalTime ZonedDateTime ZoneId DayOfWeek]
           [java.time.temporal TemporalAdjusters]))


(def -WEEKDAYS
  (zipmap
    [:monday :tuesday :wednesday :thursday :friday :saturday :sunday]
    (DayOfWeek/values)))


(def WEEKDAYS (set (keys -WEEKDAYS)))


(s/def ::minute (s/int-in 0 60))
(s/def ::time   (s/cat :hour (s/int-in 0 24), :minute ::minute))


(s/def ::schedule
  (s/cat
    :quantifier #{:every}
    :period     (s/? int?)
    :unit       (into #{:minute :minutes :hour :hours :day :days} WEEKDAYS)
    :at         (s/? #{:at})
    :at         (s/? (s/alt
                       :minute ::minute
                       :times  (s/+ ::time)))))


(comment
  (s/conform ::schedule [:every :minute])
  (s/conform ::schedule [:every 2 :hours :at 50])
  (s/conform ::schedule [:every 1 :day :at 8 0, 13 0, 20 0])
  (s/conform ::schedule [:every :monday :at 9 0]))


(def tz ^ZoneId (ZoneId/of "Europe/Kiev"))


(defn ^Instant now []
  (Instant/now))


(defn ^ZonedDateTime as-local [^Instant t]
  (.atZone t tz))


(defn ^ZonedDateTime today-at [h m]
  (-> (LocalTime/of h m)
      (.adjustInto (ZonedDateTime/now tz))))


(defn with-weekday [dt weekday]
  (.with dt (TemporalAdjusters/nextOrSame (get -WEEKDAYS weekday))))


(defn ^Duration minutes [x]
  (Duration/ofMinutes x))


(defn ^Duration hours [x]
  (Duration/ofHours x))


(defn ^Duration days [x]
  (Duration/ofDays x))


(defn parse [schedule]
  (if (s/valid? ::schedule schedule)
    (s/conform ::schedule schedule)
    (throw (ex-info "invalid schedule"
             {:schedule schedule
              :explain  (s/explain-str ::schedule schedule)}))))


(defn generate
  "Returns an infinite sequence of times according to schedule"
  [{:keys [unit period at]
    :or   {period 1}}]
  (let [incr   (condp contains? unit
                 #{:minute :minutes} (minutes period)
                 #{:hour :hours}     (hours period)
                 #{:day :days}       (days period)
                 WEEKDAYS            (days (* period 7)))
        starts (case (first at)
                 :minute [(today-at 0 (second at))]
                 :times  (->> (second at)
                              (map #(today-at (:hour %) (:minute %)))
                              (sort))
                 [(today-at 0 0)])]
    (->> (mapv (fn [s]
                 (cond-> s
                   (contains? WEEKDAYS unit) (with-weekday unit)
                   :always                   .toInstant
                   :always                   (chime/periodic-seq incr)))
           starts)
         (apply interleave))))


(defn adjust
  "Returns a schedule sequence suitable for running right now.

  It means that:
  - past times are filtered out
  - first run might be _right now_ if we missed some runs,
    according to actual `last-run` and schedule"

  [last-run schedule-seq]

  (let [now           (now)
        [past future] (split-with #(.isBefore % now) schedule-seq)
        missed-last?  (and last-run (seq past) (.isAfter (last past) last-run))
        next-is-far?  (.isAfter (first future) (.plus now (minutes 5)))]
    (if (and missed-last? next-is-far?)
      (cons now future)
      future)))
