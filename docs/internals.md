# Internals

This document describes internals of UIx to give you a better overview how the library works internally.

## High level overview

On the surface UIx is a wrapper for React.js, it consists of two packages: `uix.core` and `uix.dom`, the former wraps `react` and the latter wraps `react-dom`.

![](uix_internals_1.jpg)

## uix/dom package

`uix.dom` namespace is a thin wrapper around `react-dom` API. `uix.dom.server` CLJS ns wraps `react-dom/server`, but CLJ ns is a custom made serializer that allows to render UIx components on JVM (of course third-party JS components won't run on JVM, so the JVM renderer is mostly useful for purely UIx made UIs or HTML templating, think static website generators). Also `uix.dom` includes a linter that validates DOM nodes.

## uix/core package

This one is a lot more involved. While the core package wraps a public API from `react`, it also makes hooks nicer, runs an internal linter and compiles components and UI structure into a more efficient code.

### `defui` and `fn` macros

`defui` is how you define a UIx component. What the macro is doing? Multiple things:

1. Handles props passing, to make sure that Clojure's data structures can be used as component props, otherwise React will destroy them when validating props object
2. Emits React component when compiling as ClojureScript and a plain function when running on JVM
3. Injects `react-refresh` setup for [hot-reloading](/docs/hot-reloading.md) in dev
4. Assigns readable component name to improve debugging (those names are printed in stack traces and in React DevTools)
5. Makes sure that a component is a single-arity function that takes a map of props
6. Runs a [linter](/docs/code-linting.md) on component's body

`uix.core/fn` macro is similar to `defui`, but should be used to create anonymous components, which is useful for [render props technique](https://reactjs.org/docs/render-props.html).

### `$` macro

`$` is basically `React.createElement`, but with API that makes it nicer to use in Clojure world. Here's what it does:

1. Runs a linter that validates element syntax
2. Compiles DOM elements into Clojure-less code, for performance, when possible

#### Element compiler

This part, while there's not much code to it, is probably where the most of complexity in UIx lives.

`uix.compiler.aot` namespace (ahead of time compiler) takes care of compiling `$` elements to ClojureScript that is as close as possible to plain JS from performance standpoint. The compilation process boils down to transforming something like `($ :div {:on-click on-click} ...)` into `(react/createElement "div" #js {:onClick on-click} ...)`. The end goal is to have as less Clojure data types as possible in the end to achieve best performance.

The compiler is not strict and makes a set of tradeoffs to provide flexibility for developers. Thus it's allowed to have dynamic (resolved at runtime) props, although in that case props map will be interpreted at runtime.

#### Reagent's input

`uix.compiler.input` namespace provides Reagent's input DOM node wrapper to make inputs compatible with re-frame, since re-frame's update queue is always async, but in pure React, or rather browsers in general, user input should be always handled synchronously to avoid visual glitches such as lost characters or caret position.

### Hooks

UIx wraps all React's default hooks and adds an extra layer to hide differences between JS and Clojure world, to make sure that writing is a bit less annoying:

1. Compiles vector of deps into JS array
2. Runs a linter to check for invalid usages of deps
3. Handles React's requirement to return either a function or `js/undefined` from a hook in a way that `nil` becomes acceptable return value as well

### Linter

`uix.linter` implements a built-in linter that takes care of validating components and hooks. The linter is extensible via [public API](/docs/code-linting.md#custom-linters).

The linter leverages ClojureScript's analyzer to retrieve information about code structure at compile-time. This data provides info about local and global vars, usages of vars, and of course the data structure representing the code being analyzed.

When analyzing the linter collects and reports errors into ClojureScript's analyzer, that then takes care of printing those errors in terminal, failing a build and propagating them into shadow-cljs's on screen error display.

### Re-frame

`uix.re-frame` namespace provides integration of re-frame subscriptions into UIx components, via `use-subscribe` and `use-reaction` hooks.
