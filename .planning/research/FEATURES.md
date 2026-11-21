# Feature Research

**Domain:** Small Java mail-sending library (templated mail, pluggable transports, Microsoft Graph sendMail as an Entra app) plus a serial-mail (mail-merge) CLI sample
**Researched:** 2026-11-21
**Confidence:** MEDIUM-HIGH. The Microsoft facts are HIGH (read from learn.microsoft.com). The competitor feature lists are MEDIUM (project pages and search summaries). A few Graph size and header claims are LOW and are marked where they appear.

**Scope rule for this file:** Scope is fixed by spec.md section 2 and Rule 0 ("when in doubt, build less"). This file does not propose expanding scope. It does three things:

1. It checks that the spec covers what consumers of such a library or CLI expect.
2. It lists the few gaps that are cheap and needed for the MVP, with a verdict on each.
3. It lists anti-features, meaning things that comparable tools do and this project deliberately will not.

**Spec coverage verdict:** The spec covers all the table stakes of the domain. It has 12 small gaps (G1 to G12 below). Four are needed for a correct MVP: G1 (HTTP timeouts), G2 (UTF-8 and CSV edge cases), G3 (diagnostic detail in Graph errors) and G4 (`Automatic-Module-Name`). The rest are cheap polish or documentation that the roadmap can fold into existing phases. Nothing here needs a new phase.

---

## Feature Landscape

Each table has a `Spec?` column for table stakes and differentiators:
- **Yes** means the spec already asks for it.
- **GAP Gn** means the spec is silent. See "Spec Gaps" for the verdict.

### 1. Mail Model and Builder

#### Table Stakes

| Feature | Why Expected | Complexity | Spec? | Notes |
|---------|--------------|------------|-------|-------|
| from, to, cc, bcc, reply-to, subject, HTML body, optional text body, attachments | Every library has these (Simple Java Mail, Spring `MimeMessageHelper`, Graph `message`) | LOW | Yes (section 5) | Records as specified. |
| Display name on addresses | Needed for a decent "From" and "To" in clients | LOW | Yes (`MailAddress(email, displayName)`) | Graph's `from` display name may not be honoured when sending through `/users/{id}/sendMail`. Check this in the live test (LOW confidence, unverified). |
| Default sender per account | Spring and Simple Java Mail both have a default "from" | LOW | Yes (`defaultFrom`) | |
| Fluent builder with validation at `build()` | Simple Java Mail validates when the email is built. Failing early is expected. | LOW | Yes (at least one recipient, subject, body, readable attachments) | |
| Basic address sanity: no CR/LF, a single `@` | Simple Java Mail checks for CRLF injection and validates addresses. A transport-neutral core cannot rely on a transport to do this. | LOW | **GAP G5** | The spec only checks syntax in the CLI pre-flight. Keep it to a few lines. Do not use a regex monster and do not add a dependency. |
| Attachment content type inferred when not given | Callers use `.attach(Path)` and expect it to work | LOW | Partly (the record has `contentType`, but nothing says how `.attach(Path)` sets it) | `Files.probeContentType` returns null on some platforms. Fall back to `application/octet-stream`. Do not add a MIME database. |
| Non-ASCII subjects, names and bodies work | Any German-language consumer hits this on day one | LOW | **GAP G2** | JSON handles it. The trap is reading template and CSV files with the platform charset on Java 17. Always pass `UTF_8` explicitly. |
| Plain data records that can be serialized by the consumer | Queue-based consumers need this. Simple Java Mail ships Java serialization of `Email`. | LOW | Yes (records, no library serialization) | Shipping no serialization code is correct. |

#### Differentiators

| Feature | Value Proposition | Complexity | Spec? | Notes |
|---------|-------------------|------------|-------|-------|
| `Mail` = account ID + message (transport-neutral message) | The same message can feed a JSON transport (Graph) or a MIME transport (SMTP later). Jakarta-wrapper libraries are MIME-first. | LOW | Yes | The architectural differentiator. Protect it with the rule "no Graph assumptions in core". |
| `mailer.validate(mail)` without sending | Lets the sample's pre-flight and dry-run check transport limits without a network call | LOW | Yes | Few libraries separate "validate" from "send". |
| `contentId` on `Attachment`, rejected in MVP | Inline images (milestone 6) need no model change | LOW | Yes | The only forward-looking field. Keep it at one field. |

#### Anti-Features (do not build)

| Feature | Why Requested | Why Problematic | Alternative |
|---------|---------------|-----------------|-------------|
| `byte[]` / `InputStream` / `DataSource` attachments | Simple Java Mail supports them. Consumers with an in-memory PDF will ask. | Breaks "plain serializable records", and `Path` is enough for the MVP. | The consumer writes a temp file. Revisit after 0.1.0 if users ask. |
| Custom headers, importance, read and delivery receipts, flags, calendar invites | Simple Java Mail and the Graph `message` resource have them. | Each is a new field plus a per-transport mapping. The spec puts headers out of scope. | Not in MVP. |
| Reply, reply-all and forward builders | Simple Java Mail has them. | A different use case (conversation handling). | Not in MVP. |
| Conversion to or from EML or `.msg`, `MimeMessage` interop | Simple Java Mail offers it. | It would pull the core towards Jakarta Mail. | Not in MVP. |
| Async sending (`CompletableFuture`), connection pools, batch or cluster delivery | Simple Java Mail offers async and batch modules. | Spec: "the library sends one mail". `Mailer` is thread-safe, so the consumer can use its own executor. | Document thread safety. Done. |
| DKIM, S/MIME, encryption | Simple Java Mail core features. | Out of scope. Exchange signs outbound mail itself. | None. |
| Library-level "shared attachments" or batch concept | A natural extension for mail merge | Explicitly forbidden by the spec. | The sample builds one `Mail` per row. |

---

### 2. Templates

#### Table Stakes

| Feature | Why Expected | Complexity | Spec? | Notes |
|---------|--------------|------------|-------|-------|
| Placeholder substitution from a data map | The reason a "templated mail" library exists. Competitors delegate this to Thymeleaf, FreeMarker or Mustache. | LOW | Yes (`$$name$$`) | |
| HTML-escaping of values in the HTML body, with an explicit raw escape hatch | Without it, any CSV value with `<` or `&` breaks the mail or opens an injection hole. | LOW | Yes (`$$!name$$`) | The most important correctness item in the renderer. Escape `& < > " '`. |
| Header-injection guard on the subject | Simple Java Mail guards against CRLF injection. Subject values come from data. | LOW | Yes (line breaks become a space) | |
| Apply to subject, HTML and text | Expected | LOW | Yes | |
| Fail on a missing placeholder, naming the part and the field | Silent blanks are the classic mail-merge disaster ("Dear , ..."). | LOW | Yes (`InvalidMailException`) | |

#### Differentiators

| Feature | Value Proposition | Complexity | Spec? | Notes |
|---------|-------------------|------------|-------|-------|
| Strict by default, with no "lenient" mode | Most template engines render a missing variable as empty. Failing loudly is a safety feature for serial mail. | LOW | Yes | Keep "fail" as the only policy. |
| Zero-dependency, no expression language | No injection surface and no engine to learn. A reader can understand the whole renderer in minutes. | LOW | Yes | |
| Unrecognized `$$` text is left as it is | The renderer will not mangle CSS, JS or prices in HTML. | LOW | Yes | Also the source of G9. |

#### Anti-Features

| Feature | Why Requested | Why Problematic | Alternative |
|---------|---------------|-----------------|-------------|
| Loops, conditionals, partials, layouts, i18n, default-value syntax (`$$name\|fallback$$`) | The Python mailmerge tool has Jinja conditionals, and every engine has these. | It becomes a template language. Spec: "No expressions, loops or conditionals." | Consumers who need this render with their own engine and pass the finished strings. Simple Java Mail takes the same approach. |
| Missing-placeholder policies (empty, keep, warn) | Convenience | Spec explicitly defers. They invite the "Dear ," bug. | Fail only. |
| Markdown to HTML, auto-generated text part from HTML | Python mailmerge supports Markdown, and some libraries auto-generate a plain-text alternative. | Another subsystem. Graph sends HTML only anyway. | The consumer supplies both bodies. |
| Template loading (classpath, file, URL, caching) | Engines include loaders. | The renderer takes strings. File reading belongs to the consumer (the sample). | Sample reads files as UTF-8. |

---

### 3. Accounts and Registry

#### Table Stakes

| Feature | Why Expected | Complexity | Spec? | Notes |
|---------|--------------|------------|-------|-------|
| Account object holding transport, credentials and defaults | Spring's `JavaMailSenderImpl` and Simple Java Mail's `Mailer` both bundle these | LOW | Yes (`MailAccount`) | |
| Thread-safe sender object | Servers share one instance across threads | LOW | Yes (`Mailer`, transports and token providers safe for concurrent use, stated in Javadoc) | Needs a concurrency test. Cover it in the Entra single-flight test. |
| Clear failure for a wrong transport name or account ID | Basic usability | LOW | Yes (the unknown-transport error lists available IDs; unknown account is a `PermanentMailException`) | |
| A way to plug in your own transport | Simple Java Mail has custom mailers. Spring has `MailSender`. | LOW to MED | Yes (`TransportProvider` + `ServiceLoader`) | |

#### Differentiators

| Feature | Value Proposition | Complexity | Spec? | Notes |
|---------|-------------------|------------|-------|-------|
| Multiple accounts in one `Mailer`, selected by ID on the mail | Spring needs several `JavaMailSender` beans, and Simple Java Mail needs several `Mailer` objects. Here the queue consumer needs only the ID in its message. | LOW | Yes | The ID-in-message design is what makes a later queue integration free. |
| Built-in plus `ServiceLoader` registry, where an external duplicate of a built-in ID fails | Predictable. No silent override of the Graph transport by a classpath accident. | LOW to MED | Yes | Test the duplicate-ID failure explicitly. |
| `RecordingTransport` as a test double that is registered like a real plug-in | It proves the SPI works and lets the sample be tested without a network. For SMTP libraries the usual fake is GreenMail, which needs a server. | LOW | Yes (test-jar, ID `recording`) | Consumers will ask for it next. See the anti-feature below. |

#### Anti-Features

| Feature | Why Requested | Why Problematic | Alternative |
|---------|---------------|-----------------|-------------|
| Properties/YAML account loading, Spring Boot starter, secret references, vault adapters | Spring and Simple Java Mail load config from properties. | Spec: the consumer's concern. A config format is a permanent public API. | Accounts are built in code. The sample reads its own `.properties`. |
| Dynamic transport lookup by class name, optional Maven dependencies | Convenient plug-ins | Spec forbids reflection and class-name loading. | `ServiceLoader` only. |
| Local allow-list of from-addresses | Looks safer | Exchange enforces it server-side. A local copy goes stale. | Map the server error to `SenderRejectedException`. |
| Publishing the `RecordingTransport` test-jar to Maven Central | Consumers want a fake for their own tests | The spec says the test-jar is not published. It would become a public API. | Defer. Make sure the Central bundle does not include the test-jar (see G11). |
| Failover or round-robin across accounts | Common in provider SDKs | The consumer's policy. | None. |

---

### 4. Errors

#### Table Stakes

| Feature | Why Expected | Complexity | Spec? | Notes |
|---------|--------------|------------|-------|-------|
| One exception root so callers can catch everything | Basic API hygiene | LOW | Yes (`MailException`) | |
| A retry-or-not signal | Any caller that sends in a loop needs it | LOW | Yes (`Transient` vs `Permanent`) | |
| `Retry-After` carried on transient errors | HTTP 429/503 contract. Graph throttling responses include it. | LOW | Yes (`Optional<Duration> retryAfter`) | Parse the seconds form. The HTTP-date form is optional. Anything unparseable becomes empty. |
| Errors that identify what to fix, without secrets | Diagnosing a 403 from Exchange is the dominant support problem for Graph senders. | LOW | Partly. See G3. | Put Graph `error.code`, the message and the `request-id` header into the exception message. Never put in tokens, assertions or the mail body. |
| Preserved cause | Debugging | LOW | Implied | Pass the `cause` through on network errors. |

#### Differentiators

| Feature | Value Proposition | Complexity | Spec? | Notes |
|---------|-------------------|------------|-------|-------|
| `SenderRejectedException` vs `AuthRequiredException` | One mailbox is unusable, or the whole account is. A queue worker sets the mail aside or pauses the account. SDK exceptions (Graph SDK `ODataError`, Spring `MailException`) carry no such distinction. | LOW | Yes | The most useful part of the error design. Keep the 3-way split and resist adding more subclasses. |
| Checked exceptions with a closed set of categories | Forces the caller to decide | LOW | Yes | |

#### Anti-Features

| Feature | Why Requested | Why Problematic | Alternative |
|---------|---------------|-----------------|-------------|
| Built-in retry/backoff inside `send` | "Reliable" libraries retry. | Spec: retrying is the caller's decision. Sending is not idempotent (see the pitfall in "Known limits"). | The sample does its own bounded retry. |
| Error-code enums for every Graph error, or a typed `GraphError` public class | Looks thorough | It leaks Graph into the core and widens the API. | Put the Graph code in the message text only. |
| Delivery-status tracking, bounce parsing, NDR polling | Marketing tools do it. Graph posts NDRs to the sender's Inbox after the 202. | A second system (a mailbox reader, `Mail.Read`). | Document that 202 means accepted, not delivered. |

---

### 5. Entra Auth

#### Table Stakes

| Feature | Why Expected | Complexity | Spec? | Notes |
|---------|--------------|------------|-------|-------|
| Client secret and certificate flows | Both MSAL4J and Azure Identity offer them. Production use favours certificates. | MED | Yes | The PS256 assertion with `x5t#S256` is the hard part. Test by verifying the signature with the certificate's public key. |
| In-memory token cache with an expiry margin | Without it every send makes a token call and risks throttling. | LOW | Yes (5-minute margin) | |
| Single-flight refresh | Concurrent senders must not stampede the token endpoint. | LOW to MED | Yes | Needs a concurrency test. |
| Invalidation after a 401 | Handles a token revoked or a cache gone stale | LOW | Yes | Keep inside the library. Do not widen `TokenProvider`. |
| Distinguish "credential unusable" (4xx) from "Entra is down" (5xx, 429, network) | The same split as the error categories | LOW | Yes | |
| Authority host overridable | Tests with WireMock. Sovereign clouds come free. | LOW | Yes | |
| Loading a PKCS12 file | The usual keystore format in Java | LOW | Yes | Define which alias is used: the first key entry. Use the same password for store and key. |
| HTTP timeouts on the token call | A hung token endpoint must not hang a worker. | LOW | **GAP G1** | `HttpClient` has no default request timeout. |

#### Differentiators

| Feature | Value Proposition | Complexity | Spec? | Notes |
|---------|-------------------|------------|-------|-------|
| JDK-only (no MSAL4J, no Azure Identity, no JWT library) | The jar stays at Jackson only. MSAL4J and Azure Identity bring multiple extra libraries. (MEDIUM: from general knowledge, not measured.) | MED | Yes | The whole project premise. |
| Modern certificate credentials (PS256 and `x5t#S256`) | Many online samples still show RS256 and SHA-1 `x5t`. | MED | Yes | Follow the current English Microsoft page. |
| `TokenProvider` is a functional interface | A later SMTP XOAUTH2 or delegated flow can reuse it. | LOW | Yes | |

#### Anti-Features

| Feature | Why Requested | Why Problematic | Alternative |
|---------|---------------|-----------------|-------------|
| Managed identity, workload identity federation, device code, interactive login, user (delegated) flows | Azure Identity offers a credential chain. | Spec: later milestones. Each is a new auth type. | Consumers can implement `TokenProvider` themselves. |
| PEM or PKCS8 parsing, certificate rotation, Key Vault loading | Convenient in real deployments | A parser is a security-sensitive surface. Spec: "Nothing else (no PEM parsing)". | Convert to PKCS12 with `openssl` (documented in the setup guide). |
| Persistent or distributed token cache | MSAL offers token cache serialization | Not needed. An app-only token is cheap to re-request. | In-memory only. |
| Parsing or validating the access token (JWT claims) | Looks like diligence | Spec: "Do not parse or validate the access token". The token is opaque to the client. | Rely on the 401 path. |
| Credential chain, auto-discovery from environment variables | Azure Identity's `DefaultAzureCredential` | Hidden behaviour. | Explicit construction only. |

---

### 6. Graph Transport

#### Table Stakes

| Feature | Why Expected | Complexity | Spec? | Notes |
|---------|--------------|------------|-------|-------|
| `POST /users/{from}/sendMail` with JSON, HTML body, recipients, reply-to | The documented call. Confirmed: the `message` has a single `body` with a content type. | LOW | Yes | |
| File attachments as base64 `fileAttachment` | Documented for JSON sendMail. Confirmed: `@odata.type`, `name`, `contentType` and `contentBytes` are what the examples use. | LOW | Yes | |
| `saveToSentItems` option, sent only when false | Documented | LOW | Yes | |
| 202 means accepted | Microsoft: not delivered. NDRs go to the sender's Inbox later. | LOW | Yes | Also document it in the README. |
| Error mapping to the three categories | Core value | MED | Yes (table in 6.3) | The spec itself says to verify against real responses at the checkpoint. |
| Attachment size limit checked before sending | A clear local error beats a 413 | LOW | Yes (under 3 MB per file and in total) | See "Known limits" for a caveat on the total. |
| One retry after a 401 with a fresh token | Handles a stale cached token | LOW | Yes | |
| Request timeouts, and a `HttpTimeoutException` to `TransientMailException` mapping | A serial sender must never block forever. | LOW | **GAP G1** | |
| Base URL overridable (for WireMock tests) | The spec requires WireMock tests for Graph, so this must be possible. | LOW | Implied | Make it a transport option, not a core feature. |

#### Differentiators

| Feature | Value Proposition | Complexity | Spec? | Notes |
|---------|-------------------|------------|-------|-------|
| No Graph SDK | The Graph Java SDK (Kiota based) with Azure Identity, MSAL4J and an HTTP stack is a large dependency tree for one POST. (MEDIUM: general knowledge.) | MED | Yes | Keep the transport one class plus JSON building. |
| App-only, per-mailbox scope via Exchange RBAC for Applications | Most samples use tenant-wide `Mail.Send`, which lets the app send as any user. | LOW (docs only) | Yes (docs, section 9) | A security differentiator. It lives in the setup guide, not the code. |
| Everything checked in `validate()` before any network call | The CLI pre-flight gets Graph's limits for free. | LOW | Yes | |

#### Anti-Features

| Feature | Why Requested | Why Problematic | Alternative |
|---------|---------------|-----------------|-------------|
| Upload sessions for 3 MB to 150 MB attachments | Real mails have big PDFs. | Needs the draft-then-send flow and `Mail.ReadWrite`, and widens the permission. | The limit message names the file. A later milestone. |
| MIME format sending (HTML and text as alternatives, custom headers, S/MIME) | Gives full MIME control. | Needs a MIME builder, which is the SMTP transport's job. | HTML only. Log once at INFO when both bodies are given. |
| `internetMessageHeaders` / List-Unsubscribe | Graph supports `internetMessageHeaders`. Bulk mail wants List-Unsubscribe. | Out of scope. I could not confirm whether Graph accepts non-`x-` header names (LOW). | Do not build. |
| Draft-based send, `me/sendMail`, delegated mode, shared-mailbox special cases | Different permissions and endpoints | Not the app-only story. | Test shared mailboxes in the owner's live test and document the result. |
| Batch ($batch) or parallel sending, internal rate limiting | Throughput | A consumer concern. Exchange limits per mailbox are low. | The sample rate-limits. |
| National cloud profiles | The authority and base URL options already allow it. | Untestable here. | No extra code. |

---

### 7. Serial-Mail CLI (`morimail-sample-serial`)

Reference points: the Python `mailmerge` tool defaults to dry-run and a limit of one message. Nylas-style shell loops use a fixed delay. Thunderbird add-ons and Gmail mail merge are GUI tools. I found no CLI with a durable send journal or resume.

#### Table Stakes

| Feature | Why Expected | Complexity | Spec? | Notes |
|---------|--------------|------------|-------|-------|
| Dry-run that renders everything and sends nothing | Every mail-merge tool has a preview. Python mailmerge makes it the default. | LOW | Yes (files in `out/`) | |
| `--limit N` | For a test send to yourself. Mailmerge's default limit is 1. | LOW | Yes | The setup guide uses `--limit 1`. |
| Pre-flight check of the whole input before the first send | Prevents the half-sent campaign | MED | Yes (all problems reported at once, exit 2) | |
| Rate limiting | Exchange allows 30 messages per minute per mailbox. | LOW | Yes (default 20/min) | 20 is below Microsoft's documented 30/min (HIGH). Keep it. |
| Send log, and resume without duplicates | Core of the "never sends twice" goal | MED | Yes (JSON-lines journal, key `campaignId:lowercase(email)`) | Failed rows are retried on the next run, because only "accepted" rows are skipped. Document that. |
| Bounded retry of transient failures | Expected | LOW | Yes (3 times, `retryAfter` or 30 s) | |
| Stop on account-level errors | One bad credential must not produce 500 identical failures | LOW | Yes (exit 3) | |
| Summary counts and exit codes | Scriptability | LOW | Yes | |
| Secrets via environment variables only | Basic security | LOW | Yes | |
| Per-row progress line on the console | Without it a 500-row run is silent for 25 minutes. | LOW | **GAP G6** | One line per row: index, address, outcome. |
| CSV that works with real-world files (UTF-8, BOM, quoted fields) | Excel exports break naive parsers. | LOW to MED | **GAP G2** | Decide the parser (see G2). |
| Example campaign | Lowers the barrier | LOW | Yes (five `example.org` rows, generated PDF and PNG) | |

#### Differentiators

| Feature | Value Proposition | Complexity | Spec? | Notes |
|---------|-------------------|------------|-------|-------|
| Durable, append-only journal with resume | Mail-merge tools usually have no memory. A crash means resending to everyone. | MED | Yes | The headline feature of the sample. |
| Pre-flight includes the transport's `validate()` | Catches a 3 MB attachment before mail 1 is sent. | LOW | Yes | |
| Dry-run with no network and no journal changes | Safe to run anywhere | LOW | Yes | Make it work without secrets (G7). |
| Sample doubles as the contract test of the library | Built against `RecordingTransport` first | MED | Yes (milestone order) | |

#### Anti-Features

| Feature | Why Requested | Why Problematic | Alternative |
|---------|---------------|-----------------|-------------|
| Open and click tracking, tracking pixels, link rewriting | Marketing tools | A server component, plus privacy issues. | None. |
| Unsubscribe handling, suppression lists, bounce processing, List-Unsubscribe | Required for bulk-sender compliance (Google and Yahoo ask for it above about 5,000 messages a day; I could not verify this threshold against their own pages, LOW) | Microsoft says Exchange Online is not for bulk commercial mail. This is a small, serial tool. | State the intended scale in the README (see G10). |
| Per-recipient attachments, CC/BCC columns, scheduling, segmentation, A/B | The Python mailmerge supports templated attachment paths. | Spec out of scope. Grows the CSV schema. | One shared attachment folder. |
| Interactive "Send 500 mails? [y/N]" prompt, `--yes` flag | A safety net | Blocks automation, adds a flag. | Print a one-line banner before sending ("N mails as X from account Y"). Recommend a dry-run first in the docs. |
| Parallel sending or worker threads | Speed | At 20 mails per minute there is no gain. It would break the journal. | Sequential. |
| GUI, web UI, daemon mode, built-in scheduler | Convenience | A different product. | Run it from cron. |
| Config file formats beyond `.properties` (YAML, JSON), CLI flags for every setting | Flexibility | More surface. | The single `.properties` file. |
| Plug-in mail transports from the CLI (a `transport=` key) | Looks symmetric | Not asked. The CLI must still be testable against `RecordingTransport`. | Test seam in code: a `run(Mailer, ...)` entry point that tests call. |

---

### 8. Repo and Release

#### Table Stakes

| Feature | Why Expected | Complexity | Spec? | Notes |
|---------|--------------|------------|-------|-------|
| `LICENSE` (MIT), `README.md` with coordinates and quick start, `SECURITY.md` | Required by Central consumers' expectations. A library handling credentials must have a private reporting channel. | LOW | Yes | |
| CI running `mvn verify` on Java 17 | Expected | LOW | Yes | |
| Central requirements: POM metadata, sources jar, Javadoc jar, GPG signatures | Central rejects a release without them. | MED | Yes (`release` profile) | The publishing plugin does not create them, so the profile must. |
| Semantic versioning with a 0.x warning | Expected | LOW | Yes | |
| Maven wrapper | Reproducible builds | LOW | Yes | `.gitignore` excludes `*.jar`, so un-ignore `.mvn/wrapper/maven-wrapper.jar`. |
| Stable JPMS module name | Library authors are expected to set one. The default name derives from the file name. After 1.0 it cannot change without breaking users. | LOW | **GAP G4** | One manifest line: `Automatic-Module-Name: de.morisoft.morimail`. Do not add a `module-info.java` (Rule 0, and `ServiceLoader` works through `META-INF/services`). |
| Offline `mvn verify` | Reproducible CI | LOW | Yes | |

#### Differentiators

| Feature | Value Proposition | Complexity | Spec? | Notes |
|---------|-------------------|------------|-------|-------|
| `live` test profile that never runs in CI | Real Graph validation without leaking secrets | LOW | Yes | |
| Public repo hygiene rules (no tenant IDs, zero GUIDs, a clearly named test keystore) | Safe for a public history | LOW | Yes | Also scan for leaks before the first push. |

#### Anti-Features

| Feature | Why Requested | Why Problematic | Alternative |
|---------|---------------|-----------------|-------------|
| CONTRIBUTING, CODE_OF_CONDUCT, issue templates, badges | "Looks like a real project" | Spec: not until there are contributors | Leave out. |
| CHANGELOG file, release automation, dependabot, signing via CI | Maturity signals | More files and secrets in CI. Spec: portal token and GPG key stay with the owner. | GitHub releases written by hand. |
| Multi-JDK CI matrix, `module-info.java`, reproducible-build tuning, BOM artifact | Typical for mature libraries | Premature | Java 17 only. |
| Publishing the sample or the test-jar | Convenience | Spec says no | `maven.deploy.skip` on the sample. |

---

### 9. Docs

#### Table Stakes

| Feature | Why Expected | Complexity | Spec? | Notes |
|---------|--------------|------------|-------|-------|
| README quick start (one copy-paste example that sends) | The first thing a user reads | LOW | Yes | |
| Javadoc on public types with thread-safety statements | Required for the release build and by the spec | LOW | Yes | Javadoc errors block the release. |
| Entra and Exchange setup guide | The hardest part of Graph app-only sending is the admin setup, not the code. | MED | Yes (`docs/entra-setup.md`) | |
| One-page architecture note | Records deviations from the spec | LOW | Yes | |
| Mapping from exception type to meaning and next action | The point of the error design | LOW | **G3 / G10** | A short table in the README or the setup guide. |

#### Differentiators

| Feature | Value Proposition | Complexity | Spec? | Notes |
|---------|-------------------|------------|-------|-------|
| RBAC-for-Applications recommendation with the union trap and the 30-minute to 2-hour delay | The guide that most Graph-sendMail tutorials lack. They end at "grant admin consent". | MED | Yes | The Entra grant must be removed or the RBAC scope has no effect. |
| Honest "what this is not" paragraph | Sets expectations and prevents misuse as a bulk marketing sender | LOW | **GAP G10** | Include the Exchange limits (30/min, 10,000 recipients per day) and Microsoft's own advice to use a bulk provider for newsletters (HIGH, from the Exchange Online limits page). |

#### Anti-Features

| Feature | Why Requested | Why Problematic | Alternative |
|---------|---------------|-----------------|-------------|
| A docs site, versioned docs, generated API reference site, tutorials | Polish | Maintenance for a 0.1 library | Markdown in the repo plus Javadoc. |
| Documentation of future milestones (SMTP, SES, OAuth) as if planned | Roadmap marketing | Promises that bind | Mention only the inline-image milestone, which the spec names. |

---

## Spec Gaps and Verdicts

**Needed for the MVP (put into the phase plans):**

| ID | Gap | Why it matters | Cost | Phase |
|----|-----|----------------|------|-------|
| G1 | **HTTP timeouts** on the Graph and token calls (connect and request). `java.net.http.HttpClient` has no default request timeout, so a stalled connection blocks the sender forever. Map `HttpTimeoutException` to `TransientMailException`. | The spec maps "network or timeout error" to transient, but never says how a timeout arises. A serial sender that hangs mid-campaign is the worst failure for this CLI. | LOW. Two constants (for example 10 s connect and 30 s request), plus a test with a WireMock fixed delay. | Entra and Graph phase |
| G2 | **UTF-8 and CSV real-world input.** (a) Read template, CSV and account files with `StandardCharsets.UTF_8` explicitly. On Java 17 the default charset is platform-dependent (it only became UTF-8 in JDK 18). (b) Strip a UTF-8 BOM. (c) Support RFC 4180 quoted fields (commas and newlines inside quotes). (d) Decide the delimiter. Fixed comma, documented, is the Rule 0 answer. German-locale Excel exports use semicolons, so the pre-flight should detect "header has no `email` column but contains `;`" and say so, rather than fail with "column email missing". Write the CSV parser by hand (about 40 lines). Ask before adding a CSV dependency. | Without these the first real CSV breaks. A mis-decoded umlaut in a name goes out in a customer-visible mail. | LOW to MED | CLI phase |
| G3 | **Diagnostic detail in Graph errors.** Include `error.code`, the error message and the `request-id` response header in the `MailException` message. | Exchange 403/404 problems are diagnosed from the code (for example mailbox or permission errors), and Microsoft support asks for the request ID. It also makes the owner's live-test feedback on the section 6.3 table precise. | LOW. Never include tokens or full request bodies. | Graph phase |
| G4 | **`Automatic-Module-Name`** in the library jar manifest. | A stable name cannot be changed later without breaking users. Not having one means the name derives from the jar file name. | LOW. One plugin setting. | Repo and POM phase |

**Cheap polish. Recommended, but each can be dropped without harming the MVP:**

| ID | Gap | Verdict |
|----|-----|---------|
| G5 | Builder rejects CR/LF in addresses and subject, and requires one `@`. The spec's CLI pre-flight does "syntactically valid email" but the library builder does not. Put the one check in the builder so both the library and the CLI use it. | Recommended. About 10 lines. It is a transport-neutral rule, and a later MIME/SMTP transport would need it anyway. |
| G6 | One progress line per row on the CLI console (index, address, outcome). | Recommended. About 5 lines. |
| G7 | Dry-run works without secrets. `EntraClientCredentials.withCertificate(path, ...)` loads the keystore eagerly, so a dry-run on a laptop without the certificate or the env var would fail. Dry-run only needs `mailer.validate`, which never asks for a token, so give it a stub `TokenProvider`. | Recommended design note for the CLI phase. |
| G8 | Single-run guard on the journal: take an exclusive `FileChannel.tryLock` on the journal file. Two parallel runs of one campaign would both send everything. | Optional. About 5 lines. The alternative is one sentence in the README. |
| G9 | Warn in the pre-flight about text that looks like a placeholder but does not match the grammar (`$$ firstName $$`, `$$Größe$$` with a non-ASCII name). The spec leaves such text as it is, so it would be sent as literal `$$...$$`. | Optional. Do it in the CLI only. Do not change the renderer rules. A pre-flight check on the rendered output of the first row is enough. |
| G10 | README and setup guide state the intended scale and the Exchange Online limits. | Recommended. Documentation only. Also covers the default rate (20/min vs 30/min limit). |
| G11 | Make sure the Central bundle does not include the test-jar. The spec says it is not published, but the publishing plugin uploads attached artifacts. | Check in the release phase. |
| G12 | Decide `saveToSentItems` for the sample. `account.properties` has no key for it, so the Graph default (true) applies and a 500-row campaign leaves 500 copies in Sent Items. | Decision for the roadmap. Rule 0 answer: leave the default and say so in the setup guide. It doubles as an audit trail. |

---

## Known Limits Worth Stating (not features)

- **Retry can duplicate a mail.** `sendMail` is not idempotent. If a request times out after Graph accepted it, a retry sends a second copy. The spec accepts the retry for network errors. The journal cannot prevent this for the in-run retry. Document it. A crash between "202 received" and "journal line written" has the same effect on the next run. (Reasoned from the spec and the API behaviour. HIGH on the mechanism.)
- **3 MB total versus the request size.** Attachments travel base64-encoded in the JSON body, which adds about 33%. A total of just under 3 MB of raw files becomes about 4 MB of JSON. Microsoft staff in a Q&A thread say the sendMail request limit is "generally around 4 MB" (413 above that). The official sendMail page does not state this limit (LOW: unofficial). The spec's "under 3 MB in total" may therefore be too generous for the total. Have the owner try a 2.9 MB attachment in the live test, and lower the total limit if it returns 413.
- **Accepted is not delivered.** After the 202, Exchange still processes the message. Invalid recipients produce non-delivery reports in the sender's Inbox, which this library never sees. (HIGH: Microsoft "send mail process" page.)
- **Exchange Online sending limits** (HIGH, Exchange Online limits page, updated 2026-11): 30 messages per minute per mailbox, 10,000 recipients per 24 hours per mailbox, and a tenant-level external recipient limit. Mail from the default `onmicrosoft.com` domain is throttled to 100 external recipients per day. The spec's 20/min default stays under the per-minute limit. Microsoft recommends a bulk provider for customer newsletters. The README should not oversell the sample as a newsletter platform.
- **The 3 MB rule is about `fileAttachment`, and inline images use the same type.** The `fileAttachment` resource carries `contentId` and `isInline`, so milestone 6 is a field mapping, not a new endpoint. (HIGH: resource page. Not needed for the MVP.)

---

## Feature Dependencies

```
Mail model records (MailAddress, Attachment, MailMessage, Mail)
    |--required by--> Builder + validation (incl. G5 address rules)
    |                     |--required by--> Template rendering (builder.template(...))
    |                     |--required by--> Mailer.validate / send
    |--required by--> TransportProvider / MailTransport SPI
                          |--required by--> Registry (built-in list + ServiceLoader)
                                                |--required by--> MailAccount.build (unknown ID error lists IDs)
                                                |--required by--> RecordingTransport (registered as "recording")

Error hierarchy
    |--required by--> every transport, the token client, Mailer, and the CLI exit codes (2, 3)

Entra token client (secret, then certificate, cache, single-flight, invalidate)
    |--required by--> Graph transport (401 retry needs invalidate)
    (graph and entra do not import each other; they meet only at TokenProvider)

RecordingTransport + Mailer + Builder + Renderer
    |--required by--> Serial-mail CLI (built and tested before the Graph transport)
          |--required by--> pre-flight (uses mailer.validate)
          |--required by--> journal --required by--> resume / skip on rerun
          |--required by--> rate limit, retry, stop-on-auth-error
          |--required by--> example campaign --> dry-run output
          (CLI dry-run works without secrets: G7)

Graph transport (validate limits, error mapping, G1 timeouts, G3 detail)
    |--required by--> live test (owner) --> corrections to the error table and the 3 MB total
          |--required by--> docs/entra-setup.md final wording, CLI rate default (20/min)

Release profile (sources, Javadoc, GPG) <-- needs Javadoc on all public types (every earlier phase)
G4 Automatic-Module-Name <-- parent POM / library POM
```

### Dependency Notes

- **The CLI requires the core, not Graph.** This confirms the spec's order (sample before Graph). The contracts settle against `RecordingTransport` before a real transport depends on them.
- **Graph requires Entra only through `TokenProvider`.** Build the token client first or stub it, and test Graph with a stub `TokenProvider`. They can be developed in parallel after the core.
- **The error hierarchy is needed first.** The CLI's exit codes, retry and stop behaviour all depend on it. It comes in the first phase with the records.
- **Javadoc is a cross-cutting dependency of the release.** A late Javadoc failure blocks Central. Keep `mvn -Prelease verify -Dgpg.skip` in view from the start, not just at the end.
- **The setup guide and the error-table corrections depend on the owner's live test.** They cannot be finished before the checkpoint.
- **Conflicts:** "inline images (CID)" conflicts with "no `contentId` in MVP" until milestone 6. "Shared attachments" must stay a CLI idea and never enter the library.

---

## MVP Definition

### Launch With (v0.1.0)

Everything in spec section 2, plus the four MVP gaps:

- [ ] Mail model records, builder with validation (including the small G5 rule)
- [ ] `TemplateRenderer` (`$$name$$`, `$$!name$$`, strict, subject guard)
- [ ] Error hierarchy with the three categories
- [ ] `MailAccount`, `Mailer`, `TransportProvider` registry (built-in plus `ServiceLoader`, duplicate-ID failure), `RecordingTransport`
- [ ] Serial-mail CLI: pre-flight (exit 2), journal and resume, rate limit, transient retry, stop on account errors (exit 3), dry-run, counts, G2 CSV handling, G6 progress line, G7 dry-run without secrets
- [ ] Entra token client: secret and certificate (PS256), cache, single-flight, invalidation, G1 timeouts
- [ ] Graph transport: sendMail JSON, attachments under 3 MB, `saveToSentItems`, error table, one 401 retry, G1 timeouts, G3 error detail
- [ ] Repo and release: POM metadata, wrapper, `LICENSE`, `README`, `SECURITY.md`, CI, `release` profile, G4
- [ ] Docs: `entra-setup.md` (RBAC for Applications, union trap, delay), `architecture.md`, README quick start, G10 limits paragraph

### Add After Validation (v0.2 or later)

- [ ] Inline images (milestone 6), because `contentId` is already in the model
- [ ] Larger attachments through the upload session, triggered by real users hitting the limit
- [ ] `byte[]` attachments, if consumers ask
- [ ] A published `RecordingTransport` for consumers' tests, if consumers ask
- [ ] CSV delimiter option, if semicolon files turn out to be common
- [ ] SMTP transport (the contracts already allow it)

### Future Consideration (v1+)

- [ ] MIME-format sending (HTML and text alternatives, custom headers, List-Unsubscribe)
- [ ] Delegated OAuth, token store
- [ ] SES, Gmail transports
- [ ] Queue integration helpers (the spec leaves them to the consumer)

---

## Feature Prioritization Matrix

| Feature | User Value | Implementation Cost | Priority |
|---------|------------|---------------------|----------|
| Records, builder, validation | HIGH | LOW | P1 |
| Renderer (escape, strict, subject guard) | HIGH | LOW | P1 |
| Error hierarchy | HIGH | LOW | P1 |
| Registry plus `RecordingTransport` | HIGH | MED | P1 |
| Graph sendMail plus attachments plus error mapping | HIGH | MED | P1 |
| Entra secret and certificate, cache, single-flight | HIGH | MED | P1 |
| CLI pre-flight, journal, resume, rate limit, retry | HIGH | MED | P1 |
| G1 HTTP timeouts | HIGH | LOW | P1 |
| G2 UTF-8 and CSV handling | HIGH | LOW to MED | P1 |
| G3 Graph error code and request ID in messages | MED | LOW | P1 |
| G4 `Automatic-Module-Name` | MED | LOW | P1 |
| `entra-setup.md` (RBAC) | HIGH | MED | P1 |
| `release` profile | HIGH | MED | P1 |
| G5 builder address rules | MED | LOW | P2 |
| G6 CLI progress line | MED | LOW | P2 |
| G7 dry-run without secrets | MED | LOW | P2 |
| G10 limits paragraph in the docs | MED | LOW | P2 |
| G8 journal lock, G9 placeholder-lookalike warning | LOW | LOW | P3 |
| Inline images (milestone 6) | MED | MED | P3 (post-MVP) |
| Upload sessions, MIME, SMTP, byte[] attachments | MED | HIGH | P3 (post-MVP) |

**Priority key:** P1 = must have for launch. P2 = should have, add when cheap. P3 = nice to have, or post-MVP.

---

## Competitor Feature Analysis

| Feature | Simple Java Mail | Spring `JavaMailSender` | Graph SDK samples / Azure Identity | Mail-merge CLIs (Python `mailmerge`, shell loops) | Our Approach |
|---------|------------------|-------------------------|-------------------------------------|---------------------------------------------------|--------------|
| Transport | SMTP only (Jakarta Mail, OAuth2 token providers) | SMTP only (Jakarta Mail) | Graph only | SMTP | Pluggable. Graph first. |
| Dependencies | Jakarta Mail plus modules | Spring plus Jakarta Mail | Graph SDK, Kiota, Azure Identity, MSAL4J (MEDIUM) | Python stack | Jackson only |
| Templates | None. Bring your own engine. | None. Bring your own. | None | Jinja2 (loops, conditionals), Markdown | Minimal `$$name$$`, strict, HTML-escaped |
| Missing template data | n/a | n/a | n/a | Renders empty or errors, depending on the engine | Always fails with the part and field named |
| Validation | Address and CRLF injection checks | Minimal | None | Dry-run checks attachments exist | Builder rules plus transport `validate()` |
| Attachments | Bytes, DataSource, files; embedded images | `MimeMessageHelper` | `fileAttachment` base64, upload session for large files | Via `ATTACHMENT:` header, may be per-recipient | `Path` only, under 3 MB (Graph) |
| Inline images | Yes | Yes | `isInline` and `contentId` | Yes | Milestone 6 |
| Async / batch / pooling | Yes (batch module) | Via the application | Via the SDK | Sequential | None. Sequential, consumer-owned. |
| DKIM / S/MIME | Yes | Manual | S/MIME via MIME | No | No |
| Error categories | Exceptions from Jakarta Mail | `MailException` family | `ODataError` | Process exit code | Transient / permanent / auth-required, with `retryAfter` |
| Multi-account | Several `Mailer` objects | Several beans | Several clients | One SMTP config | Accounts by ID in one `Mailer` |
| Test double | Needs a fake SMTP server | GreenMail or a mock | Mock the HTTP layer | `--dry-run` | `RecordingTransport` plus `ServiceLoader` |
| Dry-run | n/a | n/a | n/a | Yes (default in Python `mailmerge`) | Opt-in `--dry-run`, plus `mailer.validate` |
| Send journal / resume | n/a | n/a | n/a | Not found | Yes (JSON lines, keyed by campaign and address) |
| Rate limit | n/a | n/a | n/a | Fixed delay in shell loops | `--rate`, default 20/min |
| App-only Entra auth | No | No | Yes (library) | No | Yes, JDK only, certificate or secret |

## Sources

- Microsoft Graph `user: sendMail` (HIGH): https://learn.microsoft.com/en-us/graph/api/user-sendmail (JSON and MIME formats, single `body`, `fileAttachment` example, `internetMessageHeaders` example, `saveToSentItems`, 202 semantics, updated 2026-06)
- Overview of the Microsoft Graph send mail process (HIGH): https://learn.microsoft.com/en-us/graph/outlook-things-to-know-about-send-mail (post-202 processing, NDRs in the sender's Inbox)
- `fileAttachment` resource (HIGH): https://learn.microsoft.com/en-us/graph/api/resources/fileattachment (`contentBytes`, `contentId`, `isInline`)
- Exchange Online limits (HIGH): https://learn.microsoft.com/en-us/office365/servicedescriptions/exchange-online-service-description/exchange-online-limits (30 messages per minute, 10,000 recipients per day, recommendation to use a bulk provider for newsletters, onmicrosoft.com throttling; page dated 2026-11-11)
- Attach large files to Outlook messages (HIGH, cited from the spec): https://learn.microsoft.com/graph/outlook-large-attachments
- Microsoft Q&A, sendMail request size (LOW, unofficial): https://learn.microsoft.com/en-in/answers/questions/1824204/graph-api-user-sendmail-request-size-limit (the "around 4 MB" statement)
- Simple Java Mail features (MEDIUM): https://www.simplejavamail.org/features.html
- `awdeorio/mailmerge` (MEDIUM): https://github.com/awdeorio/mailmerge (dry-run by default, limit 1, Jinja2, per-message attachments)
- Nylas CLI mail-merge guides (LOW to MEDIUM, third-party): https://cli.nylas.com/guides/email-mail-merge-cli
- Spring and Graph SDK feature comparisons are from general knowledge (MEDIUM), not re-fetched.
- Not verified: Graph's rules on custom header names, whether `message.from` display name is honoured through `/users/{id}/sendMail`, Google and Yahoo bulk-sender thresholds, and the Outlook per-app Graph throttling limits (the throttling page was too large to read in full).

---
*Feature research for: Java mail library (morimail) plus serial-mail CLI sample, Microsoft Graph app-only*
*Researched: 2026-11-21*
