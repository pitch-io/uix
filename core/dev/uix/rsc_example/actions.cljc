(ns uix.rsc-example.actions
  (:require [uix.rsc :refer [defaction]]
            [uix.rsc-example.server.db :as db]))

(defaction vote [{:keys [id]}]
  (db/vote-on-story id))