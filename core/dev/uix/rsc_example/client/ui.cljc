(ns uix.rsc-example.client.ui
  (:require [uix.core :refer [defui $] :as uix]
            [uix.rsc :as rsc]))

;; todo: make client any var via ^:client meta
;; in cljs? maybe scan vars in shadow?
(def say-hi
  ^{:rsc/id "uix.rsc-example.client.ui/say-hi"} (fn []
                                                  (prn :HEllo!)))

#?(:cljs
    (uix.rsc/register-rsc-client! "uix.rsc-example.client.ui/say-hi" say-hi))

(defui vote-btn [{:keys [score label]}]
  ($ :button {:type :submit
              :style {:text-decoration :underline
                      :cursor :pointer}}
     label " " score))

;; ^:client turns client component into a client ref
;; when the component is used in server components tree
#_
(defui ^:client vote-btn [{:keys [score label on-vote] :as props}]
  (rsc/use-client {:fallback ($ :button "Vote 0")}
    ($ vote-btn* props)))