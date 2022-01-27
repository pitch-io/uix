(ns uix.core-test
  (:require [clojure.test :refer :all]
            [uix.core]))

(deftest test-parse-sig
  (is (thrown-with-msg? AssertionError #"uix.core\/defui doesn't support multi-arity"
                        (uix.core/parse-sig 'component-name '(([props]) ([props x])))))
  (is (thrown-with-msg? AssertionError #"uix.core\/defui should be a single-arity component"
        (uix.core/parse-sig 'component-name '([props x])))))
