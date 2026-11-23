---
phase: 07
review: 07-REVIEW.md
titles: json
findings:
  - id: IN-01
    severity: info
    disposition: deferred
    title: "`strip()` trimming is undocumented and not the HTML rule"
  - id: IN-02
    severity: info
    disposition: deferred
    title: "The Error-rethrow path has no test"
  - id: IN-03
    severity: info
    disposition: fixed
    title: "The reader thread catches `Throwable` and reports it as a retryable failure"
  - id: WR-01
    severity: warning
    disposition: fixed
    title: "Blocking body reads run on the shared common pool, so concurrent embeds can produce false \"download timed out\""
  - id: WR-02
    severity: warning
    disposition: fixed
    title: "The static `HttpClient` is built when the class loads, so local-only users pay for it and can fail at class init"
  - id: WR-03
    severity: warning
    disposition: fixed
    title: "Wall-clock assertions in the download tests are likely to flake on slow CI runners"
  - id: IN-04
    severity: info
    disposition: fixed
    title: "The `CID_REFERENCE` pattern now has three copies kept in sync by comment only"
  - id: IN-05
    severity: info
    disposition: fixed
    title: "The helper's behaviour decisions are not recorded in `docs/architecture.md`"
open: 2
total: 8
recorded: 2026-11-23T17:25:03.319Z
---

# Phase 07: Code Review Disposition

| Finding | Severity | Disposition | Source |
|---------|----------|-------------|--------|
| IN-01 | info | deferred | owner accepted as-is in 07-UAT test 4, 2026-11-23 |
| IN-02 | info | deferred | owner accepted as-is in 07-UAT test 4, 2026-11-23 |
| IN-03 | info | fixed | 07-REVIEW-FIX.md (not in the current review) |
| WR-01 | warning | fixed | 07-REVIEW-FIX.md (not in the current review) |
| WR-02 | warning | fixed | 07-REVIEW-FIX.md (not in the current review) |
| WR-03 | warning | fixed | 07-REVIEW-FIX.md (not in the current review) |
| IN-04 | info | fixed | 07-REVIEW-FIX.md (not in the current review) |
| IN-05 | info | fixed | 07-REVIEW-FIX.md (not in the current review) |

Dispositions: `open` (recorded, not yet triaged), `fixed`, `skipped`, `deferred`.
Set `deferred` by hand and put the reason in the Source cell; both are preserved. A `|` in the reason is kept as prose and escaped on the next run.
Re-running the gate keeps every row it can. A row the current review no longer reports is kept and its Source cell flagged, so a finding does not leave this record silently. ONE exception: when a finding id is REUSED by a different finding, the earlier decision cannot keep a row — the id is taken — and it is dropped. A RECORDED decision (anything but `open`) is named on the console when that happens; a row still at `open` is replaced silently, because `open` records no decision to lose.
