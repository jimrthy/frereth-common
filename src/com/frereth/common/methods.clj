(ns com.frereth.common.methods
  "Common Lisp-style standard method combinations

Please look up
http://gigamonkeys.com/book/object-reorientation-generic-functions.html
for an explanation of what I'm trying to set up here.

The way ::around works isn't exactly intuitive. Please
see methods-test under the unit tests for an example.

My original motivation was an HTTP dispatcher that really
needed a RING middleware chain when I'm a little burned out
on debugging what happens in the middle of those in which
order.

Time will tell whether this proves to be an improvement."
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
(s/def ::around (s/fspec :args (s/cat :next (s/or :around ::around
                                                  :before ::before
                                                  :primary ::primary
                                                  :after ::after)
                                      :x any?)
                         :ret any?))
;; Actually, we have to have at least one of these keys
;; TODO: Work out what the spec looks like for that
(s/def ::standard-cl-method-combination (s/keys :opt [::around ::before ::primary ::after]))
(s/def ::standard-cl-method (s/coll-of ::standard-cl-method-combination))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Internal Helpers

(s/fdef process-around
        :args (s/cat :chain ::standard-cl-methods
                     :nested ::around
                     :x any?)
        :ret any?)
(defn process-around
  "Chain of calls that wraps around the :before/:primary/:after sequence"
  [chain nested x]
  (let [arounds (filter some? (map ::around chain))
        wrapper (reduce (fn [next-method f]
                          (fn [y]
                            (f next-method y)))
                        nested
                        arounds)]
    (wrapper x)))

(s/fdef pre-process
        :args (s/cat :handler-chain ::standard-cl-methods
                     :x any?)
        :ret (s/nilable ::ring-request))
(defn pre-process
  "Process the ::before members of handler-chain in order."
  [handler-chain x]
  (reduce (fn [acc before]
            (before acc))
          x
          (filter some? (map ::before handler-chain))))

(s/fdef process-primary
        :args (s/cat :handler-chain ::standard-cl-methods
                     :x any?)
        :ret any?)
(defn process-primary
  "Chain of probably-recursive calls between the :before and :after methods"
  [handler-chain x]
  (let [primaries (filter some? (map ::primary handler-chain))
        chain (reduce (fn [next-method f]
                        ;; Return a function that will be called
                        ;; with the return value from the previous
                        ;; value and, in turn, call this primary
                        ;; method with
                        ;; a) the function to call next
                        ;; b) that "previous" value
                        (fn [y]
                          (f next-method y)))
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
  (let [handlers (reverse (filter some? (map ::after handler-chain)))]
    (reduce (fn [acc f]
              (f acc))
            x
            handlers)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

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
