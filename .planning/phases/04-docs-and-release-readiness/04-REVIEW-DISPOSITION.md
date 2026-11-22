---
phase: 04
review: 04-REVIEW.md
titles: json
findings:
  - id: WR-01
    severity: warning
    disposition: open
    title: "Tracked debug note commits the maintainer's GPG key ID to the public repo"
  - id: IN-01
    severity: info
    disposition: fixed
    title: "The doc puts the \"cannot prompt\" behaviour on batch mode, but `--pinentry-mode error` is the real cause"
  - id: IN-02
    severity: info
    disposition: fixed
    title: "\"No pinentry means the agent cache was empty\" is incomplete when several secret keys exist"
  - id: IN-03
    severity: info
    disposition: fixed
    title: "The warm-up gives no success signal, so a failed warm-up is only discovered by the deploy"
open: 1
total: 4
recorded: 2026-11-22T21:02:50.121Z
---

# Phase 04: Code Review Disposition

| Finding | Severity | Disposition | Source |
|---------|----------|-------------|--------|
| WR-01 | warning | open | - |
| IN-01 | info | fixed | 04-REVIEW-FIX.md (not in the current review) |
| IN-02 | info | fixed | 04-REVIEW-FIX.md (not in the current review) |
| IN-03 | info | fixed | 04-REVIEW-FIX.md (not in the current review) |

Dispositions: `open` (recorded, not yet triaged), `fixed`, `skipped`, `deferred`.
Set `deferred` by hand and put the reason in the Source cell; both are preserved. A `|` in the reason is kept as prose and escaped on the next run.
Re-running the gate keeps every row it can. A row the current review no longer reports is kept and its Source cell flagged, so a finding does not leave this record silently. ONE exception: when a finding id is REUSED by a different finding, the earlier decision cannot keep a row — the id is taken — and it is dropped. A RECORDED decision (anything but `open`) is named on the console when that happens; a row still at `open` is replaced silently, because `open` records no decision to lose.
