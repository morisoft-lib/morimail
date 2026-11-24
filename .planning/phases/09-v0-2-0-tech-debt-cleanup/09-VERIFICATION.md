---
phase: 09-v0-2-0-tech-debt-cleanup
verified: 2026-11-24T09:25:00Z
status: passed
score: 17/17 must-haves verified
covered_files:
  - .planning/phases/09-v0-2-0-tech-debt-cleanup/09-01-PLAN.md
  - .planning/phases/09-v0-2-0-tech-debt-cleanup/09-01-SUMMARY.md
  - .planning/phases/09-v0-2-0-tech-debt-cleanup/09-02-PLAN.md
  - .planning/phases/09-v0-2-0-tech-debt-cleanup/09-02-SUMMARY.md
  - .planning/phases/09-v0-2-0-tech-debt-cleanup/09-03-PLAN.md
  - .planning/phases/09-v0-2-0-tech-debt-cleanup/09-03-SUMMARY.md
  - README.md
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
covered_digest: "v4:sha256:74b95dd41a1e7f35925b7df64a8acacbef9fe6c2f6617ccba0d98048ad1bc852"
behavior_unverified: 0
overrides_applied: 0
re_verification:
  previous_status: passed
  previous_score: 17/17
  gaps_closed: []
  gaps_remaining: []
  regressions: []
---

# Phase 9: v0.2.0 tech debt cleanup Verification Report

**Phase Goal:** Close the open tech debt found by the v0.2.0 milestone audit so the milestone can be archived with no known loose ends: Phase 5 WR-01, WR-02, IN-01, IN-03; Phase 6 pre-flight checks only the first mail; Phase 7 IN-01, IN-02, the 07-01-SUMMARY.md frontmatter and "provided scope" text; Phase 8 08-02-SUMMARY.md "0.2.0 not on Central".
**Verified:** 2026-11-24
**Status:** passed (one open code-quality warning, WR-01 of the Phase 9 review, see "Warnings")
**Re-verification:** Yes. The previous report (06:20Z) went stale because quick tasks 261124-8os and 261124-9og changed `HtmlImages.java`, `HtmlImagesTest.java`, `CampaignTest.java`, `Mail.java` (Javadoc), `docs/architecture.md` and `README.md` afterwards. Every must-have was re-checked against the HEAD tree (5021f01), not against the SUMMARYs. No gap had been open, so no gap was reopened, and none of the later changes undoes a Phase 9 must-have.

## Goal Achievement

The phase has no requirement IDs (`requirements: []` in all three plans, `**Requirements**: TBD` in ROADMAP, nothing in REQUIREMENTS.md mapped to Phase 9), so there is nothing to cross-reference or orphan. The contract is the ROADMAP goal bullets plus CONTEXT D-01..D-17 and the `must_haves` of the three plans.

### Observable Truths

| #  | Truth | Status | Evidence |
|----|-------|--------|----------|
| 1  | D-04/D-05: `<` ends a `cid:` id; whole-HTML scan kept; the three pattern copies are identical (Phase 5 WR-02) | VERIFIED | `Mail.java:50-51`, `Campaign.java:48-49` and `HtmlImages.java:121-122` all hold `(?i)(?<![\p{L}\p{N}])cid:([^"'\s)<>]*)`; `sort | uniq -c` over the pattern lines gives one distinct pattern. The agreement corpora in `CampaignTest` and `HtmlImagesTest` build each entry through `Mail.build()`; both ran green. |
| 2  | D-01/D-03: the echoed id is cleaned of ISO control, LINE_SEPARATOR, PARAGRAPH_SEPARATOR and FORMAT characters and cut at 64 code points (Phase 5 WR-01) | VERIFIED | `Mail.Builder.shownId` (`Mail.java:546-561`) works on code points, writes `?` and appends `...` only past 64. The only use is `Mail.java:533`. `MailBuilderTest.controlCharactersInAReferenceAreNotEchoed` exists (line 545) and passed in the full `verify`. |
| 3  | D-02: only ids read from rendered HTML are cleaned; the sample and the helper do not echo a recipient-derived id | VERIFIED | `shownId` is used once, on the HTML-read id. The helper uses its pattern only to fill the `taken` set (`HtmlImages.java` around 344-350). |
| 4  | D-06: an unquoted `src=cid:logo/>` reads `logo/`; documented | VERIFIED | `docs/architecture.md:40` says "an unquoted `src=cid:logo/>` reads the ID `logo/`; quoting the value avoids this". The pattern keeps `/` as an id character. |
| 5  | D-07: planning decision IDs gone from `morimail/src/main`; the architecture row names `<`, the `/` note and the `ref=cid:123` trade-off | VERIFIED | `grep -rnE '\bD-[0-9]+' morimail/src/main` finds nothing. The architecture row (line 40) contains all three, after the quick-task edits. |
| 6  | D-08: webp, bmp, svg resolve in both content-type tables; `attach` Javadoc lists fifteen extensions (Phase 5 IN-01) | VERIFIED | `Mail.java:59-61` and `HtmlImages.java:148-150` hold `image/webp`, `image/bmp`, `image/svg+xml`; `Mail.java:283` says "fifteen extensions (pdf, png, jpg, jpeg, gif, webp, bmp, svg, ...)". |
| 7  | D-09: ids stay case-sensitive in the core and the case note is documented (Phase 5 IN-03) | VERIFIED | `Mail.java:341` "Content ids are compared exactly, so Logo and logo are two ..."; architecture row says "IDs stay case-sensitive as released in 0.2.0; the owner's live test of 2026-11-23 did not cover IDs that differ only in case". Commit 271f262 made only the helper's own generated ids case-insensitive, which is stricter than the core and does not change the core comparison (see IN-01 of the review). |
| 8  | D-10: pre-flight calls `mailer.validate` on every non-null built mail; problems grouped by message through `invalidMail`, after the build problems (Phase 6) | VERIFIED | `SendCommand.java:208-225`: loop over all `mails`, null skipped, `LinkedHashMap` keyed `"mailer.validate rejected the mail: " + message`, each entry through `invalidMail(..., campaign.rows().size())`. The old first-mail-only block is gone. Grouping is by identical message, as D-10 words it; see WR-01 under Warnings. |
| 9  | D-11: a later failing row stops pre-flight, exit 2, nothing sent | VERIFIED | `PreflightTest.laterRowThatGraphRejectsStopsPreflight` (line 80) ran green in the full `verify`. |
| 10 | D-10: a limit every row shares is one line `(all N rows)`; recording-transport test updated | VERIFIED | `PreflightTest:61` expects `...too large for the test (recipients.csv lines 2, 4)`; `:105` `limitProblemOfEveryRowIsOneLine` expects `(all 3 rows)` at `:123`. Green. |
| 11 | D-12: spec says pre-flight validates every row's mail | VERIFIED | `docs/spec.md:321` "`mailer.validate` passes for every row's mail". `git diff 8ee52be..HEAD -- docs/spec.md` shows this one line only. |
| 12 | D-13: `strip()` kept; class Javadoc says white space around a `src` is ignored; the comment no longer claims the HTML rule (Phase 7 IN-01) | VERIFIED | `HtmlImages.java:50` "White space before and after a `src` value is ignored." The comment above `strip()` now says "removes Unicode white space (Character.isWhitespace), which is not the same set as the C0 controls and space that browsers strip from a URL". Note: the plan's artifact marker string "wider set than the ASCII white space" no longer occurs in the file (reworded by quick task 8os/9og); the must-have's intent, a comment that does not claim the HTML rule, holds and is arguably more precise. Not a gap. |
| 13 | D-14: no seam for the Error-rethrow branch; closure recorded (Phase 7 IN-02) | VERIFIED | No new package-private path in `HtmlImages`; `07-REVIEW-DISPOSITION.md:48` records IN-02 `skipped` with "closed in Phase 9, accepted without test, no seam per Rule 0". |
| 14 | D-15: `07-01-SUMMARY.md` parses; stale "provided scope" text corrected | VERIFIED | `gsd-tools query summary-extract` returns JSON with a one-liner naming "compile scope since 07-04 (provided-scope only at 07-01)". All five `provided` lines (13, 59, 119, 131, 148) carry the 07-04 note. |
| 15 | D-16: `08-02-SUMMARY.md` marks the "0.2.0 not on Central" statement superseded | VERIFIED | Lines 10 and 104 contain "superseded" and "tag v0.2.0"; the file parses with `summary-extract`. |
| 16 | D-17: Phase 5 and 7 dispositions settled | VERIFIED | `05-REVIEW-DISPOSITION.md`: WR-01, WR-02, IN-01, IN-02 fixed, IN-03 deferred, `open: 0`, `total: 5`. `07-REVIEW-DISPOSITION.md`: IN-01 fixed, IN-02 skipped, `open: 0`. |
| 17 | Gates and hygiene: offline `verify` and scoped release build green; maintainer identity; scope of changes | VERIFIED | I ran `./mvnw -B -ntp -o verify`: BUILD SUCCESS, 166 sample tests, 0 failures, all four reactor modules SUCCESS. I ran `./mvnw -B -ntp -o -Prelease verify -Dgpg.skip -pl morimail,morimail-html -am`: BUILD SUCCESS, zero WARNING lines (grep over the output). All 29 commits since 8ee52be (plans, quick tasks, reviews) are authored and committed as `Stephan Wald <stephan@morisoft.de>`. Non-`.planning` files changed since 8ee52be are the ten planned files plus `README.md` and `docs/architecture.md` wording from the quick tasks; no file outside the phase's concern changed. |

**Score:** 17/17 truths verified (0 behavior-unverified; each behavior-dependent truth has a passing named test: control-character echo, the `<` agreement cases, later-row pre-flight stop, grouped every-row limit).

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `morimail/.../Mail.java` | `<` terminator, `shownId`, three image types, Javadoc | VERIFIED | Substantive and wired (`checkReferences` calls `shownId`; the content-type table feeds `contentTypeOf`) |
| `morimail/.../MailBuilderTest.java` | D-03, D-05, D-06, D-08 tests | VERIFIED | `controlCharactersInAReferenceAreNotEchoed` present and green |
| `morimail-sample-serial/.../Campaign.java` | pattern copy equal to core | VERIFIED | Line 49 identical |
| `morimail-sample-serial/.../SendCommand.java` | validate every mail | VERIFIED | Contains the loop with `mailer.validate(mails.get(i))` |
| `morimail-sample-serial/.../PreflightTest.java` | later-row, shared-limit, updated expectation | VERIFIED | Lines 61, 80, 105 |
| `morimail-html/.../HtmlImages.java` | pattern copy, seven image rows, Javadoc sentence | VERIFIED | Lines 121-122, 148-150, 50 (plan marker string `wider set than the ASCII white space` reworded, intent kept, see truth 12) |
| `docs/architecture.md`, `docs/spec.md` | §10.3 row, §8.3 step 2 | VERIFIED | Lines 40 and 321 |

### Key Link Verification

| From | To | Via | Status |
|------|----|-----|--------|
| `Mail.CID_REFERENCE` | `Campaign.CID_REFERENCE`, `HtmlImages.CID_REFERENCE` | byte-equal pattern, pinned by both `cidPatternAgreesWithTheCore` tests | WIRED |
| `SendCommand` pre-flight | `Mailer.validate` -> `GraphTransport.validate` | per-row call, `PermanentMailException` caught, grouped, reported before the journal opens | WIRED |
| `Mail.java` comments | `ModuleRulesTest.morimailDoesNotReferenceTheHelper` | comment wording avoids the helper's artifact id and package | WIRED (test green in `verify`) |
| 05/07 dispositions | code | rows recorded fixed only where `shownId`, the `<` terminator, the types and the Javadoc exist | WIRED |

### Data-Flow Trace (Level 4)

Not applicable in the dynamic-data sense. The one data path of note is rendered HTML (recipient data) -> `CID_REFERENCE` -> `shownId` -> exception message; the chain ends in a real cleaned value, not a static one.

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Whole offline build with all unit tests | `./mvnw -B -ntp -o verify` | BUILD SUCCESS, 166 sample tests 0 failures | PASS |
| Scoped release build (Javadoc strict) | `./mvnw -B -ntp -o -Prelease verify -Dgpg.skip -pl morimail,morimail-html -am` | BUILD SUCCESS, 0 WARNING | PASS |
| Three pattern copies equal | `grep` and `sort | uniq -c` | one distinct pattern | PASS |
| Summaries parse | `gsd-tools query summary-extract` on 07-01 and 08-02 | JSON with one-liner | PASS |

### Probe Execution

SKIPPED: the phase declares no probes (`scripts/*/tests/probe-*.sh` is not referenced in any plan or summary).

### Requirements Coverage

No requirement IDs are declared by any plan (`requirements: []`) and REQUIREMENTS.md maps none to Phase 9. By design, nothing to account for; no orphaned requirements.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| (changed phase files) | - | `TBD`, `FIXME`, `XXX` markers | none found | - |
| `SendCommand.java` | 217-224 | validate grouping key embeds the per-row size estimate | Warning (review WR-01) | See Warnings; no must-have broken |

### Human Verification Required

None open. `09-UAT.md` is complete with 5 of 5 passed (cid id ends at `<`, svg inline, control characters shown as `?`, later oversize row stops pre-flight, every-row limit grouped as one line). `09-SECURITY.md` has `threats_open: 0`. The Temurin 17 CI run on 9e3fab0 passed (reported by the orchestrator; not re-checked here, no non-`.planning` file has changed since).

### Warnings

**WR-01 (from `09-REVIEW.md`, still open, advisory).** The 4 MiB request-size rejection message carries the estimated size (`GraphTransport.java:127`, "estimated request size N bytes ..."). Pre-flight groups `mailer.validate` rejections by exact message, so rows whose bodies differ in size give different keys and each prints its own line. I confirmed this in the code. It does not break D-10 (D-10 asks for grouping "by message like the build problems", and the shared-attachment case groups), D-11 (a later bad row stops pre-flight, exit 2, nothing sent, tested), or the goal (a later larger row no longer fails at send time; all problems are still reported before any send). It is a noise-level defect for a large campaign with a per-recipient oversize body. Disposition is still `open` in `09-REVIEW-DISPOSITION.md`, like the three info findings (IN-01 helper case rule missing from the architecture §10.4 row, IN-02 dry-run overwrite on case-insensitive file systems, IN-03 "whitespace" comment wording). None of these four is in the ROADMAP goal list. **Recommended before archiving v0.2.0:** triage the four rows (fix, defer or skip with a reason) so the milestone really carries no untriaged item.

**Stale plan marker.** The 09-03 artifact check expects the string "wider set than the ASCII white space" in `HtmlImages.java`; later quick-task edits reworded that comment. Behavior and intent are unchanged, so this is informational only.

### Gaps Summary

No gaps. Every ROADMAP goal item is closed in the HEAD tree: Phase 5 WR-01 (control-character cleaning of the echoed id), WR-02 (the `<` terminator and the documented trade-off), IN-01 (webp, bmp, svg in both tables), IN-03 (case-sensitivity documented, deferred with reason); Phase 6 (pre-flight validates every row); Phase 7 IN-01 (Javadoc and comment), IN-02 (closed without test, recorded), 07-01-SUMMARY parse and scope text; Phase 8 08-02-SUMMARY superseded note. The later quick-task changes extend, and do not undo, any of these.

---

_Verified: 2026-11-24T09:25:00Z_
_Verifier: Claude (gsd-verifier)_
