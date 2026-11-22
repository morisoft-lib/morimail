---
phase: "03"
slug: "graph-and-entra"
status: verified
# threats_open = count of OPEN threats at or above workflow.security_block_on severity (the blocking gate)
threats_open: 0
asvs_level: 1
created: "2026-11-22"
---

# Phase 03 — Security

> Per-phase security contract: threat register, accepted risks, and audit trail.

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| library to the Entra token endpoint | the client secret or a signed assertion leaves the process; the response is untrusted input | client secret, client assertion, access token (high) |
| library to the Graph endpoint | the bearer token and the mail leave the process; status, headers and body of the response are untrusted input | bearer token, mail content, addresses (high) |
| consumer's mail data to the request | addresses, names, subject, bodies and files become JSON and a URL path | mail content (medium) |
| key-store file to the library | the PKCS12 file and its password come from the consumer | private key, password (high) |
| classpath to the registry | any jar can register a TransportProvider through ServiceLoader | transport ids (medium) |
| library and sample to logs, exceptions, console and journal | messages are printed, journaled or shipped to log systems | error text (medium) |
| environment variables and account.properties to the sample | secrets, passwords and paths enter the process | secrets, paths (high) |
| threads of one consumer | many threads share one transport and one EntraClientCredentials | token cache (low) |
| test build and CI to the network | default-build and CI tests must stay on localhost; the network-free CI step uses sudo for a namespace | none intended (medium) |
| live test output to the owner's STOP report | printed lines are pasted into a report | outcome lines (medium) |

---

## Threat Register

| Threat ID | Category | Component | Severity | Disposition | Mitigation | Status |
|-----------|----------|-----------|----------|-------------|------------|--------|
| T-03-01 | Information Disclosure | GraphTransport and EntraClientCredentials HttpClient | high | mitigate | `followRedirects(HttpClient.Redirect.NEVER)` at `GraphTransport.java:71` and `EntraClientCredentials.java:111`; WireMock Location-target tests in `GraphErrorMappingTest` and `EntraTokenErrorsTest` | closed |
| T-03-02 | Information Disclosure | EntraClientCredentials.accessToken | high | mitigate | No logger or log call in `entra/EntraClientCredentials.java`; error message is fixed text plus status and cleaned `error`/`error_description` (`:366`); sentinel tests in `EntraTokenErrorsTest` | closed |
| T-03-03 | Spoofing | TransportRegistry with the built-in graph | medium | mitigate | Duplicate id throws `IllegalStateException` with `(built-in)` (`TransportRegistry.java:75-77`); `TransportRegistryTest` asserts it | closed |
| T-03-04 | Information Disclosure | default-build tests | medium | mitigate | Tests use WireMock base URLs, zero GUIDs and example.com/example.org; network-free CI step (`ci.yml`, `unshare --net`, `-o verify`) | closed |
| T-03-05 | Information Disclosure | EntraClientCredentials messages and logging | high | mitigate | No logging API in entra; log-handler and message sentinel tests in `EntraTokenErrorsTest` (secret, token, assertion absent) | closed |
| T-03-06 | Information Disclosure | withCertificate(Path, char[]) | medium | mitigate | Password used only during load, not stored or modified; fixed `IllegalArgumentException` text naming the file (`EntraClientCredentials.java:185`); `aWrongPasswordNeverAppearsInTheMessage` and array-unchanged test | closed |
| T-03-07 | Spoofing | client assertion | medium | mitigate | Fresh assertion per request: random UUID `jti`, `nbf`/`iat` now, `exp` = now + 600 s, `aud` = token URL (`:426-430`), PS256 (`:435-436`); `EntraClientCredentialsTest` pins claims and two distinct `jti` | closed |
| T-03-08 | Denial of Service | token fetch under concurrency | low | mitigate | `ReentrantLock` single flight with unlock in `finally` (`:88`, `:282`, `:307`); `EntraTokenCacheTest` | closed |
| T-03-09 | Tampering | error and error_description from the server | low | mitigate | `clean` replaces ISO control characters, collapses whitespace, caps at 300 (`:72`, `:374-381`); CR/LF and length tests in `EntraTokenErrorsTest` | closed |
| T-03-10 | Information Disclosure | the checked-in key store | low | accept | Throwaway PKCS12 permitted by spec §10.1; see Accepted Risks Log AR-03-01 | closed |
| T-03-11 | Information Disclosure | GraphTransport.failure messages and graph log records | high | mitigate | Fixed layout of status, cleaned code, request-id and message (`GraphTransport.java:271-272`), raw body never used; log-handler test with body, token and subject sentinels (`GraphErrorMappingTest:373-412`) | closed |
| T-03-12 | Information Disclosure | bearer token and redirects | high | mitigate | `Redirect.NEVER`; 3xx falls to `PermanentMailException`; `aRedirectIsPermanentAndItsTargetIsNeverRequested` (300, 302) | closed |
| T-03-13 | Tampering | sender address in the URL path, JSON fields | medium | mitigate | `URLEncoder.encode(from, UTF_8)` (`GraphTransport.java:76`); every JSON value written by Jackson `ObjectNode.put` (`:139-161`); address validation from Phase 1 | closed |
| T-03-14 | Tampering | error.message and request-id from the server | low | mitigate | `clean` (`GraphTransport.java:305-322`): control characters replaced, 300-character cap, surrogate-safe | closed |
| T-03-15 | Repudiation | duplicate delivery through hidden retries | medium | mitigate | Single retry only after 401 (`GraphTransport.java:197-201`); request counts pinned per status in `GraphErrorMappingTest` | closed |
| T-03-16 | Denial of Service | oversized requests | low | mitigate | `validate` rejects at 3 MiB per file and in total and above a 4 MiB estimate from file sizes (`GraphTransport.java:48-124`); `GraphValidateTest` | closed |
| T-03-17 | Information Disclosure | SerialMail.mailer pre-flight messages | high | mitigate | Messages name keys only (`SerialMail.java:71`, `:78`, `:99-109`), key-store error wrapped into fixed text; password sentinel and temp path checked against err and out (`GraphEndToEndTest:206-207`, `:278-283`) | closed |
| T-03-18 | Information Disclosure | tests reaching a real tenant or sending real mail | high | mitigate | No `getenv` in sample tests; `LiveGraphTest` is `@Tag("live")`, excluded by default (`pom.xml:57`), fails in `@BeforeAll` without variables; network-free CI step | closed |
| T-03-19 | Information Disclosure | LiveGraphTest output | medium | mitigate | Lines print flow, outcome and `describe(e)` only; library messages carry no token, body or address (T-03-02, T-03-11, T-03-22) | closed |
| T-03-20 | Elevation of Privilege | CI network-free step | low | mitigate | sudo only for `unshare` and loopback, Maven via `setpriv --reuid` as runner user; `permissions: contents: read`, no secrets, actions pinned by SHA | closed |
| T-03-21 | Tampering | the checked-in example campaign | low | mitigate | `GraphEndToEndTest` passes `--journal` to a temp file (`:123-126`); `ExampleCampaignTest:57` asserts `example-campaign/journal.jsonl` does not exist | closed |
| T-03-22 | Information Disclosure | both-bodies INFO record, attachment and option errors | medium | mitigate | One fixed INFO text without address, subject or body (`GraphTransport.java:136-137`); attachment errors name file names and sizes only; option errors key-only (parameterized `GraphValidateTest`) | closed |
| T-03-SC | Tampering | Maven artifacts (org.wiremock:wiremock 3.13.2) | high | mitigate | Version pinned once in parent `dependencyManagement`; `test` scope in `morimail/pom.xml:34-35` and `morimail-sample-serial/pom.xml:57-58`; plans 03-02 to 03-04 add no dependency | closed |

*Status: open · closed · open — below high threshold (non-blocking)*
*Severity: critical > high > medium > low — only open threats at or above workflow.security_block_on count toward threats_open*
*Disposition: mitigate (implementation required) · accept (documented risk) · transfer (third-party)*

T-03-SC appears in all five plans; each row covers that plan's own dependency changes, so it is recorded here once.

---

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|-------------|------|
| AR-03-01 | T-03-10 | `morimail/src/test/resources/test-keystore-throwaway.p12` is the one throwaway credential spec §10.1 permits: password `changeit`, `CN=morimail-test-throwaway`, registered with no tenant, protects nothing, explained in README. | plan 03-02 (spec §10.1) | 2026-11-22 |

*Accepted risks do not resurface in future audit runs.*

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-11-22 | 23 | 23 | 0 | /gsd-secure-phase (L1 grep-depth, auditor skipped by short-circuit rule) |

---

## Sign-Off

- [x] All threats have a disposition (mitigate / accept / transfer)
- [x] Accepted risks documented in Accepted Risks Log
- [x] `threats_open: 0` confirmed
- [x] `status: verified` set in frontmatter

**Approval:** verified 2026-11-22
