---
status: testing
phase: 08-docs-and-release-readiness
source: [08-VERIFICATION.md]
started: 2026-11-23T20:42:44Z
updated: 2026-11-23T20:42:44Z
---

## Current Test

number: 1
name: Push the phase 8 commits and watch the three CI steps on Temurin 17
expected: |
  All three steps (verify, scoped release build, offline build) green for the pushed commit. The zero-warning release build was measured locally on JDK 21 with --release 17 only.
awaiting: user response

## Tests

### 1. Push the phase 8 commits and watch the three CI steps on Temurin 17
expected: All three steps (verify, scoped release build, offline build) green for the pushed commit. The zero-warning release build was measured locally on JDK 21 with --release 17 only.
result: [pending]

### 2. Release 0.2.0 by following docs/release.md, starting with the step 5 skipUpload check
expected: central-bundle.zip holds exactly 54 entries (the nine listed base files, each with .asc and four checksums), no -tests.jar, no sample entry. Review in the Portal before Publish.
result: [pending]

### 3. Read docs/release.md as the owner would for a second release (WR-01)
expected: Steps 1 to 3 (namespace, GPG key, Portal token) are understood as one-time, and the existing 0.1.0 signing key is reused rather than a new one generated.
result: [pending]

### 4. Enable GitHub private vulnerability reporting if still off
expected: Setting enabled (SECURITY.md routes reports there).
result: [pending]

## Summary

total: 4
passed: 0
issues: 0
pending: 4
skipped: 0
blocked: 0

## Gaps
