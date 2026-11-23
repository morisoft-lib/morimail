---
phase: 07-html-image-helper-module
reviewed: 2026-11-23T17:30:00Z
depth: standard
files_reviewed: 2
files_reviewed_list:
  - morimail-html/src/main/java/de/morisoft/morimail/html/HtmlImages.java
  - morimail-html/src/test/java/de/morisoft/morimail/html/HtmlImagesTest.java
findings:
  critical: 0
  warning: 0
  info: 2
  total: 2
status: issues_found
---

# Phase 7: Code Review Report

**Reviewed:** 2026-11-23
**Depth:** standard (incremental re-review against diff_base 64b1d92)
**Files Reviewed:** 2
**Status:** issues_found

## Summary

I re-reviewed the changes since 64b1d92 and read both files in full. The three earlier findings are fixed correctly, and the fixes add no defects. `./mvnw -o -pl morimail-html -am test` passes.

- **Whitespace strip (IN-01 earlier):** `value = raw.replace("&amp;", "&").strip()` at `HtmlImages.java:349` feeds every later use: the `$$` check, scheme detection, the dedupe key, `parseUrl`, `decodeData` and `resolveLocal`. `raw` is kept for messages, and the replaced span still uses `m.start(g)` and `m.end(g)`, so the rewrite replaces the whole original value including its whitespace. A leading space no longer turns `" data:..."` or `" https://..."` into a local-path lookup. A `cid:` source with whitespace is skipped and left byte-for-byte unchanged, which the new test asserts. A blank source (`src=" "`) now resolves to the base directory and fails with "is not a readable file". That is a clear error and not a crash.
- **`+suffix` Javadoc (IN-02 earlier):** The new text matches `fileName()` at line 696. `image/svg+xml` gives `svg`. `IMAGE_TYPE` guarantees a non-empty subtype that starts with `[a-z0-9]`, so the suffix cut cannot leave an empty extension.
- **Error rethrow (IN-03 earlier):** `throw error;` at line 570 sits inside the try-with-resources, so the body stream is still closed. The outer `catch (IOException)` does not intercept an `Error`. An `UncheckedIOException` from `readAtMost` still maps to a transient failure. The reader thread catches `Throwable`, so the future always completes and the caller cannot hang.

I found no bug or security issue in the new code. Two minor Info items follow.

## Info

### IN-01: `strip()` trimming is undocumented and not the HTML rule

**File:** `morimail-html/src/main/java/de/morisoft/morimail/html/HtmlImages.java:348-349`
**Issue:** The class Javadoc lists which character references are decoded ("The only character reference that is decoded is `&amp;`") but not that surrounding white space of a `src` is now ignored. This is observable behaviour: `src=" logo.png "` now works and `src=" cid:x"` is left unchanged. `String.strip()` also removes Unicode white space such as U+2003 and U+2028. HTML and URL parsing trim only leading and trailing C0 control characters and the space (U+0000 to U+0020). So an unusual file name that starts or ends with an em space is trimmed here but not by a mail client. This is extremely unlikely in practice. The only cost is that the comment ("Browsers and mail clients strip surrounding whitespace") and the Javadoc are slightly imprecise.
**Fix:** Add one sentence to the class Javadoc, for example "White space around the value of a `src` is ignored." If an exact match with the browser rule matters, trim with a small helper that drops chars `<= ' '`, or `value.replaceAll("^[\\x00-\\x20]+|[\\x00-\\x20]+$", "")`. Otherwise keep `strip()` and say so.

### IN-02: The Error-rethrow path has no test

**File:** `morimail-html/src/test/java/de/morisoft/morimail/html/HtmlImagesTest.java` (no test for `HtmlImages.java:569-571`)
**Issue:** The new `instanceof Error` branch is untested. The reader runs on a private thread over a `ofInputStream()` body, so a test could force it by serving a body whose stream throws an `Error` (for example `OutOfMemoryError` or a custom `Error`) from `read`. That is hard to do through WireMock, so the omission is understandable. It is a three-line branch and the risk is low.
**Fix:** Optionally cover it with a package-private seam, or accept the gap. If the gap is accepted, nothing more is needed. Do not add a seam only for this (Rule 0).

---

_Reviewed: 2026-11-23_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
