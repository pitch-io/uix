(ns uix.rsc-example.server.core
  (:require [uix.core :refer [defui $] :as uix]
            [uix.rsc :as rsc]
            [org.httpkit.server :as server]
            [compojure.core :refer [defroutes GET POST]]
            [compojure.route :as route]
            [ring.util.response :as resp]
            [ring.middleware.params :as rmp]
            [ring.middleware.multipart-params :as rmmp]
            [reitit.core :as r]
            [uix.rsc-example.server.root :as server.root]
            [uix.rsc-example.routes :refer [routes]])
  (:import (java.io PipedInputStream PipedOutputStream)
           (java.nio ByteBuffer)
           (java.util.zip GZIPOutputStream))
  (:gen-class))

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
        pipe-in (PipedInputStream. pipe-out 16384)
        gzip (GZIPOutputStream. pipe-out true)]
    (server/send! ch {:status 200
                      :headers {"Content-Type" "text/x-component; charset=utf-8"
                                "Content-Encoding" "gzip"
                                #_#_"Cache-Control" "max-age=10"}}
                  false)
    (chunk-gzip pipe-in ch)
    (fn [^String chunk]
      (if (= chunk :done)
        (.close gzip)
        (do (.write gzip (.getBytes chunk "UTF-8"))
            (.flush gzip))))))

(defn rsc-handler [request & {:keys [result]}]
  (let [path (get (:query-params request) "path")
        route (r/match-by-path router path)]
    ;; request -> route -> react flight rows -> response stream
    (server/as-channel request
      {:on-open (fn [ch]         ;; use compression on reverse proxy in prod
                  (let [on-chunk (with-gzip ch)]
                    (rsc/render-to-flight-stream ($ server.root/page {:route route})
                      {:on-chunk on-chunk :result result})))})))

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

(defn action-handler [request]
  (try
    (rsc/handle-action request)
    ;; todo: route invalidation
    (rsc-handler request :result :done)
    (catch Exception e
      ;; todo: proper error response
      (-> (resp/bad-request (ex-message e))
          (resp/header "Content-Type" "text/edn")))))

(defroutes server-routes
  ;; react flight payload endpoint
  (GET "/_rsc" req
    (rsc-handler req))
  ;; server actions endpoint
  (POST "/_rsc" req
    (action-handler req))
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
      (rmmp/wrap-multipart-params)
      (rmp/wrap-params)))

(defn start-server []
  (println "Server is listening at http://localhost:8080")
  (server/run-server #'handler {:port 8080}))

(defn -main [& [training-run]]
  (let [stop (start-server)]
    (when training-run
      (slurp "http://localhost:8080")
      (slurp "http://localhost:8080/_rsc?path=/")
      (stop))))

(comment
  (def stop-server (start-server))
  (stop-server))