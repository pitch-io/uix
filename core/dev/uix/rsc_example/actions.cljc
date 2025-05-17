(ns uix.rsc-example.actions
  (:require [uix.rsc :refer [defaction]]))

(defaction vote [id score]
  ;; todo: quick db,sqlite example
  (inc score))