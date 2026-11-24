---
phase: 09-v0-2-0-tech-debt-cleanup
plan: 03
subsystem: docs
tags: [html-helper, review-dispositions, planning-records, phase-gate]
status: complete

requires:
  - phase: 09-v0-2-0-tech-debt-cleanup
    provides: "plans 09-01 and 09-02 (core cid: rules, content types, pre-flight validation) in HEAD"
provides:
  - HtmlImages class Javadoc sentence on white space around a src value, and a strip() comment that no longer claims the HTML rule
  - 07 and 05 review dispositions settled with a Source on every row, open 0
  - 07-01 and 08-02 summaries that parse and say what is true now
affects: [milestone archive]

actuals:
  tokens: 2200
  tasks: 2
  commits: 2
plan_head_before: de28a7e16b6565c03e84098c90bf929eb5321208
plan_head_after: 7620fae121349dd4189c728053fa3f69180ed73c

tech-stack:
  added: []
  patterns: []

key-files:
  created: []
  modified:
    - morimail-html/src/main/java/de/morisoft/morimail/html/HtmlImages.java
    - .planning/phases/07-html-image-helper-module/07-REVIEW-DISPOSITION.md
    - .planning/phases/07-html-image-helper-module/07-01-SUMMARY.md
    - .planning/phases/08-docs-and-release-readiness/08-02-SUMMARY.md
    - .planning/phases/05-inline-images-in-core-and-graph/05-REVIEW-DISPOSITION.md

key-decisions:
  - "09-03: 07 IN-02 is recorded skipped, not deferred; the finding is closed by a decision without a code change and nothing is planned for it"
  - "09-03: open counts only rows at open, so both disposition files carry open: 0 (corrects research assumption A4)"
  - "09-03: 05 IN-02 is recorded fixed for the core only; decision-ID comments in morimail-html and morimail-sample-serial are outside the finding and stay"
  - "09-03: 05 IN-03 stays deferred (case-sensitive content IDs kept as released in 0.2.0)"

requirements-completed: []

duration: about 15 min
completed: 2026-11-24
---

# Phase 9 Plan 03: helper docs, planning records, dispositions and the phase gate Summary

**HtmlImages now documents that white space around a src value is ignored and no longer claims the HTML rule for strip(); the 05 and 07 review dispositions are settled (open 0), the 07-01 and 08-02 summaries parse again, and both builds are green on the combined tree.**

## Accomplishments

- D-13: the class Javadoc says `White space before and after a {@code src} value is ignored.` The comment above `strip()` in `rewrite()` says strip() removes Unicode white space, a wider set than the ASCII white space HTML strips from a URL. The code line is unchanged.
- D-14: no test and no seam. `readAtMost` and `download` are private static and the stream comes only from the private `Http.CLIENT`, so no existing package-private path reaches the reader with a custom InputStream, and WireMock can cause IOExceptions and timeouts but not an Error. 07 IN-02 is closed as accepted without test.
- D-15: 07-01-SUMMARY.md line 11 is now a quoted YAML string, and the five lines that said `provided` now say `compile scope since 07-04` and keep `provided ... at 07-01` as history. `summary-extract` parses it and the one-liner names compile scope.
- D-16: 08-02-SUMMARY.md lines 10 and 104 keep the historical statement and add that it is superseded by the release of 0.2.0 as tag v0.2.0.
- D-17: 07-REVIEW-DISPOSITION.md has IN-01 fixed and IN-02 skipped, the other six rows still fixed, `open: 0`, `total: 8`. 05-REVIEW-DISPOSITION.md has WR-01, WR-02, IN-01 and IN-02 fixed and IN-03 deferred, `open: 0`, `total: 5`, every row with a Source.

## Task Commits

| Task | Commit | Subject |
|------|--------|---------|
| 1 (tracer) | 3918d7a | docs(09-03): state the src white-space rule and close 07 IN-01 and IN-02 |
| 2 | 7620fae | docs(09-03): repair the 07-01 and 08-02 summaries and settle the 05 review |

## Disposition choices

- 07 IN-02 is `skipped`: the file's vocabulary is open, fixed, skipped, deferred; `deferred` would claim postponement and nothing is planned. 05 IN-03 stays `deferred` because CONTEXT lists rejecting case-colliding ids as an idea to revisit after a live test.
- `open:` counts only rows whose disposition is `open`, which is how the disposition gate computes it. This corrects 09-RESEARCH.md assumption A4 (which inferred "every non-fixed row" from the hand-edited 07 file that still said `open: 2` with no open row).
- 05 IN-02 is `fixed` for the scope the finding names, the core: spec lines aligned by 5d50c94 and 90162d7, the decision-ID comment in Mail.java removed by 09-01, `morimail/src/main` holds none. The decision-ID comments left in morimail-html and morimail-sample-serial are outside this finding and stay; the Source cell says so and the phase gate checks exactly `morimail/src/main`.

## Gate results

- Offline `./mvnw -B -ntp -o verify`: BUILD SUCCESS, morimail 374 tests, morimail-html 71, morimail-sample-serial 166, no failures.
- `./mvnw -B -ntp -Prelease verify -Dgpg.skip -pl morimail,morimail-html -am`: BUILD SUCCESS, 0 `[WARNING]` lines.
- Helper and 07 record gate, summaries gate (all summaries of phases 05 to 08 parse), 05 record gate, both commit gates: ok.
- Phase gate: exactly the ten planned files changed outside .planning since 58ad024; all phase commits authored and committed as Stephan Wald <stephan@morisoft.de>; every source commit has the Co-Authored-By trailer; no decision-ID comment in morimail/src/main; the three `cid:` pattern lines are equal; `shownId` and the svg entry are in Mail.java.

## Deviations from Plan

None - plan executed exactly as written. Notes, not deviations: the new strip() comment line is 114 characters (plan said about 100), and in this shell `grep` is a ugrep wrapper, so every gate used `/usr/bin/grep`.

## Known Stubs

None.

## Threat Flags

None. T-09-09 is mitigated by running the phase gate before the 05 records were written; T-09-10 by the summaries gate; T-09-11 and T-09-12 by the commit gates and the one-identity check. No raw invisible Unicode character was written into any file.

## Pending owner actions

- Push main when ready so Temurin 17 CI runs on the phase commits (only JDK 21 is installed here). Nothing was pushed, tagged or published.
- Git housekeeping the ROADMAP leaves to the owner: 07-PATTERNS.md and state.json are still untracked.

## Suggested STATE.md decision line

[Phase 09]: v0.2.0 tech debt closed: `<` ends a cid: id in all three copies, the echoed id shows control, separator and format characters as ?, webp/bmp/svg in both tables, pre-flight validates every mail (grouped by message), helper white-space rule documented; 05 and 07 dispositions settled (07 IN-02 skipped, no seam; 05 IN-03 deferred); 07-01 and 08-02 summaries parse

## Self-Check: PASSED

- Modified files exist and are committed: HtmlImages.java, 07-REVIEW-DISPOSITION.md, 07-01-SUMMARY.md, 08-02-SUMMARY.md, 05-REVIEW-DISPOSITION.md.
- Commits 3918d7a and 7620fae are ancestors of HEAD; `git rev-list --count de28a7e..HEAD` was 2 before this SUMMARY.
- All task gates passed; author and committer are Stephan Wald <stephan@morisoft.de> on both commits.
