(ns uix.rsc-example.server.root
  (:require [uix.core :refer [defui $] :as uix]
            [uix.rsc :as rsc]
            [uix.rsc-example.routes :refer [routes]]
            [uix.rsc-example.server.db :as db]))

(defui root [{:keys [route]}]
  (let [{:keys [path path-params data]} route
        {:keys [component]} data]
    ($ :div.flex.flex-col.items-center
      ($ :ul.flex.gap-2.text-sm.py-1.font-medium
        (for [[route-path {:keys [title]}] routes
              :when title]
          ($ :li {:key route-path}
             ($ rsc/link
                {:href route-path
                 :class (if (= route-path path)
                          "text-emerald-500"
                          "text-stone-800")}
                title))))
      ($ :div.max-w-128
         ($ component {:path path :params path-params})))))

(defui header []
  ($ :div {:class "text-5xl text-center p-12 bg-black text-white dark:bg-white dark:text-black font-boldonse mb-22"}
     ($ rsc/link
        {:href "/"}
        "RSC Movies")))

(defui fav-link [{:keys [id]}]
  ;; todo: fix batching
  (let [{:movies/keys [thumbnail] :as m} (first (db/fetch-movies [id]))]
    ($ rsc/link {:href (str "/movie/" id)}
      ($ :img {:class "w-[112px] h-[162px] object-cover"
               :src thumbnail}))))

(defui favourites []
  (let [favorites (db/favs db/*sid*)]
    (when (seq favorites)
      ($ :div {:class "fixed bottom-0 left-0 right-0 bg-black/66 backdrop-blur-sm border-t-black/10 p-4"}
         ($ :div {:class "overflow-x-auto snap-x snap-mandatory"}
           ($ :div {:class "flex flex-nowrap gap-x-2"}
              (for [{:favorites/keys [movie_id]} favorites]
                ($ :div {:class "flex-shrink-0 snap-start"
                         :key movie_id}
                   ($ fav-link {:id movie_id})))))))))

(defui html-page [{:keys [children]}]
  ($ :html {:lang "en"}
    ($ :head
      ($ :meta {:charset "utf-8"})
      ($ :meta {:name "viewport" :content "width=device-width, initial-scale=1"})
      ($ :meta {:name "description" :content "UIx RSC Demo page"})
      ($ :title "UIx RSC Demo")
      ($ :link {:rel :preconnect :href "https://fonts.googleapis.com"})
      ($ :link {:rel :preconnect :href "https://fonts.gstatic.com"})
      ;; todo: this url breaks hydration
      #_($ :link {:rel :stylesheet :href "https://fonts.googleapis.com/css2?family=Boldonse&family=Instrument+Sans:ital,wght@0,400..700;1,400..700&family=Instrument+Serif&display=swap"})
      ($ :link {:rel :stylesheet :href "/rsc-out/main.css"})
      ($ :script {:src "/rsc-out/rsc.js" :async true}))
    ($ :body.font-instrumentSans.pb-56
      ($ header)
      children
      ($ favourites))))

(defui page [{:keys [route]}]
  (let [{:keys [path path-params data]} route
        {:keys [component]} data]
    ($ html-page
       ($ component {:path path :params path-params}))))