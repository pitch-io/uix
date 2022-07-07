(ns uix.core-test
  (:require [clojure.test :refer :all]
            [uix.core]
            [cljs.analyzer :as ana]
            [uix.lib]))

(deftest test-parse-sig
  (let [[sym methods] (uix.lib/parse-sig 'test '("docstring" ([x y] x) ([x] x)))]
    (is (= 'test sym))
    (is (= "docstring" (:doc (meta sym))))
    (is (= '(([x y] x) ([x] x)) methods))))

(deftest test-parse-defui-sig
  (is (thrown-with-msg? AssertionError #"uix.core\/defui doesn't support multi-arity"
                        (uix.core/parse-defui-sig 'component-name '(([props]) ([props x])))))
  (is (thrown-with-msg? AssertionError #"uix.core\/defui is a single argument component"
                        (uix.core/parse-defui-sig 'component-name '([props x])))))

(deftest test-parse-defhook-sig
  (is (thrown-with-msg? AssertionError #"React Hook name should start with `use-`, found `get-element-size` instead"
                        (uix.core/parse-defhook-sig 'get-element-size '(([props]) ([props x])))))
  (is (= '[use-element-size [([props]) ([props x])]]
         (uix.core/parse-defhook-sig 'use-element-size '(([props]) ([props x]))))))

(deftest test-vector->js-array
  (is (= '(uix.core/jsfy-deps (cljs.core/array 1 2 3)) (uix.core/vector->js-array [1 2 3])))
  (is (= '(uix.core/jsfy-deps x) (uix.core/vector->js-array 'x)))
  (is (nil? (uix.core/vector->js-array nil))))

(deftest test-$
  (testing "in cljs env"
    (with-redefs [uix.lib/cljs-env? (fn [_] true)
                  ana/resolve-var (fn [_ _] nil)]
      (is (= (macroexpand-1 '(uix.core/$ :h1))
             '(uix.compiler.aot/>el "h1" (cljs.core/array nil) (cljs.core/array))))
      (is (= (macroexpand-1 '(uix.core/$ identity {} 1 2))
             '(uix.compiler.alpha/component-element identity (cljs.core/array {}) (cljs.core/array 1 2))))
      (is (= (macroexpand-1 '(uix.core/$ identity {:x 1 :ref 2} 1 2))
             '(uix.compiler.alpha/component-element identity (cljs.core/array {:x 1 :ref 2}) (cljs.core/array 1 2))))))
  (testing "in clj env"
    (is (= (macroexpand-1 '(uix.core/$ :h1))
           [:h1]))
    (is (= (macroexpand-1 '(uix.core/$ identity {} 1 2))
           '[identity {} 1 2]))
    (is (= (macroexpand-1 '(uix.core/$ identity {:x 1 :ref 2} 1 2))
           '[identity {:x 1 :ref 2} 1 2]))))

(uix.core/defui clj-component [props] props)
(deftest test-defui
  (is (= {:x 1} (clj-component {:x 1}))))
