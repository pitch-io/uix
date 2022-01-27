(ns uix.test-lazy
  (:require [uix.core :refer [defui]]))

(defui lazy-component [{:keys [x y after-render]}]
  (uix.core/use-effect
    (fn []
      (after-render x y))
    #js [])
  x)
