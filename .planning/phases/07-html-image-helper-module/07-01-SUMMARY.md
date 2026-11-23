---
phase: 07-html-image-helper-module
plan: 01
subsystem: html-image-helper
tags: [maven-module, morimail-html, inline-images, cid, java17, recording-transport]

requires:
  - phase: 05-inline-images-core-graph
    provides: build() content-ID and image-type checks (Mail.Builder.CID_REFERENCE, CONTENT_TYPES, Attachment.contentId)
  - phase: 06-sample-inline-images
    provides: the Temurin 17 mid-phase CI round-trip pattern and the pinned cid: pattern copy technique
provides:
  - morimail-html module (de.morisoft:morimail-html, package de.morisoft.morimail.html) with morimail in provided scope
  - HtmlImages.embed(MailTemplate, Path) and HtmlImages.Result for local images
  - scoped release build and CI release line covering morimail and morimail-html
affects: [07-02 downloads, 07-03 data URIs and ModuleRulesTest, 08 docs and release readiness]

actuals:
  tokens: 10500
  tasks: 3
  commits: 2
plan_head_before: 2814e82eb211a2e0caa036783b2bbc3f9c4ad636
plan_head_after: cce3625037329c14931d243bc9a082b7ac332fd1
commits: 2

tech-stack:
  added: []
  patterns:
    - "Copy of the core cid: pattern, pinned by behaviour in HtmlImagesTest (no reflection)"
    - "Module-level release profile that unbinds the three test executions so the scoped release build never resolves the unattached test-jar"
    - "Offset splice over a possessive-quantifier regex instead of the Matcher replacement API"

key-files:
  created:
    - morimail-html/pom.xml
    - morimail-html/src/main/java/de/morisoft/morimail/html/HtmlImages.java
    - morimail-html/src/test/java/de/morisoft/morimail/html/EmbedThroughRecordingTest.java
    - morimail-html/src/test/java/de/morisoft/morimail/html/HtmlImagesTest.java
  modified:
    - pom.xml
    - .github/workflows/ci.yml
    - docs/architecture.md

key-decisions:
  - "A1: the scheme pattern needs one or more characters, so C:\\img\\logo.png has the unknown scheme c and stays unchanged (D-09 outcome)"
  - "A5: <img src=\"\"> is a local src that resolves to the base directory and fails with is not a readable file"
  - "A7: only the scoped release line -pl morimail,morimail-html -am is supported; the helper's release build runs none of its tests"
  - "P1: an image type must match image/[a-z0-9][a-z0-9.+-]* in full"
  - "P2: ID base, attachment file name and table lookup use the name as written; the real path is the dedupe key; the first written name wins"

patterns-established:
  - "Local source resolution: decode via java.net.URI, normalize, toRealPath, element-wise startsWith against the real base directory"
  - "Failure message form: img src \"SRC\": REASON with SRC exactly as written in the template"

requirements-completed: [HTML-01, HTML-02, HTML-03, HTML-04, HTML-06]

coverage:
  - id: D1
    description: "morimail-html module in the reactor and the scoped release build, morimail in provided scope, morimail jar and tree unchanged"
    requirement: HTML-01
    verification:
      - kind: other
        ref: "./mvnw -B -ntp -Prelease clean verify -Dgpg.skip -pl morimail,morimail-html -am (JDK 21) and dependency-tree gate"
        status: pass
      - kind: other
        ref: "GitHub Actions run 45194793703 on cce3625 (Temurin 17: verify, scoped release build, offline build)"
        status: pass
    human_judgment: false
  - id: D2
    description: "HtmlImages.embed rewrites local img src values to cid: references with inline Attachments and leaves cid:, data:, remote, unknown-scheme and $$ sources, subject and text unchanged"
    requirement: HTML-02
    verification:
      - kind: unit
        ref: "morimail-html/src/test/java/de/morisoft/morimail/html/HtmlImagesTest.java#unchangedSourcesStayByteForByte"
        status: pass
      - kind: unit
        ref: "HtmlImagesTest#quotingAndCaseArePreserved, onlyImgSrcIsRewritten, ampersandIsTheOnlyDecodedReference, percentEncodingQueryAndFragment"
        status: pass
    human_judgment: false
  - id: D3
    description: "Local sources outside the base directory, missing, unreadable, non-image and invalid fail with InvalidMailException naming the src as written"
    requirement: HTML-03
    verification:
      - kind: unit
        ref: "HtmlImagesTest#sourcesOutsideTheBaseDirectoryFail, missingUnreadableAndNonImageFilesFail, invalidLocalPathsFail, missingBaseDirectoryFailsOnlyForLocalSources"
        status: pass
    human_judgment: false
  - id: D4
    description: "Content IDs come from the file name, pass the core rules, get numeric suffixes in document order, and one source yields one image"
    requirement: HTML-04
    verification:
      - kind: unit
        ref: "HtmlImagesTest#contentIdsFollowTheFileName, collisionsGetANumericSuffixInDocumentOrder, sameSourceGivesOneImage, attachmentCarriesNameTypeAndRealPath"
        status: pass
    human_judgment: false
  - id: D5
    description: "A template converted once and sent for two records to recording passes build() and carries both inline images in every mail"
    requirement: HTML-06
    verification:
      - kind: integration
        ref: "morimail-html/src/test/java/de/morisoft/morimail/html/EmbedThroughRecordingTest.java#templateConvertedOnceIsSentToTwoRecipientsWithItsImages"
        status: pass
    human_judgment: false
  - id: D6
    description: "Hostile markup (20,000 unclosed img tags, 200,000 attributes, 8 MB data: src) and eight concurrent callers are handled within the test timeout with equal results"
    verification:
      - kind: unit
        ref: "HtmlImagesTest#hostileMarkupStaysFast, concurrentCallsGiveEqualResults"
        status: pass
    human_judgment: false

duration: 12 min
completed: 2026-11-23
status: complete
---

# Phase 7 Plan 01: The morimail-html module and local images end to end Summary

**New `morimail-html` module whose `HtmlImages.embed(template, baseDir)` turns local `<img src>` values into `cid:` references plus core-valid inline images, with `morimail` provided-scope only, a scoped release build and CI line for both published modules, and a green Temurin 17 round-trip.**

## Performance

- **Duration:** 12 min
- **Started:** 2026-11-23T14:22:15Z (orchestrator phase start)
- **Completed:** 2026-11-23T14:34:13Z
- **Tasks:** 3 (2 code tasks, 1 owner checkpoint)
- **Files modified:** 7 (4 created, 3 changed)

## Accomplishments

- `de.morisoft:morimail-html` exists in the reactor with `morimail` as its only non-test dependency (provided), `Automatic-Module-Name: de.morisoft.morimail.html`, and a module release profile that unbinds the three test executions so `./mvnw -Prelease clean verify -Dgpg.skip -pl morimail,morimail-html -am` builds main, sources and Javadoc jars (doclint all, failOnWarnings).
- `HtmlImages.embed` rewrites only the `src` of `<img>` elements in the HTML part, keeps quoting and case, leaves `cid:`, `data:`, `http(s):`, unknown schemes and `$$` values unchanged, and fails with `img src "SRC": REASON` for local sources that are outside the base directory (traversal, percent-encoded, absolute, `file:`, symlink, `base-evil` prefix), missing, unreadable, non-image or invalid.
- Content IDs follow the file name, pass `build()`, get numeric suffixes in document order against hand-written `cid:` IDs, and one real path gives one image; the converted template sent for two recipients to `recording` passes `build()` in both mails (HTML-06).
- 19 rule tests plus 2 end-to-end tests pin all of it, including the hostile-markup bound (T-07-04) and eight-thread equality.
- `morimail` is untouched: `git diff --name-only f9b2f57 -- morimail/` is empty and its dependency tree names no helper.

## Task Commits

1. **Task 1: Tracer, module POM, reactor and CI wiring, HtmlImages.embed for local files** - `623616e` (feat)
2. **Task 2: Pin the local-source and content-ID rules plus the section 10.1 doc row** - `cce3625` (test)
3. **Task 3: Owner pushes and confirms Temurin 17 CI** - checkpoint, no commit (owner reply "green")

**Plan metadata:** recorded in the `docs(07-01)` commit that adds this file.

## Files Created/Modified

- `pom.xml` - module entry after `morimail`; release comment names the scoped line
- `morimail-html/pom.xml` - provided `morimail`, test-only dependencies, `Automatic-Module-Name`, release profile with three unbound test executions
- `.github/workflows/ci.yml` - the release step builds `-pl morimail,morimail-html -am`
- `morimail-html/src/main/java/de/morisoft/morimail/html/HtmlImages.java` - `HtmlImages`, `HtmlImages.Result`, `embed(MailTemplate, Path)`
- `morimail-html/src/test/java/de/morisoft/morimail/html/EmbedThroughRecordingTest.java` - HTML-06 end-to-end through `Mailer` and `recording`
- `morimail-html/src/test/java/de/morisoft/morimail/html/HtmlImagesTest.java` - the 19 rule tests
- `docs/architecture.md` - section 10.1 row names the new release line and the unbound test executions

## Decisions Made

- A1, A5, A7 and P1 to P4 (see "Owner review") were applied as the plan flagged them; none was changed during execution.
- No source change was needed after the tests were written (see "Owner review").

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None. All JDK 21 gates passed on the first run (full verify, scoped release build, dependency-tree gate, `morimail/` guard empty), and the Temurin 17 CI run was green without a fix commit.

## Owner review

These are the flagged assumptions and planner choices this plan applied. Reply only where one is wrong.

- A1: scheme pattern needs one or more characters, so `C:\img\logo.png` is an unknown scheme `c` and stays unchanged (D-09 outcome; its "two or more characters" reason would instead fail it as a local path).
- A5: `<img src="">` counts as a local src, resolves to the base directory itself and fails with `img src "": is not a readable file`.
- A7: only the scoped release line `./mvnw -B -ntp -Prelease clean verify -Dgpg.skip -pl morimail,morimail-html -am` is supported; a full-reactor release build stays unsupported as in v0.1.0, and the helper's release build runs none of its tests (the plain verify in the same CI job runs them).
- P1: an image type must match `(?i)image/[a-z0-9][a-z0-9.+-]*` in full, so remote and `data:` types in Plans 07-02 and 07-03 can never carry a quote, angle bracket or `$` into the HTML or an `Attachment`.
- P2 (owner, 2026-11-23): for a local file the ID base, the attachment file name and the type lookup use the name as the template author wrote it; the real path is the dedupe key and the attachment's file; the first written name wins when two names resolve to one file.
- P3: the public parameters are named `template`, `baseDir` and `workDir` (D-04 writes `t`); class, method and record names are as in D-04.
- P4: no `package-info.java` (the core has none and doclint passes without it, Rule 0).
- Task 2 needed no source fix: all 19 `HtmlImagesTest` methods passed on the first run against the Task 1 `HtmlImages.java`, so `cce3625` holds only the test file and the `docs/architecture.md` row.

Temurin 17 CI (mid-phase, 07-01 Task 3): green on cce3625 (verify, scoped release build, offline build)

## Task 3 verification record

- Owner pushed main (`4ea76d8..cce3625`) and replied "green".
- `git merge-base --is-ancestor cce3625 origin/main` exits 0.
- `gh run list --workflow ci.yml --commit "$(git rev-parse origin/main)"` reports `completed success` (run 45194793703, head `cce3625037329c14931d243bc9a082b7ac332fd1`).
- Claude pushed nothing.

## User Setup Required

None - no external service configuration required.

## Known Stubs

None. No placeholder values flow to any output; HTTP(S) and `data:` sources are deliberately left unchanged here and are the scope of Plans 07-02 and 07-03.

## Threat Flags

None. The only new surface (local file access) is the plan's own T-07-01, mitigated and covered by `sourcesOutsideTheBaseDirectoryFail`.

## Next Phase Readiness

- Plan 07-02 may start: its precondition, the Temurin 17 line above, is recorded.
- The helper is not yet wired for downloads (07-02) or `data:` URIs and `ModuleRulesTest` (07-03); `http(s)` and `data:` sources stay unchanged until then.

---
*Phase: 07-html-image-helper-module*
*Completed: 2026-11-23*

## Self-Check: PASSED

- FOUND: morimail-html/pom.xml, HtmlImages.java, EmbedThroughRecordingTest.java, HtmlImagesTest.java
- FOUND: commits 623616e and cce3625 (ancestors of HEAD and of origin/main)
- `git diff --name-only f9b2f57 -- morimail/` prints nothing
