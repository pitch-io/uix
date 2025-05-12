(ns uix.rsc
  #?(:cljs (:require-macros [uix.rsc]))
  (:require #?@(:cljs [["@kentcdodds/tmp-react-server-dom-esm/client" :as rsd-client]
                       [clojure.edn :as edn]
                       [clojure.walk :as walk]
                       [reitit.core :as r]
                       [reitit.frontend :as rf]
                       [reitit.frontend.easy :as rfe]])
            #?@(:clj [[cheshire.core :as json]
                      [clojure.core.async :as async]
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
   (defn register-rsc-client! [str-name ref]
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
   (defn- exec-server-action [id args]
     (if-let [endpoint @server-actions-endpoint-]
       (-> (js/fetch endpoint
                 #js {:method "POST"
                      :body (str {:action id :args (vec args)})
                      :headers #js {:content-type "text/edn"}})
           (.then #(.text %))
           (.then #(edn/read-string %)))
       (js/Promise.reject "server-action-fn is not set"))))

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
                           (js* "(window.__FLIGHT_DATA ||= []).forEach(~{})" handle-chunk)
                           (set! (.. js/window -__FLIGHT_DATA -push) handle-chunk)))})
         #js {:moduleBaseURL "/"
              :callServer exec-server-action}))))

#?(:cljs
   (defn- create-from-fetch [route & {:keys [priority]}]
     (rsd-client/createFromFetch
       (js/fetch (str @rsc-endpoint- "?path=" (:path route)) #js {:priority (or priority "auto")})
       #js {:moduleBaseURL "/"
            :callServer exec-server-action})))

#?(:cljs
   (defn- init-rsc [ssr-enabled]
     (if ssr-enabled
       (create-initial-flight-stream)
       (create-from-fetch {:path js/location.pathname}))))

#?(:cljs
   (defui router
     ;; link pressed -> url change -> request server render -> update DOM
     [{:keys [ssr-enabled routes rsc-endpoint server-actions-endpoint]}]
     (reset! rsc-endpoint- rsc-endpoint)
     (reset! server-actions-endpoint- server-actions-endpoint)
     (let [initialized? (uix/use-ref false)
           router (uix/use-memo #(reset! router- (rf/router routes))
                                [routes])
           [route set-route] (uix/use-state #(r/match-by-path router js/location.pathname))
           [resource set-resource] (uix/use-state #(init-rsc ssr-enabled))
           on-navigate (uix/use-effect-event
                         (fn [route]
                           (when @initialized?
                             (let [path (:path route)
                                   resource (or (@rsc-cache path)
                                                (create-from-fetch {:path path}))]
                               (swap! rsc-cache dissoc path)
                               (uix/start-transition
                                 (fn []
                                   (set-route route)
                                   (set-resource resource)))))
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
         (swap! rsc-cache assoc href (create-from-fetch {:path href} :priority "low"))))))

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
     (rsd-client/createServerReference id exec-server-action)))

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
   (defn- stream-suspended [sb on-chunk set-done & [f]]
     (async/go
       (loop [ch->to-id (->> @sb :suspended vals
                             (map (fn [[ch id to-id]] [ch [id to-id]]))
                             (into {}))]
         (if (seq ch->to-id)
           (let [[element c] (async/alts! (keys ch->to-id))
                 [id to-id] (ch->to-id c)]
             (->> (server.flight/-render element sb)
                  (server.flight/emit-row id)
                  on-chunk)
             (when f (f to-id element))
             (recur (dissoc ch->to-id c)))
           (set-done))))))

#?(:clj
   (defn render-to-flight-stream
     "Renders UIx components into React Flight payload"
     [src {:keys [on-chunk] :as opts}]
     (let [done-count (atom 0)
           set-done #(when (== 2 (swap! done-count inc))
                       (on-chunk :done))
           handle-chunk #(if (= :done %)
                           (set-done)
                           (on-chunk %))
           sb (server.flight/create-state)
           ast (server.flight/-unwrap src sb)]
       ;; streamed flight payload -> suspended flight chunks
       (server.flight/render-to-flight-stream ast {:on-chunk handle-chunk :sb sb})
       (stream-suspended sb on-chunk set-done))))

#?(:clj
   ;; todo: cleanup, streaming ssr should be a part of uix.dom.server
   (defn render-html-chunk [from-id to-id element *state sb]
     (dom.server/append! sb "<div hidden id='" from-id "'>")
     (dom.server/-render-html element *state sb)
     (dom.server/append! sb (str "</div><script>$RC('" to-id "', '" from-id "');</script>"))
     (str (.sb sb))))

#?(:clj
   (def suspense-cleanup-js
     "<script>
     $RC=function(b,c,e){c=document.getElementById(c);c.parentNode.removeChild(c);var a=document.getElementById(b);if(a){b=a.previousSibling;if(e)b.data=\"$!\",a.setAttribute(\"data-dgst\",e);else{e=b.parentNode;a=b.nextSibling;var f=0;do{if(a&&8===a.nodeType){var d=a.data;if(\"/$\"===d)if(0===f)break;else f--;else\"$\"!==d&&\"$?\"!==d&&\"$!\"!==d||f++}d=a.nextSibling;e.removeChild(a);a=d}while(a);for(;c.firstChild;)e.insertBefore(c.firstChild,a);b.data=\"$\"}b._reactRetry&&b._reactRetry()}};
     </script>"))

#?(:clj
   (defn render-to-html-stream
     [src {:keys [on-html on-chunk] :as opts}]
     (let [done-count (atom 0)
           set-done #(when (== 2 (swap! done-count inc))
                       (on-chunk :done))
           handle-chunk #(if (= :done %)
                           (set-done)
                           (on-chunk (str "<script>window.__FLIGHT_DATA.push(" (json/generate-string %) ");</script>")))
           sb (server.flight/create-state)
           ast (server.flight/-unwrap src sb)
           *state (volatile! :state/root)]
       ;; initial html -> streaming html helpers -> streamed flight payload -> suspended flight + html chunks
       (on-html (dom.server/render-to-string ast))
       (on-chunk suspense-cleanup-js)
       (on-chunk "<script>window.__FLIGHT_DATA ||= [];</script>")
       (server.flight/render-to-flight-stream ast {:on-chunk handle-chunk :sb sb})
       (stream-suspended sb handle-chunk set-done
                         (fn [to-id element]
                           (let [ssb (dom.server/make-static-builder)
                                 from-id (str (gensym "S:"))]
                             (on-chunk (render-html-chunk from-id to-id element *state ssb))))))))