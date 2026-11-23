---
phase: 06-inline-images-in-the-serial-mail-sample
reviewed: 2026-11-23T09:15:00Z
depth: standard
files_reviewed: 4
files_reviewed_list:
  - morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/Campaign.java
  - morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/SendCommand.java
  - morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/DryRunTest.java
  - morimail/src/main/java/de/morisoft/morimail/Mail.java
findings:
  critical: 0
  warning: 0
  info: 3
  total: 3
  necessity: 0
status: issues_found
---

# Phase 6: Code Review Report

**Reviewed:** 2026-11-23
**Depth:** standard
**Files Reviewed:** 4
**Status:** issues_found

## Summary

This is a second pass over the four files after the first review's fixes (WR-01, IN-01, IN-03, IN-04 were applied; IN-02 was skipped). I read each file in full and traced the inline-image path end to end:

- `Campaign.read` and `checkInline`
- `SendCommand.call` and `writeDryRun`
- `Mail.Builder.resolveAttachments` and `checkReferences`

I found no bugs, security problems or correctness defects that I can prove.

- **Pre-flight vs `build()`:** the pre-flight probes match `build()`'s order and wording. The two `CID_REFERENCE` patterns are identical.
- **Dry-run rewrite:** it uses the same pattern. A reference with an empty ID is left alone, which matches the core.
- **Dry-run `src` values:** they are built with the multi-argument `URI` constructor, which always quotes `%`, `#`, `"`, `<`, `>` and space. A path starting with `inline/` cannot trip the colon-in-first-segment rule. The only HTML-significant character left unquoted is `&`, which is escaped. A single quote also stays unquoted, but an extension containing one cannot resolve to an image type, so that case cannot be reached.
- **Empty `mails` / null first mail:** `mails.get(0)` in `writeDryRun` and the `mailer.validate` call cannot see an empty or null-first list. Both situations add a problem and exit with 2 first.
- **Unreadable inline files:** a defect in `inline/` makes `read` return null, so no mail is built. The scan does not report a defect twice. `withoutField` strips the `attachments[0]: ` prefix, and the core always emits that prefix for these probes.

Three Info items remain. None blocks shipping.

## Info

### IN-01: Pre-flight accepts content IDs that an HTML parser or mail client may rewrite

**File:** `morimail/src/main/java/de/morisoft/morimail/Mail.java:492-506`, `morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/Campaign.java:268-272`
**Issue:** The content ID of an inline image is its file name minus the last extension (D-03). `contentIdProblem` only rejects blank IDs, line breaks, whitespace, quotes, parentheses and angle brackets. An ID such as `50%` or `a&b` passes the pre-flight, and `DryRunTest.inlineSrcIsPercentEncodedAndHtmlSafe` pins both as accepted.

I could not run a mail client here, so this part is inferred and not verified. In a real mail, the HTML parser reads `src="cid:..."` as an attribute value. Legacy named character references without a semicolon (for example `&copy`) can be decoded there. RFC 2392 also defines the `cid:` URL as percent-encoded, so some clients decode `%XX` before they look the part up. In either case the reference would no longer equal the `Content-ID` header. The image would be missing in the recipient's mail while the pre-flight said it was fine. The core's "never decoded" rule is deliberate (D-05, D-06), so this is a known limit and not a regression. The pre-flight is the place that could catch it.
**Fix:** Either document the limit in the sample's README or `--help` text (use letters, digits, `-`, `_`, `.` in inline file names), or make `checkInline` report an ID that holds `%` or `&` as a problem. If the second option is chosen, change `Campaign` only, not the core.

### IN-02: The copied `CID_REFERENCE` is kept in step by a comment and eight sample inputs

**File:** `morimail/src/main/java/de/morisoft/morimail/Mail.java:46-49`, `morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/Campaign.java:44-49`
**Issue:** The IN-01 fix of the first review added a "keep both equal" comment, and `CampaignTest.cidPatternAgreesWithTheCore` compares the two patterns' behaviour on eight fixed strings. A change to the core's lookbehind or character class that those eight strings do not exercise would not fail any test. Line 46 of `Mail.java` is also a 160-character comment, while the first review's IN-03 wrapped the long lines in the sample.
**Fix:** Cheap and within Rule 0: add a test that compares `Campaign.CID_REFERENCE.pattern()` and `flags()` with the core pattern. The core's field is private, so the test could build both patterns from the same literal. Alternatively, accept the eight-case test as enough and wrap line 46 at about 100 columns.

### IN-03: `inlineSrcIsPercentEncodedAndHtmlSafe` assumes the host keeps `ü` as written

**File:** `morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/DryRunTest.java:127-156`
**Issue:** The `assumeTrue` added for WR-01 only checks that `sun.jnu.encoding` can encode `ü`. The test then needs the name read back from the directory to equal the `ü` in the HTML (`cid:ü`) code point for code point. On a host or file system that stores names in decomposed form (macOS HFS+ is the usual example), the listed name could be `u` plus a combining diaeresis. The scan would then report `cid:ü has no image` and the test would fail. I have not run the test on such a host, so this is a risk and not a confirmed failure. The CI described in the project runs on Linux, where the risk does not exist.
**Fix:** If the test ever fails on macOS, build the HTML reference from the listed name instead of the literal, or add a second `assumeTrue` that `Files.list` returns the name that was written.

---

_Reviewed: 2026-11-23T09:15:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
