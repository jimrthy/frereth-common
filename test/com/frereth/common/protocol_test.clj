(ns com.frereth.common.protocol-test
  (:require [clojure.spec.gen :as gen]
            [clojure.test :refer (deftest is testing)]
            [com.frereth.common.protocol :as proto]
            [manifold.deferred :as d]
            [manifold.stream :as stream]))

(deftest happy-path
  (let [client-strm (stream/stream)
        server-strm (stream/stream)
        server (proto/server-version-protocol server-strm 50)
        client (proto/client-version-protocol client-strm 50)
        xchng (d/chain (stream/take! client-strm) ;; versions understood
                        (fn [x]
                          (println "Understood versions from client:" x)
                          x)
                        #(stream/put! server-strm %)
                        (fn [_] (stream/take! server-strm)) ;; best match
                        #(stream/put! client-strm %))
        protected (comment (d/catch xchng Exception (fn [ex]
                                                      ;; Q: Why aren't I getting here on test failures?
                                                      (is false (str "Failure: " ex)))))
        ;; outcome is really boring in this scenario, since stream/put!
        ;; derefs to true on success.
        ;; This is one area where implementing this as FSMs would be much more satisfying.
        ;; Then I could know that I've reached an end state.
        outcome (deref xchng 1500 ::timeout)]
    (is outcome)
    (is (not= ::timeout outcome))
    (is (= (deref client 50 :did-this-exit?) [:frereth [0 0 1]]))))
