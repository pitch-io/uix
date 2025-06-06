(ns uix.rsc
  #?(:clj (:refer-clojure :exclude [partial]))
  #?(:cljs (:require-macros [uix.rsc]))
  (:require #?@(:cljs [["@roman01la/react-server-dom-esm/client" :as rsd-client]
                       ["js.foresight" :as jsf]
                       [cljs-bean.core :as bean]
                       [clojure.edn :as edn]
                       [clojure.walk :as walk]
                       [clojure.string :as str]
                       [reitit.core :as r]
                       [reitit.frontend :as rf]
                       [reitit.frontend.easy :as rfe]])
            #?@(:clj [[cheshire.core :as json]
                      [clojure.core.async :as async]
                      [clojure.edn :as edn]
                      [clojure.java.io :as io]
                      [clojure.string :as str]
                      [uix.dom.server :as dom.server]
                      [uix.dom.server.flight :as server.flight]
                      [uix.lib :as lib]
                      [uix.rsc.loader :as loader]])
            [uix.core :refer [defui $] :as uix])
  #?(:clj (:import (java.io PushbackReader))))

#?(:cljs
   (defn- create-rsc-client-container [uix-comp]
     (fn [props]
       (let [rsc-props (aget props "rsc/props")
             rsc-refs (aget props "rsc/refs")
             children (aget props "children")
             props (uix/use-memo
                     #(let [{:uix/keys [context] :as edn-props} (edn/read-string rsc-props)
                            edn-props (walk/postwalk
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
                                        (dissoc edn-props :uix/context))]
                        (if (and context children)
                          (assoc edn-props :children children :uix/context context)
                          edn-props))
                     [rsc-props rsc-refs children])]
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
     (defonce ^:private router- (atom nil))
     (defonce ^:private hacky-update-rsc-fn- (atom nil))))

#?(:cljs
   (defn- exec-server-action [id args]
     (let [result (rsd-client/createFromFetch
                    (js/fetch (str js/location.pathname "?_rsc")
                      #js {:method "POST"
                           :body (first args)})
                    #js {:moduleBaseURL "/"
                         :callServer exec-server-action})]
       (-> result
           (.then (fn [^js response]
                    (@hacky-update-rsc-fn- (.-root response))
                    (.-result response)))))))

#?(:cljs
   (defn- create-direct-server-action [id args]
     (js/fetch (str js/location.pathname "?_rsc")
       #js {:method "POST"
            :body (str {:id id :args args})
            :headers #js {:content-type "text/edn"}})))

#?(:cljs
   (defn- create-initial-flight-stream []
     (let [controller (atom nil)
           encoder (js/TextEncoder.)]
       (js/document.addEventListener "DOMContentLoaded"
          (fn []
            (some-> @controller (.close))
            (set! (.. js/window -__FLIGHT_DATA) nil)))
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
       (js/fetch (str (:path route) "?_rsc") #js {:priority (or priority "auto")})
       #js {:moduleBaseURL "/"
            :callServer exec-server-action})))

#?(:cljs
   (def ^:private init-rsc
     (memoize
       (fn [ssr-enabled]
         (if ssr-enabled
           (create-initial-flight-stream)
           (create-from-fetch {:path js/location.pathname}))))))

#?(:cljs
   (def prefetcher-ctx (uix/create-context {})))

#?(:cljs
   (defn- prefetch [href]
     ;; todo: invalidate prefetched routes
     (when (r/match-by-path @router- href)
       (when-not (@rsc-cache href)
         (swap! rsc-cache assoc href (create-from-fetch {:path href} :priority "low"))))))

#?(:cljs
   (defonce fm (.-instance jsf/ForesightManager)))

#?(:cljs
   (defui prefetcher [{:keys [level children]}]
     (let [observe (uix/use-callback
                     (fn [node]
                       (case level
                         :medium
                         (-> (.register fm
                                        #js {:element node
                                             :hitSlop 0
                                             :name (.. node -href)
                                             :unregisterOnCallback true
                                             :callback #(let [url (js/URL. (.. node -href))]
                                                          (prefetch (.-pathname url)))})
                             (.-unregister))
                         :high (let [obs (js/IntersectionObserver.
                                           (fn [entries]
                                             (when (= :high level)
                                               (doseq [entry entries]
                                                 (when (.-isIntersecting entry)
                                                   (let [url (js/URL. (.. entry -target -href))]
                                                     (prefetch (.-pathname url))))))))]
                                 (.observe obs node)
                                 #(.unobserve obs node))
                         nil))
                     [level])]
       ($ prefetcher-ctx {:value {:observe observe :level level}}
          children))))

#?(:cljs
   (def error-boundary
     (uix/create-error-boundary
       {:display-name "uix.rsc/error-boundary"
        :derive-error-state (fn [error] {:error error})
        :did-catch (fn [error info])}
       (fn [[state] {:keys [children]}]
         (if-let [error (:error state)]
           ($ :div {:style {:width "100vw"
                            :height "100vh"
                            :display :flex
                            :justify-content :center
                            :padding "32px 0 0"}}
             ($ :div {:style {:max-width 800}}
               ($ :div {:style {:color "rgb(206, 17, 38)"
                                :font-size 21
                                :margin-bottom 24}}
                  (.-message error))
               (let [{:keys [src line start-line frame-line name]}
                     (bean/bean (or js/window.__ERROR_SRC (.-digest error) #js {}))]
                 ($ :<>
                   ($ :div {:style {:font-size 12
                                    :color "#5a5a5a"}}
                      name)
                   (when src
                     ($ :pre {:style {:font-size 12
                                      :max-height 360
                                      :overflow-y :auto
                                      :background-color "rgba(206, 17, 38, 0.05)"
                                      :margin "16px 0"
                                      :padding "8px 0"
                                      :border-radius 5}}
                        ($ :code
                          (->> (str/split-lines src)
                               (map-indexed (fn [idx ln]
                                              (if (= idx line)
                                                ($ :div {:key idx
                                                         :style {:background-color "#ff000047"
                                                                 :padding "0 8px"}}
                                                   (str (+ start-line idx) "  ")
                                                   ln)
                                                ($ :div {:key idx
                                                         :style {:padding "0 8px"}}
                                                   (str (+ start-line idx) "  ")
                                                   ln))))))))
                   ($ :pre {:style {:font-size 12
                                    :max-height 360
                                    :overflow-y :auto
                                    :background-color "rgba(206, 17, 38, 0.05)"
                                    :margin "24px 0 16px"
                                    :padding "8px 0"
                                    :border-radius 5}}
                      ($ :code
                         (->> (str/split-lines (.-stack error))
                              (map-indexed (fn [idx ln]
                                             ($ :div
                                                {:key idx
                                                 :style {:background-color (when (= idx frame-line) "#ff000047")
                                                         :padding "0 8px"}}
                                               ln))))))))
               ($ :div {:style {:font-size 12
                                :color "#5a5a5a"}}
                  "This screen is visible only in development. It will not appear if the app crashes in production. Open your browser’s developer console to further inspect this error.")))
           children)))))

#?(:cljs
   (defui router
     ;; link pressed -> url change -> request server render -> update DOM
     [{:keys [ssr-enabled routes]}]
     (let [initialized? (uix/use-ref false)
           router (uix/use-memo #(reset! router- (rf/router routes))
                                [routes])
           [route set-route] (uix/use-state #(r/match-by-path router js/location.pathname))
           [resource set-resource] (uix/use-state (init-rsc ssr-enabled))
           _ (reset! hacky-update-rsc-fn- (fn [resource]
                                            (uix/start-transition #(set-resource resource))))
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
         ($ prefetcher {:level :disabled}
           (if ^boolean goog.DEBUG
             ($ error-boundary resource)
             resource))))))

(defui ^:client link [props]
  #?(:clj ($ :a props)
     :cljs
     ;; todo: wip
      (let [wrap-handler (fn [handler f]
                           (fn [e]
                             (when handler (handler e))
                             (f e)))
            {:keys [observe level]} (uix/use prefetcher-ctx)]
        ($ :a (-> props
                  (assoc :ref observe)
                  (update :on-mouse-enter wrap-handler #(when (= :low level)
                                                          (prefetch (:href props)))))))))

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
     (rsd-client/createServerReference id create-direct-server-action)))

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
   (defn read-edn-stream [body]
     (with-open [reader (io/reader body)]
       (edn/read (PushbackReader. reader)))))

#?(:clj
   (def ^:dynamic *bound-cache*))

#?(:clj
   (defn handle-action
     "client payload -> executes server action"
     [{:keys [multipart-params body headers]}]
     (let [content-type (headers "content-type")
           multipart? (str/starts-with? content-type "multipart/form-data")
           bound (multipart-params "_$bound")
           {:keys [id args]} (cond
                               multipart?
                               {:id (multipart-params "_$action")
                                :args (->> (dissoc multipart-params "_$action" "_$bound")
                                           (reduce-kv #(assoc %1 (keyword %2) %3) {}))}
                               (= content-type "text/edn") (read-edn-stream body)
                               :else (throw (IllegalArgumentException. "Unhandled server action: unknown content-type " content-type)))]
       (if-let [handler (get (all-actions) id)]
         (if (and multipart? bound)
           (let [get-bound (:get-bound *bound-cache*)]
             (handler (into args (get-bound bound))))
           (handler args))
         (throw (IllegalArgumentException. (str "Unhandled action " id " " args)))))))

(defn use-client [{:keys [fallback]} & [child]]
  (let [[mounted? set-mounted] (uix/use-state false)]
    (uix/use-effect #(set-mounted true) [])
    (if mounted? child fallback)))

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
   (defn partial [f args]
     (let [{:keys [store-bound]} *bound-cache*]
       [:rsc/partial f (store-bound args)])))

#?(:clj
   (def ^:dynamic *cache*))

#?(:clj
   (defmacro cache [cache-key & body]
     `(let [k# ~cache-key]
        (if (contains? @*cache* k#)
          (get @*cache* k#)
          (let [ret# (do ~@body)]
            (swap! *cache* assoc k# ret#)
            ret#)))))

#?(:clj
   (defn render-to-flight-stream
     "Renders UIx components into React Flight payload"
     [src {:keys [on-chunk cache result]}]
     (binding [*cache* (or cache (atom {}))]
       (let [done-count (atom 0)
             set-done #(when (== 2 (swap! done-count inc))
                         (on-chunk :done))
             handle-chunk #(if (= :done %)
                             (set-done)
                             (on-chunk %))
             sb (server.flight/create-state)
             ast (loader/run-with-loader
                   #(server.flight/-unwrap src sb))]
         ;; streamed flight payload -> suspended flight chunks
         (server.flight/render-to-flight-stream ast
           {:on-chunk handle-chunk :sb sb :result result})
         (stream-suspended sb on-chunk set-done)))))

#?(:clj
   ;; todo: cleanup, streaming ssr should be a part of uix.dom.server
   (defn render-html-chunk [from-id to-id element *state sb]
     (binding [dom.server/*sync-suspense* false]
       (dom.server/append! sb "<div hidden id='" from-id "'>")
       (dom.server/-render-html element *state sb)
       (dom.server/append! sb (str "</div><script>$RC('" to-id "', '" from-id "');</script>"))
       (str (.sb sb)))))

#?(:clj
   (def suspense-cleanup-js
     "<script>
     $RC=function(b,c,e){c=document.getElementById(c);c.parentNode.removeChild(c);var a=document.getElementById(b);if(a){b=a.previousSibling;if(e)b.data=\"$!\",a.setAttribute(\"data-dgst\",e);else{e=b.parentNode;a=b.nextSibling;var f=0;do{if(a&&8===a.nodeType){var d=a.data;if(\"/$\"===d)if(0===f)break;else f--;else\"$\"!==d&&\"$?\"!==d&&\"$!\"!==d||f++}d=a.nextSibling;e.removeChild(a);a=d}while(a);for(;c.firstChild;)e.insertBefore(c.firstChild,a);b.data=\"$\"}b._reactRetry&&b._reactRetry()}};
     </script>"))

#?(:clj
   (defn render-to-html-stream
     [src {:keys [on-chunk] :as opts}]
     (binding [*cache* (atom {})
               dom.server/*sync-suspense* false]
       (let [done-count (atom 0)
             set-done #(when (== 2 (swap! done-count inc))
                         (on-chunk :done))
             sb (server.flight/create-state)
             emit-error #(when (seq (:error-component @sb))
                           ;; todo: only in dev
                           (on-chunk (str "<script>window.__ERROR_SRC = " (json/generate-string (:error-component @sb)) ";</script>")))
             handle-chunk #(if (= :done %)
                             (do (emit-error) (set-done))
                             (on-chunk (str "<script>window.__FLIGHT_DATA.push(" (json/generate-string %) ");</script>")))
             ast (loader/run-with-loader
                   #(server.flight/-unwrap src sb))
             *state (volatile! :state/root)]
         ;; initial html -> streaming html helpers -> streamed flight payload -> suspended flight + html chunks
         (on-chunk (str "<!DOCTYPE html>" (dom.server/render-to-string ast)))
         (when (seq (:suspended @sb))
           (on-chunk suspense-cleanup-js))
         (on-chunk "<script>window.__FLIGHT_DATA ||= [];</script>")
         (server.flight/render-to-flight-stream ast {:on-chunk handle-chunk :sb sb :cache *cache*})
         ;; todo: handle errors in suspended blocks
         (stream-suspended sb handle-chunk set-done
           (fn [to-id element]
             (let [ssb (dom.server/make-static-builder)
                   from-id (str (gensym "S:"))]
               (on-chunk (render-html-chunk from-id to-id element *state ssb)))))))))