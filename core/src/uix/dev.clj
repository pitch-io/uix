(ns uix.dev
  (:require [clojure.string :as str]
            [uix.linter]
            [uix.lib]
            [hickory.core :as h])
  (:import (cljs.tagged_literals JSValue)))

(def ^:private goog-debug (with-meta 'goog.DEBUG {:tag 'boolean}))

(defn with-fast-refresh [var-sym fdecl]
  (let [signature `(when ~goog-debug
                     (when-let [f# (.-fast-refresh-signature ~var-sym)]
                       (f#)))
        maybe-conds (first fdecl)]
    (if (and (map? maybe-conds) (or (:pre maybe-conds) (:post maybe-conds)))
      (cons maybe-conds (cons signature (rest fdecl)))
      (cons signature fdecl))))

(defn- rewrite-form
  "Rewrites a form to replace generated names with stable names
  to make sure that hook's signature for fast-refresh does not update
  on every compilation, unless there was a change in the hook's body."
  [form]
  (clojure.walk/prewalk
   (fn [x]
     (cond
       (and (symbol? x) (re-matches #"^p\d*__\d+#$" (str x)))
       (symbol (str/replace (str x) #"__\d+#$" ""))

       (= (type x) JSValue)
       (.-val x)

       :else x))
   form))

(defn- rewrite-forms [forms]
  (map rewrite-form forms))

(defn fast-refresh-signature [var-sym body]
  `(when ~goog-debug
     (when (cljs.core/exists? js/globalThis.uix.dev)
       (let [sig# (js/globalThis.uix.dev.signature!)]
         (sig# ~var-sym ~(str/join (rewrite-forms (uix.lib/find-form uix.linter/hook-call? body))) nil nil)
         (js/globalThis.uix.dev.register! ~var-sym (.-displayName ~var-sym))
         (set! (.-fast-refresh-signature ~var-sym) sig#)))))

(defn from-hiccup [form]
  (cond
    (seq? form)
    (mapv from-hiccup form)

    (vector? form)
    (let [form (cond
                 (= :> (first form))
                 (rest form)

                 :else form)
          [tag attrs & children] form
          [attrs children] (cond
                             (map? attrs) [attrs children]
                             (> (count form) 1) [nil (into [attrs] children)]
                             :else [nil children])
          attrs (cond-> attrs
                  (contains? (meta form) :key)
                  (assoc :key (:key (meta form))))
          children (map from-hiccup children)]
      (if attrs
        `(~'$ ~tag ~attrs ~@children)
        `(~'$ ~tag ~@children)))

    :else form))

(comment
  (from-hiccup
   [:div
    [:div {:class "foo"} "bar"]
    [:> 'js-component]
    [:<> [:button "hello"] [:span "world"]]
    ^{:key "hello"} [:span "world"]]))

(defn from-html [html-str]
  (map (comp from-hiccup h/as-hiccup)
       (h/parse-fragment html-str)))

(comment
  (from-html
   "<p class=\"c-fDhfVa c-fDhfVa-dkirSI-spaced-true c-fDhfVa-jFCKZD-family-default c-fDhfVa-grGuE-size-3 c-fDhfVa-hYBDYy-variant-default c-fDhfVa-kHnRXL-weight-2\">Finally, one last scene, just for fun! I ported to React Three Fiber a Three.js demo from <a href=\"http://barradeau.com/blog/?p=621\" class=\"c-iNkjEl c-iNkjEl-dNnDWN-underline-true c-iNkjEl-igJWTOZ-css\">an article</a> written by <a href=\"https://twitter.com/nicoptere\" class=\"c-iNkjEl c-iNkjEl-hGYKvZ-discreet-true c-iNkjEl-goIlEV-favicon-true c-iNkjEl-idwngVA-css\">@nicoptere</a> that does a pretty good job at deep diving into the FBO technique.</p>"))