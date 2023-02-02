(ns parameter-parser
  (:require [instaparse.core :as insta]))

(def parameter-parser (insta/parser "
par       = (input | output)
input     = <('par' | 'set' | 'sca')> type module ((reference rest?) | reference)?
output    = <'par_out'> type module ((reference rest?) | reference)?
<tag>       = #'[^_]*'
newline   = #'[^\n\r]*'
type      = <'_'> tag 
module    = <'_'> tag
reference = <'_'> tag
rest      = <'_'> tag (<'_'> tag)*"))


(defn parameter-name-parser
  "Creates a function to parse and translate the individual components of a parameter name."
  [input-map output-map]
  (let [parameter-in-transform {:type #(hash-map :type (get-in input-map [:type %] %)
                                                 :type-raw %)
                                :module #(hash-map :module (get-in input-map [:module %] %) 
                                                   :module-raw %)
                                :reference #(hash-map :reference (get-in input-map [:reference %] %)
                                                      :reference-raw %)
                                :rest #(hash-map :description-raw (vec %&))}
        parameter-out-transform {:type #(hash-map :type (get-in output-map [:type %] %)
                                                  :type-raw %)
                                 :module #(hash-map :module (get-in output-map [:module %] %) 
                                                    :module-raw %)
                                 :reference #(hash-map :reference (get-in output-map [:reference %] %)
                                                       :reference-raw %)
                                 :rest #(hash-map :description-raw (vec %&))}
        parameter-transform {:input (fn [& vs] (insta/transform parameter-in-transform (vec vs)))
                             :output (fn [& vs] (insta/transform parameter-out-transform (vec vs)))
                             :par (fn [maps] (apply merge maps))}]
    (fn [name]
      (let [parsed (insta/parse parameter-parser name)]
        (if (insta/failure? parsed)
          {}
          (insta/transform parameter-transform parsed))))))
