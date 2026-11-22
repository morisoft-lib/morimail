---
status: complete
phase: 04-docs-and-release-readiness
source: [04-VERIFICATION.md]
started: 2026-11-22T19:40:36Z
updated: 2026-11-22T20:20:44.012Z
---

## Current Test

[testing complete]

## Tests

### 1. Follow docs/entra-setup.md on the real tenant
expected: The dry run writes the rendered mails. `--limit 1` ends with `accepted 1, skipped 0, failed 0`. The full run skips row 1 and sends the rest. The second full run prints `accepted 0, skipped <rows>, failed 0` and sends nothing. Report any step whose screen, cmdlet or output differs from the guide.
result: pass

### 2. Push and watch GitHub Actions on Temurin 17
expected: All three CI steps green (`./mvnw -B -ntp verify`, the release build `-Prelease clean verify -Dgpg.skip -pl morimail -am`, and the `unshare --net` offline verify).
result: pass
note: "Pushed d2d2931; CI run 45063447212 green on temurin 17, all three steps success"

### 3. Run the real release by docs/release.md
expected: The Portal validates the deployment (POM metadata, signatures, sources and javadoc) and the bundle matches the 30-entry listing.
result: issue
reported: "that's missing in the document"
severity: major
context: "Step 5 deploy failed: maven-gpg-plugin 3.2.8 sign on morimail-parent, Exit code: 2. Release commit 72e7f7f pushed, CI 45065502383 green; nothing uploaded. Cause is pinentry with no terminal (GPG_TTY unset, e.g. run via Claude Code). docs/release.md does not tell the owner to warm gpg-agent (echo test | gpg --clearsign) in a real terminal or how to read an exit code 2."

### 4. Triage the three open review warnings in 04-REVIEW.md (WR-01, WR-02, WR-03)
expected: Each warning is fixed or accepted in writing.
result: pass

## Summary

total: 4
passed: 3
issues: 1
pending: 0
skipped: 0
blocked: 0

## Gaps

- gap_id: G-04-3
  truth: "docs/release.md lets the owner run the release, including the signed deploy, without hitting an undocumented failure; the Portal validates the deployment and the bundle matches the 30-entry listing."
  status: failed
  reason: "User reported: that's missing in the document"
  severity: major
  test: 3
  artifacts: []
  missing: []
