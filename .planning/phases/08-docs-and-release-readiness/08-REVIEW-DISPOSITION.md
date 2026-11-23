---
phase: 08
review: 08-REVIEW.md
titles: json
findings:
  - id: WR-01
    severity: warning
    disposition: open
    title: "release.md presents one-time first-release setup as part of every release"
  - id: IN-01
    severity: info
    disposition: open
    title: "Step 4 says \"Steps 5 to 8 use both variables\", which is not true"
  - id: IN-02
    severity: info
    disposition: open
    title: "README tells readers to depend on a version that is not on Maven Central"
  - id: IN-03
    severity: info
    disposition: open
    title: "spec section 10.3 still says \"without extension\" and \"not verified\""
  - id: IN-04
    severity: info
    disposition: open
    title: "SendCommandHelpTest cannot detect a missing dry-run description"
  - id: IN-05
    severity: info
    disposition: open
    title: "Dry-run rewrite writes the file name into an HTML attribute without escaping quotes"
open: 6
total: 6
recorded: 2026-11-23T20:40:05.540Z
---

# Phase 08: Code Review Disposition

| Finding | Severity | Disposition | Source |
|---------|----------|-------------|--------|
| WR-01 | warning | open | - |
| IN-01 | info | open | - |
| IN-02 | info | open | - |
| IN-03 | info | open | - |
| IN-04 | info | open | - |
| IN-05 | info | open | - |

Dispositions: `open` (recorded, not yet triaged), `fixed`, `skipped`, `deferred`.
Set `deferred` by hand and put the reason in the Source cell; both are preserved. A `|` in the reason is kept as prose and escaped on the next run.
Re-running the gate keeps every row it can. A row the current review no longer reports is kept and its Source cell flagged, so a finding does not leave this record silently. ONE exception: when a finding id is REUSED by a different finding, the earlier decision cannot keep a row — the id is taken — and it is dropped. A RECORDED decision (anything but `open`) is named on the console when that happens; a row still at `open` is replaced silently, because `open` records no decision to lose.
