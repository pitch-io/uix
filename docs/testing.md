# Testing

There are multiple stages of testing UIs built for web browsers and React/UIx specifically. Let's take a look at each of them, starting from higher level testing.

## End to end testing

End to end (e2e) tests are not really specific to any UI library and they are rather expensive, but it's a good way to make sure that critical user flows are working as expected.

In e2e tests, you simulate user interactions with the browser, using one of popular APIs that control browsers, for example [Puppeteer](https://pptr.dev/) or [Selenium](https://www.selenium.dev/). Each test covers a single user flow, for example, "user logs in", "user adds a product to the cart", "user checks out", etc.

Since Puppeteer is Node.js library and e2e tests do not interact with any of application code, I recommend to write e2e tests in JS as well to avoid additional complexity with tests setup.

## Component testing

In UIx/React, it's common to test components in isolation, but sometimes you need to test how they work together.

For example, you might have a form component with multiple fields and validation rules. You can test each field separately, but you also need to test how the form works when all fields are filled in and the submit button is clicked.

For both isolated unit (or component) tests and integration tests it's recommended to use [React Testing Library](https://testing-library.com/docs/react-testing-library/example-intro). The library provides a set of utilities to render components and hooks, and interact with them.

The following example demonstrates how to test a simple form component that submits data to the server.

```clojure
(defui form []
  (let [[value set-value] (uix/use-state "")
        [submitted? set-submitted] (uix/use-state false)
        submit (fn []
                 (-> (js/fetch "https://localhost:9988/submit"
                               #js {:method "POST"
                                    :body (js/JSON.stringify #js {:value value})
                                    :headers #js {"Content-Type" "application/json"}})
                     (.then #(.json %))
                     (.then #(when (= "ok" (.-message %))
                               (set-submitted true)))))]
    ($ :form
       {:on-submit (fn [e]
                    (.preventDefault e)
                    (submit))}
       ($ :input {:value value
                  :data-testid "form-input"
                  :on-change #(set-value (.. % -target -value))})
       ($ :button {:type "submit"
                   :data-testid "form-submit-button"}
          "Submit")
       (when submitted?
         ($ :p {:data-testid "form-submitted-message"}
            "Submitted!")))))
```

```clojure
(ns app.core-test
  (:require [cljs.test :refer [deftest is async testing run-tests use-fixtures]]
            [uix.core :as uix :refer [defui $]]
            [shadow.cljs.modern :refer [js-await]]
            ["global-jsdom/register"] ; simulates browser's DOM
            ["@testing-library/react" :as rtl]
            ["@testing-library/user-event" :default user-event]
            [msw]
            ["msw/node" :as msw-node]))

;; setup mock server
;; to handle form submission
;; POST https://localhost:9988/submit -> {message: "ok"}
(def server
  (msw-node/setupServer
    (.post msw/http "https://localhost:9988/submit"
      (fn []
        (.json msw/HttpResponse #js {:message "ok"})))))

;; reset mock server handlers after each test
(use-fixtures :each
  {:before (fn []
             (.listen server))
   :after (fn []
            (.resetHandlers server))})

(defn type-input [target value]
  (.type (.setup user-event) target value))

(defn click [target]
  (.click rtl/fireEvent target))

(deftest test-form
  (testing "should submit form"
    (async done ;; this is async test
      (let [container (rtl/render ($ form))
            input (.getByTestId container "form-input")
            button (.getByTestId container "form-submit-button")]
        (type-input input "hello world")
        (click button)
        ;; findByTestId is async operation
        (js-await [message (.findByTestId container "form-submitted-message")]
          (is (= "Submitted!" (.-textContent message)))
          (done))))))

(defn -main []
  (run-tests))
```

Install required dependencies for integration testing with React Testing Library

```bash
npm i -D @testing-library/react @testing-library/user-event msw jsdom global-jsdom
```

## Hooks testing

Hooks are tested similarly to components, React Testing Library provides utilities to render hooks and test their behavior.

```clojure
(ns app.core-test
  (:require [cljs.test :refer [deftest is testing]]
            [uix.core :as uix :refer [defui $ defhook]]
            ["global-jsdom/register"]
            ["@testing-library/react" :as rtl]))

(defhook use-set-document-title [title]
  (uix/use-effect
    #(set! (.-title js/document) title)
    [title]))

(defhook use-encoded-uri [uri]
  (uix/use-memo #(js/encodeURI uri) [uri]))

(deftest test-hooks

  (testing "should update document title"
    (let [container (rtl/renderHook #(use-set-document-title "hello world"))
          title (.-title js/document)]
      (is (= "hello world" title))))

  (testing "should encode URI"
    (let [container (rtl/renderHook #(use-encoded-uri "https://testing-library.com?q= 1"))
          result (.. container -result -current)]
      (is (= "https://testing-library.com?q=%201" result)))))
```
