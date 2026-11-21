---
phase: 01-library-core
plan: 07
subsystem: infra
tags: [javadoc, doclint, jdk17, record, ci, gap-closure]

requires:
  - phase: 01-library-core/05
    provides: MailMessage record with its compact constructor
  - phase: 01-library-core/06
    provides: Phase 1 test suite and the CI workflow the three commands come from
provides:
  - MailMessage compact constructor Javadoc with all nine @param tags plus the @throws NullPointerException, passing JDK 17 doclint (pending Temurin 17 CI confirmation)
affects: [phase-01-verification, phase-02]

actuals:
  tokens: 160
  tasks: 1
  commits: 1

plan_head_before: 88cff7c2bae6cfc2177215fa338611cf9a1f6b6c
plan_head_after: 510985459aa611b0939653eae93b6ee5a9e54051
commits: 1

tech-stack:
  added: []
  patterns:
    - a documented explicit compact constructor of a record repeats the record header's @param tags word for word, because JDK 17 javadoc does not inherit them (JDK 18 and later do)

key-files:
  created: []
  modified:
    - morimail/src/main/java/de/morisoft/morimail/MailMessage.java

key-decisions:
  - "Fixed G-01-1 by adding the nine @param tags to the compact constructor comment, not by dropping the comment: a constructor without a doc comment could draw a doclint 'no comment' warning on JDK 17, which cannot be checked on this JDK 21 machine"
  - "Gate left untouched: doclint all and failOnWarnings true stay in the release profile; no POM, CI workflow, toolchain or dependency change"

requirements-completed: [REPO-07, REPO-09, REPO-10]

coverage:
  - id: D1
    description: "The MailMessage compact constructor Javadoc carries one @param per record component, identical to the record header, and keeps its @throws NullPointerException"
    requirement: "REPO-10"
    verification:
      - kind: other
        ref: "constructor block gate and header-equality gate from 01-07-PLAN.md task 1 verify (both exit 0)"
        status: pass
    human_judgment: false
  - id: D2
    description: "The three CI commands pass on the local JDK 21"
    requirement: "REPO-07"
    verification:
      - kind: other
        ref: "./mvnw -B -ntp verify; ./mvnw -B -ntp -Prelease clean verify -Dgpg.skip -pl morimail -am; ./mvnw -B -ntp -o verify (all BUILD SUCCESS)"
        status: pass
    human_judgment: false
  - id: D3
    description: "G-01-1 is closed: the JDK 17 javadoc run in CI step 2 reports no 'no @param' warning for MailMessage.java"
    requirement: "REPO-09"
    verification: []
    human_judgment: true
    rationale: "JDK 21 javadoc inherits the record header tags and cannot reproduce the JDK 17 warning; only the Temurin 17 CI run after the owner pushes proves the fix"

duration: 2min
completed: 2026-11-21
status: complete
---

# Phase 1 Plan 07: JDK 17 doclint fix for the MailMessage compact constructor Summary

**Nine @param tags added to the MailMessage compact constructor Javadoc so JDK 17 doclint (all, failOnWarnings) no longer reports "no @param"; comment-only change, three CI commands green on JDK 21, Temurin 17 confirmation handed to the owner**

## Performance

- **Duration:** 2 min
- **Started:** 2026-11-21T13:58:46Z
- **Completed:** 2026-11-21T13:59:50Z
- **Tasks:** 1
- **Files modified:** 1

## Accomplishments

- The Javadoc comment above `public MailMessage {` now has `@param` tags for from, to, cc, bcc, replyTo, subject, html, text and attachments, in component order and with the record header's exact text. The first sentence and the `@throws NullPointerException if one of the four lists is {@code null}` line are unchanged.
- The fix commit holds `morimail/src/main/java/de/morisoft/morimail/MailMessage.java` only and changes nine comment lines, no code line. `pom.xml`, `morimail/pom.xml`, `morimail-sample-serial/pom.xml` and `.github/workflows/ci.yml` are untouched; the release profile still has `<doclint>all</doclint>` and `<failOnWarnings>true</failOnWarnings>`.

## Choice between the two fixes in G-01-1

The gap listed two options: add the nine `@param` tags, or drop the constructor comment. The first was chosen. An explicitly declared compact constructor with no doc comment could be reported by JDK 17 doclint as "no comment" (the `missing` group is part of `all`), and that cannot be checked on this machine. Documented parameters are accepted by every doclint version, and the `@throws` stays on the constructor that throws.

## Commands run on JDK 21 (CI order)

| Step | Command | Result |
|------|---------|--------|
| 1 | `./mvnw -B -ntp verify` | BUILD SUCCESS |
| 2 | `./mvnw -B -ntp -Prelease clean verify -Dgpg.skip -pl morimail -am` (D-20) | BUILD SUCCESS, no Javadoc warning |
| 3 | `./mvnw -B -ntp -o verify` (D-18) | BUILD SUCCESS |

Plan gates: constructor block gate 0, header-equality gate 0, commit-content gate 0 (one file, comment lines only), release-gate guard 0 (doclint all and failOnWarnings true present, no POM or workflow change), identity and trailer gate 0 (author and committer are the maintainer, Claude Co-Authored-By trailer present, commit is not an ancestor of origin/main). `grep -cE '^ +\* @param '` on the file gives 18 (nine in the record header, nine on the constructor). The whole-tree email gate over `morimail` exits 1 (no foreign address). Author and committer checks over `8c9b082..HEAD` are empty.

## Task Commits

1. **Task 1: document the MailMessage compact constructor parameters** - `5109854` (fix)

**Plan metadata:** recorded in the docs commit that follows (docs: complete plan).

## Files Created/Modified

- `morimail/src/main/java/de/morisoft/morimail/MailMessage.java` - nine `@param` lines added to the compact constructor Javadoc (comment lines only)

## Decisions Made

- Add the tags rather than remove the comment (see above).
- No change to the gate: G-01-1 is closed in the source comment, never by relaxing doclint, failOnWarnings, the CI matrix or the JDK version.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None.

## Pending owner action

The executor does not push. After the owner pushes the fix commit, open the GitHub Actions run of `.github/workflows/ci.yml` for it on Temurin 17 and confirm that all three steps are green (`verify`, `-Prelease clean verify -Dgpg.skip -pl morimail -am`, `-o verify`) and that the step 2 log has no "no @param" warning for `MailMessage.java` and no "Project contains Javadoc Warnings". **G-01-1 closes only after that run**, because JDK 21 javadoc cannot reproduce the JDK 17 warning; the local builds prove only that nothing else broke. Then re-run verify-work for phase 01 so test 1 in `.planning/phases/01-library-core/01-UAT.md` is recorded as passed. 01-UAT.md was not edited by this plan.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Phase 1 gap closure is in place locally; phase verification can be re-run once the Temurin 17 CI run is green.
- No blockers for Phase 2.

## Self-Check: PASSED

- `morimail/src/main/java/de/morisoft/morimail/MailMessage.java` exists and carries the nine constructor `@param` tags.
- Fix commit `5109854` is an ancestor of HEAD.

---
*Phase: 01-library-core*
*Completed: 2026-11-21*
