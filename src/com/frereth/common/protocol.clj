(ns com.frereth.common.protocol
  "Centralize the communications hand-shake layer"
  (:require [clojure.spec :as s]
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
  (s/tuple #(= % ::icanhaz) (s/keys :req [::protocol-versions])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;; Internal Implementation

(defmethod -client-step-builder ::client->server
  [strm timeout dscr]
  [(fn [x]
      (let [spec (::spec dscr)
            generator (::client-gen dscr)
            msg (generator x)]
        (stream/try-put! strm msg timeout ::timeout)))])

(defn build-prev-timeout-checker
  [strm timeout]
  (fn [sent]
    (if (not= sent ::timeout)
      (stream/try-take! strm ::drained timeout ::timeout))))

(defn build-received-validator
  [dscr]
  (fn [recvd]
    (if recvd
      (let [spec (::spec dscr)]
        (when-not (s/valid? spec recvd)
          (throw (ex-info (::problem dscr) {:problem (s/explain spec recvd)}))))
      (throw (ex-info "Stream closed unexpectedly" {})))))

(defmethod -client-step-builder ::server->client
  [strm timeout dscr]
  ;; This assumes that the client always initiates the sequence.
  ;; The first thing we always have to do for each step is to
  ;; verify that the
  [(build-prev-timeout-checker strm timeout)
   (build-received-validator dscr)])

(defmethod -server-step-builder ::client->server
  [strm timeout dscr]
  (let [responder (if-let [r (::server-gen dscr)]
                    r
                    (build-received-validator dscr))]
    (if-not (contains? dscr ::initial-step)
      [stream/take! (build-prev-timeout-checker strm timeout) responder]
      [stream/take! responder])))

(defmethod -server-step-builder ::server->client
  [strm timeout dscr]
  [(build-prev-timeout-checker strm timeout)
   (::server-gen dscr)])

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
        (d/catch ExceptionInfo #(println "Whoops:" %))
        (d/catch RuntimeException #(println "How'd I miss:" %))
        (d/catch Exception #(println "Major oops:" %)))))

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
  "Declaration of the handshake to allow client and server to agree on handling the next pieces"
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
    ::client-gen (fn [_] {:frereth [[0 0 1]]})}
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
                     (-> versions :frereth last)
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
