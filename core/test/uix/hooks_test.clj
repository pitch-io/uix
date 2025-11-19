(ns uix.hooks-test
  (:require [clojure.test :refer [deftest is testing run-tests]]
            [uix.core :as uix]))

(deftest test-use-state
  (is (= 1 (first (uix/use-state 1)))))

(deftest test-use-reducer
  (let [called? (atom false)
        f #(reset! called? true)
        [v rf] (uix/use-reducer f 1)]
    (is (= 1 v))
    (is (false? @called?))
    (is (thrown-with-msg? UnsupportedOperationException
                          #"set-state can't be called during SSR"
                          (rf 1 2)))))

(deftest test-use-ref
  (let [ref (uix/use-ref 1)]
    (is (= 1 @ref))))

(deftest test-use-effect
  (let [called? (atom false)]
    (is (nil? (uix/use-effect #(reset! called? true))))
    (is (false? @called?))))

(deftest test-use-layout-effect
  (let [called? (atom false)]
    (is (nil? (uix/use-layout-effect #(reset! called? true))))
    (is (false? @called?))))

(deftest test-use-insertion-effect
  (let [called? (atom false)]
    (is (nil? (uix/use-insertion-effect #(reset! called? true))))
    (is (false? @called?))))

(deftest test-use-callback
  (let [f (uix/use-callback inc [])]
    (is (= 1 (f 0)))))

(deftest test-use-memo
  (is (= 1 (uix/use-memo #(inc 0) []))))

(deftest test-use-imperative-handle
  (let [called? (atom false)]
    (is (nil? (uix/use-imperative-handle nil #(reset! called? true))))
    (is (false? @called?))))

(deftest test-use-debug
  (is (nil? (uix/use-debug 1 2))))

(deftest test-use-deferred-value
  (is (= 1 (uix/use-deferred-value 1))))

(deftest test-use-transition
  (let [[pending? start-transition] (uix/use-transition)]
    (is (false? pending?))
    (is (thrown-with-msg? UnsupportedOperationException
                          #"start-transition can't be called during SSR"
                          (start-transition)))))

(deftest test-use-id
  (is (uuid? (uix/use-id))))

(deftest test-use-sync-external-store
  (is (= 1 (uix/use-sync-external-store
            (constantly nil)
            (constantly nil)
            (constantly 1))))
  (is (thrown-with-msg? UnsupportedOperationException
                        #"should provide get-server-snapshot as well, during SSR"
                        (uix/use-sync-external-store
                         (constantly nil)
                         (constantly nil)))))

(comment
  (run-tests))
