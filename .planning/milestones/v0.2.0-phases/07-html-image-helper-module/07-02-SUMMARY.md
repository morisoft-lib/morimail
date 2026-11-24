---
phase: 07-html-image-helper-module
plan: 02
subsystem: html-image-helper
tags: [morimail-html, remote-images, httpclient, wiremock, work-directory, java17]

requires:
  - phase: 07-html-image-helper-module
    provides: HtmlImages.embed(MailTemplate, Path), the rewrite loop, newId, fileName, the img src message form (Plan 07-01)
  - phase: 05-inline-images-core-graph
    provides: build() content-ID and image-type checks; GraphTransport 3 MiB limit and timeout constants
provides:
  - HtmlImages.embed(MailTemplate, Path, Path) that downloads http(s) image sources into a caller-owned work directory
  - package-private embed(MailTemplate, Path, Path, Duration) timeout seam, CONNECT_TIMEOUT, REQUEST_TIMEOUT, MAX_IMAGE_BYTES
  - private Fetched(bytes, contentType) record that Plan 07-03 reuses for data: sources
affects: [07-03 data URIs and ModuleRulesTest, 08 docs and release readiness]

actuals:
  tokens: 10000
  tasks: 2
  commits: 2
plan_head_before: a833ce494986c3760521d3b1fa73929df455cf17
plan_head_after: 9c82cebc97fc56c08b92544c09c6920ab8c1efa3
commits: 2

tech-stack:
  added: []
  patterns:
    - "One time budget per download: deadline taken before HTTP.send, the body read waits only for what is left, close() of the response stream ends a blocked reader"
    - "Package-private Duration overload as the timeout test seam (GraphTransport precedent)"
    - "DEBUG record names scheme, host, port and path only, built from the parsed URI"

key-files:
  created:
    - morimail-html/src/test/java/de/morisoft/morimail/html/HtmlImagesDownloadTest.java
  modified:
    - morimail-html/src/main/java/de/morisoft/morimail/html/HtmlImages.java

key-decisions:
  - "A2 (boundary part): an image of 3,145,728 bytes or more is refused, 3,145,727 passes, the comparison GraphTransport.validate uses"
  - "A3: 304, 204, 1xx and other unfollowed statuses are InvalidMailException with the status only; 408 is a 4xx and therefore Invalid (owner-confirmed 2026-11-23)"
  - "A6: no cleanup; files written before a later source fails stay in the caller-owned work directory"
  - "P9: one Duration is the whole budget of a download; close() of the response stream is the only way a reader ends"

patterns-established:
  - "Failure message form img src \"SRC\": REASON now also covers remote reasons: is not a valid URL, download failed, download failed with HTTP N, download timed out, download was interrupted, response has no image content type, image is 3 MiB or larger, work directory is not usable"

requirements-completed: [HTML-05, HTML-04]

coverage:
  - id: D1
    description: "With a work directory embed downloads an http(s) image once into a morimail-* file, gives cid: and an inline Attachment named after the URL's last segment, and both rendered mails pass build() through recording; without a work directory, and for ftp:, cid: and $$ sources, nothing is fetched"
    requirement: HTML-05
    verification:
      - kind: integration
        ref: "morimail-html/src/test/java/de/morisoft/morimail/html/HtmlImagesDownloadTest.java#downloadedImageIsSentInlineFromTheWorkDirectory"
        status: pass
      - kind: unit
        ref: "HtmlImagesDownloadTest#withoutAWorkDirectoryNothingIsDownloaded"
        status: pass
    human_judgment: false
  - id: D2
    description: "Failures map by cause: network error, timeout, interrupt, 5xx and 429 are TransientMailException; other statuses, non-image types, invalid URLs, an unusable work directory and bodies of 3 MiB or more are InvalidMailException; messages hold the src and the status only"
    requirement: HTML-05
    verification:
      - kind: unit
        ref: "HtmlImagesDownloadTest#statusDecidesTheCategory, contentTypeMustBeAnImage, networkFailuresAreTransient, interruptedDownloadIsTransientAndKeepsTheFlag, unusableWorkDirectoryFails, invalidUrlFails"
        status: pass
    human_judgment: false
  - id: D3
    description: "The 3 MiB boundary (3,145,727 passes, 3,145,728 refused, with and without Content-Length) and one 30 s budget for headers and body, with no reader thread left after the call"
    requirement: HTML-05
    verification:
      - kind: unit
        ref: "HtmlImagesDownloadTest#sizeCapIsThreeMebibytes, slowHeadersTimeOut, stalledBodyTimesOut"
        status: pass
    human_judgment: false
  - id: D4
    description: "Remote content IDs and file names come from the URL's last segment, share the ID namespace with local images, one source gives one download, and concurrent callers get separate files"
    requirement: HTML-04
    verification:
      - kind: unit
        ref: "HtmlImagesDownloadTest#fileNamesComeFromTheLastUrlSegment, sameUrlIsDownloadedOnce, sameLastSegmentGivesTwoFiles, remoteAndLocalIdsShareOneNamespace, concurrentDownloadsUseSeparateFiles, redirectIsFollowed"
        status: pass
    human_judgment: false
  - id: D5
    description: "A failed call leaves earlier files and unrelated files untouched, and the one DEBUG record shows scheme, host, port and path only, never userinfo, query or fragment"
    requirement: HTML-05
    verification:
      - kind: unit
        ref: "HtmlImagesDownloadTest#failedCallKeepsEarlierFiles, downloadIsLoggedAtDebugOnly"
        status: pass
    human_judgment: false

duration: 8 min
completed: 2026-11-23
status: complete
---

# Phase 7 Plan 02: Remote images downloaded into the work directory Summary

**`HtmlImages.embed(template, baseDir, workDir)` downloads `http(s)` images into the caller's work directory with one 30 s budget for headers and body, a hard 3 MiB cap (3,145,728 bytes refused, as Graph refuses them), an `image/` content type check and transient/invalid categories by cause, all pinned offline against WireMock.**

## Performance

- **Duration:** 8 min
- **Started:** 2026-11-23T14:37:18Z
- **Completed:** 2026-11-23T14:45:00Z
- **Tasks:** 2 (tracer, edge tests)
- **Files modified:** 2 (1 created, 1 changed)

## Accomplishments

- With a work directory, `embed` downloads each distinct `http(s)` source once, writes it to a `Files.createTempFile(workDir, "morimail-", null)` file and returns a `cid:` reference plus an inline `Attachment` whose name and ID come from the URL's last path segment (`My%20Logo.png?x=1` gives `My Logo.png` and `My-Logo`; an empty segment gives `image.png`; a control character falls back to `ID.ext`).
- One shared `HttpClient` (connect timeout 10 s, `Redirect.NORMAL`) and one budget per download: the deadline is taken before `HTTP.send`, the body is read on a `CompletableFuture` within what is left, and closing the response stream ends a blocked reader. `stalledBodyTimesOut` sees the reader thread while the body stalls and none within 1 s after the call threw.
- Failure categories by cause (D-13, A3): network error, timeout, interrupt, 5xx and 429 are `TransientMailException`; other statuses, non-image types, invalid URLs, an unusable work directory and bodies of 3 MiB or more are `InvalidMailException`. Messages hold the src as written and the status only.
- The only log record per download is at DEBUG on `de.morisoft.morimail.html.HtmlImages` and names `scheme://host[:port]/path`; a src with userinfo, query and fragment never puts them into a record.
- Without a work directory, and for `ftp:`, `cid:` and `$$` sources, no request is made.
- The core module is untouched: `git diff --name-only f9b2f57 -- morimail/` prints nothing.

## Task Commits

1. **Task 1: Tracer, one remote image downloaded into the work directory and sent twice** - `c18b26b` (feat)
2. **Task 2: Pin the download edges** - `9c82ceb` (test)

**Plan metadata:** recorded in the `docs(07-02)` commits that add this file and update STATE.md and ROADMAP.md.

## Files Created/Modified

- `morimail-html/src/main/java/de/morisoft/morimail/html/HtmlImages.java` - 3-argument `embed`, the package-private timeout seam, constants, shared client, `parseUrl`, `download`, `readAtMost`, `writeToWorkDir`, `Fetched`, class and method Javadoc
- `morimail-html/src/test/java/de/morisoft/morimail/html/HtmlImagesDownloadTest.java` - 19 test methods (30 executed cases with the parameterized status test), all on WireMock or a closed local port

## Decisions Made

- A2, A3, A6, P5 to P9 were applied as the plan flagged them (see "Owner review"); none changed during execution.
- Internal shape: `parseUrl(raw, value)` parses and checks the host once in `rewrite`, and `download(raw, uri, timeout)` takes the `URI`, so the last segment comes from the same parsed `URI` (the plan sketched `download(raw, value, timeout)`). Private only, no behaviour difference.

## Deviations from Plan

None - plan executed exactly as written.

The plan's step 7 lists 19 test method names and calls them "20 methods"; the report holds all 19 names (30 executed cases, because `statusDecidesTheCategory` runs 11 times).

## Issues Encountered

- The first run of Task 2 had one failure, in the test and not in the source: the helper that checks whether the stub sends the 3 MiB body without a Content-Length asked for `Transfer-Encoding: chunked`, but the JDK client negotiates HTTP/2 with WireMock's cleartext port and an HTTP/2 response has no such header. The check now asserts that no `Content-Length` arrives (what matters for the capped read). Measured with an HTTP/1.1 client, the same stub does send `Transfer-Encoding: chunked`.
- A temporary mutation (body deadline replaced by a fresh full timeout) made `stalledBodyTimesOut` fail on the `isLessThan(2700)` assertion, and the source was restored from the commit.

## Owner review

These are the flagged assumptions and planner choices this plan applied. Reply only where one is wrong.

- A2 (boundary part): an image of 3 MiB (3,145,728 bytes) or more is refused and 3,145,727 bytes pass, the same `>=` comparison as `GraphTransport.validate`, so the helper never admits what Graph refuses. Pinned by `sizeCapIsThreeMebibytes`, with a Content-Length and without one.
- A3: statuses D-13 does not name map to `InvalidMailException` with the status only (304, 204, 1xx, an unfollowed redirect); HTTP 408 is a 4xx and therefore invalid (confirmed by the owner 2026-11-23). Any other 2xx continues.
- A6 (Rule 0): no cleanup. Files written before a later source fails stay in the work directory, which the caller owns and cleans up; the Javadoc of the 3-argument overload says so.
- P5: no Content-Length pre-check; `readNBytes((int) MAX_IMAGE_BYTES)` bounds every read, and a result of `MAX_IMAGE_BYTES` bytes means the body is 3 MiB or more.
- P6: one `private static final HttpClient HTTP` field; the JDK starts its selector thread when `HtmlImages` loads, also for local-only use, and no holder class was added.
- P7: one DEBUG record per download with `scheme://host[:port]/path`, built from the parsed `URI` (no userinfo, query or fragment); nothing else is logged.
- P8: a private record `Fetched(byte[] bytes, String contentType)` carries a download result; Plan 07-03 reuses it for `data:` sources.
- P9: the package-private overload `embed(MailTemplate, Path, Path, Duration)` is the test seam, and its `Duration` is the whole budget of one download.
- Exception messages name the src as written (D-13), so a query or userinfo in a template URL reaches the message; the DEBUG log record omits them (P7).
- HttpRequest.timeout is re-armed by the JDK for each followed redirect, so the headers of a redirect chain can take longer than the 30 s budget; the body then gets no time. Bounding the hops too would need sendAsync with a cancelled future, which is not built (P9).
- Task 2 needed no source fix: all 30 cases passed against the Task 1 `HtmlImages.java`, so `9c82ceb` holds only the test file.
- The chunked case: with an HTTP/1.1 client the stub (`withChunkedDribbleDelay(2, 50)`) sends `Transfer-Encoding: chunked` and no Content-Length (measured). The helper's own client negotiates HTTP/2 with WireMock, where the same response is a length-less stream. Either way the capped read decides the result, and the test asserts that no Content-Length arrives.

## User Setup Required

None - no external service configuration required.

## Known Stubs

None. `data:` sources are deliberately left unchanged here and are the scope of Plan 07-03.

## Threat Flags

None. The new network surface is the plan's own T-07-07 to T-07-12, each mitigated and covered by a test (`withoutAWorkDirectoryNothingIsDownloaded`, `sizeCapIsThreeMebibytes`, `slowHeadersTimeOut`, `stalledBodyTimesOut`, `contentTypeMustBeAnImage`, `fileNamesComeFromTheLastUrlSegment`, `downloadIsLoggedAtDebugOnly`, `statusDecidesTheCategory`). Residual for T-07-08: the JDK re-arms the header timeout per redirect hop (Owner review).

## Next Phase Readiness

- Plan 07-03 may start: `Fetched`, `writeToWorkDir`, the `taken` and `cidByKey` handling and the work-directory opt-in are in place for `data:` sources and `toDataUris`.
- Temurin 17 has not run these commits yet; everything here was verified on JDK 21 only (`./mvnw -B -ntp verify`, the offline verify and the scoped release build with the Javadoc jar). The phase's final Temurin 17 check covers the HTTP and timing behaviour.

---
*Phase: 07-html-image-helper-module*
*Completed: 2026-11-23*

## Self-Check: PASSED

- FOUND: morimail-html/src/main/java/de/morisoft/morimail/html/HtmlImages.java, morimail-html/src/test/java/de/morisoft/morimail/html/HtmlImagesDownloadTest.java
- FOUND: commits c18b26b and 9c82ceb (ancestors of HEAD)
- `git diff --name-only f9b2f57 -- morimail/` prints nothing
