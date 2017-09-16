(ns user
  "TODO: Really should figure out a way to quit duplicating this everywhere"
  (:require [clojure.core.async :as async]
            [clojure.edn :as edn]
            [clojure.inspector :as i]
            [clojure.java.io :as io]
            [clojure.pprint :refer (pprint)]
            [clojure.repl :refer :all]  ; dir is very useful
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [clojure.string :as string]
            [clojure.test :as test]
            [clojure.tools.namespace.repl :refer (refresh refresh-all)]
            [com.frereth.common.aleph :as aleph]
            [com.frereth.common.communication :as com-comm]
            [com.frereth.common.config :as cfg]
            [com.frereth.common.system :as sys]
            [com.frereth.common.util :as util]
            [clj-time.core :as dt]
            [hara.event :refer (raise)]
            [integrant.repl :refer (clear go halt init reset reset-all)]))

(def +frereth-component+
  "Just to help me track which REPL is which"
  'common)

;; Because this seems to be the only namespace I ever actually
;; use in here, and I'm tired of typing it out because it's
;; ridiculously long
(require '[com.frereth.common.async-zmq-test :as azt])

(def system nil)

(defn ctor
  "Constructs the current development system."
  []
  (set! *print-length* 50)

  (sys/build-event-loop-description {::sys/client-keys [:FIXME "generate this"]
                                     ::sys/event-loop-name "For working in REPL"
                                     ::sys/server-keys [:FIXME "ditto"]
                                     ::sys/url {:what "goes here?"}}))
(integrant.repl/set-prep! ctor)

(println "Now use go, halt, and reset to accomplish things")
