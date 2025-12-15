(ns uix.compiler.aot
  "Runtime helpers for HyperScript compiled into React"
  (:require [clojure.string :as str]
            [react :as react]
            [uix.compiler.input]
            [uix.compiler.alpha :as uixc]
            [uix.compiler.attributes :as attrs]
            [uix.lib :refer [doseq-loop]]))

(def react-19+?
  (-> react/version
      (.split ".")
      first
      js/parseInt
      (>= 19)))

(defn hiccup? [el]
  (when (vector? el)
    (let [tag (nth el 0 nil)]
      (or (keyword? tag)
          (symbol? tag)
          (fn? tag)
          (instance? MultiFn tag)))))

(defn validate-children [children]
  (doseq-loop [child children]
    (cond
      (hiccup? child)
      (throw (js/Error. (str "Hiccup is not valid as UIx child (found: " child ").\n"
                             "If you meant to render UIx element, use `$` macro, i.e. ($ " (str/join " " child) ")\n"
                             "If you meant to render Reagent element, wrap it with r/as-element, i.e. (r/as-element " child ")")))

      (sequential? child)
      (validate-children child)))
  true)

(defn >el [tag attrs-children children]
  (let [args (.concat #js [tag] attrs-children)]
    (when ^boolean goog.DEBUG
      (validate-children args))
    (uixc/create-element args children)))

(defn create-uix-input [tag attrs-children children]
  (if (uix.compiler.input/should-use-reagent-input?)
    (let [props (aget attrs-children 0)
          children (.concat #js [(aget attrs-children 1)] children)]
      (uixc/create-element #js [uix.compiler.input/reagent-input #js {:props props :tag tag}] children))
    (>el tag attrs-children children)))

(def fragment react/Fragment)

(defn merge-props [static-class props]
  (let [props (js* "Object.assign(...~{})" props)
        class-name (.-className props)]
    (when (and class-name (not (.includes class-name static-class)))
      (set! (.-className props) (attrs/class-names static-class class-name)))
    props))