(ns uix.benchmark
  (:require-macros [uix.benchmark :refer [bench]])
  (:require [reagent.core :as r]
            ["react-dom/server" :as rserver]
            [react :as react]
            [uix.core :refer [defui $]]
            [uix.uix :as uix]
            [uix.reagent :as reagent]
            [uix.react :refer [Editor]]
            [uix.helix :as helix]))

(set! (.-React js/global) react)

(defn render [el]
  (rserver/renderToString el))

(def reagent-compiler
  (r/create-compiler {:function-components true}))

(defn -main [& args]
  (js/console.log "Warming up...")
  (bench :react 10000 (render (react/createElement Editor)))
  (bench :uix 10000 (render ($ uix/editor-compiled)))
  (bench :helix 10000 (render ($ helix/editor-compiled)))
  (bench :reagent 10000 (render (r/as-element [reagent/editor])))

  (js/console.log "Running the benchmark...")
  (bench :react 10000 (render (react/createElement Editor)))
  (bench :uix 10000 (render ($ uix/editor-compiled)))
  (bench :helix 10000 (render ($ helix/editor-compiled)))
  (bench :reagent 10000 (render (r/as-element [reagent/editor]))))
