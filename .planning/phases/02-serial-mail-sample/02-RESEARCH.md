# Phase 2: Serial-Mail Sample - Research

**Researched:** 2026-11-21
**Domain:** Java 17 command-line sample (picocli) on top of the Phase 1 morimail library: hand-written CSV reader, append-only forced journal, sleep-seam retry and rate spacing, dry-run rendering, example fixtures
**Confidence:** HIGH (every API and behaviour below that matters was read in the repo or executed in a scratch copy this session; the open items are spec ambiguities, listed in Open Questions)

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

### Real send before Graph
- **D-01:** In Phase 2, a non-dry-run `serial-mail send` started from the command line parses and checks `account.properties`. Building the real `Mailer` then fails with a clear message that the graph transport is not available yet, and the run exits with code 2. Tests run the command logic with a recording-backed `Mailer` through the CLI-12 seam. Phase 3 replaces only that one factory method. There is no temporary `transport=` key or other extra option (Rule 0).
- **D-02:** Dry-run builds no `Mailer` and does not call `mailer.validate`. It still runs the rest of pre-flight: the CSV header and delimiter, placeholders against columns, email syntax and duplicates (D-06), and `build()` of every row's mail, so build errors show up. Transport limits such as the Graph 3 MB rule only show up in a real run.
- **D-03:** `account.properties` is always checked, in dry-run as well: the required keys `account.id`, `tenantId`, `clientId` and `from` must be present, with exactly one credential form (`certificate.path` + `certificate.passwordEnv`, or `secretEnv`). Problems are reported with the other pre-flight problems (exit 2). The named environment variables only have to be set on a real run.
- **D-04:** The CLI sets `.from(account.from)` explicitly on every mail, in both modes, so dry-run and a real run render the same mail and the CLI does not depend on the account's `defaultFrom`.

### Journal and rerun rules
- **D-05:** A rerun skips only rows whose key has an `accepted` line in the journal (spec §8.3.4, literal reading). A row recorded as failed, permanently or after its retries ran out, is sent again on the next run.
- **D-06:** A duplicate address in `recipients.csv` (case-insensitive, `Locale.ROOT`, the same normalisation as the journal key) is a pre-flight problem (exit 2). The message names both line numbers.
- **D-07:** The journal gets one forced line per send attempt. A retried transient attempt has outcome `transient`, and the row's last attempt has `accepted` or `failed`. Each line holds the key, email, timestamp, outcome, and the error class and message if there is one (CLI-06).
- **D-08:** The campaign ID lives only in the key prefix (`campaignId:lowercase(email)`). Lines from other campaigns in the same journal file are not matched and raise no error. There is no extra campaign check.

### Retry and wait timing
- **D-09:** A server-supplied `retryAfter` is capped at a 5-minute constant in the code, with no option for it. Without `retryAfter` the wait is 30 s.
- **D-10:** "Retry up to 3 times" means up to 4 attempts per row (1 + 3 retries), so a row writes at most four journal lines.
- **D-11:** Spacing applies to every send attempt, retries included. The wait before a retry is `max(retryAfter capped or 30 s, rate spacing)`, so the mailbox never gets more than `--rate` requests per minute. Skipped rows don't wait (CLI-07).
- **D-12:** Tests avoid real waits through a plain sleep function (for example `Consumer<Duration>`, defaulting to `Thread.sleep`) passed through the command class constructor next to the `Mailer`. Tests record and assert the waits. No `Clock`, no new interface.

### Output and exit codes
- **D-13:** Exit codes: `0` nothing failed. `1` at least one row failed, or a journal write failed (the run stops at once). `2` covers pre-flight, account-file and CLI-argument errors (picocli's default usage-error code is 2 as well) and the Phase 2 "graph not available" stop (D-01). `3` means the run stopped on `SenderRejectedException` or `AuthRequiredException`. There are no other codes.
- **D-14:** stdout gets one progress line per recipient in the form `[3/5] anna@example.org accepted`, `[4/5] bob@example.org skipped (journal)`, `[5/5] carl@example.org failed: PermanentMailException: <message>`, and `... transient, retry 1/3 in 30s` for a retry. Pre-flight problems, the stop reason and the journal error go to stderr. The final counts line (accepted / skipped / failed) goes to stdout. The CLI's console output is user-facing output, not library logging, so the address may appear in it. The library's DEBUG-only rule for addresses is not affected.
- **D-15:** Dry-run creates `out/` if needed and overwrites files with the same name. Other files in it stay.
- **D-16:** Dry-run file names keep `[A-Za-z0-9._@-]` from the address and replace every other character with `_`. `<n>` is zero-padded to the width of the row count (`01-`, `02-` ...) so the files sort in order.

### Claude's Discretion
- How the owner starts the CLI: the expected simple choice is the runnable jar with a manifest classpath and a `target/lib/` directory filled by `maven-dependency-plugin` `copy-dependencies`, as the stack research suggests. No shade plugin.
- How the example PDF and PNG are made: generate them once (each under 100 KB, neutral metadata, no author or host names), check them in, and add no generator code to the build.
- Dry-run writes `.txt` only when `body.txt` exists. It lists the attachments on stdout.
- Exact class split inside the sample (for example command, account file, campaign/CSV reader, journal), within Rule 0: few classes, no forwarding layers.
- Exact journal outcome spelling beyond `accepted` / `failed` / `transient`, timestamp format (ISO-8601 UTC recommended), and how a damaged last journal line is handled (it must not be silently counted as accepted).
- How `--limit N` counts: the first N CSV rows, skipped rows included, is the literal reading of §8.2 ("process only the first N recipients").
- Wording of pre-flight messages, as long as each names the file, line or column involved and never prints a secret value.

### Deferred Ideas (OUT OF SCOPE)
None. The discussion stayed within phase scope.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| CLI-01 | picocli `serial-mail send` with all options, campaign ID defaults to the directory name | picocli 4.7.7 verified (root + `addSubcommand(instance)`, exit 2 on usage errors, `getOut()/getErr()` capture); default id via `toAbsolutePath().normalize().getFileName()` (Pitfall 7) |
| CLI-02 | `account.properties` UTF-8, keys, env-var names only | `Files.newBufferedReader` + `Properties.load(Reader)` (strict UTF-8); backslash and trailing-space traps verified (Pitfalls 2, 3) |
| CLI-03 | campaign files UTF-8, `subject.txt` trimmed, optional `body.txt`, `attachments/` | one `readText` helper: strict UTF-8, strip leading BOM (verified `readString` keeps it) |
| CLI-04 | hand-written RFC 4180 reader, `;` detection, BOM | state machine in Code Examples; edge-case list in Pattern 2 |
| CLI-05 | pre-flight reports all problems, exit 2 | collect-into-`List<String>` pattern; own placeholder regex (core renderer is package-private and reports the first problem only) |
| CLI-06 | append-only JSON-lines journal, forced per line, write failure stops the run | `FileChannel` CREATE+WRITE+APPEND + `force(true)` verified; Jackson `ObjectMapper` for lines; newline repair + lenient decode (Pattern 3) |
| CLI-07 | `--rate` spacing, skipped rows don't wait | sleep-before-attempt algorithm with no clock (Pattern 4) |
| CLI-08 | transient retry, other permanent failures recorded | catch order matters: `AuthRequiredException` is NOT a `PermanentMailException` (Pitfall 1) |
| CLI-09 | sender-rejected / auth-required stop, exit 3 | same catch order; count and print behaviour on stop is an open question |
| CLI-10 | progress line per recipient, final counts, exit 0 only if nothing failed | D-14 format; picocli `getOut()` autoflush verified |
| CLI-11 | dry-run files, attachments listed, no network, no journal | `Mail.message().html()/text()/subject()` are public record accessors; sanitiser in Code Examples |
| CLI-12 | command logic takes a `Mailer` seam | `Function<AccountFile, Mailer>` + `Consumer<Duration>` constructor seam (Pattern 1) |
| CLI-13 | example campaign: 3 placeholders, PDF + PNG < 100 KB, 5 `example.org` rows | generator script verified: PNG 15.6 KB, PDF 602 bytes, no metadata; `.gitignore` blocks `account.properties` (Pitfall 4) |
</phase_requirements>

## Project Constraints (from CLAUDE.md)

Extracted from `/home/coder/morimail/CLAUDE.md` and `/home/coder/morimail/.claude/CLAUDE.md`. Treat as locked.

- **Rule 0:** simplest code that meets the spec. Only two extension points (`TransportProvider`, `TokenProvider`). No new interface, SPI, factory, strategy or abstract base class unless two real implementations exist. No frameworks, DI, annotation processing or reflection (picocli's own runtime reflection is the accepted exception, per the stack research). Prefer one clear class over several forwarding classes. "If a requirement seems to need something elaborate, stop and ask."
- **Dependencies:** ask before adding any. picocli 4.7.7 is already approved for the sample only. No CSV library. No shade plugin.
- **Public repo:** no secrets, tenant IDs, real addresses, internal hosts or customer names in any file, fixture or commit. Examples use `example.com`, `example.org` and zero GUIDs.
- **Never send real mail; never publish, deploy or tag.**
- **Do not log tokens, secrets, assertions or full mail bodies.** (The CLI's console output may print the address, D-14.)
- **Commits:** as `Stephan Wald <stephan@morisoft.de>`; the workspace `GIT_*` env vars override local config, so export the identity before every commit (memory note `git-identity-env-override`).
- **Build:** `./mvnw verify` must pass offline; Javadoc must build (the sample skips Javadoc, `maven.javadoc.skip=true`).
- **Java 17 API surface** (`--release 17` on JDK 21). Verified compile failures on this machine: `Thread.sleep(Duration)` and `List.getFirst()` do not compile (see Pitfall 8).
- **Stop at the Phase 3 checkpoint:** no Entra or Graph code in this phase.

## Summary

The Phase 1 library already provides everything the sample needs. `Mail.using(accountId)...from(..).to(..).template(MailTemplate, Map<String,?>).attach(Path).build()` renders and validates in one call; a CSV row `Map<String,String>` passes straight in. `Mailer.validate/send`, the error tree, and the `RecordingTransport` test-jar are all consumed by the sample already (the smoke test passes). The sample module has no `src/main` yet and no picocli declaration. I added picocli plus the jar-manifest and `copy-dependencies` wiring to a scratch copy of the repo and ran `./mvnw -o -B verify`: BUILD SUCCESS offline, `target/lib/` holds picocli 4.7.7, jackson 2.23.0 and the morimail jar, and `java -jar target/morimail-sample-serial-0.1.0-SNAPSHOT.jar send --rate 5` runs; an unknown option exits 2. No new dependency is needed for anything in this phase.

The design that satisfies every locked decision with the least code is five small classes in `de.morisoft.morimail.sample`: the picocli root `SerialMail` (main plus the Phase 3 factory stub), `SendCommand` (pre-flight and run loop, taking a `Function<AccountFile, Mailer>` and a `Consumer<Duration>`), `AccountFile`, `Campaign` (with the CSV reader as a static method or its own small class), and `Journal`. Retry/rate timing needs no clock: sleep once before every attempt except the first attempt of the run, with `spacing` for a new row and `max(retryWait, spacing)` for a retry; tests record the `Consumer<Duration>` calls.

Three things in the existing repo will bite the planner if missed: (1) `AuthRequiredException` extends `MailException`, not `PermanentMailException` (CONTEXT.md's code-context note has this wrong), so the catch order is load-bearing; (2) `.gitignore` line 32 `account.properties` blocks the shipped example account file, which dry-run needs; (3) the core's placeholder regex is package-private and `build()` reports only the first problem, so "all placeholders exist as columns, all problems together" needs a one-line regex in the sample.

**Primary recommendation:** Build `SendCommand` as a picocli `Callable<Integer>` whose constructor takes the mailer factory and the sleep function; collect all pre-flight problems in a `List<String>`; keep the built `Mail` objects from pre-flight for the send loop; journal with `FileChannel` + `force(true)` + Jackson; check in generator-made fixtures (PNG 15.6 KB, PDF 602 B) and add a `.gitignore` negation for the example `account.properties`.

## Architectural Responsibility Map

Single-tier command-line tool; the table records which part owns what so the planner does not push library concerns into the sample or the reverse.

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Argument parsing, exit codes | Sample CLI (picocli) | — | picocli gives exit 2 for usage errors (verified) |
| `account.properties` parsing | Sample CLI | — | Sample-only file format (REQUIREMENTS "Out of Scope": config files are a consumer concern) |
| CSV reading, campaign loading | Sample CLI | — | Hand-written, comma-only (owner decision) |
| Placeholder-vs-column check | Sample CLI | Library renderer | The renderer is package-private; it fails on the first missing placeholder only |
| Rendering, address and attachment validation | Library (`Mail.Builder.build()`) | — | Pre-flight just calls `build()` per row |
| Transport limits | Library transport `validate` via `Mailer.validate` | — | Sample calls it once for the first row (real runs only) |
| Send, error categories | Library (`Mailer.send`, error tree) | — | Sample only reacts to the categories |
| Retry, rate spacing, journal, resume | Sample CLI | — | Spec §2: retries/queues belong to the consumer |
| Real `Mailer` construction (Entra + graph) | Sample CLI factory method | Phase 3 | Phase 2 stub throws the D-01 message |

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| picocli (`info.picocli:picocli`) | 4.7.7 | `serial-mail send` command, options, exit 2 on usage errors | Owner-approved stack for the sample only. Already version-managed in the parent POM (`<picocli.version>4.7.7</picocli.version>`). [VERIFIED: https://repo1.maven.org/maven2/info/picocli/picocli/maven-metadata.xml: `<latest>4.7.7</latest>` `<release>4.7.7</release>`, lastUpdated 20250419004347] |
| `de.morisoft:morimail` | `${project.version}` | Mail model, templates, `Mailer`, error tree | Already a dependency of the sample |
| jackson-databind | 2.23.0 (transitive via `morimail`, BOM-managed) | One JSON object per journal line (write) and reading lines back | Already in the runtime tree: `target/lib/` listing showed `jackson-databind-2.23.0.jar`, `jackson-core-2.23.0.jar`, `jackson-annotations-2.23.jar` [VERIFIED: scratch build `ls target/lib`] |
| JDK only | 17 API level | `java.nio.file`, `FileChannel`, `Properties`, `Duration`, `Locale` | No other dependency (Rule 0) |

### Supporting (test scope, already declared in the sample POM)
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| junit-jupiter (BOM 6.1.3) | 6.1.3 | Tests, `@TempDir` for campaign/journal/out fixtures | All sample tests |
| assertj-core | 3.27.7 | Assertions | All sample tests |
| morimail `test-jar` | `${project.version}` | `RecordingTransport`, registered as `recording` through `ServiceLoader` | Every command test (see "Test seams") |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Transitive jackson-databind | Declare `jackson-databind` explicitly in the sample POM | Adds no artifact (same BOM-managed version) and states the real dependency; but touches the POM for no behaviour change. Planner choice; the build works either way (verified without the declaration). |
| `FileChannel` + `force(true)` | `Files.write(..., APPEND)` + `FileDescriptor.sync()` via `FileOutputStream` | `FileChannel` is the shortest correct way to get a forced append; `Files.write` has no fsync |
| `Consumer<Duration>` sleep seam | `java.time.Clock` / a `Sleeper` interface | Locked out by D-12 (Rule 0) |

**Installation (sample `pom.xml`, versions come from the parent):**
```xml
<dependency>
  <groupId>info.picocli</groupId>
  <artifactId>picocli</artifactId>
</dependency>
```
Verified wiring for the runnable jar (scratch build, offline, Maven 3.10.1 wrapper, JDK 21 with release 17):
```xml
<build>
  <plugins>
    <plugin>
      <artifactId>maven-jar-plugin</artifactId>
      <configuration>
        <archive>
          <manifest>
            <mainClass>de.morisoft.morimail.sample.SerialMail</mainClass>
            <addClasspath>true</addClasspath>
            <classpathPrefix>lib/</classpathPrefix>
          </manifest>
        </archive>
      </configuration>
    </plugin>
    <plugin>
      <artifactId>maven-dependency-plugin</artifactId>
      <executions>
        <execution>
          <id>copy-lib</id>
          <phase>package</phase>
          <goals><goal>copy-dependencies</goal></goals>
          <configuration>
            <outputDirectory>${project.build.directory}/lib</outputDirectory>
            <includeScope>runtime</includeScope>
          </configuration>
        </execution>
      </executions>
    </plugin>
  </plugins>
</build>
```
Both plugins are already pinned in the parent `pluginManagement` (`maven-jar-plugin` 3.5.1, `maven-dependency-plugin` 3.11.0 [VERIFIED: /home/coder/morimail/pom.xml:126-129, 150-153]). `includeScope=runtime` kept the `test` scope morimail test-jar out of `target/lib`. The result is run as `java -jar target/morimail-sample-serial-0.1.0-SNAPSHOT.jar send ...`.

**Version verification:** picocli 4.7.7 confirmed as the latest release on Maven Central today (command above). The dependency-plugin 3.11.0 and picocli 4.7.7 jars are already in `~/.m2`, which is why the offline build passed.

## Package Legitimacy Audit

| Package | Registry | Age | Downloads | Source Repo | Verdict | Disposition |
|---------|----------|-----|-----------|-------------|---------|-------------|
| info.picocli:picocli 4.7.7 | Maven Central | latest release 2025-04-19; project long established | n/a (not reported by Maven Central) | https://github.com/remkop/picocli | not checkable: `gsd_run query package-legitimacy check` supports only npm, pypi, crates ("Usage: ... --ecosystem <npm|pypi|crates>") | Approved by owner in the project stack (`.claude/CLAUDE.md`: "picocli 4.7.7 | Sample CLI only"); no new package is introduced by this phase |

**Packages removed due to [SLOP] verdict:** none
**Packages flagged as suspicious [SUS]:** none

No other package is added: Jackson is already in the library's tree, everything else is JDK. [ASSUMED] that the `remkop/picocli` repository URL is the project's source (from training knowledge); not load-bearing because the dependency is pre-approved and already cached locally.

## Architecture Patterns

### System Architecture Diagram

```
CLI args ──► picocli (SerialMail root ► SendCommand) ──usage error──► exit 2
                              │
                              ▼
        ┌──────────────── PRE-FLIGHT (collect List<String> problems) ────────────────┐
        │ AccountFile.read(account.properties)      keys, one credential form       │
        │ Campaign.read(dir): subject/body/csv/attachments  (UTF-8, BOM stripped)   │
        │   CSV reader: header ';' check, email column, field counts, duplicates    │
        │   placeholders in subject/html/text  vs  CSV columns  (sample regex)      │
        │   per row: Mail.using(id).from(..).to(..).template(..).attach(..).build() │
        │   real run only: mailerFactory.apply(account) ► mailer.validate(first)    │
        └───────────────┬──────────────────────────────────────┬────────────────────┘
          problems ≠ ∅ ─┘ ► stderr, exit 2          none ─────┘
                              │
              ┌───────────────┴────────────────┐
         --dry-run                          real run
              │                                 │
   write out/NN-<safe>.html|.txt|        Journal.open(read accepted keys; append channel)
   .subject.txt, list attachments        for row in first N rows:
   (no Mailer, no journal, no net)         key accepted? ► "skipped (journal)", no wait
              │                            else attempt loop (≤ 4):
          exit 0                             sleep(spacing | max(retryWait, spacing)) unless first attempt of run
                                             mailer.send(mail)
                                              ├ ok ► journal accepted, "accepted"
                                              ├ TransientMailException ► journal transient (retry) / failed (4th)
                                              ├ SenderRejected | AuthRequired ► journal failed, stop, exit 3
                                              └ other MailException ► journal failed, continue
                                           journal IOException ► stderr, stop, exit 1
                                           counts line ► stdout; exit 0 iff failed == 0 else 1
```

### Recommended Project Structure
```
morimail-sample-serial/
├── pom.xml                                   # + picocli, jar manifest, copy-dependencies
├── example-campaign/
│   ├── account.properties                    # example.com, zero GUIDs (needs .gitignore negation)
│   ├── subject.txt   body.html   body.txt   recipients.csv
│   └── attachments/  info.pdf   logo.png
└── src/
    ├── main/java/de/morisoft/morimail/sample/
    │   ├── SerialMail.java      # root @Command + main + real-Mailer factory (Phase 3 fills it)
    │   ├── SendCommand.java     # @Command(name="send") Callable<Integer>: pre-flight + run loop
    │   ├── AccountFile.java     # record + static read(Path, List<String> problems)
    │   ├── Campaign.java        # record: template, rows (line, Map), attachments + CSV reader
    │   └── Journal.java         # accepted-key set + forced append
    └── test/java/de/morisoft/morimail/sample/
        ├── RecordingTestJarSmokeTest.java    (exists)
        ├── CsvReaderTest / AccountFileTest / JournalTest
        └── SendCommandTest.java              # recording-backed, recorded waits, @TempDir campaigns
        └── ExampleCampaignTest.java          # dry-run of ./example-campaign (cwd = module dir)
```
Keep the classes package-private except `SerialMail` (has `main`). The library's Javadoc and thread-safety rules target public library types (REPO-10); the sample is single-threaded and not published.

### Pattern 1: The CLI-12 seam (constructor injection with plain JDK types)
**What:** `SendCommand(Function<AccountFile, Mailer> mailerFactory, Consumer<Duration> sleep)`. `main` passes `SerialMail::realMailer` and a `Thread.sleep` wrapper; tests pass `settings -> Mailer.of(account)` and a list-recording consumer. Dry-run never calls the factory. If the factory throws (`IllegalStateException`), pre-flight records it as a problem, skips `mailer.validate`, and the run exits 2 with the other problems (so "all problems together" still holds).
**Why a `Function`, not a `Mailer` instance:** D-01/D-02 need the account file parsed (and dry-run to run with no Mailer) before a Mailer exists; a `Function` is a JDK type, not a new interface, so Rule 0 holds. Phase 3 replaces only the body of `realMailer` (CLI-14).
**Picocli wiring (verified):** a root class with `@Command(name = "serial-mail")` and no `@Command(subcommands=...)`; `new CommandLine(new SerialMail()).addSubcommand(new SendCommand(...))` (the instance carries its own `@Command(name = "send")`). `cl.setOut/ setErr` on the root propagates to the subcommand; inside `call()` print through `spec.commandLine().getOut()/getErr()` (`@Spec CommandSpec spec`), so tests capture output with `StringWriter`s and never touch `System.out`. A test helper `static CommandLine commandLine(Function<..>, Consumer<Duration>)` in `SerialMail` lets `main` and tests build the identical tree.
**Verified behaviour (scratch run of picocli 4.7.7):**
```
send --campaign c            => exit 0   (Callable returned 0)
send --campaign c --rate 3   => exit 3   (Callable returned 3 -> exit code 3)
send                         => exit 2   "Missing required option: '--campaign=<campaign>'"
(no args)                    => exit 2   "Missing required subcommand"
send ... --rate abc          => exit 2   "Invalid value for option '--rate': 'abc' is not an int"
call() throws ParameterException => exit 2
call() throws IllegalStateException => exit 1 (stack trace)
```
`Path` options need no custom converter. Return 2 directly (after a stderr line) for `--rate < 1` or `--limit < 1`; no `ParameterException` needed.

### Pattern 2: CSV reader (hand-written, one static method, ~50 lines)
Input is the whole file as a `String` (UTF-8, leading `\uFEFF` removed by the shared `readText` helper). A char loop with these rules:
- `,` ends a field outside quotes; `"` opens quotes only at field start; inside quotes `""` is a literal quote and `,`/CR/LF are data; a quote closes otherwise.
- Record ends at `\n`, `\r\n` or a lone `\r` (outside quotes). Track the physical line number where each record **starts** (count LF inside quoted fields too), because D-06 and the pre-flight messages name line numbers.
- A completely empty unquoted line is skipped; a final line terminator does not create an extra empty record; EOF inside quotes is a problem ("unterminated quoted field starting in line N").
- Return `List<Record(int line, List<String> cells)>`. Everything else (header handling, `;`, field count, duplicates, placeholders) happens in `Campaign` on top of that list.
Header rules: any header cell containing `;` means the file looks `;`-delimited -> one clear problem ("recipients.csv: header looks ';'-delimited; this reader accepts commas only") and **stop analysing the rows** (every row would add noise). No cell exactly equal to `email` -> problem. Duplicate header names -> problem. Data rows with a cell count different from the header -> problem naming the line. Header-only file (zero rows) -> problem (an empty campaign must not exit 0 silently). `Map<String,String>` per row: `LinkedHashMap` in header order.
Header name match for `email`: exact, case-sensitive (placeholders are case-sensitive in the renderer). See Open Questions for trimming.

### Pattern 3: Journal (forced, append-only, JSON lines)
```java
// Source: JDK java.nio.channels.FileChannel; append + force behaviour executed in scratch (3 forced lines read back in order)
FileChannel ch = FileChannel.open(path, CREATE, WRITE, APPEND);
ByteBuffer b = ByteBuffer.wrap((json + "\n").getBytes(StandardCharsets.UTF_8));
while (b.hasRemaining()) ch.write(b);
ch.force(true);                       // data + metadata to the device, once per line (<= 20/min, cost irrelevant)
```
- **Reading at start:** `new String(Files.readAllBytes(p), UTF_8)` (lenient, replacement characters) then split on `\n`. Do **not** use `Files.readAllLines`/`readString` here: they throw `MalformedInputException` for the whole file when a crash tears a multibyte character in the last line (strict behaviour verified: `strict: MalformedInputException`).
- Per line: `mapper.readTree(line)`; collect `key` where `outcome` equals `accepted`. A blank line is ignored. A line that does not parse is **reported on stderr with its line number and ignored** (never counted as accepted; the row is simply sent again, which is the owner's documented at-least-once rule). Planner may instead make it a pre-flight problem; see Open Questions.
- **Newline repair:** when opening for append, if the file is non-empty and its last byte is not `\n`, write one `\n` first so the next record never fuses with a torn one.
- **Line shape (Claude's discretion fields):** `{"key":"...","email":"...","timestamp":"2026-11-21T14:50:00.123456Z","outcome":"accepted"}` plus `"errorClass"`, `"errorMessage"` only when present. `Instant.now().toString()` is ISO-8601 UTC. Build the line with `ObjectMapper.writeValueAsString(LinkedHashMap)`: default output is a single line and embedded newlines in an exception message are escaped, so one record is always one line.
- A write or force `IOException` stops the run at once: stderr message, exit 1 (D-13).
- Open the channel only after pre-flight passed (a failed pre-flight must not create a journal file); an open failure at that point is exit 2 with a message. Journal default `<campaign>/journal.jsonl` is already ignored by `.gitignore` (`*.jsonl`).
- Key: `campaignId + ":" + email.toLowerCase(Locale.ROOT)`. A valid address cannot contain `:` (the library rejects it), so the key is unambiguous.

### Pattern 4: Retry and rate with no clock (D-09 .. D-12)
```java
// spacing = Duration.ofMillis(60_000L / rate); CAP = Duration.ofMinutes(5); DEFAULT_WAIT = Duration.ofSeconds(30)
boolean sentAny = false;                    // any attempt made earlier in THIS run (skipped rows don't set it)
for (Row row : firstN) {
    if (accepted.contains(key)) { skipped++; print skipped; continue; }
    Duration retryWait = null;              // null = first attempt of this row
    for (int attempt = 1; attempt <= 4; attempt++) {
        if (sentAny) sleep.accept(retryWait == null ? spacing : max(retryWait, spacing));
        sentAny = true;
        try { mailer.send(mail); ... break; }
        catch (SenderRejectedException | AuthRequiredException e) { ...stop, exit 3 }
        catch (TransientMailException e) {
            if (attempt == 4) { journal failed; failed++; break; }
            retryWait = min(e.retryAfter().orElse(DEFAULT_WAIT), CAP);
            journal transient; print "transient, retry <attempt>/3 in <wait>s"
        }
        catch (MailException e) { journal failed; failed++; break; }   // PermanentMailException, InvalidMailException, other
    }
}
```
This gives a recordable list of waits (`[3s, 3s, 30s, 3s, ...]`) and needs no `Instant`/`Clock` outside the journal timestamp. It paces by sleeping before each attempt, so time spent inside `send` makes the real rate slightly lower than `--rate`: conservative for a throttled mailbox. `--rate 20` -> 3000 ms; `60_000L / rate` truncates (rate 7 -> 8571 ms); validate `rate >= 1`.
Default sleeper: `d -> { try { Thread.sleep(d.toMillis()); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException("Interrupted while waiting", e); } }`. (`Thread.sleep(Duration)` does not compile at release 17, Pitfall 8.)

### Pattern 5: Pre-flight is one method that fills a `List<String>`
Order, so that problems are reported together and nothing is created on failure: (1) `AccountFile.read` (missing keys, credential form); (2) `Campaign.read` (files, CSV, placeholders vs columns, duplicates, per-row `build()`); (3) real run only, and only if (1) found no problem: `mailerFactory.apply(account)` and `mailer.validate(firstMail)` (a `PermanentMailException` becomes a problem naming the first row's CSV line); (4) if the list is non-empty print each as one stderr line and return 2. Keep the built `List<Mail>` from step 2 and reuse it in the run loop (one build per row; attachments are `Path` references, not bytes).
`build()` per row reports the first problem of that row only (Phase 1 D-05), and a campaign-wide defect (an unreadable attachment) fails identically for every row. Planner decision: accept N identical lines (simplest) or check attachments once before the row loop. With the five-row example this is not an issue.

### Pattern 6: Dry-run output
`Mail m = built.get(i); m.message().html()`, `.text()` (null when there is no `body.txt`; write `.txt` only then, per discretion), `.subject()`. All three are public components of the `MailMessage` record [VERIFIED: morimail/src/main/java/de/morisoft/morimail/MailMessage.java: `public record MailMessage(MailAddress from, List<MailAddress> to, List<MailAddress> cc, List<MailAddress> bcc, MailAddress replyTo, String subject, String html, String text, List<Attachment> attachments)`]. So dry-run never needs the core renderer. Files: `Files.createDirectories(out)`, `Files.writeString(path, content)` (UTF-8 default, overwrites). List attachments on stdout (name, content type, size), "attachments: none" when the directory is absent or empty.

### Anti-Patterns to Avoid
- **`catch (PermanentMailException)` before the stop case, or assuming `AuthRequiredException` is permanent.** It is not (Pitfall 1).
- **Reading `account.properties` with `Properties.load(InputStream)`.** ISO-8859-1, not UTF-8.
- **Real `Thread.sleep` or `System.out` in the command logic.** Tests then wait or cannot capture.
- **A `Mailer` built before the account file is validated, or built in dry-run.** Violates D-01/D-02/D-03.
- **A second placeholder parser that differs from the core's.** Use the identical regex (Code Examples) so the sets agree.
- **Extra abstraction** (journal interface, CSV strategy, pluggable sleeper type). Rule 0.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Argument parsing, usage errors, exit 2 | manual `args` loop | picocli | Verified: missing option, bad int, missing subcommand all exit 2 with a message |
| JSON for journal lines (escaping of quotes, newlines, unicode) | string concatenation | Jackson `ObjectMapper` (already transitive) | Messages contain arbitrary text; guaranteed one line per record |
| Address validation, CR/LF checks, rendering, attachment existence | own checks | `Mail.Builder.build()` | Phase 1 owns these rules; pre-flight reuses them by building every row's mail |
| fsync | `FileOutputStream.flush()` | `FileChannel.force(true)` | `flush()` on a stream does not reach the device |
| Failure injection in tests | a fake `Mailer` | `RecordingTransport.failFor/failNext/failValidation` from the test-jar | Already built for this (CORE-07) |
| UTF-8 file reading | `new String(bytes)` / `FileReader` | `Files.readString` (strict) + BOM strip; `Files.newBufferedReader` for Properties | Strictness gives a real "not valid UTF-8" pre-flight message |

**Key insight:** the genuinely custom code in this phase is small: the CSV state machine, the attempt loop, and the pre-flight list. Everything that looks like validation is already in `build()`.

## Runtime State Inventory

Not a rename, refactor or migration phase: omitted.

## Common Pitfalls

### Pitfall 1: Wrong exception hierarchy assumption
**What goes wrong:** Code that groups `AuthRequiredException` under `PermanentMailException` records the auth failure and carries on, or the stop branch is unreachable.
**Why it happens:** `02-CONTEXT.md` (code_context) says "Error tree: `TransientMailException`, `PermanentMailException` > `SenderRejectedException` / `AuthRequiredException` / `InvalidMailException`". The source says otherwise: [VERIFIED: morimail/src/main/java/de/morisoft/morimail/AuthRequiredException.java] `public class AuthRequiredException extends MailException`, while [VERIFIED: SenderRejectedException.java] `public class SenderRejectedException extends PermanentMailException` and [VERIFIED: InvalidMailException.java] `public class InvalidMailException extends PermanentMailException`. REQUIREMENTS ERR-01 matches the source ("`AuthRequiredException`" is a sibling of `PermanentMailException`).
**How to avoid:** Catch in this order: `SenderRejectedException | AuthRequiredException` (stop, exit 3), `TransientMailException` (retry), `MailException` (record, continue). `Mailer.send` declares `throws MailException`. A test must use `failFor(email, new AuthRequiredException("..."))` and assert exit 3.
**Warning signs:** an auth failure shows `failed` in the progress line and the run continues.

### Pitfall 2: `Properties` eats backslashes and keeps trailing spaces
**What goes wrong:** `certificate.path=C:\keys\app.p12` loads as `C:keysapp.p12`; `from=a@example.com   ` keeps the trailing spaces and then fails address validation.
**Verified:** `C:keysapp.p12 | [a@example.com   ] | Ünï` (UTF-8 value survived because a `Reader` was used).
**How to avoid:** `Files.newBufferedReader(path)` (UTF-8, strict) into `Properties.load(Reader)`, `.trim()` every value, and note in the example `account.properties` comment that paths use `/` or doubled backslashes. Missing/blank key -> problem naming the **key**, never printing a value.

### Pitfall 3: Echoing property values leaks secrets
**What goes wrong:** If a user mistakenly puts the secret itself in `secretEnv=` (the key expects an env var *name*), a message like "environment variable 'xyz...' is not set" prints the secret to stderr and CI logs.
**How to avoid:** Account pre-flight messages name keys only ("account.properties: missing key 'tenantId'", "exactly one credential form is required"). The Phase 3 factory must follow the same rule when an env var is unset. Mention it in the factory stub comment.

### Pitfall 4: `.gitignore` swallows the example account file
**What goes wrong:** [VERIFIED: /home/coder/morimail/.gitignore lines 28-32] it contains `out/`, `*.jsonl`, `account.properties` (and `*.jar`, `*.zip`). `git check-ignore -v morimail-sample-serial/example-campaign/account.properties` reports `.gitignore:32:account.properties`. The example would never be committed, and dry-run (which always reads the account file, D-03) would fail on a fresh clone; success criterion 1 breaks.
**How to avoid:** add after line 32: `!morimail-sample-serial/example-campaign/account.properties`. Verified in a scratch repo: the example path is then not ignored while `other/account.properties` still is. Alternative: ship `account.example.properties`, but the CONTEXT/spec examples and tests use `account.properties`. Also expect: `recipients.csv`, `body.*`, `attachments/*.pdf|png` are not ignored (checked); `journal.jsonl` and `out/` stay ignored (wanted).

### Pitfall 5: Reading files with a BOM or bad bytes
**Verified:** `Files.readString` keeps a leading BOM (`65279` as first char) and throws `MalformedInputException` on ISO-8859-1 bytes. A Windows editor writing `subject.txt` with a BOM would put `\uFEFF` into the subject.
**How to avoid:** one `readText(Path)` helper: strict `Files.readString`, strip a single leading `\uFEFF`, report `MalformedInputException` as "<file> is not valid UTF-8". `subject.txt`: remove trailing `\r`/`\n` characters (spec: "trailing newline trimmed"). Use the helper for CSV, subject, body.html, body.txt.

### Pitfall 6: Placeholder check must match the core exactly and report all columns
**What goes wrong:** relying on `build()` only reports the first missing column per row (Phase 1 D-05) and names the first row only.
**How to avoid:** extract names from subject, html and text with the same pattern the core uses: [VERIFIED: morimail/src/main/java/de/morisoft/morimail/TemplateRenderer.java] `Pattern.compile("\\$\\$(!?)([A-Za-z0-9_.-]+)\\$\\$")` (group 2 is the name; `$$!name$$` raw form included). `TemplateRenderer` is package-private (`final class TemplateRenderer`, `static MailTemplate render(...)`) and the core exposes no placeholder-name API, so a 3-line duplicate in the sample is the Rule 0 answer (widening the core's public API is not). Add a test that renders the example templates with a map built from the extracted names and expects success.

### Pitfall 7: Campaign ID default and path handling
**What goes wrong:** `--campaign .` or `--campaign ./campaign/` gives file name `.` or an odd id; the default journal path then moves.
**How to avoid:** `campaign.toAbsolutePath().normalize().getFileName().toString()`. Default journal `campaign.resolve("journal.jsonl")`; default `--out` is `out` (relative to the working directory).

### Pitfall 8: Java 17 API surface (`--release 17` on JDK 21)
**Verified compile errors** (javac --release 17): `Thread.sleep(java.time.Duration.ofMillis(1))` -> "incompatible types: Duration cannot be converted to long"; `list.getFirst()` -> "cannot find symbol". Use `d.toMillis()` and `list.get(0)`. Also avoid pattern-matching `switch`, `Math.clamp`, `StringBuilder.repeat` ([ASSUMED] these are 21-only; the first two compile failures are verified). Records, `instanceof` patterns, switch expressions, text blocks are fine on 17. JDK 17 itself is not installed here, CI runs it.

### Pitfall 9: Dry-run file names and traversal
`/` and `\` are not rejected by the library's address check (it forbids only controls, space, `,;<>()[]\":`) [VERIFIED: MailAddress.java `",;<>()[]\\\":".indexOf(c)`], so `../x@example.org` is a "valid" address. The D-16 sanitiser keeps only `[A-Za-z0-9._@-]`, so `/` becomes `_` and the `<n>-` prefix means a name can never be `.` or `..`. Two addresses that sanitise equally stay distinct through `<n>`. Always resolve the name against `out` and write only there.

### Pitfall 10: Ctrl-C and the in-flight mail
A send in flight when the JVM is killed has no journal line, so a rerun sends it again (at-least-once, the owner's recorded decision). Not a bug; mention in the README/help text if desired (Phase 4 docs).

## Code Examples

### Placeholder names for the pre-flight column check
```java
// Source: same pattern as morimail/src/main/java/de/morisoft/morimail/TemplateRenderer.java (PLACEHOLDER)
private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\$(!?)([A-Za-z0-9_.-]+)\\$\\$");

static Set<String> placeholders(String... parts) {
    Set<String> names = new LinkedHashSet<>();
    for (String part : parts) {
        if (part == null) continue;
        Matcher m = PLACEHOLDER.matcher(part);
        while (m.find()) names.add(m.group(2));
    }
    return names;
}
```

### Dry-run file name (D-16)
```java
static String safe(String email) {
    StringBuilder sb = new StringBuilder(email.length());
    for (int i = 0; i < email.length(); i++) {
        char c = email.charAt(i);
        boolean keep = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                || c == '.' || c == '_' || c == '@' || c == '-';
        sb.append(keep ? c : '_');
    }
    return sb.toString();
}
// "%0" + width + "d-%s": width = Math.max(2, String.valueOf(rowCount).length())  (Open Question 3, RESOLVED by the owner: minimum width 2)
String base = String.format("%0" + width + "d-%s", n, safe(email));
```

### Building one row's mail (D-04: explicit `from`)
```java
// Mail.using(String), .from(String), .to(String...), .template(MailTemplate, Map<String, ?>), .attach(Path), .build() throws InvalidMailException
// [VERIFIED: morimail/src/main/java/de/morisoft/morimail/Mail.java]
Mail.Builder b = Mail.using(account.id()).from(account.from()).to(row.email())
        .template(new MailTemplate(subject, html, textOrNull), row.fields());
for (Path file : attachments) b.attach(file);       // attachments sorted by file name
Mail mail = b.build();
```
Sort the attachment list (`Files.list` order is unspecified) so every mail, and the dry-run listing, is deterministic. Only regular files; a missing `attachments/` directory means none (see Open Question 6).

### Test setup against `recording` (CLI-12)
```java
// Source: pattern from RecordingTestJarSmokeTest (exists) and RecordingTransport API read in this session
MailAccount account = MailAccount.builder("entra-main").transport("recording")
        .defaultFrom("newsletter@example.com").build();      // id must equal account.id in the test's account.properties
RecordingTransport rec = (RecordingTransport) account.transport();
List<Duration> waits = new ArrayList<>();
CommandLine cl = SerialMail.commandLine(settings -> Mailer.of(account), waits::add);
StringWriter out = new StringWriter(), err = new StringWriter();
cl.setOut(new PrintWriter(out)); cl.setErr(new PrintWriter(err));
int code = cl.execute("send", "--account", acct.toString(), "--campaign", dir.toString(), "--rate", "20");
```
Scripted failures: `rec.failFor("bob@example.org", new AuthRequiredException("denied"))`, `rec.failNext(new TransientMailException("busy", Duration.ofSeconds(7)), ...)`, `rec.failValidation(new InvalidMailException("too big"))`, then `rec.sent()` and `rec.attempts()`. A "rerun" test builds a fresh `MailAccount` (new `RecordingTransport` per account) and reuses the same journal path.

### Fixture generator (run once, not part of the build)
Executed in the scratch directory with stdlib Python only (no Pillow, no PDF tool exists on this machine). Output: `logo.png` 15 619 bytes, 240x120, only IHDR/IDAT/IEND chunks (no tEXt/time/ICC), re-read successfully by `javax.imageio.ImageIO`; `info.pdf` 602 bytes, hand-written PDF 1.4 with correct xref offsets, no `/Info`, `/Producer`, `/Creator` or dates (`strings | grep -ci producer\|creator\|author` -> 0). The generator text is at `/tmp/claude-1000/-home-coder-morimail/c504dddd-f32c-4871-a71f-e8cc8bd9d849/scratchpad/gen/make_fixtures.py` (scratchpad; may be cleared, so the executing plan should recreate it from the description: PNG = signature + IHDR + IDAT(zlib level 9 of filter-0 RGB gradient rows) + IEND; PDF = catalog, pages, one page, one Helvetica text stream, xref table, trailer without `/Info`). Do not commit the script into the build. The byte output is deterministic for a given zlib build; the committed binaries are the source of truth.

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `Thread.sleep(long)` only | `Thread.sleep(Duration)` | JDK 19 | Not usable at `--release 17` |
| `Files.readAllLines` for logs | byte read + lenient decode where torn data is expected | n/a | Journal tolerance of a torn last line |
| Shade plugin for CLI jars | manifest classpath + `target/lib` | project decision | No service-file merging (the library's `ServiceLoader` entries stay intact) |

**Deprecated/outdated:** none relevant. picocli 4.7.7 is the latest release and there is no 5.x [VERIFIED: Maven Central metadata above].

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `Math.clamp` and `StringBuilder.repeat` are post-17 APIs (21) | Pitfall 8 | Low: they would simply fail to compile at release 17 and be caught by `./mvnw verify` |
| A2 | `remkop/picocli` is picocli's source repository | Package Legitimacy Audit | None for the build; the package is already owner-approved and cached |
| A3 | A user's torn last journal line is best handled by "warn on stderr, ignore, resend that row" rather than a pre-flight failure | Pattern 3 | If the owner prefers a hard stop, a rerun after a crash needs a manual journal edit; flagged in Open Questions |
| A4 | Counting the row that triggered a stop (exit 3) as `failed` and printing the counts line on a stop | Open Question 4 | Only affects counts display; exit code is 3 either way |

## Open Questions (RESOLVED)

1. **Does `--limit N` limit pre-flight too?**
   - Known: §8.2 says "process only the first N recipients"; §8.3.2 says "every row has a syntactically valid email".
   - Unclear: whether a bad row beyond N blocks a `--limit 1` verification run (DOCS-01 verification flow).
   - Recommendation: pre-flight checks the whole CSV (spec literal, no extra code), `--limit` only restricts the processing loop and the `[n/total]` total. Confirm with the owner when the plan is reviewed.
   - RESOLVED: pre-flight checks every row of the file; `--limit` restricts only the processing loop, the dry-run writer and the `[n/total]` total. Source: planner choice in plan 02-03 (flagged assumption "Pre-flight ignores --limit", reversible in SendCommand if the owner prefers otherwise).

2. **Trim the email cell?**
   - Known: the library rejects an address containing a space (`c <= ' '` in `MailAddress.invalidReason`), so a CSV cell `anna@example.org ` fails pre-flight with a "not allowed" message.
   - Recommendation: no silent trimming (the journal key and the mail must use the same string); the pre-flight message names the line. Header names are matched exactly.
   - RESOLVED: no trimming of cells or header names; header names are matched exactly and case-sensitively, and an address cell with a space reaches `build()`, which rejects it with the line number. Source: planner choice in plan 02-01 (Campaign.read) and plan 02-02 (CampaignTest `headerNamesAreMatchedExactly`, `addressCellIsNotTrimmed`).

3. **D-16 zero padding width.**
   - Known: "zero-padded to the width of the row count (`01-`, `02-` ...)". For the five-row example the width of `5` is one digit, giving `1-`, which contradicts the `01-` illustration.
   - Recommendation: planner picks one; the literal rule (`String.valueOf(rowCount).length()`) gives `1-..5-` for the example, a minimum width of 2 gives `01-..05-`. Tests assert whichever is chosen; ask the owner if it matters for the shipped example. Row count = rows processed (after `--limit`) is the natural reading.
   - RESOLVED: minimum width 2, so width = max(2, digit count of the processed-row count): the five-row example gives `01-` to `05-`, 120 rows give `001-` to `120-`. Source: owner decision (2026-11-21, plan revision); implemented in plan 02-01 Task 2 and pinned by DryRunTest and ExampleCampaignTest (plan 02-04).

4. **What happens to counts on the exit-3 stop?**
   - Known: §8.3.10 prints counts at the end; D-07 says the last attempt's line is `failed`.
   - Recommendation: journal a `failed` line for the stopping row, count it as failed, leave later rows unprocessed (neither counted), print the stop reason on stderr and the counts line on stdout, exit 3.
   - RESOLVED: as recommended: the stopping row gets a `failed` journal line and counts as failed, later rows are neither processed nor counted, the stop reason goes to stderr and the counts line to stdout, exit 3. Source: planner choice in plan 02-03 Task 1 (flagged assumption "Counts on a stop").

5. **Damaged journal lines.**
   - Recommendation in Pattern 3 (warn, ignore, newline repair). A damaged line in the middle (not the tail) is treated the same way.
   - RESOLVED: a damaged line anywhere in the file is reported as `warning: <file> line <n> is damaged and was ignored`, never counts as accepted (the row is sent again, at-least-once), and a missing final line feed is repaired before the first append; it is never a pre-flight stop. Source: planner choice in plan 02-03 Task 3 (Assumption A3).

6. **Missing `attachments/` directory.**
   - Known: CLI-03 lists `attachments/` without "optional" (unlike `body.txt`); the library accepts mails with no attachments.
   - Recommendation: absent or empty directory means no attachments, shown as "attachments: none" in the dry-run output so a typo is visible.
   - RESOLVED: as recommended: an absent or empty `attachments/` directory means no attachments, and the dry-run prints `attachments: none`. Source: planner choice in plan 02-01 (Task 1 Campaign.read, Task 2 DryRunTest).

7. **Spec §8.3.1 vs D-01.** The spec builds the token provider and `Mailer` before reading template and CSV; the locked D-01/D-02 define a different order for Phase 2. Locked decisions win; no action beyond following them.
   - RESOLVED: the locked D-01/D-02 order is followed (account file, campaign files, row builds, then the Mailer factory and `mailer.validate` on a real run only, then the journal). Source: owner decisions D-01 and D-02 in 02-CONTEXT.md; implemented in plan 02-01 Task 1.

8. **`recipients.csv` pre-flight with campaign-wide defects** (unreadable attachment fails every row identically): accept N lines or check once (Pattern 5). Planner choice.
   - RESOLVED: accept one problem line per failing row (`recipients.csv line <n>: <message>`), the simplest correct form. Source: planner choice in plan 02-01 Task 1 (flagged assumption "Campaign-wide build problems").

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| JDK | build and test | yes | Temurin 21.0.13.1 (compiles with `--release 17`) | JDK 17 only on CI (not installed here) |
| Maven wrapper | `./mvnw verify` | yes | 3.10.1 cached in `~/.m2/wrapper/dists/apache-maven-3.10.1` | — |
| picocli 4.7.7 jar | sample compile | yes | in `~/.m2/repository/info/picocli/picocli/4.7.7` | — |
| maven-dependency-plugin 3.11.0 | `target/lib` | yes, runs offline | in `~/.m2` (verified: `./mvnw -o verify` BUILD SUCCESS in scratch) | — |
| python3 (stdlib) | one-off PNG/PDF generation | yes | `/usr/bin/python3` | The same bytes can be produced with a throwaway Java single-file program |
| PDF/PNG tools (pdfinfo, qpdf, ImageMagick, Pillow) | fixture validation | no | — | PNG validated with `javax.imageio`; PDF xref offsets checked with a Python regexp |
| Network | not needed for `verify`; used only to confirm picocli's latest version | yes (curl succeeded) | — | — |

**Missing dependencies with no fallback:** none.
**Missing dependencies with fallback:** JDK 17 locally (CI covers it).

## Validation Architecture

`workflow.nyquist_validation` is `false` in `.planning/config.json`, so the formal section is omitted. Test seams the planner needs anyway:
- **Framework:** JUnit Jupiter 6.1.3 + AssertJ 3.27.7, run by Surefire 3.6.1 (`./mvnw -o -B -ntp verify`; already green with the smoke test).
- **Working directory:** Surefire's default is the module directory, so `ExampleCampaignTest` can use `Path.of("example-campaign")` and `Path.of("example-campaign/account.properties")` for the SC1 dry-run check (5 files per kind in a `@TempDir` out dir; assert no journal file was created and the factory lambda was never called).
- **Recommended cases (one per success criterion):** SC1 example dry-run + needs no env vars; SC2 broken campaign (placeholder without column, invalid email, `;` header, `failValidation`) -> one stderr report with all problems, exit 2, nothing sent, no journal created; SC3 five rows sent once, one progress line per row, journal has 5 `accepted` lines, rerun -> 5 `skipped`, `rec.sent()` empty; SC4 `failNext(Transient(retryAfter 7s), Transient(no retryAfter))` -> waits recorded (`max(7s, spacing)`, `30s`), 4th transient -> `failed`, run continues; `failFor(x, SenderRejected/AuthRequired)` -> exit 3 and later rows untouched; SC5 `--rate`, `--limit`, `--campaign-id`, `--journal` and UTF-8 round trip (umlaut in a CSV cell and in `account.properties`).
- **Unit tests:** CSV reader (BOM, CRLF, quoted comma, quoted newline with correct start line, `""`, unterminated quote, blank lines, no trailing newline, `;` header), journal (torn last line, newline repair, other campaign ignored, `transient` lines do not count as accepted).

## Security Domain

`security_enforcement` is enabled (ASVS level 1).

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no (Phase 3 owns credentials) | Phase 2 reads env-var **names** only |
| V3 Session Management | no | — |
| V4 Access Control | no | — |
| V5 Input Validation | yes | CSV/properties parsed by strict readers; addresses and content validated by `Mail.Builder.build()`; dry-run file names sanitised to `[A-Za-z0-9._@-]` |
| V6 Cryptography | no | none in this phase |
| V7 Error Handling and Logging | yes | pre-flight and stop messages name keys, files, lines; never values of `account.properties`; no token, secret or full body is printed |
| V8 Data Protection | yes | journal and `out/` hold recipient addresses and rendered mail: both are `.gitignore`d (`*.jsonl`, `out/`); secrets only via env vars |

### Known Threat Patterns

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Path traversal through an address used in a file name (`../x@example.org` passes the library's address check) | Tampering | D-16 sanitiser plus `<n>-` prefix; write only below the `--out` directory |
| Secret echoed in a pre-flight message (user puts the secret in `secretEnv`) | Information Disclosure | messages name keys, never values (Pitfall 3) |
| Journal line forged or corrupted by a crash | Tampering / Repudiation | one JSON object per forced line; unparsable lines are never treated as accepted |
| HTML injection through a CSV value | Tampering | the renderer escapes values in HTML; `$$!name$$` raw insertion is template-author-controlled, not recipient-controlled |
| Real credentials or addresses committed in the example | Information Disclosure | example uses `example.com`, `example.org`, zero GUIDs only; no real values anywhere |
| Duplicate send after crash (at-least-once) | Repudiation | owner decision, documented; per-attempt forced journal narrows the window |

## Sources

### Primary (HIGH confidence)
- Repo files read this session: `docs/spec.md` §8, §10.1, §10.2; `.planning/phases/02-serial-mail-sample/02-CONTEXT.md`; `.planning/REQUIREMENTS.md`; `.planning/STATE.md`; `docs/architecture.md`; `pom.xml`; `morimail-sample-serial/pom.xml`; `morimail/pom.xml`; `.gitignore`; `.github/workflows/ci.yml`; `Mail.java`, `Mailer.java`, `MailAccount.java`, `MailAddress.java`, `MailMessage.java`, `Attachment.java`, `MailTemplate.java`, `TemplateRenderer.java`, the six exception classes, `RecordingTransport.java`, `RecordingTransportProvider.java`, `RecordingTestJarSmokeTest.java`.
- Scratch experiments (all executed here): offline `./mvnw -o -B -ntp verify` of a copy of the repo with picocli + manifest + `copy-dependencies` (BUILD SUCCESS, runnable jar, exit 2 on unknown option); picocli 4.7.7 exit-code and output-capture matrix; `FileChannel` append + force round trip; `Files.readString` BOM/strictness; `Properties` backslash and trailing-space behaviour; `javac --release 17` failures for `Thread.sleep(Duration)` and `getFirst()`; PNG/PDF generation and validation; `.gitignore` negation check in a scratch git repo.
- https://repo1.maven.org/maven2/info/picocli/picocli/maven-metadata.xml: picocli latest = release = 4.7.7.

### Secondary (MEDIUM confidence)
- `.claude/CLAUDE.md` stack research (versions, `lib/` directory rationale), consistent with what was executed.

### Tertiary (LOW confidence)
- none beyond the items in the Assumptions Log.

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH, executed offline build and version check.
- Architecture: HIGH for seams and APIs (read and run); MEDIUM for the exact class split (discretion).
- Pitfalls: HIGH, each verified by execution or by reading the source line.

**Research date:** 2026-11-21
**Valid until:** 2026-12-21 (stable domain; re-check only if the library API changes before planning)
