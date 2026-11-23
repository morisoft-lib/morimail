---
phase: 07-html-image-helper-module
verified: 2026-11-23T15:30:00Z
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
  - docs/architecture.md
  - morimail-html/pom.xml
  - morimail-html/src/main/java/de/morisoft/morimail/html/HtmlImages.java
  - morimail-html/src/test/java/de/morisoft/morimail/html/EmbedThroughRecordingTest.java
  - morimail-html/src/test/java/de/morisoft/morimail/html/HtmlImagesDataUriTest.java
  - morimail-html/src/test/java/de/morisoft/morimail/html/HtmlImagesDownloadTest.java
  - morimail-html/src/test/java/de/morisoft/morimail/html/HtmlImagesTest.java
  - morimail-html/src/test/java/de/morisoft/morimail/html/ModuleRulesTest.java
  - pom.xml
covered_digest: "v4:sha256:df7a295d95c92542f14578797d740f00349bdb98d36726fced3547071291b367"
behavior_unverified: 0
overrides_applied: 0
re_verification:
  previous_status: human_needed
  previous_score: 7/7
  gaps_closed: []
  gaps_remaining: []
  regressions: []
human_verification:
  - test: "Push the Phase 7 commits (07-02, 07-03 and the review-fix commits, up to HEAD) and let GitHub Actions run on Temurin 17"
    expected: "All three CI steps are green on Temurin 17: `./mvnw -B -ntp verify`, `./mvnw -B -ntp -Prelease clean verify -Dgpg.skip -pl morimail,morimail-html -am`, and the offline `unshare --net` build. Only commit cce3625 (end of 07-01 Task 2) has run there so far."
    why_human: "Claude never pushes, and only JDK 21 is installed locally. The download code (HttpClient, the new dedicated reader thread, timeout and read-deadline behaviour) and the `data:` code are runtime-sensitive and have not run on JDK 17. The download tests still assert wall-clock bounds (now 2900 and 3000 ms), so a very slow runner could turn them red."
  - test: "Read the owner-review items in the 07-01, 07-02 and 07-03 SUMMARYs (assumptions A1-A7, planner choices P1-P10) and accept or correct them"
    expected: "Owner accepts or corrects, for example the `provided` scope of `morimail` in the helper POM (D-02), the 3 MiB `>=` refusal rule (A2) and HTTP 408 mapped to InvalidMailException. The `image-3.svg-xml` file name for `image/svg+xml` (P10) is now `image-3.svg` after IN-01, so P10 may be accepted as changed."
    why_human: "These are design decisions the plans reserved for the owner. They are not code defects."
---

# Phase 7: HTML Image Helper Module Verification Report

**Phase Goal:** A template author can write ordinary image URLs in an HTML template, and one call to `HtmlImages` turns them into `cid:` references plus inline images that pass the Phase 5 checks.
**Verified:** 2026-11-23
**Status:** human_needed
**Re-verification:** Yes, after the code-review fixes (commits 4e8befa..684a2b4). The earlier report had no `gaps:`, so all truths were re-checked in full, not only spot-checked.

## Goal Achievement

The goal is achieved in the codebase, and the review fixes did not break any must-have. I re-read the diff `8a2df38..HEAD` for `HtmlImages.java`, the four edited test files and `docs/architecture.md`, re-read the changed download section (lines 499-588), and re-ran the build myself. The only open items are two owner-only steps (a Temurin 17 CI run and the owner's review of design choices). Neither is a code gap.

### Observable Truths (ROADMAP success criteria)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | New module `morimail-html` depends on `morimail`, no other runtime dependency, in the release profile, `morimail` jar unchanged | VERIFIED | `pom.xml` lists the module; `morimail-html/pom.xml` has `morimail` as `provided`, the rest as `test`. `git diff f9b2f57 HEAD --stat -- morimail` is empty. Built `morimail-0.2.0-SNAPSHOT.jar` has 0 `html` entries and `morimail/pom.xml` has 0 `morimail-html` matches. My run of `./mvnw -B -ntp -o -Prelease clean verify -Dgpg.skip -pl morimail,morimail-html -am` is BUILD SUCCESS and produced the `morimail-html` jar, sources jar and javadoc jar. `ci.yml` covers both modules. `ModuleRulesTest` (3 tests) pins the no-reference rule. |
| 2 | `embed(template, baseDir)` gives `cid:` references plus one `Attachment` per distinct image; `cid:`, `data:`, unknown schemes and `$$` sources unchanged; subject and text untouched | VERIFIED | `HtmlImagesTest` (19 tests) passes after the fixes, including `unchangedSourcesStayByteForByte`, `onlyImgSrcIsRewritten`, `quotingAndCaseArePreserved`. The fixes did not touch the rewrite or classification code. IN-03 only added a Javadoc sentence. |
| 3 | Local `src` outside the base directory, or a missing, unreadable or non-image file, fails with `InvalidMailException` naming the `src` as written | VERIFIED | `resolveLocal` is unchanged by the fixes. `sourcesOutsideTheBaseDirectoryFail`, `missingUnreadableAndNonImageFilesFail`, `invalidLocalPathsFail` pass. |
| 4 | Content IDs follow the file name without extension, pass the Phase 5 rules, suffix on collision, same source gives one image | VERIFIED | `newId` changed only for an all-dots base (IN-02), which now becomes `image`. That value matches the core pattern. `contentIdsFollowTheFileName`, `collisionsGetANumericSuffixInDocumentOrder`, `sameSourceGivesOneImage`, `cidPatternAgreesWithTheCore` pass. IN-04 widened the `cidPatternAgreesWithTheCore` corpus to `(cid:a)`, `cid:a"b` and letter- or digit-prefixed references, and it still agrees with `Mail.build()`. |
| 5 | `http(s)` unchanged by default; with a download directory, downloaded (WireMock, offline) with timeout, size cap, image content-type check; failures map to the discussed categories | VERIFIED | Download path re-read after WR-01 and WR-02. The `HttpClient` now sits in a private holder class `Http` (line 100) and is touched only in `download` (line 517). The body read runs on a daemon thread named `morimail-html-download` (lines 550-559) that completes a `CompletableFuture`. The wait is still `read.get(left, NANOSECONDS)` and the stream is still closed by the enclosing try-with-resources on every exit, so the one 30 s budget, the `>= 3 MiB` rule, the content-type check and the transient/invalid split are unchanged. `HtmlImagesDownloadTest` (30 tests) passes, including `stalledBodyTimesOut`, `concurrentDownloadsUseSeparateFiles`, `withoutAWorkDirectoryNothingIsDownloaded`, `sizeCapIsThreeMebibytes`, `networkFailuresAreTransient`. |
| 6 | Template converted once, rendered for two records and sent to `recording`, gives two mails that each carry the inline images and pass `build()` | VERIFIED | `EmbedThroughRecordingTest.templateConvertedOnceIsSentToTwoRecipientsWithItsImages` passes. |
| 7 | Storable form: `toDataUris` gives HTML with `data:` images; `embed` with a work directory turns it back into `cid:` plus inline images that pass `build()`; `morimail` POM and jar do not reference `morimail-html` | VERIFIED | `HtmlImagesDataUriTest` (16 tests) passes. The test expectation for the SVG file name was updated to `image-3.svg` (IN-01); `fileName` now cuts the `+suffix` before sanitising. The no-reference rule is shown under truth 1. |

**Score:** 7/7 truths verified (0 present but behavior-unverified)

Behavior-dependent truths (read deadline, interrupt, concurrency) are backed by named passing tests: `stalledBodyTimesOut`, the interrupt test, `concurrentDownloadsUseSeparateFiles`, `concurrentCallsGiveEqualResults`, `concurrentRoundTripsUseSeparateFiles`, `failedCallKeepsEarlierFiles`. They ran on JDK 21 only, which is why the Temurin 17 run stays under Human Verification.

### Review-Fix Regression Check

| Finding | Commit | Effect on must-haves | Result |
|---------|--------|----------------------|--------|
| WR-01 dedicated reader thread | 4e8befa | Truth 5: read deadline and cleanup preserved. The thread ends when `readAtMost` returns or the stream is closed. `Throwable` is passed to the future, so failures still map to `TransientMailException`. | No regression |
| WR-02 lazy `HttpClient` holder | ee7cc69 | Truth 5: local-only calls no longer touch the client, which strengthens "no network by default". | No regression |
| WR-03 looser timing bounds, no sleep or method-name coupling | ada5ade | Test-only. The new bounds (2900 and 3000 ms) are still below the 3.0 s and 3.2 s of the broken variants, so the tests still detect a missing read deadline. | No regression |
| IN-01 `image/svg+xml` gives `.svg` | ddc48fa | Attachment file name only. The content type is still `image/svg+xml` and the content ID is unchanged. | No regression |
| IN-02 `.`/`..`/over-100-character segment falls back to `ID.ext` | 52d9408 | Truth 4: IDs stay valid. Tests for `..` and 101 characters pass. | No regression |
| IN-03 Javadoc limitation | 8b556d6 | Documentation only. | No regression |
| IN-04 wider cid corpus | 3315edf | Test-only, and it passes against the core. | No regression |
| IN-05 `docs/architecture.md` row | 684a2b4 | Documentation only. The row matches the code (3 MiB, 10 s and 30 s, redirect rule, `provided` scope). | No regression |

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `morimail-html/pom.xml` | Module POM, `provided` core, release profile | VERIFIED | Present and in the parent `<modules>`. Unchanged since 07-01. |
| `morimail-html/src/main/java/.../HtmlImages.java` | `embed` (2 forms), `toDataUris` (2 forms), `Result` | VERIFIED | Substantive, no stubs, no debt markers. |
| `EmbedThroughRecordingTest`, `HtmlImagesTest`, `HtmlImagesDownloadTest`, `HtmlImagesDataUriTest`, `ModuleRulesTest` | Tests | VERIFIED | 70 tests (2 + 19 + 30 + 16 + 3), 0 failures, 0 skipped, run by me and by the orchestrator. |
| `.github/workflows/ci.yml` | Scoped release line for both modules | VERIFIED | Unchanged. |
| `pom.xml` (parent) | Module entry | VERIFIED | Unchanged. |
| `docs/architecture.md` | Helper decisions recorded | VERIFIED | New row for §10.4 and §12. |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `HtmlImages` | core `build()` | `Attachment` with `contentId`, `template()` | WIRED | End-to-end tests build and send through `Mailer` and `recording`. |
| `HtmlImages.CID_REFERENCE` | core `Mail.Builder.CID_REFERENCE` | Copy, pinned by behavior test | WIRED | Corpus widened by IN-04. |
| Content-type table | core `CONTENT_TYPES` | Copy, pinned by `localTypeAgreesWithTheCore` | WIRED | Unchanged. |
| `download` | `Http.CLIENT` and the reader thread | Holder class, `CompletableFuture` | WIRED | Used on the single download path. |
| `morimail-html` | `morimail` | `provided` dependency | WIRED | Compiles and runs. No reverse reference. |

### Data-Flow Trace (Level 4)

| Artifact | Data | Source | Real Data | Status |
|----------|------|--------|-----------|--------|
| `HtmlImages.rewrite` | image bytes | local file (real path), WireMock HTTP body, decoded `data:` payload | Yes: tests compare bytes and recorded attachments with the originals | FLOWING |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Helper module and core tests | `./mvnw -B -ntp -o verify -pl morimail-html -am` | BUILD SUCCESS: morimail 366, morimail-html 70, 0 failures | PASS |
| Whole reactor (orchestrator) | `./mvnw -B -ntp -o verify` | BUILD SUCCESS: 366 + 70 + 163 tests | PASS |
| Scoped release build (Javadoc doclint, sources and javadoc jars) | `./mvnw -B -ntp -o -Prelease clean verify -Dgpg.skip -pl morimail,morimail-html -am` | BUILD SUCCESS, three `morimail-html` jars | PASS |
| Core jar and POM have no helper reference | `unzip -l ... | grep -ci html`; `grep -ci morimail-html morimail/pom.xml` | 0 and 0 | PASS |
| Core module untouched in Phase 7 | `git diff --stat f9b2f57 HEAD -- morimail` | empty | PASS |

### Probe Execution

No probes declared by this phase. Step 7c: not applicable.

### Requirements Coverage

All seven IDs appear in the PLAN frontmatter (07-01: HTML-01, 02, 03, 04, 06; 07-02: HTML-05, 04; 07-03: HTML-07, 01, 06) and in REQUIREMENTS.md, where all are marked complete and mapped to Phase 7. None is orphaned.

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| HTML-01 | 07-01, 07-03 | Module `morimail-html`, only `morimail`, in release profile, core jar unchanged | SATISFIED | Truth 1 |
| HTML-02 | 07-01 | `embed` rewrites `<img src>`; `cid:`, `data:`, unknown, `$$`, subject, text unchanged | SATISFIED | Truth 2 |
| HTML-03 | 07-01 | Local sources only under base dir; failures name the `src` | SATISFIED | Truth 3 |
| HTML-04 | 07-01, 07-02 | Content IDs from file name, suffix on collision, one image per source | SATISFIED | Truth 4 |
| HTML-05 | 07-02 | `http(s)` opt-in download with timeout, cap, content-type check, offline tests | SATISFIED | Truth 5 |
| HTML-06 | 07-01, 07-03 | Converted template sent to `recording` for several records | SATISFIED | Truth 6 |
| HTML-07 | 07-03 | `toDataUris` and `data:` decoding in `embed`, same checks and cap | SATISFIED | Truth 7 |

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| (none) | - | No `TBD`, `FIXME`, `XXX`, `TODO` or `HACK` in `morimail-html/` | - | - |

All commits since `f9b2f57` have author and committer `Stephan Wald <stephan@morisoft.de>`. No secrets, tenant IDs or real addresses were found (`example.com` and `example.org` only). Rule 0 holds: no new interface or dependency was added by the fixes. The reader thread is plain JDK code. All three review warnings are now fixed, so the earlier "worth fixing" advice is closed (07-REVIEW-DISPOSITION.md shows 8 of 8 fixed, 0 open).

### Human Verification Required

#### 1. Temurin 17 CI run of the final commits

**Test:** Push the unpushed Phase 7 commits (07-02, 07-03 and the review fixes up to HEAD) and let GitHub Actions run.
**Expected:** `verify`, the scoped release build, and the offline `unshare --net` build are all green on Temurin 17.
**Why human:** Claude never pushes, and only JDK 21 is installed locally. Only `cce3625` has run on Temurin 17. The download code (with the new reader thread) and the `data:` code came later, and the download tests keep wall-clock bounds.

#### 2. Owner review of assumptions and choices

**Test:** Review A1-A7 and P1-P10 in the three SUMMARYs.
**Expected:** Accept or correct them, for example `provided` scope, the `>= 3 MiB` rule, HTTP 408 as `InvalidMailException`. P10 (SVG file name) changed with IN-01 to `image-3.svg`.
**Why human:** Owner-reserved design decisions.

### Gaps Summary

No gaps. All seven success criteria and all seven requirement IDs are backed by code I read and by tests I ran green after the review fixes. The status is `human_needed` and not `passed` because the final commits have not run on Temurin 17, which only the owner can trigger, and because the SUMMARYs list owner-review items. These match the items already stored in 07-UAT.md, which still apply.

---

_Verified: 2026-11-23_
_Verifier: Claude (gsd-verifier)_
