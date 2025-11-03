(ns uix.reagent-test
  (:require [clojure.test :refer [deftest is testing async]]
            ["@testing-library/react" :as rtl]
            [reagent.core :as r]
            [reagent.impl.batching]
            [uix.reagent :refer [use-reaction]]
            [shadow.cljs.modern :refer [js-await]]))

(defn wait-frame []
  (js/Promise. (fn [resolve _]
                 (reagent.impl.batching/next-tick resolve))))

(defn test-for-atom-like [^js source-ref ^js sink-ref f]
  (let [result (rtl/renderHook #(use-reaction sink-ref))
        rerender (.-rerender result)
        unmount (.-unmount result)
        result (.-result result)]
    (testing "should track ref value"
      (is (= 0 (.-current result)))
      (f source-ref inc)
      (js-await [_ (wait-frame)]
        (rerender)
        (is (= 1 (.-current result)))
        (testing "should cleanup listeners on unmount"
          (unmount)
          (is (nil? (.-react-listeners sink-ref)))
          (is (empty? (.-watches sink-ref))))
        (testing "should continue tracking the same ref"
          (let [result (rtl/renderHook #(use-reaction sink-ref))
                rerender (.-rerender result)
                result (.-result result)]
            (is (= 1 (.-current result)))
            (f source-ref inc)
            (js-await [_ (wait-frame)]
              (rerender)
              (is (= 2 (.-current result))))))))))

(deftest test-use-reaction-with-reaction
  (async done
    (let [ref (r/atom 0)]
      (.then (test-for-atom-like ref ref swap!)
             done))))

(deftest test-use-reaction-with-track
  (async done
    (let [ref1 (r/atom 0)
          ref2 (r/atom 0)
          sink (r/track (fn [] @ref1))
          sink-2 (r/track! (fn [] @ref2))]
      (-> (test-for-atom-like ref1 sink swap!)
          (.then #(test-for-atom-like ref2 sink-2 swap!))
          (.then done)))))

(deftest test-use-reaction-with-cursor
  (async done
    (let [ref (r/atom {:a 0})
          cursor (r/cursor ref [:a])]
      (.then (test-for-atom-like ref cursor #(swap! %1 update :a %2))
             done))))
