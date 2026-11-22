---
phase: 02-serial-mail-sample
verified: 2026-11-22T07:10:00Z
status: passed
score: 5/5 must-haves verified
covered_files:
  - .planning/phases/02-serial-mail-sample/02-01-PLAN.md
  - .planning/phases/02-serial-mail-sample/02-01-SUMMARY.md
  - .planning/phases/02-serial-mail-sample/02-02-PLAN.md
  - .planning/phases/02-serial-mail-sample/02-02-SUMMARY.md
  - .planning/phases/02-serial-mail-sample/02-03-PLAN.md
  - .planning/phases/02-serial-mail-sample/02-03-SUMMARY.md
  - .planning/phases/02-serial-mail-sample/02-04-PLAN.md
  - .planning/phases/02-serial-mail-sample/02-04-SUMMARY.md
  - morimail-sample-serial/example-campaign/recipients.csv
  - morimail-sample-serial/pom.xml
  - morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/AccountFile.java
  - morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/Campaign.java
  - morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/Journal.java
  - morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/SendCommand.java
  - morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/SerialMail.java
covered_digest: "v4:sha256:0c9e5fb34ae25a2e46493811a78cfc0eca8b3808eb22a755974171aa3820cbda"
behavior_unverified: 0
overrides_applied: 0
re_verification:
  previous_status: human_needed
  previous_score: 5/5
  gaps_closed:
    - "Human check (example PDF/PNG and rendered pages in real viewers): satisfied by 02-UAT.md (status complete, test 1 pass, 2026-11-22)"
  gaps_remaining: []
  regressions: []
---

# Phase 2: Serial-Mail Sample Verification Report

**Phase Goal:** As a campaign sender, I want to dry-run and send a CSV serial mail with a journal, so that a rerun never mails a recipient twice.
**Verified:** 2026-11-22T07:10:00Z
**Status:** passed
**Re-verification:** Yes. The previous report (human_needed) went stale after the code-review fixes (commits 3e71c50 to 3981cf8). I re-read the changed code and re-ran the build and the jar. I did not rely on SUMMARY.md or REVIEW-FIX.md.

## Result

All five roadmap success criteria still hold after the review fixes. None of the nine fixes (WR-01 to WR-03, IN-01 to IN-06) broke a must-have. The one human item from the previous report is closed by 02-UAT.md (test 1: pass), so no human item remains.

## Goal Achievement

### Observable Truths (roadmap Success Criteria)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | `send --dry-run` on the example campaign writes 5 rendered mails (`.html`, `.txt`, `.subject.txt`, sanitised names), lists the attachments, needs no secret env vars, and touches neither the network nor the journal. | VERIFIED | Ran the built jar under `env -i` on a copy of the shipped campaign: exit 0, 15 output files, line `attachments: info.pdf (application/pdf, 602 bytes), logo.png (image/png, 60959 bytes)`, five `[i/5] <address> rendered` lines, and no `journal.jsonl` in the campaign directory. In `SendCommand.call` the dry-run branch returns before any `Journal` use (the journal is opened only under `!dryRun`) and `mailerFactory` is only called under `!dryRun`. `DryRunTest` (7 tests) passes. |
| 2 | A broken campaign is rejected before the first send, all problems reported together, exit 2 (placeholder without column, invalid email, `;` header, first row failing `mailer.validate`). | VERIFIED | Ran the jar on a campaign with a `;`-delimited header: 9 problems listed at once (the `;` header, no `email` column, 7 placeholder-without-column problems, one per placeholder per file), then `Pre-flight failed with 9 problem(s); nothing was sent.` and exit 2. The real (non-dry-run) run of the shipped campaign exits 2 with one account problem and sends nothing. The post-WR-03 grouping code (`invalidMail`, lines 105-119, and the per-row loop, lines 157-177) still collects every problem before the single exit-2 return, and the first-row `mailer.validate` branch (lines 187-194) is intact. `PreflightTest` (10 tests, including the two new WR-03 tests) passes. |
| 3 | Against `recording`, each row is sent once with one progress line and one forced journal line per attempt; a rerun sends nothing and counts every row as skipped. | VERIFIED | `Journal.append` forces the channel after each line. `send` checks `journal.isAccepted(key)` before any attempt, increments `skipped` and `continue`s. `SendRunTest` (6) and `JournalTest` (18) pass against the real `RecordingTransport` attempt count, including the rerun-sends-nothing case and "a failed journal write stops the run". The WR/IN fixes did not touch `Journal.java`. |
| 4 | A transient failure waits `retryAfter` (capped) or 30 s and retries up to 3 times, then is recorded and the run continues. Sender-rejected or auth-required stops at once with exit 3. The run ends with counts and exits 0 only if nothing failed. | VERIFIED | `DEFAULT_RETRY_WAIT` 30 s, `MAX_RETRY_WAIT` 5 min, `MAX_ATTEMPTS` 4. The `SenderRejectedException \| AuthRequiredException` branch journals, prints counts and returns 3. Other `MailException` records and continues. Return is `exit != 0 \|\| failed > 0 ? 1 : 0`. `FailureHandlingTest` (15, including the new IN-02 and IN-03 tests) passes. The IN-02 `RuntimeException` branch only adds a stop with counts and exit 1 for an otherwise unhandled error, and does not alter the four specified paths. |
| 5 | Sends are spaced by `--rate`, skipped rows don't wait. `--limit`, `--campaign-id`, `--journal` behave as specified. Account and campaign files are UTF-8, secrets come from env vars only, `./mvnw verify` is green offline. | VERIFIED | `spacing(rate)` is `ceil(60000/rate)`. The sleep sits inside the attempt loop after the skip `continue`, gated by `sentAny`. `PacingAndOptionsTest` (22) passes. `AccountFile.read` now uses `Files.readString` (strict UTF-8, malformed input still reported) and strips one leading BOM (WR-01); `AccountFileTest` (22) passes. `Campaign` skips dot-files in `attachments/` (WR-02, line 85) and writes the BOM as `the \u + FEFF escape` (IN-05); `CampaignTest` (23) passes. `./mvnw -B -ntp -o verify` run by me: BUILD SUCCESS, library module and sample module green, sample 128 tests, 0 failures, 0 errors, 0 skipped. |

**Score:** 5/5 truths verified (0 present but behavior-unverified)

All state-transition truths (journal append, skip on rerun, stop on exit 3, retry count) have passing behavioral tests through the CLI-12 seam; none rests on presence alone.

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `SerialMail.java` (62 lines) | picocli entry, CLI-12 seam | VERIFIED | `commandLine(factory, sleep)` is the seam; `realMailer` fails clearly until Phase 3 (CLI-14). Javadoc now lists exit code 3 (IN-01). |
| `SendCommand.java` (361 lines) | send, dry-run, retry, stop, rate, limit | VERIFIED | Substantive, all seven options present, wired to `Journal`, `Campaign`, `AccountFile`, `Mailer`. |
| `Campaign.java` (299 lines) | hand-written RFC 4180 reader, template, attachments | VERIFIED | No CSV library imported. `Campaign.PLACEHOLDER` is reused by tests (IN-04). |
| `Journal.java` (131 lines) | forced append-only JSONL | VERIFIED | Unchanged by the fixes. |
| `AccountFile.java` (98 lines) | UTF-8 properties, one credential form | VERIFIED | BOM handling added; problems name keys, never values. |
| `example-campaign/*` | 3 placeholders, PDF and PNG, 5 `example.org` rows | VERIFIED | Dry-run output confirms; PDF 602 bytes, PNG 60,959 bytes (both under 100 KB). Viewer check passed in UAT. |
| `pom.xml` (sample) | runnable jar with `lib/` classpath, now declares `jackson-databind` | VERIFIED | Jar ran on a clean environment; the version comes from the parent BOM and the build is green offline. |
| 9 sample test classes | CLI-12 coverage | VERIFIED | 128 tests pass. |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `SerialMail.main` | `SendCommand` | `commandLine(realMailer, sleep)` | WIRED | Confirmed by running the jar. |
| `SendCommand` | `Journal` | `open`, `isAccepted`, `append` | WIRED | |
| `SendCommand` | `Campaign`, `AccountFile` | `read(..., problems)` | WIRED | One `problems` list feeds exit 2. |
| `SendCommand` | `Mailer.validate`, `Mailer.send` | first-row validate, per-row send | WIRED | Tested against `RecordingTransport` from the test-jar. |
| `Campaign` | core `TemplateRenderer` | placeholder names | WIRED | `extractedNamesSatisfyTheCoreRenderer` guards pattern drift (IN-04). |
| sample `pom.xml` | `SerialMail` | jar manifest main class, `lib/` prefix | WIRED | Jar runs. |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Real Data | Status |
|----------|---------------|--------|-----------|--------|
| dry-run files | `message.html()`, `.subject()`, `.text()` | `Mail.using(...).template(campaign.template(), row.fields())` from the CSV | Yes, visible in the generated files | FLOWING |
| skip decision | accepted-key set | `Journal.open` parses the existing file | Yes (`rerunOfACompleteCampaignSendsNothing`) | FLOWING |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Full build offline | `./mvnw -B -ntp -o verify` | BUILD SUCCESS, 128 sample tests, 0 failures | PASS |
| Dry-run, no env | `env -i java -jar ... send --dry-run` | exit 0, 15 files, attachments listed, no journal | PASS |
| Broken campaign | jar with a `;` header | 9 problems together, exit 2 | PASS |
| Real run before Phase 3 | jar without `--dry-run` | one `account:` problem, exit 2, nothing sent | PASS (expected, CLI-14 is Phase 3) |

### Probe Execution

Step 7c: SKIPPED. No probes are declared in the plans and there is no `scripts/*/tests/probe-*.sh`.

### Requirements Coverage

The PLAN frontmatter requirement IDs are CLI-01 to CLI-07, CLI-10 to CLI-12 (02-01); CLI-02, CLI-04, CLI-05, CLI-12 (02-02); CLI-01, CLI-06 to CLI-10 (02-03); CLI-02, CLI-11, CLI-13 (02-04). The union is exactly CLI-01 to CLI-13, the 13 IDs REQUIREMENTS.md maps to Phase 2 (all marked complete). CLI-14 maps to Phase 3. No ID is orphaned or missing.

| Requirement | Status | Evidence |
|-------------|--------|----------|
| CLI-01 | SATISFIED | `SendCommand` options; campaign id defaults to the directory name |
| CLI-02 | SATISFIED | `AccountFile`; UTF-8 and BOM handled (WR-01), env-var names only |
| CLI-03 | SATISFIED | `Campaign.read`; hidden files no longer attached (WR-02, narrowing noted in REVIEW-FIX) |
| CLI-04 | SATISFIED | Hand-written reader, `;` header error (jar run) |
| CLI-05 | SATISFIED | Jar run, `PreflightTest`; identical defects reported once (WR-03) |
| CLI-06 | SATISFIED | `Journal`, `JournalTest` |
| CLI-07 | SATISFIED | `PacingAndOptionsTest` |
| CLI-08 | SATISFIED | `FailureHandlingTest` |
| CLI-09 | SATISFIED | `FailureHandlingTest` (exit 3, two exception types) |
| CLI-10 | SATISFIED | `SendRunTest`; exit-code help text (IN-01) |
| CLI-11 | SATISFIED | Jar run under `env -i`; `DryRunTest` |
| CLI-12 | SATISFIED | All send tests run through the `Mailer` seam against `recording` |
| CLI-13 | SATISFIED | Example campaign verified; viewer check passed in UAT |

### Anti-Patterns Found

A grep for `TBD`, `FIXME`, `XXX`, `TODO`, `HACK` over the sample sources and example campaign found nothing, so no debt-marker blocker applies. No raw U+FEFF remains in the changed sources (written as `the \u + FEFF escape`). The only stub-like code, `SerialMail.realMailer`, is the documented Phase 3 hook (CLI-14).

### Human Verification Required

None. The previous report's one item (rendered pages, PDF and PNG in real viewers) is recorded as passed in `02-UAT.md`. I treat it as satisfied.

### Warnings (not goal blockers)

1. **Workstation path in a phase artifact.** `02-REVIEW-FIX.md` lines 4 and 15 contain an absolute workstation path (the review path). Plans 02-01 and 02-04 set a repo hygiene gate of no absolute workstation path under the phase directory, and the project is public. The file was added by the review-fix step after those plans ran, so it is not a plan deliverable, but it should be changed to a repo-relative path before this is pushed. The same pattern exists in `.planning/phases/01-library-core/01-REVIEW-FIX.md` (lines 4 and 15), which is outside this phase. This report itself uses repo-relative paths only.
2. **Journal has no exclusive lock (IN-06, partial).** Two concurrent runs on one campaign could each send the pending rows. The reviewer called this a robustness gap, and the fixer deliberately did not add `tryLock` under Rule 0. The goal ("a rerun never mails a recipient twice") holds for sequential reruns, which is what the phase guarantees. The `jackson-databind` declaration part of IN-06 is applied.
3. **WR-02 narrows the spec wording.** Dot-files are skipped, while spec section 8 says every file in `attachments/` is attached. `Thumbs.db` and `desktop.ini` are still attached. This is recorded in REVIEW-FIX; the dry-run lists attachments so an owner sees them.
4. **Real sends are not available until Phase 3.** `SerialMail.realMailer` throws a clear message and the run exits 2. This is as scoped (CLI-14).

Commit identity check: every commit after the initial commit is authored and committed as the maintainer identity (the two earlier mis-attributed commits no longer appear).

### Gaps Summary

No gaps. The phase goal is achieved: a campaign sender can dry-run a CSV campaign and send it with a forced journal, and a rerun skips every accepted row. The review fixes changed pre-flight reporting, attachment selection, BOM handling, error text and one POM dependency, and none of them alters a roadmap success criterion; all are covered by passing tests.

---

_Verified: 2026-11-22T07:10:00Z_
_Verifier: Claude (gsd-verifier)_
