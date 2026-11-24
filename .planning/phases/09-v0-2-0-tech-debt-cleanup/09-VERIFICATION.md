---
phase: 09-v0-2-0-tech-debt-cleanup
verified: 2026-11-24T06:20:00Z
status: passed
score: 17/17 must-haves verified
covered_files:
  - .planning/phases/09-v0-2-0-tech-debt-cleanup/09-01-PLAN.md
  - .planning/phases/09-v0-2-0-tech-debt-cleanup/09-01-SUMMARY.md
  - .planning/phases/09-v0-2-0-tech-debt-cleanup/09-02-PLAN.md
  - .planning/phases/09-v0-2-0-tech-debt-cleanup/09-02-SUMMARY.md
  - .planning/phases/09-v0-2-0-tech-debt-cleanup/09-03-PLAN.md
  - .planning/phases/09-v0-2-0-tech-debt-cleanup/09-03-SUMMARY.md
  - docs/architecture.md
  - docs/spec.md
  - morimail-html/src/main/java/de/morisoft/morimail/html/HtmlImages.java
  - morimail-html/src/test/java/de/morisoft/morimail/html/HtmlImagesTest.java
  - morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/Campaign.java
  - morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/SendCommand.java
  - morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/CampaignTest.java
  - morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/PreflightTest.java
  - morimail/src/main/java/de/morisoft/morimail/Mail.java
  - morimail/src/test/java/de/morisoft/morimail/MailBuilderTest.java
covered_digest: "v4:sha256:70eb377c24c6cb5c8466eb2cf6053472aec9da04283c3c490043b7e0ff3da9f5"
behavior_unverified: 0
overrides_applied: 0
---

# Phase 9: v0.2.0 tech debt cleanup Verification Report

**Phase Goal:** Close the open tech debt found by the v0.2.0 milestone audit so the milestone can be archived with no known loose ends: Phase 5 WR-01, WR-02, IN-01, IN-03; Phase 6 pre-flight checks only the first mail; Phase 7 IN-01, IN-02, the 07-01-SUMMARY.md frontmatter and "provided scope" text; Phase 8 08-02-SUMMARY.md "0.2.0 not on Central".
**Verified:** 2026-11-24
**Status:** passed (with one open advisory warning, see "Warnings")
**Re-verification:** No, initial verification

## Goal Achievement

The phase has no ROADMAP success-criteria array and no requirement IDs. The contract is CONTEXT decisions D-01..D-17 plus the `must_haves` of the three plans. Every one was checked against the code and the planning files, not against the SUMMARYs.

### Observable Truths

| #  | Truth | Status | Evidence |
|----|-------|--------|----------|
| 1  | D-04/D-05: `<` ends a `cid:` id; whole-HTML scan kept; three pattern copies identical | VERIFIED | `Mail.java:51`, `Campaign.java:49`, `HtmlImages.java:114` all hold `(?i)(?<![\p{L}\p{N}])cid:([^"'\s)<>]*)`; `sort | uniq -c` of the three lines gives one distinct pattern. Both `cidPatternAgreesWithTheCore` corpora contain `<p>cid:logo</p>` (`CampaignTest:440`, `HtmlImagesTest:285`); `MailBuilderTest:457` has it as a build case. |
| 2  | D-01/D-03: echoed id cleaned of ISO control, LINE_SEPARATOR, PARAGRAPH_SEPARATOR, FORMAT, cut at 64 code points | VERIFIED | `Mail.Builder.shownId` (`Mail.java:546-561`) works on code points, replaces with `?`, appends `...` only if over 64. `controlCharactersInAReferenceAreNotEchoed` goes through a template placeholder (recipient-data path) and asserts exactly `no inline image for cid:a?b?c?d?e` with ESC, NEL, U+2028, U+202E; `longReferenceIsCutAtWholeCodePoints` covers supplementary characters. Tests ran green. |
| 3  | D-02: only HTML-read ids cleaned; sample and helper do not echo an uncleaned recipient-derived id | VERIFIED | `checkReferences` is the only echo of an id read from rendered HTML; `Campaign.checkInline` names ids from the unrendered `body.html` (a campaign file); `HtmlImages` uses its pattern only to fill a `taken` set. |
| 4  | D-06: unquoted `src=cid:logo/>` still reads `logo/`; documented | VERIFIED | `unquotedSrcBeforeASlashKeepsTheSlashInTheId` pins both outcomes; the architecture row states it and says quoting avoids it. |
| 5  | D-07: planning decision IDs gone from pattern comments; architecture §10.3 row rewritten | VERIFIED | `grep -rnE '\bD-[0-9]+' morimail/src/main` finds nothing. `docs/architecture.md:40` names `<` as terminator, the `/` note, the `ref=cid:123` trade-off, the cleaning and the case note. |
| 6  | D-08: webp, bmp, svg in both content-type tables; attach Javadoc lists fifteen extensions | VERIFIED | `Mail.java:59-61` and `HtmlImages.java:140-142` hold `image/webp`, `image/bmp`, `image/svg+xml`; `Mail.java:283` says "fifteen extensions (pdf, png, jpg, jpeg, gif, webp, bmp, svg, ...)". `resolvesTheAddedImageTypesOnEveryPlatform` and the helper's `localTypeAgreesWithTheCore` pin the types. |
| 7  | D-09: ids stay case-sensitive; Javadoc and architecture note it | VERIFIED | No behaviour change in the pattern or comparison; architecture row says `Logo` and `logo` are two images, the 2026-11-23 live test did not cover it, careful authors avoid it. 05 IN-03 recorded deferred. |
| 8  | D-10: pre-flight calls `mailer.validate` on every non-null built mail; problems grouped by message through `invalidMail`, reported after build problems | VERIFIED | `SendCommand.java` loop over `mails` with null skip, `mailer.validate(mails.get(i))`, `LinkedHashMap` keyed by `"mailer.validate rejected the mail: " + message`, each entry through `invalidMail`. `InvalidMailException extends PermanentMailException`, so the catch covers Graph's `InvalidMailException` from `validate`. Grouping is by identical message, as D-10 words it; see WARNING below for the body-size case. |
| 9  | D-11: later failing row stops pre-flight, exit 2, names that row's line, nothing sent | VERIFIED | `laterRowThatGraphRejectsStopsPreflight`: first two rows pass, row at line 4 has a 4.2 million character field; asserts exit 2, empty stdout, two exact stderr lines, token calls 0, no journal file. Ran green. |
| 10 | D-10: a limit every row shares is one line `(all N rows)`; recording-transport test updated | VERIFIED | `limitProblemOfEveryRowIsOneLine` (3 MiB shared attachment, `(all 3 rows)`); `brokenCampaignReportsEveryProblemTogether` expects `...too large for the test (recipients.csv lines 2, 4)`, count 4. Tests use `baseUrl http://localhost:1/v1.0` and assert zero token calls (no real host contacted). |
| 11 | D-12: spec says pre-flight validates every row's mail | VERIFIED | `docs/spec.md:321` "`mailer.validate` passes for every row's mail". No remaining "first mail" validate wording in spec; README line 169 refers to "before the first mail is sent" and is correct. |
| 12 | D-13: `strip()` kept; class Javadoc states white space around a `src` is ignored; code comment no longer claims the HTML rule | VERIFIED | `HtmlImages.java:50` "White space before and after a ..." ; comment at 353-354 says Unicode white space, wider than ASCII white space HTML strips. Code line unchanged. (See IN-02 of the review on the comment's precision.) |
| 13 | D-14: no seam added for the Error rethrow; closure recorded | VERIFIED | No new package-private path or test added in `HtmlImages`; 07 disposition records IN-02 `skipped` with reason. |
| 14 | D-15: 07-01-SUMMARY.md parses; stale "provided scope" text corrected | VERIFIED | `gsd-tools query summary-extract` on the file returns JSON with a one-liner that says "compile scope since 07-04 (provided-scope only at 07-01)". Every `provided` line also carries the 07-04 note. |
| 15 | D-16: 08-02-SUMMARY.md keeps the historical statement and marks it superseded by the v0.2.0 release | VERIFIED | Lines 10 and 104 contain "superseded" and "tag v0.2.0"; file parses with `summary-extract`. |
| 16 | D-17: 05 and 07 dispositions settled | VERIFIED | 05: WR-01, WR-02, IN-01, IN-02 fixed, IN-03 deferred, `open: 0`, `total: 5`. 07: IN-01 fixed, IN-02 skipped, others fixed, `open: 0`, `total: 8`. |
| 17 | Gates: offline verify and scoped release build green; maintainer identity; trailers; nothing outside scope changed | VERIFIED | I re-ran `./mvnw -B -ntp -o verify`: BUILD SUCCESS, 166 sample tests, 0 failures (core and helper SUCCESS). `./mvnw -B -ntp -Prelease verify -Dgpg.skip -pl morimail,morimail-html -am`: BUILD SUCCESS, zero WARNING lines. All 15 commits since 58ad024 are authored and committed as Stephan Wald <stephan@morisoft.de>; all six source/doc-touching commits carry `Co-Authored-By`. Files changed outside `.planning` are exactly the ten planned files. None contains raw invisible Unicode characters (control, NEL, zero-width, U+2028..U+202E, bidi isolates, BOM). |

**Score:** 17/17 truths verified (0 behavior-unverified; each behavior-dependent truth has a passing named test).

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `morimail/.../Mail.java` | `<` terminator, `shownId`, three types, Javadoc | VERIFIED | Present, substantive, wired (`checkReferences` calls `shownId`; `contentTypeOf` uses table) |
| `morimail/.../MailBuilderTest.java` | D-03, D-05, D-06, D-08 tests | VERIFIED | `controlCharactersInAReferenceAreNotEchoed` etc. exist and pass |
| `morimail-sample-serial/.../Campaign.java` | pattern copy equal to core | VERIFIED | Line 49 identical |
| `morimail-sample-serial/.../SendCommand.java` | validate every mail | VERIFIED | Loop present and wired to `invalidMail` and the problems list |
| `morimail-html/.../HtmlImages.java` | pattern copy, seven image rows, Javadoc sentence | VERIFIED | Lines 114, 140-142, 50 |
| `docs/architecture.md`, `docs/spec.md` | row 10.3, 8.3 step 2 | VERIFIED | Lines 40 and 321 |

### Key Link Verification

| From | To | Via | Status |
|------|----|-----|--------|
| `Mail.CID_REFERENCE` | `Campaign.CID_REFERENCE`, `HtmlImages.CID_REFERENCE` | byte-equal pattern, pinned by both `cidPatternAgreesWithTheCore` tests that build each corpus entry through `Mail.build()` | WIRED |
| `SendCommand` pre-flight | `Mailer.validate` -> `GraphTransport.validate` | per-row call, `PermanentMailException` caught, grouped, reported before journal open | WIRED |
| `Mail.java` comments | `ModuleRulesTest.morimailDoesNotReferenceTheHelper` | core comment names `HtmlImages.CID_REFERENCE in the HTML image helper` without artifact id or package | WIRED (test green) |

### Data-Flow Trace (Level 4)

| Artifact | Data | Source | Real Data | Status |
|----------|------|--------|-----------|--------|
| `shownId` | id read from rendered HTML | `CID_REFERENCE` group(1) over the rendered body, including template-substituted recipient fields | Yes (test drives it through a placeholder) | FLOWING |
| Pre-flight `rejected` map | `mailer.validate` exception messages | `GraphTransport.validate` (`Files.size`, body JSON length) | Yes | FLOWING |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Whole reactor offline | `./mvnw -B -ntp -o verify` | BUILD SUCCESS, sample 166 tests, no failures | PASS |
| Release build, scoped | `./mvnw -B -ntp -Prelease verify -Dgpg.skip -pl morimail,morimail-html -am` | BUILD SUCCESS, no WARNING lines | PASS |
| `summary-extract` on 07-01 and 08-02 | gsd-tools query | parses, one-liners correct | PASS |

### Probe Execution

SKIPPED: the phase declares no probes and `scripts/*/tests/probe-*.sh` does not apply.

### Requirements Coverage

No requirement IDs exist for this phase. All three PLAN files declare `requirements: []`, and `.planning/REQUIREMENTS.md` has no row mapped to Phase 9. Nothing claims an ID and nothing is orphaned.

### Anti-Patterns Found

No TBD, FIXME or XXX markers added in the changed source files; no stub returns or hardcoded empty data in the new code; no decision-ID comment in `morimail/src/main`.

### Deferred Items

Out of scope per ROADMAP and CONTEXT, not gaps: the `cid:` pattern-copy equality note (covered by `cidPatternAgreesWithTheCore`), pushing main / Temurin 17 CI, and tracking or deleting the untracked `07-PATTERNS.md` and `state.json`. The working tree still shows those untracked, plus `.planning/v0.2.0-MILESTONE-AUDIT.md` and the `.planning/config.json` change; none is a Phase 9 deliverable.

### Human Verification Required

None. (Temurin 17 CI on the phase commits is an owner action the SUMMARYs list as pending; it is not a Phase 9 must-have.)

## Warnings

These do not fail a must-have, but the owner should triage them before archiving, because the goal says "no known loose ends". `09-REVIEW-DISPOSITION.md` records all three as `open`.

1. **WR-01 (review): pre-flight grouping does not collapse the 4 MiB request-size rejection.** I confirmed this against the code. `SendCommand` keys `rejected` on the full message. `GraphTransport.validate` builds the request-size message as `"estimated request size " + estimate + " bytes ..."`, and the estimate includes each row's rendered body, so it differs from row to row. Attachment-limit messages are identical across rows and do group (tested). A large shared attachment close to the limit with varying template fields can therefore print one line per row. D-10 and its truths say "group identical messages", and that holds literally, but D-10's stated purpose ("a limit problem shared by 500 rows is one problem line") is only met for the attachment limits. Behaviour stays safe: the run still stops with exit 2 and every offending row is named. It is a noise problem, not a correctness problem. Fix options are in 09-REVIEW.md (key on the message with digit runs masked, or cap the output).
2. **IN-01 (review): "whitespace" in the `build()` Javadoc and the architecture row** means ASCII white space only (`\s` without UNICODE_CHARACTER_CLASS). Documentation precision only.
3. **IN-02 (review): the new `strip()` comment** in `HtmlImages.java:353-354` is loose about how the Unicode and HTML white-space sets differ. Comment precision only.

## Gaps Summary

No gaps. Every D-01..D-17 decision is implemented in the code or recorded in the planning files, with passing tests for the behavioural ones (cleaned echo, `<` terminator, content types, pre-flight validation of a later row and of a shared limit). The offline build and the scoped release build are green when I run them myself, and the work is committed under the maintainer identity with nothing pushed. The only residue is the open review warning WR-01, which narrows how well D-10 reduces noise for the body-size limit.

---

_Verified: 2026-11-24T06:20:00Z_
_Verifier: Claude (gsd-verifier)_
