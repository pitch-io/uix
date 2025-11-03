(ns uix.benchmark.helix
  (:require [helix.core :refer [defnc $]]
            [helix.dom :as d]))

(defnc input-field-compiled
  [{:keys [field-type type placeholder size]
    :or {field-type :input}}]
  (if (= field-type :textarea)
    (d/textarea
       {:class ["form-control" (get {:large "form-control-lg"} size)]
        :type type
        :placeholder placeholder
        :style {:border "1px solid blue"
                :border-radius 3
                :padding "4px 8px"}})
    (d/input
       {:class ["form-control" (get {:large "form-control-lg"} size)]
        :type type
        :placeholder placeholder
        :style {:border "1px solid blue"
                :border-radius 3
                :padding "4px 8px"}})))

(defnc button-compiled [{:keys [size kind class children]}]
  (d/button
     {:class ["btn"
              (get {:large "btn-lg"} size)
              (get {:primary "btn-primary"} kind)
              class]
      :style {:padding "8px 24px"
              :color :white
              :background :blue
              :font-size "11px"
              :text-transform :uppercase
              :text-align :center}}
     children))

(defnc fieldset-compiled [{:keys [children]}]
  (d/fieldset
     {:class "form-group"
      :style {:padding 8
              :border :none}}
     children))

(defnc form-compiled [{:keys [children]}]
  (d/form children))

(defnc row-compiled [{:keys [children]}]
  (d/div {:class "row"} children))

(defnc col-compiled [{:keys [md xs offset-md children]}]
  (d/div {:class [(str "col-md-" md)
                  (str "col-xs-" xs)
                  (str "offset-md-" offset-md)]}
     children))

(defnc editor-compiled []
  (d/div {:class "editor-page"}
     (d/div {:class "container page"}
        ($ row-compiled
           ($ col-compiled
              {:md 10
               :xs 12
               :offset-md 1}
              ($ form-compiled
                 (d/fieldset
                    ($ fieldset-compiled
                       ($ input-field-compiled
                          {:type "text"
                           :placeholder "Article Title"
                           :size :large}))
                    ($ fieldset-compiled
                       ($ input-field-compiled
                          {:type "text"
                           :placeholder "What's this article about?"}))
                    ($ fieldset-compiled
                       ($ input-field-compiled
                          {:rows "8"
                           :field-type :textarea
                           :placeholder "Write your article (in markdown)"}))
                    ($ fieldset-compiled
                       ($ input-field-compiled
                          {:type "text"
                           :placeholder "Enter tags"})
                       (d/div {:class "tag-list"}))
                    ($ button-compiled
                       {:size :large
                        :kind :primary
                        :class "pull-xs-right"}
                       "Update Article"))))))))
