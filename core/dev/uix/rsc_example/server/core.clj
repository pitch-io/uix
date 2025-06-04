(ns uix.rsc-example.server.core
  (:require [uix.core :refer [defui $] :as uix]
            [uix.rsc :as rsc]
            [org.httpkit.server :as server]
            [compojure.core :refer [defroutes GET POST]]
            [compojure.route :as route]
            [ring.util.response :as resp]
            [ring.middleware.params :as rmp]
            [ring.middleware.multipart-params :as rmmp]
            [ring.middleware.cookies :as rmc]
            [reitit.core :as r]
            [reitit.ring :as rer]
            [uix.rsc-example.server.root :as server.root]
            [uix.rsc-example.routes :refer [routes]]
            [uix.rsc-example.server.db :as db])
  (:import (java.io PipedInputStream PipedOutputStream)
           (java.nio ByteBuffer)
           (java.security MessageDigest)
           (java.util.zip GZIPOutputStream))
  (:gen-class))

(defn sha-256 [input]
  (let [digest (MessageDigest/getInstance "SHA-256")
        bytes (.digest digest (.getBytes input "UTF-8"))]
    (apply str (map #(format "%02x" (bit-and % 0xff)) bytes))))

;; use persistent LRU cache in prod
(defonce session-db (atom {}))

(def actions-context
  {:store-bound (fn [args]
                  (let [id (sha-256 (str (hash args)))]
                    (swap! session-db assoc id args)
                    id))
   :get-bound #(get @session-db %)})

(def router
  (rer/router routes))

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

(defn get-session [request]
  (get-in request [:cookies "_session" :value]))

(defn with-session [request resp]
  (if (contains? @db/sessions (get-session request))
    resp
    (let [id (str (random-uuid))]
      (swap! db/sessions conj id)
      (assoc-in resp [:headers "set-cookie"] [(str "_session=" id "; Max-Age=86400")]))))

(defn with-gzip [request ch content-type]
  (let [pipe-out (PipedOutputStream.)
        pipe-in (PipedInputStream. pipe-out 16384)
        gzip (GZIPOutputStream. pipe-out true)]
    (server/send! ch (with-session request
                       {:status 200
                        :headers {"Content-Type" content-type
                                  "Content-Encoding" "gzip"
                                  #_#_"Cache-Control" "max-age=10"}})
                  false)
    (chunk-gzip pipe-in ch)
    (fn [^String chunk]
      (if (= chunk :done)
        (.close gzip)
        (do (.write gzip (.getBytes chunk "UTF-8"))
            (.flush gzip))))))

(def error-boundary
  (uix/create-error-boundary
    {:derive-error-state (fn [error] {:error error})
     :did-catch (fn [error info]
                  (prn error))}
    (fn [[state] {:keys [children]}]
      (if-let [error (:error state)]
        ($ server.root/html-page
           ($ :h1.text-5xl.text-center "Something went wrong"))
        children))))

(defn render-response [{:keys [content-type result]} render-fn request]
  (when-let [route (r/match-by-path router (:uri request))]
    (server/as-channel request
      {:on-open (fn [ch]         ;; use compression on reverse proxy in prod
                  (let [on-chunk (with-gzip request ch content-type)]
                    (render-fn
                      ($ error-boundary ($ server.root/page {:route route}))
                      {:on-chunk on-chunk :result result})))})))

(defn rsc-handler [request & {:keys [result]}]
  (render-response
    {:content-type "text/x-component; charset=utf-8"
     :result result}
    rsc/render-to-flight-stream
    request))

(defn html-handler [request]
  (render-response
    {:content-type "text/html; charset=utf-8"}
    rsc/render-to-html-stream
    request))

(defn rsc-action-handler [request]
  (try
    (rsc/handle-action request)
    ;; todo: route invalidation
    (rsc-handler request :result :done)
    (catch Exception e
      ;; todo: proper error response
      (-> (resp/bad-request (ex-message e))
          (resp/header "Content-Type" "text/x-component; charset=utf-8")))))

(defn html-action-handler [request]
  (try
    (rsc/handle-action request)
    (html-handler request)
    (catch Exception e
      ;; todo: proper error response
      (-> (resp/bad-request (ex-message e))
          (resp/header "Content-Type" "text/html")))))

(defn handle-action [req]
  (if (contains? (:params req) "_rsc")
    (rsc-action-handler req)
    (html-action-handler req)))

(defn handle-route [req]
  (if (contains? (:params req) "_rsc")
    (rsc-handler req)
    (html-handler req)))

(defroutes server-routes
  (route/files "/" {:root "./"})
  (GET "/*" req
    (binding [db/*sid* (get-session req)
              rsc/*bound-cache* actions-context]
      (handle-route req)))
  (POST "/*" req
    (binding [db/*sid* (get-session req)
              rsc/*bound-cache* actions-context]
      (handle-action req)))
  (resp/not-found "404"))

(defn wrap-rsc [handler]
  (-> handler
      (rmc/wrap-cookies)
      (rmmp/wrap-multipart-params)
      (rmp/wrap-params)))

(def handler
  (-> #'server-routes
      (wrap-rsc)))

(defn start-server []
  (println "Server is listening at http://localhost:8080")
  (server/run-server #'handler {:port 8080}))

(defn -main [& [training-run]]
  (let [stop (start-server)]
    (when training-run
      (slurp "http://localhost:8080")
      (slurp "http://localhost:8080/?_rsc")
      (stop))))

(comment
  (def stop-server (start-server))
  (stop-server))