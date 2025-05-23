(ns uix.dom.rsc
  (:require [uix.core :refer [defui $] :as uix]
            [uix.dom :as dom]
            [uix.rsc]))

;; Client references resolver
;; component ref -> component function
(set! (.-resolveRSCModule js/window)
      #(js/Promise.resolve (.-RSC_MODULES js/window)))

(defn render-root
  "container – root DOM element
   routes – reitit routes
   ssr-enabled - whether HTML for initial load should be rendered"
  [{:keys [container routes ssr-enabled]}]
  (let [element ($ uix.rsc/router
                   {:ssr-enabled ssr-enabled
                    :routes routes})]
    (uix/start-transition
      #(if ssr-enabled
         (dom/hydrate-root container element)
         (dom/render-root element (dom/create-root container))))))

