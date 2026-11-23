---
phase: 07-html-image-helper-module
reviewed: 2026-11-23T00:00:00Z
depth: standard
files_reviewed: 10
files_reviewed_list:
  - .github/workflows/ci.yml
  - docs/architecture.md
  - morimail-html/pom.xml
  - morimail-html/src/main/java/de/morisoft/morimail/html/HtmlImages.java
  - morimail-html/src/test/java/de/morisoft/morimail/html/EmbedThroughRecordingTest.java
  - morimail-html/src/test/java/de/morisoft/morimail/html/HtmlImagesDataUriTest.java
  - morimail-html/src/test/java/de/morisoft/morimail/html/HtmlImagesDownloadTest.java
  - morimail-html/src/test/java/de/morisoft/morimail/html/HtmlImagesTest.java
  - morimail-html/src/test/java/de/morisoft/morimail/html/ModuleRulesTest.java
  - pom.xml
findings:
  critical: 0
  warning: 3
  info: 5
  total: 8
status: issues_found
---

# Phase 7: Code Review Report

**Reviewed:** 2026-11-23
**Depth:** standard
**Files Reviewed:** 10
**Status:** issues_found

## Summary

I reviewed `HtmlImages` against spec section 10.4, the module POM, the parent POM change, the CI workflow and the tests. The path-containment logic (real-path check after `normalize()`, symlink escape rejected, `file://host` rejected) holds up. The `IMG_SRC` regex is possessive and cannot backtrack catastrophically. Download categories, size caps and the "no payload in messages" rule are implemented as documented. Logging stays within the project rules: DEBUG only, and no userinfo or query.

I found no blockers. The main concerns are the use of the shared `ForkJoinPool.commonPool()` for blocking network reads, eager creation of the static `HttpClient` for local-only users, and timing-sensitive tests. Because the logic in `HtmlImages` is unchanged since the Phase 07 plans, I did not re-trace Mail.Builder, which this module only mirrors.

Necessity assessment (#4100) was not performed. I did not load `necessity-review.md`, so no `necessity:` count is emitted.

## Warnings

### WR-01: Blocking body reads run on the shared common pool, so concurrent embeds can produce false "download timed out"

**File:** `morimail-html/src/main/java/de/morisoft/morimail/html/HtmlImages.java:544`
**Issue:** `CompletableFuture.supplyAsync(() -> readAtMost(body))` without an executor uses `ForkJoinPool.commonPool()`. That pool has `cores - 1` threads, and the JVM shares it with every parallel stream and `CompletableFuture` in the consumer's application. Each read blocks a pool thread on network I/O for up to the 30 s budget. Several concurrent `embed` calls, or an unrelated blocked task, can queue the reader task behind other work. The deadline `left` is consumed while the task waits to start, and the call then fails with a `TransientMailException` ("download timed out") although the server answered promptly. The same blocking can starve unrelated code in the host application. Javadoc promises a library that is "safe for concurrent use", and this is the one place where that holds only loosely.
**Fix:** Do not use the common pool for blocking I/O. Give each download its own short-lived thread, for example:
```java
CompletableFuture<byte[]> read = new CompletableFuture<>();
Thread reader = new Thread(() -> {
    try { read.complete(readAtMost(body)); }
    catch (Throwable t) { read.completeExceptionally(t); }
}, "morimail-html-download");
reader.setDaemon(true);
reader.start();
```
`read.get(left, NANOSECONDS)` and the closing `try` block stay as they are. The test `readerRunning()` looks for a `readAtMost` stack frame, so it keeps working.

### WR-02: The static `HttpClient` is built when the class loads, so local-only users pay for it and can fail at class init

**File:** `morimail-html/src/main/java/de/morisoft/morimail/html/HtmlImages.java:99-102`
**Issue:** `HTTP` is a static field initializer. `embed(template, baseDir)` and `toDataUris(template, baseDir)` never touch the network, yet the first call to any `HtmlImages` method builds an `HttpClient`. Building the client starts its selector thread and initializes the default `SSLContext`. In a restricted environment (for example a broken `javax.net.ssl` configuration or a security manager), `build()` throws. The error then surfaces as `ExceptionInInitializerError` and later `NoClassDefFoundError` for every method, including the pure file methods that need no network. The Javadoc promises that "only `embed(...Path, Path)` makes network requests".
**Fix:** Defer creation to a holder class so the client exists only when a download starts:
```java
private static final class Http {
    static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
}
// in download(): Http.CLIENT.send(...)
```

### WR-03: Wall-clock assertions in the download tests are likely to flake on slow CI runners

**File:** `morimail-html/src/test/java/de/morisoft/morimail/html/HtmlImagesDownloadTest.java:312`, `:366` (also `:410`)
**Issue:** `stalledBodyTimesOut` asserts `elapsed < 2700` ms for a 2 s budget (700 ms of slack including thread start, WireMock dribble and teardown). The header-timeout test asserts `< 2500` ms for a 1 s budget. `interruptedDownloadIsTransientAndKeepsTheFlag` interrupts after a fixed `Thread.sleep(500)` and relies on that timing. `stalledBodyTimesOut` also finds the reader by scanning every thread's stack for the private method name `readAtMost`, so the test depends on an implementation detail. The CI runs the full reactor three times, the last under `unshare` with a cold JIT. A loaded runner will turn these into intermittent red builds that block the owner's release flow.
**Fix:** Use a wider upper bound that still separates the cases. The comments give 2.0 s for the correct code against 3.2 s and about 6 s for the broken variants, so about 3000 ms would still tell them apart. For the interrupt test, wait on a condition (WireMock request received, via `wm.getWireMock().getServeEvents()`) instead of sleeping 500 ms. If the reader lookup stays, derive it from a named thread (see WR-01) rather than a method name.

## Info

### IN-01: A source of `image/svg+xml` produces the unusable attachment name `image-3.svg-xml`

**File:** `morimail-html/src/main/java/de/morisoft/morimail/html/HtmlImages.java:672` (pinned by `HtmlImagesDataUriTest.java:256`)
**Issue:** `fileName` derives the extension from the whole subtype and replaces every character outside `A-Za-z0-9._-` with `-`. For `image/svg+xml` the recipient sees `image-3.svg-xml`, and the test pins this. `IMAGE_TYPE` accepts the `+` suffix subtypes on purpose, so the output is deliberate. Most mail clients do not render SVG inline anyway.
**Fix:** Cut the structured-syntax suffix before sanitizing, for example `subtype.replaceFirst("\\+.*", "")`, which gives `svg`. Alternatively reject `image/svg+xml` with a clear message, since Outlook and Graph-based clients do not display it.

### IN-02: The attachment file name is taken from a remote URL segment without length or content checks

**File:** `morimail-html/src/main/java/de/morisoft/morimail/html/HtmlImages.java:666-673`
**Issue:** `fileName` accepts any segment that is non-blank and has no `/`, `\` or control characters. That allows `.` and `..`, a name like `download.php` or `logo.exe` served as `image/png`, and names of arbitrary length, and the segment also becomes the content ID base. The name is shown to the recipient and sent to Graph. The template author is trusted, but the segment comes from a remote URL that the author may not control, such as a CDN redirect or a generated path.
**Fix:** Fall back to `id + "." + subtype` also when the segment is `.` or `..`, is longer than about 100 characters, or has no extension that matches the content type.

### IN-03: `<img alt="x"src="y">` (no whitespace between attributes) is silently not rewritten

**File:** `morimail-html/src/main/java/de/morisoft/morimail/html/HtmlImages.java:117-120`
**Issue:** The regex requires `[\s/]++` before every attribute, so a tag such as `<img alt="x"src="logo.png">` does not match. Browsers accept it as a parse error and recover. The relative `src` then stays in the HTML with no error, and the recipient sees a broken image. All other failures in this class fail loudly.
**Fix:** Allow zero separators after a closing quote: `(?:[\s/]*+(?!src[\s=/>])ATTRIBUTE)*+[\s/]*+src...`. Check the possessive quantifiers still avoid backtracking after the change. Alternatively, document the limitation in the class Javadoc next to the HTML-comment note.

### IN-04: The `CID_REFERENCE` pattern now has three copies kept in sync by comment only

**File:** `morimail-html/src/main/java/de/morisoft/morimail/html/HtmlImages.java:106-108`
**Issue:** The same regex lives in `Mail.Builder`, the sample's `Campaign` and `HtmlImages`, with "keep both equal" comments. `HtmlImagesTest` uses the helper's copy but never compares it with the core's (which is private), so a drift would go unseen. The consequence of drift is an unexpected `cid:` ID collision or a "no inline image" failure at `build()`. The end-to-end test `EmbedThroughRecordingTest` catches some, but not all, of those cases. Rule 0 argues against exposing the core constant, so a test is the cheapest guard.
**Fix:** Add a test that reads `Mail.Builder`'s pattern text from the source file, or one that feeds a small corpus (`acid:x`, `cid:`, `CID:a"b`, `(cid:a)`) through both the helper and `Mail.builder().build()` and compares the results.

### IN-05: The helper's behaviour decisions are not recorded in `docs/architecture.md`

**File:** `docs/architecture.md` (only the §10.1 row changed in this phase)
**Issue:** Spec section 12 says the download-failure categorisation, the size cap, the timeouts and the redirect rule are "decided in the phase that builds it", and `CLAUDE.md` asks for departures to be noted in `docs/architecture.md`. The decisions exist only in the `HtmlImages` Javadoc and the planning files. Examples are the transient/invalid split (429 and 5xx are transient), the 3 MiB cap, the 30 s budget, the https-to-http redirect refusal, the decision that `data:` decoding goes through the work directory, and the choice of `provided` scope for `morimail`. The `provided` scope in particular surprises consumers, because the helper's published POM does not pull in the core.
**Fix:** Add a short "morimail-html" row or section to the architecture table with these decisions and the reasons for them.

---

_Reviewed: 2026-11-23_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
