(ns uix.re-frame-test
  (:require [clojure.test :refer [deftest is]]
            [reagent.impl.batching]
            [uix.re-frame :refer [use-reaction use-subscribe]]
            [uix.reagent :as uix-reagent]))


(deftest test-use-reaction-identical
  ; make sure the same use-reaction is used as in uix.reagent so tests can translate
  (is (identical? uix-reagent/use-reaction use-reaction)))