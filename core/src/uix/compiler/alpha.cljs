(ns uix.compiler.alpha
  "UTL and UIx components interpreter. Based on Reagent."
  (:require [react :as react]
            [uix.hooks.alpha :as hooks]
            [cljs-bean.core :as bean]))

(def ^:dynamic *default-compare-args* #(= (.-argv %1) (.-argv %2)))

(defn js-val? [x]
  (not (identical? "object" (goog/typeOf x))))

(defn symbol-for [s]
  (js* "Symbol.for(~{})" s))

(defn as-react [f]
  #(f (bean/bean %)))

(defn validate-component [^js component-type]
  (when (and (not ^boolean (.-uix-component? component-type))
             (not= (symbol-for "react.lazy") (.-$$typeof component-type)))
    (let [name-str (or (.-displayName component-type)
                       (.-name component-type))]
      (throw (js/Error. (str "Invalid use of a non-UIx component " name-str " in #el form.\n"
                             "If you meant to render React element, use :> syntax for interop with JavaScript components, i.e. #el [:> " name-str "]\n"
                             "If you meant to render Reagent element, make it Hiccup wrapped with r/as-element, i.e. (r/as-element [" name-str "])")))))
  true)

(defn component-element [component-type ^js props-children children]
  (when ^boolean goog.DEBUG
    (validate-component component-type))
  (let [props (aget props-children 0)
        js-props (if-some [key (:key props)]
                   #js {:key key :argv (dissoc props :key)}
                   #js {:argv props})
        args (if (= 2 (.-length props-children))
               #js [component-type js-props (aget props-children 1)]
               #js [component-type js-props])]
    (.apply react/createElement nil (.concat args children))))
