# React Server Components

UIx implements [React Server Components (RSC)](https://react.dev/reference/rsc/server-components) in Clojure JVM.

## Server Components

Server components are defined in `.clj` files and run on server. The purpose of a server component is to render HTML (or RSC payload). Server components can access database, fetch data and render client components.

Since server components run in Clojure JVM, they do not have access to any of JavaScript APIs.

A typical server component looks like a normal UIx component.

```clojure
(defui list-item [{:keys [item]}]
  (let [{:keys [title]} item]
    ($ :li title)))

(defui sidebar []
  (let [items (fetch-agenda)]
    ($ :aside
      ($ typeahead-input)
      ($ :ul
        (for [item items]
          ($ list-item {:key (:id item) :item item}))))))
```

## Client Components

Client components are defined either in `.cljs` or `.cljc` files and run on the client. At build time, client components code is compiled to JavaScript by [shadow-cljs](https://github.com/thheller/shadow-cljs). Since server components can render client components, those client components should be written in `.cljc` files.

A client-only component looks like a normal UIx component. Components that can be rendered by server components should be marked with `^:client` meta tag.

Since client components can be rendered on server and they are written in `.cljc` files, you have to use [reader conditionals](https://clojure.org/guides/reader_conditionals) to mark client-specific code via `#?(:cljs ...)` reader conditional, since JavaScript APIs doesn't exist on server.

```clojure
(defui input [props]
  ($ :input.text-input props))

;; used in `sidebar` server component in previous example
(defui ^:client typeahead-input []
  ($ input ...))
```

To render client-only component, wrap it with `use-client` hook. The hook excludes the component from server rendering pass. Here's an example using react-select library.

```clojure
;; client, .cljc
(ns client.ui
  (:require [uix.core :as uix :refer [defui $]]
            [uix.rsc :as rsc]
            #?(:cljs [react-select :default Select])))

(defui ^:client select [{:keys [options]}]
  (rsc/use-client {:fallback "loading select"}
    #?(:cljs ($ Select {:options (clj->js options)}))))

;; server, .clj
(ns server.ui
  (:require [uix.core :as uix :refer [defui $]]
            [client.ui :as ui]))

(defui page []
  ($ :html
    ($ :body
      ($ ui/select {:options [{:value 1 :label "one"}
                              {:value 2 :label "two"}]}))))
```

## Server Actions

Server action is a Clojure function that runs on server and is exposed transparently to the client as backend API call.

```clojure
;; server, .clj
(ns server.actions
  (:require [uix.rsc :refer [defaction]]))

(defaction like-article [{:keys [id]}]
  (sql/exec {:update :articles,
             :set    {:likes [:+ :likes 1]},
             :where  [:= :id id]}))

;; client, .cljc
(ns client.ui
  (:require [server.actions :as actions]))

(defui like-button [{:keys [id]}]
  ($ :button {:on-click #(actions/like-article {:id id})}
    "Like"))
```

When the above client code is compiled to JavaScript, the `:on-click` handler becomes something like this `#(call-server "/api" {:action "like-article" :id id})`.

It's also possible to pass server actions as props to client components, so that client code won't refer server code at all.

```clojure
;; server, .clj
(ns server.actions
  (:require [uix.rsc :refer [defaction]]))

(defaction like-article [{:keys [id]}]
  (sql/exec {:update :articles,
             :set    {:likes [:+ :likes 1]},
             :where  [:= :id id]}))

;; server, .clj
(ns server.ui
  (:require [server.actions :as actions]
            [client.ui]))

(defui article [{:keys [title content id]}]
  ($ :article
    ($ :h1 title)
    content
    ($ :footer
      ($ client.ui/like-button
        {:on-like actions/like-article
         :id id}))))

;; client, .cljc
(ns client.ui
  (:require [server.actions :as actions]))

(defui like-button [{:keys [id on-like]}]
  ($ :button {:on-click #(on-like {:id id})}
    "Like"))
```

Furthermore, it's possible to partially apply server actions on server. Keep in mind that server arguments bound to the action function are _still sent to the client_.

```clojure
;; server, .clj
(ns server.actions
  (:require [uix.rsc :refer [defaction]]))

(defaction like-article [{:keys [id]}]
  (sql/exec {:update :articles,
             :set    {:likes [:+ :likes 1]},
             :where  [:= :id id]}))

;; server, .clj
(ns server.ui
  (:require [server.actions :as actions]
            [uix.rsc :as rsc]
            [client.ui]))

(defui article [{:keys [title content id]}]
  ($ :article
    ($ :h1 title)
    content
    ($ :footer
      ($ client.ui/like-button
        {:on-like (rsc/partial actions/like-article {:id id})}))))

;; client, .cljc
(ns client.ui
  (:require [server.actions :as actions]))

(defui like-button [{:keys [on-like]}]
  ($ :button {:on-click #(on-like)}
    "Like"))
```

### Form actions

While the above example does execute a server action, it won't update contents of a page, if it is expected that updated state on the server should be instantly reflected on the client.

Instead of manually calling a server action from event handler, use `:form` element to submit data to a server. The server will re-render server components and respond to form submission with updated RSC payload for current route. UIx will instantly display new content on a screen. This setup doesn't require client components at all.

```clojure
;; server, .clj
(ns server.actions
  (:require [uix.rsc :refer [defaction]]))

(defaction like-article [{:keys [id]}]
  (sql/exec {:update :articles,
             :set    {:likes [:+ :likes 1]},
             :where  [:= :id id]}))

;; server, .clj
(ns server.ui
  (:require [server.actions :as actions]
            [uix.rsc :as rsc]))

(defui like-button [{:keys [likes]}]
  ($ :button {:type :submit}
    "Like " likes))

(defui article [{:keys [title content id likes]}]
  ($ :article
    ($ :h1 title)
    content
    ($ :footer
      ($ :form {:action (rsc/partial like-button {:id id})}
        ($ like-button
          {:likes likes})))))
```

Note that `:form` element takes partially applied action and the `like-button` button is of type `:submit` now. Normally, in pure HTML, pressing the button would cause a browser to submit data to a server and reload the page. But here React will intercept form submission and send `FormData` object asynchrounously. Server response is then picked up by UIx router and put on a screen.

## Caching

### Request scoped caching

When server components fetch data from external data sources multiple times for a single client request, you might want to cache data fetching to make sure that all server components render the same data.

Most of the time you can fetch data in a single location and pass it to child components via props. But other time you might need to make identical requests in a couple of different components.

In this example, when both `article` and `page` components are rendered in the same request and both of them fetch the same data, `rsc/cache` will memoize the first call and return cached result for all subsequent executions.

`rsc/cache` is request scoped, the cache exists for a lifetime of a single render pass, served by your web server, so it doesn't leak into other client requests.

```clojure
(ns server.ui
  (:require [uix.rsc :as rsc]))

(defn fetch-data [id]
  (rsc/cache [:data id]
    (http/get ... {:id id})))

(defui article [{:keys [id]}]
  (let [data (fetch-data id)]
    ...))

(defui page [{:keys [id]}]
  (let [data (fetch-data id)]
    ...))
```

### Batching

TODO

## Non-blocking server rendering

When fetching data in server components you'll inevitably delay client request. Depending on how much time blocking code takes to fetch data, the client will see a blank page because it doesn't receive any HTML for initial request.

To solve this problem, you can rendered components that include blocking code on a separate thread, by wrapping them with `suspense` component.

```clojure
(defui article [{:keys [id]}]
  (let [data (fetch-data id)]
    ...))

(defui placeholder []
  ($ :span "Loading..."))

(defui page [{:keys [id]}]
  ($ :html
    ($ :head)
    ($ :body
      ($ page-header)
      ($ sidebar)
      ($ uix/suspense {:fallback ($ placeholder)}
        ($ article))
      ($ page-footer))))
```

This way server will send initial HTML, with specified `:fallback` placeholders, over persistent HTTP connection, and later, when suspended components unblock, chunks of rendered HTML will be streamed to the client.
