(defproject com.frereth/common "0.0.1-SNAPSHOT"
  :description "Pieces that the different Frereth parts share"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  ;; Q: Could I totally pull datomic dependencies out of everything else?
  ;; A: Probably. But it would be a foolish choice. The web and client components
  ;; really shouldn't have access to that sort of thing.
  :dependencies [;; Q: Do I really want to choose this over clj-time?
                 [clojure.joda-time "0.6.0"]
                 ;; Q: Does this make any sense in production?
                 ;; A: Well, it makes sense for the general runtime which
                 ;; is the primary goal.
                 [com.cemerick/pomegranate "0.3.0" :exclusions [org.codehaus.plexus/plexus-utils]]
                 ;; For now, this next library needs to be distributed to
                 ;; a local maven repo.
                 ;; It seems like it should really take care of its handler
                 ;; ...except that very likely means native libraries, so
                 ;; it gets more complicated. Still, we shouldn't be worrying
                 ;; about details like jeromq vs jzmq here.
                 ;; Q: Does the reference to this really belong in here?
                 ;; After all, there's a pretty strong chance that "only"
                 ;; server and client will actually use it.
                 ;; Then again, if that happens, web will only inherit
                 ;; this through client. And, if it doesn't, renderer
                 ;; will need this to talk to the stand-alone "client."
                 ;; So the short answer is "Yes"
                 [com.jimrthy/cljeromq "0.1.1-clj-zmq-SNAPSHOT" :exclusions [com.stuartsierra/component
                                                                             org.clojure/clojure
                                                                             prismatic/schema]]
                 [com.jimrthy/component-dsl "0.1.1-SNAPSHOT" :exclusions [org.clojure/clojure]]
                 [com.taoensso/timbre "4.2.0" :exclusions [org.clojure/clojure
                                                           org.clojure/tools.reader]]
                 [fullcontact/full.async "0.9.0" :exclusions [org.clojure/clojure
                                                               org.clojure/core.async]]
                 ;; This has been deprecated.
                 ;; TODO: Switch to hara
                 [im.chit/ribol "0.4.1" :exclusions [org.clojure/clojure]]
                 [io.aviso/config "0.1.9" :exclusions [org.clojure/clojure
                                                       prismatic/schema]]
                 ;; This is screwing up EDN serialization
                 ;; In particular dates.
                 ;; TODO: Make it ignore those
                 ;; Actually, by now, it seems like there should really be a newer
                 ;; version that fixes the problem so it's no longer an issue
                 #_[mvxcvi/puget "0.8.1" :exclusions [org.clojure/clojure]]
                 [org.clojure/clojure "1.8.0-RC4"]
                 [org.clojure/core.async "0.2.374" :exclusions [org.clojure/clojure]]
                 [org.clojure/tools.reader "1.0.0-alpha1" :exclusions [org.clojure/clojure]]
                 [prismatic/plumbing "0.5.2"]
                 [prismatic/schema "1.0.4"]]

  :jvm-opts [~(str "-Djava.library.path=/usr/local/lib:" (System/getenv "LD_LIBRARY_PATH"))]

  :plugins []

  :profiles {:dev {:dependencies [[org.clojure/java.classpath "0.2.3"
                                   :exclusions [org.clojure/clojure]]
                                  [org.clojure/tools.namespace "0.2.11" :exclusions [org.clojure/clojure]]]
                   :source-paths ["dev"]}
             :uberjar {:aot :all}}
  :repl-options {:init-ns user})
