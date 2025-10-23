(ns uix.dom
  "Public API"
  (:require-macros [uix.dom.linter])
  (:require ["react-dom/client" :as rdom-client]
            [react-dom :as rdom]))

;; react-dom top-level API

(defn create-root
  "Create a React root for the supplied container and return the root.

  See: https://react.dev/reference/react-dom/client/createRoot"
  ([node]
   (rdom-client/createRoot node))
  ([node {:keys [on-recoverable-error identifier-prefix on-caught-error on-uncaught-error]}]
   (rdom-client/createRoot node #js {:onRecoverableError on-recoverable-error
                                     :identifierPrefix identifier-prefix
                                     :onCaughtError on-caught-error
                                     :onUncaughtError on-uncaught-error})))

(defn hydrate-root
  "Same as `create-root`, but is used to hydrate a container whose HTML contents were rendered by ReactDOMServer.

  See: https://react.dev/reference/react-dom/client/hydrateRoot"
  ([container element]
   (rdom-client/hydrateRoot container element))
  ([container element {:keys [on-recoverable-error identifier-prefix] :as options}]
   (rdom-client/hydrateRoot container element #js {:onRecoverableError on-recoverable-error
                                                   :identifierPrefix identifier-prefix})))

(defn render-root
  "Renders React root into the DOM node."
  [element ^js/ReactDOMRoot root]
  (.render root element))

(defn unmount-root
  "Remove a mounted React root from the DOM and clean up its event handlers and state."
  [^js/ReactDOMRoot root]
  (.unmount root))

(defn render
  "DEPRECATED: Renders element into DOM node. The first argument is React element."
  [element node]
  (rdom/render element node))

(defn hydrate
  "DEPRECATED: Hydrates server rendered document at `node` with `element`."
  [element node]
  (rdom/hydrate element node))

(defn flush-sync
  "Force React to flush any updates inside the provided callback synchronously.
  This ensures that the DOM is updated immediately.

  See: https://react.dev/reference/react-dom/flushSync"
  [callback]
  (rdom/flushSync callback))

(defn batched-updates [f]
  (rdom/unstable_batchedUpdates f))

(defn unmount-at-node
  "Unmounts React component rendered into DOM node"
  [node]
  (rdom/unmountComponentAtNode node))

(defn find-dom-node
  "If this component has been mounted into the DOM, this returns the corresponding native browser DOM element.

  See: https://react.dev/reference/react-dom/findDOMNode"
  [component]
  (rdom/findDOMNode component))

(defn create-portal
  "Creates a portal. Portals provide a way to render children into a DOM node
  that exists outside the hierarchy of the DOM component.

  See: https://react.dev/reference/react-dom/createPortal"
  ([child node]
   (rdom/createPortal child node))
  ([child node key]
   (rdom/createPortal child node key)))

;; hooks

(defn use-form-status
  "Gives you status information of the last form submission

  See: https://react.dev/reference/react-dom/hooks/useFormStatus"
  []
  (rdom/useFormStatus))

;; resources preloading

(defn prefetch-dns
  "Lets you eagerly look up the IP of a server that you expect to load resources from.
  See: https://react.dev/reference/react-dom/prefetchDNS"
  [url]
  (rdom/prefetchDNS url))

(defn preconnect
  "Lets you eagerly connect to a server that you expect to load resources from.
  See: https://react.dev/reference/react-dom/preconnect"
  [url]
  (rdom/preconnect url))

(defn preload
  "Lets you eagerly fetch a resource such as a stylesheet, font, or external script that you expect to use.
  See: https://react.dev/reference/react-dom/preload"
  [url {:keys [as cross-origin referrer-policy integrity type nonce fetch-priority image-src-set image-sizes]}]
  (rdom/preload url #js {:as as
                         :crossOrigin cross-origin
                         :referrerPolicy referrer-policy
                         :integrity integrity
                         :type type
                         :nonce nonce
                         :fetchPriority fetch-priority
                         :imageSrcSet image-src-set
                         :imageSizes image-sizes}))

(defn preload-module
  "Lets you eagerly fetch an ESM module that you expect to use.
  See: https://react.dev/reference/react-dom/preloadModule"
  [url {:keys [as cross-origin integrity nonce]}]
  (rdom/preloadModule url #js {:as as
                               :crossOrigin cross-origin
                               :integrity integrity
                               :nonce nonce}))

(defn preinit
  "Lets you eagerly fetch and evaluate a stylesheet or external script.
  See: https://react.dev/reference/react-dom/preinit"
  [url {:keys [as precedence cross-origin integrity nonce fetch-priority]}]
  (rdom/preinit url #js {:as as
                         :precedence precedence
                         :crossOrigin cross-origin
                         :integrity integrity
                         :nonce nonce
                         :fetchPriority fetch-priority}))

(defn preinit-module
  "Lets you eagerly fetch and evaluate an ESM module.
  See: https://react.dev/reference/react-dom/preinitModule"
  [url {:keys [as cross-origin integrity nonce]}]
  (rdom/preinitModule url #js {:as as
                               :crossOrigin cross-origin
                               :integrity integrity
                               :nonce nonce}))