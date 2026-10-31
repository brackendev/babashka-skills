# Babashka rules from babashka-skills

This is Babashka code. The Iron Laws below come from the `babashka` skill and add to the `clojure` baseline laws. Never write code that breaks one. Before writing, reviewing, or refactoring code that bb runs, load the `babashka` skill along with the `clojure` skill. The skills hold the full laws, their exceptions, and the references.

## Iron Laws

- BB-1: Wait for background work before the script exits. Dereference every `future` and `process`, and take from every `a/thread` or `go` channel, whose result, effect, or failure matters, because bb kills daemon threads at exit and loses their exceptions.
- BB-2: When a reader conditional has both a `:bb` and a `:clj` branch, put `:bb` first, because bb evaluates whichever of the two comes first.
- BB-3: A test runner exits nonzero on any failure or error. `clojure.test/run-tests` returns the counts and exits 0 by itself.
- BB-4: Check the result of every subprocess before reporting success. `shell` throws on a nonzero exit, but `sh`, `process`, and `shell` with `:continue true` do not, so read `:exit` or call `p/check`.

## JVM code in the same project

In a project that also runs code on the JVM, the `clojure-jvm` laws apply to that code. JVM-4 and JVM-5 do not apply to code that bb runs: bb runs `go` blocks on virtual threads, and BB-1 replaces `shutdown-agents`.
