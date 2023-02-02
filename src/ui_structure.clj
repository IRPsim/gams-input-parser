(ns ui-structure
  (:require [clojure.set :refer [subset? difference]]
            clojure.walk
            [cheshire.core :as json]
            [clojure.set :as cs]))


(defn- matches-tags? [should is] 
  (or (empty? should) 
      (some #(subset? (set %) (set is)) should)))

(defn- find-set 
  "Find set by name."
  [n p]
  (-> p :sets (get n)))

(defn- names-of 
  "Find names of all constructs (sets, tables, timeseries, scalars, set attributes) that match
tags or where the name equals one of the tags (allows to use the full parameter name as a tag)."
  ([tags data type] (names-of tags data type true))
  ([tags data type exclude-hidden?] 
   (->> data
        (filter #(or (some (partial = (:name %)) tags) (matches-tags? tags (:tags %))))
        (remove #(and exclude-hidden? (:hidden %))) 
        (mapv #(hash-map :type type :name (:name %) :index (:index %))))))

(defn- tables-of-set [s data]
  (filter #(some #{s} (:dependencies %)) (vals (:tables data))))

(defn- find-all-selected-parameters [vs]
  (->> vs
    (tree-seq (some-fn map? sequential?) #(if (map? %) (vals %) (seq %)))
    (filter #(and (map? %) (= #{:name :type :index} (set (keys %)))))
    (into #{})))

(defn- find-all-selected-sets [vs]
  (->> vs
    (tree-seq (some-fn map? sequential?) #(if (map? %) (vals %) (seq %)))
    (filter map?)
    (map :set)
    (keep identity) 
    (into #{})))

#_"section types:
   - generic (label, description, children)
   - set (label and description from set's definition, must not have other 'set' children as a descendant)
   - parameter (parameter of a set, only valid as descendant of a section of type 'set'
   (- table (table row of a set, only valid as descendant of a section of type 'set')
   - misc. (scalar/timeseries via tags) "

;;;;;;;;;;;; graph specifics ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- validate-graph [data {edges :edges {:keys [set where color border shape icon]} :nodes :as g}]
  (letfn [(fixed-spec? [s] (and (map? s)
                                (= (:type s) :fix)))
          (subset-spec? [s] (and (map? s)
                                 (= (:type s) :subsets)
                                 (or (not (:groups s))
                                     (and (vector? (:groups s))
                                          (every? #(= #{:tags :value :label}
                                                      (clojure.core/set (keys %)))
                                                  (:groups s))))))
          (param-spec? [set-name spec]
            ;; TODO validate that the references parameter depends on set `set-name`, is a table parameter and has boolean values
            (and (map? spec)
                 (= (:type spec) :parameter)))]
    (assert (string? set) (format "set=\"%s\" muss ein Setname sein, ist aber kein String." (str set)))
    (assert (boolean (find-set set data)) (format "nodes=\"%s\" muss ein Setname sein, aber nicht gefunden." (str set)))
    (assert (or (nil? where) (string? where)) (format "where=\"%s\" muss entweder leer oder ein gültiger Parametername sein." where))
    ;; TODO validate `where` to be a parameter of set 'nodes' or a superset of it
    (assert (or (nil? color)
                (fixed-spec? color)
                (subset-spec? color)
                (param-spec? set color))
            (format "color=\"%s\" ist keine zulässige Spezifikation." color))
    (assert (or (nil? border)
                (fixed-spec? border)
                (subset-spec? border)
                (param-spec? set border))
            (format "border=\"%s\" ist keine zulässige Spezifikation." border))
    (assert (or (nil? shape)
                (fixed-spec? shape)
                (subset-spec? shape)
                (param-spec? set shape))
            (format "shape=\"%s\" ist keine zulässige Spezifikation." shape))    
    g))


(defn- find-edge-parameters
"edges are all boolean table parameters that have both sets as either the given `set` or a sub/super set of it."
  [{:keys [tags groups heading]} nodes-set data]
  (let [s (find-set nodes-set data)
        valid-set? (-> #{nodes-set}
                       (into (:supersets s))
                       (into (:subsets s)))
        potentials (filter (fn [{t :data-type deps :dependencies}]
                                  (and (= t "boolean")
                                       (= 2 (count deps))
                                       (every? valid-set? deps)))
                           (vals (:tables data)))
        parameters (map :name (names-of tags potentials :tables))
        groups' (for [{additional-tags :tags :as group} groups
                      :let [full-tags (for [tags tags, add-tags additional-tags] ;; cross product of common and additional tags
                                        (into (set tags) add-tags))
                            parameter-names (map :name (names-of full-tags potentials :tables))]]
                  (assoc group :parameters parameter-names))
        grouped-parameters (mapcat :parameters groups')
        ungrouped-parameters (cs/difference (set parameters) (set grouped-parameters))]
    {:heading heading
     :groups (into (vec groups') (for [p ungrouped-parameters
                                       :let [{l :identifier c :color v :value} (get-in data [:tables p])]]
                                   {:label l
                                    :color (or v c)
                                    :parameters [p]}))}))

(defn- normalize-node-parameter
"We know three kinds of node attribute config: :subsets, :fix and :parameter.
For :parameter we need to find all eligible parameters, that is boolean table parameters
where at least one dependency is in the set hierarchy of the graphs nodes set."
  [{:keys [type tags groups] :as m} attribute nodes-set data]
  (let [s (find-set nodes-set data)
        valid-set? (-> #{nodes-set}
                       (into (:supersets s))
                       (into (:subsets s)))]
    (case type
      :parameter (let [potential-params (filter (fn [{t :data-type [d1 d2] :dependencies}]
                                                  (and (= t "boolean")
                                                       (and d1 d2)
                                                       (or (valid-set? d1)
                                                           (valid-set? d2))))
                                                (vals (:tables data)))]
                   (assoc m :value (mapv :name (names-of tags potential-params :tables))))
      :subsets (let [subsets (->> s
                              :subsets
                              (map #(get-in data [:sets %]))
                              (filter (comp empty? :subsets)))
                     groups' (for [{specific-tags :tags l :label :as group} groups
                                   :let [set-names (map :name (names-of specific-tags subsets :sets false))]]
                               {:label l
                                :sets set-names
                                attribute (:value group)})
                     grouped-sets (mapcat :sets groups')
                     ungrouped-sets (cs/difference (set (map :name subsets)) (set grouped-sets))]
                 (assoc m :groups (into (vec groups')
                                        (for [s ungrouped-sets
                                              :let [d (get-in data [:sets s])]]
                                          {:label (:identifier d)
                                           attribute (get d attribute)
                                           :sets [s]}))))
      m)))


;;;;;;;;;;;; main functions ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn normalize-template
  [template data context]
  (cond
    (vector? template) (vec (keep #(normalize-template % data (update context :depth (fnil inc 1))) template))
    (nil? template) nil
    :else (let [context (if (:set template) (assoc context :set (find-set (:set template) data)) context)
                s (:set context)
                label (or (:label template) (:identifier s) "Keine Überschrift")
                desc (or (:description template) (:description s) "Keine Beschreibung")
                icon (:icon template)
                tags (mapv #(conj % (:default-tag context)) (:tags template))
                image (:image template)
                contents (vec
                          (when (:tags template)
                            (if s 
                              (concat (names-of tags (distinct (into (:attributes s) (mapcat #(:attributes (find-set % data)) (:supersets s)))) :attributes)
                                      (names-of tags (distinct (into (tables-of-set (:name s) data) (mapcat #(tables-of-set % data) (:supersets s)))) :tables))
                              (concat (names-of tags (vals (:timeseries data)) :timeseries)
                                      (names-of tags (vals (:scalars data)) :scalars)))))
                graph (when-let [g (:graph template)]
                        (let [{edges :edges {:keys [color border shape icon set where hide-single?]} :nodes} (validate-graph data g)]
                          {:edges (find-edge-parameters edges set data)
                           :nodes {:set set
                                   :hide-single? hide-single?
                                   :where where
                                   :fill (normalize-node-parameter color :color set data)
                                   :border (normalize-node-parameter border :border set data)
                                   :shape (normalize-node-parameter shape :shape set data)
                                   :icon (normalize-node-parameter icon :icon set data)}}))
                children (normalize-template (:sections template) data context)]                  
            {:label label
             :iconExpand (:iconExpand template icon)
             :iconCollapse (:iconCollapse template icon)
             :iconLeaf (:iconLeaf template icon)
             :description desc
             :set (:name s)
             :image image
             :contents (cond
                         graph (conj contents {:type :graph :graphSpec graph})
                         image (conj contents {:type :image})
                         :else contents)
             :children children})))


(defn collect-missing-parameters [normalized parsed {:keys [default-tag] :as context}]
  (let [all-parameters-found (find-all-selected-parameters normalized)
        all-timeseries (names-of [[default-tag]] (vals (:timeseries parsed)) :timeseries)
        timeseries-missing-set (difference (set all-timeseries) (set (filter #(= :timeseries (:type %)) all-parameters-found)))
        timeseries-missing (filter timeseries-missing-set all-timeseries)
        all-scalars (names-of [[default-tag]] (vals (:scalars parsed)) :scalars)
        scalars-missing-set (difference (set all-scalars) (set (filter #(= :scalars (:type %)) all-parameters-found)))
        scalars-missing (filter scalars-missing-set all-scalars)
        all-sets (set (keys (:sets parsed)))
        all-sets-found (set (find-all-selected-sets normalized))
        sets-missing-set (difference all-sets all-sets-found)
        sets-missing (filter sets-missing-set all-sets)]
    (concat 
      (when (not-empty sets-missing) [{:label "Fehlende Sets" 
                                       :description "Sets die nicht von der UI-Spezifikation erfasst wurden"
                                       :children (normalize-template (mapv #(hash-map :set %) sets-missing) parsed context)}])
      (when (not-empty scalars-missing) [{:label "Fehlende Skalare"
                                          :iconLeaf "fa fa-question" 
                                          :description "Skalare Parameter die nicht von der UI-Spezifikation erfasst wurden"
                                          :contents scalars-missing}])
      (when (not-empty timeseries-missing) [{:label "Fehlende Zeitreihen"
                                             :iconLeaf "fa fa-question"
                                             :description "Zeitreihenparameter die nicht von der UI-Spezifikation erfasst wurden"
                                             :contents timeseries-missing}]))))

(defn normalize 
  "Create a normalized version of a UI structure definition.
- Adds 'set' to each subtree that relates to a set
- Adds contents based on tags/sets
- Adds 'rest' section to each set with attributes/tables that were not selected in previous children
- Adds label/description to set children"
  [template parsed default-tag ignore-missing?]
  (let [context {:depth 1 :default-tag default-tag :ignore-missing? ignore-missing?}
        normalized (normalize-template template parsed context)
        missing (when (not ignore-missing?) 
                  (collect-missing-parameters normalized parsed context))] 
    (clojure.walk/postwalk
      (comp (fn [f] 
              (if (and (sequential? f)
                       (every? map? f)
                       (every? :index f))
                       (mapv #(dissoc % :index) (sort-by :index f))
                       f))
            (fn [f] (if (map? f)
                     (into (empty f) (filter (comp identity second) f))
                     f))) 
      (vec (concat normalized missing)))))

(comment
  (require '[clojure.java.io :as io])
  (require '[clojure.edn :as edn])
  (require 'frontend-generator)
  (require 'clojure.inspector)
  
  (def path "d:/Dropbox/Workspaces/irpsim/model/IRPsim")
  (def path "/home/steffen/Dropbox/Workspaces/irpsim/model/IRPsim")
  (def p (frontend-generator/generate-ui-data* (parser/parse-gams-model path)))
  
  (def demo-input (edn/read-string (slurp (io/file path "input" "ui-input.edn") :encoding "Windows-1252")))
  (def demo-output (edn/read-string (slurp (io/file path "output" "ui-output.edn") :encoding "Windows-1252")))
  (def demo-input [(first demo-input)])
  (def demo-input [(update-in (nth demo-input 6) [:sections] subvec 1 2)])
  (def structure (normalize demo-input  p "input" true))
  (def structure (normalize demo-output p "output" true))
  (clojure.pprint/pprint structure)
  (require '[cheshire.core :as json])
  (spit "d:/graph.json "(json/generate-string (:graph (first structure))))
  
  (clojure.inspector/inspect-tree demo-input)
  (clojure.inspector/inspect-tree structure)
  (clojure.inspector/inspect-tree p)
  (println (clojure.string/replace (json/write-str structure) "\"" "'"))
  (find-all-selected-sets structure)
  (frontend-generator/-main path)
  )
