---
status: diagnosed
phase: 03-graph-and-entra
source: [03-VERIFICATION.md]
started: 2026-11-22T09:27:25Z
updated: 2026-11-22T12:21:31.000Z
---

## Current Test

[testing complete]

## Tests

### 1. Run the live test with a real app registration: ./mvnw -Plive test -pl morimail with the MORIMAIL_LIVE_* variables (certificate flow, then secret flow)
expected: Entra accepts the PS256 client assertion with the x5t#S256 header (no AADSTS error, no clock-skew complaint) and Graph answers 202
result: pass
notes: "2026-11-22 run 1 (certificate flow): token issued, so Entra accepted the PS256 assertion with x5t#S256. Graph sendMail answered 403 ErrorAccessDenied, mapped to SenderRejectedException as the 6.3 table says. Likely cause is the Mail.Send grant (no admin consent, still propagating, or mailbox outside the RBAC scope). Graph 202 and the secret flow not yet seen. Cause found: Mail.Send application permission was added but admin consent not granted. After granting: secret flow accepted (202). Certificate flow re-run pending. 11:12-11:15Z: certificate token decoded (roles=[Mail.Send], aud Graph, appid/tid match, appidacr=2) yet Graph answered 403 ErrorAccessDenied intermittently, minutes after the consent; consistent with Exchange propagation (30 min to 2 h), not with a code fault. Final: certificate flow 7 of 10 runs accepted (3 x 403 during propagation), secret flow accepted at 11:10Z and 11:11Z."

### 2. Provoke each row of the section 6.3 error table live (403 vs 404 vs MailboxNotEnabledForRESTAPI, an out-of-scope mailbox with MORIMAIL_LIVE_OUT_OF_SCOPE_FROM, a 401 after a fresh token)
expected: Each real response maps to the documented exception; a mailbox 401 maps to SenderRejectedException through the mailboxHint rule (code contains 'mailbox'); otherwise AuthRequiredException
result: issue
reported: "pass, log the address issue" (403 and 404 rows confirmed live; Graph's error message carries the sender address and the exception message copies it verbatim)
severity: major
notes: "2026-11-22: sender without an Exchange mailbox -> HTTP 404, code ErrorInvalidUser, mapped to SenderRejectedException (matches the 404 row; not the 401 the forum answer reported). Graph's error message contains the sender address, and the exception message carries it verbatim, so the live test output line includes an address despite its Javadoc. Exchange mailbox without working grant -> 403 ErrorAccessDenied -> SenderRejectedException (matches the 403 row). Owner accepted the 403/404 rows as sufficient; out-of-scope RBAC probe, MailboxNotEnabledForRESTAPI and the 401 cases not provoked."

### 3. Run the opt-in big-attachment probe (MORIMAIL_LIVE_BIG_ATTACHMENT, about 2.9 MB)
expected: Graph accepts it; confirms whether the 3 MiB / 4 MiB constants in GraphTransport (binary, not decimal MB) are the right interpretation of the documented 3 MB / 4 MB limits
result: pass
notes: "2026-11-22 11:20Z, certificate flow: 2,900,000 bytes accepted; 3,100,000 bytes accepted (above 3 MB and, base64-encoded, above 4 MB decimal, below 3 MiB / 4 MiB). Graph limits are binary; the MiB constants are right."

### 4. Let the GitHub Actions CI run on Java 17, including the new unshare --net step
expected: Both ./mvnw verify and the release build pass on Temurin 17 (PSS signing, PKCS12 legacy-format loading) and the network-free ./mvnw -o verify step passes with only loopback up
result: issue
reported: "push it and watch the CI run for me" -> CI run 45016998414 failed twice (attempts 1 and 2) on Temurin 17 in ./mvnw verify: EntraTokenErrorsTest.nothingSecretReachesALogOrAMessage throws ConcurrentModificationException at line 451; release build and unshare --net steps never ran
severity: blocker

### 5. Owner decisions for the STOP checkpoint: from display name, shared mailbox, Exchange per-mailbox limits behind --rate 20, LICENSE holder, SECURITY.md contact, Co-Authored-By trailer
expected: Findings reported back before Phase 4 starts
result: pass
notes: "Owner decisions 2026-11-22: (a) from display name: only the address was seen; the live test sets no display name, so the name rendering is still unverified live. (b) shared mailbox as sender: untested. (c) --rate 20 default: okay. (d) LICENSE holder Stephan Wald, MIT: okay. (e) SECURITY.md contact (GitHub private reporting, maintainer address fallback): okay. (f) Co-Authored-By trailer in commits: okay, keep."

### 6. Confirm that Retry-After is read as seconds only (the review fix for IN-05 reversed phase decision D-10, which read seconds and HTTP dates)
expected: Findings reported back before Phase 4 starts
result: pass
notes: "Owner accepts seconds-only Retry-After (HTTP-date form yields no delay), matching the Graph throttling documentation."

### 7. Optional live re-check of G-03-2: re-run the live main test with a sender that has no Exchange mailbox (the case that returned 404 ErrorInvalidUser)
expected: The printed line reads 'Graph sendMail failed: HTTP 404, code ErrorInvalidUser, request-id <guid>' with no address and no Graph sentence, and the exception is SenderRejectedException
result: [pending]

### 8. Optional: re-run secure-phase 03
expected: T-03-19 in 03-SECURITY.md assumed address-free messages; 03-06 makes that true for Graph, so the threat can be re-confirmed
result: [pending]

## Summary

total: 8
passed: 4
issues: 2
pending: 2
skipped: 0
blocked: 0

## Gaps

- gap_id: G-03-2
  truth: "Exception messages and live-test output never contain a mail address (addresses at DEBUG only)"
  status: failed
  reason: "User reported: pass, log the address issue. Live 404 ErrorInvalidUser: Graph's error message names the sender address, and GraphTransport.failure copies the message into the SenderRejectedException text, so the LiveGraphTest output line shows the address despite its Javadoc and consumers logging the exception write the address at ERROR."
  severity: major
  test: 2
  root_cause: "GraphTransport.failure (GraphTransport.java:271-272) builds the exception message from status, error.code, request-id AND error.message (clean(text)). Graph's error.message is free text that can name the sender (404 ErrorInvalidUser: \"The requested user '<address>' is invalid.\"), so the address reaches every exception message, the LiveGraphTest output line, and in the sample the stderr summary (SendCommand.java:101-102, 204) and the journal errorMessage field (Journal.java:112-113)."
  artifacts:
    - path: "morimail/src/main/java/de/morisoft/morimail/graph/GraphTransport.java"
      issue: "failure() appends Graph's error.message verbatim to the exception text"
    - path: "morimail/src/test/java/de/morisoft/morimail/graph/GraphErrorMappingTest.java"
      issue: "no test asserts that an address in error.message stays out of the exception message"
    - path: "morimail/src/test/java/de/morisoft/morimail/LiveGraphTest.java"
      issue: "Javadoc (line 41) promises no address in output; true only once failure() is fixed"
  missing:
    - "Drop error.message from the exception text (keep HTTP status, error.code, request-id), the simplest fix under Rule 0"
    - "A GraphErrorMappingTest case: a 404 body whose error.message contains an example.com address; assert the exception message does not contain it"
    - "Note the change in docs/architecture.md next to the 6.3 error table row"
  debug_session: ""

- gap_id: G-03-4
  truth: "./mvnw verify, the release build and the network-free unshare --net step all pass on Temurin 17 in GitHub Actions"
  status: failed
  reason: "User reported: push it and watch the CI run for me. CI run 45016998414 failed on both attempts: EntraTokenErrorsTest.nothingSecretReachesALogOrAMessage (line 451) throws ConcurrentModificationException. The test adds a Handler to the root logger at Level.ALL that appends to plain ArrayLists (seen, loggers) while JDK HttpClient and WireMock threads keep logging, and the test iterates the list concurrently. Passes locally on JDK 21; fails reliably on CI. Release build and unshare --net steps were skipped, so they remain unverified."
  severity: blocker
  test: 4
  root_cause: "EntraTokenErrorsTest.nothingSecretReachesALogOrAMessage (EntraTokenErrorsTest.java:393-422) installs a Handler on the root logger at Level.ALL whose publish() appends to two plain ArrayLists (seen, loggers). JDK HttpClient selector threads and WireMock/Jetty threads keep publishing records while the test thread iterates seen at line 451, so ArrayList's iterator throws ConcurrentModificationException. Timing on the slower CI runner makes it fail reliably; on the local JDK 21 box it happens to pass. Test-only race, no library defect."
  artifacts:
    - path: "morimail/src/test/java/de/morisoft/morimail/entra/EntraTokenErrorsTest.java"
      issue: "unsynchronized lists shared between logging threads and the asserting thread; iteration while the handler is still attached"
  missing:
    - "Remove the handler (and restore the level) before asserting, then assert on a snapshot, or collect into thread-safe lists (CopyOnWriteArrayList / Collections.synchronizedList with a copy before iterating)"
    - "Check the rest of the test sources for the same root-logger Handler pattern and fix it the same way"
    - "Push and confirm CI run green on Temurin 17, including the release build and the unshare --net step that were skipped"
  debug_session: ""
