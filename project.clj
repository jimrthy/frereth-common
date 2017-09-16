(defproject com.frereth/common "0.0.1-SNAPSHOT"
  :description "Pieces that the different Frereth parts share
TODO: This needs to be converted to either
a. boot
b. lein managed dependencies"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[aleph "0.4.3"]
                 #_[buddy/buddy-core "1.1.1"]  ;; Q: Is there any point to this now?
                 [clj-time "0.14.0"]
                 ;; Q: Does this make any sense in production?
                 ;; A: Well, it makes sense for the general runtime which
                 ;; is the primary goal.
                 ;; It seems to totally fit for frereth.client.
                 ;; And possibly for the server.
                 ;; Q: Does it make any sense for the renderer?
                 ;; A: Depends on whether that uses the client as a library
                 ;; or a stand-alone executable.
                 ;; As it stands: absolutely. Especially if I stick with a browser-
                 ;; based renderer
                 [com.cemerick/pomegranate "0.4.0" :exclusions [org.apache.httpcomponents/httpclient
                                                                org.apache.httpcomponents/httpcore
                                                                org.apache.maven.wagon/wagon-http
                                                                org.codehaus.plexus/plexus-utils]]
                 [com.taoensso/timbre "4.10.0" :exclusions [org.clojure/clojure
                                                           org.clojure/tools.reader]]
                 [im.chit/hara.event "2.5.10" :exclusions [org.clojure/clojure]]
                 [integrant "0.6.1"]
                 ;; They're up to 5.0.0.Alpha2, but that breaks aleph
                 [io.netty/netty-all "4.1.6.Final"]
                 ;; Because pomegranate and lein conflict.
                 ;; Try the latest versions to see how it works
                 [org.apache.maven.wagon/wagon-http "3.0.0"]
                 [org.apache.httpcomponents/httpcore "4.4.7"]
                 [org.apache.httpcomponents/httpclient "4.5.3"]

                 ;; Sticking with this version due to CIDER incompatabilities
                 [org.clojure/clojure "1.9.0-alpha17"]
                 [org.clojure/core.async "0.3.443" :exclusions [org.clojure/clojure
                                                                org.clojure/tools.analyzer]]
                 [org.clojure/tools.analyzer "0.6.9"]
                 [org.clojure/test.check "0.9.0"]
                 [org.clojure/tools.reader "1.1.0" :exclusions [org.clojure/clojure]]]
  :profiles {:dev {:dependencies [[integrant/repl "0.2.0"]
                                  [org.clojure/java.classpath "0.2.3"
                                   :exclusions [org.clojure/clojure]]
                                  [org.clojure/test.check "0.9.0"]
                                  [org.clojure/tools.namespace "0.2.11"]]
                   ;; Q: Why do I have tools.namespace under both dependencies and plugins?
                   :plugins [[org.clojure/tools.namespace "0.2.11" :exclusions [org.clojure/clojure]]]
                   :source-paths ["dev"]}
             :uberjar {:aot :all}}
  :repl-options {:init-ns user
                 :timeout 120000})
