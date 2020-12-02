(ns saturn.core
  (:require [clojure.tools.logging :as log]
            [chime.core :as chime]
            [saturn.store :as store]
            [saturn.schedule :as schedule]))


(defn- run [time command {:keys [func args args-fn schedule scheduler]}]
  (.setName (Thread/currentThread) (str "saturn-" command))
  (when (.isAfter (schedule/now) (.plus time (schedule/minutes 1)))
    (log/warnf "saturn command overlap %s" command)
    (when (:report-overlap scheduler)
      ((:report-overlap scheduler) {:command command})))
  (let [args (if args-fn
               (args-fn time)
               args)]
    (log/infof "running saturn command %s %s %s"
      command (pr-str args) (pr-str schedule))
    (store/running (:store scheduler) command args time)
    (func args)
    (store/completed (:store scheduler) command)
    (log/infof "saturn command %s succeeded" command)))


(defn- make-command [command {:keys [schedule scheduler last-run] :as opts}]
  (let [schedule-seq (->> schedule
                          (schedule/parse)
                          (schedule/generate)
                          (schedule/adjust last-run))]
    (chime/chime-at schedule-seq
      #(run % command opts)
      {:error-handler (fn [e]
                        (log/errorf e "saturn command %s failed" command)
                        (store/failed (:store scheduler) command)
                        (when (:report-error scheduler)
                          ((:report-error scheduler) {:error e, :command command}))
                        (not (instance? InterruptedException e)))})))


;;; Public API


(defn validate [commands]
  (doseq [[command {:keys [schedule]}] commands]
    (when-not (seq (-> schedule schedule/parse schedule/generate))
      (throw (ex-info (format "invalide schedule for command '%s'" command)
               {:command command
                :schedule schedule})))))


(defn make
  ([commands]
   (make commands {:store (store/dummy)}))
  ([commands opts]
   (validate commands)
   (merge opts
     {:commands commands
      :*running (atom {})})))


(defn start [{:keys [commands *running store
                     restart-skipped?
                     cleanup-history?] :as scheduler}]
  (when cleanup-history?
    (store/cleanup store))
  (let [runs (when restart-skipped? (store/history store))]
    (doseq [[command-name opts] commands
            :when (not (:disabled opts))
            :let  [opts (assoc opts
                          :last-run  (get runs command-name)
                          :scheduler scheduler)]]
      (log/infof "starting saturn schedule %s" command-name)
      (swap! *running assoc command-name (make-command command-name opts))))
  scheduler)


(defn stop [{:keys [*running] :as scheduler}]
  (swap! *running
    (fn [commands]
      (doseq [[command-name cmd] commands]
        (log/infof "stopping saturn schedule %s" command-name)
        (.close cmd))
      {}))
  scheduler)


(defn run-once [scheduler command]
  (let [opts (get-in scheduler [:commands command])]
    (assert (some? opts) (format "unknown command %s" command))
    (run (schedule/now) command (assoc opts :scheduler scheduler))))
