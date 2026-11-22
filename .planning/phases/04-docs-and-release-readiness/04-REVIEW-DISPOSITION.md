---
phase: 04
review: 04-REVIEW.md
titles: json
findings:
  - id: WR-01
    severity: warning
    disposition: fixed
    title: "README states as fact a limit that architecture.md says is unconfirmed for Graph"
  - id: WR-02
    severity: warning
    disposition: fixed
    title: "entra-setup step 2 tells the reader to delete the key, then needs it"
  - id: WR-03
    severity: warning
    disposition: fixed
    title: "release.md asks for CI on the release commit, but the commit is made after CI could run and before the irreversible step"
  - id: IN-01
    severity: info
    disposition: fixed
    title: "Review and plan identifiers leak into public source and docs"
  - id: IN-02
    severity: info
    disposition: fixed
    title: "The two `clean()` methods sanitise differently, though both documents say the same thing"
  - id: IN-03
    severity: info
    disposition: fixed
    title: "Test names and coverage no longer match the new behaviour"
  - id: IN-04
    severity: info
    disposition: fixed
    title: "`.gitignore` covers the key formats in the guide but not the common neighbours"
open: 0
total: 7
recorded: 2026-11-22T19:45:28.599Z
---

# Phase 04: Code Review Disposition

| Finding | Severity | Disposition | Source |
|---------|----------|-------------|--------|
| WR-01 | warning | fixed | 04-REVIEW-FIX.md |
| WR-02 | warning | fixed | 04-REVIEW-FIX.md |
| WR-03 | warning | fixed | 04-REVIEW-FIX.md |
| IN-01 | info | fixed | 04-REVIEW-FIX.md |
| IN-02 | info | fixed | 04-REVIEW-FIX.md |
| IN-03 | info | fixed | 04-REVIEW-FIX.md |
| IN-04 | info | fixed | 04-REVIEW-FIX.md |

Dispositions: `open` (recorded, not yet triaged), `fixed`, `skipped`, `deferred`.
Set `deferred` by hand and put the reason in the Source cell; both are preserved. A `|` in the reason is kept as prose and escaped on the next run.
Re-running the gate keeps every row it can. A row the current review no longer reports is kept and its Source cell flagged, so a finding does not leave this record silently. ONE exception: when a finding id is REUSED by a different finding, the earlier decision cannot keep a row — the id is taken — and it is dropped. A RECORDED decision (anything but `open`) is named on the console when that happens; a row still at `open` is replaced silently, because `open` records no decision to lose.
