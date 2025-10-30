# Hooks linter

UIx has a built-in linter that will help you to use React Hooks correctly.
The linter is built into `defui`, `defhook` and the default `uix.core/*` hooks,
and
implements a set of rules from React's official [ESLint plugin](https://reactjs.org/docs/hooks-rules.html).

While in the original ESLint plugin there are rules that can be considered as suggestions and thus reported as warnings, most of the rules implemented in UIx should always be followed as breaking them will lead to bugs in your UI. For this reason in UIx a broken rule will fail to build so that it's impossible to build a project with problematic behaviour in UI components.

## What's the rule of thumb to use Hooks correctly?

Call hooks at the top level in the component body.

```clojure
;; bad
(defui component [{:keys [active?]}]
  (when active?
    (use-effect ...))
  ...))

;; good
(defui component [{:keys [active?]}]
  (use-effect
    (fn []
      (when active?
        ...)))
  ...))
```

List all necessary dependencies in deps vector for hooks that require dependencies.

```clojure
;; bad
(defui component [{:keys [active? id]}]
  (use-effect
    (fn []
      (when active?
        (rf/dispatch :user/set-id {:id id})))
    [active?])
  ...))

;; good
(defui component [{:keys [active? id]}]
  (use-effect
    (fn []
      (when active?
        (rf/dispatch :user/set-id {:id id})))
    [active? id])
  ...))
```

## What type of errors can UIx catch?

### Hooks called inside of conditions or iterating functions

The rule here is to call the function at the top level of the component body.

```clojure
(defui component [{:keys [active?]}]
  (when active?
    (use-effect ...)) ;; error
  ...))

(defui component [{:keys [items]}]
  (for [item items]
    ($ list-item
      {:item item
                 ;; error
       :on-click (use-callback #(rf/dispatch %) [item])}))))
```

### A hook doesn't meet its dependencies requirements

> This rule is currently experimental, to opt-out add `^:lint/disable` meta in front of the deps vector

This rule will check for missing and unnecessary dependencies and suggest a correct deps vector.

```clojure
(defui component [{:keys [active? id]}]
  (use-effect
    (fn []
      (when active?
        (rf/dispatch :user/set-id {:id id})))
    [active?]) ;; error, update deps vector to [active? id]
  ...))
```

### Unsafe set-state in effect hook without dependencies

This type of code leads to an infinite loop of updates in components.

```clojure
(defui component [{:keys [active? id]}]
  (let [[value set-value] (use-state 0)]
    (use-effect
      (fn []
        (set-value (inc value)))))) ;; error

(defui component [{:keys [active? id]}]
  (let [[value set-value] (use-state 0)]
    (use-effect
      (fn []
        (set-value (inc value)))
      [value]))) ;; fix: only run hook when value changes
```

### A hook is being passed something as deps that is not a vector literal

Deps should be always a vector literal of constant size. React doesn't allow deps to be of dynamic length because it causes issues in UI components.

```clojure
;; incorrect
(defui component [{:keys [labels]}]
  (let [dimensions (use-memo #(measure-labels labels) labels)]
    ...))

;; correct
(defui component [{:keys [labels]}]
  (let [dimensions (use-memo #(measure-labels labels) [labels])]
    ...))
```

### A hook is being passed deps as a JS array instead of a vector

This is UIx specific. Since UIx is a Clojure wrapper it expects a vector of deps instead of JS array to be more idiomatic and allow for easier interop with Clojure code.

```clojure
(defui component [{:keys [html]}]
  (let [html (use-memo #(sanitize-html html) #js [html])] ;; incorrect
    ...))

(defui component [{:keys [html]}]
  (let [html (use-memo #(sanitize-html html) [html])] ;; correct
    ...))
```

### A function reference is passed into a hook instead of an inline function

This won't cause actual bugs, but it prevents further type checking to determine if the hook satisfies dependency requirements, thus it's encouraged to use inline function instead. Note that the linter might improve in the future and this rule will be deprecated.

```clojure
(defui component [{:keys [active? id]}]
  (let [do-something (fn []
                       (when active?
                         (rf/dispatch :user/set-id {:id id})))]
    ;; deps are correct, but it still gonna error
    (use-effect do-something [active? id])))

(defui component [{:keys [active? id]}]
  (let [do-something (fn [active? id]
                       (when active?
                         (rf/dispatch :user/set-id {:id id})))]
  ;; now linter is able to check whether the effect meets deps requirements correctly
  (use-effect #(do-something active? id) [active? id])))
```

# Missing `:key` attribute

UIx will check for missing `:key` attribute when a UIx element is rendered as a list item (via `for`, `map`, etc.).

```clojure
(for [x items]
  ($ item {:title "hello" :x x})) ;; error: missing key

(for [x items]
  ($ item {:title "hello" :x x :key x})) ;; no error
```

# DOM attributes

When possible, UIx will verify DOM attributes at compile time.

```clojure
($ :div {:autoplay true})
;; WARNING: Invalid DOM property :autoplay. Did you mean :auto-play?
```

## Config

UIx's linter can be provided with an external configuration that should live in the file `.uix/config.edn` at the root of your project.

```clojure
{:linters {:react-key {:enabled? false}}}
;; the rule is enabled by default
```

# Reagent interop linter

When migrating from Reagent + re-frame to UIx you might want to keep using re-frame or at least stick with it for some time because migrating data management is not as simple as rewriting UI components.

To make sure this transition path is smooth UIx will check for re-frame `subscribe` calls in UIx components and trigger a compilation error that will suggest the use of a `use-subscribe` hook instead. It will also point to the [“Syncing with ratoms and re-frame”](https://github.com/pitch-io/uix/blob/master/docs/interop-with-reagent.md#syncing-with-ratoms-and-re-frame) section in UIx docs.

Given this piece of code

```clojure
(defui component []
  (rf/subscribe [:user/id]))
```

You'll get the following compilation error

```
re-frame subscription (rf/subscribe [:user/id])) is non-reactive in UIx components when called via re-frame.core/subscribe, use `use-subscribe` hook instead.

Read https://github.com/pitch-io/uix/blob/master/docs/interop-with-reagent.md#syncing-with-ratoms-and-re-frame for more context
```

## Config

It is possible to add re-frame specific rules to the linter config file (located in the file `.uix/config.edn` at the root of your project).

```clojure
{:linters
 {:re-frame
  {:resolve-as {my.app/subscribe re-frame.core/subscribe}}}}
  ;; re-frame.core/subscribe is checked by default
```

# Custom linters

UIx exposes a public API to register custom linters, so that you can have your own linting rules specific to your project. There are three types of linters in UIx:

- Component linters `uix.linter/lint-component` — those execute on entire `defui` form
- Element linters `uix.linter/lint-element` — execute per `$` form
- Hook linters `uix.linter/lint-hook-with-deps` — execute for every Hook form that takes deps (`use-effect`, `use-callback`, etc.)

See [core/dev/uix/linters.clj](/core/dev/uix/linters.clj) for a set of complete examples.

# clj-kondo

UIx has importable configuration for clj-kondo. You can import the configuration with:

```bash
clj-kondo --lint "$(clojure -Spath)" --copy-configs --skip-lint
```

There is only one custom hook, which validates the arguments passed to `uix.core/$`.
