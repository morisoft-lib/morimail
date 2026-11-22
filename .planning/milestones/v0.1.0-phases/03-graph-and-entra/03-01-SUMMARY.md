---
phase: 03-graph-and-entra
plan: 01
subsystem: transport
tags: [graph, entra, client-credentials, wiremock, java-http-client]

requires:
  - phase: 01-core
    provides: TokenProvider, TransportProvider, MailTransport, registry with the built-in list
provides:
  - EntraClientCredentials secret flow with the authority copy method
  - GraphTransportProvider (id graph) and a package-private GraphTransport sending one text mail
  - graph registered as the built-in transport
  - SendThroughGraphTest end to end against WireMock
affects: [03-02, 03-03, 03-04, 03-05]

actuals:
  tokens: 7300
  tasks: 2
  commits: 2
plan_head_before: 347499d5b3a351ce765274acff61ca591eac5d8c
plan_head_after: 2eda26761861a46803d11a312b903ea0c0508146

tech-stack:
  added: [org.wiremock:wiremock 3.13.2 (test scope)]
  patterns:
    - "graph and entra share no code and never import each other; the TokenProvider seam joins them"
    - "one HttpClient per class with 10 s connect timeout and no redirects; 30 s per request"

key-files:
  created:
    - morimail/src/main/java/de/morisoft/morimail/entra/EntraClientCredentials.java
    - morimail/src/main/java/de/morisoft/morimail/graph/GraphTransportProvider.java
    - morimail/src/main/java/de/morisoft/morimail/graph/GraphTransport.java
    - morimail/src/test/java/de/morisoft/morimail/SendThroughGraphTest.java
  modified:
    - morimail/pom.xml
    - morimail/src/main/java/de/morisoft/morimail/BuiltInTransports.java
    - morimail/src/test/java/de/morisoft/morimail/TransportRegistryTest.java
    - morimail/src/test/java/de/morisoft/morimail/ImportRulesTest.java

key-decisions:
  - "Tracer gaps stay as missing branches: no token cache, no certificate flow, no size checks, no 401 retry yet"
  - "Unusable 200 token response (no access_token) raises AuthRequiredException until plan 03-02 sets the final rule"

requirements-completed: [ENTRA-01, ENTRA-04, GRAPH-01]

duration: 25min
completed: 2026-11-22
status: complete
---

# Phase 3 Plan 01: The send path, end to end Summary

**A secret-authenticated Entra app gets a token from a WireMock endpoint and sends one text mail through a registry-built `graph` account, with graph resolved from the built-in list.**

## Performance

- **Duration:** about 25 min
- **Tasks:** 2 of 2
- **Files modified:** 8 (4 created, 4 modified)

## Accomplishments

- `EntraClientCredentials.withSecret(..).authority(URI)`: form-encoded token POST (client_id, scope, client_secret, grant_type), tested with a secret holding `+`, `&`, `=` and `%`.
- `GraphTransport` posts to `{baseUrl}/users/{URL-encoded from}/sendMail` with the bearer token; 202 returns, 403 raises `SenderRejectedException`; the sender is encoded exactly once and a trailing `/` on the base URL is ignored.
- `BuiltInTransports.providers()` holds the graph provider; no services file names it; an external `graph` fails the registry with `(built-in)`; an unknown id lists `Available: graph, recording`.
- `ImportRulesTest` fails if the graph or entra source directory is missing or empty.
- Both HTTP clients use a 10 s connect timeout and `Redirect.NEVER`; requests carry a 30 s timeout.

## Task Commits

1. Task 1 (tracer): `ab78150` feat(03-01): send one text mail through Graph with an Entra secret
2. Task 2: `2eda267` feat(03-01): register graph as the built-in transport

## Verification

- `./mvnw -B -ntp verify`, `./mvnw -B -ntp -o verify` and `./mvnw -B -ntp -Prelease clean verify -Dgpg.skip -pl morimail -am`: all BUILD SUCCESS, no Javadoc warnings.
- SendThroughGraphTest 4 tests, TransportRegistryTest 14, ImportRulesTest 1, none skipped.
- Hygiene greps (paths, non-zero GUIDs, non-example addresses, private keys) found no match.
- Tracer gate: the full `verify` was re-run end to end after the tracer commit and passed before the expansion task started.

## Docs differences

none. The Microsoft pages could not be fetched in this session (no web fetch tool); the 03-RESEARCH.md record of 2026-11-22 was used. Form fields, scope and the `/users/{id}/sendMail` path match it. Plan 03-05 should still confirm against the live pages when it writes `docs/architecture.md`.

## Deviations from Plan

None - plan executed as written. Minor additions inside the plan's scope: `InvalidMailException` is declared on the test helper that builds a mail, and the token and send paths each map a non-JSON or empty body to "no field" instead of failing.

## Known Stubs

None that block the plan goal. Intentional tracer gaps, each filled by a later plan: `GraphTransport.validate` checks nothing (03-03), the `saveToSentItems` constructor value is stored but not yet written to the request (03-03), the `privateKey`, `certificate` and `clock` fields of `EntraClientCredentials` are unused (03-02), no 401 retry (03-04).

## Threat Flags

None beyond the plan's threat model. T-03-01 (no redirects) and T-03-03 (built-in id cannot be taken by an external jar) are in place and tested for the registry half; the redirect test belongs to plan 03-04.

## Self-Check: PASSED

- Created files exist (checked with the listing of the commit diff): all four new source files and the test.
- Commits `ab78150` and `2eda267` are on `main`; identity Stephan Wald for author and committer.
