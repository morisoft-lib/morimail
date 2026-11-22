---
phase: 04-docs-and-release-readiness
verified: 2026-11-22T21:45:00Z
status: human_needed
score: 5/6 must-haves verified
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
covered_digest: "v4:sha256:7a0925dd682f34a41a550149042fc9bdcc306f38d6eb361892c131874141fe73"
behavior_unverified: 0
overrides_applied: 0
re_verification:
  previous_status: human_needed
  previous_score: 4/5
  gaps_closed:
    - "G-04-3 (written fix): docs/release.md step 5 now warms up gpg-agent before each -B deploy, no longer promises a prompt, and explains exit code 2"
    - "Previous human item 1 (owner walk-through of docs/entra-setup.md): UAT test 1 pass"
    - "Previous human item 2 (CI on Temurin 17): UAT test 2 pass, run 45063447212"
    - "Previous human item 4 (review warnings WR-01..WR-03 of the first review): fixed in commits 29555d4, f99d4cc and the release.md reorder; UAT test 4 pass"
  gaps_remaining: []
  regressions: []
human_verification:
  - test: "In a real terminal in this repository, continue docs/release.md at step 5: warm-up, skipUpload check (30 entries), warm-up again, real `./mvnw -B -ntp -Prelease clean deploy -pl morimail -am`, then step 6 in the Portal"
    expected: "The deploy signs from the agent cache without an undocumented failure, and the Central Portal validates the deployment (POM metadata, signatures, sources, javadoc) and lists the 30-entry bundle. Report any step where the doc and the real output differ."
    why_human: "UAT test 3 (G-04-3). Publishing, the Portal's validation and the owner's gpg-agent are the owner's. The agent must not run gpg, deploy, push or tag. The Portal validates only on upload. Also open: the Portal's acceptance of the owner's EdDSA key is not documented (04-05-SUMMARY flagged assumption); a rejection happens at validation, before anything is public."
  - test: "Before step 7, decide on review finding WR-01 of 04-REVIEW.md (step 7 tags HEAD, but HEAD is six commits past the release commit 72e7f7f). Either tag `72e7f7f` explicitly, or accept the one-line doc fix first"
    expected: "`git show --no-patch v0.1.0^{commit}` shows the `release 0.1.0` commit that CI tested, not a later docs commit."
    why_human: "It is a wording and ordering decision in an owner-facing document, and it only matters at step 7, after publishing. See the weighing below."
---

# Phase 4: Docs and Release Readiness Verification Report

**Phase Goal:** As the owner, I want the setup guide, final docs and a release rehearsal shaped by the live test, so that I can set up my tenant and publish 0.1.0.
**Verified:** 2026-11-22T21:45:00Z
**Status:** human_needed
**Re-verification:** Yes. An earlier 04-VERIFICATION.md (human_needed, no `gaps:`) exists. This pass regenerates it and adds gap-closure plan 04-05 for UAT gap G-04-3.

## Goal Achievement

All four documents the phase promised exist and are consistent with the code and with the owner's live record. Since the first verification the owner has walked the setup guide on the real tenant (UAT test 1 pass) and CI is green on Temurin 17 (UAT test 2 pass). UAT test 3, the real signed deploy, failed on an undocumented gpg-agent condition. G-04-3 was diagnosed and 04-05 corrected `docs/release.md`. I found no FAILED truth and no blocker. What is left is the owner's re-run of step 5 and the Portal's validation, which only the real deploy can prove.

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Live-test findings applied: error mapping, attachment boundary and default rate in code, spec §6.3 and architecture.md match the real responses, every error-table row still tested | VERIFIED | Unchanged since the first verification. `git diff 72e7f7f HEAD` shows no file outside `.planning/` other than `docs/release.md`, so code, spec and architecture are exactly what was verified before. `./mvnw -B -ntp -o verify` was run green by the orchestrator (137+ tests, all modules). `docs/architecture.md` is 62 lines and has no planning ids left (`grep -c 'WR-0'` gives 0). |
| 2 | The owner can follow `docs/entra-setup.md` from registration through to the second run that sends nothing | VERIFIED (human evidence) | `04-UAT.md` test 1: `result: pass`. The owner ran the guide on the real tenant. The earlier WR-02 defect (delete `app.key` before the fallback needs it) was fixed in f99d4cc. |
| 3 | `docs/architecture.md` summarises the contracts on one page and records every deviation | VERIFIED | Regression check: file present (62 lines), unchanged since 72e7f7f. IN-01 cleanup of planning ids is in e6707b5. |
| 4 | `README.md` states what morimail is and is not, status, coordinates, quick start, delivery semantics, keystore sentence | VERIFIED | Regression check: file present (106 lines), unchanged since 72e7f7f. The earlier WR-01 overclaim on the 30 per minute limit was reworded in 29555d4 ("not confirmed for Graph"). |
| 5 | Release build is warning-free; the rehearsal bundle holds only `morimail-parent` and `morimail`; the owner's steps are listed | VERIFIED | `docs/release.md` has the nine `##` headings (Before the first release, steps 1 to 8), and both deploy commands keep `-B`. The owner's own `-DskipUpload=true` run left `target/central-publishing/central-bundle.zip`. I listed it with `unzip -Z1`: exactly 30 entries, five base files each with `.asc .md5 .sha1 .sha256 .sha512`, no `-tests.jar`, no sample artifact. This is real, independent evidence for the bundle shape, and it shows signing works once the agent is warm. The three POMs are at 0.1.0. |
| 6 | **G-04-3.** `docs/release.md` lets the owner run the release, including the signed deploy, without hitting an undocumented failure; the Portal validates the deployment and the bundle matches the 30-entry listing | UNCERTAIN (written fix verified, real deploy pending; `backstop`) | Written fix verified below. The part that needs the owner's own passphrase, agent and the Portal cannot be proven by a presence check. Routed to human verification. Not a gap. |

**Score:** 5/6 truths verified. 0 truths are present-but-behavior-unverified. Truth 6 is the one human item.

### G-04-3 Gap Closure Check (plan 04-05)

I checked each `missing` item of G-04-3 against `docs/release.md` as it exists now, and against the diagnosis in `.planning/debug/release-doc-gpg-signing.md`.

| G-04-3 missing item | Status | Evidence in `docs/release.md` |
|---|---|---|
| 1. Warm-up before each deploy, real terminal, 10-minute cache | VERIFIED | Lines 68 to 75: "real terminal, not as a `!` command in Claude Code, where `tty` prints `not a tty`"; the two-line block `export GPG_TTY=$(tty)` and `echo test \| gpg --clearsign > /dev/null`; "keeps the passphrase for 10 minutes after its last use". The same block repeats at lines 100 to 105, directly before the real deploy. Order in the file: warm-up, skipUpload check, warm-up, real deploy. |
| 2. Replace the prompt promise | VERIFIED | Line 113: "The deploy signs from the agent cache and cannot prompt, because of `-B`." `grep` finds no remaining "asks for the passphrase" promise. |
| 3. Re-scope step 2 item 3 | VERIFIED | Line 33: "`GPG_TTY` is for that warm-up; it does not make the deploy prompt. Never set `gpg.passphrase`". |
| 4. Read the failure | VERIFIED | Line 115: `maven-gpg-plugin:3.2.8:sign` with `Exit code: 2` gives no reason; the unprefixed `gpg:` line is under `--- gpg:3.2.8:sign` and above the Reactor Summary; `No pinentry` means an empty cache. Matches the diagnosis. |
| 5. Several secret keys | VERIFIED | Line 75: `--local-user <KEYID>` on the warm-up and `-Dgpg.keyname=<KEYID>` on both deploy commands. Placeholders only. |

Plan 04-05 prohibitions, checked:

- **No POM, source, workflow or version change.** `git diff --name-only 72e7f7f HEAD` outside `.planning/` lists only `docs/release.md`. The POM versions are 0.1.0, and `pom.xml` still has the single `sign-artifacts` execution with no configuration, as the doc says. Verified.
- **No new heading, `-B` kept, step 4 untouched.** Headings are the same nine. Both deploy lines contain `-B`. Verified.
- **No key id, fingerprint, passphrase, token or foreign address.** A grep for 16+ hex characters and for addresses in `docs/release.md` finds only the maintainer identity (line 20, allowed by section 13.4). Keys appear only as `<KEYID>`. The diagnosis file names a key id, but it lives in `.planning/debug/` (a public repo, see Anti-Patterns). Verified for `docs/release.md`.
- **No other route for handing the passphrase to the plugin.** `grep` for `MAVEN_GPG_PASSPHRASE`, `loopback` and `pinentry-mode` in the owner-facing docs finds nothing. The only `gpg.passphrase` mention is the "never set" warning. Verified.
- **Agent ran no gpg, deploy, push or tag.** `git tag` is empty, `origin/main` is `72e7f7f`, and `main` is six commits ahead (local only). Verified.

### Review weighing: 04-REVIEW.md (1 warning, 3 info, advisory)

**WR-01 (step 7 tags HEAD, not the release commit): a warning, not a blocker.**

The facts check out. `docs/release.md` lines 125 to 128 run `git tag -a v0.1.0 ...` on `HEAD`, and `HEAD` is `b75c8ac`, six commits after the CI-green release commit `72e7f7f`. A literal reading of step 7 would put `v0.1.0` on a docs commit that CI did not test.

It does not block the phase goal, for four reasons:

1. The goal is "set up my tenant and publish 0.1.0". Publishing happens in steps 5 and 6, from the working tree. Step 7 runs after the permanent publish and cannot change what Central receives.
2. The build inputs at `HEAD` equal the release commit. Only `docs/release.md` and `.planning/` differ, so the artifacts are the same bytes of source.
3. The tag is local until the owner pushes it, and nothing has been tagged. The owner can still choose the right commit.
4. The failure is specific to the current repository state (docs commits after step 4). It recurs only if a commit lands between steps 4 and 7.

It is still a real defect in the owner's runbook, and a public tag is hard to move. The fix is one line (`git tag -a v0.1.0 -m "morimail 0.1.0" 72e7f7f`, or the `--grep` form from the review). I recommend applying it before the owner reaches step 7. It is recorded as a human item, so it cannot be silently absorbed into a clean pass.

**IN-01 to IN-03 (gpg wording):** wording gaps in the new text. They do not make a must-have false. IN-02 (several keys, same `<KEYID>` in warm-up and deploy) and IN-03 (the warm-up prints nothing on success) are the two most useful for an owner debugging a failed deploy. The disposition file keeps all four at `open`.

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `docs/release.md` | Owner's release steps with gpg-agent warm-up | VERIFIED | 134 lines, nine headings, contains `echo test \| gpg --clearsign > /dev/null` (twice). |
| `docs/entra-setup.md` | Setup guide | VERIFIED | 240 lines. Owner walked it (UAT test 1). |
| `docs/architecture.md` | One-page contracts and deviations | VERIFIED | 62 lines. |
| `README.md` | What it is and is not | VERIFIED | 106 lines. |
| `docs/spec.md` | §6.3 live column, §8.3 limits | VERIFIED | 500 lines, unchanged since the first verification. |
| `.gitignore` | Key and certificate files ignored | VERIFIED | 49 lines (IN-04 widened the patterns in 6792cdf). The only tracked key file is `morimail/src/test/resources/test-keystore-throwaway.p12`. No `.asc` is tracked. |

### Key Link Verification

| From | To | Status | Details |
|------|----|--------|---------|
| `docs/release.md` step 5 | `pom.xml` release profile | WIRED | The doc says one `sign-artifacts` execution of `maven-gpg-plugin` with no configuration. `pom.xml` lines 215 to 221 match: id `sign-artifacts`, phase `verify`, goal `sign`, no `<configuration>`. Under `-B` the plugin uses the agent cache only, as the diagnosis proved with bytecode and a real release-profile repro. |
| `docs/release.md` step 5 | owner's gpg-agent | NOT PROVABLE LOCALLY | Needs the owner's key and agent. Human item 1. |
| `README.md` | `docs/entra-setup.md` | WIRED | Unchanged. |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Full offline build is green | `./mvnw -B -ntp -o verify` (run by the orchestrator, not re-run) | BUILD SUCCESS, 137+ tests, all modules | PASS (reported) |
| Rehearsal bundle shape | `unzip -Z1 target/central-publishing/central-bundle.zip` | 30 entries, five base files with `.asc` and four checksums each, no tests jar, no sample | PASS |
| No stray build-input change since the release commit | `git diff --name-only 72e7f7f HEAD` outside `.planning/` | only `docs/release.md` | PASS |

I did not re-run the build myself. The orchestrator's green run covers the same tree, and the only non-planning change since the last verified build is a docs file.

### Probe Execution

Step 7c: SKIPPED. The phase declares no `probe-*.sh` scripts, and none exist under `scripts/`.

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| DOCS-01 | 04-02 | `docs/entra-setup.md` from app registration to verification | SATISFIED | Owner walked the guide (UAT test 1 pass). |
| DOCS-02 | 04-01, 04-02 | `docs/architecture.md` one-page contracts and every deviation | SATISFIED | Truth 3. |
| DOCS-03 | 04-03 | README with limits, delivery semantics and keystore sentence | SATISFIED | Truth 4. |
| DOCS-04 | 04-04, 04-05 | Release build, rehearsal bundle, owner's release steps | SATISFIED in writing, real deploy pending | Truth 5 and the G-04-3 check. The signed bundle exists (30 entries). The Portal validation is human item 1. |

Every ID in the PLAN frontmatter (DOCS-01 to DOCS-04, across plans 04-01 to 04-05) appears in REQUIREMENTS.md and is mapped to Phase 4. REQUIREMENTS.md maps no other ID to Phase 4, so there are no orphaned requirements.

### Anti-Patterns Found

| File | Pattern | Severity | Impact |
|------|---------|----------|--------|
| Phase files, `docs/` | `TBD`, `FIXME`, `XXX` markers | none found | The debt-marker gate is clear. |
| `.planning/debug/release-doc-gpg-signing.md` | Names the owner's gpg key id (a public identifier, from the owner's own keyring) | Info | A key id is public by design (it is sent to a keyserver), but the project rule is "no real address, tenant id ... into any file". Not a secret and not in the owner-facing docs. Worth a glance before the owner pushes `.planning/`. |
| `docs/release.md:68`, `:115` | IN-01 and IN-02 wording | Info | See the review weighing. |
| `docs/release.md:125-128` | WR-01 | Warning | See the review weighing. |

### Human Verification Required

#### 1. Re-run the signed deploy (UAT test 3, G-04-3)

**Test:** In a real terminal in this repository, continue `docs/release.md` at step 5: warm-up, skipUpload check (30 entries), warm-up again, real deploy, then step 6 in the Portal. Step 4 does not need to be redone, because the fix commit changes only `docs/release.md`.
**Expected:** The deploy signs without an undocumented failure, and the Portal validates the deployment and shows the 30-entry bundle. Report any step where the doc and the real output differ.
**Why human:** Needs the owner's passphrase, agent and Portal account. The Portal validates only on upload. `autoPublish` is false, so a rejection (for example of the EdDSA key, the flagged assumption in 04-05-SUMMARY) stops before anything is public. Step 6 item 3 covers it.

#### 2. Settle review WR-01 before step 7

**Test:** Tag the release commit explicitly (`72e7f7f`) or fix step 7 first.
**Expected:** `git show --no-patch v0.1.0^{commit}` shows the `release 0.1.0` commit.
**Why human:** A runbook wording decision that matters only after publishing.

### Gaps Summary

There are no gaps. The goal is achieved to the extent the agent can prove it: the documents are complete and consistent, the owner has walked the setup guide and CI is green on Java 17, the G-04-3 fix is in `docs/release.md` and matches the diagnosis, and no build input changed since the CI-green release commit. The remaining work is the owner's: re-run step 5 with a warm agent and let the Portal validate the deployment (G-04-3 as a `backstop` truth), and decide WR-01 before tagging. Nothing here blocks moving on to the real release.

---

_Verified: 2026-11-22T21:45:00Z_
_Verifier: Claude (gsd-verifier)_
