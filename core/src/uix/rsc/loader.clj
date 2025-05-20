(ns uix.rsc.loader)

(def ^:dynamic *loader-ctx*)
(def ^:dynamic *max-render-passes* 100)

(defn- cache-get [f args]
  (get-in @*loader-ctx* [f :cache args]))

(defn- cache-hit? [f args]
  (contains? (get-in @*loader-ctx* [f :cache]) args))

(defn- update-cache [f cache]
  (swap! *loader-ctx* update-in [f :cache] merge cache))

(defn- enqueue [f args]
  (swap! *loader-ctx* update-in [f :queue] (fnil conj []) args))

(defn- clear-queue [f]
  (swap! *loader-ctx* assoc-in [f :queue] []))

(defn batch [f]
  (fn [& args]
    (if-not (cache-hit? f args)
      (do (enqueue f args)
          (throw (ex-info "loader/interrupted" {})))
      (cache-get f args))))

(defmacro with-loader [& body]
  `(try
     ~@body
     (catch Exception e#
       (if (= "loader/interrupted" (.getMessage e#))
         :loader/interrupted
         (throw e#)))))

(defn- process-queues! []
  (doseq [[f {:keys [queue]}] @*loader-ctx*]
    (when (seq queue)
      (clear-queue f)
      (->> (apply f queue)
           (map (fn [entry result]
                  [entry result])
                queue)
           (into {})
           (update-cache f)))))

(defn- queues-drained? []
  (->> (vals @*loader-ctx*)
       (every? (comp empty? :queue))))

(defn run-with-loader [f]
  (binding [*loader-ctx* (atom {})]
    (loop [idx 0]
      (let [ret (f)]
        (assert (<= idx *max-render-passes*) "Too many render passes")
        (if (queues-drained?)
          ret
          (do (process-queues!)
              (recur (inc idx))))))))

(comment
  (let [fetch (batch (fn [& args]
                       (prn :fetch)
                       args))]
    (let [run #(with-loader
                 [:div
                  (with-loader
                    [:div (fetch 1)])
                  (with-loader
                    [:h1 (fetch 2)])
                  (with-loader
                    [:h1 (fetch 3)])])]
      (run-with-loader run))))
