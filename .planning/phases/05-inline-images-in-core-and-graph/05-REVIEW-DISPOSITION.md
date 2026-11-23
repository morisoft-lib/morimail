---
phase: 05
review: 05-REVIEW.md
titles: json
findings:
  - id: WR-01
    severity: warning
    disposition: open
    title: "Id read from rendered HTML (possibly recipient data) goes into the exception message without control-character cleaning"
  - id: WR-02
    severity: warning
    disposition: open
    title: "The `cid:` scan covers the whole rendered HTML, so prose, URLs and unquoted markup can fail a mail that has no inline image"
  - id: IN-01
    severity: info
    disposition: open
    title: "Content type for inline images depends on the platform for formats outside the built-in map"
  - id: IN-02
    severity: info
    disposition: open
    title: "`docs/spec.md` still states the MVP rule, and the source comments cite internal decision IDs"
  - id: IN-03
    severity: info
    disposition: open
    title: "Case-sensitive content ID uniqueness and the live status of the Graph properties"
open: 5
total: 5
recorded: 2026-11-23T06:22:55.927Z
---

# Phase 05: Code Review Disposition

| Finding | Severity | Disposition | Source |
|---------|----------|-------------|--------|
| WR-01 | warning | open | - |
| WR-02 | warning | open | - |
| IN-01 | info | open | - |
| IN-02 | info | open | - |
| IN-03 | info | open | - |

Dispositions: `open` (recorded, not yet triaged), `fixed`, `skipped`, `deferred`.
Set `deferred` by hand and put the reason in the Source cell; both are preserved. A `|` in the reason is kept as prose and escaped on the next run.
Re-running the gate keeps every row it can. A row the current review no longer reports is kept and its Source cell flagged, so a finding does not leave this record silently. ONE exception: when a finding id is REUSED by a different finding, the earlier decision cannot keep a row — the id is taken — and it is dropped. A RECORDED decision (anything but `open`) is named on the console when that happens; a row still at `open` is replaced silently, because `open` records no decision to lose.
