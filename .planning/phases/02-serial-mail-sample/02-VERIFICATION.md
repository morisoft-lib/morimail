---
phase: 02-serial-mail-sample
verified: 2026-11-21T18:30:00Z
status: human_needed
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
covered_digest: "v4:sha256:eb8df6bd90ad9f4c6750cda5951f44870d515ceff4b5eb81aba451a815a26b4d"
behavior_unverified: 0
overrides_applied: 0
human_verification:
  - test: "Run the jar dry-run with --out in a new mktemp -d directory outside the repo, open the five .html files in a browser, and open example-campaign/attachments/info.pdf in a real PDF viewer and logo.png in an image viewer."
    expected: "Each page shows the greeting with the recipient's name, city and topic (clara's topic reads 'Tips, tricks & shortcuts' as an ampersand). The PDF opens as one page with the text 'morimail example attachment'. The PNG shows a colour gradient. Neither viewer shows an author, creator or producer."
    why_human: "No PDF tool is installed here. The structural checks (xref offsets, no Info dictionary, no text chunks) cannot prove a real viewer accepts the file, and rendering is visual. Harvested from the 02-04 PLAN human-check block."
---

# Phase 2: Serial-Mail Sample Verification Report

**Phase Goal:** As a campaign sender, I want to dry-run and send a CSV serial mail with a journal, so that a rerun never mails a recipient twice.
**Verified:** 2026-11-21T18:30:00Z
**Status:** human_needed
**Re-verification:** No, initial verification

All five roadmap success criteria hold in the code, and I confirmed this myself. I did not rely on SUMMARY.md. I read the 910 lines of production code, ran `./mvnw -B -ntp -o verify` (121 tests, 0 failures, BUILD SUCCESS), and ran the built jar against the shipped example campaign and against deliberately broken campaigns. One human check remains, the PDF viewer check. Two process warnings and the open code-review items are listed below. None of them is a goal blocker.

## Goal Achievement

### Observable Truths (roadmap Success Criteria)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | `send --dry-run` on the example campaign writes 5 rendered mails (`.html`, `.txt`, `.subject.txt`, sanitised names) and lists attachments. It needs no secret env vars and touches neither the network nor the journal. The campaign has 3 placeholders, a PDF and a PNG under 100 KB each, and 5 `example.org` rows. | VERIFIED | I ran `env -i java -jar target/morimail-sample-serial-0.1.0-SNAPSHOT.jar send --dry-run` on a copy of the campaign. Exit 0 and 15 files `01-anna@example.org.{html,txt,subject.txt}` through `05-emma@...`. Printed `attachments: info.pdf (application/pdf, 602 bytes), logo.png (image/png, 60959 bytes)`. No `journal.jsonl` was created. HTML escapes `&amp;`. The CSV has 5 rows of `@example.org` and the placeholders are `firstName`, `city` and `topic`. Pre-flight sits before the `dryRun` branch and `Journal.open` is skipped when `dryRun` is set. `mailerFactory` is not called when dry-run is set, so no env var is read. |
| 2 | A broken campaign is rejected before the first send, with all problems reported together and exit 2. The problems include a placeholder with no CSV column, an invalid email, a `;`-delimited header, and a first row that fails `mailer.validate`. | VERIFIED | Run through the jar: a `;` header gave 10 problems at once (the `;` header, no `email` column, 9 placeholder problems, the account problem) and exit 2. A second run gave a missing-column report, `duplicate address ... line 4`, `line 2: ... email must contain exactly one '@'` and exit 2, all reported together. The `mailer.validate` of the first row is at `SendCommand.java` (the `PermanentMailException` branch) and covered by `PreflightTest` (`brokenCampaignReportsEveryProblemTogether`, line 60). The journal is opened only when `problems.isEmpty()`. |
| 3 | Against `recording`, each row is sent once, with one progress line per recipient and one forced journal line per attempt. Rerunning sends nothing and counts every row as skipped. | VERIFIED | `Journal.append` writes through a `FileChannel` opened `CREATE, WRITE, APPEND` and calls `force(true)` after each line. `SendRunTest.sendsEveryRowOnceAndJournalsIt` asserts exact stdout `[i/N] email accepted` plus `accepted 3, skipped 0, failed 0`, empty stderr, and the journal key, email, timestamp and outcome fields. `rerunOfACompleteCampaignSendsNothing` asserts `skipped (journal)` for all rows, `attempts()==0`, no waits and an unchanged 3-line journal. The tests are behavioral and assert on the real `RecordingTransport` attempt count. A failed journal write stops the run (`IOException` leads to exit 1, `JournalTest.aFailedJournalWriteStopsTheRun`). |
| 4 | A transient failure waits `retryAfter` (capped) or 30 s and retries up to 3 times, then is recorded and the run continues. Sender-rejected or auth-required stops at once with exit 3. The run ends with accepted/skipped/failed counts and exits 0 only if nothing failed. | VERIFIED | The constants are `DEFAULT_RETRY_WAIT=30s`, `MAX_RETRY_WAIT=5min` and `MAX_ATTEMPTS=4`. `catch (SenderRejectedException \| AuthRequiredException)` journals, prints the counts and returns 3. Other `MailException` is recorded and the loop continues. Exit is 1 if `failed>0`, else 0. `FailureHandlingTest` covers retry then accept (waits 7s then 30s), the cap and the spacing floor (5 parameter cases), 4 transient attempts ending as failed with the run continuing, permanent failures that continue, stopping on both exception types with exit 3 and exactly 2 attempts, auth during retries, and resume after a stop. |
| 5 | Sends are spaced by `--rate`, skipped rows don't wait. `--limit`, `--campaign-id` and `--journal` behave as specified. `account.properties` and the campaign files are read as UTF-8, secrets come from env vars only, and `./mvnw verify` stays green offline. | VERIFIED | `spacing(rate)` is `ceil(60000/rate)`. The sleep happens only before an actual attempt (`sentAny`), and the skip path `continue`s first. `PacingAndOptionsTest` covers the spacing table, skipped rows without waits, `--limit` (including with skips and with dry-run), `--campaign-id` prefixing, `--journal` relocation and bad-argument pre-flight problems. Account and campaign files use strict UTF-8 (`Files.newBufferedReader` and `Files.readString` raise `MalformedInputException`, reported as a problem). The account file holds only env var names (`certificate.passwordEnv`, `secretEnv`) and tests assert that no file value is echoed. `./mvnw -B -ntp -o verify` ended BUILD SUCCESS with 121 tests, 0 failures. |

**Score:** 5/5 truths verified (0 present but behavior-unverified)

The state-transition invariants (journal append, skip on rerun, stop on exit 3, retry count) each have a passing behavioral test that exercises the real command through the CLI-12 seam. They are not verified by presence alone.

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `SerialMail.java` (61 lines) | picocli entry, CLI-12 seam | VERIFIED | `commandLine(Function<AccountFile,Mailer>, Consumer<Duration>)` is used by `main` and by all tests. `realMailer` throws a clear "graph transport not available yet" message, which is the planned Phase 3 hook. |
| `SendCommand.java` (329 lines) | send, dry-run, retry, stop, rate, limit | VERIFIED | Substantive and wired. All seven options are present. |
| `Campaign.java` (295 lines) | hand-written RFC 4180 reader, template and attachments | VERIFIED | Handles quotes, CRLF, lone CR, BOM, line numbers, a `;` header, duplicate columns and a field-count mismatch. No CSV library is imported. |
| `Journal.java` (131 lines) | forced append-only JSONL, accepted-key lookup | VERIFIED | Torn last line repair and damaged-line warnings are present. |
| `AccountFile.java` (94 lines) | UTF-8 properties, credential form check | VERIFIED | Enforces exactly one credential form. Problems name keys, never values. |
| `example-campaign/*` | 3 placeholders, PDF and PNG, 5 `example.org` rows | VERIFIED | The PDF is 602 bytes with correct xref offsets (1:9, 2:58, 3:115, 4:241, 5:349, xref:419) and no Info dictionary. The PNG is 60,959 bytes with only IHDR, IDAT and IEND chunks (no tEXt or eXIf). Account values are zero GUIDs and `example.com`. |
| `pom.xml` (sample) | runnable jar with `lib/` classpath | VERIFIED | The manifest has `Main-Class` and `Class-Path: lib/...`. The jar ran on a clean `env -i` environment. |
| 9 test classes (121 tests) | CLI-12 coverage | VERIFIED | The tests are substantive, with exact-output assertions. |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `SerialMail.main` | `SendCommand` | `commandLine(realMailer, sleep)` | WIRED | Confirmed by running the jar. |
| `SendCommand` | `Journal` | `open`, `isAccepted`, `append` | WIRED | |
| `SendCommand` | `Campaign` and `AccountFile` | `read(..., problems)` | WIRED | A single `problems` list feeds exit 2. |
| `SendCommand` | `Mailer.validate` and `Mailer.send` | first-row validate, per-row send | WIRED | Tested against the real `RecordingTransport` from the test-jar. |
| `Journal.append` | disk | `channel.force(true)` | WIRED | |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Real Data | Status |
|----------|---------------|--------|-----------|--------|
| dry-run files | `message.html()` etc. | `Mail.using(...).template(campaign.template(), row.fields())` from the CSV | Yes (visible in the generated files) | FLOWING |
| send skip decision | `accepted` set | `Journal.open` parses the existing file | Yes (`rerunOfACompleteCampaignSendsNothing`) | FLOWING |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Full build offline | `./mvnw -B -ntp -o verify` | 121 tests pass, BUILD SUCCESS | PASS |
| Dry-run, no env | `env -i java -jar ... send --dry-run` | exit 0, 5 mails, attachments listed, no journal | PASS |
| Broken campaign | jar with a `;` header and a bad email | all problems listed, exit 2 | PASS |
| Real run before Phase 3 | jar without `--dry-run` | `account: the graph transport is not available yet...`, exit 2, nothing sent | PASS (expected, see below) |

### Probe Execution

Step 7c: SKIPPED. No probes are declared in the plans and there is no `scripts/*/tests/probe-*.sh`.

### Requirements Coverage

The plan frontmatter declares CLI-01 to CLI-07, CLI-10, CLI-11, CLI-12 (02-01); CLI-02, CLI-04, CLI-05, CLI-12 (02-02); CLI-01, CLI-06 to CLI-10 (02-03); and CLI-02, CLI-11, CLI-13 (02-04). Their union is exactly CLI-01 to CLI-13. REQUIREMENTS.md maps those 13 IDs to Phase 2 and CLI-14 to Phase 3, so no ID is orphaned and none is missing.

| Requirement | Description | Status | Evidence |
|-------------|-------------|--------|----------|
| CLI-01 | picocli `send` with all options, campaign id defaults to dir name | SATISFIED | `SendCommand` options. `campaignIdDefaultsToTheNormalisedDirectoryName` |
| CLI-02 | `account.properties` UTF-8, credential forms, env-only secrets | SATISFIED | `AccountFile`. A UTF-8 BOM edge case is a warning (WR-01) |
| CLI-03 | campaign files UTF-8, subject trailing newline trimmed, attachments | SATISFIED | `Campaign.read`. WR-02 notes that hidden files are attached |
| CLI-04 | hand-written RFC 4180 reader, `;` header error | SATISFIED | `Campaign.parseCsv`, `CampaignTest` (22 tests) |
| CLI-05 | pre-flight, all problems, exit 2 | SATISFIED | Jar run and `PreflightTest` |
| CLI-06 | JSONL journal, key, forced, rerun skips, a failed write stops | SATISFIED | `Journal`, `JournalTest` (17 tests) |
| CLI-07 | even spacing from `--rate`, skips don't wait | SATISFIED | `PacingAndOptionsTest` |
| CLI-08 | transient retry (3x, capped), permanent records and continues | SATISFIED | `FailureHandlingTest` |
| CLI-09 | sender or auth error stops with exit 3 | SATISFIED | `FailureHandlingTest.senderRejectedOrAuthRequiredStopsTheRun` |
| CLI-10 | progress lines, counts, exit 0 only if nothing failed | SATISFIED | `SendRunTest` |
| CLI-11 | dry-run files, no network, no journal, no secrets | SATISFIED | Jar run with `env -i` and `DryRunTest` |
| CLI-12 | command logic takes a `Mailer`, tested against `recording` | SATISFIED | The seam is used by all send tests. |
| CLI-13 | example campaign | SATISFIED | Verified above. The PDF viewer check remains for a human. |

### Anti-Patterns Found

A grep for `TBD`, `FIXME`, `XXX`, `TODO` and `HACK` over the sample sources and campaign found nothing, so no debt-marker blocker applies. The empty-collection initialisations I saw are filled before use, so they are not stubs. The one real-run stub, `SerialMail.realMailer`, is intentional and documented in the roadmap scope notes (CLI-14 belongs to Phase 3).

### Human Verification Required

#### 1. Example attachments and rendered pages in real viewers

**Test:** Run the jar dry-run with `--out` pointing at a new `mktemp -d` directory outside the repository. Open the five `.html` files in a browser. Open `example-campaign/attachments/info.pdf` in a real PDF viewer and `logo.png` in an image viewer.
**Expected:** Each page greets the recipient and shows their city and topic (clara's topic shows as `Tips, tricks & shortcuts`). The PDF opens as one page with the text "morimail example attachment". The PNG shows a colour gradient. Neither viewer shows an author, creator or producer.
**Why human:** No PDF tool is installed here. I verified the structure (xref offsets match, no Info dictionary, PNG has only IHDR, IDAT and IEND) but not that a real viewer renders it.

### Warnings (not goal blockers)

1. **Git identity on two commits (project rule, CLAUDE.md "Commit as Stephan Wald <stephan@morisoft.de>").** Commits `33d0b53` (code review report) and `28d0326` (review disposition) have author and committer `swald@basis.cloud`, a real address. The workspace `GIT_AUTHOR_*` and `GIT_COMMITTER_*` environment variables override the local git config (this matches the saved memory note). All 24 executor commits use the required identity. The branch is 33 commits ahead of `origin/main` and has not been pushed, so the two commits can still be rewritten before any push. I did not modify history. History is permanent once pushed, so I recommend fixing it before pushing. (The pre-existing `Initial commit` 8c9b082 also carries this address. That is outside Phase 2 and the owner's call.)
2. **Commit trailer.** All 23 trailers read `Co-Authored-By: Claude Sonnet 5.5`, while the plan text said Opus 5.5. This is cosmetic. It matches the attribution the environment requires and it has no bearing on the goal.
3. **Open code-review findings (0 critical, 3 warning, 6 info, all `open` in 02-REVIEW-DISPOSITION.md).** I read the code behind them and agree with the review's severity.
   - WR-01: an `account.properties` with a UTF-8 BOM reports a false "missing key" (the campaign reader strips the BOM, the account reader does not). This is an edge case against CLI-02.
   - WR-02: every file in `attachments/`, including `.DS_Store`, is attached.
   - WR-03: a row-independent defect (bad `from`, a bad attachment) produces one misattributed problem per row.
   - IN-06: the journal takes no lock, so two concurrent runs on one campaign could each send every pending row. The spec's rerun guarantee holds for sequential reruns, which is what the goal states.
   - IN-02: an unexpected `RuntimeException` ends the run with a stack trace and no counts.

   The owner should triage these (fix, defer or skip). WR-02 is the one with real recipient impact if someone's campaign directory holds junk files.
4. **A real (non-dry-run) run cannot send until Phase 3.** `SerialMail.realMailer` throws, and the run exits 2 with "graph transport is not available yet". This is as scoped (CLI-14 belongs to Phase 3), and the phase's sending logic is proven through the `recording` seam as the roadmap requires.

### Gaps Summary

No gaps. The phase goal is achieved: a campaign sender can dry-run a CSV campaign and send it with a forced journal, and a rerun skips every accepted row. The status is `human_needed` only because of the one visual PDF and PNG viewer check that the 02-04 plan explicitly deferred to a human.

---

_Verified: 2026-11-21T18:30:00Z_
_Verifier: Claude (gsd-verifier)_
