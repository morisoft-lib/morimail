# Phase 3: Graph and Entra - Research

**Researched:** 2026-11-22
**Domain:** Microsoft Graph `sendMail` over the JDK HTTP client; Entra client-credentials token client (secret and PS256 certificate assertion); WireMock tests; sample wiring
**Confidence:** HIGH for the Entra token flow, the assertion format, the sendMail request shape and the code integration points. MEDIUM for the 4 MB request ceiling and for which status codes Graph returns for mailbox problems (only the owner's live test settles these, as planned).

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-01:** The three static factories stay exactly as in spec §5.1 (`withSecret`, `withCertificate(key, cert)`, `withCertificate(Path p12, password)`). The authority host (ENTRA-04) is overridden with one instance copy method, `authority(URI)`, which returns a new immutable instance. The default is the public cloud `https://login.microsoftonline.com`. There is no builder and there are no overloads. — **Reversibility:** one-way. It is a public method on a published type after 0.1.0.
- **D-02:** The connect and request timeouts (ENTRA-08, GRAPH-09) are fixed private constants in each class, for example connect 10 s and request 30 s. They are not settable and there is no `timeout` option key (Rule 0; they can be made settable later without a break).
- **D-03:** The PKCS12 password parameter is `char[]` (the `KeyStore.load` convention). The sample calls `System.getenv(..).toCharArray()`. — **Reversibility:** one-way. It is a public signature.
- **D-04:** The PKCS12 file must contain exactly one private-key entry, which is used with its certificate. Zero or several key entries are an error naming the file. There is no alias parameter.
- **D-05:** PKCS12 load failures (missing file, wrong password, not exactly one key) throw `IllegalArgumentException` with the cause attached. The message names the file and never the password. This is a setup error, like an unknown transport ID, not a `MailException`.
- **D-06:** Responses not listed in §6.3 are mapped by status class. Any 2xx is accepted. Every 5xx is `TransientMailException` (with `Retry-After` when present). Every other 4xx, and any 1xx/3xx, is `PermanentMailException`. The specific rows of §6.3 (403/404 → `SenderRejectedException`, 401 handling, 429 → transient) take precedence over the class rule. The live test corrects these later.
- **D-07:** If a 401 comes back again after the fresh-token retry (GRAPH-07), the response counts as "pointing at the mailbox" when Graph's `error.code` contains `Mailbox` (case-insensitive, `Locale.ROOT`, e.g. `MailboxNotEnabledForRESTAPI`). That gives `SenderRejectedException`; anything else gives `AuthRequiredException`. The match lives in one place so the live test can correct it. `error.message` is not matched.
- **D-08:** "3 MB" in GRAPH-05 means 3 × 1024 × 1024 = 3,145,728 bytes, held as one constant in the Graph transport. A single attachment at or above it, or attachments whose total reaches it, are rejected.
- **D-09:** The request-size limit is one constant of 4 × 1024 × 1024 bytes. It is compared with an estimate: the base64 size of each attachment (4/3, rounded up to a multiple of 4), plus the UTF-8 length of the body sent, plus a small fixed JSON allowance. The researcher confirms the 4 MB figure against the current Microsoft docs and records any difference in `docs/architecture.md`.
- **D-10:** `Retry-After` is parsed in both forms: delta-seconds, and an RFC 1123 HTTP-date (`DateTimeFormatter.RFC_1123_DATE_TIME`, where a date in the past gives zero). A value that cannot be parsed gives an empty `retryAfter`. The same parser is used for the Entra 429 if it sends the header.
- **D-11:** `SerialMail.realMailer(AccountFile)` delegates to a new package-private `mailer(AccountFile account, Function<String,String> env, URI authority, String graphBaseUrl)`, passing `System::getenv` and the public defaults. The E2E test passes `a -> mailer(a, Map.of(...)::get, wmAuthority, wmGraphUrl)` through the existing `commandLine(Function<AccountFile, Mailer>, sleep)` seam. `account.properties` gets no new keys (this keeps Phase 2 D-01). The account is built through the registry (`transport("graph")` plus the `baseUrl` option), not by constructing the transport directly.
- **D-12:** The E2E test uses the certificate flow: the checked-in throwaway PKCS12 from the library test-jar, copied to a temp dir and referenced through `certificate.path` + `certificate.passwordEnv`. The secret flow is covered by the library's Entra tests.
- **D-13:** The E2E test asserts the following. (a) The example campaign produces 5 `POST /users/{from}/sendMail` calls, each to the right recipient with both attachments, plus exactly one token request (cache reuse), and exits 0. (b) A second run against the same journal makes 0 Graph calls and reports 5 skipped. (c) A 403 stubbed for one recipient stops the run with exit 3 and journals the failure.
- **D-14:** On a real run, an unset or empty password/secret env var, or a PKCS12 that cannot be loaded (D-05), is reported as a pre-flight problem with exit code 2, before any send. The message names the env var or the key, never the value. This replaces the Phase 2 "graph not available" stop and `GRAPH_NOT_AVAILABLE`.
- **D-15:** The `live` test sits in the library module (e.g. `morimail/src/test/java/de/morisoft/morimail/LiveGraphTest.java`, tagged `live`, excluded by default, run with `-Plive`). It builds `EntraClientCredentials` and a registry `graph` account directly. The owner covers the other STOP-report items with the sample CLI (`--limit 1`, `--rate`, a shared mailbox, a second run).
- **D-16:** The test is one class. Its main test sends one mail with a small attachment, which proves PS256 acceptance and the 202. It has optional probe methods, each skipped unless its env var is set: an out-of-scope mailbox (expects `SenderRejectedException` and prints the real status, `error.code` and `request-id`) and a large attachment of about 2.9 MB taken from a file the owner supplies. Each probe prints one sanitised line the owner can paste into the report (no token, no body, no secret). This deviates slightly from spec §10.1's "one JUnit test" (one class, opt-in probe methods); record it in `docs/architecture.md`.
- **D-17:** The env vars are `MORIMAIL_LIVE_TENANT_ID`, `MORIMAIL_LIVE_CLIENT_ID`, `MORIMAIL_LIVE_FROM` and `MORIMAIL_LIVE_TO`, then `MORIMAIL_LIVE_CERT_PATH` + `MORIMAIL_LIVE_CERT_PASSWORD` or `MORIMAIL_LIVE_SECRET`, and the optional `MORIMAIL_LIVE_OUT_OF_SCOPE_FROM` and `MORIMAIL_LIVE_BIG_ATTACHMENT` (a file path). With `-Plive` and required vars missing, the test fails fast and names the missing vars.
- **D-18:** If both cert and secret vars are set, the certificate is used and the printed line states which flow ran.

### Claude's Discretion

- Exact class split inside `graph` and `entra` (Rule 0: one class each plus the provider; a package-private helper only if it clearly removes duplication, such as the `Retry-After` parser, which both packages need without importing each other; duplicating about 10 lines is acceptable).
- How "log once at INFO when both html and text are given" is scoped (per transport instance is the simple choice).
- How single-flight token fetching is done (e.g. `synchronized` or a `ReentrantLock` around the fetch with a re-check of the cache).
- Exact timeout values, as long as they are explicit and documented.
- Name and location of the checked-in keystore (e.g. `morimail/src/test/resources/test-keystore-throwaway.p12`, legacy PKCS12, password `changeit`) and the one README sentence (TEST-01, Phase 1 D-17).
- How REPO-08 "passes with no network access" is proven (e.g. CI runs `verify` after the dependencies are resolved, with no network needed by tests since all HTTP goes to in-process WireMock on localhost).
- Exception message wording, within GRAPH-08 (`error.code`, message, `request-id`; never tokens or bodies).

### Deferred Ideas (OUT OF SCOPE)

- Settable timeouts and an `authority`/`graphBaseUrl` key in `account.properties` (sovereign clouds): add them when someone needs them.
- A live probe above 3 MB (about 3.5 MB). The library's `validate` rejects it before sending, so Graph's real response can only be seen with a hand-made request. The owner does this manually if wanted; the upload-session route is a later milestone.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| ENTRA-01 | Secret flow form to `{authority}/{tenant}/oauth2/v2.0/token` | Form fields confirmed against the client-credentials page (see Spec vs Docs, Code Example 1) |
| ENTRA-02 | PS256 assertion with `x5t#S256` and claims | Certificate-credentials page confirms header and claims; PSS signing proven on this JDK (spike); Code Example 2 |
| ENTRA-03 | PKCS12 loader | Keytool legacy PKCS12 generated and loaded in a spike; exactly-one-key logic; Pitfall 5 |
| ENTRA-04 | Authority override | `authority(URI)` copy method (D-01); `aud` follows the overridden token URL |
| ENTRA-05 | Cache until 5 min before expiry, single flight | ReentrantLock + re-check pattern; Clock seam for testing (Pattern 2) |
| ENTRA-06 | Compare-and-drop `freshAccessToken` | Pattern 2 |
| ENTRA-07 | 4xx → AuthRequired (error, error_description); 5xx/429/network → Transient | Error shape confirmed; 429 must be tested before the 4xx rule (Pitfall 3) |
| ENTRA-08 / GRAPH-09 | Explicit connect and request timeouts | `HttpClient.Builder.connectTimeout` + `HttpRequest.Builder.timeout`; test seam advice (Pitfall 10) |
| ENTRA-09 | Never log secret, assertion, token | No logging in `entra`; JUL-handler capture test (Pattern 6) |
| GRAPH-01 | `POST {baseUrl}/users/{from}/sendMail`, bearer, scope, percent-encoded from, 202 | sendMail page; WireMock matches the raw encoded path (spike) |
| GRAPH-02 | One body, HTML else text; INFO once if both | Message resource has a single `body` (itemBody) — confirms spec assumption |
| GRAPH-03 | `#microsoft.graph.fileAttachment` + base64 `contentBytes` | sendMail example 3 and fileAttachment page; Code Example 4 |
| GRAPH-04 | `saveToSentItems`, `baseUrl`, unknown keys rejected | Docs say send it only when false |
| GRAPH-05 | 3 MB per file and total, request-size estimate | 3 MB from large-attachments page; 4 MB is community-sourced only; Pitfall 6 |
| GRAPH-06 | Error table mapping | Error page status table; D-06 class rule; Code Example 5 |
| GRAPH-07 | One 401 retry with fresh token, then map | `TokenProvider.freshAccessToken` already exists; Code Example 5 |
| GRAPH-08 | Message has `error.code`, message, `request-id`; no tokens or bodies | `innerError` vs `innererror` casing trap; Pitfall 4 |
| TEST-01 | Throwaway PKCS12 checked in | Generated with `keytool -J-Dkeystore.pkcs12.legacy`; verified legacy algorithms; Pitfall 5 |
| TEST-02 | Entra tests | Test patterns in Architecture Patterns and Validation notes |
| TEST-03 | Graph tests with stub TokenProvider | Header-keyed WireMock stubs for the 401 retry (Pattern 5) |
| CLI-14 | Sample E2E against WireMock | `mailer(...)` seam, journal path pitfall, existing-test updates (Pitfall 8) |
| REPO-08 | `verify` offline; `live` test excluded, `-Plive` runs it | Profile mechanics proven in a spike; offline build with WireMock proven |
</phase_requirements>

## Summary

The phase is mostly mechanical once the Microsoft pages are read. The token endpoint, the PS256 assertion and the `sendMail` JSON shape match spec §6 and §7. The Microsoft pages confirm the two assumptions the spec asked to be checked: the message resource has exactly one `body` (HTML or text), so "HTML only when both are given" is correct, and `x5t#S256` plus PS256 is the documented header. I found four real differences between the spec and the current docs. They are listed in the "Spec vs Docs" section and each belongs in `docs/architecture.md`. The most important one for planning is that the "4 MB request limit" is not on any Microsoft reference page; the only source is a Microsoft Q&A answer that says "generally around 4 MB". It stays a LOW-MEDIUM confidence constant, and the live test cannot probe it (deferred).

No new runtime dependency is needed. The only new dependency is `org.wiremock:wiremock` 3.13.2 in test scope, already managed in the parent POM. I ran a scratch copy of the repo (outside the repository) with WireMock added to the library: the offline build (`mvnw -o`) resolved it, a legacy PKCS12 made with `keytool` loaded on JDK 21 and signed and verified with `RSASSA-PSS` (SHA-256, MGF1 SHA-256, salt 32), `System.Logger` output was capturable through a `java.util.logging` handler, and the `-Plive` profile excluded and then ran a `@Tag("live")` test, including in the full reactor. All of these were proven this session, not assumed.

The central risk is not code complexity but three places where the existing repo forces coordination: (1) three Phase 2 tests reference `SerialMail.GRAPH_NOT_AVAILABLE` and must change with D-14; (2) the E2E test must pass `--journal` to a temp file or it writes `journal.jsonl` into the checked-in example campaign; (3) with the 3 MB per-file rule and the 4 MB estimate, an attachment just under 3 MiB is already rejected by the estimate, not by the per-file rule (Pitfall 6). Everything else follows decisions D-01..D-18.

**Primary recommendation:** Build `entra` (one public class) and `graph` (one public provider plus one package-private transport) in parallel, wire them last. Give each class one private all-args constructor that the public factories call with the default constants, and let same-package tests call a package-private variant that takes a `Clock` and short timeouts. That one seam makes expiry, JWT claims and timeout mapping testable without making anything settable in the public API.

## Project Constraints (from CLAUDE.md)

- **Rule 0:** build the simplest code; only two extension points (`TransportProvider`, `TokenProvider`); no new interface, factory, strategy or abstract base class; no frameworks, DI, annotation processing or reflection. Ask before adding any dependency.
- Library runtime dependency is Jackson only; `System.Logger` for logging; `java.net.http.HttpClient`; JDK crypto for the JWT.
- `graph` and `entra` must not import each other; the core imports only the JDK (its one outside reference is the built-in list). Limits are checked in the transport's `validate`.
- No secrets, tenant IDs, real addresses, internal hosts or customer names in any file, fixture or commit. Use `example.com`, `example.org` and zero GUIDs. Exceptions: the throwaway PKCS12 (password `changeit`) and the maintainer identity.
- Never log tokens, secrets, assertions or full bodies; recipients at DEBUG only.
- Public types get Javadoc that states thread safety; Javadoc must build cleanly (`-Prelease` uses doclint all with `failOnWarnings`).
- Never send real mail, never publish, deploy or tag. Commit as `Stephan Wald <stephan@morisoft.de>` (the workspace `GIT_*` env vars override local config; export the identity before every commit, per the project memory).
- `mvn verify` must pass offline. STOP after this phase (milestone 4) and report; do not start Phase 4.
- Read the current Microsoft pages before coding and note differences in `docs/architecture.md` (done below).

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Token acquisition, caching, assertion signing | Library `entra` package | — | Knows Entra; implements `TokenProvider`; nothing else imports it except the sample |
| Building the `sendMail` request, size limits, error mapping | Library `graph` package | Core (exception types) | Graph-specific rules live in the transport (`validate`, `send`), not in the core |
| Registering `graph` | Core `BuiltInTransports` | Registry | The one allowed core reference to a transport package |
| Reading env vars, building accounts from `account.properties` | Sample `SerialMail.mailer(...)` | `AccountFile` | The library never reads env or files for accounts |
| Retry/backoff on transient errors, journal, stop on auth error | Sample `SendCommand` (exists) | — | The library does one HTTP attempt (plus the single 401 retry) |
| HTTP stand-ins for Entra and Graph | Test tier (WireMock in-process) | — | `mvn verify` needs no network |
| PS256 acceptance, real error codes | Owner's live test | — | Cannot be proven offline |

## Standard Stack

### Core (no new library in the library jar)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `java.net.http.HttpClient` | JDK 17 API | Token and Graph HTTP calls | Mandated by spec §3; `--release 17` keeps the API surface honest |
| `jackson-databind` | 2.23.0 (managed by `jackson-bom`) | JSON for assertion parts, token response, error bodies, request body | Only runtime dependency; already declared in `morimail/pom.xml` |
| JDK `KeyStore`, `Signature`, `MessageDigest`, `Base64` | JDK 17 API | PKCS12 load, RSASSA-PSS, thumbprint, base64url | No JWT library allowed (spec §3) |

### Supporting (test scope)

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `org.wiremock:wiremock` | 3.13.2 | Fake token endpoint and Graph | Library tests and sample E2E; add `<scope>test</scope>` dependency to both `morimail/pom.xml` and `morimail-sample-serial/pom.xml` (version already managed) |
| JUnit Jupiter 6.1.3, AssertJ 3.27.7 | already declared | Tests | `@ParameterizedTest` is available through the `junit-jupiter` aggregator (the sample already uses it) |

[VERIFIED: pom.xml:53] `<wiremock.version>3.13.2</wiremock.version>` and [VERIFIED: pom.xml:86] `<version>${wiremock.version}</version>`. Version check: [VERIFIED: repo.maven.apache.org maven-metadata, fetched this session] 3.13.2 is the latest 3.x entry (`3.13.0`, `3.13.1`, `3.13.2`); `<latest>` is `4.0.0-beta.44`, not stable, do not use.

**Installation (test dependency, both modules):**
```xml
<dependency>
  <groupId>org.wiremock</groupId>
  <artifactId>wiremock</artifactId>
  <scope>test</scope>
</dependency>
```

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Hand-written JWT with JDK `Signature` | A JWT library or MSAL4J | Forbidden by spec §2/§3 (Jackson-only) |
| One shared `Retry-After` helper | Duplicate about 10 lines in `graph` and `entra` | A shared helper would have to live in core and be public API, or `graph` and `entra` would import each other. Duplicate it. |

## Package Legitimacy Audit

The `package-legitimacy` seam supports `npm|pypi|crates` only (it printed its usage error for a Maven coordinate), so it was not run for this Maven dependency. The check below is manual.

| Package | Registry | Age | Downloads | Source Repo | Verdict | Disposition |
|---------|----------|-----|-----------|-------------|---------|-------------|
| `org.wiremock:wiremock` 3.13.2 | Maven Central | years (3.x line, many releases) | n/a | github.com/wiremock/wiremock | OK (manual) | Approved: already pinned in the parent POM and named in the project stack; present in the local `~/.m2` and resolved offline in the spike |

**Packages removed due to [SLOP] verdict:** none
**Packages flagged as suspicious [SUS]:** none
No other package is added. The library jar gains no new runtime dependency. Maven has no `postinstall` mechanism.

## Architecture Patterns

### System Architecture Diagram

```
 sample: serial-mail send
   AccountFile + env ──► SerialMail.mailer(account, env, authority, graphBaseUrl)
        │                    │ builds EntraClientCredentials (secret | PKCS12) .authority(..)
        │                    └ MailAccount.builder(id).transport("graph").auth(creds)
        │                                  .option("baseUrl", ..).defaultFrom(..).build()
        ▼                         (registry: BuiltInTransports → GraphTransportProvider)
   SendCommand ── mailer.validate(first mail) ──► GraphTransport.validate  (3 MB / 4 MB limits)
        │
        └─ per row: mailer.send(mail) ──► GraphTransport.send
                                              │ token = auth.accessToken(scope)
                                              ▼
                            POST {baseUrl}/users/{percent-encoded from}/sendMail
                            Authorization: Bearer token, JSON body
                                              │
              ┌──────────── 202 ──────────────┼──────── 401 (first time) ───────────┐
              ▼ return                        │                                       ▼
                                              │            token2 = auth.freshAccessToken(scope, token)
                                              │            POST again (once)
                                              ▼                                       │
                    status/headers/body ─► map (403,404→SenderRejected; 429,5xx,IO→Transient;
                                              other 4xx,1xx,3xx→Permanent; 401 again→
                                              SenderRejected if error.code has "mailbox" else AuthRequired)

 EntraClientCredentials.accessToken(scope)
   lock ─► cached token valid? (now < expiry − 5 min) ─ yes ─► return
        └─ no ─► build form (secret | PS256 assertion) ─► POST {authority}/{tenant}/oauth2/v2.0/token
                    200 ─► parse access_token + expires_in ─► cache ─► return
                    429 | 5xx | IOException ─► TransientMailException (Retry-After parsed)
                    other 4xx ─► AuthRequiredException(error + error_description)
```

### Recommended Project Structure

```
morimail/src/main/java/de/morisoft/morimail/
├── BuiltInTransports.java          # one new line: new GraphTransportProvider()
├── entra/
│   └── EntraClientCredentials.java # public final; implements TokenProvider; all Entra logic
└── graph/
    ├── GraphTransportProvider.java # public final; id() = "graph"; validates options; explicit Javadoc'd constructor
    └── GraphTransport.java         # package-private final; validate + send + error mapping
morimail/src/test/java/de/morisoft/morimail/
├── entra/EntraClientCredentialsTest.java      # assertion + WireMock flows (same package: sees the test seam)
├── graph/GraphTransportTest.java              # WireMock + stub TokenProvider
├── LiveGraphTest.java                         # @Tag("live"), one class with opt-in probes (D-15..D-18)
└── (TransportRegistryTest, ImportRulesTest extended)
morimail/src/test/resources/test-keystore-throwaway.p12
morimail-sample-serial/src/test/java/.../GraphEndToEndTest.java   # CLI-14
```

### Pattern 1: One private constructor with defaults, one package-private seam

**What:** Each class has one private all-args constructor. Public factories pass the constants (connect 10 s, request 30 s, `Clock.systemUTC()` for Entra). Tests in the same package call a package-private factory that passes a `Clock` and short timeouts. Nothing public becomes settable (D-02 holds).
**When to use:** Expiry tests (boundary at 5 minutes), JWT claim values (`nbf`, `iat`, `exp`) and timeout-to-Transient mapping. If the planner rejects the seam as too heavy, the fallback is `expires_in` of 299 or less (already inside the 5-minute window, so every call refetches) for the refetch test and `Fault.CONNECTION_RESET_BY_PEER` for the network-error test, and review-only for timeouts. [ASSUMED] that the seam is wanted; it is a judgment call under Rule 0.

### Pattern 2: Single-flight cache with `ReentrantLock` and re-check

**What:** `accessToken(scope)`: read the cached entry without the lock; if still valid return it; otherwise take the lock, re-check, fetch, store. `freshAccessToken(scope, rejected)`: take the lock; if the cached token equals `rejected` clear it; then run the same re-check-and-fetch. Two threads that both got the same 401 therefore cause exactly one new token request, and a mismatching rejected token costs no request. Use one cached entry that also records its scope (a different scope just refetches and replaces; Graph always uses one scope). A `ReentrantLock` is preferable to `synchronized` because the lock is held across network I/O.
**Expiry:** `usableUntil = now + expires_in − 300 s`; valid while `now < usableUntil`. Read `expires_in` with `JsonNode.asLong()`, which also accepts a numeric string.
**Verify with:** WireMock stub with `withFixedDelay(300)` and N threads released by a `CountDownLatch`; assert exactly 1 request.

### Pattern 3: Assertion build (verified shape)

See Code Example 2. Header map order `alg`, `typ`, `x5t#S256` (a `LinkedHashMap` keeps it); claims `aud`, `exp`, `iss`, `jti`, `nbf`, `sub`, `iat` as in the docs example; numeric dates are epoch seconds as JSON numbers; compact JSON from Jackson; three base64url parts without padding.

### Pattern 4: Graph request body

See Code Example 4. Built with Jackson's `ObjectNode` tree. Omit empty recipient arrays and omit the `attachments` key when there are none. `emailAddress.name` only when the display name is non-null. Do not send `message.from` (the mailbox is in the URL; see Open Question 2).

### Pattern 5: WireMock stubs keyed by header for the 401 retry

Stub 1: `post(urlEqualTo("/v1.0/users/<encoded>/sendMail")).withHeader("Authorization", equalTo("Bearer t1"))` returns 401. Stub 2: same URL with `Bearer t2` returns 202. The stub `TokenProvider` returns `t1` from `accessToken` and `t2` from `freshAccessToken("…", "t1")`. Then assert exactly two requests and their headers. For "401 twice" stub both tokens to 401 and assert exactly two requests.

### Pattern 6: Capturing `System.Logger` output in tests

[VERIFIED: scratch spike run this session] `System.getLogger("de.morisoft.morimail.graph").log(INFO, …)` reached a `java.util.logging.Handler` attached to `java.util.logging.Logger.getLogger("de.morisoft.morimail.graph")` (level ALL) on JDK 21. Use this to assert (a) the "both bodies given" INFO is logged once per transport instance, and (b) no record from either package contains a secret, assertion or token sentinel. Attach the handler at the root logger for (b). Remove it in a `finally`. The `entra` package should not log at all (simplest guarantee for ENTRA-09); `graph` logs only the one INFO line and, at DEBUG, nothing about tokens or bodies.

### Anti-Patterns to Avoid

- **Settable timeouts or clocks in the public API:** violates D-02 and Rule 0.
- **Matching `error.message` text to classify errors:** the Graph errors page says not to ("shouldn't take any dependency on the `message` property values"). D-07 already matches only `error.code`.
- **Following redirects with a bearer token:** call `.followRedirects(HttpClient.Redirect.NEVER)` explicitly on the builder so a 3xx surfaces as a status (D-06 maps it to Permanent).
- **Putting raw response bodies, tokens, assertions or secrets in exception messages or logs.**
- **A shared helper class between `graph` and `entra`:** they must not import each other (REPO-12).

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| JSON for requests, token response, error bodies | String concatenation | Jackson `ObjectNode` / `readTree` | Escaping of subjects, names and HTML bodies |
| Form encoding of the token request | Manual escaping | `URLEncoder.encode(value, UTF_8)` per name and value | The secret and the assertion need URL-encoding (docs: "The client secret must be URL-encoded before being sent") |
| Path-segment encoding of the sender | Regex or manual percent-escaping | `URLEncoder.encode(email, UTF_8)` | Gives `%40` for `@` and `%2B` for `+`; the address validation already rejects whitespace, so the `+`-for-space quirk cannot occur [VERIFIED: spike printed `news%2Btag%40example.com`] |
| Base64 | Own encoder | `Base64.getEncoder()` for `contentBytes`; `Base64.getUrlEncoder().withoutPadding()` for JWT parts and the thumbprint | Standard JDK |
| RSASSA-PSS | Own padding | `Signature.getInstance("RSASSA-PSS")` + `PSSParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, 32, 1)` | Proven by round trip in the spike |
| PKCS12 parsing | Own parser | `KeyStore.getInstance("PKCS12")` | Standard |
| RFC 1123 date parsing | Own parser | `DateTimeFormatter.RFC_1123_DATE_TIME` | [VERIFIED: spike] parses `Wed, 21 Oct 2015 07:28:00 GMT` |
| Concurrency of the token fetch | Own queue or futures | `ReentrantLock` with re-check | Simplest correct single flight |
| Test doubles for HTTP | Own mini server | WireMock 3.13.2 | Already approved |

**Key insight:** every hard part here (crypto, JSON, HTTP, date parsing) is in the JDK or Jackson. The only custom logic is the policy: caching, error mapping, limits. Keep each class one screen of policy.

## Runtime State Inventory

Not applicable: this is a greenfield feature phase, not a rename, refactor or migration. None — verified by the phase description and CONTEXT (new packages `entra` and `graph`; one new line in `BuiltInTransports`; the sample's `realMailer` is replaced).

## Common Pitfalls

### Pitfall 1: Doclint failure on a public class with an implicit constructor or missing tags
**What goes wrong:** `./mvnw -Prelease verify -Dgpg.skip -pl morimail -am` (CI runs it) fails with a doclint warning, because `failOnWarnings` is on.
**Why it happens:** A public class with no explicit constructor gets a "use of default constructor" warning; any public method missing `@param`, `@return` or `@throws` is flagged. Phase 1 already hit this once (G-01-1: nine missing `@param` tags on a record constructor).
**How to avoid:** Give `GraphTransportProvider` an explicit public constructor with Javadoc. Write full Javadoc with `@param/@return/@throws` for every public member of `EntraClientCredentials` and `GraphTransportProvider`. Keep `GraphTransport` package-private. Run the release build locally in each library plan.
**Warning signs:** `warning: no comment` or `use of default constructor` in the Javadoc step.

### Pitfall 2: Thread-safety Javadoc test
**What goes wrong:** `PublicTypesDocumentThreadSafetyTest` fails.
**Why:** It requires the words "thread" or "immutable" in the Javadoc of every public type in `src/main/java`. [VERIFIED: PublicTypesDocumentThreadSafetyTest.java, `Pattern.compile("thread|immutable", Pattern.CASE_INSENSITIVE)`]
**How to avoid:** State thread safety on `EntraClientCredentials` (safe for concurrent use; at most one token request at a time) and on `GraphTransportProvider`.

### Pitfall 3: 429 is also a 4xx
**What goes wrong:** ENTRA-07 says 4xx → `AuthRequiredException` and 429 → Transient. A naive `status >= 400 && < 500` branch turns Entra throttling into "fix your credentials".
**How to avoid:** Check 429 first (transient, parse `Retry-After`), then 5xx (transient), then any other 4xx (auth required). Same ordering idea for Graph (D-06: specific rows first, class rule last).

### Pitfall 4: Graph error body shape and `request-id` location
**What goes wrong:** `request-id` comes back empty in the message.
**Why:** The Graph errors page shows `innerError` in its example body and `innererror` in its resource definition; the throttling page shows `innerError."request-id"`. [CITED: learn.microsoft.com/en-us/graph/errors, /graph/throttling]
**How to avoid:** Read the `request-id` response header first (case-insensitive in `HttpHeaders`), then fall back to `error.innerError["request-id"]` and `error.innererror["request-id"]`. That Graph sends `request-id` and `client-request-id` as response headers is [ASSUMED] (training knowledge; not on the pages I fetched), so the body fallback and a "-" placeholder are required. Never put the raw body in the message. Cap and clean the text (see Pitfall 11). The error body may be empty, non-JSON (proxy HTML) or lack `error`; every field must have a fallback.

### Pitfall 5: PKCS12 specifics
**What goes wrong:** (a) A keystore made by a modern keytool (JDK 20+) or OpenSSL 3 uses PBES2/AES-256 and a SHA-256 MAC, which an older JDK 17 patch level may not read. (b) Several entries or a trusted-cert entry confuse the "exactly one key" rule. (c) A non-RSA key cannot do PS256.
**How to avoid:** Generate the checked-in file with `keytool -J-Dkeystore.pkcs12.legacy -genkeypair -alias test -keyalg RSA -keysize 2048 -validity 3650 -dname "CN=morimail-test-throwaway" -keystore test-keystore-throwaway.p12 -storetype PKCS12 -storepass changeit -keypass changeit`. [VERIFIED: spike] `openssl pkcs12 -info -legacy` reports `MAC: sha1`, `pbeWithSHA1And3-KeyTripleDES-CBC` and `pbeWithSHA1And40BitRC2-CBC`; `KeyStore` on JDK 21 loaded it; `aliases()` gave one alias `test`; key algorithm `RSA`. CI on Temurin 17 is the proof for JDK 17. In the loader, count `isKeyEntry(alias)` entries (ignore trusted-cert entries), require exactly one, require `getKey(...)` to be a `PrivateKey` with algorithm `RSA` and `getCertificate(...)` to be an `X509Certificate`; everything else is `IllegalArgumentException` naming the file, cause attached, password never in the message (D-05). `.gitignore` ignores `*.pfx` but not `*.p12` [VERIFIED: `.gitignore:34` and `git check-ignore` printed "not ignored"]; no change needed.
**Also:** wipe nothing and keep no copy of the `char[]` password beyond the load call.

### Pitfall 6: The 3 MB rule and the 4 MB estimate interact
**What goes wrong:** An attachment of 3 MiB − 1 byte passes the per-file rule, but its base64 size is already `4 × ceil(3,145,727 / 3)` = 4,194,304 bytes = exactly 4 MiB, so adding the body and the JSON allowance puts the estimate above the limit and `validate` rejects it. Test authors expecting "just under 3 MiB is accepted" will be surprised.
**Why:** 3 MiB of raw data is exactly 4 MiB of base64. Base64 inflates by about 33% [CITED: learn.microsoft.com Q&A "Why is the Graph API response payload size about 1/3 larger", quoting the Exchange message-size page].
**How to avoid:** Keep both checks (GRAPH-05 asks for both). Choose the JSON allowance (suggest 16 KiB, one named constant) and say in the message which rule fired. Tests: exactly 3 MiB rejected by the per-file rule; two files totalling 3 MiB rejected by the total rule; a mid-size attachment (for example 2 MiB) plus a body of about 2 MiB of text rejected by the estimate; a ~2.9 MB file (the live probe, D-16) accepted: 2,900,000 bytes → 3,866,668 base64 bytes + allowance, below 4,194,304. Use `>` for "above the limit" in the estimate. Read sizes with `Files.size`; an `IOException` there becomes `InvalidMailException` naming the file.

### Pitfall 7: Percent-encoded path in tests
**What goes wrong:** A stub written with the decoded address (`/users/news@example.com/sendMail`) returns 404 for the encoded request.
**Why:** [VERIFIED: spike] WireMock 3.13.2 matches the raw encoded path: `urlEqualTo` and `urlPathEqualTo` with `%40` matched a `%40` request; `urlPathEqualTo("/…/y@example.com/…")` did not match the encoded request (404).
**How to avoid:** Write stubs with the encoded form (`newsletter%40example.com`). Build the request URI with `URI.create(base + "/users/" + encoded + "/sendMail")` so the escape is kept raw. Strip a trailing `/` from `baseUrl` first. Validate `baseUrl` (http or https scheme, a host) in the provider and raise `IllegalArgumentException` naming the option key, never echoing the value.

### Pitfall 8: Phase 2 tests and the journal in the sample
**What goes wrong:** (a) Three existing assertions reference the removed `GRAPH_NOT_AVAILABLE`: [VERIFIED: grep] `AccountFileTest.java:183`, `AccountFileTest.java:207`, `SendRunTest.java:134`, plus the constant at `SerialMail.java:19` and its use at `SerialMail.java:51`. (b) The E2E test, run against `example-campaign`, writes `example-campaign/journal.jsonl` into the checked-in directory unless `--journal` points at a temp file. (c) `AccountFileTest.realRunStopHoldsNoValueFromTheFile` puts a sentinel in `secretEnv` and asserts the sentinel never appears in output, which conflicts with a message that echoes the env var name.
**How to avoid:** Update those tests to call the new `mailer(a, env, …)` with a `Map.of()` env (no dependency on the real process environment) and the D-14 message. Use a message that names the key (`secretEnv` or `certificate.passwordEnv`), not the variable name, so the sentinel tests keep their meaning; D-14 allows either. `SendCommand` already catches `IllegalStateException | IllegalArgumentException` from the factory and prints `account: <message>` then exits 2 [VERIFIED: SendCommand.java:183-184], so `mailer(...)` only has to throw. Update the stale comment on `realMailer` ("This is the one method the Graph and Entra phase replaces"). In the E2E test pass `--journal <temp>/journal.jsonl`, write the account file into a temp dir with `certificate.path` pointing at a temp copy of the keystore (use forward slashes: a backslash starts an escape in `.properties`), and inject the recording sleep so the 3 s pacing at `--rate 20` costs no time. The example campaign's relative path works because Surefire runs in the module directory [VERIFIED: `ExampleCampaignTest.java` uses `Path.of("example-campaign")`].

### Pitfall 9: The keystore reaches the sample only through the test-jar
**What goes wrong:** A `Path` into `morimail/src/test/resources` does not exist from the sample module.
**How to avoid:** Library tests use `Path.of("src/test/resources/test-keystore-throwaway.p12")` (module directory). The sample reads `getResourceAsStream("/test-keystore-throwaway.p12")` from the test-jar and copies it to a temp file (D-12). Test resources are inside the test-jar (`maven-jar-plugin` test-jar execution in `morimail/pom.xml`), and the reactor falls back to `target/test-classes` for `mvn test`. The README sentence is required (TEST-01).

### Pitfall 10: Timeout testing without settable timeouts
**What goes wrong:** With fixed 10 s / 30 s constants a timeout test either waits 30 s or is skipped.
**How to avoid:** See Pattern 1. Also note [ASSUMED] that `HttpRequest.timeout` guards the wait for the response, not necessarily a slow body read; error and token bodies are small, so accept this. Treat `HttpTimeoutException` and every `IOException` as transient. On `InterruptedException`, call `Thread.currentThread().interrupt()` and throw a `TransientMailException`.

### Pitfall 11: Error text is untrusted input
**What goes wrong:** `error.message` / `error_description` can contain line breaks (the Entra sample description contains `\r\n` plus Trace ID and Correlation ID lines) and arbitrary length; they end up in exceptions, the console and the journal.
**How to avoid:** One small private method per package: replace control characters with a space, collapse runs of whitespace, cap at about 300 characters. Duplicate it (about 5 lines). Keep the Entra `error` and `error_description` and Graph `error.code`, message and request id in a fixed layout, for example `Graph sendMail failed: HTTP 403, code ErrorAccessDenied, request-id 0000…, message: …`. No raw body, ever.

### Pitfall 12: `TransientMailException` constructor ambiguity
`new TransientMailException(msg, null)` is ambiguous between `(String, Throwable)` and `(String, Duration)`. [VERIFIED: TransientMailException.java:33 and :45] Pass a typed `Duration retryAfter` variable (null allowed there) or the `Throwable` variant explicitly. A negative `Duration` throws `IllegalArgumentException`, so the `Retry-After` parser must clamp: negative or past date → `Duration.ZERO`, unparsable → null. Parse delta-seconds with a digits-only check and catch `NumberFormatException` for absurdly long digit strings.

### Pitfall 13: A `graph` account without a token provider
`TransportProvider.create` receives `auth` possibly `null` [VERIFIED: TransportProvider.java:28 `MailTransport create(Map<String, Object> options, TokenProvider auth);`]. A Graph transport without one can never send, so throw `IllegalArgumentException` from `create` (the Javadoc of `create` already allows it for invalid options) rather than a late `NullPointerException`.

### Pitfall 14: Live profile only runs when a test is tagged
[VERIFIED: spike] With a `@Tag("live")` test, the default build ran 0 live tests, `-Plive` ran only it and failed fast with the missing-env message, and `-Plive` over the full reactor also passed the sample module with 0 tests. The documented owner command is `./mvnw -Plive test -pl morimail` (add `-o` if desired). The live test must read all env vars first and fail with one message listing every missing required name (D-17).

## Code Examples

Values below come from the verified quotes in this document: the endpoint path and form fields from the Microsoft client-credentials page, the header and claim names from the certificate-credentials page, the JSON shape from the sendMail example, and the repo signatures quoted in "Integration Points". Treat the code as a sketch; the planner decides names.

### 1. Token request form (both flows)
```java
// Source: learn.microsoft.com/en-us/entra/identity-platform/v2-oauth2-client-creds-grant-flow
// secret flow fields: client_id, scope, client_secret, grant_type=client_credentials
// certificate flow: client_secret replaced by client_assertion_type + client_assertion
static String form(Map<String, String> fields) {
    StringJoiner joiner = new StringJoiner("&");
    fields.forEach((k, v) -> joiner.add(
            URLEncoder.encode(k, UTF_8) + "=" + URLEncoder.encode(v, UTF_8)));
    return joiner.toString();
}
// client_assertion_type = "urn:ietf:params:oauth:client-assertion-type:jwt-bearer"
// URL: authority + "/" + tenantId + "/oauth2/v2.0/token"   (authority without trailing slash)
// headers: Content-Type: application/x-www-form-urlencoded
```

### 2. PS256 assertion (shape proven by round trip in the spike)
```java
// Header per certificate-credentials page: alg=PS256, typ=JWT, x5t#S256 = base64url(SHA-256(cert DER))
Map<String, Object> header = new LinkedHashMap<>();
header.put("alg", "PS256");
header.put("typ", "JWT");
header.put("x5t#S256", B64.encodeToString(MessageDigest.getInstance("SHA-256").digest(cert.getEncoded())));
long now = clock.instant().getEpochSecond();
Map<String, Object> claims = new LinkedHashMap<>();
claims.put("aud", tokenUrl);            // the token endpoint URL actually used
claims.put("exp", now + 600);           // at most 10 minutes (spec 7.2)
claims.put("iss", clientId);
claims.put("jti", UUID.randomUUID().toString());
claims.put("nbf", now);
claims.put("sub", clientId);
claims.put("iat", now);
String signingInput = b64(MAPPER.writeValueAsBytes(header)) + "." + b64(MAPPER.writeValueAsBytes(claims));
Signature s = Signature.getInstance("RSASSA-PSS");
s.setParameter(new PSSParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, 32, 1));
s.initSign(privateKey);
s.update(signingInput.getBytes(StandardCharsets.US_ASCII));
String assertion = signingInput + "." + B64.encodeToString(s.sign());   // B64 = Base64.getUrlEncoder().withoutPadding()
```
[VERIFIED: scratch spike] signing with the keystore's `RSA` private key and verifying with `cert.getPublicKey()` through `RSASSA-PSS` and the same `PSSParameterSpec` returned true; signature length 256 bytes. A test should verify with `cert.getPublicKey()` and the same parameters, decode the header and claims with Jackson, and compare them with the injected clock. An optional one-time manual cross-check with `openssl` (`rsa_padding_mode:pss`, `rsa_pss_saltlen:32`, `rsa_mgf1_md:sha256`) was verified in an earlier research pass per `.claude/CLAUDE.md`.

### 3. Retry-After parser (duplicate in both packages)
```java
static Duration retryAfter(HttpHeaders headers) {            // null when absent or unusable
    String v = headers.firstValue("Retry-After").map(String::trim).orElse("");
    if (v.matches("\\d{1,9}")) return Duration.ofSeconds(Long.parseLong(v));
    try {
        Instant at = ZonedDateTime.parse(v, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
        Duration d = Duration.between(Instant.now(), at);
        return d.isNegative() ? Duration.ZERO : d;
    } catch (DateTimeParseException e) {
        return null;
    }
}
```

### 4. sendMail JSON body
```json
// Source: learn.microsoft.com/en-us/graph/api/user-sendmail (examples 1 and 3), fileAttachment resource page
{
  "message": {
    "subject": "…",
    "body": { "contentType": "HTML", "content": "…" },
    "toRecipients": [ { "emailAddress": { "address": "reader@example.org", "name": "optional" } } ],
    "ccRecipients": [], "bccRecipients": [], "replyTo": [],
    "attachments": [ { "@odata.type": "#microsoft.graph.fileAttachment",
                       "name": "info.pdf", "contentType": "application/pdf", "contentBytes": "<base64>" } ]
  },
  "saveToSentItems": false
}
```
`contentType` is `"HTML"` when `message.html()` is non-null, otherwise `"Text"` with `message.text()`. Add `saveToSentItems` only when the option is false, as a JSON boolean. Add empty recipient arrays only if you prefer; omitting them is simpler and valid.

### 5. Send flow with the single 401 retry
```java
public void send(MailMessage m) throws MailException {
    byte[] body = build(m);                                  // reads attachments; IOException → InvalidMailException naming the file
    URI uri = URI.create(baseUrl + "/users/" + URLEncoder.encode(m.from().email(), UTF_8) + "/sendMail");
    String token = auth.accessToken(SCOPE);                  // SCOPE = "https://graph.microsoft.com/.default"
    HttpResponse<byte[]> r = post(uri, token, body);
    if (r.statusCode() == 401) {
        token = auth.freshAccessToken(SCOPE, token);
        r = post(uri, token, body);                          // exactly one retry
    }
    if (r.statusCode() / 100 == 2) return;
    throw map(r, wasRetried401);
}
// map: 401 (after retry) → mailboxHint(code) ? SenderRejected : AuthRequired;  403, 404 → SenderRejected;
//      429 → Transient(retryAfter); 5xx → Transient(retryAfter); others → Permanent
static boolean mailboxHint(String errorCode) {               // the one place the live test may correct (D-07)
    return errorCode != null && errorCode.toLowerCase(Locale.ROOT).contains("mailbox");
}
```
`post` builds `HttpRequest.newBuilder(uri).timeout(REQUEST).header("Authorization", "Bearer " + token).header("Content-Type", "application/json").POST(BodyPublishers.ofByteArray(body))` and sends with `BodyHandlers.ofByteArray()`. An `IOException` becomes `TransientMailException` with the cause; the cause must not carry the token (the JDK does not put headers in exception messages, but never add them yourself).

### 6. Sample wiring (D-11, D-14)
```java
static Mailer realMailer(AccountFile a) {
    return mailer(a, System::getenv, DEFAULT_AUTHORITY, DEFAULT_GRAPH_URL);   // two sample-local constants
}
static Mailer mailer(AccountFile a, Function<String, String> env, URI authority, String graphBaseUrl) {
    EntraClientCredentials creds;
    if (a.secretEnv() != null) {
        String secret = env.apply(a.secretEnv());
        if (secret == null || secret.isEmpty()) {
            throw new IllegalStateException("secretEnv: the environment variable it names is not set or is empty");
        }
        creds = EntraClientCredentials.withSecret(a.tenantId(), a.clientId(), secret);
    } else {
        String pw = env.apply(a.certificatePasswordEnv());
        if (pw == null || pw.isEmpty()) {
            throw new IllegalStateException("certificate.passwordEnv: the environment variable it names is not set or is empty");
        }
        creds = EntraClientCredentials.withCertificate(a.tenantId(), a.clientId(),
                Path.of(a.certificatePath()), pw.toCharArray());            // IAE on a bad key store (D-05)
    }
    MailAccount account = MailAccount.builder(a.id()).transport("graph").auth(creds.authority(authority))
            .option("baseUrl", graphBaseUrl).defaultFrom(a.from()).build();
    return Mailer.of(account);
}
```
`authority(URI)` must return `EntraClientCredentials` so it chains before the value is widened to `TokenProvider`.

## Spec vs Docs: Differences Found (for `docs/architecture.md`)

| # | Topic | Spec says | Current Microsoft docs say | Action |
|---|-------|-----------|---------------------------|--------|
| 1 | One body vs two | §6.1: "assumes the JSON format carries one body; confirm" | Message `body` is one `itemBody`: "It can be in HTML or text format" [CITED: learn.microsoft.com/en-us/graph/api/resources/message] | Assumption confirmed: HTML if present, else text; sending both needs MIME (later) |
| 2 | 4 MB request limit | D-09: confirm 4 MB | **Not on any reference page I fetched** (sendMail, large-attachments, throttling, throttling-limits, send-mail overview). Only a Microsoft Q&A answer by Microsoft external staff: "the request size limit is generally around 4 MB ... you will encounter a 413 error" [CITED: learn.microsoft.com/en-in/answers/questions/1824204/graph-api-user-sendmail-request-size-limit]. The errors page lists `413 Request Entity Too Large: The request size exceeds the maximum limit` without a number. | Keep the 4 MiB constant as a LOW-MEDIUM confidence estimate; record that it is community-sourced and hedged ("around"). The live test cannot probe it (deferred). |
| 3 | 3 MB origin | §6.2 | The 3 MB figure is on the large-attachments page for single POST to a message's `attachments` navigation property: "If the file size is under 3 MB, do a single POST" [CITED: learn.microsoft.com/en-us/graph/outlook-large-attachments]. The `sendMail` page states no attachment size limit. | The spec rule (reject 3 MB or more) is conservative and stays; note it is inferred for `sendMail` |
| 4 | `saveToSentItems` type | boolean, send only when false | The docs example sends the string `"false"` in JSON; the SDK snippets send a boolean; parameter type is Boolean; "Specify it only if the parameter is false; default is true" [CITED: learn.microsoft.com/en-us/graph/api/user-sendmail] | Send a JSON boolean `false`. Low risk. |
| 5 | Stale RS256 sample | §7.2 already warns | The client-credentials page's own encoded assertion sample still decodes to an RS256/`x5t` header (`eyJhbGciOiJSUzI1NiIsIng1dCI6…`), while the certificate-credentials page says `alg` "Should be **PS256**" and `x5t#S256` [CITED: both pages] | Follow the certificate-credentials page; PS256 acceptance by the live service is only provable by the live test (no RS256 fallback, per ROADMAP) |
| 6 | Token response fields | `access_token`, `token_type`, `expires_in` | The documented success body has exactly those three, `expires_in` as a number (3599) [CITED: client-creds page]. Real responses may add fields, so parse only what is needed. | Require a non-empty `access_token` and a numeric `expires_in`; always send `Bearer` |
| 7 | Token error status | §7.3: "HTTP 400 with error and error_description" | The documented example is 400; the field set is `error`, `error_description`, `error_codes`, `timestamp`, `trace_id`, `correlation_id` [CITED: client-creds page]. Other 4xx are possible (that invalid credentials return 401 is [ASSUMED] from training knowledge). | Map every 4xx except 429 to `AuthRequiredException`, as ENTRA-07 already says |
| 8 | Error body casing | §6.3 silent | `innerError` in the errors-page example and the throttling sample, `innererror` in the errors-page resource definition [CITED] | Read both spellings; header first (Pitfall 4) |
| 9 | `Retry-After` format | D-10 supports both | Graph docs say "Wait the number of seconds specified in the Retry-After header" [CITED: learn.microsoft.com/en-us/graph/throttling]; no HTTP-date form documented | Keep both forms (harmless); note the docs only show seconds |
| 10 | `from` property | spec: URL mailbox only | `message.from` "must correspond to the actual mailbox used"; changing it needs sendAs rights [CITED: message resource and outlook-create-send-messages] | Do not send `from` in the body (Open Question 2) |
| 11 | Limits | spec 8.3 guess of 20/min | Outlook service limits per app and mailbox: "10,000 API requests in a 10-minute period", "Four concurrent requests", "150 megabytes (MB) upload (PATCH, POST, PUT) in a 5-minute period" [CITED: learn.microsoft.com/en-us/graph/throttling-limits]; at most 500 recipients per message [CITED: message resource] | A serial sender stays far inside these; useful for DOCS-03 in Phase 4. No code. |

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| JDK | Build and tests | ✓ | Temurin 21.0.13 (`--release 17` compiles) | CI runs Temurin 17 (existing workflow) |
| Maven wrapper (3.10.1) | Build | ✓ | 3.10.1 in `~/.m2/wrapper` | — |
| `keytool` | Generating the throwaway PKCS12 | ✓ | JDK 21 | — |
| `openssl` | Optional signature cross-check and `pkcs12 -info` | ✓ | 3.x (`/usr/bin/openssl`) | Skip; the JDK round trip is the required test |
| WireMock 3.13.2 and its Jetty dependencies | Tests | ✓ in local repo | 3.13.2 | — (offline build with WireMock proven in the spike) |
| Network | Not needed by any test | n/a | — | All HTTP is in-process on localhost |
| JDK 17 runtime | Final proof of PSS and PKCS12 on 17 | ✗ locally | — | CI Temurin 17 |

**Missing dependencies with no fallback:** none.
**Missing dependencies with fallback:** JDK 17 runtime locally; CI covers it.

## Validation Architecture

Skipped: `workflow.nyquist_validation` is explicitly `false` in `.planning/config.json` [VERIFIED: config.json]. Test expectations are already listed per requirement above (TEST-01..03, CLI-14, REPO-08). Quick commands for the planner: `./mvnw -o -B test -pl morimail` (library), `./mvnw -o -B verify` (everything), `./mvnw -B -Prelease clean verify -Dgpg.skip -pl morimail -am` (Javadoc strictness), `./mvnw -Plive test -pl morimail` (owner only; fails fast naming the missing env vars).

## Security Domain

`security_enforcement` is enabled and `security_asvs_level` is 1 [VERIFIED: config.json].

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | yes (service-to-service credentials) | Client-credentials flow with certificate assertion (PS256) or secret; credentials only from env vars or a key store; never logged |
| V3 Session Management | no | Tokens are cached in memory for one process; no sessions |
| V4 Access Control | partly | Server-side: Graph/Exchange RBAC decide which mailboxes the app may use; the library maps 403/404 to `SenderRejectedException`; no local from-address check (out of scope) |
| V5 Input Validation | yes | `Mail.Builder` validates addresses and CR/LF already; the Graph transport validates options (unknown keys, types, `baseUrl`), attachment sizes, and percent-encodes the sender into the path |
| V6 Cryptography | yes | JDK `Signature` RSASSA-PSS, `MessageDigest` SHA-256, `KeyStore` PKCS12; no hand-rolled crypto |
| V7 Error handling and logging | yes | No tokens, secrets, assertions or bodies in logs or messages; error text cleaned and capped |
| V9 Communication | yes | HTTPS to the real endpoints; bearer token only to the configured Graph base URL; `followRedirects(NEVER)` |

### Known Threat Patterns

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Secret or token leak through logs, exception messages or the journal | Information disclosure | No logging in `entra`; messages built from fixed text plus cleaned Graph/Entra fields; sentinel tests; the sample journal already stores only error class and message |
| Bearer token sent to an attacker-controlled host after a redirect | Information disclosure | `HttpClient.Redirect.NEVER`; a 3xx maps to `PermanentMailException` |
| Header or URL injection through the sender address | Tampering | Address validation rejects whitespace, CR/LF and `, ; < > ( ) [ ] \ " :`; the path segment is percent-encoded |
| Log or console injection through error text from the server | Tampering | Strip control characters, collapse whitespace, cap length |
| Concurrent token stampede or replay | Denial of service | Single-flight lock; fresh `jti` and short-lived assertion per request |
| Throwaway PKCS12 mistaken for a leaked credential | Information disclosure (perception) | File name `test-keystore-throwaway.p12`, password `changeit`, one README sentence |
| Real mail or real tenant data in fixtures | Information disclosure | Zero GUIDs and `example.com`/`example.org` only; WireMock never leaves localhost; live test is owner-run |

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| RS256 assertion with SHA-1 `x5t` | PS256 with `x5t#S256` | Current certificate-credentials page | Spec §7.2 already follows it; some Microsoft pages still show the old form (difference 5) |
| Application Access Policies | RBAC for Applications (union with Entra grants) | Current Exchange page | Context for the out-of-scope live probe only; docs belong to Phase 4 |
| Graph docs with `saveToSentItems: "false"` strings | Boolean parameter | Docs examples mixed | Send a boolean (difference 4) |

**Deprecated/outdated:** the RS256 `x5t` sample on the client-credentials page.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Graph returns `request-id` and `client-request-id` as response headers | Pitfall 4 | Low: the body fallback and a "-" placeholder cover it |
| A2 | Entra returns 401 for invalid client credentials (not only 400) | Spec vs Docs 7 | None for code: every 4xx except 429 maps to auth-required |
| A3 | `HttpRequest.timeout` does not cover a slow body read | Pitfall 10 | Low: bodies are small; a stalled body could block a send |
| A4 | A package-private `Clock` and timeout seam is acceptable under Rule 0 | Pattern 1 | Low: fall back to `expires_in` ≤ 299, `Fault`-based network tests and review for timeouts |
| A5 | A 16 KiB JSON allowance is "small" and large enough for typical recipient lists | Pitfall 6 | Low: only affects when `validate` rejects; the live 413 behaviour is unknown |
| A6 | Graph accepts a percent-encoded `@` (`%40`) in the `/users/{id}` segment | GRAPH-01 | Medium: standard HTTP, REQUIREMENTS demands it; the live test proves it |
| A7 | Graph status for "mailbox does not exist / not licensed" is 404 or 401 with a code containing `Mailbox` | D-07 | Medium: community reports only (404 `MailboxNotEnabledForRESTAPI`, 401 for no licensed mailbox); the live test corrects the mapping, as planned |
| A8 | PS256 assertions are accepted by the live token endpoint | ENTRA-02 | Medium: the docs say PS256, nothing here proves acceptance; owner's live test |
| A9 | The Graph 4 MB figure ("generally around") is close enough to use as the estimate ceiling | D-09 | Medium: too high gives a 413 mapped to Permanent; too low rejects valid mails |

## Open Questions (RESOLVED)

1. **How should the sample phrase env-var problems (D-14) given the Phase 2 "no value from the file" tests?** (RESOLVED)
   - Known: `SendCommand` prints `account: <message>` and exits 2 for `IllegalStateException`/`IllegalArgumentException`; Phase 2 tests use a sentinel in `secretEnv`.
   - Unclear: whether to name the variable or the key.
   - Recommendation: name the key (`secretEnv`, `certificate.passwordEnv`, `certificate.path`); no existing test needs changing except the three `GRAPH_NOT_AVAILABLE` lines.
   - **Resolution:** adopted as recommended. Messages name the key, never the variable or its value. 03-05 Task 1 adds `secretEnv: ...` and `certificate.passwordEnv: ...` and updates the three Phase 2 assertions that used `GRAPH_NOT_AVAILABLE`. 03-05 Task 2 adds the `certificate.path: ...` messages and wraps the library's key-store message, which names the file.

2. **Does the recipient see the mailbox's display name when `message.from` is not sent?** (RESOLVED)
   - Known: spec §6.1 sends no `from`; the docs say `from` must match the actual mailbox.
   - Unclear: display-name behaviour (STATE.md lists "the `from` display name" as live-test knowledge).
   - Recommendation: do not send `from`; ask the owner to note what the recipient sees in the STOP report. `MailAddress.displayName()` of `from` is simply unused by this transport.
   - **Resolution:** adopted as recommended. 03-03 Task 1 builds the sendMail JSON with no `from` key (flagged assumption "No `from` in the body"), and the display-name behaviour is an item of the STOP report in 03-05's success criteria.

3. **JSON allowance and `validate` message wording.** Pick one constant (suggest 16 KiB) and one message format naming the file or "estimated request size" and the limit in MiB. Planner's call. (RESOLVED)
   - **Resolution:** 03-03 Task 2 sets `JSON_ALLOWANCE = 16L * 1024` and uses three messages: "attachment <filename> has <size> bytes; Graph takes attachments below 3 MiB (3145728 bytes)", "attachments <names> have <total> bytes together; Graph takes below 3 MiB (3145728 bytes) in total" (names: every attachment file name, comma-separated, in attach order) and "estimated request size <estimate> bytes is above the limit of 4 MiB (4194304 bytes)". The body text is counted as the JSON-escaped UTF-8 bytes that send writes, so the allowance has to cover only the subject, the addresses and the JSON syntax.

4. **Where `LiveGraphTest` fails fast.** Recommendation: a `@BeforeAll` that reads all env vars and throws one `IllegalStateException` listing the missing required names; probe methods use `Assumptions.assumeTrue` on their own optional variable. (RESOLVED)
   - **Resolution:** adopted as recommended. In 03-05 Task 3, `@BeforeAll` throws `IllegalStateException` "Live test: missing environment variables: <names>" before any network call, and the out-of-scope and big-attachment probes use `Assumptions.assumeTrue` on their own variables.

## Notes for the Planner (waves and gotchas)

- **Wave A (parallel):** Entra (class, loader, keystore resource, README sentence, tests) and Graph (provider, transport, tests with a stub `TokenProvider`). They share no code. Add the WireMock test dependency to `morimail/pom.xml` in whichever plan lands first, or in a tiny shared plan to avoid a merge conflict.
- **Wave B:** `BuiltInTransports` line (update its "No built-in transport exists yet" Javadoc), registry tests, `ImportRulesTest` hardening, sample wiring.
  - Registry test: `TransportRegistry.load().find("graph")` works; `ServiceLoader.load(TransportProvider.class)` ids do not include `graph` (so it is not registered through the services file); a duplicate external `graph` fails with the existing "(built-in)" message; `MailAccount.builder(..).transport("graph").auth(stub).build()` works; `auth == null` is an `IllegalArgumentException`.
  - `ImportRulesTest` already scans `.../graph` and `.../entra` for mentions of each other [VERIFIED: ImportRulesTest.java, the two `javaFiles(module.resolve(CORE).resolve("graph"/"entra"), Integer.MAX_VALUE)` loops], but it returns an empty list silently if a directory is missing. Add `assertThat(...).isNotEmpty()` for both after this phase so the rule cannot pass vacuously.
- **Wave C:** E2E test (CLI-14) and `LiveGraphTest`; owner-facing docs of the live run stay in Phase 4 (DOCS-01), but the live test's Javadoc must list its env vars (D-17) and the `docs/architecture.md` deviations above.
- **Every library plan:** run `./mvnw -o -B verify` and the scoped release build; both are CI steps.
- **`docs/architecture.md` additions from this phase:** differences 1-11 above, the D-16 live-test deviation, `authority(URI)`, `char[]` password, `IllegalArgumentException` for key-store problems, class-based status mapping beyond §6.3, and the Retry-After HTTP-date support.
- **Commit identity:** export `GIT_AUTHOR_*` and `GIT_COMMITTER_*` as Stephan Wald before every commit (workspace env overrides local config).
- **STOP:** after the phase, report to the owner; the STOP report items are in ROADMAP Phase 3.

## Sources

### Primary (HIGH confidence)
- Repo files read this session: `docs/spec.md`, `.planning/REQUIREMENTS.md`, `.planning/STATE.md`, `03-CONTEXT.md`, `pom.xml`, `morimail/pom.xml`, `morimail-sample-serial/pom.xml`, `TokenProvider.java`, `TransportProvider.java`, `MailTransport.java`, `BuiltInTransports.java`, `TransportRegistry.java`, `MailAccount.java`, `Mailer.java`, `TransientMailException.java`, `ImportRulesTest.java`, `PublicTypesDocumentThreadSafetyTest.java`, `SerialMail.java`, `SendCommand.java`, `AccountFile.java`, the sample tests, `.github/workflows/ci.yml`.
- learn.microsoft.com/en-us/graph/api/user-sendmail (fetched; updated_at 2026-06-19): request, body, 202, saveToSentItems, attachment example.
- learn.microsoft.com/en-us/entra/identity-platform/certificate-credentials (fetched; updated 2026-06-15): PS256, `x5t#S256`, claims, exp guidance.
- learn.microsoft.com/en-us/entra/identity-platform/v2-oauth2-client-creds-grant-flow (fetched; updated 2026-06-15): form fields, success and error response.
- learn.microsoft.com/en-us/graph/outlook-large-attachments, /graph/errors, /graph/throttling, /graph/throttling-limits, /graph/api/resources/message, /graph/api/resources/fileattachment, /graph/outlook-create-send-messages, /graph/outlook-things-to-know-about-send-mail.
- learn.microsoft.com/en-us/exchange/permissions-exo/application-rbac (union of grants, 30 min to 2 h cache, `Application Mail.Send`).
- repo.maven.apache.org `org/wiremock/wiremock/maven-metadata.xml` (3.13.2 latest 3.x).
- Scratch spike (outside the repo, in the session scratchpad): PSS sign and verify, legacy PKCS12 via keytool and openssl, JUL capture of `System.Logger`, RFC 1123 parse, WireMock raw-path matching, offline Maven build with WireMock, `-Plive` mechanics.

### Secondary (MEDIUM confidence)
- Microsoft Q&A answer (Microsoft external staff) on the sendMail ~4 MB request limit and 413: learn.microsoft.com/en-in/answers/questions/1824204/graph-api-user-sendmail-request-size-limit.
- Microsoft Q&A answer on base64 growing size by about 33%: learn.microsoft.com/en-us/answers/questions/1878897/why-is-the-graph-api-response-payload-size-about-1.

### Tertiary (LOW confidence)
- Community reports about `MailboxNotEnabledForRESTAPI` (404), 401 for unlicensed mailboxes, and 403 with RBAC scoping (WebSearch results, Microsoft Q&A and forums); input to D-07 only, to be corrected by the live test.

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH. No new runtime dependency; WireMock version checked on Central and already pinned.
- Architecture: HIGH. Integration points read from the code; spikes prove the risky mechanics.
- Pitfalls: HIGH for the repo-specific ones (verified by reading and by spike); MEDIUM for Graph's real status codes and the 4 MB ceiling.

**Research date:** 2026-11-22
**Valid until:** 2026-12-22 (Microsoft docs and the Graph limits change; re-check the 4 MB figure and the error table against the live test results before Phase 4)
