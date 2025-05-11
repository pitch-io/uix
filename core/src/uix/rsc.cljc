(ns uix.rsc
  #?(:cljs (:require-macros [uix.rsc]))
  (:require #?@(:cljs [["@kentcdodds/tmp-react-server-dom-esm/client" :as rsd-client]
                       [clojure.edn :as edn]
                       [clojure.walk :as walk]
                       [reitit.core :as r]
                       [reitit.frontend :as rf]
                       [reitit.frontend.easy :as rfe]])
            #?@(:clj [[cheshire.core :as json]
                      [uix.dom.server :as dom.server]
                      [uix.dom.server.flight :as server.flight]
                      [uix.lib :as lib]])
            [uix.core :refer [defui $] :as uix]))

#?(:cljs
   (defn- create-rsc-client-container [uix-comp]
     (fn [props]
       (let [rsc-props (aget props "rsc/props")
             rsc-refs (aget props "rsc/refs")
             props (uix/use-memo
                     #(walk/postwalk
                        (fn [form]
                          ;; todo: maybe use data readers?
                          (cond
                            (and (string? form) (.startsWith form "$F:"))
                            (let [action-id (.replace form "$F:" "")
                                  action (aget (.-RSC_MODULES js/window) action-id)]
                              (when ^boolean goog/DEBUG
                                (when (nil? action)
                                  (js/console.error "action " action-id " is not registered")))
                              action)

                            (and (string? form)
                                 (.startsWith form "$")
                                 rsc-refs)
                            (let [ref (aget rsc-refs form)]
                              (when ^boolean goog/DEBUG
                                (when (nil? ref)
                                  (js/console.error "server reference " ref " is not registered")))
                              ref)

                            :else form))
                        (edn/read-string rsc-props))
                     [rsc-props rsc-refs])]
         (uix.core/$ uix-comp props)))))

#?(:cljs
   (defn ^{:jsdoc ["@nosideeffects"]} register-rsc-client! [str-name ref]
     (js* "window.RSC_MODULES ||= {}")
     (if (.-uix-component? ^js ref)
       (let [comp (create-rsc-client-container ref)]
         (set! (.-displayName comp) (str "rsc(" str-name ")"))
         (aset (.-RSC_MODULES js/window) str-name comp))
       (aset (.-RSC_MODULES js/window) str-name ref))))

;; Router =============

#?(:cljs
   (defonce router-context (uix/create-context)))

#?(:cljs
   (defn use-route []
     (:route (uix/use router-context))))

#?(:cljs
   (do
     (defonce ^:private rsc-cache (atom {}))
     (defonce ^:private server-actions-endpoint- (atom nil))
     (defonce ^:private rsc-endpoint- (atom nil))
     (defonce ^:private router- (atom nil))))

#?(:cljs
   (defn- create-initial-flight-stream []
     (let [controller (atom nil)
           encoder (js/TextEncoder.)]
       (js/document.addEventListener "DOMContentLoaded" #(some-> @controller (.close)))
       (rsd-client/createFromReadableStream
         (js/ReadableStream.
           #js {:start (fn [ctrl]
                         (reset! controller ctrl)
                         (let [handle-chunk #(.enqueue ctrl (.encode encoder %))]
                           (js* "window.__FLIGHT_DATA ||= []")
                           (js* "window.__FLIGHT_DATA.forEach(~{})" handle-chunk)
                           (js* "window.__FLIGHT_DATA.push = ~{}" handle-chunk)))})
         #js {:moduleBaseURL "/"}))))

#?(:cljs
   (defn- create-from-fetch [route]
     (rsd-client/createFromFetch
       (js/fetch @rsc-endpoint-
         #js {:method "POST"
              :body (str {:route route})
              :headers #js {:content-type "text/edn"}})
       #js {:moduleBaseURL "/"})))

#?(:cljs
   (defonce ^:private init-rsc (create-initial-flight-stream)))

#?(:cljs
   (defui router
     ;; link pressed -> url change -> request server render -> update DOM
     [{:keys [routes rsc-endpoint server-actions-endpoint]}]
     (reset! rsc-endpoint- rsc-endpoint)
     (reset! server-actions-endpoint- server-actions-endpoint)
     (let [initialized? (uix/use-ref false)
           router (uix/use-memo #(reset! router- (rf/router routes))
                                [routes])
           [route set-route] (uix/use-state #(r/match-by-path router js/location.pathname))
           [resource set-resource] (uix/use-state init-rsc)
           on-navigate (uix/use-effect-event
                         (fn [route]
                           (when @initialized?
                             (let [path (:path route)
                                   resource (or (@rsc-cache path)
                                                (create-from-fetch {:path path}))]
                               (swap! rsc-cache dissoc path)
                               (uix/start-transition #(set-resource resource))
                               (set-route route)))
                           (reset! initialized? true)))
           _ (uix/use-memo
               #(rfe/start! router on-navigate {:use-fragment false})
               [router])]
       ($ router-context {:value {:route route}}
         resource))))

#?(:cljs
   (defn- prefetch [href]
     (when (r/match-by-path @router- href)
       (when-not (@rsc-cache href)
         (swap! rsc-cache assoc href (create-from-fetch {:path href}))))))

(defui ^:client link [props]
  #?(:clj ($ :a props)
     :cljs
     ;; wip
      (let [wrap-handler (fn [handler f]
                           (fn [e]
                             (when handler (handler e))
                             (f e)))
            href (:href props)
            ref (uix/use-ref)]

        (uix/use-effect
          (fn []
            (let [node @ref
                  observer (js/IntersectionObserver.
                             (fn [entries]
                               (doseq [entry entries]
                                 (when (.-isIntersecting entry)
                                   (prefetch href)))))]
              (.observe observer node)
              #(.unobserve observer node)))
          [href])
        ($ :a (-> props
                  (assoc :ref ref)
                  (update :on-mouse-enter wrap-handler #(prefetch (:href props))))))))

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
              (apply handler# args#))
            (register-rsc-client! ~id ~name)))
       (let [id (-> (str *ns* "/" name) symbol str)
             name (vary-meta name assoc ::action-id id)]
         `(def ~name ~(with-meta `(fn ~args ~@body) {::action-id id}))))))

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

#?(:clj
   (defn render-to-html-stream
     [src {:keys [on-html on-chunk] :as opts}]
     (let [on-chunk #(if (= :done %)
                       (on-chunk :done)
                       (on-chunk (str "<script>(window.__FLIGHT_DATA ||=[]).push(" (json/generate-string %) ");</script>")))
           ast (server.flight/-unwrap src)]
       (on-html (dom.server/render-to-string ast))
       (render-to-flight-stream ast {:on-chunk on-chunk}))))