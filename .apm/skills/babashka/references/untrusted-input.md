# Untrusted Input in Babashka Subprocesses

Part of the `babashka` skill. The host-neutral rules are in `clojure/references/untrusted-input.md` in the `clojure` skill (Iron Laws CLJ-10 and CLJ-11). This file covers the Babashka form of CLJ-11: passing input to an external program through `babashka.process`, which bb includes. Every result below came from bb v1.13.229, which bundles `babashka/process` v0.6.25.

## The first string is split into arguments

`shell`, `process`, and `sh` split their first string argument into several arguments. The `babashka/process` README at v0.6.25: "All of `shell`, `process` and `sh` support tokenization on the first string argument using `tokenize`." No shell runs, so `;` and `|` have no effect. The `shell` docstring says it "does not support bash syntax like `ls *.clj`."

Input joined into that first string still becomes several arguments, and quote characters inside it change the split:

```clojure
(require '[babashka.process :as p])

(:cmd (p/process (str "ls " "my file.txt")))  ; => ["ls" "my" "file.txt"]
(p/tokenize "echo it's")                       ; => ["echo" "its"]
```

A value that starts with `-` becomes an option of the program, and a value with a space becomes two arguments.

## Pass each value as its own argument

Keep the first string to a fixed program name or fixed command words, and pass every value after it as its own argument or inside a vector. bb does not split the later arguments:

```clojure
;; bad: "my dir" becomes the arguments "my" and "dir", and "-n hello" becomes an echo option
(p/shell (str "ls " folder))
(p/sh (str "echo " input))

;; good: each value is one argument
(p/shell "ls" folder)
(p/sh ["echo" input])
```

With `input` set to `"x; echo INJECTED"`, `(p/sh "echo" input)` printed the input unchanged.

## A shell script with a fixed text

`sh -c` runs a shell, and the shell parses its script. With `input` set to `"x; echo INJECTED"`, `(p/sh "sh" "-c" (str "echo " input))` printed `x` and then `INJECTED`. When a command needs a shell feature such as a pipe or a redirect, keep the script a fixed string and pass the input after it. POSIX `sh -c` sets `$0` from the first argument after the script, "and the positional parameters ($1, $2, and so on) in sequence from the remaining argument operands." Quote each parameter inside the script:

```clojure
;; good: the script is fixed, and file reaches it as "$1"
(p/sh "sh" "-c" "wc -l < \"$1\"" "sh" file)
```

With `file` set to `"my file.txt"`, this printed the line count of that file.

## Check the exit status

BB-4 applies to every command that takes input: a failure must not read as success. `shell` throws on a nonzero exit unless the options map has `:continue true`. `process` and `sh` do not check the exit status, so call `p/check` or read `:exit`. `references/built-ins.md` shows each form.

A separate argument can still reach the program as an option, such as a file name that starts with `-`. The `clojure` skill covers validating arguments and putting `--` before positional arguments.
