(ns uix.rsc
  #?(:cljs (:require-macros [uix.rsc]))
  (:require #?@(:cljs [["@kentcdodds/tmp-react-server-dom-esm/client" :as rsd-client]
                       [clojure.edn :as edn]
                       [reitit.core :as r]
                       [reitit.frontend :as rf]
                       [reitit.frontend.easy :as rfe]])
            #?@(:clj [[uix.dom.server.flight :as server.flight]
                      [uix.lib :as lib]])
            [uix.core :refer [defui $] :as uix]))

;; Router =============

#?(:cljs
   (defonce router-context (uix/create-context)))

#?(:cljs
   (defn use-route []
     (:route (uix/use router-context))))

#?(:cljs
   (defn use-navigate []
     (:navigate (uix/use router-context))))

#?(:cljs
   (defonce server-actions-endpoint- (atom nil)))

#?(:cljs
   (defui router
     ;; link pressed -> url change -> request server render -> update DOM
     [{:keys [routes rsc-endpoint server-actions-endpoint]}]
     (reset! server-actions-endpoint- server-actions-endpoint)
     (let [create-from-fetch (uix/use-callback
                               #(rsd-client/createFromFetch
                                  (js/fetch rsc-endpoint
                                    #js {:method "POST"
                                         :body (str {:route %})
                                         :headers #js {:content-type "text/edn"}})
                                  #js {:moduleBaseURL "/"})
                               [rsc-endpoint])
           [children set-children] (uix/use-state nil)
           navigate (uix/use-callback
                      #(-> (create-from-fetch {:path %})
                           (.then set-children))
                      [create-from-fetch])
           router (uix/use-memo #(rf/router routes)
                                [routes])
           [route set-route] (uix/use-state #(r/match-by-path router js/location.pathname))
           path (:path route)
           _ (uix/use-memo
               #(rfe/start! router set-route {:use-fragment false})
               [router])
           _ (uix/use-memo #(navigate path) [navigate path])]
       ($ router-context {:value {:route route :navigate navigate}}
          children))))

(defui link [props]
  #?(:clj ($ :a props)
     :cljs
     ;; wip
      (let [navigate (use-navigate)]
        (prn props)
        ($ :a (update props :on-click
                      (fn [handler]
                        (fn [e]
                          (when handler (handler e)))))))))

#?(:clj
   (defmacro defroutes [name routes]
     (if (lib/cljs-env? &env)
       ;; todo: allow client routes
       `(def ~name ~(->> routes
                         (mapv (fn [[path opts]]
                                 [path (dissoc opts :component)]))))

       `(def ~name ~routes))))

;; Server Actions =============

#?(:cljs
   (defn create-server-ref [id]
     (rsd-client/createServerReference id
       (fn [id args]
         (if-let [endpoint @server-actions-endpoint-]
           (-> (js/fetch endpoint
                 #js {:method "POST"
                      :body (str {:action id :args (vec args)})
                      :headers #js {:content-type "text/edn"}})
               (.then #(.text %))
               (.then #(edn/read-string %)))
           (js/Promise.reject "server-action-fn is not set"))))))

#?(:clj
   (defmacro defaction
     "creates server action
     in clj – executable server function behind api endpoint
     in cljs – client-side function that hits the api endpoint"
     [name args & body]
     (if (lib/cljs-env? &env)
       (let [id (-> (str (-> &env :ns :name) "/" name) symbol str)]
         `(let [handler# (create-server-ref ~id)]
            (defn ~name [& args#]
              (apply handler# args#))))
       (let [id (-> (str *ns* "/" name) symbol str)
             name (vary-meta name assoc ::action-id id)]
         `(defn ~name ~args ~@body)))))

#?(:clj
   (defn- all-actions []
     (->> (all-ns)
          (mapcat (comp vals ns-publics))
          (keep #(when-let [id (-> % meta ::action-id)]
                   [id @%]))
          (into {}))))

#?(:clj
   (defn handle-action
     "client payload -> executes server action"
     [{:keys [action args]}]
     (if-let [handler (get (all-actions) action)]
       (apply handler args)
       (throw (IllegalArgumentException. (str "Unhandled action " action " " args))))))

;; Rendering =============

#?(:clj
   (defn render-to-flight-stream
     "Renders UIx components into React Flight payload"
     [src {:keys [on-chunk] :as opts}]
     (server.flight/render-to-flight-stream src opts)))