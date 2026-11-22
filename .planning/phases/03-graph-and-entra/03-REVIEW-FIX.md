---
phase: 03-graph-and-entra
fixed_at: 2026-11-22T00:00:00Z
review_path: /home/coder/morimail/.planning/phases/03-graph-and-entra/03-REVIEW.md
iteration: 1
findings_in_scope: 9
fixed: 8
skipped: 1
status: partial
---

# Phase 3: Code Review Fix Report

**Fixed at:** 2026-11-22
**Source review:** /home/coder/morimail/.planning/phases/03-graph-and-entra/03-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope: 9 (fix_scope `all`; no necessity findings)
- Fixed: 8
- Skipped: 1

**Verification:** every commit was checked with `./mvnw -B -ntp -o verify` (green, offline) inside the isolated review-fix worktree. After the fast-forward, `./mvnw -B -ntp -o verify` was run once more in the main checkout and passed. The library Javadoc (release profile) built cleanly; the sample module could not be resolved under `-Prelease -DskipTests` because the test-jar is not built when tests are skipped, which is unrelated to these changes. No live test was run and no mail was sent.

## Fixed Issues

### WR-01: A malformed access token escapes `send` as an unchecked exception that contains the token

**Files modified:** `morimail/src/main/java/de/morisoft/morimail/graph/GraphTransport.java`, `morimail/src/main/java/de/morisoft/morimail/entra/EntraClientCredentials.java`, `morimail/src/test/java/de/morisoft/morimail/graph/GraphErrorMappingTest.java`, `morimail/src/test/java/de/morisoft/morimail/entra/EntraTokenErrorsTest.java`
**Commit:** cce4e18
**Status:** fixed: requires human verification (validation logic)
**Applied fix:** `requireToken` rejects a token with a blank, control or non-ASCII character with a fixed `AuthRequiredException` message, before the header is built. `EntraClientCredentials.fetch` applies the same check to `access_token`, so a bad token is never cached. New tests cover a space, CR/LF and a non-ASCII character, and assert that nothing is sent and the token is never quoted.

### WR-02: Server-echoed text goes into exception messages, journal and stdout, but the docs promise "never addresses"

**Files modified:** `morimail/src/main/java/de/morisoft/morimail/graph/GraphTransport.java`, `morimail/src/main/java/de/morisoft/morimail/entra/EntraClientCredentials.java`, `docs/architecture.md`, `morimail/src/test/java/de/morisoft/morimail/graph/GraphErrorMappingTest.java`, `morimail/src/test/java/de/morisoft/morimail/entra/EntraTokenErrorsTest.java`
**Commit:** dc60f51
**Status:** fixed: requires human verification (regex)
**Applied fix:** Option (a). Both `clean` methods replace anything that looks like a mail address with `<address>`, so the documented guarantee holds. The architecture note now also says the quoted text is the service's own and that the Entra description can still name application and tenant ids. Tests send a 404 and a 400 whose text contains `x@example.org`.

### WR-03: Plain `http` is accepted for the token authority and the Graph base URL

**Files modified:** `morimail/src/main/java/de/morisoft/morimail/entra/EntraClientCredentials.java`, `morimail/src/main/java/de/morisoft/morimail/graph/GraphTransportProvider.java`, `docs/architecture.md`, `morimail/src/test/java/de/morisoft/morimail/graph/GraphRequestTest.java`, `morimail/src/test/java/de/morisoft/morimail/entra/EntraTokenErrorsTest.java`
**Commit:** 24b5638
**Status:** fixed: requires human verification (condition)
**Applied fix:** `authority(URI)` and the `baseUrl` option require https; http is accepted only for `localhost`, `127.0.0.1` and `[::1]`. The helper is written twice because `graph` and `entra` may not import each other. The error messages and Javadoc say so. Tests cover accepted and rejected URLs; the WireMock tests keep working because `getHttpBaseUrl()` is `http://localhost:<port>`.

### IN-01: The private `Token` record's `toString()` prints the access token

**Files modified:** `morimail/src/main/java/de/morisoft/morimail/entra/EntraClientCredentials.java`
**Commit:** 74d5823
**Applied fix:** The record overrides `toString()` and leaves out the token value. No test was added: the record is private and nothing stringifies it.

### IN-03: The request-size estimate is looser than its Javadoc suggests

**Files modified:** `morimail/src/main/java/de/morisoft/morimail/graph/GraphTransport.java`
**Commit:** 5a38aba
**Applied fix:** The review said to document the approximation until the live probe is done. `validate` now has a Javadoc that says the estimate omits the subject, recipients and attachment names, and that the 3 MiB and 4 MiB limits are readings of the documentation, not confirmed by a live probe. The constants are unchanged.

### IN-04: Under a token-endpoint outage, waiting threads are served one after another

**Files modified:** `morimail/src/main/java/de/morisoft/morimail/entra/EntraClientCredentials.java`
**Commit:** 9305395
**Applied fix:** The optional fix was taken as documentation only: the class Javadoc now says callers queue during an outage, each with its own 10 s connect and 30 s request timeout, and that a thread waiting for its turn cannot be interrupted. `lockInterruptibly` was not introduced (Rule 0).

### IN-05: Rule 0: tolerance code for inputs the documentation says do not occur

**Files modified:** `morimail/src/main/java/de/morisoft/morimail/graph/GraphTransport.java`, `morimail/src/main/java/de/morisoft/morimail/entra/EntraClientCredentials.java`, `docs/architecture.md`, `morimail/src/test/java/de/morisoft/morimail/graph/GraphErrorMappingTest.java`, `morimail/src/test/java/de/morisoft/morimail/entra/EntraTokenErrorsTest.java`, `morimail/src/test/java/de/morisoft/morimail/entra/EntraTokenCacheTest.java`
**Commit:** 3f736b7
**Status:** fixed: requires human verification (behaviour change)
**Applied fix:** Removed the HTTP-date branch of `Retry-After` in both packages (now seconds only; a date gives no delay) and the textual `expires_in` (a string now makes the token response unusable). `docs/architecture.md` and the tests were updated; the string-`expires_in` cache test became an unusable-response case. The `Clock` and `requestTimeout` constructor parameters were kept: the cache-expiry and timeout tests need them, and removing them would be a refactor, not a deletion. Note that this undoes decision D-10 of the phase context (both forms); the owner may want to confirm.

### IN-06: Test gaps and one weak assertion

**Files modified:** `morimail/src/test/java/de/morisoft/morimail/graph/GraphErrorMappingTest.java`, `morimail/src/test/java/de/morisoft/morimail/SendThroughGraphTest.java`
**Commit:** 617edf5
**Applied fix:** (1) The no-leak test now also sends a mail with both an HTML and a text body, so a log record exists, and asserts `records` is not empty before the content check. (2) New `SendThroughGraphTest` case runs the real `EntraClientCredentials` behind the Graph transport: the token endpoint returns `t1` then `t2`, Graph answers 401 for `Bearer t1` and 202 for `Bearer t2`, and the test checks two token requests and two sends.

## Skipped Issues

### IN-02: A 401 that is not about the token still discards a valid cached token

**File:** `morimail/src/main/java/de/morisoft/morimail/graph/GraphTransport.java:195-199`, `morimail/src/main/java/de/morisoft/morimail/entra/EntraClientCredentials.java:279-290`
**Reason:** skipped by design: the review states "None needed now". The retry on a 401 is what spec 6.3 asks for, and whether a mailbox 401 exists is only known after the owner's live run. If it does, skip the retry when the first 401 already has a mailbox code (`mailboxHint(code)`).
**Original issue:** A 401 for a sender without a mailbox triggers a fresh-token request that drops the still-valid cached token, so every mail from such a sender costs two POSTs plus one token request.

---

_Fixed: 2026-11-22_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
