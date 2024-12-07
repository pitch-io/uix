(ns example.core
  (:require [uix.dom.server :as dom.server]
            [uix.core :as uix :refer [defui $]]
            [org.httpkit.server :as server]
            [compojure.core :refer [defroutes GET]]
            [compojure.route :as route]
            [example.ui :as ui]))

(defn page [{:keys [children]}]
  ($ :html
     ($ :head
        ($ :title "Hello, world!"))
     ($ :body
        ($ :#root
           children)
        ($ :script {:src "/main.js"}))))

(defn handler [request]
  (server/as-channel request
    {:on-open (fn [ch]
                (dom.server/render-to-stream ($ page ($ ui/app))
                  {:on-chunk
                   (fn [chunk]
                     (server/send! ch chunk false))
                   :on-done
                   (fn []
                     (server/close ch))}))}))

(defroutes server-routes*
  (GET "/" req
    (handler req))
  (route/files "/" {:root "out"})
  (route/not-found "<p>Page not found.</p>"))

(defn start-server []
  (server/run-server #'server-routes* {:port 9090}))

(defn -main [& args]
  (start-server)
  (println "Server started on http://localhost:9090"))

(comment
  (def stop-server (start-server))
  (stop-server))