(ns uix.compiler.alpha
  (:require [react]
            [goog.object :as gobj]
            [uix.compiler.attributes :as attrs]
            [clojure.string :as str]))

(defn- reagent-component? [^js component-type]
  (->> (.keys js/Object component-type)
       (some #(when (str/starts-with? % "G_")
                (identical? component-type (gobj/get component-type %))))))

(defn validate-component [^js component-type]
  (when (and (not (.-uix-component? component-type))
             (reagent-component? component-type))
    (let [name-str (or (.-displayName component-type)
                       (.-name component-type))]
      (throw (js/Error. (str "Invalid use of Reagent component " name-str " in `$` form.\n"
                             "UIx doesn't know how to render Reagent components.\n"
                             "Reagent element should be Hiccup wrapped with r/as-element, i.e. (r/as-element [" name-str "])")))))
  true)

(defn- normalise-args [component-type js-props props-children]
  (if (== 2 (.-length ^js props-children))
    #js [component-type js-props (aget props-children 1)]
    #js [component-type js-props]))

(defn- react-context? [x]
  (identical? (aget x "$$typeof")
              (.for js/Symbol "react.context")))



(defn- pojo? [x]
  (and (not (.hasOwnProperty x "$$typeof"))
       (some-> x .-constructor (identical? js/Object))))

(defn- js-props? [tag props]
  (and (or (string? tag) (not (.-uix-component? ^js tag)))
       props (pojo? props)))

(defn create-element [args children]
  (let [tag (aget args 0)
        props (aget args 1)
        child (aget args 2)]
    (if (js-props? tag child)
      ;; merge dynamic js props onto static ones
      (.apply react/createElement nil (.concat #js [tag (js/Object.assign props child)] children))
      (.apply react/createElement nil (.concat args children)))))

(defn- uix-component-element [component-type ^js props-children children]
  (let [props (aget props-children 0)
        js-props (if-some [key (:key props)]
                   #js {:key key :argv (dissoc props :key)}
                   #js {:argv props})
        args (normalise-args component-type js-props props-children)]
    (create-element args children)))

(defn- react-context-element [component-type ^js props-children children]
  (let [component-type (.-Provider ^js component-type)
        props (aget props-children 0)
        js-props #js {:value (:value props)}
        args (normalise-args component-type js-props props-children)]
    (create-element args children)))

(defn- react-component-element [component-type ^js props-children children]
  (let [js-props (-> (aget props-children 0)
                     (attrs/interpret-attrs #js [] true)
                     (aget 0))
        args (normalise-args component-type js-props props-children)]
    (create-element args children)))

(defn- dynamic-element [component-type ^js props-children children]
  (let [tag-id-class (attrs/parse-tag component-type)
        js-props (-> (aget props-children 0)
                     (attrs/interpret-attrs tag-id-class false)
                     (aget 0))
        tag (aget tag-id-class 0)
        args (normalise-args tag js-props props-children)]
    (create-element args children)))

(defn component-element [^clj component-type props-children children]
  (when ^boolean goog.DEBUG
    (validate-component component-type))
  (cond
    (.-uix-component? component-type)
    (uix-component-element component-type props-children children)

    (keyword? component-type)
    (dynamic-element component-type props-children children)

    (react-context? component-type)
    (react-context-element component-type props-children children)

    :else (react-component-element component-type props-children children)))
