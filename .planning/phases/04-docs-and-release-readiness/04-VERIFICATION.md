---
phase: 04-docs-and-release-readiness
verified: 2026-11-22T20:10:00Z
status: human_needed
score: 4/5 must-haves verified
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
  - README.md
  - docs/architecture.md
  - docs/entra-setup.md
  - docs/release.md
  - docs/spec.md
  - morimail/src/main/java/de/morisoft/morimail/entra/EntraClientCredentials.java
  - morimail/src/main/java/de/morisoft/morimail/graph/GraphTransport.java
  - morimail/src/test/java/de/morisoft/morimail/entra/EntraTokenErrorsTest.java
  - morimail/src/test/java/de/morisoft/morimail/graph/GraphErrorMappingTest.java
covered_digest: "v4:sha256:acade4e1af7bc6069cba5ca8d95af87bd24be9b66726befd25b5be1cdd8cb6d9"
behavior_unverified: 0
overrides_applied: 0
human_verification:
  - test: "Follow docs/entra-setup.md on the real tenant: register the app, create the certificate, grant Mail.Send (path A, and path B if you want the scoped route), fill in a campaign copy, then run the dry run, `--limit 1` to your own address, the full run to test addresses, and the same full command a second time"
    expected: "The dry run writes the rendered mails. `--limit 1` ends with `accepted 1, skipped 0, failed 0`. The full run skips row 1 and sends the rest. The second full run prints `accepted 0, skipped <rows>, failed 0` and sends nothing. Report any step whose screen, cmdlet or output differs from the guide."
    why_human: "Roadmap success criterion 2 needs a real tenant and real mail. The project rules forbid the agent from sending mail. Path B has never been run live (the guide says so under 'Not yet verified live')."
  - test: "Push the 27 local commits (or a branch) and watch GitHub Actions on Temurin 17: `./mvnw -B -ntp verify`, the release build `-Prelease clean verify -Dgpg.skip -pl morimail -am`, and the `unshare --net` offline verify"
    expected: "All three steps green."
    why_human: "Nothing is pushed (the agent must not push). Only JDK 21 was available here, so JDK 17 runtime behaviour (PS256 signing, PKCS12 load) is untested by the agent."
  - test: "Run the real release by docs/release.md: namespace, GPG key, Portal token, version bump, `deploy`, Portal review and publish, tag, next SNAPSHOT"
    expected: "The Portal validates the deployment (POM metadata, signatures, sources and javadoc) and the bundle matches the 30-entry listing."
    why_human: "Publishing is the owner's step. The Portal validates only on upload, so no local command can prove it. `autoPublish` is false, so a rejection stops before anything is public."
  - test: "Triage the three open review warnings in 04-REVIEW.md (WR-01, WR-02, WR-03) before the owner walk-through and the release; WR-02 and WR-03 change what the owner does"
    expected: "Each warning is fixed or accepted in writing."
    why_human: "These are wording and ordering decisions in owner-facing documents."
---

# Phase 4: Docs and Release Readiness Verification Report

**Phase Goal:** As the owner, I want the setup guide, final docs and a release rehearsal shaped by the live test, so that I can set up my tenant and publish 0.1.0.
**Verified:** 2026-11-22T20:10:00Z
**Status:** human_needed
**Re-verification:** No, initial verification

## Goal Achievement

All four documents the phase promised exist, are substantive, are linked to each other and to real code, and match the code and the live-test record. The one release-shaped build gate I re-ran passed. What is left is what only the owner can do: a live walk-through of the guide, CI on Temurin 17 after a push, and the real release. I found no FAILED truth. Three review warnings are open, and two of them (WR-02, WR-03) point at real defects in the owner's instructions. They do not make a must-have false, but the owner should fix them before following the documents.

### Observable Truths (roadmap success criteria)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | The live-test findings are applied: error mapping, attachment boundary and default rate in code, spec §6.3 and architecture.md match the real responses, and every error-table row is still tested | VERIFIED | `03-UAT.md` records 202 (both flows), 403 `ErrorAccessDenied`, 404 `ErrorInvalidUser`, 2,900,000 and 3,100,000 byte attachments accepted, and `--rate 20` accepted by the owner. `git diff ed97e36 HEAD -- morimail/src/main` touches only `clean()` and Javadoc, so the mapping, `MAX_ATTACHMENT_BYTES = 3L*1024*1024`, `MAX_REQUEST_BYTES = 4L*1024*1024` and `--rate` default 20 are unchanged, as the live answers matched them. `docs/spec.md` §6.3 has the `Live status (2026-11-22)` column and the 404 finding, §8.3 states 30/min and 10,000/day, and §11 is untouched (the diff has three hunks, none in §11). `GraphErrorMappingTest` still has the status table, 401 retry, mailbox-hint, `Retry-After`, redirect, timeout and reset tests. The only removed test lines are the two single-long-word expectations, now `-` as planned. |
| 2 | The owner can follow `docs/entra-setup.md` from app registration and certificate, through RBAC for Applications (union trap, delay, Enterprise-application IDs, Organization Management), to a dry run, a `--limit 1` send, the example campaign, and a second run that sends nothing | UNCERTAIN (human) | The guide is complete and its facts match the code. See the Setup Guide check below. Whether a real person can follow it on a real tenant is the owner's walk-through, a `backstop` truth that no presence check can settle. Routed to human verification. WR-02 is a defect on this path. |
| 3 | `docs/architecture.md` summarises the contracts on one page and records every deviation | VERIFIED | One `## Contracts` heading with 10 bullets and 459 words (plan limit: 8 to 12 bullets, at most 650 words). It comes before `## Deviations from the spec` and `## Differences from the Microsoft documentation`. The table has 18 rows. `freshAccessToken` (row 1), `baseUrl` (§6.1), unchecked registry exceptions (§2/§5.1), JUnit 6 (§3), `changeit` keystore (§10.1) and the reactor-only test-jar (§10.1) are all there. `§6.3 (live results)` sits directly under `§6.3 (failure message)`, and `§8.3 (rate default)` is the last row. The Microsoft-doc differences section has 18 dated bullets, including the Exchange Administrator role, `Test-ServicePrincipalAuthorization` ignoring Entra grants, the `PrimarySmtpAddress` advice and the limits-page wording. |
| 4 | `README.md` says what morimail is and is not, pre-1.0 status, coordinates, quick start, at-least-once and accepted-not-delivered semantics, and the test keystore | VERIFIED | Present: status, coordinates `de.morisoft:morimail:0.1.0`, quick start, "What morimail is not" (not a bulk mailer; 30/min and 10,000/day; 100 external recipients per 24 h for onmicrosoft.com; under 3 MiB), "Delivery semantics" with the at-least-once sentence, and the keystore sentence (password `changeit`). I extracted the Java block and compiled it with `javac --release 17 -Xlint:all -Werror` against the freshly built jar: COMPILE_OK. The three catches come in the order Transient, Permanent, AuthRequired. The sample flags in the dry-run command (`--account`, `--campaign`, `--dry-run`, `--out`) exist in `SendCommand`. No quick-start mirror file exists in the repository (D-09). WR-01 is a wording problem in this file (see below). |
| 5 | `mvn -Prelease verify -Dgpg.skip -pl morimail -am` builds sources and Javadoc jars without warnings; a `-DskipUpload=true` bundle holds only `morimail-parent` and `morimail`; the owner's steps are listed | VERIFIED (bundle by structure plus a recorded run) | I ran `./mvnw -B -ntp -o -Prelease clean verify -Dgpg.skip -pl morimail -am`: BUILD SUCCESS, 314 tests, 0 failures, no `.java` warning line, and `morimail-0.1.0-SNAPSHOT-sources.jar` and `-javadoc.jar` exist. The bundle listing (30 entries, no `-tests.jar`, no sample) is the 04-04 rehearsal's own report. The rehearsal ran in a scratch copy and was cleaned up by design, so I could not re-open the zip. Structural evidence supports it: `morimail/pom.xml` release profile sets `attach false` on the test-jar, the central plugin sets `autoPublish false` and `publishingServerId central`, and `-pl morimail -am` leaves out the sample module. `docs/release.md` has the nine required headings and the owner's steps. Cleanup holds: no `de/morisoft` under `~/.m2`, no `settings.xml`, no `~/.gnupg`, no tag, no `.asc` tracked. |

**Score:** 4/5 truths verified. The fifth (the owner's walk-through) is routed to human verification. 0 truths are present-but-behavior-unverified.

### Plan must-haves beyond the roadmap criteria

| Plan | Truth | Status | Evidence |
|------|-------|--------|----------|
| 04-01 | `clean()` cuts at a word boundary in both classes; the straddling address leaves no part behind; a single word over 1000 characters gives `-` | VERIFIED (behavioral) | Both `clean()` methods run `lastIndexOf(' ')` after the 1000-character cut, before the address pattern. `anAddressStraddlingTheCutLeavesNoPartOfItInTheMessage` exists in `GraphErrorMappingTest` and `EntraTokenErrorsTest` and asserts the exact message and `doesNotContain("alice")`. Both pass in the 314-test run. |
| 04-01 | `graph` and `entra` do not import each other; no shared cleaning class | VERIFIED | The cut is written twice. `ImportRulesTest` passes. |
| 04-02 | Guide has the ten required `##` headings in order, ending in `## Not yet verified live` with six items | VERIFIED | Headings match exactly. The closing section lists path B, shared mailbox, display name, out-of-scope refusal, `MailboxNotEnabledForRESTAPI` and the mailbox 401. |
| 04-02 | Secrets only through `read -rs` and environment variables; key files ignored; test keystore stays tracked | VERIFIED | `read -rs` for `MAIL_CERT_PASSWORD` and `MAIL_CLIENT_SECRET`; `-passout env:`; `account.properties` holds only env var names. `.gitignore` has `*.p12 *.key *.pem *.cer` with `!morimail/src/test/resources/test-keystore-throwaway.p12`. `git check-ignore` reports the keystore as not ignored, and it is the only tracked key file. |
| 04-03 | README states the attachment limit in binary units and the Sent Items note (D-03) | VERIFIED | "under 3 MiB (3 x 1024 x 1024 bytes), per file and in total" matches `MAX_ATTACHMENT_BYTES`. The `saveToSentItems` sentence is in the sample paragraph. |
| 04-04 | Nothing uploaded, published, tagged or pushed | VERIFIED | `git tag` is empty. `main` is ahead of `origin/main` by 27 commits, so nothing was pushed. No Central credential exists. |
| Prohibitions | No real address, tenant id, GUID, token or host in README, docs or release.md | VERIFIED | Grep for non-zero GUIDs and for addresses outside `example.com`, `example.org` and the maintainer identity found nothing. `release.md` uses `TOKEN_USERNAME`, `TOKEN_PASSWORD` and `<KEYID>` only. |

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `docs/entra-setup.md` | Setup guide | VERIFIED | 240 lines, ten sections, real commands and cmdlets, linked from README. |
| `docs/architecture.md` | One-page contracts and deviations | VERIFIED | 62 lines. See truth 3. |
| `README.md` | What it is and is not, quick start | VERIFIED | 106 lines. See truth 4. |
| `docs/release.md` | Owner's release steps | VERIFIED | 115 lines, nine headings. See WR-03. |
| `docs/spec.md` | §6.3 live column, §8.3 limits | VERIFIED | Edits confirmed by diff. |
| `.gitignore` | Key files ignored | VERIFIED | See above. |
| `GraphTransport.java`, `EntraClientCredentials.java` | Word-boundary `clean()` | VERIFIED | Wired: `clean()` is called from the failure paths, and both tests exercise it end to end through WireMock. |

### Key Link Verification

| From | To | Status | Details |
|------|----|--------|---------|
| `README.md` | `docs/entra-setup.md` | WIRED | Linked twice. |
| `README.md` QuickStart | `EntraClientCredentials.withCertificate(...)` | WIRED | Compiles with `-Werror` against the real jar. |
| `docs/entra-setup.md` step 8 | `example-campaign/account.properties` | WIRED | The keys `account.id`, `tenantId`, `clientId`, `from`, `certificate.path`, `certificate.passwordEnv` match the shipped file. `secretEnv` is documented as the alternative. |
| `docs/entra-setup.md` step 9 | `SendCommand` | WIRED | `--limit`, `--rate` default 20, `journal.jsonl` in the campaign directory, the `dry run:` and `accepted N, skipped N, failed N` lines all exist in the sample source. |
| `docs/release.md` step 5 | root `pom.xml` release profile | WIRED | `-Prelease ... -pl morimail -am`, server id `central`, `autoPublish false`. |
| `docs/spec.md` edits | `docs/architecture.md` | WIRED | Both edits are recorded as deviation rows. |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Release build is warning-free and tests pass | `./mvnw -B -ntp -o -Prelease clean verify -Dgpg.skip -pl morimail -am` | BUILD SUCCESS, 314 tests, 0 failures, no Javadoc warning | PASS |
| README quick start compiles | `javac --release 17 -Xlint:all -Werror` on the extracted block against the built jar | COMPILE_OK | PASS |

I ran only the release-profile build, which includes the test run. I did not re-run the rehearsal deploy (it needs GPG and writes to `~/.m2`).

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| DOCS-01 | 04-02 | `docs/entra-setup.md` from app registration to verification | SATISFIED in writing, walk-through pending | All required content is present (registration, certificate and secret, paths A and B, union trap, 30 min to 2 h delay, Enterprise-application IDs, Organization Management, licensed mailbox, shared-mailbox question, `saveToSentItems`, `account.properties`, dry run and `--limit 1`). The live walk-through is a human item. See WR-02. |
| DOCS-02 | 04-01, 04-02 | `docs/architecture.md` one-page contracts and every deviation | SATISFIED | See truth 3. |
| DOCS-03 | 04-03 | README with limits, delivery semantics and keystore sentence | SATISFIED | See truth 4. See WR-01 on wording. |
| DOCS-04 | 04-04 | Release build, rehearsal bundle, owner's steps | SATISFIED | See truth 5. See WR-03. |

Every ID in the PLAN frontmatter (DOCS-01 to DOCS-04) is in REQUIREMENTS.md and mapped to Phase 4. REQUIREMENTS.md maps no further ID to Phase 4, so there are no orphaned requirements. The traceability table marks all four Complete, and I agree with that for the written deliverables. DOCS-01's end-to-end claim still waits on the owner.

### Anti-Patterns Found

| File | Pattern | Severity | Impact |
|------|---------|----------|--------|
| Phase files | `TBD`, `FIXME`, `XXX` markers | none found | The debt-marker gate is clear. |
| `docs/architecture.md`, two `clean()` comments, two test comments | Planning ids (`WR-01`, `G-03-2`, `D-06`) in public files | Info (IN-01) | Meaningless to an outside reader. |
| `EntraClientCredentials.clean()` | Treats only `isISOControl` as a space, while `GraphTransport.clean()` also treats `isWhitespace` as one; U+2028 and U+2029 pass through the Entra path | Info (IN-02) | Cosmetic. Both documents say "control characters". |
| Two test names | `aVeryLong...IsCutAndCleaned...` now assert `-` | Info (IN-03) | Misleading name. No test covers a long text with spaces whose kept words still exceed 300 characters. |
| `.gitignore` | `*.jks`, `*.keystore`, `*.crt` and similar are not ignored | Info (IN-04) | Not required by the plan. |

### Review Warnings (all three open, none a must-have failure)

I read each against the must-haves, not just the review's own severity.

- **WR-01 (README and spec §8.3 state the 30/min limit as fact).** Confirmed in `README.md:86`. `docs/architecture.md` itself says Microsoft gives 30/min for SMTP client submission and does not say that Graph `sendMail` counts against it. DOCS-03 asks the README to state those limits, so the requirement is met. The wording overclaims. Fix before release so the README does not contradict the architecture page.
- **WR-02 (guide says delete `app.key`, then the fallback needs it).** Confirmed: `entra-setup.md:51` says "Then delete `app.key`", and lines 55-61 give a fallback that needs `app.key`. The happy path still works. Only a reader on an old JDK 17 patch level who deletes the key as told must regenerate the key pair and re-upload the certificate. It sits directly on the success-criterion-2 path, so fix it before the owner's walk-through.
- **WR-03 (CI on the release commit runs after publishing).** Confirmed in `docs/release.md`. Step 4 makes the commit locally, step 6 publishes, and step 7 is the first push. The "Before the first release" list asks for green CI on the released commit, but the steps do not allow it. The local `./mvnw verify` in step 4 is the only guard before the permanent publish. Reorder so the release commit is pushed and CI is green before step 5.

### Gaps Summary

There are no gaps that block the goal. The documents, the code change and the build all exist and are consistent with each other and with the live-test record. The phase is `human_needed` because three things can only be done by the owner: the live walk-through of `docs/entra-setup.md` (roadmap criterion 2, and path B has never run live), CI on Temurin 17 after a push, and the real Central release. The agent's own project rules forbid all three. I recommend fixing WR-01, WR-02 and WR-03 (documentation edits only) before the owner starts.

---

_Verified: 2026-11-22T20:10:00Z_
_Verifier: Claude (gsd-verifier)_
