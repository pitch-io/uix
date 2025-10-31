# Server-side rendering

- [Public API](#public-api)
- [How to write cross-platform UI code](#how-to-write-cross-platform-ui-code)
- [Hooks](#hooks)
- [Cross-platform `uix.core` API](#cross-platform-uixcore-api)

`uix.dom.server` implements React's server-side rendering (SSR) on the JVM, so you can serialize UIx components to HTML and optionally hydrate them on the client.

> When to use SSR: faster first paint, better SEO, and the ability to stream HTML before JS loads. Hydration turns the static HTML into interactive UI on the client.

```clojure
(ns my.app
  (:require [uix.core :refer [defui $]]
            [uix.dom.server :as dom.server]))

(defui title-bar []
  ($ :div.title-bar
    ($ :h1 "Hello")))

(dom.server/render-to-static-markup ($ title-bar))
;; "<div class=\"title-bar\"><h1>Hello</h1></div>"
```

## Public API

Similar to `react-dom/server` API there are two functions to render UIx components to HTML string and their streaming counterparts:

- `uix.dom.server/render-to-static-markup` and `uix.dom.server/render-to-static-stream` — generates HTML string, can be used for templating
- `uix.dom.server/render-to-string` and `uix.dom.server/render-to-stream` — generates HTML string that will be hydrated by React on the front-end, when HTML document is loaded

When to choose which:

- Use `render-to-static-markup`/`render-to-static-stream` for HTML that will never hydrate (emails, CMS pages, simple templates).
- Use `render-to-string`/`render-to-stream` when the client will hydrate into a live React app.

You can read more about these [here](https://react.dev/reference/react-dom/server).

## How to write cross-platform UI code

When UI should be rendered on JVM and hydrated on the client, that essentially means that same UI code should be able to run in both Clojure/JVM and ClojureScript/JS environments.

For that it's recommended to put shared code in `.cljc` namespaces and use [reader conditionals](https://clojure.org/guides/reader_conditionals) for platform specific code.

```clojure
;; ui.cljc
(ns app.ui
  (:require [uix.core :refer [defui $]]))

(defui title-bar []
  ($ :div.title-bar
    ($ :h1 "Hello")
    ;; js/console.log doesn't exist on JVM, thus the code
    ;; should be included only for ClojureScript
    ($ :button {:on-click #?(:cljs #(js/console.log %)
                             :clj nil)}
       "+")))

;; server.clj
(ns app.server
  (:require [uix.core :refer [$]]
            [uix.dom.server :as dom.server]
            [app.ui :as ui]))

(defn handle-request
  "Generates HTML to be sent to the client"
  []
  (dom.server/render-to-string ($ ui/title-bar)))

;; client.cljs
(ns app.client
  (:require [uix.core :refer [$]]
            [uix.dom :as dom.client]
            [app.ui :as ui]))

;; Hydrates server generated HTML into dynamic React UI
(dom.client/hydrate-root (js/document.getElementById "root") ($ ui/title-bar))
```

You can find a runnable template repo [here](https://github.com/elken/uix-ssr-demo) which you can adjust yourself as needed.

This repo is setup for everything you'll need, and since it's a template repo you can easily use it to bootstrap your own SSR web apps.

## Hooks

Only a subset of Hooks runs when server rendering. Read [React docs](https://react.dev/) to understand how hooks work when server rendering.

---

## Common pitfalls

- Browser‑only APIs on the server — guard with reader conditionals (`#?(:cljs ...)`).
- Mismatched markup between server and client — ensure props/locale/random values are consistent across environments.
- Long‑running effects in SSR — effects don’t run during SSR; move data fetching outside render or use loaders on the server.
