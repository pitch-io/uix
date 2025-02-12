(ns uix.core-test
  (:require [cljs.env :as env]
            [clojure.string :as str]
            [clojure.test :refer :all]
            [uix.core :as uix]
            [cljs.analyzer :as ana]
            [preo.core]))

(deftest test-parse-sig
  (is (thrown-with-msg? AssertionError #"uix.core\/defui doesn't support multi-arity"
                        (uix.core/parse-defui-sig 'uix.core/defui 'component-name '(([props]) ([props x])))))
  (is (thrown-with-msg? AssertionError #"uix.core\/defui is a single argument component"
                        (uix.core/parse-defui-sig 'uix.core/defui 'component-name '([props x])))))

(deftest parse-defhook-sig
  (is (thrown-with-msg? AssertionError #"uix.core\/defhook should be single-arity function"
                        (uix.core/parse-defhook-sig 'use-hook '(([x]) ([x y])))))
  (is (thrown-with-msg? AssertionError #"React Hook name should start with `use-`, found `hook` instead."
                        (uix.core/parse-defhook-sig 'hook '([x])))))

(deftest test-defhook
  (uix.core/defhook use-hook
    "simple hook"
    [x]
    {:pre [(number? x)]}
    x)
  (is (:uix/hook (meta #'use-hook)))
  (is (= "simple hook" (:doc (meta #'use-hook)))))

(deftest test-vector->js-array
  (is (= '(cljs.core/array (uix.hooks.alpha/use-clj-deps [1 2 3]))
         (uix.core/->js-deps [1 2 3]))))

(deftest test-$
  (testing "in cljs env"
    (with-redefs [uix.lib/cljs-env? (fn [_] true)
                  ana/resolve-var (fn [_ _] nil)
                  env/*compiler* (atom {})]
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

(def clj-fn-component (uix.core/fn [props] props))
(deftest test-fn
  (is (= {:x 1} (clj-fn-component {:x 1}))))

(deftest test-clone-element
  (uix.core/defui test-clone-element-comp [])
  (let [el1 (uix.core/clone-element (uix.core/$ test-clone-element-comp {:title 0 :key 1 :ref 2} "child")
                                    {:data-id 3}
                                    "child2")
        el2 (uix.core/clone-element (uix.core/$ :div {:title 0 :key 1 :ref 2} "child")
                                    {:data-id 3}
                                    "child2")]
    (is (= el1 [test-clone-element-comp {:title 0 :key 1 :ref 2 :data-id 3 :children ["child2"]}]))
    (is (= el2 [:div {:title 0 :key 1 :ref 2 :data-id 3 :children ["child2"]}]))))

(deftest test-rest-props
  (testing "rest-props should extract rest props"
    (is (= '[[{:keys [a b c :uix.core-test/name],
               :strs [a b c "d"],
               :syms [a b c uix.core/str],
               :person/keys [name age],
               {:m 1 :n 2 :k 3} :opts,
               :or {a 1, b 2, c 3},
               on-click :on-click}]
             #{"d" a :on-click
               :person/age :opts :c "a"
               :uix.core-test/name uix.core/str
               :person/name "b" :b c b "c" :a}
             props]
           (uix.lib/rest-props '[{:keys [a b c ::name]
                                  :strs [a b c "d"]
                                  :syms [a b c uix.core/str]
                                  :person/keys [name age]
                                  {:m 1 :n 2 :k 3} :opts
                                  :or {a 1 b 2 c 3}
                                  on-click :on-click
                                  :& props}]))))
  (testing "defui should return rest props"
    (uix.core/defui rest-component [{:keys [a b] :& props}]
      [props a b])
    (is (= [{:c 3} 1 2] (rest-component {:a 1 :b 2 :c 3})))
    (is (= [{} 1 2] (rest-component {:a 1 :b 2}))))
  (testing "fn should return rest props"
    (let [f (uix.core/fn rest-component [{:keys [a b] :& props}]
              [props a b])]
      (is (= [{:c 3} 1 2] (f {:a 1 :b 2 :c 3})))
      (is (= [{} 1 2] (f {:a 1 :b 2}))))))

(deftest test-props-check
  (testing "props check in defui"
    (uix.core/defui props-check-comp
      [props]
      {:props [map?]}
      props)
    (try
      (props-check-comp [])
      (catch AssertionError e
        (is (str/starts-with? (ex-message e) "Invalid argument"))))
    (try
      (props-check-comp {})
      (catch AssertionError e
        (is false))))
  (testing "props check in fn"
    (let [f (uix.core/fn
              [props]
              {:props [map?]}
              props)]
      (try
        (f [])
        (catch AssertionError e
          (is (str/starts-with? (ex-message e) "Invalid argument"))))
      (try
        (f {})
        (catch AssertionError e
          (is false))))))

(deftest test-spread-props
  (let [props {:width 100}]
    (is (= [:div {:on-click prn :width 100} "child"]
           (uix/$ :div {:on-click prn :& props} "child")))
    (is (= [identity {:on-click prn :width 100} "child"]
           (uix/$ identity {:on-click prn :& props} "child")))
    (is (= [identity {:on-click identity :width 100 :x 1} "child"]
           (uix/$ identity {:on-click prn :& [props {:on-click identity} {:x 1}]}
                  "child")))))
