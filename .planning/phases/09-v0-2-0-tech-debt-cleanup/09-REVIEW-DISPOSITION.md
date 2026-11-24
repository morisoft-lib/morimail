---
phase: 09
review: 09-REVIEW.md
titles: json
findings:
  - id: WR-01
    severity: warning
    disposition: open
    title: "Pre-flight grouping does not group the 4 MiB request-size rejection, so one line per row is possible"
  - id: IN-01
    severity: info
    disposition: fixed
    title: "\"whitespace\" in the Javadoc and architecture row is ASCII white space only"
  - id: IN-02
    severity: info
    disposition: fixed
    title: "Comment on `strip()` describes the HTML rule loosely"
open: 1
total: 3
recorded: 2026-11-24T05:58:25.622Z
---

# Phase 09: Code Review Disposition

| Finding | Severity | Disposition | Source |
|---------|----------|-------------|--------|
| WR-01 | warning | open | - |
| IN-01 | info | fixed | quick-261124-8os: the build() Javadoc and the docs/architecture.md §10.3 row say ASCII white space, matching the \s class without UNICODE_CHARACTER_CLASS; wording only, no code change |
| IN-02 | info | fixed | quick-261124-8os: the strip() comment in HtmlImages says it removes Unicode white space (Character.isWhitespace), not the C0 controls and space that browsers strip from a URL; comment only, no code change |

Dispositions: `open` (recorded, not yet triaged), `fixed`, `skipped`, `deferred`.
Set `deferred` by hand and put the reason in the Source cell; both are preserved. A `|` in the reason is kept as prose and escaped on the next run.
Re-running the gate keeps every row it can. A row the current review no longer reports is kept and its Source cell flagged, so a finding does not leave this record silently. ONE exception: when a finding id is REUSED by a different finding, the earlier decision cannot keep a row — the id is taken — and it is dropped. A RECORDED decision (anything but `open`) is named on the console when that happens; a row still at `open` is replaced silently, because `open` records no decision to lose.
