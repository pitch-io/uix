(ns uix.rsc-example.routes
  (:require [uix.rsc :refer [defroutes]]
            #?(:clj [uix.rsc-example.server.ui :as ui])))

(defroutes routes2
  [["/" {:component ui/stories :title "new"}]
   ["/askstories" {:component ui/stories :title "ask"}]
   ["/showstories" {:component ui/stories :title "show"}]
   ["/jobstories" {:component ui/stories :title "job"}]
   ["/topstories" {:component ui/stories :title "top"}]
   ["/beststories" {:component ui/stories :title "best"}]
   ["/item/:id" {:component ui/item}]])

(defroutes routes
  [["/" {:component ui/home}]
   ["/movie/:id" {:component ui/movie}]
   ["/actor/:id" {:component ui/actor}]])