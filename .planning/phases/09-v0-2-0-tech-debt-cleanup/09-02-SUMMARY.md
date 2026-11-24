---
phase: 09-v0-2-0-tech-debt-cleanup
plan: 02
subsystem: sample
tags: [preflight, serial-mail, graph-limits, validate, spec]
status: complete

requires:
  - phase: 06-inline-images-in-the-serial-mail-sample
    provides: SendCommand pre-flight and the invalidMail grouping helper (Phase 6 audit item: pre-flight validated only the first mail)
  - phase: 09-v0-2-0-tech-debt-cleanup
    provides: plan 09-01 (core cid: rules); no file overlap, ordered for linear history
provides:
  - pre-flight that runs mailer.validate on every built mail and groups identical messages with their row lines
  - spec section 8.3 step 2 in line with the code ("for every row's mail")
affects: [09-03, milestone archive]

actuals:
  tokens: 1361
  tasks: 2
  commits: 2
plan_head_before: 5410b81780896a474a9605a407b023de853c87eb
plan_head_after: 1f783ae216ab006be9413b49e36f33339ce610c5

tech-stack:
  added: []
  patterns: ["validate problems grouped by message through the existing invalidMail helper, no new seam"]

key-files:
  created: []
  modified:
    - morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/SendCommand.java
    - morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/PreflightTest.java
    - docs/spec.md

key-decisions:
  - "09-02: pre-flight validates every non-null built mail (also rows beyond --limit, like the build loop); a message shared by rows is one line, `mailer.validate rejected the mail: <message>` plus the row lines, reported after the build problems"
  - "09-02: the later-row and shared-limit tests use the real graph transport with baseUrl http://localhost:1/v1.0 and a counting token provider asserted to be zero, because the recording transport fails every mail or none"

requirements-completed: []

duration: about 15 min
completed: 2026-11-24
---

# Phase 9 Plan 02: pre-flight validates every mail Summary

**A serial run now stops in pre-flight, exit 2, when any row's mail is refused by the transport's validate (for Graph: a body estimate above 4 MiB or an attachment of 3 MiB or more), and a limit that every row breaks is one line ending `(all N rows)`.**

## Accomplishments

- D-10: `SendCommand.call()` loops over the index-aligned `mails` list, skips null elements, calls `mailer.validate(mails.get(i))` and collects `PermanentMailException` messages in a `LinkedHashMap` keyed by `mailer.validate rejected the mail: <message>`. Each entry goes through `invalidMail`, after the build problems. The Javadoc of `invalidMail` now names `build()` and `mailer.validate` messages. `writeDryRun` still lists only the first mail's attachments (its one `mails.get(0)` stays).
- D-11: `laterRowThatGraphRejectsStopsPreflight` runs a campaign whose first two rows pass and whose third row (line 4) has a 4.2 million character city. Result: exit 2, empty stdout, two stderr lines (`recipients.csv line 4: mailer.validate rejected the mail: estimated request size ... bytes is above the limit of 4 MiB (4194304 bytes)` and `Pre-flight failed with 1 problem(s); nothing was sent.`), zero token calls, no journal file.
- D-10 grouping: `limitProblemOfEveryRowIsOneLine` uses a sparse 3 MiB `big.bin` shared attachment; the three rows give one line ending `(all 3 rows)`. `brokenCampaignReportsEveryProblemTogether` now expects `mailer.validate rejected the mail: too large for the test (recipients.csv lines 2, 4)` with the problem count still 4.
- D-12: docs/spec.md section 8.3 step 2 (line 321) says `mailer.validate` passes for every row's mail. Only that line changed.

## Task Commits

| Task | Commit | Subject | Sample tests after |
|------|--------|---------|--------------------|
| 1 (tracer) | 7bd268d | fix(09-02): validate every mail in pre-flight | 165 |
| 2 | 1f783ae | test(09-02): one pre-flight line for a limit every row breaks, spec 8.3 says every row | 166 |

After each commit `./mvnw -B -ntp -o verify` was green (core 374, helper 71, sample 165 then 166). Sample test count: 164 before this plan, 166 after.

Tracer gate: the red run is recorded below; after the loop was in, the same targeted run and the full offline verify passed, and the tracer's verify steps were re-run end to end before Task 2 began.

## Red run of Task 1 step 1

Before the SendCommand edit, `PreflightTest` ran 14 tests with 2 failures in under a second: `laterRowThatGraphRejectsStopsPreflight` failed with `expected: 2 but was: 1` (the old pre-flight let the third row through, and the run reached only the refused local port 1), and `brokenCampaignReportsEveryProblemTogether` failed on the changed fourth line. No external host was contacted.

## D-12 checks (recorded, no edit)

- README.md line 169, "Pre-flight checks the campaign before the first mail", means before the first mail is sent; it stays. `git diff 58ad024 -- README.md` is empty.
- `morimail-sample-serial` has no README of its own (directory holds `example-campaign`, `pom.xml`, `src`).
- The send help text in `SendCommand` says nothing about which mails are validated; the only mentions of `validate` in the file are the new loop, its message key and the `invalidMail` Javadoc.

## Deviations from Plan

None - plan executed exactly as written.

Two small notes, not deviations. The first draft of the new test split the `startsWith` literal across a string concatenation; the plan's source gate wants the literal `"recipients.csv line 4: mailer.validate rejected the mail: estimated request size "` on one line, so I put it on one line before the first green run. And in this shell `grep` is a ugrep wrapper, so I ran every gate with `/usr/bin/grep` (`unset -f grep`).

## Known Stubs

None.

## Threat Flags

None. T-09-05 (half-sent campaign) is mitigated by the loop and proven by `laterRowThatGraphRejectsStopsPreflight`; T-09-07 (a test reaching a real host) by the localhost:1 base URL and the zero token-call assertions in both Graph tests; T-09-08 by the commit gates (no address outside example.com and example.org, no home path, no long hex string in the added lines). No raw invisible Unicode character was written into any file.

## Pending owner actions

- Push when ready so Temurin 17 CI runs on this phase. Nothing was pushed, tagged or published.

## Next Phase Readiness

Ready for 09-03.

## Self-Check: PASSED

- Modified files exist and are committed: SendCommand.java, PreflightTest.java, docs/spec.md.
- Commits 7bd268d and 1f783ae are ancestors of HEAD; `git rev-list --count 5410b81..HEAD` was 2 before this SUMMARY.
- All task source, content, test, verify and commit gates passed; author and committer are Stephan Wald <stephan@morisoft.de> on both commits.
