---
phase: 04-docs-and-release-readiness
verified: 2026-11-22T22:30:00Z
status: passed
score: 6/6 must-haves verified
covered_files:
  - .gitignore
  - .planning/phases/04-docs-and-release-readiness/04-01-PLAN.md
  - .planning/phases/04-docs-and-release-readiness/04-01-SUMMARY.md
  - .planning/phases/04-docs-and-release-readiness/04-02-PLAN.md
  - .planning/phases/04-docs-and-release-readiness/04-02-SUMMARY.md
  - .planning/phases/04-docs-and-release-readiness/04-03-PLAN.md
  - .planning/phases/04-docs-and-release-readiness/04-03-SUMMARY.md
  - .planning/phases/04-docs-and-release-readiness/04-04-PLAN.md
  - .planning/phases/04-docs-and-release-readiness/04-04-SUMMARY.md
  - .planning/phases/04-docs-and-release-readiness/04-05-PLAN.md
  - .planning/phases/04-docs-and-release-readiness/04-05-SUMMARY.md
  - README.md
  - docs/architecture.md
  - docs/entra-setup.md
  - docs/release.md
  - docs/spec.md
  - morimail/src/main/java/de/morisoft/morimail/entra/EntraClientCredentials.java
  - morimail/src/main/java/de/morisoft/morimail/graph/GraphTransport.java
  - morimail/src/test/java/de/morisoft/morimail/entra/EntraTokenErrorsTest.java
  - morimail/src/test/java/de/morisoft/morimail/graph/GraphErrorMappingTest.java
covered_digest: "v4:sha256:e7927cb53860d9a91509fceb88e1ab35cce7f77a4f754e0bca6609e7bea084e5"
behavior_unverified: 0
overrides_applied: 0
re_verification:
  previous_status: human_needed
  previous_score: 5/6
  gaps_closed:
    - "G-04-3: docs/release.md step 5 warm-up, no prompt promise, GPG_TTY scoped, exit code 2 explained. The owner's real signed deploy then succeeded and the Central Portal validated the deployment (UAT test 5 pass)"
    - "Previous human item 2 (WR-01, step 7 tags HEAD): fixed in 794661d, and v0.1.0 on origin resolves to 72e7f7f (UAT test 6 pass)"
    - "Review IN-01, IN-02, IN-03 of the gpg wording: fixed in 49cfe7b, 7b97943, f9096a9"
    - "Review WR-01 of the fresh review (maintainer GPG key id in .planning/debug/release-doc-gpg-signing.md): fixed by a rewrite of unpushed history"
  gaps_remaining: []
  regressions: []
---

# Phase 4: Docs and Release Readiness Verification Report

**Phase Goal:** As the owner, I want the setup guide, final docs and a release rehearsal shaped by the live test, so that I can set up my tenant and publish 0.1.0.
**Verified:** 2026-11-22T22:30:00Z
**Status:** passed
**Re-verification:** Yes. The previous report (human_needed) is stale: `docs/release.md` changed after it, through four review fixes. The owner has since run the real release. This pass re-checks the changed file in full and runs regression checks on the rest.

## Goal Achievement

The goal is achieved. All four documents exist and match the code. The one thing a presence check could not prove, that `docs/release.md` carries an owner through a signed deploy and Portal validation, is now backed by the owner's real run (04-UAT.md tests 3 and 5). The commit the owner released is the one CI tested: origin/main, `72e7f7f` and the tag all agree.

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Live-test findings applied: error mapping, attachment boundary and default rate in code, spec §6.3 and architecture.md match the real responses, and every error-table row is still tested | VERIFIED | `git diff --name-only 72e7f7f HEAD` outside `.planning/` lists only `docs/release.md`, so code, spec and architecture are the files verified before. `docs/spec.md:224-236` carries the live-status column dated 2026-11-22. `GraphTransport.java:282-285` still maps 403/404 and 429/5xx. `GraphErrorMappingTest` (21 tests) and `EntraTokenErrorsTest` (19 tests) exist, with the word-boundary cut tested (`EntraTokenErrorsTest:211-228`). `./mvnw -B -ntp -o verify` passed three consecutive times at HEAD (orchestrator-reported). |
| 2 | The owner can follow `docs/entra-setup.md` from registration through to the second run that sends nothing | VERIFIED (human evidence) | `04-UAT.md` test 1: `result: pass`. The guide is 240 lines with sections 1 to 9, ending at the second run printing `accepted 0, skipped <rows>, failed 0` (lines 196-204). |
| 3 | `docs/architecture.md` summarises the contracts on one page and records every deviation | VERIFIED | 62 lines. It has a Contracts section, a deviations table that names `freshAccessToken`, `baseUrl`, unchecked registry exceptions, JUnit 6, the `changeit` keystore and the reactor-only test-jar, and a Microsoft-doc differences section (read 2026-11-22). Unchanged since 72e7f7f. |
| 4 | `README.md` states what morimail is and is not, status, coordinates, quick start, delivery semantics, keystore sentence | VERIFIED | 106 lines. Sections Status, Coordinates, Quick start, "What morimail is not" (30 per minute and 10,000 per day, worded as not confirmed for Graph; not a bulk mailer), Delivery semantics (accepted not delivered, at-least-once), Tests (keystore sentence). Unchanged since 72e7f7f. |
| 5 | Release build is warning-free; the rehearsal bundle holds only `morimail-parent` and `morimail`; the owner's steps are listed | VERIFIED | `target/central-publishing/central-bundle.zip` is present and `unzip -Z1` lists 30 entries. The three POMs are at 0.1.0. `docs/release.md` has nine `##` headings (Before the first release, steps 1 to 8). Both deploy commands keep `-B`. The owner's real deploy used this bundle shape and the Portal validated it (UAT test 5). |
| 6 | **G-04-3.** `docs/release.md` lets the owner run the release, including the signed deploy, without an undocumented failure; the Portal validates the deployment | VERIFIED (human evidence, replaces the earlier `backstop` UNCERTAIN) | `04-UAT.md` test 5: `result: pass`; test 3's gap is `resolved_by: 04-05-PLAN.md`. Owner-reported: signed deploy succeeded, the Central Portal validated the deployment. Independent checks below confirm the written fix. |

**Score:** 6/6 truths verified. 0 present-but-behavior-unverified. 0 coincidental-reliance.

### Re-check of `docs/release.md` (the file that changed)

I read all 139 lines.

| Check | Status | Evidence |
|-------|--------|----------|
| Warm-up before each deploy, in a real terminal, with the cache lifetime | VERIFIED | Lines 68-77 and 102-107: `export GPG_TTY=$(tty)` and `echo test \| gpg --clearsign > /dev/null`, "10 minutes after its last use". Order in the file is warm-up, skipUpload check, warm-up, real deploy. |
| No false prompt promise; cause named correctly (IN-01) | VERIFIED | Line 68 names `--pinentry-mode error` as the cause; line 115 says the deploy "cannot prompt, because of `-B`". |
| `GPG_TTY` re-scoped to the warm-up | VERIFIED | Line 33. |
| Exit code 2 explained, several-keys case (IN-02), success signal (IN-03) | VERIFIED | Line 117 locates the `gpg:` line under `--- gpg:3.2.8:sign` and above the Reactor Summary and says to name the same `<KEYID>` in warm-up and deploy. Line 75 says the warm-up prints nothing on success. |
| Step 7 tags the release commit, not HEAD (WR-01) | VERIFIED | Lines 125-133 resolve the commit by `git log -1 --format=%H --grep='^release 0\.1\.0$'`. In this repo `git rev-parse 'v0.1.0^{commit}'` gives `72e7f7f`, and `git ls-remote --tags origin` shows `refs/tags/v0.1.0^{}` at the same hash. An empty grep fails loudly (checked by the reviewer in 04-REVIEW.md). |
| No key id, fingerprint, token or address other than maintainer and example ones | VERIFIED | `grep` for 16 or more hex characters across `docs/`, `README.md` and `.planning/debug` finds nothing. The only 40-hex strings are commit hashes in SUMMARY frontmatter (`plan_head_*`). Keys appear as `<KEYID>` only. |
| Plan 04-05 prohibitions: no build-input change | VERIFIED | Outside `.planning/`, only `docs/release.md` differs from the release commit. POM versions are 0.1.0. |
| Nine headings, `-B` on both deploy commands, step 4 untouched | VERIFIED | Counted in the file. |

### Fresh review (04-REVIEW.md, WR-01) and public-repo hygiene

The review found `docs/release.md` clean and raised one warning: the maintainer's GPG key id in `.planning/debug/release-doc-gpg-signing.md`. Verified fixed. The file now shows `<KEYID>` at lines 23, 38 and 50, and no 16-hex string remains in the phase directory, `docs/` or `.planning/debug`. All 17 commits after `origin/main` are authored and committed by `Stephan Wald <stephan@morisoft.de>`. `04-REVIEW-DISPOSITION.md` shows all four findings `fixed`, `open: 0`.

One thing worth knowing: the local ref `refs/backup/pre-keyid-rewrite` (719e354) still holds the pre-rewrite history, with the key id. A plain `git push` or `git push origin main` does not send it. `git push --all`, `--mirror` or `--tags` pattern pushes of `refs/*` could. The key id is public key metadata (it is on the keyserver and in the Central signatures), so the exposure would be a hygiene lapse, not a leak of a secret. Delete the ref once the owner no longer needs the backup (`git update-ref -d refs/backup/pre-keyid-rewrite`, then `git gc`).

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `docs/release.md` | Owner's release steps with gpg-agent warm-up | VERIFIED | 139 lines, nine headings, warm-up block twice, step 7 resolves the release commit. |
| `docs/entra-setup.md` | Setup guide | VERIFIED | 240 lines, sections 1 to 9, owner walked it. |
| `docs/architecture.md` | One-page contracts and deviations | VERIFIED | 62 lines. |
| `README.md` | What it is and is not | VERIFIED | 106 lines. |
| `docs/spec.md` | §6.3 live column, §8.3 limits | VERIFIED | 500 lines, live column at 224-236. |
| `.gitignore` | Key and certificate files ignored | VERIFIED | 42 non-blank lines. The only tracked key material is `morimail/src/test/resources/test-keystore-throwaway.p12`. |
| `target/central-publishing/central-bundle.zip` | 30-entry bundle | VERIFIED | 30 entries. |

### Key Link Verification

| From | To | Status | Details |
|------|----|--------|---------|
| `docs/release.md` step 5 | `pom.xml` release profile | WIRED | One `sign-artifacts` execution with no configuration, as the doc says (checked in the previous pass, unchanged: no `pom.xml` diff since 72e7f7f). The owner's real deploy confirmed the behaviour. |
| `docs/release.md` step 7 | git history | WIRED | The grep resolves to `72e7f7f`, the tag points at it locally and on origin. |
| `README.md` | `docs/entra-setup.md` | WIRED | Link present on lines 5 and 99. |

### Data-Flow Trace (Level 4)

Not applicable. The phase delivers documents. The only code touched (word-boundary cut in the two `clean()` methods) is exercised by the straddle tests.

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Full offline build green | `./mvnw -B -ntp -o verify`, three consecutive runs at HEAD (orchestrator-reported, not re-run here) | BUILD SUCCESS | PASS (reported) |
| Bundle shape | `unzip -Z1 target/central-publishing/central-bundle.zip \| wc -l` | 30 | PASS |
| Release tag is the CI-tested commit | `git rev-parse 'v0.1.0^{commit}'` and `git ls-remote --tags origin` | `72e7f7f` on both | PASS |
| No build input differs from the release commit | `git diff --name-only 72e7f7f HEAD` outside `.planning/` | only `docs/release.md` | PASS |

I did not run a deploy, gpg, tag or push, and sent no mail.

### Probe Execution

Step 7c: SKIPPED. The phase declares no `probe-*.sh` and none exist under `scripts/`.

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| DOCS-01 | 04-02 | `docs/entra-setup.md` from app registration to verification | SATISFIED | Truth 2. UAT test 1 pass. |
| DOCS-02 | 04-01, 04-02 | `docs/architecture.md` one-page contracts and every deviation | SATISFIED | Truth 3. |
| DOCS-03 | 04-03 | README with limits, delivery semantics and keystore sentence | SATISFIED | Truth 4. |
| DOCS-04 | 04-04, 04-05 | Release build, rehearsal bundle, owner's release steps | SATISFIED | Truths 5 and 6. The owner's real signed deploy and the Portal's validation back it. |

PLAN frontmatter IDs: 04-01 DOCS-02; 04-02 DOCS-01, DOCS-02; 04-03 DOCS-03; 04-04 DOCS-04; 04-05 DOCS-04. All four appear in REQUIREMENTS.md (lines 118 to 126, traceability 236 to 239, all `Complete`) and map to Phase 4. REQUIREMENTS.md maps no other ID to Phase 4. No orphaned requirements.

### Prohibitions (must_haves.prohibitions of the five plans)

All judgment-tier, all checked and held: no change to the Graph mapping or the 3 MiB, 4 MiB and `--rate` constants (no code diff since 72e7f7f other than earlier plan work already verified); no real address, tenant id or key id in tracked files (grep clean); no secret as a value in README or the guide; no build-structure change to make the bundle pass; no second route for the gpg passphrase (the only `gpg.passphrase` mention is "never set"); the agent ran no gpg, deploy, push or tag. None is left flagged.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| docs/, README.md | - | `TBD`, `FIXME`, `XXX` | none found | Debt-marker gate is clear. |
| `README.md` | 11, 22 | "not yet released to Maven Central", "not published yet" | Info | Intentional until release.md step 8 ("Update the README sentences that say 0.1.0 is not released"). It is a post-publish task, not a phase gap. Do it after pressing Publish in the Portal. |
| local ref `refs/backup/pre-keyid-rewrite` | - | Holds the pre-rewrite history with the key id | Info | Local only, not sent by a normal push. See the hygiene section. |

### Human Verification Required

None. The two items from the previous pass were completed by the owner (UAT tests 5 and 6) and are taken as evidence as instructed.

### Gaps Summary

There are no gaps. The documents are complete and consistent with the code, the written gpg fix matches the diagnosis and held in the owner's real deploy, the tag points at the CI-tested commit, and the public-repo hygiene finding is closed. Not checked here, because it is outside the phase goal: that the owner pressed Publish in the Portal and the README sentences were updated (step 8).

---

_Verified: 2026-11-22T22:30:00Z_
_Verifier: Claude (gsd-verifier)_
