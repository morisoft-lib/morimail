# Spec: morimail - Java mail library MVP with a serial-mail sample (Microsoft Graph, Entra app-only)

Status: draft for implementation with Claude Code, lean revision
Date: 2026-11-21
Project name: morimail. Repository: https://github.com/morisoft-lib/morimail
Maven coordinates: `de.morisoft:morimail`. Base package: `de.morisoft.morimail`
Maintainer: Stephan Wald <stephan@morisoft.de>
License: MIT. The project is open source from the first commit, developed in a public repository, and published to Maven Central (section 13)

## 1. Goal

Build two things in one repository, in parallel:

1. A small Java library that sends templated mail through pluggable transports. The MVP ships one transport: Microsoft Graph, authenticated as an Entra app (client credentials with certificate or client secret, no user sign-in).
2. A command-line sample that sends a serial mail: one template with placeholders, one CSV of recipients, shared attachments, one mail per recipient.

The MVP is done when the sample sends a newsletter with attachments to a list of test recipients from a mailbox in the owner's tenant, and a second run of the same campaign sends nothing twice.

The library sends one mail at a time. Everything around that (recipient lists, campaigns, retries, queues, configuration files) belongs to the consumer. The sample is such a consumer.

## 2. Scope

### In scope

- Mail model: account ID, from, to, cc, bcc, reply-to, subject, HTML body, optional text body, attachments.
- Template rendering with `$$name$$` placeholders from a data record, applied to subject, HTML and text.
- Accounts, built in code: an account is an ID, a transport and its auth. A mail refers to an account by ID.
- Transport SPI and registry. Graph is the first built-in transport.
- Entra client-credentials token client written on the JDK, with certificate and client secret.
- Three error categories: transient, permanent, auth-required.
- Sample CLI with dry-run, rate limit, send journal and resume.
- Admin setup guide for the Entra and Exchange Online side.

### Out of scope for the MVP

Planned for later milestones. The contracts must accept them without change:

- SMTP, SES, Gmail transports.
- Delegated (user) OAuth flows, token store, interactive consent.
- Inline images (CID). Scheduled as milestone 6, the first after the MVP (section 10). The `Attachment` record is prepared for them.
- Attachments of 3 MB or more (section 6.3).
- Sending HTML and text as two alternative parts (section 6.1).

Left to the consumer, or added only when a real need appears:

- Queue integration (RabbitMQ or other), including idempotency keys and expiry times on the mail. The consumer wraps the mail in its own message.
- Loading accounts from configuration files, secret references, vault adapters.
- A local check of which from-addresses an account may use. Exchange enforces this on the server.
- Missing-placeholder policies other than "fail".
- Per-recipient attachments in the sample CLI. At library level this needs nothing: every mail is built on its own with its own attachments, and the library must not add any batch or "shared attachments" concept.
- S/MIME, DKIM, custom internet headers, List-Unsubscribe.
- Spring Boot starter.

### Registry and dependency requirements, in scope now

The Graph transport is the first plug-in, not a special case.

- **Two registration paths, one registry.**
  - Built-in: supported transports that stay within the dependency allowlist live in the library jar and are registered in code, in one explicit list. Adding one means adding its package and one line in that list.
  - External: `ServiceLoader` finds transports in other jars. These are a consumer's own transports, and later any supported transport with heavy dependencies.
  - Both paths yield the same `TransportProvider` objects. An external transport that uses the ID of a built-in one is an error when the registry loads; it does not override the built-in.
- **Dependency allowlist for the library jar: Jackson.** Nothing else at runtime. Logging uses the JDK's `System.Logger`. Angus Mail will join the list when the SMTP transport is built.
- **No vendor SDK where a REST call does the job.** Built-in transports and auth clients use the JDK HTTP client and Jackson. This applies to Graph and to the Entra token request in the MVP. Expected later: Gmail API as a REST call, SES through its SMTP endpoint or a hand-signed HTTP call. Check these when they are built; they were not verified for this spec.
- **A transport with heavy dependencies is an external jar.** If a transport cannot do without a vendor SDK, it becomes its own Maven module and artifact in this repository (for example `morimail-ses`), depends on `morimail`, and registers through `ServiceLoader`. No optional Maven dependencies, no loading by class name. None is built in the MVP.
- **No Graph assumptions in the core.** The model, `MailTransport` and the error types contain nothing that only Graph has or needs. A transport that builds MIME and one that builds JSON must both be able to implement the interface from the same message.
- **Auth is optional per transport.** `TransportProvider.create` accepts a null `TokenProvider` (SES and password SMTP will not use one).
- **Transport options are opaque to the core.** Each transport validates its own options map.
- **Limits come from the transport.** Rules such as Graph's 3 MB attachment limit are checked in the transport's `validate` method, not held as constants in the core.
- **Unknown transport ID fails when the account is built,** with a message that lists the available IDs.

## 3. Technology

| Item | Choice | Note |
|---|---|---|
| Language | Java 17 | Records are used for the model |
| Build | Maven: parent, one library module, one sample module; from 0.2.0 also the helper module `morimail-html` (section 10.4) | Maven wrapper checked in |
| HTTP | `java.net.http.HttpClient` | Graph and token endpoint |
| JSON | Jackson databind | Only runtime dependency of the library |
| Token and JWT | JDK only: `KeyStore`, `MessageDigest`, `Signature`, `Base64` | No MSAL4J, no JWT library |
| Logging | `System.Logger` | No logging dependency |
| CLI | picocli | Sample module only |
| Sample config | Java `.properties` file | Sample module only |
| Tests | JUnit 5, AssertJ, WireMock | |

Use the latest stable release of each dependency at implementation time. Check Maven Central; do not rely on remembered version numbers.

## 4. Repository layout

```
pom.xml                        parent (`morimail-parent`), dependency management
morimail/                      the library: one jar
  de.morisoft.morimail         core: model, builder, renderer, Mailer, registry, errors
  de.morisoft.morimail.graph   Graph transport
  de.morisoft.morimail.entra   Entra client-credentials token client
morimail-html/                 helper jar (from 0.2.0, section 10.4): depends on morimail
  de.morisoft.morimail.html    turns image URLs in a template into inline images
morimail-sample-serial/        CLI sample
docs/
  spec.md                      this document
  entra-setup.md               admin guide, see section 9
  architecture.md              one page: the contracts and the reasons for them
CLAUDE.md                      working rules from section 11
LICENSE                        MIT
README.md                      what it is, status, quick start
SECURITY.md                    how to report a vulnerability
.github/workflows/ci.yml       mvn verify on push and pull request
```

Import rules inside the library jar:

- The core package imports only the JDK. Its single reference to another package is the built-in list.
- `graph` and `entra` do not import each other. The transport gets a `TokenProvider` and does not know how the token was obtained. A later SMTP transport can then reuse Entra tokens, and a later delegated flow can reuse the Graph transport.

## 5. Core (package `de.morisoft.morimail`)

Signatures are binding in intent, not in every detail. Record deviations in `docs/architecture.md`.

```java
public record MailAddress(String email, String displayName) {}
// Named MailAddress, not Address: Jakarta Mail has an Address class, and the later
// SMTP transport will use both in one file.

public record Attachment(String filename, String contentType, Path file, String contentId) {}
// contentId null = regular attachment. Non-null = inline image (section 10.3); build() checks
// its content ID, its image type and the cid: references of the HTML body.

public record MailMessage(MailAddress from, List<MailAddress> to, List<MailAddress> cc, List<MailAddress> bcc,
                          MailAddress replyTo, String subject, String html, String text,
                          List<Attachment> attachments) {}

public record Mail(String accountId, MailMessage message) {}

public interface MailTransport {
    void validate(MailMessage message) throws InvalidMailException;   // transport-specific limits
    void send(MailMessage message) throws MailException;              // returns normally = accepted
}

public interface TransportProvider {       // built-in list, or ServiceLoader for external jars
    String id();                           // "graph"
    MailTransport create(Map<String, Object> options, TokenProvider auth);   // auth may be null
}

@FunctionalInterface
public interface TokenProvider {
    String accessToken(String scope) throws MailException;
}

public final class MailAccount { ... }     // id, transport, defaultFrom
public final class Mailer { ... }          // holds accounts by ID
```

### 5.1 Usage

```java
TokenProvider auth = EntraClientCredentials.withCertificate(tenantId, clientId, privateKey, certificate);
// or: EntraClientCredentials.withCertificate(tenantId, clientId, Path.of("app.p12"), password)
// or: EntraClientCredentials.withSecret(tenantId, clientId, secret)

MailAccount account = MailAccount.builder("entra-main")
    .transport("graph")                    // resolved through the registry
    .auth(auth)
    .option("saveToSentItems", false)
    .defaultFrom("newsletter@example.com")
    .build();

Mailer mailer = Mailer.of(account);

Mail mail = Mail.using("entra-main")
    .to(record.get("email"))
    .template(template, record)
    .attach(Path.of("agenda.pdf"))
    .build();

mailer.send(mail);
```

- `build()` checks what every transport needs: at least one recipient, a subject, html or text present, attachment files exist and are readable, and inline images pass the section 10.3 checks (a valid content ID, an `image/` content type, unique content IDs, `cid:` references in both directions).
- `mailer.validate(mail)` resolves the account and runs the transport's `validate`. `mailer.send(mail)` runs it too before sending.
- If `from` is not set on the mail, the account's `defaultFrom` is used.
- `Mail` and `MailMessage` are plain records without behaviour, so a consumer can serialize them for a queue with its own tooling. The library ships no serialization code.

### 5.2 Errors

```
MailException (checked)
  TransientMailException      retry later; carries Optional<Duration> retryAfter
  PermanentMailException      do not retry this mail
    InvalidMailException      the mail itself is wrong
    SenderRejectedException   this mailbox is not usable by this account
  AuthRequiredException       the account's credential is unusable; every mail of the account will fail
```

The difference between `SenderRejectedException` (one mailbox) and `AuthRequiredException` (whole account) is what a later queue worker needs: the first sets one mail aside, the second pauses sending for the account. An unknown account ID in `send` is a `PermanentMailException`.

### 5.3 Template rendering

One final class, `TemplateRenderer`. No interface.

- `MailTemplate` holds three strings: subject, html, optional text.
- A placeholder is `$$name$$` where the name matches `[A-Za-z0-9_.-]+`. Any other text, including a lone `$$`, is left as it is.
- In the HTML body, values are HTML-escaped. `$$!name$$` inserts the value unescaped.
- In subject and text, values are inserted as they are. Line breaks in a subject value are replaced by a space.
- A placeholder with no matching field is an `InvalidMailException` that names the template part and the placeholder.
- Data is `Map<String, Object>`; values are converted with `String.valueOf`. No expressions, loops or conditionals.

## 6. Graph transport (package `de.morisoft.morimail.graph`)

### 6.1 Call

- Endpoint: `POST https://graph.microsoft.com/v1.0/users/{from-address}/sendMail` with `Content-Type: application/json` (Microsoft n.d.-f).
- The mailbox in the URL is the message's from-address. With application permission, the app sends through that user's mailbox (Microsoft n.d.-e).
- Token scope: `https://graph.microsoft.com/.default`.
- Success is `202 Accepted` with an empty body. It means accepted, not delivered (Microsoft n.d.-f). `send` therefore returns nothing.
- Body: if html is present, send it with content type HTML. If both html and text are supplied, send the html only and log this once at INFO. This assumes the JSON format carries one body; confirm that against the message resource documentation before coding. Sending both parts needs the MIME format (Microsoft n.d.-f) and is a later milestone.
- Option `saveToSentItems` (default true). It only needs to be sent when false (Microsoft n.d.-f).

### 6.2 Attachments

- JSON `sendMail` can carry file attachments in the same call (Microsoft n.d.-f). Each becomes a file attachment with base64 content.
- Microsoft documents the direct route for files under 3 MB and an upload session on a draft message for files from 3 MB to 150 MB (Microsoft n.d.-a).
- MVP rule, implemented in the Graph transport's `validate`: reject any attachment of 3 MB or more, and reject when the attachments together reach 3 MB. The message names the file and the limit.
- The upload-session route is a later milestone. It needs a draft-then-send sequence and the `Mail.ReadWrite` application permission in addition to `Mail.Send` (Microsoft n.d.-a).
- Inline images (section 10.3) are file attachments with `"isInline": true` and `"contentId"` set to the bare content ID, without angle brackets, that the HTML refers to as `cid:<contentId>`. `contentLocation` is not sent.
- Regular attachments carry no `isInline` key.
- Inline images count toward the same limits as regular attachments.
- Confirmed live on 2026-11-23: the image showed inline, not as a separate attachment, in Outlook on the web and one desktop client (`LiveGraphTest.sendsOneMailWithAnInlineImage`).
- The Microsoft pages behind these property names are listed in `docs/architecture.md`.

### 6.3 Error mapping

Checked against real responses in the owner's live test on 2026-11-22, after milestone 4. The last column says which rows were seen live. When a real response differs from a row, correct it in code and here.

| Response | Exception | Live status (2026-11-22) |
|---|---|---|
| 202 | none | Confirmed for the certificate flow and the secret flow |
| 429, 503, 504 | `TransientMailException`, with `Retry-After` when present | WireMock only |
| Network or timeout error | `TransientMailException` | WireMock only |
| 401 after a fresh token was obtained | `SenderRejectedException` if the body points at the mailbox, otherwise `AuthRequiredException` | Not provoked live (WireMock only) |
| 403 | `SenderRejectedException` (mailbox outside the app's scope) | Confirmed: `ErrorAccessDenied`. Also seen for minutes after a permission grant, while Exchange applies it |
| 404 | `SenderRejectedException` (mailbox does not exist) | Confirmed: `ErrorInvalidUser` for a sender without an Exchange Online mailbox |
| 400, 413 | `PermanentMailException` | WireMock only. No 413 seen: attachments of 2,900,000 and 3,100,000 bytes were accepted |

On a 401, ask the token provider for a new token once and repeat the call one time before mapping the error. In the live test a sender without an Exchange Online mailbox got 404 `ErrorInvalidUser`, not a 401, so the 404 row covers it. `MailboxNotEnabledForRESTAPI` and the refusal of a mailbox outside an RBAC scope were not provoked.

The transport performs no other retries. Retrying is the caller's decision.

## 7. Entra token client (package `de.morisoft.morimail.entra`)

One class, `EntraClientCredentials implements TokenProvider`, on the JDK HTTP client. No MSAL4J, no JWT library.

### 7.1 Token request

- `POST https://login.microsoftonline.com/{tenant}/oauth2/v2.0/token`, content type `application/x-www-form-urlencoded` (Microsoft n.d.-d).
- Form fields for a client secret: `client_id`, `scope`, `client_secret`, `grant_type=client_credentials` (Microsoft n.d.-d).
- Form fields for a certificate: the same, with `client_secret` replaced by `client_assertion_type=urn:ietf:params:oauth:client-assertion-type:jwt-bearer` and `client_assertion` (Microsoft n.d.-d).
- The success response carries `access_token`, `token_type` and `expires_in` in seconds (Microsoft n.d.-d). Do not parse or validate the access token itself.
- The authority host is a constructor option with the public cloud as default, so tests can point it at WireMock.

### 7.2 Certificate assertion

Build the JWT by hand. Follow the current English version of the certificate credentials page; an older copy still shows RS256 with a SHA-1 `x5t` header and must not be used.

- Header: `alg` PS256, `typ` JWT, `x5t#S256` with the base64url SHA-256 thumbprint of the certificate's DER encoding (Microsoft n.d.-b).
- Claims: `aud` is the token endpoint URL of the tenant; `iss` and `sub` are the client ID; `jti` is a new GUID; `nbf` and `iat` are the current time; `exp` is at most 10 minutes later (Microsoft n.d.-b).
- Signature: RSA with PSS padding (Microsoft n.d.-b). In the JDK: `Signature.getInstance("RSASSA-PSS")` with a `PSSParameterSpec` for SHA-256, MGF1 with SHA-256, salt length 32.
- Encoding: base64url without padding for all three parts. JSON through Jackson.
- A new assertion is built for every token request.

Inputs: a `PrivateKey` and an `X509Certificate`. A convenience factory loads both from a PKCS12 file with a password. Nothing else (no PEM parsing).

### 7.3 Caching and errors

- Cache the access token in memory per instance and reuse it until 5 minutes before `expires_in` runs out. Safe for concurrent use; at most one token request at a time per instance.
- A method to drop the cached token, used by the Graph transport's 401 handling. Keep this inside the library; do not widen the `TokenProvider` interface for it if a simpler way exists.
- Error response: HTTP 400 with `error` and `error_description` fields (Microsoft n.d.-d). Map 4xx responses to `AuthRequiredException` with both fields in the message. Map 5xx, 429 and network errors to `TransientMailException`.
- Never log the secret, the assertion or the token.

## 8. Sample: serial mail CLI (`morimail-sample-serial`)

### 8.1 Inputs

```
campaign/
  subject.txt          Welcome to the November update, $$firstName$$
  body.html
  body.txt             optional
  recipients.csv       header row; column "email" required; all columns are placeholder fields
  attachments/         every file here is attached to every mail (sample logic, not a library feature)

account.properties
  account.id=entra-main
  tenantId=...
  clientId=...
  from=newsletter@example.com
  certificate.path=/path/app.p12
  certificate.passwordEnv=MAIL_CERT_PASSWORD
  # or, in place of the two certificate lines:
  # secretEnv=MAIL_CLIENT_SECRET
```

Secrets and passwords are read from the named environment variables only. Ship one complete example campaign under `morimail-sample-serial/example-campaign/` with three placeholder fields, two small generated attachments (a PDF and a PNG, under 100 KB each) and five rows using `example.org` addresses.

### 8.2 Command

```
serial-mail send
  --account account.properties
  --campaign ./campaign
  [--campaign-id 2026-11-update]       default: campaign directory name
  [--dry-run]                          render only, send nothing
  [--out ./out]                        dry-run output directory
  [--limit N]                          process only the first N recipients
  [--rate 20]                          mails per minute, default 20
  [--journal ./campaign/journal.jsonl]
```

### 8.3 Behaviour

1. Read the account file, build the token provider, the account and the `Mailer`. Read the template and CSV.
2. Check everything before the first send: all placeholders in the template exist as CSV columns, every row has a syntactically valid email, and `mailer.validate` passes for the first row's mail. Report all problems at once and exit with code 2.
3. For each row: render, build, send.
4. Journal key: `campaignId + ":" + lowercase(email)`. Skip rows the journal records as accepted.
5. After each attempt, append one JSON line to the journal: key, email, timestamp, outcome, error class and message if any. Flush after every line.
6. Rate limit: fixed spacing between sends derived from `--rate`.
7. `TransientMailException`: wait `retryAfter` or 30 seconds, retry up to 3 times, then record the failure and continue.
8. Other `PermanentMailException` for a recipient: record and continue.
9. `SenderRejectedException` or `AuthRequiredException`: stop the run at once. Exit code 3.
10. At the end, print counts: accepted, skipped, failed. Exit code 0 only if nothing failed.

Dry-run writes `out/<n>-<email>.html`, `.txt` and `.subject.txt` per recipient, lists the attachments, and touches neither the network nor the journal.

The default of 20 mails per minute stays below the Exchange Online limit of 30 messages per minute per mailbox for SMTP client submission; Microsoft does not say whether Graph `sendMail` counts against it. A mailbox can also send to at most 10,000 recipients per day (Microsoft n.d.-g). The owner confirmed the default after milestone 4.

## 9. Admin setup guide (`docs/entra-setup.md`)

A step-by-step guide the repository owner can follow with full admin rights:

1. Create the app registration (single tenant).
2. Create a certificate and upload its public part to the app registration. Show one way to create a self-signed certificate and a PKCS12 file for it. Mention the client secret as the quick alternative for a first test.
3. Grant `Mail.Send`. Explain both ways and recommend B:
   - A. Entra consent: add the Microsoft Graph application permission `Mail.Send` and grant admin consent. The app can then send as any user in the organization (Microsoft n.d.-e).
   - B. Exchange RBAC for Applications, scoped to chosen mailboxes (Microsoft n.d.-c):
     - `New-ServicePrincipal` with the IDs from Enterprise applications, not from App registrations.
     - `New-ManagementScope` with a recipient filter for the sending mailboxes.
     - `New-ManagementRoleAssignment -App <id> -Role "Application Mail.Send" -CustomResourceScope <scope>`.
     - `Test-ServicePrincipalAuthorization -Identity <app> -Resource <mailbox>` and check `InScope`.
4. State the trap: Entra grants and RBAC grants are combined as a union. If `Mail.Send` is also consented in Entra, the RBAC scope has no effect; the Entra consent must be removed (Microsoft n.d.-c).
5. State the delay: permission changes can take 30 minutes to 2 hours to reach live API calls, while the test cmdlet shows the configured state at once (Microsoft n.d.-c). A 403 right after setup is expected.
6. Required role: Organization Management in Exchange Online for the role assignment (Microsoft n.d.-c).
7. The sending mailbox must exist and be licensed for Exchange Online. Whether a shared mailbox works with the scoped assignment is to be confirmed in the owner's live test.
8. How to fill `account.properties` and the environment variables.
9. Verification: the sample in dry-run mode, then with `--limit 1` to the owner's own address.

Copy cmdlet syntax from the Microsoft page at writing time, not from this spec.

## 10. Testing and milestones

### 10.1 Tests

- Renderer: escaping, raw insert, missing field, subject line-break guard, text that looks almost like a placeholder.
- Builder and `Mailer`: validation rules, default from, unknown account.
- Registry: built-in `graph` resolves with no `ServiceLoader` entry; an external transport is found through `ServiceLoader`; an external transport that reuses a built-in ID fails.
- `RecordingTransport`: lives in the library's test sources, is published as a test-jar and registers through `ServiceLoader` under the ID `recording`, the way an external transport would. It stores sent messages in memory.
- Graph transport (WireMock, stub `TokenProvider`): request URL, headers, JSON body with attachments, `saveToSentItems`, every row of the error table, the single retry after 401.
- Entra token client:
  - Assertion: decode the JWT, compare header and claims with section 7.2, verify the signature with the certificate's public key.
  - WireMock token endpoint: form fields for the secret flow and the certificate flow, token reuse on a second call, a new request after expiry, error mapping.
  - Test certificate: a throwaway PKCS12 file created with `keytool`, checked in under `src/test/resources` with the password `test`. This is the one permitted exception to the no-secrets rule; name the file so that its purpose is obvious.
- Sample (with `RecordingTransport`): journal, resume, skip on rerun, stop on auth error, pre-flight report, dry-run output.
- Live test: one JUnit test tagged `live`, excluded from the default build, enabled with `-Plive`, configured through environment variables. Claude Code does not run it; the owner does.

`mvn verify` must pass with no network access.

### 10.2 Milestones

Work in this order. Finish and commit each milestone before starting the next. Each ends with `mvn verify` green.

| # | Deliverable | Check |
|---|---|---|
| 1 | Parent POM with the metadata from section 13, two modules, `LICENSE`, `README.md` stub, `SECURITY.md`, CI workflow, `CLAUDE.md`, `docs/spec.md`, model records, error types | Build passes locally and in CI |
| 2 | Renderer, builder, `MailAccount`, `Mailer`, registry, `RecordingTransport` | Core and registry tests |
| 3 | Sample CLI complete against `RecordingTransport` | Dry-run of the example campaign produces 5 rendered mails; sample tests pass |
| 4 | Entra token client and Graph transport as built-ins | Token and Graph WireMock tests pass; sample runs end to end against WireMock |
| 5 | `docs/entra-setup.md`, `docs/architecture.md`, README with a quick start, `release` profile from section 13 | Owner follows the guide and sends the example campaign to test addresses; a second run sends nothing. `mvn -Prelease verify -Dgpg.skip` builds sources and Javadoc jars without warnings |

The sample comes before the Graph work on purpose: it exercises the contracts first, so contract mistakes surface before a real transport depends on them.

Stop after milestone 4 and report, so the owner can run the live test and feed real responses back into section 6.3.

### 10.3 Milestone 6: inline images, implemented in 0.2.0

- Builder: `.inline(Path image, String contentId)`; the HTML template refers to the image as `<img src="cid:logo">`.
- `build()`: every `cid:` reference in the rendered HTML has a matching inline attachment and the other way round; content IDs are unique within a mail; only image content types.
- A transport that cannot send inline images rejects them in `validate`.
- Graph transport: send inline images as file attachments marked inline with their content ID. Read the current `fileAttachment` resource documentation for the property names first; they were not verified for this spec.
- Inline images count toward the same size limits as other attachments.
- Sample: an optional `inline/` directory in the campaign; the file name without extension is the content ID. Dry-run writes the images next to the rendered HTML and rewrites `cid:` references to relative paths in the dry-run output only.
- The core library does not rewrite HTML and does not generate content IDs. That is the job of the helper in section 10.4.

### 10.4 Milestone 6, continued: HTML image helper (`morimail-html`)

Added by the owner on 2026-11-22. A convenience for template authors who write ordinary image URLs (`<img src="logo.png">`, `file:`, `https://`) instead of `cid:` references.

- **Module and artifact.** A separate Maven module `morimail-html`, artifact `de.morisoft:morimail-html`, package `de.morisoft.morimail.html`. It depends on `morimail`. The library jar stays unchanged and keeps its Jackson-only allowlist. The helper is not a transport, so it has no `ServiceLoader` entry and adds no extension point.
- **One class.** A final class `HtmlImages`, which is stateless and safe for concurrent use. It has a static method that takes a `MailTemplate` and returns a result record with the rewritten `MailTemplate` and a `List<Attachment>` whose `contentId` is set. The consumer passes both to the builder (`.template(result.template(), data)`, then each image through `.attach(...)`). The checks from section 10.3 then apply as usual.
- **Template, not mail.** The helper works on the template **before** rendering, once per template, not once per mail. Recipient data therefore never reaches it: a placeholder value cannot point it at a local file or a network host. A campaign converts its template once, however many mails it sends. A `src` that contains a placeholder (`$$`) is left unchanged.
- **What is rewritten.** Only `src` attributes of `<img>` elements in the HTML part. Subject and text stay as they are. `cid:` sources and unknown schemes stay as they are. `data:` sources stay as they are unless the caller passes a work directory (see "Storable form").
- **Local images.** A relative path or a `file:` URI is resolved against a base directory that the caller must pass. After normalization the path must lie inside that directory. Otherwise the call fails, as it does for a missing or unreadable file or a non-image content type.
- **Remote images (opt-in).** `http:` and `https:` sources are left unchanged unless the caller opts in by passing a download directory. The caller owns that directory and cleans it up. A download has a connect and request timeout and a size cap, and it must return an image content type. Downloads use the JDK `HttpClient`.
- **Content IDs.** The helper generates them from the file name without its extension, reduced to characters that the section 10.3 rules accept, with a numeric suffix when two names collide. The same source used twice gives one image and one content ID.
- **Storable form.** Added by the owner on 2026-11-23. A second static method turns local (and, with a work directory, remote) image sources into `data:image/...;base64,` URIs, so the HTML alone carries its images and can be stored as one string. When a work directory is passed, the conversion to `cid:` also turns `data:` image sources into inline images: it decodes them into files in that directory, because an `Attachment` holds a path. The same image type check and size cap apply to the decoded bytes. The directory that holds downloads is this work directory.
- **Errors.** Problems with the template or a local file are `InvalidMailException`. How a failed download is categorized is decided in the phase that builds it (section 12). Messages name the `src` value as written in the template, never recipient data. Logging follows section 11: remote URLs only at DEBUG.
- **Dependencies.** JDK and `morimail` only, unless the owner approves an HTML parser (section 12).

## 11. Working rules for Claude Code (copy into `CLAUDE.md`)

### Rule 0: do not overthink, do not overengineer

This rule ranks above the others. When in doubt, build less.

- Write the simplest code that meets this spec and passes its tests. A reader should be able to follow a send from `Mail.using(...)` to the HTTP call in a few minutes.
- The library has two extension points: `TransportProvider` and `TokenProvider`. Add no further interface, SPI, factory, strategy or abstract base class unless two real implementations exist in this repository.
- Prepare for later milestones only where this spec says so (the registry requirements in section 2, the `contentId` field). Write no code, option or hook for anything else that might come later.
- No feature, flag or option this spec does not ask for.
- No frameworks, dependency injection, annotation processing or reflection. Plain constructors, records and static factories.
- No dependency in the library jar beyond Jackson. No vendor SDK for something that is a REST call. A transport that needs more is an external jar. Ask before adding any dependency.
- Prefer one clear class over several small ones that only forward calls. Prefer a 20-line method over a pattern.
- The library sends one mail. Batching, campaigns, scheduling, retries, queues and configuration files belong to the consumer.
- If a requirement seems to need something elaborate, stop and ask. The spec is more likely unclear than the problem is hard.

### Further rules

- The repository is public from the first commit, and git history is permanent. Never write a secret, tenant ID, real mail address, internal host name or customer name into any file, test fixture, commit message or issue text. Examples use `example.com`, `example.org` and zero GUIDs. Two exceptions: the test keystore from section 10.1, and the maintainer identity from section 13.4 where that section and the POM, `LICENSE` and `SECURITY.md` call for it.
- Commit as `Stephan Wald <stephan@morisoft.de>` (section 13.4).
- Never publish, deploy or tag a release. Releasing to Maven Central is done by the owner.
- Never send real mail. Live tests are run by the owner only.
- Before coding against the token endpoint or a Graph endpoint, read the current Microsoft documentation page for it. Where it differs from this spec, follow the documentation and note the difference in `docs/architecture.md`.
- Public types get Javadoc that states thread safety. `Mailer`, transports and token providers must be safe for concurrent use.
- Do not log tokens, secrets, assertions or full mail bodies. Recipient addresses may be logged at DEBUG only.
- Keep the MVP scope. If something from "out of scope" seems necessary, stop and ask.

## 12. Open decisions

Each has a default so work is not blocked. Raise them at the milestone-4 report.

| Decision | Default |
|---|---|
| Copyright holder in `LICENSE` | Stephan Wald. Assumed from the maintainer identity; confirm before the first commit. |
| Contact in `SECURITY.md` | stephan@morisoft.de. Assumed; confirm, or name a dedicated address. |
| `Co-Authored-By` trailer that Claude Code adds to commit messages | Leave Claude Code's default. The owner decides whether a public history should carry it and can switch it off in the Claude Code settings. |
| HTML plus text both supplied on Graph | Send HTML only in the MVP; MIME format later |
| Attachment limit | Under 3 MB per file and in total; upload session later |
| HTML parser for `morimail-html` (10.4) | None: a JDK regular expression over `<img src>`. A parser such as jsoup needs the owner's explicit approval |
| Failed download in `morimail-html` (10.4) | Decided in the phase that builds it, together with the size cap, the timeouts and the redirect rule |

## 13. Open source and Maven Central

Decided: MIT license, public repository from the first commit, releases on Maven Central.

### 13.1 Repository

- `LICENSE` with the MIT text. No license headers in source files.
- `README.md` states what the library does, that it is pre-1.0 and its API may change, the Maven coordinates, and a quick start.
- `SECURITY.md` asks for vulnerability reports through a private channel and names it. The library handles credentials, so this file is not optional.
- CI: one GitHub Actions workflow that runs `mvn verify` on push and pull request, on Java 17. No secrets in CI; the live test never runs there.
- The README explains the checked-in test keystore in one sentence, so nobody mistakes it for a leaked credential.
- Keep it to these files. No contribution guide, code of conduct, issue templates or badges until there are contributors.

### 13.2 Coordinates and versions

- GroupId `de.morisoft`. Maven Central only accepts a groupId whose namespace the publisher has verified in the Central Portal, by DNS for a domain or through a code-hosting account (Postman n.d.). The owner must control `morisoft.de` and verify it before the first release.
- Repository and SCM URL: `https://github.com/morisoft-lib/morimail`.
- Published artifacts: the parent POM (`de.morisoft:morimail-parent`) and the library (`de.morisoft:morimail`). From 0.2.0 also the helper `de.morisoft:morimail-html` (section 10.4), released with the same version. These coordinates are final; Maven Central does not allow changing them after the first release.
- Later external transports follow the pattern `morimail-<transport>`, for example `morimail-ses`.
- The sample module and the test-jar are not published (`maven.deploy.skip` for the sample).
- Versions: `0.1.0-SNAPSHOT` during the MVP, first release `0.1.0`. Semantic versioning; before 1.0 the API may change between minor versions.
- A release on Central is permanent and cannot be replaced (Apache Maven Project n.d.).

### 13.3 What the POM and build must provide

Maven Central rejects a release that lacks any of these (Sonatype n.d.-b):

- POM metadata: name, description, project URL, license, developer, SCM. Put them in the parent POM so `morimail` inherits them. Developer: name `Stephan Wald`, email `stephan@morisoft.de`.
- A `-sources.jar` and a `-javadoc.jar` for every artifact that is not of packaging `pom`.
- A GPG signature for every file.

Set this up as a Maven profile named `release` that adds the source, Javadoc and GPG plugins and Sonatype's Central publishing plugin. The publishing plugin does not create the sources jar, the Javadoc jar or the signatures itself and does not check the POM metadata (Sonatype n.d.-a), so the profile must configure all of them. Read the two Sonatype pages for the current plugin coordinates and settings before writing the profile.

Javadoc must build without errors, since a failing Javadoc build blocks a release. This is one more reason for the Javadoc rule in section 11.

### 13.4 Identity

- Every git commit has author and committer `Stephan Wald <stephan@morisoft.de>`. Set this in the repository's local git config (`user.name`, `user.email`) before the first commit, and check it with `git log --format='%an <%ae> | %cn <%ce>'` after each milestone. Do not change the global git config.
- The same name and address appear as the developer in the POM (13.3) and so in everything published to Maven Central.
- The GPG key used for signing releases should carry the same identity. Creating it is the owner's task.

### 13.5 Who does what

- Claude Code prepares the POM and the `release` profile and checks that `mvn -Prelease verify -Dgpg.skip` succeeds.
- The owner creates the Central Portal account, verifies the namespace, creates the GPG key and runs the release. Portal token and GPG key live in the owner's Maven settings and never in the repository or in CI for now.

## Bibliography

Apache Maven Project. n.d. "[Guide to Uploading Artifacts to the Central Repository](https://maven.apache.org/repository/guide-central-repository-upload)." Accessed November 21, 2026.


Microsoft. n.d.-a. "[Attach Large Files to Outlook Messages or Events](https://learn.microsoft.com/en-us/graph/outlook-large-attachments)." Microsoft Learn. Accessed November 21, 2026.

Microsoft. n.d.-b. "[Microsoft Identity Platform Application Authentication Certificate Credentials](https://learn.microsoft.com/entra/identity-platform/certificate-credentials)." Microsoft Learn. Accessed November 21, 2026.

Microsoft. n.d.-c. "[Role Based Access Control for Applications in Exchange Online](https://learn.microsoft.com/Exchange/permissions-exo/application-rbac)." Microsoft Learn. Accessed November 21, 2026.

Microsoft. n.d.-d. "[Microsoft Identity Platform and the OAuth 2.0 Client Credentials Flow](https://learn.microsoft.com/en-us/entra/identity-platform/v2-oauth2-client-creds-grant-flow)." Microsoft Learn. Accessed November 21, 2026.

Microsoft. n.d.-e. "[Understanding Application-Only Access](https://learn.microsoft.com/entra/identity-platform/app-only-access-primer)." Microsoft Learn. Accessed November 21, 2026.

Microsoft. n.d.-f. "[user: sendMail](https://learn.microsoft.com/en-us/graph/api/user-sendmail)." Microsoft Graph REST API v1.0 reference, Microsoft Learn. Accessed November 21, 2026.

Microsoft. n.d.-g. "[Exchange Online Limits](https://learn.microsoft.com/en-us/office365/servicedescriptions/exchange-online-service-description/exchange-online-limits)." Exchange Online service description, Microsoft Learn. Accessed November 22, 2026.

Postman. n.d. "[Publish Java SDKs Manually](https://learning.postman.com/docs/sdk-generator/publish/java-manual)." Postman Docs. Accessed November 21, 2026.

Sonatype. n.d.-a. "[Publishing By Using the Maven Plugin](https://central.sonatype.org/publish/publish-portal-maven)." The Central Repository Documentation. Accessed November 21, 2026.

Sonatype. n.d.-b. "[Requirements](https://central.sonatype.org/publish/requirements/)." The Central Repository Documentation. Accessed November 21, 2026.
