# Interop with Reagent

## Using Reagent components in UIx

In order to use a Reagent component in UIx you need to wrap Reagent's Hiccup with `r/as-element` so that Reagent can take care of Hiccup and convert it into React calls.

```clojure
(defn reagent-component []
  ...)

(defui uix-component []
  ($ :div (r/as-element [reagent-component])))
```

## Using UIx components in Reagent

When using a UIx component in Reagent (or anywhere else) you can continue to use the`$` macro without changing anything. The macro will make sure that UIx component is properly created no matter which context it is used in.

```clojure
(defui uix-component []
  ...)

(defn reagent-component []
  [:div ($ uix-component)])
```

## Syncing with ratoms and re-frame

External data sources can be consumed in hooks-based components via `useSyncExternalStoreWithSelector` from the `"use-sync-external-store"` package. This part is only concerned about making the UI components reactive on external data sources.

To sync UIx components with re-frame or Reagent's reactions use `uix.re-frame/use-subscribe` and `uix.reagent/use-reaction` hooks respectively.

### Usage

```clojure
(ns my.app
  (:require [reagent.core :as r]
            [uix.reagent :as ur]
            [uix.re-frame :as urf]
            [uix.core :as uix :refer [defui $]]))

(def counter (r/atom 0))

(defui title-bar []
  (let [n (ur/use-reaction counter) ;; Reagent's reaction
        title (urf/use-subscribe [:app/title])] ;; re-frame subscription
    ($ :div
      title
      ($ :button {:on-click #(swap! counter inc)}
        n))))
```
