(ns frontend-generator
  (:refer-clojure :exclude [set?])
  (:require parser
            [ui-structure :as ui]
            util
            [instaparse.core :as insta]
            [cheshire.core :as json]
            [clojure.java.io :as io]
            [clojure.walk :as walk]
            [clojure.set :refer [difference]])
  (:gen-class))

;;;;;;;;;;;; generate ui data ;;;;;;;;;;;;;;;;;;;;;;;;;

(defn add-default-value [m] 
  (let [dt (:data-type m)
        default (:default m)]
    (cond 
      (or (nil? default) 
          (= "" default)) (assoc m :default 
                                 (case dt
                                   "integer" 0
                                   "integers" 0
                                   "float" 0.0
                                   "floats" 0.0
                                   "boolean" 0
                                   "timeseries" 0 
                                   "string" [] 
                                   nil
                                   #_(do (println "ERROR: Don't know the default value for data type " (:data-type m)))))
      (string? default) (assoc m :default
                               (case dt
                                 ("integer" "boolean") (java.lang.Integer/parseInt default)
                                 ("float" "floats") (java.lang.Float/parseFloat default)
                                 "string" (vec (.split default "\\s*,\\s*")) ;; comma separated list of names without whitespaces
                                 default))
      :else m)))

(def parameter? (comp #{:parameter} :type))
(def set? (comp #{:set} :type))

(defn- to-map [vs]
  (into {} (map (juxt :name identity) vs)))

(defn- find-subsets [sets s]
  (let [subset-names (map :name (filter #(some #{s} (:dependencies %)) (vals sets)))]
    (when (not-empty subset-names)
      (apply concat subset-names (map (partial find-subsets sets) subset-names)))))

(defn- find-supersets [sets s]
  (lazy-seq
   (let [ss (first (:dependencies (sets s)))]
     (when ss
       (cons ss (find-supersets sets ss))))))

(defn generate-ui-data* [parsed]
  (let [parsed (mapv #(let [details (:type-details %)
                            potential-tags (->> details vals distinct)]
                        (-> %
                            (update :tags (fn [tags] (vec (flatten (concat tags potential-tags)))))
                            add-default-value))
                     parsed)
        by-name (zipmap (map :name parsed) parsed)
        timeseries? (fn [{[dep1] :dependencies}] (= "timeseries" (:data-type (by-name dep1))))
        parameters (filter parameter? parsed)
        sets-with-parameters (->> parameters
                               (filter #(or (and (= 2 (count (:dependencies %)))
                                                 (timeseries? %)) 
                                            (= (count (:dependencies %)) 1)))
                               (map #(if (timeseries? %)
                                       (update-in % [:data-type] str "s") 
                                       %))
                               (group-by (comp last :dependencies))
                               (mapv (fn [[k vs]] (let [s (by-name k)]
                                                  (assert s (str "There is no set definition for " k))
                                                  (assoc s :attributes vs))))
                               (map (fn [m] (if ((set (mapcat :tags (:attributes m))) "output")
                                              (update m :tags conj "output")
                                              m))))
        sets-with-parameters-names (set (map :name sets-with-parameters))
        sets-without-parameters (->> parsed 
                                  (filter set?)
                                  (remove (comp sets-with-parameters-names :name))
                                  (map #(assoc % :attributes [])))
        timeseries-entry? #(= "timeseries" (-> % :data-type))
        timeseries-defs (mapcat :attributes (filter timeseries-entry? sets-with-parameters))
        sets (to-map (into sets-with-parameters sets-without-parameters))
        sets (map (fn [{s :name :as m}]
                    (assoc m :subsets   (vec (find-subsets   sets s))
                           :supersets (vec (find-supersets sets s)))) (vals sets))        
        table-defs (->> parameters
                     (filter #(or (and (= 2 (count (:dependencies %)))
                                       (not (timeseries? %)))
                                  (and (= 3 (count (:dependencies %)))
                                       (timeseries? %))))
                     (map #(if (timeseries? %)
                             (-> %
                               (update-in [:data-type] str "s")
                               (update-in [:dependencies] rest)) 
                             %)))
        scalars (filter #(= :scalar (:type %)) parsed)]
    {:scalars (to-map scalars)
     :sets (to-map sets)
     :tables (to-map table-defs)
     :timeseries (to-map timeseries-defs)}))

(defn parse-ui-structures [path]
  {:input (util/safely-read-edn (io/file path "input" "ui-input.edn"))
   :delta (util/safely-read-edn (io/file path "input" "ui-input-delta.edn"))
   :output (util/safely-read-edn (io/file path "output" "ui-output.edn"))})

(defn generate-ui-data [parsed path] 
  (let [p (generate-ui-data* parsed)
        uis (parse-ui-structures path)]
    {:definitions p
     :specifications {:input  (ui/normalize (:input uis) p "input" true)
                      :delta  (ui/normalize (:delta uis) p "input" true)
                      :output (ui/normalize (:output uis) p "output" true)}
     ;; load all SVG icons
     :icons (->> p
                 :sets
                 vals
                 (map :icon)
                 (keep identity)
                 distinct
                 (reduce (fn [m file]
                           (assoc m file (slurp (io/file path file))))
                         {}))}))
  
(defn -main [&[arg]]
  (if (not arg)
    (println "Ein Argument: Verzeichnis des GAMS-Basismodells")
    (let [m (-> arg
                parser/parse-gams-model
                (generate-ui-data arg))]
     (println (json/generate-string m)))))

(comment  
  ;; generate UI specific input parameter description
  (generate-ui-data parser/parsed)
  )
