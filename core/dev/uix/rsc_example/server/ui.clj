(ns uix.rsc-example.server.ui
  (:require [cheshire.core :as json]
            [clojure.string :as str]
            [uix.core :refer [defui $] :as uix]
            [uix.rsc :as rsc]
            [uix.rsc-example.actions :as actions]
            [uix.rsc-example.server.db :as db]
            [uix.rsc-example.server.services :as services]
            [uix.rsc-example.client.ui :as ui])
  (:import (java.util Locale)
           [org.apache.commons.text StringEscapeUtils]
           [java.time ZonedDateTime]
           [java.time.format DateTimeFormatter FormatStyle]))

(defn to-locale-string [ts]
  (.format (ZonedDateTime/now)
           (.withLocale (DateTimeFormatter/ofLocalizedDateTime FormatStyle/MEDIUM FormatStyle/MEDIUM)
                        Locale/US)))

(defn unescape-text [text]
  (StringEscapeUtils/unescapeHtml4 text))

(defui label []
  ($ :span "Vote"))

(defui external-link [props]
  ($ :a.text-sm.text-emerald-50.mb-1.block.hover:underline
     (into props {:target "_blank"})))

(defui story [{:keys [data]}]
  (let [{:keys [id by score time title url kids]} data
        time (or time 0)]
    ($ :div.text-stone-800.px-4.py-2.bg-emerald-600.border-b.border-emerald-700.hover:bg-emerald-700
       ($ uix/suspense {:fallback ($ :span.text-sm.text-emerald-50.mb-1.block.hover:underline "[title]")}
         ($ external-link
            {:href (or url (str "/item/" (:id data)))}
            title))
       ($ :div.text-xs.flex.gap-2
         ($ :div "by "
            ($ :span.font-medium by))
         " | "
         ;; todo: file upload
         ($ :form {:action (rsc/partial actions/vote {:id id})}
           ($ ui/vote-btn
              {:score score
               :label ($ label)}))
         " | "
         ($ :div
            (to-locale-string (* 1e3 time)))
         " | "
         ($ rsc/link
           {:href (str "/item/" (:id data))
            :class "hover:underline"}
           (str (count kids) " comments"))))))

(defui item-comment [{:keys [data]}]
  (let [{:keys [by text time deleted]} data]
    ($ :div.text-stone-800.px-4.py-2.bg-emerald-600.border-b.border-emerald-700.hover:bg-emerald-700
       ($ :div.text-sm.text-emerald-50.mb-1
          (if deleted
            "[deleted]"
            (unescape-text text)))
       ($ :div.text-xs.flex.gap-2
          (if deleted
            ($ :div
               (to-locale-string (* 1e3 time)))
            ($ :<>
              ($ :div "by "
                 ($ :span.font-medium by))
              " | "
              ($ :div
                 (to-locale-string (* 1e3 time)))))))))

(defui stories [{:keys [path]}]
  (let [data (services/fetch-stories path)]
    (for [d data]
      ($ story {:key (:id d) :data d}))))

(defui item [{:keys [params]}]
  (let [{:keys [id]} params
        {:keys [kids]} (services/fetch-item id)]
    (for [d kids]
      ($ item-comment {:key (:id d) :data d}))))

(defui actor-link [{:keys [id]}]
  (let [{:cast_members/keys [id name]} (db/fetch-actor id)]
    ($ rsc/link {:href (str "/actor/" id)
                 :class "text-[#1458E1] hover:underline"}
       name)))

(defui fav-form [{:keys [id]}]
  (let [liked? (db/fav? db/*sid* id)]
    ($ :form {:action (rsc/partial actions/update-fav {:id id :intent (if liked? :remove :add)})}
       ($ ui/fav-button {:liked? liked?}))))

;; todo: reload client when server updates, in dev
(defui movie-title [{:keys [id]}]
  (let [{:movies/keys [id title year thumbnail extract]
         :keys [cast_ids]}
        (db/fetch-movie id)]
    ($ :div {:class "w-[296px] flex flex-col gap-y-9"}
       ($ rsc/link {:href (str "/movie/" id)}
          ($ :img {:class "w-full h-[435px] object-cover mb-4"
                   :src (if (str/blank? thumbnail)
                          "https://picsum.photos/150/225"
                          thumbnail)}))
       ($ fav-form {:id id})
       ($ :h2 {:class "font-instrumentSerif text-3xl"}
          ($ rsc/link {:href (str "/movie/" id)
                       :class "hover:underline"}
             title)
          " (" year ")")
       ($ :p.mb-2
          (if (> (count extract) 350)
            (str (.substring extract 0 350)
                 "...")
            extract))
       ($ :p
          ($ :b.font-semibold
             "Starring")
          ": "
          (->>
            (for [id (take 10 cast_ids)]
              ($ :span {:key id}
                 ($ actor-link {:id id})))
            (interpose ($ :span.mx-1 "•")))))))

(defui movie-grid [{:keys [children]}]
  ($ :div {:class "p-12 grid grid-cols-1 md:grid-cols-2 lg:grid-cols-[auto_auto_auto] w-max mx-auto gap-x-12 gap-y-24"}
     children))

(defui home [route]
  (let [ids [32932, 23643, 29915, 30895, 31472, 33411]]
    ($ movie-grid
       (for [id ids]
         ($ movie-title {:key id :id id})))))

(defui actor [{:keys [params]}]
  (let [{:keys [id]} params
        {:cast_members/keys [name] :keys [movie_ids]}
        (db/fetch-actor id)]
    ($ :div {:class "flex flex-col gap-15"}
      ($ :div {:class "flex flex-col gap-2"}
        ($ :div {:class "font-bold text-center"}
           "Starring")
        ($ :h1 {:class "text-center font-instrumentSerif text-6xl"}
           name))
      ($ movie-grid
         (for [id movie_ids]
           ($ movie-title {:key id :id id}))))))

(defui img [{:keys [thumbnail]}]
  ($ :img {:class "h-[435px] object-cover mb-4"
           :src thumbnail}))

(defui movie [{:keys [params]}]
  (let [{:keys [id]} params
        {:movies/keys [thumbnail title extract]
         :keys [cast_ids]}
        (db/fetch-movie id)]
    ($ :div {:class "p-12 items-center flex flex-col gap-y-12 lg:items-start lg:w-5xl lg:mx-auto lg:flex-row lg:gap-x-12"}
      ($ :div {:class "w-[296px] flex-none flex flex-col gap-y-2"}
         ($ img {:thumbnail thumbnail})
         ($ fav-form {:id id}))
      ($ :div {:class "flex-1 flex flex-col gap-y-8"}
         ($ :h1 {:class "font-instrumentSerif leading-[125%] text-6xl"}
            title)
         ($ :p extract)
         ($ :div {:class "flex flex-col gap-y-2"}
           ($ :div {:class "font-bold text-xl"} "Cast")
           ($ :div
              (->>
                (for [id cast_ids]
                  ($ :span {:key id}
                     ($ actor-link {:id id})))
                (interpose ($ :span.mx-1 "•")))))))))