(ns uix.core.lazy-loader
  #?(:clj (:require [clojure.spec.alpha :as s]
                    [uix.specs.alpha]
                    [uix.lib])
     :cljs (:require [shadow.lazy]
                     [react])))

#?(:cljs
   (def react-lazy react/lazy))

#?(:clj
   (s/fdef require-lazy
     :args (s/cat :form :lazy/libspec)))

#?(:clj
   (defn require-lazy [form]
     (let [m (s/conform :lazy/libspec form)]
       (when (not= m :clojure.spec.alpha/invalid)
         (let [{:keys [lib refer]} (:libspec m)]
           `(do
              ~@(for [sym refer]
                  (let [qualified-sym (symbol (str lib "/" sym))]
                    `(def ~sym
                       (let [loadable# (shadow.lazy/loadable ~qualified-sym)]
                         (react-lazy #(-> (shadow.lazy/load loadable#)
                                          (.then (fn [f#] (cljs.core/js-obj "default" f#)))))))))))))))
