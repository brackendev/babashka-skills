# babashka-skills

Babashka development skills packaged as an [APM](https://github.com/microsoft/apm) plugin. One install deploys the full set to every runtime in APM's default target set: Claude Code, Codex, OpenCode, Cursor, Copilot, Gemini, Windsurf, Kiro, and Grok Build. Antigravity is supported by naming it explicitly with `--target antigravity`.

Skills follow the [Agent Skills](https://agentskills.io) open standard. One auto-triggers from conversation context (`babashka`). The other, `/bb-fix`, is a slash command, and the agent can also run it from a natural-language request. Runtimes differ in how they offer these commands: Codex lists them under `/skills` and runs them as `$name`, OpenCode attaches them with `@name`, and Gemini CLI also offers the auto-triggered skill as a slash command.

This package layers on top of the host-neutral [clojure-skills](https://github.com/brackendev/clojure-skills) baseline. Install both together so the `clojure` skill handles general Clojure family style (naming, threading, collections, atoms, dispatch, formatting, namespaces, testing) and this package covers what differs on [Babashka](https://babashka.org/): the built-in namespaces, `bb.edn` tasks, subprocesses, threads and process exit, records and types without Java classes, testing, pods, and packaging. Babashka runs Clojure with an interpreter inside a native binary, so some JVM Clojure advice does not hold there. The `babashka` skill names those cases.

## Companion packages

Eight sibling APM packages. This package layers on top of [clojure-skills](https://github.com/brackendev/clojure-skills) and covers code that Babashka runs. Install both together for Babashka scripts and `bb.edn` projects. When a project also runs JVM code, for example `bb.edn` tasks that start the Clojure CLI, install [clojure-jvm-skills](https://github.com/brackendev/clojure-jvm-skills) as well. The ClojureScript, Biff, Electric, Fulcro, and ClojureDart packages are not required for Babashka work.

| Package | Focus | Layers on |
|---------|-------|-----------|
| [clojure-skills](https://github.com/brackendev/clojure-skills) | Host-neutral [Clojure](https://clojure.org/) family baseline: style, naming, threading, collections, atoms, dispatch, formatting, namespaces, and testing. Triggers on `.clj`, `.cljs`, `.cljc`, `.cljd`. | — |
| [clojure-jvm-skills](https://github.com/brackendev/clojure-jvm-skills) | [Clojure](https://clojure.org/) on the JVM: Java interop, refs / agents / STM, `with-open`, JVM-typed exceptions, `alter-var-root`, and the Clojure CLI / `tools.build` / `clj-kondo` / `cljfmt` / `test-runner` / nREPL workflow. | `clojure-skills` |
| [clojurescript-skills](https://github.com/brackendev/clojurescript-skills) | [ClojureScript](https://clojurescript.org/) style and tooling: JavaScript interop, externs inference, macro stage separation, `catch :default`, JS-flavored numbers and truthiness, and the `cljs.main` workflow. Triggers on `.cljs`, `.cljc` compiled to JS, `shadow-cljs.edn`, `figwheel-main.edn`. | `clojure-skills` |
| [babashka-skills](https://github.com/brackendev/babashka-skills) (this package) | [Babashka](https://babashka.org/) scripting host: built-in namespaces (`babashka.fs`, `babashka.process`, `babashka.http-client`), `bb.edn` tasks, subprocess exit checks, threads and process exit, records and types without Java classes, testing, pods, and packaging. Triggers on `.bb`, `bb.edn`. | `clojure-skills` |
| [biff-skills](https://github.com/brackendev/biff-skills) | [Biff](https://biffweb.com/) 2 web framework on the JVM: conventions and deployment. | `clojure-skills` + `clojure-jvm-skills` |
| [electric-skills](https://github.com/brackendev/electric-skills) | [Electric](https://github.com/hyperfiddle/electric) v3 full-stack reactive framework: `e/defn` components, `e/client` / `e/server` site transfer, `e/for` over `e/diff-by`, `e/watch` state, `e/Token` transactions, `e/Offload`, the `electric-dom3` and `electric-forms5` APIs, and the single-JVM shadow-cljs workflow. Triggers on `hyperfiddle.electric3`, `hyperfiddle.electric-dom3`, `e/defn`, `e/client`, `e/server`, and `com.hyperfiddle/electric`. | `clojure-skills` + `clojurescript-skills` + `clojure-jvm-skills` |
| [fulcro-skills](https://github.com/brackendev/fulcro-skills) | [Fulcro](https://github.com/fulcrologic/fulcro) 3 full-stack framework: `defsc` components, idents and the normalized client database, mutations, `df/load!`, dynamic routing, forms, UI state machines, Fulcro Inspect, and the Pathom 3 server. Triggers on `com.fulcrologic.fulcro.*`, `com.fulcrologic.rad.*`, `com.wsscode.pathom3.*`, `defsc`, `defmutation`, `defrouter`, `df/load!`, and ident vectors. | `clojure-skills` + `clojurescript-skills` + `clojure-jvm-skills` |
| [clojuredart-skills](https://github.com/brackendev/clojuredart-skills) | [ClojureDart](https://github.com/Tensegritics/ClojureDart) on [Flutter](https://flutter.dev/): Dart interop, type hints, `cljd.flutter` directives, async, FFI, REPL, and the Flutter project workflow. Triggers on `.cljd`, `cljd.flutter`. | `clojure-skills` |

## Install

Install [APM](https://github.com/microsoft/apm) 0.34.0 first. Homebrew offers only the newest release, so remove a Homebrew copy with `brew uninstall apm` and install from PyPI with [uv](https://docs.astral.sh/uv/getting-started/installation/): `uv tool install apm-cli==0.34.0`. APM 0.35.0 is not supported because it can delete installed skill files ([microsoft/apm#3372](https://github.com/microsoft/apm/issues/3372)). Then, in a project:

```bash
apm install brackendev/babashka-skills --target all
apm install brackendev/clojure-skills --target all
```

Globally for your user account:

```bash
apm install brackendev/babashka-skills -g --target all
apm install brackendev/clojure-skills -g --target all
```

Update later with `apm update [-g]`. Remove with `apm uninstall brackendev/babashka-skills [-g]`. A local filesystem path can replace the shorthand at either scope.

## Requirements

- [Babashka](https://babashka.org) (`bb`) for any skill in this package and to run the `bb-rules` hook. The skills were checked against bb v1.13.229. Without bb, the hook does nothing and prints a notice on standard error.
- [clojure-skills](https://github.com/brackendev/clojure-skills) installed alongside, for the host-neutral baseline.
- [clj-kondo](https://github.com/clj-kondo/clj-kondo) for the `/bb-fix` lint step.
- [cljfmt](https://github.com/weavejester/cljfmt) for the `/bb-fix` format step. The step uses the first of these that the project has: a `bb.edn` task that runs cljfmt, a `:cljfmt` alias in `deps.edn`, the standalone `cljfmt` binary, or a Clojure CLI tool installed as `cljfmt`. Without any of them, the format step reports that it was skipped.

## Command guide

A quick guide to every slash command. The detailed entries under [Skills](#skills) cover arguments and examples.

| Command | Use it when | What it does |
|---------|-------------------|--------------|
| `/bb-fix` | Lint, format, or tests need attention in code that bb runs | Runs the project's bb lint, format, and test tasks, or lints the `bb.edn` paths, `bb.edn`, and loose `.bb` scripts and rewrites their formatting with `cljfmt fix` |

## Skills

User-invocable skills share an argument grammar, scope vocabulary, and mutation default. See [CONVENTIONS.md](CONVENTIONS.md) for the full standard. In short: skills accept natural-language keywords and bare paths, the single sanctioned flag is `--report`, and mutating skills apply changes by default.

### Quality

#### `/bb-fix [lint|format|test] [--report] [all|<path>]`

Fix a Babashka project or script. The skill runs all three steps by default. When `bb.edn` defines lint or format tasks, the skill runs them, because they record which files the project checks, and it lists the files they leave out instead of checking those. Without a lint task, the lint step runs clj-kondo on the `bb.edn` `:paths`, on `bb.edn` itself, and on each loose `.bb` script in its own run, because scripts without an `ns` form share the `user` namespace. Without a format task, the format step rewrites the same files in place with `cljfmt fix`, through the first cljfmt runner the project has. The test step runs the project's bb test task and reports a failure when the task exits 0 although `clojure.test` counted failures, because `run-tests` does not set the exit status. Pass `--report` to replace the format step with `cljfmt check`, which previews diffs without writing. A path argument limits the lint and format steps to one file or directory. In a project that also has `deps.edn`, run `/clj-fix` from clojure-skills for the JVM side.

```bash
/bb-fix
/bb-fix lint
/bb-fix --report
/bb-fix scripts/release.bb
```

### Auto-triggered

These skills activate from conversation context. They have no slash commands, except in Gemini CLI, which offers every skill as one.

| Skill | Triggers |
|-------|----------|
| **babashka** | `.bb` files, `bb.edn`, a `#!/usr/bin/env bb` shebang, bb tasks, the `babashka.*` namespaces, pods, `bb nrepl-server`, `bb uberscript`, `bb uberjar`, `:min-bb-version`, SCI, or any mention of Babashka. Covers the built-in namespaces, `bb.edn` tasks, subprocesses, threads and process exit, records and types, Java classes, reader conditionals, testing, pods, packaging, the REPL, upgrading bb, and when to move code to the JVM. Defers to the [clojure](https://github.com/brackendev/clojure-skills) baseline for general style. The skill opens with Iron Laws (BB-1 through BB-4), rules that prevent lost work, false success reports, and wrong branches. The agent does not write code that breaks them, and it reports a violation it finds in existing code before changing it. |

## Hooks

The package includes one hook, `bb-rules`.

### `bb-rules`

Agents seldom load an auto-triggered skill on their own, so this hook gives the agent the BB-1 through BB-4 Iron Laws of the `babashka` skill as text. In a project with `bb.edn`, it puts the condensed laws into the agent's context when a session starts, and again after `/clear` or a compaction, with an instruction to load the `babashka` skill along with the `clojure` skill. When the session starts elsewhere, the hook delivers the laws on the first edit of a `.clj`, `.cljc`, `.edn`, or `.bb` file in a `bb.edn` project, once per session. bb runs a `.bb` file wherever it is, so the first edit of a `.bb` file delivers the laws in any directory, including a standalone script with no project file and a script in a project without `bb.edn`. The block also says that in a project that runs JVM code too, the JVM-4 and JVM-5 laws of clojure-jvm-skills do not apply to code that bb runs. The hook never edits a file. Without Babashka it does nothing and prints a notice on standard error.

The hook has been run only under Claude Code, where it registers `SessionStart` and `PostToolUse`. The package ships empty Copilot and Cursor hook files, so APM writes no Copilot hook and no native Cursor hook. APM also installs the generic copy for Codex, Gemini CLI, Kiro, and Windsurf, where its effect has not been tested. OpenCode and Grok Build have no hook support.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).

## License

MIT. See [LICENSE](LICENSE).
