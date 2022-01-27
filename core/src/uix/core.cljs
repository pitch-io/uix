(ns uix.core
  "Public API"
  (:require-macros [uix.core])
  (:require [react :as r]
            [uix.core.lazy-loader]
            [uix.compiler.debug :as debug]
            [uix.hooks.alpha :as hooks]
            [uix.compiler.alpha :as compiler]
            [uix.compiler.aot]
            [uix.lib :refer [doseq-loop]]))

;; React's top-level API

(defn create-class
  "Creates class based React component"
  [{:keys [constructor static prototype]}]
  (let [ctor (fn [props]
               (this-as this
                 (.apply r/Component this (js-arguments))
                 (when constructor
                   (constructor this props)))
               nil)]
    (set! (.-prototype ctor) (.create js/Object (.-prototype r/Component)))
    (doseq-loop [[k v] static]
                (aset ctor (name k) v))
    (doseq-loop [[k v] prototype]
                (aset (.-prototype ctor) (name k) v))
    (set! (.-uix-component? ctor) true)
    ctor))

(defn create-error-boundary
  "Creates React's Error Boundary component

    display-name — the name of the component to be displayed in stack trace
    error->state — maps error object to component's state that is used in render-fn
    handle-catch — for side-effects, logging etc.
    render-fn — takes state value returned from error->state and a vector of arguments passed into error boundary"
  [{:keys [display-name error->state handle-catch]
    :or {display-name (str (gensym "error-boundary"))}}
   render-fn]
  (let [constructor (fn [^js/React.Component this _]
                      (set! (.-state this) #js {:argv nil})
                      (specify! (.-state this)
                        IDeref
                        (-deref [o]
                          (.. this -state -argv))
                        IReset
                        (-reset! [o new-value]
                          (.setState this #js {:argv new-value})
                          new-value)
                        ISwap
                        (-swap!
                          ([o f]
                           (-reset! o (f (-deref o))))
                          ([o f a]
                           (-reset! o (f (-deref o) a)))
                          ([o f a b]
                           (-reset! o (f (-deref o) a b)))
                          ([o f a b xs]
                           (-reset! o (apply f (-deref o) a b xs))))))
        derive-state (fn [error] #js {:argv (error->state error)})
        render (fn []
                 (this-as ^react/Component this
                   (let [args (.. this -props -argv)
                         state (.-state this)]
                     ;; `render-fn` should return compiled UTL
                     (render-fn state args))))]
    (create-class {:constructor constructor
                   :static {:displayName display-name
                            :getDerivedStateFromError derive-state}
                   :prototype {:componentDidCatch handle-catch
                               :render render}})))

(defn create-ref
  "Creates React's ref type object."
  []
  (r/createRef))

(defn memo
  "Takes component `f` and comparator function `should-update?`
  that takes previous and next props of the component.
  Returns memoized `f`.

  When `should-update?` is not provided uses default comparator
  that compares props with clojure.core/=

  UIx components are memoized by default"
  ([f]
   (memo f compiler/*default-compare-args*))
  ([f should-update?]
   (react/memo f should-update?)))

(defn use-state
  "Takes initial value and returns React's state hook wrapped in atom-like type."
  [value]
  (hooks/use-state value))

(defn use-effect
  "Takes a function to be executed in an effect and optional vector of dependencies.

  See: https://reactjs.org/docs/hooks-reference.html#useeffect"
  ([setup-fn]
   (hooks/use-effect setup-fn))
  ([setup-fn deps]
   (hooks/use-effect setup-fn deps)))

(defn use-layout-effect
  "Takes a function to be executed in a layout effect and optional vector of dependencies.

  See: https://reactjs.org/docs/hooks-reference.html#uselayouteffect"
  ([setup-fn]
   (hooks/use-layout-effect setup-fn))
  ([setup-fn deps]
   (hooks/use-layout-effect setup-fn deps)))

(defn use-memo
  "Takes function f and optional vector of dependencies, and returns memoized f."
  ([f]
   (hooks/use-memo f))
  ([f deps]
   (hooks/use-memo f deps)))

(defn use-ref
  "Takes optional initial value and returns React's ref hook wrapped in atom-like type."
  ([]
   (hooks/use-ref nil))
  ([value]
   (hooks/use-ref value)))

(defn use-callback
  "Takes function f and optional vector of dependencies, and returns f."
  ([f]
   (hooks/use-callback f))
  ([f deps]
   (hooks/use-callback f deps)))

(defn create-context [v]
  (react/createContext v))

(defn use-context
  "Takes React context and returns its current value"
  [v]
  (hooks/use-context v))

(def create-element r/createElemenet)

(defn glue-args [^js props]
  (cond-> (.-argv props)
          (.-children props) (assoc :children (.-children props))))

(def with-name debug/with-name)

(defn as-react
  "Interop with React components. Takes UIx component function and returns same component wrapped into interop layer."
  [f]
  (compiler/as-react f))
