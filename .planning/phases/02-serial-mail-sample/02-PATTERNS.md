# Phase 2: Serial-Mail Sample - Pattern Map

**Mapped:** 2026-11-21
**Files analyzed:** 26 (new or modified by plans 02-01 to 02-04; the list below matches their `files_modified`)
**Analogs found:** 15 / 26 (the sample has no main code yet; the rest is greenfield, use RESEARCH.md)

All analog paths below were checked as git-tracked (`git ls-files`). `.../sample/` stands for `morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/` and `src/test/.../` for `morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/`. The two test analogs are `src/test/.../RecordingTestJarSmokeTest.java` (sample) and `morimail/src/test/java/de/morisoft/morimail/SendThroughRecordingTest.java` (library).

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality | Plan |
|-------------------|------|-----------|----------------|---------------|------|
| `morimail-sample-serial/pom.xml` (modify) | config | n/a | itself + root `pom.xml` pluginManagement (jar 3.5.1, dependency 3.11.0, picocli managed) | exact | 02-01 (picocli), 02-04 (build section) |
| `.gitignore` (modify) | config | n/a | `.gitignore` lines 28-32 | exact | 02-04 |
| `.../sample/SerialMail.java` | CLI root, main, Phase 3 factory stub | request-response | none (RESEARCH Pattern 1) | no analog | 02-01 |
| `.../sample/SendCommand.java` | CLI command (pre-flight + run loop) | batch, request-response | none in sample; library API use per `SendThroughRecordingTest` | partial | 02-01, 02-03 |
| `.../sample/AccountFile.java` | model (record + static read) | file-I/O | none; library records `MailTemplate`, `MailAddress` for record style | partial | 02-01, 02-02 |
| `.../sample/Campaign.java` (+ CSV reader) | model + utility | file-I/O, transform | `TemplateRenderer.java` (regex, package-private final class) | partial | 02-01, 02-02 |
| `.../sample/Journal.java` | utility | file-I/O (append) | none (RESEARCH Pattern 3) | no analog | 02-01, 02-03 |
| `src/test/.../SampleFixtures.java` | test helper (campaign, account, recording account, output capture) | request-response | `RecordingTestJarSmokeTest.java` (recording account) | role-match | 02-01 |
| `src/test/.../SendRunTest.java` | test (command, send once, rerun) | request-response | `RecordingTestJarSmokeTest.java`, `SendThroughRecordingTest.java` | role-match | 02-01 |
| `src/test/.../DryRunTest.java` | test (command, dry-run files) | file-I/O | same two tests | role-match | 02-01 |
| `src/test/.../ExampleCampaignTest.java` | test (shipped example) | file-I/O | same two tests | role-match | 02-04 |
| `src/test/.../PreflightTest.java` | test (command, pre-flight report) | request-response | same two tests | role-match | 02-02 |
| `src/test/.../CampaignTest.java` | test (CSV reader, campaign files) | file-I/O | same two tests | role-match | 02-02 |
| `src/test/.../AccountFileTest.java` | test (account file) | file-I/O | same two tests | role-match | 02-02 |
| `src/test/.../FailureHandlingTest.java` | test (command, retry and stop) | request-response | same two tests | role-match | 02-03 |
| `src/test/.../PacingAndOptionsTest.java` | test (command, options) | request-response | same two tests | role-match | 02-03 |
| `src/test/.../JournalTest.java` | test (journal) | file-I/O | same two tests | role-match | 02-03 |
| `morimail-sample-serial/example-campaign/` `subject.txt`, `body.html`, `body.txt`, `recipients.csv`, `account.properties` | fixture | n/a | none | no analog | 02-04 |
| `morimail-sample-serial/example-campaign/attachments/info.pdf`, `logo.png` | fixture | n/a | none (generated once, RESEARCH "Fixture generator") | no analog | 02-04 |
| `.planning/phases/02-serial-mail-sample/02-RESEARCH.md`, `02-PATTERNS.md` (modify) | planning docs, scrub only | n/a | none (no code pattern) | no analog | 02-01 |

## Pattern Assignments

### `morimail-sample-serial/pom.xml` (config)

**Analog:** the file itself (`morimail-sample-serial/pom.xml` lines 26-47) and the parent `pom.xml`.

Existing dependency block style, versions come from parent management (no `<version>`):
```xml
<dependency>
  <groupId>de.morisoft</groupId>
  <artifactId>morimail</artifactId>
</dependency>
<dependency>
  <groupId>de.morisoft</groupId>
  <artifactId>morimail</artifactId>
  <type>test-jar</type>
  <scope>test</scope>
</dependency>
```
Add `info.picocli:picocli` the same way (no version), after the `morimail` dependency and before the test ones. Add a `<build><plugins>` section after `</dependencies>` with the jar-plugin manifest (`mainClass de.morisoft.morimail.sample.SerialMail`, `addClasspath`, `classpathPrefix lib/`) and dependency-plugin `copy-dependencies` (phase `package`, `includeScope runtime`, output `${project.build.directory}/lib`). Exact XML is in RESEARCH "Installation". Do not repeat plugin versions; they are pinned in parent pluginManagement.

### `.gitignore` (config)

Lines 28-32 ignore `out/`, `*.jsonl`, `account.properties`. Add directly after line 32:
```
!morimail-sample-serial/example-campaign/account.properties
```
(RESEARCH Pitfall 4: otherwise the example account file is never committed and dry-run fails on a fresh clone.)

### `SerialMail.java` and `SendCommand.java` (CLI, request-response)

**No in-repo CLI analog.** Use RESEARCH Pattern 1, 4, 5 and the Test setup example. Library API usage to copy, from `morimail/src/test/java/de/morisoft/morimail/SendThroughRecordingTest.java` lines 12-30:
```java
MailAccount account = MailAccount.builder("main")
        .transport("recording")
        .defaultFrom("newsletter@example.com")
        .build();
Mailer mailer = Mailer.of(account);
Mail mail = Mail.using("main").to("reader@example.org").subject("Hello").text("Plain text body").build();
mailer.validate(mail);
mailer.send(mail);
```
In the sample, per D-04 use `Mail.using(id).from(from).to(email).template(new MailTemplate(subject, html, textOrNull), row.fields())` then `.attach(Path)` per file, then `.build()`.

Signatures to code against (verified): `Mailer.of(MailAccount...)` (Mailer.java:30), `Mailer.validate(Mail) throws PermanentMailException` (:48), `Mailer.send(Mail) throws MailException` (:60), `TransientMailException.retryAfter()` returns `Optional<Duration>` (:58), `MailTemplate(String subject, String html, String text)` record.

**Catch order is load-bearing.** `AuthRequiredException extends MailException` (not Permanent); `SenderRejectedException` and `InvalidMailException` extend `PermanentMailException`; `TransientMailException extends MailException`. Order: `SenderRejectedException | AuthRequiredException` (stop, exit 3), `TransientMailException` (retry), `MailException` (record failed, continue). CONTEXT.md's error-tree note is wrong on this point.

**Style conventions from the library:** package `de.morisoft.morimail.sample`, 4-space indent, plain constructors, records, static factories, no DI. Sample classes stay package-private except `SerialMail` (has `main`).

### `AccountFile.java` (model, file-I/O)

No analog for properties reading. Follow RESEARCH Pitfalls 2, 3: `Files.newBufferedReader(path)` into `Properties.load(Reader)`, trim values, messages name keys only. Use a record plus `static read(Path, List<String> problems)` (Rule 0).

### `Campaign.java` and CSV reader (model/utility, transform)

**Analog:** `morimail/src/main/java/de/morisoft/morimail/TemplateRenderer.java` line 16, the placeholder regex to duplicate exactly (the class is package-private, so the sample needs its own copy):
```java
private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\$(!?)([A-Za-z0-9_.-]+)\\$\\$");
```
(Pattern text taken from RESEARCH Pitfall 6, which verified it against this file; group 2 is the name.) Helper `placeholders(String...)` in RESEARCH "Code Examples". CSV state machine: RESEARCH Pattern 2. `readText` helper (strict UTF-8, strip a leading BOM (U+FEFF)): RESEARCH Pitfall 5. Remember Java 17 limits: `list.get(0)`, not `getFirst()`.

### `Journal.java` (utility, file-I/O append)

No analog. Use RESEARCH Pattern 3: `FileChannel.open(path, CREATE, WRITE, APPEND)`, write bytes, `ch.force(true)`; read at start with `new String(Files.readAllBytes(p), UTF_8)`; Jackson `ObjectMapper` over a `LinkedHashMap` for each line; newline repair for a torn tail. Key = `campaignId + ":" + email.toLowerCase(Locale.ROOT)`. Jackson is already transitive via `morimail`.

### Test files (test)

**Applies to:** `SampleFixtures`, `SendRunTest`, `DryRunTest` (plan 02-01), `PreflightTest`, `CampaignTest`, `AccountFileTest` (plan 02-02), `FailureHandlingTest`, `PacingAndOptionsTest`, `JournalTest` (plan 02-03), `ExampleCampaignTest` (plan 02-04).

**Analog:** `morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/RecordingTestJarSmokeTest.java` (lines 1-32).

Imports and conventions to copy:
```java
package de.morisoft.morimail.sample;

import static org.assertj.core.api.Assertions.assertThat;

import de.morisoft.morimail.MailAccount;
import de.morisoft.morimail.Mailer;
import de.morisoft.morimail.test.RecordingTransport;
import org.junit.jupiter.api.Test;

class RecordingTestJarSmokeTest {   // package-private class, no public modifier
    @Test
    void recordingTransportComesFromTheTestJar() throws MailException { ... }
}
```
Access the transport via `(RecordingTransport) account.transport()`. Scripted failure hooks (RecordingTransport.java): `failFor(String email, MailException)` (:102), `failNext(MailException...)` (:90), `failValidation(InvalidMailException)` (:112), `sent()` (:130), `attempts()` (:121). Each `MailAccount` gets a fresh `RecordingTransport`, so a rerun test builds a new account and reuses the same journal path. Use `@TempDir` for campaigns, journal and `out/`. Command-test wiring (waits recorded through `Consumer<Duration>`, `StringWriter` capture of `getOut()/getErr()`): RESEARCH "Test setup against recording". The account id in the test's `account.properties` must equal the `MailAccount` id.

## Shared Patterns

### Error and exit-code mapping
**Source:** D-13 plus the library error tree above. 0 none failed; 1 any row failed or journal write failed; 2 pre-flight/args/Phase 2 "graph not available"; 3 sender-rejected or auth-required stop.

### Output
**Apply to:** `SendCommand`. Print through `spec.commandLine().getOut()/getErr()` (not `System.out`) so tests capture output. Never print property values, tokens, secrets or full bodies.

### No secrets or real addresses
**Apply to:** every fixture and test. Only `example.com`, `example.org`, zero GUIDs. The example `account.properties` holds env-var names only.

### Java 17 API surface
**Apply to:** all new main and test code. No `Thread.sleep(Duration)`, `getFirst()`, `Math.clamp`, `StringBuilder.repeat`, pattern-matching switch.

## No Analog Found

| File | Role | Data Flow | Reason |
|------|------|-----------|--------|
| `SerialMail.java` | CLI root | request-response | No picocli code exists; use RESEARCH Pattern 1 |
| `SendCommand.java` | CLI command | batch | Sample has no main code; use RESEARCH Patterns 4-6 |
| `Journal.java` | utility | file-I/O | No file-append code anywhere; use RESEARCH Pattern 3 |
| `AccountFile.java` | model | file-I/O | No properties reader; use RESEARCH Pitfalls 2-3 |
| example-campaign files, PDF, PNG | fixtures | n/a | New; generate once per RESEARCH "Fixture generator", no generator in the build |

## Metadata

**Analog search scope:** `morimail/src/main`, `morimail/src/test`, `morimail-sample-serial/`, root `pom.xml`, `.gitignore`
**Files scanned:** about 45 tracked files listed; 6 read in full or in part
**Pattern extraction date:** 2026-11-21
