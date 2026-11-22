# Phase 1: Library Core - Research

**Researched:** 2026-11-21
**Domain:** Java 17 library core (records, builder, template renderer, ServiceLoader registry, checked error tree), Maven reactor and release-shaped build, GitHub Actions CI, doclint-clean Javadoc, public-repo hygiene
**Confidence:** HIGH (design checked by running code and the JDK tools this session; JDK 17 runtime and the CI run itself are the only unproven parts)

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

#### Mail builder input shapes
- **D-01:** `to`/`cc`/`bcc`/`replyTo`/`from` each have three forms: bare `String` email, `(String email, String displayName)`, and `MailAddress`. — **Reversibility:** one-way — after the 0.1.0 Central release, removing a public overload breaks consumers.
- **D-02:** `to`/`cc`/`bcc` take varargs for the bare and `MailAddress` forms (`to(String...)`, `to(MailAddress...)`). The `(email, displayName)` form takes one named address. Repeated calls append to the list.
- **D-03:** `from()` and `replyTo()` are single-valued, and a second call replaces the first (last call wins).
- **D-04:** `template(t, data)` and an explicit `subject()`/`html()`/`text()` cannot be combined. `build()` throws `InvalidMailException` if both are set. There are no precedence rules.
- **D-05:** `build()` reports the **first** problem it finds, as one `InvalidMailException` that names it. It does not collect all problems (the sample's pre-flight aggregates across rows itself).
- **D-06:** Template rendering happens in `build()`, so a missing placeholder field surfaces there as `InvalidMailException` (success criterion 3, TMPL-04).

#### Attachment content type and name
- **D-07:** Content type is resolved in this order: (1) a small built-in extension map (about 10 entries: pdf, png, jpg/jpeg, gif, txt, html/htm, csv, zip, docx, xlsx), (2) `Files.probeContentType`, (3) `application/octet-stream` (MODEL-05). The resolved type is therefore the same on every OS and CI image.
- **D-08:** The extension match is case-insensitive using `Locale.ROOT` (`Report.PDF` → `application/pdf`).
- **D-09:** The filename defaults to `path.getFileName().toString()`.
- **D-10:** The builder offers three attach forms: `attach(Path)`, `attach(Path, String contentType)` and `attach(Attachment)` (full record for a custom name and type; `contentId` must still be null in the MVP). — **Reversibility:** one-way — these are public API once released.

#### RecordingTransport test hooks
- **D-11:** `MailAccount` has a public `transport()` accessor (it holds id, transport and defaultFrom per spec §5). A test reaches the registry-created instance with `(RecordingTransport) account.transport()`. There are no statics and no test-only hooks in the core. — **Reversibility:** costly — public accessor on a core type.
- **D-12:** Failures are scripted two ways: `failFor(email, exception)` fails any send that addresses that recipient, and `failNext(exception...)` is a queue consumed by the next sends in order, for sequences like "transient twice, then accept". Per-recipient matching ignores case (`Locale.ROOT`).
- **D-13:** `validate` is scriptable through `failValidation(InvalidMailException)`, so tests can prove that `Mailer.validate`/`send` run the transport's `validate` (CORE-05) and the Phase 2 pre-flight path.
- **D-14:** `RecordingTransport` stores accepted messages in memory per instance. It must be thread-safe (CORE-06), and its Javadoc says so even though it lives in test sources.

#### Repository docs
- **D-15:** A new **root** `CLAUDE.md` holds the spec §11 rules verbatim (Rule 0 + further rules). The existing `.claude/CLAUDE.md` stays as is (GSD-managed). Both are loaded.
- **D-16:** `SECURITY.md` names GitHub private vulnerability reporting as the primary channel (the owner enables it in the repo settings) and `stephan@morisoft.de` as the fallback.
- **D-17:** The `README.md` stub in Phase 1 is minimal: what morimail is, a pre-1.0/API-may-change notice, Maven coordinates, the accepted-not-delivered and at-least-once note (ERR-02), and "quick start coming". The test-keystore sentence is added in Phase 3 when the keystore exists. The full quick start is Phase 4.

### Claude's Discretion
- Exact method names on `RecordingTransport` beyond the shape above (e.g. `sent()` returning an immutable snapshot), and whether failed attempts are also recorded.
- Message wording of `InvalidMailException` and registry errors, as long as each names the field/placeholder/ID involved.
- Handling of duplicate account IDs in `Mailer.of(...)`: rejecting them with `IllegalArgumentException` is the expected simple choice.
- Logging (via `System.Logger`) levels and content, within the rules (never secrets or bodies; recipients at DEBUG only).
- Package placement of the registry and `BuiltInTransports`, within the import rules.

### Deferred Ideas (OUT OF SCOPE)
None. The discussion stayed within the phase scope.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| REPO-01 | Reactor parent `morimail-parent` + `morimail` + `morimail-sample-serial`, `0.1.0-SNAPSHOT`, `release` 17, all versions pinned | Standard Stack, Parent POM recipe, Pitfall 15 (unpinned plugin defaults break offline) |
| REPO-02 | Wrapper checked in, script-only, Maven 3.x | Wrapper recipe, Pitfall 14 (exec bit, CRLF) |
| REPO-03 | Central metadata in the parent | POM metadata block |
| REPO-04 | Only runtime dep `jackson-databind` 2.23.x via BOM; JUnit 6, AssertJ, WireMock | Standard Stack, Open Question 7 (when to declare WireMock) |
| REPO-05 | `Automatic-Module-Name`, no `module-info` | Pitfall 6 (put config on `default-jar` execution) |
| REPO-06 | LICENSE, README stub, SECURITY.md, root CLAUDE.md, `docs/spec.md` | Repository files section, D-15..D-17 |
| REPO-07 | CI: `mvn verify` and `mvn -Prelease verify -Dgpg.skip -pl morimail -am` (scoped per D-20), Java 17, no secrets, `contents: read` | CI workflow with verified SHA pins |
| REPO-09 | `release` profile, test-jar reactor-only, sample skips | Release profile recipe (STACK.md), sample POM properties |
| REPO-10 | Javadoc states thread safety, doclint clean | Pitfall 4 with verified doclint behaviour; thread-safety guard test |
| REPO-11 | Public-repo hygiene, identity override | Pitfalls 7 and 17, hygiene grep commands |
| REPO-12 | Import-rule JUnit test | Import-rule test approach (prototype executed) |
| MODEL-01 | Immutable records, defensive list copy, no serialization | Record design, Pattern 1 |
| MODEL-02 | Fluent builder | Builder surface and overload hazard (Pitfall 1) |
| MODEL-03 | build() rejections | Check order in Pattern 2 |
| MODEL-04 | CR/LF and single-`@` | Pattern 2, address rule |
| MODEL-05 | Default `application/octet-stream` | Content type resolution, Pitfall 10 |
| TMPL-01 | `$$name$$` renderer | Renderer prototype (executed), Pattern 3 |
| TMPL-02 | HTML escaping, raw `$$!name$$`, literal `$`/`\` | Renderer prototype, edge-case table |
| TMPL-03 | Subject/text insert, line break to space | Renderer prototype |
| TMPL-04 | Missing placeholder fails naming part and placeholder | Renderer prototype |
| CORE-01 | SPI types | Core types section |
| CORE-02 | `freshAccessToken` default | Code example, test in Validation Architecture |
| CORE-03 | Registry: built-in list + ServiceLoader, duplicate `IllegalStateException` | Pattern 4, Pitfall 5 |
| CORE-04 | `MailAccount` builder, unknown ID lists IDs | Pattern 4 |
| CORE-05 | `Mailer.of/validate/send`, unknown account | Pattern 5 |
| CORE-06 | Thread safety | Concurrency table, concurrency test |
| CORE-07 | `RecordingTransport` test-jar, `recording`, scripted failures | Pattern 6 |
| ERR-01 | Checked hierarchy | Error tree notes, Pitfall 9 |
| ERR-02 | accepted-not-delivered, at-least-once in Javadoc and README | Code examples (Javadoc text), README content |
</phase_requirements>

## Project Constraints (from CLAUDE.md)

Extracted from `.claude/CLAUDE.md` (GSD-managed, loaded) and spec §11 (becomes the root `CLAUDE.md`). Treated with the same authority as locked decisions.

- **Rule 0:** simplest code that meets the spec. Exactly two extension points (`TransportProvider`, `TokenProvider`). No further interface, SPI, factory, strategy or abstract base class. No frameworks, DI, reflection, annotation processing. No feature, option or hook the spec does not ask for. **Ask before adding any dependency.**
- Library jar runtime dependency: Jackson only. `System.Logger` for logging. JDK `HttpClient`. picocli sample only.
- Core package imports only the JDK; its one outside reference is the built-in list. `graph` and `entra` do not import each other. No Graph assumptions in the core. Transport options opaque. Limits in the transport's `validate`.
- Public repo from the first commit: no secrets, tenant IDs, real addresses, internal hosts, customer names in files, fixtures or commit messages. `example.com`/`example.org` and zero GUIDs only. Exceptions: the throwaway test PKCS12 (`changeit`) and the maintainer identity.
- Commits authored and committed as `Stephan Wald <stephan@morisoft.de>`; local git config is set but the workspace `GIT_*` environment variables override it (memory note).
- Never send real mail, never publish/deploy/tag.
- Public types get Javadoc stating thread safety; Javadoc builds cleanly. `Mailer`, transports, token providers safe for concurrent use.
- Never log tokens, secrets, assertions or full bodies; recipients at DEBUG only.
- `mvn verify` passes offline; each unit of work is committed with `mvn verify` green.
- GSD workflow: file edits go through a GSD command (planned phase work uses `/gsd-execute-phase`).

## Summary

Phase 1 is a plain-Java design problem plus a build-hardening problem, and almost nothing in it needs a library. The core is five records, a six-class checked error tree, one regex renderer, one `ServiceLoader` registry, and two small final classes (`MailAccount`, `Mailer`). Everything was prototyped or probed this session: the renderer edge cases, the import-rule scan, the doclint behaviour for records and nested builders, and the Maven Central versions. The project-level research already settled versions and the release profile, so this document concentrates on the decisions that research left open.

Five findings change how the plan should be written. (1) **D-01 plus D-02 create a silent overload trap:** `to("a@example.org", "b@example.org")` resolves to the `(email, displayName)` overload, not to varargs, and the second address becomes a display name. This was reproduced with `javac`. A mitigation that stays inside the locked decisions is given (Pitfall 1). (2) `template(template, record)` with `Map<String, Object>` rejects a `Map<String,String>` CSV row at compile time; the parameter should be `Map<String, ?>`. (3) Doclint on JDK 21 warns on every public builder method without `@param`/`@return`, on declared `throws` without `@throws`, on explicit compact constructors without a comment, and on public classes with an implicit constructor; this was run, and the builder's roughly 23 public methods all need full Javadoc. (4) The empty sample jar produces a `JAR will be empty` warning (reproduced), so "zero WARNING lines" is not a usable Phase 1 gate for the whole reactor. (5) Two committed research files contain absolute workstation paths, and one pre-existing commit has a different author address; both are REPO-11 items to fix or decide.

**Primary recommendation:** Build the reactor and CI first with `-Prelease clean verify -Dgpg.skip -pl morimail -am` (scoped to the published modules, superseding the unscoped form per D-20) green from the first commit, then add the core in dependency order (errors and records, SPI, renderer, builder, registry/account/mailer, `RecordingTransport`), running the release profile after every Javadoc-bearing task. Keep `TemplateRenderer`, `TransportRegistry` and `BuiltInTransports` package-private, use `Map<String, ?>` for template data, and add a mechanical import-rule test and a thread-safety-Javadoc test.

## Architectural Responsibility Map

This is a library plus build, so "tier" means package or build layer rather than browser/server.

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Mail model records, builder validation | core `de.morisoft.morimail` | — | Transport-neutral checks only (spec §5.1); no Jackson, no Graph |
| Template rendering | core (package-private `TemplateRenderer`) | — | Called only from `Mail.Builder.build()` (D-06) |
| Transport lookup and duplicate detection | core (package-private `TransportRegistry`) | `BuiltInTransports` list | Spec §2: one registry, two sources |
| Applying `defaultFrom`, running `validate` then `send` | core `Mailer` | transport `validate` | Limits stay in the transport (spec §2) |
| Error taxonomy | core | — | Shared by every transport and by the sample |
| Test transport `recording` | library test sources, shipped as test-jar | sample test classpath | Registered like an external transport; never in the main jar |
| Import rules | library test (`ImportRulesTest`) | — | Plain JUnit, no ArchUnit (dependency) |
| Release shape (sources, Javadoc, GPG, publishing, test-jar `attach=false`) | parent POM `release` profile | library POM, sample POM overrides | Spec §13.3 |
| CI | `.github/workflows/ci.yml` | wrapper | No secrets, `contents: read` |
| Repo hygiene and identity | repo root files, git config | `.gitignore`, `.gitattributes` | Public repo from the first commit |

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Java `--release 17` | 17 | Language level | Records, `System.Logger`; local machine has only JDK 21, so CI on 17 is the proof [VERIFIED: `java -version` printed 21.0.13.1; no JDK 17 under /opt/java or /usr/lib/jvm] |
| Maven via wrapper | 3.10.1, `only-script` | Build | maven.apache.org current line; Maven 4 never [CITED: STACK.md]. Wrapper distribution 3.10.1 is already cached locally [VERIFIED: `ls ~/.m2/wrapper/dists` showed `apache-maven-3.10.1`] |
| `com.fasterxml.jackson.core:jackson-databind` via `jackson-bom` | 2.23.0 | Only runtime dep (unused by the core until Phase 3) | [VERIFIED: repo1.maven.org, `maven-metadata.xml` latest/release 2.23.0; POM Last-Modified 2026-11-08] |

### Supporting (test scope unless noted)
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `org.junit.jupiter:junit-jupiter` via `junit-bom` | 6.1.3 | Tests (spec says 5; current line is 6) | Always. Metadata `latest` is `6.2.0-M1` (milestone, do not use) [VERIFIED: repo1.maven.org; POM Last-Modified 2026-08-07] |
| `org.assertj:assertj-core` | 3.27.7 | Assertions | Always. `4.0.0-M1` is a milestone [VERIFIED: repo1.maven.org; Last-Modified 2026-01-24] |
| `org.wiremock:wiremock` | 3.13.2 | Fake Graph/token endpoints | Phase 3 first use. `4.0.0-beta.44` is `latest` in metadata, not stable [VERIFIED: repo1.maven.org; Last-Modified 2025-11-14] |
| picocli | 4.7.7 | Sample CLI | Phase 2 first use; do not declare in Phase 1 |

### Build plugins (pin all in `pluginManagement`)
| Plugin | Version | Verified this session |
|--------|---------|-----------------------|
| maven-compiler-plugin | 3.16.0 | POM exists, Last-Modified 2026-08-30; `latest` is the unstable `4.0.0-beta-5` |
| maven-surefire-plugin | 3.6.1 | metadata release 3.6.1 |
| maven-jar-plugin | 3.5.1 | POM exists, Last-Modified 2026-07-19; `latest` is `4.0.0-beta-2` |
| maven-source-plugin | 3.4.0 | POM exists, Last-Modified 2025-11-22 |
| maven-javadoc-plugin | 3.12.0 | metadata release 3.12.0 |
| maven-gpg-plugin | 3.2.8 | POM exists, Last-Modified 2025-06-28 |
| org.sonatype.central:central-publishing-maven-plugin | 0.12.0 | metadata release 0.12.0 |
| maven-resources-plugin | 3.5.0 | POM exists (metadata `release` is `4.0.0-beta-2`; pin 3.5.0) |
| maven-clean-plugin / maven-install-plugin / maven-deploy-plugin | 3.5.0 / 3.2.0 / 3.2.0 | POMs exist |
| maven-wrapper-plugin | 3.3.4 | metadata release 3.3.4 |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Hand-written import-rule test | ArchUnit | Extra dependency; Rule 0 says ask first. The scan is about 40 lines (prototype below) |
| `Map<String, Object>` for data | `Map<String, ?>` | Wildcard accepts CSV rows typed `Map<String,String>`; same behaviour; recommended (Open Question 2) |
| `synchronized` in `RecordingTransport` | `CopyOnWriteArrayList` + concurrent queue | The "consume a scripted failure or accept" step needs atomicity; one lock is simpler and obviously correct for a test fixture |

**Installation:** none beyond Maven; the wrapper is generated once:
```bash
mvn org.apache.maven.plugins:maven-wrapper-plugin:3.3.4:wrapper -Dmaven=3.10.1
git add --chmod=+x mvnw && git add mvnw.cmd .mvn/wrapper/maven-wrapper.properties
```

**Version verification:** all rows above were confirmed on `repo1.maven.org` on 2026-11-21 (metadata files and POM `Last-Modified`). Re-check at implementation time per spec §3.

## Package Legitimacy Audit

The `package-legitimacy` seam supports `npm|pypi|crates` only; running it with a Maven ecosystem returned `Usage: gsd-tools package-legitimacy check --ecosystem <npm|pypi|crates> ...` [VERIFIED: tool output]. The Maven coordinates below were taken from the binding spec and `.claude/CLAUDE.md` (not discovered by search), exist on Maven Central with the version and date shown above, and are well-known groupIds. No postinstall mechanism exists for Maven artifacts.

| Package | Registry | Age | Downloads | Source Repo | Verdict | Disposition |
|---------|----------|-----|-----------|-------------|---------|-------------|
| com.fasterxml.jackson.core:jackson-databind | Maven Central | years | not queried | github.com/FasterXML/jackson-databind | seam unsupported; names from spec | Approved (spec allowlist) |
| org.junit.jupiter:junit-jupiter | Maven Central | years | not queried | github.com/junit-team/junit5 | seam unsupported; names from spec | Approved |
| org.assertj:assertj-core | Maven Central | years | not queried | github.com/assertj/assertj | seam unsupported; names from spec | Approved |
| org.wiremock:wiremock | Maven Central | years | not queried | github.com/wiremock/wiremock | seam unsupported; names from spec | Approved (first used Phase 3) |

**Packages removed due to SLOP verdict:** none. **Packages flagged SUS:** none.
These Maven artifacts are tagged `[ASSUMED]` for provenance only in the sense that the seam could not grade them; the planner does not need a `checkpoint:human-verify` because the coordinates come from the owner's spec and `CLAUDE.md`, not from a search.

## Architecture Patterns

### System Architecture Diagram

```
                       build time (Maven reactor, CI)
 ┌──────────────────────────────────────────────────────────────────────────┐
 │ parent pom ──┬─ morimail (jar, test-jar)  ──release profile──► -sources, -javadoc,
 │              │      src/main: core only          (attach=false on test-jar)    .asc (skipped in CI)
 │              │      src/test: tests + RecordingTransport + META-INF/services
 │              └─ morimail-sample-serial (jar, never published)
 │                     test dep: morimail test-jar (reactor only)
 └──────────────────────────────────────────────────────────────────────────┘

                       run time (consumer or test)
 consumer ──► Mail.using(id) ──► Mail.Builder ──build()──► Mail(accountId, MailMessage)
                                   │  1 id, template-vs-explicit conflict (D-04)
                                   │  2 TemplateRenderer (D-06) ── missing field ─► InvalidMailException
                                   │  3 recipients, subject, body, address rules, attachments, contentId
                                   ▼
 consumer ──► MailAccount.builder(id).transport("recording").auth(..).option(..).defaultFrom(..).build()
                                   │
                                   ▼
                       TransportRegistry.load()
                         BuiltInTransports.providers()   (empty in Phase 1)
                         ServiceLoader.load(TransportProvider.class)
                         ├─ duplicate id ─► IllegalStateException   (at load)
                         ├─ ServiceConfigurationError ─► IllegalStateException
                         └─ unknown id ─► IllegalArgumentException "Available: ..."
                                   │ provider.create(Map.copyOf(options), auth)
                                   ▼
                           MailAccount(id, MailTransport, defaultFrom)
 consumer ──► Mailer.of(account..).send(mail)
                 prepare: account by id ── none ─► PermanentMailException
                          apply defaultFrom ── still no sender ─► InvalidMailException
                          transport.validate(message)         ── InvalidMailException
                 then   : transport.send(message)             ── MailException (scripted in tests)
```

### Recommended Project Structure
```
pom.xml                                     parent: metadata, BOMs, pluginManagement, release profile
.mvn/wrapper/maven-wrapper.properties       only-script, Maven 3.10.1
mvnw  mvnw.cmd                              mvnw committed with exec bit
.gitattributes                              mvnw text eol=lf ; *.cmd text eol=crlf
.gitignore                                  add target/, out/, .idea/, *.iml, *.jsonl, account.properties, *.pfx
.github/workflows/ci.yml
LICENSE (exists)  README.md  SECURITY.md  CLAUDE.md (root, spec §11 verbatim)
docs/spec.md                                moved from root (untracked today)
morimail/
  pom.xml                                   jackson-databind, junit-jupiter, assertj; jar + test-jar; release profile test-jar attach=false
  src/main/java/de/morisoft/morimail/
    MailAddress Attachment MailMessage Mail(+Builder) MailTemplate        public
    MailException TransientMailException PermanentMailException
    InvalidMailException SenderRejectedException AuthRequiredException    public
    MailTransport TransportProvider TokenProvider                          public
    MailAccount(+Builder) Mailer                                           public
    TemplateRenderer TransportRegistry BuiltInTransports                   package-private
  src/test/java/de/morisoft/morimail/        unit tests (same package, so package-private is testable)
  src/test/java/de/morisoft/morimail/test/   RecordingTransport, RecordingTransportProvider
  src/test/resources/META-INF/services/de.morisoft.morimail.TransportProvider
morimail-sample-serial/
  pom.xml                                   skip props; test-jar test dep; no picocli yet
  src/test/java/.../RecordingTestJarSmokeTest.java   (recommended, Open Question 8)
```

### Pattern 1: Records plus defensive copy (MODEL-01)
**What:** `MailMessage` compact constructor does `to = List.copyOf(to)` (and `cc`, `bcc`, `attachments`). Records implement no `Serializable`, hold no behaviour beyond `Mail.using`.
**When to use:** all five records. `MailAddress`, `Attachment`, `Mail`, `MailTemplate` need no compact constructor.
**Notes:** a null list throws a bare `NullPointerException` from `List.copyOf`. That is acceptable (Rule 0; records are built by the builder). Every record needs `@param` for each component (verified: missing ones warn). An explicitly declared compact constructor needs its own Javadoc comment (verified: `warning: no comment`).

### Pattern 2: `Mail.Builder.build()` check order (D-04, D-05, D-06)
Report the first problem as one `InvalidMailException`. Use a fixed order so tests are deterministic:
1. `accountId` null or blank.
2. Template set together with an explicit subject/html/text (D-04). Track "template used" and "explicit used" as booleans.
3. Render the template (D-06). A missing field raises the renderer's message (TMPL-04).
4. At least one recipient across to, cc, bcc.
5. Subject present and not blank.
6. html or text present and not blank.
7. Every address in from, to, cc, bcc, replyTo: email non-null, exactly one `@`, no CR/LF in email or display name.
8. Subject has no CR or LF (applies to a literal template subject too; a rendered value cannot contain one because of the guard).
9. Each attachment: file exists, is a regular file, is readable; `contentId` null; resolve default filename and content type (after the existence check, because `probeContentType` may touch the file).

Resolve content type in `build()`, not in `attach(...)`, so `attach(Path)` on a missing path fails with the builder's own message and not with an `IOException` from the probe.

### Pattern 3: Renderer as one manual find loop (TMPL-01..04)
Executed prototype, all rows verified by running it (JDK 21, `--release 17` API only):
```java
// Source: prototype run this session; package-private final class TemplateRenderer
private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\$(!?)([A-Za-z0-9_.-]+)\\$\\$");

static String render(Part part, String template, Map<String, ?> data) throws InvalidMailException {
    Matcher m = PLACEHOLDER.matcher(template);
    StringBuilder out = new StringBuilder(template.length() + 32);
    int last = 0;
    while (m.find()) {
        out.append(template, last, m.start());
        String name = m.group(2);
        if (!data.containsKey(name)) {
            throw new InvalidMailException("Missing data field '" + name + "' for placeholder "
                    + m.group() + " in the " + part + " template");   // never echo the data map
        }
        String value = String.valueOf(data.get(name));
        boolean raw = !m.group(1).isEmpty();
        switch (part) {
            case HTML -> out.append(raw ? value : escapeHtml(value));
            case SUBJECT -> out.append(value.replaceAll("\\R", " "));   // literal replacement, no $ risk
            case TEXT -> out.append(value);
        }
        last = m.end();
    }
    return out.append(template, last, template.length()).toString();
}
// escapeHtml: char switch: & -> &amp;  < -> &lt;  > -> &gt;  " -> &quot;  ' -> &#39;
```
`Part` is a private nested enum of `TemplateRenderer` (not a new public type).

Edge cases pinned by the run (these become the `TemplateRendererTest` rows):

| Template (HTML part) | Output | Note |
|---|---|---|
| `$$a$$$$b$$` | `AB` | adjacent placeholders |
| `$$$name$$` | `$` + escaped value | the leading `$` is plain text |
| `$$a$$b$$` | `Ab$$` | trailing `b$$` is plain text |
| `$$$$`, `$$ $$`, `$$a b$$`, `$$!$$`, `lone $$` | unchanged | not placeholders |
| `$$name$$` with value containing `< > " ' & $1 \ C:\temp` | all escaped, `$1` and `\` literal | no replacement-syntax interpretation |
| `$$!name$$` | raw value | `!` is part of the delimiter |
| `$$nested$$` with value `$$a$$` | `$$a$$` | inserted values are never re-scanned |
| `$$nul$$` with a present-but-null value | `null` | `String.valueOf`; pin it in a test (Open Question 4) |
| `$$5$$`, `price $$20$$` | digits-only names match and fail "missing field" | by spec; document, do not widen |
| Subject `Hi $$multi$$ end`, value `x\r\ny\nz` | `Hi x y z end` | `\R` treats CRLF as one break; same for `$$!multi$$` |

### Pattern 4: Registry, account, and error behaviour (CORE-03, CORE-04)
```java
// Source: adapted from .planning/research/ARCHITECTURE.md Pattern 1, hardened this session
final class TransportRegistry {
    private final Map<String, TransportProvider> byId;                 // TreeMap: stable, sorted message

    static TransportRegistry load() {
        try {
            return of(BuiltInTransports.providers(), ServiceLoader.load(TransportProvider.class));
        } catch (ServiceConfigurationError e) {                         // an Error, not an Exception
            throw new IllegalStateException("Cannot load a transport provider: " + e.getMessage(), e);
        }
    }
    static TransportRegistry of(List<TransportProvider> builtIn, Iterable<TransportProvider> external) {
        Map<String, TransportProvider> map = new TreeMap<>();
        for (TransportProvider p : builtIn) put(map, p, null);
        for (TransportProvider p : external) put(map, p, builtIn);      // iteration happens here, inside the try
        return new TransportRegistry(map);
    }
    // put(): id null/blank -> IllegalStateException naming p.getClass(); putIfAbsent collision ->
    //   IllegalStateException "Transport id 'x' from A is already taken by B" (+ "(built-in; ...)" when applicable)
    TransportProvider find(String id) {
        TransportProvider p = byId.get(id);
        if (p == null) throw new IllegalArgumentException(
                "Unknown transport '" + id + "'. Available: " + String.join(", ", byId.keySet()));
        return p;
    }
}
```
- Build a fresh registry per `MailAccount.Builder.build()`; no static state, no static initializer.
- `MailAccount.Builder.build()`: missing transport ID → `IllegalArgumentException`; options copied with `Map.copyOf` (null values rejected with `NullPointerException`; document); `defaultFrom` validated with the same address rule as the builder, failing with `IllegalArgumentException` (it is configuration, and `defaultFrom` bypasses `Mail.Builder.build()`).
- Tests of the duplicate rule call `TransportRegistry.of(List.of(a), List.of(b))` directly. Never add a second `META-INF/services` entry for a failing provider (it would break every test that builds an account).

### Pattern 5: `Mailer` with one shared `prepare` (CORE-05)
```java
// Source: derived from spec §5.1/§5.2 and ARCHITECTURE.md data flow
public void validate(Mail mail) throws PermanentMailException { prepare(mail); }
public void send(Mail mail) throws MailException {
    Prepared p = prepare(mail);
    p.account().transport().send(p.message());
}
private Prepared prepare(Mail mail) throws PermanentMailException {
    MailAccount account = accounts.get(mail.accountId());
    if (account == null) throw new PermanentMailException("Unknown account '" + mail.accountId()
            + "'. Known: " + String.join(", ", new TreeSet<>(accounts.keySet())));
    MailMessage m = mail.message();
    if (m.from() == null) {
        MailAddress from = account.defaultFrom().orElseThrow(() -> new InvalidMailException(
                "No sender: set from() on the mail or defaultFrom() on account '" + account.id() + "'"));
        m = withFrom(m, from);
    }
    account.transport().validate(m);                          // InvalidMailException
    return new Prepared(account, m);
}
private record Prepared(MailAccount account, MailMessage message) {}   // private nested type, no Javadoc needed
```
`validate` declares `throws PermanentMailException`, which covers both the unknown account and `InvalidMailException` (a subclass). `Mailer` holds `Map.copyOf` of accounts; `Mailer.of` rejects duplicate account IDs with `IllegalArgumentException` (discretion item, recommended).

### Pattern 6: `RecordingTransport` (CORE-07, D-11..D-14)
Two public top-level classes in `de.morisoft.morimail.test` (a public provider needs its own file for `ServiceLoader`): `RecordingTransport implements MailTransport` and `RecordingTransportProvider implements TransportProvider` with `id()` returning `"recording"`. Services file content is the single line `de.morisoft.morimail.test.RecordingTransportProvider`.

Recommended surface (names are discretion):
```java
public List<MailMessage> sent();                     // accepted messages only, List.copyOf snapshot
public int attempts();                               // every send() call including failed ones
public void failFor(String email, MailException e);  // persistent; matches to/cc/bcc, Locale.ROOT lower-case
public void failNext(MailException... failures);     // FIFO queue, one entry per send
public void failValidation(InvalidMailException e);  // persistent until called with null
```
Rules to pin in Javadoc and tests: in `send`, a matching `failFor` wins and does not consume the queue; otherwise the head of the `failNext` queue is consumed and thrown; otherwise the message is recorded. `attempts` increments first. All state guarded by one `synchronized` lock (no I/O inside). The class Javadoc states thread safety.

The provider returns a new `RecordingTransport` per `create`, so each `MailAccount.build()` gets its own instance and tests read it with `(RecordingTransport) account.transport()` (D-11). No statics.

### Pattern 7: Import-rule test (REPO-12), no dependencies
Prototype executed against a fake tree (comments ignored, FQN usage caught, `BuiltInTransports` allowed to reach `graph`, `graph` → `entra` caught):
```java
// Source: prototype run this session
static final Pattern IMPORT = Pattern.compile("^\\s*import\\s+(static\\s+)?([\\w.]+?)(\\.\\*)?\\s*;", Pattern.MULTILINE);
static final String BASE = "de.morisoft.morimail";

String code(Path p) throws IOException {                       // crude comment strip is enough here
    return Files.readString(p).replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\n]*", "");
}
// Rule 1: for each *.java directly in src/main/java/de/morisoft/morimail (maxDepth 1):
//   every import starts with "java." or "javax.", except in BuiltInTransports.java which may also
//   import BASE + ".graph."; and no file except BuiltInTransports mentions BASE + ".graph" or ".entra"
//   (this catches fully qualified use without an import).
// Rule 2: no file under .../graph mentions BASE + ".entra".
// Rule 3: no file under .../entra mentions BASE + ".graph".
// Guards: assert the core file list is non-empty and contains "Mailer.java" (no vacuous pass);
//         tolerate graph/ and entra/ not existing yet (Files.isDirectory).
```
Surefire runs with the module directory as the working directory, so `Path.of("src/main/java/de/morisoft/morimail")` resolves [ASSUMED: Surefire default `workingDirectory` is `${basedir}`; confirm on the first run]. The test must not import anything from `graph` or `entra` itself.

A companion `PublicTypesDocumentThreadSafetyTest` (recommended, ~25 lines) scans main sources for public type declarations and requires the nearest preceding Javadoc block to match `(?i)thread|immutable`. It turns REPO-10's "states thread safety on every public type" into a failing build rather than a review item.

### Pattern 8: Parent POM, release profile, sample POM
Copy from `.planning/research/STACK.md` (executed there, "Parent POM skeleton", "Release profile", "Maven wrapper", "Publishing a test-jar and consuming it in the sample"). Phase 1 specifics:
- `<modules>`: `morimail`, `morimail-sample-serial`. Children name the parent with an explicit `<version>0.1.0-SNAPSHOT</version>`; no `${revision}`.
- Metadata in the parent: `name`, `description`, `url` `https://github.com/morisoft-lib/morimail`, MIT license, developer `Stephan Wald` / `stephan@morisoft.de`, `scm` (connection, developerConnection, url, tag).
- Library `maven-jar-plugin`: put `Automatic-Module-Name` on the `default-jar` execution only, so the test-jar does not also claim the module name. `test-jar` execution (goal `test-jar`). A `release` profile in the library POM overrides that execution with `<attach>false</attach>`.
- Sample POM properties, all `true`: `maven.deploy.skip`, `maven.source.skip`, `maven.javadoc.skip`, `gpg.skip`, `skipPublishing`. Test dependency on `de.morisoft:morimail` type `test-jar`.
- Defer the `live` profile, the `excludedGroups`/`groups` properties and any `@Tag("live")` test to Phase 3 (REPO-08 is mapped there; nothing exercises them now).

### CI workflow (REPO-07)
Action SHAs resolved from the GitHub API this session (`actions/checkout` v7.0.1 and `actions/setup-java` v6.3.0 are the latest releases; both tags are commit tags):
```yaml
name: CI
on:
  push:
  pull_request:
permissions:
  contents: read
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1   # v7.0.1
      - uses: actions/setup-java@d1374f8ac74474200d16ef8ecee22c7ae7a51880 # v6.3.0
        with:
          distribution: temurin
          java-version: '17'
          cache: maven
      - run: ./mvnw -B -ntp verify
      # release build scoped per D-20 (01-CONTEXT.md): an unattached test-jar breaks a full-reactor release build
      - run: ./mvnw -B -ntp -Prelease clean verify -Dgpg.skip -pl morimail -am
      # warmed-cache check that Maven needs no remote resolution (D-18), not network isolation
      - run: ./mvnw -B -ntp -o verify
```
No `pull_request_target`, no secrets, no live test. The wrapper must be committed with the executable bit (`git ls-files --stage mvnw` shows `100755`), otherwise CI fails with `Permission denied`.

### Anti-Patterns to Avoid
- **`appendReplacement`/`replaceAll(String)` with a data value:** `$` and `\` become replacement syntax (Pitfall 2).
- **Static registry or `ServiceLoader` in a static initializer:** class-init failures and test pollution.
- **A second services file that makes loading fail:** poisons every test; pass lists to `TransportRegistry.of`.
- **Jackson, or any non-JDK import, in the core:** the import rule fails the build.
- **`{@link}` to `RecordingTransport` from main Javadoc:** it exists only in test sources; doclint reports `reference not found`. Use `{@code}`.
- **Re-validating inside records' constructors:** violates "plain records" and moves failure from `build()` to `to()`.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Placeholder substitution | `appendReplacement`, per-field `String.replace`, a template engine | One `Pattern` + manual `find()` loop appending literals | Value text is never interpreted or re-scanned (prototype verified) |
| Address syntax validation | RFC 5322 parser, Jakarta `InternetAddress` | Exactly-one-`@` check plus CR/LF rejection (spec G5) | Dependency is forbidden; the server validates the rest |
| Architecture rules | ArchUnit | A plain JUnit source scan | No dependency; the rules are three string checks |
| Provider discovery | Custom classpath scanning, reflection | `java.util.ServiceLoader` | JDK, spec §2 |
| Content type database | Large extension table | 10-entry map, then `Files.probeContentType`, then `application/octet-stream` | Determinism, not completeness (CONTEXT specifics) |
| Immutable list copy | Own wrapper | `List.copyOf` | JDK |
| Test double framework | Mockito | A hand-written `RecordingTransport` | Spec §10.1; Mockito is not allowed |
| Release metadata checks | A custom script | Maven Central rules via the `release` profile and the bundle rehearsal in Phase 4 | Plugin already generates checksums |

**Key insight:** every "problem" in this phase is under 50 lines of JDK code, and the risk is in the details (overload resolution, regex replacement syntax, doclint strictness), not in missing libraries.

## Common Pitfalls

### Pitfall 1: `to("a@example.org", "b@example.org")` silently becomes a display name (D-01 + D-02)
**What goes wrong:** with both `to(String...)` and `to(String email, String displayName)`, a two-argument call with two Strings resolves to the named overload (Java picks non-varargs methods in phases 1 and 2, before varargs in phase 3). The second address becomes a display name; no address is rejected.
**Verified:** `javac --release 17` + run printed `named:a@example.org|b@example.org` for two string arguments, `varargs:1` for one, `varargs:3` for three, `varargs:2` for a `String[]` [VERIFIED: executed this session].
**How to avoid (stays inside the locked decisions):** (a) keep the overloads exactly as D-01/D-02 say; (b) in `build()`, reject a display name that contains `@` with a message such as `display name 'x' looks like an email address; to add several recipients pass one address per call or use MailAddress...`; (c) put a `<strong>` warning in the Javadoc of the named overload; (d) add a `MailBuilderTest` row that asserts the guard fires. This is a one-line rule, stays within Rule 0, and turns a silent wrong-recipient bug into a loud `InvalidMailException`. Flagged for the owner in Open Question 1 because the cleaner fix (a different method name for the named form) would change a locked decision.
**Warning signs:** a display name that looks like an address in dry-run output; a campaign mail with one recipient fewer than expected.
**Superseded (2026-11-21):** the owner revised D-01 and dropped the `(String email, String displayName)` overload, so this hazard no longer exists and the guard in "How to avoid" is not built. See Open Question 1 (RESOLVED).

### Pitfall 2: Regex replacement syntax corrupts data values (TMPL-02)
**What goes wrong:** `Matcher.appendReplacement`/`replaceAll` interpret `$1` and `\` in the value; a price, a Windows path or `$5 Fund` throws or loses characters.
**How to avoid:** the manual loop above. Test rows must include values with `$`, `$1`, `\`, `C:\temp`, `&`, `<`, `"`, `'`, and a value equal to `$$other$$`.

### Pitfall 3: `Map<String, Object>` rejects `Map<String, String>`
**What goes wrong:** the CSV row the sample passes is naturally `Map<String,String>`; `template(MailTemplate, Map<String,Object>)` does not compile for it.
**How to avoid:** declare `template(MailTemplate template, Map<String, ?> data)` and `render(..., Map<String, ?>)`. Behaviour is identical (values go through `String.valueOf`). Record the deviation from spec §5.3 in `docs/architecture.md` (Phase 4).

### Pitfall 4: Doclint is strict, and JDK 21 is stricter than 17 (REPO-10)
Verified by running `javadoc -Xdoclint:all --release 17` on JDK 21:

| Source construct | Result |
|---|---|
| Record without `@param` for each component | `warning: no @param for a` (one per component) |
| Explicit compact constructor with no comment | `warning: no comment` |
| Public method on a public nested `Builder` with no comment | `warning: no comment` |
| Public method with a comment but no `@return` | `warning: no @return` |
| Declared `throws X` with no `@throws X` | `warning: no @throws for java.lang.Exception` |
| Public class with implicit constructor | `warning: use of default constructor, which does not provide a comment` |
| `List<String>` outside `{@code}`, raw `&` or `<` | `error: unknown tag`, `error: bad HTML entity`, `error: element not closed` |
| `{@link}` to a missing class | `error: reference not found` |
| `@throws` for an exception the method does not declare | `error: exception not thrown` |
| Missing `package-info.java` | no warning |
| Inline email address `a@example.org` mid-line | no warning |

**How to avoid:** the plan needs an explicit Javadoc pass: about 23 public builder methods (3 forms each for to/cc/bcc/replyTo/from, subject, html, text, template, 3 attach forms, build) each with a sentence, `@param` and `@return`. Give every public class a private constructor (static-factory classes `Mailer`, `MailAccount`) or an explicit documented one (exceptions). Use `{@code ...}` for generics and `&amp;`. Run `./mvnw -Prelease clean verify -Dgpg.skip -pl morimail -am` (scoped per D-20) after every Javadoc-bearing task, not only at the end. Local JDK is 21 and CI is 17; the 17 javadoc is believed to be less strict on default constructors [ASSUMED], so passing locally implies passing in CI. The `failOnWarnings` flag lives in the parent's `release` profile, so plain `verify` never shows these.

### Pitfall 5: Registry tests that poison each other
`RecordingTransportProvider` is on every library test's classpath through `src/test/resources/META-INF/services/de.morisoft.morimail.TransportProvider`. Test duplicates with `TransportRegistry.of(...)` and anonymous providers. The unknown-ID assertion must use `contains("recording")`, not equality against the whole list, because Phase 3 adds `graph`. A broken provider surfaces as `ServiceConfigurationError` (an `Error`): catch it in `load()` and rethrow as `IllegalStateException`. Keep the built-in list out of `ServiceLoader`, or "built-in resolves with no services entry" proves nothing (Phase 3).

### Pitfall 6: Test-jar packaging
- A plain `test-jar` execution attaches `-tests.jar` and the Central plugin would stage it. Fix: library `release` profile with `<attach>false</attach>` on the `test-jar` execution [CITED: STACK.md "Findings That Change the Plan" 2, executed there].
- `Automatic-Module-Name` configured at plugin level lands in both jars; configure it on the `default-jar` execution.
- The sample needs `<type>test-jar</type><scope>test</scope>`, versions from `dependencyManagement`. Verified in the smoke project; recommended to re-prove in Phase 1 with a sample smoke test (Open Question 8).
- **Correction (2026-11-21, plan-checker iteration 1, see D-20 in `01-CONTEXT.md`):** with `attach=false`, a full-reactor `-Prelease` build cannot resolve the test-jar. It fails with "Could not find artifact ...:jar:tests" on Maven 3.10.1 and 3.9.16, even with the sample's tests skipped. Release builds are therefore scoped to `-pl morimail -am`, and the sample's use of the test-jar is proven in the default build.

### Pitfall 7: Workspace `GIT_*` environment overrides the repo identity
The workspace exports author and committer email variables that win over local config. Every commit command, including GSD `query commit` calls and subagents, must be prefixed with `export GIT_AUTHOR_EMAIL=stephan@morisoft.de GIT_COMMITTER_EMAIL=stephan@morisoft.de` [CITED: project memory note; VERIFIED: `env | grep GIT_` shows the overriding email variables]. Check with `git log --format='%an <%ae> | %cn <%ce>'`. Existing history: the earliest commit (`8c9b082`, the GitHub-created initial LICENSE commit) has a different author address and a GitHub committer; all later commits are correct [VERIFIED: `git log`]. Rewriting it is the owner's call (Open Question 5).

### Pitfall 8: Import-rule test passes vacuously or flags comments
Strip comments before scanning (Javadoc `{@link ...graph...}` would otherwise fail the build), assert the core file list is non-empty, and scan for the fully qualified name as well as `import` lines (the architecture sketch uses a fully qualified reference in `BuiltInTransports`, which a pure import scan would miss).

### Pitfall 9: `Optional` field in an exception
`Exception` is `Serializable`; `Optional` is not. Store `retryAfter` as a nullable `Duration` field and expose `Optional<Duration> retryAfter()` as ERR-01 requires. Constructors: `(String)`, `(String, Throwable)`, and for `TransientMailException` additionally the retry-after variant. Keep all six classes at those shapes; nothing Graph-specific.

### Pitfall 10: Content type resolution varies by OS and may throw
`Files.probeContentType` is platform-dependent and can throw `IOException` or return null; run it only after the extension map misses and the file is known to exist; catch `IOException` and fall back to `application/octet-stream`. Lower-case the extension with `Locale.ROOT` (D-08). Extract the extension from the last `.`; a name with no dot or a trailing dot has none.

### Pitfall 11: "Unreadable attachment" tests that pass as root
`Files.isReadable` is true for root even at mode 000. Test missing file, directory, and a not-a-regular-file case unconditionally; guard the permission-denied variant with `Assumptions.assumeTrue(!Files.isReadable(file))` after `setReadable(false)`.

### Pitfall 12: `System.Logger` parameterised messages use `MessageFormat`
With parameters, `'` is an escape and `{0}` is a placeholder; an apostrophe in a message swallows text. Avoid apostrophes in parameterised messages, guard with `isLoggable`, and never pass tokens, secrets or bodies. Recipients only at `DEBUG`. Phase 1 needs at most one `DEBUG` line in `Mailer` (account ID and recipient count).

### Pitfall 13: No sender at all
If neither the mail nor the account has a sender, no transport can send. `Mailer.prepare` raises `InvalidMailException` naming the account. This is a core-generic rule, not a Graph assumption.

### Pitfall 14: Wrapper and line endings
`mvnw` needs the exec bit in git (`git add --chmod=+x mvnw`) and LF endings (`.gitattributes`: `mvnw text eol=lf`, `*.cmd text eol=crlf`). Use `only-script`; the `.gitignore` rule `*.jar` would swallow a `maven-wrapper.jar`.

### Pitfall 15: Unpinned plugin defaults break the offline claim
Running a bare project offline under system Maven 3.9.16 failed because the default `maven-surefire-plugin:3.5.4` was not in the local repository [VERIFIED: executed this session]. Pin every plugin (including `resources`, `clean`, `install`, `deploy`, `surefire`) in `pluginManagement`. "Offline" means the dependencies are already cached; the wrapper's Maven distribution must be cached too.

### Pitfall 16: The empty sample jar warns
A jar module with no main content logs `[WARNING] JAR will be empty - no content was marked for inclusion!` [VERIFIED: executed this session]. Do not gate Phase 1 on "zero WARNING lines" for the whole reactor; gate on `BUILD SUCCESS` and Javadoc `failOnWarnings` for the library. Phase 4's DOCS-04 warning-free check runs after the sample has classes.

### Pitfall 17: Public-repo hygiene already has two findings
`.planning/research/ARCHITECTURE.md` line 551 and `.planning/research/PITFALLS.md` line 562 contain absolute workstation paths with the home directory [VERIFIED: `git grep -nI '/home/[a-z]'`]. Scrub them (replace with repo-relative paths) in the first hygiene task. Run the three greps in the Validation Architecture before the first publish.

### Pitfall 18: `-Dtest` in a multi-module build
Selecting one test class with `-pl morimail -Dtest=X` is fine; at the reactor root a module with no match fails unless `-Dsurefire.failIfNoSpecifiedTests=false` is added [ASSUMED: Surefire behaviour from experience; confirm on first use].

## Code Examples

### `TokenProvider` with the frozen default (CORE-01, CORE-02)
```java
// Source: spec.md:141-144 (signature) + ARCHITECTURE.md "The 401 problem" option E (default method)
@FunctionalInterface
public interface TokenProvider {
    String accessToken(String scope) throws MailException;

    default String freshAccessToken(String scope, String rejectedToken) throws MailException {
        return accessToken(scope);
    }
}
```
Verbatim spec lines 141-144 for the abstract part: `@FunctionalInterface`, `public interface TokenProvider {`, `String accessToken(String scope) throws MailException;`, `}` [VERIFIED: spec.md:141-144]. Test: a lambda `scope -> "tok"` compiles as a `TokenProvider` and `freshAccessToken("s", "old")` returns `tok`; a counting stub shows `freshAccessToken` delegates exactly once.

### SPI and error tree, verbatim from the spec
```
MailException (checked)
  TransientMailException      retry later; carries Optional<Duration> retryAfter
  PermanentMailException      do not retry this mail
    InvalidMailException      the mail itself is wrong
    SenderRejectedException   this mailbox is not usable by this account
  AuthRequiredException       the account's credential is unusable; every mail of the account will fail
```
[VERIFIED: spec.md:182-189]. `MailTransport`: `void validate(MailMessage message) throws InvalidMailException;` and `void send(MailMessage message) throws MailException;` [VERIFIED: spec.md:132-133]. `TransportProvider`: `String id();` and `MailTransport create(Map<String, Object> options, TokenProvider auth);` [VERIFIED: spec.md:137-138]. The spec's `Attachment` is `public record Attachment(String filename, String contentType, Path file, String contentId) {}` [VERIFIED: spec.md:121] and `MailMessage` has the components `from, to, cc, bcc, replyTo, subject, html, text, attachments` [VERIFIED: spec.md:125-127].

### ERR-02 Javadoc text for `MailTransport.send`
```java
/**
 * Hands one message to the service. Returning normally means the service <em>accepted</em> the
 * message, not that it was delivered. A mail whose send ended in a timeout may nevertheless have
 * been accepted, so retrying after a timeout can deliver the same mail twice: delivery is
 * at-least-once.
 *
 * @param message the message, with a sender already applied
 * @throws TransientMailException   retry later, possibly after {@code retryAfter()}
 * @throws PermanentMailException   do not retry this mail
 * @throws AuthRequiredException    the account's credential is unusable
 * @throws MailException            any other failure
 */
```
Only declared exceptions may carry `@throws` (verified: otherwise `exception not thrown`); `send` declares `MailException`, so list subclasses only if the signature also names them, or put them in the prose. Keep the final text consistent with the README note.

### Content type map (D-07, D-08)
```java
// about 10 entries; keys lower-case, looked up with ext.toLowerCase(Locale.ROOT)
Map.ofEntries(
  entry("pdf","application/pdf"), entry("png","image/png"), entry("jpg","image/jpeg"), entry("jpeg","image/jpeg"),
  entry("gif","image/gif"), entry("txt","text/plain"), entry("html","text/html"), entry("htm","text/html"),
  entry("csv","text/csv"), entry("zip","application/zip"),
  entry("docx","application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
  entry("xlsx","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
```
(MIME strings are standard IANA values, [ASSUMED] from training knowledge; the unit test pins them.)

### Hygiene `.gitignore` additions
`target/`, `out/`, `.idea/`, `*.iml`, `*.jsonl`, `account.properties` (do not ignore `account.properties.example`), `*.pfx`. Keep the existing `*.jar`, `*.zip`, `*.log` rules.

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| JUnit 5 | JUnit 6.1.3, same coordinates and `@Test`/`@Tag` API | JUnit 6 GA | Spec text says 5; record in `docs/architecture.md` |
| `maven-wrapper.jar` (`bin` type) | `only-script` wrapper | wrapper plugin 3.3.x | No binary in the repo, compatible with `*.jar` ignore |
| `appendReplacement` for templates | manual find loop | long-standing | Required by TMPL-02 |
| `ArchUnit` for layer rules | source-scan JUnit test | project choice | Zero dependencies |

**Deprecated/outdated:** Maven 4.x for releases (central-publishing plugin documents it as broken); `maven-gpg-plugin` `gpg.passphrase`; unpinned plugin defaults.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | JDK 17's javadoc is no stricter than JDK 21's on the constructs in Pitfall 4 (notably the default-constructor warning, introduced after 17) | Pitfall 4 | A construct passes locally on 21 but fails on 17 in CI; fix by documenting it. Low: 21 is the stricter tool |
| A2 | Surefire's default working directory is the module directory, so relative `src/main/java` paths work | Pattern 7 | Import-rule test finds no files; the non-empty guard fails loudly. Fix with `System.getProperty("basedir")` |
| A3 | `-Dsurefire.failIfNoSpecifiedTests=false` is the right property for single-test runs at the reactor root | Pitfall 18 | A documented test command fails; use `-pl morimail` |
| A4 | The ten MIME type strings in the content type map | Code Examples | A wrong string only affects a unit-test expectation that the planner writes from the same list |
| A5 | GitHub's "Report a vulnerability" link pattern `https://github.com/morisoft-lib/morimail/security/advisories/new` is correct for D-16 | SECURITY.md | A dead link in SECURITY.md; the owner enables private reporting anyway |
| A6 | Phase 1 `verify` stays green with `jackson-databind` declared but unused | Open Question 7 | None expected; it is a normal compile dependency |
| A7 | Declaring `RecordingTransport` in package `de.morisoft.morimail.test` does not conflict with the main jar on the classpath | Pattern 6 | None expected; different package |

## Open Questions (RESOLVED)

All ten questions are resolved. Q1, Q2 and Q5 were decided by the owner (CONTEXT.md D-01 revised, D-02a, D-02b, all 2026-11-21); the other seven are adopted as recommended during planning and implemented by the plan named in each entry.

1. **D-01 + D-02 overload hazard.** RESOLVED (owner, revised D-01): the `(String email, String displayName)` overload was dropped; a display name is given only through `MailAddress.of(email, displayName)`, so two bare strings can never become email plus display name, and the `@` guard recommended below is moot and not built. Plans 01-03 and 01-05 gate that no builder method takes two strings for one address.
   - What we know: reproduced; two bare strings become email plus display name.
   - What's unclear: whether the owner accepts the guard (reject a display name containing `@`) or prefers renaming the named form.
   - Recommendation: implement the guard and the Javadoc warning now, inside the locked decisions; surface the rename option once in the plan summary. The overload set is a one-way door after 0.1.0.

2. **`Map<String, ?>` vs spec's `Map<String, Object>` for template data.** RESOLVED (owner, D-02a): `template(MailTemplate, Map<String, ?>)`, recorded as a spec deviation in `docs/architecture.md` (plan 01-02) and proven at compile time by `TemplatedSendTest` with a `Map<String, String>` (plan 01-05).
   - Recommendation: use `Map<String, ?>` (accepts everything the spec accepts, plus typed CSV rows). Record in `docs/architecture.md`. One-way door, so decide before the first commit of `Mail.Builder`.

3. **`TemplateRenderer` visibility.** RESOLVED (adopted as recommended): package-private final class (plan 01-05).
   - Recommendation: package-private. Phase 2 dry-run reads the built message; nothing outside the core needs to call the renderer. Widening later is non-breaking, narrowing is not. `MailTemplate` stays public.

4. **A present-but-null data value.** RESOLVED (adopted as recommended): `String.valueOf` gives the text `null`, only an absent key is missing; pinned by `TemplateRendererTest` (plan 01-05).
   - Recommendation: follow the spec literally (`String.valueOf` gives `null`; absent key is the only "missing" case, tested with `containsKey`) and pin it with a test. Note in Javadoc.

5. **Pre-existing first commit author address.** RESOLVED (owner, D-02b): `8c9b082` is left unchanged, no history rewrite; every identity gate checks `8c9b082..HEAD` and treats it as the one known exception, and the owner decides before publishing.
   - What we know: `8c9b082` has a different author address and committer GitHub; everything after is correct.
   - What's unclear: whether the owner wants it left (history rewrite is invasive once public) or accepted.
   - Recommendation: do not rewrite; mention it at the phase verification and let the owner decide. The hygiene check `git log` therefore expects one known exception.

6. **Scrub absolute paths from committed research docs.** RESOLVED (adopted as recommended): plan 01-02 Task 1 makes the two references repo-relative and runs the whole-tree hygiene gates (in wave 2, right after the build skeleton).
   - Recommendation: include a small first-wave task replacing the two `/home/...` references with repo-relative paths, then run the hygiene greps.

7. **What to declare in Phase 1 POMs.** RESOLVED (adopted as recommended): plan 01-01 manages every version in the parent and declares `jackson-databind`, `junit-jupiter` and `assertj-core` in the library; WireMock and picocli are declared when first used (Phases 3 and 2); the `live` profile arrives in Phase 3.
   - Recommendation: manage every version in the parent (`dependencyManagement`, BOM imports); declare `jackson-databind`, `junit-jupiter` and `assertj-core` in the library POM now (REPO-04 is a Phase 1 requirement); declare WireMock and picocli only when first used (Phases 3 and 2). Add the `live` profile and `excludedGroups` properties in Phase 3.

8. **Sample smoke test in Phase 1.** RESOLVED (adopted as recommended): `RecordingTestJarSmokeTest` in plan 01-04, run in the default build. Correction 2026-11-21: per D-20 the release build is scoped to `-pl morimail -am` and does not build the sample, so the smoke test does not run under `-Prelease`. A planning probe showed the sample's test-jar dependency fails `-Prelease` while the library has no test classes, so plan 01-04 adds that dependency together with the fixture.
   - Recommendation: one test in `morimail-sample-serial` asserts `MailAccount.builder("x").transport("recording").build().transport()` is a `RecordingTransport`. It proves the test-jar and its services file reach the sample, including under `-Prelease verify` with `attach=false`, which Phase 2 depends on. Accept the empty-jar warning (Pitfall 16).

9. **Direct-constructed `Mail` bypasses `build()` validation.** RESOLVED (adopted as recommended): `Mailer` does not re-validate; the `Mail` and `MailMessage` Javadoc name `build()` as the validating path (plans 01-03 and 01-04).
   - Recommendation: do not re-validate in `Mailer` (Rule 0; the transport's `validate` guards its own limits). Mention in `Mail` Javadoc that `build()` is the validating path.

10. **CI Java matrix.** RESOLVED (adopted as recommended): one Java 17 job (plan 01-01 Task 2).
    - Recommendation: keep one job on Java 17 as REPO-07 says. Local development on 21 provides the stricter doclint run.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| JDK 17 | CI proof of the 17 runtime | ✗ locally | only 21.0.13.1 Temurin installed | CI (`setup-java` 17) is the proof; `--release 17` guards the API surface locally |
| Maven (wrapper) | build | ✓ | 3.10.1 dist cached; system Maven 3.9.16 | — |
| Network to Maven Central | first dependency resolution | ✓ | HTTP 200 on metadata | `-o` after the cache is warm |
| gpg | `release` profile | ✓ (not needed with `-Dgpg.skip`) | present | — |
| keytool, openssl | Phase 3 only | ✓ | present | — |
| git | commits | ✓ | local identity set, env override needs export | — |
| `gh` CLI | CI run inspection by the owner | ✓ | present | — |
| actionlint / yamllint / PyYAML | local YAML lint | ✗ | — | validate by pushing; keep the YAML minimal as above |
| Docker | none | n/a | — | — |

**Missing dependencies with no fallback:** none for Phase 1. **Missing with fallback:** JDK 17 (CI), YAML lint (review by eye, first push is the check).

## Validation Architecture

> `.planning/config.json` has `workflow.nyquist_validation: false`. This section is included because the invoking prompt asked for it explicitly.

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit Jupiter 6.1.3 + AssertJ 3.27.7, Surefire 3.6.1 (no config block) |
| Config file | none; versions in the parent POM |
| Quick run command | `./mvnw -B -ntp -q -pl morimail test` |
| Single class | `./mvnw -B -ntp -pl morimail test -Dtest=MailBuilderTest` |
| Full suite command | `./mvnw -B -ntp verify` |
| Release shape | `./mvnw -B -ntp -Prelease clean verify -Dgpg.skip -pl morimail -am` (scoped per D-20) |
| Offline proof | `./mvnw -B -ntp -o verify` (after one online run) |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| REPO-01 | Three-module reactor, pinned versions, release 17 | smoke | `./mvnw -B -ntp verify` | ❌ Wave 0 (poms) |
| REPO-02 | Script-only wrapper, exec bit, Maven 3.10.1 | shell | `git ls-files --stage mvnw` (mode `100755`); `grep -q only-script .mvn/wrapper/maven-wrapper.properties`; `./mvnw -v` | ❌ Wave 0 |
| REPO-03 | Central metadata present | shell | `grep -c -E '<license>\|<developer>\|<scm>' pom.xml` (3); url value check | ❌ Wave 0 |
| REPO-04 | Only Jackson at runtime | shell | `./mvnw -B -ntp -pl morimail dependency:tree -Dscope=runtime` shows only `jackson-*` | ❌ Wave 0 |
| REPO-05 | Module name, no `module-info` | shell | `unzip -p morimail/target/morimail-0.1.0-SNAPSHOT.jar META-INF/MANIFEST.MF \| grep 'Automatic-Module-Name: de.morisoft.morimail'`; `unzip -l` shows no `module-info.class`; the tests jar has no such entry | ❌ Wave 0 |
| REPO-06 | Repo files present, `spec.md` moved | shell | `test -f LICENSE -a -f README.md -a -f SECURITY.md -a -f CLAUDE.md -a -f docs/spec.md && ! test -f spec.md`; `grep -qi 'pre-1.0' README.md`; `grep -qi 'accepted' README.md` | ❌ Wave 0 |
| REPO-07 | CI shape | shell + first push | `grep -q 'contents: read' .github/workflows/ci.yml`; `! grep -q pull_request_target`; green run on first push (owner/`gh run list`) | ❌ Wave 0 |
| REPO-09 | Release profile builds jars, test-jar not attached, sample skipped | build | `./mvnw -B -ntp -Prelease clean verify -Dgpg.skip -pl morimail -am` (scoped per D-20) then `test -f morimail/target/morimail-0.1.0-SNAPSHOT-javadoc.jar -a -f morimail/target/morimail-0.1.0-SNAPSHOT-sources.jar` | ❌ Wave 0 |
| REPO-10 | Javadoc clean; thread safety stated on public types | build + unit | release build above (`failOnWarnings`); `PublicTypesDocumentThreadSafetyTest` | ❌ Wave 0 |
| REPO-11 | No secrets, GUIDs, real addresses, home paths; identity | shell | `git grep -nIEi '[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}' \| grep -viE 'example\.(com\|org)\|stephan@morisoft\.de'` (empty); GUID grep excluding zero GUID (empty); `git grep -nI '/home/[a-z]'` (empty); `git log --format='%an <%ae> \| %cn <%ce>'` (known exception `8c9b082`) | ❌ Wave 0 |
| REPO-12 | Import rules | unit | `./mvnw -B -ntp -pl morimail test -Dtest=ImportRulesTest` | ❌ Wave 0 |
| MODEL-01 | Records immutable, lists copied, not Serializable | unit | `-Dtest=MailModelTest` (mutate the source list after construction; `to().add` throws `UnsupportedOperationException`; `Serializable.class.isAssignableFrom(MailMessage.class)` is false) | ❌ Wave 0 |
| MODEL-02 | Fluent builder, D-01..D-03, D-10 forms | unit | `-Dtest=MailBuilderTest` (append semantics, last-wins for from/replyTo, the three attach forms) | ❌ Wave 0 |
| MODEL-03 | Each rejection is an `InvalidMailException` naming the problem | unit | `-Dtest=MailBuilderTest` (no recipient, no subject, no body, missing file, directory, set `contentId`, template plus explicit, first-problem order) | ❌ Wave 0 |
| MODEL-04 | CR/LF and single-`@` | unit | `-Dtest=MailBuilderTest` (address, display name, subject; zero `@`, two `@`; display name containing `@` guard from Pitfall 1) | ❌ Wave 0 |
| MODEL-05 | Content type resolution | unit | `-Dtest=MailBuilderTest` (`Report.PDF` → `application/pdf`; unknown extension → `application/octet-stream`; explicit type wins; custom `Attachment` filename kept) | ❌ Wave 0 |
| TMPL-01 | Placeholder grammar | unit | `-Dtest=TemplateRendererTest` (the edge-case table above) | ❌ Wave 0 |
| TMPL-02 | Escaping, raw, literal `$`/`\` | unit | `-Dtest=TemplateRendererTest` | ❌ Wave 0 |
| TMPL-03 | Subject/text insert, line break to space | unit | `-Dtest=TemplateRendererTest` (CRLF, LF, CR, `$$!x$$` in subject) | ❌ Wave 0 |
| TMPL-04 | Missing field names part and placeholder, not the data | unit | `-Dtest=TemplateRendererTest` and via `Mail.Builder` | ❌ Wave 0 |
| CORE-01 | SPI shapes, nothing Graph-specific | compile + unit | compile of `RecordingTransport`; `ImportRulesTest` | ❌ Wave 0 |
| CORE-02 | `freshAccessToken` default | unit | `-Dtest=TokenProviderTest` (lambda assignable; default delegates once) | ❌ Wave 0 |
| CORE-03 | Duplicate ID fails at load; ServiceLoader finds `recording` | unit | `-Dtest=TransportRegistryTest` (`of(..)` with duplicates built-in/external and external/external; broken provider via `ServiceConfigurationError` path; `load()` finds `recording`) | ❌ Wave 0 |
| CORE-04 | Account builder, unknown ID lists available IDs, options opaque | unit | `-Dtest=MailAccountTest` (message contains `recording`; options map reaches the provider unchanged; invalid `defaultFrom` rejected) | ❌ Wave 0 |
| CORE-05 | `defaultFrom` applied, `validate` runs, unknown account | unit | `-Dtest=MailerTest` (recorded message has the default from; explicit from wins; `failValidation` surfaces from both `validate` and `send` and nothing is recorded; unknown account → `PermanentMailException`; no sender → `InvalidMailException`; scripted transient, permanent, sender-rejected, auth-required surface with their types and `retryAfter`) | ❌ Wave 0 |
| CORE-06 | Concurrent use | unit | `-Dtest=MailerConcurrencyTest` (N threads via `ExecutorService`, `CountDownLatch`, assert `sent().size()` and `attempts()`) | ❌ Wave 0 |
| CORE-07 | `RecordingTransport` as test-jar, scripts | unit + reactor | `-Dtest=RecordingTransportTest` (`failFor` case-insensitive, `failNext` order and precedence, `failValidation`, snapshot immutability); `./mvnw -B -ntp verify` runs the sample smoke test | ❌ Wave 0 |
| ERR-01 | Checked tree and `retryAfter` | unit | `-Dtest=ErrorHierarchyTest` (superclass chain per spec; none extends `RuntimeException`; `retryAfter()` empty by default, present when set) | ❌ Wave 0 |
| ERR-02 | accepted-not-delivered note | shell | `grep -qi 'at-least-once' morimail/src/main/java/de/morisoft/morimail/MailTransport.java && grep -qi 'at-least-once' README.md` | ❌ Wave 0 |

### Sampling Rate
- **Per task commit:** `./mvnw -B -ntp -q -pl morimail test`, plus the release-shape command after any task that adds or edits Javadoc.
- **Per wave merge:** `./mvnw -B -ntp verify` and `./mvnw -B -ntp -Prelease clean verify -Dgpg.skip -pl morimail -am` (scoped per D-20).
- **Phase gate:** both of the above, then `./mvnw -B -ntp -o verify`, the hygiene greps, and a green GitHub Actions run before `/gsd-verify-work`.

### Wave 0 Gaps
- [ ] Parent, library and sample POMs, wrapper, `.gitattributes`, `.gitignore` additions: nothing exists yet.
- [ ] All test classes listed above, plus `RecordingTransport`, `RecordingTransportProvider` and the services file.
- [ ] `.github/workflows/ci.yml`.
- [ ] Repo files: `README.md`, `SECURITY.md`, root `CLAUDE.md`, `docs/spec.md` (move from the untracked root `spec.md`).
- [ ] Scrub of the two absolute paths in committed research files.

## Security Domain

`security_enforcement` is enabled (`workflow.security_enforcement: true`, `security_asvs_level: 1`).

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no (Phase 3 token client) | — |
| V3 Session Management | no | — |
| V4 Access Control | no | — |
| V5 Input Validation / Output Encoding | yes | CR/LF rejection and single-`@` rule in `build()`; HTML escaping of placeholder values; filenames from `Path.getFileName()` only |
| V6 Cryptography | no (Phase 3: JDK `Signature`, never hand-rolled) | — |
| V7 Error Handling and Logging | yes | no tokens, secrets or bodies in logs or exception messages; recipients at DEBUG only; the missing-field message names the placeholder, not the data map |
| V14 Configuration / supply chain | yes | all plugin and dependency versions pinned; CI `permissions: contents: read`, action SHAs pinned, no secrets, no `pull_request_target`; Maven Central coordinates only |

### Known Threat Patterns for this stack

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Header or recipient injection through CR/LF in addresses, display name, subject | Tampering | Reject in `build()`; subject-value guard in the renderer; test rows for each |
| Wrong recipient from the overload trap (Pitfall 1) | Tampering / Info disclosure | Display-name-contains-`@` guard in `build()` |
| HTML injection via data values | Tampering | Default escaping; `$$!name$$` only for trusted values (state in `MailTemplate` Javadoc; escaping does not protect a value placed inside `href="..."` or `<style>`) |
| Path traversal / local path disclosure in attachment names | Info disclosure | Use `getFileName()` only; never the full path |
| Secret or identity leak into the public repo | Info disclosure | `example.com`/`example.org`, zero GUIDs, hygiene greps, no absolute paths in docs, identity export before each commit |
| Compromised or mutable CI action | Tampering / Elevation | Commit-SHA pinned actions, read-only token, no secrets |
| Dependency confusion or typosquat | Tampering | Coordinates from the spec allowlist; BOM-managed versions; no new dependency without owner approval |
| Log injection through recipient values | Tampering | Recipients at DEBUG only, never bodies |

## Sources

### Primary (HIGH confidence)
- Binding spec `spec.md` (read in full this session): §2, §3, §4, §5, §5.1, §5.2, §5.3, §10.1, §11, §12, §13.
- `.planning/phases/01-library-core/01-CONTEXT.md`, `.planning/REQUIREMENTS.md`, `.planning/ROADMAP.md` Phase 1, `.planning/STATE.md`, `.planning/research/SUMMARY.md`, `ARCHITECTURE.md`, `PITFALLS.md`, `STACK.md` (project-level research, built on, not redone).
- Executed this session: `javac --release 17` overload test; `javadoc -Xdoclint:all --release 17` on JDK 21 across eight constructs; renderer prototype with 19 edge cases; import-rule scan prototype; empty-jar and offline Maven build probes; `git grep` hygiene scans; `git log` identity check.
- repo1.maven.org: `maven-metadata.xml` and POM `Last-Modified` for the artifacts in Standard Stack.
- GitHub API: `actions/checkout` v7.0.1 and `actions/setup-java` v6.3.0 latest releases and their commit SHAs.

### Secondary (MEDIUM confidence)
- STACK.md recipes (parent POM, release profile, test-jar, wrapper) executed in the earlier smoke project; not re-executed here.

### Tertiary (LOW confidence)
- Surefire working-directory and `failIfNoSpecifiedTests` behaviour, JDK 17 javadoc strictness (see Assumptions Log).

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH, versions confirmed against Maven Central today and consistent with the project research.
- Architecture: HIGH, follows the binding spec; registry, renderer, import scan and doclint behaviour executed.
- Pitfalls: HIGH for items marked verified; MEDIUM for the CI-only and JDK 17 items.

**Research date:** 2026-11-21
**Valid until:** 2026-12-21 (stable stack; re-check Maven Central versions at implementation time per spec §3)
