(ns saturn.store.pg
  (:require [clojure.java.jdbc :as jdbc]
            [chime.core :as chime]
            [chime.joda-time]  ;; register chime/->instant for JodaTime
            [saturn.store :as store])
  (:import [java.time Instant]
           [java.sql Timestamp]))


(extend-protocol jdbc/ISQLValue
  Instant
  (sql-value [v]
    (Timestamp/from v)))


(defn ->state [state]
  (doto (org.postgresql.util.PGobject.)
    (.setType "saturn_state")
    (.setValue (name state))))


(defn set-state! [db command state]
  (jdbc/execute! db
    ["update saturn set state = ?, completed_at = now() where state = ? and name = ?"
     (->state state) (->state :running) command]))


(defn get-history [db]
  (jdbc/query db
    ["select name, max(scheduled_at) as scheduled_at from saturn
      where state in (?, ?) group by name"
     (->state :completed) (->state :failed)]))


(defrecord PgStore [db]
  store/State
  (running [_ command args time]
    (jdbc/insert! db :saturn {:name         command
                              :args         (some-> args pr-str)
                              :state        (->state :running)
                              :scheduled_at time}))

  (completed [_ command]
    (set-state! db command :completed))

  (failed [_ command]
    (set-state! db command :failed))

  store/History
  (history [_]
    (into {}
      (map (juxt :name (comp chime/->instant :scheduled_at)))
      (get-history db)))

  store/Cleanup
  (cleanup [_]
    (jdbc/delete! db :saturn ["scheduled_at <= now() - '3 month'::interval"])))


(defn pg [db-spec]
  (->PgStore db-spec))


(comment
  (def db {:dbtype   "postgresql"
           :host     "localhost"
           :port     5432
           :dbname   "test"
           :user     "test"
           :password "test"})
  (jdbc/execute! db (slurp "resources/pg_table.sql"))
  (def s (pg db))
  (store/running s "test" {:a 1, :b 3} (Instant/now))
  (store/completed s "test")
  (store/failed s "test")
  (store/history s)
  (store/cleanup s))
