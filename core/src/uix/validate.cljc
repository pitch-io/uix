(ns uix.validate
  (:require [preo.core]
            [uix.core]))

(defn set-props-assertion-enabled! [enabled?]
  (reset! uix.core/props-assert-fn
          (fn [pred val]
            (if enabled?
              (preo.core/arg! pred val)
              true))))
