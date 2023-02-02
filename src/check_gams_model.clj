(ns check-gams-model
  (:require [parser]
            frontend-generator
            [instaparse.core :as insta]
            clojure.pprint
            [clojure.java.io :refer [file]]
            [clojure.string :as str])
  (:gen-class))

(defn check-model 
  "Returns seq of [file error] for all erroneous input/output files."
  [dir]
  (->> dir
    (java.io.File.)
    file-seq
    (filter #(re-matches #"(input|output).*gms" (.getName %)))
    (keep (fn [file]
            (let [text (slurp file :encoding "Windows-1252")
                  parsed (insta/parse parser/gams-input-parser text :total true)]
              (when (insta/failure? parsed)
                [file (insta/get-failure parsed)]))))))


(defn check-set-hierarchies
  "validate that all sets that have subsets are hidden and therefore not modifyable by user actions."
  [ui-data]
  (let [invalid-sets (->> ui-data :sets vals (filter (comp not-empty :subsets)) (remove :hidden))]
    (if (not-empty invalid-sets)
      (do
        (doseq [{name :name ss :subsets} invalid-sets]
          (printf "Set \"%s\" hat Untermengen (%s), darf also nicht editierbar sein (Bitte setze \"hidden: 1\")\n" name (str/join "," ss)))
        (flush)
        false)
      true)))

(defn print-table 
  "Print a tabular representation of the parsed information from an IRPsim GAMS model."
  [parsed keys]
  (with-out-str
    (clojure.pprint/print-table keys parsed)))

(defn -main [& args]
  (if (not= 1 (count args))
    (println "Ein Argument: Verzeichnis des GAMS-Basismodells")
    (let [dir (first args)
          parsed (parser/parse-gams-model dir)
          model-name (.getName (file dir))]
      (.mkdir (file "target"))
      (frontend-generator/parse-ui-structures dir)
      (let [errors (check-model dir)]
        (if (empty? errors)
          (if (check-set-hierarchies (frontend-generator/generate-ui-data* parsed))
            (do
              ((juxt println (partial spit (file "target" (str model-name ".txt.utf8")))) (print-table parsed [:name :type :data-type :unit :domain :dependencies :identifier :tags]))
              ((juxt println (partial spit (file "target" (str model-name "-validation.txt.utf8")))) (print-table parsed [:name :type :validation :rule])))
            (System/exit 1))              
          (binding [*out* *err*]
            (clojure.pprint/pprint errors)
            (System/exit 1)))))))

