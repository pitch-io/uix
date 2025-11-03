(ns uix.test-runner
  (:require [cljs.test]
            [uix.aot-test]
            [uix.core-test]
            [uix.compiler-test]
            [uix.hooks-test]
            [uix.reagent-test]
            [uix.re-frame-test]
            [uix.test-utils :as t]))

(defn- print-comparison [m]
  (println "expected:" (pr-str (:expected m)))
  (if (instance? js/Error (:actual m))
    (js/console.error (:actual m))
    (println "  actual:" (pr-str (:actual m)))))

(defmethod cljs.test/report [::cljs.test/default :error] [m]
  (cljs.test/inc-report-counter! :error)
  (println "\nERROR in" (cljs.test/testing-vars-str m))
  (when (seq (:testing-contexts (cljs.test/get-current-env)))
    (println (cljs.test/testing-contexts-str)))
  (when-let [message (:message m)] (println message))
  (print-comparison m))

(defmethod cljs.test/report [::cljs.test/default :summary] [m]
  (t/destroy-dom)
  (println "\nRan" (:test m) "tests containing"
           (+ (:pass m) (:fail m) (:error m)) "assertions.")
  (println (:fail m) "failures," (:error m) "errors.")
  (when (pos? (+ (:fail m) (:error m)))
    (js/process.exit 1)))

(defn -main [& args]
  (t/setup-dom)
  (cljs.test/run-tests
   (cljs.test/empty-env)
   'uix.aot-test
   'uix.core-test
   'uix.compiler-test
   'uix.hooks-test
   'uix.reagent-test
   'uix.re-frame-test))
