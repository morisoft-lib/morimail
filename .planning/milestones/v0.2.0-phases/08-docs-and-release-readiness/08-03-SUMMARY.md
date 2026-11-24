---
phase: 08-docs-and-release-readiness
plan: 03
subsystem: release
tags: [release, maven-central, snapshot-guard, docs, gap-closure]
status: complete
gap_closure: true
gap_ids: [G-08-2]

requires:
  - phase: 08-docs-and-release-readiness
    provides: 08-02 docs/release.md, the version-neutral release procedure whose step 5 this plan corrects
provides:
  - docs/release.md step 5 with both deploy commands chained behind a SNAPSHOT guard over the three published POMs
  - the SNAPSHOT note replaced by the plugin's real behaviour (snapshots repository, only -DskipUpload stops the upload)
affects: [next owner release (0.3.0)]

actuals:
  tokens: 400
  tasks: 2
  commits: 1
plan_head_before: fb566029ff709a87eec0f2bcd325a7142bf6c36f
plan_head_after: 2ad672c

tech-stack:
  added: []
  patterns: ["a trailing && on the guard line chains it to the next line of the same fenced block, so a pasted deploy cannot start past it"]

key-files:
  created: []
  modified: [docs/release.md]

key-decisions:
  - "release.md step 5 chains both deploy commands behind `! grep -- -SNAPSHOT` over pom.xml, morimail/pom.xml and morimail-html/pom.xml; a SNAPSHOT deploy goes to Central's snapshots repository unless -DskipUpload is set"

requirements-completed: [DOCS-06]

coverage:
  - id: D1
    description: "Each step 5 deploy command is chained behind the SNAPSHOT guard in its own fenced block; on POM copies a SNAPSHOT in any of the three published POMs stops it and a release version lets it start"
    requirement: DOCS-06
    verification:
      - kind: other
        ref: "Task 1 guard gate (interactive bash, POM copies, deploy replaced by echo): exit 0"
        status: pass
      - kind: other
        ref: "Task 1 content gate (whole-file comparison with 8df5f6a, both deploy lines, -pl set, no date): exit 0"
        status: pass
    human_judgment: false
  - id: D2
    description: "The SNAPSHOT bullet states the plugin's real behaviour and tells the owner to finish step 4 first"
    requirement: DOCS-06
    verification:
      - kind: other
        ref: "Task 1 content gate (required phrases present, old note gone): exit 0"
        status: pass
    human_judgment: true
    rationale: "Whether the wording is clear to the owner is a reading judgment; the gates only prove the phrases are present"
  - id: D3
    description: "UAT test 2 (G-08-2 missing 3) closed by the owner's report and repository evidence, not by a re-run"
    requirement: DOCS-06
    verification: []
    human_judgment: true
    rationale: "The 54-entry bundle check and the Maven Central publish are owner-reported only and cannot be checked from the repository"

duration: 12min
completed: 2026-11-23
---

# Phase 8 Plan 03: stop step 5 deploys at a SNAPSHOT version (gap G-08-2) Summary

**docs/release.md step 5 now chains both deploy commands behind `! grep -- -SNAPSHOT pom.xml morimail/pom.xml morimail-html/pom.xml &&`, and its SNAPSHOT note states the plugin's real behaviour (snapshots repository, only `-DskipUpload=true` stops the upload).**

## Performance

- **Duration:** 12 min
- **Tasks:** 2
- **Files modified:** 1 (docs/release.md, 4 insertions, 2 deletions)

## Accomplishments

- Each of the two step 5 deploy commands sits in its own fenced block whose first line is the guard, joined to the deploy line with a trailing `&&`. Both deploy lines are byte-identical to before (D-13), and the warm-up blocks are untouched, so no pasted line follows the gpg line (04-05).
- The paragraph before the first block tells the owner to copy both lines together and to finish step 4 first.
- The first bullet under "Two notes on that check:" now says: at a SNAPSHOT version the plugin builds no bundle, deploys to Central's snapshots repository, only `-DskipUpload=true` stops that upload, and the real deploy has no such flag. The offline bullet stays.
- The guard names no version (D-12), so the lines naming 0.2.0 stay at 2. release.md holds no date and no 0.2.0-specific pre-check (D-15).

## Task Commits

1. **Task 1: guard both step 5 deploys and state the plugin's SNAPSHOT behaviour** - `2ad672c` (docs). The edit and its gates were run first; the single docs/release.md-only commit that Task 2 specifies carries it.
2. **Task 2: commit the fix and prove the gate** - same commit `2ad672c`. No second commit was needed; the verify and commit gates ran on it.

**Plan metadata:** the docs(08-03) completion commit that holds this SUMMARY, STATE.md and ROADMAP.md.

## Files Created/Modified

- `docs/release.md` - step 5 only: two guard lines, the check paragraph, the SNAPSHOT bullet.

## Gate results

- Task 1 content gate: `content gate: ok` (exit 0). Every line outside the two guard lines, the check paragraph and the SNAPSHOT bullet matches docs/release.md at 8df5f6a.
- Task 1 guard gate: `guard gate: a release version starts the deploy, a SNAPSHOT in any of the three POMs stops it` (exit 0). Run in an interactive bash (history expansion on) on copies of the three POMs, with the deploy replaced by an echo.
- Task 2 verify gate: `./mvnw -B -ntp verify` on a git-archive copy of the fix commit, BUILD SUCCESS. Tests run: 366 (morimail), 71 and 164, 0 failures, 0 errors, 0 skipped.
- Task 2 commit gate: `commit gate: ok` (exit 0). The commit touches docs/release.md only, sits on top of 8df5f6a, is authored and committed as Stephan Wald <stephan@morisoft.de> with the Co-Authored-By trailer, is not on origin/main, and its added lines hold no address, long hex string or home path.

## Decisions Made

- Guard chained with a trailing `&&` inside the deploy's own fenced block, as the plan chose. A separate check line cannot stop a deploy pasted after it, and `exit` would close the owner's terminal.
- The guard greps the three published POMs only. The sample POM is not deployed.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None. The working tree had an orchestrator-modified `.planning/STATE.md` and two untracked orchestrator files (`.planning/state.json`, `.planning/phases/07-html-image-helper-module/07-PATTERNS.md`). The fix commit left them out; the two untracked files were never staged.

## Release safety

No deploy, `-Prelease` build, gpg command, push or tag was run. Maven ran only in a git-archive copy in a temporary directory, removed afterwards. The working tree's `target/` and `~/.m2/repository/de/morisoft` were not touched, and 08-UAT.md was not edited.

## Assumption A1 (untested)

In an interactive bash, `!` followed by a space is not a history expansion, and the guard gate proves it with `bash -i`. zsh is not installed here, so zsh was not tested. The zsh manual gives the same rule for `!` followed by a blank. Step 4 mentions macOS `sed -i ''`, so an owner on macOS zsh is possible; the owner should watch the first use.

## UAT test 2

- **Owner-reported on 2026-11-23:** the owner wrote "release worked now" after running the release.
- **Repository evidence:** the tag v0.2.0 on origin peels to the release commit 2c4ea80. `git ls-remote --tags origin | grep -F 'refs/tags/v0.2.0^{}'`, run at execution time, printed `2c4ea801d629de97a70f2741e771c152ae457a06	refs/tags/v0.2.0^{}`. The owner's bump commit is 8df5f6a (0.3.0-SNAPSHOT).
- **Not checkable here:** the 54-entry bundle check and the publish on Maven Central are owner-reported only. This SUMMARY claims nothing beyond the report and this evidence.
- The guard takes effect from the next release. Step 5 was not re-run for 0.2.0.

## Pending owner actions

- Push the fix commit if wanted, and let CI run on it.
- The guard is first used in the next release.

## STATE.md decision line

[Phase 08]: 08-03 (G-08-2): release.md step 5 chains both deploy commands behind `! grep -- -SNAPSHOT` over the three published POMs; a SNAPSHOT deploy goes to Central's snapshots repository unless -DskipUpload; docs only; the owner reported the 0.2.0 release as working on 2026-11-23 (tag v0.2.0 on 2c4ea80), the guard applies from the next release

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Phase 8 has all three plans executed. G-08-2 is ready for `/gsd-verify-work` to reconcile from `gap_ids`.

## Known Stubs

None.

## Self-Check: PASSED

- docs/release.md exists and holds both guard lines (content gate ok).
- Commit 2ad672c is an ancestor of HEAD.
- commit gate: ok, verify BUILD SUCCESS on the archive copy.

---
*Phase: 08-docs-and-release-readiness*
*Completed: 2026-11-23*
