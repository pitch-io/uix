(ns uix.linter
  (:require [cljs.env :as env]
            [clojure.walk]
            [cljs.analyzer :as ana]
            [clojure.string :as str]
            [clojure.pprint :as pp]
            [cljs.analyzer.api :as ana-api]
            [uix.lib]
            [clojure.java.io :as io]
            [clojure.edn]
            [cljs.spec.alpha]
            [clojure.spec.alpha])
  (:import (cljs.tagged_literals JSValue)
           (java.io Writer)))

;; === Rules of Hooks ===

(def ^:dynamic *component-context* nil)
(def ^:dynamic *source-context* false)
(def ^:dynamic *in-branch?* false)
(def ^:dynamic *in-loop?* false)

(defn- read-config [path]
  (let [file (io/file ".uix/config.edn")
        config (try
                 (if (.isFile file)
                   (clojure.edn/read-string (slurp file))
                   {})
                 (catch Exception e
                   {}))]
    (get-in config path)))

(defn hook?
  ([sym]
   (hook? sym nil))
  ([sym hook-name]
   (and (symbol? sym)
        (if hook-name
          (= hook-name (name sym))
          (some? (re-find #"^use-|use[A-Z]" (name sym)))))))

(defn hook-call?
  ([form]
   (hook-call? form nil))
  ([form hook-name]
   (and (list? form) (hook? (first form) hook-name))))

(defn interop-prop? [ast prop-name]
  (and (= :host-field (:op ast))
       (= prop-name (:field ast))))

(defn interop-ref-read? [ast]
  (when (interop-prop? ast 'current)
    (let [form (-> ast :target :init :form)]
      (uix.linter/hook-call? form "use-ref"))))

(defn interop-ref-write? [ast]
  (when (and (= :set! (:op ast))
             (interop-prop? (:target ast) 'current))
    (let [form (-> ast :target :target :init :form)]
      (uix.linter/hook-call? form "use-ref"))))

(def effect-hooks
  #{"use-effect" "useEffect"
    "use-layout-effect" "useLayoutEffect"})

(defn effect-hook? [form]
  (contains? effect-hooks (name (first form))))

(defn form->loc [form]
  (select-keys (meta form) [:line :column]))

(defn find-env-for-form [type form]
  (case type
    (::hook-in-branch ::hook-in-loop
     ::deps-coll-literal ::literal-value-in-deps
     ::unsafe-set-state ::missing-key
     ::interop-ref-read ::interop-ref-write)
    (form->loc form)

    ::inline-function
    (form->loc (second form))

    ::deps-array-literal
    (form->loc (.-val form))

    nil))

(defn add-error!
  ([form type]
   (add-error! form type (find-env-for-form type form)))
  ([form type env]
   (swap! *component-context* update :errors conj {:source form
                                                   :source-context *source-context*
                                                   :type type
                                                   :env env})))

(defn uix-element? [form]
  (and (list? form) (= '$ (first form))))

(defn- missing-key? [[_ _ attrs :as form]]
  (cond
    (and (map? attrs) (not (contains? attrs :key)))
    (add-error! attrs ::missing-key)

    (or (and (not (map? attrs))
             (not (symbol? attrs))
             (not (list? attrs)))
        (uix-element? attrs))
    (add-error! form ::missing-key)))

(def mapping-forms
  '{:for #{for}
    :iter-fn #{map mapv map-indexed reduce reduce-kv
               keep keep-indexed mapcat}})

(defn- lint-missing-key!* [expr]
  (cond
    (uix-element? expr)
    (missing-key? expr)

    (and (list? expr)
         (= '->> (first expr))
         (= 3 (count expr))
         (uix-element? (last expr)))
    (missing-key? `(~@(last expr) ~(second expr)))

    (list? expr)
    (recur (last expr))))

(def react-key-rule-enabled?
  (if-some [v (read-config [:linters :react-key :enabled?])]
    v
    true))

(defn- lint-missing-key! [kv sym body]
  (when (and react-key-rule-enabled?
             (contains? (get mapping-forms kv) sym))
    (lint-missing-key!* (last body))))

(declare lint-body!*)

(def forms
  '{:when #{when when-not when-let when-some when-first}
    :if #{if if-not if-let if-some}
    :logical #{and or}
    :cond #{cond}
    :cond-threaded #{cond-> cond->>}
    :some-threaded #{some-> some->>}
    :condp #{condp}
    :case #{case}
    :loop #{loop}
    :for  #{for doseq}
    :iter-fn #{map mapv map-indexed filter filterv reduce reduce-kv keep keep-indexed
               remove mapcat drop-while take-while group-by partition-by split-with
               sort-by some}})

(defmulti maybe-lint
  (fn [[sym :as form]]
    (reduce-kv
     (fn [ret kw forms]
       (if (forms sym)
         (reduced kw)
         ret))
     form
     forms)))

(defmethod maybe-lint :default [form]
  form)

(defmethod maybe-lint :when [[_ test & body]]
  (lint-body!* test :in-branch? false)
  (run! #(lint-body!* % :in-branch? true) body))

(defmethod maybe-lint :if [[_ test then else]]
  (lint-body!* test :in-branch? false)
  (lint-body!* then :in-branch? true)
  (lint-body!* else :in-branch? true))

(defmethod maybe-lint :logical [[_ test & tests]]
  (lint-body!* test :in-branch? false)
  (run! #(lint-body!* % :in-branch? true) tests))

(defmethod maybe-lint :cond [[_ clause & clauses]]
  (lint-body!* clause :in-branch? false)
  (run! #(lint-body!* % :in-branch? true) clauses))

(defmethod maybe-lint :condp [[_ pred e clause & clauses]]
  (lint-body!* pred :in-branch? false)
  (lint-body!* e :in-branch? false)
  (lint-body!* clause :in-branch? false)
  (run! #(lint-body!* % :in-branch? true) clauses))

(defmethod maybe-lint :cond-threaded [[_ e & clauses]]
  (lint-body!* e :in-branch? false)
  (->> (partition 2 clauses)
       (run! (fn [[test expr]]
               (lint-body!* test :in-branch? false)
               (lint-body!* expr :in-branch? true)))))

(defmethod maybe-lint :some-threaded [[_ e clause & clauses]]
  (lint-body!* e :in-branch? false)
  (lint-body!* clause :in-branch? false)
  (run! #(lint-body!* % :in-branch? true) clauses))

(defmethod maybe-lint :case [[_ e clause & clauses]]
  (lint-body!* e :in-branch? false)
  (lint-body!* clause :in-branch? false)
  (run! #(lint-body!* % :in-branch? true) clauses))

(defmethod maybe-lint :loop [[_ bindings & body]]
  (lint-body!* bindings :in-loop? false)
  (run! #(lint-body!* % :in-loop? true) body))

(defmethod maybe-lint :for [[sym bindings & body]]
  (let [[binding & bindings] (partition 2 bindings)]
    (lint-body!* (second binding) :in-loop? false)
    (run! (fn [[v expr]] (lint-body!* expr :in-loop? true))
          bindings)
    (lint-missing-key! :for sym body)
    (run! #(lint-body!* % :in-loop? true) body)))

(defmethod maybe-lint :iter-fn [[sym f :as form]]
  (when (and (list? f)
             ('#{fn fn*} (first f))
             (vector? (second f)))
    (let [[_ _ & body] f]
      (lint-missing-key! :iter-fn sym body)
      (run! #(lint-body!* % :in-loop? true) body))))

(defn ast->seq [ast]
  (tree-seq :children (fn [{:keys [children] :as ast}]
                        (let [get-children (apply juxt children)]
                          (->> (get-children ast)
                               (mapcat #(if (vector? %) % [%])))))
            ast))

(defn lint-body!*
  [expr & {:keys [in-branch? in-loop?]
           :or {in-branch? *in-branch?*
                in-loop? *in-loop?*}}]
  (binding [*in-branch?* (or *in-branch?* in-branch?)
            *in-loop?* (or *in-loop?* in-loop?)]
    (clojure.walk/prewalk
     (fn [form]
       (cond
         (hook-call? form)
         (do (when *in-branch?* (add-error! form ::hook-in-branch))
             (when *in-loop?* (add-error! form ::hook-in-loop))
             nil)

         (and (list? form) (or (not *in-branch?*) (not *in-loop?*)))
         (binding [*source-context* form]
           (maybe-lint form))

         :else form))
     expr)
    nil))

(defn- list-ast! [exprs env]
  (binding [ana/*cljs-warnings* (into (zipmap (keys ana/*cljs-warnings*) (repeat false))
                                      {::interop-ref-read true
                                       ::interop-ref-write true})]
    (loop [[node & nodes] (uix.linter/ast->seq (ana/analyze env (last exprs)))]
      (cond
        (interop-ref-read? node)
        (do (add-error! (:form node) ::interop-ref-read)
            (recur nodes))

        (interop-ref-write? node)
        (do
          (add-error! (:form node) ::interop-ref-write)
          (recur (rest nodes)))

        (seq nodes) (recur nodes)))))

(defn lint-body! [exprs env]
  (when (uix.lib/cljs-env? env)
    (list-ast! exprs env))
  (run! lint-body!* exprs))

(defmethod ana/error-message ::interop-ref-read [_ _]
  "use-ref hook in UIx returns Atom-like ref, use @ instead of .-current to access its value.")

(defmethod ana/error-message ::interop-ref-write [_ _]
  "use-ref hook in UIx returns Atom-like ref, use reset! instead of set! to update its value.")

(defmethod ana/error-message ::missing-key [_ _]
  (str "UIx element is missing :key attribute, which is required\n"
       "since the element is rendered as a list item.\n"
       "Make sure to add a unique value for `:key` attribute derived from element's props,\n"
       "do not use index."))

(defmethod ana/error-message ::hook-in-branch [_ {:keys [name column line source]}]
  ;; https://github.com/facebook/react/blob/d63cd972454125d4572bb8ffbfeccbdf0c5eb27b/packages/eslint-plugin-react-hooks/src/RulesOfHooks.js#L457
  (str "React Hook " source " is called conditionally.\n"
       "React Hooks must be called in the exact same order in every component render.\n"
       "Read https://react.dev/reference/rules/rules-of-hooks for more context"))

(defmethod ana/error-message ::hook-in-loop [_ {:keys [name column line source]}]
  ;; https://github.com/facebook/react/blob/d63cd972454125d4572bb8ffbfeccbdf0c5eb27b/packages/eslint-plugin-react-hooks/src/RulesOfHooks.js#L438
  (str "React Hook " source " may be executed more than once. Possibly because it is called in a loop.\n"
       "React Hooks must be called in the exact same order in every component render.\n"
       "Read https://react.dev/reference/rules/rules-of-hooks for more context"))

;; re-frame linter

(defn- rf-subscribe-call? [form]
  (and (list? form)
       (symbol? (first form))
       (= "subscribe" (name (first form)))))

(defn- read-re-frame-config []
  (-> (reduce-kv (fn [ret k v]
                   (update ret v (fnil conj #{}) k))
                 '{re-frame.core/subscribe #{re-frame.core/subscribe}}
                 (read-config [:linters :re-frame :resolve-as]))
      (get 're-frame.core/subscribe)))

(def re-frame-config
  (read-re-frame-config))

(defn lint-re-frame! [form env]
  (let [resolve-fn (if (uix.lib/cljs-env? env)
                     ana/resolve-var
                     resolve)
        sources    (->> (uix.lib/find-form rf-subscribe-call? form)
                        (keep #(let [v (resolve-fn env (first %))]
                                 (when (contains? re-frame-config (:name v))
                                   (assoc v :source %)))))]
    (run! #(ana/warning ::non-reactive-re-frame-subscribe env %)
          sources)))

(defmethod ana/error-message ::non-reactive-re-frame-subscribe [_ {:keys [source] :as v}]
  (str "re-frame subscription " source " is non-reactive in UIx components when called via "
       (:name v) ", use `use-subscribe` hook instead.\n"
       "Read https://github.com/pitch-io/uix/blob/master/docs/interop-with-reagent.md#syncing-with-ratoms-and-re-frame for more context"))

(defmethod ana/error-message ::element-non-ref-spread [_ {:keys [source] :as v}]
  "Spread syntax can be used only with references to props values. Spreading map literal doesn't make sense, inline it into props map instead.")

(defmethod ana/error-message ::element-unnecessary-spread [_ {:keys [source] :as v}]
  "Spreading a single props map into empty map literal doesn't make sense, instead pass props symbol itself.")

(defmulti lint-component (fn [type form env]))
(defmulti lint-element (fn [type form env]))
(defmulti lint-hook-with-deps (fn [type form env]))

(defmethod lint-element :element/spread-syntax [_ form env]
  (let [props (nth form 2 nil)]
    (when (and (map? props) (contains? props :&))
      (when-not (or (symbol? (:& props))
                    (vector? (:& props)))
        (add-error! form ::element-non-ref-spread (form->loc (:& props))))
      (when (and (empty? (dissoc props :&))
                 (or (not (vector? (:& props)))
                     (== 1 (count (:& props)))))
        (add-error! form ::element-unnecessary-spread (form->loc (:& props)))))))

(defn- run-linters! [mf & args]
  (doseq [[key f] (.getMethodTable ^clojure.lang.MultiFn mf)]
    (apply f key args)))

(defn- report-errors!
  ([env]
   (report-errors! env nil))
  ([env m]
   (let [{:keys [errors]} @*component-context*
         {:keys [column line]} env]
     (run! #(ana/warning (:type %)
                         (or (:env %) env)
                         (merge {:column column :line line} m %))
           errors))))

(defn lint! [sym body form env]
  (binding [*component-context* (atom {:errors []})]
    (lint-body! body env)
    (lint-re-frame! body env)
    (run-linters! lint-component form env)
    (report-errors! env {:name (str (-> env :ns :name) "/" sym)})))

;; === Exhaustive Deps ===

(defn find-local-variables
  "Finds all references in `form` to local vars in `env`"
  [env form]
  (let [syms (atom #{})]
    (clojure.walk/postwalk
     #(cond
        (symbol? %)
        (do (swap! syms conj %)
            %)

        (= (type %) JSValue)
        (.-val %)

        :else %)
     form)
    ;; return only those that are local in `env`
    (filter #(get-in env [:locals % :name]) @syms)))

(defn find-free-variable-nodes [env f deps]
  (let [ast (ana/analyze env f)
        deps (set deps)]
    (->> (ast->seq ast)
         (filter #(and (= :local (:op %)) ;; should be a local
                       (get-in env [:locals (:name %) :name]) ;; from an outer scope
                       (or (-> % :info :shadow not) ;; but not a local shadowing locals from outer scope
                           (-> % :info :shadow :ns (= 'js))) ;; except when shadowing JS global
                       (not (deps (:name %)))))))) ;; and not declared in deps vector

(defn find-free-variables [env f deps]
  (->> (find-free-variable-nodes env f deps)
       (map :name)
       distinct))

(defmethod pp/code-dispatch JSValue [alis]
  (.write ^Writer *out* "#js ")
  (pp/code-dispatch (.-val alis)))

(defn ppr [s]
  (let [s (pp/with-pprint-dispatch pp/code-dispatch
            (with-out-str (pp/pprint s)))
        source (->> (str/split-lines s)
                    (take 8)
                    (str/join "\n"))]
    (str "```\n" source "\n```")))

(defmethod ana/error-message ::inline-function [_ {:keys [source]}]
  "React Hook received a function whose dependencies are unknown. Pass an inline function instead.")

(defmethod ana/error-message ::missing-deps [_ {:keys [source missing-deps unnecessary-deps suggested-deps]}]
  (str "React Hook has "
       (when (seq missing-deps)
         (str "missing dependencies: [" (str/join " " missing-deps) "]\n"))
       (when (seq unnecessary-deps)
         (str (when (seq missing-deps) "and ")
              "unnecessary dependencies: [" (str/join " " unnecessary-deps) "]\n"
              (->> unnecessary-deps
                   (keep (fn [sym]
                           (case (:hook (meta sym))
                             ("use-ref" "useRef")
                             (str "`" sym "` is an unnecessary dependency because it's a ref that doesn't change")

                             ("use-state" "useState" "use-reducer" "useReducer")
                             (str "`" sym "` is an unnecessary dependency because it's a state updater function with a stable identity")

                             ("use-event" "useEvent" "use-effect-event" "useEffectEvent")
                             (str "`" sym "` is an unnecessary dependency because it's a function created using useEffectEvent hook that has a stable identity")

                             nil)))
                   (str/join "\n"))
              "\n"))
       "Update the dependencies vector to be: [" (str/join " " suggested-deps) "]\n"
       "Read https://react.dev/learn/synchronizing-with-effects#step-2-specify-the-effect-dependencies for more context\n"
       (ppr source)))

(defmethod ana/error-message ::deps-array-literal [_ {:keys [source]}]
  (str "React Hook was passed a "
       "dependency list that is a JavaScript array, instead of Clojure’s vector. "
       "Change it to be a vector literal.\n"
       (ppr source)))

(defmethod ana/error-message ::deps-coll-literal [_ {:keys [source]}]
  (str "React Hook was passed a "
       "dependency list that is not a vector literal. This means we "
       "can’t statically verify whether you've passed the correct dependencies. "
       "Change it to be a vector literal with explicit set of dependencies.\n"
       (ppr source)))

(defmethod ana/error-message ::literal-value-in-deps [_ {:keys [source literals]}]
  (str "React Hook was passed literal values in dependency vector: [" (str/join ", " literals) "]\n"
       "Those are not valid dependencies because they never change. You can safely remove them.\n"
       (ppr source)))

(defmethod ana/error-message ::unsafe-set-state [_ {:keys [source unsafe-calls]}]
  (str "React Hook contains a call to `" (first unsafe-calls) "`.\n"
       "Without a vector of dependencies, this can lead to an infinite chain of updates.\n"
       "To fix this, pass the state value into a vector of dependencies of the hook.\n"
       (ppr source)))

(defn- fn-literal? [form]
  (and (list? form) ('#{fn fn*} (first form))))

(def literal?
  (some-fn keyword? number? string? nil? boolean?))

(defn- deps->literals [deps]
  (filter literal? deps))

(defn- lint-deps [form deps]
  (when deps
    (cond
      ;; when deps are passed as JS Array, should be a vector instead
      (and (= (type deps) JSValue) (vector? (.-val deps))) [::deps-array-literal {:source form :env (find-env-for-form ::deps-array-literal deps)}]

      ;; when deps are neither JS Array nor Clojure's vector, should be a vector instead
      (not (vector? deps)) [::deps-coll-literal {:source form :env (find-env-for-form ::deps-coll-literal deps)}]

      ;; when deps vector has a primitive literal, it can be safely removed
      (and (vector? deps) (seq (deps->literals deps)))
      [::literal-value-in-deps {:source form
                                :literals (deps->literals deps)
                                :env (find-env-for-form ::literal-value-in-deps deps)}])))

(defn find-hook-for-symbol [env sym]
  (when-let [init (-> env :locals (get sym) :init)]
    (let [form (:form init)]
      (when (list? form)
        (cond
          (and (= 'clojure.core/nth (first form)) (= 1 (nth form 2)))
          (recur (:env init) (second form))

          (hook? (first form))
          (name (first form)))))))

(def stable-hooks
  #{"use-state" "useState"
    "use-reducer" "useReducer"
    "use-ref" "useRef"
    "use-event" "useEvent"
    "use-effect-event" "useEffectEvent"})

(defn find-unnecessary-deps [env deps]
  (keep (fn [sym]
          (if-let [hook (find-hook-for-symbol env sym)]
            ;; stable hook
            (when (contains? stable-hooks hook)
              (with-meta sym {:hook hook}))
            ;; constant value
            (when (some-> env :locals (get sym) :init :op (= :const))
              sym)))
        deps))

(def state-hooks
  #{"use-state" "useState"
    "use-reducer" "useReducer"})

(defn find-unsafe-set-state-calls [env f]
  (let [set-state-calls (->> (find-local-variables env f)
                             (filter #(contains? state-hooks (find-hook-for-symbol env %)))
                             set)
        ast (ana-api/no-warn (ana-api/analyze env f))]
    (loop [[{:keys [children] :as node} & nodes] (:methods ast)
           unsafe-calls []]
      (if (= :fn (:op node))
        (recur nodes unsafe-calls)
        (let [child-nodes (mapcat #(let [child (get node %)]
                                     (if (map? child) [child] child))
                                  children)
              unsafe-calls (if (and (= :invoke (:op node))
                                    (-> node :fn :form set-state-calls))
                             (->> node :fn :form set-state-calls
                                  (conj unsafe-calls))
                             unsafe-calls)]
          (cond
            (seq child-nodes) (recur (concat child-nodes nodes) unsafe-calls)
            (seq nodes) (recur nodes unsafe-calls)
            :else (seq unsafe-calls)))))))

(defn find-missing-and-unnecessary-deps [env f deps]
  (let [free-vars (find-free-variables env f deps)
        all-unnecessary-deps (set (find-unnecessary-deps env (concat free-vars deps)))
        declared-unnecessary-deps (keep all-unnecessary-deps deps)
        missing-deps (filter (comp not all-unnecessary-deps) free-vars)
        suggested-deps (-> (filter (comp not (set declared-unnecessary-deps)) deps)
                           (into missing-deps))]
    [missing-deps declared-unnecessary-deps suggested-deps]))

(defn- lint-body [env form f deps]
  (cond
    ;; when a reference to a function passed into a hook, should be an inline function instead
    (not (fn-literal? f)) [::inline-function {:source form :env (find-env-for-form ::inline-function form)}]

    (and (vector? deps) (not (:lint/disable (meta deps))))
    (let [[missing-deps declared-unnecessary-deps suggested-deps] (find-missing-and-unnecessary-deps env f deps)]
      ;; when hook function is referencing vars from out scope that are missing in deps vector
      (when (or (seq missing-deps) (seq declared-unnecessary-deps))
        [::missing-deps {:missing-deps missing-deps
                         :unnecessary-deps declared-unnecessary-deps
                         :suggested-deps suggested-deps
                         :source form}]))

    (and (effect-hook? form) (nil? deps))
    (when-let [unsafe-calls (find-unsafe-set-state-calls env f)]
      ;; when set-state is called directly in a hook without deps, causing infinite loop
      [::unsafe-set-state {:unsafe-calls unsafe-calls
                           :source form
                           :env (find-env-for-form ::unsafe-set-state (first unsafe-calls))}])))

(defn lint-exhaustive-deps [env form f deps]
  (let [errors [(lint-deps form deps)
                (lint-body env form f deps)]]
    (filter identity errors)))

(defn lint-exhaustive-deps! [env form f deps]
  (doseq [[error-type opts] (lint-exhaustive-deps env form f deps)]
    (ana/warning error-type (or (:env opts) env) opts))
  (binding [*component-context* (atom {:errors []})]
    (run-linters! lint-hook-with-deps form env)
    (report-errors! env)))

(defn- keys-spec? [spec]
  (contains? #{'cljs.spec.alpha/keys
               'clojure.spec.alpha/keys}
             (first spec)))

(defn- comp-spec->keys [comp-spec]
  (let [specs (->> comp-spec
                   (map (comp
                          #(if (clojure.spec.alpha/spec? %)
                             (clojure.spec.alpha/form %)
                             %)
                          (merge @@#'cljs.spec.alpha/registry-ref
                                 @@#'clojure.spec.alpha/registry-ref))))]
    (doseq [spec (remove keys-spec? specs)]
      (assert (keys-spec? spec) (str "Only s/keys specs can be used to validate component props")))
    (->> specs
         (filter keys-spec?)
         (mapcat (comp #(apply concat %)
                       (fn [[req-un req]]
                         [(map (comp keyword name) req-un)
                          req])
                       (juxt :req-un :req)
                       #(apply array-map %)
                       rest)))))

(defn- list-missing-props-keys [tag attrs children env]
  (let [ns (-> env :ns :name)
        props-cond (get-in @env/*compiler* [::ana/namespaces ns :uix/specs tag])]
    (when props-cond
      (let [missing-keys (->> (comp-spec->keys props-cond)
                              (remove #(if (= :children %)
                                         (or (contains? attrs %)
                                             (seq children))
                                         (contains? attrs %))))]
        (when (seq missing-keys)
          (ana/warning ::missing-props-keys (merge env (uix.linter/form->loc (or attrs tag))) {:missing-keys (vec missing-keys)}))))))


(defmethod ana/error-message ::missing-props-keys [_ {:keys [missing-keys]}]
  (str "Required keys are missing in props: " (str/join ", " missing-keys)))

(defmethod lint-element ::missing-props-keys [type form env]
  (let [v (rest form)
        [tag attrs & children :as v] (uix.lib/normalize-element env v)]
    (when (and (uix.lib/cljs-env? env)
               (symbol? tag)
               (or (map? attrs) (== 1 (count v))))
      (list-missing-props-keys tag attrs children env))))

(defn lint-element* [form env]
  (binding [*component-context* (atom {:errors []})]
    (run-linters! lint-element form env)
    (report-errors! env)))
