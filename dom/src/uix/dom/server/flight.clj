(ns uix.dom.server.flight
  (:require [clojure.string :as str]
            [clojure.walk :as walk]
            [uix.compiler.attributes :as attrs]
            [uix.core :refer [$ defui]]
            [uix.dom.server :as dom.server]
            [uix.rsc.loader :as loader]
            [cheshire.core :as json]
            [clojure.core.async :as async])
  (:import [clojure.lang IBlockingDeref IPersistentVector ISeq]))

(defprotocol FlightRenderer
  (-unwrap [this sb]
    "Unwraps component tree into data representation")
  (-render [this sb]
    "Renders a type into React Flight format"))

(defn get-id [sb]
  (Integer/toHexString (:id (swap! sb update :id inc))))

(defn- normalize-props [[tag attrs & children :as el]]
  (when (> (count el) 1)
    (if (map? attrs)
      (cond-> attrs (seq children) (assoc :children children))
      {:children (seq (into [attrs] children))})))

(defn- get-cached-id [sb key value]
  (or (->> @sb key (some (fn [[id v]] (when (= v value) id))))
      (let [id (get-id sb)]
        (swap! sb update key assoc id value)
        id)))

(defn- create-action-ref [sb refs action-id bound]
  (let [ref-fn (cond-> {:id action-id}
                       (seq bound) (assoc :bound bound))
        id (get-cached-id sb :refs ref-fn)
        ref-id (str "$F" id)
        _ (swap! refs assoc ref-id ref-id)]
    ref-id))

(defn- with-client-refs [sb props]
  (let [refs (atom {})]
    [(walk/prewalk
       (fn [form]
         (cond
           ;; server action as prop to client comp
           (and (fn? form)
                (:uix.rsc/action-id (meta form)))
           (let [action-id (:uix.rsc/action-id (meta form))]
             (create-action-ref sb refs action-id []))

           ;; partially applied server action
           (and (vector? form)
                (identical? :rsc/partial (nth form 0 nil)))
           (let [[_ f args] form
                 action-id (:uix.rsc/action-id (meta f))]
             (create-action-ref sb refs action-id args))

           ;; server comp as prop to client comp
           (and (vector? form)
                (:uix/element? (meta form)))
           (let [ref-el (-render form sb)
                 id (get-cached-id sb :refs ref-el)
                 ref-id (str "$" id)
                 _ (swap! refs assoc ref-id ref-id)]
             ref-id)

           :else form))
       props)
     @refs]))

(defn- serialize-props [sb {:keys [children] :as props}]
  (str (cond-> (dissoc props :children :key)
               (seq children)
               (assoc :children (map #(-render % sb) children)))))

(defn render-component-element-client-ref
  [[tag :as el] sb]
  (let [rsc-id (:rsc/id (meta tag))
        [props refs] (with-client-refs sb (normalize-props el))
        ref-import (str "I" (json/generate-string ["rsc" rsc-id false]))
        id (get-cached-id sb :imports ref-import)]
    ["$" (str "$L" id)
     (:key props)
     (cond-> {}
             (seq refs) (assoc "rsc/refs" refs)
             (seq props) (assoc "rsc/props" (serialize-props sb props)))]))

(defn render-component-element-server [[tag :as el] sb]
  (let [props (normalize-props el)
        v (if (seq props)
            (tag props)
            (tag))]
    (-render v sb)))

(defn client-component? [tag]
  (:client (meta tag)))

(defn- render-props [attrs children sb]
  (cond-> (attrs/compile-attrs (dissoc attrs :children :key))
          (seq children)
          (assoc :children (map #(-render % sb) children))))

(defn- simple-action? [attrs]
  (-> attrs :action meta :uix.rsc/action-id))

(defn- partial-action? [attrs]
  (and (vector? (:action attrs))
       (identical? :rsc/partial (nth (:action attrs) 0 nil))))

(defn- action? [attrs]
  (or (simple-action? attrs)
      (partial-action? attrs)))

(defn render-form-element [[tag attrs children :as el] sb]
  (let [action (:rsc/action attrs)
        [action-id args] (if (partial-action? attrs)
                           [(-> (nth action 1) meta :uix.rsc/action-id)
                            (nth action 2 nil)]
                           [(-> action meta :uix.rsc/action-id)
                            []])
        ref-id (create-action-ref sb (atom {}) action-id [])
        attrs (-> attrs
                  (assoc :action ref-id)
                  (dissoc :rsc/action))]
    [tag attrs children]))

(defn- form-with-action? [tag attrs]
  (and (= :form tag) (action? attrs)))

(defn render-dom-element
  "input: ($ :h1 'hello')
   output: [$ h1 key props]"
  [element sb]
  (let [[tag attrs :as el] (update (dom.server/normalize-element element) 0 keyword)
        [tag attrs children] (cond-> el
                                     (form-with-action? tag attrs)
                                     (render-form-element sb))]
    ["$" (name tag)
     (:key attrs)
     (render-props attrs children sb)]))

(defn render-fragment-element
  "input: ($ :<> el1 el2 ...)
   output: [el1 el2 ...]"
  [[tag attrs & children] sb]
  (let [children (if (map? attrs)
                   children
                   (cons attrs children))]
    (-render children sb)))

(defn render-suspense-element [[_ {:keys [fallback children]}] sb]
  ["$" "$Sreact.suspense"
   nil
   {:fallback (-render fallback sb)
    :children children}])

(defn render-element!
  [[tag :as el] sb]
  (cond
    (client-component? tag) (render-component-element-client-ref el sb)
    (fn? tag) (render-component-element-server el sb)
    (= :<> tag) (render-fragment-element el sb)
    (= :uix.core/suspense tag) (render-suspense-element el sb)
    (keyword? tag) (render-dom-element el sb)
    :else (throw (IllegalArgumentException. (str (type tag) " " tag " is not a valid element type")))))

(defn- unwrap-component-element [[tag :as el] sb]
  (let [props (normalize-props el)
        v (loader/with-loader
            (if (seq props)
              (tag props)
              (tag)))]
    (-unwrap v sb)))

(defn- unwrap-fragment-element [[tag attrs & children] sb]
  (let [children (if (map? attrs)
                   children
                   (cons attrs children))]
    (-unwrap children sb)))

(defn- unwrap-form-element [[tag attrs children]]
  (let [attrs (into attrs {:rsc/action (:action attrs)})
        fields (if (simple-action? attrs)
                 [[:input {:type :hidden :name "_$action" :value (-> attrs :action meta :uix.rsc/action-id)}]]
                 (let [action (:action attrs)
                       action-id (-> action (nth 1) meta :uix.rsc/action-id)
                       args (nth action 2 [])]
                   [[:input {:type :hidden :name "_$action" :value action-id}]
                    [:input {:type :hidden :name "_$args" :value (str args)}]]))]
    [tag attrs (into fields children)]))

(defn- unwrap-dom-element [el sb]
  (let [[tag attrs :as el] (update (dom.server/normalize-element el) 0 keyword)
        [tag attrs children] (cond-> el
                                     (form-with-action? tag attrs)
                                     (unwrap-form-element))]
    (into [tag attrs] (map #(-unwrap % sb) children))))

(defn serialize-blocking [this sb key tag & args]
  (when-not (contains? (key @sb) this)
    (let [id (get-id sb)
          ch this]
      (swap! sb update key assoc this (into [ch id] args))))
  (let [[_ id] (get (key @sb) this)]
    (str tag id)))

(defn- unwrap-suspense-element [[tag {:keys [fallback children]}] sb]
  (let [to-id (str (gensym "B:"))
        futures (map #(async/thread
                        (try
                          (-unwrap % sb)
                          (catch Throwable e
                            e)))
                     children)
        tags (map #(serialize-blocking % sb :suspended "$L" to-id) futures)]
    [tag {:to-id to-id
          :fallback (-unwrap fallback sb)
          :children tags}]))

(defn unwrap-element [[tag :as el] sb]
  (cond
    (client-component? tag) el
    (fn? tag) (unwrap-component-element el sb)
    (= :<> tag) (unwrap-fragment-element el sb)
    (= :uix.core/suspense tag) (unwrap-suspense-element el sb)
    (keyword? tag) (unwrap-dom-element el sb)
    :else (throw (IllegalArgumentException. (str (type tag) " " tag " is not a valid element type")))))

(extend-protocol FlightRenderer
  IPersistentVector
  (-unwrap [this sb]
    (if (vector? (first this))
      (map #(-unwrap % sb) this)
      (unwrap-element this sb)))
  (-render [this sb]
    (if (vector? (first this))
      (map #(-render % sb) this)
      (render-element! this sb)))

  ISeq
  (-unwrap [this sb]
    (doall (map #(-unwrap % sb) this)))
  (-render [this sb]
    (doall (map #(-render % sb) this)))

  String
  (-unwrap [this sb]
    this)
  (-render [this sb]
    this)

  Object
  (-unwrap [this sb]
    (-unwrap (str this) sb))
  (-render [this sb]
    (-render (str this) sb))

  Throwable
  ;; throwable serializer
  (-unwrap [this sb]
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
  ;; todo: async value can be any prop
  (-unwrap [this sb]
    (atom (serialize-blocking this sb :pending "$@")))
  (-render [this sb]
    nil)

  clojure.lang.Atom
  (-render [this sb]
    (-render @this sb))

  nil
  (-unwrap [this sb]
    nil)
  (-render [this sb]
    nil))

(defn emit-row [id src]
  (str id ":"
       (if (and (string? src)
                (or (str/starts-with? src "E{")
                    (str/starts-with? src "I[")))
         src
         (json/generate-string src))
       "\n"))

(defn create-state []
  (atom {:id 0
         :pending {}
         :suspended {}
         :imports {}
         :refs {}}))

(defn- render-result [src result sb]
  (let [id (get-id sb)]
    [(emit-row id (-render src sb))
     (emit-row 0 {:result result :root (str "$" id)})]))

(defn render-to-flight-stream [src {:keys [on-chunk sb result] :as opts}]
  (let [sb (or sb (create-state))
        root-rows (if (some? result)
                    (render-result src result sb)
                    [(emit-row 0 (-render src sb))])
        imports (mapv #(apply emit-row %) (:imports @sb))
        refs (mapv #(apply emit-row %) (:refs @sb))]
    ;; flight stream structure
    ;; 1. imports/client refs
    ;; 2. ui structure interleaved with async values
    (async/go
      (on-chunk (str/join "" (into (into imports refs) root-rows)))
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