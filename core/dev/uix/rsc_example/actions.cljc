(ns uix.rsc-example.actions
  (:require [uix.rsc :refer [defaction]]
            [uix.rsc-example.server.db :as db]))

(defaction vote [{:keys [id]}]
  (Thread/sleep 1000)
  (db/vote-on-story id))

(defaction update-fav [{:keys [id intent my-file]}]
  (prn my-file)
  (case intent
    :add (db/favs+ db/*sid* id)
    :remove (db/favs- db/*sid* id))
  true)