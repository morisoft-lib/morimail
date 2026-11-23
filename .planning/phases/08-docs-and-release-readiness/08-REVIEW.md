---
phase: 08-docs-and-release-readiness
reviewed: 2026-11-23T23:30:00Z
depth: standard
files_reviewed: 1
files_reviewed_list:
  - docs/release.md
findings:
  critical: 0
  warning: 0
  info: 3
  total: 3
  necessity: 0
status: issues_found
---

# Phase 8: Code Review Report

**Reviewed:** 2026-11-23T23:30:00Z
**Depth:** standard
**Files Reviewed:** 1
**Status:** issues_found

## Summary

This is the incremental re-review of `docs/release.md` after the fixes in da6bc67 (WR-01) and 088d37d (WR-02). I read the whole file and checked it against the four POMs, `README.md`, the tags and the commit log.

**Status of the prior findings**

- **WR-01 (SNAPSHOT guard fails open when grep errors): fixed.** Both blocks, at lines 101-102 and 136-137, now read `grep -- -SNAPSHOT pom.xml morimail/pom.xml morimail-html/pom.xml; [ $? -eq 1 ] &&` followed by the `./mvnw` line. I ran the two-line paste under `bash -c`:
  - With `0.3.0` in all three scratch POMs, nothing is printed and the deploy line runs.
  - With `-SNAPSHOT` in one POM, grep prints the match (exit 0) and the deploy line does not run.
  - A missing file gives grep exit 2, which also fails `[ $? -eq 1 ]`, so the deploy does not run. This follows from grep's exit codes and from the fix report's test table. I did not re-run it.
  - The guard has no `!`, so history expansion is not an issue. `$?` is read straight after grep. The trailing `&&` continues onto the next line in bash and zsh, so pasting both lines works. zsh is not installed here, so I did not run it there. Nothing in the guard is shell-specific.
  - The prose at line 98 matches the behaviour: a match stops the deploy, and so does a grep error such as `No such file`.
- **WR-02 (defaults target the released 0.2.0): fixed.** `VERSION=0.3.0` and `NEXT=0.4.0-SNAPSHOT` match the POMs at `0.3.0-SNAPSHOT`.
  - Only the four expected lines match the step 4 `sed` pattern: the root `<version>` at `pom.xml:10` and the parent `<version>` at line 10 of each of the three module POMs. All other `<version>` entries are `${...}` properties or plugin versions, so the step 4.3 diff check and the step 8 reverse `sed` (`<version>${VERSION}</version>` to `${NEXT}`) touch exactly four lines.
  - No tag `v0.3.0` and no commit "release 0.3.0" exists yet, so the step 7 `--grep="^release ${VERSION}\$"` cannot match the `release 0.2.0` commit (2c4ea80).
  - The only `0.x.y` strings left in the file are the two defaults at lines 63-64. Line 12 now says "any version".

The guard is correct and the VERSION/NEXT wording is consistent across steps 4, 7 and 8. The step 7 quoting (`\$`, `^{commit}`) is safe in bash and zsh. The guard checks three POMs and step 4 edits four. This is intentional, because the sample POM is not deployed (`-pl morimail,morimail-html -am`), and the prose says "three POMs". I found no new correctness issue. Three minor wording points follow.

## Info

### IN-01: README step 8 only renames the version, but the Coordinates sentence is release-specific

**File:** `docs/release.md:169`
**Issue:** The README sentence under Coordinates currently reads "0.2.0 is the current release on Maven Central. It adds inline images and the `morimail-html` helper." (`README.md:21`). Step 8.3 says to change the sentence "so that it names `${VERSION}`". Following that literally for 0.3.0 gives "0.3.0 is the current release ... It adds inline images and the `morimail-html` helper", which is false. The second sentence describes what 0.2.0 added.
**Fix:** Say to rewrite the sentence, for example: "In `README.md`, rewrite the sentence under Coordinates so that it names `${VERSION}` as the current release on Maven Central and describes what that release adds. Check that both dependency blocks show `${VERSION}`, and change them if they do not."

### IN-02: Step 8 `sed -i` has no macOS note

**File:** `docs/release.md:165`
**Issue:** Step 4 (line 69) says "On macOS use `sed -i ''`", but step 8 repeats the same `sed -i` form without the note. On BSD sed, `sed -i "s#...#...#" pom.xml ...` takes the script as the backup suffix and then fails or misreads the file list. A reader who starts at step 8 in a new shell, as step 4 invites, may miss the note. Step 8 is after the Central publish and the tag, so a failed sed there is recoverable, but it is confusing.
**Fix:** Add "On macOS use `sed -i ''`." to step 8.1, or move the note above both `sed` commands.

### IN-03: "Steps 5 to 8 use `VERSION`" overstates the shell dependency

**File:** `docs/release.md:67`
**Issue:** Step 5 only names `${VERSION}` as a placeholder in the bundle file list, which is prose, not a command. Step 6 does not use it. Only steps 7 and 8 read the shell variables (`VERSION` in both, `NEXT` in step 8). The sentence implies that the variables must be set for the step 5 deploy, which they do not. The guard and deploy commands contain no variables.
**Fix:** "Steps 7 and 8 use `VERSION`, and step 8 also uses `NEXT`. Step 5 names `${VERSION}` only in the bundle listing. Set them again in every new shell."

---

_Reviewed: 2026-11-23T23:30:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
