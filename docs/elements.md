# Elements

UIx uses the `$` macro to create elements (no JSX required). You’ll use it for three things: DOM nodes, UIx components, and JS React components.

> Who is this for: Anyone writing UI with UIx. If you know `React.createElement`, `$` is the same idea with nicer syntax.
>
> Related: [Components](./components.md), [Interop with React](./interop-with-react.md), [DOM attributes](./components.md#dom-attributes), [:ref](./components.md#ref-attribute)

## At a glance

```clojure
;; DOM element
($ :button#save.btn.primary {:disabled false} "Save")

;; UIx component instance
($ my-button {:on-click f} "Save")

;; JS React component instance
($ Button {:on-click f} "Save")
```

The first argument can be:

- A keyword DOM tag (supports `#id` and `.class` shorthand)
- A UIx component var (from `defui` or `uix.core/fn`)
- A JS React component (function or class)

## DOM elements

`$` takes a tag name keyword, an optional map of attributes, and zero or more child elements.

```clojure
($ :button {:title "Submit"} "press me")
```

Element name is declared as a keyword with optional `id` and `class` attributes defined as a part of the name. Together they resemble CSS selector syntax.

```clojure
($ :div) ;; <div></div>
($ :h1.heading {:class "h1"} "👋") ;; <h1 class="heading h1">👋</h1>
($ :button#id.class1.class2) ;; <button id="id" class="class1 class2"></button>
```

## UIx component instances

Component instances are also created via the `$` macro call, where the first argument is the component function itself, the second argument is an optional map of props, and the rest are child elements.

```clojure
(defui button [{:keys [on-click children]}]
  ($ :button.btn {:on-click on-click}
    children))

($ button {:on-click #(js/console.log :click)}
  "press me")
```

## React component instances

React components written in JavaScript can be used directly in UIx with minor differences in how props are passed into a component. See more details on the [“Interop with React”](./interop-with-react.md) page.

```clojure
($ Button {:on-click #(js/console.log :click)}
  "press me")
```

When passing props:

- Use CLJ maps for props. For JS components, UIx converts them for you.
- Spread additional props with `:&` when you need to combine maps or include JS objects. See [props spread](./components.md#props-transferring-via-rest-and-spread-syntax).
