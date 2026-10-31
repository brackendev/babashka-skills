# bb.edn and Tasks

Part of the `babashka` skill. `bb.edn` configures bb for a project: its classpath, its dependencies, its pods, and its tasks. bb reads the `bb.edn` next to the script it runs or in the current directory, and `bb --config <file>` names another one. Behaviors marked as verified were checked on bb v1.13.229. The task options come from the Babashka book (https://book.babashka.org/#tasks).

## The classpath

```clojure
{:paths ["src" "resources"]
 :deps {io.github.example/lib {:git/tag "<release-tag>" :git/sha "<release-sha>"}}}
```

bb ignores `deps.edn`. In a verified test, a namespace under a `deps.edn` `:paths` entry failed with "Could not locate ... on classpath" until `bb.edn` named the same path. A project that has both files declares its bb source paths in `bb.edn`.

A Git dependency must have a `deps.edn` at its root. For a repository without one, bb cloned the commit and then stopped with "Error building classpath. Manifest file not found" (verified). Vendor such a library's code instead, or ask its maintainers to add a `deps.edn`.

## Tasks

```clojure
{:paths ["src"]
 :tasks
 {:requires ([app.cli :as cli])

  clean {:doc "Delete build output."
         :task (fs/delete-tree "target")}

  build {:doc "Build the site."
         :depends [clean]
         :task (cli/build *command-line-args*)}

  test {:doc "Run the tests."
        :extra-paths ["test"]
        :task (do (require 'app.core-test)
                  (let [{:keys [fail error]} (clojure.test/run-tests 'app.core-test)]
                    (System/exit (if (zero? (+ fail error)) 0 1))))}

  serve {:doc "Serve the site."
         :exec-fn app.server/start
         :exec-args {:port 8080}}

  -private-step (println "Not listed by bb tasks.")}}
```

- `bb tasks` lists the tasks with their `:doc` strings. A task whose name starts with `-` is private and is not listed.
- `bb <task> args...` runs a task, and `*command-line-args*` holds the arguments. It is `nil` when there are none (verified).
- A task value is either an expression or a map with `:task` and options: `:doc`, `:requires`, `:depends`, `:extra-paths`, `:extra-deps`, `:enter`, `:leave`, `:override-builtin`, and `:exec-fn` with `:exec-args`.
- `:depends` runs the named tasks first (verified). `bb run --parallel <task>` runs a task's dependencies in parallel.
- The top-level `:requires` applies to every task, and a task-level `:requires` applies to one task. `:init` runs before any task, and `:enter` and `:leave` run before and after each task.
- A task name must be a valid var name, so `site:emit` and `site-emit` work, and `site/emit` does not.
- `:exec-fn` calls a function with an options map built from `:exec-args` and the command-line options, parsed by `babashka.cli`. `bb serve --port 9000` passes `{:port 9000}` after coercion. `bb -x app.server/start --port 9000` calls a function the same way without a task (verified).

## Task functions

`babashka.tasks` provides `shell`, `clojure`, `run`, and `current-task` without a namespace prefix inside tasks.

- `shell` runs an external program, like `babashka.process/shell`. A nonzero exit throws, which stops the task, prints `Error while executing task: <name>`, and makes `bb` exit 1 (verified). Pass `{:continue true}` to keep going and read `:exit` yourself (BB-4).
- `clojure` starts the Clojure CLI through the bundled `deps.clj`, with the same arguments as the `clojure` command. It reads `deps.edn`, so it runs JVM code from a bb task. It treats the exit code the same way `shell` does.
- `run` runs another task by name.

`shell` does not start a shell, so `(shell "rm -rf target/*")` passes `target/*` literally. Use `fs/glob` and `fs/delete-tree`, or run a fixed script with `(shell "bash" "-c" "...")` and the guidance in `references/untrusted-input.md`.

## Projects with both bb.edn and deps.edn

A common layout runs the application or the scripts on bb and keeps JVM tools in `deps.edn` aliases, for example cljfmt or dry4clj:

```clojure
;; bb.edn
{:paths ["src" "resources"]
 :tasks {fmt-check (clojure "-M:format-check")
         check {:doc "Run every check."
                :task (do (run 'lint) (clojure "-M:format-check") (run 'test))}}}
```

- The code on `bb.edn` `:paths` runs on bb and follows this skill.
- The code that `clojure` runs is JVM code and follows the `clojure-jvm` skill.
- `deps.edn` may list bb's built-in libraries for editor tooling such as clojure-lsp. bb never reads those entries.
- `/bb-fix` checks the bb side. `/clj-fix` from clojure-skills checks the `deps.edn` side.
