(ns uix.rsc-example.server.services
  (:require [org.httpkit.client :as http]
            [cheshire.core :as json]
            [uix.rsc :as rsc]))

(def fetch-json
  (memoize
    (fn [path]
      (-> @(http/get path)
          :body
          (json/parse-string keyword)))))

(defn fetch-story [id]
  (rsc/cache [:story id]
    (fetch-json (str "https://hacker-news.firebaseio.com/v0/item/" id ".json"))))

(defn fetch-stories [pathname]
  (rsc/cache [:stories pathname]
    (let [pathname (if (= pathname "/")
                     "/newstories"
                     pathname)]
      (->> (fetch-json (str "https://hacker-news.firebaseio.com/v0/" pathname ".json"))
           (take 10)
           (pmap fetch-story)
           seq))))

(defn fetch-item [id]
  (rsc/cache [:item id]
    (-> (fetch-story id)
        (update :kids #(seq (pmap fetch-item %))))))