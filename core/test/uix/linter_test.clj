(ns uix.linter-test
  (:require [clojure.test :refer [deftest is testing]]
            [shadow.cljs.devtools.cli]
            [uix.assertions]
            [uix.linter]
            [uix.linters]
            [uix.core]
            [clojure.string :as str]))

;; === Rules of Hooks ===

(defn lint-syms [syms expr-fn]
  (binding [uix.linter/*component-context* (atom {:errors []})]
    (let [exprs (map expr-fn syms)
          _ (uix.linter/lint-body! exprs {})
          errors (:errors @uix.linter/*component-context*)]
      [exprs (map :source-context errors)])))

(deftest test-lint-when
  (testing "hook in a branch should error"
    (let [[exprs errors] (lint-syms
                          (:when uix.linter/forms)
                          (fn [sym] (list sym 'x '(use-state 0))))]
      (is (= exprs errors))))
  (testing "hook in test expr should not error"
    (let [[exprs errors] (lint-syms
                          '[when when-not]
                          (fn [sym] (list sym '(use-state 0) 'x)))]
      (is (zero? (count errors)))))
  (testing "hook in test bindings should not error"
    (let [[exprs errors] (lint-syms
                          '[when-let when-some when-first]
                          (fn [sym] (list sym '[x (use-state 0)])))]
      (is (zero? (count errors))))))

(deftest test-lint-if
  (testing "hook in a branch should error"
    (let [[exprs errors] (lint-syms
                          (:if uix.linter/forms)
                          (fn [sym] (list sym 'test 'x '(use-state 0))))]
      (is (= exprs errors))))
  (testing "hook in test expr should not error"
    (let [[exprs errors] (lint-syms
                          '[if if-not]
                          (fn [sym] (list sym '(use-state 0) 'then 'else)))]
      (is (zero? (count errors)))))
  (testing "hook in test expr should not error"
    (let [[exprs errors] (lint-syms
                          '[if-let if-some]
                          (fn [sym] (list sym '[x (use-state 0)] 'then 'else)))]
      (is (zero? (count errors))))))

(deftest test-lint-logical
  (testing "hook at the first test position should not error"
    (let [[exprs errors] (lint-syms
                          (:logical uix.linter/forms)
                          (fn [sym] (list sym '(use-state 0) 'test)))]
      (is (zero? (count errors)))))
  (testing "hook at second+ test position should error"
    (let [[exprs errors] (lint-syms
                          (:logical uix.linter/forms)
                          (fn [sym] (list sym 'test '(use-state 0))))]
      (is (= exprs errors)))))

(deftest test-lint-cond
  (testing "hook at the first test position should not error"
    (let [[exprs errors] (lint-syms
                          (:cond uix.linter/forms)
                          (fn [sym] (list sym '(use-state 0) 'expr)))]
      (is (zero? (count errors)))))
  (testing "hook at second+ test position should error"
    (let [[exprs errors] (lint-syms
                          (:cond uix.linter/forms)
                          (fn [sym] (list sym 'test 'expr '(use-state 0) 'expr)))]
      (is (= exprs errors))))
  (testing "hook at expr position should error"
    (let [[exprs errors] (lint-syms
                          (:cond uix.linter/forms)
                          (fn [sym] (list sym 'test '(use-state 0))))]
      (is (= exprs errors)))))

(deftest test-lint-cond-threaded
  (testing "hook at expr position should not error"
    (let [[exprs errors] (lint-syms
                          (:cond-threaded uix.linter/forms)
                          (fn [sym] (list sym '(use-state 0) 'test 'form)))]
      (is (zero? (count errors)))))
  (testing "hook at test position should not error"
    (let [[exprs errors] (lint-syms
                          (:cond-threaded uix.linter/forms)
                          (fn [sym] (list sym 'expr '(use-state 0) 'form '(use-state 0) 'form)))]
      (is (zero? (count errors)))))
  (testing "hook at form position should error"
    (let [[exprs errors] (lint-syms
                          (:cond-threaded uix.linter/forms)
                          (fn [sym] (list sym 'expr 'test '(use-state 0))))]
      (is (= exprs errors)))))

(deftest test-lint-some-threaded
  (testing "hook at expr position should not error"
    (let [[exprs errors] (lint-syms
                          (:some-threaded uix.linter/forms)
                          (fn [sym] (list sym '(use-state 0) 'form 'form)))]
      (is (zero? (count errors)))))
  (testing "hook at the first form position should not error"
    (let [[exprs errors] (lint-syms
                          (:some-threaded uix.linter/forms)
                          (fn [sym] (list sym 'expr '(use-state 0) 'form)))]
      (is (zero? (count errors)))))
  (testing "hook at second+ form position should error"
    (let [[exprs errors] (lint-syms
                          (:some-threaded uix.linter/forms)
                          (fn [sym] (list sym 'expr 'form '(use-state 0))))]
      (is (= exprs errors)))))

(deftest test-lint-condp
  (testing "hook at predicate position should not error"
    (let [[exprs errors] (lint-syms
                          (:condp uix.linter/forms)
                          (fn [sym] (list sym '(use-state 0) 'expr 'test 'form)))]
      (is (zero? (count errors)))))
  (testing "hook at expr position should not error"
    (let [[exprs errors] (lint-syms
                          (:condp uix.linter/forms)
                          (fn [sym] (list sym 'pred '(use-state 0) 'test 'form)))]
      (is (zero? (count errors)))))
  (testing "hook at the first test position should not error"
    (let [[exprs errors] (lint-syms
                          (:condp uix.linter/forms)
                          (fn [sym] (list sym 'pred 'expr '(use-state 0) 'form)))]
      (is (zero? (count errors)))))
  (testing "hook at second+ test position should error"
    (let [[exprs errors] (lint-syms
                          (:condp uix.linter/forms)
                          (fn [sym] (list sym 'pred 'expr 'test 'form '(use-state 0) 'form)))]
      (is (= exprs errors))))
  (testing "hook at form position should error"
    (let [[exprs errors] (lint-syms
                          (:condp uix.linter/forms)
                          (fn [sym] (list sym 'pred 'expr 'test '(use-state 0))))]
      (is (= exprs errors)))))

(deftest test-lint-case
  (testing "hook at expr position should not error"
    (let [[exprs errors] (lint-syms
                          (:case uix.linter/forms)
                          (fn [sym] (list sym '(use-state 0) 'test 'form)))]
      (is (zero? (count errors)))))
  (testing "hook at the first test position should not error"
    (let [[exprs errors] (lint-syms
                          (:case uix.linter/forms)
                          (fn [sym] (list sym 'expr '(use-state 0) 'form)))]
      (is (zero? (count errors)))))
  (testing "hook at second+ test position should error"
    (let [[exprs errors] (lint-syms
                          (:case uix.linter/forms)
                          (fn [sym] (list sym 'expr 'test 'form '(use-state 0) 'form)))]
      (is (= exprs errors))))
  (testing "hook at form position should error"
    (let [[exprs errors] (lint-syms
                          (:case uix.linter/forms)
                          (fn [sym] (list sym 'expr 'test '(use-state 0))))]
      (is (= exprs errors)))))

(deftest test-lint-loop
  (testing "hook at body position should error"
    (let [[exprs errors] (lint-syms
                          (:loop uix.linter/forms)
                          (fn [sym] '(loop []
                                       (use-state 0))))]
      (is (= exprs errors))))
  (testing "hook at bindings position should not error"
    (let [[exprs errors] (lint-syms
                          (:loop uix.linter/forms)
                          (fn [sym] '(loop [[x & xs] (use-state 0)])))]
      (is (zero? (count errors))))))

(deftest test-lint-for-doseq
  (testing "hook at body position should error"
    (let [[exprs errors] (lint-syms
                          (:for uix.linter/forms)
                          (fn [sym] (list sym '[x xs]
                                          '(use-state 0))))]
      (is (= exprs errors))))
  (testing "hook at the first bindings position should not error"
    (let [[exprs errors] (lint-syms
                          (:for uix.linter/forms)
                          (fn [sym] (list sym '[x (use-state 0)])))]
      (is (zero? (count errors)))))
  (testing "hook at second+ bindings position should error"
    (let [[exprs errors] (lint-syms
                          (:for uix.linter/forms)
                          (fn [sym] (list sym '[x (use-state 0)
                                                y (use-state 0)])))]
      (is (= exprs errors))))
  (testing "hook at modifier position should error"
    (let [[exprs errors] (lint-syms
                          (:for uix.linter/forms)
                          (fn [sym] (list sym '[x (use-state 0)
                                                :let [y (use-state 0)]])))]
      (is (= exprs errors)))))

(deftest test-lint-iterfn
  (let [syms (:iter-fn uix.linter/forms)]
    (testing "hook at coll position should not error"
      (let [[exprs errors] (lint-syms syms (fn [sym] (list sym identity '(use-state 0))))]
        (is (zero? (count errors)))))
    (testing "hook at anonymous f position should error"
      (let [[exprs errors] (lint-syms syms (fn [sym] (list sym '(fn [x] (use-state x)) 'coll)))]
        (is (= exprs errors))))
    (testing "hook at anonymous f shorthand position should error"
      (let [[exprs errors] (lint-syms syms (fn [sym] (list sym '#(use-state %) 'coll)))]
        (is (= exprs errors))))))

;; === Exhaustive Deps ===

(deftest test-build
  (let [out-str (with-out-str (shadow.cljs.devtools.cli/-main "compile" "linter-test"))]

    (testing "should fail on missing dependency"
      (is (str/includes? out-str (str :uix.linter/missing-deps)))
      (is (str/includes? out-str "React Hook has missing dependencies: [x]")))

    (testing "should not fail on disabled missing deps check"
      (is (not (str/includes? out-str "React Hook has missing dependencies: [z]"))))

    (testing "should fail on unnecessary deps"
      (is (str/includes? out-str "`ref` is an unnecessary dependency because it's a ref that doesn't change"))
      (is (str/includes? out-str "`set-v` is an unnecessary dependency because it's a state updater function with a stable identity")))

    (testing "#72 should not fail with a local var shadowing the var in outer scope"
      (is (not (str/includes? out-str "React Hook has missing dependencies: [y]"))))

    (testing "should fail when a function reference passed into a hook"
      (is (str/includes? out-str (str :uix.linter/inline-function)))
      (is (str/includes? out-str "React Hook received a function whose dependencies are unknown.")))

    (testing "should fail on deps being a JS array"
      (is (str/includes? out-str (str :uix.linter/deps-array-literal))))

    (testing "should fail on deps being something else, rather than vector"
      (is (str/includes? out-str (str :uix.linter/deps-coll-literal))))

    (testing "should fail on deps vector including a literal of a primitive type"
      (is (str/includes? out-str (str :uix.linter/literal-value-in-deps)))
      (is (str/includes? out-str "[:kw, 1, str, , true]")))

    (testing "should fail on set-state in an effect hook w/o deps"
      (is (str/includes? out-str (str :uix.linter/unsafe-set-state)))
      (is (str/includes? out-str "React Hook contains a call to `set-value`")))

    (testing "should fail on hook call in a branch"
      (is (str/includes? out-str (str :uix.linter/hook-in-branch)))
      (is (str/includes? out-str "React Hook (uix.core/use-effect (fn [])) is called conditionally.")))

    (testing "should fail on hook call in loop"
      (is (str/includes? out-str (str :uix.linter/hook-in-loop)))
      (is (str/includes? out-str "React Hook (uix.core/use-effect (fn [])) may be executed more than once.")))

    (testing "should fail on hook call in nested branch or loop"
      (is (str/includes? out-str (str :uix.linter/hook-in-branch)))
      (is (str/includes? out-str (str :uix.linter/hook-in-loop)))
      (is (str/includes? out-str "React Hook (uix.core/use-effect (fn [] \"nested condition\")) is called conditionally."))
      (is (str/includes? out-str "React Hook (uix.core/use-effect (fn [] \"nested loop\")) may be executed more than once.")))

    (testing "should fail on missing key in a loop"
      (is (str/includes? out-str (str :uix.linter/missing-key)))
      (is (str/includes? out-str "UIx element is missing :key attribute, which is required"))
      (is (str/includes? out-str "($ :div.test-missing-key {} x)"))
      (is (str/includes? out-str "($ :div.test-missing-key ($ x))"))
      (is (str/includes? out-str "($ :div.test-missing-key-nested ($ x))")))

    (testing "plugin linters should work"
      (is (str/includes? out-str (str :component/kebab-case-name)))
      (is (str/includes? out-str (str :hooks/too-many-lines)))
      (is (str/includes? out-str (str :element/no-inline-styles)))
      (is (str/includes? out-str "Component name should be in kebab case"))
      (is (str/includes? out-str "React hook is too large to be declared directly in component's body"))
      (is (str/includes? out-str "Inline styles are not allowed, put them into a CSS file instead")))

    (testing "should dedupe missing deps"
      (is (not (str/includes? out-str "[dsym dsym dsym]"))))

    (testing "#106, should report when missing dep is shadowed JS global"
      (is (str/includes? out-str "React Hook has missing dependencies: [document]")))

    (testing "#100, should report when a key is missing in thread-last macro and vice versa"
      (is (str/includes? out-str "UIx element is missing :key attribute, which is required"))
      (is (str/includes? out-str "(->> {:not-key (str \"bar-\" i)}"))
      (is (not (str/includes? out-str "(->> {:key (str \"foo-\" i)}"))))

    (testing "should lint uix.core/defhook"
      (is (str/includes? out-str (str :uix.linter/missing-deps)))
      (is (str/includes? out-str "React Hook has missing dependencies: [hook-hook]")))

    (testing "should fail on non-ref spread"
      (is (str/includes? out-str (str :uix.linter/element-non-ref-spread)))
      (is (str/includes? out-str "Spread syntax can be used only with references to props values. Spreading map literal doesn't make sense, inline it into props map instead.")))

    (testing "should fail on unnecessary spread"
      (is (str/includes? out-str (str :uix.linter/element-unnecessary-spread)))
      (is (str/includes? out-str "Spreading a single props map into empty map literal doesn't make sense, instead pass props symbol itself.")))

    (testing "should fail on missing required props"
      (is (str/includes? out-str (str :uix.linter/missing-props-keys)))
      (is (str/includes? out-str "Required keys are missing in props: :on-click, :button/title")))

    (testing "should fail on ref interop read/write"
      (is (str/includes? out-str (str :uix.linter/interop-ref-read)))
      (is (str/includes? out-str (str :uix.linter/interop-ref-write)))
      (is (str/includes? out-str "use-ref hook in UIx returns Atom-like ref, use @ instead of .-current to access its value."))
      (is (str/includes? out-str "use-ref hook in UIx returns Atom-like ref, use reset! instead of set! to update its value.")))

    (testing "should fail on effect event passed as prop"
      (is (str/includes? out-str (str :uix.linter/effect-event-as-prop)))
      (is (str/includes? out-str "useEffectEvent value should not be passed as a prop")))

    (testing "should fail on effect event leaked from hook"
      (is (str/includes? out-str (str :uix.linter/effect-event-leaked-from-hook)))
      (is (str/includes? out-str "useEffectEvent value should not be returned from a custom hook")))))

;; === Subscribe call in JVM ===

(defn subscribe [v]
  {:value v})

(def some-value "x")

(deftest test-subscribe-function-call
  (testing "subscribe function can be used in clj"
    (is (not-thrown?
         (macroexpand-1 '(uix.core/defui subscribe-component [props]
                           (let [current-value (subscribe some-value)]
                             (:value current-value))))))))
