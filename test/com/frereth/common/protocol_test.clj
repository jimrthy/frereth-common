(ns com.frereth.common.protocol-test
  (:require [clojure.spec.gen :as gen]
            [clojure.test :refer (deftest is testing)]
            [com.frereth.common.protocol :as proto]
            [manifold.deferred :as d]
            [manifold.stream :as stream]))

(deftest happy-path
  ;; Trying to get them to share the same stream seems
  ;; like a bad idea.
  ;; Proably need to give each its own stream, then swap
  ;; between take! and put! until...what?
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
                        (fn [_] stream/take! server-strm) ;; best match
                        #(stream/put! client-strm %))
        outcome (deref xchng 1500 ::timeout)]
    (is outcome)
    (is (not= outcome ::timeout))))
(comment
  (happy-path)
  )
