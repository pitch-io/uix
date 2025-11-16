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
  (refresh/injectIntoGlobalHook js/globalThis))

(defn refresh!
  "Should be called after hot-reload, in shadow's ^:dev/after-load hook"
  []
  (refresh/performReactRefresh))
