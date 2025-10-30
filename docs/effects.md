# Effects

Use effects to perform work “outside” your component: network calls, DOM APIs, subscriptions, timers. UIx wraps React’s hooks so you can write idiomatic Clojure and avoid JS/CLJ return‑value gotchas.

> Rule of thumb: if it touches the outside world, it’s an effect. Keep effects minimal and declare their dependencies explicitly.
>
> Related: [Hooks](./hooks.md#return-value-in-effect-hooks)

UIx provides a way to perform side effects in components via [React’s effect hook](https://reactjs.org/docs/hooks-effect.html). Effects are useful for making HTTP requests, interacting with the DOM, or performing any kind of impure operation that mutates some global state and might need to be cleaned up at some point in the future.

The `uix.core/use-effect` function wraps React's `useEffect` hook and takes care of handling certain inconsistencies between JS and Clojure worlds so that you don't have to. More information about effect hook is available in [React documentation on effect hook](https://reactjs.org/docs/hooks-effect.html).

In the example below, the effect's callback will execute and update the document’s title after every update of the component.

```clojure
(defui example []
  (let [[clicks set-clicks!] (uix.core/use-state 0)]
    (uix.core/use-effect
      (fn []
        (set! (.-title js/document)
              (str "Number of clicks: " clicks))))
    ($ :button {:on-click #(set-clicks! inc)}
      "increment")))
```

An optional dependencies vector allows conditional execution of an effect. In the example above, dependencies are not passed into the effect causing it to execute on every update of the component. [Read more about conditional effect execution in React docs](https://reactjs.org/docs/hooks-reference.html#conditionally-firing-an-effect).

In the example below the effect will be executed every time when a number of `clicks` becomes a multiple of 5.

```clojure
(defui example []
  (let [[clicks set-clicks!] (uix.core/use-state 0)]
    (uix.core/use-effect
      (fn []
        (set! (.-title js/document)
              (str "Number of clicks: " clicks)))
      [(zero? (mod clicks 5))])
    ($ :button {:on-click #(set-clicks! inc)}
      "increment")))
```
