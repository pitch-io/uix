(ns uix.dom.server.flight
  (:require [clojure.string :as str]
            [uix.compiler.attributes :as attrs]
            [uix.core :refer [$ defui]]
            [uix.dom.server :as dom.server]
            [cheshire.core :as json]
            [clojure.core.async :as async])
  (:import [clojure.lang IBlockingDeref IPersistentVector ISeq]))

(defprotocol FlightRenderer
  (-unwrap [this]
    "Unwraps component tree into data representation")
  (-render [this sb]
    "Renders a type into React Flight format"))

(defn get-id [sb]
  (Integer/toHexString (:id (swap! sb update :id inc))))

(defn- normalize-props [[tag attrs & children :as el]]
  (when (> (count el) 1)
    (if (map? attrs)
      (cond-> attrs (seq children) (assoc :children children))
      {:children (into [attrs] children)})))

(defn render-component-element-client-ref
  "input: ($ var-name 'hello')
   output: I:['rsc' 'var-name' false]
           [$ $L key props]"
  [[tag :as el] sb]
  (let [rsc-id (:rsc/id (meta tag))
        {:keys [children] :as props} (normalize-props el)
        ref-import (str "I" (json/generate-string ["rsc" rsc-id false]))
        ;; dedupe client refs
        id (or (->> @sb :imports (some (fn [[id v]] (when (= v ref-import) id))))
               (let [id (get-id sb)]
                 (swap! sb update :imports assoc id ref-import)
                 id))]
    ["$" (str "$L" id)
     (:key props)
     {"rsc/props"
      (str (cond-> (dissoc props :children :key)
                   (seq children)
                   (assoc :children (map #(-render % sb) children))))}]))

(defn render-component-element-server [[tag :as el] sb]
  (let [props (normalize-props el)
        v (if (seq props)
            (tag props)
            (tag))]
    (-render v sb)))

(defn client-component? [tag]
  (:client (meta tag)))

(defn render-dom-element
  "input: ($ :h1 'hello')
   output: [$ h1 key props]"
  [element sb]
  (let [[tag attrs children] (dom.server/normalize-element element)]
    ["$" (name tag)
     (:key attrs)
     (cond-> (attrs/compile-attrs (dissoc attrs :children :key))
             (seq children)
             (assoc :children (map #(-render % sb) children)))]))

(defn render-fragment-element
  "input: ($ :<> el1 el2 ...)
   output: [el1 el2 ...]"
  [[tag attrs & children] sb]
  (let [children (if (map? attrs)
                   children
                   (cons attrs children))]
    (-render children sb)))

(defn render-element!
  [[tag :as el] sb]
  (cond
    (client-component? tag) (render-component-element-client-ref el sb)
    (fn? tag) (render-component-element-server el sb)
    (= :<> tag) (render-fragment-element el sb)
    (keyword? tag) (render-dom-element el sb)
    :else (throw (IllegalArgumentException. (str (type tag) " " tag " is not a valid element type")))))

(defn unwrap-component-element [[tag :as el]]
  (let [props (normalize-props el)
        v (if (seq props)
            (tag props)
            (tag))]
    (-unwrap v)))

(defn unwrap-fragment-element [[tag attrs & children]]
  (let [children (if (map? attrs)
                   children
                   (cons attrs children))]
    (-unwrap children)))

(defn unwrap-dom-element [el]
  (let [[tag attrs children] (dom.server/normalize-element el)]
    (into [(keyword tag) attrs] (map -unwrap children))))

(defn unwrap-element [[tag :as el]]
  (cond
    (client-component? tag) el
    (fn? tag) (unwrap-component-element el)
    (= :<> tag) (unwrap-fragment-element el)
    (keyword? tag) (unwrap-dom-element el)
    :else (throw (IllegalArgumentException. (str (type tag) " " tag " is not a valid element type")))))

(extend-protocol FlightRenderer
  IPersistentVector
  (-unwrap [this]
    (if (vector? (first this))
      (map -unwrap this)
      (unwrap-element this)))
  (-render [this sb]
    (if (vector? (first this))
      (map #(-render % sb) this)
      (render-element! this sb)))

  ISeq
  (-unwrap [this]
    (map -unwrap this))
  (-render [this sb]
    (map #(-render % sb) this))

  String
  (-unwrap [this]
    this)
  (-render [this sb]
    this)

  Object
  (-unwrap [this]
    (-unwrap (str this)))
  (-render [this sb]
    (-render (str this) sb))

  Throwable
  ;; throwable serializer
  (-unwrap [this]
    this)
  (-render [this sb]
    (str "E" (json/generate-string
               {:digest ""
                :name (str (type this))
                :message (ex-message this)})))

  IBlockingDeref
  ;; async values serializer
  ;; and continues rendering while blocking operation
  ;; runs concurrently
  ;; todo: support pending components
  (-unwrap [this]
    ;; todo: maybe should not be empty
    @this)
  (-render [this sb]
    (when-not (contains? (:pending @sb) this)
      (let [id (get-id sb)
            ch (async/go
                 (try
                   @this
                   (catch Throwable e
                     e)))]
        (swap! sb update :pending assoc this [ch id])))
    (let [[_ id] (get (:pending @sb) this)]
      (str "$@" id)))

  nil
  (-unwrap [this]
    :nop)
  (-render [this sb]
    :nop))

(defn emit-row [id src]
  (str id ":"
       (if (and (string? src)
                (or (str/starts-with? src "E{")
                    (str/starts-with? src "I[")))
         src
         (json/generate-string src))
       "\n"))

(defn render-to-flight-stream [src {:keys [on-chunk]}]
  (let [sb (atom {:id 0
                  :pending {}
                  :imports {}})
        root-row (emit-row 0 (-render src sb))
        imports (->> (:imports @sb)
                     (map #(apply emit-row %)))]
    ;; flight stream structure
    ;; 1. imports/client refs
    ;; 2. ui structure interleaved with async values
    (async/go
      (doseq [import imports]
        (on-chunk import))
      (on-chunk root-row)
      (loop [ch->id (->> @sb :pending vals (into {}))]
        (if (seq ch->id)
          (let [[v c] (async/alts! (keys ch->id))
                id (ch->id c)]
            (on-chunk (emit-row id (-render v sb)))
            (recur (dissoc ch->id c)))
          (on-chunk :done))))))

(comment
  (do
    (require 'uix.rsc)
    (uix.rsc/defaction like-article []
      1)
    (defui page []
      ($ :div
         ($ :span @(future (Thread/sleep 1000) "hello"))
         ($ :button "press me")))
    (let [ast (-unwrap ($ page))]
      (println (dom.server/render-to-string ast))
      (render-to-flight-stream
        ast
        {:on-chunk println}))))