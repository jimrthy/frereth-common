(ns com.frereth.common.protocol-test
  (:require [clojure.spec.gen :as gen]
            [clojure.test :refer (deftest is testing)]
            [com.frereth.common.protocol :as proto]
            [manifold.stream :as stream]))

(deftest happy-path
  ;; Trying to get them to share the same stream seems
  ;; like a bad idea.
  ;; Proably need to give each its own stream, then swap
  ;; between take! and put! until...what?
  (let [strm (stream/stream)
        server (proto/server-version-protocol strm 50)
        client (proto/client-version-protocol strm 50)
        outcome (deref client 500 ::timeout)]
    (is (not= outcome ::timeout))))
(comment
  (happy-path)
  )
