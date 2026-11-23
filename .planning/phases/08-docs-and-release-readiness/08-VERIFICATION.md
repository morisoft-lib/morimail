---
phase: 08-docs-and-release-readiness
verified: 2026-11-23T22:30:00Z
status: human_needed
score: 5/5 roadmap success criteria verified
covered_files:
  - ".planning/phases/08-docs-and-release-readiness/08-01-PLAN.md"
  - ".planning/phases/08-docs-and-release-readiness/08-01-SUMMARY.md"
  - ".planning/phases/08-docs-and-release-readiness/08-02-PLAN.md"
  - ".planning/phases/08-docs-and-release-readiness/08-02-SUMMARY.md"
  - ".planning/phases/08-docs-and-release-readiness/08-03-PLAN.md"
  - ".planning/phases/08-docs-and-release-readiness/08-03-SUMMARY.md"
  - "README.md"
  - "docs/architecture.md"
  - "docs/release.md"
  - "docs/spec.md"
  - "morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/SendCommand.java"
  - "morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/SendCommandHelpTest.java"
covered_digest: "v4:sha256:7ac55f5ff71c368e4038e6759866b2c275f063c919787fa687ef9f67dfa209b2"
behavior_unverified: 0
overrides_applied: 0
re_verification:
  previous_status: human_needed
  previous_score: 5/5
  gaps_closed:
    - "G-08-2 (UAT test 2): release.md step 5 deployed at a SNAPSHOT version and uploaded to Central's snapshots repository (401). Closed by 08-03 (2ad672c): both deploy commands are chained behind a SNAPSHOT guard and the SNAPSHOT bullet states the plugin behaviour."
  gaps_remaining: []
  regressions: []
gaps: []
advisory:
  - finding: "WR-01 (08-REVIEW.md): the `! grep -- -SNAPSHOT ... &&` guard in docs/release.md fails open when grep exits 2 (POM missing, wrong working directory)"
    category: other
    reason: "Reproduced here: from `/` the guard lets the chained command start. In the normal case (right directory, POMs present) it stops on any -SNAPSHOT and starts at a release version (also reproduced). `./mvnw` is relative too, so a wrong directory also cannot find the wrapper. The guard works for the case UAT G-08-2 hit; it is not hardened against a missing POM. Suggested fix is in 08-REVIEW.md (`grep ...; [ $? -eq 1 ] &&`)."
    evidence_status: "reproduced, not goal-blocking"
  - finding: "WR-02 (08-REVIEW.md): docs/release.md still defaults to the released 0.2.0 (step 4 VERSION=0.2.0 and NEXT=0.3.0-SNAPSHOT; line 12 'If you already released 0.1.0'; line 169 'for the 0.2.0 release they already do')"
    category: other
    reason: "Confirmed in the file (lines 12, 63-64, 169). 0.2.0 is released, so a literal paste for the next release matches nothing in sed; the step 4.3 four-line diff check and the new guard stop the harmful outcomes. Doc staleness after a release, not a failure of the phase goal. Fix: set VERSION=0.3.0 / NEXT=0.4.0-SNAPSHOT or a placeholder and reword the two sentences."
    evidence_status: "confirmed in file, not goal-blocking"
unverified_prohibitions:
  - statement: "MUST NOT upload, publish, deploy to Maven Central, push or tag (08-02 and 08-03 prohibitions, judgment tier)"
    verdict: "NON-AUTHORITATIVE LLM-judge: the only deploy evidence is the owner's release (2c4ea80 'release 0.2.0', tag v0.2.0 on origin, 8df5f6a bump), all authored by the maintainer without a Claude trailer; the phase commits themselves contain no deploy, push or tag artefacts. The owner cannot be told apart from Claude by git alone."
    flag: "unverified-prohibition - human review recommended"
  - statement: "MUST NOT commit a release version to any POM, and MUST NOT leave the throwaway key, gpg-agent or signed artifacts on the machine (08-02, test tier)"
    verdict: "No wired enforcement test exists, so treated as unverified. Direct observation: no phase commit other than the owner's 2c4ea80 sets a release version; ~/.gnupg-rh08 does not exist. ~/.m2/repository/de/morisoft exists again, which is the expected result of the owner's release.md step 5 check (the doc says so)."
    flag: "unverified-prohibition - human review recommended"
  - statement: "MUST NOT write the throwaway key id or fingerprint, or Maven settings values, into a file, SUMMARY or commit (08-02, test tier)"
    verdict: "No wired enforcement test exists. Direct observation: no 16+ character hex string, no address other than example.com/example.org and the maintainer identity, and no /home path in any non-.planning line added since 400357d."
    flag: "unverified-prohibition - human review recommended"
human_verification:
  - test: "Confirm that no phase 8 Claude run deployed, pushed or tagged, and that the only deploy was your own 0.2.0 release (the three flagged prohibitions above)"
    expected: "Yes. Everything Claude ran in phase 8 was a git-archive copy or a skipUpload rehearsal; the 0.2.0 deploy, push and tag v0.2.0 were yours."
    why_human: "Judgment-tier and test-tier prohibitions with no wired enforcement; git history cannot distinguish the owner's commands from Claude's. Everything else in this report is directly evidenced."
---

# Phase 8: Docs and Release Readiness Verification Report

**Phase Goal:** A developer reading the README and docs can use inline images, and the build is ready for the owner to release 0.2.0.
**Verified:** 2026-11-23
**Status:** human_needed
**Re-verification:** Yes, after UAT gap G-08-2 and gap plan 08-03

Every roadmap success criterion holds and nothing fails. The one remaining item is the owner's confirmation of the flagged prohibitions. Both open code-review warnings are recorded as advisory, not as gaps.

## Goal Achievement

### Observable Truths (ROADMAP success criteria)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | README shows a copyable inline-image example using `.inline(...)` and `<img src="cid:...">` | VERIFIED | README "## Inline images" (line 74): class `InlineImage` with `<img src=\"cid:logo\" ...>` (line 87) and `.inline(Path.of("logo.png"), "logo")` (line 93); text names the build() checks (line 101). I extracted all three README java blocks (QuickStart, InlineImage, EmbedImages) and compiled them with `javac --release 17 -Xlint:all` against `morimail/target/classes`, `morimail-html/target/classes` and Jackson 2.23.0: exit 0, no output. |
| 2 | Spec section 6 and `docs/architecture.md` name the Graph inline property names and the live status as reported | VERIFIED | spec 6.2 lines 223-226: `"isInline": true`, bare `"contentId"`, no `contentLocation`, no `isInline` on regular attachments, same limits, "Confirmed live on 2026-11-23 ... Outlook on the web and one desktop client". architecture.md line 52 carries the same sentence. Spec 5/5.1/10.3 (lines 124, 177, 393-400) describe the implemented checks. |
| 3 | The sample docs describe `inline/`, the content-ID rule and the dry-run rewrite | VERIFIED | README "## Serial-mail sample" (lines 155-180): layout, content ID = file name without last extension, pre-flight exit 2, `out/inline/` dry-run paragraph. spec 8.1 (line 289) and 8.3 (line 333) match. `SendCommandHelpTest` re-run offline: exit 0 (`./mvnw -o test -pl morimail-sample-serial -am -Dtest=SendCommandHelpTest`). |
| 4 | README shows a copyable `HtmlImages` example (local, remote opt-in) and the `morimail-html` coordinates | VERIFIED | README "## morimail-html" (line 105): dependency block with `morimail-html` (line 112), `EmbedImages` using `embed(template, base)`, `embed(template, base, work)` and `toDataUris` (lines 132-146), compiled as above. |
| 5 | `./mvnw verify` passes offline; scoped release build passes without warnings; `docs/release.md` covers the second jar and the build is ready for the owner to release 0.2.0 | VERIFIED | Release outcome is now observable, not only prepared. Central (repo1.maven.org) serves `de/morisoft/morimail/0.2.0`, `morimail-html/0.2.0` and `morimail-parent/0.2.0`: for `morimail` and `morimail-html` each of pom, jar, sources and javadoc with `.asc` (and checksums on `morimail`), for the parent pom plus `.asc`; no `-tests.jar`, no sample artifact (the sample directory returns 404). That is the nine base files with signatures that release.md lists. `git ls-remote` shows `v0.2.0^{}` at `2c4ea80`. GitHub Actions (Temurin 17) is `success` for the pre-release docs commit 857fb56 (run 45263150088), for `release 0.2.0` 2c4ea80 (two runs) and for the bump 8df5f6a. Orchestrator's `./mvnw -B -ntp verify` after 08-03: BUILD SUCCESS, 366/71/164 tests, 0 failures, accepted. release.md `-pl morimail,morimail-html -am` matches `.github/workflows/ci.yml:20`. |

**Score:** 5/5 roadmap truths verified. 0 behavior-unverified.

Plan-level truths beyond the roadmap that I re-checked: README Coordinates now at 0.2.0 with "0.2.0 is the current release on Maven Central" (the owner's step 8, expected after release); `git diff 400357d HEAD` for `morimail/src`, `morimail-html/src` and `.github` is empty (08-01 and 08-03 scope prohibitions); 08-03's commit 2ad672c touches `docs/release.md` only (4 insertions, 2 deletions); both step 5 deploy blocks carry the guard line and the SNAPSHOT bullet states the snapshots-repository behaviour (release.md lines 98-103, 119-122, 135-138).

### G-08-2 closure (UAT test 2)

UAT test 2 still reads `result: issue` in 08-UAT.md (08-03 deliberately did not edit it). The gap's three "missing" items are met:

1. Guard before each deploy: present at release.md lines 101 and 136. Reproduced on scratch POM copies: with `0.3.0-SNAPSHOT` grep prints three lines, exit 1, the chained command does not start; with the suffix removed the command starts.
2. SNAPSHOT bullet replaced with real behaviour: line 121.
3. Re-run of the release: the owner released 0.2.0 on 2026-11-23, independently confirmed by Central and the tag above. The literal 54-entry bundle listing is not re-observable from the repository; what Central serves matches its structure.

### Required Artifacts

| Artifact | Status | Details |
|----------|--------|---------|
| `README.md` | VERIFIED | Sections Inline images, morimail-html, Serial-mail sample; snippets compile |
| `docs/spec.md` | VERIFIED | 5, 5.1, 6.2, 8.1, 8.3, 10.3 updated |
| `docs/architecture.md` | VERIFIED | Live sentence, no-parser clause, stale row removed |
| `docs/release.md` | VERIFIED (see advisory WR-01, WR-02) | Two-jar procedure; step 5 guarded |
| `SendCommand.java`, `SendCommandHelpTest.java` | VERIFIED | Test passes |

### Key Link Verification

| From | To | Status | Details |
|------|----|--------|---------|
| README Inline images snippet | `Mail.Builder.inline(Path,String)` | WIRED | Compiles against built classes |
| README morimail-html snippet | `HtmlImages.embed/toDataUris` | WIRED | Compiles against built classes |
| spec 6.2 | `GraphTransport` attachment JSON | WIRED | Property names match (checked in the earlier pass, source unchanged: `morimail/src` diff since 400357d is empty) |
| release.md `-pl` | `.github/workflows/ci.yml:20` | WIRED | Identical `-pl morimail,morimail-html -am` |
| release.md guard | `pom.xml`, `morimail/pom.xml`, `morimail-html/pom.xml` | WIRED (fails open on grep error, WR-01) | Reproduced both ways |

### Data-Flow Trace (Level 4)

Not applicable: documentation phase. The sample's dynamic output was exercised in the earlier pass.

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| README snippets compile | `javac --release 17 -Xlint:all` on the 3 java blocks | exit 0, no warnings | PASS |
| Help-text test | `./mvnw -B -ntp -o -q test -pl morimail-sample-serial -am -Dtest=SendCommandHelpTest` | exit 0 | PASS |
| Guard, SNAPSHOT POMs | `! grep -- -SNAPSHOT ...` on copies | prints 3 lines, chained command does not start | PASS |
| Guard, release-version POMs | same, suffix removed | starts | PASS |
| Guard, wrong cwd | same from `/` | starts (fails open) | WARNING (WR-01) |
| 0.2.0 on Central | `curl` on repo1.maven.org listings | nine base files with `.asc`; no tests jar; no sample | PASS |
| CI on Temurin 17 | `gh run list` | success for 857fb56, 2c4ea80, 8df5f6a | PASS |

I did not re-run the release-profile build or the full `verify`: 08-03 changed only `docs/release.md`, and the orchestrator's full verify and CI cover the same tree.

### Probe Execution

SKIPPED: no probe scripts declared or present for this phase.

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| DOCS-05 | 08-01 | README inline example; architecture and spec 6 record property names and live status; sample docs describe `inline/` | SATISFIED | Truths 1-3 |
| DOCS-06 | 08-02, 08-03 | Offline verify and scoped release build pass; ready for owner release from `docs/release.md` | SATISFIED | Truth 5: released, CI green, G-08-2 fixed |
| DOCS-07 | 08-01, 08-02 | README `HtmlImages` example and coordinates; release.md covers the second jar | SATISFIED | Truths 4, 5 (`morimail-html` 0.2.0 is on Central) |

All three IDs appear in plan frontmatter (08-01: DOCS-05, DOCS-07; 08-02: DOCS-06, DOCS-07; 08-03: DOCS-06) and REQUIREMENTS.md maps DOCS-05, 06 and 07 to Phase 8 (marked complete). No orphaned requirements.

### Anti-Patterns Found

None blocking. No TBD/FIXME/XXX in README or the three docs. Hygiene scan of non-planning lines added since 400357d: no 16+ character hex string, no `/home` path, no address other than example.com/example.org and the maintainer identity. All commits since 400357d are authored and committed by Stephan Wald <stephan@morisoft.de>.

### Weighing the open review warnings (honestly)

- **WR-01 (guard fails open on grep error).** Real and reproduced. It does not undo the fix: the failure that UAT recorded (deploy at a SNAPSHOT version from the right directory) is stopped. The unsafe cases need a wrong directory or a missing POM, and the former also loses `./mvnw`. It is a hardening gap in a one-line guard, not a missed must-have. Fix is one line per block.
- **WR-02 (release.md still names 0.2.0).** Real, and the first thing the owner will trip over at 0.3.0. The goal states readiness for 0.2.0, which was reached and then used. The doc is correct as the record of that procedure but has not been rolled to the next release. Not a blocker; worth a doc-only follow-up.

Neither warning blocks the goal. Both stay `open` in 08-REVIEW-DISPOSITION.md.

### Human Verification Required

#### 1. Confirm the flagged prohibitions

**Test:** Confirm that the 0.2.0 deploy, push and tag v0.2.0 were your own, and that no Claude run in phase 8 deployed, pushed, tagged or left a signing key behind.
**Expected:** Yes. Claude's phase 8 work was a git-archive rehearsal with `-DskipUpload` plus docs edits.
**Why human:** The judgment-tier prohibition is only an unauthoritative LLM-judge verdict, and the test-tier ones have no wired enforcement test, so they must not read as green. The direct observations (no throwaway key directory, no hygiene hits, no non-owner release commit) are in the frontmatter.

### Gaps Summary

No gaps. The README and docs let a developer use inline images and `HtmlImages`, the snippets compile, and 0.2.0 is on Maven Central with CI green on Temurin 17. G-08-2 is closed in the docs. The only open items are the owner's prohibition confirmation and two optional docs/release.md follow-ups (WR-01 hardening, WR-02 roll-forward to the next version).

---

_Verified: 2026-11-23_
_Verifier: Claude (gsd-verifier)_
