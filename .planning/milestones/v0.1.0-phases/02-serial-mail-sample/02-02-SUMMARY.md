---
phase: 02-serial-mail-sample
plan: 02
subsystem: sample-cli
tags: [preflight, csv, placeholders, account-file, utf-8]

requires:
  - phase: 02-serial-mail-sample
    provides: SendCommand pre-flight list, Campaign and AccountFile readers (plan 01)
provides:
  - placeholder-against-column check (core pattern copied, raw form included)
  - semicolon-header, repeated-column and duplicate-address problems
  - account file credential-form rule and strict UTF-8 messages
affects: [02-03 argument checks, 02-04 example campaign, 03 Graph and Entra wiring]

actuals:
  tokens: 10750
  tasks: 3
  commits: 5
plan_head_before: afc64aa2b055a9fd10131eab485c3c8c66540ed0
plan_head_after: b219c5b6c1dace268dd6cfdf9a61de7f17e43675

tech-stack:
  added: []
  patterns:
    - "Header rules set one headerBroken flag and never return early, so every determinable problem is reported in one run"
    - "A missing placeholder column is filled with an empty value so build() still reports the other problems of each row"

key-files:
  created:
    - morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/PreflightTest.java
    - morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/CampaignTest.java
    - morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/AccountFileTest.java
  modified:
    - morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/Campaign.java
    - morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/AccountFile.java

key-decisions:
  - "A broken header (semicolon, no email column, repeated column) still runs the placeholder check but not the row analysis"
  - "Duplicate addresses compare with toLowerCase(Locale.ROOT); blank addresses are left to build()"
  - "Unknown account.properties keys stay ignored (flagged assumption left open)"

patterns-established:
  - "Pre-flight problems name file and key only, proven by a sentinel test"

requirements-completed: [CLI-02, CLI-04, CLI-05, CLI-12]

coverage:
  - id: D1
    description: "A broken campaign (missing placeholder column, invalid address, duplicate, failing validate) prints all four problems in the fixed order and exits 2 with nothing sent and no journal; a dry-run reports the same without the Mailer"
    requirement: "CLI-05"
    verification:
      - kind: unit
        ref: "morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/PreflightTest.java#brokenCampaignReportsEveryProblemTogether"
        status: pass
      - kind: unit
        ref: "morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/PreflightTest.java#dryRunReportsTheSameProblemsWithoutTheMailer"
        status: pass
    human_judgment: false
  - id: D2
    description: "A semicolon header is reported together with the account, email-column and placeholder problems"
    requirement: "CLI-04"
    verification:
      - kind: unit
        ref: "morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/PreflightTest.java#semicolonHeaderIsReportedWithEveryOtherProblem"
        status: pass
    human_judgment: false
  - id: D3
    description: "The CSV reader and campaign files hold for every quoting, line-ending, BOM, encoding, header and attachment edge case"
    requirement: "CLI-04"
    verification:
      - kind: unit
        ref: "morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/CampaignTest.java (22 methods)"
        status: pass
    human_judgment: false
  - id: D4
    description: "The account file needs exactly one credential form, is read as strict UTF-8 and never echoes a value; a dry-run needs no environment variable"
    requirement: "CLI-02"
    verification:
      - kind: unit
        ref: "morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/AccountFileTest.java (21 test cases)"
        status: pass
    human_judgment: false

duration: 12min
completed: 2026-11-21
status: complete
---

# Phase 2 Plan 02: A broken campaign is rejected before the first send, with every problem at once Summary

**Pre-flight now reports placeholders without a CSV column, semicolon headers, repeated columns, duplicate addresses and bad account credentials together in one fixed order, exit 2, nothing sent, no journal, and never prints a value from account.properties.**

## Performance

- **Duration:** about 12 min
- **Completed:** 2026-11-21T17:58Z
- **Tasks:** 3
- **Files modified:** 5 (two sample sources, three new test classes)

## Accomplishments
- `Campaign.PLACEHOLDER` copies the core renderer's pattern text; `placeholders(String)` returns names in first-appearance order. Every name without an exactly equal header cell is one problem per file, the raw `$$!name$$` form included.
- Header rules (semicolon, no email column, repeated column) all report and set one flag; the placeholder check still runs; only the rows are skipped when the header is broken.
- Duplicate addresses (lowercase compare, `Locale.ROOT`) are reported per later line; both rows stay in the list.
- `AccountFile.read` enforces one credential form (not both, not none, certificate keys together), reports `is not valid UTF-8` and `contains a malformed unicode escape`, and the sentinel test proves no value is echoed on a broken file or on the D-01 stop.
- 51 new test cases: `PreflightTest` 8, `CampaignTest` 22, `AccountFileTest` 21.

## Task Commits

1. **Task 1: broken campaign rejected with every problem (tracer)** - `e550b85` (feat)
2. **Task 2: reader edge cases (TDD)** - RED `7d89d18` (test), GREEN `3ee67eb` (feat)
3. **Task 3: account file rules (TDD)** - RED `16e497e` (test), GREEN `b219c5b` (feat)

**Plan metadata:** the docs commit that follows this SUMMARY.

## TDD notes

- Task 2 RED: 22 tests, one failure (`duplicateHeaderColumnIsAProblem`), the other 21 already held under the plan 01 reader. `gsd_run check tdd-red-evidence` returned `RED_EVIDENCE_OK` (target_test_failed). Semantic assessment: the test ran and failed on the planned assertion (no repeated-column problem reported), not on a compile or fixture fault.
- Task 3 RED: 21 cases, 5 failures and 1 error (`noCredentialIsAProblem`, `bothFormsAreAProblem`, `oneCertificateKeyAloneIsAProblem`, `fileThatIsNotUtf8IsAProblem`, `problemsNeverEchoAValueFromTheFile`, and `malformedUnicodeEscapeIsAProblem` as an uncaught `IllegalArgumentException`). `RED_EVIDENCE_OK`. Semantic assessment: each failure is the planned missing rule.
- Task 1 is a tracer, not a TDD task: the first test run failed to compile because `Campaign.placeholders` did not exist yet, so it was implemented together with the tests and verified green end to end.
- No refactor commits were needed.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Checked exception in a test method**
- **Found during:** Task 1
- **Issue:** `Mail.Builder.build()` throws the checked `InvalidMailException`, so `extractedNamesSatisfyTheCoreRenderer` did not compile.
- **Fix:** the test method declares `throws InvalidMailException`.
- **Files modified:** `morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/PreflightTest.java`
- **Commit:** `e550b85`

### Process deviation

**2. Commit trailer names a different model than the plan text** (same as plan 01)
- The plan asks for a trailer naming Claude Opus 5.5; the commits carry the Sonnet 5.5 trailer of the model that did the work, per the session attribution. Author and committer identity check passes on every commit.

**Total deviations:** 1 auto-fixed (blocking, test only), 1 process deviation.
**Impact on plan:** none on behaviour.

## Issues Encountered
None. `./mvnw -B -ntp verify` (65 tests in the sample reactor run, 0 failures), `-Prelease clean verify -Dgpg.skip -pl morimail -am` and the offline `-o verify` are green. The scoped hygiene gates (no `System.getenv`, no `java.net.` in the sample, no CSV library, no non-example address, no non-zero GUID) hold.

## Known Stubs
None.

## Threat Flags
None. This plan adds no network, auth or trust-boundary surface; T-02-07 (value echo) and T-02-08 (misparsed rows) are mitigated and tested.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Ready for 02-03 (argument checks, retry, stop, journal robustness) and 02-04 (example campaign): the example campaign must keep its placeholders as columns, no repeated column and no semicolon in its header.
- Open flagged assumptions left to the owner: unknown keys in account.properties are ignored; text after a closing CSV quote is appended and a quote inside an unquoted field is literal.

---
*Phase: 02-serial-mail-sample*
*Completed: 2026-11-21*

## Self-Check: PASSED
