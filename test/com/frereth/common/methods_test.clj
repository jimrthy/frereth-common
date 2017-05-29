(ns com.frereth.common.methods-test
  (:require [clojure.spec :as s]
            [clojure.test :refer (deftest is testing)]
            [com.frereth.common.methods :as m]))

(deftest basic-idea
  (let [dscr [{::m/around (fn [x]
                            (println "pre-around:" x)
                            (let [result (m/call-next-method x)]
                              (println "post-around:" result)
                              result))}
              {::m/before (fn [x]
                            (println "Before:" x)
                            (+ 2 x))}
              {::m/primary (fn [x]
                             (println "Main:" x)
                             (m/call-next-method (* 2 x)))}
              {::m/after (fn [x]
                           (println "After:" x)
                           (- 2 x))}]
        f (m/build-common-lisp-dispatcher dscr)]
    (is (= 10 (f 4)))
    (comment (is (= 12 (f 5))))))


(deftest actual-point
  (let [dscr [{::m/before (fn [x]
                            (is (= ::pre-1 x))
                            ::pre-2)}
              {::m/around (fn [x]
                            (is (= ::pre x))
                             (try
                               (m/call-next-method ::pre-1)
                               (is false "Shouldn't have succeeded")
                               (catch clojure.lang.ExceptionInfo ex
                                 (is (= {::problem ::expected} (.getData ex))))))}

              {::m/before (fn [x]
                            (is (= ::pre-1 x))
                            ::primary)
               ::m/primary (fn [x]
                             (is (= ::primary x))
                             ::after)
               ::m/after (fn [x]
                           (is (= ::after x))
                           ::after-1)}
              {::m/after (fn [x]
                           (is (= ::after-1 x))
                           (throw (ex-info "around should catch this"
                                           {::problem ::expected})))}]
        f (m/build-common-lisp-dispatcher dscr)]
    (f ::pre)))
