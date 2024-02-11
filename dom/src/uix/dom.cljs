(ns uix.dom
  "Public API"
  (:require-macros [uix.dom.linter])
  (:require ["react-dom/client" :as rdom-client]
            [react-dom :as rdom]))

;; react-dom top-level API

(defn create-root
  "Create a React root for the supplied container and return the root.

  See: https://reactjs.org/docs/react-dom-client.html#createroot"
  ([node]
   (rdom-client/createRoot node))
  ([node {:keys [on-recoverable-error identifier-prefix] :as options}]
   (rdom-client/createRoot node #js {:onRecoverableError on-recoverable-error
                                     :identifierPrefix identifier-prefix})))

(defn hydrate-root
  "Same as `create-root`, but is used to hydrate a container whose HTML contents were rendered by ReactDOMServer.

  See: https://reactjs.org/docs/react-dom-client.html#hydrateroot"
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

  See: https://reactjs.org/docs/react-dom.html#flushsync"
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

  See: https://reactjs.org/docs/react-dom.html#finddomnode"
  [component]
  (rdom/findDOMNode component))

(defn create-portal
  "Creates a portal. Portals provide a way to render children into a DOM node
  that exists outside the hierarchy of the DOM component.

  See: https://reactjs.org/docs/react-dom.html#createportal"
  ([child node]
   (rdom/createPortal child node))
  ([child node key]
   (rdom/createPortal child node key)))
