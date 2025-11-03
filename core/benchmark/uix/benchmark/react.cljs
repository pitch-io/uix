(ns uix.benchmark.react)

(defn InputField
  [^js props]
  (.createElement
    js/React
    "input"
    #js {:className #js ["form-control" (aget #js {:large "form-control-lg"} (.-size props))],
         :type (.-type props),
         :placeholder (.-placeholder props),
         :style #js {:border "1px solid blue", :borderRadius 3, :padding "4px 8px"}}))

(defn Button
  [^js props]
  (.createElement
    js/React
    "button"
    #js
        {:className
         #js
             ["btn" (aget #js {:large "btn-large"} (.-size props))
              (aget #js {:primary "btn-primary"} (.-kind props))
              (.-className props)],
         :style
         #js
             {:padding "8px 24px",
              :color "white",
              :background "blue",
              :fontSize "11px",
              :textTransform "uppercase",
              :textAlign "center"}}
    (.-children props)))

(defn Fieldset
  [^js props]
  (.createElement
    js/React
    "fieldset"
    #js {:className "form-group", :style #js {:padding 8, :border "none"}}
    (.-children props)))

(defn Form [props] (.createElement js/React "form" #js {} (.-children props)))

(defn Row
  [^js props]
  (.createElement js/React "div" #js {:className "row"} (.-children props)))

(defn Col
  [^js props]
  (.createElement
    js/React
    "div"
    #js
        {:className
         #js
             [(str "col-md-" (.-md props)) (str "col-xs-" (.-xs props))
              (str "offset-md-" (.-offsetMd props))]}
    (.-children props)))

(defn Editor
  []
  (.createElement
    js/React
    "div"
    #js {:className "editor-page"}
    (.createElement
      js/React
      "div"
      #js {:className "container page"}
      (.createElement
        js/React
        Row
        #js {}
        (.createElement
          js/React
          Col
          #js {:md 10, :xs 12, :offsetMd 1}
          (.createElement
            js/React
            Form
            #js {}
            (.createElement
              js/React
              "fieldset"
              #js {}
              (.createElement
                js/React
                Fieldset
                #js {}
                (.createElement
                  js/React
                  InputField
                  #js
                      {:type "text", :placeholder "Article Title", :size "large"}))
              (.createElement
                js/React
                Fieldset
                #js {}
                (.createElement
                  js/React
                  InputField
                  #js
                      {:type "text", :placeholder "What's this article about?"}))
              (.createElement
                js/React
                Fieldset
                #js {}
                (.createElement
                  js/React
                  InputField
                  #js
                      {:rows 8,
                       :fieldType "textarea",
                       :placeholder "Write your article (in markdown)"}))
              (.createElement
                js/React
                Fieldset
                #js {}
                (.createElement
                  js/React
                  InputField
                  #js {:type "text", :placeholder "Enter tags"})
                (.createElement js/React "div" #js {:className "tag-list"}))
              (.createElement
                js/React
                Button
                #js {:size "large", :kind "primary", :className "pull-xs-right"}
                "Update Article"))))))))

