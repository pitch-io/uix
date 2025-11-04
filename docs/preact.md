# Preact

If you want to go light and have a smaller bundle size, using [Preact](https://preactjs.com/) as a drop-in replacement for React might be the way to go.

1. Install Preact: `npm i preact -D`

2. In your `shadow-cljs.edn` add the following option to map `react` imports to `preact`:

```clojure
:js-options {:resolve {"react" {:target :npm
                                :require "preact/compat"}
                        "react-dom" {:target :npm
                                    :require "preact/compat"}
                        "react-dom/client" {:target :npm
                                            :require "preact/compat/client"}}}
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

## Gotchas

### Rendering a list of items

Unlike React, Preact can't render iterable collections other than `Array`. When you render a list of items in ClojureScript via `map`, `for`, etc. this produces an iterable object which Preact can't unwind into an array. To make this work you need to convert a collection into an array manually:

```clojure
(into-array
  (for [item items]
    ($ :li {:key item}
      item)))
```

### Hot-reloading

Preact doesn't support [react-refresh](/docs/hot-reloading.md). You have to use traditional, render from the root, style hot-reloading.

```clojure
(defn init []
  (let [root (js/document.getElementById "root")]
    (preact/render ($ app)
                   root)))

(defn ^:dev/after-load reload []
  (init))
```
