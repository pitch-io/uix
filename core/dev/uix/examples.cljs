(ns uix.examples
  (:require [cljs.spec.alpha :as s]
            [uix.core :as uix :refer [$ defui]]
            [uix.dom]
            [uix.css :refer [css]]
            [uix.css.adapter.uix]
            [shadow.cljs.devtools.client.hud :as shadow.hud]))

(def global-styles
  (css {:global {:html {:box-sizing :border-box}
                 "html *" {:box-sizing :inherit}
                 :body {:margin 0
                        :font "400 16px Inter, sans-serif"
                        :letter-spacing 0
                        :-webkit-font-smoothing :antialiased
                        :-moz-osx-font-smoothing :grayscale
                        :line-height 1.4
                        :text-rendering :optimizeLegibility}}}))

(def tools [:rect :circle :text])

(s/def :tool-button/selected? boolean?)
(s/def :button/label string?)
(s/def :button/on-press fn?)

(s/def ::tool-button
  (s/keys :req-un [:tool-button/selected? :button/label :button/on-press]))

(defui tool-button [{:keys [selected? label on-press]}]
  {:props [::tool-button]}
  ($ :div {:on-click on-press
           :style (css {:padding "4px 8px"
                        :cursor :pointer
                        :border-radius 3
                        :color (when selected? "#fff")
                        :background-color (when selected? "#ff89da")})}
    label))

(defui toolbar [{:keys [state set-state on-add-shape]}]
  (let [{:keys [grid?]} state]
    ($ :div {:style (css {:padding "8px 16px"
                          :height 46
                          :display :flex
                          :align-items :center
                          :background-color "#fff"
                          :position :relative
                          :box-shadow "0 1px 1px rgba(0, 0, 10, 0.2)"})}
       ($ :img {:src "https://raw.githubusercontent.com/pitch-io/uix/master/logo.png"
                :style (css {:height "100%"
                             :margin "0 16px 0 0"})})
       (for [t tools]
         ($ tool-button {:key t :label (name t) :on-press #(on-add-shape t) :selected? false}))
       ($ :div {:style (css {:width 1 :height "60%" :background-color "#c1cdd0" :margin "0 8px"})})
       ($ tool-button {:label "grid"
                       :selected? grid?
                       :on-press #(set-state (update state :grid? not))}))))

(defui ^:memo canvas-grid [{:keys [width height size color]}]
  (let [wn (Math/ceil (/ width size))
        hn (Math/ceil (/ height size))]
    ($ :<>
      (for [widx (range wn)]
        ($ :line {:key widx
                  :x1 (* size widx)
                  :x2 (* size widx)
                  :y1 0
                  :y2 height
                  :stroke color}))
      (for [hidx (range hn)]
        ($ :line {:key hidx
                  :y1 (* size hidx)
                  :y2 (* size hidx)
                  :x1 0
                  :x2 width
                  :stroke color})))))

(defui cursor [{:keys [mx my r color]}]
  (let [mx (+ mx (/ r 2))
        my (+ my (/ r 2))]
    ($ :circle {:cx (- mx (/ r 2)) :cy (- my (/ r 2)) :r r :fill color})))

(defui rect [{:keys [x y width height fill-color stroke-width stroke-color
                     children on-mouse-down on-mouse-up]}]
  ($ :rect
    {:on-mouse-down on-mouse-down
     :on-mouse-up on-mouse-up
     :width width
     :height height
     :x x
     :y y
     :fill fill-color
     :stroke-width stroke-width
     :stroke stroke-color}
    children))

(defui circle [{:keys [x y width height fill-color stroke-width stroke-color on-mouse-down]}]
  ($ :ellipse
    {:on-mouse-down on-mouse-down
     :cx (+ x (/ width 2))
     :cy (+ y (/ height 2))
     :rx (/ width 2)
     :ry (/ height 2)
     :fill fill-color
     :stroke-width stroke-width
     :stroke stroke-color}))

(defui text [{:keys [x y width height fill-color stroke-width stroke-color
                     value font-size font-family font-style
                     on-mouse-down]}]
  ($ :text
    {:on-mouse-down on-mouse-down
     :x x
     :y y
     :font-family font-family
     :font-size font-size
     :font-style font-style}
    value))

(defn map-object [object size]
  (-> object
      (update :x * size)
      (update :y * size)
      (update :width * size)
      (update :height * size)))

(defui ^:memo objects-layer [{:keys [objects size on-select]}]
  (for [{:keys [id] :as object} objects]
    (let [idx (.indexOf objects object)
          object (-> (map-object object size)
                     (assoc :key id :on-mouse-down #(on-select idx)))]
      (case (:type object)
        :rect ($ rect object)
        :circle ($ circle object)
        :text ($ text object)))))

(defui ^:memo edit-layer [{:keys [mx my on-object-changed on-select idx selected size]}]
  (let [[active? set-active] (uix/use-state false)
        selected? (some? selected)
        on-move (uix/use-effect-event
                  (fn [x y]
                    (on-object-changed idx (assoc selected :x x :y y))))
        on-resize (fn [object idx width height]
                    (on-object-changed idx (assoc object :width width :height height)))]

    (uix/use-effect
      #(when active?
         (on-move mx my))
      [selected? active? mx my])

    (uix/use-effect
      #(when selected?
         (set-active true))
      [selected?])

    (when selected
      ($ rect
        (-> (map-object selected size)
            (assoc
              :on-mouse-down #(set-active true)
              :on-mouse-up #(set-active false)
              :stroke-width 1
              :stroke-color "#0000ff"
              :fill-color :transparent))))))

(s/def :canvas/width number?)
(s/def :canvas/height number?)
(s/def :fn/on-object-changed fn?)

(s/def :background-layer/props
  (s/keys :req-un [:canvas/width :canvas/height :fn/on-mouse-down]))

(defui ^:memo background-layer [{:keys [width height on-mouse-down]}]
  {:props [:background-layer/props]}
  ($ rect
    {:on-mouse-down #(on-mouse-down)
     :x 0
     :y 0
     :width width
     :height height
     :fill-color :transparent
     :stroke-color :none}))

(s/def :state/grid? boolean?)
(s/def :state/selected (s/nilable number?))
(s/def :state/objects (s/coll-of map?))

(s/def :state/canvas
  (s/keys :req-un [:state/selected :state/objects]))

(s/def ::state
  (s/keys :req-un [:state/grid? :state/canvas]))

(s/def :fn/on-object-changed fn?)
(s/def :fn/on-object-select fn?)

(s/def :canvas/props
  (s/keys :req-un [::state :fn/on-object-changed :fn/on-object-select]))

(defui ^:memo canvas [{:keys [state on-object-changed on-object-select]}]
  {:props [:canvas/props]}
  (let [{:keys [grid? canvas]} state
        [[width height] set-size] (uix/use-state [0 0])
        [[ox oy] set-offset] (uix/use-state [0 0])
        [[mx my] set-mouse] (uix/use-state [0 0])
        ref (uix/use-ref)
        size 8
        mx (quot (- mx ox) size)
        my (quot (- my oy) size)]
    (uix/use-effect
      (fn []
        (set-offset [(.-offsetLeft @ref) (.-offsetTop @ref)])
        (set-size [(.-width js/screen) (.-height js/screen)]))
      [])
    ($ :div {:ref ref
             :on-mouse-move (fn [^js e]
                              (set-mouse [(.-clientX e) (.-clientY e)]))
             :style (css {:flex 1
                          :position :relative
                          :background-color "#ebeff0"})}
      ($ :svg {:style (css {:width width
                            :height height
                            :position :absolute
                            :left 0
                            :top 0})
               :view-box (str "0 0 " width " " height)}
        (when grid?
          ($ :<>
            ($ canvas-grid {:width width :height height :size size :color "#c1cdd0"})
            ($ cursor {:r 2
                       :color "#4f7f8b"
                       :mx (* size mx)
                       :my (* size my)})))
        ($ background-layer
          {:width width
           :height height
           :on-mouse-down on-object-select})
        ($ objects-layer
          {:objects (:objects canvas)
           :size size
           :on-select on-object-select})
        ($ edit-layer
          {:size size
           :on-select on-object-select
           :on-object-changed on-object-changed
           :mx mx
           :my my
           :idx (:selected canvas)
           :selected (when (:selected canvas)
                       (nth (:objects canvas) (:selected canvas)))})))))

(def default-styles
  {:x 32
   :y 32
   :width 12
   :height 12
   :stroke-width 2
   :stroke-color "#ff0000"
   :fill-color "#00ff00"})

(defui ^:memo app []
  (let [[state set-state] (uix/use-state {:grid? true
                                          :canvas {:selected nil
                                                   :objects []}})
        on-add-shape (fn [shape]
                       (let [id (random-uuid)]
                         (set-state
                           (->> (case shape
                                  :rect (merge default-styles {:type :rect :id id})
                                  :circle (merge default-styles {:type :circle :id id})
                                  :text (merge default-styles {:type :text :id id
                                                               :value "text" :font-family "Inter"
                                                               :font-size 32 :font-style :normal}))
                                (update-in state [:canvas :objects] conj)))))
        on-object-select (fn
                           ([]
                            (set-state (assoc-in state [:canvas :selected] nil)))
                           ([idx]
                            (set-state (assoc-in state [:canvas :selected] idx))))
        on-object-changed (uix/use-callback
                            (fn [idx object]
                              (set-state
                                #(assoc-in % [:canvas :objects idx] object)))
                            [])]
    ($ :div {:style (css {:font-family "Inter"
                          :font-size 14
                          :display :flex
                          :flex-direction :column
                          :width "100vw"
                          :height "100vh"})}
       ($ toolbar {:state state :set-state set-state :on-add-shape on-add-shape})
       ($ canvas {:state state
                  :on-object-select on-object-select
                  :on-object-changed on-object-changed}))))

(def shadow-error-boundary
  (uix.core/create-error-boundary
    {:derive-error-state (fn [error]
                           {:error error})}
    (fn [[state _set-state!] {:keys [children]}]
      (if-let [error (:error state)]
        (do (shadow.hud/dom-insert
              [:div
               {:id shadow.hud/hud-id
                :style {:position "fixed"
                        :left "0px"
                        :top "0px"
                        :bottom "0px"
                        :right "0px"
                        :color "#000"
                        :background-color "#fff"
                        :border "5px solid red"
                        :z-index "10000"
                        :padding "20px"
                        :overflow "auto"
                        :font-family "monospace"
                        :font-size "12px"}}
               [:div {:style "color: red; margin-bottom: 10px; font-size: 2em;"}
                (.. error -constructor -name)]
               [:pre (pr-str error)]])
            nil)
        children))))


;; init app
(defonce -init
         (let [root (uix.dom/create-root (js/document.getElementById "root"))]
           (uix.dom/render-root ($ shadow-error-boundary ($ app)) root)
           nil))
