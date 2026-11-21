# Phase 1: Library Core - Pattern Map

**Mapped:** 2026-11-21
**Files analyzed:** about 45 new files (no modified source files)
**Analogs found:** 0 in-repo / all files

## Greenfield Statement

`git ls-files` shows no Java, POM, workflow or script file. The repository holds only `LICENSE`, `.gitignore`, `.claude/CLAUDE.md` and `.planning/` docs (`spec.md` is untracked). There is no in-repo code analog for any file, and none is invented here. The references are the executed prototypes and recipes in `.planning/phases/01-library-core/01-RESEARCH.md` (RESEARCH) and `.planning/research/STACK.md` (STACK).

Precedence: where RESEARCH still shows the old D-01/D-02 text (three address forms, `Map<String, Object>`), `01-CONTEXT.md` wins. Current D-01 allows only `String` and `MailAddress` forms, plus `MailAddress.of(email)` and `MailAddress.of(email, displayName)`. D-02a requires `Map<String, ?>`. The Pitfall 1 guard against display names containing `@` is no longer needed, because the `(email, displayName)` overload is gone.

Tracked-source gate: no analog paths are named, so no gitignored mirror can appear.

## File Classification

| New File | Role | Data Flow | Reference Source | Match |
|----------|------|-----------|------------------|-------|
| `pom.xml` (parent) | config | build | STACK "Parent POM skeleton", "Release profile" | recipe |
| `morimail/pom.xml` | config | build | STACK "Publishing a test-jar"; RESEARCH Pattern 8 and Pitfall 6 | recipe |
| `morimail-sample-serial/pom.xml` | config | build | STACK test-jar consumption; RESEARCH Pattern 8 (skip properties) | recipe |
| `mvnw`, `mvnw.cmd`, `.mvn/wrapper/maven-wrapper.properties`, `.gitattributes` | config | build | STACK "Maven wrapper" and Development Tools | recipe |
| `.github/workflows/ci.yml` | config | build | RESEARCH "CI workflow" (SHA-pinned) | recipe |
| `README.md`, `SECURITY.md`, root `CLAUDE.md`, `docs/spec.md` | docs | n/a | spec §11 and §13.1, D-15..D-17 | none |
| `MailAddress`, `Attachment`, `MailMessage`, `Mail`(+Builder), `MailTemplate` | model | transform | RESEARCH Patterns 1 and 2; spec §5 | none |
| `MailException` tree (6 classes) | model | n/a | RESEARCH "SPI and error tree", Pitfall 9; spec §5.2 | none |
| `MailTransport`, `TransportProvider`, `TokenProvider` | SPI | request-response | RESEARCH Code Examples; spec lines 132-144 | none |
| `TemplateRenderer` (package-private) | utility | transform | RESEARCH Pattern 3 (executed prototype) | prototype |
| `TransportRegistry`, `BuiltInTransports` (package-private) | service | registry lookup | RESEARCH Pattern 4; ARCHITECTURE.md Pattern 1 | prototype |
| `MailAccount`(+Builder), `Mailer` | service | request-response | RESEARCH Patterns 4 and 5 | prototype |
| `RecordingTransport`, `RecordingTransportProvider` + `META-INF/services/de.morisoft.morimail.TransportProvider` | test fixture | event-driven (scripted) | RESEARCH Pattern 6 | none |
| `ImportRulesTest`, `PublicTypesDocumentThreadSafetyTest` | test | file-I/O scan | RESEARCH Pattern 7 (executed prototype) | prototype |
| Unit tests (renderer, builder, registry, mailer, errors, token default) | test | n/a | RESEARCH Validation Architecture | none |
| `morimail-sample-serial/.../RecordingTestJarSmokeTest` | test | ServiceLoader | RESEARCH Open Question 8; STACK test-jar recipe | recipe |

## Pattern Assignments

Every file follows the same rule: copy the named RESEARCH or STACK section, with the CONTEXT overrides above. Cross-file conventions this phase sets (CONTEXT "Established Patterns"):

- **Model:** records with a compact constructor only where a defensive copy is needed (`List.copyOf` in `MailMessage`). They implement no `Serializable` and hold no behaviour.
- **Classes:** final classes with static factories and builders. `Mailer` and `MailAccount` get a private constructor.
- **Visibility:** `TemplateRenderer`, `TransportRegistry` and `BuiltInTransports` are package-private. Unit tests sit in the same package.
- **Errors:** a checked tree under `MailException`. `TransientMailException` stores `retryAfter` as a nullable `Duration` and exposes `Optional<Duration> retryAfter()`.
- **Core imports:** the core imports only `java.*`/`javax.*`. `BuiltInTransports` is the one exception, and it is an empty list in Phase 1.

### Mail builder (`Mail.Builder`)
**Reference:** RESEARCH Pattern 2 (fixed check order, first problem only). Resolve the content type in `build()` after the existence check (D-07, D-08, Pitfall 10).

Build the address API from CONTEXT D-01 and D-02:
- `to(String...)`, `to(MailAddress...)`, and the same for `cc` and `bcc`, appending on repeated calls.
- `from(String)`, `from(MailAddress)`, `replyTo(String)` and `replyTo(MailAddress)`, where the last call wins.
- `template(MailTemplate, Map<String, ?>)`.
- `attach(Path)`, `attach(Path, String)`, `attach(Attachment)`.

### `TemplateRenderer`
**Reference:** RESEARCH Pattern 3 (lines 272-298). Use one `Pattern` and a manual `find()` loop that appends literals, and never `appendReplacement` or `replaceAll(String)` with a data value. The edge-case table in Pattern 3 becomes the test rows. Change the `data` parameter to `Map<String, ?>`.

### `TransportRegistry`, `MailAccount.Builder`, `Mailer`
**Reference:**
- **`TransportRegistry`:** RESEARCH Pattern 4. Use a per-build registry with no static state. Catch `ServiceConfigurationError` and rethrow it as `IllegalStateException`. Test duplicates via `TransportRegistry.of(...)`.
- **`Mailer`:** Pattern 5, with one shared `prepare` used by both `validate` and `send`. Reject duplicate account ids in `Mailer.of` with `IllegalArgumentException`.
- **`MailAccount`:** a public `transport()` accessor (D-11).
- **Logging:** `System.Logger` with `isLoggable` guards (Pitfall 12).

### `RecordingTransport` / `RecordingTransportProvider`
**Reference:** RESEARCH Pattern 6, lives in `src/test/java/de/morisoft/morimail/test/`. Use `synchronized` state with a thread-safety Javadoc (D-14). Provide `failFor`, `failNext` and `failValidation` (D-12, D-13). `create` returns a new instance per account. Ship it via the test-jar and a services file in `src/test/resources/META-INF/services/`.

### Build files
**Reference:**
- **Parent POM, release profile, wrapper:** STACK recipes. Pin every plugin in `pluginManagement` (Pitfall 15).
- **`Automatic-Module-Name`:** put it on the `default-jar` execution only.
- **Test-jar:** in the library's `release` profile set `attach=false` on the `test-jar` execution.
- **Sample module:** skip properties `maven.deploy.skip`, `maven.source.skip`, `maven.javadoc.skip`, `gpg.skip` and `skipPublishing`.
- **Executable bit:** `git add --chmod=+x mvnw`.
- **Deferred:** the `live` profile and `@Tag("live")` stay in Phase 3.

### Import-rule and thread-safety Javadoc tests
**Reference:** RESEARCH Pattern 7. Strip comments before scanning, assert the core file list is non-empty and contains `Mailer.java`, and scan for fully qualified names as well as imports.

## Shared Patterns

- **Javadoc strictness (every public type and member):** RESEARCH Pitfall 4 table.
  - Add `@param` for every record component, `@return` and `@throws` on every method, and a comment on explicit compact constructors.
  - State thread safety on each public type.
  - Use `{@code}` for generics and for references to test-only types.
  - Run `./mvnw -Prelease clean verify -Dgpg.skip` after each Javadoc-bearing task.
- **Error messages:** each names the offending field, placeholder or id. They never echo the data map, bodies or tokens (spec §11, Pitfall 12).
- **Commits:** run `export GIT_AUTHOR_EMAIL=stephan@morisoft.de GIT_COMMITTER_EMAIL=stephan@morisoft.de` first (Pitfall 7). Commit `8c9b082` is the one known exception (D-02b).
- **Hygiene:** `example.com`/`example.org` only. Scrub the absolute workstation paths in `.planning/research/ARCHITECTURE.md` (about line 551) and `.planning/research/PITFALLS.md` (about line 562) per Pitfall 17.

## No Analog Found

All files above. Plan from the RESEARCH and STACK sections named in the table. The Phase 1 plan should create the reactor and CI first (green from commit one), then add the core in dependency order: errors and records, SPI, renderer, builder, registry/account/mailer, `RecordingTransport`.

## Metadata

**Analog search scope:** `git ls-files` for the whole repo (no source files found).
**Sources read:** `01-CONTEXT.md` (full), `01-RESEARCH.md` (lines 1-587 of 794; Validation Architecture and Open Questions not re-read), `.claude/CLAUDE.md`.
**Pattern extraction date:** 2026-11-21
