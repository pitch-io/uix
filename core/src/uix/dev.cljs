(ns uix.dev
  (:require ["react-refresh/runtime" :as refresh]))

(defn signature! []
  (refresh/createSignatureFunctionForTransform))

(defn register! [type id]
  (refresh/register type id))

;;;; Public API ;;;;

(defn init-fast-refresh!
  "Injects react-refresh runtime. Should be called before UI is rendered"
  []
  (refresh/injectIntoGlobalHook js/window))

(defn refresh!
  "Should be called after hot-reload, in shadow's ^:dev/after-load hook"
  []
  (refresh/performReactRefresh))

(defn uix-component?
  "Returns true, if `f` is a UIx component created by `defui` or `uix/fn`"
  [^js f]
  (true? (.-uix-component? f)))
