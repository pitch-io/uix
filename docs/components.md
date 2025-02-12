# Components

UIx components are defined using the `defui` macro, which returns React elements created using the `$` macro. The signature of `$` macro is similar to `React.createElement`, with an additional shorthand syntax in the tag name to declare CSS id and class names (similar to Hiccup):

```js
// React without JSX
React.createElement("div", { onClick: f }, child1, child2);
```

```clojure
;; UIx
($ :div#id.class {:on-click f} child1 child2)
```

```clojure
(ns my.app
  (:require [uix.core :refer [defui $]]))

(defui button [{:keys [on-click children]}]
  ($ :button {:on-click on-click}
    children))

(defui text-input [{:keys [value type on-change]}]
  ($ :input {:value value
             :type type
             :on-change #(on-change (.. % -target -value))}))

(defui sign-in-form [{:keys [email password]}]
  ($ :form
    ($ text-input {:value email :type :email})
    ($ text-input {:value password :type password})
    ($ button {} "Sign in")))
```

## Inline components

Sometimes you might want to create an inline component using anonymous function. Let's take a look at the following example:

```clojure
(defui ui-list [{{:keys [key-fn data item]}}]
  ($ :div
    (for [x data]
      ($ item {:data x :key (key-fn x)}))))

(defui list-item [{:keys [data]}]
  ($ :div (:id data)))

($ ul-list
  {:key-fn :id
   :data [{:id 1} {:id 2} {:id 3}]
   :item list-item})
```

In the example above `ul-list` takes `item` props which has to be a `defui` component, which means you have to declare `list-item` elsewhere.

With `uix.core/fn` it becomes less annoying:

```clojure
(defui ui-list [{{:keys [key-fn data item]}}]
  ($ :div
    (for [x data]
      ($ item {:data x :key (key-fn x)}))))

($ ul-list
  {:key-fn :id
   :data [{:id 1} {:id 2} {:id 3}]
   :item (uix/fn [{:keys [data]}]
           ($ :div (:id data)))})
```

## Component props

`defui` components are similar to React’s JSX components. They take props and children and provide them within a component as a single map of props.

Let's take a look at the following example:

```js
function Button({ onClick, children }) {
  return <button onClick={onClick}>{children}</button>;
}

<Button onClick={console.log}>Press me</Button>;
```

The `Button` component takes JSX attributes and the `"Press me"` string as a child element. The signature of the component declares a single parameter which is assigned to an object of passed in attributes + child elements stored under the `children` key.

Similarly in UIx, components take a map of props and an arbitrary number of child element. The signature of `defui` declares a single parameter which is assigned a hash map of passed in properties + child elements stored under the `:children` key.

```clojure
(defui button [{:keys [on-click children]}]
  ($ :button {:on-click on-click}
    children))

($ button {:on-click js/console.log} "Press me")
```

## Performance optimisation

To avoid unnecessary updates, UIx components can be memoised using `uix.core/memo` function or `^:memo` tag.

```clojure
(defui ^:memo child [props] ...)

(defui parent []
  ($ child {:x 1}))
```

As long as `props` doesn't change when `parent` is updated, the `child` component won't rerun. Read [React docs on memoisation](https://react.dev/reference/react/memo) to learn when to use this optimisation.

## DOM attributes

DOM attributes are written as keywords in kebab-case. Values that are normally strings without whitespace can be written as keywords as well, which may improve autocompletion in your IDE.

```clojure
($ :button {:title "play button"
            :data-test-id :play-button})
```

## children

Similar to React, child components are passed as `children` in the props map. `children` is a JS Array of React elements.

```clojure
(defui popover [{:keys [children]}]
  ($ :div.popover children))
```

## :ref attribute

[Refs](https://reactjs.org/docs/refs-and-the-dom.html) provide a way to refer to DOM nodes. In UIx `ref` is passed as a normal attribute onto DOM elements, similar to React. `use-ref` returns a ref with an Atom-like API: the ref can be dereferenced using `@` and updated with either `clojure.core/reset!` or `clojure.core/swap!`.

```clojure
(defui form []
  (let [ref (uix.core/use-ref)]
    ($ :form
      ($ :input {:ref ref})
      ($ :button {:on-click #(.focus @ref)}
        "press to focus on input"))))
```

> UIx components don't take refs because they are built on top of React's function-based components which don't have instances.

When you need to pass a ref into child component, pass it as a normal prop.

```clojure
(defui text-input [{:keys [ref]}]
  ($ :input {:ref ref}))

(defui form []
  (let [ref (uix.core/use-ref)]
    ($ :form
      ($ text-input {:ref ref})
      ($ :button {:on-click #(.focus @ref)}
        "press to focus on input"))))
```

## Class-based components

Sometimes you want to create a class-based React component, for example an error boundary. For that there's the `uix.core/create-class` function.

```clojure
(def error-boundary
  (uix.core/create-class
    {:displayName "error-boundary"
     :getInitialState (fn [] #js {:error nil})
     :getDerivedStateFromError (fn [error] #js {:error error})
     :componentDidCatch (fn [error error-info]
                          (this-as this
                            (let [props (.. this -props -argv)]
                              (when-let [on-error (:on-error props)]
                                (on-error error)))))
     :render (fn []
               (this-as this
                 (if (.. this -state -error)
                   ($ :div "error")
                   (.. this -props -children))))}))

($ error-boundary {:on-error js/console.error}
  ($ some-ui-that-can-error))
```

## Props transferring via rest and spread syntax

One thing that is sometimes useful in React/JavaScript, but doesn't exist in Clojure is object spread and rest syntax for Clojure maps (see [object spread in JS](https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Operators/Spread_syntax)). It's often used for props transferring to underlying components and merging user-defined props with props provided by third-party React components.

```javascript
import * as rhf from "react-hook-form";

function Form({ inputStyle, ...props }) {
  const { register, handleSubmit } = rhf.useForm();

  return (
    <UIForm onSubmit={handleSubmit} {...props}>
      <input style={inputStyle} {...register("firstName")} />
    </UIForm>
  );
}
```

In Clojure you'd have to `dissoc` keys manually, which is more verbose and annoying since this pattern is widespread.
And then you have to `merge` props manually, which is not gonna work for third-party React components that supply props as JS object, because in UIx props is Clojure map.

```clojure
(ns app.core
  (:require [uix.core :as uix :refer [defui $]]
            ["react-hook-form" :as rhf]))

(defui form [{:keys [input-style] :as props}]
  (let [f (rhf/useForm)]
    ($ ui-form (merge {:on-submit (.-handleSubmit f)}
                      ;; removing unrelated props
                      (dissoc props :input-style))
      ($ :input (merge {:style input-style}
                       ;; can't merge JS object returned from .register call
                       ;; with Clojure map above
                       (.register f "first-name"))))))
```

With associative rest and spread syntax, things become cleaner:

```clojure
(ns app.core
  (:require [uix.core :as uix :refer [defui $]]
            ["react-hook-form" :as rhf]))

(defui form [{:keys [input-style] :& props}] ;; props rest syntax
  (let [f (rhf/useForm)]
    ($ ui-form {:on-submit (.-handleSubmit f)
                ;; props spread
                :& props}
      ($ :input (merge {:style input-style
                        ;; props spread with JS object
                        :& (.register f "first-name")})))))
```

### Props rest syntax

When destructing props in `uix.core/defui` or `uix.core/fn`, all keys that are not mentioned in destructing form will be stored in a map assigned to `:&` keyword. The syntax is composable with all other means of destructuring maps in Clojure, except that `:&` exists only at top level, it won't work for nested maps.

### Props spread syntax

To spread or splice a map into props, use `:&` key. This works only at top level of the map literal: `{:width 100 :& props1}`. When spreading multiple props, use vector syntax `{:width 100 :& [props1 props2 props3]}`.

> Note that props spreading works the same way how `merge` works in Clojure or `Object.assign` in JS, it's not a "deep merge".
