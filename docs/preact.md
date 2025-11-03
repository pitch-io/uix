# Preact

If you want to go light and have a smaller bundle size, using [Preact](https://preactjs.com/) as a drop-in replacement for React might be the way to go.

1. Install Preact: `npm i preact -D`

2. In your `shadow-cljs.edn` add the following option to map `react` imports to `preact`:

```clojure
:js-options {:resolve {"react" {:target :npm
                                :require "preact/compat"}}}

```

3. Require `preact` instead of `uix.dom` and render your root component:

```clojure
(ns uix.examples
  (:require [uix.core :as uix :refer [$ defui]]
            [preact]))

(defui app []
  ($ :button {:on-click #(js/console.log 123)}
    "Hello"))

(defn init []
  (let [root (js/document.getElementById "root")]
    (preact/render ($ app) root)))
```
