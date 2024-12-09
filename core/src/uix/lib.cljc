(ns uix.lib
  #?(:cljs (:require-macros [uix.lib :refer [doseq-loop]]))
  #?(:cljs (:require [goog.object :as gobj]))
  #?(:clj (:require [cljs.analyzer :as ana]
                    [clojure.walk]
                    [cljs.core])))

#?(:clj
   (defmacro assert! [x message]
     `(when-not ~x
        (throw (new AssertionError (str "Assert failed: " ~message "\n" (pr-str '~x)))))))

#?(:clj
   (defmacro doseq-loop [[v vs] & body]
     `(let [v# ~vs]
        (when (seq v#)
          (loop [x# (first v#)
                 xs# (next v#)]
            (let [~v x#]
              ~@body)
            (when (seq xs#)
              (recur (first xs#) (next xs#))))))))

#?(:cljs
   (defn map->js [m]
     (reduce-kv (fn [o k v]
                  (gobj/set o (name k) v)
                  o)
                #js {}
                m)))

#?(:clj
   (defn cljs-env? [env]
     (boolean (:ns env))))

#?(:clj
   (defn find-form [pred sexp]
     (let [forms (atom [])]
       (clojure.walk/prewalk
        (fn [x]
          (when (pred x)
            (swap! forms conj x))
          x)
        sexp)
       @forms)))

#?(:clj
   (defn parse-sig [name fdecl]
     (let [[fdecl m] (if (string? (first fdecl))
                       [(next fdecl) {:doc (first fdecl)}]
                       [fdecl {}])
           [fdecl m] (if (map? (first fdecl))
                       [(next fdecl) (conj m (first fdecl))]
                       [fdecl m])
           fdecl (if (vector? (first fdecl))
                   (list fdecl)
                   fdecl)
           [fdecl m] (if (map? (last fdecl))
                       [(butlast fdecl) (conj m (last fdecl))]
                       [fdecl m])
           m (conj {:arglists (list 'quote (#'cljs.core/sigs fdecl))} m)
           m (conj (if (meta name) (meta name) {}) m)]
       [(with-meta name m) fdecl])))

#?(:clj
   (do
     (defn- uix-element?
       "Returns true when `form` is `(uix.core/$ ...)`"
       [env form]
       (let [resolve-fn (if (uix.lib/cljs-env? env)
                          ana/resolve-var
                          resolve)]
         (and (list? form)
              (symbol? (first form))
              (->> (first form) (resolve-fn env) :name (= 'uix.core/$)))))

     (def elements-list-fns
       '#{for map mapv filter filterv remove keep keep-indexed})

     (defn- elements-list?
       "Returns true when `v` is form commonly used to render a list of elements
       `(map ...)`, `(for ...)`, etc"
       [v]
       (and (list? v)
            (symbol? (first v))
            (elements-list-fns (first v))))

     (defn normalize-element
       "When the second item in the element `v` is either UIx element or `elements-list?`,
       returns normalized element with empty map at props position
       and child element shifted into children position"
       [env v]
       (if (or (uix-element? env (second v))
               (elements-list? (second v)))
         (into [(first v) {}] (rest v))
         v))))

#?(:clj
   (do
    (defn used-keys [sig]
      (->> sig
           (reduce-kv
             (fn [ret k v]
               (cond
                 (and (keyword? k) (= "keys" (name k)))
                 (if-let [ns (namespace k)]
                   (into ret (map #(keyword ns (name %))) v)
                   (into ret (map keyword) v))

                 (= :strs k) (into ret (map str) v)
                 (= :syms k) (into ret v)
                 (= :& k) ret
                 (keyword? v) (conj ret v)
                 (string? v) (conj ret v)
                 (symbol? v) (conj ret v)
                 :else ret))
             #{})))

    (defn rest-props [[sig :as args]]
      (if (map? sig)
        (if-not (contains? sig :&)
          [args]
          [(update args 0 dissoc :&) (used-keys sig) (:& sig)])
        [args]))))