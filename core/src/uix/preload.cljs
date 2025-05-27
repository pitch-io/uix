(ns uix.preload
  (:require [uix.dev]
            [uix.validate]))

(uix.validate/set-props-assertion-enabled! true)

(uix.dev/init-fast-refresh!)

(defn ^:dev/after-load refresh []
  (uix.dev/refresh!))

