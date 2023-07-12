(ns uix.core-test
  (:require [clojure.string :as str]
            [clojure.test :refer [async deftest is run-tests testing]]
            [react :as r]
            [react-dom]
            [uix.compiler.attributes :as attrs]
            [uix.core :refer [$ defui]]
            [uix.dev]
            [uix.hiccup :refer [row-compiled]]
            [uix.lib]
            [uix.test-utils :as t]))

(deftest test-use-ref
  (uix.core/defui test-use-ref-comp [_]
    (let [ref1 (uix.core/use-ref)
          ref2 (uix.core/use-ref 0)]
      (is (nil? (.-current ref1)))
      (is (nil? @ref1))
      (set! (.-current ref1) :x)
      (is (= :x (.-current ref1)))

      (is (= 0 (.-current ref2)))
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
  (uix.core/defui test-memoize-comp [{:keys [x]}]
    (is (= 1 x))
    ($ :h1 x))
  (let [f (uix.core/memo test-memoize-comp)]
    (is (t/react-element-of-type? f "react.memo"))
    (is (= "<h1>1</h1>" (t/as-string ($ f {:x 1}))))))

(deftest test-html
  (is (t/react-element-of-type? ($ :h1 1) "react.element")))

(deftest test-defui
  (defui h1 [{:keys [children]}]
    ($ :h1 {} children))
  (is (= (t/as-string ($ h1 {} 1)) "<h1>1</h1>")))

(deftest test-jsfy-deps
  (is (= [1 "str" "k/w" "uix.core/sym" "b53887c9-4910-4d4e-aad9-f3487e6e97f5" nil [] {} #{}]
         (vec (uix.core/jsfy-deps [1 "str" :k/w 'uix.core/sym #uuid "b53887c9-4910-4d4e-aad9-f3487e6e97f5" nil [] {} #{}]))))
  (is (= [1 "str" "k/w" "uix.core/sym" "b53887c9-4910-4d4e-aad9-f3487e6e97f5" nil [] {} #{}]
         (vec (uix.core/jsfy-deps #js [1 "str" :k/w 'uix.core/sym #uuid "b53887c9-4910-4d4e-aad9-f3487e6e97f5" nil [] {} #{}]))))
  (is (= #{} (uix.core/jsfy-deps #{})))
  (is (= {} (uix.core/jsfy-deps {}))))

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
                              (this-as this
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
    (is (map? props))
    (is (= "TEXT" (:text props)))
    ($ :h1 (:text props)))
  (let [h1 (uix.core/as-react test-c)
        el (h1 #js {:text "TEXT"})
        props (.-props el)]
    (is (= (.-type el) "h1"))
    (is (= (.-children props) "TEXT"))))

(defui test-source-component []
  "HELLO")

(deftest test-source
  (is (= (uix.core/source test-source-component)
         "(defui test-source-component []\n  \"HELLO\")"))
  (is (= (uix.core/source uix.hiccup/form-compiled)
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

(deftest test-uix-component?-predicate
  (let [f (uix.core/fn [_] ($ :p "lorem ipsum"))]
    (is (true? (uix.dev/uix-component? f))))
  (let [f* (constantly ($ :p "lorem ipsum"))]
    (is (false? (uix.dev/uix-component? f*)))))

(defn -main []
  (run-tests))
