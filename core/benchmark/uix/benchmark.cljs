(ns uix.benchmark
  (:require-macros [uix.benchmark :refer [bench]])
  (:require [reagent.core :as r]
            ["react-dom/server" :as rserver]
            [react :as react]
            [uix.core :refer [defui $]]
            [uix.uix :as uix]
            [uix.reagent :as reagent]
            [uix.reagent2 :as reagent2]
            [uix.react :refer [Editor]]
            [uix.helix :as helix]))

(set! (.-React js/globalThis) react)

(defn render [el]
  (rserver/renderToString el))

(def reagent-compiler
  (r/create-compiler {:function-components true}))

(defn ^:export run-react []
  (bench :react 10000 (render (react/createElement Editor))))

(defn ^:export run-uix []
  (bench :uix 10000 (render ($ uix/editor-compiled))))

(defn ^:export run-helix []
  (bench :helix 10000 (render ($ helix/editor-compiled))))

(defn ^:export run-reagent []
  (bench :reagent 10000 (render (r/as-element [reagent/editor]))))

(defn ^:export run-reagent2 []
  (bench :reagent 10000 (render (r/as-element [reagent2/editor]))))

(defn -main [& args]
  (js/console.log "Warming up...")
  (bench :react 10000 (render (react/createElement Editor)))
  (bench :uix 10000 (render ($ uix/editor-compiled)))
  (bench :helix 10000 (render ($ helix/editor-compiled)))
  (bench :reagent 10000 (render (r/as-element [reagent/editor])))
  (bench :reagent2 10000 (render (r/as-element [reagent2/editor])))

  (js/console.log "Running the benchmark...")
  (bench :react 10000 (render (react/createElement Editor)))
  (bench :uix 10000 (render ($ uix/editor-compiled)))
  (bench :helix 10000 (render ($ helix/editor-compiled)))
  (bench :reagent 10000 (render (r/as-element [reagent/editor])))
  (bench :reagent2 10000 (render (r/as-element [reagent2/editor]))))
