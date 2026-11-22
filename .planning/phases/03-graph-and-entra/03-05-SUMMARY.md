---
phase: 03-graph-and-entra
plan: 05
subsystem: sample
tags: [sample, e2e, wiremock, entra, graph, live-test, ci, docs]

requires:
  - phase: 03-graph-and-entra
    provides: certificate flow and cache (03-02), full Graph request and limits (03-03), Graph error mapping and 401 retry (03-04)
provides:
  - SerialMail.mailer(account, env, authority, graphBaseUrl) building Entra credentials and a registry graph account
  - GraphEndToEndTest (CLI-14 against WireMock for token and Graph, 9 tests)
  - LiveGraphTest for the owner (tag live, excluded by default, opt-in probes)
  - network-free CI build step
  - docs/architecture.md deviations and "Differences from the Microsoft documentation"
affects: [phase-04, owner-live-test]

actuals:
  tokens: 21000
  tasks: 3
  commits: 3
plan_head_before: 7a1dbf936d9673ec17c8173c7c178f9a4ac9cc00
plan_head_after: a730bf87f7f4dcc8ab260ebe9d73cfe95778428a

tech-stack:
  added: []
  patterns:
    - "one package-private seam (mailer with env, authority and base URL) lets tests reach the real wiring with an injected environment and WireMock"
    - "pre-flight messages are fixed texts naming keys; the library message that names the key-store path is wrapped"

key-files:
  created:
    - morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/GraphEndToEndTest.java
    - morimail/src/test/java/de/morisoft/morimail/LiveGraphTest.java
  modified:
    - morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/SerialMail.java
    - morimail-sample-serial/pom.xml
    - morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/AccountFileTest.java
    - morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/SendRunTest.java
    - .github/workflows/ci.yml
    - docs/architecture.md

key-decisions:
  - "No new key in account.properties and no defaultFrom on the sample account: every mail carries its own from"
  - "Key-store path problems and load failures are fixed texts; the library message (which names the file) never reaches the console"
  - "One WireMock server serves both the token endpoint and Graph"

requirements-completed: [CLI-14, REPO-08]

duration: about 45 min
completed: 2026-11-22
status: complete
---

# Phase 3 Plan 05: The sample sends for real (against WireMock), the owner's live test, and the record Summary

**`serial-mail send` now builds an Entra token provider and a registry-built graph account from account.properties and the environment, delivers the example campaign once through WireMock, and stops in pre-flight or with exit 3 as the plan requires.**

## Performance

- **Duration:** about 45 min
- **Tasks:** 3 of 3
- **Files modified:** 8 (2 created, 6 modified)

## Accomplishments

- `SerialMail.realMailer` delegates to `mailer(AccountFile, Function<String,String> env, URI authority, String graphBaseUrl)` with `System::getenv`, `https://login.microsoftonline.com` and `https://graph.microsoft.com/v1.0`. The Phase 2 not-available constant is gone and the three Phase 2 assertions that used it now assert the D-14 message.
- `GraphEndToEndTest` (9 tests, none skipped, all with `@WireMockTest` on a dynamic localhost port): the example campaign through the certificate flow (copied throwaway PKCS12, `certificate.path` plus `certificate.passwordEnv`) makes one token request (assertion, no secret) and five sendMail calls with both attachments and prints five accepted lines; a rerun makes no request and prints five `skipped (journal)` lines; the secret flow sends `client_secret` and no assertion; unset and empty secret and password variables, a missing key-store file and a wrong key-store password are pre-flight problems with exit 2 that name keys only (no password, no path), with zero requests and no journal; a 403 for clara stops the run with exit 3, journals `SenderRejectedException`, and never requests david or emma.
- `LiveGraphTest` (library tests, `@Tag("live")`): one main send with a small attachment (prints which flow ran) and two probes (out-of-scope mailbox, 2.9 MB attachment) skipped unless their variable is set. Without a required `MORIMAIL_LIVE_*` variable it fails in `@BeforeAll` before any network call and names every missing variable. It does not run in the default build.
- CI: the last step now runs the offline build in a fresh network namespace (`unshare --net`, loopback up, `setpriv` back to the runner user).
- `docs/architecture.md`: ten new deviation rows, two contract bullets and the section "Differences from the Microsoft documentation", checked against the pages the orchestrator fetched on 2026-11-22.

## Task Commits

1. Task 1 (tracer): `7f43dff` feat(03-05): send the example campaign through Entra and Graph from the sample
2. Task 2: `39b7836` feat(03-05): stop a real run before sending on a missing secret, a broken key store or a rejected mailbox
3. Task 3: `a730bf8` feat(03-05): add the owner's live test, a network-free CI build and the phase deviations

## Verification

- `./mvnw -B -ntp verify`, `./mvnw -B -ntp -o verify` and `./mvnw -B -ntp -Prelease clean verify -Dgpg.skip -pl morimail -am`: BUILD SUCCESS, no Javadoc warnings; the sample module ran 137 tests green in the last offline run, GraphEndToEndTest 9 tests, none skipped.
- `-Plive` with every `MORIMAIL_LIVE_*` variable removed: fails in `@BeforeAll`; the report names all missing variables. The default build produces no LiveGraphTest report. `-Plive` was never run with variables set.
- `morimail-sample-serial/example-campaign/journal.jsonl` does not exist after the build.
- Hygiene greps (home paths, non-zero GUIDs, non-example addresses, private keys) found no match.
- Tracer gate: the full `verify` passed after Task 1 was final, before Task 2 started.
- TDD: Task 2's key-store path and password tests were run red first (two failures on the unwrapped library message); the Task 1 end-to-end test could only fail by compile error before the implementation, so it was written first and passed at first run.
- The network-free CI step could not be rehearsed here (no network namespace in this workspace); locally the same Maven command ran without the namespace. The owner's first push proves it.

## Docs differences

Written into `docs/architecture.md`, from the 2026-11-22 orchestrator check: the 3 MiB and 4 MiB limits are not stated on the sendMail page (3 MB is the single-POST attachment threshold); `innerError` and `innererror` are both spelled; the `request-id` header name is not stated; `Retry-After` is documented in seconds only; the examples show `saveToSentItems` as the string `"false"`; the PS256, `x5t#S256` and claims match; the Jackson emoji escape finding from 03-03.

## Deviations from Plan

None - plan executed as written. In the live test the "accepted instead of rejected" branch of the out-of-scope probe uses `fail(...)` from JUnit.

## Known Stubs

None.

## Threat Flags

None.

## STOP: report for the owner (ROADMAP Phase 3 checkpoint)

Phase 3 is complete; do not start Phase 4. The owner runs `./mvnw -Plive test -pl morimail` with the `MORIMAIL_LIVE_*` variables (listed in the `LiveGraphTest` Javadoc) and reports:

- whether Entra accepts the PS256 certificate assertion, and any clock skew
- the real Graph responses behind each spec 6.3 row (403, 404, `MailboxNotEnabledForRESTAPI`, a 401 after a fresh token), which also decides whether the `mailbox` hint catches an unlicensed mailbox
- the 2.9 MB boundary: the live test covers only the 2.9 MB send; a 3.5 MB send is owner-manual with a hand-made request, because `validate` rejects it before sending
- the RBAC for Applications scope (a mailbox outside it is refused)
- whether the `from` display name and a shared mailbox work
- the Exchange per-mailbox limits behind the `--rate 20` default
- the open spec section 12 decisions (`LICENSE` holder, `SECURITY.md` contact, the `Co-Authored-By` trailer)
- the first CI run, including the network-free step (a namespace refusal on the runner is an environment reason to report, not to change silently)

## Self-Check: PASSED

- Files found: GraphEndToEndTest.java, LiveGraphTest.java, SerialMail.java, ci.yml, docs/architecture.md.
- Commits found: 7f43dff, 39b7836, a730bf8.
