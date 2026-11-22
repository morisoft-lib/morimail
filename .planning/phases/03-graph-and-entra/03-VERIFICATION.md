---
phase: 03-graph-and-entra
verified: 2026-11-22T18:00:00Z
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
covered_digest: "v4:sha256:214d1526fae69d726703cc182e4a59fca2422aba60ec171a7282a53846c54e06"
behavior_unverified: 0
overrides_applied: 0
re_verification:
  previous_status: human_needed
  previous_score: 5/5
  gaps_closed: []
  gaps_remaining: []
  regressions: []
advisory:
  - finding: "WR-01 (03-REVIEW, open): in both clean() methods the 1000-character cut runs before whitespace collapse, so a service string that starts with roughly 700 or more whitespace characters and then holds an address can be cut inside the address. The address pattern then no longer matches and the local-part prefix stays in the message"
    category: other
    reason: "Reproduced by me against the compiled classes by reflection: clean(' '.repeat(995) + 'alice@example.org') returns 'alice' in both GraphTransport and EntraClientCredentials. Reachable only through error.code, request-id or an Entra error field that is mostly whitespace. GRAPH-08 (code and request-id only, never Graph's free-text error.message, tokens or bodies) and ENTRA-09 (no secret, assertion or token) are met. The address scrub is documented as best effort in docs/architecture.md. Suggested fix, from the review: collapse whitespace first, then cut, then run the address pattern, plus one test with 995 spaces and an address"
    evidence_status: "reproduced by reflection probe; no failing test exists"
  - finding: "IN-01 (03-REVIEW, open): the two 'quickly' regression tests (EntraTokenErrorsTest, GraphErrorMappingTest) use @Timeout(10) with a 100,000-character input, a thin margin against the quadratic cost they guard"
    category: other
    reason: "Test strength only. The cut itself is verified: a 100,000-character input returns 303 characters at once"
    evidence_status: "test read, probe run; no behavior defect"
  - finding: "IN-02 (03-REVIEW, open): the literal 1000 is duplicated in two files, and Graph repeats 300 as a literal"
    category: other
    reason: "Maintainability only. A shared helper would couple graph and entra, which the architecture forbids"
    evidence_status: "code read"
  - finding: "Earlier advisories carried forward: a quoted local part ('\"john doe\"@example.org') is not redacted by the pattern; EntraClientCredentials.isHeaderSafe duplicates the Graph requireToken guard; Entra failure messages quote error and error_description (spec 7.3), which can name application and tenant ids"
    category: other
    reason: "None touches a must-have; docs/architecture.md says so since f084394"
    evidence_status: "code read"
human_verification:
  - test: "Push and read the GitHub Actions run on Temurin 17: all three steps, ./mvnw -B -ntp verify, the release build (-Prelease clean verify -Dgpg.skip -pl morimail -am) and the network-free ./mvnw -o verify under unshare --net"
    expected: "All three steps green. EntraTokenErrorsTest.nothingSecretReachesALogOrAMessage no longer throws ConcurrentModificationException (CI run 45016998414 failed twice on it); PSS signing and legacy PKCS12 loading work on JDK 17; the release and unshare --net steps, which never ran, pass. If step 1 fails on a content assertion instead, report the logger name, never the record text"
    why_human: "UAT test 4 (G-03-4) is still recorded as issue. The local branch is 23 commits ahead of origin/main and the newest CI run on GitHub (45016998414, 2026-11-22T11:23Z) is the failed one. There is no JDK 17 here and unshare --net needs root, so only a CI run closes G-03-4 and proves REPO-08's network-off step"
  - test: "Optional live re-check of G-03-2 (UAT test 7, pending): re-run the live main test with a sender that has no Exchange mailbox (the case that returned 404 ErrorInvalidUser)"
    expected: "The printed line reads 'Graph sendMail failed: HTTP 404, code ErrorInvalidUser, request-id <guid>' with no address and no Graph sentence, and the exception is SenderRejectedException"
    why_human: "WireMock proves the code path, but only live Graph produces the real text that leaked. The owner runs live tests; Claude never sends real mail"
  - test: "Re-run verify-work for phase 03 so UAT tests 2 and 4 are recorded again (03-UAT.md keeps tests 1, 3, 5 and 6 as pass; tests 2 and 4 as issue; tests 7 and 8 pending)"
    expected: "UAT tests 2 and 4 move from issue to pass once the two items above are done"
    why_human: "UAT results are the owner's record; the verifier does not edit 03-UAT.md"
  - test: "Section 6.3 rows and sender variants the owner chose not to provoke live (UAT tests 2 and 5): an out-of-scope mailbox (RBAC), MailboxNotEnabledForRESTAPI, a mailbox 401 after a fresh token, the from display name rendering, a shared mailbox as sender"
    expected: "Each maps to the documented exception; the display name appears as sent"
    why_human: "Owner accepted the 403 and 404 rows as sufficient. The rest exist only against live Graph"
  - test: "Optional (UAT test 8, pending): re-run secure-phase 03"
    expected: "T-03-19 assumed address-free messages; 03-06 makes that true for Graph, and the 03-REVIEW fixes keep it, so the threat can be re-confirmed"
    why_human: "Security sign-off is the owner's decision. 03-SECURITY.md currently reads threats_open: 0"
---

# Phase 3: Graph and Entra Verification Report

**Phase Goal:** As a Java developer, I want to send mail through Microsoft Graph as an Entra app, so that each failure says whether to retry, drop the mail or fix the account.
**Verified:** 2026-11-22
**Status:** human_needed
**Re-verification:** Yes. The previous report had no `gaps:` section, so its five truths were re-checked against the current code. Full scrutiny went to what changed after it: commits 2f1e499 (WR-01 cut), f084394 (architecture.md wording), 691c615 (IN-02 tests).

## Goal Achievement

The goal is achieved in code. A developer builds a `graph` account through the registry, authenticates as an Entra app (secret or certificate) and sends. Each failure arrives as a checked category: transient (optionally with a seconds-only `Retry-After`), permanent, sender-rejected or auth-required. That category is what tells the caller to retry, drop the mail or fix the account. The two UAT gaps (G-03-2, G-03-4) are closed in code. What is left is one CI result and owner-run live checks. Neither is a code defect I can find.

I did not accept SUMMARY claims. Evidence I produced:

- `./mvnw -B -ntp -o test -pl morimail -Dtest='GraphErrorMappingTest,EntraTokenErrorsTest,GraphRequestTest,EntraClientCredentialsTest,EntraTokenCacheTest'`: 69 + 25 + 4 + 11 + 40 = 149 tests, 0 failures, BUILD SUCCESS.
- The orchestrator's full `./mvnw -B -ntp -o verify` on HEAD (library 310 tests, sample 137, 0 failures) is taken as given. It ran on JDK 21 only.
- `git diff bdd91fb..HEAD` outside `.planning` touches exactly five files: `docs/architecture.md`, `EntraClientCredentials.java`, `GraphTransport.java`, `EntraTokenErrorsTest.java`, `GraphErrorMappingTest.java`. No POM, `ci.yml` or sample change.
- A reflection probe against `morimail/target/classes` (see WR-01 below).
- `git status` shows only the untracked `.planning/state.json`. All recent commits are authored and committed as Stephan Wald <stephan@morisoft.de>.

### WR-01 weighed against the requirements

The review warning is real. I reproduced it: `clean(" ".repeat(995) + "alice@example.org")` returns `alice` in both `GraphTransport` and `EntraClientCredentials`, because the 1000-character cut runs before whitespace collapse. The cut lands inside the address, the `@` is lost and the pattern cannot match. A second probe, `"a".repeat(990) + " alice@example.org"`, shows the address is hidden by the 300-character output cut as intended. Ordinary text such as `ErrorInvalidUser alice@example.org` gives `ErrorInvalidUser <address>`. A 100,000-character token gives 303 characters at once.

Why it does not fail a must-have:

- **GRAPH-08** requires the message to carry `error.code` and `request-id` and never Graph's free-text `error.message`, tokens or bodies. The code never reads `error.message` (`grep 'get("message")'` finds nothing), and the message is exactly `"Graph sendMail failed: HTTP <status>, code <clean>, request-id <clean>"`. Graph codes are fixed identifiers and request-ids are GUIDs. The leak needs a code or request-id of about 700 or more whitespace characters followed by an address.
- **ENTRA-09** covers secrets, assertions and tokens. The partial local part is none of those.
- The address scrub is not a requirement of its own. The project rule is "recipient addresses at DEBUG only", and `docs/architecture.md` now states the pattern is best effort and that code and request-id are identifiers in practice (f084394).
- The input is service-controlled, and the leaked part is a local-part prefix only.

So it is a WARNING-level advisory, not a BLOCKER. It is a real inconsistency with the "anything that looks like an address is replaced" comment. The fix is short (collapse, then cut, then pattern, plus one test) and worth doing before release. It does not block the phase.

## Observable Truths (ROADMAP success criteria)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | `EntraClientCredentials` gets a token from WireMock with a secret, with key and certificate, and with the checked-in PKCS12. The assertion decodes to the PS256 header with `x5t#S256` and the specified claims and verifies with the certificate's public key | VERIFIED | `EntraClientCredentials.java` documents and builds PS256 with `x5t#S256`; `EntraClientCredentialsTest` and the factory tests pass in my run; `test-keystore-throwaway.p12` is present. Live: UAT test 1 recorded Entra accepting the PS256 assertion (certificate flow 7 of 10 runs, the 403s were Exchange propagation) and the secret flow accepted |
| 2 | Token reused until 5 minutes before expiry, then refetched. Concurrent callers cause one request. `freshAccessToken` drops only a matching token. 4xx gives auth-required with `error` and `error_description`; 5xx, 429, network and timeout give transient. No secret, assertion or token is logged | VERIFIED | `EXPIRY_MARGIN = 5 min`, 10 s connect and 30 s request timeouts in the source. `EntraTokenCacheTest` (11) covers single flight and compare-and-drop; `EntraTokenErrorsTest` (40) covers error mapping, the log-capture sentinel test and the new long-description case. All passed in my run. The entra package writes no log records |
| 3 | A `graph` account built through the registry (no `ServiceLoader` entry) sends `POST /users/{from}/sendMail` with percent-encoded from, bearer token, recipients, HTML-or-text body, base64 file attachments, `saveToSentItems` only when false; 202 returns normally; attachments of 3 MB or more (file or total) and unknown options are rejected | VERIFIED | `BuiltInTransports` returns `new GraphTransportProvider()`; `morimail/src/main/resources` does not exist, so there is no service entry. `GraphRequestTest` (25) passed in my run; `GraphValidateTest`, `SendThroughGraphTest` and `TransportRegistryTest` are in the orchestrator's green build. Live: UAT test 3 sent 2,900,000 and 3,100,000 byte attachments and Graph accepted both, which confirms the MiB constants |
| 4 | Every error-table row maps to its documented exception. The message carries Graph's `error.code` and `request-id`, never Graph's free-text message, tokens or bodies. A 401 triggers exactly one retry with a fresh token before it is mapped | VERIFIED | `GraphTransport.send`: one post, on 401 `freshAccessToken(SCOPE, token)` and one repeat with the same bytes, no other retry. `failure()`: 401 to `SenderRejectedException` if the code contains "mailbox", else `AuthRequiredException`; 403 and 404 to `SenderRejectedException`; 429 and 5xx to `TransientMailException` with seconds-only `Retry-After`; the rest to `PermanentMailException`; IO and interrupt in `post()` to `TransientMailException`. `GraphErrorMappingTest` (69, including the new request-id and long-code cases) passed. Live: 403 and 404 rows confirmed (UAT test 2) |
| 5 | `serial-mail send` runs the example campaign end to end against WireMock for the token endpoint and Graph, and `./mvnw verify` passes offline. A `live`-tagged test is skipped by default and is ready for `-Plive` | VERIFIED (CI step is a human item) | `GraphEndToEndTest` is in the green sample build (137 tests). `LiveGraphTest` carries `@Tag("live")`. `ci.yml` line 22 holds the `unshare --net` offline step; it has never run on CI (human item 1) |

**Score:** 5/5 truths verified. 0 behavior-unverified: each state and ordering invariant (single flight, compare-and-drop, single 401 retry, failures not cached) has a named passing test. The Temurin 17 outcome is a CI result, listed as a human item and not counted as verified.

## Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `morimail/.../graph/GraphTransport.java` | request build, validate, 401 retry, error mapping, status/code/request-id message | VERIFIED | read `send`, `failure`, `retryAfter`, `clean` in full |
| `morimail/.../graph/GraphTransportProvider.java` | id `graph`, option rules | VERIFIED | on the built-in list |
| `morimail/.../entra/EntraClientCredentials.java` | secret and certificate flows, cache, errors | VERIFIED | `fetch` and `clean` read; cut added in 2f1e499 |
| `morimail/.../BuiltInTransports.java` | `graph` on the built-in list | VERIFIED | line 24 |
| `morimail/src/test/resources/test-keystore-throwaway.p12` | TEST-01 key store | VERIFIED | present |
| `GraphErrorMappingTest.java`, `EntraTokenErrorsTest.java` | error rows, address cases, log capture | VERIFIED | 69 and 40 tests, green |
| `GraphEndToEndTest.java`, `LiveGraphTest.java` | CLI-14 and the owner's live test | VERIFIED | present, tagged and wired |
| `docs/architecture.md` | recorded deviations | VERIFIED | contracts bullet matches the code, now with the best-effort wording |
| `.github/workflows/ci.yml` | network-free step | VERIFIED in file | CI run is human item 1 |

## Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `SerialMail.mailer` | `GraphTransportProvider` | `transport("graph")` through the registry | WIRED | no ServiceLoader entry |
| `GraphTransport.failure` message | sample stop line and journal `errorMessage` | `MailException.getMessage()` | WIRED | `GraphEndToEndTest` asserts both stay free of Graph text |
| `GraphTransport.send` | `TokenProvider` | core interface only | WIRED | `ImportRulesTest` (graph and entra import each other nowhere) is in the green build |
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
| Graph and Entra suites | `./mvnw -B -ntp -o test -pl morimail -Dtest='GraphErrorMappingTest,EntraTokenErrorsTest,GraphRequestTest,EntraClientCredentialsTest,EntraTokenCacheTest'` | 149 tests, 0 failures | PASS |
| Graph's own message is never read | `grep 'get("message")' GraphTransport.java` | no match | PASS |
| Whitespace-led address at the cut (WR-01) | reflection probe on `clean` in both classes | returns `alice` | CONFIRMED (advisory) |
| Long token is cut fast | reflection probe, 100,000 `x` into Entra `clean` | 303 characters, immediate | PASS |
| Ordinary address in a code | reflection probe, `ErrorInvalidUser alice@example.org` | `ErrorInvalidUser <address>` | PASS |
| Debt markers in `graph`, `entra`, `docs/architecture.md` | `grep -n -E "TBD|FIXME|XXX"` | none | PASS |

## Probe Execution

Step 7c: SKIPPED. No plan names a probe script and `scripts/*/tests/probe-*.sh` does not exist.

## Requirements Coverage

Plan frontmatter IDs: 03-01 {ENTRA-01, ENTRA-04, GRAPH-01}; 03-02 {ENTRA-01, 02, 03, 05, 06, 07, 08, 09, TEST-01, TEST-02}; 03-03 {GRAPH-01, 02, 03, 04, 05, TEST-03}; 03-04 {GRAPH-01, 06, 07, 08, 09, TEST-03}; 03-05 {CLI-14, REPO-08}; 03-06 {GRAPH-08, TEST-03, ENTRA-09, TEST-02, REPO-07, REPO-08}. Their union covers exactly the 23 phase IDs (ENTRA-01 to 09, GRAPH-01 to 09, TEST-01 to 03, CLI-14, REPO-08), and every one is marked complete in REQUIREMENTS.md. 03-06 also lists REPO-07, a Phase 1 ID it re-touched. No orphaned requirement.

| Requirement | Source Plan | Status | Evidence |
|-------------|-------------|--------|----------|
| ENTRA-01 | 03-01, 03-02 | SATISFIED | form fields in `fetch`; `SendThroughGraphTest`; live secret flow accepted |
| ENTRA-02 | 03-02 | SATISFIED | assertion test; live Entra accepted the PS256 assertion |
| ENTRA-03 | 03-02 | SATISFIED | PKCS12 loader tests |
| ENTRA-04 | 03-01 | SATISFIED | `authority(URI)` copy method, recorded in architecture.md |
| ENTRA-05 | 03-02 | SATISFIED | `EntraTokenCacheTest` |
| ENTRA-06 | 03-02 | SATISFIED | compare-and-drop tests |
| ENTRA-07 | 03-02 | SATISFIED | `EntraTokenErrorsTest` |
| ENTRA-08 | 03-02 | SATISFIED | `CONNECT_TIMEOUT` 10 s, `REQUEST_TIMEOUT` 30 s |
| ENTRA-09 | 03-02, 03-06 | SATISFIED | no logging in `entra`; thread-safe log-capture test (Temurin 17 proof pending, human item 1) |
| GRAPH-01 | 03-01, 03-03, 03-04 | SATISFIED | `sendMailUri`, 2xx accepted |
| GRAPH-02 | 03-03 | SATISFIED | body rules and INFO-once test |
| GRAPH-03 | 03-03 | SATISFIED | `fileAttachment` with base64 |
| GRAPH-04 | 03-03 | SATISFIED | provider option rules |
| GRAPH-05 | 03-03 | SATISFIED | `validate` constants; live probe confirmed the MiB reading |
| GRAPH-06 | 03-04 | SATISFIED | `failure()` status table; 403 and 404 rows live-confirmed |
| GRAPH-07 | 03-04 | SATISFIED | single-retry tests |
| GRAPH-08 | 03-04, 03-06 | SATISFIED | message is status, cleaned code, cleaned request-id; WR-01 is a best-effort scrub edge, not a breach (see above) |
| GRAPH-09 | 03-03, 03-04 | SATISFIED | timeouts, including the retried request |
| TEST-01 | 03-02 | SATISFIED | key store checked in |
| TEST-02 | 03-02, 03-06 | SATISFIED | assertion, form, reuse, expiry, errors, single flight, compare-and-drop, log capture |
| TEST-03 | 03-03, 03-04, 03-06 | SATISFIED | Graph tests with a stub token provider |
| CLI-14 | 03-05, 03-06 | SATISFIED | `GraphEndToEndTest` |
| REPO-08 | 03-05, 03-06 | SATISFIED in code; Temurin 17 `unshare --net` run is human item 1 | `live` tag excluded by default, `-Plive`, offline verify green |

## Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `GraphTransport.java` | 316-317 | WR-01: cut before whitespace collapse | Warning (advisory) | Partial local part possible only for a mostly-whitespace code or request-id |
| `EntraClientCredentials.java` | 383-385 | WR-01, same order | Warning (advisory) | Same, for the Entra `error` and `error_description` |
| `EntraTokenErrorsTest.java`, `GraphErrorMappingTest.java` | 211-219, 361-366 | IN-01: thin `@Timeout(10)` margin | Info | Weak regression guard |
| both `clean()` methods | - | IN-02: literal 1000 (and 300 in Graph) | Info | Maintainability |

No `TBD`, `FIXME` or `XXX` in the files touched. The three fix commits add only `example.org` addresses.

## Human Verification Required

See the `human_verification` list in the frontmatter. It matches `03-UAT.md`: UAT test 4 stays `issue` until a CI run is green, test 2 stays `issue` until the owner's optional live re-check (test 7), test 8 is pending, and tests 1, 3, 5 and 6 stay `pass`. The most important item is the Temurin 17 CI run.

## Gaps Summary

No gaps. All 5 roadmap success criteria and all 23 requirement IDs are implemented, wired and backed by passing tests against the current code. Status is `human_needed` because G-03-4 and REPO-08's network-off step can only be closed by a CI run that has not happened (the newest run on GitHub is the earlier failed one, and HEAD is 23 commits ahead of origin). WR-01 is a confirmed but contrived scrub-order edge. It does not breach GRAPH-08 or ENTRA-09, and the fix before release is small. Phase 4 must not start before the owner's CI result and live report (ROADMAP hard stop).

---

_Verified: 2026-11-22_
_Verifier: Claude (gsd-verifier)_
