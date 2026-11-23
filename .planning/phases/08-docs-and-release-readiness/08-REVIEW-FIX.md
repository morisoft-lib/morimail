---
phase: 08-docs-and-release-readiness
fixed_at: 2026-11-23T23:00:00Z
review_path: /home/coder/morimail/.planning/phases/08-docs-and-release-readiness/08-REVIEW.md
iteration: 1
findings_in_scope: 2
fixed: 2
skipped: 0
status: all_fixed
---

# Phase 8: Code Review Fix Report

**Fixed at:** 2026-11-23T23:00:00Z
**Source review:** /home/coder/morimail/.planning/phases/08-docs-and-release-readiness/08-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope: 2
- Fixed: 2
- Skipped: 0

Both fixes are text edits to `docs/release.md`. No release, deploy, tag or publish command was run.

**Verification location:** the guard check ran in the isolated review-fix worktree (`bash` against scratch POM copies under the session scratchpad), not in the main checkout. There is no build gate for a doc-only edit, so no Maven run was made.

## Fixed Issues

### WR-01: SNAPSHOT guard fails open when grep errors

**Files modified:** `docs/release.md`
**Commit:** da6bc67
**Applied fix:** Both guard blocks (the `-DskipUpload=true` check and the real deploy) now read `grep -- -SNAPSHOT pom.xml morimail/pom.xml morimail-html/pom.xml; [ $? -eq 1 ] &&` followed by the `./mvnw` line. The deploy starts only when grep exits 1, meaning it searched all three files and found no match. The prose before the first block now states that rule and says that a grep error (for example `No such file`) also stops the deploy. The "copy both lines" wording was already present.

Verification: I extracted both blocks from the edited doc, replaced the `./mvnw` line with `echo would-deploy`, and ran each under bash against scratch POM copies in `/tmp/claude-1000/-home-coder-morimail/50ea31fe-8e2f-4ec9-b9cb-0bd776ede28f/scratchpad/guard`:

| Scratch case | Output | Deploy started |
|---|---|---|
| `-SNAPSHOT` in all three POMs | three match lines | no |
| `0.3.0` in all three POMs | none | yes |
| no POMs (wrong directory) | three grep errors | no |
| two SNAPSHOT POMs, one missing | two matches and one error | no |
| release POMs, one missing | one error | no |

Status: fixed. This is a logic change that Tier 1 and a syntax check cannot prove, but the table above exercises it directly. I did not run it under zsh (not installed here).

### WR-02: Procedure defaults and wording still target the already-released 0.2.0

**Files modified:** `docs/release.md`
**Commit:** 088d37d
**Applied fix:**
- Step 4 defaults changed to `VERSION=0.3.0` and `NEXT=0.4.0-SNAPSHOT`.
- Line 12 now says "If you already released any version" and "the key that signed that release".
- Step 8.3 no longer says "for the 0.2.0 release they already do". The README already says "0.2.0 is the current release on Maven Central", so the old instruction to replace a "not on Maven Central yet" sentence was also out of date. It now says to change the sentence under Coordinates that names the current release so that it names `${VERSION}`, and to change both dependency blocks if they do not show `${VERSION}`. This goes slightly beyond the reviewer's suggested wording, to match the current README.

---

_Fixed: 2026-11-23T23:00:00Z_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
