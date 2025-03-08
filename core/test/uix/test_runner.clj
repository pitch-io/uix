(ns uix.test-runner
  (:require [clojure.test :refer :all]
            [uix.core-test]
            [uix.aot-test]
            [uix.linter-test]
            [uix.hooks-test]
            [uix.dce-test]))

(defn -main [& args]
  (let [{:keys [fail error]} (run-tests
                              'uix.core-test
                              'uix.aot-test
                              'uix.hooks-test
                              'uix.linter-test
                              'uix.dce-test)]
    (when (pos? (+ fail error))
      (System/exit 1))))
