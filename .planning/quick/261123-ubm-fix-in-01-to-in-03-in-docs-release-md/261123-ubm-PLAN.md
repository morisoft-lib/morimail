---
phase: quick-261123-ubm
plan: 01
type: execute
wave: 1
depends_on: []
files_modified:
  - docs/release.md
autonomous: true
requirements: [IN-01, IN-02, IN-03]   # the three Info findings of .planning/phases/08-docs-and-release-readiness/08-REVIEW.md

estimate:
  tokens: 20000             # reads 9k (this plan 4k, docs/release.md 3k, 08-REVIEW.md 2k) + three one-line edits 2k + content gate 1k + offline verify output tail 2k + commit and commit gate 1k + SUMMARY 2k + slack 3k
  raw_tokens: 20000         # factor 1: estimate-calibration has 0 samples
  tasks: 1
  confidence: low

must_haves:
  truths:
    - 'IN-03: in docs/release.md step 4.1, the sentence under the VERSION/NEXT block says that steps 7 and 8 use `VERSION`, that step 8 also uses `NEXT`, and that step 5 needs neither because it names `${VERSION}` only in the bundle listing. It still ends with "Set them again in every new shell."'
    - 'IN-02: docs/release.md step 8.1 carries the same note as step 4.2, "On macOS use `sed -i ''''`.", so a reader who starts at step 8 sees it. The step 8 sed command is unchanged'
    - 'IN-01: docs/release.md step 8.3 tells the owner to rewrite the README.md text under Coordinates so that it names `${VERSION}` and says what that release adds, because the old text describes the previous release. The check of both dependency blocks stays'
    - 'Only these three lines of docs/release.md change (3 insertions, 3 deletions). Every fenced command (the step 4 and step 8 sed lines, the guard and deploy lines, the warm-ups, the tag commands), the bundle listing and the headings stay byte-identical. README.md, the POMs and every other file are untouched'
    - '`./mvnw -B -ntp -o verify` is green. The change is one local commit that holds docs/release.md only, authored and committed as Stephan Wald <stephan@morisoft.de> with the Co-Authored-By trailer, and not pushed'
  artifacts:
    - path: "docs/release.md"
      provides: "the release procedure with the step 4.1 variable note, the step 8.1 macOS note and the step 8.3 README instruction corrected"
      contains: "Steps 7 and 8 use `VERSION`"
  key_links:
    - from: "docs/release.md"
      to: "README.md"
      via: "step 8.3 tells the owner to rewrite the Coordinates text (README.md line 21 today) for the new release; this task does not edit README.md"
      pattern: "rewrite the text under Coordinates"
    - from: "docs/release.md"
      to: "pom.xml"
      via: "the step 8.1 sed command over the four POMs, now preceded by the same BSD sed note as step 4.2; the command itself is unchanged"
      pattern: "On macOS use .sed -i ''"
---

<objective>
Fix the three Info findings IN-01, IN-02 and IN-03 from the Phase 8 re-review (`.planning/phases/08-docs-and-release-readiness/08-REVIEW.md`) in `docs/release.md`. Each fix rewrites one prose line; no command changes.

- IN-03 (line 67): the sentence after the VERSION/NEXT block names the wrong steps. Only steps 7 and 8 read the shell variables; step 5 names `${VERSION}` only in the bundle listing.
- IN-02 (line 162): step 8.1 runs `sed -i` without the macOS note that step 4.2 has.
- IN-01 (line 169): step 8.3 asks only for the version number to be changed in the README Coordinates text, but that text also says what the release adds, so it must be rewritten for each release.

Purpose: post-phase polish of the release procedure that the owner follows for the next release (0.3.0). Phase 8 and milestone v0.2.0 are complete; this changes no behaviour.
Output: `docs/release.md` with three corrected lines, in one commit that touches nothing else.

Tracer-first does not apply: the whole change is three prose lines in one file, with no layers to wire, so the plan is one `auto` task (Rule 0: build less).
</objective>

<execution_context>
@~/.claude-shared/dot-claude/gsd-core/workflows/execute-plan.md
@~/.claude-shared/dot-claude/gsd-core/templates/summary.md
</execution_context>

<context>
@CLAUDE.md
@.claude/CLAUDE.md
@.planning/STATE.md
@.planning/phases/08-docs-and-release-readiness/08-REVIEW.md
@docs/release.md
</context>

<tasks>

<task type="auto">
  <name>Task 1: Rewrite the three prose lines of docs/release.md named by IN-01 to IN-03, verify offline, commit</name>
  <files>docs/release.md</files>
  <precondition>`git diff HEAD --quiet -- docs/release.md` succeeds (no uncommitted change to docs/release.md), so the line numbers below match the review.</precondition>
  <read_first>
    - docs/release.md (whole file, 170 lines; the edits are at lines 67, 162 and 169)
    - .planning/phases/08-docs-and-release-readiness/08-REVIEW.md (section "Info"; its fix wording is a non-binding example, the wording below is the one to use)
  </read_first>
  <action>
Edit `docs/release.md` with the Edit tool. Replace exactly three whole lines and keep each one a single Markdown line with its current indentation, so the diff is 3 insertions and 3 deletions. Do not touch any fenced block, heading, blank line or other line.

1. IN-03, line 67 (inside step 4.1, indented three spaces, the line directly under the fenced VERSION/NEXT block). Replace the whole line with this text, keeping the three-space indent:
   Steps 7 and 8 use `VERSION`, and step 8 also uses `NEXT`. Step 5 needs neither; it names `${VERSION}` only in the bundle listing. Set them again in every new shell.
   This is accurate: the step 5 commands (warm-up, guard and deploy) contain no shell variable, step 6 has none, step 7 reads `VERSION`, step 8 reads `VERSION` and `NEXT`.

2. IN-02, line 162 (step 8.1 lead-in, which today ends in a colon). Replace the whole line with:
   1. Set the four versions to the next SNAPSHOT. On macOS use `sed -i ''`.
   This mirrors step 4.2 at line 69, which ends its sentence the same way before its fenced sed block. The fenced sed command at line 165 stays as it is.

3. IN-01, line 169 (step 8.3). Replace the whole line with:
   3. In `README.md`, rewrite the text under Coordinates that names the current release on Maven Central. Name `${VERSION}` and say what that release adds; the old text describes the previous release. Check that both dependency blocks show `${VERSION}`, and change them if they do not.
   README.md has two dependency blocks (lines 17 and 113), so "both" stays correct.

Do not edit README.md now: step 8.3 is an instruction for the owner at the next release, not work for this task. Do not edit the POMs, `docs/spec.md`, `docs/architecture.md` or any planning file in this commit. The new text names no version number, date, address or host (the file is version-neutral since 08-02 apart from the two step 4.1 defaults).

Then run the four content gates and `./mvnw -B -ntp -o verify` from `<verify>` (allow up to 10 minutes; the build is offline and uses the cached wrapper Maven 3.10.1 and `~/.m2`). docs/release.md is read by no test, so a red build means something else is wrong in the tree: stop and report it, do not change other files to make it green.

When both are green, commit only this file, never with `git add -A` or `git add .`, because the working tree holds untracked planning files (`.planning/phases/07-html-image-helper-module/07-PATTERNS.md`, `.planning/state.json`) that must stay out of this commit:
   git add docs/release.md
   git commit -m with subject `docs(quick-261123-ubm): fix IN-01 to IN-03 in docs/release.md`, a short body naming the three fixes (step 4.1 variable note, step 8.1 macOS note, step 8.3 README rewrite), and the last line `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
The repo-local git config and the pre-commit hook set the identity Stephan Wald <stephan@morisoft.de>; do not override them. Do not push, tag or deploy (CLAUDE.md: never publish, deploy or tag).
  </action>
  <verify>
    <automated>F=docs/release.md; test "$(grep -c 'Steps 5 to 8' $F)" -eq 0 && test "$(grep -c 'change the sentence under Coordinates' $F)" -eq 0 # negatives: the old IN-03 and IN-01 wordings are gone [HEAD=1 EXPECT==0 @4922caa] (exit status; both grep -c printed 1 at HEAD)</automated>
    <automated>F=docs/release.md; S=$(sed -n '/^## 8\./,$p' $F); test "$(grep -c 'Steps 7 and 8 use' $F)" -ge 1 && test "$(grep -c 'Step 5 needs neither' $F)" -ge 1 && test "$(printf '%s\n' "$S" | grep -c 'rewrite the text under Coordinates')" -ge 1 && test "$(printf '%s\n' "$S" | grep -c 'say what that release adds')" -ge 1 # positives: the new IN-03 wording, and the new IN-01 wording inside step 8 [HEAD=1 EXPECT==0 @4922caa] (exit status; every grep -c printed 0 at HEAD; passes on a scratch copy with the three planned lines applied)</automated>
    <automated>test "$(grep -n "On macOS use .sed -i ''" docs/release.md | cut -d: -f1 | tr '\n' ' ')" = "69 162 " # IN-02: the macOS note sits on step 4.2 (line 69) and step 8.1 (line 162) and nowhere else [HEAD=1 EXPECT==0 @4922caa] (exit status; grep -n printed line 69 only at HEAD; passes on the scratch copy)</automated>
    <automated>test "$(git diff HEAD --numstat -- docs/release.md)" = "$(printf '3\t3\tdocs/release.md')" # scope: exactly three lines replaced; run before the commit [HEAD=1 EXPECT==0 @4922caa] (exit status; numstat printed nothing at HEAD; the scratch copy gives 3 3)</automated>
    <automated>set -o pipefail; ./mvnw -B -ntp -o verify 2>&1 | tail -20 # [HEAD=0 EXPECT==0 GUARD MUTATION: a compile error or a failing test in any module UNPINNED] (exit status; a regression guard, since a docs-only change cannot break the build; green in the Phase 8 verification, not re-run while planning because build gates are not executed at plan time)</automated>
  </verify>
  <done>Before the commit, the four content gates exit 0: the three target lines carry the new text, the two old wordings are gone, the macOS note is on lines 69 and 162 only, and `git diff HEAD --numstat` shows exactly 3 insertions and 3 deletions in docs/release.md. `./mvnw -B -ntp -o verify` ends with BUILD SUCCESS. One commit with the subject `docs(quick-261123-ubm): fix IN-01 to IN-03 in docs/release.md` exists locally and passes the commit gate in `<verification>`.</done>
  <reversibility rating="reversible">Three prose lines in a doc, in one local unpushed commit; `git revert` undoes it.</reversibility>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| docs/release.md to the owner's shell | The owner pastes the fenced commands of this file into a terminal during a release to Maven Central, which cannot be undone |
| repository to the public | The repository is public and git history is permanent |

## STRIDE Threat Register

| Threat ID | Category | Component | Severity | Disposition | Mitigation Plan |
|-----------|----------|-----------|----------|-------------|-----------------|
| T-ubm-01 | Tampering | docs/release.md fenced commands (step 4 and 8 sed, step 5 guard and deploy, step 7 tag) | medium | mitigate | Only three prose lines change; the content gate requires `git diff HEAD --numstat` to be exactly 3 insertions and 3 deletions, and every replaced line is outside a fenced block, so no pasted command can change |
| T-ubm-02 | Information disclosure | new text in docs/release.md and the commit message | low | mitigate | The new lines are fixed in the action and hold no address, host, tenant ID, key or secret; the commit gate fails if an added line holds a mail address or a home path |
| T-ubm-03 | Repudiation | commit identity | low | mitigate | The repo-local config and pre-commit hook set Stephan Wald <stephan@morisoft.de>; the commit gate checks author and committer and the Co-Authored-By trailer |
| T-ubm-SC | Tampering | npm/pip/cargo installs | low | accept | This plan installs no package; `./mvnw -B -ntp -o verify` runs offline from the cached wrapper Maven 3.10.1 and `~/.m2`, and no dependency changes |
</threat_model>

<verification>
After the commit, run the commit gate from the repository root:

C=$(git log -1 --format=%H --grep='^docs(quick-261123-ubm): fix IN-01 to IN-03') && test -n "$C" && test "$(git show --numstat --format= "$C")" = "$(printf '3\t3\tdocs/release.md')" && test "$(git log -1 --format='%an <%ae>|%cn <%ce>' "$C")" = 'Stephan Wald <stephan@morisoft.de>|Stephan Wald <stephan@morisoft.de>' && git log -1 --format=%B "$C" | grep -q '^Co-Authored-By: Claude' && ! git show -U0 --format= "$C" | grep '^+[^+]' | grep -qE '[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]+|/home/' && ! git merge-base --is-ancestor "$C" origin/main && echo "commit gate: ok"

[HEAD=1 EXPECT==0 @4922caa] (exit status; no such commit at HEAD). It proves the commit touches docs/release.md only, with 3 insertions and 3 deletions, under the maintainer identity with the trailer, with no address or home path in the added lines, and not pushed (origin/main is at 8df5f6a).

Also confirm `git status --short` still lists the two untracked planning files and no modified tracked file outside `.planning/`.
</verification>

<success_criteria>
- IN-01, IN-02 and IN-03 are each fixed on their own line of docs/release.md, with the wording given in the task.
- No command, heading or other line of docs/release.md changes; README.md, POMs and all other files are untouched.
- `./mvnw -B -ntp -o verify` is green.
- One local commit, docs/release.md only, maintainer identity, trailer, not pushed.
</success_criteria>

<output>
Create `.planning/quick/261123-ubm-fix-in-01-to-in-03-in-docs-release-md/261123-ubm-SUMMARY.md` when done
</output>
