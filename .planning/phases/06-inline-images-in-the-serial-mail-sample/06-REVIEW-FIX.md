---
phase: 06-inline-images-in-the-serial-mail-sample
fixed_at: 2026-11-23T08:55:00Z
review_path: /home/coder/morimail/.planning/phases/06-inline-images-in-the-serial-mail-sample/06-REVIEW.md
iteration: 1
findings_in_scope: 5
fixed: 4
skipped: 1
status: partial
---

# Phase 6: Code Review Fix Report

**Fixed at:** 2026-11-23
**Source review:** /home/coder/morimail/.planning/phases/06-inline-images-in-the-serial-mail-sample/06-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope: 5
- Fixed: 4
- Skipped: 1

**Verification:** `./mvnw -o -q verify` passed (exit 0) in the isolated review-fix worktree before the fast-forward, and again in the main checkout (`main`) after it. The WR-01 test also passed under `LC_ALL=C` (it skips there).

## Fixed Issues

### WR-01: Test creates non-ASCII file names, so it fails on hosts with a non-UTF-8 file-name encoding

**Files modified:** `morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/DryRunTest.java`
**Commit:** a92b831
**Applied fix:** `inlineSrcIsPercentEncodedAndHtmlSafe` now starts with a JUnit `Assumptions.assumeTrue` that checks `sun.jnu.encoding` can encode the u-umlaut (falls back to US-ASCII if the property is missing). The whole test is skipped on hosts that cannot hold the name, as the review suggested.

### IN-01: Core logic is copied into the sample and kept in step only by a test and a comment

**Files modified:** `morimail/src/main/java/de/morisoft/morimail/Mail.java`
**Commit:** ed29b34
**Applied fix:** Added a one-line comment next to the private `CID_REFERENCE` saying the sample copies it (`Campaign.CID_REFERENCE`) and both must stay equal. No API change.

### IN-03: Over-long lines and a magic number

**Files modified:** `morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/Campaign.java`, `morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/SendCommand.java`
**Commit:** 0aea8d0
**Applied fix:** Wrapped the record header, the comment in `checkInline`, the `SendCommand` comment and the `describeAll` Javadoc. Added `IMAGE_PREFIX = "image/"` and used `IMAGE_PREFIX.length()` in `regionMatches`.

### IN-04: Pre-flight scans the unrendered template for `cid:` references

**Files modified:** `morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/Campaign.java`
**Commit:** 48bb97e
**Applied fix:** Added a sentence to the `checkInline` Javadoc stating that the HTML is scanned as written, so a `cid:` reference cannot be built from a placeholder.

## Skipped Issues

### IN-02: Two throw-away `Mail.build()` probes per image

**File:** `morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/Campaign.java:195-221`
**Reason:** skipped: the review marks it "not urgent" and only worth doing if the D-01 "two defects, two lines" rule is dropped. That rule is still in force and pinned by tests, and no trivial improvement exists. Restructuring would go against Rule 0 and the task instructions.
**Original issue:** `checkInline` builds a fake mail twice per file and re-implements the image rule by hand, which is intricate for a sample.

---

_Fixed: 2026-11-23_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
