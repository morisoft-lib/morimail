---
phase: 07-html-image-helper-module
verified: 2026-11-23T19:00:00Z
status: passed
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
covered_digest: "v4:sha256:7ec029ce3c08b6400064282670af4d6cacaf62bb7efb5348f11c9f451cb743ae"
behavior_unverified: 0
overrides_applied: 0
re_verification:
  previous_status: human_needed
  previous_score: 7/7
  gaps_closed: []
  gaps_remaining: []
  regressions: []
human_verification:
  - test: "Push the unpushed commits (HEAD is 21 ahead of origin/main at 1e5e891) and let GitHub Actions run on Temurin 17"
    expected: "All three CI steps are green on Temurin 17: `./mvnw -B -ntp verify`, `./mvnw -B -ntp -Prelease clean verify -Dgpg.skip -pl morimail,morimail-html -am`, and the offline `unshare --net` build. The commits to cover include the compile-scope change b333689 and the code-review fixes 4e49cd3, 66b5264, 457d469, 2a14312."
    why_human: "Claude never pushes, and only JDK 21 is installed locally. The scope change alters the published POM and the dependency resolution of a published module, and the download reader fix changes thread and error handling; both are what a Temurin 17 run exercises. UAT test 1 recorded a pass for the earlier pushed commits only."
  - test: "Rerun /gsd-verify-work 7 so the UAT file reconciles G-07-2 (07-UAT.md still shows status diagnosed, gap failed, tests 3 and 4 pending)"
    expected: "G-07-2 is marked closed; the remaining owner-review items (A1-A7, P1-P10 in the 07-01 to 07-03 SUMMARYs) are accepted or corrected, apart from the D-02 scope point the owner has already decided. Also decide the two open review findings (07-REVIEW-DISPOSITION.md): the undocumented `strip()` trimming (IN-01) and the untested Error-rethrow branch (IN-02)."
    why_human: "These are owner-reserved decisions, not code defects."
---

# Phase 7: HTML Image Helper Module Verification Report

**Phase Goal:** A template author can write ordinary image URLs in an HTML template, and one call to `HtmlImages` turns them into `cid:` references plus inline images that pass the Phase 5 checks.
**Verified:** 2026-11-23
**Status:** human_needed
**Re-verification:** Yes. The previous report (e5138da) went stale because four code-review fix commits touched `HtmlImages.java` and `HtmlImagesTest.java`. It had no `gaps:` section, so all seven truths were re-checked against the current code.

## Goal Achievement

The goal is achieved in the codebase. `HtmlImages` rewrites image sources to `cid:` references and returns the matching `Attachment`s. The end-to-end test sends them through `Mailer` to `recording`, so they pass `Mail.build()`. The status is `human_needed` only because the commits since origin/main (21) have never run on Temurin 17 and the UAT record is not yet reconciled.

### What changed since the last verification

`git diff 8d07df3 HEAD` outside `.planning` touches five files:

| File | Change | Effect on must-haves |
|------|--------|----------------------|
| `HtmlImages.java` | `raw.replace("&amp;", "&").strip()` at line 349 (IN-02 fix). `ExecutionException` handler rethrows an `Error` cause (lines 568-570), reader thread still completes the future with every `Throwable`. `+suffix` Javadoc sentence. | The `strip()` feeds the `$$` check, scheme detection, dedupe key, URL parsing and `data:` decoding. `raw` is kept for messages and for the replaced span, which still uses `m.start(g)` and `m.end(g)`. I read the loop and found no path where a stripped value is used where the original is needed. The rethrow sits inside the try-with-resources, so the stream is still closed, and `UncheckedIOException` still maps to a transient failure. No truth weakens. |
| `HtmlImagesTest.java` | New `surroundingWhitespaceOfASourceIsIgnored` | Suite is 71 tests, was 70. |
| `morimail-html/pom.xml`, `ModuleRulesTest.java`, `docs/architecture.md` | Compile-scope change of plan 07-04 (already verified in the last report) | Re-checked below. |

### Observable Truths (ROADMAP success criteria)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | New module `morimail-html` depends on `morimail`, uses no other runtime dependency, is in the release profile, `morimail` jar unchanged | VERIFIED | `morimail-html/pom.xml`: `de.morisoft:morimail` with no scope element and no version, plus a comment "compile scope (D-02, amended 2026-11-23)". `dependency:tree` from my run: `de.morisoft:morimail:jar:0.2.0-SNAPSHOT:compile` with `jackson-databind` below it; no other non-test dependency. The `morimail` tree shows only `jackson-databind`, `jackson-annotations` and `jackson-core` at compile scope. `git diff --name-only f9b2f57 -- morimail` is empty, so the core jar's inputs are unchanged. `grep` for `morimail-html` / `morimail.html` in `morimail/pom.xml` and `morimail/src` gives 0 matches. The release profile build (below) writes the helper's main, sources and Javadoc jars. |
| 2 | `embed(template, baseDir)` gives `cid:` references plus one `Attachment` per distinct image; `cid:`, `data:`, unknown schemes, `$$`, subject and text unchanged | VERIFIED | Read the rewrite loop (`HtmlImages.java` 340-420): a `$$` value is skipped, a non-`file` non-remote non-data scheme is skipped (so `cid:` and unknown schemes stay byte for byte), `Result` returns the original template when nothing is replaced, replacement touches only the `src` value span. The new test asserts a whitespace-padded `cid:` source is left unchanged. `HtmlImagesTest` green. |
| 3 | Local `src` outside the base directory, or a missing, unreadable or non-image file, fails with `InvalidMailException` naming the `src` as written | VERIFIED | `resolveLocal(raw, value, ...)` and `invalid(raw, ...)` use `raw`, the text as written. Tests `sourcesOutsideTheBaseDirectoryFail` and siblings green. The whitespace fix does not change the message source. |
| 4 | Content IDs from the file name, pass the Phase 5 rules, numeric suffix on collision, same source gives one image | VERIFIED | `newId(name, taken)` seeded with the template's existing `cid:` references; dedupe by real path (local) or stripped value (remote and `data:`). `cidPatternAgreesWithTheCore` and the collision test green. |
| 5 | `http(s)` unchanged by default; with a download directory, downloaded (WireMock, offline) with timeout, 3 MiB cap, image content-type check; failures map to the decided categories | VERIFIED | `remote` is true only when `workDir != null`. `download` uses a private reader thread, a deadline budget, `>= MAX_IMAGE_BYTES` refusal, `IMAGE_TYPE` check; `HtmlImagesDownloadTest` green offline. The Error-rethrow branch is untested (review IN-02, owner decision), but the code is read and correct: the future is always completed, so the caller cannot hang. |
| 6 | Template converted once, rendered for two records, sent to `recording`: two mails with the inline images that pass `build()` | VERIFIED | `EmbedThroughRecordingTest` builds `Mail` via `Mail.using(...).template(result.template(), data)`, attaches `result.images()`, calls `build()` and `Mailer.send` to `recording`. Green. |
| 7 | Storable form: `toDataUris` and `embed` with a work directory round trip; `morimail` POM and jar do not reference `morimail-html` | VERIFIED | `HtmlImagesDataUriTest` green. `data` branch decodes into a unique work-directory file with the same `IMAGE_TYPE` and 3 MiB checks. `ModuleRulesTest.morimailDoesNotReferenceTheHelper` and `mainImportsOnlyTheJdkAndTheCore` pass; main imports are `java.*` plus `Attachment`, `InvalidMailException`, `MailTemplate`, `TransientMailException`. |

**Score:** 7/7 truths verified (0 present but behavior-unverified)

None of the truths asserts a state transition or a cancellation/cleanup/ordering invariant that no test exercises, with one note: the Error-rethrow path in `download` is cleanup-adjacent and has no test. It is not a roadmap truth, and review IN-02 records it as an owner decision. No coincidental-reliance finding.

### Plan 07-04 must-haves (gap G-07-2) re-checked

| Must-have | Status | Evidence |
|-----------|--------|----------|
| POM declares `morimail` with no scope; other four dependencies test scope; no version, optional or exclusions | VERIFIED | Read lines 17-44 of the POM. |
| Helper's only non-test direct dependency is `morimail` at compile; core tree has no helper; core runtime is the three Jackson artifacts | VERIFIED | Tree output from my offline `verify dependency:tree` run. |
| Nothing changed under `morimail/` since f9b2f57 | VERIFIED | `git diff --name-only f9b2f57 -- morimail` is empty. |
| `ModuleRulesTest` Javadoc and `docs/architecture.md` state compile scope | VERIFIED | Diff shows the Javadoc line "it comes with the compile-scoped `morimail`". |
| Offline verify and offline scoped release build green | VERIFIED | Runs below. |

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `morimail-html/pom.xml` | Module POM, compile-scoped core, release profile | VERIFIED | Present, built. |
| `HtmlImages.java` | `embed` (2 forms), `toDataUris` (2 forms), `Result` | VERIFIED | Substantive and wired from the tests; no debt markers. |
| Five test classes | 71 tests | VERIFIED | `EmbedThroughRecordingTest`, `HtmlImagesDataUriTest`, `HtmlImagesDownloadTest`, `HtmlImagesTest`, `ModuleRulesTest`; reactor reports 71 tests in `morimail-html`, 0 failures. |
| `ci.yml`, parent `pom.xml` | Scoped release line, module entry | VERIFIED | Unchanged since 8d07df3. |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `morimail-html/pom.xml` | parent `pom.xml` | version from dependencyManagement | WIRED | Tree resolves `0.2.0-SNAPSHOT`. |
| `HtmlImages` | core `build()` | `Attachment` with `contentId`, `template()` | WIRED | `EmbedThroughRecordingTest`. |
| `morimail` | `morimail-html` | none allowed | NOT REFERENCED (as required) | 0 matches. |

### Data-Flow Trace (Level 4)

| Artifact | Data | Source | Real Data | Status |
|----------|------|--------|-----------|--------|
| `HtmlImages.embed` | image bytes and content type | real file path, WireMock body, decoded `data:` payload | Yes: tests compare recorded attachments and work-directory bytes with the originals | FLOWING |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| `morimail` and `morimail-html` build, tests, dependency tree (offline) | `./mvnw -B -ntp -o verify dependency:tree -pl morimail,morimail-html` | BUILD SUCCESS; 366 and 71 tests, 0 failures | PASS |
| Scoped release build (doclint, Javadoc, sources jar) | `./mvnw -B -ntp -o -Prelease clean verify -Dgpg.skip -pl morimail,morimail-html -am` | BUILD SUCCESS; `morimail-html-0.2.0-SNAPSHOT.jar`, `-sources.jar`, `-javadoc.jar` written | PASS |
| Core names no helper | `grep -c morimail-html morimail/pom.xml`; `grep -rn` in `morimail/src` | 0; 0 | PASS |
| Debt markers | `grep -rn -E "TBD|FIXME|XXX|TODO|HACK" morimail-html` | 0 | PASS |

The orchestrator's full-reactor run (600 tests, 0 failures) agrees with the two modules I ran; I did not repeat the sample module.

### Probe Execution

No probes declared by this phase. Step 7c: not applicable.

### Requirements Coverage

All seven IDs from the PLAN frontmatter (07-01: HTML-01, 02, 03, 04, 06; 07-02: HTML-05, 04; 07-03: HTML-07, 01, 06; 07-04: HTML-01) appear in `REQUIREMENTS.md`, checked and mapped to Phase 7 as Complete. No requirement mapped to Phase 7 is missing from a plan, so none is orphaned.

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| HTML-01 | 07-01, 07-03, 07-04 | Module `morimail-html`, depends on `morimail`, no other runtime dependency, in release profile, core jar unchanged | SATISFIED | Truth 1 |
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
- Incremental code review (07-REVIEW.md): 0 critical, 0 warning, 2 info, both `open` in `07-REVIEW-DISPOSITION.md`. IN-01: `strip()` trims Unicode white space, wider than the HTML rule, and the class Javadoc does not mention that surrounding white space of a `src` is ignored. IN-02: the Error-rethrow branch has no test. Neither affects a must-have.
- `07-SECURITY.md`: status verified, 19 of 19 threats closed, `threats_open: 0`. I spot-read the mitigations tied to code changed since the last report (T-07-08 reader and cap, T-07-13 and T-07-14 data decoding) in the current source; they match.
- `.planning/ROADMAP.md` line 119 still says "provided `morimail`" for plan 07-01. It records what that plan did; plan 07-04 left it alone. Info only.
- Rule 0 holds: the post-verification fixes add no dependency, class or option.
- The working tree has two untracked planning files (`07-PATTERNS.md`, `.planning/state.json`); neither is implementation.

### Human Verification Required

#### 1. Temurin 17 CI run of everything since origin/main

**Test:** Push the unpushed commits (HEAD is 21 ahead of `origin/main` at 1e5e891) and open the GitHub Actions run.
**Expected:** `verify`, the scoped release build and the offline `unshare --net` build are all green on Temurin 17.
**Why human:** Claude never pushes, and only JDK 21 is installed locally. The published helper POM resolution and the reader thread's error handling are what this run exercises.

#### 2. Reconcile the UAT record and decide the open review items

**Test:** Rerun `/gsd-verify-work 7`; read the "Owner review" sections of the 07-01 to 07-04 SUMMARYs; triage review IN-01 and IN-02.
**Expected:** G-07-2 is marked closed. A1-A7 and P1-P10 are accepted or corrected. IN-01 and IN-02 are fixed, deferred or accepted.
**Why human:** Owner-reserved decisions, not code defects. Also note from the 07-04 review: Maven's nearest-wins still lets a consumer's direct `morimail` 0.1.0 override the helper's, in which case `build()` fails loudly rather than silently.

### Gaps Summary

No gaps. All seven success criteria and all seven requirement IDs are backed by code I read at HEAD and gates I ran offline: the helper and core build with 366 and 71 tests green, the dependency tree shows `morimail` at compile scope as the helper's only non-test dependency, the scoped release build writes the three helper jars, and the core's sources are unchanged. The four review-fix commits do not weaken any truth. The status is `human_needed` because the Temurin 17 run and the UAT reconciliation belong to the owner.

---

_Verified: 2026-11-23_
_Verifier: Claude (gsd-verifier)_
