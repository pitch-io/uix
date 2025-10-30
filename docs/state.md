# State

Local component state in UIx is powered by React’s `useState`, with a Clojure‑friendly API.

> Mental model: Treat state as immutable values updated through a single setter function. Prefer deriving UI from props and state.
>
> Related: [Hooks](./hooks.md), [Effects](./effects.md)

UIx is using [React's state hook](https://reactjs.org/docs/hooks-state.html#hooks-and-function-components) for local state.

```clojure
(defui form []
  (let [[value set-value!] (uix.core/use-state "")]
    ($ :input {:value value
               :on-change #(set-value! (.. % -target -value))})))
```

In some React wrappers state is represented using the `Atom` datatype that has `reset!` and `swap!` operations. We take a similar naming approach and suffix state updating function with a bang `!` denoting that the operation is mutating a value.

> Similar to `clojure.core/swap!`, `set-value!` from the above example also takes a function that will receive the current value and arbitrary number of additional arguments, and return the next value of component's state.

When the initial value has to be computed before it’s used, it is recommended to pass it in a callback to `use-state`. The callback will be executed only once, in component’s initialization stage.

```clojure
(defn calculate-initial-value []
  ;; expensive computation here
  )

(uix.core/use-state calculate-initial-value)
```

## Reusable state

Because state is not directly coupled with components it is possible to build reusable behaviors on top of it. In the example below we create a `use-validation` function to encapsulate logic that keeps track of a value and applies updates when the value satisfies provided validation function.

```clojure
(defn use-validation [initial-value valid?]
  (let [[value set-value!] (uix.core/use-state initial-value)
        on-change #(let [v (.. % -target -value)]
                    (when (valid? v)
                      (set-value! v)))]
    [value on-change]))

(defui form []
  (let [[value on-change] (use-validation "" #(not (empty? %)))]
    ($ :input {:value value
               :on-change on-change})))
```
