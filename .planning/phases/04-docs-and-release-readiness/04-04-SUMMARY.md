---
phase: 04-docs-and-release-readiness
plan: 04
subsystem: release
tags: [release, central-publishing, gpg, rehearsal, docs]
status: complete

requires:
  - phase: 04-docs-and-release-readiness
    provides: 04-01 live findings applied, 04-02 docs/entra-setup.md, 04-03 README
provides:
  - a signed 0.1.0 rehearsal bundle (scratch copy, upload disabled) that holds only morimail-parent and morimail, so the roadmap research flag on excludeArtifacts is closed
  - docs/release.md, the owner's release checklist in D-10 order with placeholders only
affects: []

actuals:
  tokens: 1000
  tasks: 2
  commits: 1
plan_head_before: 5527197b53c2565416cf7746ca8e7bf6551763a8
plan_head_after: 813eebaa86bd47727ac3085e0098fd583d0e8ef0

tech-stack:
  added: []
  patterns:
    - "the rehearsal runs from one scratchpad script with an EXIT trap on git archive HEAD at 0.1.0, so the repository never carries 0.1.0 and no key or residue survives"

key-files:
  created:
    - docs/release.md
  modified: []

key-decisions:
  - "04-04: the rehearsal bundle at 0.1.0 holds only morimail-parent and morimail; excludeArtifacts is not needed"

requirements-completed: [DOCS-04]

coverage:
  - id: D1
    description: "A signed rehearsal bundle at 0.1.0 holds exactly 30 entries: the parent POM and the morimail POM, jar, sources jar and javadoc jar, each with .asc, .md5, .sha1, .sha256 and .sha512, and no -tests.jar and no sample"
    requirement: DOCS-04
    verification:
      - kind: command
        ref: "rehearse.sh report: BUILD=success, LISTING_MATCHES=yes (listing.diff empty, 30 lines), GOOD_SIGNATURES=5, CHECKSUMS_OK=yes, METADATA_OK=yes, REHEARSAL_DONE=yes"
        status: pass
    human_judgment: false
  - id: D2
    description: "Nothing was uploaded, published, tagged or pushed, and the throwaway key, its gpg-agent, the /tmp symlink, the scratch copy and the ~/.m2 de/morisoft artifacts are gone"
    requirement: DOCS-04
    verification:
      - kind: command
        ref: "plan Task 1 cleanup gate and untouched-repository gate: both exit 0 (no gh symlink, no scratch copy, no de/morisoft under ~/.m2, clean tree outside .planning, no tag, no central credential, no tracked .asc)"
        status: pass
    human_judgment: false
  - id: D3
    description: "docs/release.md lists the owner's steps under the nine required headings, with placeholders only, the maintainer identity for the GPG key, server id central, both deploy commands, autoPublish false, no Maven 4, and that the owner runs every step"
    requirement: DOCS-04
    verification:
      - kind: command
        ref: "plan Task 2 gates: headings and required strings, token/key id/address/home-path hygiene"
        status: pass
    human_judgment: false
  - id: D4
    description: "The three CI commands pass in the repository and the release build has no Javadoc warning"
    requirement: DOCS-04
    verification:
      - kind: command
        ref: "./mvnw -B -ntp verify; ./mvnw -B -ntp -Prelease clean verify -Dgpg.skip -pl morimail -am (no .java:N: warning/error line); ./mvnw -B -ntp -o verify: all BUILD SUCCESS"
        status: pass
    human_judgment: false
  - id: D5
    description: "The live walk-through of docs/entra-setup.md on the owner's tenant (dry run, --limit 1, full run to test addresses, second run sends nothing)"
    requirement: DOCS-04
    verification: []
    human_judgment: true
    rationale: "Only the owner sends real mail (roadmap success criterion 2). Handed over as a pending owner action."
---

# Phase 4 Plan 04: Release rehearsal and the owner's release steps Summary

A signed 0.1.0 release rehearsal, run in a scratch copy with the upload disabled, produced a 30-entry bundle with only `morimail-parent` and `morimail`, and `docs/release.md` now gives the owner the release as a checklist.

## Accomplishments

- **Rehearsal (Task 1).** `rehearse.sh` (scratchpad only, never committed) ran `./mvnw -B -ntp -Prelease clean deploy -DskipUpload=true -pl morimail -am` online in a scratch copy of `git archive HEAD` with the three POM versions set to 0.1.0, signed with a throwaway key (`Throwaway Rehearsal <rehearsal@example.com>`, 1 day). Result: BUILD SUCCESS, and `target/central-publishing/central-bundle.zip` was written.
  - **Listing:** exactly 30 entries, equal to the expected list (`listing.diff` is empty): the five base files `morimail-parent-0.1.0.pom`, `morimail-0.1.0.pom`, `.jar`, `-sources.jar` and `-javadoc.jar`, each with `.asc`, `.md5`, `.sha1`, `.sha256` and `.sha512`. No `-tests.jar`, no sample artifact.
  - **Signatures:** 5 of 5 `Good signature`.
  - **Checksums:** the `.sha1` and `.sha256` files match their files.
  - **POM metadata:** the bundled parent POM carries url, licenses, developers, scm, description and version 0.1.0. The morimail POM carries name, description and version 0.1.0.
  - **Tests ran** (314 in the library module, 0 failures); `REHEARSAL_SKIP_TESTS` was not needed. The run took 16 seconds.
- **Research flag closed.** `attach` false on the test-jar (release profile of `morimail/pom.xml`) together with `-pl morimail -am` is enough. `excludeArtifacts` is not needed, and it could not drop one classifier anyway, since it matches whole artifactIds. No POM, source or workflow file was changed.
- **Cleanup (D-12), checked.** The `/tmp/gh-*` symlink, the keyring and its gpg-agent, the scratch copy and the 0.1.0 artifacts under `~/.m2/repository/de/morisoft` are gone. `de/morisoft` did not exist before the run and does not exist now. The repository is clean outside `.planning`, has no tag, no tracked `.asc`, and `~/.m2/settings.xml` still does not exist, so no Central credential was ever available.
- **`docs/release.md` (Task 2, D-10).** Nine `##` sections in the required order: Before the first release, steps 1 to 8 (namespace, GPG key, Portal token, release version, deploy, review and publish, tag, next SNAPSHOT). It includes the skipUpload check with the expected 30-entry bundle and the rehearsal date, the real deploy command, `autoPublish` false, the Maven 4 warning, that a Central release is permanent, and the GitHub private vulnerability reporting item. Placeholders only: `TOKEN_USERNAME`, `TOKEN_PASSWORD`, `<KEYID>`.
- **CI commands, in CI order, all green:** `./mvnw -B -ntp verify`; `./mvnw -B -ntp -Prelease clean verify -Dgpg.skip -pl morimail -am` with no `.java:N: warning:` line; `./mvnw -B -ntp -o verify`.

## Task Commits

| Task | Commit | Files |
| ---- | ------ | ----- |
| 1. Release rehearsal | none (changes no repository file) | scratchpad only |
| 2. docs/release.md | 813eeba | docs/release.md |

## Deviations from Plan

None - plan executed exactly as written.

The deploy goal was used once, in the D-11 upload-disabled form (`-DskipUpload=true`), in a scratch copy. Nothing was uploaded, published, tagged or pushed.

## Flagged assumption (open for the owner)

The Central Portal validates the real deployment (POM metadata, signatures, sources and javadoc presence) only on upload, and no local command can run that validation. The rehearsal proves the bundle's content, signatures, checksums and POM fields. A Portal-side rejection would appear at the owner's first deploy and stop at "validated" before anything is published, because `autoPublish` is false.

## Known Stubs

None.

## Threat Flags

None. No source, POM or workflow file changed, and `docs/release.md` holds placeholders only.

## Pending owner actions

1. Push, and confirm the three CI steps on Temurin 17 on GitHub Actions, including the release build.
2. Follow `docs/entra-setup.md` on the tenant (path B recommended, path A verified live). In a campaign copy run the dry run, then `--limit 1` to your own address, then the full run to test addresses, then the same command again, which must print `accepted 0` and send nothing (roadmap success criterion 2). Report any step whose screen, cmdlet or output differs from the guide.
3. When ready, release by `docs/release.md`.
4. Enable GitHub private vulnerability reporting (Settings, Code security).

## Self-Check: PASSED

- `docs/release.md` exists, and commit 813eeba is an ancestor of HEAD.
- Task 1 gates: report gate, cleanup gate and untouched-repository gate all exit 0.
- Task 2 gates: release.md headings and strings, secrets and hygiene, the three CI commands and the commit gate (only `docs/release.md`, maintainer identity, trailer present, not on origin/main, no tag) all pass.
- Plan-level verification: no 04-04 commit touches a POM, workflow or source file, and `git tag -l` is empty.
