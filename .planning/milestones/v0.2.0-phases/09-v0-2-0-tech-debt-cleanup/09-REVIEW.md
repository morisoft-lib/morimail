---
phase: 09-v0-2-0-tech-debt-cleanup
reviewed: 2026-11-24T00:00:00Z
depth: standard
files_reviewed: 9
files_reviewed_list:
  - README.md
  - docs/architecture.md
  - docs/spec.md
  - morimail-html/src/main/java/de/morisoft/morimail/html/HtmlImages.java
  - morimail-html/src/test/java/de/morisoft/morimail/html/HtmlImagesTest.java
  - morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/SendCommand.java
  - morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/CampaignTest.java
  - morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/PreflightTest.java
  - morimail/src/main/java/de/morisoft/morimail/Mail.java
findings:
  critical: 0
  warning: 1
  info: 3
  total: 4
  necessity: 0
status: issues_found
---

# Phase 09: Code Review Report

**Reviewed:** 2026-11-24
**Depth:** standard
**Files Reviewed:** 9
**Status:** issues_found

## Summary

I reviewed the nine files in full and traced the changes since `a41778b` in detail. The changes are commit 271f262 (case-insensitive helper content IDs), commit 703889c (the `CampaignTest` agreement cases), and the wording changes in `Mail.java`, `README.md` and `docs/architecture.md`. `docs/spec.md` has no diff.

- **271f262, `HtmlImages`:** The `TreeSet(String.CASE_INSENSITIVE_ORDER)` change is correct. Both the template-reserved IDs and the generated IDs go through the same set. The `-2` suffix loop terminates, and the new `reserved` test case pins the `cid:LOGO` plus `logo.png` outcome. I found no defect in the code. Two documentation gaps remain (IN-01 and IN-02).
- **703889c, `CampaignTest`:** The three new cases match what `Campaign.CID_REFERENCE` and the core's `Mail.Builder.CID_REFERENCE` do. The patterns are identical, and each case also builds through `Mail.build()`. `HtmlImagesTest` carries the same cases.
- **`Mail.java`:** The change is Javadoc only. "ASCII white space" is accurate for the `\s` class without `UNICODE_CHARACTER_CLASS`. `contentIdProblem` uses `Character.isWhitespace`, which is a superset of that class, so every character that ends an ID is rejected as an ID character. The architecture claim "each accepted ID can be found" holds.
- **`SendCommand`:** I found no new defect. WR-01 from the previous review still applies and is re-reported below.

I did not load `necessity-review.md`, because no necessity candidate came up. The `necessity:` count is 0.

## Warnings

### WR-01: Pre-flight does not group the 4 MiB request-size rejection, so it can print one line per row (still open, tracked in 09-REVIEW-DISPOSITION.md)

**File:** `morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/SendCommand.java:217-224`
(message source: `morimail/src/main/java/de/morisoft/morimail/graph/GraphTransport.java:127`)
**Issue:** The pre-flight pass groups `mailer.validate` rejections by exact message text, with `rejected.computeIfAbsent("mailer.validate rejected the mail: " + e.getMessage(), ...)`. The Graph request-size rejection embeds the estimated size: `estimated request size N bytes is above the limit of 4 MiB`. When the body is large, N depends on the recipient data (the rendered template), so each row gets a different message text. Every oversize row then becomes its own problem line and its own entry in the "Pre-flight failed with N problem(s)" count. A 10,000-row campaign with a per-recipient body over the limit prints 10,000 lines.

The comment at line 173 says "The same defect ... hits every row: report it once", and the `limitProblemOfEveryRowIsOneLine` test covers only the attachment-size message, which has a fixed text. `laterRowThatGraphRejectsStopsPreflight` has a single oversize row, so it does not exercise this case. The attachment-based request-size case (all rows share the same attachments and a short body) does group, because the sizes are nearly equal, but only when the estimates are byte-identical.
**Fix:** Group on a size-independent key and show the largest size, or drop the number from the grouping key:

```java
// Key without the digits that vary per row; keep the first message as the shown text.
String key = "mailer.validate rejected the mail: " + e.getMessage().replaceAll("\\d+ bytes", "N bytes");
rejected.computeIfAbsent(key, m -> new ArrayList<>()).add(campaign.rows().get(i).line());
```

Alternatively, have `GraphTransport.validate` state only the limit in the message and leave the size to a DEBUG log. Add a `PreflightTest` case with two rows of different oversize bodies that expects one line.

## Info

### IN-01: The helper's case-insensitive ID rule is not recorded in docs/architecture.md and departs from the "exact comparison" contract

**File:** `docs/architecture.md:40-41`, `morimail-html/src/main/java/de/morisoft/morimail/html/HtmlImages.java:344`
**Issue:** Commit 271f262 makes `HtmlImages` treat content IDs as equal regardless of case. This applies both to generated IDs (`Logo` then `logo` gives `logo-2`) and to the IDs reserved by template `cid:` references. The Javadoc states it. The architecture page does not. The §10.3 row says IDs are "compared exactly ... `Logo` and `logo` are two different inline images ... IDs stay case-sensitive as released in 0.2.0". The §10.4/§12 `morimail-html` row lists the helper's decisions and omits the new rule. README line 101 and the core `Mail.Builder.inline` Javadoc also say "exact, case included". A reader of the architecture page therefore gets a contradiction. The same page explains the owner's reason for staying case-sensitive, and the helper now deliberately avoids what the core allows. The helper is stricter than the core: it never produces IDs that differ only in case. This is safe, but it is an undocumented design decision in the file that exists to record those.
**Fix:** Add one sentence to the §10.4 row, for example: "The helper keeps the IDs it generates distinct regardless of case (and treats a template `cid:` reference as taking its ID regardless of case), because some mail clients compare content IDs without regard to case; the core still compares exactly."

### IN-02: Dry-run can overwrite one inline image with another on a case-insensitive file system

**File:** `morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/SendCommand.java:312-318`
**Issue:** Pre-flight accepts `inline/Logo.png` and `inline/logo.png` as two images, because it compares IDs exactly (`cid:Logo` and `cid:logo`). The dry run copies each image to `out/inline/<filename>` with `REPLACE_EXISTING`. On macOS or Windows (case-insensitive default) the second copy overwrites the first, and both rewritten `src` values point at the same file. The preview then shows one image twice. The result is wrong, and no error is reported. The sent mail is not affected. This is a preview-only defect, and it is the situation the architecture row warns about for mail clients.
**Fix:** Either reject IDs that differ only in case in `Campaign` pre-flight (consistent with the helper's new rule), or detect the collision in the dry run: copy to a name derived from the lower-cased filename and fail with exit 1 if a different image already maps to it.

### IN-03: The `CID_REFERENCE` comment in `HtmlImages` still says "whitespace"

**File:** `morimail-html/src/main/java/de/morisoft/morimail/html/HtmlImages.java:119-120`
**Issue:** Quick task 8os changed the `build()` Javadoc and the architecture row to "ASCII white space", because `\s` without `UNICODE_CHARACTER_CLASS` is ASCII only. The comment above `HtmlImages.CID_REFERENCE` ("the id ends at a quote, whitespace, ...") was not updated. The wording is harmless and nothing is wrong at run time, but the copy in `Mail.java:47` says "ASCII whitespace" and this one does not.
**Fix:** Say "ASCII white space" in the `HtmlImages` comment, as `Mail.java:47` already does.

---

_Reviewed: 2026-11-24_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
