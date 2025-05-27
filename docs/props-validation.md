# Props validation

While in React it's common to use [`PropTypes`](https://legacy.reactjs.org/docs/typechecking-with-proptypes.html) for runtime validation or [TypeScript](https://www.typescriptlang.org/docs/handbook/jsx.html#function-component) for static type checking, in Clojure we can leverage `:pre` conditions to assert component's props at runtime.

Here's a typical example using `defn`.

```clojure
(defn user->full-name
  [{:keys [fname lname]}]
  {:pre [(string? fname) (string? lname)]}
  (str fname " " lname))

(user->full-name {:lname "Doe"})

;; Execution error (AssertionError) at user/user->full-name (form-init2978563934614804694.clj:1).
;; Assert failed: (string? fname)
```

In UIx, the syntax of the `defui` macro inherits most properties of `defn`, including pre conditions.

```clojure
(defui button
  [{:keys [children on-click]}]
  {:pre [(fn? on-click)]}
  ($ :button {:on-click on-click}
    children))
```

To improve things further and leverage `clojure.spec` for rich data validation and helpful error messages, it's recommended to use [adamrenklint/preo](https://github.com/adamrenklint/preo) library.

```clojure
(ns app.ui
  (:require [clojure.spec.alpha :as s]
            [preo.core :as p]))

(s/def :prop/on-click fn?)
(s/def ::button (s/keys :req-un [:prop/on-click]))

(defui button
  [{:keys [children on-click] :as props}]
  {:pre [(p/arg! ::button props)]}
  ($ :button {:on-click on-click}
    children))

;; trigger spec error
($ button {})


Invalid argument: props
-- Spec failed --------------------

  {}

should contain key: :on-click

| key       | spec |
|===========+======|
| :on-click | fn?  |

-- Relevant specs -------

:app.ui/button:
  (clojure.spec.alpha/keys :req-un [:prop/on-click])

-------------------------
Detected 1 error
```

> Most likely you don't want those runtime checks in production. Make sure `:elide-asserts` compiler option is set to `true`, unless if you are using `shadow-cljs`, where the option is set to `true` for `release` builds by default.

To validate React `children` you can use the following spec.

```clojure
(s/def :react/element
  (s/or
    :string string?
    :number number?
    :nil nil?
    :element react/isValidElement ;; for actual React elements
    :elements :react/elements)) ;; for nested collection of elements

;; a collection of child elements
;; can be either JS array of Clojure's sequential collection
(s/def :react/elements
  (s/coll-of :react/element :kind #(or (array? %) (sequential? %))))

;; `children` can be either a single element
;; or a collection of elements
(s/def :react/children
  (s/or :element :react/element
        :elements :react/elements))
```

## Compile-time props validation

You can also opt into props validation at compile time to further improve developer experience and make sure that no code is shipped with missing props.

To enable this feature you have to use clojure.spec (this might change in the future to allow pluggable spec libraries) and switch from `:pre` to `:props` assertion in UIx components.

```clojure
(s/def :prop/on-click fn?)
(s/def ::button (s/keys :req-un [:prop/on-click]))

(defui button
  [{:keys [children on-click] :as props}]
  {:props [::button]}
  ($ :button {:on-click on-click}
    children))
```

Given such usage of the `button` component UIx will emit compiler warning telling you that required key `:on-click` is missing from props map passed into the element.

```clojure
($ button {} "press me")
```

Why is this useful? Similar to other UIx linters, props linter enables validation during local development, in tests and on CI. Which reduces probability of shipping buggy UI.

### Limitations

Of course compile time props checking is limited by dynamic nature of the language. There are some requirements to the code that enables this linter:

- There's no validation for values in props map, only for existence of specified keys (this might change in the future, since it's possible to have partial checking for contents of props map)
- Only required keys (`:req` and `:req-un`) are checked
- The check kicks-in only for props written as map literal

To overcome these limitations you can also enable runtime props validation that will run alongside compile-time checking. To enable runtime checking in development, include `uix.preload` namespace in `:preloads` build option.

If you want to keep assertions in production build, include `(uix.validate/set-props-assertion-enabled! true)` in an entry point namespace in your project.
