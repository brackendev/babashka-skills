---
name: bb-fix
description: >-
  Fix a Babashka project or script with lint, format, and test steps. Runs the
  project's own bb.edn lint, format, and test tasks, or else lints the bb.edn
  source paths, bb.edn itself, and loose .bb scripts with clj-kondo and formats
  them with cljfmt. Use when asked
  to lint, format, test, or check a Babashka project, a bb.edn, or a .bb
  script, or to fix clj-kondo warnings or cljfmt formatting in code that bb
  runs. For the deps.edn side of a mixed project, also run /clj-fix. Use
  --report to preview the formatting changes without writing.
argument-hint: "[lint|format|test] [--report] [all|<path>]"
user-invocable: true
---

# Babashka Fix

Run lint, format, and test steps on code that bb runs. Only the `format` step writes: it rewrites formatting with `cljfmt fix`. The `lint` and `test` steps are pure-read.

## Arguments

| Input             | Target                                                                       |
|-------------------|------------------------------------------------------------------------------|
| (no argument)     | Run all three steps (`lint`, `format`, `test`) on the project                |
| `all`             | Same as (no argument). Accepted for family consistency                       |
| `<path>`          | Run the `lint` and `format` steps on one file or directory. The `test` step still runs the project's test task |
| `lint`            | Run lint only                                                                |
| `format`          | Run format only                                                              |
| `test`            | Run tests only                                                               |
| `--report`        | Disable all writes: run `cljfmt check` instead of `cljfmt fix`               |

Step keywords are combinable (for example, `/bb-fix lint test`). The `--report` flag may appear in any position. When `--report` is present without an explicit step keyword, every step still runs, and the format step reports its changes instead of writing them.

## Mutation

The `format` step writes by default. It runs `cljfmt fix` through the runner the Format step below selects, rewriting files in place. With `--report`, it runs `cljfmt check` through the same runner, which exits nonzero when files would change but does not write. The `lint` and `test` steps are pure-read regardless of `--report`.

This skill excludes vendored, generated, and dependency-locked paths from the file set it walks. The filter combines `.gitignore` matches and a hardcoded floor (`node_modules/`, `vendor/`, `third_party/`, `.bundle/`, `target/`, `build/`, `dist/`, `out/`, `.cpcache/`, `.clj-kondo/`, `*.lock`). A path argument that names an excluded path is linted and formatted, because the user asked for it. When the filter excludes any path, add a single "skipped N vendored or generated paths" line to the report. Under `--report`, list every skipped path. The full policy is Rule 4 in the package's [CONVENTIONS.md](https://github.com/brackendev/babashka-skills/blob/master/CONVENTIONS.md).

## Files

Resolve the file set before the first step. bb reads `bb.edn` and ignores `deps.edn`, so take the source paths from `bb.edn`, never from `deps.edn`:

1. **Project paths.** In the nearest directory with `bb.edn`, read its top-level `:paths` and the `:extra-paths` of each task. Skip paths that do not exist and resource directories that hold no Clojure files.
2. **`bb.edn` itself.** clj-kondo checks it for undefined task dependencies, cyclic dependencies, and unexpected keys.
3. **Loose scripts.** Every `*.bb` file in the project root and in `scripts/`, `bin/`, and `bb/`, and every file there whose first line is `#!/usr/bin/env bb`, when it is not already under a project path.

Without a `bb.edn`, the file set is the path argument, or else the `*.bb` files in the working directory. A path argument replaces the project paths and the loose scripts, and `bb.edn` is still linted when it exists.

When the project also has `deps.edn`, its JVM code is outside this skill. Say in the report that `/clj-fix` from clojure-skills checks that side.

## Steps

Parse `$ARGUMENTS` to determine which steps to run, the path, and whether `--report` is present. If no step keyword is supplied (or only `all` is supplied), run every step in order.

### 1. Lint

When `bb.edn` defines a lint task, run it as this step and report its exit code and output. A lint task is a task named `lint`, or the one task whose body runs `clj-kondo`. The task records which files the project lints and with which configuration, so never widen it. Some projects lint `src` with a strict configuration and leave `scripts/` unlinted on purpose. List the files from the Files section that the task does not cover as "not linted by the project's lint task", without linting them or failing the step. Skip the rest of this step.

Without a lint task, run clj-kondo from the project root, so it reads the project's `.clj-kondo/config.edn` and cache. Lint the project paths and `bb.edn` together:

```bash
clj-kondo --lint bb.edn <project-paths...>
```

Lint each loose script in its own run. A script without an `ns` form belongs to the `user` namespace, so linting several of them together reports false `duplicate require` and `redefined var` warnings:

```bash
clj-kondo --lint scripts/release.bb
```

clj-kondo knows the aliases that bb predefines in the `user` namespace, such as `fs` and `str`, so a script without an `ns` form may use them without a warning. Once the script has an `ns` form, clj-kondo reports `Unresolved namespace fs`, and bb fails with "Unable to resolve symbol" at run time. Report that finding as a real error and add the missing `require`, as the `babashka` skill's Key Rule 5 describes. Report a reader conditional in a `.bb` or `.clj` file ("Reader conditionals are only allowed in .cljc files") as an error too: the file must become `.cljc`, and BB-2 governs its branch order.

When `clj-kondo` is not on `PATH`, report `SKIPPED` and name the clj-kondo installation instructions (https://github.com/clj-kondo/clj-kondo/blob/master/doc/install.md).

Report pass if no `:error` or `:warning` finding remains, and fail otherwise. List `:info` findings for review without failing the step. Show the clj-kondo output.

### 2. Format

cljfmt formats `.bb` files with its default `:file-pattern`. Run it with the first runner the project supports, checking the rows in order, and check each condition before running its command:

| Runner | When | Fix | Check |
|--------|------|-----|-------|
| bb task | `bb.edn` has a task that runs cljfmt with `fix`, or a pair of tasks for `fix` and `check`, such as `fmt-fix` and `fmt-check` or `format:fix` and `format:check` | the fix task | the check task |
| Project alias | `deps.edn` defines a `:cljfmt` alias | `clojure -M:cljfmt fix <files...>` | `clojure -M:cljfmt check <files...>` |
| Standalone binary | `command -v cljfmt` succeeds | `cljfmt fix <files...>` | `cljfmt check <files...>` |
| Clojure CLI tool | `clojure -Ttools list` shows a `cljfmt` tool | `clojure -Tcljfmt fix :paths '[...]'` | `clojure -Tcljfmt check :paths '[...]'` |

Without `--report`, run the Fix command. With `--report`, run the Check command, which writes nothing. A bb task formats the paths the project chose, so run it without arguments and never add files to it. List the files from the Files section that it does not cover, such as `bb.edn` or loose scripts, as "not formatted by the project's format task", without formatting them. For the other runners, pass the files that the Files section resolved, including `bb.edn` and the loose scripts.

cljfmt reads its configuration from `.cljfmt.edn` or `cljfmt.edn` in the project directory or a parent. A regular expression there must use the `#re` tag, because `#"..."` is not valid EDN. When no runner is available, report `SKIPPED` and name the cljfmt README (https://github.com/weavejester/cljfmt) for the installation methods.

Report pass if exit code is 0, fail otherwise. Show the formatting changes (under `fix`) or the diff (under `check`).

### 3. Test

Find the project's test task. Run `bb tasks` in the project root, and read `bb.edn`:

- When a task is named `test`, use it.
- Otherwise, when exactly one task's name or `:doc` names tests (`test:unit`, "Run the tests"), use it.
- When several tasks qualify, ask the user which one to run.
- When none qualifies, report `SKIPPED` and say that `bb.edn` defines no test task. Show the test task in `babashka/references/testing.md`, in the `babashka` skill, as the one to add.

Run `bb <task>` and show its output. Report pass only when the exit code is 0 and the output reports no failures or errors. `clojure.test` prints a line such as `1 failures, 0 errors.`, and a task that only calls `run-tests` exits 0 even then. When the exit code is 0 but that line counts a failure or an error, report the step as failed and name BB-3: the runner must exit nonzero, and the report shows the exit form to add.

## Report

After running all requested steps, print a summary:

```
Babashka Fix Results:
  Lint:   PASS/FAIL/SKIPPED
  Format: PASS/FAIL/SKIPPED
  Test:   PASS/FAIL/SKIPPED
```

If `--report` was passed, append `(report mode: format checked, not written)` after the summary. If a lint, format, or test step fails, stop and report the failure. Do not continue to the next step. When the project also has `deps.edn`, end the report with one line that names `/clj-fix` for the JVM side.

## Gotchas

- `bb.edn` is read as EDN, so a regex literal in it stops every `bb` command in that directory, including `bb tasks`, with "Invalid regex literal found in EDN config, use re-pattern instead". When `bb tasks` fails that way, report the test step as failed with that message, and show the task rewritten with `re-pattern`.
- `bb` can be the Bitbucket CLI on some machines. Confirm that `bb --version` prints `babashka v<version>` before the test step.
