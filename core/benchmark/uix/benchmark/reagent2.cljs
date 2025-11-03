(ns uix.benchmark.reagent2
  (:require [reagent.core :refer [defc]]))

(defc input-field [{:keys [field-type type placeholder size]
                    :or {field-type :input}}]
  [field-type
   {:class ["form-control" (get {:large "form-control-lg"} size)]
    :type type
    :placeholder placeholder
    :style {:border "1px solid blue"
            :border-radius 3
            :padding "4px 8px"}}])

(defc button [{:keys [size kind class]} child]
  [:button.btn
   {:class [(get {:large "btn-lg"} size)
            (get {:primary "btn-primary"} kind)
            class]
    :style {:padding "8px 24px"
            :color :white
            :background :blue
            :font-size "11px"
            :text-transform :uppercase
            :text-align :center}}
   child])

(defc fieldset [& children]
  (into [:fieldset.form-group
         {:style {:padding 8
                  :border :none}}]
        children))

(defc form [& children]
  (into [:form] children))

(defc row [& children]
  (into [:div.row] children))

(defc col [{:keys [md xs offset-md]} & children]
  (into
    [:div {:class [(str "col-md-" md)
                   (str "col-xs-" xs)
                   (str "offset-md-" offset-md)]}]
    children))

(defc editor []
  [:div.editor-page
   [:div.container.page
    [row
     [col {:md 10
           :xs 12
           :offset-md 1}
      [form
       [:fieldset
        [fieldset
         [input-field {:type "text"
                       :placeholder "Article Title"
                       :size :large}]]
        [fieldset
         [input-field {:type "text"
                       :placeholder "What's this article about?"}]]
        [fieldset
         [input-field {:rows "8"
                       :field-type :textarea
                       :placeholder "Write your article (in markdown)"}]]
        [fieldset
         [input-field {:type "text"
                       :placeholder "Enter tags"}]
         [:div.tag-list]]
        [button
         {:size :large
          :kind :primary
          :class "pull-xs-right"}
         "Update Article"]]]]]]])
