---
name: babashka
description: >-
  Use when writing, editing, reviewing, or discussing code that Babashka (bb)
  runs. Triggers: .bb files, bb.edn, a `#!/usr/bin/env bb` shebang, bb tasks
  (`bb <task>`, `bb tasks`, `:tasks`, `:requires`, `:depends`, `exec`,
  `:exec-fn`), the `babashka.*` namespaces (`babashka.fs`, `babashka.process`,
  `babashka.http-client`, `babashka.cli`, `babashka.pods`, `babashka.deps`),
  `System/getProperty "babashka.file"`, pods, `bb nrepl-server`, `bb
  uberscript`, `bb uberjar`, `:min-bb-version`, SCI, or any mention of
  Babashka. Covers the built-in namespaces, bb.edn tasks, the host differences
  from JVM Clojure, subprocesses, testing, pods, packaging, the REPL, and when
  to move code to the JVM.
user-invocable: false
---

# Babashka

Babashka (`bb`) runs Clojure scripts with the Small Clojure Interpreter (SCI) inside a GraalVM native binary. It starts in milliseconds, carries a fixed set of Java classes and libraries, and interprets code form by form instead of compiling it to JVM bytecode.

Layered on top of the [clojure](https://github.com/brackendev/clojure-skills) skill, which defines host-neutral Clojure family guidance. This skill applies to code that bb runs: `.bb` scripts, `bb.edn` tasks, and the namespaces on a `bb.edn` `:paths` entry. It overrides the baseline for the built-in libraries, subprocesses, threads and process exit, records and types, reader conditionals, testing, and the bb project workflow. The baseline still governs syntax, naming, namespace hygiene, function design, collections, atoms, dispatch, and formatting.

A project can hold both bb code and JVM code, for example `bb.edn` tasks next to a `deps.edn` whose aliases run on the Clojure CLI. Apply this skill to the code that bb runs and the `clojure-jvm` skill from [clojure-jvm-skills](https://github.com/brackendev/clojure-jvm-skills) to the code that the JVM runs. Its laws JVM-4 (never block in `go`) and JVM-5 (call `shutdown-agents`) do not hold on bb, and BB-1 below replaces JVM-5.

Every host claim in this skill was checked against bb v1.13.229, and `tests/host/host-claims.bb` in the package repository checks them again. The Babashka README and book are older than the binary in places: they say that `deftype` is unavailable and that `go` maps to `thread`, but `deftype` works with `Object` methods and `go` runs on a virtual thread. Run a claim in `bb -e` before relying on it.

## Iron Laws

These rules prevent lost work, false success reports, and wrong branches on Babashka. The `clojure` skill's Iron Laws (CLJ-1 through CLJ-12) also apply. Never write code that breaks one. When existing code breaks a law, stop and report the law ID, the location, the offending code, and the correct pattern, then ask before changing code outside the current task. A law overrides any Key Rule or reference that seems to conflict with it.

1. **BB-1: WAIT FOR BACKGROUND WORK BEFORE THE SCRIPT EXITS.** bb exits as soon as the last top-level form returns. `future`, `send-off`, `a/thread`, and `go` run on daemon threads, so bb kills their unfinished work, an exception inside them is lost, and the script still exits 0. A `process` that is never dereferenced keeps running after bb exits, and its exit status is lost. Dereference every `future` and `process`, and take from every `a/thread` or `go` channel, whose result, effect, or failure matters. `shutdown-agents` does not help, because bb never waited for those threads (`references/host-differences.md`).
2. **BB-2: PUT `:bb` BEFORE `:clj` IN A READER CONDITIONAL.** bb reads both branches and takes whichever comes first, so `#?(:clj (jvm-only) :bb (bb-version))` runs the JVM branch on bb. When a form has both a `:bb` and a `:clj` branch, write the `:bb` branch first. A file that uses reader conditionals must be `.cljc`, because clj-kondo reports them as errors in `.clj` and `.bb` files (`references/host-differences.md`).
3. **BB-3: A TEST RUNNER EXITS NONZERO ON ANY FAILURE OR ERROR.** `clojure.test/run-tests` returns a map of counts and does not set the exit status, so a bb script or task that only calls it exits 0 with failing tests. End the runner with `(System/exit (if (zero? (+ fail error)) 0 1))` on the returned counts (`references/testing.md`).
4. **BB-4: CHECK THE RESULT OF EVERY SUBPROCESS BEFORE REPORTING SUCCESS.** `babashka.process/shell` throws on a nonzero exit. `sh`, `process`, and `shell` with `{:continue true}` return the exit code without throwing. Read `:exit` from the dereferenced result, or call `p/check`, which throws on a nonzero exit (`references/built-ins.md`).

## Key Rules

1. **Use the built-in libraries first.** bb bundles `babashka.fs`, `babashka.process`, `babashka.http-client`, `babashka.cli`, `cheshire.core`, `clojure.tools.cli`, `clojure.data.json`, `clj-yaml.core`, `org.httpkit.*`, `clojure.core.async`, and more. `bb print-deps` lists the bundled versions. Never add a bundled library to `bb.edn` `:deps`, because the bundled copy is compiled into the binary (`references/built-ins.md`).
2. **Declare source paths in `bb.edn`.** bb reads `bb.edn` and ignores `deps.edn`, so a `deps.edn` `:paths` entry does not put a directory on bb's classpath. Set `:paths` in `bb.edn`, and give a test task its own `:extra-paths` (`references/tasks.md`).
3. **Guard the entry point of a script that other code loads.** Call `-main` inside `(when (= *file* (System/getProperty "babashka.file")) (apply -main *command-line-args*))`. bb runs `-main` when it runs the file, and a `require` or `load-file` from a test or a REPL only defines the functions. This is how a bb script meets CLJ-8. `*command-line-args*` is `nil`, not an empty list, when there are no arguments.
4. **Find the script's own files from `*file*`.** The working directory is the caller's. Build sibling paths from `(fs/parent (fs/absolutize *file*))`.
5. **Write an `ns` form and require every alias.** bb predefines aliases such as `fs`, `str`, `io`, `json`, `shell`, and `edn` in the `user` namespace only. A script that uses them without a `require` breaks when it gains an `ns` form, because `(resolve 'fs/exists?)` is `nil` in any other namespace.
6. **Pass `:extra-env`, not `:env`, to add environment variables.** `:env` replaces the whole environment of the child process, including `PATH`. `:extra-env` adds to the inherited one (`references/built-ins.md`).
7. **Treat `:min-bb-version` as a warning, not a guard.** An older bb prints a `WARNING` and runs the script anyway, with exit 0. When an older bb would cause harm, compare `(System/getProperty "babashka.version")` and exit with a message (`references/packaging-and-repl.md`).
8. **Confirm that a class exists in bb before relying on it.** bb carries a fixed set of Java classes and cannot load new ones. A class outside the set fails at `import` with "Unable to resolve classname". Check with `bb -e '(import <class>)'`. `java.time`, `java.nio.file`, `java.util.concurrent`, `java.security.MessageDigest`, `java.util.zip`, and `java.math.BigDecimal` are available (`references/host-differences.md`).
9. **Keep to the type forms bb supports.** `defrecord` and `defprotocol` work, but a record's class is `sci.impl.records.SciRecord`, not a generated Java class. `deftype` works with `Object` methods and fails with `clojure.lang.IFn`. `reify` implements one interface. `proxy` works for `Object` and fails for `Runnable`. `definterface` and `gen-class` do not exist (`references/host-differences.md`).
10. **Use a pod for a database or another library that needs classes bb lacks.** `next.jdbc` and JDBC drivers are not built in. Load a pod from the pod registry, for example `org.babashka/postgresql`, and pin its version in `bb.edn` `:pods` or the `load-pod` call (`references/packaging-and-repl.md`).
11. **Carry the JVM resource and state rules over.** `with-open` closes resources on bb as it does on the JVM, refs and `dosync` retry transactions, and an exception escapes a `future` only on `deref`. Close block-scoped resources with `with-open`, never swallow a `Throwable`, keep side effects out of `dosync`, and dereference a future whose failure matters (`references/host-differences.md`).
12. **Move code to the JVM when bb is the wrong host.** bb interprets code, so a hot loop runs much slower than JVM Clojure. Move the code to a `deps.edn` project when it is CPU-bound, needs a class or library that bb does not carry and no pod provides, or needs `gen-class` or `definterface`. bb can still drive the JVM code from a task with `clojure` (`references/packaging-and-repl.md`).

For `bb.edn` tasks, the task functions, and mixed `bb.edn` and `deps.edn` projects, read `references/tasks.md`.

## References

The Iron Laws and Key Rules are the short form. Each reference file below holds the detailed guidance and the examples for one topic. Before writing, reviewing, or refactoring code that bb runs, read every reference whose topic the task touches, and read the next one when the work moves to a new topic.

| When the task involves | Read |
|------------------------|------|
| Files and paths (`babashka.fs`), subprocesses (`babashka.process`), HTTP (`babashka.http-client`), JSON, or command-line options (`babashka.cli`, `clojure.tools.cli`) | `references/built-ins.md` |
| `bb.edn` tasks, `:requires`, `:depends`, `exec` and `:exec-fn`, the `shell` and `clojure` task functions, or a project with both `bb.edn` and `deps.edn` | `references/tasks.md` |
| Threads and process exit, `core.async`, records, types, `reify`, `proxy`, Java classes, reader conditionals, or which JVM Clojure rules hold on bb | `references/host-differences.md` |
| Writing or running tests for bb code, or testing a script without running it | `references/testing.md` |
| Uberscripts, uberjars, `bb nrepl-server`, pods, `:min-bb-version`, upgrading bb, or moving code to the JVM | `references/packaging-and-repl.md` |
| Input from outside the program passed to a subprocess with `babashka.process` | `references/untrusted-input.md` |

## Gotchas

### `bb` is also the Bitbucket CLI's name on some machines

Before running `bb`, confirm that it is Babashka with `bb --version`, which prints `babashka v<version>`.

### No regex literals in `bb.edn`

`bb.edn` is read as EDN, so a regex literal such as `#"app\..*-test"` in a task stops every `bb` command in that directory, including `bb -e` and `bb tasks`, with "Invalid regex literal found in EDN config, use re-pattern instead". Build the pattern with `(re-pattern "...")`, or move the code into a namespace on `:paths`.

### A task that starts background work

A bb task ends when its body returns, and BB-1 applies to it. A `future` started in a task body is killed when the task returns, unless the task dereferences it.

### Printed return values

bb prints the value of the last form of an `-e` expression unless it is `nil`. A script file prints nothing unless it calls a print function.
