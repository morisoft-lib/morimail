---
phase: 06
review: 06-REVIEW.md
titles: json
findings:
  - id: IN-01
    severity: info
    disposition: fixed
    title: "Pre-flight accepts content IDs that an HTML parser or mail client may rewrite"
  - id: IN-02
    severity: info
    disposition: fixed
    title: "The copied `CID_REFERENCE` is kept in step by a comment and eight sample inputs"
  - id: IN-03
    severity: info
    disposition: deferred
    title: "`inlineSrcIsPercentEncodedAndHtmlSafe` assumes the host keeps `ü` as written"
  - id: WR-01
    severity: warning
    disposition: fixed
    title: "Test creates non-ASCII file names, so it fails on hosts with a non-UTF-8 file-name encoding"
  - id: IN-04
    severity: info
    disposition: fixed
    title: "Pre-flight scans the unrendered template for `cid:` references, so a placeholder inside a reference is reported as a missing image"
open: 0
total: 5
recorded: 2026-11-23T09:55:58.576Z
---

# Phase 06: Code Review Disposition

| Finding | Severity | Disposition | Source |
|---------|----------|-------------|--------|
| IN-01 | info | fixed | quick-261124-9og: the README Serial-mail sample section tells authors to name inline files with ASCII letters, digits, -, _ and . only, because a mail client may decode % or & in a cid: reference; pre-flight and the core are unchanged (the core never decodes a reference, by design) |
| IN-02 | info | fixed | quick-261124-9og: CampaignTest.cidPatternAgreesWithTheCore runs the same 12 cases as HtmlImagesTest, adding the parenthesised, quote-ended and letter-or-digit-prefixed references; the long Mail.java comment was rewrapped by 09-01 (4e4e4df); no pattern-string test, because the core field is private (Rule 0) |
| IN-03 | info | deferred | the risk exists only where a file system stores names decomposed (macOS HFS+); CI and the workspace run Linux, where the test passes; revisit if the test fails on macOS, with the fix the review names |
| WR-01 | warning | fixed | 06-REVIEW-FIX.md (not in the current review) |
| IN-04 | info | fixed | 06-REVIEW-FIX.md (not in the current review) |

Dispositions: `open` (recorded, not yet triaged), `fixed`, `skipped`, `deferred`.
Set `deferred` by hand and put the reason in the Source cell; both are preserved. A `|` in the reason is kept as prose and escaped on the next run.
Re-running the gate keeps every row it can. A row the current review no longer reports is kept and its Source cell flagged, so a finding does not leave this record silently. ONE exception: when a finding id is REUSED by a different finding, the earlier decision cannot keep a row — the id is taken — and it is dropped. A RECORDED decision (anything but `open`) is named on the console when that happens; a row still at `open` is replaced silently, because `open` records no decision to lose.
