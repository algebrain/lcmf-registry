(ns lcmf.registry-test-runner
  (:require [clojure.test :as t]
            [lcmf.registry-test]))

(defn -main []
  (let [{:keys [fail error]} (t/run-tests 'lcmf.registry-test)]
    (when (pos? (+ fail error))
      (System/exit 1))))
