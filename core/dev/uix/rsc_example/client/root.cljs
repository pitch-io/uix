(ns uix.rsc-example.client.root
  (:require [uix.dom.rsc :as dom.rsc]
            [uix.rsc-example.routes :refer [routes]]
            [uix.rsc-example.client.ui]))

(defn init []
  (dom.rsc/render-root
    {:container js/document
     :routes routes
     :rsc-endpoint "/rsc"
     :server-actions-endpoint "/api"}))
