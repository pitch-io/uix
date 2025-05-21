(ns uix.rsc-example.server.db
  (:require [cheshire.core :as json]
            [clojure.string :as str]
            [next.jdbc :as jdbc]
            [uix.rsc.loader :as loader]))

(def ^:dynamic *sid*)
(def sessions (atom #{}))

;; sqlite
(def ds
  (jdbc/get-datasource
    {:jdbcUrl "jdbc:sqlite:/Users/romanliutikov/projects/uix/core/database.sqlite"}))

(defn ds-exec [stmt]
  (jdbc/execute! ds stmt))

(defn fetch-movies [ids]
  #_(println :fetch-movies ids)
  (let [query (str "SELECT
                      m.*,
                      JSON_GROUP_ARRAY(DISTINCT mg.genre_id) as genre_ids,
                      JSON_GROUP_ARRAY(DISTINCT mc.cast_id) as cast_ids
                    FROM movies m
                    LEFT JOIN movie_genres mg ON m.id = mg.movie_id
                    LEFT JOIN movie_cast mc ON m.id = mc.movie_id
                    WHERE m.id IN (" (str/join "," (repeat (count ids) "?")) ")
                    GROUP BY m.id")]
    (->> (ds-exec `[~query ~@ids])
         (map #(update % :cast_ids json/parse-string))
         ;; batch function results must be sorted in the same order as the input
         (sort-by #(.indexOf ids (:movies/id %))))))

(def fetch-movie
  (loader/batch
    (fn [& args]
      (let [ids (mapv first args)]
        (fetch-movies ids)))))

(defn- fetch-actors [ids]
  #_(println :fetch-actors ids)
  (let [query (str "SELECT
                      actor.*,
                      GROUP_CONCAT(
                        DISTINCT mc.movie_id
                        ORDER BY movie.year DESC
                      ) as movie_ids
                    FROM cast_members as actor
                    LEFT JOIN movie_cast mc ON actor.id = mc.cast_id
                    LEFT JOIN movies movie ON mc.movie_id = movie.id
                    WHERE actor.id IN (" (str/join "," (repeat (count ids) "?")) ")
                    GROUP BY actor.id")
        parse-movie-ids (fn [movie_ids]
                          (->> (str/split movie_ids #",")
                               (map #(Integer/parseInt %))))]
    (->> (ds-exec `[~query ~@ids])
         (map #(update % :movie_ids parse-movie-ids))
         ;; batch function results must be sorted in the same order as the input
         (sort-by #(.indexOf ids (:actor/id %))))))

(def fetch-actor
  (loader/batch
    (fn [& args]
      (let [ids (mapv first args)]
        (fetch-actors ids)))))

(defn favs [sid]
  (ds-exec ["SELECT movie_id FROM favorites WHERE session_id = ?" sid]))

(defn fav? [sid id]
  (-> (ds-exec ["SELECT id FROM favorites WHERE session_id = ? AND movie_id = ?" sid id])
      empty?
      not))

(defn favs+ [sid id]
  (or (fav? sid id)
      (ds-exec ["INSERT INTO favorites (session_id, movie_id) VALUES (?, ?)" sid id])))

(defn favs- [sid id]
  (ds-exec ["DELETE FROM favorites WHERE session_id = ? AND movie_id = ?" sid id]))

;; mem atom
(def db (atom {:stories {}}))

(defn story-by-id [id]
  (get-in @db [:stories id]))

(defn insert-story [story]
  (swap! db assoc-in [:stories (:id story)] story))

(defn vote-on-story [id]
  (swap! db update-in [:stories id :score] inc))