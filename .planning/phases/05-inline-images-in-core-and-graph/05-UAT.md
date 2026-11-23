---
status: testing
phase: 05-inline-images-in-core-and-graph
source: [05-VERIFICATION.md]
started: 2026-11-23T06:24:53Z
updated: 2026-11-23T06:24:53Z
---

## Current Test

number: 1
name: Live inline-image render through Graph (GRAPH-12)
expected: |
  Output line 'live inline: flow certificate|secret, accepted'; the generated blue 64x64 square shows inside the mail body under the first line, not as a separate attachment, in Outlook on the web and one desktop client.
awaiting: user response

## Tests

### 1. Live inline-image render through Graph (GRAPH-12)
expected: Owner runs ./mvnw -Plive test -pl morimail -Dtest=LiveGraphTest#sendsOneMailWithAnInlineImage (with the MORIMAIL_LIVE_* variables). Output line 'live inline: flow certificate|secret, accepted'; the generated blue 64x64 square shows inside the mail body under the first line, not as a separate attachment, in Outlook on the web and one desktop client.
result: [pending]

### 2. Second Temurin 17 CI run after pushing the 05-02 commits
expected: Owner pushes the 05-02 commits and the GitHub Actions run on Temurin 17 is green in all three steps: verify, scoped release build (-Prelease clean verify -Dgpg.skip -pl morimail -am), offline build.
result: [pending]

## Summary

total: 2
passed: 0
issues: 0
pending: 2
skipped: 0
blocked: 0

## Gaps
