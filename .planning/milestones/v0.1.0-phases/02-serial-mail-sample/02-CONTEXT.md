# Phase 2: Serial-Mail Sample - Context

**Gathered:** 2026-11-21
**Status:** Ready for planning

<domain>
## Phase Boundary

The `serial-mail send` picocli command in `morimail-sample-serial`. It reads `account.properties` and a campaign directory, runs a pre-flight that reports all problems at once, then dry-runs (rendered files to `out/`) or sends one mail per CSV row through a `Mailer`, with an append-only journal (resume, skip on rerun), `--rate` spacing, transient retry, stop on sender/auth errors, counts and exit codes. It also ships the example campaign. Requirements: CLI-01..CLI-13. In this phase every real send runs against `recording`, injected through the CLI-12 seam. No Entra or Graph code is written (CLI-14 and the real account wiring are Phase 3).

</domain>

<decisions>
## Implementation Decisions

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

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Sample specification
- `docs/spec.md` §8 (8.1 Inputs, 8.2 Command, 8.3 Behaviour): the binding sample spec
- `docs/spec.md` §10.1: sample tests with `RecordingTransport` (journal, resume, skip on rerun, stop on auth error, pre-flight report, dry-run output)
- `docs/spec.md` §10.2: milestone order (sample against `recording` before any Graph/Entra code)
- `docs/spec.md` §11 / `CLAUDE.md`: Rule 0 and the further working rules (no secrets, no real addresses, no extra dependencies)

### Requirements and roadmap
- `.planning/REQUIREMENTS.md`: CLI-01..CLI-13 (Phase 2), CLI-14 (Phase 3, not this phase)
- `.planning/ROADMAP.md` Phase 2: success criteria 1-5 and scope notes

### Prior decisions
- `.planning/phases/01-library-core/01-CONTEXT.md`: D-02a (`Map<String, ?>` template data), D-05 (`build()` reports the first problem only), D-11..D-14 (`RecordingTransport` hooks `failFor`, `failNext`, `failValidation`, access via `account.transport()`), D-18 (offline-verify proof), D-20 (release build scoped `-pl morimail -am`)
- `docs/architecture.md`: recorded spec deviations (reactor-only test-jar, unchecked config errors)
- `.claude/CLAUDE.md`: stack (picocli 4.7.7 sample-only, no CSV library, `lib/` directory instead of shade)

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `Mail.using(id)...template(MailTemplate, Map<String, ?>).attach(Path).build()`: a CSV row map can be passed directly. Rendering happens in `build()`, so a missing placeholder surfaces as `InvalidMailException`.
- `MailTemplate` / `TemplateRenderer` (`morimail/src/main/java/de/morisoft/morimail/`): placeholder syntax `$$name$$` / `$$!name$$`. Pre-flight's "placeholder has no CSV column" check needs the template's placeholder names. Check whether the core exposes them before writing a second parser in the sample.
- `Mailer.of(...)`, `Mailer.validate(Mail)` (throws `PermanentMailException`), `Mailer.send(Mail)`.
- `MailAccount.builder(id).transport("recording").defaultFrom(...)` and `account.transport()` for test access.
- `TransientMailException.retryAfter()` returns `Optional<Duration>`.
- Error tree: `TransientMailException`, `PermanentMailException` > `SenderRejectedException` / `AuthRequiredException` / `InvalidMailException`.
- `RecordingTransport` (library test-jar, `de.morisoft.morimail.test`): `failFor(email, ex)`, `failNext(ex...)`, `failValidation(ex)`, `sent()`.

### Established Patterns
- Plain constructors, records and static factories. No DI, no reflection beyond picocli itself.
- The sample consumes the library's test-jar in test scope (already wired in `morimail-sample-serial/pom.xml`). `RecordingTestJarSmokeTest` proves `ServiceLoader` finds `recording`.
- The sample module skips deploy, sources, Javadoc, gpg and publishing (pom properties already set).

### Integration Points
- `morimail-sample-serial/pom.xml`: add picocli (version already managed in the parent), and the dependency-plugin/jar-plugin manifest config for `lib/`.
- The CLI-12 seam: the command class takes a `Mailer` (plus the sleep function, D-12). Its `main` builds the real `Mailer` from `account.properties`. In Phase 2 that factory throws the D-01 message. Phase 3 (CLI-14) fills it with Entra plus Graph.
- `morimail-sample-serial/example-campaign/`: new directory with `subject.txt`, `body.html`, `body.txt`, `recipients.csv`, `attachments/` (PDF + PNG), and an `account.properties` example using `example.com` and zero GUIDs.

</code_context>

<specifics>
## Specific Ideas

- Progress line format as in D-14 (`[n/total] address result`).
- Retry history visible in the journal (one line per attempt) so the owner can audit throttling after a live run.

</specifics>

<deferred>
## Deferred Ideas

None. The discussion stayed within phase scope.

</deferred>

---

*Phase: 02-serial-mail-sample*
*Context gathered: 2026-11-21*
