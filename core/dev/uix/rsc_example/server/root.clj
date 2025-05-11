(ns uix.rsc-example.server.root
  (:require [uix.core :refer [defui $] :as uix]
            [uix.rsc :as rsc]
            [uix.rsc-example.routes :refer [routes]]))

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

(defui page [{:keys [route]}]
  ($ :html
    ($ :head
      ($ :link {:rel :stylesheet :href "/rsc-out/main.css"}))
    ($ :body
      ($ root {:route route})
      ($ :script {:src "/rsc-out/rsc.js"}))))