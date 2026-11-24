---
phase: 09
review: 09-REVIEW.md
titles: json
findings:
  - id: WR-01
    severity: warning
    disposition: deferred
    title: "Pre-flight does not group the 4 MiB request-size rejection, so it can print one line per row (still open, tracked in 09-REVIEW-DISPOSITION.md)"
  - id: IN-01
    severity: info
    disposition: fixed
    title: "The helper's case-insensitive ID rule is not recorded in docs/architecture.md and departs from the \"exact comparison\" contract"
  - id: IN-02
    severity: info
    disposition: deferred
    title: "Dry-run can overwrite one inline image with another on a case-insensitive file system"
  - id: IN-03
    severity: info
    disposition: fixed
    title: "The `CID_REFERENCE` comment in `HtmlImages` still says \"whitespace\""
open: 0
total: 4
recorded: 2026-11-24T09:18:45.442Z
---

# Phase 09: Code Review Disposition

| Finding | Severity | Disposition | Source |
|---------|----------|-------------|--------|
| WR-01 | warning | deferred | output noise only: when the 4 MiB request-size message embeds a size that differs per row, pre-flight prints one line per oversize row, and the run still sends nothing; revisit with the review's size-independent grouping key if a campaign hits it |
| IN-01 | info | fixed | quick-261124-d10: the docs/architecture.md §10.4 and §12 row states that the helper keeps the content IDs it generates distinct regardless of case and treats a template cid: reference as reserving its ID regardless of case, while the core still compares exactly; the §10.3 row now scopes case-sensitive IDs to the core; README and the core Javadoc are unchanged, because both speak about the core |
| IN-02 | info | deferred | preview only: on a case-insensitive file system the dry run can copy two inline files whose names differ only in case onto one out/inline file; the sent mail is unaffected, and CI and the workspace run Linux |
| IN-03 | info | fixed | quick-261124-d10: the comment above HtmlImages.CID_REFERENCE says ASCII white space; the three CID_REFERENCE patterns are byte-identical to before |

Dispositions: `open` (recorded, not yet triaged), `fixed`, `skipped`, `deferred`.
Set `deferred` by hand and put the reason in the Source cell; both are preserved. A `|` in the reason is kept as prose and escaped on the next run.
Re-running the gate keeps every row it can. A row the current review no longer reports is kept and its Source cell flagged, so a finding does not leave this record silently. ONE exception: when a finding id is REUSED by a different finding, the earlier decision cannot keep a row — the id is taken — and it is dropped. A RECORDED decision (anything but `open`) is named on the console when that happens; a row still at `open` is replaced silently, because `open` records no decision to lose.
