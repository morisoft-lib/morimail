---
phase: 01-library-core
verified: 2026-11-21T14:20:00Z
status: human_needed
score: 4/5 must-haves verified
covered_files: [".gitattributes", ".github/workflows/ci.yml", ".mvn/wrapper/maven-wrapper.properties", ".planning/phases/01-library-core/01-01-PLAN.md", ".planning/phases/01-library-core/01-01-SUMMARY.md", ".planning/phases/01-library-core/01-02-PLAN.md", ".planning/phases/01-library-core/01-02-SUMMARY.md", ".planning/phases/01-library-core/01-03-PLAN.md", ".planning/phases/01-library-core/01-03-SUMMARY.md", ".planning/phases/01-library-core/01-04-PLAN.md", ".planning/phases/01-library-core/01-04-SUMMARY.md", ".planning/phases/01-library-core/01-05-PLAN.md", ".planning/phases/01-library-core/01-05-SUMMARY.md", ".planning/phases/01-library-core/01-06-PLAN.md", ".planning/phases/01-library-core/01-06-SUMMARY.md", ".planning/phases/01-library-core/01-07-PLAN.md", ".planning/phases/01-library-core/01-07-SUMMARY.md", "CLAUDE.md", "LICENSE", "README.md", "SECURITY.md", "docs/architecture.md", "docs/spec.md", "morimail-sample-serial/pom.xml", "morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/RecordingTestJarSmokeTest.java", "morimail/pom.xml", "morimail/src/main/java/de/morisoft/morimail/Attachment.java", "morimail/src/main/java/de/morisoft/morimail/AuthRequiredException.java", "morimail/src/main/java/de/morisoft/morimail/BuiltInTransports.java", "morimail/src/main/java/de/morisoft/morimail/InvalidMailException.java", "morimail/src/main/java/de/morisoft/morimail/Mail.java", "morimail/src/main/java/de/morisoft/morimail/MailAccount.java", "morimail/src/main/java/de/morisoft/morimail/MailAddress.java", "morimail/src/main/java/de/morisoft/morimail/MailException.java", "morimail/src/main/java/de/morisoft/morimail/MailMessage.java", "morimail/src/main/java/de/morisoft/morimail/MailTemplate.java", "morimail/src/main/java/de/morisoft/morimail/MailTransport.java", "morimail/src/main/java/de/morisoft/morimail/Mailer.java", "morimail/src/main/java/de/morisoft/morimail/PermanentMailException.java", "morimail/src/main/java/de/morisoft/morimail/SenderRejectedException.java", "morimail/src/main/java/de/morisoft/morimail/TemplateRenderer.java", "morimail/src/main/java/de/morisoft/morimail/TokenProvider.java", "morimail/src/main/java/de/morisoft/morimail/TransientMailException.java", "morimail/src/main/java/de/morisoft/morimail/TransportProvider.java", "morimail/src/main/java/de/morisoft/morimail/TransportRegistry.java", "morimail/src/test/java/de/morisoft/morimail/ErrorHierarchyTest.java", "morimail/src/test/java/de/morisoft/morimail/ImportRulesTest.java", "morimail/src/test/java/de/morisoft/morimail/MailAccountTest.java", "morimail/src/test/java/de/morisoft/morimail/MailBuilderTest.java", "morimail/src/test/java/de/morisoft/morimail/MailModelTest.java", "morimail/src/test/java/de/morisoft/morimail/MailerConcurrencyTest.java", "morimail/src/test/java/de/morisoft/morimail/MailerTest.java", "morimail/src/test/java/de/morisoft/morimail/PublicTypesDocumentThreadSafetyTest.java", "morimail/src/test/java/de/morisoft/morimail/RecordingTransportTest.java", "morimail/src/test/java/de/morisoft/morimail/SendThroughRecordingTest.java", "morimail/src/test/java/de/morisoft/morimail/TemplateRendererTest.java", "morimail/src/test/java/de/morisoft/morimail/TemplatedSendTest.java", "morimail/src/test/java/de/morisoft/morimail/TokenProviderTest.java", "morimail/src/test/java/de/morisoft/morimail/TransportRegistryTest.java", "morimail/src/test/java/de/morisoft/morimail/test/RecordingTransport.java", "morimail/src/test/java/de/morisoft/morimail/test/RecordingTransportProvider.java", "morimail/src/test/resources/META-INF/services/de.morisoft.morimail.TransportProvider", "pom.xml"]
covered_digest: "v4:sha256:9410979d746b79562d834b8b7e86af0578e312c56cb26a9ffa2f28da0f166052"
behavior_unverified: 0
overrides_applied: 1
overrides:
  - must_have: "Every commit has author and committer Stephan Wald <stephan@morisoft.de> (REPO-11); the repository is safe to publish from its first commit (ROADMAP SC5)"
    reason: "Root commit 8c9b082 was created by GitHub (a different author address, committer GitHub noreply) before the project started. Recorded as D-02b in 01-CONTEXT.md and accepted as is by the owner on 2026-11-21 (01-UAT.md test 2: pass). No history rewrite. All 64 later commits have the required author and committer."
    accepted_by: "owner (decision D-02b, UAT test 2, 2026-11-21)"
    accepted_at: "2026-11-21T00:00:00Z"
human_verification:
  - test: "Push the branch (8 commits ahead of origin/main, including fix 5109854) and let .github/workflows/ci.yml run on its Temurin 17 runner"
    expected: "All three steps are green on Java 17: ./mvnw -B -ntp verify; ./mvnw -B -ntp -Prelease clean verify -Dgpg.skip -pl morimail -am; ./mvnw -B -ntp -o verify. The step 2 log has no 'no @param' warning for MailMessage.java and no 'Project contains Javadoc Warnings'. 134 library tests and 1 sample test pass."
    why_human: "UAT gap G-01-1: the earlier Temurin 17 run (44910275508) failed step 2 on nine 'no @param' doclint warnings at the MailMessage compact constructor. Plan 01-07 added the nine tags (commit 5109854). This machine has only JDK 21, whose javadoc inherits the record header tags and never produced the warning, so a local green build cannot prove the fix. Nothing has been pushed. Only a Temurin 17 CI run closes G-01-1 and roadmap criterion 1 / REPO-07."
---

# Phase 1: Library Core Verification Report

**Phase Goal:** As a Java developer, I want to build a templated mail and send it through a Mailer account, so that the core contracts are proven before any real transport.
**Verified:** 2026-11-21T14:20:00Z
**Status:** human_needed
**Re-verification:** Yes, after gap-closure plan 01-07 (UAT gap G-01-1) and the code-review fixes (WR-01 to WR-05, IN-02 to IN-07)

The code goal is achieved. A mail built with `Mail.using(id)...template(t, data).attach(p).build()` is sent through `Mailer.of(account).send(mail)` to the `recording` transport, and the error categories and configuration errors behave as specified. The one open item is the Temurin 17 CI run that confirms the G-01-1 fix. It cannot be done here, so it is a human item and not marked passed.

## G-01-1 re-check (gap-closure plan 01-07)

| Check | Result |
|-------|--------|
| Fix commit `5109854` exists, authored and committed by the maintainer, touches one file | Verified. `git show --stat` shows `MailMessage.java` only, 9 insertions, 0 deletions. |
| Nine `@param` tags on the compact constructor (from, to, cc, bcc, replyTo, subject, html, text, attachments), same wording and order as the record header | Verified by reading `MailMessage.java` lines 29-43. `@throws NullPointerException` is kept. Code lines are the same four `List.copyOf` calls. |
| Gate not relaxed | Verified. `pom.xml` still has `<doclint>all</doclint>` and `<failOnWarnings>true</failOnWarnings>` (lines 200-201). `ci.yml` still runs the same three commands on Temurin 17. The only workflow change since the failing run is the earlier WR-04 `timeout-minutes: 15`. |
| Any other documented explicit constructor on a record that JDK 17 doclint could flag | None. `MailMessage` is the only explicit record constructor in `src/main` (grep for `public <Record>` constructors finds only `MailMessage.java:43`). The other records (`MailAddress`, `Attachment`, `MailTemplate`, `Mail`) carry their `@param` tags on the record header and declare no documented constructor. This matches the UAT root-cause note. |
| JDK 17 javadoc run | **Not possible here.** `java -version` reports Temurin 21.0.13.1. The local JDK 21 release build passes, but it passed before the fix too (UAT says so), so it is no evidence for the fix. Routed to human verification. |
| Pushed? | No. `git status -sb` shows `main` ahead of `origin/main` by 8 commits. |

## Goal Achievement

### Observable Truths

| # | Truth (ROADMAP success criterion) | Status | Evidence |
|---|-----------------------------------|--------|----------|
| 1 | `./mvnw verify` passes offline on Java 17; CI runs both builds green on push and PR; Javadoc doclint-clean and states thread safety on every public type | ? UNCERTAIN (human: Temurin 17 CI run) | Verified here on JDK 21 with `--release 17`: `./mvnw -B -ntp -o verify` gives BUILD SUCCESS (134 library tests, 1 sample test, 0 failures); `./mvnw -B -ntp -o -Prelease clean verify -Dgpg.skip -pl morimail -am` gives BUILD SUCCESS and builds the main, sources, javadoc and tests jars with no Javadoc warning. `ci.yml` has the three commands, `push` and `pull_request`, Temurin 17, `permissions: contents: read`, SHA-pinned actions, a 15-minute timeout. `PublicTypesDocumentThreadSafetyTest` passes. NOT verifiable here: the Java 17 doclint rule behind G-01-1 and any CI run. |
| 2 | A mail can be built with `Mail.using(id)...template(t,data).attach(p).build()`. HTML values are escaped, `$$!name$$` is raw, subject line breaks become a space, `$` and `\` are literal | ✓ VERIFIED | Fresh probe (session scratchpad) against the built classes. Data `A<b>&"' $1 \ x<LF>z` gives subject `Hi A<b>&"' $1 \ x z`; HTML `A&lt;b&gt;&amp;&quot;&#39; $1 \ x<LF>z` for `$$name$$` and the raw value for `$$!name$$`; text unchanged. `$$ lone $$ $$x y$$` stays as written. 30 `TemplateRendererTest` tests and `TemplatedSendTest` pass. |
| 3 | `build()` raises `InvalidMailException`, naming the problem, for: missing placeholder field, no recipient, no subject, no body, unreadable attachment, set `contentId`, CR/LF in address, display name or subject, address without exactly one `@` | ✓ VERIFIED | Probe: `Missing data field 'name' for placeholder $$name$$ in the subject template`; `at least one recipient (to, cc or bcc) is required`; `subject is missing`; `html or text body is missing`; `attachments[0]: file x.pdf not found`; `subject contains a line break`; `r@@example.org` gives `exactly one '@'`. CR/LF in an address, display name and subject, and the `contentId` rejection, are asserted in `MailBuilderTest` (48 tests, line-break cases at lines 152-189, 333-353) and `MailModelTest`; `MailAddress.invalidReason` checks `\r` and `\n` explicitly. Messages do not echo the rejected value. |
| 4 | `MailAccount.builder(id).transport("recording")` and `Mailer.of(account).send(mail)` records the message with `defaultFrom` applied. Scripted failures surface as transient, permanent, sender-rejected, auth-required. Unknown account raises `PermanentMailException`. Unknown transport ID fails and lists the IDs. Duplicate external ID fails when the registry loads | ✓ VERIFIED | Probe: `Unknown account 'nope'. Known accounts: acc` as `PermanentMailException`; `Unknown transport 'zzz'. Available: recording` as `IllegalArgumentException`. A `null` account ID on a hand-built `Mail` now also gives `PermanentMailException` (review finding WR-03 is fixed). Default sender, the four scripted failure types, duplicate IDs and the registry are covered by `SendThroughRecordingTest`, `MailerTest` (12), `RecordingTransportTest` (7), `TransportRegistryTest` (11, incl. duplicate built-in and external IDs). `MailerConcurrencyTest` passes. |
| 5 | The repository is safe to publish from its first commit: LICENSE, README stub, SECURITY.md, CLAUDE.md, docs/spec.md in place; no secret or real address in files; import-rule test fails the build if the core imports anything but the JDK | ✓ VERIFIED (override: root commit identity) | `LICENSE`, `README.md`, `SECURITY.md`, `CLAUDE.md`, `docs/spec.md`, `docs/architecture.md` exist; no `spec.md` at the root. A scan of tracked non-planning files finds no email other than `example.org`/`example.com` (the hits are upper-case `Example.org` test inputs) and the maintainer address; no GUID, no `.p12`. No `TBD`/`FIXME`/`XXX`/`TODO` in `src`. No `java.net.` use in `src/main`. `ImportRulesTest` passes. Root commit `8c9b082` is the only commit with a different identity; see Overrides. |

**Score:** 4/5 truths verified (1 awaits a Temurin 17 CI run, 0 present-but-behavior-unverified)

### Overrides

Root commit `8c9b082` carries a different author address and GitHub as committer, which does not meet the letter of REPO-11. Owner decision D-02b left it as is, and the owner accepted it on 2026-11-21 (`01-UAT.md` test 2: pass). Accepted as an override. `git tag --list` is empty. Every other commit (64) has author and committer Stephan Wald <stephan@morisoft.de>. The only other address in history is the Claude co-author trailer address.

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `pom.xml` | Parent `de.morisoft:morimail-parent` 0.1.0-SNAPSHOT, Central metadata, pinned plugins, release profile | ✓ VERIFIED | Release profile javadoc execution has `doclint all` and `failOnWarnings true` |
| `morimail/pom.xml` | Jackson-only runtime, `Automatic-Module-Name` | ✓ VERIFIED | `dependency:tree`: compile scope has `jackson-databind` 2.23.0, core 2.23.0, annotations 2.23 only. Jar manifest: `Automatic-Module-Name: de.morisoft.morimail`, `Java-Version: 17`. No `module-info.java`. |
| `morimail-sample-serial/pom.xml` | Unpublished, consumes the test-jar | ✓ VERIFIED | `RecordingTestJarSmokeTest` passes (finds `recording` via `ServiceLoader`) |
| `mvnw`, `.mvn/wrapper/maven-wrapper.properties`, `.gitattributes` | Script-only wrapper pinned to Maven 3.10.1 | ✓ VERIFIED | `distributionType=only-script`, 3.10.1, checksum pinned (IN-07); `mvnw` is mode 100755 |
| `.github/workflows/ci.yml` | Three Maven steps on Java 17, no secrets | ✓ VERIFIED (file) / confirmation pending | See truth 1 and the G-01-1 table |
| `LICENSE`, `README.md`, `SECURITY.md`, `CLAUDE.md`, `docs/spec.md`, `docs/architecture.md` | Publish-safe repository files | ✓ VERIFIED | Present, no foreign addresses |
| `MailAddress`, `Attachment`, `MailMessage`, `Mail`, `MailTemplate` | Immutable records, defensive copies | ✓ VERIFIED | `MailMessage` copies the four lists with `List.copyOf`; no Jackson in the core |
| `Mail.Builder`, `TemplateRenderer` | Fluent builder, validating `build()` | ✓ VERIFIED | Behaviour confirmed by probe and tests |
| `MailTransport`, `TransportProvider`, `TokenProvider` | SPI with nothing Graph-specific | ✓ VERIFIED | `TokenProviderTest` pins the `freshAccessToken` default |
| `MailException` tree | Checked hierarchy | ✓ VERIFIED | `ErrorHierarchyTest` (6) |
| `TransportRegistry`, `BuiltInTransports`, `MailAccount`, `Mailer` | Registry and account contracts | ✓ VERIFIED | `BuiltInTransports.providers()` is an empty list by plan; the Graph entry arrives in Phase 3 |
| `RecordingTransport`, `RecordingTransportProvider` | In test sources and the test-jar, ID `recording` | ✓ VERIFIED | Present in `morimail-0.1.0-SNAPSHOT-tests.jar` with the services file |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `MailAccount.Builder.build()` | `TransportRegistry.load().find(id)` then `provider.create(options, auth)` | direct call | WIRED | Unknown transport probe lists the available IDs |
| `TransportRegistry.load()` | `BuiltInTransports.providers()` and `ServiceLoader` | `of(...)` | WIRED | Registry tests pass |
| `Mail.Builder.build()` | `TemplateRenderer.render(...)` | direct call | WIRED | Missing-field probe |
| `Mailer.send/validate` | `defaultFrom`, `transport.validate`, `transport.send` | `prepare()` | WIRED | Probe and tests |
| `recording` provider | sample module | test-jar plus `META-INF/services` | WIRED | `RecordingTestJarSmokeTest` |
| `MailMessage` compact constructor Javadoc | release-profile javadoc gate | doclint all, failOnWarnings | WIRED (JDK 21) / unconfirmed on JDK 17 | Gate intact; tags present; JDK 17 result pending |
| Core package | `graph` / `entra` | none | NOT PRESENT by design | `ImportRulesTest` |

### Data-Flow Trace (Level 4)

Not applicable. There is no UI or rendering layer. The one data path is template data through `TemplateRenderer` into `MailMessage` into `RecordingTransport.sent()`, and the probe and `TemplatedSendTest` show real values arrive.

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Full offline build | `./mvnw -B -ntp -o verify` | BUILD SUCCESS, 134 + 1 tests, 0 failures | PASS |
| Release build, Javadoc, sources jar | `./mvnw -B -ntp -o -Prelease clean verify -Dgpg.skip -pl morimail -am` | BUILD SUCCESS, four jars, no Javadoc warning (JDK 21 only) | PASS (JDK 21) |
| Template, validation, account, failure behaviours | Own probe run against `target/classes` and `target/test-classes` | Outputs as listed under truths 2 to 4 | PASS |
| Runtime dependencies | `./mvnw -o dependency:tree -pl morimail` | Jackson only at compile scope | PASS |
| Jar manifest | `unzip -p ... MANIFEST.MF` | `Automatic-Module-Name` and `Java-Version: 17` present | PASS |

### Probe Execution

SKIPPED: no probe scripts are declared by the plans and `scripts/` does not exist.

### Requirements Coverage

The `requirements:` fields of plans 01-01 to 01-07 cover all 29 IDs the roadmap assigns to Phase 1 (01-07 adds REPO-07, REPO-09, REPO-10). All 29 are marked Complete in REQUIREMENTS.md; none is missing and none is orphaned. `REPO-08` is mapped to Phase 3 and is correctly not claimed.

| Requirement | Source Plan | Status | Evidence |
|-------------|-------------|--------|----------|
| REPO-01 | 01-01 | ✓ SATISFIED | Three-module reactor at 0.1.0-SNAPSHOT; `Java-Version: 17` in the manifest |
| REPO-02 | 01-01 | ✓ SATISFIED | Script-only wrapper, Maven 3.10.1 |
| REPO-03 | 01-01 | ✓ SATISFIED | Central metadata in `pom.xml` |
| REPO-04 | 01-01 | ✓ SATISFIED | `dependency:tree` |
| REPO-05 | 01-01 | ✓ SATISFIED | Manifest; no `module-info.java` |
| REPO-06 | 01-02 | ✓ SATISFIED | Files present |
| REPO-07 | 01-01, 01-07 | ? NEEDS HUMAN | Workflow file correct; the Temurin 17 run after the G-01-1 fix has not happened |
| REPO-09 | 01-01, 01-03, 01-04, 01-07 | ✓ SATISFIED on JDK 21 / JDK 17 pending | Release build passes locally; gate unchanged |
| REPO-10 | 01-03, 01-04, 01-07 | ✓ SATISFIED on JDK 21 / JDK 17 pending | Thread-safety test passes; the doclint half is confirmed only on JDK 21 |
| REPO-11 | 01-01 to 01-04 | ✓ SATISFIED (override) | See Overrides; hygiene scans clean |
| REPO-12 | 01-04 | ✓ SATISFIED | `ImportRulesTest` |
| MODEL-01 to MODEL-05 | 01-03, 01-05 | ✓ SATISFIED | Probe, `MailModelTest`, `MailBuilderTest` |
| TMPL-01 to TMPL-04 | 01-05 | ✓ SATISFIED | Probe, `TemplateRendererTest` |
| CORE-01 to CORE-07 | 01-03, 01-04, 01-06 | ✓ SATISFIED | Probe, registry/mailer/account tests, concurrency test, test-jar listing |
| ERR-01, ERR-02 | 01-02, 01-03, 01-06 | ✓ SATISFIED | `ErrorHierarchyTest`; at-least-once text in `send` Javadoc and README |

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| (all `src`) | - | `TBD`/`FIXME`/`XXX`/`TODO` | none found | Clean |
| `BuiltInTransports.java` | 24 | `return List.of()` | Info | Intentional: the Graph entry arrives in Phase 3 |
| `morimail-sample-serial` | - | "JAR will be empty" warning | Info | Intentional: the CLI is Phase 2 |

### Code Review Status

`01-REVIEW-DISPOSITION.md` records 12 findings: 11 fixed, 1 skipped (IN-01, the root commit identity, accepted by the owner), 0 open. The incremental review after plan 01-07 (`01-REVIEW.md`) is clean: Javadoc-only change, no findings. The earlier review warnings that touched must-haves (WR-01 address validation, WR-03 null account ID) are fixed and the fixes show in the probe (`Unknown account 'null'` as `PermanentMailException`) and in the 48 builder tests.

### Human Verification Required

#### 1. Temurin 17 CI run after the G-01-1 fix

**Test:** Push `main` (8 commits ahead of `origin/main`, including `5109854`) or open a PR, then read the run of `.github/workflows/ci.yml`.
**Expected:** All three Maven steps are green on Temurin 17. In step 2 (`-Prelease clean verify -Dgpg.skip -pl morimail -am`) there is no "no @param" warning for `MailMessage.java` and no "Project contains Javadoc Warnings". Step 3 (`-o verify`) runs and passes. 134 library tests and 1 sample test pass.
**Why human:** G-01-1 was found by a Temurin 17 run that failed in step 2. JDK 21 javadoc does not report the warning, so the local green build is the same result as before the fix and proves nothing about it. Only a JDK 17 javadoc run can. If the run is green, record UAT test 1 as passed (re-run verify-work); if it fails, the gap stays open.

### Gaps Summary

No code gaps. Every must-have that can be checked on JDK 21 holds, and the SUMMARY claims match the repository: the fix commit contains exactly the nine `@param` lines, the gate is unchanged, the build and tests are green, and the behaviours match the spec when exercised directly. The phase is `human_needed` only because G-01-1 can be closed solely by a Temurin 17 CI run, which has not happened because nothing has been pushed.

---

_Verified: 2026-11-21T14:20:00Z_
_Verifier: Claude (gsd-verifier)_
