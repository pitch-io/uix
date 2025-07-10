(ns uix.compiler.aot
  "Compiler code that translates HyperScript into React calls at compile-time."
  (:require [cljs.analyzer :as ana]
            [cljs.env :as env]
            [clojure.string :as str]
            [uix.compiler.js :as js]
            [uix.compiler.attributes :as attrs]
            [uix.lib]
            [uix.linter])
  (:import (clojure.lang IMapEntry IMeta IRecord MapEntry)
           (cljs.tagged_literals JSValue)))

(defn- props->spread-props [props]
  (let [spread-props (:& props)]
    (cond
      (symbol? spread-props) [spread-props]
      (vector? spread-props) spread-props
      :else nil)))

(defmulti compile-spread-props (fn [tag attrs tag-id-class f] tag))

(defmethod compile-spread-props :element [_ attrs tag-id-class f]
  (if-let [spread-props (props->spread-props attrs)]
    `(merge-props ~(nth tag-id-class 2 nil)
       (cljs.core/array ~(f (dissoc attrs :&)) ~@(for [props spread-props]
                                                   `(uix.compiler.attributes/convert-props ~props (cljs.core/array) false))))
    (f attrs)))

(defmethod compile-spread-props :component [_ props _ f]
  (if-let [spread-props (props->spread-props props)]
    (f `(merge ~(dissoc props :&) ~@spread-props))
    (f props)))

(defmulti compile-attrs
  "Compiles a map of attributes into JS object,
  or emits interpretation call for runtime, when a value
  at props position is dynamic (symbol)"
  (fn [tag attrs opts] tag))

(defn- safe-child? [form]
  (or (string? form)
      (number? form)
      (uix.linter/uix-element? form)))

(defmethod compile-attrs :element [_ attrs {:keys [tag-id-class]}]
  (cond
    (or (map? attrs) (nil? attrs))
    `(cljs.core/array
      ~(compile-spread-props :element attrs tag-id-class
         #(cond-> %
            ;; merge parsed id and class with attrs map
            :always (attrs/set-id-class tag-id-class)
            ;; interpret :style if it's not map literal
            (and (some? (:style %))
                 (not (map? (:style %))))
            (assoc :style `(uix.compiler.attributes/convert-props ~(:style %) (cljs.core/array) true))
            ;; camel-casify the map
            :always (attrs/compile-attrs {:custom-element? (last tag-id-class)})
            ;; emit JS object literal
            :always js/to-js)))

    (safe-child? attrs)
    (if (attrs/id-class? tag-id-class)
      `(cljs.core/array (uix.compiler.attributes/convert-props {} (cljs.core/array ~@tag-id-class) false) ~attrs)
      `(cljs.core/array nil ~attrs))

    (and (instance? JSValue attrs) (map? (.-val attrs)))
    (if (attrs/id-class? tag-id-class)
      `(cljs.core/array (uix.compiler.attributes/set-id-class ~attrs (cljs.core/array ~@tag-id-class)))
      `(cljs.core/array ~attrs))

    :else
    ;; otherwise emit interpretation call
    `(uix.compiler.attributes/interpret-attrs ~attrs (cljs.core/array ~@tag-id-class) false)))

(defmethod compile-attrs :component [_ props _]
  (cond
    (or (map? props) (nil? props))
    (compile-spread-props :component props nil (fn [props] `(cljs.core/array ~props)))

    (safe-child? props) `(cljs.core/array nil ~props)

    :else))

(def attrs-memo-reg (atom {}))
(def elements-memo-reg (atom {}))

(defn- resolve-var [env var-name]
  (ana/gets @env/*compiler* ::ana/namespaces (-> env :ns :name) :defs var-name))

(def ^:dynamic *memo-disabled?* false)

(defn with-memo-attrs [env attrs]
  (if *memo-disabled?*
    attrs
    (let [var-name (symbol (str "memo-attrs" "-" (:line env) "-" (:column env)))
          ns (-> env :ns :name)]
      (if-let [{:keys [var-name deps]} (get-in @attrs-memo-reg [ns var-name])]
        (do
          (swap! attrs-memo-reg update ns dissoc var-name)
          (if (resolve-var env var-name)
            `(~var-name ~@deps)
            attrs))
        (if (seq attrs)
          (let [deps-nodes (uix.linter/find-free-variable-nodes env attrs [])
                deps (->> deps-nodes (map :name) distinct vec)]
            (swap! attrs-memo-reg assoc-in [ns var-name] {:deps-nodes deps-nodes :deps deps :value attrs :var-name var-name})
            (if (get-in env [:locals 'uix-memarker])
              `(~var-name ~@deps)
              attrs))
          attrs)))))

(declare prewalk)

(defn- memo-body [fdecl reg cache-hit]
  (let [get-loc (juxt :column :line)
        loc->node (->> reg
                       (mapcat :deps-nodes)
                       distinct
                       (map (juxt (comp get-loc :env) identity))
                       (into {}))]
    (prewalk
      (fn [x]
        (if-not (and (list? x)
                     (= 'let (first x))
                     (vector? (second x)))
          x
          (let [[_ bindings & body] x
                bindings (->> bindings
                              (partition-all 2)
                              (mapcat (fn [[sym value]]
                                        (let [loc (get-loc (meta sym))
                                              [col line] loc]
                                          (if-let [env (:env (loc->node loc))]
                                            (let [deps (uix.linter/find-free-variables env value [])]
                                              [sym `(~cache-hit ~(into [(str col "-" line)] deps) (fn [] ~value))])
                                            [sym value])))))]
            `(let ~(vec bindings)
               ~@body))))
      fdecl)))

(defn emit-memoized [env args fdecl]
  (if *memo-disabled?*
    [[] [] fdecl]
    (do
      (reset! attrs-memo-reg {})
      (reset! elements-memo-reg {})
      (let [[args dissoc-ks rest-sym] (uix.lib/rest-props args)]
        (ana/no-warn
          (ana/analyze* env
            `(fn ~'uix-memarker [props#]
               (let [~args [props#]
                     ~(or rest-sym (gensym "rest-sym")) (dissoc props# ~@dissoc-ks)]
                 ~@fdecl))
            nil
            (when env/*compiler*
              (:options @env/*compiler*)))))
      (let [cache-hit (gensym "cache-hit")
            ns (-> env :ns :name)
            reg (concat (vals (@attrs-memo-reg ns)) (vals (@elements-memo-reg ns)))
            cache-binds `[cache# (atom {})
                          ~cache-hit (fn [args# get-value#]
                                       (let [deps# args#]
                                         (or (get (deref cache#) deps#)
                                             (get (swap! cache# assoc deps# (get-value#)) deps#))))]
            decls (->> reg
                       (mapv (fn [{:keys [deps value var-name]}]
                               `(defn ~var-name ~deps
                                  (~cache-hit ~(into [(str var-name)] deps) (fn [] ~value))))))
            body (memo-body fdecl reg cache-hit)]
        [cache-binds decls body]))))


(defmethod compile-attrs :element [_ attrs {:keys [tag-id-class env]}]
  (if (or (map? attrs) (nil? attrs))
    `(cljs.core/array
      ~(with-memo-attrs env
         (compile-spread-props :element attrs
           #(cond-> %
              ;; merge parsed id and class with attrs map
              :always (attrs/set-id-class tag-id-class)
              ;; interpret :style if it's not map literal
              (and (some? (:style %))
                   (not (map? (:style %))))
              (assoc :style `(uix.compiler.attributes/convert-props ~(:style %) (cljs.core/array) true))
              ;; camel-casify the map
              :always (attrs/compile-attrs {:custom-element? (last tag-id-class)})
              ;; emit JS object literal
              :always js/to-js))))
    ;; otherwise emit interpretation call
    `(uix.compiler.attributes/interpret-attrs ~attrs (cljs.core/array ~@tag-id-class) false)))

(defmethod compile-attrs :component [_ props {:keys [env]}]
  (if (or (map? props) (nil? props))
    (with-memo-attrs env (compile-spread-props :component props (fn [props] `(cljs.core/array ~props))))
    `(uix.compiler.attributes/interpret-props ~props)))

(defmethod compile-attrs :fragment [_ attrs _]
  (cond
    (map? attrs)
    `(cljs.core/array ~(-> attrs attrs/compile-attrs js/to-js))

    (safe-child? attrs) `(cljs.core/array nil ~attrs)

    :else
    `(uix.compiler.attributes/interpret-attrs ~attrs (cljs.core/array) false)))

(defn- input-component? [x]
  (contains? #{"input" "textarea"} x))

(defn form->element-type [tag]
  (cond
    (= :<> tag) :fragment
    (keyword? tag) :element

    (or (symbol? tag)
        (list? tag)
        (instance? clojure.lang.Cons tag))
    :component))

(defmulti compile-element*
  "Compiles UIx elements into React.createElement"
  (fn [[tag] _]
    (form->element-type tag)))

(defmethod compile-element* :default [[tag] _]
  (throw (AssertionError. (str "Incorrect element type. UIx elements can be one of the following types:\n"
                               "React Fragment: :<>\n"
                               "Primitive element: keyword\n"
                               "Component element: symbol"))))

(declare static-child-element? static-attrs?)

(defn with-memo-element [env el ret]
  (if *memo-disabled?*
    ret
    (let [var-name (symbol (str "memo-element" "-" (:line env) "-" (:column env)))
          ns (-> env :ns :name)]
      (if-let [{:keys [var-name deps]} (get-in @elements-memo-reg [ns var-name])]
        (do
          (swap! elements-memo-reg update ns dissoc var-name)
          (if (resolve-var env var-name)
            `(~var-name ~@deps)
            ret))
        (let [deps-nodes (uix.linter/find-free-variable-nodes env el [])
              deps (->> deps-nodes (map :name) distinct vec)]
          (swap! elements-memo-reg assoc-in [ns var-name] {:deps-nodes deps-nodes :deps deps :value ret :var-name var-name})
          ret)))))

(defmethod compile-element* :element [v {:keys [env]}]
  (let [[tag attrs & children] (uix.lib/normalize-element env v)
        tag-id-class (attrs/parse-tag tag)
        attrs-children (compile-attrs :element attrs {:tag-id-class tag-id-class :env env})
        tag-str (first tag-id-class)
        ret (cond
              (input-component? tag-str)
              `(create-uix-input ~tag-str ~attrs-children (cljs.core/array ~@children))

              (and (static-child-element? attrs) (every? static-child-element? children))
              (vary-meta `(~'js* "~{}(~{}, ...~{}, ...~{})" uix.compiler.alpha/create-element* ~tag-str ~attrs-children (cljs.core/array ~@children))
                         assoc :tag 'js)

              (and (static-attrs? attrs) (every? static-child-element? children))
              (vary-meta `(~'js* "~{}(~{}, ...~{}, ...~{})" uix.compiler.alpha/create-element* ~tag-str ~attrs-children (cljs.core/array ~@children))
                         assoc :tag 'js)

              :else `(>el ~tag-str ~attrs-children (cljs.core/array ~@children)))]
    (with-memo-element env (into [attrs-children] children) ret)))

(defmethod compile-element* :component [v {:keys [env]}]
  (let [[tag props & children] (uix.lib/normalize-element env v)
        tag (vary-meta tag assoc :tag 'js)
        props-children (compile-attrs :component props {:env env})
        ret `(uix.compiler.alpha/component-element ~tag ~props-children (cljs.core/array ~@children))]
    (with-memo-element env (into [tag props-children] children) ret)))

(defmethod compile-element* :fragment [v {:keys [env]}]
  (let [[_ attrs & children] v
        attrs (compile-attrs :fragment attrs {:env env})
        ret `(>el fragment ~attrs (cljs.core/array ~@children))]
    ret))

(defn- with-spread-props [v]
  (let [props (nth v 1 nil)]
    (if (and (map? props) (contains? props :&))
      (let [spread-props (:& props)]
        (assoc v 1 `(merge ~(dissoc props :&) ~@(if (vector? spread-props)
                                                  spread-props
                                                  [spread-props]))))
      v)))

(defn compile-element-clj* [v {:keys [env] :as opts}]
  (if (and (symbol? (first v))
           (every? true? ((juxt :dynamic :uix/context) (meta (resolve env (first v))))))
    (let [[_ {:keys [value]} & children] v]
      [:uix/bind-context `(fn [f#] (binding [~(first v) ~value] (f#))) (vec children)])
    (with-spread-props v)))

(defn compile-element [v {:keys [env] :as opts}]
  (if (uix.lib/cljs-env? env)
    (compile-element* v opts)
    (compile-element-clj* v opts)))

;; ========== forms rewriter ==========

(defn- form-name [form]
  (when (and (seq? form) (symbol? (first form)))
    (first form)))

(defmulti compile-form form-name)

(defmethod compile-form :default [form]
  form)

(defmethod compile-form 'for
  [[_ bindings & body :as form]]
  (if (== 2 (count bindings))
    (let [[k v] bindings]
      `(map (fn [~k] ~@body) ~v))
    form))

(defn maybe-with-meta [from to]
  (if (instance? IMeta from)
    (with-meta to (meta from))
    to))

(defn walk
  "Like clojure.walk/postwalk, but preserves metadata"
  [inner outer form]
  (cond
    (list? form)
    (outer (maybe-with-meta form (apply list (map inner form))))

    (instance? IMapEntry form)
    (outer (MapEntry/create (inner (key form)) (inner (val form))))

    (seq? form)
    (outer (maybe-with-meta form (doall (map inner form))))

    (instance? IRecord form)
    (outer (maybe-with-meta form (reduce (fn [r x] (conj r (inner x))) form form)))

    (coll? form)
    (outer (maybe-with-meta form (into (empty form) (map inner form))))

    (instance? JSValue form)
    (outer (JSValue. (inner (.-val form))))

    :else (outer form)))

(defn postwalk [f form]
  (walk (partial postwalk f) f form))

(defn prewalk [f form]
  (walk (partial prewalk f) identity (f form)))

(defn static-attrs? [attrs]
  (if (:ref attrs)
    false
    (let [static-attrs? (atom true)]
      (postwalk
        (fn [form]
          (if (or (symbol? form)
                  (list? form)
                  (instance? clojure.lang.Cons form))
            (reset! static-attrs? false)
            form))
        attrs)
      @static-attrs?)))

(declare static-element?)


(defn static-child-element? [form]
  (or (string? form)
      (number? form)
      (nil? form)
      (static-element? form)))

(defn static-element? [form]
  (if (uix.linter/uix-element? form)
    (let [[_ tag attrs & children] form]
      (and (keyword? tag)
           (not= :<> tag)
           (if (map? attrs)
             (static-attrs? attrs)
             (static-child-element? attrs))
           (every? static-child-element? children)))
    (and (symbol? form)
         (str/starts-with? (name form) "uix-aot-hoisted"))))

(defn release-build? []
  (-> @env/*compiler*
      :shadow.build.cljs-bridge/state
      :mode
      (= :release)))

(defn rewrite-forms [body & {:keys [hoist? fname force?]}]
  (let [hoisted (atom [])
        hoist? (or (and force? hoist?)
                   (and hoist? (release-build?)))
        body (postwalk
               (fn [form]
                 (let [form (compile-form form)]
                   (if-not hoist?
                     form
                     (if (static-element? form)
                       (let [sym (symbol (str "uix-aot-hoisted" (hash form) fname))]
                         (swap! hoisted conj [form sym])
                         sym)
                       form))))
               body)]
    [(distinct @hoisted)
     body]))

(defn- js-obj* [kvs]
  (let [kvs-str (->> (repeat "~{}:~{}")
                  (take (count kvs))
                  (interpose ",")
                  (apply str))]
    (vary-meta
      (list* 'js* (str "({" kvs-str "})") (apply concat kvs))
      assoc :tag 'object)))

(defn inline-element [v opts]
  (let [[_ tag props & children] v
        [props children key ref] (if (map? props)
                                   [(dissoc props :key :ref) (vec children) (:key props) (:ref props)]
                                   [nil (into [props] children) nil nil])
        props (if (seq children)
                (assoc props :children children)
                props)
        el (compile-element* [tag props] opts)]
    (if (= `>el (first el))
      (let [[_ tag [_ props]] el
            props (or props (js-obj* {}))]
        `(let [ref# ~ref
               props# ~(js-obj*
                         {"$$typeof" `(if react-19+? (.for ~'js/Symbol "react.transitional.element")
                                                     (.for ~'js/Symbol "react.element"))
                          "type" tag
                          "props" props
                          "key" key
                          "_owner" nil
                          "_store" (js-obj* {"validated" true})})]
           (if react-19+?
             (when ref# (aset props# "ref" ref#))
             (aset props# "ref" ref#))
           props#))
      el)))

(defn inline-elements [hoisted env enabled? force?]
  (when (or force? (and enabled? (release-build?)))
    (for [[form sym] hoisted]
      `(def ~sym ~(inline-element form {:env env})))))
