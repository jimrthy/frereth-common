(def project 'com.frereth/common)
(def version "0.0.1-SNAPSHOT")

(set-env! :dependencies   '[[adzerk/boot-test "RELEASE" :scope "test"]
                            [aleph "0.4.3"]
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
                            [com.cemerick/pomegranate "0.4.0" :exclusions [#_org.apache.httpcomponents/httpclient
                                                                           #_org.apache.httpcomponents/httpcore
                                                                           #_org.apache.maven.wagon/wagon-http
                                                                           #_org.codehaus.plexus/plexus-utils]]
                            ;; Q: Do I want to stick with the clojure.tools.logging approach?
                            [com.taoensso/timbre "4.10.0" :exclusions [org.clojure/clojure
                                                                       org.clojure/tools.reader]]
                            [im.chit/hara.event "2.5.10" :exclusions [org.clojure/clojure]]
                            [integrant "0.6.1"]
                            [integrant/repl "0.2.0" :scope "test"]
                            ;; They're up to 5.0.0.Alpha2, but that breaks aleph
                            [io.netty/netty-all "4.1.6.Final"]
                            #_[org.apache.logging.log4j/log4j-core "2.8.2" :scope "test"]
                            #_[org.apache.logging.log4j/log4j-1.2-api "2.8.2" :scope "test"]

                            ;; Sticking with this version due to CIDER incompatabilities
                            [org.clojure/clojure "1.9.0-alpha17"]
                            [org.clojure/core.async "0.3.443" :exclusions [org.clojure/clojure
                                                                           org.clojure/tools.analyzer]]
                            [org.clojure/java.classpath "0.2.3"
                             :exclusions [org.clojure/clojure] :scope "test"]
                            [org.clojure/spec.alpha "0.1.123"]
                            [org.clojure/tools.analyzer "0.6.9"]
                            [org.clojure/test.check "0.9.0" :scope "test"]
                            [org.clojure/tools.logging "0.4.0"]
                            [org.clojure/tools.reader "1.1.0" :exclusions [org.clojure/clojure]]
                            [samestep/boot-refresh "0.1.0" :scope "test"]
                            [tolitius/boot-check "0.1.4" :scope "test"]]
          :project 'com.frereth/common
          :resource-paths #{"src"}
          :source-paths   #{"dev" "dev-resources" "test"})

(task-options!
 aot {:namespace   #{'com.frereth.common.system}}
 pom {:project     project
      :version     version
      :description "Shared frereth components"
      ;; TODO: Add a real website
      :url         "https://github.com/jimrthy/frereth-common"
      :scm         {:url "https://github.com/jimrthy/frereth-common"}
      :license     {"Eclipse Public License"
                    "http://www.eclipse.org/legal/epl-v10.html"}}
 jar {:file        (str "common-" version ".jar")})

(require '[samestep.boot-refresh :refer [refresh]])
(require '[tolitius.boot-check :as check])

(deftask build
  "Build the project locally as a JAR."
  [d dir PATH #{str} "the set of directories to write to (target)."]
  ;; Note that this approach passes the raw command-line parameters
  ;; to -main, as opposed to what happens with `boot run`
  ;; TODO: Eliminate this discrepancy
  (let [dir (if (seq dir) dir #{"target"})]
    (comp (javac) (aot) (pom) (uber) (jar) (target :dir dir))))

(deftask local-install
  "Create a jar to go into your local maven repository"
  [d dir PATH #{str} "the set of directories to write to (target)."]
  (let [dir (if (seq dir) dir #{"target"})]
    (comp (pom) (jar) (target :dir dir) (install))))

(deftask cider-repl
  "Set up a REPL for connecting from CIDER"
  []
  ;; Just because I'm prone to forget one of the vital helper steps
  (comp (cider) (javac) (repl)))

(deftask run
  "Run the project."
  [f file FILENAME #{str} "the arguments for the application."]
  ;; This is a leftover template from another project that I
  ;; really just copy/pasted over.
  ;; Q: Does it make any sense to keep it around?
  (require '[frereth-cp.server :as app])
  (apply (resolve 'app/-main) file))

(require '[adzerk.boot-test :refer [test]])
