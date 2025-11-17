(ns uix.linter-test
  (:require [cljs.spec.alpha :as s]
            [uix.core :as uix :refer [defui $ defhook]]))

(defui test-missing-deps [{:keys [x]}]
  (uix.core/use-effect (fn [] x) []))

(defui test-missing-deps-disabled [{:keys [z]}]
  (uix.core/use-effect (fn [] z) ^:lint/disable []))

(defui test-unnecessary-deps []
  (let [ref (uix.core/use-ref)
        [v set-v] (uix.core/use-state 0)]
    (uix.core/use-effect
     (fn []
       @ref
       (set-v (inc v)))
     [v ref set-v])))

(defui test-missing-deps-with-shadowing [{:keys [y]}]
  (uix.core/use-effect
   (fn []
     (let [y 1]
       y))
   []))

(defui test-fn-ref-passed-into-hook []
  (uix.core/use-effect identity))

(defui test-deps-js-array []
  (uix.core/use-effect (fn []) #js []))

(defui test-deps-something-else []
  (uix.core/use-effect (fn []) (list 1 2 3)))

(defui test-deps-includes-primitive-literals []
  (uix.core/use-effect (fn []) [:kw 1 "str" nil true]))

(defui test-unsafe-set-state []
  (let [[value set-value] (uix.core/use-state 0)]
    (uix.core/use-effect
     (fn []
       (set-value inc)))))

(defui test-hook-in-branch []
  (when true
    (uix.core/use-effect (fn []))))

(defui test-hook-in-loop []
  (for [x []]
    (uix.core/use-effect (fn []))))

(defui test-nested []
  (when false
    (or (uix.core/use-effect (fn [] "nested condition")) 1))
  (loop []
    (loop [x (uix.core/use-effect (fn [] "nested loop"))])))

(defui test-missing-key []
  (for [x []]
    ($ :div.test-missing-key {} x))
  (for [x []]
    ($ :div.test-missing-key ($ x)))
  (for [x []]
    (let [x x]
      (do
        ($ :div.test-missing-key ($ x))
        ($ :div.test-missing-key-nested ($ x))))))

(defui Button [{:keys [on-click children]}]
  (uix.core/use-effect
   (fn []
     "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
     "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
     "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
   [])
  ($ :button {:on-click on-click
              :style {:color "red"}}
     children))

(defui test-duplicate-syms [{:keys [dsym]}]
  (uix.core/use-effect
   (fn []
     (prn dsym dsym dsym))
   []))

(defui test-106 [{:keys [x]}]
  (let [document x]
    (uix.core/use-effect
     (fn []
       (println x document))
     [x])))

(defui test-100 [props]
  ($ :span
     {}
     (for [i (range 10)]
       (->> {:not-key (str "bar-" i)}
            ($ :span)))))



(defhook use-hook [hook-hook]
  (uix/use-effect
   (fn []
     (prn hook-hook))
   []))


($ :div {:width 100 :& {:on-click prn}})

($ :div {:& {:on-click prn}})

($ :div {:& [{:on-click prn}]})


(s/def ::button
  (s/keys :req-un [:button/on-click]
          :req [:button/title]
          :opt [:aria/label]))

(defui button [{:keys [on-click button/title aria/label]}]
  {:props [::button]}
  ($ button {:on-click on-click
             :title title
             :aria-label label}))

($ button)


(defn create-fake-ref [])

(defui test-interop-ref-read-write []
  (let [ref (uix/use-ref nil)
        js-ref (create-fake-ref)]
    (set! (.-current ref) 1)
    (prn (.-current ref))
    (prn (.-current js-ref))))


(defui test-100-2 []
  ($ :span
     {}
     (for [i (range 10)]
       (->> {:key (str "foo-" i)}
            ($ :span)))))