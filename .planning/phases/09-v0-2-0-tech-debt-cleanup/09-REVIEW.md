---
phase: 09-v0-2-0-tech-debt-cleanup
reviewed: 2026-11-24T06:00:00Z
depth: standard
files_reviewed: 10
files_reviewed_list:
  - docs/architecture.md
  - docs/spec.md
  - morimail-html/src/main/java/de/morisoft/morimail/html/HtmlImages.java
  - morimail-html/src/test/java/de/morisoft/morimail/html/HtmlImagesTest.java
  - morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/Campaign.java
  - morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/SendCommand.java
  - morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/CampaignTest.java
  - morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/PreflightTest.java
  - morimail/src/main/java/de/morisoft/morimail/Mail.java
  - morimail/src/test/java/de/morisoft/morimail/MailBuilderTest.java
findings:
  critical: 0
  warning: 1
  info: 2
  total: 3
  necessity: 0
status: issues_found
---

# Phase 9: Code Review Report

**Reviewed:** 2026-11-24T06:00:00Z
**Depth:** standard
**Files Reviewed:** 10
**Status:** issues_found

## Summary

I reviewed the diff against `58ad024` for the ten listed files and checked it against the decisions in 09-CONTEXT.md. I did not flag owner decisions (D-04 whole-HTML scan, D-06 no trailing-slash strip, D-09 case-sensitive IDs).

What holds up:

- `./mvnw -o -B -ntp -q verify` passes for the whole reactor.
- The three `CID_REFERENCE` copies (`Mail.java`, `Campaign.java`, `HtmlImages.java`) are identical.
- The claim in architecture.md that adding `<` as a terminator fails no mail that built before is correct. A captured ID containing `<` could never match an accepted content ID, so such a mail always failed.
- `shownId` is correct. It limits to 64 code points, replaces ISO control, line separator, paragraph separator and format characters with `?`, and only then appends `...`. The code points are never split.
- The sample's `Campaign.checkInline` and the helper do not echo an HTML-read ID that is not cleaned. `Campaign` echoes IDs from the unrendered `body.html`, which is a campaign file and not recipient data.
- The pre-flight loop keeps `mails` and `campaign.rows()` aligned. Every row adds exactly one entry, and the loop is skipped when `mails` is empty, so the old `!mails.isEmpty()` guard is not needed.
- The helper's `CONTENT_TYPES` now matches the core's image entries, and `localTypeAgreesWithTheCore` pins that.

One defect undercuts the goal of D-10. Two minor doc and comment inaccuracies are also noted.

## Warnings

### WR-01: Pre-flight grouping does not group the 4 MiB request-size rejection, so one line per row is possible

**File:** `morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/SendCommand.java:219-221`
**Issue:** D-10 says a limit problem shared by many rows must be one problem line, not one per row. `rejected` is keyed by the full text `"mailer.validate rejected the mail: " + e.getMessage()`. For the per-file and total attachment limits the message is the same for every row, so grouping works. That case is the only one `limitProblemOfEveryRowIsOneLine` covers.

`GraphTransport.validate` builds the body-size rejection as `"estimated request size " + estimate + " bytes is above the limit..."`. The estimate includes the rendered body of that row, so it differs for every row whose template fields have different lengths. Each such row then gets its own key and its own problem line. A 500-row campaign with a large shared attachment close to the limit and varying fields can print hundreds of nearly identical lines. It can also print a mix of one grouped line and many single-row lines. The test `laterRowThatGraphRejectsStopsPreflight` has only one rejecting row, so it cannot show this.

**Fix:** Group by something that does not change from row to row, and keep the first row's exact message for display. Two simple options:

```java
// Option A: key on the text up to the first digit run is fragile; prefer keeping the first message.
record Rejection(String message, List<Integer> lines) { }
// Key by exception class + the message with digit runs replaced by "#",
// show the message of the first row in the problem line.
String key = e.getMessage().replaceAll("\\d+", "#");
Rejection r = rejected.computeIfAbsent(key,
        k -> new Rejection("mailer.validate rejected the mail: " + e.getMessage(), new ArrayList<>()));
r.lines().add(campaign.rows().get(i).line());
```

Option B is to leave the grouping as it is and cap the output, for example report the first five rows and a count, as `invalidMail` already does for line numbers. Add a test with two later rows of different body length that both break the 4 MiB estimate, and assert that the problem is one line.

## Info

### IN-01: "whitespace" in the Javadoc and architecture row is ASCII white space only

**File:** `morimail/src/main/java/de/morisoft/morimail/Mail.java:369-370` (also `docs/architecture.md:40`)
**Issue:** The `build()` Javadoc and the §10.3 architecture row say an ID "ends at a quote, whitespace, `)`, `<` or `>`". The pattern uses `\s` without `UNICODE_CHARACTER_CLASS`, so only space, tab, LF, VT, FF and CR end an ID. NEL (U+0085), U+2028 and NBSP do not end it. The new test `controlCharactersInAReferenceAreNotEchoed` relies on exactly that. The code comment above the pattern correctly says "ASCII whitespace", but the public Javadoc and the doc do not. An author reading the docs could expect a Unicode space to end the ID.
**Fix:** Say "ASCII white space" in the `build()` Javadoc and in the architecture row.

### IN-02: Comment on `strip()` describes the HTML rule loosely

**File:** `morimail-html/src/main/java/de/morisoft/morimail/html/HtmlImages.java:353-354`
**Issue:** The new comment says `strip()` removes "a wider set than the ASCII white space HTML strips from a URL". The HTML URL parser strips leading and trailing C0 controls and space (U+0000 to U+0020). `String.strip()` removes `Character.isWhitespace` characters. That set is wider in one direction, because it includes Unicode spaces such as U+2003. It is narrower in the other, because it does not remove NUL or most C0 controls, and it does not remove NBSP. "ASCII white space" is therefore not the accurate description of either side, and the point of D-13 was to make this comment accurate.
**Fix:** Reword, for example: "strip() removes Unicode white space (Character.isWhitespace), which is not the same set as the C0 controls and space that browsers strip from a URL; raw stays as written for messages and the span."

---

_Reviewed: 2026-11-24T06:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
