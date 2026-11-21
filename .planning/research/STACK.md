# Stack Research

**Domain:** Java 17 mail library (JDK-only HTTP/JWT, Jackson only) with a Microsoft Graph transport, plus a picocli sample CLI; published to Maven Central
**Researched:** 2026-11-21
**Confidence:** HIGH (every version read from repo1.maven.org metadata; the build, test, release and crypto recipes below were executed in a throwaway project, not just read from docs)

The stack is fixed by spec.md section 3. This file makes it concrete: exact versions, exact POM wiring, and the places where the spec's assumptions did not survive contact with the tooling (section "Findings That Change the Plan" is the part the roadmap must not skip).

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

Notes from the executed test:
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

These were found by building and running, not by reading. Each needs a roadmap or spec decision.

1. **Spec says the test keystore password is `test`; keytool refuses it.** `keytool` errors with "Keystore password must be at least 6 characters" (same for key password). Options: (a) use a 6+ character password, e.g. `changeit` (recommended: documented once in the README sentence about the keystore, zero special tooling); (b) create the file with `openssl pkcs12 -export ... -passout pass:test` (works, and the JDK loads it, verified, but then it is no longer "created with keytool" as spec 10.1 says). Needs an owner decision recorded in PROJECT.md. Note that PROJECT.md "Security" names the password `test` as the permitted exception.

2. **The test-jar would be published to Central.** The Central plugin replaces the deploy plugin and stages every attached artifact. With a plain `test-jar` execution the bundle contained `morimail-0.1.0-tests.jar` plus its signature. Spec 13.2 says the test-jar is not published. Fix verified: in the `morimail` module, a `release` profile overriding the `test-jar` execution with `<attach>false</attach>`. The sample still resolves the test-jar in the reactor afterwards (verified on 3.9.16 and 3.10.1, with `verify` and `-Prelease verify`). In the default build the test-jar stays attached (so `mvn install` then `-pl morimail-sample-serial` works).
   - **Correction (2026-11-21, Phase 1 plan check, see D-20 in `.planning/phases/01-library-core/01-CONTEXT.md`):** the claim that the sample still resolves the test-jar under `-Prelease verify` did not reproduce. A full-reactor `-Prelease` build with `attach=false` fails with "Could not find artifact ...:jar:tests" on Maven 3.10.1 and 3.9.16, with and without `clean`, and even with the sample's tests skipped. A test-jar installed earlier in the local repository would explain the earlier pass. Release builds are scoped to `-pl morimail -am`.

3. **`maven.deploy.skip=true` does NOT keep the sample out of the Central bundle.** The sample (unsigned, no sources/Javadoc) was staged anyway, which would fail Central validation for the whole deployment. Fix verified: property `skipPublishing=true` in the sample POM (log line: "Skipping Central Release Publishing for artifact 'morimail-sample-serial'"). Keep `maven.deploy.skip` too; it is harmless.

4. **`failOnWarnings` in the parent's release profile fails the sample's Javadoc** (warning: "use of default constructor, which does not provide a comment"). The sample is never published, so set `maven.source.skip`, `maven.javadoc.skip` and `gpg.skip` to `true` in the sample POM. With those, `-Prelease verify` on the whole reactor produced zero WARNING lines.

5. **The same doclint rule bites the library.** Every public class needs an explicit constructor or a class comment that covers it; a public class relying on the implicit default constructor breaks a release build once `doclint=all` + `failOnWarnings=true` is on. Records and interfaces are unaffected. Put the Javadoc rule in CLAUDE.md and turn the release profile on in CI-free local checks early (from the first library phase), not only in milestone 5.

6. **The Central plugin treats `-SNAPSHOT` versions as snapshot publishing** (separate path, off by default for the namespace). The owner must release from a non-SNAPSHOT version (`0.1.0`). Release rehearsal without sending anything: `-DskipUpload=true` (verified property name) writes `target/central-publishing/central-bundle.zip`.

7. **JUnit is 6.x, not 5.** Called out so nobody "fixes" the version back to 5.x. See table above.

8. **Jackson 2.x vs 3.x is a decision, not an accident.** Jackson 3.3.0 (`tools.jackson.core:jackson-databind`, package `tools.jackson.*`, JDK 17 baseline) is out. This file recommends **2.23.0** because the spec says "Jackson databind" with `com.fasterxml` conventions, WireMock 3.13.x and most downstream consumers are still on 2.x, and 2.x is actively released (2.23.0 on 2026-11-08). Jackson 3 is a package-level migration (different groupId and imports); if the owner prefers it, decide before any code is written. Confidence that 2.23.0 is the right call for 0.1.0: MEDIUM-HIGH.

## Recipes (all verified by execution unless marked)

### Parent POM skeleton (property and BOM wiring)

```xml
<properties>
  <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
  <maven.compiler.release>17</maven.compiler.release>
  <project.build.outputTimestamp>2026-11-21T00:00:00Z</project.build.outputTimestamp>
  <jackson.version>2.23.0</jackson.version>
  <junit.version>6.1.3</junit.version>
  <assertj.version>3.27.7</assertj.version>
  <wiremock.version>3.13.2</wiremock.version>
  <picocli.version>4.7.7</picocli.version>
  <!-- live-test switch -->
  <excludedGroups>live</excludedGroups>
  <groups></groups>
</properties>

<dependencyManagement>
  <dependencies>
    <dependency><groupId>com.fasterxml.jackson</groupId><artifactId>jackson-bom</artifactId>
      <version>${jackson.version}</version><type>pom</type><scope>import</scope></dependency>
    <dependency><groupId>org.junit</groupId><artifactId>junit-bom</artifactId>
      <version>${junit.version}</version><type>pom</type><scope>import</scope></dependency>
    <dependency><groupId>org.assertj</groupId><artifactId>assertj-core</artifactId><version>${assertj.version}</version></dependency>
    <dependency><groupId>org.wiremock</groupId><artifactId>wiremock</artifactId><version>${wiremock.version}</version></dependency>
    <dependency><groupId>info.picocli</groupId><artifactId>picocli</artifactId><version>${picocli.version}</version></dependency>
    <dependency><groupId>de.morisoft</groupId><artifactId>morimail</artifactId><version>${project.version}</version></dependency>
    <dependency><groupId>de.morisoft</groupId><artifactId>morimail</artifactId><version>${project.version}</version><type>test-jar</type></dependency>
  </dependencies>
</dependencyManagement>
```

`project.build.outputTimestamp` (reproducible jars) produced no warnings. Required POM metadata per Central (name, description, url, licenses, developers, scm with connection, developerConnection and url) goes in the parent; the child inherits it. Verified list from central.sonatype.org/publish/requirements.

`morimail/pom.xml` extra: `maven-jar-plugin` with `<archive><manifestEntries><Automatic-Module-Name>de.morisoft.morimail</Automatic-Module-Name></manifestEntries></archive>` (one line, gives consumers a stable module name without a `module-info`; optional, recommended) and the `test-jar` execution (id `test-jar`, goal `test-jar`).

### Excluding the `live` test by default, `-Plive` to run it

Surefire reads the user properties `groups` and `excludedGroups`, so no Surefire `<configuration>` is needed. Default properties are in the skeleton above; the profile flips them:

```xml
<profile>
  <id>live</id>
  <properties>
    <excludedGroups></excludedGroups>
    <groups>live</groups>
  </properties>
</profile>
```

Test class: `@Tag("live")`. Verified: default `mvn verify` ran 2 tests and printed nothing from the live test; `-Plive` ran only the live test (1 test) and the sample module reported "Tests run: 0" without failing. `-Plive` runs ONLY live tests (intended: the owner does not need the whole suite again). The live test lives in the library module, read config from environment variables, and must skip itself with `Assumptions.assumeTrue(...)` when they are absent so an accidental `-Plive` without config does not look like a failure.

### Publishing a test-jar and consuming it in the sample

- Library: `maven-jar-plugin` execution `test-jar` (goal `test-jar`). `RecordingTransport`, its `TransportProvider`, and `src/test/resources/META-INF/services/de.morisoft.morimail.TransportProvider` all land in `morimail-<v>-tests.jar`.
- Sample: `de.morisoft:morimail` `<type>test-jar</type>` `<scope>test</scope>` (version from dependencyManagement). Verified: `ServiceLoader.load(TransportProvider.class)` in the sample's test finds the id `recording` from the test-jar.
- Works with `mvn verify`, `mvn test` (reactor falls back to `target/test-classes`), and `mvn -Prelease verify` with `attach=false`.
  - **Correction (2026-11-21, see D-20 in `.planning/phases/01-library-core/01-CONTEXT.md`):** the `-Prelease verify` part does not hold for a full reactor; scope release builds to `-pl morimail -am` (finding 2 above).
- Not published: see Finding 2. Spec 10.1 wording "published as a test-jar" should be read as "built and shared inside the reactor".

### Release profile (in the parent POM, verified end to end)

```xml
<profile>
  <id>release</id>
  <build>
    <plugins>
      <plugin>
        <artifactId>maven-source-plugin</artifactId>
        <executions><execution><id>attach-sources</id><goals><goal>jar-no-fork</goal></goals></execution></executions>
      </plugin>
      <plugin>
        <artifactId>maven-javadoc-plugin</artifactId>
        <configuration><doclint>all</doclint><failOnWarnings>true</failOnWarnings></configuration>
        <executions><execution><id>attach-javadocs</id><goals><goal>jar</goal></goals></execution></executions>
      </plugin>
      <plugin>
        <artifactId>maven-gpg-plugin</artifactId>
        <executions><execution><id>sign-artifacts</id><phase>verify</phase><goals><goal>sign</goal></goals></execution></executions>
      </plugin>
      <plugin>
        <groupId>org.sonatype.central</groupId>
        <artifactId>central-publishing-maven-plugin</artifactId>
        <extensions>true</extensions>
        <configuration><publishingServerId>central</publishingServerId></configuration>
      </plugin>
    </plugins>
  </build>
</profile>
```

Do not set `autoPublish` (default false, `waitUntil` default `validated`): the owner reviews the validated deployment in the Portal and clicks Publish, because a Central release is permanent. Parent POM (`packaging pom`) needs no sources or Javadoc; the plugins no-op on it (verified).

Sample POM properties: `maven.deploy.skip`, `maven.source.skip`, `maven.javadoc.skip`, `gpg.skip`, `skipPublishing` all `true`. Library POM: the `release` profile overriding the test-jar execution with `<attach>false</attach>`.

Verified results: `-Prelease verify -Dgpg.skip` over the 3-module reactor: BUILD SUCCESS, no WARNING lines, on 3.9.16 and 3.10.1. `-Prelease deploy -DskipUpload=true` with a throwaway key: bundle contained exactly `morimail-parent` pom and `morimail` (jar, sources, javadoc, pom), each with `.asc`, `.md5`, `.sha1`, `.sha256`, `.sha512`; all 5 signatures verified by `gpg --verify`. The plugin generates all four checksums itself; Central requires `.md5` and `.sha1`.

Owner-only release steps (from the Sonatype pages; do not run from Claude):
1. Verify namespace `de.morisoft` in the Central Portal by DNS TXT record on `morisoft.de` (the check is on the exact base domain; do not request verification before the record exists because NXDOMAIN is cached; Sonatype says it usually takes minutes).
2. Create a Portal user token at https://central.sonatype.com/usertoken and put it in `~/.m2/settings.xml` as `<server><id>central</id><username>...</username><password>...</password></server>`. The token page itself shows no XML; the `central` id comes from the plugin page and must equal `publishingServerId`.
3. Create the GPG key with the maintainer identity and publish the public key to a keyserver Central supports: `keyserver.ubuntu.com`, `keys.openpgp.org` or `pgp.mit.edu`.
4. Set version `0.1.0` in the three POMs (no SNAPSHOT), commit.
5. Rehearse: `./mvnw -Prelease clean deploy -DskipUpload=true`, inspect `target/central-publishing/central-bundle.zip`.
6. Release: `./mvnw -Prelease clean deploy`, then publish in the Portal UI.

### RSASSA-PSS (PS256) on the JDK

```java
PSSParameterSpec pss = new PSSParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, 32,
        PSSParameterSpec.TRAILER_FIELD_BC);
Signature s = Signature.getInstance("RSASSA-PSS");
s.setParameter(pss);          // before initSign
s.initSign(privateKey);       // a plain "RSA" key from PKCS12 is accepted
s.update(signingInput.getBytes(StandardCharsets.US_ASCII));
byte[] sig = s.sign();        // 256 bytes for a 2048-bit key; base64url without padding for the JWT
```

- Signing input is `base64url(header) + "." + base64url(claims)`, ASCII bytes.
- `x5t#S256` = `Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(cert.getEncoded()))`.
- Verified on JDK 21 with `--release 17`: round trip with `cert.getPublicKey()` passes, and **OpenSSL independently verifies the signature** with `rsa_padding_mode:pss`, `rsa_pss_saltlen:32`, `rsa_mgf1_md:sha256`. A negative control (salt length 20) fails, so the test is real. Use the same OpenSSL check once by hand to prove interop with the RFC 7518 PS256 definition.
- Runtime behaviour on a real JDK 17 was not testable here (only 21 installed). The API exists since JDK 11, so risk is low, and CI on Java 17 is the proof. Confidence: HIGH on API, MEDIUM-HIGH on identical runtime behaviour.
- The spec's unit test (decode JWT, verify with the cert public key) is exactly this round trip; add header/claim assertions with Jackson.

### PKCS12 loading on Java 17

```java
KeyStore ks = KeyStore.getInstance("PKCS12");
try (InputStream in = Files.newInputStream(path)) { ks.load(in, password); }
String alias = Collections.list(ks.aliases()).stream().filter(a -> ks.isKeyEntry(a)).findFirst().orElseThrow(...);
PrivateKey key = (PrivateKey) ks.getKey(alias, password);          // key password = store password
X509Certificate cert = (X509Certificate) ks.getCertificate(alias);
```

(`isKeyEntry` throws a checked `KeyStoreException`; wrap it in the lambda or use a loop.) `key.getAlgorithm()` is `RSA` and works with RSASSA-PSS as shown. Pick the first key entry rather than hard-coding an alias, so files from keytool, openssl and Windows tools all work. `KeyStore.getInstance(File, char[])` (JDK 9+) also works if a Path/File is at hand.

Creating the checked-in test keystore (verified commands):

```bash
keytool -genkeypair -alias test -keyalg RSA -keysize 2048 -storetype PKCS12 \
  -keystore morimail/src/test/resources/test-keystore-throwaway.p12 \
  -storepass changeit -dname "CN=morimail-test, O=example" -validity 36500
```

- Password must be 6+ characters (Finding 1).
- **Compatibility trap:** keytool from JDK 20 and later, and OpenSSL 3, write PKCS12 with PBES2/PBKDF2/AES-256 and a SHA-256 MAC. Reading that on an old JDK 17 patch level is MEDIUM confidence (not testable here). To remove the doubt, create the file with `keytool -J-Dkeystore.pkcs12.legacy ...`: verified output is RC2/3DES with a SHA-1 MAC, which every JDK reads, and JDK 21 loads it. Recommended for the checked-in file. CI on Java 17 will prove either way.
- Use an obviously named file (`test-keystore-throwaway.p12`) as the spec wants, and the README sentence about it.

### Maven wrapper (checked in)

```bash
mvn org.apache.maven.plugins:maven-wrapper-plugin:3.3.4:wrapper -Dmaven=3.10.1
git add --chmod=+x mvnw && git add mvnw.cmd .mvn/wrapper/maven-wrapper.properties
```

Result (verified): `distributionType=only-script`, `distributionUrl=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.10.1/apache-maven-3.10.1-bin.zip`, `wrapperVersion=3.3.4`. No jar, no `.gitignore` change. Do NOT use `-Dtype=bin` (adds `maven-wrapper.jar`, the thing `.gitignore` would swallow) or `-Dtype=source`. `./mvnw` ran the full build, tests and release bundle in the smoke project.

### Running the sample (optional, small)

`morimail-sample-serial` needs a way to run outside Maven for the owner's end-to-end test. Verified approach with no shading: `maven-jar-plugin` manifest (`mainClass`, `addClasspath=true`, `classpathPrefix=lib/`) plus `maven-dependency-plugin:3.11.0` `copy-dependencies` (phase `package`, `includeScope=runtime`, output `target/lib`). Then `java -jar target/morimail-sample-serial-<v>.jar send ...` works (verified with a smoke command: exit 0, picocli parsed `--rate`). Avoid `maven-shade-plugin`: it would need `ServicesResourceTransformer` for the `ServiceLoader` files and adds nothing here.

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

```bash
# Wrapper (once, with any local Maven)
mvn org.apache.maven.plugins:maven-wrapper-plugin:3.3.4:wrapper -Dmaven=3.10.1

# Everyday
./mvnw -B -ntp verify                 # offline-capable after first dependency download; no live test
./mvnw -Plive verify                  # owner only: live test, env-configured
./mvnw -Prelease verify -Dgpg.skip    # must pass without warnings
```

Library `morimail` dependencies: `com.fasterxml.jackson.core:jackson-databind` (compile); `junit-jupiter`, `assertj-core`, `wiremock` (test). Sample: `morimail`, `picocli` (compile); `morimail` test-jar, `junit-jupiter`, `assertj-core` (test).

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

---
*Stack research for: Java 17 mail library with Microsoft Graph app-only transport, published to Maven Central*
*Researched: 2026-11-21*
