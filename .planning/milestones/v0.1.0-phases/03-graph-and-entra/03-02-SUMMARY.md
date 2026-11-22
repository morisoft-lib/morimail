---
phase: 03-graph-and-entra
plan: 02
subsystem: auth
tags: [entra, client-credentials, ps256, pkcs12, token-cache, wiremock]

requires:
  - phase: 03-graph-and-entra
    provides: EntraClientCredentials secret flow, authority copy method (plan 03-01)
provides:
  - EntraClientCredentials certificate flow (key and certificate, or PKCS12 file) with a PS256 assertion
  - per-instance token cache with single flight, 5-minute margin and compare-and-drop freshAccessToken
  - full token error mapping with Retry-After, timeouts and cleaned Entra error text
  - throwaway PKCS12 key store for tests and its README sentence
affects: [03-03, 03-04, 03-05]

actuals:
  tokens: 14900
  tasks: 3
  commits: 3
plan_head_before: bd5a2240234c66bae6e2b5542f698bf2c880a52f
plan_head_after: 525cc37becefb6026ec0ae22358f6199a77e0636

tech-stack:
  added: []
  patterns:
    - "package-private forTest(Clock, Duration) seam for deterministic expiry, claim and timeout tests"
    - "ReentrantLock single flight with re-check; failures never cached"

key-files:
  created:
    - morimail/src/test/resources/test-keystore-throwaway.p12
    - morimail/src/test/java/de/morisoft/morimail/entra/EntraClientCredentialsTest.java
    - morimail/src/test/java/de/morisoft/morimail/entra/EntraTokenCacheTest.java
    - morimail/src/test/java/de/morisoft/morimail/entra/EntraTokenErrorsTest.java
  modified:
    - morimail/src/main/java/de/morisoft/morimail/entra/EntraClientCredentials.java
    - README.md

key-decisions:
  - "An unusable 2xx token response (not JSON, no access_token, missing or non-positive expires_in) is AuthRequiredException"
  - "expires_in is clamped to one year so a hostile value cannot overflow the Instant arithmetic"
  - "The entra package writes no log records; the log-capture test listens on the root java.util.logging logger"

requirements-completed: [ENTRA-01, ENTRA-02, ENTRA-03, ENTRA-05, ENTRA-06, ENTRA-07, ENTRA-08, ENTRA-09, TEST-01, TEST-02]

duration: 25min
completed: 2026-11-22
status: complete
---

# Phase 3 Plan 02: Entra tokens with a certificate, cached safely, failing clearly Summary

**EntraClientCredentials signs a PS256 client assertion from a key and certificate or a PKCS12 file, caches the token with single flight and compare-and-drop, and maps every token failure to transient or auth-required without a secret in any message.**

## Performance

- **Duration:** about 25 min
- **Tasks:** 3 of 3
- **Files modified:** 6 (4 created, 2 modified)

## Accomplishments

- `withCertificate(tenant, client, PrivateKey, X509Certificate)` and `withCertificate(tenant, client, Path, char[])`: the file factory needs exactly one RSA private key entry with an X.509 certificate and fails with `IllegalArgumentException` that names the file, carries the cause and never holds the password.
- The assertion header is exactly `alg` PS256, `typ` JWT and `x5t#S256`; the claims are `aud` (the token URL called), `iss`, `sub`, `jti`, `nbf`, `iat` and `exp` = `iat` + 600. A new assertion with a new `jti` and a new `Signature` instance is built per request. The test verifies the signature through its own `RSASSA-PSS` verifier and a changed input fails.
- Cache: reused until five minutes before `expires_in` (3299 s reuses, 3300 s fetches; 300 or less is never reused; a numeric string is accepted); 8 threads cause one request; `freshAccessToken` drops only an equal token, and two threads rejecting the same token cause one new request.
- Error mapping in the plan's order: 2xx with token and positive `expires_in`; other 2xx `AuthRequiredException` "not usable"; 429 and 5xx `TransientMailException` with `Retry-After` (seconds or RFC 1123 date, past date PT0S, junk empty); every other status `AuthRequiredException` with cleaned `error` and `error_description` (controls to spaces, whitespace collapsed, 300 characters plus `...`). Redirects are not followed (302 test). IO errors and timeouts are transient with the cause; an interrupted thread keeps its flag.
- `test-keystore-throwaway.p12` made by `keytool -J-Dkeystore.pkcs12.legacy` (openssl shows SHA-1 MAC and 3DES key bag); README has the `## Tests` sentence.

## Task Commits

1. Task 1 (tracer): `3b46765` feat(03-02): get Entra tokens with a certificate and a PS256 assertion
2. Task 2: `f96cc50` feat(03-02): cache Entra tokens with single flight and compare-and-drop
3. Task 3: `525cc37` feat(03-02): map every Entra token failure to transient or auth-required

## Verification

- `./mvnw -B -ntp -o verify` and `./mvnw -B -ntp -o -Prelease clean verify -Dgpg.skip -pl morimail -am`: BUILD SUCCESS after every task, no Javadoc warnings.
- EntraClientCredentialsTest 4 tests, EntraTokenCacheTest 12, EntraTokenErrorsTest 27, none skipped.
- Hygiene greps (home paths, non-zero GUIDs, non-example addresses, private key headers) found no match. `git check-ignore` reports the key store as not ignored. Acceptance greps: no `getLogger` outside comments, one `Signature.getInstance(`, one `"RSASSA-PSS"`, one `RFC_1123_DATE_TIME`, one `Duration.ofMinutes(5)`.
- Tracer gate: the full `verify` passed end to end after the tracer task before the cache task started.

## Docs differences

none. The Microsoft pages could not be fetched in this session (no web fetch tool available), so the 03-RESEARCH.md record of 2026-11-22 was followed: PS256, `x5t#S256`, the claim set and the form fields match it. Plan 03-05 should confirm against the live pages when it writes `docs/architecture.md`.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing critical functionality] Clamp expires_in**
- **Found during:** Task 2
- **Issue:** `Instant.plusSeconds` throws an unchecked `DateTimeException` for a huge `expires_in` from a hostile or broken endpoint.
- **Fix:** `expires_in` is capped at one year (31,536,000 s) before the date arithmetic.
- **Files modified:** `morimail/src/main/java/de/morisoft/morimail/entra/EntraClientCredentials.java`
- **Commit:** `f96cc50`

Otherwise the plan was executed as written. The tracer's tests were written against a not yet existing `forTest` seam, so the "see them fail first" step for Task 1 was a compile failure; for Tasks 2 and 3 the behavioural failures (9 of 12 cache tests, 10 of 27 error tests) were seen before the implementation.

## Known Stubs

None.

## Threat Flags

None beyond the plan's threat model (T-03-05 to T-03-10 are mitigated and tested; T-03-10 accepted).

## Self-Check: PASSED

- Created files exist: the key store, the three Entra test classes and the changed class.
- Commits `3b46765`, `f96cc50` and `525cc37` are on `main`; author and committer are Stephan Wald for all three.
