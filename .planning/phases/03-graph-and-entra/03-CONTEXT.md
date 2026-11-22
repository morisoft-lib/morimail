# Phase 3: Graph and Entra - Context

**Gathered:** 2026-11-22
**Status:** Ready for planning

<domain>
## Phase Boundary

Phase 3 delivers the `EntraClientCredentials` token provider (secret, key + certificate, PKCS12) in `de.morisoft.morimail.entra` and the built-in `graph` transport in `de.morisoft.morimail.graph`, registered with one line in `BuiltInTransports`. It also wires both into the sample's real-run `Mailer` (replacing `SerialMail.realMailer`), adds an end-to-end sample test against WireMock for both the token endpoint and Graph (CLI-14), and adds the owner-run `live` test. Requirements: ENTRA-01..09, GRAPH-01..09, TEST-01..03, CLI-14, REPO-08.

After this phase there is a hard STOP (spec §10.2 milestone 4). Report to the owner and do not start Phase 4.

</domain>

<decisions>
## Implementation Decisions

### Entra public API
- **D-01:** The three static factories stay exactly as in spec §5.1 (`withSecret`, `withCertificate(key, cert)`, `withCertificate(Path p12, password)`). The authority host (ENTRA-04) is overridden with one instance copy method, `authority(URI)`, which returns a new immutable instance. The default is the public cloud `https://login.microsoftonline.com`. There is no builder and there are no overloads. — **Reversibility:** one-way. It is a public method on a published type after 0.1.0.
- **D-02:** The connect and request timeouts (ENTRA-08, GRAPH-09) are fixed private constants in each class, for example connect 10 s and request 30 s. They are not settable and there is no `timeout` option key (Rule 0; they can be made settable later without a break).
- **D-03:** The PKCS12 password parameter is `char[]` (the `KeyStore.load` convention). The sample calls `System.getenv(..).toCharArray()`. — **Reversibility:** one-way. It is a public signature.
- **D-04:** The PKCS12 file must contain exactly one private-key entry, which is used with its certificate. Zero or several key entries are an error naming the file. There is no alias parameter.
- **D-05:** PKCS12 load failures (missing file, wrong password, not exactly one key) throw `IllegalArgumentException` with the cause attached. The message names the file and never the password. This is a setup error, like an unknown transport ID, not a `MailException`.

### Graph error mapping and limits
- **D-06:** Responses not listed in §6.3 are mapped by status class. Any 2xx is accepted. Every 5xx is `TransientMailException` (with `Retry-After` when present). Every other 4xx, and any 1xx/3xx, is `PermanentMailException`. The specific rows of §6.3 (403/404 → `SenderRejectedException`, 401 handling, 429 → transient) take precedence over the class rule. The live test corrects these later.
- **D-07:** If a 401 comes back again after the fresh-token retry (GRAPH-07), the response counts as "pointing at the mailbox" when Graph's `error.code` contains `Mailbox` (case-insensitive, `Locale.ROOT`, e.g. `MailboxNotEnabledForRESTAPI`). That gives `SenderRejectedException`; anything else gives `AuthRequiredException`. The match lives in one place so the live test can correct it. `error.message` is not matched.
- **D-08:** "3 MB" in GRAPH-05 means 3 × 1024 × 1024 = 3,145,728 bytes, held as one constant in the Graph transport. A single attachment at or above it, or attachments whose total reaches it, are rejected.
- **D-09:** The request-size limit is one constant of 4 × 1024 × 1024 bytes. It is compared with an estimate: the base64 size of each attachment (4/3, rounded up to a multiple of 4), plus the UTF-8 length of the body sent, plus a small fixed JSON allowance. The researcher confirms the 4 MB figure against the current Microsoft docs and records any difference in `docs/architecture.md`.
- **D-10:** `Retry-After` is parsed in both forms: delta-seconds, and an RFC 1123 HTTP-date (`DateTimeFormatter.RFC_1123_DATE_TIME`, where a date in the past gives zero). A value that cannot be parsed gives an empty `retryAfter`. The same parser is used for the Entra 429 if it sends the header.

### Sample end-to-end wiring (CLI-14)
- **D-11:** `SerialMail.realMailer(AccountFile)` delegates to a new package-private `mailer(AccountFile account, Function<String,String> env, URI authority, String graphBaseUrl)`, passing `System::getenv` and the public defaults. The E2E test passes `a -> mailer(a, Map.of(...)::get, wmAuthority, wmGraphUrl)` through the existing `commandLine(Function<AccountFile, Mailer>, sleep)` seam. `account.properties` gets no new keys (this keeps Phase 2 D-01). The account is built through the registry (`transport("graph")` plus the `baseUrl` option), not by constructing the transport directly.
- **D-12:** The E2E test uses the certificate flow: the checked-in throwaway PKCS12 from the library test-jar, copied to a temp dir and referenced through `certificate.path` + `certificate.passwordEnv`. The secret flow is covered by the library's Entra tests.
- **D-13:** The E2E test asserts the following. (a) The example campaign produces 5 `POST /users/{from}/sendMail` calls, each to the right recipient with both attachments, plus exactly one token request (cache reuse), and exits 0. (b) A second run against the same journal makes 0 Graph calls and reports 5 skipped. (c) A 403 stubbed for one recipient stops the run with exit 3 and journals the failure.
- **D-14:** On a real run, an unset or empty password/secret env var, or a PKCS12 that cannot be loaded (D-05), is reported as a pre-flight problem with exit code 2, before any send. The message names the env var or the key, never the value. This replaces the Phase 2 "graph not available" stop and `GRAPH_NOT_AVAILABLE`.

### Live test (REPO-08)
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

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Binding spec
- `docs/spec.md` §2 (Registry and dependency requirements): built-in list, no Graph assumptions in the core, opaque options, limits in `validate`.
- `docs/spec.md` §5, §5.1, §5.2: core contracts, usage, error hierarchy.
- `docs/spec.md` §6.1–§6.3: Graph call, attachments, error-mapping table.
- `docs/spec.md` §7.1–§7.3: token request, certificate assertion (PS256, `x5t#S256`), caching and errors.
- `docs/spec.md` §10.1, §10.2: tests, milestone 4, STOP after milestone 4.
- `docs/spec.md` §11 / `CLAUDE.md`: Rule 0, no secrets, never send real mail, read the Microsoft docs before coding.
- `.planning/REQUIREMENTS.md`: ENTRA-01..09, GRAPH-01..09, TEST-01..03, CLI-14, REPO-08.
- `.planning/ROADMAP.md` Phase 3: success criteria, scope notes (parallel Entra/Graph waves, wiring last), STOP checkpoint list.
- `docs/architecture.md`: where spec deviations and doc differences are recorded.

### Microsoft documentation (read the current versions before coding)
- https://learn.microsoft.com/en-us/graph/api/user-sendmail: sendMail request, 202, `saveToSentItems`, JSON body.
- https://learn.microsoft.com/en-us/graph/outlook-large-attachments: 3 MB direct-attachment limit.
- https://learn.microsoft.com/entra/identity-platform/certificate-credentials: PS256 and the `x5t#S256` assertion (use the current English page, not the old RS256/SHA-1 copy).
- https://learn.microsoft.com/en-us/entra/identity-platform/v2-oauth2-client-creds-grant-flow: token endpoint form fields and error response.
- https://learn.microsoft.com/Exchange/permissions-exo/application-rbac: RBAC for Applications (context for the out-of-scope probe).

### Prior phase context
- `.planning/phases/01-library-core/01-CONTEXT.md`: D-11 (`MailAccount.transport()`), D-20 (release build scope).
- `.planning/phases/02-serial-mail-sample/02-CONTEXT.md`: D-01 (only `realMailer` is replaced), D-03 (account keys), D-09..D-13 (retry, exit codes).

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `morimail/src/main/java/de/morisoft/morimail/TokenProvider.java`: already has `freshAccessToken(scope, rejectedToken)` with a default that calls `accessToken` again. `EntraClientCredentials` overrides it (compare-and-drop, ENTRA-06).
- `morimail/src/main/java/de/morisoft/morimail/BuiltInTransports.java`: returns `List.of()`. Phase 3 adds the one `graph` provider line here.
- `morimail/src/main/java/de/morisoft/morimail/TransportRegistry.java`: resolves built-ins plus ServiceLoader and rejects duplicate IDs. The registry test for the built-in `graph` goes in `TransportRegistryTest`.
- `TransientMailException` carries `Optional<Duration> retryAfter`.
- `morimail/src/test/java/de/morisoft/morimail/ImportRulesTest.java`: enforces the import rules. It must also cover `graph` and `entra` not importing each other.
- `morimail/src/test/java/de/morisoft/morimail/PublicTypesDocumentThreadSafetyTest.java`: new public types need thread-safety Javadoc.

### Established Patterns
- Plain records, static factories, `System.Logger`, no DI or reflection.
- The sample's seam is `SerialMail.commandLine(Function<AccountFile, Mailer>, Consumer<Duration> sleep)`. Tests inject a Mailer and a recording sleep.
- The `live` tag is excluded by `excludedGroups` in the parent `pom.xml`. The `-Plive` profile sets `groups=live`.
- The library test-jar (`attach=false`) is consumed by the sample in test scope. Resources under `morimail/src/test/resources` (the new PKCS12) reach the sample through it.

### Integration Points
- `morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/SerialMail.java` `realMailer`: replaced (D-11, D-14). `GRAPH_NOT_AVAILABLE` and its tests are removed or updated.
- `morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/AccountFile.java`: supplies tenant, client, from, the credential form and the env var names to `mailer(...)`.
- WireMock (`org.wiremock:wiremock` 3.13.2, test scope) is added to the library and the sample. This is the first HTTP test dependency, already approved in the stack.

</code_context>

<specifics>
## Specific Ideas

- Live probes print one sanitised, pasteable line each, so the owner's STOP report is quick to put together.
- The E2E rerun check mirrors the owner's Phase 4 acceptance check ("a second run sends nothing").

</specifics>

<deferred>
## Deferred Ideas

- Settable timeouts and an `authority`/`graphBaseUrl` key in `account.properties` (sovereign clouds): add them when someone needs them.
- A live probe above 3 MB (about 3.5 MB). The library's `validate` rejects it before sending, so Graph's real response can only be seen with a hand-made request. The owner does this manually if wanted; the upload-session route is a later milestone.

</deferred>

---

*Phase: 03-graph-and-entra*
*Context gathered: 2026-11-22*
