(ns util
  (:require [clojure.edn :as edn]))

(defn safely-read-edn [file]
  (try
    (let [data (edn/read-string (slurp file :encoding "Windows-1252"))]
      (assert (not (some symbol? (tree-seq coll? seq data))) "There seems to be a problem with string quotations. Check for superfluous quotes!")
      data)
    (catch RuntimeException e
      (println "could not parse EDN file" file ", message: " (.getMessage e))
      (throw e))))


