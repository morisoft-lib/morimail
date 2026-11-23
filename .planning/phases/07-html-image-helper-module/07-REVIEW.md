---
phase: 07-html-image-helper-module
reviewed: 2026-11-23T17:00:00Z
depth: standard
files_reviewed: 7
files_reviewed_list:
  - docs/architecture.md
  - morimail-html/pom.xml
  - morimail-html/src/main/java/de/morisoft/morimail/html/HtmlImages.java
  - morimail-html/src/test/java/de/morisoft/morimail/html/HtmlImagesDataUriTest.java
  - morimail-html/src/test/java/de/morisoft/morimail/html/HtmlImagesDownloadTest.java
  - morimail-html/src/test/java/de/morisoft/morimail/html/HtmlImagesTest.java
  - morimail-html/src/test/java/de/morisoft/morimail/html/ModuleRulesTest.java
findings:
  critical: 0
  warning: 0
  info: 3
  total: 3
  necessity: 0
status: issues_found
---

# Phase 7: Code Review Report

**Reviewed:** 2026-11-23
**Depth:** standard
**Files Reviewed:** 7
**Status:** issues_found

## Summary

This is an incremental re-review. I diffed `0ad2c36..HEAD` for the listed files and read the whole of `HtmlImages.java`, the POM and `ModuleRulesTest`. I also read the changed parts of the download, data-URI and `HtmlImagesTest` tests. I ran `./mvnw -B -ntp -o -q verify -pl morimail,morimail-html`, and it passed.

All eight findings of the previous review are fixed correctly:

- The reader runs on its own daemon thread, and the stream is closed on every exit path, so a timed-out read ends.
- The `HttpClient` sits in a lazy holder class.
- The `+suffix` is cut from the subtype, so the name is `image-3.svg`.
- A name of `.` or `..`, or one longer than 100 characters, falls back to `ID.ext`.
- A dot-only ID becomes `image`.
- The no-whitespace limitation is documented.
- The test bounds are widened, and `readerRunning()` now keys on the thread name.

The compile-scope change (plan 07-04, commit b333689) is sound:

- `morimail` has no scope element in `morimail-html/pom.xml`, so Maven gives it compile scope. The version comes from the parent `dependencyManagement` (`${project.version}`), which carries no scope. The parent POM is published, so the version resolves for consumers.
- `provided` also put Jackson on the helper's compile classpath, so the `ModuleRulesTest` guard against Jackson imports is still needed. Its updated comment is accurate.
- The test-jar dependency keeps test scope.
- The `-pl morimail,morimail-html -am` release line still works, because the main `morimail` jar resolves in the reactor. The profile only disables test phases, because of the test-jar.
- `docs/architecture.md` matches the code on every number: 3 MiB, 10 s and 30 s, the redirect rule, `data:` decoding and the scope.
- The core does not reference the helper.

I found no correctness or security defects. These three Info items are small robustness and documentation gaps.

## Info

### IN-01: Javadoc for the `data:` file name is stale after the `+suffix` fix

**File:** `morimail-html/src/main/java/de/morisoft/morimail/html/HtmlImages.java:217-218`
**Issue:** The `embed(template, baseDir, workDir)` Javadoc says the file name of a decoded image is "the ID plus the subtype of the type". Since commit ddc48fa, `fileName()` cuts the structured-syntax suffix first (line 690). For `image/svg+xml` the name is `image.svg`, not `image.svg+xml`, and non-name characters are replaced with `-`. The text therefore does not describe the rule the code now follows.
**Fix:** Reword to "the ID plus the subtype of the type, without a `+suffix` (for example `image-3.svg` for `image/svg+xml`)".

### IN-02: A `src` value with leading or trailing whitespace is treated as a file name

**File:** `morimail-html/src/main/java/de/morisoft/morimail/html/HtmlImages.java:346-358, 601-640`
**Issue:** Browsers and mail clients strip surrounding ASCII whitespace from a `src` value. This code does not. `<img src=" https://example.org/a.png">` has no scheme match, because `SCHEME` is anchored at `^[A-Za-z]`. It falls through to the local branch and fails with "file not found". `src=" data:image/png;base64,..."` and `src=" cid:logo"` fail or are misread the same way. Without a work directory, a template that renders fine in a browser makes `embed` throw. The failure is loud and names the `src`, so the impact is low.
**Fix:** Either `strip()` the value after the `&amp;` decode, or document that surrounding whitespace is not removed.
```java
String value = raw.replace("&amp;", "&").strip();
```
If you strip, keep `raw` unchanged for messages and for the replacement span, as the code does now.

### IN-03: The reader thread catches `Throwable` and reports it as a retryable failure

**File:** `morimail-html/src/main/java/de/morisoft/morimail/html/HtmlImages.java:551-557, 565-566`
**Issue:** The reader lambda catches `Throwable` and passes it to `completeExceptionally`. `download()` maps every `ExecutionException` to `TransientMailException("download failed")`. An `Error` raised while reading, such as `OutOfMemoryError` or `StackOverflowError`, therefore reaches the caller as an ordinary retryable network failure. The read is capped at 3 MiB, so this is unlikely. If it happens, a transient-category result tells the consumer to retry a condition that is not transient.
**Fix:** Catch only `RuntimeException` in the lambda (`readAtMost` throws `UncheckedIOException`). An `Error` then ends only the reader thread and the call times out. Alternatively, rethrow when `e.getCause() instanceof Error` in the `ExecutionException` handler.

---

_Reviewed: 2026-11-23_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
