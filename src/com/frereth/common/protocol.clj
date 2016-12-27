(ns com.frereth.common.protocol
  "Centralize the communications hand-shake layer"
  (:require [clojure.spec :as s]
            [clojure.spec.gen :as gen]
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
  [(fn [_]
      (let [spec (::spec dscr)
            generator-override (::client-gen dscr)
            msg (gen/generate (if generator-override
                                (s/with-gen (s/gen spec)
                                  generator-override)
                                (s/gen spec)))]
        (stream/try-put! strm msg timeout ::timeout)))])

(defmethod -client-step-builder ::server->client
  [strm timeout dscr]
  [(fn [sent]
     (if (not= sent ::timeout)
       (stream/try-take! strm ::drained timeout ::timeout)))
   (fn [response]
     (let [spec (::spec dscr)]
       (when-not (s/valid? spec response)
         (throw (ex-info (::problem dscr) {:problem (s/explain spec response)})))))])

(defn build-steps
  [builder strm timeout step-descriptions]
  (mapcat (partial builder strm timeout)
          step-descriptions))

(defn protocol-agreement
  [strm builder timeout step-descriptions]
  (let [lazy-steps (build-steps builder strm timeout step-descriptions)
        steps (vector lazy-steps)]
    ;; This next line's failing with
    ;; IllegalArgumentException Don't know how to create ISeq
    ;; from: com.frereth.common.protocol$eval29647$fn__29648$fn__29649
    ;; clojure.lang.RT.seqFrom (RT.java:547)
    (println "Getting ready to chain together" steps)
    (println "onto" strm)
    (-> (apply d/chain strm steps)
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
    ::client-gen #(s/gen #{::ohai})
    ::problem "Illegal greeting"}
   {::direction ::server->client
    ::spec (s/and keyword? #(= % ::orly?))
    ::server-gen #(s/gen #{::orly?})
    ::problem "Broken handshake"}
   {::direction ::client->server
    ::spec ::icanhaz
    ::client-gen #(s/gen {:frereth [0 0 1]})}
   {::direction ::server->client
    ;; This spec is too loose.
    ;; It shall pick one of the versions suggested
    ;; by the client in the previous step.
    ;; This approach seems to fall apart here
    ::spec (s/or :match (s/and ::protocol-versions
                               #(= 1 (count %)))
                 :fail #(= % ::lolz))
    ::server-gen #(s/gen {:frereth [0 0 1]})}])

(defn client-version-protocol
  [strm timeout]
  (client-protocol-agreement strm timeout (version-contract)))

(comment
  ;; Trying to call client-version-protocol is throwing a fairly
  ;; cryptic exception/stack-trace that seems to center around
  ;; this:
  (build-steps -client-step-builder (future) 500 (version-contract))
  (-client-step-builder (future) 500 (first (version-contract)))
  (-client-step-builder (future) 500 (second (version-contract)))
  (mapcat (partial -client-step-builder (future) 500) (version-contract))
  )
