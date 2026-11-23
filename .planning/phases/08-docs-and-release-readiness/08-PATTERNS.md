# Phase 8: Docs and Release Readiness - Pattern Map

**Mapped:** 2026-11-23
**Files analyzed:** 8 (6 modified docs/code, 1 new test, 1 module-level README section group)
**Analogs found:** 8 / 8 (docs analogs are section style in the same file)

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `README.md` (Coordinates, Inline images, morimail-html, Serial-mail sample) | doc | n/a | `README.md` Coordinates / Quick start / try-it block (lines 12-81) | exact (same file) |
| `docs/spec.md` (§5, §5.1, §6.2, §10.3/§10.4 titles, §12 rows) | doc | n/a | the same sites (see RESEARCH edit table, lines 124, 177, 217-222, 385, 395, 448-449) | exact |
| `docs/architecture.md` (line 53, delete row ~40, extend §10.4/§12 row ~43) | doc | n/a | neighbouring table rows and the `§10.3 (cid: references)` row | exact |
| `docs/release.md` (steps 4, 5, 7, 8, lines 8, 100, 115) | doc | n/a | the v0.1.0 steps in the same file (lines 50-100) | exact |
| `morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/SendCommand.java` | command (picocli annotation text) | request-response | its own `@Command` footer array and `--out` option description (lines 38-45, 69-75) | exact |
| `morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/SendCommandHelpTest.java` (new) | test | transform | `PacingAndOptionsTest.java` (package, imports, `SerialMail.commandLine(...)` use) | role-match |

## Pattern Assignments

### `SendCommand.java` (picocli text only, no new option)

**Analog:** itself, lines 38-45 and 69-75.

Footer is an array; add the campaign-layout entries as further elements (each prints as its own paragraph):
```java
@Command(name = "send",
        description = "Sends one mail per row of recipients.csv, or renders them with --dry-run.",
        footer = {"A mail that is in flight when the run is stopped has no journal line, "
                + "so the next run sends it again.",
                "Exit codes: 0 nothing failed, ..."})
```
Option text pattern (string concat in `description`):
```java
@Option(names = "--dry-run",
        description = "Write the rendered mails to --out and send nothing.")
@Option(names = "--out", defaultValue = "out", paramLabel = "DIR",
        description = "Where --dry-run writes the rendered mails. Default: ${DEFAULT-VALUE}.")
```
Rules: no `%`, no `${...}` other than `${DEFAULT-VALUE}` (picocli formats with `String.format`; RESEARCH Pitfall 2). Say "writes inline images once to `inline/` in the output directory and rewrites `cid:` to `inline/NAME` in the written HTML only" (not "next to each mail", Pitfall 3). `cid:NAME` and `inline/` are safe text.

### `SendCommandHelpTest.java` (test, new)

**Analog:** `PacingAndOptionsTest.java` (same package `de.morisoft.morimail.sample`, JUnit 5 + AssertJ).

Imports/convention (lines 1-33):
```java
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class PacingAndOptionsTest { ... }
```
Build the command via the existing factory (`SerialMail.java:46`, package-private static):
```java
static CommandLine commandLine(Function<AccountFile, Mailer> mailerFactory, Consumer<Duration> sleep)
```
Suggested body (assumption A1, `getSubcommands()` not yet run): 
```java
String usage = SerialMail.commandLine(SerialMail::realMailer, d -> { })
        .getSubcommands().get("send").getUsageMessage();
assertThat(usage).contains("inline/").contains("cid:");
```
Do not call `send --help` (not a real option, exits 2). No `SampleFixtures` needed. Run with `-pl morimail-sample-serial -am` because the sample needs the `morimail` test-jar.

### `README.md`

**Analog:** same file. Section style: `##` heading, one short paragraph, a fenced block, one or two sentences.

Coordinates (lines 12-21):
```xml
<dependency>
  <groupId>de.morisoft</groupId>
  <artifactId>morimail</artifactId>
  <version>0.1.0</version>
</dependency>
```
followed by `0.1.0 is the first release, published on Maven Central.` Change per D-04 to 0.2.0 and keep the sentence that 0.1.0 is the current release.

"Inline images" section goes after Quick start (ends line ~72), before the sample block. Use the compiled snippets in RESEARCH "Code Examples" (lines 281-318). Limits sentence mirrors line 87 ("Attachments must stay under 3 MiB ... per file and in total").

"Serial-mail sample" section: move the try-it block (lines 74-81) in verbatim, including:
```bash
./mvnw -B -ntp -DskipTests package -pl morimail-sample-serial -am
java -jar morimail-sample-serial/target/morimail-sample-serial-*.jar send --account morimail-sample-serial/example-campaign/account.properties --campaign morimail-sample-serial/example-campaign --dry-run --out out
```
and the sentence "The sample sends no `saveToSentItems` ...". Add the layout from `morimail-sample-serial/example-campaign/` and the `inline/` rules (name without last extension is the content ID, dot-files skipped, dry-run shows relative `inline/NAME`, real send keeps `cid:`). Examples use `example.org` only.

### `docs/architecture.md`

**Analog:** table rows of the form `| §x (topic) | decision | reason |`.
- Line 53: replace the final sentence "Live status: not yet confirmed, the owner runs `LiveGraphTest.sendsOneMailWithAnInlineImage`." with the D-08 text.
- Delete the single row `| §5 and §5.1 (contentId in build()) | ... |` (~line 40).
- Extend the `§10.4 and §12 (morimail-html decisions)` row (~line 43) with "No HTML parser: a JDK regular expression over `<img src>`", so the spec §12 pointer has a target.

### `docs/spec.md`

**Analog:** adjacent bullets/rows. §6.2 add bullets after the five existing attachment bullets (lines 217-222); wording for property names from `GraphTransport.java:168-170` (`isInline: true`, bare `contentId`, no `contentLocation`, none on regular attachments). §12 rows 448-449 are `| topic | outcome |` style; fill with the D-07 outcomes and a pointer to architecture.md. Retitle headings 385 and 395 to "implemented in 0.2.0". Optional (RESEARCH Open Question 3): one line in §8.1 for `inline/` and one sentence in §8.3 for `out/inline/`.

### `docs/release.md`

**Analog:** same file steps. Existing patterns to generalise:
```
sed -i 's/0\.1\.0-SNAPSHOT/0.1.0/' pom.xml morimail/pom.xml morimail-sample-serial/pom.xml
./mvnw -B -ntp -Prelease clean deploy -DskipUpload=true -pl morimail -am
```
Target form: `VERSION=0.2.0`, `NEXT=0.3.0-SNAPSHOT`, `sed -i "s/${VERSION}-SNAPSHOT/${VERSION}/" pom.xml morimail/pom.xml morimail-html/pom.xml morimail-sample-serial/pom.xml`, four changed lines, `-pl morimail,morimail-html -am` in both deploy commands (also line 8 and line 112), 54 entries and nine base files (parent pom; morimail pom/jar/sources/javadoc; morimail-html pom/jar/sources/javadoc; each with `.asc`, `.md5`, `.sha1`, `.sha256`, `.sha512`), rehearsal date 2026-11-23. Line 115 "Tests run." must say the helper's tests do not run in a release-profile deploy (they run in step 4's plain verify). After editing, `grep -n "pl morimail -am\|0\.1\.0\|three\|30 entries" docs/release.md` should return no stale hits.

## Shared Patterns

### Public-repo example rules
**Apply to:** every doc and snippet. Use `example.org`/`example.com`, zero GUIDs, no real hosts, no throwaway key id. Maintainer identity only where already present.

### Verified build gates
**Apply to:** the final plan.
`./mvnw -B -ntp -o verify` and `./mvnw -B -ntp -Prelease clean verify -Dgpg.skip -pl morimail,morimail-html -am` (grep WARN count 0). Never use `-o` for the deploy rehearsal; run it in a scratch copy with a short `GNUPGHOME` path; never commit a release version.

## No Analog Found

None. The `-Prelease deploy` rehearsal is a procedure, not a file; its evidence is in RESEARCH lines 164-180.

## Metadata

**Analog search scope:** README.md, docs/*.md, morimail-sample-serial main and test sources.
**Files scanned:** about 10 (all tracked: README.md, docs/*.md, sample sources verified present under git).
**Pattern extraction date:** 2026-11-23
