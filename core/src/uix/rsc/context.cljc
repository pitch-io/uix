(ns uix.rsc.context
  (:require [uix.core :refer [defui $]]
            #?(:cljs [uix.rsc])))

#?(:cljs
   (defn- with-providers [context children]
     (if (seq context)
       (let [[[k v] & context] context
             el (@uix.core/context-reg k)]
         ($ el {:value v}
            (with-providers context children)))
       children)))

(defui ^:client context-provider
  [{:keys [children]
    :uix/keys [context]}]
  #?(:clj children
     :cljs (with-providers context children)))
