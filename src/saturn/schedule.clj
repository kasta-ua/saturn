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


(defn- zoned-periodic-seq
  "Like chime/periodic-seq but uses ZonedDateTime arithmetic to respect DST.
   For minutes, uses Instant + Duration (DST-agnostic).
   For hours/days, uses ZonedDateTime + Period (DST-aware)."
  [^ZonedDateTime start unit period]
  (let [advance (cond
                  (contains? #{:minute :minutes} unit)
                  ;; For minutes, convert to Instant and use Duration (DST-agnostic is correct)
                  (let [dur (minutes period)]
                    (fn [^Instant t] (.plus t dur)))

                  (contains? #{:hour :hours} unit)
                  ;; For hours, use Period.ofHours to respect DST
                  (let [p (java.time.Period/ofDays 0)]
                    (fn [^ZonedDateTime t] (.plusHours t period)))

                  (contains? #{:day :days} unit)
                  ;; For days, use plusDays to respect DST
                  (fn [^ZonedDateTime t] (.plusDays t period))

                  (contains? WEEKDAYS unit)
                  ;; For weekdays, add weeks (7 days)
                  (fn [^ZonedDateTime t] (.plusWeeks t period)))]
    (if (contains? #{:minute :minutes} unit)
      ;; Minutes: use Instant-based sequence
      (chime/periodic-seq (.toInstant start) (minutes period))
      ;; Hours/Days/Weekdays: generate ZonedDateTime sequence, convert to Instant
      (map #(.toInstant %) (iterate advance start)))))


(defn generate
  "Returns an infinite sequence of times according to schedule"
  [{:keys [unit period at]
    :or   {period 1}}]
  (let [starts (case (first at)
                 :minute [(today-at 0 (second at))]
                 :times  (->> (second at)
                              (map #(today-at (:hour %) (:minute %)))
                              (sort))
                 [(today-at 0 0)])]
    (->> (mapv (fn [s]
                 (let [start (cond-> s
                               (contains? WEEKDAYS unit) (with-weekday unit))]
                   (zoned-periodic-seq start unit period)))
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
