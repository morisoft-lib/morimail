# Walking Skeleton — morimail

**Phase:** 1
**Generated:** 2026-11-21
**Built by:** three plans in sequence, each led by a tracer task and each committed green: 01-01 (reactor, wrapper, `release` profile, CI), 01-03 (core records, error tree, SPI interfaces) and 01-04 (registry, `MailAccount`, `Mailer`, the `recording` test-jar fixture, the sample consumer, the import-rule and thread-safety guards). The end-to-end acceptance gate is `SendThroughRecordingTest` plus `RecordingTestJarSmokeTest` in 01-04; the expansion plans 01-05 and 01-06 start after it passes. (Split from one 33-file plan so each plan stays under 15 files.)

## Capability Proven End-to-End

A Java developer builds a plain-text mail with `Mail.using("main").to("reader@example.org").subject("Hello").text("...").build()`, sends it with `Mailer.of(account).send(mail)` through `MailAccount.builder("main").transport("recording").defaultFrom("newsletter@example.com").build()`, and reads it back, with the default sender applied, from `((RecordingTransport) account.transport()).sent()`. The `recording` transport is found by `ServiceLoader` in the library's test-jar, both in the library's own tests and in the sample module. `./mvnw -B -ntp verify`, `./mvnw -B -ntp -Prelease clean verify -Dgpg.skip -pl morimail -am` and `./mvnw -B -ntp -o verify` are green.

morimail is a library, not an application: there is no database, no UI and no deployment target. The rows below say so instead of inventing them.

## Architectural Decisions

| Decision | Choice | Rationale |
|---|---|---|
| Framework | None. Plain Java 17 (`maven.compiler.release=17`): records, final classes with static factories and builders, `java.util.ServiceLoader` | Spec Rule 0: no frameworks, DI, reflection or annotation processing |
| Build | Maven reactor `de.morisoft:morimail-parent` with modules `morimail` (library) and `morimail-sample-serial` (sample, never published); version `0.1.0-SNAPSHOT`; script-only wrapper pinned to Maven 3.10.1; every plugin and dependency version pinned once in the parent | Spec §3, §13.2; Maven 4 breaks Central publishing (STACK.md); unpinned defaults break the offline build |
| Runtime dependencies | `jackson-databind` only (via `jackson-bom` 2.23.0), unused by the core until Phase 3 | Spec §2 dependency allowlist; owner decision for Jackson 2 |
| Data layer | None. The library holds nothing persistent; messages live in memory. The Phase 2 sample keeps its own JSON-lines journal | A mail library sends one mail at a time (spec §1) |
| Auth | The `TokenProvider` SPI (`accessToken`, default `freshAccessToken`), optional per transport (null allowed). No credential handling in Phase 1; Entra client credentials arrive in Phase 3 | Spec §2, §7; CORE-02 owner decision |
| Extension points | Exactly two: `TransportProvider` (registry: one explicit built-in list, empty until Phase 3, plus `ServiceLoader`) and `TokenProvider` | Spec §2 and Rule 0 |
| Error model | Checked tree: `MailException` with `TransientMailException` (retryAfter), `PermanentMailException` (with `InvalidMailException`, `SenderRejectedException`) and `AuthRequiredException`; configuration errors are unchecked `IllegalArgumentException` / `IllegalStateException` | Spec §5.2; deviation recorded in docs/architecture.md |
| Package layout | Core `de.morisoft.morimail` imports and names only `java.*` types (its one outside reference will be `BuiltInTransports` -> `graph` in Phase 3); `de.morisoft.morimail.graph` and `de.morisoft.morimail.entra` (Phase 3) never import each other; test fixture `de.morisoft.morimail.test` ships in the test-jar | Spec §4 import rules, enforced by `ImportRulesTest` |
| Test fixture | `RecordingTransport` (id `recording`) in library test sources, registered through `META-INF/services`, built into a test-jar that the sample consumes inside the reactor in the default build; `attach=false` under `release` hides it from the reactor, so the release build runs on the published modules only (`-pl morimail -am`) | Spec §10.1; reactor-only test-jar owner decision; route checked by the plan-checker iteration 1 probe (see 01-01) |
| Documentation contract | Every public type states its thread safety in Javadoc; the `release` profile runs doclint `all` with `failOnWarnings` | Spec §11, §13.3; presence enforced by `PublicTypesDocumentThreadSafetyTest`, truth backed by the concurrency tests of plan 01-06 and review |
| Deployment target | None for the skeleton. CI is GitHub Actions on Java 17 (read-only token, SHA-pinned actions, no secrets). Release to Maven Central is the owner's job in Phase 4 | Spec §13.5: Claude never publishes, deploys or tags |
| Directory layout | `pom.xml`, `morimail/src/{main,test}/java/de/morisoft/morimail/...`, `morimail-sample-serial/`, `docs/` (spec, architecture, later entra-setup), root `README.md`, `SECURITY.md`, `CLAUDE.md`, `LICENSE`, `.github/workflows/ci.yml` | Spec §4 |

## Stack Touched in Phase 1

- [ ] Project scaffold — Maven reactor, wrapper, compiler at release 17, Surefire with JUnit Jupiter 6 and AssertJ, `release` profile (sources, Javadoc, GPG, Central plugin with `autoPublish=false`)
- [ ] Routing — not applicable as HTTP routing; the analogue is transport resolution: `MailAccount.Builder.build()` resolves the transport id through `TransportRegistry` (built-in list plus `ServiceLoader`)
- [ ] Database — not applicable (no persistence in the library); the "read and write" of the skeleton is `RecordingTransport` recording a message (write) and `sent()` returning it (read)
- [ ] UI — not applicable; the user-facing surface is the Java API (`Mail.using(..)`, `Mailer.of(..)`) exercised by `SendThroughRecordingTest` and by the sample module's `RecordingTestJarSmokeTest`
- [ ] Deployment — documented local full-stack run command: `./mvnw -B -ntp verify` (plus `-Prelease clean verify -Dgpg.skip -pl morimail -am` and `-o verify`), the same commands CI runs on Java 17

## Out of Scope (Deferred to Later Slices)

- Templates, the full builder surface and attachment rules — plan 01-05 (same phase)
- Scripted failures (`failNext`, `failFor`, `failValidation`), the registry's duplicate-id rule and the concurrency proof — plan 01-06 (same phase)
- README stub, SECURITY.md, root CLAUDE.md, the spec move and the hygiene gates — plan 01-02 (same phase, parallel to 01-03; not part of the code path)
- The serial-mail CLI, CSV reader, journal, rate limit and example campaign — Phase 2
- Entra client credentials, the Graph transport, the `graph` line in `BuiltInTransports`, the throwaway PKCS12, the `live` profile and test — Phase 3
- `docs/entra-setup.md`, the full `docs/architecture.md` contract summary, the README quick start, the release bundle rehearsal — Phase 4
- Inline images, other transports, delegated OAuth, large attachments, MIME alternative parts — after the MVP (spec §2, §10.3)

## Subsequent Slice Plan

Each later phase adds one vertical slice on top of this skeleton without altering its architectural decisions:

- Phase 2: a campaign sender dry-runs and sends a CSV serial mail through the `recording` transport, with pre-flight, journal, resume and rate limit
- Phase 3: a Java developer sends through Microsoft Graph as an Entra app, each failure mapped to transient, permanent, sender-rejected or auth-required; ends at the owner's live-test stop
- Phase 4: the owner sets up the tenant from the setup guide and publishes 0.1.0 from a rehearsed release
