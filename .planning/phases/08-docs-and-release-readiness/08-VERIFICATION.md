---
phase: 08-docs-and-release-readiness
verified: 2026-11-23T20:50:00Z
status: human_needed
score: 5/5 roadmap success criteria verified (1 backstop truth routed to the owner)
covered_files:
  - ".planning/phases/08-docs-and-release-readiness/08-01-PLAN.md"
  - ".planning/phases/08-docs-and-release-readiness/08-01-SUMMARY.md"
  - ".planning/phases/08-docs-and-release-readiness/08-02-PLAN.md"
  - ".planning/phases/08-docs-and-release-readiness/08-02-SUMMARY.md"
  - "README.md"
  - "docs/architecture.md"
  - "docs/release.md"
  - "docs/spec.md"
  - "morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/SendCommand.java"
  - "morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/SendCommandHelpTest.java"
covered_digest: "v4:sha256:b259149a578e82699f37e89993b0218c5964991f9d3cf7a67c17eeb2710a6971"
behavior_unverified: 0
overrides_applied: 0
gaps: []
unverified_prohibitions:
  - statement: "MUST NOT upload, publish, deploy to Maven Central, push or tag (08-02 prohibition, judgment tier)"
    verdict: "NON-AUTHORITATIVE LLM-judge: no new tag (only v0.1.0, which predates the phase), origin/main is still 44206a9 so nothing was pushed, no real deploy evidence. The skipUpload rehearsal itself cannot be re-observed after the fact."
    flag: "unverified-prohibition - human review recommended"
  - statement: "MUST NOT commit a release version to any POM, and MUST NOT leave the throwaway key, gpg-agent or signed artifacts on the machine (08-02 prohibition, test tier)"
    verdict: "No wired enforcement test exists, so this is treated as unverified. Direct observation today: all four POMs are at 0.2.0-SNAPSHOT, ~/.gnupg-rh08 and ~/.m2/repository/de/morisoft do not exist."
    flag: "unverified-prohibition - human review recommended"
  - statement: "MUST NOT write the throwaway key id or fingerprint, or workspace Maven settings values, into a file, the SUMMARY or a commit (08-02 prohibition, test tier)"
    verdict: "No wired enforcement test exists. Direct observation: no hex string of 16 or more characters in any non-.planning line added since 400357d; no address outside example.com/example.org and the maintainer identity."
    flag: "unverified-prohibition - human review recommended"
human_verification:
  - test: "Push the phase 8 commits (origin/main is 24 commits behind HEAD) and watch the three CI steps (verify, scoped release build, offline build) on Temurin 17"
    expected: "All three steps green for the pushed commit. JDK 17 is not installed locally, so the zero-warning release build was measured on JDK 21 with --release 17 only."
    why_human: "Backstop truth in 08-02. Claude never pushes, and the Temurin 17 runtime cannot be reproduced locally."
  - test: "Release 0.2.0 by following docs/release.md, starting with the step 5 skipUpload check"
    expected: "central-bundle.zip holds exactly 54 entries (the nine listed base files, each with .asc and four checksums), no -tests.jar, no sample entry. Review in the Portal before Publish."
    why_human: "The owner runs the release (CLAUDE.md: never publish, deploy or tag). The 54-entry rehearsal in 08-02-SUMMARY.md was not re-run by this verification because it needs a signing key and a deploy command."
  - test: "Read docs/release.md as the owner would for a second release (WR-01)"
    expected: "Steps 1 to 3 (namespace, GPG key, Portal token) are understood as one-time, and the existing 0.1.0 signing key is reused rather than a new one generated."
    why_human: "Judgment on whether the wording is clear enough; the open review warning WR-01 covers it."
  - test: "Enable GitHub private vulnerability reporting if still off (SECURITY.md routes reports there)"
    expected: "Setting enabled."
    why_human: "Repository setting, owner only."
---

# Phase 8: Docs and Release Readiness Verification Report

**Phase Goal:** A developer reading the README and docs can use inline images, and the build is ready for the owner to release 0.2.0.
**Verified:** 2026-11-23
**Status:** human_needed
**Re-verification:** No, initial verification

All five roadmap success criteria hold in the codebase. No gaps. The status is `human_needed` only because the owner-only items above (Temurin 17 CI after a push, the release itself, the flagged prohibitions) cannot be observed by Claude.

## Goal Achievement

### Observable Truths (ROADMAP success criteria)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | README shows a copyable inline-image example using `.inline(...)` and `<img src="cid:...">` | VERIFIED | README.md "## Inline images" holds a complete class `InlineImage` with `<img src=\"cid:logo\" ...>` and `.inline(Path.of("logo.png"), "logo")`. I extracted all three README java blocks and compiled them with `javac --release 17 -Xlint:all` against `morimail/target/classes`, `morimail-html/target/classes` and Jackson: COMPILED, no warnings. Text names the `build()` checks (exact match, every image referenced, no HTML body fails, unique IDs, `image/` type) and the 3 MiB / 4 MiB limits. |
| 2 | Spec section 6 and `docs/architecture.md` name the Graph inline property names and the live status as the owner reported it | VERIFIED | spec 6.2 (lines 223-228): `"isInline": true`, bare `"contentId"`, no `contentLocation`, no `isInline` on regular attachments, same limits, "Confirmed live on 2026-11-23 ... Outlook on the web and one desktop client". architecture.md line 52 has the same sentence; the stale "not yet confirmed" is gone. Cross-checked: `GraphTransport.java:168-170` writes `isInline` and `contentId` only when `contentId != null`; `05-UAT.md` test 1 is `result: pass`; `LiveGraphTest.sendsOneMailWithAnInlineImage` exists. Spec 5, 5.1 now describe the 10.3 checks, and the stale architecture deviation row is removed. |
| 3 | The sample docs (`send --help` and the README sample section) describe `inline/`, the content-ID rule and the dry-run rewrite | VERIFIED | Real `send` usage from the built 0.2.0-SNAPSHOT jar prints the footer (campaign files, optional `inline/`, "content ID is the file name without the last extension ... cid:NAME") and the `--dry-run` text (copies once into `inline/` under `--out`, rewrites to `inline/NAME` plus extension, dry-run output only, sent mails keep `cid:`). Behaviour checked by running `send --dry-run` on the example campaign into a scratch directory: exit 0, 5 mails, `src="inline/logo.png"`, matching the docs. README "## Serial-mail sample" has the layout, rules, pre-flight exit 2 and the `out/inline/` paragraph. `SendCommandHelpTest` passes (1 run, 0 failures). |
| 4 | README shows a copyable `HtmlImages` example (local, remote opt-in) and the `morimail-html` coordinates | VERIFIED | README "## morimail-html": dependency block `de.morisoft:morimail-html:0.2.0`, class `EmbedImages` with `embed(template, base)`, `embed(template, base, work)` and `toDataUris` (compiled as above), plus the trust note for work directories and the `$$` placeholder note. |
| 5 | `./mvnw verify` passes offline; scoped release build passes without warnings at 0.2.0-SNAPSHOT; `docs/release.md` covers the second jar | VERIFIED (CI on Temurin 17 pending, see human items) | I re-ran `./mvnw -B -ntp -o -Prelease clean verify -Dgpg.skip -pl morimail,morimail-html -am`: exit 0, 0 lines containing WARN, BUILD SUCCESS, and it produced `morimail-html-0.2.0-SNAPSHOT{,-sources,-javadoc}.jar` and `morimail-0.2.0-SNAPSHOT-javadoc.jar`. The orchestrator's full offline verify (366 + 71 + 164 tests) is accepted. All four POMs are `0.2.0-SNAPSHOT`. `docs/release.md` names the parent, `morimail` and `morimail-html`, uses `VERSION`/`NEXT`, all three `-pl` uses equal the CI line (`.github/workflows/ci.yml:20`), and lists the nine base files / 54 entries. I replayed the step 4 sed in a `git archive` copy: exactly four changed `<version>` lines, one per POM. Sample (`maven.deploy.skip`) and test-jar (`attach=false`) remain out of the bundle. |

**Score:** 5/5 roadmap truths verified. 0 behavior-unverified.

Plan-level truths beyond the roadmap: D-04 coordinates sentence, Quick start unchanged, D-07 (amended 2026-11-23) spec 10.3 dry-run sentence now reads "copies each inline image once into `inline/` under `--out`", spec section 12 rows filled, no new option (`SendCommand` diff is annotation text only). All present. The one 08-02 backstop truth (Temurin 17 CI green after the owner's push) is a human item, not a gap.

### Required Artifacts

| Artifact | Status | Details |
|----------|--------|---------|
| `README.md` | VERIFIED | Sections Inline images, morimail-html, Serial-mail sample; snippets compile |
| `docs/spec.md` | VERIFIED | 5, 5.1, 6.2, 8.1, 8.3, 10.3, 10.4, 12 updated |
| `docs/architecture.md` | VERIFIED | Live sentence, no-parser clause, stale row removed |
| `docs/release.md` | VERIFIED | Version-neutral, two-jar procedure |
| `SendCommand.java` | VERIFIED | Footer and `--dry-run` text; annotation-only diff |
| `SendCommandHelpTest.java` | VERIFIED | Exists, passes (weak assertions, see IN-04) |

### Key Link Verification

| From | To | Status | Details |
|------|----|--------|---------|
| README Inline images snippet | `Mail.Builder.inline(Path,String)` | WIRED | Compiles against `morimail/target/classes` |
| README morimail-html snippet | `HtmlImages.embed/toDataUris` | WIRED | Compiles against `morimail-html/target/classes` |
| spec 6.2 | `GraphTransport` attachment JSON | WIRED | `isInline`, `contentId` written only for inline images |
| release.md `-pl` | `.github/workflows/ci.yml` | WIRED | Identical `-pl morimail,morimail-html -am` |
| release.md step 8 | README sentence under Coordinates | WIRED | Sentence exists at README line 23 |
| `--dry-run` help text | `SendCommand.writeDryRun` | WIRED | Real dry run produced `inline/logo.png` reference |

### Data-Flow Trace (Level 4)

Not applicable: documentation phase. The only dynamic output (usage text, dry-run HTML) was exercised by running the jar.

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| README snippets compile | `javac --release 17 -Xlint:all` on the 3 blocks | COMPILED | PASS |
| Scoped release build, zero warnings | `./mvnw -B -ntp -o -Prelease clean verify -Dgpg.skip -pl morimail,morimail-html -am` | exit 0, 0 WARN | PASS |
| Usage text test | `-Dtest=SendCommandHelpTest` | 1 run, 0 failures | PASS |
| `send` usage from built jar | `java -jar ...0.2.0-SNAPSHOT.jar send` | Footer and dry-run text present | PASS |
| Dry run on example campaign | `send ... --dry-run --out <scratch>` | exit 0, `src="inline/logo.png"` | PASS |
| Release sed (step 4) | replay in `git archive` copy | 4 changed lines | PASS |

### Probe Execution

SKIPPED: no probe scripts declared or present for this phase.

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| DOCS-05 | 08-01 | README inline example; architecture and spec 6 record property names and live status; sample docs describe `inline/` | SATISFIED | Truths 1-3 |
| DOCS-06 | 08-02 | Offline verify and scoped release build pass without warnings at 0.2.0-SNAPSHOT; ready for owner release from release.md | SATISFIED (Temurin 17 CI is the owner's) | Truth 5 |
| DOCS-07 | 08-01, 08-02 | README HtmlImages example and coordinates; release.md covers the second jar | SATISFIED | Truths 4, 5 |

All three IDs appear in plan frontmatter (08-01: DOCS-05, DOCS-07; 08-02: DOCS-06, DOCS-07) and are mapped to Phase 8 in REQUIREMENTS.md. No orphaned requirements.

### Anti-Patterns Found

None blocking. No TBD/FIXME/XXX markers added. Hygiene scan of added non-planning lines since 400357d: no address outside example.com/example.org and the maintainer identity, no non-zero GUID, no 16+ character hex string. All 18 commits since 400357d are authored and committed by Stephan Wald <stephan@morisoft.de>.

### Code review findings (08-REVIEW.md, all recorded as `open`)

None of the findings undermines a success criterion.

| ID | Assessment |
|----|------------|
| WR-01 (release.md steps 1-3 read as part of every release) | Warning, not a gap. SC5 asks that the owner can release 0.2.0 by following the doc. The procedure is complete and correct for the two jars, but steps 1-3 are not marked one-time, so a literal reader could create a second GPG key (breaks signing-key continuity). The owner did 0.1.0 and the risk is real but small. Routed to a human item. Suggested fix is in 08-REVIEW.md. |
| IN-01 ("Steps 5 to 8 use both variables") | Wording only; only step 8 uses `NEXT`. |
| IN-02 (README names 0.2.0 before it is on Central) | Deliberate per D-04; the Coordinates sentence says so; the `morimail-html` block carries no caveat. |
| IN-03 (spec 10.3 still says "not verified" and "without extension") | Minor spec inconsistency. D-07 kept the 10.3 text by design except the dry-run sentence. 6.2 and architecture.md carry the verified property names and live result, so SC2 holds. Worth a small follow-up edit. |
| IN-04 (help test assertions are weak) | `cid:NAME` pins the new text; the dry-run sentence is not asserted. I confirmed the real help output contains it. Does not affect SC3. |
| IN-05 (single quote in an inline file extension in the dry-run `src`) | Dry-run preview only, real send unaffected. Out of scope. |

### Gaps Summary

No gaps. The documentation matches the code (checked by compiling the README snippets, running the built sample and replaying the release steps), and the scoped release build is warning-free. The remaining work is the owner's: push and confirm Temurin 17 CI, optionally tidy release.md (WR-01) and the spec 10.3 bullets (IN-03), then release 0.2.0.

---

_Verified: 2026-11-23_
_Verifier: Claude (gsd-verifier)_
