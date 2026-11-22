---
phase: 03-graph-and-entra
reviewed: 2026-11-22T00:00:00Z
depth: standard
files_reviewed: 5
files_reviewed_list:
  - docs/architecture.md
  - morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/GraphEndToEndTest.java
  - morimail/src/main/java/de/morisoft/morimail/graph/GraphTransport.java
  - morimail/src/test/java/de/morisoft/morimail/entra/EntraTokenErrorsTest.java
  - morimail/src/test/java/de/morisoft/morimail/graph/GraphErrorMappingTest.java
findings:
  critical: 0
  warning: 1
  info: 2
  total: 3
  necessity: 0
status: issues_found
---

# Phase 3: Code Review Report (incremental, gap closure 03-06)

**Reviewed:** 2026-11-22
**Depth:** standard
**Files Reviewed:** 5
**Status:** issues_found

## Summary

Scope was the diff `5018dfd..HEAD` for the five listed files, with `GraphTransport.java` read from line 150 to the end (send, failure mapping and `clean`) in its current state.

The G-03-2 fix does what the plan says.

- `failure()` no longer reads `error.message`. The exception text is `Graph sendMail failed: HTTP <status>, code <code>, request-id <id>`, with `code` and `request-id` passed through `clean()`.
- No other code path copies the Graph body into a message. The 401, 403, 404, 429, 5xx and class-rule branches all use the same `message` string.
- The status mapping is unchanged.
- `mailboxHint` still reads the raw `code`, not the cleaned one, so the 401 mapping is unaffected by the change.
- The re-pointed tests stay meaningful. Control-character, 300-character cut, surrogate-pair and address-replacement cases now run through `error.code`, so `clean()` keeps the same coverage.
- The new `graphsOwnMessageNeverReachesTheExceptionMessage` pins both a plain and a percent-encoded address.
- `GraphEndToEndTest` now checks both the stderr stop line and the journal `errorMessage`, so the sample's two leak paths are covered.
- The `CopyOnWriteArrayList` change in `EntraTokenErrorsTest` is correct for the G-03-4 race. Appends from HttpClient and WireMock threads and iteration on the test thread are both safe. `messages` and the other `ArrayList`s are only touched by the test thread, so leaving them alone is right.
- `docs/architecture.md` is consistent with the code. It states the narrowed rule and keeps the Entra exception.

No bugs or security issues were introduced by the diff. One earlier robustness issue in the surviving `clean()` path is still open. It sits next to the changed code and matters more now that `code` is the only free-text field left.

## Warnings

### WR-01: `clean()` address regex is quadratic on a long, address-free token

**File:** `morimail/src/main/java/de/morisoft/morimail/graph/GraphTransport.java:316-317`
**Issue:** `clean()` runs `replaceAll("[^\\s'\"<>]+@[^\\s'\"<>]+", ...)` over the whole value before it cuts to 300 characters. For a long run of non-space characters with no `@`, the regex retries from every start position and scans to the end each time, so it is O(n²). 03-VERIFICATION.md measured 0.12 s, 0.49 s and 2.0 s for 10k, 20k and 40k characters. It was recorded there as an advisory and is still unfixed.
- The input is the server-controlled `error.code`, and the request-id header or inner id. Nothing caps the body (`BodyHandlers.ofByteArray()`), and `baseUrl` is configurable, so the service is not guaranteed to be Microsoft's.
- This is not a data-integrity risk. It is a CPU stall on the calling thread for an error path that the caller expects to return quickly.
- The Entra `clean` is the same pattern, according to the architecture note. It is out of this review's scope but worth the same fix.

**Fix:** Bound the input before the regex. The later cut to 300 still applies.
```java
String text = sb.length() > 1000 ? sb.substring(0, 1000) : sb.toString();
text = text.replaceAll(" {2,}", " ")
        .replaceAll("[^\\s'\"<>]+@[^\\s'\"<>]+", "<address>").trim();
```
A 1000-character prefix is enough to keep the 300-character output, and the quadratic cost is capped at about 10^6 steps. Add a test that sends a 100000-character `code` and asserts the result is still `a`x300 plus `...`.

## Info

### IN-01: The "no addresses in exception messages" claim is still a best-effort statement for `code` and `request-id`

**File:** `docs/architecture.md:30`
**Issue:** The Contracts bullet says "Exception messages never carry ... mail addresses" and then says the service fields are scrubbed by a pattern. The same document says, in the new §6.3 row, that a pattern over free text "cannot be relied on". Dropping `error.message` removes the known leak. `error.code` and `request-id` are still scrubbed only by that pattern. That is fine in practice, because Graph codes are fixed identifiers and request ids are GUIDs. The text should not promise more than the code does.
**Fix:** Optional wording: "A Graph failure message holds only fields that are identifiers in practice (status, `error.code`, `request-id`); they are still passed through the same cleaning."

### IN-02: No test shows that an address-bearing `request-id` is cleaned

**File:** `morimail/src/test/java/de/morisoft/morimail/graph/GraphErrorMappingTest.java:343-351`
**Issue:** The address-replacement test now covers only `error.code`. The `request-id` goes through `clean()` too (header, `innerError`, `innererror`), but no case puts an address, control character or long value there. The fallback test at line 317 only uses the benign `rid-inner`. A future change that drops `clean()` around `requestId` would pass the whole suite.
**Fix:** Add one case that sets the `request-id` header, or `innerError.request-id`, to `x@example.org` and asserts the message ends with `request-id <address>`.

---

_Reviewed: 2026-11-22_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
