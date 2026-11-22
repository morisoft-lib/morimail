# Phase 3: Graph and Entra - Pattern Map

**Mapped:** 2026-11-22
**Files analyzed:** 14 new/modified
**Analogs found:** 11 / 14 (all analogs are git-tracked; no mirror paths)

The repo has no HTTP, JWT or Jackson code yet in the library (no `System.Logger` use either), so the HTTP-facing classes lean on RESEARCH.md patterns plus the contract analogs below.

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `morimail/src/main/java/de/morisoft/morimail/graph/GraphTransportProvider.java` | provider | request-response | `morimail/src/test/java/de/morisoft/morimail/test/RecordingTransportProvider.java` | exact |
| `morimail/src/main/java/de/morisoft/morimail/graph/GraphTransport.java` | service (transport) | request-response | `morimail/src/test/java/de/morisoft/morimail/test/RecordingTransport.java` | role-match |
| `morimail/src/main/java/de/morisoft/morimail/entra/EntraClientCredentials.java` | service (token provider) | request-response, cached | `morimail/src/main/java/de/morisoft/morimail/TokenProvider.java` (contract) | partial |
| `morimail/src/main/java/de/morisoft/morimail/BuiltInTransports.java` (modify) | config | n/a | itself | exact |
| `morimail/src/test/java/de/morisoft/morimail/graph/GraphTransportTest.java` | test | request-response | `morimail/src/test/java/de/morisoft/morimail/TransportRegistryTest.java` (style only) | partial |
| `morimail/src/test/java/de/morisoft/morimail/entra/EntraClientCredentialsTest.java` | test | request-response | `morimail/src/test/java/de/morisoft/morimail/TokenProviderTest.java` | role-match |
| `morimail/src/test/java/de/morisoft/morimail/LiveGraphTest.java` | test (live) | request-response | none | none |
| `morimail/src/test/java/de/morisoft/morimail/ImportRulesTest.java` (modify) | test | file-I/O | itself | exact |
| `morimail/src/test/java/de/morisoft/morimail/TransportRegistryTest.java` (modify) | test | n/a | itself | exact |
| `morimail/src/test/resources/test-keystore-throwaway.p12` | fixture | n/a | none | none |
| `morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/SerialMail.java` (modify) | controller/wiring | request-response | itself (`commandLine` seam) | exact |
| `morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/GraphEndToEndTest.java` | test | request-response | `SendRunTest.java` + `SampleFixtures.java` | role-match |
| `SendRunTest.java`, `AccountFileTest.java` (modify) | test | n/a | themselves | exact |
| `docs/architecture.md`, `README.md`, poms (WireMock test dep) | docs/config | n/a | existing files | exact |

## Pattern Assignments

### `graph/GraphTransportProvider.java` (provider)

**Analog:** `morimail/src/test/java/de/morisoft/morimail/test/RecordingTransportProvider.java`

Contract (`TransportProvider.java`): `String id();` and `MailTransport create(Map<String, Object> options, TokenProvider auth);`

```java
public final class RecordingTransportProvider implements TransportProvider {
    @Override
    public String id() { return "recording"; }

    @Override
    public MailTransport create(Map<String, Object> options, TokenProvider auth) {
        return new RecordingTransport(options, auth);
    }
}
```

Copy this shape: `public final`, Javadoc with a thread-safety sentence ("stateless and thread-safe"; `PublicTypesDocumentThreadSafetyTest` enforces it), plus an explicit Javadoc'd public constructor (RESEARCH Pitfall 1, doclint). `create` validates options (`baseUrl` is the only option, D-11) and a null `auth` throws `IllegalArgumentException` (RESEARCH Pitfall 13).

### `graph/GraphTransport.java` (package-private final)

**Analog:** `morimail/src/test/java/de/morisoft/morimail/test/RecordingTransport.java` (contract and thread-safety Javadoc style)

Contract (`MailTransport.java`):
```java
void validate(MailMessage message) throws InvalidMailException;
void send(MailMessage message) throws MailException;
```
Input record: `MailMessage(from, to, cc, bcc, replyTo, subject, html, text, attachments)`; `Attachment(filename, contentType, Path file, contentId)`.

**Error types** (`TransientMailException.java`): constructors `(String)`, `(String, Throwable)`, `(String, Duration retryAfter)`. `retryAfter` must be non-negative, so clamp a past HTTP-date to `Duration.ZERO` (D-10). Pitfall 12: passing `null` as the second arg is ambiguous, so cast it. Use `PermanentMailException`, `SenderRejectedException` and `AuthRequiredException` from the same package. Limits (3 MiB attachments, 4 MiB estimate) go in `validate` and throw `InvalidMailException`. Map statuses per D-06/D-07; the `Mailbox` match lives in one method.

**Token retry:** `auth.accessToken(scope)`, and on a 401 `auth.freshAccessToken(scope, rejectedToken)` once (GRAPH-07).

**Logging:** no existing `System.Logger` use in the library. Use `System.getLogger(GraphTransport.class.getName())`, log "html and text both given" once per instance at INFO, never tokens or bodies, recipients at DEBUG only.

### `entra/EntraClientCredentials.java` (public final, implements TokenProvider)

**Analog:** `morimail/src/main/java/de/morisoft/morimail/TokenProvider.java`
```java
@FunctionalInterface
public interface TokenProvider {
    String accessToken(String scope) throws MailException;
    default String freshAccessToken(String scope, String rejectedToken) throws MailException {
        return accessToken(scope);
    }
}
```
Override `freshAccessToken` with compare-and-drop (ENTRA-06). Use RESEARCH Patterns 1-3 (private constructor with defaults, `ReentrantLock` single-flight with cache re-check, PS256 assertion with `x5t#S256`). Public API per D-01..D-05: three static factories, `authority(URI)` returning a new instance, `char[]` password, `IllegalArgumentException` for PKCS12 load problems (message names the file, never the password). Timeouts are private constants (D-02). It must not import `de.morisoft.morimail.graph`. The `Retry-After` parser is duplicated (about 10 lines) from graph (D-10).

### `BuiltInTransports.java` (modify)

**Current** (the whole body, with `List.of()` as the only line to change):
```java
final class BuiltInTransports {
    static List<TransportProvider> providers() {
        return List.of();
    }
}
```
Change to `List.of(new GraphTransportProvider())` and add the import of the graph package. `ImportRulesTest` already allows this file, and only this file, to use graph types. It also allows the file to reach `entra`; check `checkMentions` for the entra rule.

### `ImportRulesTest.java` (modify)

**Analog:** itself. It has `GRAPH_PACKAGE`, `ENTRA_PACKAGE`, `BUILT_IN`, `javaFiles(...)` and `withoutComments(...)`. It already says "the graph and entra packages do not reference each other", so after the new packages exist, check that this rule scans them (the file list is collected at depth 1 for core). Add a depth-based scan of `graph/` and `entra/` if it does not.

### `TransportRegistryTest.java` (modify)

**Analog:** itself. Tests call `TransportRegistry.of(List.of(builtIn), List.of(external))` and the `none()` helper. Add a test that the real registry (`BuiltInTransports.providers()` through the default path) resolves `graph`, and that an external duplicate `graph` is rejected with `(built-in)` in the message.

### `GraphTransportTest`, `EntraClientCredentialsTest` (WireMock tests)

**Analog:** `morimail/src/test/java/de/morisoft/morimail/TokenProviderTest.java` for assertion style (JUnit 5 + AssertJ). WireMock use follows RESEARCH Patterns 5 and 6 (`@WireMockTest`, stubs keyed by header, `System.Logger` capture). Place the tests in the same package as the class under test so they see the package-private seam. Use `example.com`/`example.org` and zero GUIDs only.

### `LiveGraphTest.java`

No analog. Follow D-15..D-18 and RESEARCH Pitfall 14: `@Tag("live")` (excluded by `excludedGroups` in the parent `pom.xml`, run with `-Plive`), probes skipped through `Assumptions` on env vars, one sanitised line printed per probe.

### `SerialMail.java` (modify)

**Analog:** itself.
```java
System.exit(commandLine(SerialMail::realMailer, SerialMail::sleep).execute(args));   // main
static CommandLine commandLine(Function<AccountFile, Mailer> mailerFactory, Consumer<Duration> sleep)
static Mailer realMailer(AccountFile account) { throw new IllegalStateException(GRAPH_NOT_AVAILABLE); }
```
Replace per D-11: `realMailer(account)` calls `mailer(account, System::getenv, <default authority>, <default Graph base URL>)`. The new package-private `mailer(AccountFile, Function<String,String> env, URI authority, String graphBaseUrl)` builds `MailAccount.builder(account.id()).transport("graph").auth(entra).option("baseUrl", ...).defaultFrom(account.from()).build()` (builder methods: `transport`, `auth`, `option`, `defaultFrom`, `build`). `AccountFile` is `record AccountFile(id, tenantId, clientId, from, certificatePath, certificatePasswordEnv, secretEnv)`. Per D-14, an unset or empty env var or a bad PKCS12 must be reported as a pre-flight problem (exit 2) naming the env var or key only. The existing contract comment says a `realMailer` message must "name keys and environment-variable names only". Remove `GRAPH_NOT_AVAILABLE`. The pre-flight path is the one that currently converts the `IllegalStateException` into `account: <message>`, so check `SendCommand` to see how a thrown exception from the factory is reported, and keep that route.

### `GraphEndToEndTest.java` (sample, CLI-14)

**Analog:** `morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/SendRunTest.java` and `SampleFixtures.java`.

```java
SampleFixtures.Run run = SampleFixtures.run(SerialMail::realMailer, new ArrayList<>(),
        "send", "--account", account.toString(), "--campaign", campaign.toString());
assertThat(run.exit()).isEqualTo(2);
assertThat(run.errLines()).containsExactly(...);
```
Use `SampleFixtures.run(a -> SerialMail.mailer(a, Map.of(...)::get, wmAuthority, wmGraphUrl), waits, args...)`, `SampleFixtures.writeAccount(root, content)`, `SampleFixtures.journalLines(path)`. `SampleFixtures.ACCOUNT` shows the account keys (`account.id`, `tenantId`, `clientId`, `from`, `secretEnv`); for D-12 use `certificate.path` and `certificate.passwordEnv` instead. The campaign comes from `morimail-sample-serial/example-campaign` (5 recipients, 2 attachments) copied to a temp dir. The keystore comes from the library test-jar (see RESEARCH Pitfall 9).

### `SendRunTest.java`, `AccountFileTest.java` (modify)

`SendRunTest.realRunThroughMainStopsBeforeSending` (about lines 126-139) and `AccountFileTest` (about lines 179-183 and 200-207) assert `SerialMail.GRAPH_NOT_AVAILABLE` through `SerialMail::realMailer`. Rewrite them to the D-14 pre-flight behaviour (missing env var gives exit 2 and a message naming the env var), or delete them if they become redundant.

## Shared Patterns

### Thread-safety Javadoc
**Source:** `RecordingTransportProvider.java` ("This class is stateless and thread-safe."). **Apply to:** every new public type (`GraphTransportProvider`, `EntraClientCredentials`). Enforced by `PublicTypesDocumentThreadSafetyTest`.

### Error mapping
**Source:** `TransientMailException` / `PermanentMailException` / `SenderRejectedException` / `AuthRequiredException`. **Apply to:** `GraphTransport` and `EntraClientCredentials`. Error text is untrusted input (RESEARCH Pitfall 11): include `error.code`, message and `request-id`, never tokens or bodies.

### Dependency rules
**Source:** `ImportRulesTest`. The core imports only the JDK, `graph` and `entra` do not reference each other, and Jackson is the only extra library.

### Test data
`example.com`, `example.org`, zero GUIDs. The keystore is the one allowed throwaway file (legacy PKCS12, password `changeit`).

## No Analog Found

| File | Role | Data Flow | Reason |
|------|------|-----------|--------|
| `entra/EntraClientCredentials.java` (HTTP, JWT body) | token provider | request-response | No HTTP, JWT or Jackson code exists in the library yet; use RESEARCH Patterns 1-3 |
| `graph/GraphTransport.java` (HTTP body) | transport | request-response | Same; use RESEARCH Patterns 4-5 |
| `LiveGraphTest.java` | live test | request-response | No live or env-driven test exists |
| `test-keystore-throwaway.p12` | fixture | n/a | Created with keytool `-J-Dkeystore.pkcs12.legacy` (stack notes) |

## Metadata

**Analog search scope:** `morimail/src/main`, `morimail/src/test`, `morimail-sample-serial/src`
**Files scanned:** about 45 tracked files (verified through `git ls-files`)
**Pattern extraction date:** 2026-11-22
