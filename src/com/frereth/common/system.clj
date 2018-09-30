(ns com.frereth.common.system
  "This is another one that doesn't make a lot of sense"
  (:require [clojure.core.async :as async]
            [clojure.spec.alpha :as s]
            [com.frereth.common.async-component :as async-cpt]
            [com.frereth.common.async-zmq :as async-zmq]
            #_[com.frereth.common.curve.shared :as curve]
            [com.frereth.common.zmq-socket :as zmq-sock]
            [hara.event :refer (raise)]
            [integrant.core :as ig]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Specs

(s/def ::client-keys any?)
(s/def ::server-key any?)
(s/def ::context ::zmq-sock/ctx)
(s/def ::event-loop-name string?)
(s/def ::url any?)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(s/fdef build-event-loop-description
  ;; FIXME: These specs are broken.
  ;; Restore them when I can use my CurveCP library
  :args (s/cat :options (s/keys :opt [::client-keys
                                      ::direction
                                      ::server-key
                                      ::socket-type
                                      ::thread-count]
                                :req [::context
                                      ::event-loop-name
                                      ::url]))
  ;; Q: Do I want to try to spec the return value?
  ;; (Which is the actual description of the system to start
  :ret any?)
(defn build-event-loop-description
  "Return a component description that's suitable for nesting into yours to pass along to cpt-dsl/build

For running as an integrated library

At one point I thought I was really jumping the gun on this one.
It provides a decent example, but it really seems to just belong
inside frereth.client.

And frereth.server. And, if there's ever a 'real' stand-alone
frereth.renderer, there.

So this abstraction absolutely does belong in common.

It seems to make less sense under the system namespace, but
I'm not sure which alternatives make more sense."
  [{:keys [::client-keys
           ::context
           ::direction
           ::event-loop-name
           ::server-key
           ::socket-type
           ::thread-count
           ::url]
    :or {direction :connect
         socket-type :dealer
         thread-count 2}
    :as opts}]
  {:pre [event-loop-name]}
  (let [url #_(cond-> url
              (not (:cljeromq.common/zmq-protocol url)) (assoc :cljeromq.common/zmq-protocol :tcp)
              (not (:cljeromq.common/zmq-address url)) (assoc :cljeromq.common/zmq-address [127 0 0 1])
              (not (:cljeromq.common/port url)) (assoc :cljeromq.common/port 9182))
        (throw (RuntimeException. "What makes sense here?"))]
    {::async-zmq/event-iface {:ex-sock (ig/ref [::async-cpt/async ::in-chan])
                              :in-chan (ig/ref ::in-chan)
                              :status-chan (ig/ref [::async-cpt/async ::status-chan])}
     ::async-zmq/event-loop {:_name event-loop-name
                             :interface (ig/ref ::async-zmq/event-iface)
                             :ex-chan (ig/ref [::async-cpt/async ::ex-chan])}
     ::context {:thread-count thread-count}
     ::ex-sock {:context-wrapper (ig/ref ::context)
                :zmq-url url
                :direction direction
                :sock-type socket-type}
     [::async-cpt/async ::ex-chan] {}
     [::async-cpt/async ::in-chan] {}
     [::async-cpt/async  ::status-chan] {}}))

;; None of these are correct.
;; They're calling the constructor to define
;; the structure of the record that I've
;; been passing to (component/start).
;; But it's an initial step toward getting the
;; pieces translated to integrant
(defmethod ig/init-key ::context
  [_ opts]
  ;; I'm not sure what the CurveCP interface will
  ;; wind up looking like, but this absolutely
  ;; is not it.
  (throw (RuntimeException. "Totally obsolete")))
