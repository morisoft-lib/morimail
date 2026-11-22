---
phase: 04-docs-and-release-readiness
reviewed: 2026-11-22T00:00:00Z
depth: standard
files_reviewed: 1
files_reviewed_list:
  - docs/release.md
findings:
  critical: 0
  warning: 1
  info: 0
  total: 1
  necessity: 0
status: issues_found
---

# Phase 4: Code Review Report

**Reviewed:** 2026-11-22
**Depth:** standard
**Files Reviewed:** 1
**Status:** issues_found

## Summary

This is an incremental review of `git diff b75c8ac HEAD -- docs/release.md`. I read the whole file for context and checked the new step 7 command by running it. I checked the gpg wording against `.planning/debug/release-doc-gpg-signing.md`. I also scanned for public-repo hygiene problems.

`docs/release.md` itself is correct and clean. The one finding is in a different tracked file, which is the source of the gpg wording.

**Step 7 tag command: correct.**
- Empty grep: I tested this in a scratch repo. When no commit matches, `git log` prints nothing and exits 0. The command substitution is double-quoted, so git receives `""` as the commit argument. Git then fails with `fatal: Failed to resolve '' as a valid ref.` (exit 128) and creates no tag. It fails loudly and does not silently tag `HEAD`.
- Pattern: `^release 0\.1\.0$` is a valid basic regex and is anchored per message line. A commit with the subject "release 0.1.0 candidate" does not match.
- Real repo: in this repository the command resolves to `72e7f7f`, which matches `v0.1.0^{commit}`.
- `v0.1.0^{commit}` is unquoted. `^` and `{commit}` are not special in bash or dash. I ran it in both. I could not run it in zsh, which is not installed here. Without `EXTENDED_GLOB`, zsh does not treat `^` as special. Brace expansion needs a comma or `..`, so `{commit}` is not expanded either. The doc already assumes a POSIX shell, since `mvnw` needs one. No change is needed.

**gpg wording: matches the debug file.**
- `-B` leads to `--pinentry-mode error`, which forbids a prompt. This is stated correctly.
- `GPG_TTY` is scoped to the warm-up. This is stated correctly.
- The 10-minute cache lifetime is correct. It counts from the last use, as the doc says.
- "No pinentry" means the agent held no passphrase for the signing key. This matches the diagnosis.
- The multi-key `--local-user` / `-Dgpg.keyname` advice is consistent.
- The "prints nothing on success" claim holds, because stdout is sent to `/dev/null` and gpg 2.4 prints nothing on stderr on success.

**Hygiene in `docs/release.md`:** The doc has no key IDs, fingerprints, tokens or real addresses. The only identity is the maintainer identity, which section 13.4 allows. `<KEYID>` and `TOKEN_USERNAME` are placeholders.

No additions are recommended. The doc follows Rule 0.

## Warnings

### WR-01: Tracked debug note commits the maintainer's GPG key ID to the public repo

**File:** `.planning/debug/release-doc-gpg-signing.md:23`, also lines 38, 50, 105 and 127
**Issue:** The file is tracked (added in `536f8aa`). It is the evidence base for the `docs/release.md` gpg wording. It contains the owner's real 16-hex long key ID (also on line 38, as `ed25519 <ID>`). The project rule says no key IDs or fingerprints in any file. The repository is public and history is permanent. The key ID is public-key metadata and is already visible in the Central signatures and on the keyserver. It is therefore not a secret, but it breaks the stated hygiene rule. The same file also records details of the owner's setup, such as keyring state, `protection=P` and `cached=1`. It names the Claude Code `!` shell and the workspace's agent socket and pinentry layout.
**Fix:** Replace the ID with `<KEYID>`. The owner chose to rewrite the unpushed history (no remote branch contained the file), so the ID never reaches GitHub.

---

_Reviewed: 2026-11-22_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
