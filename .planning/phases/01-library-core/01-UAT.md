---
status: testing
phase: 01-library-core
source: [01-VERIFICATION.md]
started: 2026-11-21T13:29:40Z
updated: 2026-11-21T13:29:40Z
---

## Current Test

number: 1
name: CI run on Java 17 (push or PR runs .github/workflows/ci.yml)
expected: |
  All three CI steps are green on Temurin 17: ./mvnw -B -ntp verify; ./mvnw -B -ntp -Prelease clean verify -Dgpg.skip -pl morimail -am; ./mvnw -B -ntp -o verify. 129 library tests and 1 sample test pass; the Javadoc jar builds with no warnings.
awaiting: user response

## Tests

### 1. CI run on Java 17 (push or PR runs .github/workflows/ci.yml)
expected: All three CI steps are green on Temurin 17: ./mvnw -B -ntp verify; ./mvnw -B -ntp -Prelease clean verify -Dgpg.skip -pl morimail -am; ./mvnw -B -ntp -o verify. 129 library tests and 1 sample test pass; the Javadoc jar builds with no warnings.
result: [pending]

### 2. Decide on root commit 8c9b082 (author swald@basis.cloud) before publishing
expected: Either accept it, or rewrite the root commit author while the repository has no other consumers. Nothing in the code depends on the answer.
result: [pending]

## Summary

total: 2
passed: 0
issues: 0
pending: 2
skipped: 0
blocked: 0

## Gaps
