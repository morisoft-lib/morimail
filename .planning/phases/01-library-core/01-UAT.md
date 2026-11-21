---
status: complete
phase: 01-library-core
source: [01-VERIFICATION.md]
started: 2026-11-21T13:29:40Z
updated: 2026-11-21T13:44:50.888Z
---

## Current Test

[testing complete]

## Tests

### 1. CI run on Java 17 (push or PR runs .github/workflows/ci.yml)
expected: All three CI steps are green on Temurin 17: ./mvnw -B -ntp verify; ./mvnw -B -ntp -Prelease clean verify -Dgpg.skip -pl morimail -am; ./mvnw -B -ntp -o verify. 129 library tests and 1 sample test pass; the Javadoc jar builds with no warnings.
result: issue
reported: "CI run 44910275508 (push of 3d88c22, Temurin 17): step 1 verify green (134 + 1 tests); step 2 -Prelease verify fails: maven-javadoc-plugin attach-javadocs 'Project contains Javadoc Warnings' - MailMessage.java:34 no @param for from, to, cc, bcc, replyTo, subject, html, text, attachments. Step 3 offline verify not run. JDK 21 javadoc locally does not warn."
severity: blocker

### 2. Decide on root commit 8c9b082 (author swald@basis.cloud) before publishing
expected: Either accept it, or rewrite the root commit author while the repository has no other consumers. Nothing in the code depends on the answer.
result: pass
note: "Owner accepted the root commit as is (2026-11-21); no history rewrite."

## Summary

total: 2
passed: 1
issues: 1
pending: 0
skipped: 0
blocked: 0

## Gaps

- gap_id: G-01-1
  truth: "All three CI steps are green on Temurin 17; the Javadoc jar builds with no warnings."
  status: failed
  reason: "User reported: CI run 44910275508 on Temurin 17 fails in -Prelease verify: Javadoc warnings 'no @param' for all nine components at MailMessage.java:34 (compact constructor); offline step not run."
  severity: blocker
  test: 1
  artifacts: []
  missing: []
