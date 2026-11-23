---
phase: 06
review: 06-REVIEW.md
titles: json
findings:
  - id: IN-01
    severity: info
    disposition: open
    title: "Pre-flight accepts content IDs that an HTML parser or mail client may rewrite"
  - id: IN-02
    severity: info
    disposition: open
    title: "The copied `CID_REFERENCE` is kept in step by a comment and eight sample inputs"
  - id: IN-03
    severity: info
    disposition: open
    title: "`inlineSrcIsPercentEncodedAndHtmlSafe` assumes the host keeps `ü` as written"
  - id: WR-01
    severity: warning
    disposition: fixed
    title: "Test creates non-ASCII file names, so it fails on hosts with a non-UTF-8 file-name encoding"
  - id: IN-04
    severity: info
    disposition: fixed
    title: "Pre-flight scans the unrendered template for `cid:` references, so a placeholder inside a reference is reported as a missing image"
open: 3
total: 5
recorded: 2026-11-23T09:55:58.576Z
---

# Phase 06: Code Review Disposition

| Finding | Severity | Disposition | Source |
|---------|----------|-------------|--------|
| IN-01 | info | open | - |
| IN-02 | info | open | - |
| IN-03 | info | open | - |
| WR-01 | warning | fixed | 06-REVIEW-FIX.md (not in the current review) |
| IN-04 | info | fixed | 06-REVIEW-FIX.md (not in the current review) |

Dispositions: `open` (recorded, not yet triaged), `fixed`, `skipped`, `deferred`.
Set `deferred` by hand and put the reason in the Source cell; both are preserved. A `|` in the reason is kept as prose and escaped on the next run.
Re-running the gate keeps every row it can. A row the current review no longer reports is kept and its Source cell flagged, so a finding does not leave this record silently. ONE exception: when a finding id is REUSED by a different finding, the earlier decision cannot keep a row — the id is taken — and it is dropped. A RECORDED decision (anything but `open`) is named on the console when that happens; a row still at `open` is replaced silently, because `open` records no decision to lose.
