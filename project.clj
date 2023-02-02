(defproject gams-input-parser "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.10.3"]
                 [instaparse "1.4.10"]
                 [cheshire "5.10.0"]]
  :profiles {:dev {:plugins [[lein-localrepo "0.5.4"]
                             [rhizome "0.2.9"]]}
             :backend-generator ^:leaky {:main backend-generator
                                         :aot [backend-generator]
                                         :uberjar-name "backend-generator.jar"}
             :frontend-generator ^:leaky {:main frontend-generator
                                          :aot [frontend-generator]
                                          :uberjar-name "frontend-generator.jar"}
             :ui-preview ^:leaky {:main ui-preview
                                  :aot [ui-preview]
                                  :uberjar-name "ui-preview.jar"}
             :check-gams ^:leaky {:main check-gams-model
                                  :aot [check-gams-model]
                                  :uberjar-name "check-gams-model.jar"}})
