(ns uix.examples
  (:require ["@tanstack/react-router" :as rr]
            [cljs-bean.core :as bean]
            [cljs.spec.alpha :as s]
            [uix.core :as uix :refer [$ defui]]
            [uix.dom]))

(defn use-location []
  (bean/->clj (rr/useLocation)))

(defn use-params []
  (bean/->clj (rr/useParams #js {})))

(defn use-loader-data []
  (rr/useLoaderData #js {}))

(def route->name
  {"/" "new"
   "/askstories" "ask"
   "/showstories" "show"
   "/jobstories" "job"
   "/topstories" "top"
   "/beststories" "best"})

(defui root-layout []
  (let [current-path (:pathname (use-location))]
    ($ :div.flex.flex-col.items-center
      ($ :ul.flex.gap-2.text-sm.py-1.font-medium
        (for [[path title] route->name]
             ($ :li {:key path}
                ($ rr/Link
                   {:to path
                    :class-name (if (= path current-path)
                                  "text-emerald-500"
                                  "text-stone-800")}
                   title))))
      ($ :div.max-w-128
        ($ rr/Outlet)))))

(defonce dom-parser
  (js/DOMParser.))

(defn unescape-text [s]
  (.-textContent (.-documentElement (.parseFromString dom-parser s "text/html"))))

(s/def :link/href string?)

(s/def ::link
  (s/keys :req-un [:link/href]))

(defui link [{:keys [href children]}]
  {:props [::link]}
  ($ :a.text-sm.text-emerald-50.mb-1.block.hover:underline
     {:href href
      :target "_blank"}
     children))

(s/def :story/by string?)
(s/def :story/score number?)
(s/def :story/time number?)
(s/def :story/title string?)
(s/def :story/url string?)
(s/def :story/kids
  (s/coll-of number? :min-count 0))

(s/def :story/data
  (s/keys :req-un [:story/by :story/score :story/time :story/title]
          :opt-un [:story/url
                   :story/kids]))

(s/def ::story
  (s/keys :req-un [:story/data]))

(defui story [{:keys [data]}]
  {:props [::story]}
  (let [{:keys [by score time title url kids]} data]
    ($ :div.text-stone-800.px-4.py-2.bg-emerald-600.border-b.border-emerald-700.hover:bg-emerald-700
       ($ link {:href url}
          title)
       ($ :div.text-xs.flex.gap-2
         ($ :div "by "
            ($ :span.font-medium by))
         " | "
         ($ :div score)
         " | "
         ($ :div
            (.toLocaleString (js/Date. (* 1e3 time))))
         " | "
         ($ rr/Link {:to (str "/item/" (:id data))
                     :class-name "hover:underline"}
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
               (.toLocaleString (js/Date. (* 1e3 time))))
            ($ :<>
              ($ :div "by "
                 ($ :span.font-medium by))
              " | "
              ($ :div
                 (.toLocaleString (js/Date. (* 1e3 time))))))))))

(defui stories []
  (let [data (use-loader-data)]
    (for [d data]
      ($ story {:key (:id d) :data d}))))

(defui item []
  (let [{:keys [kids]} (use-loader-data)]
    (for [d kids]
      ($ item-comment {:key (:id d) :data d}))))

(defn fetch [url]
  (-> (js/fetch url)
      (.then #(.json %))
      (.then #(js->clj % :keywordize-keys true))))

(defn fetch-story+ [id]
  (fetch (str "https://hacker-news.firebaseio.com/v0/item/" id ".json")))

(defn fetch-stories+ [props]
  (let [pathname (-> props :location :pathname)
        pathname (if (= pathname "/")
                   "/newstories"
                   pathname)]
    (-> (fetch (str "https://hacker-news.firebaseio.com/v0/" pathname ".json"))
        (.then #(js/Promise.all (map fetch-story+ (take 10 %)))))))

(defn fetch-item+ [id]
  (-> (fetch-story+ id)
      (.then #(if-let [kids (:kids %)]
                (.then (js/Promise.all (map fetch-item+ kids))
                       (fn [kids]
                         (assoc % :kids kids)))
               %))))

(def routes
  {:layout root-layout
   :routes [{:path "/"
             :component #($ stories {:type :new})
             :loader fetch-stories+}
            {:path "/askstories"
             :component #($ stories {:type :ask})
             :loader fetch-stories+}
            {:path "/showstories"
             :component #($ stories {:type :show})
             :loader fetch-stories+}
            {:path "/jobstories"
             :component #($ stories {:type :job})
             :loader fetch-stories+}
            {:path "/topstories"
             :component #($ stories {:type :top})
             :loader fetch-stories+}
            {:path "/beststories"
             :component #($ stories {:type :best})
             :loader fetch-stories+}
            {:path "/item/$id"
             :component #($ item)
             :loader #(-> % :params :id fetch-item+)}]})

(defn create-router [{:keys [layout routes]}]
  (let [root (rr/createRootRoute #js {:component layout})
        routes (for [{:keys [path component loader]} routes]
                 (rr/createRoute #js {:getParentRoute (constantly root)
                                      :path path
                                      :loader #(loader (bean/->clj %))
                                      :component component}))
        route-tree (.addChildren root (into-array routes))]
    (rr/createRouter #js {:routeTree route-tree})))

(defn init []
  (let [root (uix.dom/create-root (js/document.getElementById "root"))]
    (uix.dom/render-root
      ($ uix/strict-mode
         ($ rr/RouterProvider {:router (create-router routes)}))
      root)))
