---
phase: 03-graph-and-entra
verified: 2026-11-22T18:30:00Z
status: passed
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
  - ".planning/phases/03-graph-and-entra/03-06-PLAN.md"
  - ".planning/phases/03-graph-and-entra/03-06-SUMMARY.md"
  - "docs/architecture.md"
  - "morimail-sample-serial/pom.xml"
  - "morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/SerialMail.java"
  - "morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/GraphEndToEndTest.java"
  - "morimail/pom.xml"
  - "morimail/src/main/java/de/morisoft/morimail/BuiltInTransports.java"
  - "morimail/src/main/java/de/morisoft/morimail/entra/EntraClientCredentials.java"
  - "morimail/src/main/java/de/morisoft/morimail/graph/GraphTransport.java"
  - "morimail/src/main/java/de/morisoft/morimail/graph/GraphTransportProvider.java"
  - "morimail/src/test/java/de/morisoft/morimail/LiveGraphTest.java"
  - "morimail/src/test/java/de/morisoft/morimail/entra/EntraTokenErrorsTest.java"
  - "morimail/src/test/java/de/morisoft/morimail/graph/GraphErrorMappingTest.java"
  - "morimail/src/test/resources/test-keystore-throwaway.p12"
  - "pom.xml"
covered_digest: "v4:sha256:e7d03544ee90281d56a440d089842a6c33eca0e242b1f50bf410f2721fa4d39a"
behavior_unverified: 0
overrides_applied: 0
re_verification:
  previous_status: human_needed
  previous_score: 5/5
  gaps_closed:
    - "G-03-4 (UAT test 4): CI run 45047907977 on Temurin 17 passed verify, release build and unshare --net verify"
    - "G-03-2 (UAT test 7): live main test printed 'HTTP 404, code ErrorInvalidUser, request-id <guid>' with no address"
    - "Previous WR-01 (whitespace-led address at the 1000-character cut): fixed in f9588ec, probe returns <address>"
  gaps_remaining: []
  regressions: []
advisory:
  - finding: "WR-01 (03-REVIEW, open): the 1000-character cut in both clean() methods runs before address replacement, so an address straddling the cut can leave its first characters in the message"
    category: other
    reason: "Reproduced by reflection probe on the compiled classes: clean('x'*996 + '@y' + ' ' + 'alice@example.org') returns '<address> a' in GraphTransport and in EntraClientCredentials. Reachable only through error.code, request-id or an Entra error field that is about 1000 characters long and holds an address at the boundary. No must-have depends on it (see Goal Achievement). Suggested fix from the review: cut at a token boundary, plus one straddling-address test per class"
    evidence_status: "reproduced by probe; no failing test exists"
  - finding: "IN-01 (03-REVIEW, open): the two whitespace-padding regression tests do not exercise a boundary inside a non-whitespace region, so they would not catch WR-01"
    category: other
    reason: "Test strength only. The tests are valid for the case they target (995 spaces then an address now gives <address>, confirmed by probe)"
    evidence_status: "test read, probe run"
  - finding: "Earlier advisories carried forward: a quoted local part is not redacted by the pattern; EntraClientCredentials.isHeaderSafe duplicates the Graph requireToken guard; Entra failure messages quote error and error_description (spec 7.3), which can name application and tenant ids; IN-01/IN-02 magic numbers are now named constants (c7c0ba5)"
    category: other
    reason: "None touches a must-have. docs/architecture.md states the address pattern is best effort and the Entra description can name application and tenant ids"
    evidence_status: "code read"
---

# Phase 3: Graph and Entra Verification Report

**Phase Goal:** As a Java developer, I want to send mail through Microsoft Graph as an Entra app, so that each failure says whether to retry, drop the mail or fix the account.
**Verified:** 2026-11-22
**Status:** passed
**Re-verification:** Yes, after the code-review fixes WR-01 and IN-02 (f9588ec, c7c0ba5), which changed four files after the previous report. The previous report had no `gaps:` section, so all five truths were re-checked against current code, with full scrutiny on the changed files.

## Goal Achievement

The goal is achieved. A developer builds a `graph` account through the registry, authenticates as an Entra app with a secret or a certificate, and sends. Every failure arrives as a checked category: `TransientMailException` (with an optional seconds-only `Retry-After`), `PermanentMailException`, `SenderRejectedException` or `AuthRequiredException`. That category tells the caller whether to retry, drop the mail or fix the account.

I did not accept SUMMARY claims. Evidence I produced:

- `./mvnw -B -ntp -o test -pl morimail -Dtest='GraphErrorMappingTest,EntraTokenErrorsTest,GraphRequestTest,GraphValidateTest,EntraClientCredentialsTest,EntraTokenCacheTest,SendThroughGraphTest,TransportRegistryTest,ImportRulesTest'`: 190 tests, 0 failures, BUILD SUCCESS (GraphErrorMappingTest 70, EntraTokenErrorsTest 41, GraphRequestTest 25, GraphValidateTest 19, EntraTokenCacheTest 11, EntraClientCredentialsTest 4, SendThroughGraphTest 5, TransportRegistryTest 14, ImportRulesTest 1).
- The orchestrator's full `./mvnw -B -ntp -o verify` on HEAD (312 library tests, 137 sample tests, 0 failures) is taken as given.
- GitHub Actions run 45047907977, read through `gh run view`: conclusion success on head sha b647b6d, with step 4 (`./mvnw -B -ntp verify`), step 5 (`-Prelease clean verify -Dgpg.skip -pl morimail -am`) and step 6 (the `unshare --net` offline verify) each green, on Temurin 17 per `ci.yml`. `git diff b647b6d..HEAD` outside `.planning` is empty, so the CI result covers the code verified here. HEAD is the two docs commits after that sha.
- A reflection probe against `morimail/target/classes` (see "Review warning WR-01" below).
- Every commit since the last report is authored and committed as Stephan Wald <stephan@morisoft.de>. `git status` shows only the untracked `.planning/state.json`.

### Review warning WR-01 weighed against the must-haves

The fresh 03-REVIEW finding is real. I reproduced it on the current compiled classes. The previous whitespace-padding variant is fixed: `" ".repeat(995) + "alice@example.org"` now returns `<address>` in both classes. The residual case is `"x".repeat(996) + "@y" + " " + "alice@example.org"`, which returns `<address> a` in both `GraphTransport.clean` and `EntraClientCredentials.clean`, because the cut at 1000 characters happens before the address pattern runs. Other probes: `ErrorInvalidUser alice@example.org` gives `ErrorInvalidUser <address>`; a 100,000-character token gives 303 characters in 14 to 27 ms (no quadratic cost); an address hidden behind a long token is cut away by the 300-character output limit.

Why it does not fail a must-have:

- **GRAPH-08** requires the message to carry `error.code` and `request-id` and never Graph's free-text `error.message`, tokens or bodies. `GraphTransport.java` never reads `error.message` (the only `"message"` literal, line 168, builds the outgoing request body). The failure message is `Graph sendMail failed: HTTP <status>, code <clean>, request-id <clean>`. Graph codes are fixed identifiers and request-ids are GUIDs. The leak needs a code or request-id of about 1000 characters with an address at the boundary, and the leaked part is one to a few leading characters. UAT test 7 confirmed live that the real 404 message holds code and request-id only.
- **ENTRA-09** covers secrets, assertions and tokens. A partial address is none of those, and `entra` writes no log records.
- The address scrub is not a requirement of its own. The project rule is "recipient addresses at DEBUG only". `docs/architecture.md` line 30 states the pattern is a best-effort safeguard, not a guarantee, and that code and request-id are identifiers in practice.

So it is a WARNING-level advisory, not a BLOCKER, and the code review is advisory. The fix is a few lines (cut at a token boundary) plus one test per class, and is worth doing before release. It does not block the phase.

## Observable Truths (ROADMAP success criteria)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | `EntraClientCredentials` gets a token from WireMock with a secret, with key and certificate, and with the checked-in PKCS12. The assertion decodes to the PS256 header with `x5t#S256` and the specified claims and verifies with the certificate's public key | VERIFIED | `EntraClientCredentials.java` sets `alg` `PS256` (line 424) and builds the `x5t#S256` header; `EntraClientCredentialsTest` and the factory tests pass in my run; `test-keystore-throwaway.p12` is present in `morimail/src/test/resources`. Live: UAT test 1 recorded Entra accepting the PS256 assertion (certificate flow 7 of 10 runs, the 403s were Exchange propagation) and the secret flow accepted. PSS signing and PKCS12 loading also ran on Temurin 17 in CI run 45047907977 |
| 2 | Token reused until 5 minutes before expiry, then refetched. Concurrent callers cause one request. `freshAccessToken` drops only a matching token. 4xx gives auth-required with `error` and `error_description`; 5xx, 429, network and timeout give transient. No secret, assertion or token is logged | VERIFIED | `EXPIRY_MARGIN` 5 min, `CONNECT_TIMEOUT` 10 s, `REQUEST_TIMEOUT` 30 s (lines 69-71). Named passing tests in `EntraTokenCacheTest`: `tokenExpiresFiveMinutesBeforeTheEnd`, `eightThreadsOnAnEmptyCacheMakeOneRequest`, `freshAccessTokenDropsTheRejectedToken`, `freshAccessTokenKeepsAnotherToken`, `twoThreadsRejectingTheSameTokenCauseOneNewRequest`, `aFailedRequestIsNotCached`. `EntraTokenErrorsTest` (41) covers error mapping and the log-capture sentinel test, now on `CopyOnWriteArrayList`. The `entra` package contains no logger |
| 3 | A `graph` account built through the registry (no `ServiceLoader` entry) sends `POST /users/{from}/sendMail` with percent-encoded from, bearer token, recipients, HTML-or-text body, base64 file attachments, `saveToSentItems` only when false; 202 returns normally; attachments of 3 MB or more (file or total) and unknown options are rejected | VERIFIED | `BuiltInTransports.java` returns `List.of(new GraphTransportProvider())`; `morimail/src/main/resources` does not exist, so there is no service entry. `GraphRequestTest` (25), `GraphValidateTest` (19), `SendThroughGraphTest` (5) and `TransportRegistryTest` (14) pass. Live: UAT test 3 sent 2,900,000 and 3,100,000 byte attachments and Graph accepted both, which confirms the binary (MiB) reading of the limits |
| 4 | Every error-table row maps to its documented exception. The message carries Graph's `error.code` and `request-id`, never Graph's free-text message, tokens or bodies. A 401 triggers exactly one retry with a fresh token before it is mapped | VERIFIED | `GraphTransport.send` calls `auth.freshAccessToken(SCOPE, token)` once (line 201) and repeats the request with the same bytes; no other retry. `GraphErrorMappingTest` (70) passes, including `a401IsRetriedOnceWithTheFreshTokenAndTheSameBody`, `aSecond401IsMappedByTheMailboxHint`, `theAnswerAfterTheRetryIsMappedLikeAnyOther`, `retryAfterIsReadAsSecondsOnly`, `theRequestAfterA401HasTheSameTimeout`, `twoThreadsRejectedWithTheSameTokenEachRetryOnce`. Live: 403 and 404 rows confirmed (UAT test 2), and UAT test 7 confirmed the printed line carries code and request-id with no address |
| 5 | `serial-mail send` runs the example campaign end to end against WireMock for the token endpoint and Graph, and `./mvnw verify` passes offline. A `live`-tagged test is skipped by default and is ready for `-Plive` | VERIFIED | `GraphEndToEndTest` is in the green sample build (137 tests). `LiveGraphTest` carries `@Tag("live")` (line 43). `ci.yml` step 6 runs verify offline under `unshare --net`, and that step passed in CI run 45047907977 |

**Score:** 5/5 truths verified, 0 behavior-unverified. Each state and ordering invariant (single flight, compare-and-drop, single 401 retry with the same body, failures not cached) has a named passing test that I ran.

## Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `morimail/src/main/java/de/morisoft/morimail/graph/GraphTransport.java` | request build, validate, 401 retry, error mapping, status/code/request-id message | VERIFIED | `send`, `failure`, `clean` read; `clean` now collapses, cuts, then replaces addresses |
| `morimail/src/main/java/de/morisoft/morimail/graph/GraphTransportProvider.java` | id `graph`, option rules | VERIFIED | on the built-in list |
| `morimail/src/main/java/de/morisoft/morimail/entra/EntraClientCredentials.java` | secret and certificate flows, cache, errors | VERIFIED | `fetch` and `clean` read; constants `MAX_ERROR_TEXT` 300 and `MAX_CLEAN_INPUT` 1000 |
| `morimail/src/main/java/de/morisoft/morimail/BuiltInTransports.java` | `graph` on the built-in list | VERIFIED | line 24 |
| `morimail/src/test/resources/test-keystore-throwaway.p12` | TEST-01 key store | VERIFIED | present |
| `GraphErrorMappingTest.java`, `EntraTokenErrorsTest.java` | error rows, address cases, log capture | VERIFIED | 70 and 41 tests, green |
| `GraphEndToEndTest.java`, `LiveGraphTest.java` | CLI-14 and the owner's live test | VERIFIED | present, tagged and wired |
| `docs/architecture.md` | recorded deviations | VERIFIED | contracts bullet (line 30) matches the code, with the best-effort wording |
| `.github/workflows/ci.yml` | network-free step | VERIFIED | three steps, all green in run 45047907977 |

## Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `SerialMail.mailer` | `GraphTransportProvider` | `transport("graph")` through the registry | WIRED | no ServiceLoader entry |
| `GraphTransport.failure` message | sample stop line and journal `errorMessage` | `MailException.getMessage()` | WIRED | `GraphEndToEndTest` asserts both stay free of Graph text |
| `GraphTransport.send` | `TokenProvider` | core interface only | WIRED | `ImportRulesTest` passes (graph and entra import each other nowhere) |
| `EntraClientCredentials` | `TokenProvider` | `implements` | WIRED | |

## Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|--------------------|--------|
| `GraphTransport.requestBody` | JSON body | `MailMessage` fields, attachment bytes | Yes (WireMock captures, tests compare bytes) | FLOWING |
| `GraphTransport.send` | bearer token | `auth.accessToken` to the HTTP token endpoint | Yes (`SendThroughGraphTest`, live UAT test 1) | FLOWING |
| `GraphTransport.failure` | code, request-id | response header and parsed `error` object | Yes (live 403 and 404 produced real values) | FLOWING |

## Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Graph, Entra, registry and import-rule suites | `./mvnw -B -ntp -o test -pl morimail -Dtest='GraphErrorMappingTest,...,ImportRulesTest'` | 190 tests, 0 failures | PASS |
| Graph's own message is never read | `grep 'get("message")' GraphTransport.java` | no match | PASS |
| Whitespace-led address at the cut (old WR-01) | reflection probe on both `clean` methods | `<address>` | PASS |
| Address straddling the cut (new WR-01) | `x*996 + "@y" + " " + "alice@example.org"` | `<address> a` in both classes | CONFIRMED (advisory) |
| Long token is cut fast | 100,000 `x` into both `clean` methods | 303 characters, 14 to 27 ms | PASS |
| Ordinary address in a code | `ErrorInvalidUser alice@example.org` | `ErrorInvalidUser <address>` | PASS |
| Debt markers in library, sample, architecture.md, graph/entra tests | `grep -E "TBD\|FIXME\|XXX"` | none | PASS |
| CI on Temurin 17 | `gh run view 45047907977` | success; verify, release build, `unshare --net` verify all green | PASS |

## Probe Execution

Step 7c: SKIPPED. No plan names a probe script and `scripts/*/tests/probe-*.sh` does not exist.

## Requirements Coverage

Plan frontmatter IDs: 03-01 {ENTRA-01, ENTRA-04, GRAPH-01}; 03-02 {ENTRA-01, 02, 03, 05, 06, 07, 08, 09, TEST-01, TEST-02}; 03-03 {GRAPH-01, 02, 03, 04, 05, TEST-03}; 03-04 {GRAPH-01, 06, 07, 08, 09, TEST-03}; 03-05 {CLI-14, REPO-08}; 03-06 {GRAPH-08, TEST-03, ENTRA-09, TEST-02, REPO-07, REPO-08}. Their union covers exactly the 23 phase IDs in the task (ENTRA-01 to 09, GRAPH-01 to 09, TEST-01 to 03, CLI-14, REPO-08), and every one is marked Complete in REQUIREMENTS.md. Plan 03-06 also lists REPO-07, a Phase 1 ID it re-touched (CI file), which is Complete under Phase 1. No orphaned requirement: REQUIREMENTS.md maps no other ID to Phase 3.

| Requirement | Source Plan | Status | Evidence |
|-------------|-------------|--------|----------|
| ENTRA-01 | 03-01, 03-02 | SATISFIED | form fields in `fetch`; `SendThroughGraphTest`; live secret flow accepted |
| ENTRA-02 | 03-02 | SATISFIED | assertion test with independent signature check; live Entra accepted the PS256 assertion |
| ENTRA-03 | 03-02 | SATISFIED | PKCS12 loader tests; legacy PKCS12 loads on Temurin 17 (CI) |
| ENTRA-04 | 03-01 | SATISFIED | `authority(URI)` copy method, recorded in architecture.md |
| ENTRA-05 | 03-02 | SATISFIED | `EntraTokenCacheTest` (single flight, failures not cached) |
| ENTRA-06 | 03-02 | SATISFIED | compare-and-drop tests |
| ENTRA-07 | 03-02 | SATISFIED | `EntraTokenErrorsTest` |
| ENTRA-08 | 03-02 | SATISFIED | `CONNECT_TIMEOUT` 10 s, `REQUEST_TIMEOUT` 30 s |
| ENTRA-09 | 03-02, 03-06 | SATISFIED | no logger in `entra`; thread-safe log-capture test passes on JDK 21 and on Temurin 17 (CI) |
| GRAPH-01 | 03-01, 03-03, 03-04 | SATISFIED | `sendMailUri`, 2xx accepted |
| GRAPH-02 | 03-03 | SATISFIED | body rules and INFO-once test (`LOG` line 138) |
| GRAPH-03 | 03-03 | SATISFIED | `fileAttachment` with base64 |
| GRAPH-04 | 03-03 | SATISFIED | provider option rules |
| GRAPH-05 | 03-03 | SATISFIED | `GraphValidateTest`; live probe confirmed the MiB reading |
| GRAPH-06 | 03-04 | SATISFIED | `failure()` status table; 403 and 404 rows live-confirmed |
| GRAPH-07 | 03-04 | SATISFIED | single-retry tests |
| GRAPH-08 | 03-04, 03-06 | SATISFIED | message is status, cleaned code, cleaned request-id; no read of `error.message`; WR-01 is a best-effort scrub edge, not a breach (see above) |
| GRAPH-09 | 03-03, 03-04 | SATISFIED | timeouts, including the retried request (`theRequestAfterA401HasTheSameTimeout`) |
| TEST-01 | 03-02 | SATISFIED | key store checked in |
| TEST-02 | 03-02, 03-06 | SATISFIED | assertion, form, reuse, expiry, errors, single flight, compare-and-drop, log capture |
| TEST-03 | 03-03, 03-04, 03-06 | SATISFIED | Graph tests with a stub token provider |
| CLI-14 | 03-05, 03-06 | SATISFIED | `GraphEndToEndTest` |
| REPO-08 | 03-05, 03-06 | SATISFIED | `live` tag excluded by default, `-Plive`; offline verify green locally and under `unshare --net` on Temurin 17 in CI |

## Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `GraphTransport.java` | 320-322 | WR-01: cut before address replacement | Warning (advisory) | A leading fragment of an address can survive for a code or request-id of about 1000 characters |
| `EntraClientCredentials.java` | 386-388 | WR-01, same order | Warning (advisory) | Same, for the Entra `error` and `error_description` |
| `EntraTokenErrorsTest.java`, `GraphErrorMappingTest.java` | whitespace-padding tests | IN-01: tests do not cover a boundary inside a non-whitespace region | Info | Would not catch WR-01 |

No `TBD`, `FIXME` or `XXX` in any file touched by the phase. The fix commits add only `example.org` addresses.

## Human Verification Required

None. The items the previous report listed are covered by `03-UAT.md`: the Temurin 17 CI run (test 4, gap G-03-4, now green in run 45047907977), the live re-check of G-03-2 (test 7, pass), the secure-phase re-run (test 8, pass), and the owner decisions (tests 5 and 6, pass). Tests 2 and 4 are recorded as `issue` in the UAT file, and both gaps are marked resolved by plan 03-06 with the confirming evidence above. The UAT summary line (6 passed, 2 issues) was written before those confirmations and the owner may refresh it.

For the record, the owner chose not to provoke these live: an out-of-scope mailbox (RBAC), `MailboxNotEnabledForRESTAPI`, a mailbox 401 after a fresh token, the `from` display-name rendering, and a shared mailbox as sender (UAT tests 2 and 5). Each mapping is covered by WireMock tests (`GraphErrorMappingTest`), and the owner accepted the 403 and 404 rows as sufficient. They are not must-haves of this phase, so they are recorded here and not as open items.

## Gaps Summary

No gaps. All 5 roadmap success criteria and all 23 requirement IDs are implemented, wired and backed by passing tests against the current code. The CI run on Temurin 17 passed, the owner's live runs are recorded in UAT, and both UAT gaps are resolved. The one open review warning (WR-01, address straddling the 1000-character cut) is reproduced and recorded as an advisory. It does not breach GRAPH-08 or ENTRA-09, and the fix before release is small. Phase 4 may start.

---

_Verified: 2026-11-22_
_Verifier: Claude (gsd-verifier)_
