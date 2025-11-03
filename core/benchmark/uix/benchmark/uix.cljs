(ns uix.benchmark.uix
  (:require [uix.core :refer [defui $]]))

(defui input-field-compiled
  [{:keys [field-type type placeholder size]
    :or {field-type :input}}]
  (if (= field-type :textarea)
    ($ :textarea
       {:class ["form-control" (get {:large "form-control-lg"} size)]
        :type type
        :placeholder placeholder
        :style {:border "1px solid blue"
                :border-radius 3
                :padding "4px 8px"}})
    ($ :input
       {:class ["form-control" (get {:large "form-control-lg"} size)]
        :type type
        :placeholder placeholder
        :style {:border "1px solid blue"
                :border-radius 3
                :padding "4px 8px"}})))

(defui button-compiled [{:keys [size kind class children]}]
  ($ :button.btn
     {:class [(get {:large "btn-lg"} size)
              (get {:primary "btn-primary"} kind)
              class]
      :style {:padding "8px 24px"
              :color :white
              :background :blue
              :font-size "11px"
              :text-transform :uppercase
              :text-align :center}}
     children))

(defui fieldset-compiled [{:keys [children]}]
  ($ :fieldset.form-group
     {:style {:padding 8
              :border :none}}
     children))

(defui form-compiled [{:keys [children]}]
  ($ :form children))

(defui row-compiled [{:keys [children]}]
  ($ :div.row children))

(defui col-compiled [{:keys [md xs offset-md children]}]
  ($ :div {:class [(str "col-md-" md)
                   (str "col-xs-" xs)
                   (str "offset-md-" offset-md)]}
     children))

(defui editor-compiled []
  ($ :div.editor-page
     ($ :div.container.page
        ($ row-compiled
           ($ col-compiled
              {:md 10
               :xs 12
               :offset-md 1}
              ($ form-compiled
                 ($ :fieldset
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
                       ($ :div.tag-list))
                    ($ button-compiled
                       {:size :large
                        :kind :primary
                        :class "pull-xs-right"}
                       "Update Article"))))))))
