(ns uix.dom.server-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [uix.core :as uix :refer [$ defui]]
            [uix.dom.server :as dom.server]
            #?@(:cljs [[fs :as fs]])
            #?@(:clj [[clojure.java.shell :as shell]
                      [clojure.java.io :as io]
                      [clj-diffmatchpatch :as diff]])))

#?(:clj
   (defn render [el]
     (dom.server/render-to-static-markup el)))

#?(:clj
   (deftest test-class-names
     (is (= (render ($ :div)) "<div></div>"))
     (testing "Named types"
       (is (= (render ($ :div {:class "a"})) "<div class=\"a\"></div>"))
       (is (= (render ($ :div {:class :a})) "<div class=\"a\"></div>")))
     (testing "Collection of classes"
       (is (= (render ($ :div {:class [1 2 3]})) "<div class=\"1 2 3\"></div>"))
       (is (= (render ($ :div {:class [1 :a "b"]})) "<div class=\"1 a b\"></div>")))))

#?(:clj
   (deftest test-class-name-attr
     (is (= (render ($ :div {:class "a"})) "<div class=\"a\"></div>"))
     (is (= (render ($ :div {:class-name "a"})) "<div class=\"a\"></div>"))
     (is (= (render ($ :div {:className "a"})) "<div class=\"a\"></div>"))))

#?(:clj
   (deftest test-for-attr
     (is (= (render ($ :div {:for "a"})) "<div for=\"a\"></div>"))
     (is (= (render ($ :div {:html-for "a"})) "<div for=\"a\"></div>"))
     (is (= (render ($ :div {:htmlFor "a"})) "<div for=\"a\"></div>"))))

#?(:clj
   (deftest test-46
     (is (= (render ($ :div#id "hi")) "<div id=\"id\">hi</div>"))
     (is (= (render ($ :div#id {:id 1} "hi")) "<div id=\"1\">hi</div>"))
     (is (= (render ($ :div {:id 1} "hi")) "<div id=\"1\">hi</div>"))

     (is (= (render ($ :div.a.b "hi")) "<div class=\"a b\">hi</div>"))
     (is (= (render ($ :div.a.b {:class "c"} "hi")) "<div class=\"a b c\">hi</div>"))
     (is (= (render ($ :div {:class "c"} "hi")) "<div class=\"c\">hi</div>"))
     (is (= (render ($ :div {:class ["c"]} "hi")) "<div class=\"c\">hi</div>"))
     (is (= (render ($ :div.a.b {:class ["c"]} "hi")) "<div class=\"a b c\">hi</div>"))))

#?(:clj
   (deftest test-defui
     (defui test-defui-comp-1 [props]
       (is (= {:x "hello" :children "child"} props))
       (:x props))
     (defui test-defui-comp-1 [props]
       (is (= {:x "hello" :ref 1 :children ["child" "more"]} props))
       (:x props))
     (is (= "hello" (render ($ test-defui-comp-1 {:x "hello" :key "k" :ref 1} "child" "more"))))))

#?(:clj
   (deftest test-no-args-component
     (testing "no args defui"
       (uix.core/defui args-not-required-component [{:keys [children]}]
         ($ :h1 children))
       (is (= "<h1></h1>"
              (render (uix.core/$ args-not-required-component)))))
     (testing "no args fn"
       (is (= "<h1></h1>"
              (render (uix.core/$ (uix.core/fn [{:keys [children]}]
                                    ($ :h1 children)))))))))


;; Adapted from https://github.com/tonsky/rum/blob/gh-pages/test/rum/test/server_render.cljc

(defui comp-simple []
  ($ :div
     ($ :div "A"
        ($ :span "A1")
        ($ :span "A2"))
     ($ :div "B")
     ($ :div "C" "D")
     ($ :div "E"
        ($ :span "E1"))
     ($ :div nil)
     ($ :div nil "F")
     ($ :div {} ((constantly nil)) "G")))

(defui comp-tag []
  ($ :div#up.header "test"))

(defui comp-list []
  ($ :ul
     ($ :li {:key "F"})
     ($ :li {:key "M"})))

(defui comp-lists []
  ($ :div
     ($ :.a (list ($ :.b {:key "b"})))
     ($ :.c "d" (list ($ :.e {:key "e"})))
     ($ :.f "g" (list ($ :.h {:key "i"})) "i")))

(defui comp-root-array []
  (list
   ($ :.a {:key :a} "A")
   ($ :.b {:key :b} "B")
   ($ :.c {:key :c} "C")))

(defui comp-header []
  ($ :ul.nav__content
     (list ($ :li.menu-item {:key "F"} "Female")
           ($ :li.menu-item {:key "M"} "Male"))
     ($ :li.menu-item {:key "outlet"} "Outlet")))

(defui comp-nil1 []
  "In this case nil will be not counted against reactid"
  ($ :div {:class "parent"}
     nil
     ($ :div.child)))

(defui comp-nil2 []
  "In this case *both* nils will be counted against reactid"
  ($ :div {:class "parent"}
     nil
     ($ :div.child)
     (list
      nil
      ($ :div.child2 {:key "a"}))))

(defui comp-nothing []
  nil)

(defui comp-nothing2 []
  ($ :div
     ($ :div ($ comp-nothing))
     ($ :div "a" ($ comp-nothing))
     ($ :div ($ comp-nothing) "b")
     ($ :div "a" ($ comp-nothing) "b" ($ :span "x"))
     ($ :div ($ :.a) ($ comp-nothing) ($ :.b))
     ($ :div ($ comp-nothing {:key "K"}))))

(defui comp-span []
  ($ :span
     "a" "b"
     "a" ($ :tag "b") "c"
     "a" (list "b" "c") "d"
     "a" (list "b" "c") (list "d" "e") "f"
     (list "a" "b") ($ :tag "c") (list "d" "e")
     "a" nil "b"
     "a" ($ comp-nothing) "b"
     "a" (list nil) "b"))

(defui comp-campaign []
  ($ :div#today.content.wrapper
     (list
      ($ :div.banner {:class " big "
                      :style {:background-image "url(123)"}
                      :key "campaign-20871"})
      ($ :a.banner__item-link {:href "/catalogue/s-10079-colin-s/"
                               :key "campaign-20871123"})

      ($ :div.banner {:class " "
                      :key "banner-:promo"})
      ($ :a.banner__item-link {:href nil, :target "_blank"
                               :key "campaign-208712391"})

      ($ :div.banner {:class " medium "
                      :style {:background-image "url(321)"}
                      :key "campaign-20872"})
      ($ :a.banner__item-link {:href "/catalogue/s-10089-rinascimento/"
                               :key "campaign-20890871"}))))

(defui comp-styles []
  ($ :div
     ($ :div.a {:style {}})
     ($ :div.b {:style {:background-color nil}})
     ($ :div.c {:style {:background-color ""}})
     ($ :div.d {:style
                {:background-image "url(\"123\")"               ;; should escape quotes
                 :line-height 24                                ;; unitless, should not add 'px'
                 :-webkit-box-flex 3                            ;; prefixed unitless
                 :margin-top 17                                 ;; should add 'px'
                 :margin-right 17.1
                 :margin-left 0}})                              ;; no 'px' added to 0
     ($ :div.e {:style
                {:border-width " 1  "                           ;; trim  numeric & append 'px'
                 :padding-right " 1.2 "
                 :padding-bottom "1em"                          ;; do not add 'px' if unit already specified
                 :text-align " left  "                          ;; trim non-numeric values
                 :flex-grow " 1  "}})                           ;; trim unitless values
     ($ :div.f {:style
                {:background-image "url('123')"                 ;; should escape quotes
                 :fontWeight 10                                 ;; should convert from react-style properties to CSS
                 "WebkitFlex" 1                                 ;; prefixed react-style prop
                 "msFlex" 1                                     ;; prefixed react-style prop (lowecase ms)
                 "zIndex" 1}})))                                ;; accept strings too

(defui comp-attrs []
  ($ :div
     (for [[a v] [[:data-attr-ibute "b"]                      ;; should not touch data-* and aria* attr names
                  [:aria-checked "c"]
                  [:form-enc-type "text/plain"]               ;; should normalize (remove dashes)
                  [:checked false]                            ;; nil and false attrs not printed
                  [:allow-full-screen true]                   ;; true printed as attr=""
                  [:href "/a=b&c=d"]]]
       (let [props (assoc {:key v} a v)]
         ($ :div props)))))

(defui comp-attrs-capitalization []
  ($ :div
     (for [a [:accept-charset :access-key :auto-complete :cell-padding :cell-spacing :char-set :class-id :content-editable :context-menu :cross-origin :date-time :enc-type :form-action :form-enc-type :form-method :form-target :frame-border :href-lang :http-equiv :input-mode :key-params :key-type :margin-height :margin-width :max-length :media-group :min-length :radio-group :referrer-policy :spell-check :src-doc :src-lang :src-set :tab-index :use-map :auto-capitalize :auto-correct :auto-save :item-prop :item-type :item-id :item-ref]]
       (let [props {a "_" :key a}]
         ($ :div props)))

     ($ :table
        ($ :td {:col-span 1
                :row-span 1}))

     ($ :svg
        (for [a [:allow-reorder :attribute-name :attribute-type :auto-reverse :base-frequency :base-profile :calc-mode :clip-path-units :content-script-type :content-style-type :diffuse-constant :edge-mode :external-resources-required :filter-res :filter-units :glyph-ref :gradient-transform :gradient-units :kernel-matrix :kernel-unit-length :key-points :key-splines :key-times :length-adjust :limiting-cone-angle :marker-height :marker-units :marker-width :mask-content-units :mask-units :num-octaves :path-length :pattern-content-units :pattern-transform :pattern-units :points-at-x :points-at-y :points-at-z :preserve-alpha :preserve-aspect-ratio :primitive-units :ref-x :ref-y :repeat-count :repeat-dur :required-extensions :required-features :specular-constant :specular-exponent :spread-method :start-offset :std-deviation :stitch-tiles :surface-scale :system-language :table-values :target-x :target-y :view-box :view-target :x-channel-selector :xlink-actuate :xlink-arcrole :xlink-href :xlink-role :xlink-show :xlink-title :xlink-type :xml-base :xmlns-xlink :xml-lang :xml-space :y-channel-selector :zoom-and-pan]]
          (let [props {a "_" :key a}]
            ($ :path props))))

     (for [a [:allow-full-screen :auto-play :form-no-validate :no-validate :read-only :item-scope]]
       (let [props (assoc {:key a} a true)]
         ($ :div props)))))

(defui comp-attrs-order []
  ($ :div
     ($ :a {:title "a"
            :alt "b"
            :rel "c"
            :target "d"
            :src "e"})
     ($ :a {:src "a"
            :target "b"
            :rel "c"
            :alt "d"
            :title "e"})
     ($ :a {:title "a" :class "b" :rel "d"})
     ($ :a {:title "a" :class ["b" "c"] :rel "d"})
     ($ :a.clazz {:title "a" :class "b" :rel "d"})
     ($ :a.clazz {:title "a" :class ["b" "c"] :rel "d"})
     ($ :a#id.clazz {:title "a"})
     ($ :a#id.clazz {:title "a" :class "b"})
     ($ :a#clazz.id {:title "a" :class "b"})))

(defui comp-classes []
  ($ :div
     ($ :div {:class [nil]})
     ($ :div {:class :c3})
     ($ :div {:class [:c3 :c4]})                                ;; list form
     ($ :div {:class "c3"})                                     ;; string form
     ($ :div {:class ["c3" "c4"]})
     ($ :div {:class [" c3  " "  c4 "]})                        ;; trimming
     ($ :div {:class [:c3 nil :c4]})                            ;; nils are not removed
     ($ :div {:class [:c2 :c3]})                                ;; removing duplicates
     ($ :.c1 {:class nil})
     ($ :.c1 {:class (when false "...")})                       ;; see #99
     ($ :.c1.c2 {:class :c3})
     ($ :.c1.c2 {:class [:c3 :c4]})                             ;; list form
     ($ :.c1.c2 {:class "c3"})                                  ;; string form
     ($ :.c1.c2 {:class ["c3" "c4"]})
     ($ :.c1.c2 {:class [" c3  " "  c4 "]})                     ;; trimming
     ($ :.c1.c2 {:class [:c3 nil :c4]})                         ;; nils are not removed
     ($ :.c1.c2 {:class [:c2 :c3]})))                           ;; not removing duplicates

(defui comp-html []
  ($ :div {:dangerouslySetInnerHTML {:__html "<span>test</span>"}}))

(defui comp-inputs []
  ($ :div
     ($ :input#id {:class "x" :type "text" :auto-complete "off"})
     ($ :input {:type "text" :default-value "x"})
     ($ :input {:type "checkbox" :default-checked true})
     ($ :input {:type "radio" :default-checked true})
     ($ :select {:default-value "A" :on-change identity}
        ($ :option {:value "A" :on-change identity} "Apple")
        ($ :option {:value "B" :on-change identity} "Banana"))
     ($ :select {:value "A" :on-change identity}
        ($ :option#id.class {:value "A" :on-change identity} "Apple")
        ($ :option#id.class {:value "B" :on-change identity} "Banana"))
     ($ :textarea {:value "text" :on-change identity})
     ($ :textarea {:default-value "text"})))

(defui comp-svg []
  ($ :svg.cclogo
     {:width 100
      :height 100
      :view-box "0 232.5 333.2 232.5"                         ;; should be rendered as viewBox
      :vector-effect "effect"                                 ;; should be rendered as vector-effect
      :version "1.1"
      :dangerouslySetInnerHTML {:__html "[...tons of raw SVG removed...]"}}))

(defui comp-aria []
  ($ :div
     {:aria-hidden true
      :aria-readonly false
      :aria-disabled "true"
      :aria-checked "false"}))

(defui comp-151 []
  ($ :<>
    ($ :div {:children "hello"})
    ($ :div {:children "world"} "yo")
    ($ :div {:children "world"} "hey ho")))

(defui comp-152 []
  ($ :div
     {:data-hidden true
      :data-readonly false
      :data-disabled "true"
      :data-checked "false"}))

(defui comp-231 []
  ($ :<>
     ($ :br)
     ($ :img)))

(def components
  {"simple" comp-simple
   "tag" comp-tag
   "list" comp-list
   "lists" comp-lists
   "root-array" comp-root-array
   "header" comp-header
   "nil1" comp-nil1
   "nil2" comp-nil2
   "nothing" comp-nothing
   "nothing2" comp-nothing2
   "span" comp-span
   "campaign" comp-campaign
   "styles" comp-styles
   "attrs" comp-attrs
   "attrs-cap" comp-attrs-capitalization
   "attrs-order" comp-attrs-order
   "classes" comp-classes
   "html" comp-html
   "inputs" comp-inputs
   "svg" comp-svg
   "aria" comp-aria
   "comp-151" comp-151
   "comp-152" comp-152
   "comp-231" comp-231})

(def render-dir "server_render_test")

#?(:cljs
   (defn -main [& args]
     (doseq [[name f] components]
       (let [html (dom.server/render-to-string ($ f))
             path (str render-dir "/html/" name ".html")]
         (fs/writeFileSync path html))
       (let [html (dom.server/render-to-static-markup ($ f))
             path (str render-dir "/markup/" name ".html")]
         (fs/writeFileSync path html)))))

#?(:clj
   (defn exec [& cmd]
     (testing cmd
       (println "Running" (str "\"" (str/join " " cmd) "\""))
       (let [{:keys [exit out err]} (apply shell/sh cmd)]
         (is (= exit 0))
         (when-not (str/blank? err)
           (binding [*out* *err*]
             (println err)))
         (when-not (str/blank? out)
           (println out))))))

#?(:clj
   (defn diff [s1 s2]
     (->> (diff/wdiff s1 s2)
          (map (fn [[op text]]
                 (case op
                   :delete (str "\033[37;41;1m" text "\033[0m")
                   :insert (str "\033[37;42;1m" text "\033[0m")
                   :equal text)))
          (str/join))))

#?(:clj
   (deftest test-ssr-compat-between-jvm-and-js
     (doseq [^java.io.File f (reverse (file-seq (io/file render-dir)))]
       (when (.exists f)
         (.delete f)))
     (.mkdir (io/file render-dir))
     (.mkdir (io/file render-dir "html"))
     (.mkdir (io/file render-dir "markup"))
     (exec "clojure" "-A:dev:test" "-M" "-m" "shadow.cljs.devtools.cli" "compile" "test")
     (exec "node" "out/main.js")
     (doseq [[name f] components]
       (testing name
         (let [react-html (slurp (str render-dir "/html/" name ".html"))
               uix-html (dom.server/render-to-string ($ f))]
           (is (= react-html uix-html) (diff react-html uix-html)))
         (let [react-html (slurp (str render-dir "/markup/" name ".html"))
               uix-html (dom.server/render-to-static-markup ($ f))]
           (is (= react-html uix-html) (diff react-html uix-html)))))))

(defonce *error-state (atom nil))

(def error-boundary
  (uix.core/create-error-boundary
   {:derive-error-state (fn [error]
                          {:error error})
    :did-catch          (fn [_error info]
                          (reset! *error-state info))}
   (fn [[state _set-state!] {:keys [children]}]
     (if (:error state)
       ($ :p "Error")
       children))))

#?(:clj
   (defui throwing-component [{:keys [throw?]}]
     (when throw?
       (throw (ex-info "Component threw" {:error 1})))))

#?(:clj
   (defui error-boundary-no-elements []
     ($ throwing-component {:throw? false})))

#?(:clj
   (defui error-boundary-catches []
     ($ error-boundary
        ($ throwing-component {:throw? true}))))

#?(:clj
   (defui error-boundary-renders []
     ($ error-boundary
        ($ throwing-component {:throw? false})
        ($ :p "After"))))

#?(:clj
   (defui error-boundary-children []
     ($ error-boundary
        ($ throwing-component {:throw? false})
        ($ :p "After throwing")
        ($ :p "After throwing 2"))))

#?(:clj
   (deftest ssr-error-boundaries
     (is (= (render ($ error-boundary-no-elements)) ""))
     (is (= (render ($ error-boundary-catches)) "<p>Error</p>"))
     (is (= (render ($ error-boundary-renders)) "<p>After</p>"))
     (is (= (render ($ error-boundary-children))
            "<p>After throwing</p><p>After throwing 2</p>"))))

#?(:clj
   (deftest ssr-error-boundary-catch-fn
     (reset! *error-state nil)
     (let [_ (render ($ error-boundary-catches))]
       ;; Tests that did-catch does run
       (is (.contains @*error-state "error-boundary")))))

(defui username [{:keys [username]}]
  ($ :p {:dangerouslySetInnerHTML {:__html username}}))

(defui bad-username [_]
  ($ :p {:dangerouslySetInnerHTML {:__not-html "foo"}}))

#?(:clj
   (deftest nil-dangerously-set-inner-html
     (is (= (render ($ username {:username nil})) "<p></p>"))
     (is (thrown? Exception (render ($ bad-username {:foo 1}))))))

#?(:clj
   (do
     (deftest test-suspense
       (is (= (render ($ uix/suspense {:fallback ($ :div 1)}
                         ($ :div 1)))
              "<div>1</div>")))

     (deftest test-strict-mode
       (is (= (render ($ uix/strict-mode
                         ($ :div 1)))
              "<div>1</div>")))

     (deftest test-profiler
       (is (= (render ($ uix/profiler
                         ($ :div 1)))
              "<div>1</div>")))))

#?(:clj
   (do
     (defui div [{:keys [children]}]
       ($ :div children))

     (deftest test-124
       (is (= (render ($ div ($ :span) ($ :span)))
              "<div><span></span><span></span></div>")))

     (deftest test-vector-children
       (is (= (render ($ :div [($ :span) ($ :span)]))
              "<div><span></span><span></span></div>")))

     (deftest test-self-closing-tags
       (is (= (render ($ :div ($ :img) ($ :br)))
              "<div><img/><br/></div>")))))
