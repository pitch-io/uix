(ns uix.rsc-example.actions
  (:require [uix.rsc :refer [defaction]]
            [uix.rsc-example.server.db :as db]))

(defaction vote [{:keys [id]}]
  (Thread/sleep 1000)
  (db/vote-on-story id))

(defaction update-fav [{:keys [id intent]}]
  (Thread/sleep 1000))