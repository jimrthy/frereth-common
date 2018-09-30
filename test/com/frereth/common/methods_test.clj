(ns com.frereth.common.methods-test
  (:require [clojure.spec.alpha :as s]
            [clojure.test :refer (deftest is testing)]
            [com.frereth.common.methods :as m]))

(deftest basic-idea
  (let [dscr [{::m/around (fn [call-next-method x]
                            (let [result (call-next-method x)]
                              result))}
              {::m/before (fn [x]
                            (+ 2 x))}
              {::m/primary (fn [call-next-method x]
                             (call-next-method (* 2 x)))}
              {::m/after (fn [x]
                           (- x 2))}]
        f (m/build-common-lisp-dispatcher dscr)]
    (is (= 10 (f 4)))
    (is (= 12 (f 5)))))

(deftest actual-point
  (let [dscr [{::m/before (fn [x]
                            (is (= ::pre-2 x))
                            ::pre-3)}
              ;; Around methods get called before
              ;; the before methods.
              ;; call-next-method returns whatever the
              ;; last after method returns.
              ;; TODO: Need similar tests that leave
              ;; out inner pieces to verify (i.e.
              ;; add one that doesn't have ::before,
              ;; another that skips the ::after,
              ;; and another that skips the ::primary).
              {::m/around (fn [call-next-method x]
                            (is (= ::pre-1 x))
                             (try
                               (call-next-method ::pre-2)
                               (is false "Shouldn't have succeeded")
                               (catch clojure.lang.ExceptionInfo ex
                                 (is (= {::problem ::expected} (.getData ex))))))}

              {::m/before (fn [x]
                            (is (= ::pre-3 x))
                            ::primary)
               ::m/primary (fn [call-next-method x]
                             (is (= ::primary x))
                             (call-next-method ::after-1))
               ::m/after (fn [x]
                           (is (= ::after-2 x))
                           (throw (ex-info "around should catch this"
                                           {::problem ::expected})))}
              {::m/after (fn [x]
                           (is (= ::after-1 x))
                           ::after-2)}]
        f (m/build-common-lisp-dispatcher dscr)]
    (f ::pre-1)))
