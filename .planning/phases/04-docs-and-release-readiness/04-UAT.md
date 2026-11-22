---
status: testing
phase: 04-docs-and-release-readiness
source: [04-VERIFICATION.md]
started: 2026-11-22T19:40:36Z
updated: 2026-11-22T19:40:36Z
---

## Current Test

number: 1
name: Follow docs/entra-setup.md on the real tenant
expected: |
  The dry run writes the rendered mails. `--limit 1` ends with `accepted 1, skipped 0, failed 0`. The full run skips row 1 and sends the rest. The second full run prints `accepted 0, skipped <rows>, failed 0` and sends nothing. Report any step whose screen, cmdlet or output differs from the guide.
awaiting: user response

## Tests

### 1. Follow docs/entra-setup.md on the real tenant
expected: The dry run writes the rendered mails. `--limit 1` ends with `accepted 1, skipped 0, failed 0`. The full run skips row 1 and sends the rest. The second full run prints `accepted 0, skipped <rows>, failed 0` and sends nothing. Report any step whose screen, cmdlet or output differs from the guide.
result: [pending]

### 2. Push and watch GitHub Actions on Temurin 17
expected: All three CI steps green (`./mvnw -B -ntp verify`, the release build `-Prelease clean verify -Dgpg.skip -pl morimail -am`, and the `unshare --net` offline verify).
result: [pending]

### 3. Run the real release by docs/release.md
expected: The Portal validates the deployment (POM metadata, signatures, sources and javadoc) and the bundle matches the 30-entry listing.
result: [pending]

### 4. Triage the three open review warnings in 04-REVIEW.md (WR-01, WR-02, WR-03)
expected: Each warning is fixed or accepted in writing.
result: [pending]

## Summary

total: 4
passed: 0
issues: 0
pending: 4
skipped: 0
blocked: 0

## Gaps
