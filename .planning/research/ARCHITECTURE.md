# Architecture Research

**Domain:** Small Java 17 mail-sending library (pluggable transports, Microsoft Graph first) plus a CLI sample
**Researched:** 2026-11-21
**Confidence:** HIGH for the structure (it follows the binding spec) and for the Microsoft facts (read from the current Microsoft Learn pages today). MEDIUM for the concurrency details and the Graph size limits (reasoned, or only community-sourced).

The architecture is fixed by `spec.md` §2, §4–§8 and §11 Rule 0. This file does not redesign it. It turns the spec into concrete component boundaries, one traced data flow, the registry mechanics, a decision on the 401 token problem, a concurrency model, a build order, and a list of places where Microsoft's current docs differ from the spec.

---

## Standard Architecture

### System Overview

```
 Consumer code (the CLI sample is one)
 ┌──────────────────────────────────────────────────────────────────────┐
 │ Mail.using(id)...build()          MailAccount.builder(id)...build()  │
 │        │  (Mail, plain record)            │  (resolves transport ID) │
 └────────┼──────────────────────────────────┼──────────────────────────┘
          │                                  │
 ═════════╪══ core: de.morisoft.morimail ════╪═══════ imports only the JDK ═
          ▼                                  ▼
 ┌─────────────────┐   ┌─────────────┐   ┌──────────────────┐
 │ Mail.Builder    │   │ Mailer      │   │ TransportRegistry│  built-in list
 │ TemplateRenderer│   │ id→account  │   │ (package-private)│◄─ BuiltInTransports
 │ records, errors │   │ validate/   │   │ built-in +       │◄─ ServiceLoader
 └─────────────────┘   │ send        │   │ ServiceLoader    │
                       └──────┬──────┘   └──────────────────┘
                              │ MailAccount(id, MailTransport, defaultFrom)
                              ▼
                  «MailTransport»  validate(msg) / send(msg)      ← extension point 1
                  «TransportProvider» id() / create(options, auth) ← extension point 1
                  «TokenProvider» accessToken(scope) (+1 default)  ← extension point 2
 ═══════════════════════════════╪═════════════════════════════════════════
          ┌─────────────────────┴───────────────┐
          ▼                                     ▼
 ┌──────────────────────────┐      ┌───────────────────────────────┐
 │ graph: GraphTransport    │      │ entra: EntraClientCredentials │
 │ + GraphTransportProvider │      │ implements TokenProvider      │
 │ JSON build, HTTP, error  │      │ assertion, token POST, cache  │
 │ mapping, one 401 retry   │      │ single-flight                 │
 └─────────────┬────────────┘      └───────────────┬───────────────┘
               │  (no import between graph and entra)│
               ▼                                     ▼
   graph.microsoft.com/v1.0/users/{from}/sendMail   login.microsoftonline.com/{tenant}/oauth2/v2.0/token
```

The consumer wires the two together: `EntraClientCredentials` is passed to `MailAccount.builder(..).auth(..)`, and the registry hands it to `GraphTransportProvider.create(options, auth)`. This is the only link between the two packages, and it goes through the core interface `TokenProvider`.

### Component Responsibilities

| Component | Package | Responsibility | Notes |
|-----------|---------|----------------|-------|
| `MailAddress`, `Attachment`, `MailMessage`, `Mail` | core | Plain immutable records, no behaviour | Compact constructors copy lists (`List.copyOf`) so that records are really immutable, which thread safety and queue serialization both rely on |
| Error types (`MailException` tree) | core | The three categories plus `InvalidMailException` and `SenderRejectedException` | Checked. `TransientMailException` carries `Optional<Duration> retryAfter` |
| `Mail.Builder` (returned by `Mail.using`) | core | Collects fields, renders the template at `build()`, runs the transport-independent checks | Only `build()` throws `InvalidMailException`. It does not know the account, so `from` can still be null |
| `MailTemplate`, `TemplateRenderer` | core | `$$name$$` substitution into subject, HTML and text | Stateless, one precompiled `Pattern`. One final class, no interface |
| `MailTransport`, `TransportProvider`, `TokenProvider` | core | The only two extension points (provider plus transport count as one SPI) | `TokenProvider` gains one default method, see "401 handling" |
| `MailAccount` (+ builder) | core | Immutable: id, `MailTransport`, `defaultFrom`. `build()` looks up the provider and calls `create` | Unknown transport ID fails here |
| `TransportRegistry` | core, package-private | Merge built-in list and `ServiceLoader` into one map, find by ID | Built fresh per `MailAccount.build()`, no static state |
| `BuiltInTransports` | core | The explicit list. Holds the **only** core-to-other-package import (`graph.GraphTransportProvider`) | One line per built-in transport |
| `Mailer` | core | Immutable map ID to account. `validate(mail)`, `send(mail)` | Applies `defaultFrom`, runs `validate` before `send` |
| `GraphTransport`, `GraphTransportProvider` | graph | Mail message to Graph JSON, the HTTP call, error mapping, the one 401 retry, limits in `validate` | Imports core, Jackson, JDK |
| `EntraClientCredentials` | entra | Secret or certificate flow, hand-built PS256 JWT, token cache, single-flight | Imports core (`TokenProvider`, `MailException`), Jackson, JDK |
| `RecordingTransport` + provider | library **test** sources, test-jar | Stores messages in memory, registered by `META-INF/services` as `recording` | Never in the main jar |
| Serial-mail CLI | `morimail-sample-serial` | Picocli command, account file, campaign loader, pre-flight, send loop, journal | A consumer. It owns all batch, retry, rate and journal logic |

---

## Recommended Project Structure

```
pom.xml                                   morimail-parent (Central metadata, dependencyManagement, release profile)
morimail/
  src/main/java/de/morisoft/morimail/
    MailAddress.java  Attachment.java  MailMessage.java  Mail.java      records (Mail holds the Builder as nested class)
    MailException.java TransientMailException.java PermanentMailException.java
    InvalidMailException.java SenderRejectedException.java AuthRequiredException.java
    MailTemplate.java TemplateRenderer.java
    MailTransport.java TransportProvider.java TokenProvider.java
    MailAccount.java Mailer.java
    TransportRegistry.java BuiltInTransports.java                        package-private
  src/main/java/de/morisoft/morimail/graph/
    GraphTransportProvider.java GraphTransport.java                      no other classes unless a method outgrows ~40 lines
  src/main/java/de/morisoft/morimail/entra/
    EntraClientCredentials.java                                          one class; JWT building as private methods
  src/test/java/...                                                      unit tests, WireMock tests, import-rule test
  src/test/java/de/morisoft/morimail/test/RecordingTransport.java        + RecordingTransportProvider (same file or sibling)
  src/test/resources/META-INF/services/de.morisoft.morimail.TransportProvider   → recording provider
  src/test/resources/morimail-test-only.p12                              throwaway keystore, password "test"
morimail-sample-serial/
  src/main/java/de/morisoft/morimail/sample/
    SerialMail.java        picocli entry point, builds the Mailer from account.properties, calls Runner
    AccountFile.java       properties file to Mailer (hard-wires "graph" + Entra, secrets only from env vars)
    Campaign.java          reads subject/body/csv/attachments
    Csv.java               tiny RFC 4180-style reader (no dependency)
    PreFlight.java         all-problems-at-once check, returns a list
    Journal.java           JSONL append + flush, loads accepted keys
    Runner.java            loop: skip, render, build, send, retry, rate, stop rules, counts
  example-campaign/
docs/ spec.md entra-setup.md architecture.md
```

### Structure Rationale

- **Records and errors in the core root package:** every other piece depends on them, so they come first and change last.
- **`TransportRegistry` and `BuiltInTransports` package-private:** the consumer never touches the registry. This keeps the public API to the spec's signatures plus the account builder.
- **`Runner` takes a `Mailer`, not account properties:** the sample's tests then build a `Mailer` over `recording` directly, with no extra `transport=` property in `account.properties` (the spec lists none). `AccountFile` is the only code that knows about Graph and Entra.
- **`Csv` hand-written:** the sample may only add picocli. A CSV parser for a header row, quoted fields and embedded commas is about 40 lines. Do not add a CSV dependency without asking.
- **Jackson appears only in `graph`, `entra` and the sample journal.** The core uses no Jackson at all, which makes "core imports only the JDK" literally true.

---

## Architectural Patterns

### Pattern 1: One registry from two sources (built-in list plus ServiceLoader)

**What:** `TransportRegistry.load()` builds an immutable map ID to `TransportProvider` from `BuiltInTransports.providers()` and `ServiceLoader.load(TransportProvider.class)`. Both paths yield the same type. Built-ins go in first, so an external duplicate is detected as a collision rather than silently overriding.

**When to use:** every `MailAccount.build()`. Do not cache in a static field.

**Trade-offs:** a `ServiceLoader` scan per account build costs about a millisecond and accounts are built a handful of times per process. In return there is no static state, no class-init failure mode, and tests can construct registries from explicit lists.

```java
final class TransportRegistry {
    private final Map<String, TransportProvider> byId;           // TreeMap: sorted, so error text is stable

    static TransportRegistry load() {
        try {
            return of(BuiltInTransports.providers(), ServiceLoader.load(TransportProvider.class));
        } catch (ServiceConfigurationError e) {                   // an Error, not an Exception
            throw new IllegalStateException("Cannot load a transport provider: " + e.getMessage(), e);
        }
    }

    // package-private so tests can pass explicit lists (see test note below)
    static TransportRegistry of(List<TransportProvider> builtIn, Iterable<TransportProvider> external) {
        Map<String, TransportProvider> map = new TreeMap<>();
        for (TransportProvider p : builtIn) {
            if (map.put(p.id(), p) != null) throw new IllegalStateException("Two built-in transports use id '" + p.id() + "'");
        }
        for (TransportProvider p : external) {
            TransportProvider prev = map.putIfAbsent(p.id(), p);
            if (prev != null) throw new IllegalStateException("Transport id '" + p.id() + "' from "
                    + p.getClass().getName() + " is already taken by " + prev.getClass().getName()
                    + (builtIn.contains(prev) ? " (built-in; external transports cannot override it)" : ""));
        }
        return new TransportRegistry(map);
    }

    TransportProvider find(String id) {
        TransportProvider p = byId.get(id);
        if (p == null) throw new IllegalArgumentException(
                "Unknown transport '" + id + "'. Available: " + String.join(", ", byId.keySet()));
        return p;
    }
}
```

```java
final class BuiltInTransports {          // the only class in core that imports another package
    static List<TransportProvider> providers() {
        return List.of(new de.morisoft.morimail.graph.GraphTransportProvider());
    }
}
```

Decisions this pattern makes (the spec is silent, record them in `docs/architecture.md`):

- Unknown ID throws unchecked `IllegalArgumentException` and duplicate ID throws unchecked `IllegalStateException`. Both are configuration or deployment errors found at account build time. `MailException` is for per-mail outcomes.
- Two **external** providers sharing an ID also fail. The spec only names the built-in case, but silent "first wins" depends on classpath order.
- Message text lists the IDs sorted, for example `Available: graph, recording`.

**Test notes (these cause real confusion):**

- The "external duplicate fails" test must call `TransportRegistry.of(List.of(graph), List.of(fakeGraph))`. It cannot use a `META-INF/services` file in test resources. That file applies to every test in the module and would make `load()` fail everywhere.
- The "external transport found through ServiceLoader" test is the `recording` provider, which is already on the test classpath.
- Because `recording` is always on the test classpath, the unknown-ID test must assert that the message `contains("graph")`, not that it equals the whole list.
- The "built-in graph resolves with no ServiceLoader entry" test needs `BuiltInTransports` to contain Graph, which only happens in the Graph phase (see build order).

### Pattern 2: Provider creates an immutable transport; the transport owns its own options

**What:** `GraphTransportProvider.create(Map<String,Object> options, TokenProvider auth)` validates its own options eagerly and returns an immutable `GraphTransport`. Failures here surface at `MailAccount.build()`.

**Rules for Graph:** `auth == null` throws `IllegalArgumentException("graph needs a TokenProvider")`. The recognised options are `saveToSentItems` (must be a `Boolean`, default true) and, see the open decision, `baseUrl` (default `https://graph.microsoft.com/v1.0`). Any other key throws `IllegalArgumentException` so typos are caught. The core never looks inside the map.

**Trade-offs:** rejecting unknown keys is 3 lines and prevents the classic silent `saveToSentItem=false` typo. It is the only validation the options need.

### Pattern 3: The transport does not know how the token was obtained

**What:** `GraphTransport` holds a `TokenProvider` and a scope constant `https://graph.microsoft.com/.default`. It calls `auth.accessToken(scope)` per send. Caching is the provider's job. The transport has no token state.

**When to use:** always. This is what lets a later SMTP transport (XOAUTH2) reuse `EntraClientCredentials`, and a delegated flow reuse the Graph transport.

### Pattern 4: JSON through Jackson's tree model, not POJOs

**What:** `GraphTransport` builds the request with `ObjectMapper.createObjectNode()` (or `Map`/`List`) and writes it with `writeValueAsBytes`. No annotations, no DTO classes, no custom serializers. The shared `ObjectMapper` is a `static final`, which is thread-safe after configuration.

**Why:** the payload is about 30 lines of node construction. DTO classes would be the "several small classes that only forward calls" that Rule 0 forbids. Empty lists and null fields are omitted by simply not adding them.

### Pattern 5: Error mapping as one switch, kept data-like so the live test can edit it

**What:** one private method `mapFailure(int status, HttpHeaders headers, String body)` returning a `MailException`, holding the §6.3 table. The error body is parsed only for `error.code` and `error.message`. Put both in the exception message (truncated), never log the body.

**Why:** the spec says the table is a starting point to be corrected after the owner's live test. One method with one `switch` keeps that edit to a few lines.

---

## Data Flow

### One send, from `Mail.using(...)` to the HTTP call

```
Consumer
  1  Mail.using("entra-main").to("a@example.org").template(t, data).attach(path).build()
       Builder stores template+data; build() renders subject/html/text (TemplateRenderer),
       checks: ≥1 recipient, subject non-blank, html or text present, files exist+readable,
       contentId == null.  Throws InvalidMailException.  Result: Mail(accountId, MailMessage(from=null,...))
  2  mailer.send(mail)
       a  account = accounts.get(mail.accountId())      none → PermanentMailException ("unknown account")
       b  msg = from==null ? copy with account.defaultFrom : as is        still null → InvalidMailException
       c  account.transport().validate(msg)             Graph: sizes via Files.size() (never reads content)
       d  account.transport().send(msg)
  3  GraphTransport.send(msg)
       a  json = build JSON from msg;  files read + Base64 here (≤3 MB each, by validate)
       b  token = auth.accessToken("https://graph.microsoft.com/.default")
                    └─ EntraClientCredentials: cached? return.  Else single-flight POST to token endpoint
       c  POST {baseUrl}/users/{urlEncoded(from.email)}/sendMail
            Authorization: Bearer <token>; Content-Type: application/json
       d  status 202                      → return (accepted, not delivered)
          status 401, first time          → token = auth.freshAccessToken(scope, token); repeat c once
          status 401 again / other        → throw mapFailure(...)
          IOException / timeout           → TransientMailException
          InterruptedException            → re-set interrupt flag, TransientMailException
```

- **Direction of dependency at runtime:** consumer to `Mailer` to `MailTransport` (interface) to `TokenProvider` (interface). Nothing flows back except exceptions.
- **Where `defaultFrom` is resolved:** only in `Mailer`, because `Mail.using(accountId)` has no account reference. `Mailer.validate(mail)` and `Mailer.send(mail)` share one private `prepare(mail)` that does steps a to c, so they cannot disagree.
- **Path variable encoding:** the `from` address goes into the URL path. Percent-encode it (`URLEncoder` turns spaces into `+`, which is wrong in a path, so encode `+` as `%20` or build the URI from components). Addresses containing `+` (plus-addressing) are legal and common.
- **Files are read at send time, not at `build()`:** `build()` only proves existence and readability. A file that vanishes or grows in between becomes an `InvalidMailException` from the transport (re-check the byte length after reading, since `validate` used `Files.size()`).

### Graph request body (verified against Microsoft Learn, see the verification section)

```json
{
  "message": {
    "subject": "Welcome",
    "body": { "contentType": "HTML", "content": "<p>Hi</p>" },
    "toRecipients":  [ { "emailAddress": { "address": "a@example.org", "name": "A" } } ],
    "ccRecipients":  [ ... ],  "bccRecipients": [ ... ],  "replyTo": [ ... ],
    "attachments": [ {
      "@odata.type": "#microsoft.graph.fileAttachment",
      "name": "agenda.pdf", "contentType": "application/pdf", "contentBytes": "<base64>" } ]
  },
  "saveToSentItems": false
}
```

Omit empty arrays, null names and `attachments` when none. Omit `saveToSentItems` unless it is false. Text-only mail sends `"contentType": "Text"`. **Omit `message.from`** in the MVP: the URL already selects the mailbox, and Microsoft says a `from` value "must correspond to the actual mailbox used", so a `from` that differs by alias or case from the real mailbox is one more way to get a rejection. The consequence is that `MailAddress.displayName` of the sender is not transmitted. Check at the live test what Exchange shows as the sender name and add `from` only if needed.

### Token flow (Entra)

```
accessToken(scope):
  t = cached                                         (volatile read, no lock)
  if t != null && scope.equals(t.scope) && now < t.refreshAt  → return t.value
  lock.lock()                                        (ReentrantLock, see concurrency)
    re-read cached; if now fresh → return it         (another thread refreshed while we waited)
    build form (secret, or new assertion with fresh jti/nbf/iat/exp)
    POST authority/{tenant}/oauth2/v2.0/token        (connect 10 s, request 30 s timeouts)
    200 → cached = new Token(value, scope, now + max(expires_in − 300, 0) s)
    4xx → AuthRequiredException("error: error_description")
    429, 5xx, IOException → TransientMailException (retryAfter from header when present)
  unlock
```

### Registry and account construction flow

```
MailAccount.builder("entra-main").transport("graph").auth(a).option(..).defaultFrom(..).build()
   → TransportRegistry.load()                         built-ins + ServiceLoader, duplicate check
   → registry.find("graph")                           unknown → IllegalArgumentException(+available IDs)
   → provider.create(Map.copyOf(options), auth)       option errors → IllegalArgumentException
   → new MailAccount(id, transport, defaultFrom)
Mailer.of(account...)                                  duplicate account IDs → IllegalArgumentException
```

---

## The 401 problem: dropping the cached token without widening `TokenProvider`

**Requirement (spec §6.3 and §7.3):** on a 401 the Graph transport asks for a new token once and repeats the call once. The cache lives in the Entra class. `graph` and `entra` must not import each other. `TokenProvider` should not be widened "if a simpler way exists". Rule 0 forbids a further interface unless two real implementations exist in the repo.

| Option | Verdict | Reason |
|--------|---------|--------|
| A. `instanceof EntraClientCredentials` in Graph | Rejected | Breaks the import rule between `graph` and `entra` |
| B. New core interface (`InvalidatableTokenProvider`) plus `instanceof` in Graph | Rejected | A third interface with exactly one implementation, which Rule 0 forbids, plus a type-test branch |
| C. Reflection or method-name lookup | Rejected | Banned by Rule 0 |
| D. Do nothing: Graph calls `accessToken` again; the consumer may call `EntraClientCredentials.invalidate()` itself | Fallback only | Needs no interface change, but the retry is then a no-op for a caching provider (same cached token, same 401). The §6.3 retry would exist on paper only |
| **E. One default method on `TokenProvider`** | **Recommended** | No new interface, no import, no `instanceof`. Stays source and binary compatible for lambdas and stubs. Providers that do not cache need no override |

**Recommendation (E):**

```java
@FunctionalInterface
public interface TokenProvider {
    String accessToken(String scope) throws MailException;

    /**
     * Called by a transport after the service rejected {@code rejectedToken} (HTTP 401).
     * A caching provider drops its cached token if it is still {@code rejectedToken} and returns a new one.
     * The default is for providers that do not cache.
     */
    default String freshAccessToken(String scope, String rejectedToken) throws MailException {
        return accessToken(scope);
    }
}
```

`@FunctionalInterface` still holds (one abstract method), so `scope -> "stub"` lambdas in the WireMock tests keep working. `EntraClientCredentials` overrides it: under its lock, `if (cached != null && cached.value.equals(rejectedToken)) cached = null;` then `return accessToken(scope)`.

**Why pass the rejected token:** it makes invalidation compare-and-drop. If ten threads all get a 401 on the same stale token, the first one drops it and fetches a new one. The other nine find a different cached token, see it is not the rejected one, and reuse it. Without the parameter, each of the nine would drop the fresh token its neighbour just fetched, and the single-flight guarantee would degrade to ten sequential token requests. The parameter costs one argument and one string comparison.

**This is a deviation from the letter of spec §7.3** (a default method is still an addition to the interface). The judgement is that no simpler way exists inside the import and Rule 0 constraints (A, B, C are each worse). Record it in `docs/architecture.md` per CLAUDE.md, and raise it at the checkpoint. If the owner wants zero change to `TokenProvider`, take option D and drop the retry from the Graph transport, mapping the first 401 directly.

**Do the contract change in the core phase, not the Graph phase.** It costs nothing there, and the sample's `recording` tests then run against the final `TokenProvider`.

---

## Concurrency Model

| Type | Thread safety | How |
|------|---------------|-----|
| Records, `MailTemplate` | Immutable | Compact constructors `List.copyOf`; `Attachment.file` is a `Path` (immutable) |
| `TemplateRenderer` | Stateless | Static `Pattern` only |
| `Mail.Builder` | **Not** thread-safe | Document it in the Javadoc. One builder per mail |
| `MailAccount`, `Mailer` | Immutable | `Mailer` holds `Map.copyOf(...)`. No setters, no lazy init |
| `GraphTransport` | Immutable fields | Shared `HttpClient` (thread-safe), shared `ObjectMapper`, `String baseUrl`, `boolean saveToSentItems`. The "HTML and text both given" INFO log uses a per-instance `AtomicBoolean` so it appears once per transport, not once per mail |
| `EntraClientCredentials` | Thread-safe | `volatile Token cached` for the lock-free fast path; a `ReentrantLock` for the refresh |
| `RecordingTransport` | Thread-safe | `CopyOnWriteArrayList` of messages |

**Single-flight token refresh:** double-checked pattern: volatile read, then lock, then re-read, then fetch. At most one token request per instance is in flight, and waiting threads reuse the result.

**Use `ReentrantLock`, not `synchronized`.** The refresh does blocking network I/O while holding the lock. On Java 21 virtual threads, `synchronized` around blocking I/O pins the carrier thread (fixed only in Java 24). The library targets 17 but consumers may run it on 21. The cost of `ReentrantLock` is the same few lines.

**Timeouts are part of the concurrency design.** A lock held across a request with no timeout can hang every sender on the account. Set `HttpClient.connectTimeout(10 s)` and a per-request timeout (30 s token, 60 s sendMail). The spec's "network or timeout error means `TransientMailException`" depends on timeouts existing. `HttpTimeoutException` is an `IOException`, so one catch covers it.

**No clock injection needed for the cache tests:** reuse is tested with `expires_in: 3600`. Expiry is tested with a short `expires_in` (for example 100 s, below the 300 s margin, so every call refetches). A `Clock` parameter would be an option the spec does not ask for.

**Margin edge case:** `expires_in − 300` can be negative. Clamp at 0, which means "refetch every time" and is correct for a tiny lifetime.

**Rate limiting is not here.** Pacing, concurrency limits and retries belong to the consumer. A multi-threaded consumer can share one `Mailer` freely. The sample is single-threaded.

---

## Scaling Considerations

The library sends one mail per call. "Scale" here means concurrent callers sharing one `Mailer`.

| Scale | Architecture Adjustments |
|-------|--------------------------|
| One sender thread (the sample) | Nothing special. One token fetch per about hour, one HTTP call per mail |
| Tens of worker threads (a queue consumer) | One shared `Mailer` and one `EntraClientCredentials` per account. Single-flight avoids a token stampede. `HttpClient` multiplexes. The mailbox's Exchange limits, not the library, are the ceiling (429 gives `TransientMailException` with `retryAfter`) |
| Many accounts / tenants | One `MailAccount` per credential. Registry load per account build is cheap. No shared mutable state between accounts |

### Scaling Priorities

1. **First bottleneck:** Exchange Online per-mailbox sending limits, surfaced as 429 and `retryAfter`. Handled by the consumer. The default of 20 mails per minute in the sample is a guess to verify after the Graph phase.
2. **Second bottleneck:** memory per send. A 3 MB attachment becomes about 4 MB of base64 text inside a JSON byte array, so peak is roughly 10 MB per in-flight send. This is fine for tens of threads and is the reason not to lift the 3 MB limit without streaming.

---

## Anti-Patterns

### Anti-Pattern 1: A static singleton registry or `ServiceLoader` in a static initializer

**What people do:** `static final TransportRegistry INSTANCE = load();`
**Why it's wrong:** a duplicate-ID error becomes `ExceptionInInitializerError`, then `NoClassDefFoundError` on every later use. The failure text is lost and tests cannot vary the registry.
**Do this instead:** build the registry per account build and throw a plain `IllegalStateException`.

### Anti-Pattern 2: Graph facts in the core

**What people do:** a `MAX_ATTACHMENT_BYTES = 3 MB` constant in `Mail.Builder`, an `html`-only assumption in `MailMessage`, a `saveToSentItems` field on `MailAccount`.
**Why it's wrong:** a later MIME-building SMTP transport must implement the same interface from the same message.
**Do this instead:** limits in `GraphTransport.validate`, options in the opaque map.

### Anti-Pattern 3: `instanceof`, reflection or a third interface to reach into the token cache

See the 401 section. Use the default method.

### Anti-Pattern 4: Reading attachment content in `validate`

**Why it's wrong:** `validate` runs for the pre-flight check and again in `send`. It would read every file twice, and for a campaign it runs against the first row only.
**Do this instead:** `Files.size()` in `validate`. Read and base64 only in `send`.

### Anti-Pattern 5: Logging or embedding bodies, tokens or assertions

**Why it's wrong:** the repo is public and mailers handle credentials. A Graph error body may echo parts of the request.
**Do this instead:** put only `error.code` and a truncated `error.message` into exception messages. Recipients at DEBUG only. Never log the `Authorization` header or the assertion.

### Anti-Pattern 6: Putting `RecordingTransport` in the main jar

**Why it's wrong:** production classpaths would then see a `recording` transport that silently swallows mail.
**Do this instead:** library test sources plus test-jar, consumed by the sample with `<scope>test</scope>` only.

### Anti-Pattern 7: Account-specific retry inside the transport

The spec allows exactly one retry (the 401 retry with a new token). Do not add retry for 429, 503 or 504. Those are `TransientMailException`, and the caller decides.

---

## Integration Points

### External Services

| Service | Integration Pattern | Notes |
|---------|---------------------|-------|
| Microsoft Graph `POST /users/{id\|upn}/sendMail` | `java.net.http.HttpClient`, JSON, Bearer token | 202 with empty body means accepted only. Application permission `Mail.Send`. Exchange enforces which mailboxes the app may use, so there is no local check |
| Entra token endpoint `POST /{tenant}/oauth2/v2.0/token` | `HttpClient`, `application/x-www-form-urlencoded` | Authority host is a constructor parameter (tests point it at WireMock). Never read the access token's contents |
| Exchange Online (server side) | Indirect | Per-mailbox sending limits and the RBAC-for-Applications scope surface as 429 and 403. Permission changes can take 30 min to 2 h, so a 403 right after setup is expected |
| Maven Central / GPG | Build only (`release` profile) | The owner publishes. Never tag or deploy |

### Internal Boundaries

| Boundary | Allowed | Forbidden |
|----------|---------|-----------|
| core | JDK, plus the single import in `BuiltInTransports` | Jackson, `graph`/`entra` anywhere else, any HTTP |
| graph | core, Jackson, JDK | `entra`, Entra types in signatures or messages |
| entra | core (`TokenProvider`, `MailException`), Jackson, JDK | `graph`, any mail-sending code |
| sample | core, `entra` (to build the provider), picocli, Jackson | Reaching into `graph` internals. It uses `"graph"` by ID |
| tests | everything | `RecordingTransport` registered anywhere except test resources |

**Enforce the import rule with a plain JUnit test, not ArchUnit** (ArchUnit would be a new dependency, which needs owner approval). The test walks `src/main/java`, reads each file's `import` lines, and asserts:

- core files import only `java.*` (and `javax.*` JDK packages), plus exactly one `de.morisoft.morimail.graph.*` import, and it is in `BuiltInTransports`
- `graph` files do not import `...entra`
- `entra` files do not import `...graph`

It is about 25 lines and turns a prose rule into a build failure.

---

## Suggested Build Order

Dependencies drive the order. The spec fixes two constraints: the sample CLI runs against `RecordingTransport` **before** Entra and Graph, and work **stops after Entra and Graph** for the owner's live test.

```
Foundation ──► Core ──► Sample (recording) ──► [Entra ∥ Graph] ──► built-in wiring + E2E ──► STOP: owner live test ──► Docs/release
                │                                 both depend only on core contracts
                └─ contracts frozen here, including TokenProvider.freshAccessToken
```

| Step | Deliverable | Depends on | Why here |
|------|-------------|------------|----------|
| 1. Foundation | Parent POM, wrapper (see gitignore note), `.gitignore` fix, CI, `CLAUDE.md`, `docs/spec.md` move, `LICENSE`/`SECURITY.md`/README stub, **records and error types** | Nothing | Everything depends on the records and errors. Repo hygiene first because the repo is public from the first commit |
| 2. Core | `TemplateRenderer`/`MailTemplate`, `Mail.Builder`, the three interfaces **including `freshAccessToken`**, `MailAccount`, `TransportRegistry`, `BuiltInTransports` with an **empty list**, `Mailer`, `RecordingTransport` + test-jar + service file, import-rule test | 1 | `BuiltInTransports` is empty here so core compiles with no `graph` package. The Graph line is added in step 5 |
| 3. Sample CLI | Picocli command, account file, campaign loader, CSV reader, pre-flight (exit 2), journal and resume, rate limit, transient retry x3, stop on sender/auth errors (exit 3), dry-run. Tests via `Runner` over `recording` | 2 | Spec §10.2 order. Exercises the contracts (`validate`, error categories, defaults) before a real transport depends on them. Contract mistakes found here cost one line, not a Graph rewrite |
| 4a. Entra | JWT assertion as a pure function (test: decode, compare header and claims, verify signature with the cert's public key), then the token client and cache, error mapping, PKCS12 loader | 2 (only `TokenProvider`, `MailException`) | Independent of Graph. Test against a WireMock token endpoint |
| 4b. Graph | `MailMessage` to JSON as a pure function (test without HTTP), then HTTP, error table, 401 retry, `validate` limits, `GraphTransportProvider` | 2 (only `MailTransport`, `TransportProvider`, and a **stub** `TokenProvider` lambda) | Independent of Entra. 4a and 4b can be built in either order or in parallel |
| 5. Wiring and E2E | Add the one Graph line to `BuiltInTransports`; registry tests that need Graph (resolves with no ServiceLoader entry, duplicate rejected); library-level E2E test: real `EntraClientCredentials` (authority at WireMock) plus real Graph transport (baseUrl at WireMock) through the registry; the `live`-tagged test and `-Plive` profile; `AccountFile` finished | 3, 4a, 4b | Joins everything. The only step that touches core and graph together |
| **STOP** | Report to the owner. Owner runs the live test | 5 | Spec milestone 4. Real responses correct the §6.3 table, the rate default, and the `from` and display-name question |
| 6. Docs and release | `docs/entra-setup.md`, `docs/architecture.md`, README quick start, `release` profile | Owner feedback | Written after the live test so the error table, limits and RBAC notes describe what really happened |

**Gitignore trap (step 1):** `.gitignore` ignores `*.jar`, which would exclude `.mvn/wrapper/maven-wrapper.jar`. Add `!.mvn/wrapper/maven-wrapper.jar` or use the wrapper's jar-less distribution. Do this before the first wrapper commit.

**Maven wiring (step 2):** the library needs `maven-jar-plugin` `test-jar` goal. The sample depends on `morimail` and on `morimail` with `<type>test-jar</type><scope>test</scope>`. Reactor order is library then sample. The sample sets `maven.deploy.skip`.

**Phase-granularity hint for the roadmapper:** steps 1 and 2 are one coherent core phase if kept small, step 3 is its own phase (CLI plus tests), steps 4a, 4b and 5 are the Entra/Graph phase. The checkpoint is a phase boundary, not a task.

---

## Verification Against Current Microsoft Documentation

Fetched 2026-11-21 from Microsoft Learn. Confidence HIGH for everything quoted from these pages.

### Graph `user: sendMail` (v1.0, page updated 2026-06)

| Spec statement | Docs say | Verdict |
|----------------|----------|---------|
| `POST /users/{id\|userPrincipalName}/sendMail`, JSON | Same. `Content-Type: application/json` for JSON, `text/plain` for MIME. `Authorization: Bearer` required | Confirmed |
| 202 Accepted, empty body | Same, and "does not indicate that the request processing has completed" | Confirmed |
| Application permission `Mail.Send` | Least privileged: `Mail.Send` (application, delegated). No higher-privileged alternative listed | Confirmed |
| §6.1: "assumes the JSON format carries one body; confirm" | `message.body` is a single `itemBody {contentType, content}`. `contentType` is `text` or `html`. No alternative-parts mechanism in JSON. MIME needs a base64 body with `Content-Type: text/plain` | **Confirmed.** HTML-only (or text-only) is the only JSON option. The "log once at INFO when both given" behaviour is right |
| `saveToSentItems` default true, send only when false | "Specify it only if the parameter is false; default is true" | Confirmed. Note: Microsoft's own JSON example writes `"saveToSentItems": "false"` (a string), while its SDKs send a real boolean. Send the JSON boolean `false` |
| File attachment in the same call | Example 3 shows `@odata.type: "#microsoft.graph.fileAttachment"`, `name`, `contentType`, `contentBytes` | Confirmed. `name` and `contentBytes` are the required properties. `contentType` is optional but include it |
| 3 MB per file, 3 MB total | Docs: "if the file size is under 3 MB, do a single POST on the attachments property" and 3–150 MB needs an upload session. **The sendMail page itself states no size cap**, and nothing about a total | **Difference, minor.** "Under 3 MB per file" is documented (for the attachments endpoint). The **total** limit is the spec's own conservative rule. Keep it, but treat it as unconfirmed against `sendMail` |
| (not in spec) JSON size after base64 | Base64 adds one third. Only community sources, no official page, mention a roughly 4 MB request-size ceiling on JSON string payloads. **LOW confidence** | Under 3 MB of raw attachments is about 4 MB of JSON before the HTML body. Have the owner try about 2.9 MB total at the live test and record the real error code (the exact response for an oversize request is unconfirmed, so keep `400`/`413` both mapped to `PermanentMailException` as the spec has) |
| `contentType` casing `HTML` / `Text` | Examples use `"HTML"` and `"Text"`. The resource page lists `text`/`html` | Use the examples' casing. Both are accepted by Graph in practice (the SDKs differ, PHP sends lowercase) |
| (not in spec) recipients cap | Message resource: max **500** total recipients in to, cc and bcc from an Exchange Online mailbox | New fact. Not a core limit (Graph-specific), so it belongs in `GraphTransport.validate` if wanted. Rule 0 says optional: server returns an error anyway |
| `from` | "The value must correspond to the actual mailbox used" | See the recommendation to omit `message.from` |
| (not in spec) `fileAttachment` for milestone 6 | Properties: `isInline` (Boolean) and `contentId` ("The ID of the attachment in the Exchange store"), `contentLocation` "not supported". The `contentId` description is ambiguous | Verify with a real inline send in milestone 6. Not needed now |

### Entra certificate credentials (page dated 2025-01, updated 2026-06)

| Spec §7.2 statement | Docs say | Verdict |
|---------------------|----------|---------|
| Header `alg` PS256, `typ` JWT, `x5t#S256` | "alg should be PS256; typ should be JWT; x5t#S256: Base64url-encoded SHA-256 thumbprint of the X.509 certificate's DER encoding" | Confirmed, no difference |
| `aud` = tenant token endpoint | `https://login.microsoftonline.com/{tenantId}/oauth2/v2.0/token` | Confirmed. Use the **v2.0** URL. Do not copy Microsoft's example JSON, which prints `"https: //login..."` with a stray space, nor the older encoded sample, whose `aud` is the v1 `/oauth2/token` URL |
| `iss` and `sub` = client ID; `jti` = new GUID | Same. `iss` "Use the GUID application ID", `sub` "Use the same value as iss" | Confirmed. `UUID.randomUUID().toString()` |
| `nbf`, `iat` = now; `exp` at most 10 min | `nbf`: "Using the current time is appropriate". `exp`: "keep it short, 5–10 minutes after nbf at most. Entra doesn't place restrictions on exp currently" | Consistent. Use **5 minutes**, inside the recommendation and not tied to the 10-minute edge |
| Signature RSA-PSS | "Use PSS padding." The page gives no salt length | Spec's `RSASSA-PSS`, SHA-256, MGF1-SHA-256, salt 32 matches RFC 7518 §3.5 for PS256. Confirmed by the signature-verification test, which is the real check |
| Form fields `client_assertion_type`, `client_assertion` | Same, fixed type `urn:ietf:params:oauth:client-assertion-type:jwt-bearer` | Confirmed |

**A stale copy is still on a neighbouring page.** The client-credentials page's "second case" example `client_assertion` is a different, older sample. It decodes to `{"alg":"RS256","x5t":"gx8tGysyjcRqKjFPnd7RFwvwZI0"}`. That is the RS256 plus SHA-1 `x5t` form the spec warns about. Ignore it. Follow the certificate-credentials page only.

### Entra client-credentials token endpoint (page updated 2026-01)

| Spec §7.1 and §7.3 statement | Docs say | Verdict |
|------------------------------|----------|---------|
| Form fields for secret flow | `client_id`, `scope`, `client_secret`, `grant_type=client_credentials`. The secret "must be URL-encoded". Basic auth is also supported | Confirmed. Use a proper form encoder (`URLEncoder.encode(..., UTF_8)` per value) |
| Scope `https://graph.microsoft.com/.default` | Same. All scopes must be for a single resource | Confirmed |
| Success: `access_token`, `token_type`, `expires_in` | Same three fields in the documented example (`token_type: Bearer`, `expires_in: 3599`) | Confirmed. Real responses also carry `ext_expires_in`. Ignore it. Read `expires_in` with `JsonNode.asLong()`, which accepts both a number and a numeric string, since older endpoints returned a string |
| "Do not parse the access token" | Warning: "Don't attempt to validate or read tokens for any API you don't own" | Confirmed |
| Errors: HTTP 400 with `error`, `error_description` | Documented as 400 with `error`, `error_description`, `error_codes`, `timestamp`, `trace_id`, `correlation_id` | Confirmed, with extra fields. Put `error` and `error_description` in the message. `error_description` contains an AADSTS code and trace and correlation IDs, which are useful for the admin and safe to include. In practice `invalid_client` (bad secret) comes back as **401**, which the spec's "map 4xx to `AuthRequiredException`" already covers |

### Summary of differences to record in `docs/architecture.md`

1. `TokenProvider` gets one default method (the 401 decision).
2. The total attachment limit of under 3 MB is the spec's own rule, not a documented sendMail limit. Base64 inflates the real payload by a third.
3. `message.from` is omitted. The URL selects the mailbox.
4. The MVP sends JSON boolean `saveToSentItems: false`, not the string form in Microsoft's example.
5. `exp` is 5 minutes after `nbf`, not the spec's "at most 10".
6. Microsoft's current sendMail page names a 500-recipient cap. Do not hold it as a core constant.

---

## Open Decisions for the Owner or Roadmapper

| Decision | Recommendation | Why it matters |
|----------|----------------|----------------|
| 401 handling: default method on `TokenProvider` vs no widening | Default method with the rejected-token parameter | Needs owner sign-off because spec §7.3 prefers no widening |
| Exception type for unknown transport ID and duplicate ID at account build | Unchecked `IllegalArgumentException` and `IllegalStateException` | Spec only says "fails". `MailException` is checked and per-mail |
| Graph `baseUrl` option | Add it (`GraphTransportProvider` reads an optional `baseUrl`, default the public cloud) | Spec milestone 4 says "sample runs end to end against WireMock", and a registry-built Graph account can only be redirected through an option. It is also what national clouds need. It is the one option the spec does not list. If the owner declines it, the E2E test lives at library level with `GraphTransport` built by a package-private constructor, and the sample is never run against WireMock |
| `Mail.Builder.to(String)` address checks | Non-blank only in the library. The sample's pre-flight does the syntax check | Rule 0. The server returns 400 for bad addresses. A later MIME transport must additionally reject CR/LF, which is a transport-level concern |
| Recorder injection for `RecordingTransport` | Pass a recorder object through the options map (`option("recorder", recorder)`) so each test holds its own instance. Avoid a global static list | The sample's tests need to read sent mail and to script failures (transient, sender-rejected, auth-required). A static would make tests order-dependent |
| `Automatic-Module-Name` in the jar manifest | Optional, add in the `release` phase (one manifest entry, no `module-info.java`) | Central consumers on the module path. Not required by the spec, so ask before adding |

---

## Sources

- Microsoft Learn, user: sendMail (v1.0): https://learn.microsoft.com/en-us/graph/api/user-sendmail?view=graph-rest-1.0 (HIGH, official, read 2026-11-21)
- Microsoft Learn, message resource type: https://learn.microsoft.com/en-us/graph/api/resources/message?view=graph-rest-1.0 (HIGH)
- Microsoft Learn, itemBody resource type: https://learn.microsoft.com/en-us/graph/api/resources/itembody?view=graph-rest-1.0 (HIGH)
- Microsoft Learn, fileAttachment resource type: https://learn.microsoft.com/en-us/graph/api/resources/fileattachment?view=graph-rest-1.0 (HIGH)
- Microsoft Learn, attach large files to Outlook messages or events: https://learn.microsoft.com/en-us/graph/outlook-large-attachments (HIGH)
- Microsoft Learn, certificate credentials: https://learn.microsoft.com/en-us/entra/identity-platform/certificate-credentials (HIGH)
- Microsoft Learn, OAuth 2.0 client credentials flow: https://learn.microsoft.com/en-us/entra/identity-platform/v2-oauth2-client-creds-grant-flow (HIGH)
- Microsoft Q&A, sendMail request size limit (community answers about a roughly 4 MB JSON ceiling, unofficial): https://learn.microsoft.com/en-in/answers/questions/1824204/graph-api-user-sendmail-request-size-limit (LOW)
- Project documents: `spec.md` (binding), `.planning/PROJECT.md`

---
*Architecture research for: morimail (Java 17 mail library, Graph transport, Entra client credentials, picocli sample)*
*Researched: 2026-11-21*
