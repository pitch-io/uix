(ns uix.aot-test
  (:require [cljs.env :as env]
            [clojure.test :refer :all]
            [uix.compiler.aot :as aot]
            [uix.compiler.attributes :as attrs]
            [cljs.analyzer :as ana]))

(deftest test-parse-tag
  (is (= (attrs/parse-tag (name :div))
         ["div" nil nil false]))
  (is (= (attrs/parse-tag (name :div#id))
         (attrs/parse-tag (name :#id))
         ["div" "id" nil false]))
  (is (= (attrs/parse-tag (name :div.class))
         (attrs/parse-tag (name :.class))
         ["div" nil "class" false]))
  (is (= (attrs/parse-tag (name :div#id.class))
         (attrs/parse-tag (name :#id.class))
         ["div" "id" "class" false]))
  (is (= (attrs/parse-tag (name :div#id.class.klass))
         (attrs/parse-tag (name :#id.class.klass))
         ["div" "id" "class klass" false]))
  (is (= (attrs/parse-tag (name :custom-tag))
         ["custom-tag" nil nil true])))

(deftest test-class-names
  (is (= (attrs/compile-config-kv :class nil) nil))
  (testing "Named types"
    (is (= (attrs/compile-config-kv :class "a") "a"))))

(deftest convert-props-test
  (is (= {:className "a" :htmlFor "x"}
         (attrs/compile-attrs {:class "a" :for "x"})))
  (is (= '{:onClick identity, :title (uix.compiler.attributes/keyword->string x), :children [1 2]}
         (attrs/compile-attrs '{:on-click identity :title x :children [1 2]})))
  (is (= '{:style {:pointerEvents (uix.compiler.attributes/keyword->string (when x :none))}}
         (attrs/compile-attrs '{:style {:pointer-events (when x :none)}}))))

(deftest test-compile-html
  (binding [aot/*memo-disabled?* true]
    (with-redefs [uix.lib/cljs-env? (fn [_] true)
                  ana/resolve-var (fn [_ _] nil)
                  env/*compiler* (atom {})]
      (is (= (aot/compile-element [:h1] nil)
             '(js* "~{}(~{}, ...~{}, ...~{})" uix.compiler.alpha/create-element* "h1" (cljs.core/array nil) (cljs.core/array))))
      (is (= (aot/compile-element '[x {} 1 2] nil)
             '(uix.compiler.alpha/component-element x (cljs.core/array {}) (cljs.core/array 1 2))))
      (is (= (aot/compile-element '[x {:x 1 :ref 2} 1 2] nil)
             '(uix.compiler.alpha/component-element x (cljs.core/array {:x 1 :ref 2}) (cljs.core/array 1 2)))))))

(defmacro stub-120 [x]
  (aot/form->element-type x))

(deftest test-120
  (is (= :component (stub-120 (inc 1))))
  (is (= :component (stub-120 `(inc 1)))))

(deftest test-114
  (testing "#114, should report when an incorrect value is provided as element type"
    (is (thrown-with-msg? AssertionError #"Incorrect element type. UIx elements can be one of the following types"
                          (aot/compile-element* nil nil)))
    (is (thrown-with-msg? AssertionError #"Incorrect element type. UIx elements can be one of the following types"
                          (aot/compile-element* [{:hello "world"}] nil)))))

(deftest test-rewrite-forms
  (is (= (aot/rewrite-forms
           '(when true
              ($ :div
                 (for [x [1 2 3]]
                   ($ :span x)))))
         [[]
          '(when true
             ($ :div (clojure.core/map
                       (clojure.core/fn [x] ($ :span x))
                       [1 2 3])))])))
