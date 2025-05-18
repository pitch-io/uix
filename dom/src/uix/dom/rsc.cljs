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
   rsc-endpoint – endpoint that generates React Flight payload and executes server actions
   ssr-enabled - whether HTML for initial load should be rendered"
  [{:keys [container routes rsc-endpoint ssr-enabled]}]
  (let [element ($ uix.rsc/router
                   {:ssr-enabled ssr-enabled
                    :routes routes
                    :rsc-endpoint rsc-endpoint})]
    (uix/start-transition
      #(if ssr-enabled
         (dom/hydrate-root container element)
         (dom/render-root element (dom/create-root container))))))

