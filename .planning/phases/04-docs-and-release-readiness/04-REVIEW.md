---
phase: 04-docs-and-release-readiness
reviewed: 2026-11-22T21:30:00Z
depth: standard
files_reviewed: 1
files_reviewed_list:
  - docs/release.md
findings:
  critical: 0
  warning: 1
  info: 3
  total: 4
  necessity: 0
status: issues_found
---

# Phase 4: Code Review Report

**Reviewed:** 2026-11-22T21:30:00Z
**Depth:** standard
**Files Reviewed:** 1
**Status:** issues_found

## Summary

This is an incremental review of `docs/release.md` since `d3c7803`. The range has two commits: ce7bf8b (WR-03 fix: push and wait for CI in step 4, tag-only push in step 7) and c359ec1 (04-05: gpg-agent warm-up before each `-B` deploy, `GPG_TTY` scoping, how to read a gpg sign failure, and a hint for several keys). I reviewed the whole file, because the new step 5 text and the older step 7 now run in sequence for the owner.

I checked the gpg and Maven claims against the diagnosis in `.planning/debug/release-doc-gpg-signing.md` (bytecode and repro evidence) and against the repository:
- Under `-B`, maven-gpg-plugin 3.2.8 adds `--batch --no-tty --pinentry-mode error`. This is confirmed.
- `-Dgpg.keyname` maps to the plugin's `keyname` parameter, which becomes `--local-user`.
- The gpg-agent default-cache-ttl is 600 s and resets on each use.
- `gpg: signing failed: No pinentry` is the cold-cache message.
- The raw `gpg:` line comes under `--- gpg:3.2.8:sign` and above the Reactor Summary.
- `target/central-publishing/central-bundle.zip` currently lists exactly 30 entries with `unzip -Z1`, with no directory entries.
- The pom.xml release profile has one `sign-artifacts` execution and no configuration, which matches what the doc says.

Both warm-up blocks are correct shell. Each one comes right before its deploy command, and neither is merged with a deploy line, so pasted text cannot land in pinentry. The nine headings and both `-B` commands are unchanged.

Public-repo hygiene is clean. The file has no hex key IDs, no fingerprints and no tokens. The only address is the maintainer identity, and keys appear only as `<KEYID>` placeholders.

The one real defect is how step 7 interacts with the repository's current state. Step 7 tags `HEAD`, but the owner was told to continue at step 5 with docs commits already on top of the release commit. The three Info items are wording gaps in the new gpg text that could send the owner the wrong way while troubleshooting.

## Narrative Findings (AI reviewer)

## Warnings

### WR-01: Step 7 tags HEAD, not the release commit, so the tag lands on unpushed docs commits

**File:** `docs/release.md:125-128`
**Issue:** Step 7 runs `git tag -a v0.1.0 -m "morimail 0.1.0"`, which tags whatever `HEAD` is. Then `git push origin v0.1.0` pushes only the tag. ce7bf8b dropped `main` from that push. The runbook assumes `HEAD` is still the "release 0.1.0" commit from step 4, but the repository is not in that state:
- `origin/main` is `72e7f7f`, the CI-green release commit.
- Local `HEAD` is five commits ahead: `85320b0`, `536f8aa`, `1f7871d`, `c359ec1` and `3841bf3`.
- 04-05-SUMMARY.md tells the owner to continue at step 5 without redoing step 4, and to "push the fix commit when convenient".

If the owner follows the doc literally, `v0.1.0` points at `3841bf3` or later. That commit is not the one CI tested, not the one step 4 pushed, and not on `origin/main`. The tag push then publishes commits that `main` does not contain. The build inputs happen to be identical, so the artifacts would match. But the tag would no longer mark the released commit, and moving a public tag afterwards is disruptive. The same trap comes back in any future release where a commit lands between step 4 and step 7.
**Fix:** Tag the release commit explicitly, and keep step 7 independent of `HEAD`:
```
git tag -a v0.1.0 -m "morimail 0.1.0" "$(git log -1 --format=%H --grep='^release 0\.1\.0$')"
git show --no-patch v0.1.0^{commit}   # must be the "release 0.1.0" commit that CI passed
git push origin v0.1.0
```
Alternatively, add one sentence to step 7: "Tag the commit 'release 0.1.0' from step 4, not a later commit." Separately, tell the owner in the hand-over that `main` must be pushed before or with the tag.

## Info

### IN-01: The doc puts the "cannot prompt" behaviour on batch mode, but `--pinentry-mode error` is the real cause

**File:** `docs/release.md:68`
**Issue:** The doc says: "the gpg plugin runs gpg in batch mode, where it cannot ask for the passphrase." In GnuPG 2.1 and later, `--batch` alone does not stop gpg-agent from starting pinentry. What blocks the prompt is the `--pinentry-mode error` that the plugin adds under `-B` when no passphrase source is set (diagnosis, 20:30 bytecode evidence). An owner who checks the gpg man page will find that batch mode can still prompt, and may decide the doc is wrong. The failure paragraph's `No pinentry` text is the direct result of `--pinentry-mode error`, so naming that flag also ties the two paragraphs together.
**Fix:** "Both deploy commands use `-B`, so the gpg plugin calls gpg with `--pinentry-mode error`, which forbids a passphrase prompt."

### IN-02: "No pinentry means the agent cache was empty" is incomplete when several secret keys exist

**File:** `docs/release.md:115`
**Issue:** With several secret keys and no `-Dgpg.keyname`, the warm-up and the plugin may pick different keys. The cache then holds a passphrase, but not for the key the plugin uses, and the deploy still fails with `No pinentry`. The paragraph's remedy ("warm up and run the same command again") then loops without ever working. The several-keys sentence on line 75 has the fix, but the failure paragraph does not point to it.
**Fix:** "`gpg: signing failed: No pinentry` means the agent held no passphrase for the signing key: warm up and run the same command again. If you have several secret keys, check that the warm-up and the deploy name the same `<KEYID>`."

### IN-03: The warm-up gives no success signal, so a failed warm-up is only discovered by the deploy

**File:** `docs/release.md:70-73`, `docs/release.md:102-105`
**Issue:** `echo test | gpg --clearsign > /dev/null` prints nothing when it succeeds. When it fails, it prints one `gpg:` line on stderr. Three cases from the diagnosis show this:
- W1, `GPG_TTY` unset: "Inappropriate ioctl for device".
- C2, `GPG_TTY="not a tty"` from a `!` shell: "No such file or directory".
- A cancelled or mistyped pinentry.

`export GPG_TTY=$(tty)` also exits 0 even when `tty` prints `not a tty`. The doc never tells the owner to check the warm-up's output, so a failed warm-up goes unnoticed. The deploy then fails with exit code 2, and only the failure paragraph gets the owner back on track.
**Fix:** Add one sentence after the first block: "The warm-up prints nothing on success. If it prints a `gpg:` line, fix that before you deploy." Optionally append `&& echo warm-up OK` to the gpg line in both blocks.

---

_Reviewed: 2026-11-22T21:30:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
