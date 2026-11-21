---
phase: 01
review: 01-REVIEW.md
titles: json
findings:
  - id: WR-01
    severity: warning
    disposition: open
    title: "Address validation accepts characters that break MIME headers (recipient injection)"
  - id: WR-02
    severity: warning
    disposition: open
    title: "Explicit `Attachment` filename and content type are not validated"
  - id: WR-03
    severity: warning
    disposition: open
    title: "`Mailer.send` and `Mailer.validate` throw a raw `NullPointerException` for a directly constructed `Mail` with a null account ID"
  - id: WR-04
    severity: warning
    disposition: open
    title: "CI job has no `timeout-minutes`"
  - id: WR-05
    severity: warning
    disposition: open
    title: "`fileDeletedAfterBuildStillSends` pins a contract that only the fixture satisfies"
  - id: IN-01
    severity: info
    disposition: open
    title: "Initial commit carries a different author identity"
  - id: IN-02
    severity: info
    disposition: open
    title: "`TransientMailException` constructor overloads are ambiguous with a literal `null`, and there is no cause+delay form"
  - id: IN-03
    severity: info
    disposition: open
    title: "Exception and fixture Javadoc carries a copy-pasted thread-safety paragraph"
  - id: IN-04
    severity: info
    disposition: open
    title: "`MailMessage` and `Attachment` default `toString()` print full bodies, recipients and paths"
  - id: IN-05
    severity: info
    disposition: open
    title: "Subject line-break handling is inconsistent between literal and rendered subjects"
  - id: IN-06
    severity: info
    disposition: open
    title: "Provider failures other than `ServiceConfigurationError` are not wrapped, and a null transport is not rejected"
  - id: IN-07
    severity: info
    disposition: open
    title: "Maven wrapper downloads the distribution without a checksum"
open: 12
total: 12
recorded: 2026-11-21T13:26:07.406Z
---

# Phase 01: Code Review Disposition

| Finding | Severity | Disposition | Source |
|---------|----------|-------------|--------|
| WR-01 | warning | open | - |
| WR-02 | warning | open | - |
| WR-03 | warning | open | - |
| WR-04 | warning | open | - |
| WR-05 | warning | open | - |
| IN-01 | info | open | - |
| IN-02 | info | open | - |
| IN-03 | info | open | - |
| IN-04 | info | open | - |
| IN-05 | info | open | - |
| IN-06 | info | open | - |
| IN-07 | info | open | - |

Dispositions: `open` (recorded, not yet triaged), `fixed`, `skipped`, `deferred`.
Set `deferred` by hand and put the reason in the Source cell; both are preserved. A `|` in the reason is kept as prose and escaped on the next run.
Re-running the gate keeps every row it can. A row the current review no longer reports is kept and its Source cell flagged, so a finding does not leave this record silently. ONE exception: when a finding id is REUSED by a different finding, the earlier decision cannot keep a row — the id is taken — and it is dropped. A RECORDED decision (anything but `open`) is named on the console when that happens; a row still at `open` is replaced silently, because `open` records no decision to lose.
