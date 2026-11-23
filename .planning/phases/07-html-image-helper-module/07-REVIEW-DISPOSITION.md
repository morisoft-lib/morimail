---
phase: 07
review: 07-REVIEW.md
titles: json
findings:
  - id: WR-01
    severity: warning
    disposition: open
    title: "Blocking body reads run on the shared common pool, so concurrent embeds can produce false \"download timed out\""
  - id: WR-02
    severity: warning
    disposition: open
    title: "The static `HttpClient` is built when the class loads, so local-only users pay for it and can fail at class init"
  - id: WR-03
    severity: warning
    disposition: open
    title: "Wall-clock assertions in the download tests are likely to flake on slow CI runners"
  - id: IN-01
    severity: info
    disposition: open
    title: "A source of `image/svg+xml` produces the unusable attachment name `image-3.svg-xml`"
  - id: IN-02
    severity: info
    disposition: open
    title: "The attachment file name is taken from a remote URL segment without length or content checks"
  - id: IN-03
    severity: info
    disposition: open
    title: "`<img alt=\"x\"src=\"y\">` (no whitespace between attributes) is silently not rewritten"
  - id: IN-04
    severity: info
    disposition: open
    title: "The `CID_REFERENCE` pattern now has three copies kept in sync by comment only"
  - id: IN-05
    severity: info
    disposition: open
    title: "The helper's behaviour decisions are not recorded in `docs/architecture.md`"
open: 8
total: 8
recorded: 2026-11-23T14:59:05.659Z
---

# Phase 07: Code Review Disposition

| Finding | Severity | Disposition | Source |
|---------|----------|-------------|--------|
| WR-01 | warning | open | - |
| WR-02 | warning | open | - |
| WR-03 | warning | open | - |
| IN-01 | info | open | - |
| IN-02 | info | open | - |
| IN-03 | info | open | - |
| IN-04 | info | open | - |
| IN-05 | info | open | - |

Dispositions: `open` (recorded, not yet triaged), `fixed`, `skipped`, `deferred`.
Set `deferred` by hand and put the reason in the Source cell; both are preserved. A `|` in the reason is kept as prose and escaped on the next run.
Re-running the gate keeps every row it can. A row the current review no longer reports is kept and its Source cell flagged, so a finding does not leave this record silently. ONE exception: when a finding id is REUSED by a different finding, the earlier decision cannot keep a row — the id is taken — and it is dropped. A RECORDED decision (anything but `open`) is named on the console when that happens; a row still at `open` is replaced silently, because `open` records no decision to lose.
