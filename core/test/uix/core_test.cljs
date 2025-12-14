(ns uix.core-test
  (:require [react :as r]
            ["@testing-library/react" :as rtl]
            [cljs.spec.alpha :as s]
            [clojure.test :refer [deftest is async testing run-tests]]
            [uix.core :refer [defui $ defcontext]]
            [uix.lib]
            [uix.test-utils :as t]
            [uix.compiler.attributes :as attrs]
            [uix.benchmark.uix :refer [row-compiled]]
            [uix.dom.server :as server]
            [uix.compiler.aot :as aot]
            [clojure.string :as str]))

(deftest test-use-ref
  (uix.core/defui test-use-ref-comp [_]
    (let [ref1 (uix.core/use-ref)
          ref2 (uix.core/use-ref 0)]
      (is (nil? @ref1))
      (is (nil? @ref1))
      (reset! ref1 :x)
      (is (= :x @ref1))

      (is (= 0 @ref2))
      (is (= 0 @ref2))
      (reset! ref2 1)
      (is (= 1 @ref2))
      (swap! ref2 inc)
      (is (= 2 @ref2))
      (swap! ref2 + 2)
      (is (= 4 @ref2))
      "x"))
  (t/as-string ($ test-use-ref-comp)))

(deftest test-memoize
  (testing "manual memo"
    (uix.core/defui test-memoize-comp [{:keys [x]}]
      (is (= 1 x))
      ($ :h1 x))
    (let [f (uix.core/memo test-memoize-comp)]
      (is (t/react-element-of-type? f "react.memo"))
      (is (= "<h1>1</h1>" (t/as-string ($ f {:x 1}))))))
  (testing "^:memo"
    (uix.core/defui ^:memo test-memoize-meta-comp [{:keys [x]}]
      (is (= 1 x))
      ($ :h1 x))
    (is (t/react-element-of-type? test-memoize-meta-comp "react.memo"))
    (is (= "<h1>1</h1>" (t/as-string ($ test-memoize-meta-comp {:x 1}))))))

(deftest test-auto-memo-cache
  (testing "auto-memoized component caches values across re-renders"
    (let [compute-count (atom 0)
          render-count (atom 0)
          state-atom (atom {:value 1 :other "a"})]
      ;; Component with auto-memoization enabled (default)
      (defui auto-memo-test-comp [{:keys [value other]}]
        (swap! render-count inc)
        (let [computed (do (swap! compute-count inc)
                          (str "computed-" value))]
          ($ :div computed)))
      ;; Wrapper that triggers re-renders
      (defui auto-memo-wrapper []
        (let [[state set-state] (uix.core/use-state @state-atom)]
          (uix.core/use-effect
            (fn []
              (add-watch state-atom :test
                         (fn [_ _ _ new-val]
                           (set-state new-val)))
              #(remove-watch state-atom :test))
            [])
          ($ auto-memo-test-comp state)))
      
      (t/with-react-root
        ($ auto-memo-wrapper)
        (fn [_node]
          (let [first-compute @compute-count]
            ;; Change `other` but not `value` - should use cached computed
            (react/act #(reset! state-atom {:value 1 :other "b"}))
            ;; With auto-memo, `computed` depends only on `value`
            ;; so changing `other` shouldn't recompute
            (is (= first-compute @compute-count) 
                "Computed value should be cached when deps unchanged")
            
            ;; Now change `value` - should recompute
            (react/act #(reset! state-atom {:value 2 :other "b"}))
            (is (> @compute-count first-compute)
                "Computed value should recompute when deps change"))))))
  
  (testing "cache uses slot-id based keys for conditional branches"
    (let [branch-a-count (atom 0)
          branch-b-count (atom 0)
          state-atom (atom {:condition true :value 1})]
      (defui conditional-memo-comp [{:keys [condition value]}]
        (if condition
          (let [a (do (swap! branch-a-count inc) (str "a-" value))]
            ($ :div a))
          (let [b (do (swap! branch-b-count inc) (str "b-" value))]
            ($ :span b))))
      
      (defui conditional-wrapper []
        (let [[state set-state] (uix.core/use-state @state-atom)]
          (uix.core/use-effect
            (fn []
              (add-watch state-atom :test2
                         (fn [_ _ _ new-val]
                           (set-state new-val)))
              #(remove-watch state-atom :test2))
            [])
          ($ conditional-memo-comp state)))
      
      (t/with-react-root
        ($ conditional-wrapper)
        (fn [_node]
          (is (= 1 @branch-a-count) "Initial render branch A")
          (is (= 0 @branch-b-count) "Branch B not rendered yet")
          
          ;; Switch to branch B
          (react/act #(reset! state-atom {:condition false :value 1}))
          (is (= 1 @branch-a-count) "Branch A unchanged")
          (is (= 1 @branch-b-count) "Branch B computed")
          
          ;; Switch back to branch A with same value
          (react/act #(reset! state-atom {:condition true :value 1}))
          ;; With slot-id caching, branch A should use cached value
          (is (= 1 @branch-a-count) "Branch A should use cached value")))))
  
  (testing "destructuring bindings are cached"
    (let [transform-count (atom 0)
          state-atom (atom {:data {:x 1 :y 2} :other "a"})]
      (defui destructuring-memo-comp [{:keys [data other]}]
        (let [{:keys [x y]} (do (swap! transform-count inc) data)]
          ($ :div (str x "-" y "-" other))))
      
      (defui destructuring-wrapper []
        (let [[state set-state] (uix.core/use-state @state-atom)]
          (uix.core/use-effect
            (fn []
              (add-watch state-atom :test3
                         (fn [_ _ _ new-val]
                           (set-state new-val)))
              #(remove-watch state-atom :test3))
            [])
          ($ destructuring-memo-comp state)))
      
      (t/with-react-root
        ($ destructuring-wrapper)
        (fn [_node]
          (let [first-count @transform-count]
            ;; Change `other` but not `data` - should use cached destructuring
            (react/act #(reset! state-atom {:data {:x 1 :y 2} :other "b"}))
            ;; With auto-memo, the destructuring depends on `data`
            ;; Since data is the same (value equality), should be cached
            (is (= first-count @transform-count)
                "Destructuring should be cached when data unchanged")
            
            ;; Now change `data` - should recompute
            (react/act #(reset! state-atom {:data {:x 2 :y 3} :other "b"}))
            (is (> @transform-count first-count)
                "Destructuring should recompute when data changes"))))))
  
  (testing "bindings after hooks are cached with hook result as dep"
    (let [transform-count (atom 0)
          state-atom (atom {:id 1 :other "a"})]
      ;; Component where hook result is used in subsequent computation
      (defui hook-then-compute-comp [{:keys [id other]}]
        (let [;; Hook call - not cached
              [counter _set-counter] (uix.core/use-state 0)
              ;; This should be cached with [counter id] as deps
              processed (do (swap! transform-count inc)
                           (str "processed-" counter "-" id))]
          ($ :div processed "-" other)))
      
      (defui hook-then-compute-wrapper []
        (let [[state set-state] (uix.core/use-state @state-atom)]
          (uix.core/use-effect
            (fn []
              (add-watch state-atom :test-hook
                         (fn [_ _ _ new-val]
                           (set-state new-val)))
              #(remove-watch state-atom :test-hook))
            [])
          ($ hook-then-compute-comp state)))
      
      (t/with-react-root
        ($ hook-then-compute-wrapper)
        (fn [_node]
          (let [first-count @transform-count]
            ;; Change `other` but not `id` - processed should be cached
            ;; because counter (from hook) and id are unchanged
            (react/act #(reset! state-atom {:id 1 :other "b"}))
            (is (= first-count @transform-count)
                "Computation after hook should be cached when deps unchanged")
            
            ;; Change `id` - should recompute
            (react/act #(reset! state-atom {:id 2 :other "b"}))
            (is (> @transform-count first-count)
                "Computation should recompute when id changes"))))))
  
  (testing "multiple hooks followed by computation"
    (let [transform-count (atom 0)
          state-atom (atom {:multiplier 2})]
      ;; Component with multiple hooks before computation
      (defui multi-hook-comp [{:keys [multiplier]}]
        (let [;; Multiple hook calls
              [count1 _] (uix.core/use-state 10)
              [count2 _] (uix.core/use-state 20)
              ref (uix.core/use-ref nil)
              ;; Computation using hook results
              total (do (swap! transform-count inc)
                       (* (+ count1 count2) multiplier))]
          ($ :div (str total))))
      
      (defui multi-hook-wrapper []
        (let [[state set-state] (uix.core/use-state @state-atom)]
          (uix.core/use-effect
            (fn []
              (add-watch state-atom :test-multi-hook
                         (fn [_ _ _ new-val]
                           (set-state new-val)))
              #(remove-watch state-atom :test-multi-hook))
            [])
          ($ multi-hook-comp state)))
      
      (t/with-react-root
        ($ multi-hook-wrapper)
        (fn [_node]
          (let [first-count @transform-count]
            ;; Re-render with same multiplier - total should be cached
            (react/act #(reset! state-atom {:multiplier 2}))
            (is (= first-count @transform-count)
                "Computation after multiple hooks should be cached")
            
            ;; Change multiplier - should recompute
            (react/act #(reset! state-atom {:multiplier 3}))
            (is (> @transform-count first-count)
                "Computation should recompute when prop changes"))))))
  
  (testing "inline function callbacks in props are cached"
    (let [props-creation-count (atom 0)
          state-atom (atom {:handler-id 1 :other "a"})]
      ;; Child component that receives callback - memoized to detect prop changes
      (defui ^{:memo false} callback-child-inner [{:keys [on-click label]}]
        (swap! props-creation-count inc)
        ($ :button {:on-click on-click} label))
      
      (def callback-child-memo (uix.core/memo callback-child-inner))
      
      ;; Parent with inline callback - the callback depends on handler-id
      (defui callback-parent-2 [{:keys [handler-id other]}]
        ($ callback-child-memo
           {:on-click (fn [_e] (js/console.log "clicked" handler-id))
            :label "Button"}))
      
      (defui callback-wrapper-2 []
        (let [[state set-state] (uix.core/use-state @state-atom)]
          (uix.core/use-effect
            (fn []
              (add-watch state-atom :test5
                         (fn [_ _ _ new-val]
                           (set-state new-val)))
              #(remove-watch state-atom :test5))
            [])
          ($ callback-parent-2 state)))
      
      ;; The props {:on-click (fn ...) :label ...} should be memoized based on deps
      ;; The inline fn captures handler-id, so deps should be [handler-id]
      (t/with-react-root
        ($ callback-wrapper-2)
        (fn [_node]
          (let [initial-count @props-creation-count]
            ;; Change `other` but not `handler-id` - callback deps unchanged
            ;; The props map should be cached, child shouldn't re-render
            (react/act #(reset! state-atom {:handler-id 1 :other "b"}))
            ;; Since callback-child-memo is memo'd and props are cached,
            ;; child should NOT re-render when only `other` changes
            (is (= initial-count @props-creation-count)
                "Memoized child should not re-render when callback deps unchanged")
            
            ;; Now change `handler-id` - callback deps change, should re-render
            (react/act #(reset! state-atom {:handler-id 2 :other "b"}))
            (is (> @props-creation-count initial-count)
                "Memoized child should re-render when callback deps change"))))))
  
  ;; ============ Real-world pattern tests ============
  
  (testing "for loop with inline callbacks (list rendering)"
    ;; NOTE: This test verifies that when props to a list DON'T change,
    ;; list items don't re-render. The callback is passed from parent
    ;; and must be stable for memo to work.
    (let [item-render-count (atom 0)
          state-atom (atom {:items [{:id 1 :name "a"} {:id 2 :name "b"}] 
                           :other "x"})]
      ;; Simulates a typical list with click handlers
      (defui list-item-for-test [{:keys [item on-select]}]
        (swap! item-render-count inc)
        ($ :li {:on-click #(on-select (:id item))} (:name item)))
      
      (def list-item-for-test-memo (uix.core/memo list-item-for-test))
      
      ;; This component receives on-select as a prop, doesn't create it
      (defui items-list-for-test [{:keys [items on-select]}]
        ($ :ul
           (for [{:keys [id] :as item} items]
             ($ list-item-for-test-memo {:key id 
                               :item item
                               :on-select on-select}))))
      
      ;; Parent creates the callback and it should be cached when deps don't change
      (defui items-wrapper-for-test [{:keys [items other]}]
        (let [[selected set-selected] (uix.core/use-state nil)
              ;; The callback depends on set-selected which is stable
              on-select (fn [id] (set-selected id))]
          ;; on-select is in a let binding, should be cached
          ;; items-list will get same on-select reference
          ($ items-list-for-test {:items items :on-select on-select})))
      
      (defui items-outer-wrapper []
        (let [[state set-state] (uix.core/use-state @state-atom)]
          (uix.core/use-effect
            (fn []
              (add-watch state-atom :test-items
                         (fn [_ _ _ new-val] (set-state new-val)))
              #(remove-watch state-atom :test-items))
            [])
          ($ items-wrapper-for-test state)))
      
      (t/with-react-root
        ($ items-outer-wrapper)
        (fn [_node]
          (let [initial-count @item-render-count]
            ;; Change `other` but not `items` - items should be cached
            ;; because on-select is stable (only depends on set-selected)
            (react/act #(swap! state-atom assoc :other "y"))
            (is (= initial-count @item-render-count)
                "List items should not re-render when unrelated prop changes"))))))
  
  (testing "when conditional with computation inside"
    (let [compute-count (atom 0)
          state-atom (atom {:show true :value 10 :other "a"})]
      (defui conditional-comp [{:keys [show value other]}]
        (when show
          (let [computed (do (swap! compute-count inc) (* value 2))]
            ($ :div (str computed)))))
      
      (defui conditional-comp-wrapper []
        (let [[state set-state] (uix.core/use-state @state-atom)]
          (uix.core/use-effect
            (fn []
              (add-watch state-atom :test-when
                         (fn [_ _ _ new-val] (set-state new-val)))
              #(remove-watch state-atom :test-when))
            [])
          ($ conditional-comp state)))
      
      (t/with-react-root
        ($ conditional-comp-wrapper)
        (fn [_node]
          (let [initial-count @compute-count]
            ;; Change `other` - computation should be cached
            (react/act #(swap! state-atom assoc :other "b"))
            (is (= initial-count @compute-count)
                "Computation inside when should be cached")
            
            ;; Change `value` - should recompute
            (react/act #(swap! state-atom assoc :value 20))
            (is (> @compute-count initial-count)
                "Computation should recompute when value changes"))))))
  
  (testing "if-else branches with different computations"
    (let [true-count (atom 0)
          false-count (atom 0)
          state-atom (atom {:active true :label "hello" :other "x"})]
      (defui if-else-comp [{:keys [active label other]}]
        (if active
          (let [upper (do (swap! true-count inc) (str/upper-case label))]
            ($ :strong upper))
          (let [lower (do (swap! false-count inc) (str/lower-case label))]
            ($ :em lower))))
      
      (defui if-else-wrapper []
        (let [[state set-state] (uix.core/use-state @state-atom)]
          (uix.core/use-effect
            (fn []
              (add-watch state-atom :test-if
                         (fn [_ _ _ new-val] (set-state new-val)))
              #(remove-watch state-atom :test-if))
            [])
          ($ if-else-comp state)))
      
      (t/with-react-root
        ($ if-else-wrapper)
        (fn [_node]
          (is (= 1 @true-count) "Initial true branch")
          (let [initial-true @true-count]
            ;; Change `other` - same branch, should be cached
            (react/act #(swap! state-atom assoc :other "y"))
            (is (= initial-true @true-count)
                "Same branch should use cached value"))))))
  
  (testing "nested let bindings"
    (let [outer-count (atom 0)
          inner-count (atom 0)
          state-atom (atom {:a 1 :b 2 :c 3 :other "x"})]
      (defui nested-let-comp [{:keys [a b c other]}]
        (let [outer (do (swap! outer-count inc) (+ a b))]
          (let [inner (do (swap! inner-count inc) (* outer c))]
            ($ :div (str inner)))))
      
      (defui nested-let-wrapper []
        (let [[state set-state] (uix.core/use-state @state-atom)]
          (uix.core/use-effect
            (fn []
              (add-watch state-atom :test-nested
                         (fn [_ _ _ new-val] (set-state new-val)))
              #(remove-watch state-atom :test-nested))
            [])
          ($ nested-let-comp state)))
      
      (t/with-react-root
        ($ nested-let-wrapper)
        (fn [_node]
          (let [initial-outer @outer-count
                initial-inner @inner-count]
            ;; Change `other` - both should be cached
            (react/act #(swap! state-atom assoc :other "y"))
            (is (= initial-outer @outer-count) "Outer let should be cached")
            (is (= initial-inner @inner-count) "Inner let should be cached")
            
            ;; Change `a` - outer changes, inner depends on outer
            (react/act #(swap! state-atom assoc :a 10))
            (is (> @outer-count initial-outer) "Outer should recompute")
            (is (> @inner-count initial-inner) "Inner should recompute (depends on outer)"))))))
  
  (testing "props with :or defaults"
    (let [compute-count (atom 0)
          state-atom (atom {:value nil :other "a"})]
      (defui defaults-comp [{:keys [value other] :or {value 0}}]
        (let [computed (do (swap! compute-count inc) (* value 2))]
          ($ :div (str computed))))
      
      (defui defaults-wrapper []
        (let [[state set-state] (uix.core/use-state @state-atom)]
          (uix.core/use-effect
            (fn []
              (add-watch state-atom :test-defaults
                         (fn [_ _ _ new-val] (set-state new-val)))
              #(remove-watch state-atom :test-defaults))
            [])
          ($ defaults-comp state)))
      
      (t/with-react-root
        ($ defaults-wrapper)
        (fn [_node]
          (let [initial-count @compute-count]
            ;; Change `other` - computation should be cached
            (react/act #(swap! state-atom assoc :other "b"))
            (is (= initial-count @compute-count)
                "Computation with default prop should be cached"))))))
  
  (testing "form submit handler pattern"
    (let [handler-create-count (atom 0)
          state-atom (atom {:form-data {:name "test"} :other "a"})]
      ;; Memoized child to detect prop changes
      (defui ^{:memo false} form-inner [{:keys [on-submit]}]
        (swap! handler-create-count inc)
        ($ :form {:on-submit on-submit}
           ($ :button "Submit")))
      
      (def form-memo (uix.core/memo form-inner))
      
      (defui form-comp [{:keys [form-data other]}]
        ($ form-memo
           {:on-submit (fn [e]
                        (.preventDefault e)
                        (js/console.log "submit" form-data))}))
      
      (defui form-wrapper []
        (let [[state set-state] (uix.core/use-state @state-atom)]
          (uix.core/use-effect
            (fn []
              (add-watch state-atom :test-form
                         (fn [_ _ _ new-val] (set-state new-val)))
              #(remove-watch state-atom :test-form))
            [])
          ($ form-comp state)))
      
      (t/with-react-root
        ($ form-wrapper)
        (fn [_node]
          (let [initial-count @handler-create-count]
            ;; Change `other` - handler should be cached (depends only on form-data)
            (react/act #(swap! state-atom assoc :other "b"))
            (is (= initial-count @handler-create-count)
                "Form handler should be cached when form-data unchanged")
            
            ;; Change `form-data` - handler should update
            (react/act #(swap! state-atom assoc :form-data {:name "new"}))
            (is (> @handler-create-count initial-count)
                "Form handler should update when form-data changes"))))))
  
  (testing "derived state from hook and props"
    (let [derived-count (atom 0)
          state-atom (atom {:multiplier 2 :other "a"})]
      (defui derived-state-comp [{:keys [multiplier other]}]
        (let [[base _set-base] (uix.core/use-state 10)
              ;; Common pattern: derive state from hook + props
              derived (do (swap! derived-count inc)
                         (* base multiplier))]
          ($ :div (str derived "-" other))))
      
      (defui derived-state-wrapper []
        (let [[state set-state] (uix.core/use-state @state-atom)]
          (uix.core/use-effect
            (fn []
              (add-watch state-atom :test-derived
                         (fn [_ _ _ new-val] (set-state new-val)))
              #(remove-watch state-atom :test-derived))
            [])
          ($ derived-state-comp state)))
      
      (t/with-react-root
        ($ derived-state-wrapper)
        (fn [_node]
          (let [initial-count @derived-count]
            ;; Change `other` - derived should be cached
            (react/act #(swap! state-atom assoc :other "b"))
            (is (= initial-count @derived-count)
                "Derived state should be cached when deps unchanged")
            
            ;; Change `multiplier` - derived should recompute
            (react/act #(swap! state-atom assoc :multiplier 3))
            (is (> @derived-count initial-count)
                "Derived state should recompute when multiplier changes"))))))
  
  (testing "threading macros (->, ->>)"
    (let [transform-count (atom 0)
          state-atom (atom {:data [1 2 3 4 5] :other "a"})]
      (defui threading-comp [{:keys [data other]}]
        (let [result (do (swap! transform-count inc)
                        (->> data
                             (filter odd?)
                             (map #(* % 2))
                             (reduce +)))]
          ($ :div (str result))))
      
      (defui threading-wrapper []
        (let [[state set-state] (uix.core/use-state @state-atom)]
          (uix.core/use-effect
            (fn []
              (add-watch state-atom :test-threading
                         (fn [_ _ _ new-val] (set-state new-val)))
              #(remove-watch state-atom :test-threading))
            [])
          ($ threading-comp state)))
      
      (t/with-react-root
        ($ threading-wrapper)
        (fn [_node]
          (let [initial-count @transform-count]
            ;; Change `other` - transform should be cached
            (react/act #(swap! state-atom assoc :other "b"))
            (is (= initial-count @transform-count)
                "Threading expression should be cached")
            
            ;; Change `data` - transform should recompute
            (react/act #(swap! state-atom assoc :data [1 2 3]))
            (is (> @transform-count initial-count)
                "Threading expression should recompute when data changes"))))))
  
  (testing "context hook pattern"
    (let [compute-count (atom 0)
          state-atom (atom {:config {:theme "dark"} :user "test" :other "a"})]
      ;; Simulates pattern: use context + compute from it
      (defui context-consumer [{:keys [config user other]}]
        (let [;; Pretend config comes from context (we simulate it as prop)
              {:keys [theme]} config
              ;; Derived value from context + props
              styled-name (do (swap! compute-count inc)
                             (str theme "-" user))]
          ($ :div styled-name)))
      
      (defui context-wrapper []
        (let [[state set-state] (uix.core/use-state @state-atom)]
          (uix.core/use-effect
            (fn []
              (add-watch state-atom :test-ctx
                         (fn [_ _ _ new-val] (set-state new-val)))
              #(remove-watch state-atom :test-ctx))
            [])
          ($ context-consumer state)))
      
      (t/with-react-root
        ($ context-wrapper)
        (fn [_node]
          (let [initial-count @compute-count]
            ;; Change `other` - styled-name should be cached
            (react/act #(swap! state-atom assoc :other "b"))
            (is (= initial-count @compute-count)
                "Derived value from context should be cached")
            
            ;; Change `user` - should recompute
            (react/act #(swap! state-atom assoc :user "new-user"))
            (is (> @compute-count initial-count)
                "Derived value should recompute when user changes"))))))
  
  (testing "sequential state updates"
    (let [compute-count (atom 0)
          state-atom (atom {:count 0 :label "item" :other "x"})]
      ;; Pattern: multiple derived values that build on each other
      (defui sequential-comp [{:keys [count label other]}]
        (let [doubled (do (swap! compute-count inc) (* count 2))
              message (str label ": " doubled)]
          ($ :div message)))
      
      (defui sequential-wrapper []
        (let [[state set-state] (uix.core/use-state @state-atom)]
          (uix.core/use-effect
            (fn []
              (add-watch state-atom :test-seq
                         (fn [_ _ _ new-val] (set-state new-val)))
              #(remove-watch state-atom :test-seq))
            [])
          ($ sequential-comp state)))
      
      (t/with-react-root
        ($ sequential-wrapper)
        (fn [_node]
          (let [initial-count @compute-count]
            ;; Change only `other` - doubled should be cached
            (react/act #(swap! state-atom assoc :other "y"))
            (is (= initial-count @compute-count)
                "First derived value should be cached")))))))

(deftest test-html
  (is (t/react-element-of-type? ($ :h1 1) "react.transitional.element")))

(deftest test-defui
  (defui h1 [{:keys [children]}]
    ($ :h1 {} children))
  (is (= (t/as-string ($ h1 {} 1)) "<h1>1</h1>")))

(deftest test-lazy
  (async done
         (let [expected-value :x
               lazy-f (uix.core/lazy (fn [] (js/Promise. (fn [res] (js/setTimeout #(res expected-value) 100)))))]
           (is (.-uix-component? lazy-f))
           (try
             (._init lazy-f (.-_payload lazy-f))
             (catch :default e
               (is (instance? js/Promise e))
               (.then e (fn [v]
                          (is (= expected-value (.-default ^js v)))
                          (done))))))))

(deftest test-create-class
  (let [actual (atom {:constructor {:this nil :props nil}
                      :getInitialState {:this nil}
                      :render {:state nil :props nil}
                      :componentDidMount false
                      :componentWillUnmount false})
        component (uix.core/create-class
                   {:displayName "test-comp"
                    :constructor (fn [this props]
                                   (swap! actual assoc :constructor {:this this :props props}))
                    :getInitialState (fn [this]
                                       (swap! actual assoc :getInitialState {:this this})
                                       #js {:x 1})
                    :componentDidMount #(swap! actual assoc :componentDidMount true)
                    :componentWillUnmount #(swap! actual assoc :componentWillUnmount true)
                    :render (fn []
                              (this-as ^react/Component this
                                       (swap! actual assoc :render {:state (.-state this) :props (.-props this)})
                                       "Hello!"))})]
    (t/with-react-root
      ($ component {:x 1})
      (fn [node]
        (is (instance? component (-> @actual :constructor :this)))
        (is (-> @actual :constructor :props .-argv (= {:x 1})))
        (is (instance? component (-> @actual :getInitialState :this)))
        (is (-> @actual :render :props .-argv (= {:x 1})))
        (is (-> @actual :render :state .-x (= 1)))
        (is (:componentDidMount @actual))
        (is (= "Hello!" (.-textContent node))))
      #(is (:componentWillUnmount @actual)))))

(deftest test-convert-props
  (testing "shallow conversion"
    (let [obj (attrs/convert-props
               {:x 1
                :y :keyword
                :f identity
                :style {:border :red
                        :margin-top "12px"}
                :class :class
                :for :for
                :charset :charset
                :hello-world "yo"
                "yo-yo" "string"
                :plugins [1 2 3]
                :data-test-id "hello"
                :aria-role "button"}
               #js []
               true)]
      (is (= 1 (.-x obj)))
      (is (= "keyword" (.-y obj)))
      (is (= identity (.-f obj)))
      (is (= "red" (.. obj -style -border)))
      (is (= "12px" (.. obj -style -marginTop)))
      (is (= "class" (.-className obj)))
      (is (= "for" (.-htmlFor obj)))
      (is (= "charset" (.-charSet obj)))
      (is (= "yo" (.-helloWorld obj)))
      (is (= [1 2 3] (.-plugins obj)))
      (is (= "string" (aget obj "yo-yo")))
      (is (= "hello" (aget obj "data-test-id")))
      (is (= "button" (aget obj "aria-role")))
      (is (= "a b c" (.-className (attrs/convert-props {:class [:a :b "c"]} #js [] true)))))))

(deftest test-as-react
  (uix.core/defui test-c [props]
    ($ :h1 (:text props)))
  (let [test-c-react (uix.core/as-react #($ test-c %))
        el (test-c-react #js {:text "TEXT"})
        props (.. ^js el -props -argv)]
    (is (= (.-type el) test-c))
    (is (= (:text props) "TEXT"))))

(defui test-source-component []
  "HELLO")

(deftest test-source
  (is (= (uix.core/source test-source-component)
         "(defui test-source-component []\n  \"HELLO\")"))
  (is (= (uix.core/source uix.benchmark.uix/form-compiled)
         "(defui form-compiled [{:keys [children]}]\n  ($ :form children))"))
  (is (= (uix.core/source row-compiled)
         "(defui row-compiled [{:keys [children]}]\n  ($ :div.row children))")))

(defui comp-42336 [{:keys [comp-42336]}]
  (let [comp-42336 1]
    "hello"))

(deftest test-42336
  (is (.-uix-component? ^js comp-42336))
  (is (str/starts-with? (.-displayName comp-42336) "uix.core-test/comp-42336")))

(defui ^{:memo false} comp-props-map [props] 1)

(deftest test-props-map
  (binding [aot/*memo-disabled?* true
            uix.core/*-use-cache-internal* false]
    (is (= 1 (comp-props-map #js {:argv nil})))
    (is (= 1 (comp-props-map #js {:argv {}})))
    (is (thrown-with-msg? js/Error #"UIx component expects a map of props, but instead got \[\]" (comp-props-map #js {:argv []})))))

(deftest test-fn
  (let [anon-named-fn (uix.core/fn fn-component [{:keys [x]}] x)
        anon-fn (uix.core/fn [{:keys [x]}] x)]

    (is (.-uix-component? ^js anon-named-fn))
    (is (= (.-displayName anon-named-fn) "fn-component"))

    (is (.-uix-component? ^js anon-fn))
    (is (str/starts-with? (.-displayName anon-fn) "uix-fn"))

    (t/with-react-root
      ($ anon-named-fn {:x "HELLO!"})
      (fn [node]
        (is (= "HELLO!" (.-textContent node)))))

    (t/with-react-root
      ($ anon-fn {:x "HELLO! 2"})
      (fn [node]
        (is (= "HELLO! 2" (.-textContent node)))))))

(defui dyn-uix-comp [props]
  ($ :button props))

(defn dyn-react-comp [^js props]
  ($ :button
     {:title (.-title props)
      :children (.-children props)}))

(deftest test-dynamic-element
  (testing "dynamic element as a keyword"
    (let [as :button#btn.action]
      (is (= "<button title=\"hey\" id=\"btn\" class=\"action\">hey</button>"
             (t/as-string ($ as {:title "hey"} "hey"))))))
  (testing "dynamic element as uix component"
    (let [as dyn-uix-comp]
      (is (= "<button title=\"hey\">hey</button>"
             (t/as-string ($ as {:title "hey"} "hey"))))))
  (testing "dynamic element as react component"
    (let [as dyn-react-comp]
      (is (= "<button title=\"hey\">hey</button>"
             (t/as-string ($ as {:title "hey"} "hey")))))))

(deftest test-class-name-attr
  (let [props {:class "two" :class-name "three" :className "four"}
        props2 {:class ["two"] :class-name ["three"] :className ["four"]}]
    (is (= "<a class=\"one two three four\"></a>"
           (t/as-string ($ :a.one {:class "two" :class-name "three" :className "four"}))))
    (is (= "<a class=\"one two three four\"></a>"
           (t/as-string ($ :a.one props))))
    (is (= "<a class=\"one two three four\"></a>"
           (t/as-string ($ :a.one {:class ["two"] :class-name ["three"] :className ["four"]}))))
    (is (= "<a class=\"one two three four\"></a>"
           (t/as-string ($ :a.one props2))))))

(defonce *error-state (atom nil))

(def error-boundary
  (uix.core/create-error-boundary
   {:derive-error-state (fn [error]
                          {:error error})
    :did-catch          (fn [error info]
                          (reset! *error-state error))}
   (fn [[state _set-state!] {:keys [children]}]
     (if (:error state)
       ($ :p "Error")
       children))))

(defui throwing-component [{:keys [throw?]}]
  (when throw?
    (throw "Component throws")))

(defui error-boundary-no-elements []
  ($ throwing-component {:throw? false}))

(defui error-boundary-catches []
  ($ error-boundary
     ($ throwing-component {:throw? true})))

(defui error-boundary-renders []
  ($ error-boundary
     ($ throwing-component {:throw? false})
     ($ :p "After")))

(defui error-boundary-children []
  ($ error-boundary
     ($ throwing-component {:throw? false})
     ($ :p "After throwing")
     ($ :p "After throwing 2")))

(deftest ssr-error-boundaries
  (t/with-react-root
    ($ error-boundary-no-elements)
    #(is (= (.-textContent %) "")))

  (t/with-react-root
    ($ error-boundary-catches)
    #(is (= (.-textContent %) "Error")))

  (t/with-react-root
    ($ error-boundary-renders)
    #(is (= (.-textContent %) "After")))

  (t/with-react-root
    ($ error-boundary-children)
    #(is (= (.-textContent %) "After throwingAfter throwing 2"))))

(deftest ssr-error-boundary-catch-fn
  (reset! *error-state nil)
  (t/with-react-root
    ($ error-boundary-catches)
    (fn [_]
      ;; Tests that did-catch does run
      (is (str/includes? @*error-state "Component throws")))))

(deftest js-obj-props
  (let [el ($ :div #js {:title "hello"} 1 2 3)]
    (is (= "hello" (.. el -props -title)))))

(defui forward-ref-interop-uix-component [{:keys [state] :as props}]
  (reset! state props)
  nil)

(deftest test-render-context
  (let [result (atom nil)
        ctx (uix.core/create-context "hello")
        comp (uix.core/fn []
               (let [v (uix.core/use-context ctx)]
                 (reset! result v)))
        _ (rtl/render
            ($ ctx {:value "world"}
               ($ comp)))]
    (is (= "world" @result))))

(deftest test-context-value-clojure-primitive
  (let [result (atom nil)
        ctx (uix.core/create-context :hello)
        comp (uix.core/fn []
               (let [v (uix.core/use-context ctx)]
                 (reset! result v)
                 nil))
        _ (rtl/render
            ($ ctx {:value :world}
               ($ comp)))]
    (is (= :world @result))))

(deftest test-forward-ref-interop
  (let [state (atom nil)
        forward-ref-interop-uix-component-ref (uix.core/forward-ref forward-ref-interop-uix-component)
        _ (rtl/render
           (react/cloneElement
            ($ forward-ref-interop-uix-component-ref {:y 2 :a {:b 1} :state state} "meh")
            #js {:ref #js {:current 6} :x 1 :t #js [2 3 4] :o #js {:p 8}}
            "yo"))
        {:keys [x y a t o ref]} @state]
    (is (= x 1))
    (is (= y 2))
    (is (= a {:b 1}))
    (is (= (vec t) [2 3 4]))
    (is (= (.-p o) 8))
    (is (= (.-current ref) 6))
    (is (= state (:state @state)))))

(deftest test-clone-element
  (testing "cloning component element"
    (uix.core/defui test-clone-element-comp [])
    (let [el (uix.core/clone-element ($ test-clone-element-comp {:title 0 :key 1 :ref 2} "child")
                                     {:data-id 3}
                                     "child2")]
      (is (= test-clone-element-comp (.-type el)))
      (is (= "1" (.-key el)))
      (is (= {:title 0 :ref 2 :data-id 3 :children ["child2"]}
             (.. el -props -argv)))))
  (testing "cloning primitive element"
    (let [el1 (uix.core/clone-element ($ :div {:title 0 :key 1 :ref 2} "child")
                                      {:data-id 3}
                                      "child2")
          el2 (uix.core/clone-element (react/createElement "div" #js {:title 0 :key 1 :ref 2} "child")
                                      {:data-id 3}
                                      "child2")]
      (doseq [^js el [el1 el2]]
        (is (= "div" (.-type el)))
        (is (= "1" (.-key el)))
        (is (= 2 (.-ref el)))
        (is (= 0 (.. el -props -title)))
        (is (= 3 (aget (.. el -props) "data-id")))
        (is (= "child2" (aget (.. el -props -children) 0)))))))

(deftest test-rest-props
  (binding [aot/*memo-disabled?* true
            uix.core/*-use-cache-internal* false]
    (testing "defui should return rest props"
      (uix.core/defui ^{:memo false} rest-component [{:keys [a b] :& props}]
        [props a b])
      (is (= [{:c 3} 1 2] (rest-component #js {:argv {:a 1 :b 2 :c 3}})))
      (is (= [{} 1 2] (rest-component #js {:argv {:a 1 :b 2}}))))
    (testing "fn should return rest props"
      (let [f (uix.core/fn rest-component [{:keys [a b] :& props}]
                [props a b])]
        (is (= [{:c 3} 1 2] (f #js {:argv {:a 1 :b 2 :c 3}})))
        (is (= [{} 1 2] (f #js {:argv {:a 1 :b 2}})))))))

(deftest test-component-fn-name
  (testing "defui name"
    (defui ^{:memo false} component-fn-name [])
    (is (= "uix.core-test/component-fn-name"
           (.-name component-fn-name))))
  (testing "fn name"
    (let [f1 (uix.core/fn component-fn-name [])
          f2 (uix.core/fn [])]
      (is (= "component-fn-name" (.-name f1)))
      (is (str/starts-with? (.-name f2) "uix-fn")))))

(deftest test-props-check
  (binding [aot/*memo-disabled?* true
            uix.core/*-use-cache-internal* false]
    (s/def ::x string?)
    (s/def ::props (s/keys :req-un [::x]))
    (testing "props check in defui"
      (uix.core/defui ^{:memo false} props-check-comp
        [props]
        {:props [::props]}
        props)
      (try
        (props-check-comp #js {:argv {:x 1}})
        (catch js/Error e
          (is (str/starts-with? (ex-message e) "Invalid argument"))))
      (try
        (props-check-comp #js {:argv {:x "1"}})
        (catch js/Error e
          (is false))))
    (testing "props check in fn"
      (let [f (uix.core/fn
                [props]
                {:props [::props]}
                props)]
        (try
          (f #js {:argv {:x 1}})
          (catch js/Error e
            (is (str/starts-with? (ex-message e) "Invalid argument"))))
        (try
          (f #js {:argv {:x "1"}})
          (catch js/Error e
            (is false)))))))

(deftest test-spread-props
  (testing "primitive element"
    (testing "static"
      (let [props {:width 100 :style {:color :blue}}
            el (uix.core/$ :div {:on-click prn :& props} "child")]
        (is (= "div" (.-type el)))
        (is (= prn (.. el -props -onClick)))
        (is (= 100 (.. el -props -width)))
        (is (= "blue" (.. el -props -style -color))))
      (testing "class names merging"
        (let [props1 {:class "ok"}
              props2 {:class "world"}
              el (uix.core/$ :div.hello {:title "x" :& [props1 props2]})]
          (is (= "hello world" (.. el -props -className))))))
    (testing "dynamic"
      (let [tag :div
            props {:width 100 :style {:color :blue}}
            el (uix.core/$ tag {:on-click prn :& props} "child")]
        (is (= "div" (.-type el)))
        (is (= prn (.. el -props -onClick)))
        (is (= 100 (.. el -props -width)))
        (is (= "blue" (.. el -props -style -color))))
      (testing "class names merging"
        (let [tag :div.hello
              props1 {:class "ok"}
              props2 {:class "world"}
              el (uix.core/$ tag {:title "x" :& [props1 props2]})]
          (is (= "hello world" (.. el -props -className)))))))
  (testing "component element"
    (testing "static"
      (defui spread-props-comp [])
      (let [props {:width 100 :style {:color :blue}}
            el (uix.core/$ spread-props-comp {:on-click prn :& props} "child")
            props (.. el -props -argv)]
        (is (= spread-props-comp (.-type el)))
        (is (= prn (:on-click props)))
        (is (= 100 (:width props)))
        (is (= :blue (-> props :style :color)))))
    (testing "dynamic"
      (let [static-comp spread-props-comp
            props {:width 100 :style {:color :blue}}
            el (uix.core/$ static-comp {:on-click prn :& props} "child")
            props (.. el -props -argv)]
        (is (= static-comp (.-type el)))
        (is (= prn (:on-click props)))
        (is (= 100 (:width props)))
        (is (= :blue (-> props :style :color))))))
  (testing "js interop component element"
    (defn spread-props-js-comp [])
    (let [props {:width 100 :style {:color :blue}}
          el (uix.core/$ spread-props-js-comp {:on-click prn :& props} "child")]
      (is (= spread-props-js-comp (.-type el)))
      (is (= prn (.. el -props -onClick)))
      (is (= 100 (.. el -props -width)))
      (is (= "blue" (.. el -props -style -color)))))
  (testing "multiple props"
    (let [props1 {:width 100 :style {:color :blue}}
          props2 {:height 200 :style {:color :red}}
          el (uix.core/$ :div {:on-click prn
                               :on-mouse-down prn
                               :& [props1 props2 {:title "hello"} #js {:onClick identity}]}
                         "child")]
      (is (= "div" (.-type el)))
      (is (= prn (.. el -props -onMouseDown)))
      (is (= 100 (.. el -props -width)))
      (is (= 200 (.. el -props -height)))
      (is (= "hello" (.. el -props -title)))
      (is (= identity (.. el -props -onClick)))
      (is (= "red" (.. el -props -style -color))))))

(deftest test-204
  (testing "should use Reagent's input when Reagent's context is reactive"
    (set! reagent.impl.util/*non-reactive* false)
    (is (identical? (.-type ($ :input)) uix.compiler.input/reagent-input)))
  (testing "should use Reagent's input when enabled explicitly"
    (set! uix.compiler.input/*use-reagent-input-enabled?* true)
    (is (identical? (.-type ($ :input)) uix.compiler.input/reagent-input))
    (set! uix.compiler.input/*use-reagent-input-enabled?* nil))
  (testing "should not use Reagent's input when enabled explicitly"
    (set! uix.compiler.input/*use-reagent-input-enabled?* false)
    (is (identical? (.-type ($ :input)) "input"))
    (set! uix.compiler.input/*use-reagent-input-enabled?* nil)))


(deftest test-hoist-inline
  (binding [aot/*memo-disabled?* true
            uix.core/*-use-cache-internal* false]
    (defui ^{:test/inline true :memo false} test-hoist-inline-1 []
      (let [title "hello"
            tag :div
            props {:title "hello"}]
        (js->clj
          #js [($ :div {:title "hello"} ($ :h1 "yo"))
               ($ :div {:title title} ($ :h1 "yo"))
               ($ tag {:title "hello"} ($ :h1 "yo"))
               ($ :div props ($ :h1 "yo"))])))
    (is (apply = (map #(-> % (assoc "_store" {"validated" 1})
                             (update "props" dissoc "children"))
                      (test-hoist-inline-1))))
    (is (apply = (->> (test-hoist-inline-1)
                      (mapcat #(let [children (get-in % ["props" "children"])]
                                 (if (or (vector? children) (js/Array.isArray children))
                                   children
                                   [children])))
                      (map #(assoc % "_store" {"validated" 1})))))

    (is (->> (js/Object.keys js/uix.core-test)
             (filter #(str/starts-with? % "uix_aot_hoisted"))
             count
             (= 2)))))

(deftest test-css-variables
  (testing "should preserve CSS var name"
    (let [el ($ :div {:style {:--main-color "red"
                              "--text-color" "blue"}})
          styles {:--main-color "red"
                  "--text-color" "blue"}
          el1 ($ :div {:style styles})]
      (is (= "red" (aget (.. el -props -style) "--main-color")))
      (is (= "blue" (aget (.. el -props -style) "--text-color")))
      (is (= "red" (aget (.. el1 -props -style) "--main-color")))
      (is (= "blue" (aget (.. el1 -props -style) "--text-color"))))))

(defcontext *theme* :light)

(defui h1t [{:keys [v]}]
  ($ :<>
    (name (uix.core/use-context *theme*))
    (when (pos? v)
      ($ *theme* {:value :blue}
        ($ h1t {:v (dec v)})))))

(deftest test-defcontext
  (is (= "dark<!-- -->blue"
         (server/render-to-string ($ *theme* {:value :dark}
                                     ($ h1t {:v 1}))))))

(defn -main []
  (run-tests))
