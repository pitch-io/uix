(ns uix.rsc-example.server.services
  (:require [org.httpkit.client :as http]
            [cheshire.core :as json]
            [uix.rsc :as rsc]
            [uix.rsc-example.server.db :as db]))

(def fetch-json
  (memoize
    (fn [path]
      (-> @(http/get path)
          :body
          (json/parse-string keyword)))))

(defn fetch-story [id]
  (or
    (db/story-by-id id)
    (let [story (fetch-json (str "https://hacker-news.firebaseio.com/v0/item/" id ".json"))]
      (db/insert-story story)
      (db/story-by-id id))))

(defn fetch-stories [pathname]
  (let [pathname (if (= pathname "/")
                   "/newstories"
                   pathname)]
    (->> (fetch-json (str "https://hacker-news.firebaseio.com/v0/" pathname ".json"))
         (take 10)
         (pmap fetch-story)
         seq)))

(defn fetch-item [id]
  (rsc/cache [:item id]
    (-> (fetch-story id)
        (update :kids #(seq (pmap fetch-item %))))))