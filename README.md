<img src="logo.png" width="125" />

_Idiomatic ClojureScript interface to modern React.js_

> "Oh god, I just started learning reagent. Don’t tell me I’ll have to switch" /r/clojure

- API compatibility: React v18.2.0
- UIx v1 is in [roman01la/uix](https://github.com/roman01la/uix) repo
- Discuss at #uix on [Clojurians Slack](http://clojurians.net)
- [A slide deck explaining UIx and migration path from Reagent](https://pitch.com/public/821ed924-6fe6-4ce7-9d75-a63f1ee3c61f)
- Try it out live in [ClojureScript Studio](https://www.clojurescript.studio/)

[![CircleCI](https://circleci.com/gh/pitch-io/uix.svg?style=svg)](https://circleci.com/gh/pitch-io/uix)
[![Clojars Project](https://img.shields.io/clojars/v/com.pitch/uix.core.svg)](https://clojars.org/com.pitch/uix.core)
[![Clojars Project](https://img.shields.io/clojars/v/com.pitch/uix.dom.svg)](https://clojars.org/com.pitch/uix.dom)

## Installation

```
yarn add react@18.2.0 react-dom@18.2.0
```

```clj
{:deps {com.pitch/uix.core {:mvn/version "1.1.0"}
        com.pitch/uix.dom {:mvn/version "1.1.0"}}}
```

### How to start a new project with UIx

- Run `npx create-uix-app@latest my-app` to scaffold a new project
- Clone starter template manually from [pitch-io/uix-starter](https://github.com/pitch-io/uix-starter)

## Usage

```clj
(ns my.app
  (:require [uix.core :refer [defui $]]
            [uix.dom]))

(defui button [{:keys [on-click children]}]
  ($ :button.btn {:on-click on-click}
    children))

(defui app []
  (let [[state set-state!] (uix.core/use-state 0)]
    ($ :<>
      ($ button {:on-click #(set-state! dec)} "-")
      ($ :span state)
      ($ button {:on-click #(set-state! inc)} "+"))))

(defonce root
  (uix.dom/create-root (js/document.getElementById "root")))

(uix.dom/render-root ($ app) root)
```

## Docs

- [What is UIx?](docs/what-is-uix.md)
- [Components](docs/components.md)
- [Elements](docs/elements.md)
- [Hooks](docs/hooks.md)
- [State](docs/state.md)
- [Effects](docs/effects.md)
- [Props validation](docs/props-validation.md)
- [Interop with React](docs/interop-with-react.md)
- [Interop with Reagent](docs/interop-with-reagent.md)
- [Code-splitting and React.lazy](docs/code-splitting.md)
- [Migrating from Reagent](docs/migrating-from-reagent.md)
- [Server-side rendering](docs/server-side-rendering.md)
- [Hot reloading](docs/hot-reloading.md)
- [React DevTools](docs/react-devtools.md)
- [Code linting](docs/code-linting.md)
- [Differences from Reagent](docs/differences-from-reagent.md)
- [Utilities](docs/utilities.md)
- [Examples](/core/dev/uix/examples.cljs)
- [Internals](docs/internals.md)
- Other render targets
  - [React Native](/docs/react-native.md)
  - [React Three Fiber](/docs/react-three-fiber.md)

## Testing

```
scripts/test
```

_Note: to ensure you're using the right Node.js version, you can use [nvm](https://github.com/nvm-sh/nvm) and run `nvm use`
once in the directory. Otherwise the Node.js version you use is in the `.nvmrc` file. See nvm repo for more documentation._

## Publishing

1. Update version in core/release.edn, dom/release.edn and in README.md
2. Update docs if needed
3. Update CHANGELOG.md
4. Publish both `core` and `dom` packages to Clojars

```
cd core && CLOJARS_PASSWORD={YOUR_CLOJARS_TOKEN} clj -A:release --skip-tag
cd dom && CLOJARS_PASSWORD={YOUR_CLOJARS_TOKEN} clj -A:release --skip-tag
```

## Who's using UIx?

- [Pitch](https://pitch.com/)
- [Pitch iOS app](https://apps.apple.com/us/app/pitch-collaborate-on-decks/id1551335606?platform=iphone)
- [Cognician](https://info.cognician.com/)
- [Multiply](https://multiply.co/)
- [Totcal](https://totcal.com/)
- [ClojureScript Studio](https://www.clojurescript.studio/)
- [Ogres, virtual tabletop](https://ogres.app/)
- [ShipClojure](https://www.shipclojure.com/)

## Thanks to

- [UIx v1](https://github.com/roman01la/uix) for initial set of ideas and learnings
- [Helix](https://github.com/lilactown/helix) for even more ideas
- [Pitch](https://github.com/pitch-io) for sponsoring and dogfooding the work
