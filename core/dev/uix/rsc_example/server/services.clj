(ns uix.rsc-example.server.services
  (:require [org.httpkit.client :as http]
            [cheshire.core :as json]))

(defn fetch-json [path]
  (-> @(http/get path)
      :body
      (json/parse-string keyword)))

(defn fetch-story [id]
  (fetch-json (str "https://hacker-news.firebaseio.com/v0/item/" id ".json")))

(defn fetch-stories [pathname]
  (let [pathname (if (= pathname "/")
                   "/newstories"
                   pathname)]
    (->> (fetch-json (str "https://hacker-news.firebaseio.com/v0/" pathname ".json"))
         (take 10)
         (pmap fetch-story))))

(defn fetch-item [id]
  (-> (fetch-story id)
      (update :kids #(pmap fetch-item %))))