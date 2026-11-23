---
status: testing
phase: 07-html-image-helper-module
source: [07-VERIFICATION.md]
started: 2026-11-23T15:02:23Z
updated: 2026-11-23T15:02:23Z
---

## Current Test

number: 1
name: Push the Phase 7 commits (07-02 and 07-03, up to HEAD) and let GitHub Actions run on Temurin 17
expected: |
  All three CI steps are green on Temurin 17: `./mvnw -B -ntp verify`, `./mvnw -B -ntp -Prelease clean verify -Dgpg.skip -pl morimail,morimail-html -am`, and the offline `unshare --net` build. Only commit cce3625 (end of 07-01 Task 2) has run there so far.
awaiting: user response

## Tests

### 1. Push the Phase 7 commits (07-02 and 07-03, up to HEAD) and let GitHub Actions run on Temurin 17
expected: All three CI steps are green on Temurin 17: `./mvnw -B -ntp verify`, `./mvnw -B -ntp -Prelease clean verify -Dgpg.skip -pl morimail,morimail-html -am`, and the offline `unshare --net` build. Only commit cce3625 (end of 07-01 Task 2) has run there so far.
result: [pending]

### 2. Read the owner-review items in the 07-01, 07-02 and 07-03 SUMMARYs (assumptions A1-A7, planner choices P1-P10) and accept or correct them
expected: Owner accepts or corrects, for example the `provided` scope of `morimail` in the helper POM (D-02), the 3 MiB `>=` refusal rule (A2), HTTP 408 mapped to InvalidMailException, and the `image-3.svg-xml` file name for `image/svg+xml` (P10)
result: [pending]

## Summary

total: 2
passed: 0
issues: 0
pending: 2
skipped: 0
blocked: 0

## Gaps
