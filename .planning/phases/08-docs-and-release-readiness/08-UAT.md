---
status: testing
phase: 08-docs-and-release-readiness
source: [08-VERIFICATION.md]
started: 2026-11-23T20:42:44Z
updated: 2026-11-23T20:55:44Z
---

## Current Test

number: 3
name: Read docs/release.md as the owner would for a second release (WR-01)
expected: |
  Steps 1 to 3 (namespace, GPG key, Portal token) are understood as one-time, and the existing 0.1.0 signing key is reused rather than a new one generated.
awaiting: user response

## Tests

### 1. Push the phase 8 commits and watch the three CI steps on Temurin 17
expected: All three steps (verify, scoped release build, offline build) green for the pushed commit. The zero-warning release build was measured locally on JDK 21 with --release 17 only.
result: pass

### 2. Release 0.2.0 by following docs/release.md, starting with the step 5 skipUpload check
expected: central-bundle.zip holds exactly 54 entries (the nine listed base files, each with .asc and four checksums), no -tests.jar, no sample entry. Review in the Portal before Publish.
result: issue
reported: "[ERROR] Failed to execute goal org.sonatype.central:central-publishing-maven-plugin:0.12.0:publish (injected-central-publishing) on project morimail-html: Failed to deploy artifacts: Could not transfer artifact de.morisoft:morimail-parent:pom.asc:0.2.0-20261123.205449-1 from/to central (https://central.sonatype.com/repository/maven-snapshots/): HTTP Status: 401"
severity: blocker

### 3. Read docs/release.md as the owner would for a second release (WR-01)
expected: Steps 1 to 3 (namespace, GPG key, Portal token) are understood as one-time, and the existing 0.1.0 signing key is reused rather than a new one generated.
result: [pending]

### 4. Enable GitHub private vulnerability reporting if still off
expected: Setting enabled (SECURITY.md routes reports there).
result: [pending]

## Summary

total: 4
passed: 1
issues: 1
pending: 2
skipped: 0
blocked: 0

## Gaps

- gap_id: G-08-2
  truth: "Following docs/release.md, the step 5 skipUpload check builds and signs central-bundle.zip with exactly 54 entries and sends nothing"
  status: failed
  reason: "User reported: [ERROR] Failed to execute goal org.sonatype.central:central-publishing-maven-plugin:0.12.0:publish (injected-central-publishing) on project morimail-html: Failed to deploy artifacts: Could not transfer artifact de.morisoft:morimail-parent:pom.asc:0.2.0-20261123.205449-1 from/to central (https://central.sonatype.com/repository/maven-snapshots/): HTTP Status: 401"
  severity: blocker
  test: 2
  artifacts: []
  missing: []
