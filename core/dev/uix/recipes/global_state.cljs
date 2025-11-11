(ns uix.recipes.global-state
  (:require [uix.core :refer [defui $] :as uix]
            [uix.re-frame :as urf]
            [re-frame.core :as rf]))

;; This recipe shows how UIx apps can architect global data store
;; and effects handling using re-frame and Hooks API.

;; Subscriptions
(rf/reg-sub :db/repos
  (fn [db]
    (:repos db)))

(rf/reg-sub :repos/value
  :<- [:db/repos]
  (fn [repos]
    (:value repos)))

(rf/reg-sub :repos/loading?
  :<- [:db/repos]
  (fn [repos]
    (:loading? repos)))

(rf/reg-sub :repos/error
  :<- [:db/repos]
  (fn [repos]
    (:error repos)))

(rf/reg-sub :repos/items
  :<- [:db/repos]
  (fn [repos]
    (:items repos)))

(rf/reg-sub :repos/count
  :<- [:repos/items]
  (fn [items]
    (count items)))

(rf/reg-sub :repos/nth-item
  :<- [:repos/items]
  (fn [items [_ idx]]
    (when (seq items)
      (nth items idx))))

;; Event handlers
(rf/reg-event-db :db/init
  (fn [_ _]
    {:repos {:value ""
             :items []
             :loading? false
             :error nil}}))

(rf/reg-event-db :set-value
  (fn [db [_ value]]
    (assoc-in db [:repos :value] value)))

(rf/reg-event-fx :fetch-repos
  (fn [{:keys [db]} [_ uname]]
    {:db (assoc-in db [:repos :loading?] true)
     :http {:url (str "https://api.github.com/users/" uname "/repos")
            :on-ok :fetch-repos-ok
            :on-failed :fetch-repos-failed}}))

(rf/reg-event-db :fetch-repos-ok
  (fn [db [_ repos]]
    (let [repos (vec repos)]
      (update db :repos assoc :items repos :loading? false :error nil))))

(rf/reg-event-db :fetch-repos-failed
  (fn [db [_ error]]
    (update db :repos assoc :loading? false :error error)))


;; Effect handlers
(rf/reg-fx :http
  (fn [{:keys [url on-ok on-failed]}]
    (-> (js/fetch url)
        (.then #(if (.-ok %)
                  (.json %)
                  (rf/dispatch [on-failed %])))
        (.then #(js->clj % :keywordize-keys true))
        (.then #(rf/dispatch [on-ok %])))))

;; UI components

(defui repo-item [{:keys [idx]}]
  (let [{:keys [name description]} (urf/use-subscribe [:repos/nth-item idx])
        [open? set-open] (uix/use-state false)]
    ($ :div {:on-click #(set-open not)
             :style {:padding 8
                     :margin "8px 0"
                     :border-radius 5
                     :background-color "#fff"
                     :box-shadow "0 0 12px rgba(0,0,0,0.1)"
                     :cursor :pointer}}
       ($ :div {:style {:font-size "16px"}}
          name)
       (when open?
         ($ :div {:style {:margin "8px 0 0"}}
            description)))))

(defui form []
  (let [uname (urf/use-subscribe [:repos/value])]
    ($ :form {:on-submit (fn [e]
                           (.preventDefault e)
                           (rf/dispatch [:fetch-repos uname]))}
       ($ :input {:value uname
                  :placeholder "GitHub username"
                  :on-change #(rf/dispatch [:set-value (.. % -target -value)])})
       ($ :button "Fetch repos"))))

(defui repos-list []
  (let [repos-count (urf/use-subscribe [:repos/count])]
    (when (pos? repos-count)
      ($ :div {:style {:width 240
                       :height 400
                       :overflow-y :auto}}
         (for [idx (range repos-count)]
           ($ repo-item {:key idx :idx idx}))))))

(defui recipe []
  (let [loading? (urf/use-subscribe [:repos/loading?])
        error (urf/use-subscribe [:repos/error])]
    ($ :<>
       ($ form)
       (when loading?
         ($ :div "Loading repos..."))
       (when error
         ($ :div {:style {:color :red}}
            (.-message error)))
       ($ repos-list))))

;; Init database
(defonce init-db
  (rf/dispatch-sync [:db/init]))
