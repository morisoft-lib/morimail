---
phase: 01
review: 01-REVIEW.md
titles: json
findings:
  - id: WR-01
    severity: warning
    disposition: fixed
    title: "Address validation accepts characters that break MIME headers (recipient injection)"
  - id: WR-02
    severity: warning
    disposition: fixed
    title: "Explicit `Attachment` filename and content type are not validated"
  - id: WR-03
    severity: warning
    disposition: fixed
    title: "`Mailer.send` and `Mailer.validate` throw a raw `NullPointerException` for a directly constructed `Mail` with a null account ID"
  - id: WR-04
    severity: warning
    disposition: fixed
    title: "CI job has no `timeout-minutes`"
  - id: WR-05
    severity: warning
    disposition: fixed
    title: "`fileDeletedAfterBuildStillSends` pins a contract that only the fixture satisfies"
  - id: IN-01
    severity: info
    disposition: skipped
    title: "Initial commit carries a different author identity"
  - id: IN-02
    severity: info
    disposition: fixed
    title: "`TransientMailException` constructor overloads are ambiguous with a literal `null`, and there is no cause+delay form"
  - id: IN-03
    severity: info
    disposition: fixed
    title: "Exception and fixture Javadoc carries a copy-pasted thread-safety paragraph"
  - id: IN-04
    severity: info
    disposition: fixed
    title: "`MailMessage` and `Attachment` default `toString()` print full bodies, recipients and paths"
  - id: IN-05
    severity: info
    disposition: fixed
    title: "Subject line-break handling is inconsistent between literal and rendered subjects"
  - id: IN-06
    severity: info
    disposition: fixed
    title: "Provider failures other than `ServiceConfigurationError` are not wrapped, and a null transport is not rejected"
  - id: IN-07
    severity: info
    disposition: fixed
    title: "Maven wrapper downloads the distribution without a checksum"
open: 0
total: 12
recorded: 2026-11-21T14:02:05.027Z
---

# Phase 01: Code Review Disposition

| Finding | Severity | Disposition | Source |
|---------|----------|-------------|--------|
| WR-01 | warning | fixed | 01-REVIEW-FIX.md (not in the current review) |
| WR-02 | warning | fixed | 01-REVIEW-FIX.md (not in the current review) |
| WR-03 | warning | fixed | 01-REVIEW-FIX.md (not in the current review) |
| WR-04 | warning | fixed | 01-REVIEW-FIX.md (not in the current review) |
| WR-05 | warning | fixed | 01-REVIEW-FIX.md (not in the current review) |
| IN-01 | info | skipped | 01-REVIEW-FIX.md (not in the current review) |
| IN-02 | info | fixed | 01-REVIEW-FIX.md (not in the current review) |
| IN-03 | info | fixed | 01-REVIEW-FIX.md (not in the current review) |
| IN-04 | info | fixed | 01-REVIEW-FIX.md (not in the current review) |
| IN-05 | info | fixed | 01-REVIEW-FIX.md (not in the current review) |
| IN-06 | info | fixed | 01-REVIEW-FIX.md (not in the current review) |
| IN-07 | info | fixed | 01-REVIEW-FIX.md (not in the current review) |

Dispositions: `open` (recorded, not yet triaged), `fixed`, `skipped`, `deferred`.
Set `deferred` by hand and put the reason in the Source cell; both are preserved. A `|` in the reason is kept as prose and escaped on the next run.
Re-running the gate keeps every row it can. A row the current review no longer reports is kept and its Source cell flagged, so a finding does not leave this record silently. ONE exception: when a finding id is REUSED by a different finding, the earlier decision cannot keep a row — the id is taken — and it is dropped. A RECORDED decision (anything but `open`) is named on the console when that happens; a row still at `open` is replaced silently, because `open` records no decision to lose.
