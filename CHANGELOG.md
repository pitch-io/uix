# CHANGELOG

## 1.4.9

### Fixes

- `defcontext`-defined context can take key prop
- fix class names merging in spread props

## 1.4.8

### New

- Added cross-platform clj/cljs React context via `uix.core/defhook` macro
- Added clj version of `use-atom`

## 1.4.7

### Fixes

- Use `globalThis` instead of `window`

## 1.4.6

### New

- Added `uix.dom/log-box` component to display caught errors in an error boundary in dev #261
- Added `core/dev/uix/recipes` with various examples #264
- Moved `use-reaction` into `uix.reagent` ns #267
- Docs: using Preact with UIx, docs/preact.md #268

### Improvements

- Minor performance improvements

### Fixes

- Merging various class name attributes #258
- Fixed `use-effect-event` linting #271
- Backport `use-sync-external-store` shim and `use-sync-external-store-with-selector` #271

## 1.4.5

### New

- React 19.2.0 compatibility:
  - Added `uix.core/activity` component, see [Activity](https://react.dev/reference/react/Activity) docs
  - Updated `uix.core/use-effect-event` to use native [`useEffectEvent`](https://react.dev/blog/2025/10/01/react-19-2#use-effect-event) hook (when available)
- Added `use-atom` hook, see [docs](https://github.com/pitch-io/uix/blob/master/docs/hooks.md#use-atom)

## 1.4.4

### New

- Enable props validation only in dev, via preload
- Add more clj-kondo linting rules for `defui` and hooks
- Remove `set-optimizations-enabled!`, doesn't work as intended

### Fixes

- JVM SSR: Correctly serialize self-closing HTML tags #231

## 1.4.3

### Fixes

- Added `uix.core/set-optimizations-enabled!` to disable elements inlining for projects that rely on monkey-patching `React.createElement` API
- Fixed missing ref attribute in inlined elements for React 18

## 1.4.2

### Fixes

- Fixed elements hoisting crashing production build

## 1.4.1

### Fixes

- Fixed linter check for UIx refs accessed via JS interop

## 1.4.0

### New

- Props rest syntax #169
- Performance: inline and hoist compile-time constant UIx elements #213
- Reagent interop: explicit control over injection of Reagent's input wrapper #205

### Fixes

- Performance: Unused components are removed in production/advanced builds #202
- Fixed CSS variables in inline styles #218

## 1.3.1

### New

- Extend `use-reaction` to Reagent's `Track` and `Cursor` types #199

## 1.3.0

### New

- React 19 API compatibility #144
- add `use-effect-event` hook #186
- renderable context value, forward compat v19 #190
- props spread syntax #193

### Fixes

- Fix clj-kondo custom $ hook #185

## 1.2.0

### New

- Preload namespace with react-refresh `uix.preload` #178
- Forms rewriter, rewrites simple `for` into `map` #181

### Improvements

- Readbale component stack traces #171

## 1.2.0-rc3

### New

- add `uix.re-frame` with `use-subscribe` hook

## 1.2.0-rc2

### New

- `set-state` in `use-state` hook behaves like `cljs.core/swap!` when passing updater function

## 1.2.0-rc1

### New

- `^:memo` tag for `defui` to create memoized components in-place
- `uix.core/clone-element` helper for cloning UIx elements
- React Hooks with deps are using now Clojure's equality check to detect whether deps were updated

## 1.1.1

### Improvements

- add defhook to lint as defn in clj-kondo config 53a7cc

## Fixes

- make args optional in clj components 806691

## 1.1.0

### Improvements

- The order of DOM attributes in JVM SSR should match JS SSR bf971d
- JVM SSR: dynamic id should overwrite static id 929c26
- Fixed props passing in `as-react` #134
- JVM SSR: `class`, `class-name` and `className` should be rendered as `class`; `for`, `html-for` and `htmlFor` should render as `for` #137

### New

- Added `use-debug` hook to `uix/core.cljs` 27b4b9
- Added a linter for DOM attributes #126
- Utilities for html and hiccup to uix conversion #127
- Support the optional key argument for `create-portal` #138
- Fix Reagent-style hot-reloading not working with `uix.corel/lazy` and code-splitting in shadow-cljs #139

## 1.0.1

### Improvements

- Fixed `$` elements not being emitted when `$` is wrapped in a macro #121

## 1.0.0

### Improvements

- Missing key linter: check in a threading macro #100
- Better error message when an incorrect value is supplied to `$` in place of a component #114
- Fix memory leak in `use-subscribe` hook #109
- Fix fast-refresh breaking 7c39a4e, 2a4504b
- Fixed minor differences in SSR output between JS and JVM renderers

### New

- cljs ns `uix.dom.server` wrapping `react-dom/server`
- `uix.core/client?` and `uix.core/server?` helpers
- `suspense`, `strict-mode` and `profiler` components
- `uix.core/defhook` macro for custom hooks

## 0.10.0

### Improvements

- Allow props as js obj into non-uix components b0fe50
- Should preserve identity of Clojure primitives when updating state with equal-by-value value a7abe1
- Better forward-ref interop 2753d9

## 0.9.1

### Improvements

- Fixed incorrect return value of `use-state` hook on JVM [99b85f](https://github.com/pitch-io/uix/commit/99b85f69f5f6402c9b2e0629e0b42913de692330)
- Fixed Hooks deps linter missing reporting deps which names are shadowing JS globals [2d11b8](https://github.com/pitch-io/uix/commit/2d11b818b8948935feb0689387bf082b64e49db8)
- Use `createRoot` in tests [32e0bc](https://github.com/pitch-io/uix/commit/32e0bc98fca5ba9433f714bada4f4191b98b5955)

## 0.9.0

### New

- Public API for linter plugins [#86](https://github.com/pitch-io/uix/pull/86)
- Deps linting enabled by default [25cc16](https://github.com/pitch-io/uix/commit/25cc16a5787d30492e17b7d3e254d351e45fc6e9)
- Added support for dynamic element types [14d5b7](https://github.com/pitch-io/uix/commit/14d5b7d29897c314a7d4a76570f47c58326a5081)
- Added clj-kondo config [#97](https://github.com/pitch-io/uix/pull/97)
- Added `error-boundary` component [#98](https://github.com/pitch-io/uix/pull/98)
- Added support for most React Hooks for SSR on JVM [#104](https://github.com/pitch-io/uix/pull/104)

### Improvements

- Improved missing deps reporting [5567dc](https://github.com/pitch-io/uix/commit/5567dce02e00cfdeb1e2da4fcd18af2fb14c16f0)
- Fixed re-frame/subscribe linter [#92](https://github.com/pitch-io/uix/pull/92)
- Fixed `dangerouslySetInnerHTML` throwing on JVM [#102](https://github.com/pitch-io/uix/pull/102)

### Docs

- Documented internals [docs/internals.md](https://github.com/pitch-io/uix/blob/master/docs/internals.md)

## 0.8.1

### Improvements

- Fixed how react-dom API is exposed (react-dom vs react-dom/client)

## 0.8.0

### New

- React v18.2.0 API compatibility [#59](https://github.com/pitch-io/uix/pull/59)
- New hooks wrappers: `use-insertion-effect`, `use-deferred-value`, `use-transition`, `start-transition`, `use-id` and `use-sync-external-store`
- New `uix.dom` public API: `create-root`, `hydrate-root`, `render-root` and `unmount-root`

## 0.7.1

### Improvements

- Fix rules of hooks linter [f7276d](https://github.com/pitch-io/uix/commit/f7276decf191e0b804f7f393add91ebd982dcade)

## 0.7.0

### New

- Added `uix.core/fn` macro [924e4b](https://github.com/pitch-io/uix/commit/924e4b37f841120312054dac283c2b59125e7cda)

## 0.6.2

### Improvements

- Improved linter rule for missing `:key` attribute [1ee076](https://github.com/pitch-io/uix/commit/1ee076bf1f877e82f105a65faaa0e6586e7c4dc1)
- Make missing `:key` rule configurable [6f4873](https://github.com/pitch-io/uix/commit/6f4873af5881cda2e3a44e0c4223c399efcf79e4)

## 0.6.1

### Improvements

- Recursive class names stringifier [ebc178](https://github.com/pitch-io/uix/commit/ebc1787dce975a7608aab5549006b7a71aa327ea)

## 0.6.0

### Improvements

- Better code location pointing in linting errors [7116d7](https://github.com/pitch-io/uix/commit/7116d76f18d6d7d216ec410894142bd041550de8)

### New

- Linter rule to report on missing `:key` attribute [fed7d8](https://github.com/pitch-io/uix/commit/fed7d8e0e2532dd6edf82dc60425a7a6e062813b)

## 0.5.0

### Improvements

- Make re-frame linter check configurable [8ef493](https://github.com/pitch-io/uix/commit/8ef4932f88071377e16dde755c18a8e60aaf05e7)
- Improve linter's error messages [03609b](https://github.com/pitch-io/uix/commit/03609bd305c6706f830a24517999f6e90527ce35)

## 0.4.0

### Improvements

- Added linter check for non-reactive usage of re-frame subscriptions in UIx components via re-frame API [071650](https://github.com/pitch-io/uix/commit/0716507b6bfdcb28091879ef14958aae4300c751)

### Docs

- Added a section on [“Syncing with ratoms and re-frame”](https://github.com/pitch-io/uix/blob/master/docs/interop-with-reagent.md#syncing-with-ratoms-and-re-frame)
- Added a section about [“Utilities”](https://github.com/pitch-io/uix/blob/master/docs/utilities.md)

## 0.3.0

### Improvements

- Fixed shadowing for non-ns component var generated in dev-only code [4458ee](https://github.com/pitch-io/uix/commit/4458ee7c31aa87e98961140ba0fa2807f57d2de9)

### New

- Basic SSR on JVM [4a10c9](https://github.com/pitch-io/uix/commit/4a10c9b9282fadb2c58029d0786ceba77f4487f4)

## 0.2.0

### Improvements

- Improved missing deps check to account for vars shadowing [#73](https://github.com/pitch-io/uix/pull/73)

### New

- Added `^:lint-deps` meta for deps vector in hooks to opt-in for missing deps check [1dbb7d9](https://github.com/pitch-io/uix/commit/1dbb7d93d17941e3066e5d5a3029d0642868c8c0)
- Documented hooks linting [baa7b9](https://github.com/pitch-io/uix/commit/baa7b90850378102d89c4fa15022569d769c1bef)
