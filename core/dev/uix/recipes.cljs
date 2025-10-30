(ns uix.recipes
  (:require [uix.core :refer [defui $] :as uix]
            [uix.recipes.global-state :as global-state]
            [uix.recipes.interop :as interop]
            [uix.recipes.error-boundary :as error-boundary]
            [uix.recipes.context :as context]))

(def recipes
  {:global-state global-state/recipe
   :interop interop/recipe
   :error-boundary error-boundary/recipe
   :context context/recipe})

(defui root []
  (let [[current-recipe set-current-recipe] (uix/use-state :global-state)]
    ($ :div {:style {:padding 24}}
       ($ :div {:style {:margin-bottom 16}}
          ($ :span "Select recipe: ")
          ($ :select {:value current-recipe
                      :on-change #(set-current-recipe (keyword (.. % -target -value)))}
             (for [[k _] recipes]
               ($ :option
                  {:key k :value k}
                  (name k)))))
       (when-let [recipe (recipes current-recipe)]
         ($ :div ($ recipe))))))