(ns uix.rsc-example.client.ui
  (:require [uix.core :refer [defui $] :as uix]
            [uix.rsc-example.actions :as actions]))

;; todo: make client any var via ^:client meta
;; in cljs? maybe scan vars in shadow?
(def say-hi
  ^{:rsc/id "uix.rsc-example.client.ui/say-hi"} (fn []
                                                  (prn :HEllo!)))
#?(:cljs
    (uix.rsc/register-rsc-client! "uix.rsc-example.client.ui/say-hi" say-hi))

;; ^:client turns client component into a client ref
;; when the component is used in server components tree
(defui ^:client vote-btn [{:keys [id score label on-click]}]
  (let [[score set-score] (uix/use-state score)
        vote #(-> (on-click id score)
                  (.then set-score))]
    ($ :button {:on-click vote
                :style {:text-decoration :underline
                        :cursor :pointer}}
       label " " score)))
