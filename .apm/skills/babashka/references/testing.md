# Testing Babashka Code

Part of the `babashka` skill. bb bundles `clojure.test`, so tests for bb code are ordinary `deftest` forms run by a small runner. The runner is the part that bb projects most often get wrong. Every behavior below was checked on bb v1.13.229.

## The runner must set the exit status (BB-3)

`clojure.test/run-tests` and `run-all-tests` return a map with `:test`, `:pass`, `:fail`, and `:error` counts. They do not exit. A script or task that only calls them exits 0 when tests fail, so CI and `/bb-fix` would report success. End every runner with an exit status computed from the counts:

```clojure
(let [{:keys [fail error]} (clojure.test/run-tests 'app.core-test 'app.cli-test)]
  (System/exit (if (zero? (+ fail error)) 0 1)))
```

A suite that ran no tests is usually a broken runner, for example a wrong namespace name. A runner that should always have tests can also fail when `:test` is zero.

## A test task

Keep tests on their own path, add it in the task with `:extra-paths`, and load the test namespaces before running them. Listing the namespaces by hand works, and it fails loudly when one is missing. Discovering them keeps a new test file from being skipped:

```clojure
{:paths ["src"]
 :tasks {test {:doc "Run the tests."
               :extra-paths ["test"]
               :task (do (doseq [file (fs/glob "test" "**_test.clj")]
                           (load-file (str file)))
                         (let [{:keys [fail error]}
                               (clojure.test/run-all-tests (re-pattern "app\\..*-test"))]
                           (System/exit (if (zero? (+ fail error)) 0 1))))}}}
```

`bb.edn` is read as EDN, so it cannot hold a regex literal. `#"app\..*-test"` in `bb.edn` stops every `bb` command in that directory, including `bb -e`, with "Invalid regex literal found in EDN config, use re-pattern instead". Build the pattern with `re-pattern`, or move the runner into a namespace and call it from the task.

In the test, `with-redefs` replaced a function for the duration of a test as it does on the JVM.

## Testing a script without running it

A script whose entry point is guarded (Key Rule 3) can be loaded by a test without running `-main`:

```clojure
(load-file "scripts/report.bb")
(let [summarize (resolve 'report/summarize)]
  (clojure.test/is (= {:total 2} (summarize [1 1]))))
```

`load-file` defines the script's functions and skips the guarded call, while `bb scripts/report.bb` runs `-main`. Give the script an `ns` form so the test can resolve its vars by a known name. A script whose last form calls `-main` unconditionally runs it on `load-file`, including any `System/exit`, which ends the test run.

To test the script as a program, run it in a subprocess and check its exit code and output with `babashka.process/sh`. Never infer success from its output alone (BB-4).

## Shared code between bb and the JVM

A test namespace that runs on both hosts lives in a `.cljc` file. Follow BB-2 for any reader conditional in it, and run the tests on both hosts: `bb test` for bb and the project's JVM test command for the JVM.
