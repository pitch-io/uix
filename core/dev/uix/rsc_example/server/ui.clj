(ns uix.rsc-example.server.ui
  (:require [uix.core :refer [defui $] :as uix]
            [uix.rsc :as rsc]
            [uix.rsc-example.actions :as actions]
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
  (Thread/sleep 1000)
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
          ;; todo: server comp type as prop
         ($ ui/vote-btn
            {:id id
             :score score
             :label ($ label)
             :on-vote (rsc/partial actions/vote id score)})
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