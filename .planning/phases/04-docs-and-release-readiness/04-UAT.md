---
status: complete
phase: 04-docs-and-release-readiness
source: [04-VERIFICATION.md]
started: 2026-11-22T19:40:36Z
updated: 2026-11-22T21:01:15.584Z
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

### 5. In a real terminal, continue docs/release.md at step 5: warm-up, skipUpload check (30 entries), warm-up again, real `./mvnw -B -ntp -Prelease clean deploy -pl morimail -am`, then step 6 in the Portal
expected: The deploy signs from the agent cache without an undocumented failure, and the Central Portal validates the deployment (POM metadata, signatures, sources, javadoc) and lists the 30-entry bundle. Report any step where the doc and the real output differ.
result: pass

### 6. Before step 7, decide on review finding WR-01 of 04-REVIEW.md (step 7 tags HEAD, but HEAD is past the release commit 72e7f7f)
expected: `git show --no-patch v0.1.0^{commit}` shows the `release 0.1.0` commit that CI tested, not a later docs commit.
result: pass
note: "v0.1.0 on origin resolves to 72e7f7f release 0.1.0"

## Summary

total: 6
passed: 5
issues: 1
pending: 0
skipped: 0
blocked: 0

## Gaps

- gap_id: G-04-3
  truth: "docs/release.md lets the owner run the release, including the signed deploy, without hitting an undocumented failure; the Portal validates the deployment and the bundle matches the 30-entry listing."
  status: resolved
  resolved_by: 04-05-PLAN.md
  resolved_at: 2026-11-22
  reason: "User reported: that's missing in the document"
  severity: major
  test: 3
  root_cause: "Both step 5 deploy commands use -B. In batch mode maven-gpg-plugin 3.2.8 runs gpg with --batch --no-tty --pinentry-mode error, so gpg-agent can only use an already cached passphrase. The owner's key is passphrase-protected and the cache was empty, so gpg failed (No pinentry) on the first file, the morimail-parent POM, exit 2. docs/release.md has no warm-up step, says 'gpg asks for the passphrase' (impossible under -B), and offers GPG_TTY as if it let the deploy prompt. The 04-04 rehearsal used a throwaway key without a passphrase, so it never hit this. The POM is not at fault."
  artifacts:
    - path: "docs/release.md"
      issue: "Step 2.3 presents GPG_TTY without a warm-up; step 5 lacks a gpg-agent warm-up before both deploy commands; 'gpg asks for the passphrase' is wrong under -B; nothing says where gpg's real error line appears (unprefixed 'gpg:' line under 'gpg:3.2.8:sign', above the Reactor Summary)"
  missing:
    - "In step 5, before each deploy command, a warm-up run in a real terminal (not a Claude Code ! shell): export GPG_TTY=$(tty); echo test | gpg --clearsign > /dev/null. Note the agent cache lasts 10 minutes after last use; warm up again if longer passes"
    - "Replace 'gpg asks for the passphrase' with: the deploy signs from the agent cache and cannot prompt, because of -B"
    - "Re-scope step 2.3: GPG_TTY is for the warm-up, it does not make the deploy prompt"
    - "One diagnosis line: on 'maven-gpg-plugin ... Exit code: 2', read the gpg: line under gpg:3.2.8:sign; 'No pinentry' means the cache was empty, warm up and rerun"
    - "Optional one sentence: with several secret keys, add --local-user <KEYID> to the warm-up and -Dgpg.keyname=<KEYID> to the deploy"
  debug_session: ".planning/debug/release-doc-gpg-signing.md"
