# UIx Auto-Memoizing Compiler - Implementation Plan

This document outlines the implementation plan for UIx's auto-memoizing compiler, similar to React Compiler (React Forget) but leveraging ClojureScript's strengths.

## Overview

The auto-memoizing compiler automatically memoizes values, props, and elements at compile-time to reduce unnecessary re-renders, without requiring manual `use-memo`, `use-callback`, or `memo` calls.

### Key Advantages in ClojureScript

- **Value-based equality**: Clojure's immutable data structures use value equality, making dependency comparison straightforward and reliable
- **Powerful macro system**: Compile-time code transformation is natural in Clojure
- **ClojureScript Analyzer**: Provides AST access for analyzing free variables and dependencies

---

## Current Implementation Status

### ✅ Completed

1. **Auto `React.memo` wrapping**: All `defui` components are wrapped in `React.memo` by default
2. **Props memoization** (`with-memo-attrs`): Caches literal props maps with free variables as deps
3. **Element memoization** (`with-memo-element`): Caches entire `$` element expressions
4. **Let binding memoization** (`memo-body`): Caches values in `let` bindings (non-hook expressions)
5. **Runtime cache** (`-use-cache-internal`): Per-render cache using `useRef`
6. **Hook detection**: Prevents caching expressions containing hook calls
7. **Linter rule**: `::memo-compiler-inline-hook` catches inline hooks in elements
8. **Opt-out mechanism**: `^{:memo false}` metadata and `*memo-disabled?*` dynamic var
9. **Slot-ID based caching**: Cache uses compile-time slot IDs (line:column) instead of sequential counters, preventing cache drift when conditional code paths change
10. **Destructuring support**: Let bindings with destructuring patterns (`{:keys [x y]}`) are now memoized correctly
11. **Function/callback memoization**: Inline functions in props are automatically cached via props memoization - free variables within the function are detected as dependencies
12. **Hook hoisting**: Bindings after hooks are correctly cached with hook results as dependencies. Hook calls themselves are never cached (via `has-hook-call?` check), while subsequent non-hook computations use hook-bound values as deps automatically.

### 🚧 In Progress / TODO

- Static element hoisting (advanced optimization)

---

## Implementation Plan

### Phase 1: Stabilize Core Caching Mechanism

#### 1.1 Fix Cache Slot Identification

**Problem**: Current runtime uses sequential ID counter which can drift if render paths change.

**Current approach**:

```clojure
(let [aid (.-current id)
      did (str ^string aid "-0")
      vid (str ^string aid "-1")]
  (set! (.-current id) (inc aid))
  ...)
```

**Solution**: Use compile-time slot IDs based on source location (already using `line:column` for var-names).

**Changes**:

1. Pass compile-time slot ID as first argument to cache lookup
2. Remove runtime ID counter
3. Use a Map or object keyed by slot ID

```clojure
;; Compile-time generated:
(-uix-ccache "e12:5" [dep1 dep2] (fn [] ...))

;; Runtime:
(defn -use-cache-internal []
  (let [cache (hooks/use-ref #js {})]
    (fn [slot-id deps get-value]
      (let [cache-obj (.-current cache)
            cached-deps (aget cache-obj (str slot-id "-d"))
            cached-value (aget cache-obj (str slot-id "-v"))]
        (if (= deps cached-deps)
          cached-value
          (let [value (get-value)]
            (aset cache-obj (str slot-id "-d") deps)
            (aset cache-obj (str slot-id "-v") value)
            value))))))
```

#### 1.2 Cache Memory Management

**Problem**: Cache entries accumulate and are never cleaned.

**Solution**:

- For stable code paths, this is fine (entries are reused)
- Consider optional cache size limit for development
- Document that dynamic code generation could cause growth

---

### Phase 2: Destructuring Support

#### 2.1 Let Binding Destructuring

**Problem**: Common patterns like `(let [{:keys [x y]} value] ...)` aren't memoized.

**Analysis needed**:

- Parse destructuring forms to extract bound symbols
- Find free variables in the value expression
- Wrap the entire destructuring in cache lookup

**Implementation**:

```clojure
;; Input:
(let [{:keys [x y]} (compute-something a b)]
  (use-effect ...))

;; Output:
(let [{:keys [x y]} (-uix-ccache "b5:7" [a b]
                      (fn [] (compute-something a b)))]
  (use-effect ...))
```

**Steps**:

1. Detect destructuring patterns in `memo-body`
2. Extract the value expression
3. Check if value contains hook calls (skip if yes)
4. Find free variables in value
5. Wrap value in cache lookup

#### 2.2 Function Argument Destructuring

Already handled by `defui` macro - props destructuring works.

---

### Phase 3: Function/Callback Memoization

#### 3.1 Inline Function Detection

**Goal**: Automatically memoize inline functions (equivalent to `useCallback`).

**Patterns to handle**:

```clojure
;; In props
($ button {:on-click (fn [e] (handler e value))})

;; In let bindings
(let [handle-click (fn [e] (do-something value))]
  ...)
```

**Implementation**:

1. Detect `fn` and `fn*` forms in props and let bindings
2. Find free variables (closed-over values)
3. Wrap in cache lookup

```clojure
;; Input:
($ button {:on-click (fn [e] (handler e value))})

;; Output:
($ button {:on-click (-uix-ccache "f8:20" [handler value]
                       (fn [] (fn [e] (handler e value))))})
```

#### 3.2 Named Function References

Functions defined at component level should also be cached:

```clojure
;; Input:
(defui my-component [{:keys [value]}]
  (let [handler (fn [e] (process value))]
    ($ child {:on-click handler})))

;; Already handled by let binding memoization
```

---

### Phase 4: Hook Hoisting ✅

#### 4.1 Problem Statement

Hooks must be called unconditionally at the top level. When memoizing expressions, we might inadvertently capture stale hook values if hooks are called after memoized blocks.

**Problematic pattern**:

```clojure
(let [cached-value (-uix-ccache [...] (fn [] (use-something)))]  ;; BAD: hook in cache
  ...)
```

**Current handling**: `has-hook-call?` check prevents caching expressions with hooks.

#### 4.2 Implementation Status

**This is already working correctly!**

The current implementation handles hook hoisting automatically:

1. **Hook bindings are never cached**: The `has-hook-call?` check ensures hook calls remain at the top level
2. **Hook results become dependencies**: When analyzing subsequent bindings, the ClojureScript analyzer's `env` includes hook-bound symbols as locals
3. **Automatic dependency tracking**: `find-best-env-for-value` finds the right environment to analyze dependencies, naturally including hook results

```clojure
;; Input:
(defui my-component [{:keys [id]}]
  (let [[counter _set-counter] (use-state 0)  ; Hook - not cached
        processed (transform counter id)]      ; Cached with [counter id] as deps
    ...))

;; Compiled output:
(defui my-component [{:keys [id]}]
  (let [[counter _set-counter] (use-state 0)  ; Hook stays uncached
        processed (-uix-ccache "b3:7" [counter id]
                    (fn [] (transform counter id)))]  ; counter is now a dep
    ...))
```

**Multiple hooks also work**:

```clojure
(let [[count1 _] (use-state 10)
      [count2 _] (use-state 20)
      ref (use-ref nil)
      total (* (+ count1 count2) multiplier)]  ; Cached with [count1 count2 multiplier]
  ...)
```

**No additional implementation needed** - the design naturally handles this case.

---

### Phase 5: Advanced Optimizations

#### 5.1 Static Element Hoisting

**Goal**: Hoist truly static elements to module scope.

Elements with zero dependencies can be created once:

```clojure
;; Input:
(defui header []
  ($ :header
    ($ :h1 "Welcome")
    ($ :nav ...)))

;; Output:
(def ^:private -static-h1 ($ :h1 "Welcome"))

(defui header []
  ($ :header
    -static-h1
    ($ :nav ...)))
```

**Already partially implemented**: `rewrite-forms` with `:hoist?` option and `uix-aot-hoisted` symbols.

#### 5.2 Conditional Caching

Handle conditional expressions safely:

```clojure
;; Input:
(if condition
  ($ :div {:data (expensive-1)})
  ($ :span {:data (expensive-2)}))

;; Each branch gets its own cache slot
```

**Current behavior**: Both branches are analyzed and get separate slots based on line:column.

#### 5.3 Loop/Iteration Caching

```clojure
;; Input:
(for [item items]
  ($ card {:data (transform item)}))

;; Challenge: Same source location, different items
```

**Solution**: Include loop variable in cache key or deps.

---

### Phase 6: Developer Experience

#### 6.1 Debugging Tools

1. **Dev-mode logging**: Optional logging of cache hits/misses
2. **React DevTools integration**: Ensure memoized components show correctly
3. **Compiler output inspection**: Macro to view generated code

```clojure
;; Debug helper
(defmacro debug-memo [& body]
  (binding [*memo-debug?* true]
    `(do ~@body)))
```

#### 6.2 Documentation

1. Document supported patterns
2. Document unsupported patterns and workarounds
3. Performance benchmarks vs manual memoization
4. Migration guide from manual memo usage

#### 6.3 Linter Enhancements

1. Warn about patterns that prevent memoization
2. Suggest fixes for common issues
3. Report memoization coverage statistics

---

## Testing Strategy

### Unit Tests

1. Cache hit/miss scenarios
2. Dependency change detection
3. Hook exclusion
4. Destructuring patterns
5. Nested elements

### Integration Tests

1. Full component render cycles
2. Re-render counting
3. Props change propagation
4. Context updates

### Performance Tests

1. Benchmark vs non-memoized
2. Benchmark vs manual memoization
3. Memory usage profiling
4. Large component trees

---

## Implementation Order

| Priority | Task                                 | Complexity | Impact |
| -------- | ------------------------------------ | ---------- | ------ |
| P0       | Fix cache slot identification        | Medium     | High   |
| P1       | Destructuring in let bindings        | Medium     | High   |
| P1       | Function/callback memoization        | Medium     | High   |
| P2       | Dev-mode debugging tools             | Low        | Medium |
| P2       | Documentation                        | Low        | High   |
| P3       | Hook hoisting                        | High       | Medium |
| P3       | Static element hoisting improvements | Medium     | Medium |
| P4       | Loop/iteration caching               | High       | Low    |

---

## Open Questions

1. **Ref handling**: Should `use-ref` return values be excluded from deps? (Currently yes via linter)

2. **Context values**: How to handle context that changes frequently?

3. **Third-party hooks**: Should we maintain a list of known-stable hooks?

4. **Partial opt-out**: Should there be a way to opt-out specific expressions rather than entire components?

5. **SSR considerations**: Does the cache mechanism work correctly with server rendering?

---

## References

- [React Compiler (Forget)](https://react.dev/learn/react-compiler)
- [React Compiler Deep Dive](https://www.youtube.com/watch?v=kjOacmVsLSE)
- [ClojureScript Analyzer](https://clojurescript.org/guides/compile-time)
- [UIx Documentation](./what-is-uix.md)
