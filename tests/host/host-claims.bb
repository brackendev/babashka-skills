;; Checks the Babashka host behavior that the babashka skill states, against the
;; bb on PATH. The Iron Laws BB-1 to BB-4 and the host differences in
;; references/host-differences.md rest on these results, and the Babashka
;; README and book disagree with some of them. When an upgrade changes a result,
;; this suite fails, and the skill text must be checked against the new behavior.
;;
;; Usage: bb host-claims.bb
(require '[babashka.fs :as fs]
         '[babashka.process :as process]
         '[clojure.string :as str])

(def here (fs/parent (fs/absolutize *file*)))
(load-file (str (fs/path here ".." "hooks" "support.bb")))
(alias 'support 'hook-test-support)

(defn bb
  "Runs bb with the arguments in dir and returns :exit, :out, :err, and :ms, the
  wall-clock milliseconds until bb exited."
  [{:keys [dir env]} & args]
  (let [started (System/nanoTime)
        result (apply process/sh (cond-> {:err :string}
                                   dir (assoc :dir dir)
                                   env (assoc :extra-env env))
                      "bb" args)]
    (assoc (select-keys result [:exit :out :err])
           :ms (quot (- (System/nanoTime) started) 1000000))))

(defn bb-eval [code] (bb {} "-e" code))

(defn out [code] (str/trim (:out (bb-eval code))))

(defn error-message [code] (:err (bb-eval code)))

(def work (support/temp-directory))

(defn marker [name] (str (fs/path work name)))

(defn eventually-exists?
  "True when the file exists after waiting longer than any background work in
  these checks sleeps."
  [path]
  (Thread/sleep 1500)
  (fs/exists? path))

;; BB-1: bb exits without waiting for daemon threads.

(let [file (marker "future")
      {:keys [exit ms]} (bb-eval (str "(future (Thread/sleep 500) (spit \"" file "\" \"ran\")) (println :main)"))]
  (support/check (and (zero? exit) (< ms 500)) "bb exits before an undereffed future finishes")
  (support/check (not (eventually-exists? file)) "bb kills an undereffed future at exit"))

(let [{:keys [exit err]} (bb-eval "(future (throw (ex-info \"boom\" {}))) (Thread/sleep 100) (println :main)")]
  (support/check (and (zero? exit) (not (str/includes? err "boom")))
                 "an exception in an undereffed future is lost and bb exits 0"))

(support/result "42" (out "(println @(future (Thread/sleep 100) 42))") "dereferencing a future waits for its result")

(let [file (marker "send-off")]
  (bb-eval (str "(send-off (agent 0) (fn [_] (Thread/sleep 500) (spit \"" file "\" \"ran\")))"))
  (support/check (not (eventually-exists? file)) "bb kills pending send-off work at exit"))

(let [file (marker "send")
      {:keys [ms]} (bb-eval (str "(send (agent 0) (fn [_] (Thread/sleep 500) (spit \"" file "\" \"ran\")))"))]
  (support/check (and (>= ms 500) (fs/exists? file))
                 "bb waits for pending send work, because the send pool uses non-daemon threads"))

(let [file (marker "go")]
  (bb-eval (str "(require '[clojure.core.async :as a]) (a/go (Thread/sleep 500) (spit \"" file "\" \"ran\"))"))
  (support/check (not (eventually-exists? file)) "bb kills an untaken go block at exit"))

(let [file (marker "thread")]
  (bb-eval (str "(require '[clojure.core.async :as a]) (a/thread (Thread/sleep 500) (spit \"" file "\" \"ran\"))"))
  (support/check (not (eventually-exists? file)) "bb kills an untaken a/thread at exit"))

(let [file (marker "process")
      {:keys [ms]} (bb-eval (str "(require '[babashka.process :as p]) "
                                 "(p/process [\"sh\" \"-c\" \"sleep 0.5; touch " file "\"])"))]
  (support/check (< ms 500) "bb exits before an undereffed process finishes")
  (support/check (eventually-exists? file) "an undereffed process keeps running after bb exits"))

;; go blocks run on virtual threads, and blocking inside one works.

(support/result "true" (out "(require '[clojure.core.async :as a]) (println (a/<!! (a/go (.isVirtual (Thread/currentThread)))))")
                "a go block runs on a virtual thread")
(support/result "7" (out (str "(require '[clojure.core.async :as a]) "
                              "(let [c (a/chan)] (a/thread (Thread/sleep 100) (a/>!! c 7)) (println (a/<!! (a/go (a/<!! c)))))"))
                "a blocking take inside a go block returns the value")

;; BB-2: the first of :clj and :bb wins.

(support/result ":clj-branch :bb-branch" (out "(println #?(:clj :clj-branch :bb :bb-branch) #?(:bb :bb-branch :clj :clj-branch))")
                "bb evaluates whichever of :clj and :bb comes first in a reader conditional")

;; BB-3: run-tests does not set the exit code.

(support/result 0 (:exit (bb-eval "(require '[clojure.test :as t]) (t/deftest fails (t/is (= 1 2))) (t/run-tests)"))
                "run-tests with a failing test exits 0")

;; BB-4: only shell checks the exit code.

(support/result 1 (:exit (bb-eval "(require '[babashka.process :as p]) (p/shell \"false\")")) "shell throws on a nonzero exit")
(support/result "1" (out "(require '[babashka.process :as p]) (println (:exit (p/shell {:continue true} \"false\")))")
                "shell with :continue true returns a nonzero exit without throwing")
(support/result "1" (out "(require '[babashka.process :as p]) (println (:exit (p/sh \"false\")))")
                "sh returns a nonzero exit without throwing")
(support/result "1" (out "(require '[babashka.process :as p]) (println (:exit @(p/process \"false\")))")
                "process returns a nonzero exit without throwing")
(support/check (not (zero? (:exit (bb-eval "(require '[babashka.process :as p]) (p/check (p/process \"false\"))"))))
               "check throws on a nonzero exit")

;; Command strings, environment, and process records.

(support/result "[\"echo\" \"its\"]" (out "(require '[babashka.process :as p]) (prn (p/tokenize \"echo it's\"))")
                "tokenize removes a quote character and splits on spaces")
(support/result "[\"ls\" \"my\" \"dir\"]"
                (out "(require '[babashka.process :as p]) (prn (:cmd (p/process {:out :string} (str \"ls \" \"my dir\"))))")
                "a value joined into the command string becomes several arguments")
(support/result "[\"ls\" \"my dir\"]"
                (out "(require '[babashka.process :as p]) (prn (:cmd (p/process {:out :string} \"ls\" \"my dir\")))")
                "a value passed as its own argument stays one argument")
(let [{:keys [out]} (bb {:env {"HOST_CLAIMS_VAR" "set"}} "-e"
                        (str "(require '[babashka.process :as p]) "
                             "(println (:out (p/sh {:env {\"OTHER\" \"1\"}} \"sh\" \"-c\" \"echo [$HOST_CLAIMS_VAR]\")))"
                             "(println (:out (p/sh {:extra-env {\"OTHER\" \"1\"}} \"sh\" \"-c\" \"echo [$HOST_CLAIMS_VAR]\")))"))]
  (support/result ["[]" "[set]"] (remove str/blank? (map str/trim (str/split-lines out)))
                  ":env replaces the environment and :extra-env adds to it"))
(support/result "nil true" (out "(require '[babashka.process :as p]) (let [r @(p/process \"true\")] (println (:pid r) (pos? (.pid (:proc r)))))")
                "a process record has no :pid key, and the Process object has the ID")

;; Types and classes.

(support/result "sci.impl.records.SciRecord true true"
                (out "(defrecord R [x]) (println (.getName (class (->R 1))) (instance? R (->R 1)) (record? (->R 1)))")
                "a record is a SciRecord that instance? and record? recognize")
(support/result "foo" (out "(deftype Foo [x] Object (toString [_] \"foo\")) (println (str (Foo. 1)))")
                "deftype works with Object methods, although the README says No deftype")
(support/check (str/includes? (error-message "(deftype Foo [x] clojure.lang.IFn (invoke [_ y] y)) ((Foo. 1) 2)") "Unable to resolve")
               "deftype with clojure.lang.IFn fails")
(support/check (str/includes? (error-message "(definterface I (foo []))") "Unable to resolve symbol: definterface")
               "definterface does not exist")
(support/check (str/includes? (error-message "(gen-class :name Foo)") "Unable to resolve symbol: gen-class")
               "gen-class does not exist")
(support/result "ran" (out "(.run (reify java.lang.Runnable (run [_] (println \"ran\"))))") "reify implements one interface")
(support/check (not (zero? (:exit (bb-eval "(reify java.lang.Runnable (run [_]) java.lang.AutoCloseable (close [_]))"))))
               "reify with two interfaces fails")
(support/result "px" (out "(println (str (proxy [Object] [] (toString [] \"px\"))))") "proxy works for Object")
(support/check (not (zero? (:exit (bb-eval "(.run (proxy [java.lang.Runnable] [] (run [] nil)))"))))
               "proxy for Runnable fails")
(support/check (str/includes? (error-message "(import java.util.concurrent.locks.StampedLock)") "Unable to resolve classname")
               "a class outside the built-in set fails at import")

;; Namespaces, configuration, and entry points.

(support/result "true nil" (out "(println (contains? (ns-aliases 'user) 'fs) (do (ns x) (resolve 'fs/exists?)))")
                "the fs alias exists in the user namespace and not in a namespace the script declares")

(let [project (str (fs/path work "paths"))]
  (support/write-file project "src/app/core.clj" "(ns app.core) (def x :from-src)\n")
  (support/write-file project "deps.edn" "{:paths [\"src\"]}\n")
  (support/check (str/includes? (:err (bb {:dir project} "-e" "(require 'app.core)")) "Could not locate")
                 "bb ignores deps.edn :paths")
  (support/write-file project "bb.edn" "{:paths [\"src\"]}\n")
  (support/result ":from-src" (str/trim (:out (bb {:dir project} "-e" "(require 'app.core) (println app.core/x)")))
                  "bb reads bb.edn :paths"))

(let [project (str (fs/path work "min-version"))]
  (support/write-file project "bb.edn" "{:min-bb-version \"99.0.0\"}\n")
  (let [{:keys [exit out err]} (bb {:dir project} "-e" "(println :ran)")]
    (support/check (and (zero? exit) (= ":ran" (str/trim out)) (str/includes? err "WARNING"))
                   ":min-bb-version only warns, and the script runs and exits 0")))

(let [project (str (fs/path work "guard"))
      script (support/write-file project "src/g.clj"
                                 (str "(ns g)\n(defn -main [& args] (println :main args))\n"
                                      "(when (= *file* (System/getProperty \"babashka.file\"))\n"
                                      "  (apply -main *command-line-args*))\n"))]
  (support/write-file project "bb.edn" "{:paths [\"src\"]}\n")
  (support/result ":main (1)" (str/trim (:out (bb {:dir project} script "1"))) "the main guard runs -main when bb runs the file")
  (support/result ":loaded" (str/trim (:out (bb {:dir project} "-e" "(require 'g) (println :loaded)")))
                  "the main guard skips -main when the file is required"))

(let [project (str (fs/path work "task-future"))
      file (marker "task-future-ran")]
  (support/write-file project "bb.edn" (str "{:tasks {start (future (Thread/sleep 500) (spit \"" file "\" \"ran\"))}}\n"))
  (bb {:dir project} "start")
  (support/check (not (eventually-exists? file)) "bb kills a future started inside a task when the task ends"))

(fs/delete-tree work)
(support/report "host claims")
