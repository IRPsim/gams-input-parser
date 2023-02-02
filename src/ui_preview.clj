(ns ui-preview
  (:require parser
            frontend-generator
            clojure.inspector)
  (:gen-class))

(defn -main [&[arg]]
  (if (not arg)
    (println "Ein Argument: Verzeichnis des GAMS-Basismodells")
    (.setDefaultCloseOperation (-> arg
                                   parser/parse-gams-model
                                   (frontend-generator/generate-ui-data arg)
                                   clojure.inspector/inspect-tree)
                               javax.swing.WindowConstants/EXIT_ON_CLOSE)))

