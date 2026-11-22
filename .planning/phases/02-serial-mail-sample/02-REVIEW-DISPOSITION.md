---
phase: 02
review: 02-REVIEW.md
titles: json
findings:
  - id: WR-01
    severity: warning
    disposition: fixed
    title: "`account.properties` with a UTF-8 BOM reports a missing `account.id` key"
  - id: WR-02
    severity: warning
    disposition: fixed
    title: "Every regular file in `attachments/` is attached, including hidden and OS junk files"
  - id: WR-03
    severity: warning
    disposition: fixed
    title: "Campaign-wide mail defects produce one misattributed problem per recipient"
  - id: IN-01
    severity: info
    disposition: fixed
    title: "Exit codes are documented incompletely"
  - id: IN-02
    severity: info
    disposition: fixed
    title: "An unexpected `RuntimeException` ends the run with a stack trace, no counts and no exit code from the table"
  - id: IN-03
    severity: info
    disposition: fixed
    title: "`describe` prints \"null\" for exceptions without a message"
  - id: IN-04
    severity: info
    disposition: fixed
    title: "Placeholder regex is copied three times and the `!` is lost in the missing-column message"
  - id: IN-05
    severity: info
    disposition: fixed
    title: "Literal invisible U+FEFF character in source"
  - id: IN-06
    severity: info
    disposition: fixed
    title: "Journal has no exclusive lock, and `jackson-databind` is used without being declared"
open: 0
total: 9
recorded: 2026-11-22T06:12:39.797Z
---

# Phase 02: Code Review Disposition

| Finding | Severity | Disposition | Source |
|---------|----------|-------------|--------|
| WR-01 | warning | fixed | 02-REVIEW-FIX.md |
| WR-02 | warning | fixed | 02-REVIEW-FIX.md |
| WR-03 | warning | fixed | 02-REVIEW-FIX.md |
| IN-01 | info | fixed | 02-REVIEW-FIX.md |
| IN-02 | info | fixed | 02-REVIEW-FIX.md |
| IN-03 | info | fixed | 02-REVIEW-FIX.md |
| IN-04 | info | fixed | 02-REVIEW-FIX.md |
| IN-05 | info | fixed | 02-REVIEW-FIX.md |
| IN-06 | info | fixed | 02-REVIEW-FIX.md |

Dispositions: `open` (recorded, not yet triaged), `fixed`, `skipped`, `deferred`.
Set `deferred` by hand and put the reason in the Source cell; both are preserved. A `|` in the reason is kept as prose and escaped on the next run.
Re-running the gate keeps every row it can. A row the current review no longer reports is kept and its Source cell flagged, so a finding does not leave this record silently. ONE exception: when a finding id is REUSED by a different finding, the earlier decision cannot keep a row — the id is taken — and it is dropped. A RECORDED decision (anything but `open`) is named on the console when that happens; a row still at `open` is replaced silently, because `open` records no decision to lose.
