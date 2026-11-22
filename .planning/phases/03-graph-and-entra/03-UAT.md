---
status: testing
phase: 03-graph-and-entra
source: [03-VERIFICATION.md]
started: 2026-11-22T09:27:25Z
updated: 2026-11-22T09:27:25Z
---

## Current Test

number: 1
name: Run the live test with a real app registration (certificate flow, then secret flow)
expected: |
  Entra accepts the PS256 client assertion with the x5t#S256 header (no AADSTS error, no clock-skew complaint) and Graph answers 202
awaiting: user response

## Tests

### 1. Run the live test with a real app registration: ./mvnw -Plive test -pl morimail with the MORIMAIL_LIVE_* variables (certificate flow, then secret flow)
expected: Entra accepts the PS256 client assertion with the x5t#S256 header (no AADSTS error, no clock-skew complaint) and Graph answers 202
result: [pending]

### 2. Provoke each row of the section 6.3 error table live (403 vs 404 vs MailboxNotEnabledForRESTAPI, an out-of-scope mailbox with MORIMAIL_LIVE_OUT_OF_SCOPE_FROM, a 401 after a fresh token)
expected: Each real response maps to the documented exception; a mailbox 401 maps to SenderRejectedException through the mailboxHint rule (code contains 'mailbox'); otherwise AuthRequiredException
result: [pending]

### 3. Run the opt-in big-attachment probe (MORIMAIL_LIVE_BIG_ATTACHMENT, about 2.9 MB)
expected: Graph accepts it; confirms whether the 3 MiB / 4 MiB constants in GraphTransport (binary, not decimal MB) are the right interpretation of the documented 3 MB / 4 MB limits
result: [pending]

### 4. Let the GitHub Actions CI run on Java 17, including the new unshare --net step
expected: Both ./mvnw verify and the release build pass on Temurin 17 (PSS signing, PKCS12 legacy-format loading) and the network-free ./mvnw -o verify step passes with only loopback up
result: [pending]

### 5. Owner decisions for the STOP checkpoint: from display name, shared mailbox, Exchange per-mailbox limits behind --rate 20, LICENSE holder, SECURITY.md contact, Co-Authored-By trailer
expected: Findings reported back before Phase 4 starts
result: [pending]

## Summary

total: 5
passed: 0
issues: 0
pending: 5
skipped: 0
blocked: 0

## Gaps
