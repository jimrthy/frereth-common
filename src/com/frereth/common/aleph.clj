(ns com.frereth.common.aleph
  "Wrappers for my aleph experiments"
  (:require [aleph.tcp :as tcp]
            [aleph.udp :as udp]
            [clojure.edn :as edn]
            [clojure.spec.alpha :as s]
            [manifold.deferred :as deferred]
            [manifold.stream :as stream]))


;; TODO: Get these spec'd
(s/def ::connection-info any?)
(s/def ::server any?)
(s/def ::stream any?)

;; None of the rest of these belong in here...do they?
(defn put!
  "Really just a convenience wrapper to cut down on the number
of namespaces clients have to :require to use this one"
  [stream msg]
  (stream/put! stream msg))

(s/fdef take!
  :args (s/or :full (s/cat :stream stream/stream?
                           :default any?
                           :timeout number?)
              :default (s/cat :stream stream/stream?
                              :default any?)
              :base (s/cat :stream stream/stream?))
  ;; Q: Is this accurate?
  :ret (s/nilable bytes?))
(defn take!
  "Note that this approach doesn't really compose well.

Since everything else in manifold expects the deferred.

That makes this much less useful"
  ([stream]
   @(stream/take! stream))
  ([stream default]
   @(stream/take! stream default))
  ([stream default timeout]
   (let [deferred (stream/take! stream default)]
     (deref deferred timeout ::timeout))))

(defn router-event-loop
  "Sets up an event loop like a 0mq router socket

At least conceptually."
  [f s cleanup]
  (comment
    (deferred/chain
      (deferred/loop []
        ;; Q: How much cleaner would this be to just
        ;; use stream/consume ?
        ;; Or would that work at all?
        (-> (deferred/let-flow [msg (stream/take! s ::none)]
              (when-not (identical? ::none msg)
                (deferred/let-flow [result (f msg)]
                  (when result
                    (deferred/recur)))))
            (deferred/catch
                (fn [ex]
                  (println "Server Oops!\n" ex)
                  (.printStackTrace ex)
                  (put! s {:error (.getMessage ex)
                           :type (-> ex class str)})
                  (stream/close! s)))))
      cleanup)))

(s/fdef router
        :args (s/cat :connections any?
                     :f (s/fspec
                         :args (s/cat :message bytes?)
                         ;; nil is the signal to close the connection
                         :ret any?))
        :ret (s/fspec :args (s/cat :s ::stream
                                   :info any?)))
(defn router
  [connections f]
  (fn [s {:keys [remote-addr]}]
    (let [putter (partial put! s)]
      (try
        (swap! connections
               assoc remote-addr putter)
        (catch Exception ex
          (println "Failed to add new connection!\n" ex)
          (.printStackTrace ex)
          (throw ex)))
      (assert (identical? (@connections remote-addr) putter)))
    (let [cleanup (fn [_]
                    (swap! connections dissoc remote-addr))]
      (router-event-loop f s cleanup))))

(s/fdef request-response
        :args (s/cat :f (s/fspec
                         :args (s/cat :message bytes?)
                         ;; nil is the signal to close the connection
                         :ret any?))
        :ret (s/fspec :args (s/cat :s ::stream
                                   :info any?)))
(defn request-response
  "This works fine for request/response style messaging,
  but seems pretty useless for real async exchanges."
  [f]
  (fn [s info]
    (deferred/chain
      (deferred/loop []
        (-> (deferred/let-flow [msg (stream/take! s ::none)]
              (when-not (identical? ::none msg)
                (deferred/let-flow [msg' (deferred/future (f msg))
                                    result (put! s msg')]
                  (when result
                    (deferred/recur)))))
            (deferred/catch
                (fn [ex]
                  (println "Server Oops!\n" ex)
                  (put! s {:error (.getMessage ex)
                           :type (-> ex class str)})
                  ;; Q: Is this really what should happen?
                  (stream/close! s)))))
      (fn [_]
        ;; Actually, I should be able to clean up in here
        (println "Client connection really exited")))
    ;; That loop's running in the background.
    (println "req-rsp loop started")))

(defn close!
  "Closes a server instance"
  [^java.io.Closeable x]
  (.close x))

(s/fdef start-deferred-client!
  ;; The specs on those are very loose
  :args (s/or :full (s/cat :host string?
                           :port int?
                           :ssl? boolean?
                           :insecure? boolean?)
              :default (s/cat :host string?
                              :port int?))
  :ret stream/stream?)
(defn start-client!
  "Starts a dumb, limited TCP stream. Read/write clojure to/from it.

  Objects are limited to 16K of EDN serialization.

  So really just an example of how things should work."
  ([host port ssl? insecure?]
   (let [strm (tcp/client {:host host
                           :port port
                           :ssl? ssl?
                           :insecure? insecure?})
         source (stream/stream)
         sink (stream/stream)
         acc (atom {::pending 0
                    ::received []})]
     ;; Read EDN from the stream
     (stream/connect-via strm
                         (fn [msg]
                           (loop [rcvd (conj (::received @acc) msg)
                                  pending (::pending @acc)]
                             (if (>= (count rcvd) pending)
                               (let [[actual remaining] (split-at pending rcvd)
                                     s (String. (bytes actual))
                                     ;; FIXME: Error handling
                                     o (edn/read-string s)
                                     pending (as-> remaining x
                                               (take 2 x)
                                               (bit-or (bit-shift-left (first x) 8)
                                                       (second x)))
                                     success @(stream/try-put! source o 25 ::timed-out)]
                                 ;; FIXME: Error handling
                                 (comment
                                   (is success)
                                   (is (not= success ::timed-out)))
                                 (if success
                                   (recur (vec (drop 2 remaining))
                                          pending)
                                   ;; Signal that downstream closed
                                   nil))
                               (swap! acc
                                      assoc
                                      ::pending pending
                                      ::received rcvd))))
                         source)
     (stream/connect-via sink
                         (fn [o]
                           (let [s (pr-str o)
                                 bs (.getBytes s)
                                 n (count bs)
                                 l1 (mod n 256)
                                 l2 (quot n 256)
                                 len (byte-array [l2 l1])]
                             (stream/put! strm len)
                             (let [success @(stream/try-put! strm bs 25 ::timed-out)]
                               ;; FIXME: Error handling
                               (comment
                                 (is success)
                                 (is (not= success ::timed-out)))
                               (if success success nil))))
                         strm)
     (stream/splice sink source)))
  ([host port]
   (start-client! host port false false)))

(s/fdef start-server!
        :args (s/cat :handler (s/fspec :args (s/cat :stream ::stream
                                                    :info ::connection-info))
                     :port :zmq-socket/port)
        :ret ::server)
(defn start-server!
  "Starts a server that listens on port and calls handler"
  ([handler port ssl-context]
    (tcp/start-server
     (fn [s info]
       (println (java.util.Date.) "Outer server handler")
       (handler (comment (simplest s)) info))
     {:port port
      :ssl-context ssl-context}))
  ([handler port]
   (start-server! handler port nil)))
