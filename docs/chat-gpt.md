# Getting help from ChatGPT

You can use ChatGPT to translate React/JavaScript code into UIx/ClojureScript using the following prompt:

```
You are language to language translator. Your job is to translate code from JS, React, JSX to Clojure. In Clojure we use UIx library which adds DSL on top of React to create components and elements. The library provides uix.core namespace which includes top level api, as well as react hooks.
Components are created using defui macro, here’s the syntax: (defui component-name [props-map] body)
Elements are created using $ macro: ($ :dom-element optional-props-map …children)
Component names and props are written in kebab-case. Dom element keywords support hyper script syntax to define classes and id: :div#id.class
JS names should be translated into idiomatic Clojure names, for example is-visible should become visible?
Translate the following code to Clojure
```

Alternatively, if you are using paid account with large enough context window, grab entire docs packed into a single markdown file here: [repomix-output.llm.md](./repomix-output.llm.md) and feed it into an LLM together with your question.

The prompt works quite well, here's an example:

```js
// input
function Item({ name, isPacked }) {
  return <li className="item">{name}</li>;
}

export default function PackingList() {
  return (
    <section>
      <h1>Sally Ride's Packing List</h1>
      <ul>
        <Item isPacked={true} name="Space suit" />
        <Item isPacked={true} name="Helmet with a golden leaf" />
        <Item isPacked={false} name="Photo of Tam" />
      </ul>
    </section>
  );
}
```

```clojure
;; output
(ns packing-list.core
  (:require [uix.core :refer [$ defui]]))

(defui item [{:keys [name packed?]}]
  ($ :li.item name))

(defui packing-list []
  ($ :section
     ($ :h1 "Sally Ride's Packing List")
     ($ :ul
        ($ item {:packed? true :name "Space suit"})
        ($ item {:packed? true :name "Helmet with a golden leaf"})
        ($ item {:packed? false :name "Photo of Tam"}))))
```
