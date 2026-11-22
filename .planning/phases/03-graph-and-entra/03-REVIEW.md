---
phase: 03-graph-and-entra
reviewed: 2026-11-22T00:00:00Z
depth: standard
files_reviewed: 24
files_reviewed_list:
  - docs/architecture.md
  - .github/workflows/ci.yml
  - morimail/pom.xml
  - morimail-sample-serial/pom.xml
  - morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/SerialMail.java
  - morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/AccountFileTest.java
  - morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/GraphEndToEndTest.java
  - morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/SendRunTest.java
  - morimail/src/main/java/de/morisoft/morimail/BuiltInTransports.java
  - morimail/src/main/java/de/morisoft/morimail/entra/EntraClientCredentials.java
  - morimail/src/main/java/de/morisoft/morimail/graph/GraphTransport.java
  - morimail/src/main/java/de/morisoft/morimail/graph/GraphTransportProvider.java
  - morimail/src/test/java/de/morisoft/morimail/entra/EntraClientCredentialsTest.java
  - morimail/src/test/java/de/morisoft/morimail/entra/EntraTokenCacheTest.java
  - morimail/src/test/java/de/morisoft/morimail/entra/EntraTokenErrorsTest.java
  - morimail/src/test/java/de/morisoft/morimail/graph/GraphErrorMappingTest.java
  - morimail/src/test/java/de/morisoft/morimail/graph/GraphRequestTest.java
  - morimail/src/test/java/de/morisoft/morimail/graph/GraphValidateTest.java
  - morimail/src/test/java/de/morisoft/morimail/graph/StubTokenProvider.java
  - morimail/src/test/java/de/morisoft/morimail/ImportRulesTest.java
  - morimail/src/test/java/de/morisoft/morimail/LiveGraphTest.java
  - morimail/src/test/java/de/morisoft/morimail/SendThroughGraphTest.java
  - morimail/src/test/java/de/morisoft/morimail/TransportRegistryTest.java
  - README.md
findings:
  critical: 0
  warning: 3
  info: 6
  total: 9
  necessity: 0
status: issues_found
---

# Phase 3: Code Review Report

**Reviewed:** 2026-11-22
**Depth:** standard
**Files Reviewed:** 24
**Status:** issues_found

## Summary

I read the four main-code files, `SerialMail.java` and the CI/POM/doc changes in full, and checked them against spec sections 6 and 7. I read `EntraTokenCacheTest`, `StubTokenProvider`, `LiveGraphTest`, `GraphEndToEndTest` and the key parts of `GraphErrorMappingTest` and `EntraTokenErrorsTest` in detail. I only skimmed (test names and the parts that bear on the review focus) `GraphRequestTest`, `GraphValidateTest`, `SendThroughGraphTest`, `TransportRegistryTest`, `ImportRulesTest` and `AccountFileTest`.

The core logic holds up on the requested focus areas:

- **Token cache:** a volatile slot plus one lock. The unlocked fast path is safe, and `freshAccessToken` drops the cached token only when it equals the rejected one. Two threads that reject the same token share one new request, failures are never cached, and the interrupt flag is preserved.
- **401 retry:** exactly one retry, with the same body bytes. A failing fresh token propagates and sends nothing more.
- **Error mapping:** it matches spec 6.3. Its deviations (every 5xx is transient, class rules for other statuses) are documented in `docs/architecture.md`.
- **Size estimate:** `base64Size` is exact, and the estimate counts the body with the same mapper that `send` uses.
- **Secrets:** no log calls on token paths. Exception messages carry only cleaned server fields, never the raw body. Redirects are never followed.

I found no Critical issues. There are three Warnings, all about leakage and cleartext transport, and six Info items.

## Warnings

### WR-01: A malformed access token escapes `send` as an unchecked exception that contains the token

**File:** `morimail/src/main/java/de/morisoft/morimail/graph/GraphTransport.java:207-220`
**Issue:** `requireToken` only rejects null or empty. `post` then calls `HttpRequest.Builder.header("Authorization", "Bearer " + token)`. The JDK rejects a header value with CR/LF or other control characters by throwing `IllegalArgumentException("invalid header value: \"Bearer <the whole value>\"")`. I reproduced this on JDK 21: the message contains the full header value, so it contains the token. Two consequences:

1. `MailTransport.send` is documented to throw only `MailException`. This unchecked exception breaks that contract. The sample catches it in `SendCommand` (`catch (RuntimeException e)`, line ~350) and prints `e.getMessage()` to stderr, so a malformed token from a custom `TokenProvider` is printed in the clear. That violates the "never log tokens" rule.
2. Callers that handle only `MailException` (a queue worker, for example) crash instead of pausing the account.

`EntraClientCredentials` returns the token unvalidated, but real Entra tokens are JWTs and are safe. The exposure is a custom `TokenProvider`, or a hostile or garbled token endpoint answer: `text(json, "access_token")` accepts any non-empty string.

**Fix:** Validate the token before building the header, in `requireToken`, with a fixed message:
```java
private static String requireToken(String token) throws AuthRequiredException {
    if (token == null || token.isEmpty()) {
        throw new AuthRequiredException("the token provider returned no access token");
    }
    for (int i = 0; i < token.length(); i++) {
        char c = token.charAt(i);
        if (c <= ' ' || c > '~') {
            throw new AuthRequiredException("the token provider returned an access token that is not a valid header value");
        }
    }
    return token;
}
```
Apply the same check to `access_token` in `EntraClientCredentials.fetch`, so a bad token is never cached.

### WR-02: Server-echoed text goes into exception messages, journal and stdout, but the docs promise "never addresses"

**File:** `morimail/src/main/java/de/morisoft/morimail/graph/GraphTransport.java:261-262`, `morimail/src/main/java/de/morisoft/morimail/entra/EntraClientCredentials.java:342-343`, `docs/architecture.md:29`
**Issue:** `failure()` copies the Graph `error.message` (cleaned of control characters and cut at 300) into the exception message. `fetch()` does the same with Entra's `error_description`. Graph error texts are commonly of the form "The requested user 'someone@...' is invalid", and Entra descriptions name the application and tenant ids. Nothing filters addresses or ids. `docs/architecture.md:29` states "Exception messages never carry tokens, secrets, assertions, mail bodies or addresses", and `LiveGraphTest.java:41` says its output has "never ... an address". Both statements are wrong for the failure paths. The project rule is that recipient addresses are logged at DEBUG only.

The sample prints `describe(e)` to stdout and stderr and writes the message into the journal (`SendCommand` ~lines 300-340), so any echoed address is persisted and printed. The Graph error text is the only place a mailbox address can reach those outputs without the owner having supplied it.

**Fix:** Either option works:
- (a) Make the claim true, by replacing anything address-like in `clean()` in `GraphTransport`:
  ```java
  text = text.replaceAll("[^\\s'\"<>]+@[^\\s'\"<>]+", "<address>");
  ```
  If this is done, add a test that sends a 404 whose message contains `x@example.org`.
- (b) Correct the sentence in `docs/architecture.md` and the `LiveGraphTest` Javadoc to say the service's own error text is quoted and may contain a mailbox address or ids, and that consumers must treat it as personal data.

### WR-03: Plain `http` is accepted for the token authority and the Graph base URL, so the client secret and bearer token can travel in cleartext

**File:** `morimail/src/main/java/de/morisoft/morimail/entra/EntraClientCredentials.java:217-223`, `morimail/src/main/java/de/morisoft/morimail/graph/GraphTransportProvider.java:93-94`
**Issue:** Both public entry points accept any `http` or `https` URL with a host. `authority(URI)` is public API. With a typo or a copied test configuration (`http://login.microsoftonline.com`), the client secret (or the signed assertion) and later the bearer token are POSTed unencrypted, with no warning. Redirects are disabled, but nothing stops the cleartext request itself. The only legitimate `http` use in this repository is WireMock on loopback.

**Fix:** Allow `http` only for loopback hosts, in both places:
```java
static boolean secureOrLoopback(URI u) {
    if ("https".equalsIgnoreCase(u.getScheme())) return true;
    String h = u.getHost();
    return "http".equalsIgnoreCase(u.getScheme())
            && h != null && (h.equals("localhost") || h.equals("127.0.0.1") || h.equals("[::1]"));
}
```
WireMock's `getHttpBaseUrl()` is `http://localhost:<port>`, so the tests keep working. Because `graph` and `entra` may not import each other, the helper has to be written twice. That is the same trade-off as `clean` and `retryAfter`.

## Info

### IN-01: The private `Token` record's `toString()` prints the access token

**File:** `morimail/src/main/java/de/morisoft/morimail/entra/EntraClientCredentials.java:91`
**Issue:** `private record Token(String scope, String value, Instant usableUntil)` gets a generated `toString()` that includes `value`. Nothing stringifies it today, but a later debug statement or a heap or log dump would leak the token, and the project rule is that tokens are never logged.
**Fix:** Override `toString()` in the record, for example `return "Token[scope=" + scope + ", usableUntil=" + usableUntil + "]";`, or turn it into a small final class.

### IN-02: A 401 that is not about the token still discards a valid cached token

**File:** `morimail/src/main/java/de/morisoft/morimail/graph/GraphTransport.java:195-199`, `morimail/src/main/java/de/morisoft/morimail/entra/EntraClientCredentials.java:279-290`
**Issue:** The spec says a 401 for a sender without a mailbox is unconfirmed but possible. In that case the first 401 triggers `freshAccessToken(SCOPE, token)`, which drops the still-valid cached token and requests a new one. Every mail from a mailbox-less sender then costs two POSTs plus one token request, and token-endpoint traffic grows with the number of rejected mails. This is not incorrect, because the spec asks for this retry. It is worth knowing for the owner's live test and for a queue worker that sets rejected mails aside and keeps going.
**Fix:** None needed now. Check after the live run whether the mailbox 401 exists, and if it does, skip the retry when the first 401 already has a mailbox code (`mailboxHint(code)`).

### IN-03: The request-size estimate is looser than its Javadoc suggests, and the MB/MiB limits are unverified

**File:** `morimail/src/main/java/de/morisoft/morimail/graph/GraphTransport.java:51-57, 87-124`
**Issue:** The estimate counts the attachment base64, the body and a fixed 16 KiB. It ignores the subject, all recipient addresses (up to 500 are allowed), attachment names and content types. A mail with many recipients or long names can pass `validate` and still get a 413, which maps to `PermanentMailException`. The 3 MiB and 4 MiB constants are interpretations: the spec says "3 MB", and `docs/architecture.md` says the 4 MB source is a Q&A answer. If Graph means decimal MB, files from 3,000,000 to 3,145,728 bytes pass `validate` and fail at the service. The failure mode is a permanent exception after the call, not data loss, and the owner's `bigAttachmentIsAccepted` probe is the planned check.
**Fix:** After the live probe, either lower the constants or add `m.subject().length()` and the address lengths to the estimate. Until then, say in the `validate` Javadoc that the estimate is approximate.

### IN-04: Under a token-endpoint outage, waiting threads are served one after another, each with its own timeout

**File:** `morimail/src/main/java/de/morisoft/morimail/entra/EntraClientCredentials.java:259-264, 306-326`
**Issue:** Failures are deliberately not cached or shared (`failuresAreNeitherCachedNorShared` asserts four requests for four callers). With a hanging endpoint, N concurrent callers wait in a queue, and each performs its own request, up to 10 s connect plus 30 s request. The last caller is blocked for about N x 40 s. `lock.lock()` is also not interruptible, so a cancelled worker thread cannot get out of the wait. This follows the spec's "at most one request at a time" and has no effect on the serial sample. It is a robustness note for concurrent consumers.
**Fix:** Optional: use `lock.lockInterruptibly()` and map `InterruptedException` to `TransientMailException` as `fetch` does, or document the behaviour in the class Javadoc.

### IN-05: Rule 0: tolerance code for inputs the documentation says do not occur

**File:** `morimail/src/main/java/de/morisoft/morimail/entra/EntraClientCredentials.java:346-358, 383-389`, `morimail/src/main/java/de/morisoft/morimail/graph/GraphTransport.java:277-289`
**Issue:** `docs/architecture.md` says the throttling page documents `Retry-After` only in seconds, and the HTTP-date branch is "extra tolerance". It exists twice (about 10 lines each, plus an injected `Clock` use in `entra`) and has its own parametrized tests. The same applies to `expires_in` given as a string (`"3599"`), where the documented body is numeric, and to the `Clock` and `requestTimeout` constructor parameters that exist only for the `forTest` seam. Each piece is small, but Rule 0 says to build no feature the spec does not ask for. The inputs come across a trust boundary, so this is not a necessity finding, only a simplicity one.
**Fix:** Optionally drop the date branch and the textual `expires_in` until a real response needs them. This is the owner's call.

### IN-06: Test gaps and one weak assertion

**File:** `morimail/src/test/java/de/morisoft/morimail/graph/GraphErrorMappingTest.java:345-386`, `morimail/src/test/java/de/morisoft/morimail/SendThroughGraphTest.java`
**Issue:**
1. `noTokenSubjectBodyOrBearerReachesAMessageOrALogRecord` asserts that no log record contains `Sentinel`, `Bearer` or an address. The only log call in `GraphTransport` fires when a mail has both an HTML and a text body (`GraphTransport.java:133`). This test sends text-only mail, so `records` is always empty and `allSatisfy` passes vacuously. The message assertions are meaningful. The log assertion is not.
2. No test runs the real `EntraClientCredentials` behind `GraphTransport` through a 401. The two halves are tested separately against `StubTokenProvider`, so the contract "pass the rejected token as `rejectedToken`, drop only that token" is not tested end to end. `GraphEndToEndTest` has no 401 case either.
**Fix:** For (1), assert `records` is non-empty in a variant that sends an HTML and a text body, or delete the log assertion. For (2), add one `SendThroughGraphTest` case: the token stub returns `t1` then `t2`, Graph answers 401 for `Bearer t1` and 202 for `Bearer t2`, and the test checks two token requests and two sends.

---

_Reviewed: 2026-11-22_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
