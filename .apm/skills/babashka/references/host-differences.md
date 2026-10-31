# Host Differences from JVM Clojure

Part of the `babashka` skill. bb runs Clojure through the Small Clojure Interpreter in a GraalVM native image. The data types and persistent collections are the same as on the JVM, `(clojure-version)` reports `1.12.6-SCI` on bb v1.13.229, and most of `clojure.core` behaves the same. This file lists where bb differs. Each result came from bb v1.13.229, and `tests/host/host-claims.bb` in the package repository checks it again. Where the Babashka README or book says otherwise, the binary's behavior is recorded here and the difference is noted.

## Threads and process exit (BB-1)

bb exits when the last top-level form returns, as `clojure -X` does. Since bb 1.12.208 it waits for non-daemon threads, but most of the ways a script starts background work use daemon threads:

| Background work | Thread | At exit |
|-----------------|--------|---------|
| `future`, `pmap` | Daemon pool (`CLI-agent-send-off-pool`) | Killed. An exception inside a future that nobody dereferences is lost, and bb exits 0. |
| `send-off` | Daemon pool | Killed |
| `send` | Non-daemon pool (`clojure-agent-send-pool`) | bb waits for the pending action |
| `a/thread` | Daemon | Killed |
| `go` | Virtual thread (daemon) | Killed |
| `(Thread. f)` with `.start` | Non-daemon | bb waits |
| `babashka.process/process` | A separate operating-system process | Keeps running after bb exits, and its exit status is lost |

The bb changelog for 1.12.208 says that futures and agents use daemon threads. That holds for `send-off`, but `send` used a non-daemon thread in the test above and kept bb alive.

A future started inside a `bb.edn` task is killed when the task returns. `shutdown-agents` changes nothing on bb, so the JVM advice to call it does not prevent lost work. Wait for the work instead:

```clojure
;; bad: bb exits at once, so the report is never written and a failure is lost
(future (write-report! data))

;; good: the result and any exception reach the main thread
@(future (write-report! data))

;; good: several tasks in parallel, all finished before the script continues
(run! deref (mapv #(future (fetch! %)) urls))

;; good: a go block or a/thread returns a channel, so take from it
(a/<!! (a/thread (copy-files!)))
```

`pmap` is lazy. Realize it inside the script with `doall`, `vec`, or `run!`, as CLJ-2 requires, and its work then finishes before the script continues.

## core.async

`go` blocks run on virtual threads. The Babashka README and the book still say that `go` maps to `a/thread` and that `<!` maps to `<!!`, which was true before bb 1.3.175, whose changelog says that `go` "now uses virtual threads". On bb v1.13.229, `(.isVirtual (Thread/currentThread))` inside `go` returns `true`, and a blocking `<!!` inside a `go` block returned the correct value. Blocking inside `go` therefore does not starve a fixed thread pool on bb, so the JVM rule JVM-4 does not apply to code that only bb runs. Write `<!` and `>!` inside `go` anyway when the same code also runs on the JVM.

## Records, protocols, and types

- `defrecord` works. A record's class is `sci.impl.records.SciRecord`, not a class named after the record, but `instance?`, `record?`, protocol dispatch, `extend-protocol`, and multimethods that dispatch on `type` all work. Code that reads the class name with `(.getName (class r))` sees the SCI class.
- `defprotocol`, `extend-protocol`, `extend-type`, and `satisfies?` work, including extending a protocol to `Object` and `String`.
- `deftype` works with `Object` methods such as `toString`. Its class is `sci.impl.deftype.SciType`. A `deftype` that implements `clojure.lang.IFn` fails with "Unable to resolve symbol: invoke". The Babashka README still says "No `deftype`", which is out of date.
- `reify` implements one interface. `(reify java.lang.Runnable (run [_] ...))` works, and a `reify` with both `Runnable` and `AutoCloseable` fails.
- `proxy` works for `Object` and fails for `java.lang.Runnable` ("No matching clause"). Use `reify` for a single interface.
- `definterface` and `gen-class` do not exist. Code that needs them belongs on the JVM.

## Java classes

bb includes a fixed set of Java classes and cannot load others. A class outside the set fails at `import` or at first use with "Unable to resolve classname", for example `java.util.concurrent.locks.StampedLock` and `java.awt.Color`. Classes that worked in the test:

- `java.time` (`LocalDate`, `Instant`, `Duration`, `format.DateTimeFormatter`)
- `java.nio.file.Files` and `Path`
- `java.util.concurrent` executors, including `Executors/newVirtualThreadPerTaskExecutor`
- `java.math.BigDecimal` and `RoundingMode`
- `java.security.MessageDigest`, `java.util.zip.ZipFile`, `java.util.zip.GZIPInputStream`, `java.net.URLEncoder`, and `java.util.regex.Pattern`

Check a class with `bb -e '(import java.util.concurrent.locks.StampedLock)'` before writing code that depends on it.

## Reader conditionals (BB-2)

bb accepts both the `:bb` and the `:clj` feature and takes the first branch that matches either one:

```clojure
#?(:clj :clj-branch :bb :bb-branch)   ; bb evaluates :clj-branch
#?(:bb :bb-branch :clj :clj-branch)   ; bb evaluates :bb-branch, and the JVM evaluates :clj-branch
```

Put the `:bb` branch first whenever a form has both. Reader conditionals belong in `.cljc` files. clj-kondo reports "Reader conditionals are only allowed in .cljc files" for a `.clj` or `.bb` file.

## Which JVM Clojure rules hold on bb

The `clojure-jvm` skill's Iron Laws were checked on bb:

| JVM law | On bb |
|---------|-------|
| JVM-1, close block-scoped resources with `with-open` | Holds. `with-open` closes resources. |
| JVM-2, never swallow a `Throwable` | Holds. `catch Throwable` catches `Error` as on the JVM. |
| JVM-3, keep side effects out of `dosync` | Holds. Refs retry: eight contending transactions ran their bodies 36 times in the test. |
| JVM-4, never block inside `go` | Does not apply to code that only bb runs, because `go` runs on a virtual thread. |
| JVM-5, call `shutdown-agents` | Does not apply. bb never waits for the future and `send-off` pools. BB-1 replaces it. |
| JVM-6, dereference a future whose failure matters | Holds, and BB-1 extends it to the future's work as well as its failure. |

`alter-var-root`, `binding` conveyance into futures, `promise` and `deliver`, `BigDecimal` arithmetic, and `ex-info` with `ex-data` behave as on the JVM.
