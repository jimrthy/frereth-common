(ns com.frereth.common.zmq-socket
  "This should be a wrapper interface that hides as many low-level queue implementation details as possible"
  (:require [cljeromq.core :as mq]
            [com.frereth.common.util :as util]
            [com.stuartsierra.component :as component]
            [schema.core :as s]
            [taoensso.timbre :as log])
  (:import [clojure.lang ExceptionInfo]
           ;; This is really where the experimental switch to zmq-jni
           ;; should happen in a branch
           #_[org.zeromq ZMQException]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schema

(def socket-types (s/enum :push :pull
                          :req :rep
                          :pair
                          :pub :sub
                          :router :dealer))

(s/defrecord ContextWrapper
    [ctx :- mq/Context
     thread-count :- s/Int]
  component/Lifecycle
  (start
   [this]
   (if-not ctx
     (let [thread-count (or thread-count 1)
           ctx (mq/context thread-count)]
       (assoc this
              :ctx ctx
              :thread-count thread-count))
     this))
  (stop
   [this]
   (if ctx
     (do
       (log/debug "Terminating context")
       ;; Note that this is going to hang until
       ;; all sockets are closed
       (try
         (mq/terminate! ctx)
         (assoc this :ctx nil)
         (finally
           (log/debug "Context terminated, one way or another")
           (assoc this :ctx nil))))
     (do
       (log/debug "No 0mq messaging context to terminate")
       this))))

(s/defrecord SocketDescription
    [ctx :- ContextWrapper
     url :- mq/zmq-url
     sock-type :- socket-types
     direction :- (s/enum :bind :connect)
     socket :- mq/Socket]
  ;; Q: Why can't I include a docstring?
  ;; "Describe a 0mq socket"
  component/Lifecycle
  (start
   [this]
   (if-not socket
     (do
       (assert ctx "Can't do anything without a Messaging Context")
       (assert url "Nowhere to connect")
       (comment) (log/debug "Getting ready to try to start a"
                            sock-type
                            "socket based on context\n"
                            (util/pretty ctx)
                            "a" (class ctx))
       (let [sock (mq/socket! (:ctx ctx) sock-type)]
         (try
           (let [uri (mq/connection-string url)]
             (if (= direction :bind)
               (mq/bind! sock uri)
               (mq/connect! sock uri)))
           (catch ExceptionInfo ex
             (log/error ex "Problem w/ connection to\n"
                        (util/pretty url)
                        "\nAre you having internet issues?")))
         (assoc this :socket sock)))
     this))
  (stop
   [this]
   (log/debug "Possibly closing socket" socket)
   (if socket
     (do
       (try
         (mq/set-linger! socket 0)
         (mq/close! socket)
         (assoc this :socket nil)
         (catch ExceptionInfo ex
           (log/error ex "Failed to close socket:" socket
                      "\nAre you trying to stop this a second time?"
                      "\n(if so, you probably have a bug where you should"
                      " be using the result of the first call to stop)")
           (assoc this :socket nil))))
     this)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public
;;; TODO: Really need to add wrappers for everything interesting,
;;; esp. send/recv

(s/defn ctx-ctor :- ContextWrapper
  "TODO: This doesn't belong in a socket namespace"
  [{:keys [thread-count]
    :or [thread-count 1]
    :as options}]
  (map->ContextWrapper options))

(s/defn ctor :- SocketDescription
  [{:keys [direction sock-type url]
    :or {direction :connect}
    :as options}]
  (map->SocketDescription options))
