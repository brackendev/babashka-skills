# Built-in Libraries

Part of the `babashka` skill. bb compiles a set of libraries into its binary, so a script can require them without a `bb.edn`. `bb print-deps` prints the bundled libraries and their versions as a `deps.edn` map. Never list a bundled library in `bb.edn` `:deps`, because the binary already contains it. Every behavior below was checked on bb v1.13.229.

## Files and paths: `babashka.fs`

`babashka.fs` wraps `java.nio.file`. Its functions accept strings, `java.io.File`, and `java.nio.file.Path`, and most return a `Path`, so call `str` before passing a result to a function that expects a string.

```clojure
(require '[babashka.fs :as fs])

(def here (fs/parent (fs/absolutize *file*)))   ; the script's own directory
(fs/path here "templates" "page.html")           ; a sibling file
(fs/exists? "out")
(fs/create-dirs "out/reports")
(fs/which "git")                                 ; nil when git is not on PATH
(fs/glob "src" "**.clj")                         ; skips hidden files
(fs/glob "src" "**.clj" {:hidden true})          ; includes them
(fs/delete-tree "out")
(fs/create-temp-dir)
```

`fs/glob` skips files and directories whose names start with a dot unless the options set `:hidden true`. Use `fs/which` to check for a required tool before running it, and fail with a message when it returns `nil`.

## Subprocesses: `babashka.process`

`babashka.process` offers three entry points. They differ in how they handle output and exit codes, and BB-4 depends on the difference.

| Function | Output | Nonzero exit | Returns |
|----------|--------|--------------|---------|
| `shell` | Inherits the terminal by default | Throws, unless `{:continue true}` | The process record after it finishes |
| `sh` | Captures `:out` and `:err` as strings | Returns `:exit` | `{:exit :out :err ...}` after it finishes |
| `process` | Streams by default | Returns `:exit` after `deref` | A process record that runs in the background until dereferenced |

```clojure
(require '[babashka.process :as p])

;; throws on failure, which stops a script or a task with an error
(p/shell "git" "fetch")

;; reads the exit code itself
(let [{:keys [exit]} (p/shell {:continue true} "git" "diff" "--quiet")]
  (when-not (zero? exit) (println "The working tree has changes.")))

;; captures output, then checks the exit code
(let [{:keys [exit out err]} (p/sh "git" "rev-parse" "HEAD")]
  (when-not (zero? exit) (throw (ex-info "git rev-parse failed" {:err err})))
  (str/trim out))

;; runs in the background, then waits with a timeout
(let [proc (p/process {:out :string} "clj-kondo" "--lint" "src")
      result (deref proc 10000 ::timeout)]
  (if (= ::timeout result)
    (do (p/destroy-tree proc) (throw (ex-info "clj-kondo timed out" {})))
    (:out (p/check result))))
```

`p/check` dereferences a process and throws when its exit code is nonzero. A process record has no `:pid` key. Read the operating-system process ID from the `java.lang.Process` object with `(.pid (:proc proc))`.

Options that matter most:

- `:dir` sets the working directory.
- `:extra-env` adds environment variables to the inherited environment. `:env` replaces the whole environment, including `PATH`, so a child started with `{:env {"FOO" "1"}}` does not see the parent's variables.
- `:out` and `:err` take `:string`, `:inherit`, `:write` with a file name in `:out-file`, or a stream. `:in` takes a string or a stream.
- `:continue true` stops `shell` from throwing.

The first string argument is split into several arguments. `references/untrusted-input.md` covers what that means for input.

## HTTP: `babashka.http-client`

`babashka.http-client` wraps `java.net.http`. It throws on a status code that is not successful: a 404 response raised `ExceptionInfo` with the message "Exceptional status code: 404" and the status in `ex-data`. Pass `{:throw false}` to read the status yourself:

```clojure
(require '[babashka.http-client :as http]
         '[cheshire.core :as json])

(let [{:keys [status body]} (http/get url {:throw false :timeout 20000})]
  (if (<= 200 status 299)
    (json/parse-string body true)
    (throw (ex-info "Request failed" {:status status :url url}))))
```

`org.httpkit.client` and `org.httpkit.server` are also built in. An http-kit request returns a promise, so dereference it.

## JSON: `cheshire.core`

`(json/parse-string s true)` turns object keys into keywords, and `(json/parse-string s)` keeps them as strings. `json/generate-string` writes JSON. `clojure.data.json` is also built in.

## Command-line options: `babashka.cli` and `clojure.tools.cli`

`babashka.cli` parses `--name value` options into a map, with coercion:

```clojure
(require '[babashka.cli :as cli])

(cli/parse-opts ["--port" "8080"] {:coerce {:port :long}})   ; => {:port 8080}
```

`cli/dispatch` routes subcommands from a table, and bb tasks with `:exec-fn` or `bb -x ns/fn` parse options with `babashka.cli` as well (`references/tasks.md`). `clojure.tools.cli` is also built in for code shared with JVM projects.

## Other bundled libraries

The bundled set includes `clojure.core.async`, `clj-yaml.core`, `clojure.data.csv`, `clojure.data.xml`, `hiccup`, `rewrite-clj`, `cognitect.transit`, `taoensso.timbre`, and `clojure.test.check`. Check `bb print-deps` for the full list and the versions before adding a dependency.
