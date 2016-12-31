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
        xchng (d/chain (stream/take! client-strm)
                        (fn [tkn]
                          (is (= tkn ::proto/ohai))
                          tkn)
                        #(stream/put! server-strm %)
                        (fn [_] (stream/take! server-strm))
                        (fn [tkn]
                          (println "Pulled" tkn "from server-stream in response to ::proto/ohai")
                          (is (= tkn ::proto/orly?))
                          tkn)
                        #(stream/put! client-strm %)
                        (fn [_] (stream/take! client-strm)) ;; versions understood
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
    ;; Really should be able to do something like this:
    (comment (is (= outcome [:frereth [0 0 1]])))
    ;; But I can't. I don't want to just put the client response back onto the stream
    (is (not= outcome ::timeout))
    (is outcome)
    ;; Then again, it looks like I can do this:
    (comment) (is (= (deref client 50 :did-this-exit?) [:frereth [0 0 1]]))))
(comment
  (happy-path)
  )
