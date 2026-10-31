# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.1.0] - 2026-10-31

### Added

- `babashka` (auto-triggered): guidance for code that Babashka runs. It covers the built-in namespaces, `bb.edn` tasks, subprocesses, threads and process exit, records and types, Java classes, reader conditionals, testing, pods, packaging, the REPL, upgrading bb, and when to move code to the JVM. It opens with Iron Laws BB-1 through BB-4, and every host claim was checked against bb v1.13.229. The Babashka untrusted-input guidance for `babashka.process` moved here from clojure-jvm-skills.
- `/bb-fix [lint|format|test] [--report] [all|<path>]` (user-invoked): runs the project's bb lint, format, and test tasks, or lints the `bb.edn` paths, `bb.edn`, and loose `.bb` scripts with clj-kondo and formats them with cljfmt. The test step reports a failure when the task exits 0 although `clojure.test` counted failures.
- `bb-rules` hook: delivers the condensed BB-1 through BB-4 Iron Laws to the agent at session start in a `bb.edn` project, after `/clear` or a compaction, and on the first matching edit when the session started elsewhere, with an instruction to load the `babashka` skill along with the `clojure` skill. The first edit of a `.bb` file delivers them in any directory. Verified only under Claude Code. Copilot and Cursor receive no copy.
- Test suites in `tests/hooks/` (`bb tests/hooks/run.bb`), including `tests/host/host-claims.bb`, which checks the stated Babashka behavior against the installed bb, and empty `copilot-hooks.json` and `cursor-hooks.json` files.
