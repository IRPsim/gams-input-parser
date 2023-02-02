(ns backend-generator
  (:require [parser]
            [cheshire.core :as json]
            frontend-generator)
  (:gen-class))

(defn- matches-tag? [tag m]
  (some #{tag} (:tags m)))

(def default-map {:dependencies []
                  :processing []
                  :overview false
                  :description ""
                  :domain {}
                  :unit "Unbekannt"})

(defn- to-backend-format [vs]
  (->> vs
    (map #(vector (:name %) (merge default-map (select-keys % [:dependencies :processing :overview :description :unit :domain]))))
    (into (sorted-map))))

(defn generate-backend-dependencies [dir]
  (let [parsed (parser/parse-gams-model dir)
        non-sets (filter (comp #{:parameter :scalar} :type) parsed)]
    (println (json/generate-string
      {:input  (to-backend-format (filter (partial matches-tag? "input") non-sets))
       :output (to-backend-format (filter (partial matches-tag? "output") non-sets))
       :sets (->> parsed
                  frontend-generator/generate-ui-data*
                  :sets
                  (reduce-kv (fn [res k v] (assoc res k (:supersets v))) {}))}
      {:pretty true}))) 
  )


(defn -main [& args]
  (if (not= 1 (count args))
    (println "Ein Argument: Verzeichnis des GAMS-Basismodells")
    (generate-backend-dependencies (first args))))

