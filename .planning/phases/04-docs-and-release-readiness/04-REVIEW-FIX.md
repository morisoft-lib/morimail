---
phase: 04-docs-and-release-readiness
fixed_at: 2026-11-22T22:00:00Z
review_path: /home/coder/morimail/.planning/phases/04-docs-and-release-readiness/04-REVIEW.md
iteration: 1
findings_in_scope: 4
fixed: 4
skipped: 0
status: all_fixed
---

# Phase 4: Code Review Fix Report

**Fixed at:** 2026-11-22T22:00:00Z
**Source review:** /home/coder/morimail/.planning/phases/04-docs-and-release-readiness/04-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope: 4
- Fixed: 4
- Skipped: 0

All four findings are in `docs/release.md`. The fixes were made in an isolated worktree and fast-forwarded onto `main`. Each claim was checked against `.planning/debug/release-doc-gpg-signing.md` before it was written. Verification was Tier 1 (re-read of the diff). The file is Markdown, so no syntax check applies, and no build file was touched. The tag command in WR-01 was also run once in the worktree against a temporary local tag, which was then deleted. `git log -1 --grep='^release 0\.1\.0$'` resolves to `72e7f7f`, the CI-green release commit, not to `HEAD`.

## Fixed Issues

### WR-01: Step 7 tags HEAD, not the release commit, so the tag lands on unpushed docs commits

**Files modified:** `docs/release.md`
**Commit:** 794661d
**Applied fix:** Step 7 now tags the commit found by `git log -1 --format=%H --grep='^release 0\.1\.0$'`. A `git show --no-patch --format='%h %s' v0.1.0^{commit}` line sits before the push, so the owner can confirm which commit the tag points at. A sentence above the block explains why `HEAD` is not used. The hand-over remark about pushing `main` was not added to the doc. The tag-only push is safe now, because the tagged commit is already on `origin/main` from step 4.
**Status:** fixed: requires human verification (the command is a logic change in a runbook, so the owner should read it once before the real tag).

### IN-01: The doc puts the "cannot prompt" behaviour on batch mode, but `--pinentry-mode error` is the real cause

**Files modified:** `docs/release.md`
**Commit:** 49cfe7b
**Applied fix:** Step 5 now says the gpg plugin calls gpg with `--pinentry-mode error`, which forbids a passphrase prompt. This replaces "runs gpg in batch mode, where it cannot ask for the passphrase". The diagnosis (GpgSigner bytecode, repro case B) confirms the flag.

### IN-02: "No pinentry means the agent cache was empty" is incomplete when several secret keys exist

**Files modified:** `docs/release.md`
**Commit:** 7b97943
**Applied fix:** The failure paragraph now says the agent held no passphrase for the signing key. It also tells the owner with several secret keys to check that the warm-up and the deploy name the same `<KEYID>`. The key ID stays a placeholder.

### IN-03: The warm-up gives no success signal, so a failed warm-up is only discovered by the deploy

**Files modified:** `docs/release.md`
**Commit:** f9096a9
**Applied fix:** Added one sentence after the first warm-up block: "The warm-up prints nothing on success. If it prints a `gpg:` line, fix that before you deploy." The optional `&& echo warm-up OK` was not added (Rule 0).

---

_Fixed: 2026-11-22T22:00:00Z_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
