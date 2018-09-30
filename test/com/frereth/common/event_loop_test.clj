(ns com.frereth.common.event-loop-test
  (:require [clojure.test :refer (deftest is testing)]
            [com.frereth.common.system :as sys]
            [integrant.core :as ig]))

(deftest start-stop
  []
  (testing "Can start and stop an Event Loop successfully"
    (let [config (sys/build-event-loop-description {::sys/event-loop-name "basic-start-stop-test"})
          system (ig/init config)
          context-wrapper (:ctx system)]
      (try
        (is system "Managed to start an Event Loop")
        (finally
          (ig/halt! system))))))
