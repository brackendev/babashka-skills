# Packaging, the REPL, Pods, and Upgrades

Part of the `babashka` skill. Behaviors marked as verified were checked on bb v1.13.229. The rest come from the Babashka book (https://book.babashka.org) and `bb help`.

## Running a script

- `bb script.bb args...` runs a file, and `bb -e '<form>'` evaluates an expression. `#!/usr/bin/env bb` on the first line lets an executable script run directly.
- `bb -m app.core args...` calls `app.core/-main` from the classpath, and `bb -x app.core/start --port 1` calls a function with options parsed by `babashka.cli` (verified).
- `bb --config path/to/bb.edn <task>` runs a task from another directory, which suits an MCP server or another tool that starts bb from an unknown working directory.

## Uberscript and uberjar

- `bb uberscript out.clj main.clj` collects every namespace that `main.clj` requires into one file, which then runs without the project's classpath (verified). Uberscripts work by evaluating the top-level `require` forms, so code that requires a namespace at run time, for example with `requiring-resolve` or a `require` inside a function, may not work in an uberscript.
- `bb uberjar out.jar -m app.core` packages the classpath into a jar that `bb out.jar` runs (verified). Use it when an uberscript's dynamic requires fail or when the project carries resources.

Both formats still need a bb binary to run.

## REPL

`bb nrepl-server` starts an nREPL server on `localhost:1667`, and `bb nrepl-server 7888` picks the port. It writes the port to `.nrepl-port` and deletes the file on exit (verified), so editors and the `clojure-repl` skill find it the same way they find a JVM nREPL. `bb repl` starts a console REPL. The server needs no dependency, because bb includes it.

The `clojure` skill's CLJ-12 applies: after REPL work, verify from the saved files in a fresh `bb` process.

## Pods

A pod is a separate program that bb loads as a library over a pipe. Pods provide what bb cannot load itself, such as database drivers. The pod registry (https://github.com/babashka/pod-registry) lists them. Declare a pod in `bb.edn`:

```clojure
{:pods {org.babashka/postgresql {:version "<pod-version>"}}}
```

or load it in a script:

```clojure
(require '[babashka.pods :as pods])
(pods/load-pod 'org.babashka/postgresql "<pod-version>")
(require '[pod.babashka.postgresql :as pg])
```

Pin the pod version that the project tested. The book describes registry pods as downloaded when first loaded, so the first run needs network access. `bb prepare` downloads the dependencies and pods that `bb.edn` declares ahead of time.

## Requiring a minimum bb version

`:min-bb-version "1.12.0"` in `bb.edn` makes an older bb print a warning. In a verified test, a `bb.edn` that required version 99.0.0 printed `WARNING: this project requires babashka 99.0.0 or newer, but you have: 1.13.229`, ran the script, and exited 0. When an older bb would cause harm, check the version yourself and stop:

```clojure
(let [[major minor] (map parse-long (str/split (System/getProperty "babashka.version") #"\."))]
  (when (neg? (compare [major minor] [1 12]))
    (binding [*out* *err*] (println "This script needs babashka 1.12 or later."))
    (System/exit 1)))
```

## Upgrading bb

bb is one binary, installed by a package manager (`brew install borkdude/brew/babashka`, scoop) or by downloading a release from https://github.com/babashka/babashka/releases. There is no project dependency to change, so an upgrade is a checklist:

1. Read `bb --version`, then read the CHANGELOG entries between that version and the latest release (https://github.com/babashka/babashka/blob/master/CHANGELOG.md). Its "Breaking changes" section lists the changes that need code edits.
2. Upgrade through the same installer that put bb on `PATH`, for example `brew upgrade babashka`, and confirm with `bb --version`.
3. Compare the bundled library versions with `bb print-deps` before and after. A bundled library can change behavior without a change to the project.
4. Raise `:min-bb-version` in `bb.edn` when the project starts to depend on the new release.
5. Update the pinned pod versions and Git dependencies in `bb.edn` separately, one at a time.
6. Run the project's tests with `bb <test task>` and check the exit code (BB-3).

## When to move code to the JVM

bb interprets code, so loops and heavy computation run much slower than compiled JVM Clojure. Move code to a `deps.edn` project and run it with the Clojure CLI when:

- The work is CPU-bound, and its run time matters more than startup time.
- It needs a Java class that bb does not carry and no pod provides.
- It needs `gen-class`, `definterface`, a `deftype` that implements `clojure.lang` interfaces, or a `reify` with more than one interface.
- It needs a library that does not load in bb.

A bb task can still start the JVM code with `(clojure "-M:alias")`, which keeps the project's commands in one place (`references/tasks.md`). Library authors who want code to run on both hosts keep it in `.cljc` files and test it on both.
