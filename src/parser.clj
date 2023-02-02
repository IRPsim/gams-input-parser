(ns parser
  (:require [instaparse.core :as insta]
            [clojure.string :refer [trim]]
            [clojure.java.io :as io]
            [parameter-parser :as papa]
            util))
(defn map-values 
  "Change all values or all keys and values by applying a function to each of them."
  ([vf m] (map-values identity vf m))
  ([kf vf m]
   (into {} (for [[k v] m] [(kf k) (vf v)]))))

(def gams-input-parser (insta/parser (-> "gams-parameters.ebnf" io/resource slurp)))

(defn- to-vector [a b]
  (conj (if (vector? a)
          a
          [a])
        b))

(defn- convert-parameter-name [type m]
  (update (assoc m :type type)
          :dependencies
          #(vec (remove #{"*"} %))))

(defn- rule-cleaner [tag]
  (fn
    ([] {tag {}})
    ([p1 b1 p2 b2] {:type tag
                    :if (assoc p1 :value b1)
                    :then (assoc p2 :value b2)})))
(defn- optional [tag default]
  (fn 
    ([] {tag default})
    ([x] {tag x})))
(def transform-map {:text trim
                    :name (fn name-fn 
                            ([n] (name-fn n []))
                            ([n deps] {:name n :dependencies deps}))    
                    :boolean (comp (partial = 1) read-string)
                    :dependencies vector
                    :description (partial hash-map :description)
                    :type (fn ([] {:data-type ""}) ([type] {:data-type (clojure.string/lower-case type)}))
                    :identifier (partial hash-map :identifier)
                    :unit (optional :unit "")
                    :domain (optional :domain {})
                    :if-validation (rule-cleaner :if-validation)
                    :comparison (fn [a op b] {:type :comparison :predicate op :parameters [a b]})
                    :validation (optional :validation []) 
                    :rule (optional :rule [])
                    :if-rule (rule-cleaner :if-rule)
                    :note (optional :note "")
                    :hidden (fn [x] {:hidden (= x "1")})
                    :overview (fn [x] {:overview (= x "1")})
                    :shape (optional :shape nil)
                    :icon (optional :icon nil)
                    :fill (optional :fill nil)
                    :color (optional :color nil)
                    :processing (fn [& args] (hash-map :processing (vec (keep not-empty (map trim args)))))                    
                    :default (partial hash-map :default)
                    :variable-definition identity
                    :number read-string
                    :min-defined #(hash-map :min % :max nil)
                    :max-defined #(hash-map :min nil :max %)
                    :both-defined #(hash-map :min %1 :max %2)
                    :domain-enumeration #(hash-map :values (vec %&))
                    :domain-range (fn [left {:keys [min max values] :as m} right]
                                    (cond-> {}
                                      min (assoc ({"[" :>= "(" :>} left) min)
                                      max (assoc ({"]" :<= ")" :<} right) max)
                                      values (assoc :values values)))
                    :set (partial convert-parameter-name :set)
                    :parameter (partial convert-parameter-name :parameter)
                    :variable (partial convert-parameter-name :parameter)
                    :scalar #(hash-map :type :scalar :name %) 
                    :input-block (partial merge-with to-vector)})

(defn- parse-files [files name-parser]
  (let [text (clojure.string/join "\n"
                                  (map #(slurp % :encoding "Windows-1252")
                                       files))
        parsed (insta/parse gams-input-parser text :total false)]
    (if (insta/failure? parsed)
      (throw (ex-info "Could not parse GAMS files." {:error parsed}))
      (->> parsed
        (insta/transform transform-map)
        (map #(assoc % :type-details (name-parser (:name %))))
        (map-indexed (fn [i m] (assoc m :index i)))))))

(defn assoc-tags [parsed tags]
  (mapv #(update % :tags (fnil into []) tags) parsed))


(defn parse-gams-model
  "Look for #'input_.*' and #'output_.*' files recursively in `dir`. Parse all files."
  [dir]
  (let [all-files (file-seq (io/file dir))
        input-files (sort-by (memfn getName) (filter #(re-matches #"input.*gms" (.getName %)) all-files)) 
        output-files (sort-by (memfn getName) (filter #(re-matches #"output.*gms" (.getName %)) all-files))
        name-parser (papa/parameter-name-parser (util/safely-read-edn (io/file dir "input" "nomenklatur.edn"))
                                                (util/safely-read-edn (io/file dir "output" "nomenklatur.edn")))
        parsed-in (parse-files input-files name-parser)
        parsed-out (parse-files output-files name-parser)] 
    (concat (assoc-tags parsed-in ["input"])
            (assoc-tags parsed-out ["output"]))))

(comment
  
  (require 'clojure.pprint)
  (def dir "d:/Dropbox/Workspaces/irpsim/model/IRPsim/")
  (def dir "/home/steffen/Dropbox/Workspaces/irpsim/model/IRPsim")
    
  (def text (slurp (clojure.java.io/file dir "output/output_opt.gms")))
  
  
  (def parsed (parse-gams-model dir))
  
  ;; visualize parse tree-]
  (insta/visualize (gams-input-parser text))
  ;; print tables for all parsed variables
  (doseq [{n :name is :inputs} parsed]
    (clojure.pprint/print-table [:name :type :validation] parsed))
    (printf "\n%s\n-------------------------------------------------------\n" n)
  
  ;; all input files parseable?
  (doseq [file (filter #(re-matches #"output.*gms" (.getName %))
                  (file-seq (java.io.File. dir)))]
    (print "failure parsing" file "? ")
    (clojure.pprint/pprint (insta/failure? (insta/parse gams-input-parser (slurp file :encoding "Windows-1252"))))
    (println))
  
  (require '[rhizome.viz :as viz])
  ;; show visualization of dependencies
  (let [blocks (mapcat :inputs parsed)
        elems (filter map? blocks)
        nodes (map :name elems)        
        adjacent (reduce (fn [m e] (assoc m (:name e) (:dependencies e))) {} elems)
        node->cluster (into {} (mapcat (fn [{block-name :name is :inputs}]
                                         (map vector (map :name is) (repeat block-name))) parsed))
        args [nodes adjacent
              :node->descriptor (fn [n] {:label n})
              :node->cluster node->cluster
              :cluster->descriptor (fn [n] {:label n})
              :vertical? false]
        image (apply viz/graph->image args)]
    (viz/view-image image)
    (viz/save-image image "/home/steffen/inputs.png"))
  
  ;; visualize without blocks, reverse
  (let [blocks (mapcat :inputs parsed)
        elems (filter map? blocks)
        nodes (-> (map :name elems) set (disj "set_ii"))        
        adjacent (reduce (fn [m e] (assoc m (:name e) (:dependencies e))) {} elems)
        rev-adjacent (reduce (fn [m e] 
                               (reduce #(update-in %1 [%2] conj (:name e)) 
                                       m 
                                       (:dependencies e))) {} elems)
        args [nodes rev-adjacent
              :node->descriptor (fn [n] {:label n})
              :vertical? false]
        image (apply viz/graph->image args)]
    ;(println (apply rhizome.dot/graph->dot args)) 
    (viz/view-image image)
    (viz/save-image image "d:/inputs-wo-time.png"))
  
  ;; extract sets with scalar properties
  (let [blocks (mapcat :inputs parsed)
        by-single-dependency (->> blocks 
                               (filter (comp #{:parameter} :type))
                               (filter #(= 1 (count (:dependencies %))))
                               (group-by (comp first :dependencies)))]
    (doseq [[k vs] by-single-dependency]
      (printf "\n%s-----------------------------\n" k)
      (clojure.pprint/print-table [:name :type :data-type :unit :domain :dependencies :identifier] vs)))
   
  )

(comment
  ;; parse names
  
  (let [by-name (zipmap (map :name parsed) parsed)] 
    (->> parsed
      (filter (fn [{[dep1] :dependencies}] (= "timeseries" (:data-type (by-name dep1)))))
      (filter #(= :parameter (:type %)))
      (map :name)
      (def names)))
  (->> names
    (map (partial insta/parse parameter-parser))
    ;(map (comp second last))
    ;distinct
    (map (juxt identity (partial insta/transform parameter-transform)))
    (map println)
    )
  )

#_"
par_energyLink(set_sector, set_fromPss, set_toPss)
par_energyLinkTarif
- festgehaltene Sets müssen hidden sein und Defaultelemente enthalten
- projections: jeweils Tupel aus key-values, key ist Name des Sets im Parameter/Multiset, value ist Liste von entweder Strings (s.o.) oder Sets, die Untersets vom Parameterset sein müssen
- erzeuge aus projections Liste von neuen Parametern, für den Namen des neuen Parameters lösche jeweils die _ aus den Namen, verbinde mit _
- auf Ebene der Metadatentemplates: Variablen [setname] zu ersetzen durch Identifier des originalen Sets
- 
"
