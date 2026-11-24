---
phase: 09
review: 09-REVIEW.md
titles: json
findings:
  - id: WR-01
    severity: warning
    disposition: open
    title: "Pre-flight does not group the 4 MiB request-size rejection, so it can print one line per row (still open, tracked in 09-REVIEW-DISPOSITION.md)"
  - id: IN-01
    severity: info
    disposition: open
    title: "The helper's case-insensitive ID rule is not recorded in docs/architecture.md and departs from the \"exact comparison\" contract"
  - id: IN-02
    severity: info
    disposition: open
    title: "Dry-run can overwrite one inline image with another on a case-insensitive file system"
  - id: IN-03
    severity: info
    disposition: open
    title: "The `CID_REFERENCE` comment in `HtmlImages` still says \"whitespace\""
open: 4
total: 4
recorded: 2026-11-24T09:18:45.442Z
---

# Phase 09: Code Review Disposition

| Finding | Severity | Disposition | Source |
|---------|----------|-------------|--------|
| WR-01 | warning | open | - |
| IN-01 | info | open | - |
| IN-02 | info | open | - |
| IN-03 | info | open | - |

Dispositions: `open` (recorded, not yet triaged), `fixed`, `skipped`, `deferred`.
Set `deferred` by hand and put the reason in the Source cell; both are preserved. A `|` in the reason is kept as prose and escaped on the next run.
Re-running the gate keeps every row it can. A row the current review no longer reports is kept and its Source cell flagged, so a finding does not leave this record silently. ONE exception: when a finding id is REUSED by a different finding, the earlier decision cannot keep a row — the id is taken — and it is dropped. A RECORDED decision (anything but `open`) is named on the console when that happens; a row still at `open` is replaced silently, because `open` records no decision to lose.
