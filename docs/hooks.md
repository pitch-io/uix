# Hooks

UIx wraps existing React hooks to smooth over some rough spots and provide a more idiomatic interface for Clojure. `uix.core` exposes only the default React hooks, named equivalently to the JS versions except in kebab-case, e.g. `useEffect` becomes `use-effect`.

There are multiple differences from pure React though.

## Dependency array

Some hooks accept an array of dependencies as the second argument. While in pure React this has to be an array literal, `#js []`, UIx uses a vector literal `[]` to make it more idiomatic for Clojure.

```clojure
(uix/use-effect
  (fn [] (prn x))
  [x])
```

## How are dependencies compared?

When the same dependency has a different value between component updates, the hook will rerun. But unlike in React, where dependencies are compared with `===`, which is referential equality check, in UIx hooks deps are compared with `clojure.core/=`, which means that it's safe to use immutable maps and vectors as deps values.

## Return value in effect hooks

The _setup_ function, which is passed into one of the effect hooks, requires the return value to be either a function (that will be called on _cleanup_) or `js/undefined`. Otherwise React will throw an error saying that it got something else.

```clojure
(react/useEffect
  (fn []
    :keyword) ;; returning `:keyword` here will throw
  #js [])
```

In ClojureScript, when an expression returns _nothing_, it actually returns `nil`, which is compiled into `null` in JS. Since `null` is neither a function nor `undefined` React will throw in this case as well.

```clojure
(react/useEffect
  (fn []
    (when false (prn :x))) ;; returns `nil` and thus React throws
  #js [])
```

Thus when using React hooks directly you'd have to explicitly return `js/undefined` in most cases.

```clojure
(react/useEffect
  (fn []
    (when false (prn :x))
    js/undefined)
  #js [])
```

This complication is also handled by UIx and if the return value is not a function it will automatically return `js/undefined`. However, keep in mind that since in Clojure the last expression is always returned implicitly, you still have to make sure the hook doesn't return a function accidentally, because it's going to be executed in its _cleanup_ phase.

In other words, React will never complain about the return value in UIx's effect hooks, unlike in pure React. And since Clojure has implicit return, make sure you don't return a function by accident.

```clojure
(uix/use-effect
  (fn []
    (when false (prn :x))) ;; `nil` is not a function, nothing from here
  [])

(uix/use-effect
  (fn []
    (map inc [1 2 3])) ;; return value is a collection, nothing wrong here either
  [])

(uix/use-effect
  (fn []
    (map inc)) ;; return value is a function (transducer),
  [])          ;; it's gonna be executed as a cleanup function,
             ;; is that intended?
```

## Differences from `use-callback` and `use-memo` hooks

In pure React both the `use-callback` and `use-memo` hooks accept an optional dependency array. However, since the purpose of both hooks is memoization it generally doesn't make sense to call them without any dependencies; not providing the dependency array effectively means there's no memoization applied. In JavaScript this is enforced by an ESLint rule. In UIx on we simply removed the single arity method for those hooks, and so you must always pass a dependency vector.

## `use-ref` hook

The `use-ref` hook returns an object that has a stable identity throughout the lifecycle of a component and allows storing arbitrary values inside of it. A ref is basically a mutable container bound to an instance of a component. This aligns pretty well with Clojure's `ref` types, namely `Atom` which is commonly used as a mutable container for immutable values.

While in pure React `useRef` returns an object with a `current` property, in UIx `use-ref` returns the same object but with an API identical to `Atom`. The ref can be dereferenced using `@` to read the current value, and updated via `reset!` or `swap!` to set a new value.

Note that unlike `r/atom` in Reagent, a ref in UIx and React is not a state primitive, it's a mutable value and doesn't trigger an update.

```clojure
(defui component []
  (let [ref (uix/use-ref)]
    (uix/use-layout-effect
      (fn []
        (js/console.log (.-clientWidth @ref))))
    ($ :div {:ref ref})))
```

> UIx has a built-in linter that will warn you when using JS interop syntax to read and update UIx refs, e.g. `(.-current ref)`. There's no reason to use interop syntax when a more idiomatic Atom-like interface is available.

## Creating custom hooks

While custom hooks can be defined as normal functions via `defn`, it's recommended to use `uix.core/defhook` macro when creating custom hooks.

```clojure
(defhook use-event-listener [target type handler]
  (uix/use-effect
    (fn []
      (.addEventListener target type handler)
      #(.removeEventListener target type handler))
    [target type handler]))
```

Here are some benefits of using `defhook`:

1. Enforced naming convention: hooks names must start with `use-`. The macro performs compile time check.
2. Enables hooks linting: the macro runs [built-in linter](/docs/code-linting.md) on the body of a custom hook, making sure that hooks are used correctly.
3. (Future improvement) Optional linter rule to make sure that all hooks in application code are created via `defhook`.

## ClojureScript specific hooks

### `use-atom`

For a quick global shared state

```clojure
(defonce state (atom 0))

(defui counter []
  (let [value (uix/use-atom state)]
    ($ :div value)))

(defui button []
  ($ :button {:on-click #(swap! state inc)}
    "+"))
```
