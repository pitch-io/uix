(ns uix.dom.server
  (:require-macros [uix.dom.server])
  (:require ["react-dom/server" :as rdom-server]))

(defn render-to-string
  "Same as https://react.dev/reference/react-dom/server/renderToString"
  [element]
  (rdom-server/renderToString element))

(defn render-to-static-markup
  "Same as https://react.dev/reference/react-dom/server/renderToStaticMarkup"
  [element]
  (rdom-server/renderToStaticMarkup element))

(defn render-to-static-node-stream
  "Same as https://react.dev/reference/react-dom/server/renderToStaticNodeStream"
  [element]
  (rdom-server/renderToStaticNodeStream element))

(defn render-to-pipeable-stream
  "Same as https://react.dev/reference/react-dom/server/renderToPipeableStream"
  ([element]
   (rdom-server/renderToPipeableStream element))
  ([element opts]
   (rdom-server/renderToPipeableStream element opts)))

(defn render-to-readable-stream
  "Same as https://react.dev/reference/react-dom/server/renderToReadableStream"
  ([element]
   (rdom-server/renderToReadableStream element))
  ([element opts]
   (rdom-server/renderToReadableStream element opts)))
