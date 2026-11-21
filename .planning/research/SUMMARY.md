# Project Research Summary

**Project:** morimail
**Domain:** Small Java 17 mail-sending library (templated mail, pluggable transports, Microsoft Graph `sendMail` as an Entra app-only client) plus a picocli serial-mail CLI sample, published to Maven Central
**Researched:** 2026-11-21
**Overall confidence:** MEDIUM-HIGH

## Executive Summary

morimail is a deliberately small library: a transport-neutral mail model, a `$$name$$` template renderer, a three-category error hierarchy, and one built-in transport (Microsoft Graph) that authenticates with an Entra client-credentials flow using a hand-built PS256 JWT assertion or a client secret. The experts' way to build this is to keep the core free of transport assumptions, put provider limits and error mapping in the transport, and let the consumer own retry, batching and rate policy. The spec already encodes this, and all four research streams agree. The jar's only runtime dependency is Jackson; everything else is JDK.

The recommended approach follows the spec's own ordering: foundation (parent POM, wrapper, records, errors, Javadoc discipline), then a core whose contracts are frozen early (including the 401 mechanism on `TokenProvider`), then the picocli sample running against the `recording` test transport, then Entra and Graph in parallel, then wiring and an end-to-end WireMock test, then a hard stop for the owner's live test, and only then docs and release. Versions were verified against Maven Central on 2026-11-21 (Jackson 2.23.0, JUnit 6.1.3, WireMock 3.13.2, picocli 4.7.7, central-publishing plugin 0.12.0, Maven 3.10.1 via wrapper; never Maven 4).

Key risks cluster in three places: (1) the Entra PS256 assertion, which can self-verify in unit tests yet be rejected by Entra, so only the live test proves it; (2) the 401 retry, which is a silent no-op unless the cache can be invalidated, a contract to decide before the sample codes against `TokenProvider`; (3) release-time surprises: the test-jar and sample staged into the Central bundle, Javadoc doclint failing only at release, and the spec's keystore password `test` rejected by `keytool`. For a serial-mail tool, a timeout after Graph accepted a message makes a retry send a duplicate, and "202 accepted" is not "delivered". These must be documented, not engineered away silently.

## Key Findings

### Recommended Stack

Spec section 3 fixes the stack; research makes it concrete and found several assumptions that do not hold. Build with `--release 17` (CI must run on 17). Pin all plugin versions in `pluginManagement`. Use the Maven wrapper in `only-script` mode, so no `maven-wrapper.jar` and the `*.jar` rule in `.gitignore` is not a problem.

**Core technologies:**
- **Java 17 (`maven.compiler.release=17`)**: records, `java.net.http`, `System.Logger`. `--release` makes javac reject post-17 APIs even on JDK 21.
- **Apache Maven 3.10.1 via wrapper** (3.9.16 also verified): current recommended line. Maven 4 is documented as broken with the Central publishing plugin.
- **jackson-databind 2.23.0 (via jackson-bom)**: the only runtime dependency. Tree model (`ObjectNode`), no DTOs. See decision 2.
- **picocli 4.7.7**: sample only; no annotation processor needed.

**Test dependencies (test scope):** JUnit Jupiter 6.1.3 (`junit-jupiter` aggregator; spec says JUnit 5, see decision 3), AssertJ 3.27.7, `org.wiremock:wiremock` 3.13.2 (not standalone). Verified together; JDK HttpClient falls back to HTTP/1.1 over cleartext against WireMock.

**Build and release:** maven-compiler 3.16.0, surefire 3.6.1, jar 3.5.1 (`test-jar` goal, `Automatic-Module-Name`), source 3.4.0, javadoc 3.12.0, gpg 3.2.8, central-publishing 0.12.0 (`<extensions>true</extensions>`, `publishingServerId=central`, `autoPublish` false). No failsafe, enforcer or shade.

**Verified findings that change the plan:**
1. `keytool` refuses the password `test` (minimum 6 characters).
2. A plain `test-jar` execution is staged into the Central bundle.
3. `maven.deploy.skip=true` does not keep the sample out of the Central bundle; `skipPublishing=true` does.
4. `failOnWarnings` in the release profile fails the sample's Javadoc; the sample needs javadoc, source and gpg skips.
5. Public classes with an implicit default constructor and no comment break `doclint=all` + `failOnWarnings`.
6. `-SNAPSHOT` takes the snapshot path; release from `0.1.0`.
7. The current JUnit line is 6.x, not 5.x.
8. Jackson 3.x (`tools.jackson`) is GA; recommend 2.23.0 for 0.1.0, decision required.

**Verified recipes:** RSASSA-PSS signing with `PSSParameterSpec("SHA-256","MGF1",MGF1ParameterSpec.SHA256,32,TRAILER_FIELD_BC)` on a plain RSA key; `x5t#S256` as base64url (no padding) of `SHA-256(cert.getEncoded())`, cross-checked with OpenSSL; PKCS12 loading by first key entry; a `live` Surefire group switched by `-Plive`; the release profile with test-jar `attach=false`; `keytool -J-Dkeystore.pkcs12.legacy` for a checked-in PKCS12 every JDK reads.

### Expected Features

The spec covers all table stakes. Research found 12 small gaps (G1 to G12). Four are needed for a correct MVP; the rest are cheap polish or documentation. None needs a new phase.

**Must have (table stakes):**
- Mail records (from, to, cc, bcc, reply-to, subject, HTML, optional text, attachments with `contentId` reserved and rejected in MVP) and a fluent builder that validates at `build()`.
- `$$name$$` renderer: HTML-escaped by default, `$$!name$$` raw, subject line-break guard, missing field fails naming part and field.
- Error hierarchy: transient / permanent / auth-required, with `Optional<Duration> retryAfter`.
- Accounts by ID in one `Mailer`; `TransportProvider` via built-in list plus `ServiceLoader`; duplicate IDs fail.
- Entra token client: secret and certificate, 5-minute cache margin, single-flight, invalidation.
- Graph `sendMail` JSON, base64 `fileAttachment`, `saveToSentItems`, `202 = accepted`, error mapping, one 401 retry.
- CLI: pre-flight (exit 2), journal and resume keyed by `campaignId:lowercase(email)`, rate limit (default 20/min), transient retry x3, stop on account errors (exit 3), dry-run, counts, example campaign.
- Repo: POM metadata, wrapper, LICENSE, README, SECURITY.md, CI on Java 17, release profile, `Automatic-Module-Name`.

**Differentiators (in scope, cheap):** strict-by-default renderer with no expression language; `validate()` separate from `send()`; the `SenderRejectedException` vs `AuthRequiredException` split; RBAC-for-Applications setup guide (union trap, 30 min to 2 h delay); durable journal with resume; `RecordingTransport` registered like a real plug-in.

**Cheap polish to include:** G5 builder CR/LF and single-`@` check (about 10 lines); G6 one progress line per row; G7 dry-run without secrets (stub `TokenProvider`); G10 "what this is not" paragraph with Exchange limits.

**MVP gaps the spec is silent on:**
- **G1 HTTP timeouts** on token and Graph calls (connect about 10 s, request 30 to 60 s); `HttpTimeoutException` maps to transient. `HttpClient` has no default timeout.
- **G2 UTF-8 and real-world CSV:** read everything as `UTF_8` explicitly (default charset is platform-dependent on Java 17), strip BOM, RFC 4180 quoted fields, comma delimiter documented, detect a `;` header and say so.
- **G3 Graph error detail:** `error.code`, message and `request-id` in the exception message; never tokens or bodies.
- **G4 `Automatic-Module-Name`** in the library manifest.

**Defer (post-MVP):** inline images (milestone 6; `contentId` already in the model), upload sessions for 3 to 150 MB attachments, `byte[]` attachments, a published `RecordingTransport`, SMTP/SES/Gmail, MIME sending, delegated OAuth, a CSV delimiter option.

**Anti-features:** template loops, conditionals, defaults or missing-placeholder policies; retry/backoff inside `send`; a Graph error enum or typed `GraphError`; delivery tracking; List-Unsubscribe or `internetMessageHeaders`; per-recipient attachments; parallel sending; interactive confirmation prompts; config formats beyond `.properties`; a Graph SDK, MSAL4J, Azure Identity or a JWT library; PEM/PKCS8 parsing; managed identity or device flows.

### Architecture Approach

The architecture follows the spec's package and import rules, enforceable by a plain JUnit test (no ArchUnit). The core (`de.morisoft.morimail`) imports only the JDK, plus one line in `BuiltInTransports` referencing `graph.GraphTransportProvider`. `graph` and `entra` do not import each other; they meet only through the core interface `TokenProvider`. Jackson appears only in `graph`, `entra` and the sample journal. Each `MailAccount.build()` builds a fresh `TransportRegistry` (built-ins first, then `ServiceLoader`) with no static state. Duplicate and unknown IDs throw unchecked exceptions at account build. `Mailer` applies `defaultFrom` and runs `validate` before `send`, through one shared `prepare(mail)` step.

**Major components:**
1. **Core records and errors** (`MailAddress`, `Attachment`, `MailMessage`, `Mail`, `MailException` tree): immutable, compact constructors copy lists; errors are checked and per-mail.
2. **`Mail.Builder` and `TemplateRenderer`/`MailTemplate`**: renders at `build()`, transport-independent checks; not thread-safe (document it).
3. **SPI: `MailTransport`, `TransportProvider`, `TokenProvider`**: the only two extension points; `TokenProvider` gains one default method (decision 1).
4. **`MailAccount`, `Mailer`, `TransportRegistry`, `BuiltInTransports`**: immutable; registry package-private; built-in list starts empty and gets its Graph line in the wiring step.
5. **`graph`: `GraphTransportProvider` and `GraphTransport`**: `ObjectNode` JSON (no DTOs), HTTP, error mapping as one `switch`, one 401 retry, limits in `validate` (sizes via `Files.size`, never reading content); options `saveToSentItems` and optional `baseUrl`; unknown keys rejected.
6. **`entra`: `EntraClientCredentials`**: PS256 assertion as a pure function; token POST; `volatile` cache plus `ReentrantLock` single-flight (not `synchronized`, to avoid virtual-thread pinning); compare-and-drop invalidation.
7. **`RecordingTransport` (test sources, test-jar only)**: per-test instance passed via options (no global list), `CopyOnWriteArrayList`, scripted failures.
8. **Serial-mail CLI**: picocli entry; `AccountFile` (the only code that knows Graph and Entra; secrets only from env vars); hand-written `Csv`; `PreFlight` (all problems at once, exit 2); `Journal` (append-only JSON lines, fsync per line, `FileChannel` lock); `Runner` (takes a `Mailer`, testable against `recording`).

**Decisions to record in `docs/architecture.md`:**
- The transport holds no token state; it calls `accessToken(scope)` per send with `https://graph.microsoft.com/.default`.
- Graph JSON omits `message.from` (the URL selects the mailbox), sends `saveToSentItems: false` as a JSON boolean, omits empty arrays and nulls, uses `contentType` `HTML`/`Text`.
- Token expiry is `expires_in - 300`, clamped at 0; `exp` = `nbf` + 5 minutes.
- Unknown and duplicate transport IDs throw unchecked exceptions at account build.

**Build order:** Foundation, then Core (contracts frozen, including `freshAccessToken`), then Sample CLI against `recording`, then Entra and Graph in parallel, then wiring and E2E, then STOP for the owner's live test, then Docs and release.

### Critical Pitfalls

1. **The 401 retry is a silent no-op.** A caching provider returns the same token inside the margin, so the retry repeats the failure. Decide the mechanism in the core phase: one default method `freshAccessToken(scope, rejectedToken)` on `TokenProvider` that drops the cache only if the cached value still equals the rejected token (compare-and-drop). Test with a stub provider that returns a different token after invalidation and assert the second request carries it.

2. **PS256 assertion self-verifies but Entra rejects it (AADSTS700027 / 700024).** Common mistakes: signing JSON instead of the ASCII bytes of `base64url(header).base64url(claims)`; padded or standard base64; `x5t#S256` taken from the SHA-1 portal thumbprint; `SHA256withRSA` instead of `RSASSA-PSS`; non-integer claims; a reused `jti`. Prevention: an independent decode-and-verify test; header keys exactly `alg`, `typ`, `x5t#S256`; integer-second claims; `nbf` backdated by about 60 s if clock skew shows up live. One forum answer says RS256; follow the official page (PS256) and make the live-test failure message point at certificate upload and client ID first.

3. **Token-endpoint error mapping and cache bugs.** Order matters: network and timeouts first, then 429/5xx/408 as transient (honoring `Retry-After`), then remaining 4xx as auth-required. `invalid_client` often arrives as 401. Parse bodies defensively (non-JSON gives status plus the first 200 characters, no secrets). The single-flight test uses 8 threads and a WireMock delay and asserts one token request; `expires_in` 299 always refetches, 3600 reuses. Failed token requests are never cached.

4. **Duplicate mail on timeout or crash.** `sendMail` is not idempotent. A read timeout or 5xx after acceptance, followed by the CLI's retry, sends a second copy; a crash between 202 and the journal write has the same effect on resume. Document at-least-once semantics in README and `MailTransport.send` Javadoc, and get the owner's decision on "in doubt" handling (decision 10). A journal write failure must be fatal for the run.

5. **Release-time surprises.** The test-jar and sample are staged into the Central bundle unless the release profile sets `attach=false` on the test-jar execution and `skipPublishing=true` on the sample. `-Prelease verify -Dgpg.skip` with `failOnWarnings` must run in CI from Phase 1. Every public record needs `@param` coverage, and public classes need constructor comments. Inspect the staged deployment (`autoPublish=false`) in the Portal before publishing.

Also important: the renderer must use a manual `find()` loop, never `appendReplacement` with raw values (`$` and `\` in data); CSV must handle BOM, CRLF, quoted newlines, ragged rows, `;` headers and duplicate addresses; the journal needs torn-line recovery and a file lock; Retry-After must accept seconds or HTTP-date and cap the wait; public-repo hygiene (zero GUIDs, `example.org`, neutral metadata in generated PDF/PNG, no real IDs pasted from live responses).

## Implications for Roadmap

Five phases with a checkpoint between phases 4 and 5. This follows the architecture build order and the spec's constraints (sample before Graph; stop after Entra and Graph for the owner's live test). The roadmapper may rename phases, but the dependencies should stand.

### Phase 1: Foundation (repo, POM, records, errors, Javadoc discipline)
**Rationale:** Everything depends on records and errors; repo hygiene must exist before the first public commit; Javadoc and release-profile checks must run from the first code.
**Delivers:** Parent POM with Central metadata, plugin and dependency pinning, `jackson-bom`, test library management, the `release` profile with `attach=false` on the test-jar and sample skip properties, `Automatic-Module-Name`, Maven wrapper (`only-script`, executable bit, `.gitignore` audit for `target/`, `out/`, `*.jsonl`, `account.properties`, keystore naming), CI (Java 17, `permissions: contents: read`, `mvn verify` plus `mvn -Prelease verify -Dgpg.skip`), LICENSE, README stub, SECURITY.md, CLAUDE.md with spec section 11 rules, `docs/spec.md`, records and the error hierarchy.
**Addresses:** repo and release table stakes; error categories; G4.
**Avoids:** pitfalls 20 (Javadoc only at release), 21 (wrapper, `.gitignore`, Central packaging), 22 (public-repo hygiene).

### Phase 2: Core contracts (renderer, builder, SPIs, registry, Mailer, RecordingTransport)
**Rationale:** Contracts must be frozen before the sample and Graph code against them. The 401 mechanism is decided here because it changes `TokenProvider`.
**Delivers:** `TemplateRenderer`/`MailTemplate` (manual `find()` loop, escape set `& < > " '`, subject guard, strict missing-field error naming part and placeholder); `Mail.Builder` with validation (including G5); `MailTransport`, `TransportProvider`, `TokenProvider` with the `freshAccessToken` default; `MailAccount` and builder; package-private `TransportRegistry` (built-ins plus `ServiceLoader`, duplicate detection, sorted available-ID message) and `BuiltInTransports` (empty list for now); `Mailer` (`validate`, `send`, shared `prepare`); `RecordingTransport` with test-jar and services file (per-test instance via options); import-rule JUnit test; `docs/architecture.md` recording deviations.
**Avoids:** pitfalls 1 (contract), 13 (regex replacement), 17 (encoding in renderer), 18 (ServiceLoader test pollution).

### Phase 3: Serial-mail CLI against `recording`
**Rationale:** Exercises the contracts (`validate`, error categories, defaults) before a real transport depends on them. Contract mistakes found here cost a line, not a Graph rewrite. The spec requires this order.
**Delivers:** Picocli command; `AccountFile` (properties read as a UTF-8 `Reader`, secrets from env vars, `account.properties.example`); campaign loader; hand-written `Csv` (BOM, CRLF, quotes, ragged rows, `;` detection, duplicates); `PreFlight` (all problems at once, exit 2, rate and campaign-ID validation, a warning about the 10,000 per-day Exchange cap); `Journal` (JSON lines via Jackson, one write per line, `force` per line, torn-line recovery, `FileChannel` lock, `Instant` timestamps, `Locale.ROOT` key folding); `Runner` (skip accepted, render, build, send, bounded retry x3 honoring `retryAfter` with a cap, rate limit that does not count skipped rows, stop on auth/sender errors with exit 3, per-row progress G6); dry-run without secrets (G7) writing sanitised file names to `out/`; example campaign with neutral metadata; exit codes returned from `call()` and tested through `execute(args)`.
**Avoids:** pitfalls 14 (CSV), 15 (journal), 16 (campaign ID, `--limit`, rate, exit codes), 17 (file names), 22 (generated PDF/PNG metadata).

### Phase 4: Entra token client and Graph transport (in parallel, then wired)
**Rationale:** Entra and Graph depend only on core contracts, not on each other. The wiring step joins them and runs E2E; the owner's live test is the hard stop.
- **4a Entra:** JWT assertion as a pure function (PS256 via `RSASSA-PSS`, `x5t#S256`, integer claims, `exp` = `nbf` + 5 min, new `jti` per request); PKCS12 loader (first key entry, same password for store and key, fail fast on non-RSA or sub-2048-bit keys); secret and certificate flows; token URL derived once and used for both the POST and `aud`; tenant alias rejection (`common`, `organizations`, `consumers`); scope check; cache with margin and clamp; single-flight with `ReentrantLock`; HTTP timeouts (G1); error mapping with 429/5xx before the generic 4xx; compare-and-drop invalidation.
- **4b Graph:** `MailMessage` to JSON as a pure function; `validate` (per-file and total limits via `Files.size`, estimated request size, 500-recipient note); HTTP send with a percent-encoded `from` path segment; status mapping (202 accepted; 401 one retry via `freshAccessToken`; oversize 413/400 to permanent; 403 to `SenderRejectedException`; 429 with `Retry-After` parsed leniently; timeouts and network to transient; G3 code and `request-id` in messages); attachments read once and base64-encoded at send time; `GraphTransportProvider` options (`saveToSentItems` boolean, optional `baseUrl`, unknown keys rejected).
- **Wiring and E2E (last step of this phase):** add the Graph line to `BuiltInTransports`; registry tests that need Graph; library-level E2E with real `EntraClientCredentials` (authority at WireMock) and real Graph transport (`baseUrl` at WireMock) through the registry; `live`-tagged test skipping via `Assumptions` plus `-Plive`; `AccountFile` finished for Graph.
**Avoids:** pitfalls 2 (PS256), 3 (authority/aud drift, tenant aliases, scope), 4 (error mapping order), 5 (cache and single-flight), 6 (HttpClient defaults, interrupts), 8 (403/404 ambiguity), 9 (attachment boundary), 10 (Retry-After), 19 (JSON shape, `saveToSentItems` boolean).

**STOP checkpoint (spec milestone 4):** Report to the owner. The owner runs the live test. Results feed back: real error responses correct the spec 6.3 table; the 2.9 MB and about 3.5 MB attachment boundaries; PS256 acceptance; the RBAC negative scope check; the 20/min default; the `from` display-name question. Fixtures must be sanitised.

### Phase 5: Docs, release rehearsal, handover
**Rationale:** Docs must describe what the live test showed, so they come after the checkpoint.
**Delivers:** `docs/entra-setup.md` (RBAC for Applications, union trap, Enterprise-application IDs, 30 min to 2 h delay, negative scope verification, Entra `Mail.Send` consent removal, secret and certificate expiry, zero GUIDs); README quick start, at-least-once and "accepted is not delivered" warnings, Exchange limits (G10), bulk-mail caveat, error-category table; final `docs/architecture.md` with every recorded deviation; sample POM `skipPublishing` and javadoc/source/gpg skips; release rehearsal with `-Prelease deploy -DskipUpload=true` and bundle inspection (only `morimail-parent` and `morimail`, no `-tests.jar`, no sample); version set to `0.1.0` by the owner; owner-only steps listed (namespace DNS, Portal token, GPG key, Portal review, Publish).
**Avoids:** pitfalls 7 and 11 (documented), 12 (RBAC set-up), 21 (staged deployment check).

### Phase Ordering Rationale
- Records and errors come first because every component depends on them.
- The `TokenProvider` contract is frozen in Phase 2 because the sample codes against it and the 401 retry must not be a no-op.
- The sample precedes Graph because it exercises validate, error categories and defaults before a real transport depends on them.
- Entra and Graph are separable and can run in parallel; the only join is registry wiring, which keeps the core free of Graph references.
- The live test is a hard stop between Phases 4 and 5 because the error table, attachment boundaries, rate default and RBAC text are unknown until real responses arrive.
- Javadoc and release-profile checks run from Phase 1 so release day is not the first time strict doclint is applied.

### Research Flags

Needs deeper research or a live check during planning:
- **Phase 4a (Entra):** PS256 acceptance is provable only live; one forum source conflicts with the official page. Plan a specific live check and AADSTS-aware error messages. Clock-skew handling depends on the live test.
- **Phase 4b (Graph):** the error table (403 vs 404 vs `MailboxNotEnabledForRESTAPI`), the real JSON request ceiling (about 4 MB is community-sourced, LOW), and whether the `from` display name is honoured are unverified. Needs sanitised live responses.
- **Phase 5 (release):** the Central `excludeArtifacts` behaviour for test-jar classifiers is unverified; confirm with a rehearsal bundle before the owner releases.

Standard patterns (skip research-phase):
- **Phase 1:** Maven parent POM, wrapper, CI, `.gitignore`, records and errors. All versions and recipes were executed and verified.
- **Phase 2:** records, renderer, registry with `ServiceLoader`, `Mailer`. Fully specified in the architecture research.
- **Phase 3:** picocli, JSON-lines journal, hand-written CSV. Behaviour is specified; only the delimiter decision needs the owner.

## Open Decisions for the Owner

Collected from all four research files. Each lists the research recommendation.

1. **TokenProvider 401 invalidation.** Recommended: a default method `freshAccessToken(scope, rejectedToken)` on `TokenProvider` with compare-and-drop, keeping the interface functional. This deviates from spec 7.3's "do not widen" preference. Alternative: leave `TokenProvider` unchanged and drop the Graph 401 retry, mapping the first 401 directly. Without one of these, the retry is a no-op. Record the choice in `docs/architecture.md`.

2. **Jackson 2.23 vs 3.x.** Recommended: 2.23.0 (`com.fasterxml`). The spec's wording, WireMock 3.13.x and most consumers are on 2.x, and 2.x is still released. Alternative: Jackson 3.3.0 (`tools.jackson`, JDK 17 baseline). The choice leaks into consumers' dependency trees and is hard to change after 0.1.0; decide before the first line of code.

3. **JUnit 6.x vs spec "JUnit 5".** Recommended: JUnit 6.1.3 (current line; same `org.junit.jupiter` coordinates and `@Test`/`@Tag` API; Java 17 baseline). Confirm the owner accepts the current line so nobody "fixes" it back.

4. **Test keystore password.** The spec says `test`, but `keytool` refuses passwords under 6 characters. Options: (a) `changeit` created with keytool, recommended, documented in one README sentence, using the legacy PKCS12 format via `-J-Dkeystore.pkcs12.legacy` so every JDK reads it; (b) `test` via `openssl pkcs12 -export -passout pass:test`, which works but is no longer "created with keytool" as spec 10.1 says. PROJECT.md names `test` as the permitted exception; update that wording to match the decision.

5. **Test-jar in the Central bundle (spec 10.1 vs 13.2).** Spec 10.1 says the test-jar is "published as a test-jar"; spec 13.2 says it is not published. Recommended: built and shared inside the reactor only, via `attach=false` in the `release` profile; the sample still resolves it in the reactor (verified). Alternatives: a test-jar only outside the release profile (sample resolution needs testing), or an unpublished `morimail-testkit` module (changes the spec layout). The owner must confirm which reading applies.

6. **Sample `skipPublishing`.** Recommended: set `skipPublishing=true` in the sample POM, alongside `maven.deploy.skip`, `maven.source.skip`, `maven.javadoc.skip` and `gpg.skip`. `maven.deploy.skip` alone does not keep the sample out of the bundle (verified). Confirm the sample is never published.

7. **CSV parsing without a dependency.** Recommended: a hand-written RFC 4180-style reader (about 60 to 80 lines) with UTF-8 and BOM stripping, comma delimiter only, and a clear error when the header looks semicolon-delimited. Owner: confirm comma-only (versus an explicit `--delimiter`, which the spec does not name) and confirm no CSV library (any dependency needs approval under Rule 0).

8. **Graph `baseUrl` option for WireMock E2E.** Recommended: an optional `baseUrl` transport option (default the public endpoint). Spec milestone 4 requires the sample to run end to end against WireMock, and a registry-built Graph account can only be redirected through an option. It is the one option the spec does not list. Without it, E2E lives at library level with a package-private constructor and the sample never runs against WireMock.

9. **Attachment limit: raw bytes vs base64.** The spec limits raw files to under 3 MB per file and in total. Base64 adds one third, so 3 MB raw becomes about 4 MB of JSON, near a community-reported (LOW confidence) 4 MB ceiling. Recommended: keep the raw rule and add an estimated request-size check in `validate` (constant in one place in `graph`); the owner runs live sends at about 2.9 MB and about 3.5 MB and records the 400/413 boundary, then fixes the rule. Decide whether the total is measured on raw or encoded size.

10. **Duplicate-send risk on timeout retries.** `sendMail` is not idempotent, and the spec's mapping retries network and timeout errors up to 3 times. Options: (a) keep retry and document at-least-once semantics (the recommended minimum); (b) on a timeout or 5xx after sending, journal an "in doubt" outcome and do not auto-retry that row (no duplicates, possible loss; needs connect failures, which are safe, distinguished from request timeouts, which are not); (c) add a write-ahead `started` journal record (an extra record type, so an owner decision under Rule 0).

11. **`saveToSentItems` default in the sample.** `account.properties` has no key for it, so Graph's default (true) applies and a 500-row campaign leaves 500 copies in Sent Items. Recommended: leave the default, state it in the setup guide, and treat Sent Items as the audit trail. Alternative: default `false` in the sample only; the library default stays true.

12. **Unchecked exceptions for unknown or duplicate transport IDs.** Recommended: `IllegalArgumentException` for an unknown ID (message lists available IDs) and `IllegalStateException` for a duplicate (including two external providers sharing an ID), both at account build. `MailException` is checked and per-mail, so these are outside it. The spec only says "fails"; confirm.

Lower stakes, to confirm: `Automatic-Module-Name` (not required by the spec; recommended, one line); `message.from` omitted in MVP (the URL selects the mailbox; check the display name live); `contentType` defaults to `application/octet-stream` when unknown.

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH | Every version read from Maven Central metadata; build, test, release and crypto recipes executed (JDK 21 with `--release 17`; JDK 17 runtime untested locally, CI is the proof). |
| Features | MEDIUM-HIGH | Microsoft facts HIGH; competitor lists MEDIUM; a few Graph limits LOW and marked. |
| Architecture | HIGH | Structure follows the binding spec; Microsoft facts re-read 2026-11-21; concurrency design reasoned, MEDIUM. |
| Pitfalls | MEDIUM-HIGH | Doc facts HIGH; real Graph and Entra error behaviour from forums (MEDIUM or LOW), needs the live test; JDK 17 behaviour not re-verified this session. |

**Overall confidence:** MEDIUM-HIGH. Build, release and crypto plumbing are verified by execution. The unresolved risk is runtime behaviour against live Microsoft services, which is what the owner's live test is for.

### Gaps to Address
- **PS256 acceptance by Entra:** verified against the official page only; one forum answer says RS256. Resolve at the Phase 4 live checkpoint. Do not code a speculative RS256 fallback.
- **Error table (spec 6.3):** the 403/404/`MailboxNotEnabledForRESTAPI` split and the 401 "points at the mailbox" rule are guesses. Replace with sanitised real responses after the live test; keep every row tested.
- **Request size ceiling:** the "about 4 MB" JSON limit is unofficial (LOW). Settle by live test.
- **Throttling numbers:** the 20/min default versus Exchange's 30/min and 10,000/day per mailbox; verify and adjust after the Graph phase.
- **Graph `from` display name and shared mailboxes:** unverified; test in the live run.
- **Central `excludeArtifacts`:** behaviour for test-jar classifiers unverified; confirm with a `-DskipUpload=true` rehearsal before release.
- **JDK 17 runtime:** PSS signing and PKCS12 reading proven on JDK 21 only; CI on Java 17 must cover them from Phase 1.
- **Version drift:** numbers are a snapshot from 2026-11-21; re-check at implementation time per spec section 3.

## Sources

### Primary (HIGH confidence)
- Microsoft Learn `user: sendMail`: https://learn.microsoft.com/en-us/graph/api/user-sendmail (JSON/MIME formats, single `body`, `fileAttachment`, `saveToSentItems`, 202 semantics).
- Microsoft Learn, Graph send mail process: https://learn.microsoft.com/en-us/graph/outlook-things-to-know-about-send-mail (post-202 processing, NDRs).
- Microsoft Learn, `fileAttachment` resource: https://learn.microsoft.com/en-us/graph/api/resources/fileattachment; large attachments: https://learn.microsoft.com/graph/outlook-large-attachments.
- Microsoft Learn, Entra certificate credentials: https://learn.microsoft.com/en-us/entra/identity-platform/certificate-credentials (PS256, `x5t#S256`, `aud`).
- Microsoft Learn, OAuth 2.0 client credentials flow: https://learn.microsoft.com/en-us/entra/identity-platform/v2-oauth2-client-creds-grant-flow.
- Microsoft Learn, Exchange Online limits: https://learn.microsoft.com/en-us/office365/servicedescriptions/exchange-online-service-description/exchange-online-limits.
- Microsoft Learn, Graph throttling limits: https://learn.microsoft.com/en-us/graph/throttling-limits.
- Microsoft Learn, RBAC for Applications in Exchange Online: https://learn.microsoft.com/en-us/exchange/permissions-exo/application-rbac.
- Sonatype Central Portal Maven plugin and requirements: https://central.sonatype.org/publish/publish-portal-maven/, https://central.sonatype.org/publish/requirements/, https://central.sonatype.org/publish/requirements/gpg/, https://central.sonatype.org/register/namespace/.
- Maven repository metadata (repo1.maven.org) for all versions in STACK.md; Maven wrapper docs: https://maven.apache.org/tools/wrapper/.
- Executed smoke project (3-module reactor; wrapper on Maven 3.10.1 and 3.9.16; JDK 21 with `--release 17`; throwaway GPG key; OpenSSL 3.5.5): build, live profile, release bundle and signature checks, PSS round trip with OpenSSL cross-check, PKCS12 variants, sample runnable jar.

### Secondary (MEDIUM confidence)
- Jackson 3.0 GA and migration guide: https://cowtowncoder.medium.com/jackson-3-0-0-ga-released-1f669cda529a; https://raw.githubusercontent.com/FasterXML/jackson/master/jackson3/MIGRATING_TO_JACKSON_3.md.
- Simple Java Mail features (competitor baseline): https://www.simplejavamail.org/features.html.
- Microsoft Q&A, client assertion valid time range: https://learn.microsoft.com/en-us/answers/questions/778031/client-assertion-is-not-within-its-valid-time-rang.
- `awdeorio/mailmerge` CLI behaviour: https://github.com/awdeorio/mailmerge.

### Tertiary (LOW confidence, needs validation)
- Microsoft Q&A, sendMail request size ("about 4 MB"): https://learn.microsoft.com/en-in/answers/questions/1824204/graph-api-user-sendmail-request-size-limit.
- Microsoft Q&A, AADSTS700027 and PS256 conflict: https://learn.microsoft.com/en-us/answers/questions/2280615/how-to-resolve-error-aadsts700027-the-certificate.
- Forum reports on `MailboxNotEnabledForRESTAPI` and app-only 403: https://learn.microsoft.com/en-us/answers/questions/5842949/mailboxnotenabledforrestapi-error-in-multi-tenant; https://techcommunity.microsoft.com/discussions/officegraph/403-forbidden-when-sending-mail-with-app-only-token-via-microsoft-graph/4458775.
- Google and Yahoo bulk-sender thresholds: not verified.

---
*Research completed: 2026-11-21*
*Ready for roadmap: yes, once the owner answers open decisions 1, 2, 4, 5 and 8, which block the first code commit*
