---
phase: 07-html-image-helper-module
fixed_at: 2026-11-23T00:00:00Z
review_path: .planning/phases/07-html-image-helper-module/07-REVIEW.md
iteration: 1
findings_in_scope: 8
fixed: 8
skipped: 0
status: all_fixed
---

# Phase 7: Code Review Fix Report

**Fixed at:** 2026-11-23
**Source review:** .planning/phases/07-html-image-helper-module/07-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope: 8
- Fixed: 8
- Skipped: 0

Verification ran in an isolated git worktree (removed afterwards), using `./mvnw -B -ntp -o verify` for the whole reactor: BUILD SUCCESS, all modules green. The worktree commits were fast-forwarded onto `main`.

## Fixed Issues

### WR-01: Blocking body reads run on the shared common pool

**Files modified:** `morimail-html/src/main/java/de/morisoft/morimail/html/HtmlImages.java`
**Commit:** 4e8befa
**Applied fix:** The body read now runs on its own daemon thread named `morimail-html-download`, completing a `CompletableFuture`. The `get(left, ...)` wait and the stream close are unchanged.

### WR-02: Static `HttpClient` built at class load

**Files modified:** `morimail-html/src/main/java/de/morisoft/morimail/html/HtmlImages.java`
**Commit:** ee7cc69
**Applied fix:** The client moved into a private holder class `Http`, so it is built on the first download only.

### WR-03: Wall-clock assertions flake on slow runners

**Files modified:** `morimail-html/src/test/java/de/morisoft/morimail/html/HtmlImagesDownloadTest.java`
**Commit:** ada5ade
**Applied fix:** Upper bounds widened (2500 to 2900 ms and 2700 to 3000 ms, still below the 3.0 s and 3.2 s of the broken variants). The interrupt test now waits for the request to reach WireMock instead of sleeping 500 ms. `readerRunning()` looks for the named reader thread, not the private method name `readAtMost`.

### IN-01: `image/svg+xml` gave `image-3.svg-xml`

**Files modified:** `morimail-html/src/main/java/de/morisoft/morimail/html/HtmlImages.java`, `morimail-html/src/test/java/de/morisoft/morimail/html/HtmlImagesDataUriTest.java`
**Commit:** ddc48fa
**Applied fix:** The `+suffix` is cut from the subtype before sanitizing, so the name is `image-3.svg`. The test expectation was updated.

### IN-02: URL segment used as file name without checks

**Files modified:** `morimail-html/src/main/java/de/morisoft/morimail/html/HtmlImages.java`, `morimail-html/src/test/java/de/morisoft/morimail/html/HtmlImagesDownloadTest.java`
**Commit:** 52d9408
**Applied fix:** A segment of `.`, `..` or more than 100 characters falls back to `ID.ext`. A content ID base made only of dots becomes `image`. Test cases added for `..` and a 101-character segment. The "no extension matching the content type" part of the suggestion was not applied (Rule 0).

### IN-03: `<img alt="x"src="y">` silently not rewritten

**Files modified:** `morimail-html/src/main/java/de/morisoft/morimail/html/HtmlImages.java`
**Commit:** 8b556d6
**Applied fix:** Documented the limitation in the class Javadoc (the reviewer's accepted simpler variant) instead of changing the regex.

### IN-04: `CID_REFERENCE` copies kept in sync by comment only

**Files modified:** `morimail-html/src/test/java/de/morisoft/morimail/html/HtmlImagesTest.java`
**Commit:** 3315edf
**Applied fix:** The existing test already compared the helper pattern with `Mail.builder().build()` on a corpus; it now also covers `(cid:a)`, `cid:a"b`, and digit or letter-prefixed references.

### IN-05: Helper decisions not recorded in `docs/architecture.md`

**Files modified:** `docs/architecture.md`
**Commit:** 684a2b4
**Applied fix:** Added one row to the deviations table for the transient/invalid split, the 3 MiB cap, the timeouts, the redirect rule, `data:` decoding through the work directory, and the `provided` scope.

---

_Fixed: 2026-11-23_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
