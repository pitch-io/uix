(ns example.ui
  #?(:cljs (:require-macros [example.ui :refer [suspend]]))
  (:require [uix.core :as uix :refer [defui $]]
            [uix.dom.server :as dom.server]
            #?@(:cljs [[uix.dom :as dom]])
            [cognitect.transit :as t]
            #?(:clj [clojure.data.json :as json]))
  #?(:clj (:import (java.io ByteArrayInputStream ByteArrayOutputStream))))

(defn write-transit [data]
  #?(:clj (let [out (ByteArrayOutputStream.)
                writer (t/writer out :json)]
            (t/write writer data)
            (str out))
     :cljs (t/write (t/writer :json) data)))

(defn read-transit [s]
  #?(:clj (let [in (ByteArrayInputStream. (.getBytes s))
                reader (t/reader in :json)]
            (t/read reader))
     :cljs (t/read (t/reader :json) s)))

(defn fake-fetch [delay data]
  ;; DB call, HTTP request, etc.
  #?(:clj
     (do (Thread/sleep delay)
         data)))

#?(:clj
    (defmacro suspend [& body]
      ;; dom.server/suspend is generic API
      ;; you have to provide your own implementation for de/serialization
      ;; + wrapping function should be a macro that provides code location
      ;; where `suspend` wrapper is called, to make sure that client can read embedded server data (needs more work)
      `(dom.server/suspend
         {:loc ~(meta &form)
          :write (comp json/write-str write-transit)
          :read #(some-> % read-transit)}
         ~@body)))

(defui button [{:keys [on-click]}]
  ($ :button {:on-click on-click}
     (:text (suspend (fake-fetch 2000 {:text "suspended button"})))))

(defui input []
  (let [initial-value (:value (suspend (fake-fetch 4000 {:value "< suspended value >"})))
        [value set-value] (uix/use-state initial-value)]
    ($ :input {:value value
               :on-change #(set-value (.. % -target -value))
               :style {:padding "4px 12px"}})))

(defui app []
  ($ :div {:style {:font "normal 16px sans-serif"
                   :display :flex
                   :flex-direction :column
                   :align-items :center
                   :gap 16}}
    ($ :h1 "Suspended!")
    ($ uix/suspense {:fallback ($ :div "Loading button...")}
      ($ button {:on-click #(prn "< suspended button >")}))
    ($ uix/suspense {:fallback ($ :div "Loading input...")}
      ($ input))
    ($ :button {:on-click #(prn "< static button >")}
       "static button")))

#?(:cljs
    (defn render []
      (dom/hydrate-root (js/document.getElementById "root") ($ app))))

#?(:cljs
   (defn ^:dev/after-load reload []
     (js/location.reload)))