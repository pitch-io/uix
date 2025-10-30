# What is UIx?

UIx is an idiomatic ClojureScript interface to modern React. It keeps you close to the React ecosystem while giving you a great Clojure(Script) developer experience: data-first APIs, simple macros for elements and components, and built‑in guard rails.

> Quick take: If you know React, you can be productive with UIx in an hour. If you know Clojure(Script), you get React’s rendering model with a friendlier API.

> Because UIx is a wrapper library for React, it's recommended to be comfortable with React’s mental model. Start with the official [React docs](https://react.dev/). If you prefer a course, try [Epic React](https://epicreact.dev/) or [Joy of React](https://www.joyofreact.com/).

---

• Who is this for? React developers exploring ClojureScript, and Clojure devs who want React without JSX.

• Prerequisites: Basic Clojure(Script), Node.js, and familiarity with React hooks and function components.

• Requirements: React 19.2+, `shadow-cljs` or your preferred CLJS toolchain. See installation and Quick start in the project’s `README.md`.

• Try it now: Use the in‑browser [playground](https://studio.learn-modern-clojurescript.com/p/default-uix) — no setup required.

## What is “modern React”?

Modern React (18+) emphasizes function components, hooks, concurrent rendering, and a clarified render/commit model. If you’re coming from class components or older wrappers, read React’s [Render and commit](https://react.dev/learn/render-and-commit) for the mental model UIx builds on.

## How is UIx different from existing CLJS wrappers?

Older wrappers were designed when React relied on class components and lacked proper scheduling/prioritization. They often recreated scheduling layers, exposed class APIs, and optimized for hiccup syntax.

UIx assumes modern React:

- Function components are the default.
- Hooks are first‑class for state, effects, and composition.
- React handles scheduling and rendering efficiency — you don’t need an extra runtime.

Existing wrappers can run on newer React versions, but fully embracing the new model often requires breaking changes. UIx is designed around the current React model, so you can use new features without legacy constraints.

## Social aspect

UIx v2 is a rewrite of the [original library](https://github.com/roman01la/uix) developed by [Roman Liutikov](https://github.com/roman01la) for [Pitch](https://pitch.com/). A key goal was to be “React‑native” for JavaScript engineers moving to ClojureScript: familiar concepts, minimal translation layer, and APIs that feel at home in both communities.

## What does UIx offer?

An idiomatic, low‑friction interface to React:

- `$` macro to create elements without JSX, including shorthand for `id`/`class`.
- `defui` to define components (function components) with simple, explicit props.
- Hooks that match React’s behavior, but with Clojure‑friendly ergonomics (e.g., `[]` deps, structural equality for deps, atom‑like `use-ref`).
- Built‑in linting rules to catch common mistakes early. See [Code linting](./code-linting.md).
- Interop with the React ecosystem: use any JS React component directly. See [Interop with React](./interop-with-react.md).

If you prefer a side‑by‑side view, see:

- [Components](./components.md)
- [Elements](./elements.md)
- [Hooks](./hooks.md)
- [State](./state.md)
- [Effects](./effects.md)
- [Server‑side rendering](./server-side-rendering.md)

---

## Choose your path

- Coming from React/JS? Skim [Elements](./elements.md) and [Components](./components.md), then check [Interop with React](./interop-with-react.md). You’ll feel at home quickly.
- Coming from Reagent? Start with [Differences from Reagent](./differences-from-reagent.md) and then follow [Migrating from Reagent](./migrating-from-reagent.md). There’s also [Interop with Reagent](./interop-with-reagent.md) for mixed codebases.
- Need SSR? Go straight to [Server‑side rendering](./server-side-rendering.md).
- Unsure where to start? The project `README.md` contains a minimal Quick start you can paste into a fresh app.

## Glossary

- Element: What `$` returns — a description of UI to render.
- Component: A function created via `defui` that returns an element tree.
- Props: A Clojure map passed to components. Children are available under `:children`.
- Ref: A mutable holder with an atom‑like API; doesn’t trigger re‑renders. See [Hooks](./hooks.md#use-ref-hook).
- Root: The mounting point created via `uix.dom/create-root` and rendered via `uix.dom/render-root`.
- Effect: Code that touches the outside world; declared via `use-effect`/`use-layout-effect`.
- State: Local component state via `use-state`.

## Next steps

1. Get a feel for the syntax in [Components](./components.md) and [Elements](./elements.md).
2. Wire up a minimal app using the Quick start in `README.md`.
3. Bring in a third‑party React component via [Interop with React](./interop-with-react.md).
4. Add SSR later with [Server‑side rendering](./server-side-rendering.md).

If you hit a snag, check the [Testing](./testing.md) and [Code linting](./code-linting.md) pages, or ask in [#uix on Clojurians Slack](https://clojurians.slack.com/archives/CNMR41NKB).
