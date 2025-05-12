(ns uix.rsc-example.server.core
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [uix.core :refer [defui $] :as uix]
            [uix.rsc :as rsc]
            [org.httpkit.server :as server]
            [compojure.core :refer [defroutes GET POST]]
            [compojure.route :as route]
            [ring.util.response :as resp]
            [ring.middleware.params :as rmp]
            [reitit.core :as r]
            [uix.rsc-example.server.root :as server.root]
            [uix.rsc-example.routes :refer [routes]])
  (:import (java.io PushbackReader PipedInputStream PipedOutputStream)
           (java.nio ByteBuffer)
           (java.util.zip GZIPOutputStream))
  (:gen-class))

(defn read-end-stream [body]
  (with-open [reader (io/reader body)]
    (edn/read (PushbackReader. reader))))

(def router
  (r/router routes))

(defn chunk-gzip [pipe-in ch]
  (future
    (let [buf (byte-array 8192)]
      (loop []
        (let [n (.read pipe-in buf)]
          (cond
            (pos? n)
            (do (server/send! ch (ByteBuffer/wrap buf 0 n) false)
                (recur))

            (= n -1)
            (server/close ch)))))))

(defn with-gzip [ch]
  (let [pipe-out (PipedOutputStream.)
        pipe-in (PipedInputStream. pipe-out)
        gzip (GZIPOutputStream. pipe-out true)]
    (server/send! ch {:status 200
                      :headers {"Content-Type" "text/x-component; charset=utf-8"
                                "Content-Encoding" "gzip"
                                #_#_"Cache-Control" "max-age=10"}}
                  false)
    (chunk-gzip pipe-in ch)
    (fn [chunk]
      (if (= chunk :done)
        (.close gzip)
        (do (.write gzip (.getBytes chunk "UTF-8"))
            (.flush gzip))))))

(defn rsc-handler [request]
  (let [path (get (:query-params request) "path")
        route (r/match-by-path router path)]
    ;; request -> route -> react flight rows -> response stream
    (server/as-channel request
      {:on-open (fn [ch]         ;; use compression on reverse proxy in prod
                  (let [on-chunk (with-gzip ch)]
                    (rsc/render-to-flight-stream ($ server.root/page {:route route})
                        {:on-chunk on-chunk})))})))

(defn html-handler [request]
  (when-let [route (r/match-by-path router (:uri request))]
    (server/as-channel request
      {:on-open (fn [ch]
                  (let [on-chunk (fn [chunk]
                                   (if (= chunk :done)
                                     (server/close ch)
                                     (server/send! ch chunk false)))
                        on-html (fn [html]
                                  (server/send! ch html false))]
                    (rsc/render-to-html-stream ($ server.root/page {:route route})
                                               {:on-chunk on-chunk
                                                :on-html on-html})))})))

(defn handle-server-action [body]
  (try
    (-> (rsc/handle-action (read-end-stream body))
        str
        (resp/response)
        (resp/header "Content-Type" "text/edn"))
    (catch Exception e
      (-> (resp/bad-request (ex-message e))
          (resp/header "Content-Type" "text/edn")))))

(defroutes server-routes
  ;; react flight payload endpoint
  (GET "/_rsc" req
    (rsc-handler req))
  ;; server actions endpoint
  (POST "/api" {body :body}
    (handle-server-action body))
  ;; static assets
  (route/files "/" {:root "./"})
  ;; generating HTML for initial load of a route
  (GET "/*" req
    (html-handler req)
    #_(-> (resp/response "<link rel=\"prefetch\" href=\"/rsc?path=/\" /><link rel=\"stylesheet\" href=\"/rsc-out/main.css\"><div id=root></div><script src=\"/rsc-out/rsc.js\"></script>\n")
          (resp/header "Content-Type" "text/html")))
  (resp/not-found "404"))

(def handler
  (-> #'server-routes
      (rmp/wrap-params)))

(defn start-server []
  (server/run-server #'handler {:port 8080})
  (println "Server is listening at http://localhost:8080"))

(defn -main [& args]
  (start-server))

(comment
  (def stop-server (start-server))
  (stop-server))