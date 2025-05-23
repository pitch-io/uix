(ns uix.rsc-example.client.ui
  (:require [uix.core :refer [defui $] :as uix]
            [uix.dom :as dom]
            [uix.rsc :as rsc]))

;; todo: make client any var via ^:client meta
;; in cljs? maybe scan vars in shadow?
(def say-hi
  ^{:rsc/id "uix.rsc-example.client.ui/say-hi"} (fn []
                                                  (prn :HEllo!)))

#?(:cljs
    (uix.rsc/register-rsc-client! "uix.rsc-example.client.ui/say-hi" say-hi))

(defui ^:client vote-btn [{:keys [score label]}]
  (let [{:keys [pending]} (dom/use-form-status)]
    ($ :button {:type :submit
                :style {:text-decoration :underline
                        :cursor :pointer}}
       (if pending
         "voting"
         ($ :<> label " " score)))))

(defui ^:client fav-button [{:keys [liked?]}]
  (let [{:keys [pending]} (dom/use-form-status)]
    ($ :button {:type :submit
                :on-click #(when pending (.preventDefault %))
                :class "font-instrumentSans font-semibold rounded-xl w-full text-xl border-[2px] py-2 px-5 group"}
       ($ :span {:class "group-hover:scale-105 group-active:scale-100 inline-block transition-transform duration-100"}
          (cond
            (and pending liked?) "Removing..."
            (and pending (not liked?)) "Adding..."
            liked? "Remove from favorites"
            :else "Add to favorites")))))

;; ^:client turns client component into a client ref
;; when the component is used in server components tree
#_
(defui ^:client vote-btn [{:keys [score label on-vote] :as props}]
  (rsc/use-client {:fallback ($ :button "Vote 0")}
    ($ vote-btn* props)))