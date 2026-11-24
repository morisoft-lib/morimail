---
phase: 07-html-image-helper-module
plan: 04
subsystem: html-image-helper
tags: [morimail-html, maven-scope, compile-scope, gap-closure, G-07-2, nearest-wins]

requires:
  - phase: 07-html-image-helper-module
    provides: morimail-html module, ModuleRulesTest, decisions row in docs/architecture.md (Plans 07-01 to 07-03)
affects: [08 docs and release readiness]

actuals:
  tokens: 6000
  tasks: 2
  commits: 2
plan_head_before: 344587492c0310ba66aeb0af5412d943d9aabd46
plan_head_after: 596d77a8844fa06e6bc6678f7a3dbb5509684be4
commits: 2

tech-stack:
  added: []
  patterns:
    - "A sibling module declares morimail with no scope element and no version, so Maven gives it compile scope and the parent supplies the version (as morimail-sample-serial does)"

key-files:
  created: []
  modified:
    - morimail-html/pom.xml
    - morimail-html/src/test/java/de/morisoft/morimail/html/ModuleRulesTest.java
    - docs/architecture.md
    - .planning/phases/07-html-image-helper-module/07-CONTEXT.md

key-decisions:
  - "G-07-2: morimail-html declares morimail in compile scope (owner decision in UAT, D-02 and D-03 amended 2026-11-23); runtime classpath morimail plus Jackson through morimail; morimail jar unchanged"

requirements-completed: [HTML-01]

coverage:
  - id: D1
    description: "morimail-html declares morimail in compile scope; the helper's runtime classpath is morimail plus Jackson through morimail, the core's tree has no morimail-html and its runtime classpath is still the three Jackson artifacts"
    requirement: HTML-01
    verification:
      - kind: command
        ref: "./mvnw -B -ntp -o dependency:tree and dependency:list -DincludeScope=runtime on morimail,morimail-html (plan 07-04 Task 1 gates)"
        status: pass
    human_judgment: false
  - id: D2
    description: "docs/architecture.md, the ModuleRulesTest Javadoc and 07-CONTEXT.md (dated D-02 and D-03 amendments) say compile scope; no older 07 file is rewritten"
    requirement: HTML-01
    verification:
      - kind: command
        ref: "plan 07-04 Task 2 docs gate and scope guard"
        status: pass
    human_judgment: false
  - id: D3
    description: "The offline verify and the offline scoped release build are green, and the morimail jar equals the one built from f9b2f57"
    requirement: HTML-01
    verification:
      - kind: command
        ref: "./mvnw -B -ntp -o verify; ./mvnw -B -ntp -o -Prelease clean verify -Dgpg.skip -pl morimail,morimail-html -am; jar gate against f9b2f57"
        status: pass
    human_judgment: false

status: complete
---

# Phase 7 Plan 04: morimail in compile scope for morimail-html Summary

**`morimail-html` now declares `morimail` with Maven's default compile scope, so the helper's published POM names the core version it is built against; docs, Javadoc and 07-CONTEXT.md agree, and the `morimail` jar is byte-identical to the one from f9b2f57.**

## What changed (G-07-2)

- **Missing item 1** (commit b333689): `morimail-html/pom.xml` lost the scope element of its `de.morisoft:morimail` dependency and got the comment "compile scope (D-02, amended 2026-11-23): ...". No version, optional, exclusions or explicit scope element was added. The word for the old scope appears in no line of the POM.
- **Missing item 2**: the class Javadoc of `ModuleRulesTest` says Jackson comes with "the compile-scoped `morimail`" (b333689). The decisions row of `docs/architecture.md` says "`morimail` has compile scope in the helper's POM." with the nearest-wins reason and the 0.1.0 `build()` rejection (596d77a). `07-CONTEXT.md` keeps the original D-02 and D-03 text and adds a dated amendment under each, and its Established Patterns line says compile scope (596d77a, 3 lines added, 1 removed).
- **Missing item 3**: the D-03 checks in their amended form passed (below).

## Gate results

- Static gate: POM equals its 8d07df3 version except the new line 18 comment and the deleted scope line; ModuleRulesTest differs only on line 24.
- Tree gate: no `morimail-html` in the core's tree; the helper's only non-test direct dependency is `de.morisoft:morimail:jar:0.2.0-SNAPSHOT:compile`; no provided node.
- Runtime-classpath gate (`dependency:list -DincludeScope=runtime`), helper as printed:
  - `de.morisoft:morimail:jar:0.2.0-SNAPSHOT:compile`
  - `com.fasterxml.jackson.core:jackson-databind:jar:2.23.0:compile`
  - `com.fasterxml.jackson.core:jackson-core:jar:2.23.0:compile`
  - `com.fasterxml.jackson.core:jackson-annotations:jar:2.23:compile`

  The core's runtime classpath is exactly the three Jackson artifacts.
- `./mvnw -B -ntp -o verify`: BUILD SUCCESS (ModuleRulesTest included).
- `./mvnw -B -ntp -o -Prelease clean verify -Dgpg.skip -pl morimail,morimail-html -am`: BUILD SUCCESS; the helper's main, sources and Javadoc jars were written.
- Jar gate: the `morimail` jar has the same file list and SHA-256 as the jar built from f9b2f57 (`07874795ed816c194cc40b9634cc2719994f8a821275b3efac2e3ca3757006c0`).
- Scope guard: nothing under `morimail/`, `morimail-sample-serial/`, `morimail-html/src/main/`, `.github/`, the parent `pom.xml`, `docs/spec.md` or `README.md` changed since 8d07df3; no older 07 phase file changed.
- Hygiene gate: no address outside example.com and example.org, no home path in the diff.
- Commit gates: both commits have Stephan Wald <stephan@morisoft.de> as author and committer, carry the Co-Authored-By line, and are not on origin/main.

## STATE.md decision line

[Phase 07]: 07-04 (G-07-2): morimail-html declares morimail in compile scope (owner decision in UAT, D-02 and D-03 amended 2026-11-23); runtime classpath morimail plus Jackson through morimail; morimail jar unchanged

## Deviations from Plan

None - plan executed exactly as written.

## Owner review

1. **0.1.0 fails loudly, not silently.** The UAT root cause says images "break silently" with `morimail` 0.1.0. At v0.1.0, `Mail.java` lines 412 to 414 make `build()` reject every `Attachment` with a `contentId` ("contentId is not supported (inline images are a later milestone)"). So the failure is an `InvalidMailException` at `build()`. `docs/architecture.md` states it that way. The decision does not change.
2. **Nearest-wins still lets a direct 0.1.0 win.** A consumer who declares `morimail` 0.1.0 directly next to `morimail-html` 0.2.0 still gets 0.1.0. Compile scope helps a consumer who declares only the helper, and it puts the version link into the published POM. Nothing more was built for this (Rule 0).
3. **No permanent scope test.** The dependency gates of this plan are the proof; no new code was added, per the gap notes.

## Known Stubs

None.

## Threat Flags

None. T-07-18 is mitigated as planned: the helper names only `morimail` at the parent-managed version, and the runtime-classpath gate pins the exact set.

## Pending owner actions

1. Push the 07-04 commits and confirm the three CI steps (verify, scoped release build, offline build) are green on Temurin 17.
2. Rerun `/gsd-verify-work 7` so G-07-2 is reconciled through `gap_ids`.

## Self-Check: PASSED

- `morimail-html/pom.xml`, `ModuleRulesTest.java`, `docs/architecture.md` and `07-CONTEXT.md` exist and carry the planned text.
- Commits b333689 (fix) and 596d77a (docs) are ancestors of HEAD.
