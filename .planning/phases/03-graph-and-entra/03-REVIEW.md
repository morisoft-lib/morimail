---
phase: 03-graph-and-entra
reviewed: 2026-11-22T00:00:00Z
depth: standard
files_reviewed: 9
files_reviewed_list:
  - docs/architecture.md
  - morimail/src/main/java/de/morisoft/morimail/entra/EntraClientCredentials.java
  - morimail/src/main/java/de/morisoft/morimail/graph/GraphTransport.java
  - morimail/src/main/java/de/morisoft/morimail/graph/GraphTransportProvider.java
  - morimail/src/test/java/de/morisoft/morimail/SendThroughGraphTest.java
  - morimail/src/test/java/de/morisoft/morimail/entra/EntraTokenCacheTest.java
  - morimail/src/test/java/de/morisoft/morimail/entra/EntraTokenErrorsTest.java
  - morimail/src/test/java/de/morisoft/morimail/graph/GraphErrorMappingTest.java
  - morimail/src/test/java/de/morisoft/morimail/graph/GraphRequestTest.java
findings:
  critical: 0
  warning: 1
  info: 2
  total: 3
  necessity: 0
status: issues_found
---

# Phase 3: Code Review Report (incremental re-review)

**Reviewed:** 2026-11-22
**Depth:** standard
**Files Reviewed:** 9

## Summary

This is a re-review of the fixes applied since `232d655` (WR-01..03, IN-01, IN-03..06; IN-02 skipped on purpose). I read the full diff, the surrounding code and the new tests. The necessity reference was read, and no necessity findings apply.

Fix status:

- **WR-01 (malformed token):** correct and complete. `requireToken` runs on both the first and the fresh token, before the header is built. The Entra side rejects the token before it is cached. Both tests assert that nothing is sent and the token text is never quoted.
- **WR-03 (http only for loopback):** correct. `URI.getHost()` returns `[::1]` with brackets, so that case matches. A userinfo trick such as `http://localhost@evil.example` yields host `evil.example` and is rejected. A host-less URI is rejected. There is no other entry point: the only callers of `authority(...)` and `baseUrl` are these two checks, and redirects stay disabled.
- **IN-01, IN-03, IN-04, IN-05, IN-06:** correct. Imports are consistent. `clock` is still used in Entra after `retryAfter` became static. The docs match the code. The new `SendThroughGraphTest` really exercises the 401, fresh token, resend path against the real Entra client.
- **WR-02 (address redaction):** works for the tested shapes, but the regex it added introduces a new problem (WR-01 below) and has gaps (IN-01 below).

No security vulnerabilities and no secret leaks were introduced by the fixes.

## Warnings

### WR-01: The new address-redaction regex is quadratic on server-controlled text and can hang a send

**File:** `morimail/src/main/java/de/morisoft/morimail/graph/GraphTransport.java:314-315` and `morimail/src/main/java/de/morisoft/morimail/entra/EntraClientCredentials.java:383-384`
**Issue:** `replaceAll("[^\\s'\"<>]+@[^\\s'\"<>]+", "<address>")` runs on the whole server text before the 300-character cut. The text comes from a response body that is read in full with `BodyHandlers.ofByteArray()`, with no size limit. For a long run of non-space characters that has no `@`, Java's regex tries every start position and backtracks to the end each time, so the cost is O(n^2). I measured it on this JDK with a single token of `a` characters:

| length | time |
|--------|------|
| 10,000 | 0.16 s |
| 50,000 | 3.2 s |
| 100,000 | 13 s |
| 200,000 | 51 s |

A `message` or `error_description` that is one long unbroken string (a base64 blob or a repeated character) therefore stalls the calling thread. In `EntraClientCredentials.fetch` this happens while the token lock is held, so every other sender blocks behind it. The source is Microsoft over TLS, so the trigger is unlikely. A faulty gateway or a TLS-inspecting proxy that returns JSON is a plausible cause. The old code was linear. Availability is not the main concern here, but the regression is a side effect of the fix.
**Fix:** Bound the work before the regex. Cut the text first, for example to 1000 characters, then replace addresses, then apply the final 300-character cut. Alternatively bound the quantifiers. Either form is linear, or close to it:
```java
// in both clean() methods, before the address replacement
if (text.length() > 1000) {
    text = text.substring(0, 1000);
}
text = text.replaceAll("[^\\s'\"<>]{1,320}@[^\\s'\"<>]{1,255}", "<address>").trim();
```
Add a test with a 200,000-character single-token message that finishes within a second.

## Info

### IN-01: The address pattern misses some forms and swallows trailing punctuation

**File:** `morimail/src/main/java/de/morisoft/morimail/graph/GraphTransport.java:315` and `morimail/src/main/java/de/morisoft/morimail/entra/EntraClientCredentials.java:384`
**Issue:** The pattern needs at least one allowed character directly before `@`. Because `"` and `'` are excluded, a quoted local part such as `"john doe"@example.org` is not redacted (verified: the output is unchanged). Trailing punctuation joins the match, so `user x@example.org.` loses its final period. Neither is a leak in the common Graph or Entra messages, which quote bare addresses. The architecture note says "anything that looks like a mail address", so a short caveat there would keep it accurate.
**Fix:** Accept the gap and name it in `docs/architecture.md`. Or drop the quote characters from the excluded set and rely on the `\s`, `<` and `>` bounds. That redacts more, which is the safer direction for this guarantee.

### IN-02: The Entra header-safety check is a second copy and sits next to the spec line "Do not parse or validate the access token"

**File:** `morimail/src/main/java/de/morisoft/morimail/entra/EntraClientCredentials.java:355-358, 398-407`
**Issue:** `docs/spec.md:249` says not to parse or validate the access token itself. The new `isHeaderSafe` check does validate its character set, and the same check runs again in `GraphTransport.requireToken` (`GraphTransport.java:209-222`), which is the only place a token reaches a header. The Graph check already stops every `TokenProvider`, built-in or not, before the JDK can throw an exception that quotes the header, so the Entra copy adds no protection for this repository's consumer. It also adds about ten lines (Rule 0). The only effect is that a bad token is reported as an Entra "response is not usable" error instead of a Graph one.
**Fix:** Either remove the Entra copy and its test case and keep the Graph guard, or leave it and add a line to `docs/architecture.md` saying that this is a deliberate, header-level exception to spec section 7.3.

---

_Reviewed: 2026-11-22_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
