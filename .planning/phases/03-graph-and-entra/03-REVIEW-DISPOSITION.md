---
phase: 03
review: 03-REVIEW.md
titles: json
findings:
  - id: WR-01
    severity: warning
    disposition: fixed
    title: "Cutting at 1000 raw characters before whitespace collapse can leave a partial mail address in the message"
  - id: IN-01
    severity: info
    disposition: skipped
    title: "The \"quickly\" regression tests have a thin time margin"
  - id: IN-02
    severity: info
    disposition: fixed
    title: "The magic number 1000 is duplicated in two files, and Graph repeats 300 as a literal"
  - id: WR-02
    severity: warning
    disposition: fixed
    title: "Server-echoed text goes into exception messages, journal and stdout, but the docs promise \"never addresses\""
  - id: WR-03
    severity: warning
    disposition: fixed
    title: "Plain `http` is accepted for the token authority and the Graph base URL, so the client secret and bearer token can travel in cleartext"
  - id: IN-03
    severity: info
    disposition: fixed
    title: "The request-size estimate is looser than its Javadoc suggests, and the MB/MiB limits are unverified"
  - id: IN-04
    severity: info
    disposition: fixed
    title: "Under a token-endpoint outage, waiting threads are served one after another, each with its own timeout"
  - id: IN-05
    severity: info
    disposition: fixed
    title: "Rule 0: tolerance code for inputs the documentation says do not occur"
  - id: IN-06
    severity: info
    disposition: fixed
    title: "Test gaps and one weak assertion"
open: 0
total: 9
recorded: 2026-11-22T16:22:10.117Z
---

# Phase 03: Code Review Disposition

| Finding | Severity | Disposition | Source |
|---------|----------|-------------|--------|
| WR-01 | warning | fixed | 03-REVIEW-FIX.md |
| IN-01 | info | skipped | 03-REVIEW-FIX.md |
| IN-02 | info | fixed | 03-REVIEW-FIX.md |
| WR-02 | warning | fixed | 03-REVIEW-FIX.md (not in the current review) |
| WR-03 | warning | fixed | 03-REVIEW-FIX.md (not in the current review) |
| IN-03 | info | fixed | 03-REVIEW-FIX.md (not in the current review) |
| IN-04 | info | fixed | 03-REVIEW-FIX.md (not in the current review) |
| IN-05 | info | fixed | 03-REVIEW-FIX.md (not in the current review) |
| IN-06 | info | fixed | 03-REVIEW-FIX.md (not in the current review) |

Dispositions: `open` (recorded, not yet triaged), `fixed`, `skipped`, `deferred`.
Set `deferred` by hand and put the reason in the Source cell; both are preserved. A `|` in the reason is kept as prose and escaped on the next run.
Re-running the gate keeps every row it can. A row the current review no longer reports is kept and its Source cell flagged, so a finding does not leave this record silently. ONE exception: when a finding id is REUSED by a different finding, the earlier decision cannot keep a row — the id is taken — and it is dropped. A RECORDED decision (anything but `open`) is named on the console when that happens; a row still at `open` is replaced silently, because `open` records no decision to lose.
