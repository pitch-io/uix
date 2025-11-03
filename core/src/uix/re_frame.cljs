(ns uix.re-frame
  (:require [re-frame.core :as rf]
            [reagent.ratom :as ratom]
            [uix.reagent :as uix-reagent]))

;; Public API

(def use-reaction
  "Takes Reagent's Reaction, Track or Cursor type,
   subscribes UI component to changes in the reaction
   and returns current state value of the reaction."
  ; for retrocompatibility
  uix-reagent/use-reaction)

(defn use-subscribe
  "Takes re-frame subscription query e.g. [:current-document/title],
  creates an instance of the subscription,
  subscribes UI component to changes in the subscription
  and returns current state value of the subscription"
  [query]
  (let [sub (binding [ratom/*ratom-context* #js {}]
              (rf/subscribe query))
        ;; using an empty atom when re-frame subscription is not registered
        ;; re-frame will still print the error in console
        ref (or sub (atom nil))]
    (use-reaction ref)))
