(ns com.frereth.common.protocol
  "Centralize the communications hand-shake layer"
  (:require [clojure.pprint :refer (pprint)]
            [clojure.spec :as s]
            [manifold.deferred :as d]
            [manifold.stream :as stream])
  (:import clojure.lang.ExceptionInfo))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;; Specs

(defmulti -client-step-builder
  "Build an individual step handler in a protocol chain"
  (fn [_ _ dscr]
    (::direction dscr)))

(defmulti -server-step-builder
  "Build an individual step handler in a protocol chain"
  (fn [_ _ dscr]
    (::direction dscr)))

(s/def ::version (s/or :ints (s/tuple int? int? int?)
                       :build-string (s/tuple int? int? string?)
                       :strings (s/tuple string? string? string?)
                       :string string?))

(s/def ::versions (s/coll-of ::version))

;; Keyword names of protocols and the supported versions
;; Note that, really, this should specifically be namespaced keywords
(s/def ::protocol-versions (s/map-of keyword? ::versions))

(s/def ::icanhaz
  ;; Note that this is really making a request for some sort of resource.
  ;; In this initial example, the server lists which further protocol versions
  ;; it supports, requesting the server to pick one.
  (s/tuple #(= % ::icanhaz) ::protocol-versions))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;; Step Builders

(defn build-sender
  [which strm timeout dscr]
  [(fn [x]
     (let [generator (which dscr)
           msg (generator x)]
       (stream/try-put! strm msg timeout ::timeout)))])

(defn build-prev-timeout-checker
  [side strm timeout]
  (fn [sent]
    (println "Checking that previous send didn't timeout. Result:" sent)
    (if (not= sent ::timeout)
      (let [response
            (stream/try-take! strm ::drained timeout ::timeout)]
        (println "Taking the response:" response)
        response)
      (throw (ex-info (str side " timed out trying to send previous step"))))))

(defn build-received-validator
  [side dscr]
  (fn [recvd]
    (if recvd
      (if (and (not= recvd ::timeout)
               (not= recvd ::drained))
        (let [spec (::spec dscr)]
          (println "Potentially have something interesting at the" side recvd)
          (when-not (s/valid? spec recvd)
            (throw (ex-info (str "Invalid input for current "
                                 side " state: " (::problem dscr))
                            {:problem (s/explain-data spec recvd)
                             :expected (s/describe spec)
                             :actual recvd})))
          recvd)
        (throw (ex-info (str side " receiving failed: " recvd) dscr)))
      (throw (ex-info "Stream closed unexpectedly" dscr)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;; Internal Implementation

(defmethod -client-step-builder ::client->server
  [strm timeout dscr]
  (build-sender ::client-gen strm timeout dscr))

(defmethod -client-step-builder ::server->client
  [strm timeout dscr]
  ;; This assumes that the client always initiates the sequence.
  ;; The first thing we always have to do for each step is to
  ;; verify that the
  [(build-prev-timeout-checker "client" strm timeout)
   (build-received-validator "client" dscr)])

(defmethod -server-step-builder ::client->server
  [strm timeout dscr]
  (let [responder (if-let [r (::server-gen dscr)]
                    r
                    (build-received-validator "server" dscr))
        take! stream/take! #_(fn [strm]
                              (throw (ex-info "What do we have here?" {:rcvd x})))]
    ;; First server step didn't send anything, since the client initiated comms.
    ;; So there's no need to check whether that send timed out
    (if (contains? dscr ::initial-step)
      [take! responder]
      [(build-prev-timeout-checker "server" strm timeout) responder])))

(defmethod -server-step-builder ::server->client
  [strm timeout dscr]
  (build-sender ::server-gen strm timeout dscr))

(defn build-steps
  "Create a list of the functions to add to the chain

Q: Is this really cleaner than setting up a sequence of
on-realized handlers?"
  [builder strm timeout step-descriptions]
  (mapcat (partial builder strm timeout)
          step-descriptions))

(defn protocol-agreement
  [strm builder timeout step-descriptions]
  (let [lazy-steps (build-steps builder strm timeout step-descriptions)]
    (-> (apply d/chain strm lazy-steps)
        ;; Note that this hierarchical exception handling
        ;; was implemented in a way that winds up with each
        ;; specific kind of exception also being handled by
        ;; the more general handler.
        ;; That's annoying.
        (d/catch ExceptionInfo (fn [ex]
                                 (println "Whoops:" ex "\nDetails:\n")
                                 (pprint (.getData ex))
                                 (throw ex)))
        (d/catch RuntimeException (fn [ex]
                                    (println "How'd I miss:" ex
                                             "\nBuilder should tell you the side:" builder)
                                    (throw ex)))
        (d/catch Exception (fn [ex]
                             (println "Major oops:" ex)
                             ;; The error disappears even
                             ;; when I bubble it up like this.
                             ;; That moves this from "annoying"
                             ;; to "absolutely useless"
                             ;; Maybe I'm just missing a fundamental
                             ;; point?
                             (throw ex))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;; Public
;;;; Actually, these probably shouldn't be public in here.
;;;; They belong in a different ns that exposes the protocol building
;;;; pieces so another ns can combine these with the actual protocol
;;;; definitions.

;;;; Then again, this is small/simple enough that that probably isn't
;;;; worth the effort

(defn client-protocol-agreement
  "Generates the client-side protocol handler"
  [strm timeout msgs]
  (protocol-agreement strm -client-step-builder timeout msgs))

(defn server-protocol-agreement
  "Generates the server-side of a protocol handler"
  [strm timeout msgs]
  (protocol-agreement strm -server-step-builder timeout msgs))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;; Protocols
;;;; These really do belong in here. The rest should probably go into a
;;;; nested impl ns

(defn version-contract
  "Declaration of the handshake to allow client and server to agree on handling the next pieces

Honestly, this is describing a pair of FSMs."
  []
  [{::direction ::client->server
    ::initial-step true
    ::spec #(= % ::ohai)
    ::client-gen (fn [_] ::ohai)
    ::problem "Illegal greeting"}
   {::direction ::server->client
    ::spec (s/and keyword? #(= % ::orly?))
    ::server-gen (fn [_] ::orly?)
    ::problem "Broken handshake"}
   {::direction ::client->server
    ::spec ::icanhaz
    ;; Here's one failure: apparently s/tuple deliberately does not allow
    ;; lists.
    ;; Which is exactly what I want here.
    ::client-gen (fn [_] (vector #_list ::icanhaz {:frereth [[0 0 1]]}))}
   {::direction ::server->client
    ;; This spec is too loose.
    ;; It shall pick one of the versions suggested
    ;; by the client in the previous step.
    ;; Q: How can I document that formally?
    ::spec (s/or :match (s/and ::protocol-versions
                               #(= 1 (count %)))
                 :fail #(= % ::lolz))
    ::server-gen (fn [versions]
                   (if (contains? versions :frereth)
                     {:frereth (-> versions :frereth last)}
                     ::lolz))}])

(defn client-version-protocol
  [strm timeout]
  (client-protocol-agreement strm timeout (version-contract)))

(defn server-version-protocol
  [strm timeout]
  (server-protocol-agreement strm timeout (version-contract)))

(comment
  (server-version-protocol (stream/stream) 500)
  (client-version-protocol (stream/stream) 500)
  (client-protocol-agreement (stream/stream) 500 (version-contract))
  (let [deferred (stream/stream)
        steps (build-steps -client-step-builder deferred 500 (version-contract))]
    (apply d/chain deferred steps))

  )
