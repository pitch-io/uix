(ns uix.rsc-example.client.ui
  (:require [uix.core :refer [defui $] :as uix]
            [uix.rsc-example.actions :as actions]))

;; ^:client turns client component into a client ref
;; when the component is used in server components tree
(defui ^:client vote-btn [{:keys [id score]}]
  (let [[score set-score] (uix/use-state score)
        vote #(-> (actions/vote id score)
                  (.then set-score))]
    ($ :button {:on-click vote
                :style {:text-decoration :underline
                        :cursor :pointer}}
       (str "Vote " score))))
