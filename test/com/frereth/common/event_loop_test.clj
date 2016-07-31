(ns com.frereth.common.event-loop-test
  (:require [clojure.test :refer (deftest is testing)]
            [com.frereth.common.system :as sys]
            [com.stuartsierra.component :as component]))

(deftest start-stop
  []
  (testing "Can start and stop an Event Loop successfully"
    (let [initial (sys/build-event-loop {})
          started (component/start initial)]
      (is started "Managed to start an Event Loop")
      (try
        (component/stop started)
        (catch Exception ex
          (is false "Stopping the event loop failed"))))))
