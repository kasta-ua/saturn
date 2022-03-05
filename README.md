# Saturn

Saturn is a library for building and managing periodic cron-like jobs.

## Usage

```clojure
(require '[mount.core :as mount]
         '[saturn.core :as saturn]
         '[saturn.store.pg :as store])


(def commands
  {"cmd1" {:func #(println "cmd1")
           :schedule [:every :monday :at 9 0]}})


(defn report-error [{:keys [command error]}]
  (println "cron error" command error))


(defn report-overlap [{:keys [command]}]
  (println "cron command overlap" command))

         
(mount/defstate cron
  :start (saturn/make commands
           {:store            (store/pg db-conn)
            :restart-skipped? true
            :cleanup-history? true
            :report-error     report-error
            :report-overlap   report-overlap})
  :stop  (saturn/stop cron))
```


## Options

| option             | type                 | description                        |
|--------------------|----------------------|------------------------------------|
| `store`            | `saturn.store/State` | schedule state storage             |
| `restart-skipped?` | `boolean`            | restart previously unfinished jobs |
| `cleanup-history?` | `boolean`            | remove runs history from storage   |
| `report-error`     | `IFn`                | function to report run errors      |
| `report-overlap`   | `IFn`                | function to report overlap errors  |


## Schedule syntax

Question mark means component is optional, star - it repeats 0 or more times.

`[:every period? unit <:at hour? minute>*]`

| component | description                                       |
|-----------|---------------------------------------------------|
| `period`  | int, like in "every 3 days" or "every 30 minutes" |
| `unit`    | minute(s), hour(s), day(s) and week day names     |
| `hour`    | from 0 to 24                                      |
| `minute`  | from 0 to 60                                      |

Examples:

* `[:every :minute]`
* `[:every 2 :hours :at 50]`
* `[:every 1 :day :at 8 0, 13 0, 20 0]`
* `[:every :monday :at 9 0]`
