---
phase: quick-261124-9og
plan: 01
subsystem: sample-serial, html-helper, planning
tags: [cid, content-id, audit, tech-debt]
requires: []
provides:
  - "CampaignTest#cidPatternAgreesWithTheCore runs the same 12 cases as HtmlImagesTest"
  - "HtmlImages content IDs compared without regard to case"
  - "06 review IN-01..03 dispositioned (open 0)"
affects: [HTML-04, INLN-04, INLN-02, CLI-16, HTML-02]
tech-stack:
  added: []
  patterns: []
key-files:
  created: []
  modified:
    - morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/CampaignTest.java
    - README.md
    - .planning/phases/06-inline-images-in-the-serial-mail-sample/06-REVIEW-DISPOSITION.md
    - morimail-html/src/main/java/de/morisoft/morimail/html/HtmlImages.java
    - morimail-html/src/test/java/de/morisoft/morimail/html/HtmlImagesTest.java
    - .planning/v0.2.0-MILESTONE-AUDIT.md
decisions:
  - "06 IN-01 fixed (README advice), IN-02 fixed (agreement cases), IN-03 deferred (macOS only)"
  - "HtmlImages taken set is a case-insensitive TreeSet; caller-added inline IDs documented in class Javadoc only, no new option"
metrics:
  duration: ~15 min
  completed: 2026-11-24
status: complete
commits: 3
plan_head_before: 0aa6d2b
plan_head_after: d150c1c
actuals:
  tasks: 3
  commits: 3
---

# Quick 261124-9og: v0.2.0 audit cleanup (phase 6 review, phase 7 helper IDs) Summary

Closed the four phase 06/07 tech-debt items of the v0.2.0 audit: the sample and helper cid: agreement tests now share 12 cases, helper content IDs are case-insensitive, and the 06 review infos are triaged.

## Commits

- 703889c test: CampaignTest gains the three helper-only cases; README line 169 advises ASCII-only inline file names (IN-01); 06-REVIEW-DISPOSITION.md open 0, total 5 (IN-01, IN-02 fixed, IN-03 deferred).
- 271f262 fix: `HtmlImages` `taken` is `new TreeSet<>(String.CASE_INSENSITIVE_ORDER)` (Logo.png then logo.png gives Logo, logo-2; `cid:LOGO` reserves `logo`); class Javadoc states the case rule and that the helper cannot see IDs added via `Mail.Builder.inline`. RED run confirmed before the change.
- d150c1c docs: audit file moves the 06/07 tech_debt blocks to `resolved_after_audit`, 06 row reads `0 open (IN-03 deferred)`, body items 3-5 marked resolved; status stays `tech_debt`.

Each task ended with `./mvnw -B -o -q verify` green; Task 2 also passed the offline `-Prelease` scoped build with 0 WARNING lines.

## Deviations from Plan

**1. [Rule 1 - Bug] Edit tool wrote a literal e-acute instead of the `é` escape**
- **Found during:** Task 1 (agreement-case diff against the helper list was not equal)
- **Fix:** replaced it with the Java escape `é`, re-ran CampaignTest and the full verify, and amended the unpushed Task 1 commit (703889c) so the commit holds the escaped form.
- **Files modified:** CampaignTest.java

Otherwise none. The plan's gate commands (long awk/grep chains) were not run verbatim; I ran the equivalent checks directly (case-list diff, line counts, numstat, doclint build, gsd-tools frontmatter reads, task-id count of 5).

## Known Stubs

None.

## Threat Flags

None.

## Self-Check: PASSED

Three commits (703889c, 271f262, d150c1c) present on main, authored as Stephan Wald; the three untracked files stay untracked; nothing pushed or tagged.
