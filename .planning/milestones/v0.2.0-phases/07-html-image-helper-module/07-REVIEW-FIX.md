---
phase: 07-html-image-helper-module
fixed_at: 2026-11-23T17:10:00Z
review_path: /home/coder/morimail/.planning/phases/07-html-image-helper-module/07-REVIEW.md
iteration: 1
findings_in_scope: 3
fixed: 3
skipped: 0
status: all_fixed
---

# Phase 7: Code Review Fix Report

**Fixed at:** 2026-11-23
**Source review:** /home/coder/morimail/.planning/phases/07-html-image-helper-module/07-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope: 3
- Fixed: 3
- Skipped: 0

**Verification:** The fixes were made in an isolated git worktree. The gate `./mvnw -B -ntp -o -q verify` ran there offline, for `morimail,morimail-html` after IN-01 and IN-02 and for the whole reactor after IN-03. It passed each time. The worktree was fast-forwarded into `main` and removed afterwards.

## Fixed Issues

### IN-01: Javadoc for the `data:` file name is stale after the `+suffix` fix

**Files modified:** `morimail-html/src/main/java/de/morisoft/morimail/html/HtmlImages.java`
**Commit:** 4e49cd3
**Applied fix:** The `embed(template, baseDir, workDir)` Javadoc now says the file name is the ID plus the subtype of the type without a `+suffix`, with `image-3.svg` for `image/svg+xml` as the example.

### IN-02: A `src` value with leading or trailing whitespace is treated as a file name

**Files modified:** `morimail-html/src/main/java/de/morisoft/morimail/html/HtmlImages.java`, `morimail-html/src/test/java/de/morisoft/morimail/html/HtmlImagesTest.java`
**Commit:** 66b5264
**Applied fix:** The `&amp;`-decoded value is now `strip()`ped before the scheme check. `raw` stays as written, so exception messages and the replacement span are unchanged. A new test, `surroundingWhitespaceOfASourceIsIgnored`, checks that a local source with surrounding whitespace resolves, and that a `cid:` source with surrounding whitespace stays byte for byte.

### IN-03: The reader thread catches `Throwable` and reports it as a retryable failure

**Files modified:** `morimail-html/src/main/java/de/morisoft/morimail/html/HtmlImages.java`
**Commit:** 457d469, corrected in 2a14312
**Applied fix:** The reader lambda now catches `RuntimeException` only. `readAtMost` throws `UncheckedIOException`, so I/O failures still map to the transient "download failed". An `Error` ends the daemon reader thread, and the caller sees the timeout. The fix changes error handling, so check it when you review. I added no test, because an `Error` in the reader cannot be triggered deterministically.

**Correction (2a14312):** With only `RuntimeException` caught, an `Error` left the future incomplete. The caller then waited out the whole budget and still threw the transient "download timed out", with the cause lost. The reader now completes the future with every `Throwable` again, and the caller rethrows an `Error` instead of wrapping it. `./mvnw -B -ntp -o -q verify` passed for the whole reactor.

---

_Fixed: 2026-11-23_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
