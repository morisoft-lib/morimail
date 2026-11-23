---
phase: 08-docs-and-release-readiness
verified: 2026-11-23T23:55:00Z
status: passed
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
covered_digest: "v4:sha256:b73f69174d8c1713006c616a0972e40381bc742baf86bb40359a38faa3f29980"
behavior_unverified: 0
overrides_applied: 0
re_verification:
  previous_status: human_needed
  previous_score: 5/5
  gaps_closed:
    - "Previous advisory WR-01 (release.md SNAPSHOT guard failed open on a grep error): fixed in da6bc67; guard is now `grep -- -SNAPSHOT ...; [ $? -eq 1 ] &&`"
    - "Previous advisory WR-02 (release.md defaults still named the released 0.2.0): fixed in 088d37d; defaults are VERSION=0.3.0 and NEXT=0.4.0-SNAPSHOT"
    - "Previous human item (confirm the three flagged prohibitions): UAT test 5 result pass in 08-UAT.md"
    - "G-08-2 (UAT test 2) stays closed: status resolved, resolved_by 08-03"
  gaps_remaining: []
  regressions: []
gaps: []
advisory:
  - finding: "IN-01 (08-REVIEW.md): release.md step 8.3 says to change the README Coordinates sentence 'so that it names ${VERSION}', but that sentence also says what 0.2.0 added, so a literal edit for 0.3.0 would keep a false clause"
    category: other
    reason: "Wording only, in the step after the release. Open and advisory in 08-REVIEW.md."
    evidence_status: "confirmed in file (docs/release.md:169, README.md:21), not goal-blocking"
  - finding: "IN-02 (08-REVIEW.md): step 8 `sed -i` has no macOS note (step 4 has one)"
    category: other
    reason: "Recoverable, after publish and tag. Open and advisory."
    evidence_status: "confirmed in file (docs/release.md:165), not goal-blocking"
  - finding: "IN-03 (08-REVIEW.md): 'Steps 5 to 8 use VERSION' overstates it; only steps 7 and 8 read the shell variables"
    category: other
    reason: "Wording only. Open and advisory."
    evidence_status: "confirmed in file (docs/release.md:67), not goal-blocking"
---

# Phase 8: Docs and Release Readiness Verification Report

**Phase Goal:** A developer reading the README and docs can use inline images, and the build is ready for the owner to release 0.2.0.
**Verified:** 2026-11-23
**Status:** passed
**Re-verification:** Yes. The previous report went stale after review fixes da6bc67 and 088d37d changed `docs/release.md`. Since the previous report (db62321) the only changed file among README, docs, module sources, POMs and sample is `docs/release.md` (7 insertions, 7 deletions, checked with `git diff --stat db62321 HEAD`).

All five roadmap success criteria hold, nothing fails, and the previously open human item is resolved by the owner's UAT. 0.2.0 was released by the owner, and the repository has moved on to `0.3.0-SNAPSHOT`.

## Goal Achievement

### Observable Truths (ROADMAP success criteria)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | README shows a copyable inline-image example using `.inline(...)` and `<img src="cid:...">` | VERIFIED | README "## Inline images" (line 74), with `cid:logo` in the HTML and `.inline(Path.of("logo.png"), "logo")` (line 93). Line 101 states the `build()` checks. I extracted all three README java blocks (QuickStart, InlineImage, EmbedImages) and compiled them again with `javac --release 17 -Xlint:all` against `morimail/target/classes`, `morimail-html/target/classes` and Jackson 2.23.0: exit 0, no warnings. |
| 2 | Spec section 6 and `docs/architecture.md` name the Graph inline property names and the live status as reported | VERIFIED | Unchanged since the previous pass (git diff shows only `docs/release.md` changed): spec 6.2 names `isInline`, bare `contentId` and the live confirmation of 2026-11-23, and `docs/architecture.md` carries the same sentence. |
| 3 | The sample docs describe `inline/`, the content-ID rule and the dry-run rewrite | VERIFIED | README "## Serial-mail sample" lines 166-180: `inline/` layout, content ID = file name without last extension, pre-flight exit 2, `out/inline/` dry-run paragraph. `SendCommand.java` and `SendCommandHelpTest.java` unchanged since the previous pass, where the help test was run (exit 0). The orchestrator's fresh `./mvnw -B -ntp -o verify` was BUILD SUCCESS on all four modules, which includes this test. |
| 4 | README shows a copyable `HtmlImages` example (local, remote opt-in) and the `morimail-html` coordinates | VERIFIED | README "## morimail-html" (line 105) with the dependency block (lines 112-113) and `EmbedImages`, which compiled as above. |
| 5 | `./mvnw verify` passes offline; the scoped release build passes without warnings; `docs/release.md` covers the second jar; the build is ready for the owner to release 0.2.0 | VERIFIED | Outcome observed rather than only prepared. The owner released 0.2.0 (`2c4ea80 release 0.2.0`, tag `v0.2.0`, bump `8df5f6a`), and the previous pass confirmed on Central the nine base files with signatures for `morimail`, `morimail-html` and the parent, with no tests jar and no sample artifact. Orchestrator's fresh offline `verify` is green on all four modules. `docs/release.md` covers the second jar: `-pl morimail,morimail-html -am`, nine base files in the bundle listing (lines 105-115), and a three-POM guard. |

**Score:** 5/5 roadmap truths verified. 0 behavior-unverified.

Plan-level truths beyond the roadmap that I re-checked:

- `git diff 400357d HEAD --stat -- morimail/src morimail-html/src .github` is empty (08-01 and 08-03 scope prohibitions hold).
- All 51 commits since 400357d are authored and committed by `Stephan Wald <stephan@morisoft.de>`.
- 08-UAT.md: status `complete`, 4 pass and 1 issue. The issue (test 2, G-08-2) is marked `status: resolved`, `resolved_by: 08-03-PLAN.md`.
- 08-SECURITY.md: `status: verified`, `threats_open: 0`.
- 08-REVIEW.md (fresh): 0 critical, 0 warning, 3 info.

### Review-fix check: the two previous advisories are now fixed

| Item | Check | Result |
|------|-------|--------|
| WR-01 guard hardening | Both deploy blocks (`docs/release.md` lines 101 and 136) read `grep -- -SNAPSHOT pom.xml morimail/pom.xml morimail-html/pom.xml; [ $? -eq 1 ] &&` followed by the `./mvnw` line. I ran the guard from `/` where no POM exists: grep reports `No such file or directory` for all three, exits 2, and the chained command does NOT start (`STOPPED`). I ran it in the repository, where the POMs read `0.3.0-SNAPSHOT`: grep prints the three matching lines and the chained command does NOT start (`STOPPED`). The release-version case (grep exit 1, chain starts) is shown by the reviewer's scratch-POM run in 08-REVIEW.md and follows from the `[ $? -eq 1 ]` test. The prose at line 98 matches this behaviour, including the error case. |
| WR-02 defaults | Lines 63-64 read `VERSION=0.3.0` and `NEXT=0.4.0-SNAPSHOT`. The root `pom.xml` reads `0.3.0-SNAPSHOT`, so the step 4 `sed` pattern matches the real POMs. The only `0.x.y` strings left in the file are those two defaults. Line 12 now says "any version". |

### G-08-2 (UAT test 2) stays closed

Both step 5 deploy commands sit behind the guard, and the SNAPSHOT bullet (line 121) states the real plugin behaviour: at a SNAPSHOT version the plugin deploys to Central's snapshots repository, and only `-DskipUpload=true` stops that. With the guard hardened, a deploy at a SNAPSHOT version and a deploy from the wrong directory are both stopped.

### Required Artifacts

| Artifact | Status | Details |
|----------|--------|---------|
| `README.md` | VERIFIED | Inline images, morimail-html and Serial-mail sample sections; snippets compile |
| `docs/spec.md` | VERIFIED | Sections 5, 5.1, 6.2, 8.1, 8.3, 10.3 updated (unchanged since the previous pass) |
| `docs/architecture.md` | VERIFIED | Live-status sentence present |
| `docs/release.md` | VERIFIED | Two-jar procedure, hardened guard, defaults rolled to 0.3.0 |
| `SendCommand.java`, `SendCommandHelpTest.java` | VERIFIED | Unchanged, covered by the green offline verify |

### Key Link Verification

| From | To | Status | Details |
|------|----|--------|---------|
| README Inline images snippet | `Mail.Builder.inline(Path,String)` | WIRED | Compiles against built classes |
| README morimail-html snippet | `HtmlImages.embed/toDataUris` | WIRED | Compiles against built classes |
| spec 6.2 | `GraphTransport` attachment JSON | WIRED | `morimail/src` unchanged since 400357d |
| release.md `-pl` | `.github/workflows/ci.yml` | WIRED | Same `-pl morimail,morimail-html -am` as in the previous pass; `.github` unchanged |
| release.md guard | `pom.xml`, `morimail/pom.xml`, `morimail-html/pom.xml` | WIRED | Stops on a SNAPSHOT and on a missing POM (reproduced) |

### Data-Flow Trace (Level 4)

Not applicable: documentation phase.

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| README snippets compile | `javac --release 17 -Xlint:all` on the 3 java blocks | exit 0, no warnings | PASS |
| Guard, SNAPSHOT POMs | guard lines run in the repository | three matches printed, chain does not start | PASS |
| Guard, wrong cwd (WR-01) | guard lines run from `/` | grep exit 2, chain does not start | PASS |
| Offline build | `./mvnw -B -ntp -o verify` (run by the orchestrator, not re-run by me) | BUILD SUCCESS, four modules | PASS (accepted) |

### Probe Execution

SKIPPED: no probe scripts declared or present for this phase.

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| DOCS-05 | 08-01 | README inline example; architecture and spec 6 record property names and live status; sample docs describe `inline/` | SATISFIED | Truths 1-3 |
| DOCS-06 | 08-02, 08-03 | Offline verify and scoped release build pass; ready for owner release from `docs/release.md` | SATISFIED | Truth 5: released, build green, G-08-2 closed and hardened |
| DOCS-07 | 08-01, 08-02 | README `HtmlImages` example and coordinates; release.md covers the second jar | SATISFIED | Truths 4, 5 |

All three IDs appear in plan frontmatter (08-01: DOCS-05, DOCS-07; 08-02: DOCS-06, DOCS-07; 08-03: DOCS-06), and REQUIREMENTS.md lists DOCS-05, DOCS-06 and DOCS-07 as `[x]` and Complete at Phase 8 (lines 45-47, 98-100). No orphaned requirements.

### Prohibitions

The three prohibitions flagged in the previous report (no deploy, publish, push or tag by Claude; no release version committed and no key material left; no key id or settings values written to files) were the subject of UAT test 5. The owner answered `pass`: the 0.2.0 deploy, push and tag were the owner's own, and Claude's phase 8 work was a git-archive copy or a `skipUpload` rehearsal. That is the explicit human resolution the judgment tier requires, so they are no longer flagged. My own observations agree: the phase commits contain no deploy, push or tag artefacts, and a hygiene scan of non-planning lines added since 400357d found no 16+ character hex string, no `/home` path, and no address other than example.com, example.org and the maintainer identity.

### Anti-Patterns Found

None blocking. No TBD, FIXME or XXX in README or the three docs. The three open info items from 08-REVIEW.md are recorded under `advisory:` above. They are wording points in `docs/release.md` step 8 and step 5 prose, apply to the next release, and do not affect any success criterion.

### Human Verification Required

None. UAT is complete (4 pass, 1 issue resolved by 08-03).

### Gaps Summary

No gaps. A developer reading the README can copy the inline-image and `HtmlImages` examples, which compile. The spec, architecture and sample docs record the Graph property names and the live result. 0.2.0 was released by the owner from `docs/release.md`, with the build green. After the review fixes, the release procedure is now rolled to 0.3.0 and its SNAPSHOT guard stops on both a SNAPSHOT version and a missing POM. Three info-level wording points remain open as advisory.

---

_Verified: 2026-11-23_
_Verifier: Claude (gsd-verifier)_
