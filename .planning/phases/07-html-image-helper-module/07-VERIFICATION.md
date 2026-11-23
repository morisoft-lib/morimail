---
phase: 07-html-image-helper-module
verified: 2026-11-23T15:10:00Z
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
  - morimail-html/pom.xml
  - morimail-html/src/main/java/de/morisoft/morimail/html/HtmlImages.java
  - morimail-html/src/test/java/de/morisoft/morimail/html/EmbedThroughRecordingTest.java
  - morimail-html/src/test/java/de/morisoft/morimail/html/HtmlImagesDataUriTest.java
  - morimail-html/src/test/java/de/morisoft/morimail/html/HtmlImagesDownloadTest.java
  - morimail-html/src/test/java/de/morisoft/morimail/html/HtmlImagesTest.java
  - morimail-html/src/test/java/de/morisoft/morimail/html/ModuleRulesTest.java
  - pom.xml
covered_digest: "v4:sha256:f7df2de4927b9ea2796277698f518731bfbb8151352c4d9b38f0934c1a515d9f"
behavior_unverified: 0
overrides_applied: 0
human_verification:
  - test: "Push the Phase 7 commits (07-02 and 07-03, up to HEAD) and let GitHub Actions run on Temurin 17"
    expected: "All three CI steps are green on Temurin 17: `./mvnw -B -ntp verify`, `./mvnw -B -ntp -Prelease clean verify -Dgpg.skip -pl morimail,morimail-html -am`, and the offline `unshare --net` build. Only commit cce3625 (end of 07-01 Task 2) has run there so far."
    why_human: "Claude never pushes, and only JDK 21 is installed locally. The 07-02 download code (HttpClient, timeout and read-deadline behaviour) and the 07-03 data: code are runtime-sensitive and have not run on JDK 17. The download tests also assert wall-clock bounds (see WR-03), so a slow runner could turn them red."
  - test: "Read the owner-review items in the 07-01, 07-02 and 07-03 SUMMARYs (assumptions A1-A7, planner choices P1-P10) and accept or correct them"
    expected: "Owner accepts or corrects, for example the `provided` scope of `morimail` in the helper POM (D-02), the 3 MiB `>=` refusal rule (A2), HTTP 408 mapped to InvalidMailException, and the `image-3.svg-xml` file name for `image/svg+xml` (P10)"
    why_human: "These are design decisions the plans reserved for the owner. They are not code defects."
---

# Phase 7: HTML Image Helper Module Verification Report

**Phase Goal:** A template author can write ordinary image URLs in an HTML template, and one call to `HtmlImages` turns them into `cid:` references plus inline images that pass the Phase 5 checks.
**Verified:** 2026-11-23
**Status:** human_needed
**Re-verification:** No, initial verification

## Goal Achievement

The goal is achieved in the codebase. I read `HtmlImages.java` (674 lines) in full and ran the build myself. The only open items are two owner-only steps (a Temurin 17 CI run of the final commits and the owner's review of design choices). Neither is a code gap.

### Observable Truths (ROADMAP success criteria)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | New module `morimail-html` depends on `morimail`, adds no other runtime dependency, is in the release profile, and the `morimail` jar is unchanged | VERIFIED | `pom.xml:43` lists the module. `morimail-html/pom.xml` declares `morimail` as `provided` and everything else as `test`; `dependency:tree` shows nothing else in a compile or runtime scope. `git diff --stat f9b2f57 HEAD -- morimail` is empty (no change to the core during Phase 7). The built `morimail-0.2.0-SNAPSHOT.jar` and its embedded POM contain no `html` entry and no `morimail-html` string. `./mvnw -o -Prelease clean verify -Dgpg.skip -pl morimail,morimail-html -am` is BUILD SUCCESS and produced `morimail-html` jar, sources jar and javadoc jar. CI line 20 covers both modules. `ModuleRulesTest.morimailDoesNotReferenceTheHelper` pins the "no reference" rule. |
| 2 | `embed(template, baseDir)` returns `cid:` references plus one `Attachment` per distinct image; `cid:`, `data:`, unknown schemes and `$$` sources stay unchanged; subject and text untouched | VERIFIED | `HtmlImages.rewrite` (lines 320-416): `$$` skipped (344), scheme classification (347-354), result built with the original subject and text (415), same instance when nothing changes (411-413). Tests `unchangedSourcesStayByteForByte`, `quotingAndCaseArePreserved`, `onlyImgSrcIsRewritten`, `nullHtmlAndNothingToRewriteReturnTheSameTemplate`, `dataSourcesStayUnlessAWorkDirectoryIsGiven` pass. |
| 3 | A local `src` outside the base directory, or a missing, unreadable or non-image file, fails with `InvalidMailException` naming the `src` as written | VERIFIED | `resolveLocal` (586-635) uses `toRealPath` after `normalize` and checks `startsWith(base)`, which also catches symlink escape. The `invalid(raw, ...)` helper puts the raw `src` in every message. Tests `sourcesOutsideTheBaseDirectoryFail`, `missingUnreadableAndNonImageFilesFail`, `invalidLocalPathsFail` pass. |
| 4 | Content IDs follow the file name without extension, pass the Phase 5 rules, get a numeric suffix on collision; the same source used twice yields one image | VERIFIED | `newId` (652-664) strips the extension, sanitises to `[A-Za-z0-9._-]`, falls back to `image`, suffixes `-2`, `-3`. The `taken` set is seeded from existing `cid:` references in the HTML (326-332), so a collision with an author-written `cid:` is also handled. Dedupe key is the real path (390-391). Tests `contentIdsFollowTheFileName`, `collisionsGetANumericSuffixInDocumentOrder`, `sameSourceGivesOneImage`, `localTypeAgreesWithTheCore`, `cidPatternAgreesWithTheCore` pass, and the end-to-end test builds through the core `build()`, which applies the Phase 5 rules. |
| 5 | `http(s)` stays unchanged by default; with a download directory they are downloaded (WireMock, offline) with a timeout, a size cap and an image content-type check; failures map to the discussed categories | VERIFIED | `remote = workDir != null && http(s)` (350), so the two-argument `embed` and `toDataUris` never touch the network. `download` (495-564): 10 s connect timeout, one 30 s budget for headers plus body (read on a future bounded by the remaining time), `>= 3 MiB` refused, `Content-Type` must match `image/...`. Network errors, timeouts, interrupts, 5xx and 429 raise `TransientMailException`; other statuses and bad content raise `InvalidMailException`. 30 tests in `HtmlImagesDownloadTest` pass against WireMock, including `withoutAWorkDirectoryNothingIsDownloaded`, `contentTypeMustBeAnImage`, `sizeCapIsThreeMebibytes`, `networkFailuresAreTransient`, `redirectIsFollowed`, `sameUrlIsDownloadedOnce`. |
| 6 | A template converted once, rendered for two data records and sent to `recording`, gives two mails that each carry the inline images and pass `build()` | VERIFIED | `EmbedThroughRecordingTest.templateConvertedOnceIsSentToTwoRecipientsWithItsImages` calls `Mail...build()` and `Mailer.send` to `recording` twice and asserts both recorded HTMLs and that both messages carry exactly `result.images()`. It passes. |
| 7 | Storable form: `toDataUris` gives HTML with `data:` images needing no files; `embed` with a work directory turns it back into `cid:` plus inline images that pass `build()`; the `morimail` POM and jar do not reference `morimail-html` | VERIFIED | `dataUri` (430-432) and `decodeData` (435-464, strict decoder, length pre-check, 3 MiB cap, no payload in messages). `HtmlImagesDataUriTest.storedHtmlRoundTripsToTwoMailsWithTheOriginalBytes` converts, then embeds from a base directory that does not exist, sends two mails through `build()`/`recording`, and compares the decoded bytes with the originals. The no-reference rule is confirmed above (truth 1). |

**Score:** 7/7 truths verified (0 present but behavior-unverified)

Behavior-dependent truths (timeouts, read deadline, concurrency) are backed by named passing tests: `stalledBodyTimesOut`, `concurrentDownloadsUseSeparateFiles`, `concurrentCallsGiveEqualResults`, `concurrentRoundTripsUseSeparateFiles`, `failedCallKeepsEarlierFiles`. They ran on JDK 21 only. The JDK 17 run is in Human Verification.

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `morimail-html/pom.xml` | Module POM, `provided` core, release profile | VERIFIED | Present, substantive, in the parent's `<modules>`. `Automatic-Module-Name` set. Its release profile skips only test phases, as documented for the unattached test-jar. |
| `morimail-html/src/main/java/.../HtmlImages.java` | `embed` (2 forms), `toDataUris` (2 forms), `Result` | VERIFIED | 674 lines, no stubs, no debt markers. |
| `EmbedThroughRecordingTest`, `HtmlImagesTest`, `HtmlImagesDownloadTest`, `HtmlImagesDataUriTest`, `ModuleRulesTest` | Tests | VERIFIED | 70 tests run: 2 + 19 + 30 + 16 + 3, 0 failures, 0 skipped. |
| `.github/workflows/ci.yml` | Scoped release line for both modules | VERIFIED | Line 20 `-pl morimail,morimail-html -am`. |
| `pom.xml` (parent) | Module entry and release-build comment | VERIFIED | Line 43. |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `HtmlImages` | `Mail.Builder` / core `build()` checks | `Attachment` with `contentId`, `template()` | WIRED | End-to-end tests build and send through `Mailer` and `recording`. |
| `HtmlImages.CID_REFERENCE` | core `Mail.Builder.CID_REFERENCE` | Copy of the pattern | WIRED | `cidPatternAgreesWithTheCore` compares behavior (see IN-04 for the remaining coupling). |
| Content-type table | core `CONTENT_TYPES` | Copy | WIRED | `localTypeAgreesWithTheCore` pins it against `build()`. |
| `morimail-html` | `morimail` | `provided` dependency | WIRED | Compiles and runs against the core. No reverse reference. |

### Data-Flow Trace (Level 4)

| Artifact | Data | Source | Real Data | Status |
|----------|------|--------|-----------|--------|
| `HtmlImages.rewrite` | image bytes / file | local file (real path), WireMock HTTP body, decoded `data:` payload | Yes: tests compare bytes and recorded attachments with the originals | FLOWING |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Whole reactor offline | `./mvnw -B -ntp -o verify` | BUILD SUCCESS: morimail 366 tests, morimail-html 70, sample 163, 0 failures | PASS |
| Scoped release build (Javadoc doclint, sources, javadoc jars) | `./mvnw -B -ntp -o -Prelease clean verify -Dgpg.skip -pl morimail,morimail-html -am` | BUILD SUCCESS, three `morimail-html` jars produced | PASS |
| Core jar has no helper reference | `unzip -l` / embedded POM grep | 0 matches | PASS |
| `morimail-html` runtime dependencies | `dependency:tree -pl morimail-html` | Only `morimail` (provided) plus test-scope libraries | PASS |

### Probe Execution

No probes declared by this phase. Step 7c: not applicable.

### Requirements Coverage

All seven IDs from the roadmap appear in the PLAN frontmatter and in REQUIREMENTS.md. None is orphaned.

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| HTML-01 | 07-01, 07-03 | Module `morimail-html`, only `morimail`, in release profile, core jar unchanged | SATISFIED | See truth 1 |
| HTML-02 | 07-01 | `embed` rewrites `<img src>`; `cid:`, `data:`, unknown, `$$`, subject, text unchanged | SATISFIED | See truth 2 |
| HTML-03 | 07-01 | Local sources only under base dir; failures name the `src` | SATISFIED | See truth 3 |
| HTML-04 | 07-01, 07-02 | Content IDs from file name, suffix on collision, one image per source | SATISFIED | See truth 4; remote and local IDs share one namespace (`remoteAndLocalIdsShareOneNamespace`) |
| HTML-05 | 07-02 | `http(s)` opt-in download with timeout, cap, content-type check, offline tests | SATISFIED | See truth 5 |
| HTML-06 | 07-01, 07-03 | Converted template sent to `recording` for several records | SATISFIED | See truth 6 |
| HTML-07 | 07-03 | `toDataUris` and `data:` decoding in `embed` with the same checks and cap | SATISFIED | See truth 7 |

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| (none) | - | No `TBD`/`FIXME`/`XXX`/`TODO`/`HACK` in `morimail-html/` or the changed POM/CI files | - | - |

Authorship: every commit since `f9b2f57` is by `Stephan Wald <stephan@morisoft.de>`. No secrets or real addresses seen (`example.com` / `example.org` only).

### Code Review Findings (07-REVIEW.md, all still `open` in the disposition file)

None breaks a must-have. 0 critical, 3 warnings, 5 info. My assessment of the three warnings:

- **WR-01** (body read on `ForkJoinPool.commonPool()`): the read can queue behind other pool work and report a false "download timed out" under load. It is a robustness risk for a library that promises concurrent use. It does not break truth 5 as tested. Worth fixing before 0.2.0 or recording as a known limit.
- **WR-02** (static `HttpClient` created at class load): the local-only methods can fail at class init in a restricted JVM. The Javadoc says only the work-directory `embed` touches the network. Low likelihood, easy fix with a holder class.
- **WR-03** (wall-clock assertions such as `elapsed < 2700` ms in the download tests): flake risk on a slow runner, which matters directly for the pending Temurin 17 run.

The info items (IN-01 to IN-05) are cosmetic or documentation: the `image-3.svg-xml` file name, unchecked remote file-name segments, `<img alt="x"src="y">` not matched, a third copy of the `cid:` pattern, and no entry for the helper's decisions in `docs/architecture.md`. Phase 8 owns the docs.

### Human Verification Required

#### 1. Temurin 17 CI run of the final commits

**Test:** Push the unpushed 07-02 and 07-03 commits and let GitHub Actions run.
**Expected:** `verify`, the scoped release build, and the offline `unshare --net` build are all green on Temurin 17.
**Why human:** Claude never pushes. Only `cce3625` has run on Temurin 17 (run 45194793703). The download and `data:` code came later, and the download tests have timing assertions (WR-03).

#### 2. Owner review of assumptions and choices

**Test:** Review A1-A7 and P1-P10 in the three SUMMARYs.
**Expected:** Accept or correct them (for example `provided` scope, the `>= 3 MiB` rule, HTTP 408 as `InvalidMailException`, the SVG file name).
**Why human:** Owner-reserved design decisions.

### Gaps Summary

No gaps. All seven success criteria and all seven requirement IDs are backed by code I read and by tests that I ran green (full offline verify and the scoped release build, both on JDK 21). The status is `human_needed` and not `passed` because the final two plans have not run on Temurin 17, which only the owner can trigger, and because the SUMMARYs list owner-review items. The three open review warnings should be triaged, but they do not block the phase goal.

---

_Verified: 2026-11-23_
_Verifier: Claude (gsd-verifier)_
