(ns uix.core-test
  (:require [cljs.spec.alpha :as s]
            [clojure.test :refer [deftest is async testing run-tests]]
            [uix.core :refer [defui $]]
            [uix.lib]
            [react :as r]
            ["@testing-library/react" :as rtl]
            [uix.test-utils :as t]
            [uix.compiler.attributes :as attrs]
            [uix.uix :refer [row-compiled]]
            [clojure.string :as str]))

(deftest test-use-ref
  (uix.core/defui test-use-ref-comp [_]
    (let [ref1 (uix.core/use-ref)
          ref2 (uix.core/use-ref 0)]
      (is (nil? @ref1))
      (is (nil? @ref1))
      (reset! ref1 :x)
      (is (= :x @ref1))

      (is (= 0 @ref2))
      (is (= 0 @ref2))
      (reset! ref2 1)
      (is (= 1 @ref2))
      (swap! ref2 inc)
      (is (= 2 @ref2))
      (swap! ref2 + 2)
      (is (= 4 @ref2))
      "x"))
  (t/as-string ($ test-use-ref-comp)))

(deftest test-memoize
  (testing "manual memo"
    (uix.core/defui test-memoize-comp [{:keys [x]}]
      (is (= 1 x))
      ($ :h1 x))
    (let [f (uix.core/memo test-memoize-comp)]
      (is (t/react-element-of-type? f "react.memo"))
      (is (= "<h1>1</h1>" (t/as-string ($ f {:x 1}))))))
  (testing "^:memo"
    (uix.core/defui ^:memo test-memoize-meta-comp [{:keys [x]}]
      (is (= 1 x))
      ($ :h1 x))
    (is (t/react-element-of-type? test-memoize-meta-comp "react.memo"))
    (is (= "<h1>1</h1>" (t/as-string ($ test-memoize-meta-comp {:x 1}))))))

(deftest test-html
  (is (t/react-element-of-type? ($ :h1 1) "react.transitional.element")))

(deftest test-defui
  (defui h1 [{:keys [children]}]
    ($ :h1 {} children))
  (is (= (t/as-string ($ h1 {} 1)) "<h1>1</h1>")))

(deftest test-lazy
  (async done
         (let [expected-value :x
               lazy-f (uix.core/lazy (fn [] (js/Promise. (fn [res] (js/setTimeout #(res expected-value) 100)))))]
           (is (.-uix-component? lazy-f))
           (try
             (._init lazy-f (.-_payload lazy-f))
             (catch :default e
               (is (instance? js/Promise e))
               (.then e (fn [v]
                          (is (= expected-value (.-default ^js v)))
                          (done))))))))

(deftest test-create-class
  (let [actual (atom {:constructor {:this nil :props nil}
                      :getInitialState {:this nil}
                      :render {:state nil :props nil}
                      :componentDidMount false
                      :componentWillUnmount false})
        component (uix.core/create-class
                   {:displayName "test-comp"
                    :constructor (fn [this props]
                                   (swap! actual assoc :constructor {:this this :props props}))
                    :getInitialState (fn [this]
                                       (swap! actual assoc :getInitialState {:this this})
                                       #js {:x 1})
                    :componentDidMount #(swap! actual assoc :componentDidMount true)
                    :componentWillUnmount #(swap! actual assoc :componentWillUnmount true)
                    :render (fn []
                              (this-as ^react/Component this
                                       (swap! actual assoc :render {:state (.-state this) :props (.-props this)})
                                       "Hello!"))})]
    (t/with-react-root
      ($ component {:x 1})
      (fn [node]
        (is (instance? component (-> @actual :constructor :this)))
        (is (-> @actual :constructor :props .-argv (= {:x 1})))
        (is (instance? component (-> @actual :getInitialState :this)))
        (is (-> @actual :render :props .-argv (= {:x 1})))
        (is (-> @actual :render :state .-x (= 1)))
        (is (:componentDidMount @actual))
        (is (= "Hello!" (.-textContent node))))
      #(is (:componentWillUnmount @actual)))))

(deftest test-convert-props
  (testing "shallow conversion"
    (let [obj (attrs/convert-props
               {:x 1
                :y :keyword
                :f identity
                :style {:border :red
                        :margin-top "12px"}
                :class :class
                :for :for
                :charset :charset
                :hello-world "yo"
                "yo-yo" "string"
                :plugins [1 2 3]
                :data-test-id "hello"
                :aria-role "button"}
               #js []
               true)]
      (is (= 1 (.-x obj)))
      (is (= "keyword" (.-y obj)))
      (is (= identity (.-f obj)))
      (is (= "red" (.. obj -style -border)))
      (is (= "12px" (.. obj -style -marginTop)))
      (is (= "class" (.-className obj)))
      (is (= "for" (.-htmlFor obj)))
      (is (= "charset" (.-charSet obj)))
      (is (= "yo" (.-helloWorld obj)))
      (is (= [1 2 3] (.-plugins obj)))
      (is (= "string" (aget obj "yo-yo")))
      (is (= "hello" (aget obj "data-test-id")))
      (is (= "button" (aget obj "aria-role")))
      (is (= "a b c" (.-className (attrs/convert-props {:class [:a :b "c"]} #js [] true)))))))

(deftest test-as-react
  (uix.core/defui test-c [props]
    ($ :h1 (:text props)))
  (let [test-c-react (uix.core/as-react #($ test-c %))
        el (test-c-react #js {:text "TEXT"})
        props (.. ^js el -props -argv)]
    (is (= (.-type el) test-c))
    (is (= (:text props) "TEXT"))))

(defui test-source-component []
  "HELLO")

(deftest test-source
  (is (= (uix.core/source test-source-component)
         "(defui test-source-component []\n  \"HELLO\")"))
  (is (= (uix.core/source uix.uix/form-compiled)
         "(defui form-compiled [{:keys [children]}]\n  ($ :form children))"))
  (is (= (uix.core/source row-compiled)
         "(defui row-compiled [{:keys [children]}]\n  ($ :div.row children))")))

(defui comp-42336 [{:keys [comp-42336]}]
  (let [comp-42336 1]
    "hello"))

(deftest test-42336
  (is (.-uix-component? ^js comp-42336))
  (is (= (.-displayName comp-42336) (str `comp-42336))))

(defui comp-props-map [props] 1)

(deftest test-props-map
  (is (= 1 (comp-props-map #js {:argv nil})))
  (is (= 1 (comp-props-map #js {:argv {}})))
  (is (thrown-with-msg? js/Error #"UIx component expects a map of props, but instead got \[\]" (comp-props-map #js {:argv []}))))

(deftest test-fn
  (let [anon-named-fn (uix.core/fn fn-component [{:keys [x]}] x)
        anon-fn (uix.core/fn [{:keys [x]}] x)]

    (is (.-uix-component? ^js anon-named-fn))
    (is (= (.-displayName anon-named-fn) "fn-component"))

    (is (.-uix-component? ^js anon-fn))
    (is (str/starts-with? (.-displayName anon-fn) "uix-fn"))

    (t/with-react-root
      ($ anon-named-fn {:x "HELLO!"})
      (fn [node]
        (is (= "HELLO!" (.-textContent node)))))

    (t/with-react-root
      ($ anon-fn {:x "HELLO! 2"})
      (fn [node]
        (is (= "HELLO! 2" (.-textContent node)))))))

(defui dyn-uix-comp [props]
  ($ :button props))

(defn dyn-react-comp [^js props]
  ($ :button
     {:title (.-title props)
      :children (.-children props)}))

(deftest test-dynamic-element
  (testing "dynamic element as a keyword"
    (let [as :button#btn.action]
      (is (= "<button title=\"hey\" id=\"btn\" class=\"action\">hey</button>"
             (t/as-string ($ as {:title "hey"} "hey"))))))
  (testing "dynamic element as uix component"
    (let [as dyn-uix-comp]
      (is (= "<button title=\"hey\">hey</button>"
             (t/as-string ($ as {:title "hey"} "hey"))))))
  (testing "dynamic element as react component"
    (let [as dyn-react-comp]
      (is (= "<button title=\"hey\">hey</button>"
             (t/as-string ($ as {:title "hey"} "hey")))))))

(deftest test-class-name-attr
  (let [props {:class "two" :class-name "three" :className "four"}
        props2 {:class ["two"] :class-name ["three"] :className ["four"]}]
    (is (= "<a class=\"one two three four\"></a>"
           (t/as-string ($ :a.one {:class "two" :class-name "three" :className "four"}))))
    (is (= "<a class=\"one two three four\"></a>"
           (t/as-string ($ :a.one props))))
    (is (= "<a class=\"one two three four\"></a>"
           (t/as-string ($ :a.one {:class ["two"] :class-name ["three"] :className ["four"]}))))
    (is (= "<a class=\"one two three four\"></a>"
           (t/as-string ($ :a.one props2))))))

(defonce *error-state (atom nil))

(def error-boundary
  (uix.core/create-error-boundary
   {:derive-error-state (fn [error]
                          {:error error})
    :did-catch          (fn [error info]
                          (reset! *error-state error))}
   (fn [[state _set-state!] {:keys [children]}]
     (if (:error state)
       ($ :p "Error")
       children))))

(defui throwing-component [{:keys [throw?]}]
  (when throw?
    (throw "Component throws")))

(defui error-boundary-no-elements []
  ($ throwing-component {:throw? false}))

(defui error-boundary-catches []
  ($ error-boundary
     ($ throwing-component {:throw? true})))

(defui error-boundary-renders []
  ($ error-boundary
     ($ throwing-component {:throw? false})
     ($ :p "After")))

(defui error-boundary-children []
  ($ error-boundary
     ($ throwing-component {:throw? false})
     ($ :p "After throwing")
     ($ :p "After throwing 2")))

(deftest ssr-error-boundaries
  (t/with-react-root
    ($ error-boundary-no-elements)
    #(is (= (.-textContent %) "")))

  (t/with-react-root
    ($ error-boundary-catches)
    #(is (= (.-textContent %) "Error")))

  (t/with-react-root
    ($ error-boundary-renders)
    #(is (= (.-textContent %) "After")))

  (t/with-react-root
    ($ error-boundary-children)
    #(is (= (.-textContent %) "After throwingAfter throwing 2"))))

(deftest ssr-error-boundary-catch-fn
  (reset! *error-state nil)
  (t/with-react-root
    ($ error-boundary-catches)
    (fn [_]
      ;; Tests that did-catch does run
      (is (str/includes? @*error-state "Component throws")))))

(deftest js-obj-props
  (let [el ($ :div #js {:title "hello"} 1 2 3)]
    (is (= "hello" (.. el -props -title)))))

(defui forward-ref-interop-uix-component [{:keys [state] :as props}]
  (reset! state props)
  nil)

(deftest test-render-context
  (let [result (atom nil)
        ctx (uix.core/create-context "hello")
        comp (uix.core/fn []
               (let [v (uix.core/use-context ctx)]
                 (reset! result v)))
        _ (rtl/render
            ($ ctx {:value "world"}
               ($ comp)))]
    (is (= "world" @result))))

(deftest test-context-value-clojure-primitive
  (let [result (atom nil)
        ctx (uix.core/create-context :hello)
        comp (uix.core/fn []
               (let [v (uix.core/use-context ctx)]
                 (reset! result v)
                 nil))
        _ (rtl/render
            ($ ctx {:value :world}
               ($ comp)))]
    (is (= :world @result))))

(deftest test-forward-ref-interop
  (let [state (atom nil)
        forward-ref-interop-uix-component-ref (uix.core/forward-ref forward-ref-interop-uix-component)
        _ (rtl/render
           (react/cloneElement
            ($ forward-ref-interop-uix-component-ref {:y 2 :a {:b 1} :state state} "meh")
            #js {:ref #js {:current 6} :x 1 :t #js [2 3 4] :o #js {:p 8}}
            "yo"))
        {:keys [x y a t o ref]} @state]
    (is (= x 1))
    (is (= y 2))
    (is (= a {:b 1}))
    (is (= (vec t) [2 3 4]))
    (is (= (.-p o) 8))
    (is (= (.-current ref) 6))
    (is (= state (:state @state)))))

(deftest test-clone-element
  (testing "cloning component element"
    (uix.core/defui test-clone-element-comp [])
    (let [el (uix.core/clone-element ($ test-clone-element-comp {:title 0 :key 1 :ref 2} "child")
                                     {:data-id 3}
                                     "child2")]
      (is (= test-clone-element-comp (.-type el)))
      (is (= "1" (.-key el)))
      (is (= {:title 0 :ref 2 :data-id 3 :children ["child2"]}
             (.. el -props -argv)))))
  (testing "cloning primitive element"
    (let [el1 (uix.core/clone-element ($ :div {:title 0 :key 1 :ref 2} "child")
                                      {:data-id 3}
                                      "child2")
          el2 (uix.core/clone-element (react/createElement "div" #js {:title 0 :key 1 :ref 2} "child")
                                      {:data-id 3}
                                      "child2")]
      (doseq [^js el [el1 el2]]
        (is (= "div" (.-type el)))
        (is (= "1" (.-key el)))
        (is (= 2 (.-ref el)))
        (is (= 0 (.. el -props -title)))
        (is (= 3 (aget (.. el -props) "data-id")))
        (is (= "child2" (aget (.. el -props -children) 0)))))))

(deftest test-rest-props
  (testing "defui should return rest props"
    (uix.core/defui rest-component [{:keys [a b] :& props}]
      [props a b])
    (is (= [{:c 3} 1 2] (rest-component #js {:argv {:a 1 :b 2 :c 3}})))
    (is (= [{} 1 2] (rest-component #js {:argv {:a 1 :b 2}}))))
  (testing "fn should return rest props"
    (let [f (uix.core/fn rest-component [{:keys [a b] :& props}]
              [props a b])]
      (is (= [{:c 3} 1 2] (f #js {:argv {:a 1 :b 2 :c 3}})))
      (is (= [{} 1 2] (f #js {:argv {:a 1 :b 2}}))))))

(deftest test-component-fn-name
  (testing "defui name"
    (defui component-fn-name [])
    (is (= "uix.core-test/component-fn-name"
           (.-name component-fn-name))))
  (testing "fn name"
    (let [f1 (uix.core/fn component-fn-name [])
          f2 (uix.core/fn [])]
      (is (= "component-fn-name" (.-name f1)))
      (is (str/starts-with? (.-name f2) "uix-fn")))))

(deftest test-props-check
  (s/def ::x string?)
  (s/def ::props (s/keys :req-un [::x]))
  (testing "props check in defui"
    (uix.core/defui props-check-comp
      [props]
      {:props [::props]}
      props)
    (try
      (props-check-comp #js {:argv {:x 1}})
      (catch js/Error e
        (is (str/starts-with? (ex-message e) "Invalid argument"))))
    (try
      (props-check-comp #js {:argv {:x "1"}})
      (catch js/Error e
        (is false))))
  (testing "props check in fn"
    (let [f (uix.core/fn
              [props]
              {:props [::props]}
              props)]
      (try
        (f #js {:argv {:x 1}})
        (catch js/Error e
          (is (str/starts-with? (ex-message e) "Invalid argument"))))
      (try
        (f #js {:argv {:x "1"}})
        (catch js/Error e
          (is false))))))

(deftest test-spread-props
  (testing "primitive element"
    (testing "static"
      (let [props {:width 100 :style {:color :blue}}
            el (uix.core/$ :div {:on-click prn :& props} "child")]
        (is (= "div" (.-type el)))
        (is (= prn (.. el -props -onClick)))
        (is (= 100 (.. el -props -width)))
        (is (= "blue" (.. el -props -style -color)))))
    (testing "dynamic"
      (let [tag :div
            props {:width 100 :style {:color :blue}}
            el (uix.core/$ tag {:on-click prn :& props} "child")]
        (is (= "div" (.-type el)))
        (is (= prn (.. el -props -onClick)))
        (is (= 100 (.. el -props -width)))
        (is (= "blue" (.. el -props -style -color))))))
  (testing "component element"
    (testing "static"
      (defui spread-props-comp [])
      (let [props {:width 100 :style {:color :blue}}
            el (uix.core/$ spread-props-comp {:on-click prn :& props} "child")
            props (.. el -props -argv)]
        (is (= spread-props-comp (.-type el)))
        (is (= prn (:on-click props)))
        (is (= 100 (:width props)))
        (is (= :blue (-> props :style :color)))))
    (testing "dynamic"
      (let [static-comp spread-props-comp
            props {:width 100 :style {:color :blue}}
            el (uix.core/$ static-comp {:on-click prn :& props} "child")
            props (.. el -props -argv)]
        (is (= static-comp (.-type el)))
        (is (= prn (:on-click props)))
        (is (= 100 (:width props)))
        (is (= :blue (-> props :style :color))))))
  (testing "js interop component element"
    (defn spread-props-js-comp [])
    (let [props {:width 100 :style {:color :blue}}
          el (uix.core/$ spread-props-js-comp {:on-click prn :& props} "child")]
      (is (= spread-props-js-comp (.-type el)))
      (is (= prn (.. el -props -onClick)))
      (is (= 100 (.. el -props -width)))
      (is (= "blue" (.. el -props -style -color)))))
  (testing "multiple props"
    (let [props1 {:width 100 :style {:color :blue}}
          props2 {:height 200 :style {:color :red}}
          el (uix.core/$ :div {:on-click prn
                               :on-mouse-down prn
                               :& [props1 props2 {:title "hello"} #js {:onClick identity}]}
                         "child")]
      (is (= "div" (.-type el)))
      (is (= prn (.. el -props -onMouseDown)))
      (is (= 100 (.. el -props -width)))
      (is (= 200 (.. el -props -height)))
      (is (= "hello" (.. el -props -title)))
      (is (= identity (.. el -props -onClick)))
      (is (= "red" (.. el -props -style -color))))))

(deftest test-204
  (testing "should use Reagent's input when Reagent's context is reactive"
    (set! reagent.impl.util/*non-reactive* false)
    (is (identical? (.-type ($ :input)) uix.compiler.input/reagent-input)))
  (testing "should use Reagent's input when enabled explicitly"
    (set! uix.compiler.input/*use-reagent-input-enabled?* true)
    (is (identical? (.-type ($ :input)) uix.compiler.input/reagent-input))
    (set! uix.compiler.input/*use-reagent-input-enabled?* nil))
  (testing "should not use Reagent's input when enabled explicitly"
    (set! uix.compiler.input/*use-reagent-input-enabled?* false)
    (is (identical? (.-type ($ :input)) "input"))
    (set! uix.compiler.input/*use-reagent-input-enabled?* nil)))


(deftest test-hoist-inline
  (defui ^:test/inline test-hoist-inline-1 []
    (let [title "hello"
          tag :div
          props {:title "hello"}]
      (js->clj
        #js [($ :div {:title "hello"} ($ :h1 "yo"))
             ($ :div {:title title} ($ :h1 "yo"))
             ($ tag {:title "hello"} ($ :h1 "yo"))
             ($ :div props ($ :h1 "yo"))])))
  (is (apply = (map #(-> % (assoc "_store" {"validated" 1})
                           (update "props" dissoc "children"))
                    (test-hoist-inline-1))))
  (is (apply = (->> (test-hoist-inline-1)
                    (mapcat #(let [children (get-in % ["props" "children"])]
                               (if (or (vector? children) (js/Array.isArray children))
                                 children
                                 [children])))
                    (map #(assoc % "_store" {"validated" 1})))))

  (is (->> (js/Object.keys js/uix.core-test)
           (filter #(str/starts-with? % "uix_aot_hoisted"))
           count
           (= 2))))

(deftest test-css-variables
  (testing "should preserve CSS var name"
    (let [el ($ :div {:style {:--main-color "red"
                              "--text-color" "blue"}})
          styles {:--main-color "red"
                  "--text-color" "blue"}
          el1 ($ :div {:style styles})]
      (is (= "red" (aget (.. el -props -style) "--main-color")))
      (is (= "blue" (aget (.. el -props -style) "--text-color")))
      (is (= "red" (aget (.. el1 -props -style) "--main-color")))
      (is (= "blue" (aget (.. el1 -props -style) "--text-color"))))))

(defn -main []
  (run-tests))
