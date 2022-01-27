(ns uix.core.lazy-loader
  #?(:cljs (:require-macros [uix.core.lazy-loader]))
  #?(:clj (:require [clojure.spec.alpha :as s]
                    [uix.specs.alpha]
                    [uix.lib])
     :cljs (:require [shadow.loader :as loader]
                     [react])))

#?(:cljs
   (def react-lazy react/lazy))

#?(:cljs
   (def load! loader/load))

#?(:clj
   (s/fdef require-lazy
     :args (s/cat :form :lazy/libspec :module-name keyword?)))

#?(:clj
   (defmacro require-lazy
     "require-like macro, returns lazy-loaded React components.

     (require-lazy '[my.ns.components :refer [c1 c2]] :shadow-module-name)"
     [form module-name]
     (let [m (s/conform :lazy/libspec form)]
       (when (not= m :clojure.spec.alpha/invalid)
         (let [{:keys [lib refer]} (:libspec m)]
           `(do
              ~@(for [sym refer]
                  (let [qualified-sym (symbol (str lib "/" sym))
                        required-var `(deref (cljs.core/resolve '~qualified-sym))
                        js-export `(cljs.core/js-obj "default" ~required-var)]
                    `(def ~sym
                       (react-lazy #(-> (load! ~(name module-name))
                                        (.then (fn [] ~js-export)))))))))))))
