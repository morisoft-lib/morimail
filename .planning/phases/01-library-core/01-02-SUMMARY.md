---
phase: 01-library-core
plan: 02
subsystem: docs
tags: [readme, security-policy, claude-md, spec, hygiene, deviations]

requires:
  - phase: 01-library-core/01
    provides: Maven reactor and wrapper (every commit here is checked with ./mvnw -B -ntp verify), .review-diagnostics/ ignore rule
provides:
  - README stub with status, coordinates and the ERR-02 accepted-not-delivered / at-least-once note
  - SECURITY.md with GitHub private vulnerability reporting and an email fallback
  - Root CLAUDE.md holding spec section 11 verbatim
  - docs/spec.md, the tracked byte-identical binding spec (root copy removed)
  - docs/architecture.md with the five recorded deviations from the spec
  - Working tree that passes the five whole-tree hygiene gates
affects: [01-03, 01-04, 01-05, 01-06, phase-03, phase-04]

actuals:
  tokens: 12500
  tasks: 2
  commits: 2

plan_head_before: 64054dd8f2e2dfaa9a0066cd4182d53e62425616
plan_head_after: 5e679c271bd011e0fa4be6479f11cb6443185fcd
commits: 2

tech-stack:
  added: []
  patterns:
    - deviations from the spec are recorded in docs/architecture.md as they are made
    - hygiene gates run per address with exact allowed domain endings, and every gate fails on a git error

key-files:
  created:
    - README.md
    - SECURITY.md
    - CLAUDE.md
    - docs/spec.md
    - docs/architecture.md
  modified:
    - .planning/research/ARCHITECTURE.md
    - .planning/research/PITFALLS.md
    - .planning/phases/01-library-core/01-REVIEWS.md

key-decisions:
  - "The spec was copied byte for byte (sha256 ff96d3ce...e3b814 verified on the working copy and on the committed blob) and the root copy removed only afterwards; this ran in the main checkout, so the root copy is gone and plan 01-04's removal step has nothing left to do"
  - "CLAUDE.md intro adds one sentence that the spec wins where the two disagree, mirroring the project rule; the section 11 block itself is unchanged"

requirements-completed: [REPO-06, REPO-11, ERR-02, CORE-02]

duration: 5min
completed: 2026-11-21
status: complete
---

# Phase 1 Plan 02: Publish-safe repository Summary

**Public-facing docs (README, SECURITY.md), the binding spec moved to docs/spec.md, spec section 11 as root CLAUDE.md, five spec deviations recorded, and every whole-tree hygiene gate green.**

## Performance

- **Duration:** about 5 min
- **Tasks:** 2
- **Files:** 8 (5 created, 3 modified)

## Accomplishments

- `README.md`: what morimail is, pre-1.0 status, the `de.morisoft:morimail:0.1.0` coordinates (not yet published), the accepted-not-delivered and at-least-once note, a quick-start placeholder, links to LICENSE and SECURITY.md. No badges and no keystore sentence.
- `SECURITY.md`: private reporting through the GitHub advisories page, the maintainer address as fallback, no public issue, pre-1.0 support statement.
- `docs/spec.md`: tracked and byte-identical to the original; no `spec.md` remains at the root.
- `CLAUDE.md` at the root: spec section 11 verbatim (`diff -B` against the spec block is empty); `.claude/CLAUDE.md` untouched.
- `docs/architecture.md`: "Deviations from the spec" with the five rows (`freshAccessToken` against 7.3, `Map<String, ?>` against 5.3, unchecked configuration errors, JUnit Jupiter 6, reactor-only test-jar).
- Two research files now use repo-relative paths; `01-REVIEWS.md` no longer spells its three non-example sample addresses with an at-sign.

## Task Commits

1. **Task 1: README, SECURITY.md, scrub, hygiene gates** - `c40bcca` (docs)
2. **Task 2: spec move, CLAUDE.md, architecture deviations** - `5e679c2` (docs)

Both commits carry author and committer `Stephan Wald <stephan@morisoft.de>` and the Claude Code `Co-Authored-By` trailer (spec 12 default, owner confirms at the Phase 3 checkpoint).

## Verification

- Email gate (per address, exact allowed endings), home-path gate, non-zero GUID gate, PEM-header gate: each exits 1 from `git grep` (no match). Key and keystore listing is empty. The 15 negative and positive email controls all behave as expected (11 lookalike or non-example addresses flagged, 4 allowed forms pass).
- `./mvnw -B -ntp verify`: BUILD SUCCESS before each commit (no build input changed).
- Spec: `sha256sum` of `docs/spec.md` and of `git show HEAD:docs/spec.md` both equal the pinned value; `spec.md` is gone from the root; `docs/spec.md` is tracked.
- Identity: no commit after 8c9b082 has another author or committer; `git tag --list` is empty.
- All acceptance criteria of both tasks pass after one fix (see Deviations).

## Hygiene scope and history exceptions

The five gates detect exactly their five patterns in the working tree: a non-example email address, a non-zero GUID, an absolute home-directory path, a PEM private-key header, and a tracked key or keystore file. They do not prove that no other secret, internal host name or customer name exists (that rests on the spec section 11 rule and review), and they do not clean history.

Known history exceptions for the owner to decide on before publishing (no history was rewritten, D-02b):

- Commit `8c9b082`, the GitHub-created initial commit, has a different author address.
- The two research files carried absolute workstation paths in commits before this plan.
- `01-REVIEWS.md` carried the three sample addresses (two suffix lookalikes, one non-example) in commits before this plan.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] README ERR-02 sentences on one line**
- **Found during:** Task 1 acceptance check
- **Issue:** the criterion `grep -cE 'not that it was delivered|delivery is at-least-once' README.md >= 2` counts lines, and both sentences sat in one paragraph (count 1).
- **Fix:** split into two paragraphs; wording unchanged.
- **Files modified:** README.md
- **Commit:** `c40bcca` (fixed before committing)

### Process notes

- The plan commit ledger (protocol 0c) was not created before the first commit. It was written afterwards with the known base (HEAD at plan start, 64054dd), so `plan_head_before` and the measured commit count (2) are exact.
- Run in the main checkout, sequentially. The spec's root copy was therefore removed here, right after `docs/spec.md` was committed and its blob hash verified.

Otherwise: none - plan executed as written.

## Known Stubs

None. The README quick-start section is an intentional one-sentence placeholder that the plan requires (the quick start arrives with the first release in Phase 4).

## Human check pending

The owner enables GitHub private vulnerability reporting (Settings, Code security) so the SECURITY.md link opens the report form.

## Issues Encountered

None.

## Self-Check: PASSED

README.md, SECURITY.md, CLAUDE.md, docs/spec.md and docs/architecture.md exist; commits `c40bcca` and `5e679c2` are on main.
