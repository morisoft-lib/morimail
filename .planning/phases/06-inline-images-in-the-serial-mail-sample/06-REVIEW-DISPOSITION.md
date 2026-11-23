---
phase: 06
review: 06-REVIEW.md
titles: json
findings:
  - id: WR-01
    severity: warning
    disposition: fixed
    title: "Test creates non-ASCII file names, so it fails on hosts with a non-UTF-8 file-name encoding"
  - id: IN-01
    severity: info
    disposition: fixed
    title: "Core logic is copied into the sample and kept in step only by a test and a comment"
  - id: IN-02
    severity: info
    disposition: skipped
    title: "Two throw-away `Mail.build()` probes per image, with a forced `image/png`, are an indirect way to reuse the core's checks"
  - id: IN-03
    severity: info
    disposition: fixed
    title: "Over-long lines and a magic number"
  - id: IN-04
    severity: info
    disposition: fixed
    title: "Pre-flight scans the unrendered template for `cid:` references, so a placeholder inside a reference is reported as a missing image"
open: 0
total: 5
recorded: 2026-11-23T08:50:38.245Z
---

# Phase 06: Code Review Disposition

| Finding | Severity | Disposition | Source |
|---------|----------|-------------|--------|
| WR-01 | warning | fixed | 06-REVIEW-FIX.md |
| IN-01 | info | fixed | 06-REVIEW-FIX.md |
| IN-02 | info | skipped | 06-REVIEW-FIX.md |
| IN-03 | info | fixed | 06-REVIEW-FIX.md |
| IN-04 | info | fixed | 06-REVIEW-FIX.md |

Dispositions: `open` (recorded, not yet triaged), `fixed`, `skipped`, `deferred`.
Set `deferred` by hand and put the reason in the Source cell; both are preserved. A `|` in the reason is kept as prose and escaped on the next run.
Re-running the gate keeps every row it can. A row the current review no longer reports is kept and its Source cell flagged, so a finding does not leave this record silently. ONE exception: when a finding id is REUSED by a different finding, the earlier decision cannot keep a row — the id is taken — and it is dropped. A RECORDED decision (anything but `open`) is named on the console when that happens; a row still at `open` is replaced silently, because `open` records no decision to lose.
