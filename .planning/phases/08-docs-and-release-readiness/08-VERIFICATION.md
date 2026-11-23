---
phase: 08-docs-and-release-readiness
verified: 2026-11-24T00:30:00Z
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
covered_digest: "v4:sha256:bd140c1d81e1fb9eed5f1fb916ab7805aa047f6d8c46ec6c1ddd6963ab63f38b"
behavior_unverified: 0
overrides_applied: 0
re_verification:
  previous_status: passed
  previous_score: 5/5
  gaps_closed:
    - "Review IN-01 (release.md step 8.3 told the owner to make the Coordinates sentence name the new version, which would have kept a false clause about what the previous release added): fixed in 9f15ca1, the step now says to rewrite the text, name the version and say what that release adds"
    - "Review IN-02 (step 8.1 `sed -i` had no macOS note): fixed in 9f15ca1, step 8.1 now says 'On macOS use `sed -i ''`.'"
    - "Review IN-03 ('Steps 5 to 8 use VERSION' overstated): fixed in 9f15ca1, step 4.1 now says steps 7 and 8 use VERSION, step 8 also NEXT, and step 5 needs neither"
  gaps_remaining: []
  regressions: []
gaps: []
---

# Phase 8: Docs and Release Readiness Verification Report

**Phase Goal:** A developer reading the README and docs can use inline images, and the build is ready for the owner to release 0.2.0.
**Verified:** 2026-11-24
**Status:** passed
**Re-verification:** Yes. The previous report (eabd844, `passed`, 5/5) went stale after quick task 261123-ubm (commit 9f15ca1), which changed three prose lines in `docs/release.md` (steps 4.1, 8.1 and 8.3). `git diff eabd844 HEAD -- . ':!.planning'` confirms that `docs/release.md` (6 lines changed) is the only non-planning file that differs. README, spec, architecture, sample sources, POMs, `.github` and module sources are byte-identical to the state that was verified.

All five roadmap success criteria still hold. Nothing fails. The three review info items that were carried as advisories are now fixed, so the report has no open advisories.

## Goal Achievement

### Observable Truths (ROADMAP success criteria)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | README shows a copyable inline-image example using `.inline(...)` and `<img src="cid:...">` | VERIFIED | README unchanged since the previous pass, where the three README java blocks were compiled with `javac --release 17 -Xlint:all` against the built classes (exit 0, no warnings). The README still carries the "Inline images" section. |
| 2 | Spec section 6 and `docs/architecture.md` name the Graph inline property names and the live status as reported | VERIFIED | `docs/spec.md` and `docs/architecture.md` are absent from the diff against eabd844. Previous evidence stands. |
| 3 | The sample docs describe `inline/`, the content-ID rule and the dry-run rewrite | VERIFIED | README, `SendCommand.java` and `SendCommandHelpTest.java` unchanged. The orchestrator's fresh offline `verify` ran the sample's help test in this state. |
| 4 | README shows a copyable `HtmlImages` example (local, remote opt-in) and the `morimail-html` coordinates | VERIFIED | README unchanged; the `EmbedImages` block compiled in the previous pass. |
| 5 | `./mvnw verify` passes offline; the scoped release build passes without warnings; `docs/release.md` covers the second jar; the build is ready for the owner to release 0.2.0 | VERIFIED | Fresh `./mvnw -B -ntp -o verify` by the orchestrator: BUILD SUCCESS, 366 + 71 + 164 tests, 0 failures. 0.2.0 was released by the owner (`v0.2.0`), and the repository is on `0.3.0-SNAPSHOT`. `docs/release.md` still covers the second jar (`-pl morimail,morimail-html -am`, nine base files in the bundle listing at lines 105-115, the three-POM SNAPSHOT guard in both deploy blocks at lines 101 and 136). |

**Score:** 5/5 roadmap truths verified. 0 behavior-unverified.

### Check of the three changed lines (quick task 261123-ubm)

| Review item | Changed text (docs/release.md) | Check | Result |
|-------------|--------------------------------|-------|--------|
| IN-03, step 4.1 (line 67) | "Steps 7 and 8 use `VERSION`, and step 8 also uses `NEXT`. Step 5 needs neither; it names `${VERSION}` only in the bundle listing." | Grepped every `VERSION`/`NEXT` use. Shell reads of the variables sit in step 4 (own sed and commit, lines 72 and 79), step 7 (tag, lines 155-157) and step 8 (line 165). Step 5's commands contain neither variable; `${VERSION}` appears there only in the bundle listing (lines 105-115) and in the prose at line 124. | True. The sentence now matches the file. |
| IN-02, step 8.1 (line 161) | "On macOS use `sed -i ''`." | Matches the same note in step 4.2 (line 69). The `sed -i` command is at line 165. | Present and consistent. |
| IN-01, step 8.3 (line 169) | "rewrite the text under Coordinates that names the current release on Maven Central. Name `${VERSION}` and say what that release adds; the old text describes the previous release." | README line 21 reads "0.2.0 is the current release on Maven Central. It adds inline images and the `morimail-html` helper." Rewriting rather than substituting a version number now avoids a false clause. The instruction to check both dependency blocks (README lines 17 and 113 read 0.2.0) is unchanged. | Fixed. |

The diff touches no command. The two shell blocks in step 8 and the two deploy blocks with the guard are identical to the previous pass, so G-08-2 (UAT test 2) stays closed and the WR-01 and WR-02 fixes stand.

### Other plan-level and process checks (re-run)

- `git diff 400357d HEAD --stat -- morimail/src morimail-html/src .github` is empty: the 08-01 and 08-03 scope prohibitions hold.
- All 57 commits since 400357d are authored and committed by `Stephan Wald <stephan@morisoft.de>`.
- `08-UAT.md`: status `complete`; tests 1, 3, 4 and 5 pass; test 2 (G-08-2) was resolved by 08-03.
- `08-SECURITY.md`: status `verified`, `threats_open: 0`.
- `08-REVIEW.md`: status `clean`. `08-REVIEW-DISPOSITION.md`: `open: 0`.
- No `TBD`, `FIXME` or `XXX` in README, `docs/release.md` or `docs/architecture.md`.
- Working tree: only `07-PATTERNS.md` and `.planning/state.json` are untracked. Both are outside this phase's covered files.

### Required Artifacts

| Artifact | Status | Details |
|----------|--------|---------|
| `README.md` | VERIFIED | Inline images, morimail-html and Serial-mail sample sections present, unchanged |
| `docs/spec.md` | VERIFIED | Unchanged since the previous pass |
| `docs/architecture.md` | VERIFIED | Unchanged since the previous pass |
| `docs/release.md` | VERIFIED | Two-jar procedure, hardened guard, defaults at 0.3.0, three wording fixes applied |
| `SendCommand.java`, `SendCommandHelpTest.java` | VERIFIED | Unchanged, covered by the green offline verify |

### Key Link Verification

| From | To | Status | Details |
|------|----|--------|---------|
| README snippets | `Mail.Builder.inline(Path,String)`, `HtmlImages` | WIRED | README and `morimail*/src` unchanged since they were compiled against the built classes |
| spec 6.2 | `GraphTransport` attachment JSON | WIRED | `morimail/src` unchanged since 400357d |
| release.md `-pl` | `.github/workflows/ci.yml` | WIRED | `.github` unchanged |
| release.md guard | the three published-artifact POMs | WIRED | Guard lines unchanged by 9f15ca1 |

### Data-Flow Trace (Level 4)

Not applicable: documentation phase.

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Offline build | `./mvnw -B -ntp -o verify` (run by the orchestrator, not re-run by me) | BUILD SUCCESS, 366 + 71 + 164 tests, 0 failures | PASS (accepted) |
| Changed prose matches the file | grep of every `VERSION` and `NEXT` use in `docs/release.md` | Matches the step 4.1 sentence | PASS |

### Probe Execution

SKIPPED: no probe scripts declared or present for this phase.

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| DOCS-05 | 08-01 | README inline example; architecture and spec 6 record property names and live status; sample docs describe `inline/` | SATISFIED | Truths 1-3 |
| DOCS-06 | 08-02, 08-03 | Offline verify and scoped release build pass; ready for the owner to release from `docs/release.md` | SATISFIED | Truth 5: released, build green, G-08-2 closed |
| DOCS-07 | 08-01, 08-02 | README `HtmlImages` example and coordinates; release.md covers the second jar | SATISFIED | Truths 4, 5 |

All three IDs appear in plan frontmatter (08-01: DOCS-05, DOCS-07; 08-02: DOCS-06, DOCS-07; 08-03: DOCS-06). REQUIREMENTS.md lists DOCS-05, DOCS-06 and DOCS-07 as `[x]` and Complete at Phase 8 (lines 45-47 and 98-100). No orphaned requirements.

### Prohibitions

The three prohibitions flagged earlier (no deploy, publish, push or tag by Claude; no release version or key material committed; no key id or settings values written to files) were resolved by the owner in UAT test 5 (`pass`). The only change since then is three prose lines in `docs/release.md`. Those lines contain no key id, address, host or secret, so the prohibitions are still not flagged.

### Anti-Patterns Found

None.

### Human Verification Required

None. UAT is complete.

### Gaps Summary

No gaps and no advisories. A developer reading the README can copy the inline-image and `HtmlImages` examples, and the spec, architecture and sample docs record the Graph property names and the live result. 0.2.0 was released by the owner from `docs/release.md` with the build green. Quick task 261123-ubm closed the last three info-level wording points in the release procedure, and the edited sentences match the file.

---

_Verified: 2026-11-24_
_Verifier: Claude (gsd-verifier)_
