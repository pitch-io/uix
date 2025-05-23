(ns hooks.uix
  (:require [clj-kondo.hooks-api :as api]))

(def html-elements
  #{:a :abbr :acronym :address :area :article :aside :audio :b :base :bdi :bdo :big :blockquote :body :br :button :canvas :caption :center :cite :code :col :colgroup :data :datalist :dd :del :details :dfn :dialog :dir :div :dl :dt :em :embed :fieldset :figcaption :figure :font :footer :form :frame :frameset :h1 :h2 :h3 :h4 :h5 :h6 :head :header :hgroup :hr :html :i :iframe :image :img :input :ins :kbd :label :legend :li :link :main :map :mark :marquee :menu :menuitem :meta :meter :nav :nobr :noembed :noframes :noscript :object :ol :optgroup :option :output :p :param :picture :plaintext :portal :pre :progress :q :rb :rp :rt :rtc :ruby :s :samp :script :search :section :select :slot :small :source :span :strike :strong :style :sub :summary :sup :table :tbody :td :template :textarea :tfoot :th :thead :time :title :tr :track :tt :u :ul :var :video :wbr :xmp})

(def html-attrs
  {:formnovalidate :form-no-validate, :y :y, :zoomandpan :zoom-and-pan, :role :role, :glyphname :glyph-name, :rel :rel, :text-anchor :text-anchor, :divisor :divisor, :text-rendering :text-rendering, :surfacescale :surface-scale, :font-style :font-style, :reversed :reversed, :keysplines :key-splines, :fontstretch :font-stretch, :xml:lang :xml-lang, :vmathematical :v-mathematical, :open :open, :focusable :focusable, :strokedasharray :stroke-dasharray, :mask :mask, :image-rendering :image-rendering, :stroke-dasharray :stroke-dasharray, :strikethrough-thickness :strikethrough-thickness, :async :async, :accumulate :accumulate, :x-height :x-height, :typeof :typeof, :colorinterpolationfilters :color-interpolation-filters, :exponent :exponent, :vertoriginx :vert-origin-x, :bbox :bbox, :color-rendering :color-rendering, :xlink:arcrole :xlink-arcrole, :dominantbaseline :dominant-baseline, :markerheight :marker-height, :min :min, :fill-rule :fill-rule, :letterspacing :letter-spacing, :font-stretch :font-stretch, :format :format, :hanging :hanging, :children :children, :xheight :x-height, :sizes :sizes, :playsinline :plays-inline, :inputmode :input-mode, :rx :rx, :cellpadding :cell-padding, :r :r, :accent-height :accent-height, :novalidate :no-validate, :colorinterpolation :color-interpolation, :vector-effect :vector-effect, :xlinktitle :xlink-title, :stroke :stroke, :stop-color :stop-color, :attributetype :attribute-type, :horizoriginx :horiz-origin-x, :clip :clip, :wrap :wrap, :glyph-orientation-horizontal :glyph-orientation-horizontal, :paintorder :paint-order, :unitsperem :units-per-em, :elevation :elevation, :crossorigin :cross-origin, :xlink:show :xlink-show, :restart :restart, :intercept :intercept, :transform :transform, :selected :selected, :dx :dx, :srcset :src-set, :color :color, :stitchtiles :stitch-tiles, :xlinkshow :xlink-show, :dir :dir, :calcmode :calc-mode, :clippath :clip-path, :muted :muted, :amplitude :amplitude, :seamless :seamless, :acceptcharset :accept-charset, :placeholder :placeholder, :tabindex :tab-index, :disabled :disabled, :allowreorder :allow-reorder, :refy :ref-y, :markerstart :marker-start, :usemap :use-map, :is :is, :primitiveunits :primitive-units, :itemtype :item-type, :font-size :font-size, :fontstyle :font-style, :diffuseconstant :diffuse-constant, :alt :alt, :rowspan :row-span, :srcdoc :src-doc, :offset :offset, :allowfullscreen :allow-full-screen, :speed :speed, :stemv :stemv, :scale :scale, :kerning :kerning, :font-variant :font-variant, :writing-mode :writing-mode, :unselectable :unselectable, :font-weight :font-weight, :contextmenu :context-menu, :autocapitalize :auto-capitalize, :security :security, :xlinkactuate :xlink-actuate, :controlslist :controls-list, :coords :coords, :keytype :key-type, :method :method, :content :content, :default :default, :datatype :datatype, :patternunits :pattern-units, :overlinethickness :overline-thickness, :u1 :u1, :overflow :overflow, :property :property, :frameborder :frame-border, :strikethroughposition :strikethrough-position, :referrerpolicy :referrer-policy, :ideographic :ideographic, :name :name, :clip-rule :clip-rule, :panose1 :panose1, :as :as, :colorrendering :color-rendering, :innerhtml :inner-html, :arabicform :arabic-form, :renderingintent :rendering-intent, :stroke-opacity :stroke-opacity, :horiz-origin-x :horiz-origin-x, :fill :fill, :keytimes :key-times, :viewtarget :view-target, :value :value, :defaultchecked :default-checked, :minlength :min-length, :xml:space :xml-space, :readonly :read-only, :kernelmatrix :kernel-matrix, :stddeviation :std-deviation, :optimum :optimum, :preservealpha :preserve-alpha, :suppresshydrationwarning :suppress-hydration-warning, :kernelunitlength :kernel-unit-length, :numoctaves :num-octaves, :color-profile :color-profile, :vert-origin-y :vert-origin-y, :underlinethickness :underline-thickness, :stroke-linejoin :stroke-linejoin, :strokewidth :stroke-width, :strikethrough-position :strikethrough-position, :y1 :y1, :scoped :scoped, :mode :mode, :width :width, :start :start, :dy :dy, :strokelinecap :stroke-linecap, :g2 :g2, :alignmentbaseline :alignment-baseline, :defer :defer, :shape-rendering :shape-rendering, :orientation :orientation, :xlinkrole :xlink-role, :cursor :cursor, :panose-1 :panose1, :stroke-dashoffset :stroke-dashoffset, :refx :ref-x, :type :type, :specularconstant :specular-constant, :classname :class-name, :hreflang :href-lang, :glyphref :glyph-ref, :controls :controls, :viewbox :view-box, :fontsizeadjust :font-size-adjust, :vert-origin-x :vert-origin-x, :nomodule :no-module, :manifest :manifest, :src :src, :points :points, :xmlns :xmlns, :autocorrect :auto-correct, :orient :orient, :formtarget :form-target, :videographic :v-ideographic, :contentscripttype :content-script-type, :underline-thickness :underline-thickness, :icon :icon, :multiple :multiple, :accesskey :access-key, :formaction :form-action, :horiz-adv-x :horiz-adv-x, :scope :scope, :overlineposition :overline-position, :sandbox :sandbox, :itemprop :item-prop, :string :string, :ascent :ascent, :radius :radius, :strokemiterlimit :stroke-miterlimit, :baseprofile :base-profile, :word-spacing :word-spacing, :xlinktype :xlink-type, :disableremoteplayback :disable-remote-playback, :xlink:type :xlink-type, :autoreverse :auto-reverse, :maxlength :max-length, :seed :seed, :stop-opacity :stop-opacity, :size :size, :pointsaty :points-at-y, :k :k, :title :title, :capheight :cap-height, :repeatcount :repeat-count, :prefix :prefix, :arabic-form :arabic-form, :headers :headers, :loop :loop, :strokedashoffset :stroke-dashoffset, :high :high, :suppresscontenteditablewarning :suppress-content-editable-warning, :widths :widths, :keyparams :key-params, :style :style, :unicode-range :unicode-range, :clip-path :clip-path, :autosave :auto-save, :markerunits :marker-units, :inlist :inlist, :lang :lang, :stroke-linecap :stroke-linecap, :rows :rows, :flood-opacity :flood-opacity, :in2 :in2, :summary :summary, :begin :begin, :cliprule :clip-rule, :g1 :g1, :shaperendering :shape-rendering, :lighting-color :lighting-color, :writingmode :writing-mode, :z :z, :strokelinejoin :stroke-linejoin, :enterkeyhint :enter-key-hint, :azimuth :azimuth, :wordspacing :word-spacing, :alphabetic :alphabetic, :alignment-baseline :alignment-baseline, :stopcolor :stop-color, :http-equiv :http-equiv, :cols :cols, :xmlns:xlink :xmlns-xlink, :scrolling :scrolling, :vocab :vocab, :valphabetic :v-alphabetic, :radiogroup :radio-group, :preload :preload, :dominant-baseline :dominant-baseline, :units-per-em :units-per-em, :marker-start :marker-start, :strokeopacity :stroke-opacity, :overline-position :overline-position, :filter :filter, :externalresourcesrequired :external-resources-required, :spellcheck :spell-check, :targety :target-y, :formmethod :form-method, :textanchor :text-anchor, :markerend :marker-end, :xmlnsxlink :xmlns-xlink, :poster :poster, :draggable :draggable, :glyph-name :glyph-name, :keypoints :key-points, :vectoreffect :vector-effect, :basefrequency :base-frequency, :pointerevents :pointer-events, :descent :descent, :stroke-width :stroke-width, :challenge :challenge, :targetx :target-x, :list :list, :cap-height :cap-height, :attributename :attribute-name, :result :result, :from :from, :u2 :u2, :hidden :hidden, :max :max, :patterncontentunits :pattern-content-units, :opacity :opacity, :cx :cx, :label :label, :fontweight :font-weight, :id :id, :requiredextensions :required-extensions, :accept-charset :accept-charset, :values :values, :dur :dur, :autofocus :auto-focus, :wmode :wmode, :k3 :k3, :resource :resource, :repeatdur :repeat-dur, :cy :cy, :underline-position :underline-position, :kind :kind, :htmlfor :html-for, :k4 :k4, :xlink:actuate :xlink-actuate, :baseline-shift :baseline-shift, :baselineshift :baseline-shift, :checked :checked, :markerwidth :marker-width, :maskunits :mask-units, :lightingcolor :lighting-color, :imagerendering :image-rendering, :v-mathematical :v-mathematical, :filterres :filter-res, :slope :slope, :pathlength :path-length, :xlink:href :xlink-href, :color-interpolation-filters :color-interpolation-filters, :ychannelselector :y-channel-selector, :contentstyletype :content-style-type, :shape :shape, :underlineposition :underline-position, :strikethroughthickness :strikethrough-thickness, :filterunits :filter-units, :fontvariant :font-variant, :xlinkhref :xlink-href, :fontsize :font-size, :xchannelselector :x-channel-selector, :pointsatx :points-at-x, :additive :additive, :datetime :date-time, :operator :operator, :fontfamily :font-family, :defaultvalue :default-value, :preserveaspectratio :preserve-aspect-ratio, :low :low, :dangerouslysetinnerhtml :dangerously-set-inner-html, :marginheight :margin-height, :xlinkarcrole :xlink-arcrole, :text-decoration :text-decoration, :cellspacing :cell-spacing, :rotate :rotate, :display :display, :mathematical :mathematical, :textdecoration :text-decoration, :order :order, :d :d, :action :action, :stopopacity :stop-opacity, :imagesrcset :image-src-set, :requiredfeatures :required-features, :horizadvx :horiz-adv-x, :xmlspace :xml-space, :by :by, :origin :origin, :httpequiv :http-equiv, :fy :fy, :stroke-miterlimit :stroke-miterlimit, :specularexponent :specular-exponent, :marginwidth :margin-width, :colorprofile :color-profile, :letter-spacing :letter-spacing, :x :x, :autocomplete :auto-complete, :maskcontentunits :mask-content-units, :vert-adv-y :vert-adv-y, :x1 :x1, :form :form, :gradienttransform :gradient-transform, :capture :capture, :autoplay :auto-play, :integrity :integrity, :target :target, :unicoderange :unicode-range, :vertoriginy :vert-origin-y, :flood-color :flood-color, :rendering-intent :rendering-intent, :v-hanging :v-hanging, :unicode-bidi :unicode-bidi, :colspan :col-span, :tablevalues :table-values, :accentheight :accent-height, :end :end, :limitingconeangle :limiting-cone-angle, :xlink:title :xlink-title, :unicode :unicode, :bias :bias, :version :version, :unicodebidi :unicode-bidi, :y2 :y2, :glyphorientationhorizontal :glyph-orientation-horizontal, :xmllang :xml-lang, :formenctype :form-enc-type, :floodcolor :flood-color, :itemref :item-ref, :textlength :text-length, :systemlanguage :system-language, :marker-mid :marker-mid, :v-ideographic :v-ideographic, :floodopacity :flood-opacity, :nonce :nonce, :pointer-events :pointer-events, :fx :fx, :gradientunits :gradient-units, :local :local, :font-size-adjust :font-size-adjust, :download :download, :cite :cite, :k1 :k1, :k2 :k2, :srclang :src-lang, :step :step, :pointsatz :points-at-z, :itemid :item-id, :decelerate :decelerate, :media :media, :xmlbase :xml-base, :glyph-orientation-vertical :glyph-orientation-vertical, :itemscope :item-scope, :glyphorientationvertical :glyph-orientation-vertical, :lengthadjust :length-adjust, :startoffset :start-offset, :x2 :x2, :color-interpolation :color-interpolation, :visibility :visibility, :enctype :enc-type, :ry :ry, :enable-background :enable-background, :direction :direction, :enablebackground :enable-background, :classid :class-id, :href :href, :profile :profile, :fill-opacity :fill-opacity, :required :required, :contenteditable :content-editable, :fillopacity :fill-opacity, :fillrule :fill-rule, :v-alphabetic :v-alphabetic, :vertadvy :vert-adv-y, :spreadmethod :spread-method, :mediagroup :media-group, :edgemode :edge-mode, :imagesizes :image-sizes, :font-family :font-family, :clippathunits :clip-path-units, :textrendering :text-rendering, :disablepictureinpicture :disable-picture-in-picture, :height :height, :spacing :spacing, :marker-end :marker-end, :about :about, :vhanging :v-hanging, :in :in, :pattern :pattern, :overline-thickness :overline-thickness, :accept :accept, :markermid :marker-mid, :span :span, :to :to, :paint-order :paint-order, :xml:base :xml-base, :xlink:role :xlink-role, :patterntransform :pattern-transform, :data :data, :stemh :stemh, :results :results})

(def mapping-forms
  '#{for map mapv map-indexed reduce reduce-kv
     keep keep-indexed mapcat})

(defn- uix-element? [name]
  (= (api/resolve {:name name :call true}) '{:name $ :ns uix.core}))

(defn $ [{:keys [node]}]
  (let [[sym props :as expr] (rest (api/sexpr node))]
    (when-not (or (symbol? sym)
                  (keyword? sym)
                  (list? sym))
      (api/reg-finding! (-> (meta node)
                            (merge {:message "First arg to $ must be a symbol, keyword, or dynamic element"
                                    :type    :uix.core/$-arg-validation}))))
    (when (map? props)
      (when (and (contains? props :&)
                 (not (or (symbol? (:& props))
                          (vector? (:& props)))))
        (api/reg-finding! (-> (meta node)
                              (merge {:message "Spread syntax can be used only with references to props values. Spreading map literal doesn't make sense, inline it into props map instead."
                                      :type    :uix.core/$-non-ref-spread}))))
      (when (and (contains? props :&)
                 (empty? (dissoc props :&))
                 (or (not (vector? (:& props)))
                     (== 1 (count (:& props)))))
        (api/reg-finding! (-> (meta node)
                              (merge {:message "Spreading a single props map into empty map literal doesn't make sense, instead pass props symbol itself."
                                      :type    :uix.core/$-unnecessary-spread}))))
      (when (contains? html-elements sym)
        (doseq [[k _] props
                :when (and (contains? html-attrs k) (not= k (html-attrs k)))]
          (api/reg-finding! (-> (meta node)
                                (merge {:message (cond-> (str "Invalid DOM property " k ".")
                                                         (html-attrs k) (str " Did you mean " (html-attrs k) "?"))
                                        :type    :uix.dom/$-invalid-attribute}))))))
    (when (->> (api/callstack)
               (some #(and (contains? '#{cljs.core clojure.core} (:ns %))
                           (contains? mapping-forms (:name %)))))
      (when-not (->> (api/callstack)
                     (take-while #(not (and (contains? '#{cljs.core clojure.core} (:ns %))
                                            (contains? mapping-forms (:name %)))))
                     (some #(uix-element? (:name %))))
        (when (or (and (map? props) (not (contains? props :key))) ;; ($ :a {})
                  (== 1 (count expr)) ;; ($ :a)
                  (and (list? props)
                       (-> props first uix-element?)) ;; ($ :a ($ :br))
                  (and (not (map? props))
                       (not (symbol? props))
                       (not (list? props)))) ;; ($ :a 123)
          (api/reg-finding! (-> (meta node)
                                (merge {:message "UIx element is missing :key attribute, which is required since the element is rendered as a list item. Make sure to add a unique value for `:key` attribute derived from element's props, do not use index."
                                        :type    :uix.core/$-missing-key}))))))))


(defn- fn-literal? [form]
  (and (list? form) ('#{fn fn*} (first form))))

(def literal?
  (some-fn keyword? number? string? nil? boolean?))

(defn- deps->literals [deps]
  (filter literal? deps))

(def cond-forms
  '#{when when-not when-let when-some when-first
     if if-not if-let if-some
     and or cond condp case cond-> cond->> some-> some->>})

(def loop-forms
  '#{map mapv map-indexed filter filterv reduce reduce-kv keep keep-indexed
     remove mapcat drop-while take-while group-by partition-by split-with
     sort-by some for doseq loop})

(defn hook [{:keys [node]}]
  (let [cs (api/callstack)]
    (when (->> cs (some #(and (contains? '#{cljs.core clojure.core} (:ns %))
                              (cond-forms (:name %)))))
      (api/reg-finding! (-> (meta node)
                            (merge {:message "React Hook is called conditionally. React Hooks must be called in the exact same order in every component render. Read https://react.dev/reference/rules/rules-of-hooks for more context"
                                    :type    :uix.core/hook-in-branch}))))
    (when (->> cs (some #(and (contains? '#{cljs.core clojure.core} (:ns %))
                              (loop-forms (:name %)))))
      (api/reg-finding! (-> (meta node)
                            (merge {:message "React Hook may be executed more than once. Possibly because it is called in a loop. React Hooks must be called in the exact same order in every component render. Read https://react.dev/reference/rules/rules-of-hooks for more context"
                                    :type    :uix.core/hook-in-loop}))))
    (when (->> cs
               (some #(or (and (contains? '#{cljs.core clojure.core} (:ns %))
                               (contains? '#{fn fn* defn defn- defmethod} (:name %)))
                          (and (contains? '#{uix.core} (:ns %))
                               (contains? '#{defui defhook fn} (:name %)))))
               not)
      (api/reg-finding! (-> (meta node)
                            (merge {:message "React Hook cannot be called at the top level. React Hooks must be called in a React function component or a custom React Hook function."
                                    :type    :uix.core/hook-top-level}))))))

(defn hook-deps [{:keys [node] :as ctx}]
  (let [[f deps :as expr] (rest (api/sexpr node))]
    (hook ctx)
    (when-not (fn-literal? f)
      (api/reg-finding! (-> (meta node)
                            (merge {:message "React Hook received a function whose dependencies are unknown. Pass an inline function instead."
                                    :type    :uix.core/hook-inline-function}))))
    (when (== 2 (count expr))
      (cond
        (and (-> node :children last :children first :value (= 'js))
             (-> node :children last :children second api/tag (= :vector)))
        (api/reg-finding! (-> (meta node)
                              (merge {:message "React Hook was passed a dependency list that is a JavaScript array, instead of Clojure’s vector. Change it to be a vector literal."
                                      :type    :uix.core/hook-deps-array-literal})))

        (not (vector? deps))
        (api/reg-finding! (-> (meta node)
                              (merge {:message "React Hook was passed a dependency list that is not a vector literal. This means we can’t statically verify whether you've passed the correct dependencies. Change it to be a vector literal with explicit set of dependencies."
                                      :type    :uix.core/hook-deps-coll-literal})))

        (seq (deps->literals deps))
        (api/reg-finding! (-> (meta node)
                              (merge {:message (str "React Hook was passed literal values in dependency vector: [" (clojure.string/join ", " (deps->literals deps)) "]. Those are not valid dependencies because they never change. You can safely remove them.")
                                      :type    :uix.core/literal-value-in-deps})))))))

(defn used-keys [sig]
  (->> sig
       (reduce-kv
         (fn [ret k v]
           (cond
             (and (keyword? k) (= "keys" (name k)))
             (if-let [ns (namespace k)]
               (into ret (map #(keyword ns (name %))) v)
               (into ret (map keyword) v))

             (= :strs k) (into ret (map str) v)
             (= :syms k) (into ret v)
             (= :& k) ret
             (keyword? v) (conj ret v)
             (string? v) (conj ret v)
             (symbol? v) (conj ret v)
             :else ret))
         #{})))

(defn rest-props [[sig :as args]]
  (if (map? sig)
    (if-not (contains? sig :&)
      [args]
      [(update args 0 dissoc :&) (used-keys sig) (:& sig)])
    [args]))

(defn rewrite [node & {:keys [defui?]}]
  (let [args (rest (:children node))
        component-name (first args)
        ?docstring (when (and defui? (string? (api/sexpr (second args))))
                     (second args))
        args (if ?docstring
               (nnext args)
               (next args))
        args-vec (api/sexpr (first args))
        [pre-body body] (if (api/map-node? (second args))
                          [(take 2 args) (nnext args)]
                          [(take 1 args) (next args)])
        body (if (and (vector? args-vec)
                      (map? (first args-vec))
                      (contains? (first args-vec) :&))
               (let [[_ _ rest-sym] (rest-props args-vec)
                     rest-sym (with-meta (api/token-node rest-sym) (meta rest-sym))]
                 [(api/list-node
                    (list* (api/token-node 'let*)
                           (api/vector-node [rest-sym (api/token-node nil)])
                           body))])
               body)]
    (with-meta
      (api/list-node
        (list* (api/token-node (if defui? 'defn 'fn))
               component-name
               (if ?docstring
                 (into (into [?docstring] pre-body) body)
                 (into (vec pre-body) body))))
      (meta node))))

(defn defui [{:keys [node] :as ctx}]
  (let [new-node (rewrite node :defui? true)]
    {:node new-node}))

(defn anon-fn [{:keys [node] :as ctx}]
  (let [new-node (rewrite node :defui? false)]
    {:node new-node}))