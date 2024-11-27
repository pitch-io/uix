(ns uix.test-utils
  (:require ["react-dom/server" :as rserver]
            ["react-dom/client" :as rdc]
            [react]
            [goog.object :as gobj]
            [clojure.test :refer [is]]
            [jsdom :refer [JSDOM]]))

(defn as-string [el]
  (rserver/renderToStaticMarkup el))

(defn js-equal? [a b]
  (gobj/equals a b))

(defn symbol-for [s]
  (js* "Symbol.for(~{})" s))

(defn react-element-of-type? [f type]
  (= (gobj/get f "$$typeof") (symbol-for type)))

(defn with-error [f]
  (let [msgs (atom [])
        cc js/console.error]
    (set! js/console.error #(swap! msgs conj %))
    (f)
    (set! js/console.error cc)
    (is (empty? @msgs))))

(defn setup-dom []
  (let [dom (JSDOM. "<!DOCTYPE html><html><body></body></html>")]
    (set! js/global.document (.. dom -window -document))
    (set! js/global.window (.-window dom))
    dom))

(defn destroy-dom []
  (set! js/global.document js/undefined)
  (set! js/global.window js/undefined))

(defn with-react-root
  ([el f]
   (with-react-root el f (fn [])))
  ([el f after-unmount]
   (set! (.-IS_REACT_ACT_ENVIRONMENT js/globalThis) true)
   (let [node (js/document.createElement "div")
         _ (js/document.body.append node)
         root (rdc/createRoot node)]
     (react/act #(.render root el))
     (f node)
     (react/act #(.unmount root))
     (after-unmount)
     (.remove node)
     (set! (.-IS_REACT_ACT_ENVIRONMENT js/globalThis) false))))
