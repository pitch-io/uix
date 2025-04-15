(ns hooks.uix
  (:require [clj-kondo.hooks-api :as api]))

(defn $ [{:keys [node]}]
  (let [[sym _args] (rest (api/sexpr node))]
    (when-not (or (symbol? sym)
                  (keyword? sym)
                  (list? sym))
      (api/reg-finding! (-> (meta node)
                            (merge {:message "First arg to $ must be a symbol, keyword, or dynamic element"
                                    :type    :uix.core/$-arg-validation}))))))
