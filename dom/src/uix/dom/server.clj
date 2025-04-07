(ns uix.dom.server
  "Server-side rendering on JVM.
  Based on https://github.com/tonsky/rum/blob/gh-pages/src/rum/server_render.clj"
  (:require [clojure.string :as str]
            [uix.compiler.attributes :as uix.attrs])
  (:import [clojure.lang IPersistentVector ISeq Ratio Keyword]))

(def ^:dynamic *select-value*)

(defprotocol IStringBuilder
  (append! [sb s0] [sb s0 s1] [sb s0 s1 s2] [sb s0 s1 s2 s3] [sb s0 s1 s2 s3 s4]))

(deftype StreamBuilder [on-chunk]
  IStringBuilder
  (append! [sb s0]
    (on-chunk s0))
  (append! [sb s0 s1]
    (on-chunk s0)
    (on-chunk s1))
  (append! [sb s0 s1 s2]
    (on-chunk s0)
    (on-chunk s1)
    (on-chunk s2))
  (append! [sb s0 s1 s2 s3]
    (on-chunk s0)
    (on-chunk s1)
    (on-chunk s2)
    (on-chunk s3))
  (append! [sb s0 s1 s2 s3 s4]
    (on-chunk s0)
    (on-chunk s1)
    (on-chunk s2)
    (on-chunk s3)
    (on-chunk s4)))

(defn make-stream-builder [on-chunk]
  (StreamBuilder. on-chunk))

(deftype StaticBuilder [^StringBuilder sb]
  IStringBuilder
  (append! [o s0]
    (.append sb s0))
  (append! [o s0 s1]
    (.append sb s0)
    (.append sb s1))
  (append! [o s0 s1 s2]
    (.append sb s0)
    (.append sb s1)
    (.append sb s2))
  (append! [o s0 s1 s2 s3]
    (.append sb s0)
    (.append sb s1)
    (.append sb s2)
    (.append sb s3))
  (append! [o s0 s1 s2 s3 s4]
    (.append sb s0)
    (.append sb s1)
    (.append sb s2)
    (.append sb s3)
    (.append sb s4)))

(defn make-static-builder []
  (StaticBuilder. (StringBuilder.)))

(defprotocol ToString
  (^String to-str [x] "Convert a value into a string."))

(extend-protocol ToString
  Keyword (to-str [k] (name k))
  Ratio (to-str [r] (str (float r)))
  String (to-str [s] s)
  Object (to-str [x] (str x))
  nil (to-str [_] ""))

(def ^{:doc "A list of elements that must be rendered without a closing tag."
       :private true}
  void-tags
  #{"area" "base" "br" "col" "command" "embed" "hr" "img" "input" "keygen" "link"
    "meta" "param" "source" "track" "wbr"})

;; https://github.com/facebook/react/blob/ca106a02d1648f4f0048b07c6b88f69aac175d3c/packages/react-dom/src/shared/possibleStandardNames.js
(def normalized-attrs
  {;; special cases
   :default-checked "checked"
   :default-value "value"

   ;; https://github.com/facebook/react/blob/v15.6.2/src/renderers/dom/shared/HTMLDOMPropertyConfig.js
   :accept-charset "accept-charset"
   :access-key "accessKey"
   :allow-full-screen "allowFullScreen"
   :allow-transparency "allowTransparency"
   :auto-complete "autoComplete"
   :auto-play "autoPlay"
   :cell-padding "cellPadding"
   :cell-spacing "cellSpacing"
   :char-set "charSet"
   :class-id "classID"
   :col-span "colSpan"
   :content-editable "contentEditable"
   :context-menu "contextMenu"
   :cross-origin "crossorigin"
   :date-time "dateTime"
   :enc-type "encType"
   :form-action "formAction"
   :form-enc-type "formEncType"
   :form-method "formMethod"
   :form-no-validate "formNoValidate"
   :form-target "formTarget"
   :frame-border "frameBorder"
   :href-lang "hrefLang"
   :http-equiv "http-equiv"
   :input-mode "inputMode"
   :key-params "keyParams"
   :key-type "keyType"
   :margin-height "marginHeight"
   :margin-width "marginWidth"
   :max-length "maxLength"
   :media-group "mediaGroup"
   :min-length "minLength"
   :no-validate "noValidate"
   :radio-group "radioGroup"
   :referrer-policy "referrerPolicy"
   :read-only "readOnly"
   :row-span "rowSpan"
   :spell-check "spellCheck"
   :src-doc "srcDoc"
   :src-lang "srcLang"
   :src-set "srcSet"
   :tab-index "tabindex"
   :use-map "useMap"
   :auto-capitalize "autoCapitalize"
   :auto-correct "autoCorrect"
   :auto-save "autoSave"
   :item-prop "itemProp"
   :item-scope "itemScope"
   :item-type "itemType"
   :item-id "itemID"
   :item-ref "itemRef"

   ;; https://github.com/facebook/react/blob/v15.6.2/src/renderers/dom/shared/SVGDOMPropertyConfig.js
   :allow-reorder "allowReorder"
   :attribute-name "attributeName"
   :attribute-type "attributeType"
   :auto-reverse "autoReverse"
   :base-frequency "baseFrequency"
   :base-profile "baseProfile"
   :calc-mode "calcMode"
   :clip-path-units "clipPathUnits"
   :content-script-type "contentScriptType"
   :content-style-type "contentStyleType"
   :diffuse-constant "diffuseConstant"
   :edge-mode "edgeMode"
   :external-resources-required "externalResourcesRequired"
   :filter-res "filterRes"
   :filter-units "filterUnits"
   :glyph-ref "glyphRef"
   :gradient-transform "gradientTransform"
   :gradient-units "gradientUnits"
   :kernel-matrix "kernelMatrix"
   :kernel-unit-length "kernelUnitLength"
   :key-points "keyPoints"
   :key-splines "keySplines"
   :key-times "keyTimes"
   :length-adjust "lengthAdjust"
   :limiting-cone-angle "limitingConeAngle"
   :marker-height "markerHeight"
   :marker-units "markerUnits"
   :marker-width "markerWidth"
   :mask-content-units "maskContentUnits"
   :mask-units "maskUnits"
   :num-octaves "numOctaves"
   :path-length "pathLength"
   :pattern-content-units "patternContentUnits"
   :pattern-transform "patternTransform"
   :pattern-units "patternUnits"
   :points-at-x "pointsAtX"
   :points-at-y "pointsAtY"
   :points-at-z "pointsAtZ"
   :preserve-alpha "preserveAlpha"
   :preserve-aspect-ratio "preserveAspectRatio"
   :primitive-units "primitiveUnits"
   :ref-x "refX"
   :ref-y "refY"
   :repeat-count "repeatCount"
   :repeat-dur "repeatDur"
   :required-extensions "requiredExtensions"
   :required-features "requiredFeatures"
   :specular-constant "specularConstant"
   :specular-exponent "specularExponent"
   :spread-method "spreadMethod"
   :start-offset "startOffset"
   :std-deviation "stdDeviation"
   :stitch-tiles "stitchTiles"
   :surface-scale "surfaceScale"
   :system-language "systemLanguage"
   :table-values "tableValues"
   :target-x "targetX"
   :target-y "targetY"
   :view-box "viewBox"
   :view-target "viewTarget"
   :x-channel-selector "xChannelSelector"
   :xlink-actuate "xlink:actuate"
   :xlink-arcrole "xlink:arcrole"
   :xlink-href "xlink:href"
   :xlink-role "xlink:role"
   :xlink-show "xlink:show"
   :xlink-title "xlink:title"
   :xlink-type "xlink:type"
   :xml-base "xml:base"
   :xmlns-xlink "xmlns:xlink"
   :xml-lang "xml:lang"
   :xml-space "xml:space"
   :y-channel-selector "yChannelSelector"
   :zoom-and-pan "zoomAndPan"})

(defn normalize-attr-key ^String [key]
  (or (normalized-attrs key)
      (when (.startsWith (name key) "on")
        (-> (name key) (str/lower-case) (str/replace "-" "")))
      (name key)))

(defn get-value [attrs]
  (or (:value attrs)
      (:default-value attrs)))

(defn escape-html [^String s]
  (let [len (count s)]
    (loop [^StringBuilder sb nil
           i (int 0)]
      (if (< i len)
        (let [char (.charAt s i)
              repl (case char
                     \& "&amp;"
                     \< "&lt;"
                     \> "&gt;"
                     \" "&quot;"
                     \' "&#x27;"
                     nil)]
          (if (nil? repl)
            (if (nil? sb)
              (recur nil (inc i))
              (recur (doto sb
                       (.append char))
                     (inc i)))
            (if (nil? sb)
              (recur (doto (StringBuilder.)
                       (.append s 0 i)
                       (.append repl))
                     (inc i))
              (recur (doto sb
                       (.append repl))
                     (inc i)))))
        (if (nil? sb) s (str sb))))))

(defn- normalize-children
  "Elements at children position take precedence over elements in :children attribute"
  [props children]
  (let [children (if (seq children) children (:children props))]
    [(dissoc props :children)
     (when children
       (if (coll? children) children [children]))]))

(defn normalize-element [[first second & rest]]
  (let [[tag tag-id tag-classes] (uix.attrs/parse-tag first)
        [attrs children] (cond
                           (map? second) (normalize-children second rest)
                           (nil? second) [nil rest]
                           :else [nil (cons second rest)])
        attrs-classes (if (vector? (:class attrs))
                        (let [c (filter some? (:class attrs))]
                          (when (seq c)
                            (vec c)))
                        (:class attrs))
        classes (if (and tag-classes attrs-classes)
                  (flatten [tag-classes attrs-classes])
                  (or tag-classes attrs-classes))
        ;; preserving insertion order
        attrs (into {}
                    (keep (fn [[k v]]
                            (cond
                              (and (= :class k) classes) [k classes]
                              (= :class k) nil
                              :else [k v])))
                    attrs)
        attrs (into
               (cond-> {}
                 tag-id (assoc :id tag-id)
                 (contains? attrs :id) (assoc :id (:id attrs))
                 classes (assoc :class classes))
               (dissoc attrs :id :class))]
    [tag attrs children]))

;;; render attributes
;; https://github.com/facebook/react/blob/main/packages/react-dom/src/shared/CSSProperty.js
(def unitless-css-props
  (into #{}
        (for [key ["animation-iteration-count","aspect-ratio","border-image-outset","border-image-slice","border-image-width","box-flex","box-flex-group","box-ordinal-group","column-count","columns","flex","flex-grow","flex-positive","flex-shrink","flex-negative","flex-order","grid-area","grid-row","grid-row-end","grid-row-span","grid-row-start","grid-column","grid-column-end","grid-column-span","grid-column-start","font-weight","line-clamp","line-height","opacity","order","orphans","tab-size","widows","z-index","zoom","fill-opacity","flood-opacity","stop-opacity","stroke-dasharray","stroke-dashoffset","stroke-miterlimit","stroke-opacity","stroke-width"]
              prefix ["" "-webkit-" "-ms-" "-moz-" "-o-"]]
          (str prefix key))))

(defn normalize-css-key [k]
  (-> (to-str k)
      (str/replace #"[A-Z]" (fn [ch] (str "-" (str/lower-case ch))))
      (str/replace #"^ms-" "-ms-")))

(defn normalize-css-value [key value]
  (cond
    (contains? unitless-css-props key)
    (escape-html (str/trim (to-str value)))
    (number? value)
    (str value (when (not= 0 value) "px"))
    :else (escape-html (str/trim (to-str value)))))

(defn render-style-kv! [sb empty? k v]
  (if (and v (not= v ""))
    (do
      (if empty?
        (append! sb " style=\"")
        (append! sb ";"))
      (let [key (normalize-css-key k)
            val (normalize-css-value key v)]
        (append! sb key ":" val))
      false)
    empty?))

(defn render-style! [map sb]
  (let [empty? (reduce-kv (partial render-style-kv! sb) true map)]
    (when-not empty?
      (append! sb "\""))))

(defn render-class! [sb first? class]
  (cond
    (nil? class)
    first?

    (string? class)
    (do
      (when-not first?
        (append! sb " "))
      (append! sb class)
      false)

    (or (sequential? class) (set? class))
    (reduce #(render-class! sb %1 %2) first? class)

    :else (recur sb first? (to-str class))))

(defn render-classes! [classes sb]
  (when classes
    (append! sb " class=\"")
    (render-class! sb true classes)
    (append! sb "\"")))

(defn- render-attr-str! [sb attr value]
  (append! sb " " attr "=\"" (escape-html (to-str value)) "\""))

(def booleanish-string
  (into #{}
        (map normalize-attr-key)
        #{:content-editable :draggable :spell-check :value
          :auto-reverse :external-resources-required :focusable :preserve-alpha}))

(defn render-attr! [tag key value sb]
  (let [attr (normalize-attr-key key)]
    (case attr
      "id" (when value (append! sb " id=\"" value "\""))
      "type" (when value (append! sb " type=\"" value "\""))
      "style" (render-style! value sb)
      ("key" "ref" "dangerouslySetInnerHTML") :nop
      ("class" "className" "class-name") (render-classes! value sb)
      ("for" "html-for" "htmlFor") (render-attr-str! sb "for" value)
      (cond
        (and (= "value" attr)
             (or (= "select" tag)
                 (= "textarea" tag))) :nop
        (.startsWith attr "aria-") (render-attr-str! sb attr value)
        (.startsWith attr "data-") (render-attr-str! sb attr value)
        (not value) :nop

        (and (true? value)
             (not (contains? booleanish-string attr)))
        (append! sb " " attr "=\"\"")

        (.startsWith attr "on") (if (string? value)
                                  (render-attr-str! sb attr value)
                                  :nop)
        :else (render-attr-str! sb attr value)))))

(defn render-attrs! [tag attrs sb]
  (reduce-kv (fn [_ k v] (render-attr! tag k v sb)) nil attrs))

;;; render html

(defprotocol HtmlRenderer
  (-render-html [this *state sb]
    "Turn a Clojure data type into a string of HTML with react ids."))

(defn render-inner-html! [attrs children sb]
  (when-let [inner-html (:dangerouslySetInnerHTML attrs)]
    (when-not (empty? children)
      (throw (Exception. "Invariant Violation: Can only set one of `children` or `props.dangerouslySetInnerHTML`.")))
    (when-not (contains? inner-html :__html)
      (throw (Exception. "Invariant Violation: `props.dangerouslySetInnerHTML` must be in the form `{__html: ...}`. Please visit https://fb.me/react-invariant-dangerously-set-inner-html for more information.")))
    (when-not (nil? (:__html inner-html))
      (append! sb (:__html inner-html)))
    true))

(defn render-textarea-value! [tag attrs sb]
  (when (= tag "textarea")
    (when-some [value (get-value attrs)]
      (append! sb (escape-html value))
      true)))

(defn render-content! [tag attrs children *state sb]
  (if (and (nil? children)
           (contains? void-tags tag))
    (append! sb "/>")
    (do
      (append! sb ">")
      (or (render-textarea-value! tag attrs sb)
          (render-inner-html! attrs children sb)
          (doseq [child children]
            (-render-html child *state sb)))
      (append! sb "</" tag ">")))
  (when (not= :state/static @*state)
    (vreset! *state :state/tag-close)))

(defn render-html-element! [[tag :as element] *state sb]
  (when-not (keyword? tag)
    (throw (ex-info "Tag should be keyword" {:tag tag})))
  (let [[tag attrs children] (normalize-element element)
        select-value (get-value attrs)]
    (append! sb "<" tag)

    (render-attrs! tag attrs sb)

    (when (and (= "option" tag) (= select-value *select-value*))
      (append! sb " selected=\"\""))

    (when (not= :state/static @*state)
      (vreset! *state :state/tag-open))

    (if (= "select" tag)
      (binding [*select-value* select-value]
        (render-content! tag attrs children *state sb))
      (render-content! tag attrs children *state sb))))

(defn render-fragment! [[tag attrs & children] *state sb]
  (let [children (if (map? attrs)
                   children
                   (cons attrs children))]
    (-render-html children *state sb)))

(defn render-suspense! [element]
  (binding [*out* *err*]
    (prn (str "React.Suspense elements are not supported on JVM, skipping: " element))))

(defn render-portal! [element]
  (binding [*out* *err*]
    (prn (str "Portal elements are not supported on JVM, skipping: " element))))

(defn render-interop! [element]
  (binding [*out* *err*]
    (prn (str "Interop elements are not supported on JVM, skipping: " element
              "\nExclude JavaScript components using reader macro: #?(:cljs ...)."))))

(defn render-error-boundary! [f args *state sb]
  (let [{:keys [display-name render-fn did-catch derive-error-state]} f
        props {:children args}]
    (try
      (-> (render-fn [nil identity] props)
          (-render-html *state sb))
      (catch Exception e
        (did-catch e display-name)
        (-> (render-fn [(derive-error-state e) identity] props)
            (-render-html *state sb))))))

(defn render-component! [[f props & children :as el] *state sb]
  (if (-> f meta :uix.core/error-boundary)
    (let [[_ & args] el]
      (render-error-boundary! f args *state sb))
    (let [v (if (== (count el) 1)
              (f)
              (let [props (if (map? props)
                            (cond-> (dissoc props :key)
                              (seq children) (assoc :children children))
                            {:children (into [props] children)})
                    props (if (:children props)
                            (update props :children #(if (== 1 (count %)) (first %) %))
                            props)]
                (if (seq props)
                  (f props)
                  (f))))]
      (-render-html v *state sb))))

(defn render-element!
  "Render an element vector as a HTML element."
  [element *state sb]
  (when-not (empty? element)
    (let [tag (nth element 0 nil)]
      (cond
        (identical? :uix/bind-context tag)
        (let [binder (nth element 1 nil)
              children (seq (nth element 2 nil))]
          (binder #(-render-html children *state sb)))
        (identical? :<> tag) (render-fragment! element *state sb)
        (keyword? tag) (render-html-element! element *state sb)
        :else (render-component! element *state sb)))))

(extend-protocol HtmlRenderer
  IPersistentVector
  (-render-html [this *state sb]
    (if (vector? (first this))
      (doseq [element this]
        (-render-html element *state sb))
      (render-element! this *state sb)))

  ISeq
  (-render-html [this *state sb]
    (when (= :state/root @*state)
      (vreset! *state :state/root-seq))
    (doseq [element this]
      (-render-html element *state sb)))

  String
  (-render-html [this *state sb]
    (when (= @*state :state/text)
      (append! sb "<!-- -->"))
    (append! sb (escape-html this))
    (when (not= :state/static @*state)
      (vreset! *state :state/text)))

  Object
  (-render-html [this *state sb]
    (-render-html (str this) *state sb))

  nil
  (-render-html [this *state sb]
    :nop))

;;;;;;;;;;;;;; Public API

(defn render-to-string
  "Takes UIx element and returns an HTML string that should be hydrated on the client"
  [src]
  (let [^StaticBuilder sb (make-static-builder)
        state (volatile! :state/root)]
    (-render-html src state sb)
    (str (.sb sb))))

(defn render-to-static-markup
  "Takes UIx element and returns HTML string, can be used for templating"
  [src]
  (let [^StaticBuilder sb (make-static-builder)]
    (-render-html src (volatile! :state/static) sb)
    (str (.sb sb))))

(defn render-to-stream
  "Same as `render-to-string`, but doesn't return anything,
  instead calls `on-chunk` for every chunk of generated HTML.
  Should be used for streaming HTML to clients to deliver markup faster."
  [src {:keys [on-chunk]}]
  (let [^StreamBuilder sb (make-stream-builder on-chunk)
        state (volatile! :state/root)]
    (-render-html src state sb)))

(defn render-to-static-stream
  "Same as `render-to-static-markup`, but doesn't return anything,
  instead calls `on-chunk` for every chunk of generated HTML.
  Should be used for streaming HTML to clients to deliver markup faster."
  [src {:keys [on-chunk]}]
  (let [^StreamBuilder sb (make-stream-builder on-chunk)
        state (volatile! :state/static)]
    (-render-html src state sb)))
