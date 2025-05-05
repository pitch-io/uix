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
   rsc-endpoint – endpoint that generates React Flight payload
   server-actions-endpoint – endpoint that executes server actions"
  [{:keys [container routes rsc-endpoint server-actions-endpoint]}]
  (let [root (dom/create-root container)]
    (dom/render-root
      ($ uix/strict-mode
        ($ uix.rsc/router
           {:routes routes
            :rsc-endpoint rsc-endpoint
            :server-actions-endpoint server-actions-endpoint}))
      root)))
