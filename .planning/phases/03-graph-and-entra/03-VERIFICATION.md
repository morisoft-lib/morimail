---
phase: 03-graph-and-entra
verified: 2026-11-22T12:00:00Z
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
covered_digest: "v4:sha256:22a60b81bce1dd9a36970722de2de826e8a3cd28c3461a5b0d5915d65399c2a3"
behavior_unverified: 0
overrides_applied: 0
human_verification:
  - test: "Run the live test with a real app registration: ./mvnw -Plive test -pl morimail with the MORIMAIL_LIVE_* variables (certificate flow, then secret flow)"
    expected: "Entra accepts the PS256 client assertion with the x5t#S256 header (no AADSTS error, no clock-skew complaint) and Graph answers 202"
    why_human: "Real PS256 acceptance by Entra cannot be proven against WireMock; only the independent signature check with the certificate public key is automated"
  - test: "Provoke each row of the section 6.3 error table live (403 vs 404 vs MailboxNotEnabledForRESTAPI, an out-of-scope mailbox with MORIMAIL_LIVE_OUT_OF_SCOPE_FROM, a 401 after a fresh token)"
    expected: "Each real response maps to the documented exception; a mailbox 401 maps to SenderRejectedException through the mailboxHint rule (code contains 'mailbox'); otherwise AuthRequiredException"
    why_human: "The real error codes and the mailbox-401 assumption (spec 6.3, IN-02 of the review) only exist against live Graph"
  - test: "Run the opt-in big-attachment probe (MORIMAIL_LIVE_BIG_ATTACHMENT, about 2.9 MB)"
    expected: "Graph accepts it; confirms whether the 3 MiB / 4 MiB constants in GraphTransport (binary, not decimal MB) are the right interpretation of the documented 3 MB / 4 MB limits"
    why_human: "Limit units and the request-size estimate (review IN-03) can only be settled by the live service"
  - test: "Let the GitHub Actions CI run on Java 17, including the new unshare --net step"
    expected: "Both ./mvnw verify and the release build pass on Temurin 17 (PSS signing, PKCS12 legacy-format loading) and the network-free ./mvnw -o verify step passes with only loopback up"
    why_human: "Only JDK 21 is installed locally, and unshare --net needs root, so REPO-08's network-off proof and JDK 17 runtime behaviour cannot be exercised here (the offline build with -o is green)"
  - test: "Owner decisions for the STOP checkpoint: from display name, shared mailbox, Exchange per-mailbox limits behind --rate 20, LICENSE holder, SECURITY.md contact, Co-Authored-By trailer"
    expected: "Findings reported back before Phase 4 starts"
    why_human: "ROADMAP hard stop between Phase 3 and Phase 4; these are owner-run live checks and decisions"
---

# Phase 3: Graph and Entra Verification Report

**Phase Goal:** As a Java developer, I want to send mail through Microsoft Graph as an Entra app, so that each failure says whether to retry, drop the mail or fix the account.
**Verified:** 2026-11-22
**Status:** human_needed
**Re-verification:** No, initial verification

## Goal Achievement

Goal achieved in code. Every roadmap success criterion is backed by implementation I read and by passing tests I ran: `./mvnw -B -ntp -o verify` is BUILD SUCCESS (morimail 286 tests, morimail-sample-serial 137 tests, 0 failures, 0 skipped). The `live` test is excluded from `verify`. With `-Plive` and no environment it fails before any network call and names every missing variable. What remains are live-only checks that the roadmap itself reserves for the owner (STOP checkpoint). No SUMMARY claim was accepted on trust.

### Observable Truths (ROADMAP success criteria)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | `EntraClientCredentials` gets a token from WireMock with a secret, with key + certificate, and with the checked-in PKCS12; the assertion decodes to the PS256 header with `x5t#S256` and the specified claims, and its signature verifies with the certificate's public key | VERIFIED | `EntraClientCredentials.java:307-311` (secret form vs `client_assertion` form), `:391-417` (header alg/typ/x5t#S256 = base64url SHA-256 of `certificate.getEncoded()`; claims aud/exp(+600)/iss/jti UUID/nbf/sub/iat; `RSASSA-PSS` with SHA-256, MGF1 SHA-256, salt 32; base64url no padding; new assertion per request), `:168-206` (PKCS12 loader, RSA only). `EntraClientCredentialsTest.assertionHeaderClaimsAndSignature` decodes both parts, checks every claim, verifies the signature with the cert public key and checks a tampered input fails; `certificateFlowFromPkcs12GetsAToken`, `keyAndCertificateFactoryGetsAToken`, `twoInstancesSendDifferentAssertions`; `SendThroughGraphTest` checks the exact secret form. `test-keystore-throwaway.p12` exists |
| 2 | Token reused until 5 min before expiry then refetched; concurrent callers cause one request; `freshAccessToken` drops only a matching token; 4xx -> auth-required with error and error_description; 5xx, 429, network, timeout -> transient; no secret, assertion or token logged | VERIFIED | `EntraClientCredentials.java:253-304` (volatile slot + `ReentrantLock`, re-check inside the lock, compare-and-drop via `token.value().equals(rejectedToken)`, failures never stored), `:336` (`expires_in` minus 5 min), `:338-343` (429/5xx transient with Retry-After, else `AuthRequiredException` with cleaned error and description), `:320-326` (IOException/timeout/interrupt -> transient), `:104-107` connect timeout, `:314` request timeout, no redirects. Tests: `EntraTokenCacheTest` (reuse, expiry margin, `eightThreadsOnAnEmptyCacheMakeOneRequest`, `freshAccessTokenDropsTheRejectedToken`, `freshAccessTokenKeepsAnotherToken`, `twoThreadsRejectingTheSameTokenCauseOneNewRequest`, failed request not cached), `EntraTokenErrorsTest` (27 tests: status table, Retry-After, reset, timeout, interrupt, `nothingSecretReachesALogOrAMessage`). No log call in the entra package |
| 3 | A `graph` account built through the registry (no `ServiceLoader` entry) sends `POST /users/{from}/sendMail` with percent-encoded from, bearer token, recipients, HTML-or-text body, base64 file attachments, `saveToSentItems` only when false; 202 returns normally; attachments of 3 MB or more (per file or in total) and unknown options are rejected | VERIFIED | `BuiltInTransports.java` lists `GraphTransportProvider`; no `META-INF/services` in main (only the test `recording` entry); `TransportRegistryTest` asserts `graph` is found and absent from the service-loader ids. `GraphTransport.requestBody` (`:130-173`) builds subject, one body (HTML if non-blank else Text), to/cc/bcc/replyTo with optional name, `#microsoft.graph.fileAttachment` + base64, `saveToSentItems:false` only when false; `sendMailUri` URL-encodes the from. `validate` (`:88-124`) rejects `>=` 3 MiB per file, `>=` 3 MiB total and an estimate above the single constant `MAX_REQUEST_BYTES`, naming the files. `GraphTransportProvider.create` rejects unknown keys, wrong types, bad `baseUrl`, missing auth. Tests: `GraphRequestTest` (documented JSON, `saveToSentItems` true/absent/false, both-bodies INFO once, option errors), `GraphValidateTest` (boundary cases, umlaut and quote accounting, no token before oversize), `SendThroughGraphTest` (exact URL, headers, form, JSON, encoded `+`/`@`, registry-built account) |
| 4 | Every error-table row maps to its exception; message carries Graph's `error.code`, message and `request-id`, never tokens or bodies; a 401 triggers exactly one retry with a fresh token before it is mapped | VERIFIED | `GraphTransport.send` (`:190-205`) posts once, on 401 calls `auth.freshAccessToken(SCOPE, token)` and re-posts the same bytes once; `failure` (`:241-274`): 403/404 -> `SenderRejectedException`; 429 and every 5xx -> `TransientMailException` with parsed `Retry-After` (seconds or date); 400, 413, other 4xx/3xx -> `PermanentMailException`; second 401 -> `SenderRejectedException` when code contains "mailbox" else `AuthRequiredException`; IOException/timeout -> transient; message built only from status, cleaned code, request-id (header, else `innerError`/`innererror`) and cleaned message, never the raw body. Tests (`GraphErrorMappingTest`, 61 tests): `a401IsRetriedOnceWithTheFreshTokenAndTheSameBody` (t1 rejected, t2 sent, identical bytes, `rejectedTokens == [t1]`), `everyStatusMapsToItsExceptionAndCostsOneRequest`, Retry-After, redirects not followed, body-sentinel never shown, 300-char cut, timeout on the retried request, parallel sends |
| 5 | `serial-mail send` runs the example campaign end to end against WireMock for token endpoint and Graph; `./mvnw verify` passes offline; a `live`-tagged test is skipped by default and ready for `-Plive` | VERIFIED | `SerialMail.mailer` (`:64-90`) builds `EntraClientCredentials` and `.transport("graph")` with `baseUrl` through the registry. `GraphEndToEndTest.exampleCampaignIsSentOnceAndARerunSendsNothing`: 5 progress lines, `accepted 5, skipped 0, failed 0`, exit 0, exactly one token request with a `client_assertion` and no `client_secret`, 5 sends each with info.pdf and logo.png and base64 content, rerun produces zero WireMock events; `aRejectedMailboxStopsTheRunWithExitThree` (exit 3, clara journaled `SenderRejectedException`, david and emma never requested); secret flow and pre-flight cases. `pom.xml:57` excludes group `live`; `-Plive` selects it (`:168-173`). I ran the live test with no environment: it fails in `@BeforeAll` listing the missing variables, before any network call. `.github/workflows/ci.yml` has the network-free `unshare --net` step |

**Score:** 5/5 truths verified (0 behavior-unverified: every state/cancellation invariant, single-flight, compare-and-drop and the single 401 retry, is exercised by a passing named test)

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `morimail/.../entra/EntraClientCredentials.java` | secret + certificate flows, cache, errors | VERIFIED | 454 lines, public, Javadoc states thread safety |
| `morimail/.../graph/GraphTransport.java` | request build, validate, 401 retry, error mapping | VERIFIED | 322 lines, package-private, thread-safety Javadoc |
| `morimail/.../graph/GraphTransportProvider.java` | id `graph`, option rules | VERIFIED | public, stateless |
| `morimail/.../BuiltInTransports.java` | `graph` on the built-in list | VERIFIED | `List.of(new GraphTransportProvider())` |
| `morimail/src/test/resources/test-keystore-throwaway.p12` | TEST-01 key store | VERIFIED | present, used by tests (password `changeit`) |
| `morimail/src/test/java/.../LiveGraphTest.java` | live test, tag `live` | VERIFIED | fails fast without env, excluded by default |
| `morimail-sample-serial/.../SerialMail.java` | real account wiring | VERIFIED | Entra credentials plus registry `graph` account |
| `.github/workflows/ci.yml` | network-free build step | VERIFIED (run is human item) | step present |
| `docs/architecture.md` | phase deviations | VERIFIED | records deviations and differences from the Microsoft docs |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `SerialMail.mailer` | `GraphTransportProvider` | `MailAccount.builder().transport("graph")` through `TransportRegistry` | WIRED | no ServiceLoader entry needed |
| `GraphTransport.send` | `TokenProvider.accessToken/freshAccessToken` | core interface only | WIRED | `graph` does not import `entra` (ImportRulesTest green) |
| `EntraClientCredentials` | `TokenProvider` | `implements` | WIRED | |
| `BuiltInTransports` | `GraphTransportProvider` | explicit list | WIRED | |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|--------------------|--------|
| `GraphTransport.requestBody` | JSON body | `MailMessage` fields and `Files.readAllBytes` | Yes (WireMock captures real bytes and tests compare them) | FLOWING |
| `GraphTransport.send` | bearer token | `auth.accessToken` -> `EntraClientCredentials.fetch` -> HTTP token endpoint | Yes | FLOWING |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Full offline build | `./mvnw -B -ntp -o verify` | BUILD SUCCESS, 286 + 137 tests, 0 failures | PASS |
| Live test without env | `./mvnw -o -Plive test -pl morimail -Dtest=LiveGraphTest` (env cleared) | fails before any network call, names missing variables | PASS |
| Git state after runs | `git status --short` | only untracked `.planning/state.json` | PASS |

### Probe Execution

Step 7c: SKIPPED, the phase declares no probe scripts (`scripts/*/tests/probe-*.sh` do not exist and no plan names one).

### Requirements Coverage

Plan frontmatter IDs: 03-01 {ENTRA-01, ENTRA-04, GRAPH-01}; 03-02 {ENTRA-01, 02, 03, 05, 06, 07, 08, 09, TEST-01, TEST-02}; 03-03 {GRAPH-01, 02, 03, 04, 05, TEST-03}; 03-04 {GRAPH-01, 06, 07, 08, 09, TEST-03}; 03-05 {CLI-14, REPO-08}. Union = all 23 phase IDs. REQUIREMENTS.md maps exactly these 23 to Phase 3. No orphaned requirement.

| Requirement | Source Plan | Status | Evidence |
|-------------|-------------|--------|----------|
| ENTRA-01 | 03-01, 03-02 | SATISFIED | form fields and URL in `fetch`; `SendThroughGraphTest` exact form |
| ENTRA-02 | 03-02 | SATISFIED | `assertion()`; `assertionHeaderClaimsAndSignature` (PS256 acceptance by Entra: human item) |
| ENTRA-03 | 03-02 | SATISFIED | PKCS12 loader and tests (wrong password, two keys, non-RSA, missing file) |
| ENTRA-04 | 03-01 | SATISFIED | `authority(URI)` copy method, default public cloud |
| ENTRA-05 | 03-02 | SATISFIED | `EntraTokenCacheTest` |
| ENTRA-06 | 03-02 | SATISFIED | compare-and-drop tests |
| ENTRA-07 | 03-02 | SATISFIED | `EntraTokenErrorsTest` |
| ENTRA-08 | 03-02 | SATISFIED | 10 s connect, 30 s request, slow-response test |
| ENTRA-09 | 03-02 | SATISFIED | no logging in entra; message and log capture test |
| GRAPH-01 | 03-01, 03-03, 03-04 | SATISFIED | `sendMailUri`, 202 accepted |
| GRAPH-02 | 03-03 | SATISFIED | body rules and INFO-once test |
| GRAPH-03 | 03-03 | SATISFIED | fileAttachment base64 |
| GRAPH-04 | 03-03 | SATISFIED | provider option rules and tests |
| GRAPH-05 | 03-03 | SATISFIED | `validate`; limits as constants (3 vs 3 MiB interpretation: human item) |
| GRAPH-06 | 03-04 | SATISFIED | `failure()` and status-table test |
| GRAPH-07 | 03-04 | SATISFIED | single retry tests |
| GRAPH-08 | 03-04 | SATISFIED | message layout, body sentinel test (see advisory WR-02/WR-01) |
| GRAPH-09 | 03-03/03-04 | SATISFIED | timeouts, including the retried request |
| TEST-01 | 03-02 | SATISFIED | keystore checked in, README sentence |
| TEST-02 | 03-02 | SATISFIED | assertion, form, reuse, expiry, errors, single flight, compare-and-drop tests |
| TEST-03 | 03-03, 03-04 | SATISFIED | graph tests with `StubTokenProvider` |
| CLI-14 | 03-05 | SATISFIED | `GraphEndToEndTest` |
| REPO-08 | 03-05 | SATISFIED in code, run in CI is a human item | tag excluded by default, `-Plive`, CI `unshare --net` step; offline `-o verify` green here |

### Anti-Patterns Found

Debt-marker scan (TBD/FIXME/XXX) on the phase's main files found none. The code-review findings (03-REVIEW.md, 0 critical, 3 warnings, 6 info, all open in 03-REVIEW-DISPOSITION.md) were re-checked against the source. I confirm each warning is real, and none breaks a roadmap truth on the normal path:

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `GraphTransport.java` | 207-212 | WR-01: `requireToken` accepts a token with control characters; the JDK `header(...)` then throws an unchecked `IllegalArgumentException` whose message holds the token | Warning | Only a custom `TokenProvider` or a hostile token endpoint can trigger it (real Entra tokens are JWTs). Violates the "send throws only MailException" contract and, via the sample's `RuntimeException` handler, could print a token. Does not affect criteria 1 to 5 as tested |
| `GraphTransport.java` / `EntraClientCredentials.java` | 261-262 / 342-343 | WR-02: server `error.message` / `error_description` is quoted and can contain an address or ids, while `docs/architecture.md:29` and `LiveGraphTest` Javadoc say "never addresses" | Warning | GRAPH-08 itself (never tokens or bodies) holds. The documentation claim is stronger than the code |
| `EntraClientCredentials.java`, `GraphTransportProvider.java` | 217-223 / 93-94 | WR-03: plain `http` is accepted for authority and `baseUrl` | Warning | A misconfiguration could send the secret or token in cleartext. Spec 7.1 requires the override and states no HTTPS rule |
| `EntraClientCredentials.java` | 91 | IN-01: private `Token` record `toString()` prints the token | Info | No call stringifies it today |
| tests | | IN-06: `noTokenSubjectBodyOrBearerReachesAMessageOrALogRecord` log assertion is vacuous (text-only mail); no end-to-end 401 test with the real `EntraClientCredentials` behind `GraphTransport` | Info | The retry and the compare-and-drop are each tested on both sides of the `TokenProvider` seam, so the contract is covered in pieces |

IN-02 to IN-05 are design notes (extra token request on a mailbox 401, looser size estimate, unfair queueing under an outage, Rule 0 tolerance code) and do not break a must-have.

### Human Verification Required

See the `human_verification` list in the frontmatter. In short:

1. **Live PS256 acceptance by Entra**, secret flow and certificate flow, with `./mvnw -Plive test -pl morimail`.
2. **Real section 6.3 responses**: 403 vs 404 vs `MailboxNotEnabledForRESTAPI`, an out-of-scope mailbox, and whether a 401 after a fresh token exists and carries a mailbox code (the `mailboxHint` assumption).
3. **Attachment and request-size boundary** with the opt-in 2.9 MB probe (3 MiB vs decimal 3 MB, and the 4 MiB estimate constant).
4. **GitHub CI on Java 17**, including PSS signing, PKCS12 legacy-format loading and the new `unshare --net` offline step (neither JDK 17 nor root is available locally).
5. **Owner checkpoint items**: `from` display name, shared mailbox, Exchange per-mailbox limits behind `--rate 20`, the open section 12 decisions (LICENSE holder, SECURITY.md contact, Co-Authored-By trailer).

### Gaps Summary

No gaps. All 5 roadmap success criteria and all 23 requirement IDs are implemented, wired and covered by passing tests. The status is `human_needed` only because the live-service checks reserved for the owner by the ROADMAP STOP checkpoint cannot be verified programmatically. The three open review warnings (WR-01 token in an unchecked exception for a malformed token, WR-02 address wording, WR-03 cleartext `http`) are recommended hardening for the owner or a `/gsd-code-review 03 --fix` pass before release. They are not goal gaps. WR-01 is the one worth fixing first, because it touches the never-log-a-token rule.

Phase 4 must not start before the owner's live-test report (ROADMAP hard stop).

---

_Verified: 2026-11-22_
_Verifier: Claude (gsd-verifier)_
