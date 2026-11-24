---
phase: quick-261124-hfx
plan: 01
type: execute
wave: 1
depends_on: []
files_modified:
  - morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/AccountFile.java
  - morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/SendCommand.java
  - morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/SerialMail.java
  - morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/SampleFixtures.java
  - morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/AccountFileTest.java
  - morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/ExampleCampaignTest.java
  - morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/SendCommandHelpTest.java
  - morimail-sample-serial/dev-account.properties
  - docs/spec.md
  - docs/architecture.md
  - .env.sample
  - morimail/src/test/java/de/morisoft/morimail/LiveGraphTest.java
  - docs/entra-setup.md
  - README.md
autonomous: true
requirements: [D-01, D-02, D-03, D-04]   # the four owner-approved design items of this quick task: 1 root .env.sample, 2 live-test variable rename, 3 account-file expansion + dev-account.properties + tests + spec/architecture notes, 4 README Development setup

estimate:
  tokens: 60000             # plan 9k + reads 12k (AccountFile 1.5k, SendCommand 95-170 1.5k, SerialMail 1.5k, SampleFixtures 1.5k, ExampleCampaignTest/AccountFileTest heads 3k, LiveGraphTest 1-100 1.5k, README 155-182, spec 8.1, architecture table tail) + edits 10k + verify output 15k (3 targeted runs, 2 offline verifies, 1 release build, scans) + 3 commits 3k + SUMMARY 3k + slack 8k
  raw_tokens: 60000         # factor 1: estimate-calibration has 0 samples
  tasks: 3
  confidence: low

must_haves:
  truths:
    - "D-03: a `${NAME}` reference in any value of an account file is replaced by the variable NAME from the environment function that SerialMail.commandLine hands to SendCommand and SendCommand hands to AccountFile.read; main passes System::getenv, the tests pass an injected function, so no test reads the real environment"
    - "D-03: an unset or blank referenced variable is exactly one pre-flight problem per reference, `<file>: key '<key>' uses ${NAME}, which is not set or is blank`; it names the key and the variable, never a value; AccountFile.read then returns null, so no mail is built with the unexpanded text and no follow-on problem (missing key, credential pair, invalid from) appears"
    - "D-03: the committed, not ignored morimail-sample-serial/dev-account.properties takes tenantId, clientId, from and certificate.path from MORIMAIL_DEV_TENANT_ID, MORIMAIL_DEV_CLIENT_ID, MORIMAIL_DEV_FROM and MORIMAIL_DEV_CERT_PATH, names MORIMAIL_DEV_CERT_PASSWORD in certificate.passwordEnv, and carries secretEnv=MORIMAIL_DEV_SECRET as a commented line; a dry run of the example campaign with it exits 2 with exactly four reference lines plus the summary line when the variables are unset, and exits 0 when they are set"
    - "D-03: an environment value is inserted literally (a `$` or `\\` in it neither throws nor changes), and text that is not a `${NAME}` reference stays as written; docs/spec.md §8.1 has a short note on `${NAME}` and docs/architecture.md has one `| §8.1` decision row"
    - "D-01: the committed, not ignored root .env.sample holds only comment lines and nine MORIMAIL_DEV_* KEY=value lines with example values (example.com/example.org addresses, all-zero GUIDs, /path/... paths, empty secrets), one short comment per variable, and a header that says development only, a test tenant and test app, never production credentials, copy to .env, load with `set -a; . ./.env; set +a`; no Java code reads it"
    - "D-02: LiveGraphTest reads MORIMAIL_DEV_TENANT_ID, CLIENT_ID, FROM, TO, CERT_PATH, CERT_PASSWORD, SECRET (required as today) and OUT_OF_SCOPE_FROM, BIG_ATTACHMENT (still optional); no file outside .planning names the old live-test prefix any more; archived .planning files are untouched"
    - "D-04: README.md has one `## Development setup` section: copy .env.sample to .env, fill in, `set -a; . ./.env; set +a`, the live test with `./mvnw -B -ntp -Plive test -pl morimail`, and the sample with `--account morimail-sample-serial/dev-account.properties` through the documented package-and-java-jar commands"
    - "`./mvnw -B -ntp -o verify` and `./mvnw -B -ntp -Prelease clean verify -Dgpg.skip -pl morimail,morimail-html -am` are green; no new dependency; Claude never runs -Plive and sends no mail; every commit is authored and committed as Stephan Wald <stephan@morisoft.de>"
  artifacts:
    - path: "morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/AccountFile.java"
      provides: "`${NAME}` expansion in every value, reference problems that name the key, null on an unresolved reference"
      contains: "Matcher.quoteReplacement"
    - path: "morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/SendCommand.java"
      provides: "the injected environment passed to the account file"
      contains: "AccountFile.read(accountFile, env, problems)"
    - path: "morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/SerialMail.java"
      provides: "main wires the process environment in one place"
      contains: "commandLine(SerialMail::realMailer, SerialMail::sleep, System::getenv)"
    - path: "morimail-sample-serial/dev-account.properties"
      provides: "the development account built from MORIMAIL_DEV_* variables"
      contains: "tenantId=${MORIMAIL_DEV_TENANT_ID}"
    - path: ".env.sample"
      provides: "the one development file template"
      contains: "MORIMAIL_DEV_TENANT_ID=00000000-0000-0000-0000-000000000000"
    - path: "morimail/src/test/java/de/morisoft/morimail/LiveGraphTest.java"
      provides: "the live test on the MORIMAIL_DEV_* names"
      contains: "env(\"MORIMAIL_DEV_TENANT_ID\")"
    - path: "README.md"
      provides: "the Development setup section"
      contains: "## Development setup"
    - path: "docs/architecture.md"
      provides: "the §8.1 decision row"
      contains: "| §8.1"
  key_links:
    - from: "morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/SerialMail.java"
      to: "morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/SendCommand.java"
      via: "commandLine(mailerFactory, sleep, env) builds new SendCommand(mailerFactory, sleep, env)"
    - from: "morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/SendCommand.java"
      to: "morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/AccountFile.java"
      via: "AccountFile.read(accountFile, env, problems) in pre-flight"
    - from: "morimail-sample-serial/dev-account.properties"
      to: ".env.sample"
      via: "the MORIMAIL_DEV_TENANT_ID, CLIENT_ID, FROM, CERT_PATH, CERT_PASSWORD, SECRET names"
    - from: "morimail/src/test/java/de/morisoft/morimail/LiveGraphTest.java"
      to: ".env.sample"
      via: "the nine MORIMAIL_DEV_* names"
---

<objective>
One development file drives everything a developer runs locally: a root `.env.sample` (copied to the ignored `.env`) lists the `MORIMAIL_DEV_*` variables, the live test reads them, and the serial-mail sample reads them through a committed `dev-account.properties` whose values use `${NAME}` references expanded from the environment.

Purpose: the owner's goal is that a developer curates ONE file on the development machine (development credentials only, never production) from which the live test and the sample both run.
Output: `${NAME}` expansion in `AccountFile` with the environment injected through `SerialMail.commandLine` and `SendCommand` (D-03), `morimail-sample-serial/dev-account.properties` (D-03), tests (D-03), notes in `docs/spec.md` §8.1 and `docs/architecture.md` (D-03), root `.env.sample` (D-01), the live-test variable rename (D-02), and a README "Development setup" section (D-04). Three commits, `./mvnw -B -ntp -o verify` green after each.
</objective>

<execution_context>
@~/.claude-shared/dot-claude/gsd-core/workflows/execute-plan.md
@~/.claude-shared/dot-claude/gsd-core/templates/summary.md
</execution_context>

<context>
@CLAUDE.md
@.claude/CLAUDE.md
@.planning/STATE.md
@morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/AccountFile.java
@morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/SerialMail.java
@morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/SampleFixtures.java

Read only these regions of the larger files:
- `morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/SendCommand.java` lines 95-110 (fields and constructor) and 150-246 (pre-flight; note the mail loop runs whenever the account is non-null with an id and a from, and `Mail.build` checks the from address).
- `morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/AccountFileTest.java` lines 1-60 and 150-227; `ExampleCampaignTest.java` lines 1-60 and 125-145; `SendCommandHelpTest.java` (23 lines).
- `morimail/src/test/java/de/morisoft/morimail/LiveGraphTest.java` lines 1-100.
- `README.md` lines 155-182; `docs/spec.md` lines 278-302 (§8.1); `docs/architecture.md` lines 18-43 (the deviation table); `docs/entra-setup.md` line 229.

Interfaces after Task 1 (planning-time rehearsal on an archive copy of db325f1 compiled, and `./mvnw -B -ntp -o verify` ran green with 374, 71 and 168 tests):
- `static AccountFile read(Path file, Function<String, String> env, List<String> problems)` in `AccountFile`.
- `static CommandLine commandLine(Function<AccountFile, Mailer> mailerFactory, Consumer<Duration> sleep, Function<String, String> env)` in `SerialMail`.
- `SendCommand(Function<AccountFile, Mailer> mailerFactory, Consumer<Duration> sleep, Function<String, String> env)`.
- `static Run run(Function<String, String> env, Function<AccountFile, Mailer> factory, List<Duration> waits, String... args)` in `SampleFixtures`; the existing three-argument `run` (about 65 callers, unchanged) delegates with `name -> null`.
</context>

<tasks>

<task type="tracer">
  <name>Task 1: End-to-end `${NAME}` from the injected environment through the CLI into the dev account file</name>
  <files>morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/AccountFile.java, morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/SendCommand.java, morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/SerialMail.java, morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/SampleFixtures.java, morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/AccountFileTest.java, morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/ExampleCampaignTest.java, morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/SendCommandHelpTest.java, morimail-sample-serial/dev-account.properties</files>
  <behavior>
    - ExampleCampaignTest.devAccountFileReadsTheDevelopmentVariables: `AccountFile.read(Path.of("dev-account.properties"), env::get, problems)` with a map of MORIMAIL_DEV_TENANT_ID and MORIMAIL_DEV_CLIENT_ID = the zero GUID, MORIMAIL_DEV_FROM = sender@example.com, MORIMAIL_DEV_CERT_PATH = /path/to/test-app.p12 gives no problems, id entra-dev, those four values, certificatePasswordEnv MORIMAIL_DEV_CERT_PASSWORD and secretEnv null.
    - ExampleCampaignTest.devAccountDryRunNamesEveryUnsetVariable: the three-argument `SampleFixtures.run` (empty environment) with `send --dry-run --account dev-account.properties --campaign example-campaign --out <tempdir>/out` and a factory that throws AssertionError exits 2 and errLines are exactly, in this order: `dev-account.properties: key 'tenantId' uses ${MORIMAIL_DEV_TENANT_ID}, which is not set or is blank`, the same for clientId / MORIMAIL_DEV_CLIENT_ID, from / MORIMAIL_DEV_FROM, certificate.path / MORIMAIL_DEV_CERT_PATH, then `Pre-flight failed with 4 problem(s); nothing was sent.` (rehearsed: these exact five lines).
    - ExampleCampaignTest.devAccountDryRunUsesTheDevelopmentVariables: the same dry run through the new env overload with the map above exits 0 (use `.as(run.err())`) and the factory is never called. Do not assert the sender on stdout: dry-run stdout never prints it (rehearsal finding).
  </behavior>
  <action>
Implements D-03 (the code path and the committed file). Rule 0: no new class, no parser, no default/nesting/escape syntax.

AccountFile: add a private static final Pattern for a reference, the regular expression `\$\{([A-Za-z_][A-Za-z0-9_]*)\}` (POSIX variable names). Change `read` to `read(Path file, Function<String, String> env, List<String> problems)` and pass `env`, the file display name and `problems` into `value`. In `value`, after `getProperty` returns non-null, replace every reference with `Matcher.replaceAll(Function<MatchResult, String>)`: look the group-1 name up through `env`; when the result is non-null and not blank, return `Matcher.quoteReplacement(result)` (without the quoting a `$` or `\` in the value throws or is garbled); otherwise add the problem `name + ": key '" + key + "' uses " + match.group() + ", which is not set or is blank"` and return `Matcher.quoteReplacement(match.group())`, so the reference text stays in place. Then trim and map empty to null as today. In `read`, take `int before = problems.size()` just before constructing the record and `boolean unresolved = problems.size() > before` right after it, run the existing require and credential checks unchanged, and return `unresolved ? null : account`. Why both: the kept text stops a second "missing key" or "must be set together" line for the same key, and the null stops SendCommand from building every mail with the unexpanded `from` (Mail.build would add an invalid-from line for all rows). Update the record Javadoc (a value may hold `{@code ${NAME}}` references replaced from the environment; the braces are balanced, so doclint accepts them) and the `read` Javadoc (returns null when the file cannot be read at all or a reference cannot be expanded; a message names the file, the key and the variable, never a value). Keep the thread-safety sentence.

SendCommand: add a final `Function<String, String> env` field as the third constructor parameter and call `AccountFile.read(accountFile, env, problems)`. Do not touch the picocli help text or option descriptions: picocli interpolates `${...}` in descriptions, and SendCommandHelpTest pins the usage text.

SerialMail: `commandLine` takes `Function<String, String> env` as its third parameter and passes it to `new SendCommand(...)`; `main` calls `commandLine(SerialMail::realMailer, SerialMail::sleep, System::getenv)`. Update the `commandLine` Javadoc to say the tests also pass an injected environment. `realMailer` stays as it is.

Call sites forced by the signature change (mechanical, no behavior change): SampleFixtures gets the four-argument `run(env, factory, waits, args)` holding today's body with `SerialMail.commandLine(factory, waits::add, env)`, and the three-argument `run` delegates with `name -> null`; AccountFileTest's `read(String)` helper and its three direct `AccountFile.read(file, problems)` calls, ExampleCampaignTest's `AccountFile.read(ACCOUNT, problems)`, and SendCommandHelpTest's `commandLine` call all pass `name -> null`. These four files exceed the five-file guideline only because one commit must compile.

Create morimail-sample-serial/dev-account.properties: comment lines saying it is the development account for the serial-mail sample, for a test tenant and test app only, never production credentials; every `${NAME}` is read from the environment and the `MORIMAIL_DEV_*` variables are listed in the root `.env.sample`, loaded with `set -a; . ./.env; set +a`; the keys ending in Env name environment variables, so no secret is written here. Then `account.id=entra-dev`, `tenantId=${MORIMAIL_DEV_TENANT_ID}`, `clientId=${MORIMAIL_DEV_CLIENT_ID}`, `from=${MORIMAIL_DEV_FROM}`, `certificate.path=${MORIMAIL_DEV_CERT_PATH}`, `certificate.passwordEnv=MORIMAIL_DEV_CERT_PASSWORD`, then `# or, in place of the two certificate lines:` and `# secretEnv=MORIMAIL_DEV_SECRET` (AccountFile rejects both credentials together). It is not ignored: .gitignore ignores the basename account.properties only.

Add the three ExampleCampaignTest tests from the behavior block (Surefire runs in the module directory, so the paths are `Path.of("dev-account.properties")` and the existing CAMPAIGN). Run the verify below, then `./mvnw -B -ntp -o verify` (green), then commit the eight files. Commit identity (spec 13.4): the workspace exports GIT_AUTHOR_* and GIT_COMMITTER_* variables that override the local config, and shell state does not persist between tool calls, so in the same Bash call as `git commit` first run `export GIT_AUTHOR_NAME="Stephan Wald" GIT_AUTHOR_EMAIL="stephan@morisoft.de" GIT_COMMITTER_NAME="Stephan Wald" GIT_COMMITTER_EMAIL="stephan@morisoft.de"`. Subject: `feat(quick-261124-hfx): read sample account values from environment references`; keep `$` out of commit messages (a double-quoted `${NAME}` is expanded by the shell). End the message with the Co-Authored-By attribution line supplied to the executing run. Never push, tag, deploy, run -Plive or send mail.
  </action>
  <verify>
    <automated>R=morimail-sample-serial/target/surefire-reports/TEST-de.morisoft.morimail.sample.ExampleCampaignTest.xml; rm -f "$R"; MORIMAIL_DEV_TENANT_ID=00000000-0000-0000-0000-000000000000 MORIMAIL_DEV_CLIENT_ID=00000000-0000-0000-0000-000000000000 MORIMAIL_DEV_FROM=sender@example.com MORIMAIL_DEV_CERT_PATH=/path/to/test-app.p12 ./mvnw -B -ntp -q -o -pl morimail-sample-serial -am test -Dtest='ExampleCampaignTest,AccountFileTest,SendCommandHelpTest' -Dsurefire.failIfNoSpecifiedTests=false || { echo "sample tests failed with the development variables in the shell"; exit 1; }; for t in devAccountFileReadsTheDevelopmentVariables devAccountDryRunNamesEveryUnsetVariable devAccountDryRunUsesTheDevelopmentVariables; do grep -qF "testcase name=\"$t\"" "$R" || { echo "the report shows no run of $t"; exit 1; }; done; test -f morimail-sample-serial/dev-account.properties || { echo "dev-account.properties is missing"; exit 1; }; git check-ignore -q morimail-sample-serial/dev-account.properties && { echo "dev-account.properties is ignored"; exit 1; }; echo "tracer: ok" # [HEAD=1 EXPECT==0 @db325f1] (exit status); at HEAD it prints "the report shows no run of devAccountFileReadsTheDevelopmentVariables". The variables set in the shell are the proof of injection: if SendCommand read the process environment, devAccountDryRunNamesEveryUnsetVariable would exit 0 and fail. Rehearsed on an archive copy with the planned code: exit 0, both dry-run tests ran</automated>
  </verify>
  <done>With the environment injected, dev-account.properties dry-runs the example campaign to exit 0. With an empty environment it stops in pre-flight with exactly the four key-naming lines and the summary line, even when the real shell has the MORIMAIL_DEV_* variables set. `./mvnw -B -ntp -o verify` is green and the commit is authored and committed as Stephan Wald.</done>
</task>

<task type="auto">
  <name>Task 2: Expansion edge cases (set, unset, blank, literal, no leak) and the spec/architecture notes</name>
  <files>morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/AccountFileTest.java, docs/spec.md, docs/architecture.md</files>
  <behavior>
    - referenceIsReplacedByTheEnvironmentValue: `tenantId=${MORIMAIL_TEST_TENANT_ID}` and `certificate.path=${MORIMAIL_TEST_DIR}/test.p12` with the map {MORIMAIL_TEST_TENANT_ID: zero GUID, MORIMAIL_TEST_DIR: /path/certs} gives no problems, tenantId the zero GUID, certificatePath `/path/certs/test.p12` (an embedded reference), certificatePasswordEnv unchanged.
    - unsetReferenceIsAProblemThatNamesTheKey: `tenantId=${MORIMAIL_TEST_UNSET_VARIABLE}` with an empty environment returns null and problems are exactly `account.properties: key 'tenantId' uses ${MORIMAIL_TEST_UNSET_VARIABLE}, which is not set or is blank` (no missing-key line).
    - blankReferenceIsAProblemThatNamesTheKey: `from=${MORIMAIL_TEST_BLANK}` with the value of three spaces returns null and problems are exactly the same shape for key 'from'.
    - environmentValueIsInsertedLiterally: MORIMAIL_TEST_DIR = `C:\certs$1` gives certificatePath `C:\certs$1/test.p12` with no exception; `account.id=$main-${1X}-${not a reference}` stays exactly as written.
    - referenceProblemsNeverEchoAValue: SENTINEL behind one set reference (tenantId), one unset reference (clientId) and one blank reference (from): the problems name the keys and contain no SENTINEL; a dry run of a written campaign through `SampleFixtures.run(env::get, ...)` with the same map exits 2 and neither err nor out contains SENTINEL.
  </behavior>
  <action>
Implements D-03 (tests and docs). Add the five AccountFileTest tests from the behavior block. Add a second private helper that takes the content and an environment map and calls `AccountFile.read(file, env::get, problems)`, next to the existing `read(String)` helper from Task 1. Reuse the class's SENTINEL, ZERO_GUID and REQUIRED constants. Write fixtures with example.com/example.org addresses and the zero GUID only, and add no new production code. If a test exposes a defect in Task 1's AccountFile code, fix AccountFile in this commit and say so in the SUMMARY.

docs/spec.md §8.1: right after the paragraph that starts "Secrets and passwords are read from the named environment variables only.", add one short paragraph. A value in account.properties may contain `${NAME}` references, NAME being a letter or `_` followed by letters, digits and `_`. Each is replaced by that environment variable's value. An unset or blank variable is a pre-flight problem that names the key and the variable, never a value. There is no default, nesting or escape syntax, and other text stays as written. `morimail-sample-serial/dev-account.properties` uses this for the `MORIMAIL_DEV_*` variables of the root `.env.sample`. Change no other spec line.

docs/architecture.md: append one row at the end of the "Deviations from the spec" table, after the §10.4 and §12 row, in the existing three-column style. Spec column: `§8.1 (`${NAME}` in account.properties)`. morimail column: every value of the sample's account file may hold `${NAME}` references; each is replaced from the environment function that SerialMail.commandLine passes to SendCommand (System::getenv in main, a map in the tests), the value taken literally; an unset or blank variable is one pre-flight problem `<file>: key '<key>' uses ${NAME}, which is not set or is blank` naming no value, and AccountFile.read then returns null so no mail is built with the unexpanded text and no follow-on problem appears; other text, a lone `$` included, stays as written. Reason column: one development file, `.env` from `.env.sample`, drives the live test and the sample (owner decision of 2026-11-24); Rule 0, one regular expression and no parser.

Run the verify, then `./mvnw -B -ntp -o verify` (green), then commit the three files with the same identity export as Task 1. Subject: `test(quick-261124-hfx): cover account file references and document them`, with no `$` in the message, ending with the supplied Co-Authored-By line.
  </action>
  <verify>
    <automated>R=morimail-sample-serial/target/surefire-reports/TEST-de.morisoft.morimail.sample.AccountFileTest.xml; rm -f "$R"; ./mvnw -B -ntp -q -o -pl morimail-sample-serial -am test -Dtest='AccountFileTest' -Dsurefire.failIfNoSpecifiedTests=false || { echo "AccountFileTest failed"; exit 1; }; for t in referenceIsReplacedByTheEnvironmentValue unsetReferenceIsAProblemThatNamesTheKey blankReferenceIsAProblemThatNamesTheKey environmentValueIsInsertedLiterally referenceProblemsNeverEchoAValue; do grep -qF "testcase name=\"$t\"" "$R" || { echo "the report shows no run of $t"; exit 1; }; done; test "$(sed -n '/^### 8.1 /,/^### 8.2 /p' docs/spec.md | grep -cF '${NAME}')" -ge 1 || { echo "spec 8.1 has no NAME reference note"; exit 1; }; test "$(grep -n '^| §8.1' docs/architecture.md | wc -l)" -eq 1 || { echo "architecture.md does not have exactly one 8.1 row"; exit 1; }; echo "expansion: ok" # [HEAD=1 EXPECT==0 @db325f1] (exit status); at HEAD it prints "the report shows no run of referenceIsReplacedByTheEnvironmentValue"; spec 8.1 reference-note lines [HEAD=0 EXPECT>=1]; architecture rows starting "| §8.1" [HEAD=0 EXPECT==1] (one table row is one line, so a line count is the row count)</automated>
  </verify>
  <done>The five edge-case tests run green: set, embedded, unset, blank, literal `$` and `\`, untouched non-references, and no SENTINEL in any problem, err or out. §8.1 of the spec carries the note, architecture.md has one §8.1 row, `./mvnw -B -ntp -o verify` is green, and the commit has the Stephan Wald identity.</done>
</task>

<!-- planner-discipline-allow: MORIMAIL_LIVE -->

<task type="auto">
  <name>Task 3: Root .env.sample, live-test rename and the README Development setup</name>
  <files>.env.sample, morimail/src/test/java/de/morisoft/morimail/LiveGraphTest.java, docs/entra-setup.md, README.md</files>
  <action>
Implements D-01, D-02 and D-04.

D-01, .env.sample at the repo root (`.env` is already ignored; `.env.sample` is not, confirmed with git check-ignore at planning time). Header comment lines must say four things. First, `Development only`: the values belong to a test tenant and a test app registration, never production credentials. Second, copy this file to `.env` (git ignores `.env`), fill it in and keep it private (`chmod 600 .env`). Third, load it with `set -a; . ./.env; set +a`. Fourth, no Java code reads this file: the live test and the serial-mail sample read the environment variables, the sample through `morimail-sample-serial/dev-account.properties`. Add one more line: quote a value in single quotes when it holds spaces or shell characters such as `$`. Then one short comment line before each assignment, nine assignments in the LiveGraphTest order:
- `MORIMAIL_DEV_TENANT_ID=00000000-0000-0000-0000-000000000000` (tenant ID of the test tenant)
- `MORIMAIL_DEV_CLIENT_ID=00000000-0000-0000-0000-000000000000` (application ID of the test app)
- `MORIMAIL_DEV_FROM=sender@example.com` (the mailbox the test app may send from)
- `MORIMAIL_DEV_TO=recipient@example.org` (the recipient of the live test mails)
- `MORIMAIL_DEV_CERT_PATH=/path/to/test-app.p12` (the PKCS12 file of the test app)
- `MORIMAIL_DEV_CERT_PASSWORD=` (its password)
- `MORIMAIL_DEV_SECRET=` (or a client secret in place of the certificate; the live test uses the certificate when path and password are both set; dev-account.properties uses the certificate unless its commented secretEnv line is swapped in)
- `MORIMAIL_DEV_OUT_OF_SCOPE_FROM=` (optional: a mailbox the test app must not send from, for the live test's out-of-scope probe)
- `MORIMAIL_DEV_BIG_ATTACHMENT=` (optional: a file of about 2.9 MB, for the big-attachment probe)
Only comment lines, blank lines and those nine assignments: no export keyword, no real value.

D-02, LiveGraphTest: replace the prefix `MORIMAIL_LIVE_` with `MORIMAIL_DEV_` in all nine variable names, in the Javadoc list, the `env(...)` calls, the missing-variable list and the two `Assumptions` messages. Required and optional stay as they are. Add one Javadoc sentence: a developer sets the variables from a copy of the root `{@code .env.sample}` (README, Development setup). In docs/entra-setup.md line 229, name the `MORIMAIL_DEV_*` variables that `LiveGraphTest` describes and the root `.env.sample` lists. Keep the rest of the line. Write no "renamed from" or "formerly" note anywhere: no file outside .planning may name the old prefix. Leave every .planning file alone, archived history included.

D-04, README.md: insert a `## Development setup` section between the "Serial-mail sample" section and "## What morimail is not". Prose: one file on the development machine holds every value the live test and the sample need; use a test tenant and a test app registration, never production credentials. Then a bash block with `cp .env.sample .env`, a comment line to fill in .env, and `set -a; . ./.env; set +a`. Then the live test, noting that it sends real mail to MORIMAIL_DEV_TO, with the command documented today, `./mvnw -B -ntp -Plive test -pl morimail`. Then the sample, with the documented package command `./mvnw -B -ntp -DskipTests package -pl morimail-sample-serial -am` and `java -jar morimail-sample-serial/target/morimail-sample-serial-*.jar send --account morimail-sample-serial/dev-account.properties --campaign morimail-sample-serial/example-campaign --dry-run --out out`. After that, one or two sentences. The dry run stops with exit code 2 and names every key whose variable is unset. A real send drops `--dry-run` and points `--campaign` at a copy with your own test recipients, as in docs/entra-setup.md. To use a client secret, swap the certificate lines of dev-account.properties for its commented secretEnv line. Keep it short: no other README change.

Run the verify below. Then run `./mvnw -B -ntp -Prelease clean verify -Dgpg.skip -pl morimail,morimail-html -am` and `./mvnw -B -ntp -o verify`; both must be green. Then `git add` the four files and run the public-repo scan from the verification section. Commit with the same identity export as Task 1. Subject: `chore(quick-261124-hfx): one development env file for the live test and the sample`, ending with the supplied Co-Authored-By line. Do not run -Plive, do not create a `.env`, and send no mail.
  </action>
  <verify>
    <automated>F=.env.sample; test -f "$F" || { echo "no .env.sample"; exit 1; }; git check-ignore -q "$F" && { echo ".env.sample is ignored"; exit 1; }; git check-ignore -q .env || { echo ".env is not ignored"; exit 1; }; sh -n "$F" || { echo ".env.sample is not valid sh"; exit 1; }; V=$(env -i PATH="$PATH" sh -c 'set -a; . ./.env.sample; set +a; env' | grep '^MORIMAIL_DEV_' | cut -d= -f1 | sort -u | wc -l); test "$V" -eq 9 || { echo "expected 9 MORIMAIL_DEV_ variables, got $V"; exit 1; }; test "$(grep -vE '^(#.*)?$' "$F" | grep -vcE '^MORIMAIL_DEV_[A-Z_]+=')" -eq 0 || { echo "a line that is neither a comment nor a MORIMAIL_DEV_ assignment"; exit 1; }; grep -qF 'set -a; . ./.env; set +a' "$F" && grep -qi 'never production credentials' "$F" || { echo "the header lacks the load line or the production warning"; exit 1; }; ! git grep -q --untracked MORIMAIL_LIVE -- ':!.planning' || { echo "a file outside .planning still names the old live-test variables"; exit 1; }; test "$(grep -oE 'MORIMAIL_DEV_[A-Z_]+' morimail/src/test/java/de/morisoft/morimail/LiveGraphTest.java | sort -u | wc -l)" -eq 9 || { echo "LiveGraphTest does not name the nine MORIMAIL_DEV_ variables"; exit 1; }; grep -qF 'MORIMAIL_DEV_*' docs/entra-setup.md || { echo "entra-setup.md does not name MORIMAIL_DEV_*"; exit 1; }; test "$(grep -n '^## Development setup$' README.md | wc -l)" -eq 1 || { echo "README has no single Development setup section"; exit 1; }; for k in 'cp .env.sample .env' 'set -a; . ./.env; set +a' './mvnw -B -ntp -Plive test -pl morimail' '--account morimail-sample-serial/dev-account.properties'; do grep -qF -- "$k" README.md || { echo "README lacks: $k"; exit 1; }; done; echo "dev env: ok" # [HEAD=1 EXPECT==0 @db325f1] (exit status); at HEAD it prints "no .env.sample"; old-prefix hits outside .planning [HEAD=23 EXPECT==0]; LiveGraphTest MORIMAIL_DEV_ names [HEAD=0 EXPECT==9]; README Development setup [HEAD=0 EXPECT==1]; the sourcing and count method was rehearsed (empty assignments are exported and counted)</automated>
    <human-check>Owner only, at a convenient time and not by Claude: copy .env.sample to .env, fill in test-tenant values, run `set -a; . ./.env; set +a`, then run `./mvnw -B -ntp -Plive test -pl morimail` and the README dry run with dev-account.properties.</human-check>
  </verify>
  <done>.env.sample is committed and not ignored, and holds nine example-valued MORIMAIL_DEV_* lines under the development-only header. LiveGraphTest and entra-setup.md use the new names, and no file outside .planning names the old prefix. The README has the Development setup section with the exact live-test and sample commands. The release build and the offline verify are green, the scan is clean, and the commit has the Stephan Wald identity.</done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| developer environment -> sample pre-flight | environment values (tenant, client, sender, certificate path) enter AccountFile through `${NAME}` and can reach problem messages |
| working tree -> public git history | .env.sample, dev-account.properties, docs and test fixtures are published permanently |
| developer .env -> shell | `. ./.env` runs the file as shell code in the developer's own shell |

## STRIDE Threat Register

| Threat ID | Category | Component | Severity | Disposition | Mitigation Plan |
|-----------|----------|-----------|----------|-------------|-----------------|
| T-hfx-01 | Information disclosure | AccountFile reference problems | medium | mitigate | A problem names the file, the key and the variable only, built from the key and match.group(), never from the env value. referenceProblemsNeverEchoAValue checks problems, err and out against a SENTINEL behind set, unset and blank references (Task 2) |
| T-hfx-02 | Information disclosure | .env.sample, dev-account.properties, fixtures in the public repo | high | mitigate | Example values only: example.com/example.org addresses, zero GUIDs, /path/... paths, empty secrets. Task 3's gate allows only comments and MORIMAIL_DEV_ assignments in .env.sample. The final scan rejects non-example addresses, non-zero GUIDs and /home/ paths in every added line since db325f1 |
| T-hfx-03 | Information disclosure | a developer's filled .env | high | mitigate | .env is already in .gitignore, and Task 3's gate asserts git check-ignore on .env and its absence on .env.sample. The header says to keep .env private (chmod 600) and to use development credentials only. Claude never creates a .env |
| T-hfx-04 | Spoofing (a test or run sends real mail) | new sample tests, LiveGraphTest | high | mitigate | Every new sample test is a dry run or a direct AccountFile.read, and its mailer factory throws AssertionError, so no network is used. LiveGraphTest stays excluded by its tag, and the executor never runs -Plive and sends no mail |
| T-hfx-05 | Tampering | expanded values with `$` or `\` | low | mitigate | Matcher.quoteReplacement on every inserted value, so the value lands literally and no IllegalArgumentException is thrown. environmentValueIsInsertedLiterally proves it (Task 2) |
| T-hfx-06 | Elevation of privilege | sourcing .env as shell code | low | accept | The file is the developer's own, on their own machine. `set -a; . ./.env; set +a` is the owner-chosen load line, and no Java code parses .env (D-01) |
| T-hfx-SC | Tampering | Maven dependencies and plugins | low | accept | No package, plugin or version is added or changed. The offline verify uses the cached repository |
</threat_model>

<verification>
After the third commit, all of these pass:
- `./mvnw -B -ntp -o verify` (green; the rehearsal of Task 1 gave 374, 71 and 168 tests; Task 2 adds 5 sample tests).
- `./mvnw -B -ntp -Prelease clean verify -Dgpg.skip -pl morimail,morimail-html -am` (green, doclint-clean).
- Public-repo scan over every added line since the planning base:

<automated>X=$(git diff db325f1c034b9ce71b89d034480a31bcd3ebeaa8 -- . ':!.planning') || exit 1; P=$(printf '%s\n' "$X" | grep '^+'); A=$(printf '%s\n' "$P" | grep -oE '[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]+' | grep -vE '@example\.(org|com)$'); test -z "$A" || { echo "address outside example.org and example.com: $A"; exit 1; }; G=$(printf '%s\n' "$P" | grep -oiE '[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}' | grep -v '^00000000-0000-0000-0000-000000000000$'); test -z "$G" || { echo "a GUID that is not all zeros: $G"; exit 1; }; if printf '%s\n' "$P" | grep -q '/home/'; then echo "an absolute home path"; exit 1; fi; T=$(git ls-files .env.sample morimail-sample-serial/dev-account.properties) || exit 1; test -n "$(printf '%s\n' "$T" | sed -n 2p)" || { echo "a new file is not tracked"; exit 1; }; echo "public repo scan: ok" # [HEAD=1 EXPECT==0 @db325f1] (exit status); at HEAD the address, GUID and path checks pass (empty diff, GUARD MUTATION: one added line with an address at a non-example domain) and it prints "a new file is not tracked"</automated>

- `git log -3 --format='%an <%ae> / %cn <%ce>'` shows Stephan Wald <stephan@morisoft.de> for author and committer on all three commits; nothing is pushed.
</verification>

<success_criteria>
- A developer fills one file (.env from .env.sample) and both the live test and `serial-mail send --account morimail-sample-serial/dev-account.properties` take their development values from it.
- An unset or blank `${NAME}` stops pre-flight with exit 2 and one line per reference that names the key, never a value.
- No file outside .planning names the old live-test variables; no secret, real address, tenant ID or internal host was added.
- Offline verify and the scoped release build are green; three commits, each with the Stephan Wald identity.
</success_criteria>

<output>
Create `.planning/quick/261124-hfx-development-env-for-the-samples-and-the-/261124-hfx-SUMMARY.md` when done
</output>
