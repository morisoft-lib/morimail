---
phase: 03-graph-and-entra
verified: 2026-11-22T13:00:00Z
status: human_needed
score: 5/5 must-haves verified
covered_files:
  - ".github/workflows/ci.yml"
  - ".planning/phases/03-graph-and-entra/03-01-PLAN.md"
  - ".planning/phases/03-graph-and-entra/03-01-SUMMARY.md"
  - ".planning/phases/03-graph-and-entra/03-02-PLAN.md"
  - ".planning/phases/03-graph-and-entra/03-02-SUMMARY.md"
  - ".planning/phases/03-graph-and-entra/03-03-PLAN.md"
  - ".planning/phases/03-graph-and-entra/03-03-SUMMARY.md"
  - ".planning/phases/03-graph-and-entra/03-04-PLAN.md"
  - ".planning/phases/03-graph-and-entra/03-04-SUMMARY.md"
  - ".planning/phases/03-graph-and-entra/03-05-PLAN.md"
  - ".planning/phases/03-graph-and-entra/03-05-SUMMARY.md"
  - "docs/architecture.md"
  - "morimail-sample-serial/pom.xml"
  - "morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/SerialMail.java"
  - "morimail/pom.xml"
  - "morimail/src/main/java/de/morisoft/morimail/BuiltInTransports.java"
  - "morimail/src/main/java/de/morisoft/morimail/entra/EntraClientCredentials.java"
  - "morimail/src/main/java/de/morisoft/morimail/graph/GraphTransport.java"
  - "morimail/src/main/java/de/morisoft/morimail/graph/GraphTransportProvider.java"
  - "morimail/src/test/java/de/morisoft/morimail/LiveGraphTest.java"
  - "morimail/src/test/resources/test-keystore-throwaway.p12"
  - "pom.xml"
covered_digest: "v4:sha256:0a61f305d0306fadf6b08ba2f1ab0369e519509604e0d8ce5fd1eb649ea0b149"
behavior_unverified: 0
overrides_applied: 0
re_verification:
  previous_status: human_needed
  previous_score: 5/5
  gaps_closed: []
  gaps_remaining: []
  regressions: []
advisory:
  - finding: "WR-01 (03-REVIEW): the address-redaction regex in both clean() methods is quadratic on one long unbroken server string (measured here: 40,000 chars about 2 s, 20,000 chars about 0.5 s, review: 100,000 chars about 13 s); in EntraClientCredentials it runs under the token lock"
    category: other
    reason: "Needs a body of tens of thousands of characters without whitespace in an error field, which real Graph and Entra answers do not produce; only a faulty proxy could. No must-have truth depends on it. Recommended fix: cut the text to about 1000 characters before the replaceAll, or bound the quantifiers"
    evidence_status: "reproduced by me with a stand-alone timing run; no failing test exists"
  - finding: "IN-01 (03-REVIEW): the address pattern misses a quoted local part such as \"john doe\"@example.org (verified: output unchanged) and swallows trailing punctuation"
    category: other
    reason: "The documented guarantee is 'anything that looks like a mail address'; common Graph and Entra messages quote bare addresses. Name the gap in docs/architecture.md or drop the quote characters from the excluded set"
    evidence_status: "reproduced by me"
  - finding: "IN-02 (03-REVIEW): isHeaderSafe in EntraClientCredentials duplicates GraphTransport.requireToken and sits next to spec 7.1 'do not parse or validate the access token itself'"
    category: other
    reason: "It checks only the character set needed for an Authorization header, not the token's structure, and keeps a bad token out of the cache. The Graph guard alone already protects every TokenProvider. Either remove the Entra copy or note the exception in docs/architecture.md"
    evidence_status: "code read; no behavior defect"
human_verification:
  - test: "Run the live test with a real app registration: ./mvnw -Plive test -pl morimail with the MORIMAIL_LIVE_* variables (certificate flow, then secret flow)"
    expected: "Entra accepts the PS256 client assertion with the x5t#S256 header (no AADSTS error, no clock-skew complaint) and Graph answers 202"
    why_human: "Real PS256 acceptance by Entra cannot be proven against WireMock; only the independent signature check with the certificate public key is automated"
  - test: "Provoke each row of the section 6.3 error table live (403 vs 404 vs MailboxNotEnabledForRESTAPI, an out-of-scope mailbox with MORIMAIL_LIVE_OUT_OF_SCOPE_FROM, a 401 after a fresh token)"
    expected: "Each real response maps to the documented exception; a mailbox 401 maps to SenderRejectedException through the mailboxHint rule (code contains 'mailbox'); otherwise AuthRequiredException"
    why_human: "The real error codes and the mailbox-401 assumption (spec 6.3) only exist against live Graph"
  - test: "Run the opt-in big-attachment probe (MORIMAIL_LIVE_BIG_ATTACHMENT, about 2.9 MB)"
    expected: "Graph accepts it; confirms whether the 3 MiB / 4 MiB constants in GraphTransport (binary, not decimal MB) are the right reading of the documented 3 MB / 4 MB limits"
    why_human: "Limit units and the request-size estimate can only be settled by the live service"
  - test: "Let the GitHub Actions CI run on Java 17, including the unshare --net step"
    expected: "Both ./mvnw verify and the release build pass on Temurin 17 (PSS signing, PKCS12 legacy-format loading) and the network-free ./mvnw -o verify step passes with only loopback up"
    why_human: "Only JDK 21 is installed locally and unshare --net needs root, so REPO-08's network-off proof and JDK 17 runtime behavior cannot be exercised here (the offline build with -o is green)"
  - test: "Owner decisions for the STOP checkpoint: from display name, shared mailbox, Exchange per-mailbox limits behind --rate 20, LICENSE holder, SECURITY.md contact, Co-Authored-By trailer; also confirm that Retry-After is read as seconds only (the review fix for IN-05 reversed phase decision D-10, which read seconds and HTTP dates)"
    expected: "Findings reported back before Phase 4 starts"
    why_human: "ROADMAP hard stop between Phase 3 and Phase 4; these are owner-run live checks and decisions"
---

# Phase 3: Graph and Entra Verification Report

**Phase Goal:** As a Java developer, I want to send mail through Microsoft Graph as an Entra app, so that each failure says whether to retry, drop the mail or fix the account.
**Verified:** 2026-11-22
**Status:** human_needed
**Re-verification:** Yes. The earlier report was stale: the code-review fixes (commits cce4e18 to 617edf5) changed `docs/architecture.md`, `EntraClientCredentials.java`, `GraphTransport.java` and `GraphTransportProvider.java` after it was written. I re-read all four files in full, re-ran the build, and re-checked every truth against the current code. The earlier report's three warnings (WR-01 token in an unchecked exception, WR-02 address wording, WR-03 cleartext http) are fixed in code and tests. The incremental review (03-REVIEW.md) found one new warning and two info items, all assessed below. No previous gaps existed, so no gap closed and nothing regressed.

## Goal Achievement

The goal is achieved in code. A developer can build a `graph` account through the registry, authenticate as an Entra app with a secret or a certificate, and send. Every failure arrives as one of the checked categories (transient with optional `Retry-After`, permanent, sender-rejected, auth-required), which is what tells the caller to retry, drop the mail or fix the account. I did not accept any SUMMARY claim on trust.

Evidence run by me: `./mvnw -B -ntp -o verify` is BUILD SUCCESS (morimail 304 tests, morimail-sample-serial 137 tests, 0 failures, 0 errors, 0 skipped). The tests that carry the truths all ran and passed: `GraphErrorMappingTest` 64, `EntraTokenErrorsTest` 39, `EntraTokenCacheTest` 11, `SendThroughGraphTest` 5, `GraphEndToEndTest` 9, `ImportRulesTest`, `TransportRegistryTest` 14. `git status` after the run shows only the untracked `.planning/state.json`.

### Observable Truths (ROADMAP success criteria)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | `EntraClientCredentials` gets a token from WireMock with a secret, with key and certificate, and with the checked-in PKCS12; the assertion decodes to the PS256 header with `x5t#S256` and the specified claims, and its signature verifies with the certificate's public key | VERIFIED | `EntraClientCredentials.java:325-335` builds the secret form (`client_secret`) or the assertion form (`client_assertion_type` + `client_assertion`); `:417-443` `assertion()` has header `alg` PS256, `typ` JWT, `x5t#S256` = base64url SHA-256 of `certificate.getEncoded()`, claims `aud` (token URL), `exp` = now + 600, `iss`/`sub` = client id, `jti` = random UUID, `nbf`, `iat`, signature `RSASSA-PSS` with SHA-256, MGF1 SHA-256, salt 32, base64url without padding, built anew per `fetch`. `:173-211` PKCS12 loader (exactly one RSA key entry, X.509 certificate, no password in messages). Tests: `EntraClientCredentialsTest.assertionHeaderClaimsAndSignature`, `certificateFlowFromPkcs12GetsAToken`, `keyAndCertificateFactoryGetsAToken`, `twoInstancesSendDifferentAssertions` (all ran green); `test-keystore-throwaway.p12` is present |
| 2 | Token reused until 5 min before expiry then refetched; concurrent callers cause one request; `freshAccessToken` drops only a matching token; 4xx gives auth-required with `error` and `error_description`; 5xx, 429, network, timeout give transient; no secret, assertion or token is logged | VERIFIED | `:272-284` lock-free fast path on a `volatile` slot, then `ReentrantLock` with re-check in `cachedOrFetch` (`:311-319`); `:297-309` `freshAccessToken` clears the slot only when `token.value().equals(rejectedToken)`, then re-checks; failures throw before `cached = token`, so they are never stored; `:359` `usableUntil = now + expires_in - 5 min`; `:361-364` 429/5xx transient; `:365-366` any other non-2xx is `AuthRequiredException` with cleaned `error` and `error_description`; `:339-345` IOException, timeout and interrupt are transient; `:109-112` connect timeout 10 s, no redirects, `:333` request timeout 30 s. The entra package has no logging (grep: no `System.Logger` or `LOG` there); `Token.toString()` omits the token value. Tests: `EntraTokenCacheTest.eightThreadsOnAnEmptyCacheMakeOneRequest`, `freshAccessTokenDropsTheRejectedToken`, `freshAccessTokenKeepsAnotherToken`, `twoThreadsRejectingTheSameTokenCauseOneNewRequest`; `EntraTokenErrorsTest` (status table, timeout, interrupt, `nothingSecretReachesALogOrAMessage`, authority scheme rules) |
| 3 | A `graph` account built through the registry (no `ServiceLoader` entry) sends `POST /users/{from}/sendMail` with percent-encoded from, bearer token, recipients, HTML-or-text body, base64 file attachments, `saveToSentItems` only when false; 202 returns normally; attachments of 3 MB or more (per file or in total) and unknown options are rejected | VERIFIED | `BuiltInTransports.providers()` is `List.of(new GraphTransportProvider())`; the only `META-INF/services` file is in `src/test/resources` (the `recording` transport); `TransportRegistryTest` (14 tests) passes. `GraphTransport.sendMailUri` URL-encodes the from; `requestBody` (`:132-175`) emits subject, one body (HTML if non-blank, else Text), to/cc/bcc/replyTo with optional name, `#microsoft.graph.fileAttachment` with base64 `contentBytes`, and `saveToSentItems:false` only when false; `send` treats any 2xx as accepted. `validate` (`:90-126`) rejects a file `>=` 3 MiB, a total `>=` 3 MiB (naming the files) and an estimate above the single constant `MAX_REQUEST_BYTES`. `GraphTransportProvider.create` rejects unknown keys, a non-Boolean flag, a `baseUrl` that is not https (http for loopback only) and a missing provider. Tests: `GraphRequestTest`, `GraphValidateTest`, `SendThroughGraphTest` (including the registry-built account) |
| 4 | Every error-table row maps to its exception; the message carries Graph's `error.code`, message and `request-id`, never tokens or bodies; a 401 triggers exactly one retry with a fresh token before it is mapped | VERIFIED | `GraphTransport.send` (`:191-207`): post once; on 401 call `auth.freshAccessToken(SCOPE, token)` and post the same bytes once; no other retry. `failure` (`:251-284`): 401 gives `SenderRejectedException` if the code contains "mailbox", else `AuthRequiredException`; 403 and 404 give `SenderRejectedException`; 429 and every 5xx give `TransientMailException` with `Retry-After` seconds; everything else (400, 413, other 4xx, 3xx) gives `PermanentMailException`. IOException, timeout and interrupt on either post are transient (`:231-239`). The message is built only from status, cleaned code, request-id (header, else `innerError` or `innererror`) and cleaned message; the raw body is never copied. Tests (`GraphErrorMappingTest`, 64 cases): `a401IsRetriedOnceWithTheFreshTokenAndTheSameBody`, `aFailingFreshTokenPropagatesAndSendsNothingMore`, `everyStatusMapsToItsExceptionAndCostsOneRequest`, `noTokenSubjectBodyOrBearerReachesAMessageOrALogRecord` (log assertion now non-vacuous), `twoThreadsRejectedWithTheSameTokenEachRetryOnce`; plus `SendThroughGraphTest` runs the real Entra client behind the transport through a 401 (t1 rejected, t2 sent). |
| 5 | `serial-mail send` runs the example campaign end to end against WireMock for token endpoint and Graph; `./mvnw verify` passes offline; a `live`-tagged test is skipped by default and ready for `-Plive` | VERIFIED | `SerialMail.mailer` (`:64-90`) builds `EntraClientCredentials` (secret or PKCS12, environment read through an injected function) and `MailAccount.builder(...).transport("graph")` with `baseUrl` through the registry. `GraphEndToEndTest` (9 tests, green) covers the campaign once, the rerun that sends nothing, and the exit-3 stop on a rejected mailbox. `pom.xml` sets `excludedGroups=live` and the `live` profile sets `groups=live`; `LiveGraphTest` is `@Tag("live")`, reads `MORIMAIL_LIVE_*` and fails in `@BeforeAll` naming missing variables before any network call. `.github/workflows/ci.yml:22` runs `./mvnw -o verify` in a network-less namespace (run on CI is a human item). Offline verify is green here |

**Score:** 5/5 truths verified. 0 behavior-unverified: each state, cancellation and ordering invariant (single flight, compare-and-drop, single 401 retry, failure not cached, no token on oversize) is exercised by a named passing test.

### Review fixes re-checked (commits cce4e18 to 617edf5)

| Fix | Code evidence | Status |
|-----|---------------|--------|
| WR-01 malformed token | `GraphTransport.requireToken` (`:209-222`) rejects null, empty, `<= ' '` and `> '~'` characters for the first and the fresh token, before the header is built; Entra applies `isHeaderSafe` before caching (`:355-358`). The message never quotes the token | Holds |
| WR-02 addresses in server text | both `clean` methods replace address-like text with `<address>`; `docs/architecture.md:29` states the guarantee and its limits | Holds for bare addresses (see IN-01) |
| WR-03 cleartext http | `secureOrLoopback` in both packages, written twice because `graph` and `entra` must not import each other (grep confirms no cross import except `BuiltInTransports`); `EntraTokenErrorsTest` and `GraphRequestTest` cover accepted and rejected URLs | Holds |
| IN-05 Retry-After seconds only, numeric `expires_in` only | `retryAfter` in both packages reads `\d{1,9}` only; `expiresIn` requires `isNumber()`; `docs/architecture.md:20,40,44` records it | Holds. Reverses decision D-10 (seconds and dates); documented, and flagged for the owner in the last human item |

### Judgment on the open review findings

- **WR-01 (quadratic regex): does not affect goal achievement.** I reproduced the scaling (`a` repeated 10,000 / 20,000 / 40,000 chars: 0.12 s / 0.49 s / 2.0 s). It needs an unbroken run of tens of thousands of non-space characters in `error.message`, `error.code`, `error_description` or `error`. Microsoft's error texts are short, so the normal and the tested failure paths are unaffected, and all five truths hold. It is a real robustness regression, because in Entra it runs while the token lock is held and the old code was linear, so it is a WARNING-level advisory. It is not a blocker, and I recommend the review's two-line fix (cut to about 1000 characters before the `replaceAll`) before release. It is not a gap that needs a closure plan.
- **IN-01 (quoted local part not redacted): advisory.** `"john doe"@example.org` passes through unchanged. The documented promise is "anything that looks like a mail address" and bare addresses are redacted, so GRAPH-08 (tokens and bodies) is untouched.
- **IN-02 (duplicate header check next to spec 7.1): advisory.** The Entra check inspects only the character set that an `Authorization` header allows. It does not parse the token and does not change the token passed on. Not a goal issue. It is a small Rule 0 and spec-wording question for the owner.

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `morimail/.../entra/EntraClientCredentials.java` | secret and certificate flows, cache, errors | VERIFIED | 480 lines, public, Javadoc states thread safety, wired through `SerialMail` and tests |
| `morimail/.../graph/GraphTransport.java` | request build, validate, 401 retry, error mapping | VERIFIED | 325 lines, package-private, thread-safety Javadoc, created by the provider |
| `morimail/.../graph/GraphTransportProvider.java` | id `graph`, option rules | VERIFIED | public, stateless, on the built-in list |
| `morimail/.../BuiltInTransports.java` | `graph` on the built-in list | VERIFIED | `List.of(new GraphTransportProvider())` |
| `morimail/src/test/resources/test-keystore-throwaway.p12` | TEST-01 key store | VERIFIED | present and loaded by tests |
| `morimail/src/test/java/.../LiveGraphTest.java` | live test, tag `live` | VERIFIED | excluded by default, `-Plive` selects it |
| `morimail-sample-serial/.../SerialMail.java` | real account wiring | VERIFIED | Entra credentials plus registry `graph` account |
| `.github/workflows/ci.yml` | network-free build step | VERIFIED in file (CI run is a human item) | step on line 22 |
| `docs/architecture.md` | phase deviations | VERIFIED | matches the current code (http rule, Retry-After, redaction, token response) |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `SerialMail.mailer` | `GraphTransportProvider` | `MailAccount.builder().transport("graph")` through `TransportRegistry` | WIRED | no ServiceLoader entry |
| `GraphTransport.send` | `TokenProvider.accessToken` / `freshAccessToken` | core interface only | WIRED | `graph` does not import `entra` (`ImportRulesTest` green, grep clean) |
| `EntraClientCredentials` | `TokenProvider` | `implements` | WIRED | |
| `BuiltInTransports` | `GraphTransportProvider` | explicit list | WIRED | |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|--------------------|--------|
| `GraphTransport.requestBody` | JSON body | `MailMessage` fields and `Files.readAllBytes` | Yes (WireMock captures the bytes and tests compare them) | FLOWING |
| `GraphTransport.send` | bearer token | `auth.accessToken` to `EntraClientCredentials.fetch` to the HTTP token endpoint | Yes (`SendThroughGraphTest` end to end) | FLOWING |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Full offline build | `./mvnw -B -ntp -o verify` | BUILD SUCCESS, 304 + 137 tests, 0 failures | PASS |
| Redaction regex scaling | stand-alone `String.replaceAll` of the production pattern on `a` x n | 20,000: 0.49 s, 40,000: 2.0 s (quadratic) | CONFIRMS advisory WR-01 |
| Quoted local part | same pattern on `"john doe"@example.org` | unchanged | CONFIRMS advisory IN-01 |
| Git state after runs | `git status --short` | only untracked `.planning/state.json` | PASS |

### Probe Execution

Step 7c: SKIPPED. No plan names a probe script, and `scripts/*/tests/probe-*.sh` does not exist.

### Requirements Coverage

Plan frontmatter IDs: 03-01 {ENTRA-01, ENTRA-04, GRAPH-01}; 03-02 {ENTRA-01, 02, 03, 05, 06, 07, 08, 09, TEST-01, TEST-02}; 03-03 {GRAPH-01, 02, 03, 04, 05, TEST-03}; 03-04 {GRAPH-01, 06, 07, 08, 09, TEST-03}; 03-05 {CLI-14, REPO-08}. The union is exactly the 23 phase IDs in ROADMAP.md and in the REQUIREMENTS.md traceability table (all marked Phase 3, Complete). No orphaned requirement.

| Requirement | Source Plan | Status | Evidence |
|-------------|-------------|--------|----------|
| ENTRA-01 | 03-01, 03-02 | SATISFIED | `fetch` form fields and `tokenUri`; `SendThroughGraphTest` checks the exact secret form |
| ENTRA-02 | 03-02 | SATISFIED | `assertion()`; `assertionHeaderClaimsAndSignature` (Entra's PS256 acceptance is a human item) |
| ENTRA-03 | 03-02 | SATISFIED | PKCS12 loader with tests for wrong password, two keys, non-RSA, missing file |
| ENTRA-04 | 03-01 | SATISFIED | `authority(URI)` copy method, public cloud default, https or loopback http |
| ENTRA-05 | 03-02 | SATISFIED | `EntraTokenCacheTest` |
| ENTRA-06 | 03-02 | SATISFIED | compare-and-drop tests |
| ENTRA-07 | 03-02 | SATISFIED | `EntraTokenErrorsTest` |
| ENTRA-08 | 03-02 | SATISFIED | 10 s connect, 30 s request, slow-response test |
| ENTRA-09 | 03-02 | SATISFIED | no logging in `entra`; `Token.toString()` redacted; log and message capture test |
| GRAPH-01 | 03-01, 03-03, 03-04 | SATISFIED | `sendMailUri`, 2xx accepted |
| GRAPH-02 | 03-03 | SATISFIED | body rules and INFO-once test |
| GRAPH-03 | 03-03 | SATISFIED | `fileAttachment` with base64 |
| GRAPH-04 | 03-03 | SATISFIED | provider option rules and tests |
| GRAPH-05 | 03-03 | SATISFIED | `validate` with constants (3 MiB reading of "3 MB" is a human item) |
| GRAPH-06 | 03-04 | SATISFIED | `failure()` and status-table test (Retry-After seconds only, per MS docs) |
| GRAPH-07 | 03-04 | SATISFIED | single-retry tests |
| GRAPH-08 | 03-04 | SATISFIED | message layout, body-sentinel test, address redaction (advisory IN-01) |
| GRAPH-09 | 03-03, 03-04 | SATISFIED | timeouts, including the retried request |
| TEST-01 | 03-02 | SATISFIED | key store checked in, README sentence |
| TEST-02 | 03-02 | SATISFIED | assertion, form, reuse, expiry, errors, single flight, compare-and-drop tests |
| TEST-03 | 03-03, 03-04 | SATISFIED | graph tests with `StubTokenProvider` |
| CLI-14 | 03-05 | SATISFIED | `GraphEndToEndTest` |
| REPO-08 | 03-05 | SATISFIED in code; the CI run is a human item | tag excluded by default, `-Plive`, `unshare --net` step; offline verify green here |

### Anti-Patterns Found

Debt-marker scan (`TBD`, `FIXME`, `XXX`) over the phase's main, sample and live-test files and `docs/architecture.md`: none. `TODO`, `HACK`, `PLACEHOLDER`: none. Secret and address hygiene scan of all tracked non-planning files: only `example.org`, the maintainer address and zero GUIDs.

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `GraphTransport.java` | 314-315 | WR-01: quadratic address regex on unbounded server text | Warning (advisory) | Stalls a send on a pathological body; no truth depends on it |
| `EntraClientCredentials.java` | 383-384 | same regex, under the token lock | Warning (advisory) | Same, and blocks other senders while it runs |
| both `clean()` | | IN-01: quoted local part and trailing punctuation | Info | Redaction gap, documented guarantee is "looks like an address" |
| `EntraClientCredentials.java` | 355-358, 398-407 | IN-02: second copy of the header check next to spec 7.1 | Info | None in behavior |

### Human Verification Required

See the `human_verification` list in the frontmatter. Everything is live-service or owner work that the ROADMAP STOP checkpoint reserves, and the existing `03-UAT.md` items still apply (all five still `[pending]`):

1. Live PS256 acceptance by Entra, secret and certificate flow, with `./mvnw -Plive test -pl morimail`.
2. Real section 6.3 responses: 403 vs 404 vs `MailboxNotEnabledForRESTAPI`, an out-of-scope mailbox, and whether a 401 after a fresh token exists with a mailbox code (the `mailboxHint` assumption).
3. The attachment and request-size boundary with the opt-in 2.9 MB probe (3 MiB vs decimal 3 MB, and the 4 MiB estimate constant).
4. GitHub CI on Java 17, including PSS signing, PKCS12 legacy loading and the `unshare --net` step.
5. Owner checkpoint items (from display name, shared mailbox, Exchange per-mailbox limits behind `--rate 20`, section 12 decisions) and a confirmation that Retry-After as seconds only is acceptable.

### Gaps Summary

No gaps. All 5 roadmap success criteria and all 23 requirement IDs are implemented, wired and covered by passing tests against the current code. The status is `human_needed` only because the live-service checks cannot be verified programmatically. The three open review items are advisory: the quadratic regex (WR-01) is worth the two-line fix before release because it can block a send behind the Entra token lock, but it does not fail any truth.

Phase 4 must not start before the owner's live-test report (ROADMAP hard stop).

---

_Verified: 2026-11-22_
_Verifier: Claude (gsd-verifier)_
