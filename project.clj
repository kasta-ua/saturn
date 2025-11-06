(require '[clojure.edn :as edn])

(def +deps+ (-> "deps.edn" slurp edn/read-string))

(defn deps->vec [deps]
  (vec (map (fn [[dep {:keys [:mvn/version exclusions]}]]
              (cond-> [dep version]
                exclusions (conj :exclusions exclusions)))
            deps)))

(def dependencies
  (deps->vec (:deps +deps+)))

(defproject ua.kasta/saturn "1.0.3"
  :dependencies ~dependencies
  :description "Run periodic tasks inside your application"
  :license {:name "Eclipse Public License - v1.0"
            :url "http://www.eclipse.org/legal/epl-v10.html"
            :distribution :repo
            :comments "same as Clojure"})

