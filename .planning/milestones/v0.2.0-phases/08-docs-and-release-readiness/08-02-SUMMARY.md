---
phase: 08-docs-and-release-readiness
plan: 02
subsystem: release
tags: [release, maven-central, gpg, rehearsal, docs]
status: complete

requires:
  - phase: 08-docs-and-release-readiness
    provides: 08-01 README sentence under Coordinates (0.2.0 not on Central yet, 0.1.0 current; superseded, the owner has since released 0.2.0 as tag v0.2.0), named by release.md step 8
  - phase: 07-html-image-helper-module
    provides: morimail-html module and its release profile (tests bound to phase none), the scoped release line in CI
provides:
  - docs/release.md as one version-neutral procedure (VERSION, NEXT) for morimail-parent, morimail and morimail-html
  - the 54-entry, nine-base-file bundle list, rehearsed with a throwaway key
  - the phase release gate: offline verify and scoped release build green, zero WARN lines
affects: [owner release of 0.2.0]

actuals:
  tokens: 2300
  tasks: 2
  commits: 1
plan_head_before: fc68d3fedb4e25e04d6780d33c7b18dd9dd3e6be
plan_head_after: 6298229c63f2c4041d49d244fbdc7f4db7afef9c

tech-stack:
  added: []
  patterns: ["release steps use VERSION and NEXT shell variables, and one -pl set equal to the CI release line"]

key-files:
  created: []
  modified:
    - docs/release.md

key-decisions:
  - "08-02: docs/release.md version-neutral (VERSION/NEXT, four POMs, -pl morimail,morimail-html -am as in CI, 54-entry bundle listed with the VERSION variable); rehearsed 2026-11-23 with a throwaway key, upload skipped, cleanup of the key and of the local de/morisoft artifacts; offline verify and scoped release build green with zero warning lines"

requirements-completed: [DOCS-06, DOCS-07]

duration: 15 min
completed: 2026-11-23
---

# Phase 8 Plan 02: `docs/release.md` for both published jars, rehearsed, and the release gate Summary

**`docs/release.md` is now one version-neutral procedure for the parent POM, `morimail` and `morimail-html`. Its step 4 and the skipUpload check, run as written with a throwaway key, built exactly the 54-entry bundle it lists, and nothing was uploaded.**

## Accomplishments

- D-12, D-15: the intro names `de.morisoft:morimail-parent`, `de.morisoft:morimail` and `de.morisoft:morimail-html`, released with one version. Step 4 sets `VERSION=0.2.0` and `NEXT=0.3.0-SNAPSHOT`, a sed anchored to the `<version>` element covers the four POMs, `git diff` expects exactly four changed lines, and the commit is "release ${VERSION}". Steps 7 and 8 use the variables. `VERSION=0.2.0` is the only line that names a release version, and the file holds no date. The headings and sections 1, 2, 3 and 6 are byte-identical to 400357d. No 0.2.0-specific doc pre-check list was added.
- D-13: `-pl morimail,morimail-html -am` appears three times (the CI bullet, the skipUpload check, the real deploy), equal to `.github/workflows/ci.yml`. The deploy text says the `morimail-html` tests do not run in a release-profile build and that step 4 ran them.
- D-12, D-14: step 5 lists the nine base files with `${VERSION}`, so the list holds for the next release unchanged. Step 8 names the sentence under Coordinates that 08-01 wrote.
- D-14: the rehearsal below ran the step 4 sed and the skipUpload deploy exactly as release.md writes them.
- DOCS-06: after the cleanup, the offline verify and the scoped release build are green (results below).

## Rehearsal evidence (D-14)

- Date of the run: 2026-11-23 (release.md carries no date, so this is the record of when the bundle list was last rehearsed).
- Printed summary line: `rehearsal: 54 entries, 9 good signatures, upload skipped`
- Setup: `git archive` copy of HEAD in a temporary directory (the working tree's POMs never changed), RSA 2048 throwaway key without passphrase in `$HOME/.gnupg-rh08` (named `throwaway@example.com`, valid 1 day), the step 4 sed with `VERSION=0.2.0` changing exactly four lines, the skipUpload deploy online with `-s` pointing at an isolated settings file whose `central` server holds dummy credentials. `-o` was not used.
- Plugin line from the log: `Skipping the upload of the bundle at user's request (skipUpload)`.
- Bundle: `central-bundle.zip` with exactly 54 entries under `de/morisoft/{morimail,morimail-html,morimail-parent}/0.2.0`, nine each of `.asc`, `.md5`, `.sha1`, `.sha256` and `.sha512`, no `-tests.jar`, no sample entry.
- The nine base files (equal to the release.md list with `${VERSION}` read as 0.2.0): `morimail-parent-0.2.0.pom`, `morimail-0.2.0.pom`, `morimail-0.2.0.jar`, `morimail-0.2.0-sources.jar`, `morimail-0.2.0-javadoc.jar`, `morimail-html-0.2.0.pom`, `morimail-html-0.2.0.jar`, `morimail-html-0.2.0-sources.jar`, `morimail-html-0.2.0-javadoc.jar`.
- All 9 signatures verified (GOODSIG). No key id or fingerprint is recorded here.

## Release safety

- D-14 is the owner's authority for the `-DskipUpload=true` rehearsal deploy. It is the one exception to the CLAUDE.md rule "Never publish, deploy or tag a release".
- That rehearsal was the only deploy Claude ran. It carried `-DskipUpload=true`, ran with an isolated settings file with dummy credentials, and never used `-o`. The log has the plugin's skipUpload line.
- Claude ran no real deploy, created no tag and pushed nothing. The cleanup gate found no `v0.2*` tag, and the commit gate found no commit on origin/main.
- Cleanup: the rehearsal's exit trap removed `$HOME/.gnupg-rh08`, killed its agent and removed the temporary tree and `$HOME/.m2/repository/de/morisoft`. The cleanup gate confirmed all three. `$HOME/.gnupg` and the workspace Maven settings were not touched or read.
- The four POMs equal 400357d, each at `0.2.0-SNAPSHOT`.

## Task Commits

1. Task 1 (tracer): `6298229` docs(08-02): make docs/release.md version-neutral for morimail and morimail-html
2. Task 2: gates only, no product change. Its commit is the SUMMARY commit.

## Gate Results

| Gate | Result |
|------|--------|
| Task 1 content gate | pass (exit 0) |
| Task 1 rehearsal gate | pass, printed `rehearsal: 54 entries, 9 good signatures, upload skipped` |
| Task 1 commit gate | pass (only docs/release.md, maintainer identity, trailer, not on origin/main) |
| Task 2 cleanup gate | pass (no local de/morisoft, no throwaway GNUPGHOME or agent, no v0.2 tag, POMs unchanged at 0.2.0-SNAPSHOT) |
| `./mvnw -B -ntp -o verify` | BUILD SUCCESS: morimail 366 tests, morimail-html 71, morimail-sample-serial 164, 0 failures, 0 errors, 0 skipped |
| `./mvnw -B -ntp -Prelease clean verify -Dgpg.skip -pl morimail,morimail-html -am` | exit 0, 0 WARN lines; `morimail-html-0.2.0-SNAPSHOT.jar`, `-sources.jar`, `-javadoc.jar` and `morimail-0.2.0-SNAPSHOT-javadoc.jar` exist |
| Phase scope gate | pass: since 400357d, outside `.planning/`, exactly README.md, docs/architecture.md, docs/release.md, docs/spec.md, SendCommand.java and SendCommandHelpTest.java changed |
| Phase hygiene gate | pass: no address outside example.org and example.com, no non-zero GUID, no hex string of 16 or more characters, no home path in added lines |

## Deviations from Plan

None - plan executed exactly as written.

Notes, not deviations: the cleanup gate's agent check and the first `ps` check matched their own command line as a false positive, so the check was repeated with a bracketed pattern. The only gpg-agent running uses the default gnupg home and is not the throwaway one. The Co-Authored-By trailer names the model of the executing run (`Claude Sonnet 5.5`), as the run's attribution reminder supplied it.

## Owner review

The 08-01 items:

1. `send --help` is not a declared option. It prints "Missing required options" and the usage on stderr with exit code 2, because the sample has no help option (D-09 forbids a new option).
2. Spec 8.1 and 8.3 got lines that D-05 to D-07 did not list (the `inline/` line and the dry-run paragraph).
3. The Coordinates block shows 0.2.0 before 0.2.0 is on Central (D-04). Release.md step 8 names the sentence under Coordinates to update after publishing. Superseded: the owner has since released 0.2.0 (tag v0.2.0), and README.md now says 0.2.0 is the current release on Maven Central.
4. Spec 10.3, the Sample bullet, changed per D-07 as amended on 2026-11-23: "Dry-run writes the images next to the rendered HTML" became "Dry-run copies each inline image once into `inline/` under `--out`".

New in this plan:

- (a) The rehearsal also removed the local leftovers of the first release's rehearsal under `$HOME/.m2/repository/de/morisoft`, per D-14's wording. Nothing in the build resolves `de.morisoft` from there, which the offline verify and the scoped release build after the cleanup prove.
- (b) The rehearsal used an isolated settings file, which release.md does not ask of the owner, because the owner's real token is meant to be used there.
- (c) The unescaped dots of `${VERSION}` in the sed and grep patterns are harmless for x.y.z versions, and the four-line diff check catches any surprise (research A2).

## Known Stubs

None.

## Threat Flags

None. The rehearsal added no network endpoint, auth path or schema change.

## Pending owner actions

1. Push the phase 8 commits and confirm the three CI steps (verify, scoped release build, offline build) are green on Temurin 17 for that commit. JDK 17 is not installed locally, so the zero-WARN result above was measured on JDK 21 with `--release 17`.
2. Enable GitHub private vulnerability reporting if still off (STATE blocker).
3. Release 0.2.0 by following `docs/release.md`.

Claude does none of these.

## Next Phase Readiness

Phase 8 plans are complete. The open items are the owner's: push, Temurin 17 CI, release.

## Self-Check: PASSED

- docs/release.md exists and 08-02-SUMMARY.md is written.
- Commit 6298229 is an ancestor of HEAD.
