---
phase: 05
review: 05-REVIEW.md
titles: json
findings:
  - id: WR-01
    severity: warning
    disposition: fixed
    title: "Id read from rendered HTML (possibly recipient data) goes into the exception message without control-character cleaning"
  - id: WR-02
    severity: warning
    disposition: fixed
    title: "The `cid:` scan covers the whole rendered HTML, so prose, URLs and unquoted markup can fail a mail that has no inline image"
  - id: IN-01
    severity: info
    disposition: fixed
    title: "Content type for inline images depends on the platform for formats outside the built-in map"
  - id: IN-02
    severity: info
    disposition: fixed
    title: "`docs/spec.md` still states the MVP rule, and the source comments cite internal decision IDs"
  - id: IN-03
    severity: info
    disposition: deferred
    title: "Case-sensitive content ID uniqueness and the live status of the Graph properties"
open: 0
total: 5
recorded: 2026-11-23T06:22:55.927Z
---

# Phase 05: Code Review Disposition

| Finding | Severity | Disposition | Source |
|---------|----------|-------------|--------|
| WR-01 | warning | fixed | 09-01: control, line separator, paragraph separator and format characters in the shown ID become ?, pinned by MailBuilderTest.controlCharactersInAReferenceAreNotEchoed |
| WR-02 | warning | fixed | 09-01: < ends an ID in the core, sample and helper patterns; the whole-HTML scan stays (owner decision of 2026-11-22), and the trade-off that prose such as ref=cid:123 still counts is in the docs/architecture.md §10.3 row |
| IN-01 | info | fixed | 09-01: webp, bmp and svg are in the core and helper content-type tables |
| IN-02 | info | fixed | scope is the core this review read: spec lines 124-125 and 177 aligned by 5d50c94 (08-01) and spec 10.3 by 90162d7 (08 IN-03); the decision-ID comment in Mail.java removed by 09-01, so morimail/src/main holds none (GraphTransport.java holds none); the decision-ID comments left in morimail-html and morimail-sample-serial are outside this finding and stay |
| IN-03 | info | deferred | case-sensitive content IDs kept as released in 0.2.0 (owner decision), documented in docs/architecture.md and the inline() Javadoc by 09-01; revisit if a live test shows trouble with IDs that differ only in case |

Dispositions: `open` (recorded, not yet triaged), `fixed`, `skipped`, `deferred`.
Set `deferred` by hand and put the reason in the Source cell; both are preserved. A `|` in the reason is kept as prose and escaped on the next run.
Re-running the gate keeps every row it can. A row the current review no longer reports is kept and its Source cell flagged, so a finding does not leave this record silently. ONE exception: when a finding id is REUSED by a different finding, the earlier decision cannot keep a row — the id is taken — and it is dropped. A RECORDED decision (anything but `open`) is named on the console when that happens; a row still at `open` is replaced silently, because `open` records no decision to lose.
