---
phase: 08-docs-and-release-readiness
reviewed: 2026-11-23T22:00:00Z
depth: standard
files_reviewed: 1
files_reviewed_list:
  - docs/release.md
findings:
  critical: 0
  warning: 2
  info: 0
  total: 2
  necessity: 0
status: issues_found
---

# Phase 8: Code Review Report

**Reviewed:** 2026-11-23T22:00:00Z
**Depth:** standard
**Files Reviewed:** 1
**Status:** issues_found

## Summary

This is an incremental re-review of `docs/release.md`. I focused on commit 2ad672c, which added the `! grep -- -SNAPSHOT ... &&` guard before both step 5 deploy commands and rewrote the SNAPSHOT bullet. I also checked the rest of the procedure against the current POMs, tags and README.

The SNAPSHOT bullet is accurate. `08-UAT.md` G-08-2 records the plugin behaviour from the 0.12.0 bytecode: a SNAPSHOT build is routed to the snapshots URL, and `skipUpload` stops it. The root POM has no `distributionManagement`, and the plugin supplies the snapshots URL itself.

I ran the guard as a two-line paste under bash against scratch copies of the POMs:
- With `-SNAPSHOT` in all three POMs, grep prints the three lines, the chained command does not start, and the exit status is 1.
- With `0.3.0` in all three POMs, nothing prints and the chained command starts.
- With a missing POM, or from the wrong working directory, grep exits 2 and the guard lets the deploy start. This is WR-01.

zsh is not installed here, so I could not run the guard there. By the language rules, `! ` followed by a space is not history-expanded in zsh and `!` is a reserved word, so I expect the same result as bash. That expectation is unverified.

The step 4 and step 8 `sed` lines, the step 7 tag commands and the quoting are copy-paste safe. I found no problem with `${VERSION}`, `\$` or `^{commit}` inside double quotes.

No critical issues. Two warnings.

## Warnings

### WR-01: SNAPSHOT guard fails open when grep errors

**File:** `docs/release.md:101` and `docs/release.md:136`
**Issue:** `! grep ...` negates grep's exit status, so exit 2 (error) counts as "no SNAPSHOT found" and the chained deploy starts. I reproduced this with bash:
- Run from a directory without the POMs: grep prints three `No such file` errors, then the next command runs.
- One POM missing while another still holds `-SNAPSHOT`: grep prints the match plus the error, exits 2, and the next command runs.

In normal use `./mvnw` is also missing from a wrong directory, so the damage is limited. The guard exists to be the only barrier against the unrecoverable SNAPSHOT upload from UAT G-08-2, and it should stop on any outcome other than "searched everything, found nothing". The doc also tells the reader that grep "prints that line and the deploy does not start", which is untrue in the error case.
**Fix:** Require grep's exit status to be exactly 1 (no match) instead of negating it. This is still a two-line paste and still prints the matching lines:
```
grep -- -SNAPSHOT pom.xml morimail/pom.xml morimail-html/pom.xml; [ $? -eq 1 ] &&
./mvnw -B -ntp -Prelease clean deploy -pl morimail,morimail-html -am
```
Change both blocks, and adjust the prose before the first block to say "copy both lines".

### WR-02: Procedure defaults and wording still target the already-released 0.2.0

**File:** `docs/release.md:12`, `docs/release.md:63-64`, `docs/release.md:169`
**Issue:** The tag `v0.2.0` exists, `2c4ea80` is the release commit, and the POMs are at `0.3.0-SNAPSHOT` (`8df5f6a`). The doc is still written as the procedure for 0.2.0:
- Step 4 sets `VERSION=0.2.0` and `NEXT=0.3.0-SNAPSHOT`. Pasted as written, `sed` matches nothing, because it looks for `0.2.0-SNAPSHOT`. Later steps then behave oddly. `git commit` finds nothing to commit. If the owner skips that, step 7's `--grep="^release 0.2.0\$"` matches the old release commit `2c4ea80`, and `git tag` fails because `v0.2.0` already exists.
- Line 12 says "If you already released 0.1.0", which is wrong now that 0.2.0 is out and signed with the same key.
- Line 169 says "for the 0.2.0 release they already do" (the README dependency blocks). That is now past tense for a finished release.

The step 4.3 diff check (exactly four changed lines) and the new guard stop the worst outcomes, but the first copy-paste of the next release fails confusingly.
**Fix:** Change the defaults to the next release, for example `VERSION=0.3.0` and `NEXT=0.4.0-SNAPSHOT`, or use an obvious placeholder (`VERSION=X.Y.Z`) with a note to set it. Reword line 12 to "If you already released any version, ..." and line 169 to "for a release where the README already shows `${VERSION}` nothing changes there". Do this as a doc-only edit; do not re-run any release step for 0.2.0.

---

_Reviewed: 2026-11-23T22:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
