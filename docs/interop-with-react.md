# Interop with React

Use this guide when you want to mix UIx with the broader React ecosystem: import JS components, pass props between worlds, and forward refs when a third‑party library requires it.

> TL;DR
>
> - Use `($ SomeJsComponent {:some-prop 1})` directly — UIx will camelCase keys and convert the top‑level props map for you.
> - When a JS component expects nested objects/arrays, pass them explicitly (use JS objects or `#js []`). Top level `:style` and keyword values are converted for you.
> - To expose a UIx component to JS/React, wrap it with `uix.core/as-react`.
> - For libraries that inject refs via render props, use `uix.core/forward-ref`.

## Using React components in UIx

In the [“Elements”](/docs/elements.md) section it was briefly mentioned that React components written in JavaScript can be used in the `$` macro, but with a difference in how props are passed into such a component.

As an example, let's say we have `Button` component that we want to use in a UIx component.

```js
function Button({ onClick, title, style, className, children }) {
  return (
    <button onClick={onClick} title={title} style={style} className={className}>
      {children}
    </button>
  );
}
```

Here’s how to use it in UIx:

```clojure
($ Button {:on-click #(js/console.log :click)
           :title "this is a button"
           :style {:border "1px solid red"}
           :class :button}
  "press me")
```

When a non-UIx component is passed into `$`, the props map is converted into JS object using the following set of rules:

1. kebab-cased keys are automatically converted into camel-cased keys.
   - Similarly to props in a DOM element, the following keys are renamed into their React counterparts:
     - `:class` -> `"className"`
     - `:for` -> `"htmlFor"`
     - `:charset` -> `"charSet"`
1. When a component expects a kebab-cased key, it can be passed as a string to avoid conversion.
1. props map is converted _shallowly_ into a JavaScript object, meaning that nested collections and maps are not converted. If a JS component expects a prop to hold an array or an object, you have to pass it explicitly. There are two exceptions though:

   - `:style` map is always converted into a JS object because it's a common prop, when passing styles into a third-party component.
   - Keyword values are converted into strings.

## Using UIx components in React

Now the other way around, we want to use a UIx component in a React component.

To achieve this we have to write interop layer using `uix.core/as-react` helper that takes a function which will take React props as a shallow [bean](https://github.com/mfikes/cljs-bean) and call the UIx component.

> Note that `as-react` doesn’t transform camel case keys into kebab case.

```clojure
(defui button [{:keys [on-click children]}]
  ($ :button {:on-click on-click}
    children))

(def Button
  (uix.core/as-react
    (fn [{:keys [onClick children]}]
      ($ button {:on-click onClick :children children}))))
```

Now `Button` can be used as a normal React component.

```js
<Button onClick={console.log}>press me</Button>
```

### Using UIx components in render props of React components

It's not uncommon in React libraries to use [“render props” pattern](https://react.dev/reference/react/cloneElement#passing-data-with-a-render-prop) to let users of the library customize UI of the library component.

Consider this example with [React Simple Maps](https://www.react-simple-maps.io/) library. The library provides `Geographies` component that takes a function as a child element and then it's up to you to decide how to render geo data. Here's how it looks like in JS:

```js
<Geographies
  geography="/features.json"
  fill="#D6D6DA"
  stroke="#FFFFFF"
  strokeWidth={0.45}
>
  {({ geographies }) =>
    geographies.map((geo) => <Geography key={geo.rsmKey} geography={geo} />)
  }
</Geographies>
```

Note that the function takes JS object of props. We can't simply destructure the object using Clojure's `:keys` destructuring, it's meant to be used with Clojure's data structures. Because of this mismatch you have to introduce a gluing layer between JS and CLJS worlds, i.e. read data from JS object and pass it into UIx component.

To make things less cumbersome, use `cljs-bean` library to lazily convert JS data to Clojure data, either shallowly via `bean` function or recursively via `->clj` function.

```clojure
($ Geographies
   {:geography "/assets/features.json"
    :fill "#D6D6DA"
    :stroke "#FFFFFF"}
   (fn [js-props]
     (let [{:keys [geographies]} (cljs-bean.core/->clj js-props)]
       (for [geo geographies]
         ($ Geography
            {:key (:rsm-key geo)
             :geography geo})))))
```

After cleaning things a bit, the final code looks much better:

```clojure
(defui geographies [{:keys [geographies]}]
  (for [geo geographies]
    ($ Geography
      {:key (:rsm-key geo)
       :geography geo})))

($ Geographies
   {:geography "/assets/features.json"
    :fill "#D6D6DA"
    :stroke "#FFFFFF"}
   (fn [js-props]
     ($ geographies (cljs-bean.core/->clj js-props))))
```

### On `ref` forwarding

[_Not relevant for React v19 or later_](https://react.dev/reference/react/forwardRef)

Some third-party React components can inject a `ref` into a child element,
which requires doing [ref forwarding](https://react.dev/reference/react/forwardRef). It's not needed when passing refs between
UIx elements, but is still required for a case when non-UIx component injects a
ref into UIx element.

For this specific case there's `uix.core/forward-ref`, which should be used exclusively in such cases. The helper takes care of merging and converting props.

> Note that `forward-ref` also doesn’t transform camel case keys into kebab case.

```clojure
(defui button [{:keys [ref children on-click onMouseDown]}]
  ;; both `ref` and `onMouseDown` were injected by `Menu`
  ...)

(def button-forwarded
  (uix.core/forward-ref button))

($ Menu
  ($ button-forwarded {:on-click handle-click}
    "press me"))
```

## Error boundaries

Although [error boundaries](https://reactjs.org/docs/error-boundaries.html) aren't fully supported in functional React components, its still possible to use them in UIx as class-based components.

Error boundaries are SSR compatible in UIx.

```clojure
(def error-boundary
  (uix.core/create-error-boundary
   {:derive-error-state (fn [error]
                          {:error error})
    :did-catch          (fn [error info]
                          (logging/error "Component did catch" error)
                          info)}
   (fn [[state set-state!] {:keys [children]}]
     (if-some [error (:error state)]
       ($ :<>
         ($ :p.warning "There was an error rendering!")
         ($ :pre (pr-str error)))
       children))))

(defui users [{:keys [users]}]
  ($ :<>
    ($ error-boundary
      ($ user-list {:users users})
      ($ :button "Load more ..."))
    ($ :a "Back home")))
```

`derive-error-state` is used to return a value that can be read by the render function passed to `uix.core/create-error-boundary`. You can use this function to parse an exception to a more friendly format, for example. `did-catch` will be called with the exception object and any additional info. In JS the `info` will be the component stack trace. In the JVM `info` will be the name of the error boundary.
