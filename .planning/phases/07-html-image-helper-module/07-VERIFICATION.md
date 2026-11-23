---
phase: 07-html-image-helper-module
verified: 2026-11-23T18:00:00Z
status: human_needed
score: 7/7 must-haves verified
covered_files:
  - .github/workflows/ci.yml
  - .planning/phases/07-html-image-helper-module/07-01-PLAN.md
  - .planning/phases/07-html-image-helper-module/07-01-SUMMARY.md
  - .planning/phases/07-html-image-helper-module/07-02-PLAN.md
  - .planning/phases/07-html-image-helper-module/07-02-SUMMARY.md
  - .planning/phases/07-html-image-helper-module/07-03-PLAN.md
  - .planning/phases/07-html-image-helper-module/07-03-SUMMARY.md
  - .planning/phases/07-html-image-helper-module/07-04-PLAN.md
  - .planning/phases/07-html-image-helper-module/07-04-SUMMARY.md
  - docs/architecture.md
  - morimail-html/pom.xml
  - morimail-html/src/main/java/de/morisoft/morimail/html/HtmlImages.java
  - morimail-html/src/test/java/de/morisoft/morimail/html/EmbedThroughRecordingTest.java
  - morimail-html/src/test/java/de/morisoft/morimail/html/HtmlImagesDataUriTest.java
  - morimail-html/src/test/java/de/morisoft/morimail/html/HtmlImagesDownloadTest.java
  - morimail-html/src/test/java/de/morisoft/morimail/html/HtmlImagesTest.java
  - morimail-html/src/test/java/de/morisoft/morimail/html/ModuleRulesTest.java
  - pom.xml
covered_digest: "v4:sha256:b955a3b7bc8d804f749809bbf18b72613d7dc0f3d72ed8e030ff44b9c9353f5f"
behavior_unverified: 0
overrides_applied: 0
re_verification:
  previous_status: human_needed
  previous_score: 7/7
  gaps_closed:
    - "G-07-2 (07-UAT.md test 2): morimail declared in compile scope in morimail-html/pom.xml; docs, ModuleRulesTest Javadoc and 07-CONTEXT.md aligned (plan 07-04)"
  gaps_remaining: []
  regressions: []
human_verification:
  - test: "Push the 07-04 commits (and the review-record commits up to HEAD) and let GitHub Actions run on Temurin 17"
    expected: "All three CI steps are green on Temurin 17: `./mvnw -B -ntp verify`, `./mvnw -B -ntp -Prelease clean verify -Dgpg.skip -pl morimail,morimail-html -am`, and the offline `unshare --net` build. origin/main is at 1e5e891; HEAD is nine commits ahead, among them the compile-scope change b333689."
    why_human: "Claude never pushes, and only JDK 21 is installed locally. The scope change alters the published POM and the dependency resolution of a published module, which is exactly what the Temurin 17 scoped release build exercises. UAT test 1 recorded a pass for the earlier pushed commits only."
  - test: "Rerun /gsd-verify-work 7 so the UAT file reconciles G-07-2 (07-UAT.md still shows status diagnosed, gap failed)"
    expected: "G-07-2 is marked closed; the remaining owner-review items (A1-A7, P1-P10 in the 07-01 to 07-03 SUMMARYs) are accepted or corrected, apart from the D-02 scope point the owner has already decided."
    why_human: "The owner decision in UAT reversed D-02. The repository now follows it, but only the owner can accept the other flagged assumptions and close the UAT record."
---

# Phase 7: HTML Image Helper Module Verification Report

**Phase Goal:** A template author can write ordinary image URLs in an HTML template, and one call to `HtmlImages` turns them into `cid:` references plus inline images that pass the Phase 5 checks.
**Verified:** 2026-11-23
**Status:** human_needed
**Re-verification:** Yes, after gap-closure plan 07-04 (G-07-2). The previous report had no `gaps:` section, so all seven truths were re-checked, with full attention on truth 1 (the scope change) and a regression run for the rest.

## Goal Achievement

The goal is achieved in the codebase. Gap G-07-2 is closed in the repository: `morimail-html/pom.xml` declares `de.morisoft:morimail` with Maven's default compile scope, and nothing outside the planned four files changed. I ran the dependency, test, release and jar gates myself. The status is `human_needed` only because the 07-04 commits have not run on Temurin 17 (the owner pushes) and the UAT record is not yet reconciled.

### Observable Truths (ROADMAP success criteria)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | New module `morimail-html` depends on `morimail`, uses no other runtime dependency, is in the release profile, `morimail` jar unchanged | VERIFIED | `morimail-html/pom.xml` has `de.morisoft:morimail` with no scope element (so compile) and a comment "compile scope (D-02, amended 2026-11-23)". The grep for "provided" in the POM and in `ModuleRulesTest.java` finds nothing. `dependency:tree`: the only non-test direct dependency of the helper is `+- de.morisoft:morimail:jar:0.2.0-SNAPSHOT:compile`, no `:provided` node. `dependency:list -DincludeScope=runtime` for the helper prints morimail, jackson-databind 2.23.0, jackson-core 2.23.0 and jackson-annotations 2.23, all compile. The core's tree has 0 matches for `morimail-html`. `git diff --name-only f9b2f57 -- morimail/` is empty. The `morimail` jar built from HEAD and from f9b2f57 has the same SHA-256 (`07874795ed81...6c0`) and file list, with 0 `html` entries. `ci.yml` and the parent `pom.xml` still carry the scoped release line for both modules. |
| 2 | `embed(template, baseDir)` gives `cid:` references plus one `Attachment` per distinct image; `cid:`, `data:`, unknown schemes, `$$`, subject and text unchanged | VERIFIED | `HtmlImages.java` (693 lines) unchanged by 07-04 (`git diff 8d07df3 -- morimail-html/src/main/` is empty). `HtmlImagesTest` passes inside the 70 helper tests. |
| 3 | Local `src` outside the base directory, or a missing, unreadable or non-image file, fails with `InvalidMailException` naming the `src` as written | VERIFIED | Same code and tests as above, all green. |
| 4 | Content IDs from the file name, pass the Phase 5 rules, numeric suffix on collision, same source gives one image | VERIFIED | Same. `cidPatternAgreesWithTheCore` still agrees with `Mail.build()`. |
| 5 | `http(s)` unchanged by default; with a download directory, downloaded (WireMock, offline) with timeout, 3 MiB cap, image content-type check; failures map to the decided categories | VERIFIED | `HtmlImagesDownloadTest` (30 tests) green in my offline run; main code unchanged by 07-04. |
| 6 | Template converted once, rendered for two records, sent to `recording`: two mails with the inline images that pass `build()` | VERIFIED | `EmbedThroughRecordingTest` green. With compile scope the test classpath still resolves the core and its test-jar; the offline reactor verify builds all modules. |
| 7 | Storable form: `toDataUris` and `embed` with a work directory round trip; `morimail` POM and jar do not reference `morimail-html` | VERIFIED | `HtmlImagesDataUriTest` (16 tests) green. `grep` for `morimail-html` / `morimail.html` in `morimail/pom.xml` and `morimail/src` finds nothing; `ModuleRulesTest.morimailDoesNotReferenceTheHelper` passes. |

**Score:** 7/7 truths verified (0 present but behavior-unverified)

### Plan 07-04 must-haves (gap G-07-2)

| Must-have | Status | Evidence |
|-----------|--------|----------|
| POM declares `morimail` with no scope element; comment says "compile scope (D-02, amended 2026-11-23)"; other four dependencies keep test scope; no version, optional or exclusions element | VERIFIED | Read in full: lines 17-23 and 24-44 of `morimail-html/pom.xml`. |
| D-03 as amended: core tree has no helper; helper's only non-test direct dependency is `morimail` compile, no provided node; runtime list is morimail plus the three Jackson artifacts; the core's is exactly the three Jackson artifacts | VERIFIED | Tree and list output above. The core's tree shows only `jackson-databind` (with annotations and core) at compile scope. |
| `morimail` jar equal to f9b2f57 in file list and SHA-256; nothing changed under `morimail/`, `morimail-sample-serial/`, `morimail-html/src/main/`, `.github/`, parent `pom.xml`, `docs/spec.md`, `README.md` since 8d07df3 | VERIFIED | Jar gate and `git diff --name-only 8d07df3 -- <those paths>` both empty. |
| `ModuleRulesTest` Javadoc says "compile-scoped"; `docs/architecture.md` row 43 says "`morimail` has compile scope in the helper's POM." with the nearest-wins and 0.1.0 reason and G-07-2; `07-CONTEXT.md` has dated amendments under D-02 and D-03 and the Established Patterns line says compile scope | VERIFIED | Read all four places. Line 43 of `docs/architecture.md` ends with "Owner decision of 2026-11-23 (gap G-07-2), replacing the earlier `provided` scope." `07-CONTEXT.md` lines 35, 37 and 136 hold the amendments and the changed pattern line. The files changed since 8d07df3 are only the four planned ones plus 07-04 PLAN/SUMMARY and the review records. |
| `ModuleRulesTest` still passes; offline verify and offline scoped release build green; helper main, sources and Javadoc jars written | VERIFIED | My runs below. |
| Two commits with maintainer identity, trailer, not pushed | VERIFIED | `b333689` (fix) and `596d77a` (docs). `git log f9b2f57..HEAD` shows the single identity `Stephan Wald <stephan@morisoft.de>` for author and committer. Both are ahead of `origin/main` (1e5e891). |
| Temurin 17 run of the 07-04 commits, then `/gsd-verify-work 7` | PENDING (backstop) | Routed to Human Verification. |

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `morimail-html/pom.xml` | Module POM, compile-scoped core, release profile that unbinds the test executions, `Automatic-Module-Name` | VERIFIED | Present. The built jar manifest shows `Automatic-Module-Name: de.morisoft.morimail.html`. |
| `HtmlImages.java` | `embed` (2 forms), `toDataUris` (2 forms), `Result` | VERIFIED | Public types are exactly `HtmlImages` and `Result`. Imports are `java.*` and the four core types only. No debt markers anywhere under `morimail-html/`. |
| Five test classes | 70 tests | VERIFIED | 2 + 19 + 30 + 16 + 3, 0 failures. |
| `ci.yml`, parent `pom.xml` | Scoped release line, module entry | VERIFIED | Unchanged. |
| `docs/architecture.md`, `07-CONTEXT.md` | Scope decision recorded | VERIFIED | See 07-04 must-haves. |

Tracked files under `morimail-html/` are exactly the POM, `HtmlImages.java` and five test classes.

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `morimail-html/pom.xml` | parent `pom.xml` | scopeless `morimail` takes its version from dependencyManagement | WIRED | The helper's tree resolves `0.2.0-SNAPSHOT` without a version element. |
| `docs/architecture.md` | `morimail-html/pom.xml` | the row states the scope the POM declares | WIRED | Both say compile. |
| `HtmlImages` | core `build()` | `Attachment` with `contentId`, `template()` | WIRED | End-to-end tests send through `Mailer` to `recording`. |
| `morimail` | `morimail-html` | none allowed | NOT REFERENCED (as required) | 0 matches in the core's POM, sources and tree. |

### Data-Flow Trace (Level 4)

| Artifact | Data | Source | Real Data | Status |
|----------|------|--------|-----------|--------|
| `HtmlImages.rewrite` | image bytes | local real path, WireMock body, decoded `data:` payload | Yes: tests compare recorded attachments and work-dir bytes with the originals | FLOWING |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Whole reactor, offline | `./mvnw -B -ntp -o verify` | BUILD SUCCESS: morimail 366, morimail-html 70, sample 163 tests, 0 failures | PASS |
| Scoped release build (doclint, failOnWarnings, jars) | `./mvnw -B -ntp -o -Prelease clean verify -Dgpg.skip -pl morimail,morimail-html -am` | BUILD SUCCESS; `morimail-html-0.2.0-SNAPSHOT.jar`, `-sources.jar`, `-javadoc.jar` written | PASS |
| Helper runtime classpath | `dependency:list -DincludeScope=runtime` | morimail, jackson-databind, jackson-core, jackson-annotations, all compile | PASS |
| Helper direct non-test dependencies | `dependency:tree` | only `de.morisoft:morimail:jar:0.2.0-SNAPSHOT:compile`; 0 `:provided` | PASS |
| Core tree names no helper | `grep -c ':morimail-html:'` | 0 | PASS |
| Core jar unchanged | SHA-256 and file list against a worktree at f9b2f57 | identical | PASS |

### Probe Execution

No probes declared by this phase. Step 7c: not applicable.

### Requirements Coverage

All seven IDs appear in PLAN frontmatter (07-01: HTML-01, 02, 03, 04, 06; 07-02: HTML-05, 04; 07-03: HTML-07, 01, 06; 07-04: HTML-01) and in REQUIREMENTS.md, where all seven are marked complete and mapped to Phase 7. None is orphaned.

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| HTML-01 | 07-01, 07-03, 07-04 | Module `morimail-html`, depends on `morimail`, no other runtime dependency, in release profile, core jar unchanged | SATISFIED | Truth 1. The helper declares only `morimail`; Jackson reaches it through `morimail`, which is the core's own dependency, not one of the helper's. |
| HTML-02 | 07-01 | `embed` rewrites `<img src>`; `cid:`, `data:`, unknown, `$$`, subject, text unchanged | SATISFIED | Truth 2 |
| HTML-03 | 07-01 | Local sources only under the base directory; failures name the `src` | SATISFIED | Truth 3 |
| HTML-04 | 07-01, 07-02 | Content IDs from file name, suffix on collision, one image per source | SATISFIED | Truth 4 |
| HTML-05 | 07-02 | `http(s)` opt-in download with timeout, cap, content-type check, offline tests | SATISFIED | Truth 5 |
| HTML-06 | 07-01, 07-03 | Converted template sent to `recording` for several records | SATISFIED | Truth 6 |
| HTML-07 | 07-03 | `toDataUris` and `data:` decoding in `embed`, same checks and cap | SATISFIED | Truth 7 |

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| (none) | - | No `TBD`, `FIXME`, `XXX`, `TODO` or `HACK` under `morimail-html/` | - | - |

Notes:
- `.planning/ROADMAP.md` line 119 (the 07-01 plan line) still says "provided `morimail`". It records what plan 07-01 did, and plan 07-04 deliberately left it alone. Info only.
- Code review at 64b1d92: 0 critical, 0 warning, 3 info; none touches a must-have. `07-REVIEW-DISPOSITION.md` lists those three as open, which is the owner's call.
- No secrets or real addresses in the diff since f9b2f57 (only `example.com` and `example.org`). Every commit since f9b2f57 is authored and committed by Stephan Wald.
- Rule 0 holds: the 07-04 change adds no code, no dependency and no test, only a scope change and text.

### Human Verification Required

#### 1. Temurin 17 CI run of the 07-04 commits

**Test:** Push the unpushed commits (HEAD is nine ahead of `origin/main` at 1e5e891, including `b333689`) and open the GitHub Actions run.
**Expected:** `verify`, the scoped release build and the offline `unshare --net` build are all green on Temurin 17.
**Why human:** Claude never pushes, and only JDK 21 is installed locally. The compile-scope change affects what the published helper POM resolves, which the scoped release build exercises.

#### 2. Reconcile the UAT record and accept the remaining owner-review items

**Test:** Rerun `/gsd-verify-work 7`; read the "Owner review" sections of the 07-01 to 07-04 SUMMARYs.
**Expected:** G-07-2 is marked closed. A1-A7 and P1-P10 are accepted or corrected. Also note the 07-04 review points: 0.1.0 fails loudly at `build()` (it does not break silently as the UAT root cause says), a consumer's direct `morimail` 0.1.0 still wins under nearest-wins, and no permanent test pins the scope (the dependency gates are the proof).
**Why human:** These are owner-reserved decisions, not code defects.

### Gaps Summary

No gaps. All seven success criteria, all seven requirement IDs and every plan 07-04 must-have are backed by code I read and gates I ran: the dependency tree and runtime list, the offline verify (366 + 70 + 163 tests, 0 failures), the offline scoped release build with the three helper jars, and the byte-identical `morimail` jar. G-07-2 is closed in the repository. The status is `human_needed` because the Temurin 17 run of the 07-04 commits and the UAT reconciliation belong to the owner.

---

_Verified: 2026-11-23_
_Verifier: Claude (gsd-verifier)_
