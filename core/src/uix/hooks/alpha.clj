(ns uix.hooks.alpha
  "React Hooks ported to JVM for SSR"
  (:refer-clojure :exclude [use])
  (:import (clojure.lang IDeref)
           (java.util UUID)))

(defn no-op [fname & args]
  (throw (UnsupportedOperationException. (str fname " can't be called during SSR"))))

;; == State hook ==
(defn use-state [value]
  [value #(no-op "set-state" %&)])

(defn use-reducer
  ([f value]
   [value #(no-op "set-state" %1 %2)])
  ([f value init-state]
   [(init-state value) #(no-op "set-state" %1 %2)]))

;; == Ref hook
(defn use-ref [value]
  (reify IDeref
    (deref [o]
      value)))

;; == Effect hook ==
(defn use-effect
  ([setup-fn])
  ([setup-fn deps]))

;; == Layout effect hook ==
(defn use-layout-effect
  ([setup-fn])
  ([setup-fn deps]))

;; == Insertion effect hook ==
(defn use-insertion-effect
  ([f])
  ([f deps]))

;; == Callback hook ==
(defn use-callback
  [f deps]
  f)

;; == Memo hook ==
(defn use-memo
  [f deps]
  (f))

;; == Context hook ==
(defn use-context [v]
  v)

;; == Imperative Handle hook ==
(defn use-imperative-handle
  ([ref create-handle])
  ([ref create-handle deps]))

;; == Debug hook ==
(defn use-debug
  ([v])
  ([v fmt]))

(defn use-deferred-value
  ([v]
   v)
  ([v initial]
   initial))

(defn use-transition []
  [false #(no-op "start-transition")])

(defn use-id
  "not stable across client and server,
  see https://github.com/facebook/react/pull/22644"
  []
  (UUID/randomUUID))

(defn use-sync-external-store
  ([subscribe get-snapshot]
   (throw (UnsupportedOperationException. "should provide get-server-snapshot as well, during SSR")))
  ([subscribe get-snapshot get-server-snapshot]
   (get-server-snapshot)))

(defn use-optimistic [state update-fn]
  [state #(no-op "use-optimistic" %1)])

(defn use-action-state
  ([f state]
   (use-action-state f state nil))
  ([f state permalink]
   [state #(no-op "use-action-state" %1) false]))

(defn use [resource]
  ;; TODO: figure out what resources can be consumed on JVM
  resource)