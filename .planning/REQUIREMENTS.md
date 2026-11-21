# Requirements: morimail

**Defined:** 2026-11-21
**Core Value:** A consumer can send one templated mail with attachments through Microsoft Graph as an Entra app, using only the JDK and Jackson, and get back a clear error category (transient / permanent / auth-required) that tells them what to do next.

Binding detail lives in `spec.md` (later `docs/spec.md`). Section references are given as `§n`. Owner decisions taken at initialization are marked **[decided]**.

## v1 Requirements

### Repository and Build (REPO)

- [x] **REPO-01**: Maven reactor: parent `de.morisoft:morimail-parent`, library `morimail`, sample `morimail-sample-serial`, version `0.1.0-SNAPSHOT`. Java 17 via `maven.compiler.release=17`. All plugin and dependency versions are pinned and verified on Maven Central at implementation time (§3, §13.2).
- [x] **REPO-02**: The Maven wrapper is checked in, script-only type, pinned to Maven 3.x (never Maven 4) (§3).
- [x] **REPO-03**: The parent POM carries the Central metadata: name, description, URL `https://github.com/morisoft-lib/morimail`, MIT license, developer Stephan Wald <stephan@morisoft.de>, SCM (§13.3).
- [x] **REPO-04**: The library's only runtime dependency is `jackson-databind` 2.23.x, managed through `jackson-bom` **[decided]**. Test stack: JUnit Jupiter 6.x via `junit-bom`, a deliberate deviation from the spec's "JUnit 5" **[decided]** (owner 2026-11-21), AssertJ, `org.wiremock:wiremock` 3.x. picocli is in the sample only (§3).
- [x] **REPO-05**: The library jar manifest has `Automatic-Module-Name: de.morisoft.morimail`. There is no `module-info.java`.
- [x] **REPO-06**: The repository holds `LICENSE` (MIT), a `README.md` stub (pre-1.0 status, coordinates), `SECURITY.md` (private reporting channel), `CLAUDE.md` with the §11 rules, and `docs/spec.md`, moved from the root (§4, §13.1).
- [x] **REPO-07**: The GitHub Actions CI runs `mvn verify` and `mvn -Prelease verify -Dgpg.skip -pl morimail -am` (release build scoped to the published modules, D-20 in `phases/01-library-core/01-CONTEXT.md`) on push and pull request, on Java 17. It uses no secrets and `permissions: contents: read` (§13.1).
- [ ] **REPO-08**: `mvn verify` passes with no network access. A JUnit test tagged `live` is excluded by default and enabled with `-Plive`; the owner runs it, configured through environment variables (§10.1).
- [x] **REPO-09**: `release` profile with the source, Javadoc (doclint, fail on warnings), GPG and `central-publishing-maven-plugin` plugins (`autoPublish=false`). The test-jar is built for reactor use only, with `attach=false` under `release` **[decided]**. The sample sets `skipPublishing`, `maven.deploy.skip` and the source/javadoc/gpg skips, so only `morimail-parent` and `morimail` are staged (§13.3).
- [x] **REPO-10**: Public types have Javadoc that states thread safety and builds cleanly under doclint from the first phase (§11, §13.3).
- [x] **REPO-11**: Public-repo hygiene. No secrets, tenant IDs, real addresses or hosts appear in any file or commit; examples use `example.com`/`example.org` and zero GUIDs. Every commit has author and committer Stephan Wald <stephan@morisoft.de>, overriding the workspace's `GIT_*` environment variables (§11, §13.4).
- [x] **REPO-12**: A plain JUnit test enforces the import rules. The core imports only the JDK, except the built-in list; `graph` and `entra` don't import each other (§4).

### Mail Model and Builder (MODEL)

- [x] **MODEL-01**: The records `MailAddress`, `Attachment(filename, contentType, file, contentId)`, `MailMessage` and `Mail(accountId, message)` are immutable, with lists copied defensively, and have no serialization code (§5).
- [x] **MODEL-02**: A developer can build a mail fluently: `Mail.using(accountId).to(..).cc(..).bcc(..).replyTo(..).from(..).subject/html/text(..).template(template, data).attach(path).build()` (§5.1).
- [x] **MODEL-03**: `build()` rejects a mail with no recipient, no subject, no html or text, a missing or unreadable attachment file, or a non-null `contentId`. Each failure is an `InvalidMailException` (§5.1).
- [x] **MODEL-04**: `build()` rejects CR/LF in addresses, display names and subject, and requires each address to have exactly one `@` (G5).
- [x] **MODEL-05**: An attachment's content type defaults to `application/octet-stream` when it cannot be determined.

### Templates (TMPL)

- [x] **TMPL-01**: `MailTemplate` holds subject, html and an optional text. `TemplateRenderer` (one final class) replaces `$$name$$` placeholders, where the name matches `[A-Za-z0-9_.-]+`, from `Map<String,Object>` data via `String.valueOf`. Other text, including a lone `$$`, is left unchanged (§5.3).
- [x] **TMPL-02**: Values in HTML are escaped (`& < > " '`), and `$$!name$$` inserts a value raw. Values containing `$` or `\` are inserted literally, because the renderer uses a manual find loop rather than `appendReplacement` (§5.3).
- [x] **TMPL-03**: Subject and text values are inserted as they are, and line breaks in a subject value become a space (§5.3).
- [x] **TMPL-04**: A placeholder with no data field raises `InvalidMailException` naming the template part and the placeholder (§5.3).

### Accounts, Registry and Mailer (CORE)

- [ ] **CORE-01**: The SPI is `MailTransport` (`validate`, `send`), `TransportProvider` (`id`, `create(options, auth)`, auth may be null) and `@FunctionalInterface TokenProvider`. None of them contains anything Graph-specific (§2, §5).
- [x] **CORE-02**: `TokenProvider` has a default method `freshAccessToken(scope, rejectedToken)` that delegates to `accessToken`, so a transport can get a new token after a 401 without importing `entra`. This is recorded as a deviation from §7.3 **[decided]**.
- [ ] **CORE-03**: The registry combines an explicit built-in list with `ServiceLoader`. An external provider whose ID duplicates another provider's fails with `IllegalStateException` when the registry loads (§2).
- [ ] **CORE-04**: `MailAccount.builder(id).transport(..).auth(..).option(..).defaultFrom(..).build()` resolves the transport through the registry. An unknown ID fails with `IllegalArgumentException` listing the available IDs. Options are passed through unread by the core (§2, §5.1).
- [ ] **CORE-05**: `Mailer.of(accounts...)` holds accounts by ID. `validate(mail)` applies `defaultFrom` and runs the transport's `validate`; `send(mail)` does the same, then sends. An unknown account ID raises `PermanentMailException` (§5.1, §5.2).
- [ ] **CORE-06**: `Mailer`, `MailAccount` and transports are safe for concurrent use, as stated in the Javadoc (§11).
- [ ] **CORE-07**: `RecordingTransport` lives in the library's test sources, ships as a test-jar, registers through `ServiceLoader` as `recording`, stores sent messages in memory per instance and can script failures (§10.1).

### Errors (ERR)

- [ ] **ERR-01**: The checked hierarchy is `MailException`, which has `TransientMailException` (`Optional<Duration> retryAfter`), `PermanentMailException` (with `InvalidMailException` and `SenderRejectedException` beneath it) and `AuthRequiredException` (§5.2).
- [x] **ERR-02**: `MailTransport.send` Javadoc and the README state that a normal return means accepted, not delivered, and that retrying after a timeout can deliver a mail twice (at-least-once) **[decided]**.

### Entra Token Client (ENTRA)

- [ ] **ENTRA-01**: `EntraClientCredentials.withSecret(tenant, clientId, secret)` obtains a token by POSTing the form `client_id`, `scope`, `client_secret`, `grant_type=client_credentials` to `{authority}/{tenant}/oauth2/v2.0/token` (§7.1).
- [ ] **ENTRA-02**: `withCertificate(tenant, clientId, privateKey, certificate)` sends a `client_assertion` JWT (§7.2):
  - Header: PS256 and `typ` JWT, plus `x5t#S256`, the base64url SHA-256 of the certificate's DER encoding.
  - Claims: `aud` is the token URL, `iss` and `sub` are the client ID, `jti` is a new GUID, `nbf` and `iat` are now, `exp` is at most 10 minutes later.
  - Signature: RSASSA-PSS with SHA-256, MGF1 SHA-256 and salt 32.
  - Encoding: base64url without padding; a new assertion is built per request.
- [ ] **ENTRA-03**: `withCertificate(tenant, clientId, Path p12, password)` loads the key and certificate from a PKCS12 file. There is no PEM support (§7.2).
- [ ] **ENTRA-04**: The authority host is a constructor option, defaulting to the public cloud, so tests can point it at WireMock (§7.1).
- [ ] **ENTRA-05**: The token is cached in memory and reused until 5 minutes before expiry. Concurrent callers trigger at most one token request at a time. Failed requests are not cached (§7.3).
- [ ] **ENTRA-06**: `freshAccessToken` drops the cached token only if it equals the rejected token, then fetches a new one (CORE-02).
- [ ] **ENTRA-07**: 4xx responses map to `AuthRequiredException` carrying `error` and `error_description`. 5xx, 429 and network or timeout errors map to `TransientMailException` (§7.3).
- [ ] **ENTRA-08**: HTTP requests have explicit connect and request timeouts (G1).
- [ ] **ENTRA-09**: The secret, the assertion and the token are never logged (§7.3).

### Graph Transport (GRAPH)

- [ ] **GRAPH-01**: The built-in transport `graph` sends `POST {baseUrl}/users/{from}/sendMail` with a bearer token for scope `https://graph.microsoft.com/.default`. The from-address is percent-encoded in the path, and 202 counts as accepted (§6.1).
- [ ] **GRAPH-02**: The JSON body carries to/cc/bcc/replyTo, subject and one body. HTML is used if present, otherwise text. When both are given, only the HTML is sent, and this is logged once at INFO (§6.1).
- [ ] **GRAPH-03**: Attachments are sent as `#microsoft.graph.fileAttachment` with base64 `contentBytes` (§6.2).
- [ ] **GRAPH-04**: The options are `saveToSentItems` (boolean, default true, sent only when false) and `baseUrl` (default `https://graph.microsoft.com/v1.0`) **[decided]**. Unknown option keys are rejected (§6.1).
- [ ] **GRAPH-05**: `validate` rejects any attachment of 3 MB or more and attachments that together reach 3 MB, naming the file and the limit. It also rejects an estimated base64 request size above the limit, defined as one constant (§6.2).
- [ ] **GRAPH-06**: Errors are mapped as follows (§6.3):
  - 429, 503 and 504 become `TransientMailException`, with `Retry-After` parsed when present.
  - Network and timeout errors become `TransientMailException`.
  - 403 and 404 become `SenderRejectedException`.
  - 400 and 413 become `PermanentMailException`.
- [ ] **GRAPH-07**: On a 401, the transport calls `freshAccessToken` once and repeats the call one time. If the second call also fails, the 401 maps to `SenderRejectedException` when the body points at the mailbox, and to `AuthRequiredException` otherwise. There are no other retries (§6.3).
- [ ] **GRAPH-08**: Exception messages include Graph's `error.code`, its message and the `request-id`, but never tokens or bodies (G3).
- [ ] **GRAPH-09**: Graph HTTP requests have explicit connect and request timeouts (G1).

### Serial-Mail CLI Sample (CLI)

- [ ] **CLI-01**: `serial-mail send --account --campaign [--campaign-id] [--dry-run] [--out] [--limit N] [--rate 20] [--journal]` is a picocli command. The campaign ID defaults to the campaign directory name (§8.2).
- [ ] **CLI-02**: `account.properties` is read as UTF-8 and supplies `account.id`, `tenantId`, `clientId` and `from`. The credential is either `certificate.path` + `certificate.passwordEnv` or `secretEnv`, and secrets come from environment variables only (§8.1).
- [ ] **CLI-03**: The campaign directory supplies `subject.txt` (trailing newline trimmed), `body.html`, an optional `body.txt`, `recipients.csv` (required `email` column; all columns are fields) and `attachments/`, whose files are attached to every mail. All files are read as UTF-8 (§8.1, G2).
- [ ] **CLI-04**: A hand-written RFC 4180 CSV reader handles commas only, quoted fields, CRLF and a BOM. A header that looks `;`-delimited produces a clear pre-flight error. No CSV library is used **[decided]** (G2).
- [ ] **CLI-05**: Pre-flight checks everything before the first send: all placeholders exist as columns, every email is valid, and `mailer.validate` passes for the first row. It reports all problems together and exits with code 2 (§8.3).
- [ ] **CLI-06**: The journal is append-only JSON lines, keyed by `campaignId:lowercase(email)` with `Locale.ROOT`. Each line records the key, email, timestamp, outcome and error class/message, and is flushed and forced after every line. A rerun skips rows already accepted. A failed journal write stops the run (§8.3).
- [ ] **CLI-07**: Sends are spaced evenly according to `--rate` (mails per minute, default 20); skipped rows don't wait (§8.3).
- [ ] **CLI-08**: On `TransientMailException`, the sample waits `retryAfter` (capped) or 30 s and retries up to 3 times, then records the failure and continues. On another `PermanentMailException`, it records and continues (§8.3).
- [ ] **CLI-09**: On `SenderRejectedException` or `AuthRequiredException`, the run stops at once with exit code 3 (§8.3).
- [ ] **CLI-10**: The sample prints one progress line per recipient, and at the end prints the accepted/skipped/failed counts. The exit code is 0 only if nothing failed (§8.3, G6).
- [ ] **CLI-11**: `--dry-run` writes `out/<n>-<email>.html`, `.txt` and `.subject.txt` with sanitised file names and lists the attachments. It touches neither the network nor the journal and works without the secret or certificate env vars (§8.3, G7).
- [ ] **CLI-12**: The command logic takes a `Mailer`, so tests run it against `recording` (journal, resume, skip on rerun, stop on auth error, pre-flight report, dry-run) (§10.1).
- [ ] **CLI-13**: The example campaign in `morimail-sample-serial/example-campaign/` has three placeholders, a generated PDF and PNG (each under 100 KB, neutral metadata) and five `example.org` rows (§8.1).
- [ ] **CLI-14**: The sample runs end to end against WireMock for both the token endpoint and Graph, through the registry-built Graph account (§10.2 milestone 4).

### Test Assets (TEST)

- [ ] **TEST-01**: A throwaway PKCS12 test keystore, created with `keytool` (legacy PKCS12, password `changeit`) and clearly named, is checked in under `src/test/resources`. It is explained in one README sentence **[decided]** (§10.1).
- [ ] **TEST-02**: Entra tests:
  - Assertion: decode the JWT and check the header and claims; verify the signature with the certificate's public key independently.
  - WireMock: check the form fields for the secret and certificate flows; reuse on a second call; a refetch after expiry; error mapping; single-flight under concurrency; compare-and-drop invalidation (§10.1).
- [ ] **TEST-03**: Graph tests against WireMock with a stub `TokenProvider` cover:
  - The URL, headers and JSON body with attachments.
  - `saveToSentItems`.
  - Every row of the error table.
  - The single retry after a 401 carrying the fresh token (§10.1).

### Documentation (DOCS)

- [ ] **DOCS-01**: `docs/entra-setup.md` guides the owner through:
  - The app registration and the certificate (self-signed certificate and PKCS12 creation; a secret as the quick alternative).
  - Granting `Mail.Send`: Entra consent (A) versus Exchange RBAC for Applications (B), with B recommended. Cmdlets are copied from the current Microsoft page.
  - The traps: Entra and RBAC grants combine as a union; changes take 30 min to 2 h to apply; Enterprise-application IDs are required; Organization Management is required.
  - The licensed mailbox, the shared-mailbox question, `saveToSentItems` defaulting to true, and the `account.properties` and environment variables.
  - Verification: a dry run, then `--limit 1` (§9).
- [ ] **DOCS-02**: `docs/architecture.md` is a one-page summary of the contracts and the reasons for them. It records every deviation, including the `freshAccessToken` default, `baseUrl`, unchecked registry exceptions, JUnit 6, the `changeit` keystore, the reactor-only test-jar, and any differences found in the Microsoft docs (§5, §11).
- [ ] **DOCS-03**: The README covers what morimail is, its pre-1.0 status, the coordinates and a quick start. It also has a "what this is not" paragraph (Exchange Online limits of 30/min and 10k recipients/day per mailbox, not a bulk mailer), the at-least-once and accepted-not-delivered note, and the test keystore sentence (§13.1, G10).
- [ ] **DOCS-04**: `mvn -Prelease verify -Dgpg.skip -pl morimail -am` (scoped to the published modules, D-20 in `phases/01-library-core/01-CONTEXT.md`) builds the sources and Javadoc jars without warnings. A rehearsal bundle (`-DskipUpload=true`) contains only `morimail-parent` and `morimail`, and the owner's release steps are listed (§10.2 milestone 5, §13.5).

## v2 Requirements

### Inline Images (milestone 6, §10.3)

- **INLN-01**: The builder accepts `.inline(Path image, String contentId)`, and the HTML references the image as `cid:`.
- **INLN-02**: `build()` checks that every `cid:` reference has a matching inline attachment and the other way round, that content IDs are unique, and that only image types are used.
- **INLN-03**: The Graph transport sends inline images as inline file attachments; size limits still apply.
- **INLN-04**: The sample accepts an optional `inline/` directory, and dry-run rewrites `cid:` references to relative paths.

### Later Transports and Flows

- **LATER-01**: SMTP transport (Angus Mail joins the allowlist).
- **LATER-02**: SES and Gmail transports (REST or external module).
- **LATER-03**: Delegated OAuth flows and a token store.
- **LATER-04**: Attachments from 3 MB to 150 MB via the upload session (draft-then-send, `Mail.ReadWrite`).
- **LATER-05**: HTML and text sent as alternative MIME parts on Graph.

## Out of Scope

| Feature | Reason |
|---------|--------|
| Queue integration, idempotency keys, expiry | The consumer wraps the mail in its own message (§2) |
| Accounts from config files, secret refs, vaults | Consumer concern; the sample's `.properties` is sample-only |
| Local check of allowed from-addresses | Exchange enforces it server-side |
| Missing-placeholder policies other than "fail" | Rule 0 |
| Template loops, conditionals, expressions | Rule 0; strict renderer by design |
| Retry or backoff inside the library's `send` | Retrying is the caller's decision (§6.3) |
| Per-recipient attachments in the CLI; batch or shared attachments in the library | §2 |
| S/MIME, DKIM, custom headers, List-Unsubscribe | §2 |
| Spring Boot starter | §2 |
| CSV `--delimiter` option or CSV library | Owner decision: comma-only reader |
| "In doubt" journal outcome / write-ahead record | Owner decision: retry and document at-least-once |
| Publishing the test-jar or the sample to Central | Owner decision: reactor-only test-jar; sample never published |
| Vendor SDKs (Graph SDK, MSAL4J, JWT library), PEM parsing, managed identity | §2, §7 |
| Contribution guide, code of conduct, issue templates, badges | §13.1, until there are contributors |
| Parallel sending in the CLI | Rule 0; the rate limit is serial by design |

## Traceability

Which phases cover which requirements. Updated during roadmap creation.

| Requirement | Phase | Status |
|-------------|-------|--------|
| REPO-01 | Phase 1 | Complete |
| REPO-02 | Phase 1 | Complete |
| REPO-03 | Phase 1 | Complete |
| REPO-04 | Phase 1 | Complete |
| REPO-05 | Phase 1 | Complete |
| REPO-06 | Phase 1 | Complete |
| REPO-07 | Phase 1 | Complete |
| REPO-08 | Phase 3 | Pending |
| REPO-09 | Phase 1 | Complete |
| REPO-10 | Phase 1 | Complete |
| REPO-11 | Phase 1 | Complete |
| REPO-12 | Phase 1 | Complete |
| MODEL-01 | Phase 1 | Complete |
| MODEL-02 | Phase 1 | Complete |
| MODEL-03 | Phase 1 | Complete |
| MODEL-04 | Phase 1 | Complete |
| MODEL-05 | Phase 1 | Complete |
| TMPL-01 | Phase 1 | Complete |
| TMPL-02 | Phase 1 | Complete |
| TMPL-03 | Phase 1 | Complete |
| TMPL-04 | Phase 1 | Complete |
| CORE-01 | Phase 1 | Pending |
| CORE-02 | Phase 1 | Complete |
| CORE-03 | Phase 1 | Pending |
| CORE-04 | Phase 1 | Pending |
| CORE-05 | Phase 1 | Pending |
| CORE-06 | Phase 1 | Pending |
| CORE-07 | Phase 1 | Pending |
| ERR-01 | Phase 1 | Pending |
| ERR-02 | Phase 1 | Complete |
| ENTRA-01 | Phase 3 | Pending |
| ENTRA-02 | Phase 3 | Pending |
| ENTRA-03 | Phase 3 | Pending |
| ENTRA-04 | Phase 3 | Pending |
| ENTRA-05 | Phase 3 | Pending |
| ENTRA-06 | Phase 3 | Pending |
| ENTRA-07 | Phase 3 | Pending |
| ENTRA-08 | Phase 3 | Pending |
| ENTRA-09 | Phase 3 | Pending |
| GRAPH-01 | Phase 3 | Pending |
| GRAPH-02 | Phase 3 | Pending |
| GRAPH-03 | Phase 3 | Pending |
| GRAPH-04 | Phase 3 | Pending |
| GRAPH-05 | Phase 3 | Pending |
| GRAPH-06 | Phase 3 | Pending |
| GRAPH-07 | Phase 3 | Pending |
| GRAPH-08 | Phase 3 | Pending |
| GRAPH-09 | Phase 3 | Pending |
| CLI-01 | Phase 2 | Pending |
| CLI-02 | Phase 2 | Pending |
| CLI-03 | Phase 2 | Pending |
| CLI-04 | Phase 2 | Pending |
| CLI-05 | Phase 2 | Pending |
| CLI-06 | Phase 2 | Pending |
| CLI-07 | Phase 2 | Pending |
| CLI-08 | Phase 2 | Pending |
| CLI-09 | Phase 2 | Pending |
| CLI-10 | Phase 2 | Pending |
| CLI-11 | Phase 2 | Pending |
| CLI-12 | Phase 2 | Pending |
| CLI-13 | Phase 2 | Pending |
| CLI-14 | Phase 3 | Pending |
| TEST-01 | Phase 3 | Pending |
| TEST-02 | Phase 3 | Pending |
| TEST-03 | Phase 3 | Pending |
| DOCS-01 | Phase 4 | Pending |
| DOCS-02 | Phase 4 | Pending |
| DOCS-03 | Phase 4 | Pending |
| DOCS-04 | Phase 4 | Pending |

**Coverage:**
- v1 requirements: 69 total
- Mapped to phases: 69
- Unmapped: 0 ✓

**Per phase:** Phase 1: 29 · Phase 2: 13 · Phase 3: 23 · Phase 4: 4

---
*Requirements defined: 2026-11-21*
*Last updated: 2026-11-21 during Phase 1 planning (REPO-04 JUnit 6 owner decision marked)*
