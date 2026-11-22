---
status: complete
phase: 01-library-core
source: [01-VERIFICATION.md]
started: 2026-11-21T13:29:40Z
updated: 2026-11-21T14:07:56.255Z
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

### 3. Temurin 17 CI re-run after fix 5109854 (G-01-1 re-check)
expected: After the owner pushes main, the CI run on Temurin 17 is green in all three steps (./mvnw -B -ntp verify; ./mvnw -B -ntp -Prelease clean verify -Dgpg.skip -pl morimail -am; ./mvnw -B -ntp -o verify). Step 2 has no 'no @param' warning for MailMessage.java and no 'Project contains Javadoc Warnings'. 134 library tests and 1 sample test pass.
result: pass
note: "CI run 44912211934 (push of 8fb280c, Temurin 17.0.21): verify, -Prelease clean verify and -o verify all BUILD SUCCESS; 134 + 1 tests; zero 'no @param' warnings."

## Summary

total: 3
passed: 2
issues: 1
pending: 0
skipped: 0
blocked: 0

## Gaps

- gap_id: G-01-1
  truth: "All three CI steps are green on Temurin 17; the Javadoc jar builds with no warnings."
  status: resolved
  reason: "User reported: CI run 44910275508 on Temurin 17 fails in -Prelease verify: Javadoc warnings 'no @param' for all nine components at MailMessage.java:34 (compact constructor); offline step not run."
  severity: blocker
  test: 1
  resolved_by: 01-07-PLAN.md
  resolved_at: 2026-11-21
  root_cause: "The compact constructor of record MailMessage has its own Javadoc (/** Copies the four lists ... @throws */) without @param tags. JDK 17 javadoc with doclint all reports 'no @param' for every record component there; JDK 18+ javadoc inherits the record header's @param tags, so the local JDK 21 build is clean. failOnWarnings=true in the release profile turns the warnings into a build failure. MailMessage is the only record with a documented compact constructor."
  artifacts:
    - path: "morimail/src/main/java/de/morisoft/morimail/MailMessage.java"
      issue: "Compact constructor Javadoc (line 29-34) lacks @param for the nine components"
  missing:
    - "Make the compact constructor Javadoc pass JDK 17 doclint: add @param for all nine components, or fold its text into the record Javadoc and drop the constructor doc comment"
    - "Confirm CI step 2 (-Prelease verify) and step 3 (offline verify) are green on Temurin 17"
  debug_session: ""
