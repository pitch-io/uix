(ns uix.recipes.context
  (:require [uix.core :refer [defui defcontext $] :as uix]))

;; React's context is similar to Clojure's `binding` macro
;; in a sense that it allows you to redefine a global var
;; locally to a specific UI subtree

;; create React context
(defcontext *ctx* nil)

(defui button [{:keys [on-click]}]
  ;; consume a value from the context
  (let [v (uix/use-context *ctx*)]
    ($ :button {:on-click on-click}
       (str "+ " v))))

(defui recipe []
  (let [[value set-value] (uix/use-state 0)]
    ;; render the context with provided value
    ($ *ctx* {:value value}
      ($ button {:on-click #(set-value inc)}))))
