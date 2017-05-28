(ns com.frereth.common.methods
  "Common Lisp-style standard method combinations"
  (:require [clojure.spec :as s]
            [clojure.tools.logging :as log])
  (:import clojure.lang.ExceptionInfo))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Specs

;; Q: Do I know anything meaningful about this?
(s/def ::before (s/fspec :args (s/cat :x any?)
                         :ret any?))
(s/def ::primary (s/fspec :args (s/cat :next ::primary
                                       :x any?)
                          :ret any?))
;; Q: Do I know anything meaningful about this?
(s/def ::after (s/fspec :args (s/cat :x any?)
                        :ret any?))
;; Q: Do I want to drag these into the mix?
;; It seems like they're really intended for doing things
;; like setting up error handlers and dynamic environments.
;; A: So definitely yes.
;; TODO: Make that happen
(s/def ::around (s/fspec :args (s/cat :next (s/or :around ::around
                                                  :before ::before
                                                  :primary ::primary
                                                  :after ::after)
                                      :x any?)))
;; TODO: Actually, we have to have at least one of these keys
(s/def ::standard-cl-method-combination (s/keys :opt [::around ::before ::primary ::after]))
(s/def ::standard-cl-method (s/coll-of ::standard-cl-method-combination))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Internal Helpers

(s/fdef process-around
        :args (s/cat :handler-chain ::standard-cl-methods
                     :x any?)
        :ret any?)
(defn process-around
  "Chain of calls that wraps around the :before/:primary/:after sequence"
  [chain internals x]
  (let [arounds (filter ::around chain)
        wrapper (reduce (fn [next-method f]
                          (letfn [(call-next-method
                                    [y]
                                    (next-method y))]
                            (fn [y]
                              (println "Around:" y)
                              (f y))))
                        internals
                        arounds)]
    (wrapper x)))

(s/fdef pre-process
        :args (s/cat :handler-chain ::standard-cl-methods
                     :x any?)
        :ret (s/nilable ::ring-request))
(defn pre-process
  "Process the ::before members of handler-chain in order.

Each link in the chain can return nil to indicate not-found
or throw an exception to trigger an error response.

Otherwise, it should return a (possibly modified) request to
pass to the next handler in the chain"
  [handler-chain x]
  (reduce (fn [acc before]
            (println "Pre:" acc)
            (before acc))
          x
          (filter ::before handler-chain)))

(declare call-next-method)

(s/fdef process-primary
        :args (s/cat :handler-chain ::standard-cl-methods
                     :x any?)
        :ret any?)
(defn process-primary
  "Chain of probably-recursive calls between the :before and :after methods"
  [handler-chain x]
  (let [primaries (filter ::primary handler-chain)
        chain (reduce (fn [next-method f]
                        ;; The main point is to override call-next-method
                        ;; dynamically, so each f can call the override as
                        ;; needed.
                        ;; I think this is how *binding* is meant to work,
                        ;; but we'd already be out of that context before
                        ;; it ever got called.
                        ;; So try this approach instead
                        (let [call-next-method next-method]
                          ;; This seems a bit silly
                          (fn [y]
                            (println "Primary:" y)
                            (f y))))
                      identity
                      (reverse primaries))]
    (chain x)))

(s/fdef post-process
        :args (s/cat :handler-chain ::standard-cl-methods
                     :x any?)
        :ret any?)
(defn post-process
  "Note that these need to be processed in an inside-out order"
  [handler-chain x]
  (let [handlers (reverse (filter ::after handler-chain))]
    (reduce (fn [acc f]
              (println "Post:" acc)
              (f acc))
            x
            handlers)))

(s/fdef build-common-lisp-dispatcher
        :args (s/cat :chain ::standard-cl-method)
        :ret (s/fspec :args (s/cat :x any?)
                      :ret any?))
(defn build-common-lisp-dispatcher
  [chain]
  (fn [x]
    (let [nested (fn [y]
                   (->> y
                        (pre-process chain)
                        (process-primary chain)
                        (post-process chain)))]
      (process-around chain nested x))))
