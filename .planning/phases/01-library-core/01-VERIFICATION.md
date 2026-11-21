---
phase: 01-library-core
verified: 2026-11-21T13:45:00Z
status: human_needed
score: 4/5 must-haves verified
covered_files: [".github/workflows/ci.yml", ".mvn/wrapper/maven-wrapper.properties", ".planning/phases/01-library-core/01-01-PLAN.md", ".planning/phases/01-library-core/01-01-SUMMARY.md", ".planning/phases/01-library-core/01-02-PLAN.md", ".planning/phases/01-library-core/01-02-SUMMARY.md", ".planning/phases/01-library-core/01-03-PLAN.md", ".planning/phases/01-library-core/01-03-SUMMARY.md", ".planning/phases/01-library-core/01-04-PLAN.md", ".planning/phases/01-library-core/01-04-SUMMARY.md", ".planning/phases/01-library-core/01-05-PLAN.md", ".planning/phases/01-library-core/01-05-SUMMARY.md", ".planning/phases/01-library-core/01-06-PLAN.md", ".planning/phases/01-library-core/01-06-SUMMARY.md", "README.md", "docs/architecture.md", "morimail-sample-serial/pom.xml", "morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/RecordingTestJarSmokeTest.java", "morimail/pom.xml", "morimail/src/main/java/de/morisoft/morimail/Attachment.java", "morimail/src/main/java/de/morisoft/morimail/AuthRequiredException.java", "morimail/src/main/java/de/morisoft/morimail/BuiltInTransports.java", "morimail/src/main/java/de/morisoft/morimail/InvalidMailException.java", "morimail/src/main/java/de/morisoft/morimail/Mail.java", "morimail/src/main/java/de/morisoft/morimail/MailAccount.java", "morimail/src/main/java/de/morisoft/morimail/MailAddress.java", "morimail/src/main/java/de/morisoft/morimail/MailException.java", "morimail/src/main/java/de/morisoft/morimail/MailMessage.java", "morimail/src/main/java/de/morisoft/morimail/MailTemplate.java", "morimail/src/main/java/de/morisoft/morimail/MailTransport.java", "morimail/src/main/java/de/morisoft/morimail/Mailer.java", "morimail/src/main/java/de/morisoft/morimail/PermanentMailException.java", "morimail/src/main/java/de/morisoft/morimail/SenderRejectedException.java", "morimail/src/main/java/de/morisoft/morimail/TemplateRenderer.java", "morimail/src/main/java/de/morisoft/morimail/TokenProvider.java", "morimail/src/main/java/de/morisoft/morimail/TransientMailException.java", "morimail/src/main/java/de/morisoft/morimail/TransportProvider.java", "morimail/src/main/java/de/morisoft/morimail/TransportRegistry.java", "morimail/src/test/java/de/morisoft/morimail/ErrorHierarchyTest.java", "morimail/src/test/java/de/morisoft/morimail/ImportRulesTest.java", "morimail/src/test/java/de/morisoft/morimail/MailAccountTest.java", "morimail/src/test/java/de/morisoft/morimail/MailBuilderTest.java", "morimail/src/test/java/de/morisoft/morimail/MailModelTest.java", "morimail/src/test/java/de/morisoft/morimail/MailerConcurrencyTest.java", "morimail/src/test/java/de/morisoft/morimail/MailerTest.java", "morimail/src/test/java/de/morisoft/morimail/PublicTypesDocumentThreadSafetyTest.java", "morimail/src/test/java/de/morisoft/morimail/RecordingTransportTest.java", "morimail/src/test/java/de/morisoft/morimail/SendThroughRecordingTest.java", "morimail/src/test/java/de/morisoft/morimail/TemplateRendererTest.java", "morimail/src/test/java/de/morisoft/morimail/TemplatedSendTest.java", "morimail/src/test/java/de/morisoft/morimail/TokenProviderTest.java", "morimail/src/test/java/de/morisoft/morimail/TransportRegistryTest.java", "morimail/src/test/java/de/morisoft/morimail/test/RecordingTransport.java", "morimail/src/test/java/de/morisoft/morimail/test/RecordingTransportProvider.java", "morimail/src/test/resources/META-INF/services/de.morisoft.morimail.TransportProvider", "pom.xml"]
covered_digest: "v4:sha256:5cb578a75def8c02e3f7f5cbce0394ba55c12765dfaa1759e3c3c5d42c6f59f2"
behavior_unverified: 0
overrides_applied: 1
overrides:
  - must_have: "Every commit has author and committer Stephan Wald <stephan@morisoft.de> (REPO-11); the repository is safe to publish from its first commit (ROADMAP SC5)"
    reason: "Root commit 8c9b082 was created by GitHub (author swald@basis.cloud, committer GitHub noreply) before the project started. Recorded as D-02b in 01-CONTEXT.md: left as is, no history rewrite; the owner decides before publishing. All 40 later commits have the required identity."
    accepted_by: "owner (decision D-02b, 2026-11-21)"
    accepted_at: "2026-11-21T00:00:00Z"
human_verification:
  - test: "Push the branch (or open a PR) and let .github/workflows/ci.yml run on its Java 17 (Temurin) runner"
    expected: "All three steps are green on Java 17: ./mvnw -B -ntp verify; ./mvnw -B -ntp -Prelease clean verify -Dgpg.skip -pl morimail -am; ./mvnw -B -ntp -o verify. The 129 library tests and 1 sample test pass, and the Javadoc jar builds with no warnings."
    why_human: "Only JDK 21 is installed here. The build ran on JDK 21 with --release 17, which guarantees the API surface but not Java 17 runtime behaviour. Roadmap success criterion 1 says 'on Java 17' and 'CI workflow ... green on push and pull request', so a CI run is the evidence. The workflow file is present and correct but has never run."
  - test: "Decide what to do with root commit 8c9b082 (author swald@basis.cloud) before the repository is published"
    expected: "Either accept it, or rewrite the root commit author while the repository has no other consumers. Nothing in the code depends on the answer."
    why_human: "Owner decision D-02b already defers it to the owner. It is listed so it is not lost: a different real address sits in the permanent public history, which REPO-11 says must not happen."
---

# Phase 1: Library Core Verification Report

**Phase Goal:** As a Java developer, I want to build a templated mail and send it through a Mailer account, so that the core contracts are proven before any real transport.
**Verified:** 2026-11-21T13:45:00Z
**Status:** human_needed
**Re-verification:** No, initial verification

The code goal is achieved. A mail built with `Mail.using(id)...template(t, data).attach(p).build()` is sent through `Mailer.of(account).send(mail)` to the `recording` transport, with every failure category and configuration error behaving as specified. The only open items are the Java 17 / CI run (cannot be done here) and an owner decision about the GitHub-created root commit. Neither is a code gap.

## Goal Achievement

### Observable Truths

| # | Truth (ROADMAP success criterion) | Status | Evidence |
|---|-----------------------------------|--------|----------|
| 1 | `./mvnw verify` passes offline on Java 17; CI runs both builds green on push and PR; Javadoc doclint-clean and states thread safety on every public type | ? UNCERTAIN (human: Java 17 and CI run) | Verified here: `./mvnw -B -ntp -o verify` gives BUILD SUCCESS (129 library tests, 1 sample test, 0 failures). `./mvnw -B -ntp -o -Prelease clean verify -Dgpg.skip -pl morimail -am` gives BUILD SUCCESS and produces main, sources, javadoc and tests jars with no warnings. `ci.yml` runs the three commands on Temurin 17 for push and pull_request with `permissions: contents: read` and SHA-pinned actions. `PublicTypesDocumentThreadSafetyTest` passes. Not verifiable here: any run on a Java 17 runtime, and any actual CI run. |
| 2 | A mail can be built with `Mail.using(id)...template(t,data).attach(p).build()`. HTML values are escaped, `$$!name$$` is raw, subject line breaks become a space, `$` and `\` are literal | ✓ VERIFIED | Ran my own probe against the built classes. Data `A<b>&"' $1 \ x\nz` gives subject `Hi A<b>&"' $1 \ x z`; HTML `A&lt;b&gt;&amp;&quot;&#39; $1 \ x\nz` for `$$name$$` and the raw value for `$$!name$$`; text unchanged; a lone `$$ lone $$` and `$$x y$$` stay as written. `TemplateRenderer` uses a `Matcher.find()` loop and never passes data to `appendReplacement`/`replaceAll`. 30 renderer tests and `TemplatedSendTest` pass. |
| 3 | `build()` raises `InvalidMailException` naming the problem for: missing placeholder field, no recipient, no subject, no body, unreadable attachment, set `contentId`, CR/LF in address, display name or subject, address without exactly one `@` | ✓ VERIFIED | Probe output for each case: `Missing data field 'name' for placeholder $$name$$ in the subject template`; `at least one recipient ... is required`; `subject is missing`; `html or text body is missing`; `attachments[0]: file x.pdf not found`; `contentId is not supported`; `display name contains a line break`; `subject contains a line break`; `exactly one '@'` for `r@@example.org` and `rexample.org`. CR/LF inside an address is covered by the check order in `MailAddress.invalidReason` and by `MailBuilderTest` (45 tests). Messages never echo the rejected value. |
| 4 | `MailAccount.builder(id).transport("recording")` and `Mailer.of(account).send(mail)` records the message with `defaultFrom` applied. Scripted failures surface as transient, permanent, sender-rejected, auth-required. Unknown account raises `PermanentMailException`. Unknown transport ID fails and lists the IDs. Duplicate external ID fails when the registry loads | ✓ VERIFIED | Probe: sent message `from=from@example.org` from the account default; `failNext` of the four types surfaced as `TransientMailException`, `SenderRejectedException`, `AuthRequiredException`, `PermanentMailException`; `Unknown account 'nope'. Known accounts: acc` as `PermanentMailException`; `Unknown transport 'zzz'. Available: recording` as `IllegalArgumentException`. `TransportRegistryTest.duplicateBuiltInAndExternalIdIsRejected` and `duplicateExternalIdsAreRejected` pass; `TransportRegistry.register` throws `IllegalStateException` naming both classes. `MailerConcurrencyTest` (8 threads, 400 mails, exact count and set) passes, which is the behavioural proof for the concurrency claim. |
| 5 | The repository is safe to publish from its first commit: LICENSE, README stub (pre-1.0, coordinates, accepted-not-delivered note), SECURITY.md, CLAUDE.md, docs/spec.md in place; no secret or real address in files; import-rule test fails the build if the core imports anything but the JDK | ✓ VERIFIED (override: root commit identity) | All five files exist and match content requirements; `docs/spec.md` is the only spec (none at root). `git grep` over tracked non-planning files finds no email other than `example.org`/`example.com`/`stephan@morisoft.de`, no GUIDs, no workstation paths, no `.p12`. `ImportRulesTest` parses core sources (imports, qualified names, graph/entra mentions) and passes. No `java.net.` use in `src/main`. Root commit `8c9b082` has a different author address; see Overrides below. |

**Score:** 4/5 truths verified (1 needs a Java 17 / CI run, 0 present-but-behavior-unverified)

### Overrides

Root commit `8c9b082` carries author `swald@basis.cloud` and committer GitHub, which does not meet the letter of REPO-11 ("every commit ... Stephan Wald <stephan@morisoft.de>"). The owner's decision D-02b in `01-CONTEXT.md` explicitly leaves it and defers the call to before publishing. Accepted as an override; also kept as a human item so the decision is made before release. The other 40 commits all have author and committer `Stephan Wald <stephan@morisoft.de>`; `git tag --list` is empty.

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `pom.xml` | Parent `de.morisoft:morimail-parent` 0.1.0-SNAPSHOT, Central metadata, pinned plugins, release and live profiles | ✓ VERIFIED | MIT, URL, developer, SCM present; every plugin pinned in `pluginManagement`; release profile has source, javadoc (`doclint all`, `failOnWarnings true`), gpg `sign` in verify, central-publishing with `autoPublish false` |
| `morimail/pom.xml` | Jackson-only runtime, `Automatic-Module-Name`, test-jar not attached under release | ✓ VERIFIED | `dependency:tree` shows only `jackson-databind` 2.23.0 + core 2.23.0 + annotations 2.23 at compile scope; manifest of built jar has `Automatic-Module-Name: de.morisoft.morimail`; no `module-info.java` anywhere; test-jar contains `RecordingTransport`, its provider and the `META-INF/services` entry |
| `morimail-sample-serial/pom.xml` | Not published; consumes the test-jar | ✓ VERIFIED | skip properties set; `RecordingTestJarSmokeTest` finds `recording` through `ServiceLoader` from the test-jar |
| `mvnw`, `.mvn/wrapper/maven-wrapper.properties`, `.gitattributes` | Script-only wrapper pinned to Maven 3.10.1, exec bit, LF | ✓ VERIFIED | `distributionType=only-script`, 3.10.1; `mvnw` mode 100755; `.gitattributes` has `mvnw text eol=lf`. The build ran through the wrapper. |
| `.github/workflows/ci.yml` | Three Maven steps on Java 17, no secrets, read-only | ✓ VERIFIED (file) / not run | See truth 1 |
| `LICENSE`, `README.md`, `SECURITY.md`, `CLAUDE.md`, `docs/spec.md`, `docs/architecture.md` | Publish-safe repository files | ✓ VERIFIED | README has pre-1.0 status, coordinates, accepted-not-delivered and at-least-once text; architecture.md records the five deviations (freshAccessToken, `Map<String,?>`, unchecked registry errors, JUnit 6, reactor-only test-jar) |
| Core model: `MailAddress`, `Attachment`, `MailMessage`, `Mail`, `MailTemplate` | Immutable records, defensive list copies, no serialization code | ✓ VERIFIED | `MailMessage` compact constructor uses `List.copyOf`; no Jackson import in the core sources |
| `Mail.Builder`, `TemplateRenderer` | Fluent builder, validating `build()`, renderer | ✓ VERIFIED | Read in full; behaviour confirmed by probe |
| `MailTransport`, `TransportProvider`, `TokenProvider` | SPI with nothing Graph-specific; `freshAccessToken` default | ✓ VERIFIED | `TokenProvider.freshAccessToken` delegates to `accessToken`; `TokenProviderTest` pins it |
| `MailException` tree | Checked hierarchy with `retryAfter` | ✓ VERIFIED | Hierarchy matches spec 5.2; `TransientMailException.retryAfter()` returns `Optional<Duration>` |
| `TransportRegistry`, `BuiltInTransports`, `MailAccount`, `Mailer` | Registry from built-in list plus `ServiceLoader`; account and mailer contracts | ✓ VERIFIED | `BuiltInTransports.providers()` is an empty list by plan (Graph line comes in Phase 3) |
| `RecordingTransport`, `RecordingTransportProvider` | In test sources, in the test-jar, `recording` ID, per-instance storage, scripted failures | ✓ VERIFIED | Present in `morimail-0.1.0-SNAPSHOT-tests.jar` with the services file |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `MailAccount.Builder.build()` | `TransportRegistry.load().find(id)` then `provider.create(options, auth)` | direct call | WIRED | Options passed as `Map.copyOf`, unread by the core |
| `TransportRegistry.load()` | `BuiltInTransports.providers()` and `ServiceLoader.load(TransportProvider.class)` | `of(...)` | WIRED | `loadFindsRecordingThroughServiceLoader` passes |
| `Mail.Builder.build()` | `TemplateRenderer.render(...)` | direct call | WIRED | Rendering happens before the recipient, subject and body checks |
| `Mailer.send/validate` | `account.defaultFrom()` then `transport.validate` then `transport.send` | `prepare()` | WIRED | `appliesDefaultFromWhenMailHasNoFrom` and the probe confirm it |
| `recording` provider | sample module | test-jar plus `META-INF/services` | WIRED | `RecordingTestJarSmokeTest` passes |
| Core package | graph / entra packages | none | NOT PRESENT by design | `ImportRulesTest` fails the build if a reference appears |

### Data-Flow Trace (Level 4)

Not applicable. This phase has no UI or rendering layer. The one data path is template data through `TemplateRenderer` into `MailMessage` into `RecordingTransport.sent()`, and the probe and `TemplatedSendTest` confirm real values arrive.

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Full offline build | `./mvnw -B -ntp -o verify` | BUILD SUCCESS, 129 + 1 tests, 0 failures | PASS |
| Release build, Javadoc, sources jar | `./mvnw -B -ntp -o -Prelease clean verify -Dgpg.skip -pl morimail -am` | BUILD SUCCESS, four jars built, no warnings | PASS |
| Template, validation, account, failure behaviours | Own probe (`Probe.java` in the session scratchpad) run against `target/classes` and `target/test-classes` | All outputs as listed under truths 2 to 4 | PASS |
| Runtime dependencies | `./mvnw -o dependency:tree -pl morimail` | Jackson only at compile scope | PASS |
| Concurrency | `MailerConcurrencyTest` (8 threads, 400 mails) | passes | PASS |

### Probe Execution

SKIPPED: no probe scripts are declared by the plans and `scripts/` does not exist.

### Requirements Coverage

Union of the `requirements:` fields of the six plans covers all 29 IDs the roadmap assigns to Phase 1. No ID is missing and none is orphaned. `REPO-08` is mapped to Phase 3 in REQUIREMENTS.md and is correctly not claimed.

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| REPO-01 | 01-01 | Reactor, versions, Java 17 via `maven.compiler.release` | ✓ SATISFIED | Parent, library and sample modules at 0.1.0-SNAPSHOT; `maven.compiler.release=17`; jar manifest `Java-Version: 17` |
| REPO-02 | 01-01 | Script-only wrapper pinned to Maven 3.x | ✓ SATISFIED | Properties file |
| REPO-03 | 01-01 | Central metadata | ✓ SATISFIED | `pom.xml` |
| REPO-04 | 01-01 | Jackson-only runtime, JUnit 6, AssertJ, WireMock managed | ✓ SATISFIED | `dependency:tree`; WireMock and picocli managed, not yet declared (per plan) |
| REPO-05 | 01-01 | `Automatic-Module-Name`, no `module-info` | ✓ SATISFIED | Manifest read from the built jar |
| REPO-06 | 01-02 | LICENSE, README stub, SECURITY, CLAUDE.md, `docs/spec.md` | ✓ SATISFIED | Files read |
| REPO-07 | 01-01 | CI workflow | ? NEEDS HUMAN (file ✓, never run) | See human item 1 |
| REPO-09 | 01-01, 01-03, 01-04 | Release profile, test-jar `attach=false` | ✓ SATISFIED | Release build ran; sample skip properties present. `-Prelease` on the full reactor is deliberately scoped to `-pl morimail -am` (D-20, recorded in architecture.md) |
| REPO-10 | 01-03, 01-04 | Javadoc with thread safety, doclint clean | ✓ SATISFIED | Release build with `failOnWarnings`; `PublicTypesDocumentThreadSafetyTest` |
| REPO-11 | 01-01 to 01-04 | Public-repo hygiene, commit identity | ✓ SATISFIED (override) | See Overrides. Hygiene greps clean. |
| REPO-12 | 01-04 | Import-rule test | ✓ SATISFIED | `ImportRulesTest` |
| MODEL-01 | 01-03, 01-05 | Immutable records, defensive copies, no serialization | ✓ SATISFIED | `MailMessage` compact constructor; `MailModelTest` |
| MODEL-02 | 01-05 | Fluent builder | ✓ SATISFIED | `Mail.Builder` has to/cc/bcc/replyTo/from/subject/html/text/template/attach |
| MODEL-03 | 01-05 | `build()` rejections | ✓ SATISFIED | Probe |
| MODEL-04 | 01-03, 01-05 | CR/LF rejection, exactly one `@` | ✓ SATISFIED | Probe and `MailBuilderTest` (see WR-01 for scope beyond the requirement) |
| MODEL-05 | 01-05 | Content type default `application/octet-stream` | ✓ SATISFIED | Probe: `.bin` file gives `application/octet-stream`; twelve-extension table first |
| TMPL-01 | 01-05 | Placeholder grammar, `String.valueOf` | ✓ SATISFIED | Probe |
| TMPL-02 | 01-05 | HTML escaping, `$$!name$$`, literal `$` and `\` | ✓ SATISFIED | Probe |
| TMPL-03 | 01-05 | Subject and text insertion | ✓ SATISFIED | Probe |
| TMPL-04 | 01-05 | Missing field names part and placeholder | ✓ SATISFIED | Probe |
| CORE-01 | 01-03, 01-04, 01-06 | SPI without Graph-specific content | ✓ SATISFIED | Interfaces read |
| CORE-02 | 01-02, 01-03, 01-06 | `freshAccessToken` default | ✓ SATISFIED | `TokenProvider`; deviation recorded |
| CORE-03 | 01-04, 01-06 | Registry, duplicate ID fails | ✓ SATISFIED | `TransportRegistry`, 11 tests |
| CORE-04 | 01-04, 01-06 | `MailAccount.builder`, unknown ID lists IDs | ✓ SATISFIED | Probe |
| CORE-05 | 01-04, 01-06 | `Mailer.of`, `validate`, `send`, unknown account | ✓ SATISFIED | Probe; see WR-03 for a null-ID edge |
| CORE-06 | 01-06 | Concurrent use and Javadoc statement | ✓ SATISFIED | `MailerConcurrencyTest` passes; Javadoc states it |
| CORE-07 | 01-04, 01-06 | `RecordingTransport` in test-jar, `recording` ID, scripted failures | ✓ SATISFIED | Jar listing and `RecordingTransportTest` |
| ERR-01 | 01-03, 01-06 | Checked hierarchy | ✓ SATISFIED | `ErrorHierarchyTest` |
| ERR-02 | 01-02, 01-03 | At-least-once note in `send` Javadoc and README | ✓ SATISFIED | Both texts present |

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| (core sources) | - | `TBD`/`FIXME`/`XXX`/`TODO` markers | none found | `grep` over `src/main` and `src/test` is clean |
| `BuiltInTransports.java` | 24 | `return List.of()` | Info | Intentional by plan: Graph line arrives in Phase 3; the registry is exercised through the external `recording` provider |
| `morimail-sample-serial` | - | no main sources, "JAR will be empty" warning | Info | Intentional: the CLI is Phase 2 |

### Code Review Findings Against the Must-Haves

`01-REVIEW.md` has 5 warnings, 0 blockers. I checked each against the requirements and roadmap criteria. None defeats a must-have.

| Finding | Effect on must-haves |
|---------|---------------------|
| WR-01 address validation is loose (accepts `a,b@example.org`, spaces and so on) | Does not defeat MODEL-04 or SC3, which ask for CR/LF rejection and exactly one `@`. It is a hardening gap that matters once an SMTP or SES transport exists. Not a Phase 1 gap. |
| WR-02 explicit `Attachment` filename and content type unchecked | Not required by MODEL-03 or the spec. Hardening item. |
| WR-03 `Mailer.send(new Mail(null, ...))` throws `NullPointerException` (confirmed by my probe) | CORE-05 and SC4 ask for `PermanentMailException` for an unknown account ID, and that works for every non-null ID. A null ID on a hand-built record is an edge outside the stated criterion. Worth fixing before a queue consumer relies on it. |
| WR-04 CI has no `timeout-minutes` | Does not affect REPO-07. |
| WR-05 test name overstates a contract | Test naming only. |

The disposition file lists all 12 findings as `open`. The owner should triage them before Phase 2 depends on the address and attachment behaviour (the sample's pre-flight needs a stricter email check, as WR-01 notes).

### Human Verification Required

#### 1. CI run on Java 17

**Test:** Push the branch or open a PR and let `.github/workflows/ci.yml` run.
**Expected:** All three Maven steps are green on Temurin 17. Library tests (129) and the sample test (1) pass; the Javadoc jar builds without warnings.
**Why human:** Only JDK 21 is installed in this environment. `--release 17` limits the API surface but cannot prove Java 17 runtime behaviour, and the workflow has never run. Roadmap criterion 1 and REPO-07 name Java 17 and CI explicitly.

#### 2. Root commit identity decision

**Test:** Decide whether to keep or rewrite root commit `8c9b082` before publishing.
**Expected:** A recorded decision. Nothing in the code depends on it.
**Why human:** Owner decision D-02b assigns this call to the owner. It is a history rewrite, which this verification must not do.

### Gaps Summary

No code gaps. Every must-have that can be checked offline on JDK 21 holds, and the claims in the SUMMARY files match what is in the repository: the build is green, the behaviours match the spec when exercised directly, the dependency and import rules are enforced by tests, and the release build produces clean Javadoc. The phase stays at `human_needed` only because the Java 17 and CI portion of criterion 1 cannot be observed here, and because of the owner-deferred root commit question.

---

_Verified: 2026-11-21T13:45:00Z_
_Verifier: Claude (gsd-verifier)_
