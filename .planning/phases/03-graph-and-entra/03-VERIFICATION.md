---
phase: 03-graph-and-entra
verified: 2026-11-22T13:30:00Z
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
covered_digest: "v4:sha256:ad3203ac14cabcc518432d0176a8904d50bd7e4b9ae075b766b5aa4733dce870"
behavior_unverified: 0
overrides_applied: 0
re_verification:
  previous_status: human_needed
  previous_score: 5/5
  gaps_closed:
    - "G-03-2 (UAT test 2): Graph's free-text error.message no longer enters any exception message, so the sender address cannot reach the exception text, the live test line, the sample's stop line or the journal errorMessage. Closed in code and tests."
  gaps_remaining:
    - "G-03-4 (UAT test 4): the thread-safe root-logger capture is in place and passes locally, but the failure only reproduced on CI Temurin 17. Closure is complete only when the owner's CI run is green (human item 1)."
  regressions: []
advisory:
  - finding: "WR-01 (03-REVIEW, still open): the address-redaction regex in both clean() methods is quadratic on one long unbroken server string (0.12 s / 0.49 s / 2.0 s for 10k / 20k / 40k characters, measured in the previous verification); in EntraClientCredentials it runs under the token lock. After 03-06 error.code and request-id are the only free-text fields left in GraphTransport.clean"
    category: other
    reason: "Needs tens of thousands of non-space characters in error.code, a request-id or an Entra error field, which real Graph and Entra answers do not produce; a faulty proxy could. No must-have depends on it. Fix recommended before release: cut the text to about 1000 characters before the replaceAll (both packages), plus one test with a 100000-character code"
    evidence_status: "reproduced earlier by stand-alone timing; code re-read now (GraphTransport.java:316-317 unchanged); no failing test exists"
  - finding: "IN-01 (03-REVIEW): docs/architecture.md says exception messages never carry addresses and also says a pattern over free text cannot be relied on; code and request-id are still scrubbed only by that pattern"
    category: other
    reason: "Graph codes are fixed identifiers and request-ids are GUIDs, so no leak is known. Optional wording change so the document promises no more than the code does"
    evidence_status: "doc read against code; no behavior defect"
  - finding: "IN-02 (03-REVIEW): no test puts an address, control character or long value into request-id (header, innerError, innererror); the address case now runs through error.code only"
    category: other
    reason: "A future change that drops clean() around requestId would pass the suite. Add one case with request-id x@example.org expecting 'request-id <address>'"
    evidence_status: "test read (GraphErrorMappingTest around the fallback test); confirmed no such case"
  - finding: "Earlier advisories carried forward: a quoted local part ('\"john doe\"@example.org') is not redacted by the pattern; EntraClientCredentials.isHeaderSafe duplicates the Graph requireToken guard next to spec 7.1; Entra failure messages still quote error and error_description (spec 7.3), which can name application and tenant ids"
    category: other
    reason: "Reported earlier; none touches a must-have. With Graph's error.message dropped, the quoted-local-part gap now only concerns the Entra description"
    evidence_status: "code read; unchanged by 03-06"
human_verification:
  - test: "Push and read the GitHub Actions run on Temurin 17: all three steps, ./mvnw -B -ntp verify, the release build (-Prelease clean verify -Dgpg.skip -pl morimail -am) and the network-free ./mvnw -o verify under unshare --net"
    expected: "All three steps green. EntraTokenErrorsTest.nothingSecretReachesALogOrAMessage no longer throws ConcurrentModificationException (CI run 45016998414 failed twice on it); PSS signing and legacy PKCS12 loading work on JDK 17; the release and unshare --net steps, which never ran before, pass. If step 1 fails on a content assertion instead, report the logger name, never the record text"
    why_human: "G-03-4 does not reproduce on the local JDK 21 (five extra isolated runs of the test here, all green); there is no JDK 17 here and unshare --net needs root. Only this run closes G-03-4 and proves REPO-08's network-off step"
  - test: "Optional live re-check of G-03-2: re-run the live main test with a sender that has no Exchange mailbox (the case that returned 404 ErrorInvalidUser)"
    expected: "The printed line reads 'Graph sendMail failed: HTTP 404, code ErrorInvalidUser, request-id <guid>' with no address and no Graph sentence, and the exception is SenderRejectedException"
    why_human: "WireMock proves the code path, but only live Graph produces the real text that leaked; the owner runs live tests (project rule: never send real mail)"
  - test: "Re-run verify-work for phase 03 so UAT tests 2 and 4 are recorded again (the earlier results in 03-UAT.md stay: tests 1, 3, 5 and 6 passed; 2 and 4 are the two issues closed here)"
    expected: "UAT tests 2 and 4 move from issue to pass once items 1 and 2 above are done"
    why_human: "UAT results are the owner's record; 03-UAT.md is not edited by the verifier"
  - test: "Section 6.3 rows not provoked live: an out-of-scope mailbox (RBAC), MailboxNotEnabledForRESTAPI, a mailbox 401 after a fresh token; also the from display name rendering and a shared mailbox as sender"
    expected: "Each maps to the documented exception; the display name appears as sent"
    why_human: "Owner accepted the 403 and 404 rows as sufficient and left these untested (UAT tests 2 and 5); they exist only against live Graph"
  - test: "Optional: re-run secure-phase 03"
    expected: "T-03-19 in 03-SECURITY.md assumed address-free messages; 03-06 makes that true for Graph, so the threat can be re-confirmed"
    why_human: "Security sign-off is the owner's decision"
---

# Phase 3: Graph and Entra Verification Report

**Phase Goal:** As a Java developer, I want to send mail through Microsoft Graph as an Entra app, so that each failure says whether to retry, drop the mail or fix the account.
**Verified:** 2026-11-22
**Status:** human_needed
**Re-verification:** Yes, after gap-closure plan 03-06 (UAT gaps G-03-2 and G-03-4). The earlier report had no `gaps:` section, so its five truths were re-checked against the current code, with full scrutiny on everything 03-06 touched.

## Goal Achievement

The goal is achieved in code, and the two UAT gaps are closed in code. A developer builds a `graph` account through the registry, authenticates as an Entra app (secret or certificate) and sends. Each failure arrives as a checked category (transient with optional `Retry-After`, permanent, sender-rejected, auth-required), which is what tells the caller to retry, drop the mail or fix the account. The remaining items are live-service or owner checks (chiefly the Temurin 17 CI run), not code defects.

I did not accept any SUMMARY claim on trust. Evidence run by me:

- `./mvnw -B -ntp -o test -pl morimail -Dtest='GraphErrorMappingTest,EntraTokenErrorsTest,GraphRequestTest'`: 66 + 39 + 25 = 130 tests, 0 failures.
- `EntraTokenErrorsTest#nothingSecretReachesALogOrAMessage` run alone five more times: green each time. This proves nothing about Temurin 17 (see human item 1).
- `./mvnw -B -ntp -o test -pl morimail-sample-serial -am -Dtest=GraphEndToEndTest`: 9 tests, 0 failures.
- The orchestrator's full `./mvnw -B -ntp -o verify` on HEAD (BUILD SUCCESS, 306 library and 137 sample tests, 0 failures, errors or skips; JDK 21 only) is taken as given. `git status` after my runs shows only the untracked `.planning/state.json`.

### Gap closure (plan 03-06)

| Gap | What I checked | Result |
|-----|----------------|--------|
| G-03-2 (address in exception messages) | `GraphTransport.failure` (`:255-286`) no longer reads `error.message`: grep for `get("message")` in the file finds nothing, the `text` variable is gone, and the message is exactly `"Graph sendMail failed: HTTP " + status + ", code " + clean(code) + ", request-id " + clean(requestId)`. All branches (401 with and without the mailbox hint, 403, 404, 429/5xx, class rule) use that one string. `mailboxHint` still reads the raw `code`. `GraphErrorMappingTest.graphsOwnMessageNeverReachesTheExceptionMessage` (2 cases: plain and percent-encoded `newsletter...@example.com`) pins the exact three-field message and `doesNotContain("newsletter")`. `GraphEndToEndTest.aRejectedMailboxStopsTheRunWithExitThree` now serves a Graph text that names the sender and asserts that neither the stderr stop line nor the journal `errorMessage` holds "newsletter" or "Access to"; it still exits 3. | FIXED in code and tests; live re-check optional (human item 2) |
| G-03-4 (CME in root-logger capture) | `EntraTokenErrorsTest.java:394-395`: `seen` and `loggers` are `CopyOnWriteArrayList`. Assertions, `Level.ALL`, the entra-logger check and the `finally` cleanup are unchanged, so the test is not weakened. Grep for `addHandler` and `getLogger("")` over all test sources: only this test attaches to the root logger; the two Graph tests attach to the named `...graph.GraphTransport` logger and call `send` on the test thread (no cross-thread sharing). `messages` is an `ArrayList` touched only by the test thread. No POM, `ci.yml` or main-code change for this gap (diff stat of `pom.xml`, `*/pom.xml`, `.github` is empty). | FIXED in code; closure needs the Temurin 17 CI run (human item 1) |

The change narrows GRAPH-08 and Phase 3 success criterion 4 (the message no longer carries Graph's own message). Spec section 6.3 does not require quoting Graph's message, and the narrowing is recorded consistently in `docs/architecture.md` (new `§6.3 (failure message)` row and Contracts bullet), `REQUIREMENTS.md` (GRAPH-08) and `ROADMAP.md` (criterion 4). The roadmap criterion is verified below in its new wording.

### Observable Truths (ROADMAP success criteria)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | `EntraClientCredentials` gets a token from WireMock with a secret, with key and certificate, and with the checked-in PKCS12; the assertion decodes to the PS256 header with `x5t#S256` and the specified claims, and its signature verifies with the certificate's public key | VERIFIED | Unchanged by 03-06 (`EntraClientCredentials.java` not in the diff). Tests `EntraClientCredentialsTest.assertionHeaderClaimsAndSignature`, `certificateFlowFromPkcs12GetsAToken` and the key-and-certificate factory test are in the green full build; `test-keystore-throwaway.p12` present. Live: the owner's UAT test 1 recorded Entra accepting the PS256 assertion (certificate flow 7 of 10 runs, the 403s were Exchange grant propagation) and the secret flow accepted |
| 2 | Token reused until 5 min before expiry then refetched; concurrent callers cause one request; `freshAccessToken` drops only a matching token; 4xx gives auth-required with `error` and `error_description`; 5xx, 429, network, timeout give transient; no secret, assertion or token is logged | VERIFIED | Unchanged code. `EntraTokenCacheTest` (single flight, compare-and-drop) and `EntraTokenErrorsTest` (39 tests, run by me, green) cover it; the entra package has no logger and `nothingSecretReachesALogOrAMessage` checks root-logger records, messages and the logger names, now on thread-safe lists |
| 3 | A `graph` account built through the registry (no `ServiceLoader` entry) sends `POST /users/{from}/sendMail` with percent-encoded from, bearer token, recipients, HTML-or-text body, base64 file attachments, `saveToSentItems` only when false; 202 returns normally; attachments of 3 MB or more (per file or in total) and unknown options are rejected | VERIFIED | `BuiltInTransports` lists `GraphTransportProvider`; `GraphRequestTest` (25, run by me), `GraphValidateTest`, `SendThroughGraphTest`, `TransportRegistryTest` in the green build. Live: UAT test 3 sent 2,900,000 and 3,100,000 byte attachments, both accepted by real Graph, which confirms the MiB constants |
| 4 | Every error-table row maps to its documented exception; the message carries Graph's `error.code` and `request-id`, never Graph's free-text message, tokens or bodies; a 401 triggers exactly one retry with a fresh token before it is mapped | VERIFIED | `GraphTransport.send` (`:191-207`): post once, on 401 `freshAccessToken(SCOPE, token)` and one repeat with the same bytes, no other retry. `failure` (`:255-286`): 401 to `SenderRejectedException` if the code contains "mailbox" else `AuthRequiredException`; 403 and 404 to `SenderRejectedException`; 429 and 5xx to `TransientMailException` with seconds-only `Retry-After`; everything else to `PermanentMailException`; IOException, timeout and interrupt on either post are transient. Message built only from status, cleaned code and cleaned request-id (header, `innerError`, `innererror`). `GraphErrorMappingTest` (66 tests incl. the status table, single retry, failing fresh token, two-thread retry) green. Live: UAT test 2 confirmed the 403 row (`ErrorAccessDenied`) and the 404 row (`ErrorInvalidUser`) against real Graph |
| 5 | `serial-mail send` runs the example campaign end to end against WireMock for token endpoint and Graph; `./mvnw verify` passes offline; a `live`-tagged test is skipped by default and ready for `-Plive` | VERIFIED (CI step is a human item) | `GraphEndToEndTest` (9, run by me): campaign once, rerun sends nothing, 403 stops with exit 3 and now leaks no Graph text. Offline verify green (orchestrator). `LiveGraphTest` is `@Tag("live")`, excluded by default. The `unshare --net` CI step exists in `ci.yml` and has never run (human item 1) |

**Score:** 5/5 truths verified. 0 behavior-unverified: the state and ordering invariants (single flight, compare-and-drop, single 401 retry, failure not cached) each have a named passing test. The one invariant that cannot be exercised here, the absence of the CME on Temurin 17, is a CI result, not a presence claim, and is listed as a human item rather than counted as verified.

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `morimail/.../graph/GraphTransport.java` | request build, validate, 401 retry, error mapping, status/code/request-id message | VERIFIED | 327 lines, thread-safety Javadoc, created by the provider; `failure` read in full |
| `morimail/.../graph/GraphTransportProvider.java` | id `graph`, option rules | VERIFIED | on the built-in list |
| `morimail/.../entra/EntraClientCredentials.java` | secret and certificate flows, cache, errors | VERIFIED | unchanged since the last verification, wired through `SerialMail` and tests |
| `morimail/.../BuiltInTransports.java` | `graph` on the built-in list | VERIFIED | |
| `morimail/src/test/resources/test-keystore-throwaway.p12` | TEST-01 key store | VERIFIED | present, loaded by tests |
| `GraphErrorMappingTest.java` | three-field expectations plus the address-in-Graph-text case | VERIFIED | contains `graphsOwnMessageNeverReachesTheExceptionMessage` |
| `EntraTokenErrorsTest.java` | thread-safe root-logger capture | VERIFIED | `new CopyOnWriteArrayList<>()` twice |
| `GraphEndToEndTest.java` | stop line and journal hold no Graph text | VERIFIED | asserts on `err()` and on `errorMessage` |
| `docs/architecture.md` | recorded deviation | VERIFIED | `§6.3 (failure message)` row and Contracts bullet match the code |
| `.github/workflows/ci.yml` | network-free build step | VERIFIED in file | run on CI is human item 1 |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `SerialMail.mailer` | `GraphTransportProvider` | `transport("graph")` through the registry | WIRED | no ServiceLoader entry |
| `GraphTransport.failure` message | sample stop line and `Journal` `errorMessage` | `MailException.getMessage()` | WIRED | `GraphEndToEndTest` asserts both stay free of Graph text |
| `GraphTransport.send` | `TokenProvider` | core interface only | WIRED | `graph` and `entra` import each other nowhere (`ImportRulesTest` in the green build) |
| `EntraClientCredentials` | `TokenProvider` | `implements` | WIRED | |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|--------------------|--------|
| `GraphTransport.requestBody` | JSON body | `MailMessage` fields, `Files.readAllBytes` | Yes (WireMock captures, tests compare bytes) | FLOWING |
| `GraphTransport.send` | bearer token | `auth.accessToken` to the HTTP token endpoint | Yes (`SendThroughGraphTest`, and live UAT test 1) | FLOWING |
| `GraphTransport.failure` | code, request-id | the response header and parsed `error` object | Yes (live 403 and 404 produced real values) | FLOWING |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Graph error mapping, Entra errors and log capture, Graph request | `./mvnw -B -ntp -o test -pl morimail -Dtest='GraphErrorMappingTest,EntraTokenErrorsTest,GraphRequestTest'` | 130 tests, 0 failures | PASS |
| The log-capture test alone, repeated | `./mvnw -o test -pl morimail -Dtest='EntraTokenErrorsTest#nothingSecretReachesALogOrAMessage'` x5 | 1 test each, 0 failures | PASS (JDK 21 only, cannot show the CI race is gone) |
| Sample end to end | `./mvnw -o test -pl morimail-sample-serial -am -Dtest=GraphEndToEndTest` | 9 tests, 0 failures | PASS |
| Graph's message cannot be read | `grep 'get("message")' GraphTransport.java` | no match | PASS |
| Only one root-logger handler in tests | `grep addHandler / getLogger("")` over test sources | one root attach (the fixed test), two named-logger attaches | PASS |
| Commit hygiene | `git log` of e648406, b2d99cc, b388ae9 | author and committer Stephan Wald, Co-Authored-By trailer, one concern each; no POM, ci.yml or spec change; only `example.com`, `example.org` addresses added | PASS |

### Probe Execution

Step 7c: SKIPPED. No plan names a probe script, and `scripts/*/tests/probe-*.sh` does not exist.

### Requirements Coverage

Plan frontmatter IDs: 03-01 {ENTRA-01, ENTRA-04, GRAPH-01}; 03-02 {ENTRA-01, 02, 03, 05, 06, 07, 08, 09, TEST-01, TEST-02}; 03-03 {GRAPH-01, 02, 03, 04, 05, TEST-03}; 03-04 {GRAPH-01, 06, 07, 08, 09, TEST-03}; 03-05 {CLI-14, REPO-08}; 03-06 {GRAPH-08, TEST-03, ENTRA-09, TEST-02, REPO-07, REPO-08}. The union covers exactly the 23 phase IDs given (ENTRA-01 to 09, GRAPH-01 to 09, TEST-01 to 03, CLI-14, REPO-08); 03-06 adds only REPO-07 as a re-touched ID from Phase 1. No orphaned requirement, no ID missing from REQUIREMENTS.md.

| Requirement | Source Plan | Status | Evidence |
|-------------|-------------|--------|----------|
| ENTRA-01 | 03-01, 03-02 | SATISFIED | form fields in `fetch`; `SendThroughGraphTest`; live secret flow accepted |
| ENTRA-02 | 03-02 | SATISFIED | `assertionHeaderClaimsAndSignature`; live Entra accepted the assertion |
| ENTRA-03 | 03-02 | SATISFIED | PKCS12 loader tests (wrong password, two keys, non-RSA, missing file) |
| ENTRA-04 | 03-01 | SATISFIED | `authority(URI)`, https or loopback http |
| ENTRA-05 | 03-02 | SATISFIED | `EntraTokenCacheTest` |
| ENTRA-06 | 03-02 | SATISFIED | compare-and-drop tests |
| ENTRA-07 | 03-02 | SATISFIED | `EntraTokenErrorsTest` |
| ENTRA-08 | 03-02 | SATISFIED | 10 s connect, 30 s request timeouts, slow-response test |
| ENTRA-09 | 03-02, 03-06 | SATISFIED | no logging in `entra`; `nothingSecretReachesALogOrAMessage` now race-free (CI proof pending) |
| GRAPH-01 | 03-01, 03-03, 03-04 | SATISFIED | `sendMailUri`, 2xx accepted |
| GRAPH-02 | 03-03 | SATISFIED | body rules and INFO-once test |
| GRAPH-03 | 03-03 | SATISFIED | `fileAttachment` with base64 |
| GRAPH-04 | 03-03 | SATISFIED | provider option rules and tests |
| GRAPH-05 | 03-03 | SATISFIED | `validate` constants; live probe confirmed MiB reading |
| GRAPH-06 | 03-04 | SATISFIED | `failure()` status table; 403 and 404 rows live-confirmed |
| GRAPH-07 | 03-04 | SATISFIED | single-retry tests |
| GRAPH-08 | 03-04, 03-06 | SATISFIED (as narrowed in REQUIREMENTS.md: code and request-id, never Graph's free text, tokens or bodies) | `failure()`, `graphsOwnMessageNeverReachesTheExceptionMessage`, `GraphEndToEndTest` |
| GRAPH-09 | 03-03, 03-04 | SATISFIED | timeouts, including the retried request |
| TEST-01 | 03-02 | SATISFIED | key store checked in, README sentence |
| TEST-02 | 03-02, 03-06 | SATISFIED | assertion, form, reuse, expiry, errors, single flight, compare-and-drop, log capture |
| TEST-03 | 03-03, 03-04, 03-06 | SATISFIED | graph tests with `StubTokenProvider`, adjusted to the three-field message |
| CLI-14 | 03-05, 03-06 | SATISFIED | `GraphEndToEndTest` (9) |
| REPO-08 | 03-05, 03-06 | SATISFIED in code; the Temurin 17 CI run with `unshare --net` is a human item | tag excluded by default, `-Plive`, offline verify green here |

### Anti-Patterns Found

Debt-marker scan (`TBD`, `FIXME`, `XXX`) over the files 03-06 changed: none. Secret and address hygiene: the 03-06 diff adds only `newsletter@example.com`, `x@example.org`, `y@example.org`; no host, tenant or real address.

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `GraphTransport.java` | 316-317 | WR-01: quadratic address regex before the 300-character cut | Warning (advisory) | A CPU stall on a pathological code or request-id; no truth depends on it. `EntraClientCredentials` has the same pattern under the token lock |
| `GraphErrorMappingTest.java` | around 317, 343 | IN-02: no `request-id` cleaning case | Info | Regression gap only |
| `docs/architecture.md` | 30 | IN-01: promise slightly broader than a pattern can guarantee | Info | Wording |

### Human Verification Required

See the `human_verification` list in the frontmatter. In order of importance:

1. **Temurin 17 CI run** (closes G-03-4, proves REPO-08's network-free step, the release build and PSS/PKCS12 on JDK 17). The race could not be reproduced here, so the fix is reasoned and reviewed (03-REVIEW found it correct), not demonstrated.
2. Optional live re-run with a sender without a mailbox to see the 404 line with no address (G-03-2).
3. Re-run verify-work for phase 03 so UAT tests 2 and 4 are recorded again. The recorded results in `03-UAT.md` (tests 1, 3, 5, 6 pass; tests 2 and 4 issues) are untouched.
4. Section 6.3 rows and sender variants the owner chose not to provoke (RBAC out-of-scope, `MailboxNotEnabledForRESTAPI`, mailbox 401, display name, shared mailbox).
5. Optional secure-phase re-run (T-03-19).

### Gaps Summary

No gaps. All 5 roadmap success criteria and all 23 requirement IDs are implemented, wired and backed by passing tests against the current code, and the two UAT gaps are closed in code. Status is `human_needed` because G-03-4 can only be closed by the owner's Temurin 17 CI run and the rest are live-service or owner decisions. The open review items (WR-01 quadratic regex, IN-01, IN-02) are advisory; WR-01 deserves its short fix before release. Phase 4 must not start before the owner's CI result and live report (ROADMAP hard stop).

---

_Verified: 2026-11-22_
_Verifier: Claude (gsd-verifier)_
