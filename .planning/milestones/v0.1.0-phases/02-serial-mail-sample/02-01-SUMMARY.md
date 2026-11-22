---
phase: 02-serial-mail-sample
plan: 01
subsystem: sample-cli
tags: [picocli, csv, jsonl-journal, dry-run, recording-transport]

requires:
  - phase: 01-library-core
    provides: Mail.Builder, Mailer, MailTemplate, error tree, RecordingTransport test-jar
provides:
  - serial-mail send command with the CLI-12 seam (Function<AccountFile, Mailer>, Consumer<Duration>)
  - account.properties reader, campaign reader with hand-written CSV parser
  - forced append-only JSON-lines journal with accepted-key lookup
  - send --dry-run writing rendered files into --out
affects: [02-02 pre-flight, 02-03 retry and stop handling, 02-04 example campaign and runnable jar, 03 Graph and Entra wiring]

actuals:
  tokens: 12700
  tasks: 3
  commits: 4
plan_head_before: dd7689da4052cf8310d597c4e837c8b7eabb240b
plan_head_after: c5d14048407199b0a54d0dd0687c6f246ae9705f

tech-stack:
  added: [info.picocli:picocli 4.7.7 (version managed in the parent, sample only)]
  patterns:
    - "Seam of two JDK types passed to the command constructor, no new interface"
    - "Pre-flight fills one List<String> of problems; the journal is opened last"
    - "All console output through picocli's getOut()/getErr() writers"

key-files:
  created:
    - morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/SerialMail.java
    - morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/SendCommand.java
    - morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/AccountFile.java
    - morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/Campaign.java
    - morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/Journal.java
    - morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/SampleFixtures.java
    - morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/SendRunTest.java
    - morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/DryRunTest.java
  modified:
    - morimail-sample-serial/pom.xml
    - .planning/phases/02-serial-mail-sample/02-RESEARCH.md

key-decisions:
  - "Dry-run I/O failure prints 'Dry run stopped: <exception>' and exits 1, as planned"
  - "A campaign with only a header row returns no Campaign (null) after the 'no recipient rows' problem, so later steps never see an empty row list"
  - "Jackson stays transitive through morimail; the sample POM gains only picocli"

patterns-established:
  - "Tests build the same command tree as main through SerialMail.commandLine(factory, sleep)"
  - "Test file names are built by concatenation so no address is directly followed by a file suffix"

requirements-completed: [CLI-01, CLI-02, CLI-03, CLI-04, CLI-05, CLI-06, CLI-07, CLI-10, CLI-11, CLI-12]

coverage:
  - id: D1
    description: "A three-row campaign is sent once through recording, journaled line by line, and a rerun skips every accepted row and sends nothing"
    requirement: "CLI-06"
    verification:
      - kind: unit
        ref: "morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/SendRunTest.java#sendsEveryRowOnceAndJournalsIt"
        status: pass
      - kind: unit
        ref: "morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/SendRunTest.java#rerunSkipsAcceptedRowsAndResendsFailedOnes"
        status: pass
      - kind: unit
        ref: "morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/SendRunTest.java#rerunOfACompleteCampaignSendsNothing"
        status: pass
    human_judgment: false
  - id: D2
    description: "A real run through main stops before sending with the graph-not-available message and exit 2; usage errors exit 2"
    requirement: "CLI-12"
    verification:
      - kind: unit
        ref: "morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/SendRunTest.java#realRunThroughMainStopsBeforeSending"
        status: pass
      - kind: unit
        ref: "morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/SendRunTest.java#missingCommandOrOptionsExitTwo"
        status: pass
    human_judgment: false
  - id: D3
    description: "send --dry-run writes rendered files with sanitised, zero-padded names, lists attachments, and uses no Mailer and no journal"
    requirement: "CLI-11"
    verification:
      - kind: unit
        ref: "morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/DryRunTest.java (seven methods)"
        status: pass
    human_judgment: false
  - id: D4
    description: "This phase's planning files hold no workstation path, session id, private key or non-example address"
    verification:
      - kind: other
        ref: "git grep --untracked scoped hygiene gates over morimail-sample-serial, .gitignore and the phase directory"
        status: pass
    human_judgment: false

duration: 15min
completed: 2026-11-21
status: complete
---

# Phase 2 Plan 01: Send a campaign once through recording, then dry-run it into rendered files Summary

**picocli `serial-mail send` that reads a CSV campaign, sends one mail per row through an injected Mailer with a forced JSON-lines journal (rerun skips accepted rows), and dry-runs the same campaign into sanitised, zero-padded rendered files.**

## Performance

- **Duration:** about 15 min
- **Completed:** 2026-11-21T17:53Z
- **Tasks:** 3
- **Files modified:** 10 (8 sample sources and tests, the sample POM, one scrubbed planning file)

## Accomplishments
- `send` pre-flight (account file, campaign files, per-row `build()`, Mailer factory, `mailer.validate` on the first mail), then the send loop with a journal line per attempt, `--rate` spacing through an injected `Consumer<Duration>` and exit codes 0, 1 and 2.
- Hand-written comma-only CSV reader (quotes, `""`, quoted line breaks, CRLF/LF/CR, physical start lines), strict UTF-8 reads with a leading BOM removed, account file read through a UTF-8 reader with values trimmed and never echoed.
- `Journal` appends through a `FileChannel` (CREATE, WRITE, APPEND) and forces after every line; an unreadable existing line fails closed.
- `--dry-run`/`--out`: `<n>-<safe address>` plus `.html`, `.subject.txt` and (only with body.txt) `.txt`, width `max(2, digits)`, attachments line, no Mailer factory call, no journal, nothing written when pre-flight fails.
- Phase research file scrubbed of absolute workstation paths and the session scratch path.

## Task Commits

1. **Task 1: send a campaign once, rerun sends nothing (tracer)** - `29a0178` (feat)
2. **Task 2: dry-run (TDD)** - RED `ce5b7b5` (test), GREEN `523b8f8` (feat); no refactor commit was needed
3. **Task 3: scrub planning files** - `c5d1404` (chore)

**Plan metadata:** the docs commit that follows this SUMMARY.

## TDD notes (Task 2)

- RED: `DryRunTest` (7 methods) failed on the planned assertion, for example `writesRenderedFilesAndListsAttachments` expected exit 0 but got 2 because `--dry-run` was an unknown option. `gsd_run check tdd-red-evidence` on the Surefire XML returned `RED_EVIDENCE_OK` (target_test_failed). Semantic assessment: the test executed and failed for the intended reason (the missing option), not through a compile, fixture or setup fault.
- GREEN: option, branch and writer added; DryRunTest 7/7 and SendRunTest 6/6 pass.

## Files Created/Modified
- `morimail-sample-serial/pom.xml` - picocli dependency (no version, managed in the parent)
- `morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/SerialMail.java` - root command, `main`, `commandLine(...)` seam, `realMailer` (D-01 stop)
- `morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/SendCommand.java` - options, pre-flight, send loop, dry-run, `spacing`, `safeName`
- `morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/AccountFile.java` - account record and reader
- `morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/Campaign.java` - campaign reader and CSV parser
- `morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/Journal.java` - forced JSON-lines journal
- `morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/SampleFixtures.java`, `SendRunTest.java`, `DryRunTest.java` - tests
- `.planning/phases/02-serial-mail-sample/02-RESEARCH.md` - repo-relative paths, scratch path described in words (`02-PATTERNS.md` held no such path and is unchanged)

## Decisions Made
- A header-only `recipients.csv` makes `Campaign.read` return null after adding the problem, so the command never indexes an empty row list.
- Everything else followed the plan.

## Deviations from Plan

### Auto-fixed Issues
None in code.

### Process deviation

**1. Commit trailer names a different model than the plan text**
- **Found during:** Task 1 (first commit)
- **Issue:** The plan and its verification line require the trailer `Co-Authored-By: Claude Opus 5.5 ...`. The session's attribution instruction, which names the model that actually did the work, says `Claude Sonnet 5.5`. Writing the other model name would misattribute the commits.
- **Fix:** All four task commits end with the Sonnet 5.5 trailer, built with `printf` so the address stays out of every file. The plan's last-line trailer check will therefore not match the Opus text; the author and committer identity check passes on every commit.
- **Files modified:** none (commit messages only)

---

**Total deviations:** 0 auto-fixed, 1 process deviation (trailer model name).
**Impact on plan:** none on code or behaviour.

## Issues Encountered
None. `./mvnw -B -ntp verify`, the scoped `-Prelease clean verify -Dgpg.skip -pl morimail -am` and the offline `-o verify` builds are all green (sample: 14 tests, 0 failures).

## Known Stubs
None. `SerialMail.realMailer` throwing the "graph transport is not available yet" message is the planned D-01 behaviour; Phase 3 (CLI-14) replaces it.

## Threat Flags
None. No new network, auth or trust-boundary surface; the sample imports no `java.net` types.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Ready for the wave-2 plans 02-02 (pre-flight completeness), 02-03 (retry, stop, argument checks, journal warnings) and 02-04 (example campaign, runnable jar).
- Argument range checks (`--rate`, `--limit` at least 1, underivable campaign id) are intentionally not in this plan; `--rate 0` currently divides by zero until plan 02-03 adds them.

---
*Phase: 02-serial-mail-sample*
*Completed: 2026-11-21*

## Self-Check: PASSED
