---
phase: 03-graph-and-entra
plan: 04
subsystem: transport
tags: [graph, error-mapping, retry-after, 401-retry, wiremock]

requires:
  - phase: 03-graph-and-entra
    provides: full Graph request, validate and StubTokenProvider (plan 03-03); TokenProvider.freshAccessToken seam
provides:
  - single 401 retry with a fresh token through the TokenProvider interface
  - error mapping of every status (spec 6.3 rows plus the class rule) with Retry-After
  - failure messages with HTTP status, code, request-id and cleaned message, free of tokens and bodies
  - GraphErrorMappingTest (61 tests)
affects: [03-05]

actuals:
  tokens: 7100
  tasks: 3
  commits: 3
plan_head_before: b09397fbec73d8ad9934cbdd8ba19c7d8653ed94
plan_head_after: 0519e6ca05726bd8e3429be63a45facd7d0f17b3

tech-stack:
  added: []
  patterns:
    - "all response handling stays in GraphTransport; no mapper class, retry policy or strategy"
    - "retryAfter and clean are written again in graph on purpose; graph and entra never share code"

key-files:
  created:
    - morimail/src/test/java/de/morisoft/morimail/graph/GraphErrorMappingTest.java
  modified:
    - morimail/src/main/java/de/morisoft/morimail/graph/GraphTransport.java

key-decisions:
  - "failure(response) takes no retried flag: a 401 reaches it only after the one retry, so the flag would be unused"
  - "A null or empty token from the provider is AuthRequiredException before any request, for the first token and the fresh one"
  - "Any parse problem of the error body leaves code and message as a dash; nothing from the raw body is ever copied"

requirements-completed: [GRAPH-01, GRAPH-06, GRAPH-07, GRAPH-08, GRAPH-09, TEST-03]

duration: about 25 min
completed: 2026-11-22
status: complete
---

# Phase 3 Plan 04: Every Graph failure maps to the right category, after one retry with a fresh token on a 401 Summary

**GraphTransport retries a 401 once with `freshAccessToken`, maps every status to the documented exception with Retry-After, and builds failure messages from the status, error code, request-id and cleaned Graph message only.**

## Performance

- **Duration:** about 25 min
- **Tasks:** 3 of 3
- **Files modified:** 2 (1 created, 1 modified)

## Accomplishments

- `send` builds the body, asks for the token (a null or empty token is `AuthRequiredException` "the token provider returned no access token" with no request), posts once, and on a 401 calls `auth.freshAccessToken(SCOPE, token)` in one place and posts the same body once more. There is no other retry and redirects are never followed.
- `failure` maps: a second 401 gives `SenderRejectedException` when `mailboxHint(error.code)` is true (the code contains `mailbox`, `Locale.ROOT`), else `AuthRequiredException`; 403 and 404 `SenderRejectedException`; 429 and 5xx `TransientMailException` with a typed `Duration retryAfter`; everything else, 1xx and 3xx included, `PermanentMailException`. IO errors and timeouts stay `TransientMailException` with the cause.
- `Retry-After` is read as 1 to 9 digits of seconds or an RFC 1123 date; a past date gives `PT0S`, junk or no header gives none.
- Message layout: `Graph sendMail failed: HTTP <status>, code <code>, request-id <id>, message: <message>`. The request-id comes from the `request-id` header, else `error.innerError`, else `error.innererror`, else `-`. `clean` turns control characters into spaces, collapses whitespace, cuts at 300 characters (299 when the cut would split a surrogate pair) plus `...`, and gives `-` for null or empty.
- `GraphErrorMappingTest` (61 tests, none skipped): the 401 retry (header order, same body, rejected token, scope), the mailbox hint, a failing fresh token, no token, every status row with exactly one request, Retry-After forms, 300 and 302 never followed, repeat after 503, inner-error request-ids, hostile bodies (empty, HTML, `{}`, `{"error":{}}`, `{"error":"text"}`), control characters, the 300 cut and the surrogate pair, token, subject and body sentinels with a log handler, connection reset, timeout on the first and the retried request, and three two-thread cases (own mapping, own code and request-id, 4 requests and 2 fresh-token calls for two threads rejected with the same token).

## Task Commits

1. Task 1 (tracer): `5bb1360` feat(03-04): retry a 401 once with a fresh token and name status, code and request-id in failures
2. Task 2: `2cd518e` feat(03-04): carry Retry-After on 429 and 5xx and pin the status table
3. Task 3: `0519e6c` feat(03-04): clean Graph failure messages and take the request-id from the inner error

## Verification

- `./mvnw -B -ntp verify`, `./mvnw -B -ntp -o verify` and `./mvnw -B -ntp -Prelease clean verify -Dgpg.skip -pl morimail -am`: all BUILD SUCCESS, no Javadoc warnings.
- GraphErrorMappingTest 61 tests (16 after Task 1, 44 after Task 2), none skipped; GraphRequestTest, GraphValidateTest and SendThroughGraphTest stay green.
- Acceptance greps: one `freshAccessToken(SCOPE`, one `contains("mailbox")`, no entra reference on a code line of GraphTransport.
- Hygiene greps (home paths, non-zero GUIDs, non-example addresses, private keys) found no match.
- Tracer gate: the full `verify` passed end to end after the Task 1 content was final, before Task 2 started.
- TDD: each task's tests were run red first (Task 1 a compile failure on the missing `mailboxHint`, Task 2 four Retry-After failures, Task 3 five failures for the inner error and cleaning); the network, timeout and parallel tests passed at once because plans 03-01 and Tasks 1 and 2 already gave that behavior, as the plan predicted.

## Docs differences

none. The Microsoft errors page could not be fetched in this session (no web fetch tool was available, as in plans 03-01 to 03-03), so the 03-RESEARCH.md record of 2026-11-22 was followed. Plan 03-05 should confirm the status table, the `request-id` header and the `innerError` shape against https://learn.microsoft.com/en-us/graph/errors when it writes `docs/architecture.md`. Still open (RESEARCH Assumption A7, for the owner's live test): which `error.code` the real service returns for an unlicensed or missing mailbox, which decides whether `mailboxHint` catches it.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Simplicity] No `retried` parameter on `failure`**
- **Found during:** Task 1
- **Issue:** The plan lists `failure(response, retried)`, but the flag has no use: a 401 reaches `failure` only after the retry.
- **Fix:** `failure(HttpResponse<byte[]>)` takes the response only. Behavior is as the plan describes.
- **Files modified:** `morimail/src/main/java/de/morisoft/morimail/graph/GraphTransport.java`
- **Commit:** `5bb1360`

Otherwise the plan was executed as written.

## Known Stubs

None.

## Threat Flags

None beyond the plan's threat model. T-03-11 (no body, token or address in messages or logs; sentinel and log-handler test), T-03-12 (no redirect followed; 300 and 302 tests), T-03-14 (`clean`) and T-03-15 (one request per status, two only after a 401) are implemented and tested.

## Self-Check: PASSED

- Created and changed files exist: `GraphErrorMappingTest.java`, `GraphTransport.java` under `morimail/src/`.
- Commits `5bb1360`, `2cd518e` and `0519e6c` are on `main`; author and committer are Stephan Wald for all three.
