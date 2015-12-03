(defproject recipes "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :source-paths ["src/clj" "src/cljs"]
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/clojurescript "1.7.145"]
                 [ring/ring-core "1.4.0"]
                 [ring/ring-jetty-adapter "1.4.0"]
                 [compojure "1.4.0"]
                 [prismatic/dommy "1.1.0"]
                 [cljs-ajax "0.5.1"]
                 [org.clojure/data.json "0.2.6"]
                 [hipo "0.5.1"]
                 [clj-orient "0.5.0"]]
  :plugins [[lein-cljsbuild "1.1.0"]
            [lein-ring "0.9.7"]]
  :main ^:skip-aot recipes.core
  :profiles {:uberjar {:aot :all}}
  :cljsbuild {:builds
              [{:source-paths ["src/cljs"]
                :compiler {:output-to "resources/public/js/recipes.js"
                           :optimizations :simple
                           :pretty-print false}}]}
;  :clean-targets ^{:protect :false} [:target-path "resources/public/js/"]
  :ring {:handler recipes.core/handler
         :auto-reload? true
         :auto-refresh? true})

