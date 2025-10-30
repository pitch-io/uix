# Code-splitting and React.lazy

Let's say you want to split out a library of UI components into a separate bundle, so that it can be shared across multiple applications that depend on the library.

```clojure
(ns app.ui.lib
  (:require [uix.core :refer [defui $]]))

(defui modal [{:keys [on-close children]}]
  ...)
```

Similarly to React, with UIx you can use `uix.core/lazy` that will take care of loading the UIx component from a separate module. Then in UI code you can use React's `Suspense` to load the component lazily and display a fallback UI while it's loading. The example below demonstrates how to create and load a lazy component using the loader API from [shadow-cljs](https://github.com/thheller/shadow-cljs).

```clojure
(ns app.core
  (:require [uix.core :refer [defui $]]
            [shadow.lazy]))

;; create shadow's loadable object that references `app.ui.lib/modal` component
(def loadable-modal (shadow.lazy/loadable app.ui.lib/modal))

;; create React's lazy component that loads the modal using shadow's API
(def modal (uix.core/lazy #(shadow.lazy/load loadable-modal)))

(defui app []
  (let [[show-modal? set-show-modal!] (uix.core/use-state false)]
    ($ :div
      ($ :button {:on-click #(set-show-modal! true)})
      ;; wrap the "lazy" `modal` with React's `Suspense` component and provide a fallback UI
      ($ uix.core/suspense {:fallback ($ :div "Loading...")}
        (when show-modal?
          ;; when rendered, React will load the module while displaying the fallback
          ;; and then render the component referenced from the module
          ($ modal {:on-close #(set-show-modal! false)}))))))
```

For the above to compile correctly you'd have to update build config in `shadow-cljs.edn`:

```clojure
{:module-loader true
 :modules {:main {:entries [app.core]}
           :ui-lib {:entries [app.ui.lib]
                    :depends-on #{:main}}}}

```

If you are not familiar with ClojureScript's `:modules` configuration, make sure to read [documentation about this compiler option](https://clojurescript.org/reference/compiler-options#modules).

Also check out [React's documentation page on Code-splitting and React.lazy](https://reactjs.org/docs/code-splitting.html) for more examples.

## When not using react-refresh

If you are using shadow-cljs and doing traditional hot-reloading aka re-render from the root, then the above setup is not going to work. However it can be easily fixed. The only change required is to provide shadow's `loadable` as the second argument to `uix.core/lazy`.

```clojure
(def modal (uix.core/lazy #(shadow.lazy/load loadable-modal)
                          loadable-modal))
```
