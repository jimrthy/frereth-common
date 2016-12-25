(ns com.frereth.common.protocol
  "Centralize the communications hand-shake layer"
  (:require [manifold.deferred :as d]
            [manifold.stream :as stream])
  (:import clojure.lang.ExceptionInfo))

(defmulti -step-builder
  "Build an individual step handler in a protocol chain"
  ::operation)

(defn protocol-agreement
  [strm msgs timeout]
  (-> strm
      (d/chain (map -step-builder msgs))
      (d/catch ExceptionInfo #(println "Whoops:" %))
      (d/catch RuntimeException #(println "How'd I miss:" %))
      (d/catch Exception #(println "Major oops:" %))))

(defn client->server
  "This really seems like it should be a higher-order handler coping with arbitrary handler chains.

Note that this is pretty deliberately insecure."
  [strm timeout versions]
  (-> strm
      (d/chain
       #(stream/try-put! % :ohai timeout ::timeout)
       (fn [sent]
         (if (not= sent ::timeout)
           (stream/try-take! strm ::drained timeout ::timeout)
           (throw (ex-info "Initial connection timed out" {}))))
       (fn [token]
         (when (not= :orly? token)
           (throw (ex-info "Broken handshake"))))
       (fn [_]
         (stream/try-put! strm (list 'icanhaz? versions) timeout ::timeout))
       (fn [sent]
         (if (not= sent ::timeout)
           (stream/try-take! strm ::drained timeout ::timeout)
           (throw (ex-info "Version negotiation timed out" {}))))
       (fn [version]
         ))
      (d/catch ExceptionInfo))
  (async/go
    (let [t-o (async/timeout timeout)]
      (async/alt!
        [[ch :ohai!]] ([_ _]
                       )
        timeout :timed-out))))

;; So, if I were writing a higher-level version of that, what would it look like?
(defn protocol-agreement
  [strm timeout versions]
  (protocol-exchange strm
                     [{::operation ::put!
                       ::value :ohai}
                      {::operation ::take!
                       ::expected :orly?
                       ::problem "Broken handshake"}
                      {::operation ::put!
                       ::value (list 'icanhaz versions)}
                      {::operation ::take!}]
                     timeout))
