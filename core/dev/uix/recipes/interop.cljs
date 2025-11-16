(ns uix.recipes.interop
  "This recipe shows how third-party React components can be used inside
  of UIx components and vice versa.

  In order to use JS component in UIx component just render it as usual, via `$` macro.
  Props map will be transformed into JS Object with top-level keys camel-cased.
  Note that values are not transformed, thus it's up to you to convert them
  before passing into JS component.

  UIx components can be adapted to JS React components
  using `uix.core/as-react`. It takes a function that takes
  JS props object transformed into immutable map.
  Again, values are not transformed."
  (:require [uix.core :refer [defui $] :as uix]
            [react]))

(set! js/globalThis.h react/createElement)

(def js-list
  (js*
    "function({ items, itemComponent }) {
      return h('ul', {}, items.map(n => h(itemComponent, { key: n }, n)));
    }"))

(defui list-item [{:keys [children]}]
  ($ :li children))

(def list-item*
  (uix/as-react #($ list-item %)))

(defui recipe []
  ($ js-list {:items #js [1 2 3]
              :item-component list-item*}))
