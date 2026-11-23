---
status: diagnosed
phase: 08-docs-and-release-readiness
source: [08-VERIFICATION.md]
started: 2026-11-23T20:42:44Z
updated: 2026-11-23T20:59:25Z
---

## Current Test

[testing complete]

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
result: pass

### 4. Enable GitHub private vulnerability reporting if still off
expected: Setting enabled (SECURITY.md routes reports there).
result: pass

## Summary

total: 4
passed: 3
issues: 1
pending: 0
skipped: 0
blocked: 0

## Gaps

- gap_id: G-08-2
  truth: "Following docs/release.md, the step 5 skipUpload check builds and signs central-bundle.zip with exactly 54 entries and sends nothing"
  status: failed
  reason: "User reported: [ERROR] Failed to execute goal org.sonatype.central:central-publishing-maven-plugin:0.12.0:publish (injected-central-publishing) on project morimail-html: Failed to deploy artifacts: Could not transfer artifact de.morisoft:morimail-parent:pom.asc:0.2.0-20261123.205449-1 from/to central (https://central.sonatype.com/repository/maven-snapshots/): HTTP Status: 401"
  severity: blocker
  test: 2
  root_cause: "docs/release.md lets the owner reach a deploy command while the POMs still read 0.2.0-SNAPSHOT. central-publishing-maven-plugin 0.12.0 (PublishMojo.doExecute -> processSnapshot) routes any SNAPSHOT build to centralSnapshotsUrl (https://central.sonatype.com/repository/maven-snapshots/) and uploads it unless skipUpload is set (postProcessSnapshot honours skipUpload, verified in the 0.12.0 bytecode). So the 401 came from a deploy without -DskipUpload at the SNAPSHOT version; the namespace has no snapshot publishing, hence 401 and nothing stored. The doc only says 'At a SNAPSHOT version no zip is made' and never says that a SNAPSHOT deploy uploads to the snapshots repository, and nothing stops a deploy before step 4. The UAT test text 'starting with the step 5 skipUpload check' also pointed past step 4."
  artifacts:
    - path: "docs/release.md"
      issue: "Step 5 has no guard against deploying at a SNAPSHOT version and does not warn that such a deploy uploads to Central's snapshots repository"
  missing:
    - "Before each deploy command in step 5, a one-line check that the POM version is not a SNAPSHOT (stop if it is), stated as: step 4 must be done first"
    - "Replace 'At a SNAPSHOT version no zip is made' with the actual behaviour: at a SNAPSHOT version the deploy tries to upload to Central's snapshots repository (the check with -DskipUpload stages only), so always finish step 4 first"
    - "Re-run UAT test 2 from step 4 after the doc fix"
  debug_session: ""
  diagnosed_by: "inline bytecode read of central-publishing-maven-plugin-0.12.0.jar PublishMojo (javap)"
