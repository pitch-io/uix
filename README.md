<img src="logo.png" width="125" />

_Idiomatic ClojureScript interface to modern React.js_

> “UIx eliminates the learning curve for React developers new to ClojureScript, allowing them to write familiar patterns seamlessly.” – Misha Karpenko, Pitch

> “UIx allows us to leverage the modern React ecosystem to efficiently build ClojureScript apps.” – Juho Teperi, Metosin

> “UIx offers a seamless React integration, making code more efficient with powerful component composition, hooks, and customizable linting for enforcing best practices.” – Chris Etheridge, Cognician

If you are using UIx, consider supporting the project via [Github Sponsors](https://github.com/sponsors/roman01la) or [Buy Me a Coffee](https://buymeacoffee.com/romanliutikov).

[![CircleCI](https://circleci.com/gh/pitch-io/uix.svg?style=svg)](https://circleci.com/gh/pitch-io/uix)
[![Clojars Project](https://img.shields.io/clojars/v/com.pitch/uix.core.svg)](https://clojars.org/com.pitch/uix.core)
[![Clojars Project](https://img.shields.io/clojars/v/com.pitch/uix.dom.svg)](https://clojars.org/com.pitch/uix.dom)

- API compatibility: React v19.2.0
- Discuss at [#uix on Clojurians Slack](https://clojurians.slack.com/archives/CNMR41NKB)
- Try it out in the [playground](https://studio.learn-modern-clojurescript.com/p/default-uix)
- [A slide deck explaining UIx and migration path from Reagent](https://pitch.com/public/821ed924-6fe6-4ce7-9d75-a63f1ee3c61f)
- [Talk about UIx at London Clojurians meetup](https://www.youtube.com/watch?v=4vgrLHsD0-I)
- ["The State of Frontend" by Alexander Davis](https://www.youtube.com/watch?v=fT28NeZtaAg)
- [ClojureStream podcast: E94 UIx with Roman Liutikov](https://soundcloud.com/clojurestream/e94-uix-with-roman-liutikov)

## Installation

```
npm i -D react@19.2.0 react-dom@19.2.0 process
```

```clj
{:deps {com.pitch/uix.core {:mvn/version "1.4.9"}
        com.pitch/uix.dom {:mvn/version "1.4.9"}}}
```

### How to start a new project with UIx

- Run `npx create-uix-app@latest my-app` to scaffold a new project
- Clone starter template manually from [pitch-io/uix-starter](https://github.com/pitch-io/uix-starter)
- Use fullstack starter project from Metosin [metosin/example-project](https://github.com/metosin/example-project)
- Template project of a web app hosted on Cloudflare with REST API served from SQLite [roman01la/uix-cloudflare-template](https://github.com/roman01la/uix-cloudflare-template)

## Quick start

1. deps.edn

```clojure
{:deps {thheller/shadow-cljs {:mvn/version "3.2.1"}
        com.pitch/uix.core {:mvn/version "1.4.9"}
        com.pitch/uix.dom  {:mvn/version "1.4.9"}}}
```

2. shadow-cljs.edn

```clojure
{:deps true
 :builds
 {:app {:target :browser
        :modules {:main {:entries [app.core]
                         :init-fn app.core/start}}
        :output-dir "out"
        :asset-path "/out"
        :devtools {:preloads [uix.preload]}}}}
```

3. index.html

```html
<!DOCTYPE html>
<html>
  <head>
    <meta charset="utf-8" />
    <meta name="viewport" content="width=device-width,initial-scale=1" />
  </head>
  <body>
    <div id="root"></div>
    <script src="/out/main.js"></script>
  </body>
</html>
```

4. src/app/core.cljs

```clojure
(ns app.core
  (:require [uix.core :as uix :refer [defui $]]
            [uix.dom]))

(defui button [{:keys [on-click children]}]
  ($ :button.btn {:on-click on-click} children))

(defui app []
  (let [[state set-state!] (uix/use-state 0)]
    ($ :<>
      ($ button {:on-click #(set-state! dec)} "-")
      ($ :span state)
      ($ button {:on-click #(set-state! inc)} "+"))))

(defonce root (uix.dom/create-root (js/document.getElementById "root")))

(defn start []
  (uix.dom/render-root ($ app) root))
```

5. Install and run

```sh
npm i -D react@19.2.0 react-dom@19.2.0 react-refresh process
clojure -M -m shadow.cljs.devtools.cli watch app
```

6. Production build

```sh
clojure -M -m shadow.cljs.devtools.cli release app
```

## Docs

- [What is UIx?](docs/what-is-uix.md)
- [Components](docs/components.md)
- [Elements](docs/elements.md)
- [Hooks](docs/hooks.md)
- [State](docs/state.md)
- [Effects](docs/effects.md)
- [Props validation](docs/props-validation.md)
  - [Compile-time props validation](docs/props-validation.md#compile-time-props-validation)
- [Interop with React](docs/interop-with-react.md)
- [Interop with Reagent](docs/interop-with-reagent.md)
- [Code-splitting and React.lazy](docs/code-splitting.md)
- [Migrating from Reagent](docs/migrating-from-reagent.md)
- [Server-side rendering](docs/server-side-rendering.md)
- [Hot reloading](docs/hot-reloading.md)
- [React DevTools](docs/react-devtools.md)
- [Code linting](docs/code-linting.md)
- [Differences from Reagent](docs/differences-from-reagent.md)
- [Testing](docs/testing.md)
  - [End to end testing](docs/testing.md#end-to-end-testing)
  - [Component testing](docs/testing.md#component-testing)
  - [Hooks testing](docs/testing.md#hooks-testing)
- [Utilities](docs/utilities.md)
- [Examples](/core/dev/uix/examples.cljs)
- [Internals](docs/internals.md)
- Other render targets
  - [React Native](/docs/react-native.md)
  - [React Three Fiber](/docs/react-three-fiber.md)
  - [Preact](/docs/preact.md)
- [Getting help from ChatGPT](/docs/chat-gpt.md)

## Recommended libraries

- Routing
  - [reitit](https://github.com/metosin/reitit), [example](https://uix-cljs.dev/recipes/routing)
  - [TanStack Router](https://tanstack.com/router/latest)
- Global state management
  - [re-frame](https://day8.github.io/re-frame/), [example](https://github.com/pitch-io/uix/blob/master/docs/interop-with-reagent.md#syncing-with-ratoms-and-re-frame)
  - [refx](https://github.com/ferdinand-beyer/refx), [example](https://github.com/ferdinand-beyer/refx/tree/main/examples/uix)
  - [si-frame](https://github.com/metosin/si-frame), [examples](https://github.com/metosin/si-frame/tree/uix-examples/examples)
- Data validation
  - clojure.spec (supported by [compile-time props validation](https://github.com/pitch-io/uix/blob/master/docs/props-validation.md#compile-time-props-validation))
  - [malli](https://github.com/metosin/malli)
- Data fetching (_unless using routing library with built-in data fetching_)
  - [TanStack Query v4](https://tanstack.com/query/v4/), (v5 doesn't work with Closure Compiler)
  - [SWR](https://swr.vercel.app/)
- Async code
  - [shadow.cljs.modern/js-await](https://clojureverse.org/t/promise-handling-in-cljs-using-js-await/8998)
  - [promesa](https://github.com/funcool/promesa)
- UI component libraries
  - [shadcn/ui](https://ui.shadcn.com/)
  - [daisyUI](https://daisyui.com/)
  - [Material UI](https://mui.com/)
- Styling
  - [Tailwind CSS](https://tailwindcss.com/)
- Icons
  - [Heroicons](https://heroicons.com/)
  - [Font Awesome](https://fontawesome.com/)
- Forms
  - [React Hook Form](https://react-hook-form.com/)
  - [Formik](https://formik.org/)
- Internationalization
  - [react-i18next](https://react.i18next.com/)

## Who's using UIx?

- [Pitch](https://pitch.com/)
- [Pitch iOS app](https://apps.apple.com/us/app/pitch-collaborate-on-decks/id1551335606?platform=iphone)
- [Cognician](https://info.cognician.com/)
- [Multiply](https://multiply.co/)
- [Totcal](https://totcal.com/)
- [ClojureScript Studio](https://studio.learn-modern-clojurescript.com/)
- [Ogres, virtual tabletop](https://ogres.app/)
- [ShipClojure](https://www.shipclojure.com/)
- [Metosin](https://www.metosin.fi/en)

## Contributing

There are several ways how you can contribute to the project:

- Improve documentation: cover missing pieces, add code examples
- Add, improve docstrings
- Propose and implement improvements and new features
- File and fix bugs
- Increase test coverage

## Support

You can support this project via [Github Sponsors](https://github.com/sponsors/roman01la) or [Buy Me a Coffee](https://buymeacoffee.com/romanliutikov).

## Testing

```
scripts/test
```

_Note: to ensure you're using the right Node.js version, you can use [nvm](https://github.com/nvm-sh/nvm) and run `nvm use`
once in the directory. Otherwise the Node.js version you use is in the `.nvmrc` file. See nvm repo for more documentation._

## Thanks to

- [UIx v1](https://github.com/roman01la/uix) for initial set of ideas and learnings
- [Helix](https://github.com/lilactown/helix) for even more ideas
- [Pitch](https://github.com/pitch-io) for sponsoring initial development and dogfooding the work
