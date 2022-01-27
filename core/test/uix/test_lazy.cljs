(ns uix.test-lazy
  (:require [uix.core :refer [defui]]))

(defui lazy-component [{:keys [x]}]
  x)
