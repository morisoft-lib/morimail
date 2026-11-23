---
phase: 08
review: 08-REVIEW.md
titles: json
findings:
  - id: IN-01
    severity: info
    disposition: open
    title: "README step 8 only renames the version, but the Coordinates sentence is release-specific"
  - id: IN-02
    severity: info
    disposition: open
    title: "Step 8 `sed -i` has no macOS note"
  - id: IN-03
    severity: info
    disposition: open
    title: "\"Steps 5 to 8 use `VERSION`\" overstates the shell dependency"
  - id: WR-01
    severity: warning
    disposition: fixed
    title: "SNAPSHOT guard fails open when grep errors"
  - id: WR-02
    severity: warning
    disposition: fixed
    title: "Procedure defaults and wording still target the already-released 0.2.0"
  - id: IN-04
    severity: info
    disposition: fixed
    title: "SendCommandHelpTest cannot detect a missing dry-run description"
  - id: IN-05
    severity: info
    disposition: fixed
    title: "Dry-run rewrite writes the file name into an HTML attribute without escaping quotes"
open: 3
total: 7
recorded: 2026-11-23T21:46:17.079Z
---

# Phase 08: Code Review Disposition

| Finding | Severity | Disposition | Source |
|---------|----------|-------------|--------|
| IN-01 | info | open | - |
| IN-02 | info | open | - |
| IN-03 | info | open | - |
| WR-01 | warning | fixed | 08-REVIEW-FIX.md (not in the current review) |
| WR-02 | warning | fixed | 08-REVIEW-FIX.md (not in the current review) |
| IN-04 | info | fixed | 08-REVIEW-FIX.md (not in the current review) |
| IN-05 | info | fixed | 08-REVIEW-FIX.md (not in the current review) |

Dispositions: `open` (recorded, not yet triaged), `fixed`, `skipped`, `deferred`.
Set `deferred` by hand and put the reason in the Source cell; both are preserved. A `|` in the reason is kept as prose and escaped on the next run.
Re-running the gate keeps every row it can. A row the current review no longer reports is kept and its Source cell flagged, so a finding does not leave this record silently. ONE exception: when a finding id is REUSED by a different finding, the earlier decision cannot keep a row — the id is taken — and it is dropped. A RECORDED decision (anything but `open`) is named on the console when that happens; a row still at `open` is replaced silently, because `open` records no decision to lose.
