---
phase: 02-serial-mail-sample
plan: 03
subsystem: sample-cli
tags: [retry, exit-codes, pacing, journal, picocli]

requires:
  - phase: 02-serial-mail-sample
    provides: SendCommand run loop, Journal, SampleFixtures, SerialMail seam (plan 01)
provides:
  - transient retry loop (4 attempts, capped waits never below the spacing) with a journal line per attempt
  - stop with exit code 3 on SenderRejectedException or AuthRequiredException
  - pre-flight checks for --rate, --limit, --campaign-id and an underivable campaign id
  - journal that warns about damaged lines, repairs a torn last line and stops the run on a failed write
affects: [02-04 example campaign and runnable jar, 03 Graph and Entra wiring]

actuals:
  tokens: 12900
  tasks: 3
  commits: 5
plan_head_before: fe51636d71424d8e9814e36028c4d1d11f21373a
plan_head_after: 59340e52c2f2a13d0cf8df48a27e6003726acb23

tech-stack:
  added: []
  patterns:
    - "Catch order SenderRejectedException | AuthRequiredException, then TransientMailException, then MailException"
    - "Every wait goes through the injected Consumer<Duration>; tests record it instead of sleeping"
    - "Journal fixtures in option tests are written as raw JSON lines, never through Journal"

key-files:
  created:
    - morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/FailureHandlingTest.java
    - morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/PacingAndOptionsTest.java
    - morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/JournalTest.java
  modified:
    - morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/SendCommand.java
    - morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/Journal.java

key-decisions:
  - "The row that causes an exit-3 stop counts as failed and has a failed journal line; later rows are not counted"
  - "A damaged journal line is warned about and the row is sent again (at-least-once); it never stops pre-flight"
  - "Pre-flight still checks every CSV row when --limit is set (spec 8.3.2 wording)"

requirements-completed: [CLI-01, CLI-06, CLI-07, CLI-08, CLI-09, CLI-10]

coverage:
  - id: D1
    description: "A transient failure is retried with capped waits that never undercut the spacing, each attempt is journaled, a row ends as failed after four attempts and the run goes on"
    requirement: "CLI-08"
    verification:
      - kind: unit
        ref: "morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/FailureHandlingTest.java#transientFailuresAreRetriedThenAccepted"
        status: pass
      - kind: unit
        ref: "morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/FailureHandlingTest.java#fourTransientAttemptsEndAsFailedAndTheRunGoesOn"
        status: pass
      - kind: unit
        ref: "morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/FailureHandlingTest.java#retryWaitIsCappedAndNeverShorterThanTheSpacing"
        status: pass
    human_judgment: false
  - id: D2
    description: "A sender-rejected or auth-required failure, also during retries, stops the run with exit 3; a rerun resumes with the failed row"
    requirement: "CLI-09"
    verification:
      - kind: unit
        ref: "morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/FailureHandlingTest.java#senderRejectedOrAuthRequiredStopsTheRun"
        status: pass
      - kind: unit
        ref: "morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/FailureHandlingTest.java#authRequiredDuringRetriesStopsAtOnce"
        status: pass
      - kind: unit
        ref: "morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/FailureHandlingTest.java#aStoppedRunResumesWithTheFailedRow"
        status: pass
    human_judgment: false
  - id: D3
    description: "Spacing is ceil(60000 / rate) ms between attempts only; --limit, --campaign-id and --journal behave as specified; bad arguments are pre-flight problems with exit 2"
    requirement: "CLI-07"
    verification:
      - kind: unit
        ref: "morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/PacingAndOptionsTest.java (22 test cases)"
        status: pass
    human_judgment: false
  - id: D4
    description: "A damaged or torn journal line is warned about and never counts as accepted, the next record starts on its own line, a failed write stops the run with exit 1, and the help text states the at-least-once rule"
    requirement: "CLI-06"
    verification:
      - kind: unit
        ref: "morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/JournalTest.java (17 methods)"
        status: pass
    human_judgment: false

duration: 5min
completed: 2026-11-21
status: complete
---

# Phase 2 Plan 03: Retry, stop, pacing and a journal that survives a crash Summary

**The `send` run loop now retries transient failures (four attempts, waits capped at 5 min and never below the `--rate` spacing), stops with exit 3 on sender-rejected or auth-required failures, rejects bad `--rate`, `--limit` and campaign ids in pre-flight, and keeps a journal that warns about damaged lines, repairs a torn last line and stops the run when a write fails.**

## Performance

- **Duration:** about 5 min (started 2026-11-21T17:59Z)
- **Completed:** 2026-11-21T18:05Z
- **Tasks:** 3
- **Files modified:** 8 (two sample sources, three new test classes)

## Accomplishments
- Attempt loop in `SendCommand` with `DEFAULT_RETRY_WAIT` 30 s, `MAX_RETRY_WAIT` 5 min, `MAX_ATTEMPTS` 4 and `seconds(Duration)`; a journal line with outcome `transient` per retried attempt and the progress line `[i/N] <email> transient, retry <k>/3 in <s>s`.
- Exit code 3 and a `Stopped: <SimpleName>: <message>` line on stderr for `SenderRejectedException | AuthRequiredException`, caught before `TransientMailException` and `MailException` (AuthRequiredException is not a PermanentMailException).
- Four argument problems printed before every other problem, so they appear together with campaign problems; `--rate 0` can no longer divide by zero (this closes the plan 01 note).
- `Journal.open(Path, List<String>)` collects `<file> line <n> is damaged and was ignored` for lines that are not JSON objects, writes a line feed when the file lacks one, and the command prints `warning: ...` before the first send.
- Help footer: a mail in flight when the run is stopped has no journal line, so the next run sends it again.
- 52 new test cases: `FailureHandlingTest` 13, `PacingAndOptionsTest` 22, `JournalTest` 17. The sample now runs 117 tests in the full build, all green.

## Task Commits

1. **Task 1: retry and stop (tracer)** - `0ef9989` (feat)
2. **Task 2: pacing and options (TDD)** - RED `e9f07f6` (test), GREEN `3382387` (feat)
3. **Task 3: journal robustness (TDD)** - RED `7573459` (test), GREEN `59340e5` (feat)

**Plan metadata:** the docs commit that follows this SUMMARY.

## TDD notes

- Task 2 RED: 22 tests, 6 failures (the four parameterized argument checks, the combined-problems test and the no-directory-name test); the pacing, limit, campaign-id and journal-option tests already held under plan 01. `gsd_run check tdd-red-evidence` on the Surefire XML with target `campaignWithoutADirectoryNameNeedsACampaignId` returned `RED_EVIDENCE_OK` (target_test_failed). Semantic assessment: the target ran and failed because `--campaign /` produced no `--campaign has no directory name` problem (it threw on the missing file name), the planned missing check.
- Task 3 RED: 17 tests, 2 failures and 4 errors. The RED commit also carries the `Journal.open(Path, List<String>)` signature with the old behaviour so every commit compiles; the four errors are the old "cannot be read" `IOException` on a damaged line, the failures are the missing `warning:` output and the missing footer. `RED_EVIDENCE_OK` for target `theHelpTextStatesTheAtLeastOnceRule`. Semantic assessment: the target ran and failed on the planned assertion (footer absent), not on a compile or fixture fault.
- Task 1 is a tracer: tests and implementation were written together and verified green end to end; `FailureHandlingTest` and `SendRunTest` pass.
- No refactor commits were needed.

## Decisions Made
- The row behind an exit-3 stop counts as failed and has its failed journal line (flagged assumption kept).
- Damaged journal lines are warned about and sent again, never a pre-flight stop, so a crash never needs a manual journal edit.
- Pre-flight checks every CSV row even with `--limit` (flagged assumption kept; reversible in `SendCommand`).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Footer assertion broke on picocli line wrapping**
- **Found during:** Task 3
- **Issue:** picocli wraps the usage footer at 80 columns, so "the next run sends it again" is split across two lines and the plain `contains` check in the new help-text test failed although the footer was correct.
- **Fix:** the test folds whitespace (`replaceAll("\\s+", " ")`) before the `contains` check; the footer text in `SendCommand` is unchanged.
- **Files modified:** `morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/JournalTest.java`
- **Commit:** `59340e5`

### Process deviations

**2. Commit trailer names a different model than the plan text** (same as plans 01 and 02)
- The plan asks for a trailer naming Claude Opus 5.5; the commits carry the Sonnet 5.5 trailer of the model that did the work, per the session attribution. Author and committer identity check passes on every commit.

**3. Task 3 RED commit includes a signature-only change to production code**
- So that every commit builds, `Journal.open(Path, List<String>)` and its call site were introduced in the RED commit with the old behaviour; the behaviour change is in the GREEN commit.

**Total deviations:** 1 auto-fixed (test only), 2 process deviations.
**Impact on plan:** none on behaviour.

## Issues Encountered
None. `./mvnw -B -ntp verify` (117 sample tests, 0 failures, 0 skipped), `-Prelease clean verify -Dgpg.skip -pl morimail -am` and the offline `-o verify` are green. The `/dev/full` write-failure test ran (no skip). The scoped hygiene gates (no workstation path, non-example address, non-zero GUID or private key in the sample sources, `java.net.` absent from the sample) hold and `git tag --list` prints nothing.

## Known Stubs
None.

## Threat Flags
None. No new network, auth or trust-boundary surface; T-02-12 (forced journal line, write failure stops the run), T-02-13 (damaged lines never count as accepted) and T-02-14 (spacing before every attempt, capped retry wait, four attempts, stop on sender or auth failure) are mitigated and tested.

## User Setup Required
None - no external service configuration required.

## Open flagged assumptions
- A transient failure that never clears costs up to four attempts with waits up to five minutes per row; there is no time budget for a whole run.
- Progress lines go to stdout and stop or warning lines to stderr; their relative order is not guaranteed when both go to one terminal or pipe.
- The stop on the first sender-rejected or auth-required failure applies even if later rows would use another sender (all rows share one account here).

## Next Phase Readiness
- Ready for 02-04 (example campaign, runnable jar): the example campaign works with the default `--rate 20` and the derived campaign id, both of which pass the new argument checks.

---
*Phase: 02-serial-mail-sample*
*Completed: 2026-11-21*

## Self-Check: PASSED
