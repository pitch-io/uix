(ns uix.rsc-example.server.core
  (:require [cheshire.core :as json]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [uix.core :refer [defui $] :as uix]
            [uix.rsc :as rsc]
            [org.httpkit.server :as server]
            [compojure.core :refer [defroutes GET POST]]
            [compojure.route :as route]
            [ring.util.response :as resp]
            [reitit.core :as r]
            [uix.rsc-example.server.root :refer [root]]
            [uix.rsc-example.routes :refer [routes]])
  (:import (java.io PushbackReader))
  (:gen-class))

(defn read-end-stream [body]
  (with-open [reader (io/reader body)]
    (edn/read (PushbackReader. reader))))

(def router
  (r/router routes))

(defn rsc-handler [request]
  (let [{:keys [route]} (read-end-stream (:body request))
        route (r/match-by-path router (:path route))]
    ;; request -> route -> react flight rows -> response stream
    (server/as-channel request
      {:on-open (fn [ch]
                  (let [on-chunk (fn [chunk]
                                   (if (= chunk :done)
                                     (server/close ch)
                                     (server/send! ch chunk false)))]
                    (rsc/render-to-flight-stream ($ root {:route route})
                                                 {:on-chunk on-chunk})))})))

(defn html-handler [request]
  (when-let [route (r/match-by-path router (:uri request))]
    (server/as-channel request
      {:on-open (fn [ch]
                  (let [on-chunk (fn [chunk]
                                   (if (= chunk :done)
                                     (server/close ch)
                                     (server/send! ch (str "<script>(window.__FLIGHT_DATA ||=[]).push(" (json/generate-string chunk) ");</script>") false)))
                        on-html (fn [html]
                                  (server/send! ch "<link rel=\"stylesheet\" href=\"/rsc-out/main.css\"><div id=root>" false)
                                  (server/send! ch html false)
                                  (server/send! ch "</div><script src=\"/rsc-out/rsc.js\"></script>" false))]
                    (rsc/render-to-html-stream ($ root {:route route})
                                               {:on-chunk on-chunk
                                                :on-html on-html})))})))

(defroutes server-routes*
  ;; react flight payload endpoint
  (POST "/rsc" req
    (rsc-handler req))
  ;; server actions endpoint
  (POST "/api" {body :body}
    (try
      (-> (rsc/handle-action (read-end-stream body))
          str
          (resp/response)
          (resp/header "Content-Type" "text/edn"))
      (catch Exception e
        (-> (resp/bad-request (ex-message e))
            (resp/header "Content-Type" "text/edn")))))
  ;; static assets
  (route/files "/" {:root "./"})
  ;; always serving index.html instead of 404
  (GET "/*" req
    (html-handler req)
    ;; todo: render flight payload into html on initial load
    #_(-> (resp/file-response "index.html" {:root "./"})
          (resp/header "Content-Type" "text/html")))
  (resp/not-found "404"))

(defn start-server []
  (server/run-server #'server-routes* {:port 8080})
  (println "Server is listening at http://localhost:8080"))

(defn -main [& args]
  (start-server))

(comment
  (def stop-server (start-server))
  (stop-server))