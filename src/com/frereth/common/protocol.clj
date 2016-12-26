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
  (fn [_]
    (let [spec (::spec dscr)
          msg "This needs to be generated, somehow"]
      (stream/try-put! strm msg timeout ::timeout))))

(defmethod -client-step-builder ::server->client
  [strm timeout dscr]
  [(fn [sent]
     (if (not= sent ::timeout)
       (stream/try-take! strm ::drained timeout ::timeout)))
   (fn [response]
     (let [spec (::spec dscr)]
       (when-not (s/valid? spec response)
         (throw (ex-info (::problem dscr) {:problem (s/explain spec response)})))))])

(defn protocol-agreement
  [strm builder timeout step-descriptions]
  (let [steps (mapcat (partial builder strm timeout)
                      step-descriptions)]
    (-> strm
        (d/chain steps)
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
    ::spec #(= % ::ohai)
    ::problem "Illegal greeting"}
   {::direction ::server->client
    ::spec #(= % ::orly?)
    ::problem "Broken handshake"}
   {::direction ::client->server
    ::spec ::icanhaz}
   {::direction ::server->client
    ::spec (s/or :match (s/and ::protocol-versions
                               #(= 1 (count %)))
                 :fail #(= % ::lolz))}])
