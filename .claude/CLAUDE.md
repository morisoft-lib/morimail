<!-- GSD:project-start source:PROJECT.md -->

## Project

**morimail**

morimail is a small open-source Java 17 library (`de.morisoft:morimail`) that sends templated mail through pluggable transports, one mail at a time. The MVP ships one built-in transport, Microsoft Graph, authenticated as an Entra app (client credentials with a certificate or client secret, no user sign-in). The same repository ships a command-line sample (`morimail-sample-serial`) that sends a serial mail: one template, one CSV of recipients, shared attachments, one mail per recipient, with a send journal so a rerun never sends twice.

The full, binding specification is `spec.md` (moved to `docs/spec.md` in the first phase). Where this document and the spec disagree, the spec wins.

**Core Value:** A consumer can send one templated mail with attachments through Microsoft Graph as an Entra app, using only the JDK and Jackson, and get back a clear error category (transient / permanent / auth-required) that tells them what to do next.

### Constraints

- **Tech stack**: Java 17 and Maven (parent + library + sample module, wrapper checked in). Library runtime dependency is Jackson only; `System.Logger` for logging; `java.net.http.HttpClient`; JDK crypto for the JWT. picocli for the sample only. Tests: JUnit 5, AssertJ, WireMock. Use the latest stable versions, checked on Maven Central.
- **Simplicity (Rule 0)**: Build the simplest code that meets the spec. Only two extension points (`TransportProvider`, `TokenProvider`). No frameworks, DI, reflection, or extra interfaces/patterns. Ask before adding any dependency. When in doubt, build less.
- **Architecture**: The core package imports only the JDK (its one outside reference is the built-in list). `graph` and `entra` don't import each other. No Graph assumptions in the core. Transport options are opaque to the core. Limits are checked in the transport's `validate`.
- **Security / public repo**: No secrets, tenant IDs, real addresses, internal hosts or customer names in any file, fixture or commit. Use `example.com`/`example.org` and zero GUIDs. The only exceptions are the throwaway test PKCS12 (password `changeit`, since keytool rejects `test`) and the maintainer identity. Never log tokens, secrets, assertions or full bodies; recipients at DEBUG only.
- **Identity**: All commits authored and committed as `Stephan Wald <stephan@morisoft.de>`, set in local git config (done 2026-11-21). Global config untouched.
- **Operations**: Never send real mail, never publish/deploy/tag. The owner does releases and live tests.
- **Concurrency/docs**: Public types get Javadoc stating thread safety. `Mailer`, transports and token providers are safe for concurrent use. Javadoc must build cleanly.
- **Build**: `mvn verify` must pass offline. Finish and commit each unit of work with `mvn verify` green.
- **Checkpoint**: Stop after the Graph transport + Entra client work (spec milestone 4) and report, so the owner can run the live test and correct the error mapping.

<!-- GSD:project-end -->

<!-- GSD:stack-start source:research/STACK.md -->

## Technology Stack

## Recommended Stack

### Core Technologies

| Technology | Version | Purpose | Why Recommended | Confidence |
|------------|---------|---------|-----------------|------------|
| Java (language level) | 17 via `maven.compiler.release=17` | Records, `java.net.http`, `System.Logger` | `--release 17` makes javac reject post-17 APIs even when building on JDK 21 (this machine has only JDK 21). CI must run on 17 to catch runtime differences. | HIGH |
| Apache Maven (via wrapper) | 3.10.1 (pin in `maven-wrapper.properties`); 3.9.16 also passes | Build | maven.apache.org lists 3.10.1 as current/recommended, 3.9.16 as previous stable, 4.0.0-rc-7 as "NOT safe for production". Full build and release bundle verified on both 3.10.1 and 3.9.16. **Never Maven 4:** the Central publishing plugin documents "Publishing doesn't work when using Maven 4.x.x". | HIGH |
| Maven Wrapper | wrapper plugin 3.3.4, type `only-script` (default) | Reproducible Maven bootstrap | Produces `mvnw`, `mvnw.cmd`, `.mvn/wrapper/maven-wrapper.properties` and no jar, so the `*.jar` rule in `.gitignore` is irrelevant (verified: `git check-ignore` matches none of the three). | HIGH |
| jackson-databind (via jackson-bom) | 2.23.0 | JSON for Graph body, token response, JWT parts, sample journal | Latest stable 2.x (released 2026-11-08). Import `jackson-bom` 2.23.0 in the parent and declare only `jackson-databind` in the library. Runtime tree is exactly databind 2.23.0 + jackson-core 2.23.0 + jackson-annotations 2.23 (annotations drop the patch number since 2.20; this is normal). Baseline JDK 8, fine on 17. | HIGH |
| picocli | 4.7.7 | Sample CLI only | Latest and only recent stable (Apr 2025); no 5.x exists. Works by reflection at runtime, no annotation processor needed. `picocli-codegen` is only for GraalVM, skip it. | HIGH |

### Test Dependencies (all `test` scope)

| Library | Version | Purpose | Why | Confidence |
|---------|---------|---------|-----|------------|
| JUnit Jupiter via `junit-bom` | 6.1.3 | Test engine | Latest stable (2026-08-07). The spec says "JUnit 5"; the current line is **JUnit 6**, which has a Java 17 baseline and the same `org.junit.jupiter` coordinates and `@Test`/`@Tag` API. Depend on the single aggregator `org.junit.jupiter:junit-jupiter`; Surefire 3.6.1 auto-detects the platform provider. Verified end to end. 6.2.0-M1 is a milestone, do not use. | HIGH |
| AssertJ (`assertj-core`) | 3.27.7 | Assertions | Latest stable 3.x (Jan 2026). 4.0.0-M1 is only a milestone. | HIGH |
| WireMock: `org.wiremock:wiremock` | 3.13.2 | Fake Graph and token endpoints | Use the plain `org.wiremock:wiremock` artifact (not `wiremock-standalone`). Works with JUnit 6 via `@WireMockTest` (`com.github.tomakehurst.wiremock.junit5`) and parameter injection of `WireMockRuntimeInfo`. Brings Jetty 11.0.26 (Java 11+ baseline, fine on 17) and aligns its Jackson to our BOM (2.23.0 ran cleanly). Latest stable is 3.13.2 (2025-11-14); the 4.0.0-beta.N line (beta.44 on 2026-11-17) is not stable. | HIGH |
- JDK `HttpClient.newHttpClient()` (default HTTP/2 preference) against WireMock over cleartext falls back to HTTP/1.1 and a POST with JSON body, header `Retry-After` and a 429 status all arrive intact. No need to force `Version.HTTP_1_1` in transport code or tests.
- WireMock prints three `SLF4J(W): No SLF4J providers` lines per test JVM. Harmless noise. Do not add `slf4j-nop` unless the owner approves a test dependency (Rule 0: ask before adding).

### Build Plugins (pin ALL in `pluginManagement`; every version below ran in the smoke build)

| Plugin | Version | Used for | Notes |
|--------|---------|----------|-------|
| maven-compiler-plugin | 3.16.0 | compile | 4.0.0-beta-5 exists, not stable. Config is just `maven.compiler.release=17`. |
| maven-surefire-plugin | 3.6.1 | all tests incl. WireMock and the `live` test | No config block needed; uses `groups`/`excludedGroups` properties (see recipe). |
| maven-failsafe-plugin | **not used** (3.6.1 would be current) | - | WireMock runs in-process and is fast; there are no integration tests in the spec. Adding failsafe is extra moving parts for nothing. |
| maven-jar-plugin | 3.5.1 | main jar, `test-jar` goal, `Automatic-Module-Name` | 4.0.0-beta-2 exists, not stable. |
| maven-source-plugin | 3.4.0 | `-sources.jar` (goal `jar-no-fork`) | 4.0.0-beta-1 exists, not stable. Release profile only. |
| maven-javadoc-plugin | 3.12.0 | `-javadoc.jar` (goal `jar`) | Release profile only. |
| maven-gpg-plugin | 3.2.8 | signing (goal `sign`, phase `verify`) | Release profile only. `gpg.passphrase` is deprecated by the plugin itself; rely on gpg-agent. |
| central-publishing-maven-plugin (`org.sonatype.central`) | 0.12.0 (2026-10-13) | Bundle and upload to Central Portal | `<extensions>true</extensions>`, server id `central`. Release profile only. |
| maven-resources-plugin | 3.5.0 | resources | Pin so Maven 3.10.1 defaults cannot drift. |
| maven-clean-plugin | 3.5.0 | clean | Same. |
| maven-install-plugin | 3.2.0 | install | Same. |
| maven-deploy-plugin | 3.2.0 | deploy | Same (the Central extension replaces it in the release profile). |
| maven-dependency-plugin | 3.11.0 | sample only: `copy-dependencies` to `target/lib` | Optional; see "Running the sample". |
| maven-enforcer-plugin | 3.7.0 | not used | Would only add a Maven-version rule that the wrapper already guarantees. |

### Development Tools

| Tool | Purpose | Notes |
|------|---------|-------|
| `mvnw` (only-script) | Run Maven on CI and locally | Needs `curl` or `wget` (or PowerShell on Windows) at first run to download Maven; GitHub runners have them. Needs a POSIX shell. Commit with the exec bit: `git add --chmod=+x mvnw`. |
| `.gitattributes` | Keep `mvnw` working on Windows checkouts | Add `mvnw text eol=lf` and `*.cmd text eol=crlf`. A CRLF `mvnw` fails with `/bin/sh^M`. |
| GitHub Actions | CI | `actions/checkout` v7.0.1 and `actions/setup-java` v6.3.0 are the current releases (GitHub API, 2026-11-21). Use `distribution: temurin`, `java-version: 17`, `cache: maven`, then `./mvnw -B -ntp verify`. No secrets. |
| keytool + openssl | Generate the throwaway test PKCS12 and cross-check signatures | See PKCS12 recipe. |

## Findings That Change the Plan

## Recipes (all verified by execution unless marked)

### Parent POM skeleton (property and BOM wiring)

### Excluding the `live` test by default, `-Plive` to run it

### Publishing a test-jar and consuming it in the sample

- Library: `maven-jar-plugin` execution `test-jar` (goal `test-jar`). `RecordingTransport`, its `TransportProvider`, and `src/test/resources/META-INF/services/de.morisoft.morimail.TransportProvider` all land in `morimail-<v>-tests.jar`.
- Sample: `de.morisoft:morimail` `<type>test-jar</type>` `<scope>test</scope>` (version from dependencyManagement). Verified: `ServiceLoader.load(TransportProvider.class)` in the sample's test finds the id `recording` from the test-jar.
- Works with `mvn verify`, `mvn test` (reactor falls back to `target/test-classes`), and `mvn -Prelease verify` with `attach=false`.
- Not published: see Finding 2. Spec 10.1 wording "published as a test-jar" should be read as "built and shared inside the reactor".

### Release profile (in the parent POM, verified end to end)

### RSASSA-PSS (PS256) on the JDK

- Signing input is `base64url(header) + "." + base64url(claims)`, ASCII bytes.
- `x5t#S256` = `Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(cert.getEncoded()))`.
- Verified on JDK 21 with `--release 17`: round trip with `cert.getPublicKey()` passes, and **OpenSSL independently verifies the signature** with `rsa_padding_mode:pss`, `rsa_pss_saltlen:32`, `rsa_mgf1_md:sha256`. A negative control (salt length 20) fails, so the test is real. Use the same OpenSSL check once by hand to prove interop with the RFC 7518 PS256 definition.
- Runtime behaviour on a real JDK 17 was not testable here (only 21 installed). The API exists since JDK 11, so risk is low, and CI on Java 17 is the proof. Confidence: HIGH on API, MEDIUM-HIGH on identical runtime behaviour.
- The spec's unit test (decode JWT, verify with the cert public key) is exactly this round trip; add header/claim assertions with Jackson.

### PKCS12 loading on Java 17

- Password must be 6+ characters (Finding 1).
- **Compatibility trap:** keytool from JDK 20 and later, and OpenSSL 3, write PKCS12 with PBES2/PBKDF2/AES-256 and a SHA-256 MAC. Reading that on an old JDK 17 patch level is MEDIUM confidence (not testable here). To remove the doubt, create the file with `keytool -J-Dkeystore.pkcs12.legacy ...`: verified output is RC2/3DES with a SHA-1 MAC, which every JDK reads, and JDK 21 loads it. Recommended for the checked-in file. CI on Java 17 will prove either way.
- Use an obviously named file (`test-keystore-throwaway.p12`) as the spec wants, and the README sentence about it.

### Maven wrapper (checked in)

### Running the sample (optional, small)

## Alternatives Considered

| Recommended | Alternative | When to Use Alternative |
|-------------|-------------|-------------------------|
| `org.wiremock:wiremock` 3.13.2 | `wiremock-standalone` 3.13.2 | Only if a Jackson or Jetty version clash ever appears on the test classpath. None did with Jackson 2.23.0. |
| `org.wiremock:wiremock` 3.13.2 | WireMock 4.0.0-beta.N | When 4.0 goes stable (Jetty 12). Not now: 44 betas, no stable. |
| JUnit 6.1.3 | JUnit 5.x | Only if a tool in the chain cannot run on JUnit 6. Nothing in this stack requires it. |
| Jackson 2.23.0 | Jackson 3.3.0 (`tools.jackson`) | If the owner wants the newest major and accepts different imports. Decide before the first line of code. |
| Maven 3.10.1 (wrapper) | Maven 3.9.16 | If 3.10.1 shows an issue in CI; 3.9.16 passed the same checks. |
| Surefire with `groups`/`excludedGroups` properties | `@EnabledIfEnvironmentVariable` on the live test | Could be used in addition (self-skipping), but the spec asks for a tag and `-Plive`. |
| `attach=false` in the release profile | Separate unpublished `morimail-testkit` module | If the test-jar workaround ever proves fragile. It would change the spec's layout, so not now. |

## What NOT to Use

| Avoid | Why | Use Instead |
|-------|-----|-------------|
| Maven 4 (any 4.0.0-rc) for releases | Central plugin documents it as broken | Wrapper pinned to 3.10.1 |
| `-Dtype=bin` wrapper (with `maven-wrapper.jar`) | `.gitignore` excludes `*.jar`; extra binary in a public repo | `only-script` (default) |
| `maven-shade-plugin` for the sample | Needs service-file merging; unnecessary | `lib/` directory with manifest classpath |
| `maven-failsafe-plugin` | No integration tests exist; WireMock is in-process | Surefire only |
| `wiremock-standalone` by default | Hides dependency versions; no clash to solve | `org.wiremock:wiremock` |
| `maven-gpg-plugin` `gpg.passphrase` | Deprecated and leaks secrets | gpg-agent / pinentry on the owner's machine |
| Hand-written checksums or bundle zips | The Central plugin generates md5, sha1, sha256, sha512 | Let the plugin do it |
| Un-pinned plugin versions | Maven 3.10.x defaults differ from 3.9.x; builds drift | Pin all in `pluginManagement` |
| `jackson-annotations` or `jackson-core` as separate dependencies | They arrive with databind and are managed by the BOM | Declare databind only |
| `slf4j`, `logback`, `commons-*` in the library | Violates the dependency allowlist | `System.Logger` |
| Any `autoPublish=true` on 0.1.0 | Release is permanent | Review in the Portal, then publish |

## Version Compatibility

| Package A | Compatible With | Notes |
|-----------|-----------------|-------|
| jackson-bom 2.23.0 | wiremock 3.13.2 | WireMock's own POM builds against an older Jackson 2.x; with our BOM on the test classpath it ran correctly. |
| wiremock 3.13.2 (Jetty 11.0.26) | Java 17, JUnit 6.1.3 | Jetty 11 needs Java 11+. `@WireMockTest` works on Jupiter 6. |
| junit-bom 6.1.3 | surefire 3.6.1 | Auto-detected `JUnitPlatformProvider`; no explicit launcher dependency needed. |
| central-publishing-maven-plugin 0.12.0 | Maven 3.9.16 and 3.10.1 | Both verified. Maven 4.x documented as broken. |
| maven-javadoc-plugin 3.12.0 | JDK 21 building `release 17` | Built both jars cleanly. |
| `--release 17` on JDK 21 | JDK 17 runtime | API surface guaranteed; runtime behaviour of PSS and PKCS12 reading confirmed on 21 only. CI on 17 is the check. |

## Installation

# Wrapper (once, with any local Maven)

# Everyday

## Open Items for the Roadmap

- Owner decision: keystore password (`test` is impossible with keytool, recommend `changeit` and legacy-format file) and Jackson 2 vs 3 (recommend 2.23.0).
- Phase 1 (parent POM): include the release profile, sample skip properties and test-jar `attach=false` override from the start, and run `-Prelease verify -Dgpg.skip` in a local check, so milestone 5 is not the first time Javadoc strictness is hit.
- The WireMock-based tests need an explicit check on JDK 17 in CI: PSS signing, PKCS12 loading of the throwaway file.
- Re-check versions at implementation time per spec 3 (this file is dated 2026-11-21); the numbers here are a snapshot.

## Sources

- repo1.maven.org `maven-metadata.xml` and POM/jar `Last-Modified` for: jackson-bom, jackson-databind (2.x and `tools.jackson` 3.x), picocli, junit-bom, assertj-core, org.wiremock:wiremock and wiremock-standalone, maven-compiler/surefire/failsafe/jar/source/javadoc/gpg/resources/clean/install/deploy/dependency/enforcer plugins, maven-wrapper and wrapper plugin, apache-maven, central-publishing-maven-plugin, versions-maven-plugin. HIGH (authoritative registry).
- https://central.sonatype.org/publish/publish-portal-maven/ : plugin coordinates, 0.12.0, `publishingServerId`, `autoPublish` default false, `waitUntil`, `skipPublishing`, `excludeArtifacts`, "does not generate sources, Javadoc, signatures", Maven 4 known issue. HIGH (official).
- https://central.sonatype.org/publish/requirements/ : required POM metadata, sources/javadoc jars, md5/sha1 required, `.asc` per file, no SNAPSHOT. HIGH (official).
- https://central.sonatype.org/publish/requirements/gpg/ : supported keyservers. HIGH (official).
- https://central.sonatype.org/register/namespace/ and https://central.sonatype.org/publish/generate-portal-token/ : namespace DNS verification and token creation. HIGH (official); settings.xml snippet is not on the token page.
- https://maven.apache.org/tools/wrapper/ and https://maven.apache.org/download.cgi : wrapper types (`only-script` default) and Maven release lines. HIGH (official).
- central-publishing-maven-plugin 0.12.0 jar and sources jar (`plugin.xml`, `PublishMojo`, `AbstractPublisherMojo`): goal `publish` on `deploy`, properties `skipUpload`, `skipPublishing`, `excludeArtifacts` (by artifactId only). HIGH (read from the artifact itself).
- Executed smoke project (3-module reactor, wrapper on Maven 3.10.1, system Maven 3.9.16, JDK 21 with `--release 17`, throwaway GPG key, OpenSSL 3.5.5): default build, `-Plive`, `mvn test`, `-Prelease verify -Dgpg.skip`, `-Prelease deploy -DskipUpload=true` bundle contents and signature verification, PSS round trip and OpenSSL cross-check, PKCS12 variants, sample runnable jar. HIGH for what ran; JDK 17 runtime behaviour untested (JDK 21 only).
- GitHub API releases for `actions/checkout` and `actions/setup-java`. MEDIUM (latest tags only, workflow not executed).

<!-- GSD:stack-end -->

<!-- GSD:conventions-start source:CONVENTIONS.md -->

## Conventions

Conventions not yet established. Will populate as patterns emerge during development.
<!-- GSD:conventions-end -->

<!-- GSD:architecture-start source:ARCHITECTURE.md -->

## Architecture

Architecture not yet mapped. Follow existing patterns found in the codebase.
<!-- GSD:architecture-end -->

<!-- GSD:skills-start source:skills/ -->

## Project Skills

No project skills found. Add skills to any of: `.claude/skills/`, `.agents/skills/`, `.cursor/skills/`, `.github/skills/`, or `.codex/skills/` with a `SKILL.md` index file.
<!-- GSD:skills-end -->

<!-- GSD:workflow-start source:GSD defaults -->

## GSD Workflow Enforcement

Before using Edit, Write, or other file-changing tools, start work through a GSD command so planning artifacts and execution context stay in sync.

Use these entry points:
- `/gsd-fast` for a trivial task inline, with no subagents and no PLAN.md
- `/gsd-quick` for small fixes, doc updates, and ad-hoc tasks
- `/gsd-debug` for investigation and bug fixing
- `/gsd-execute-phase` for planned phase work

Do not make direct repo edits outside a GSD workflow unless the user explicitly asks to bypass it.
<!-- GSD:workflow-end -->

<!-- GSD:profile-start -->

## Developer Profile

> Profile not yet configured. Run `/gsd-profile-user` to generate your developer profile.
> This section is managed by `generate-claude-profile` -- do not edit manually.
<!-- GSD:profile-end -->
