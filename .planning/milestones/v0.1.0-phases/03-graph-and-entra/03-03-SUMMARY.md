---
phase: 03-graph-and-entra
plan: 03
subsystem: transport
tags: [graph, sendmail, attachments, size-limits, wiremock, jackson]

requires:
  - phase: 03-graph-and-entra
    provides: minimal GraphTransport and GraphTransportProvider (plan 03-01)
provides:
  - full Graph sendMail request body (recipients with names, one body, reply-to, file attachments, saveToSentItems)
  - option rules of the graph provider (baseUrl, saveToSentItems, missing TokenProvider)
  - validate with the 3 MiB per-file, 3 MiB total and 4 MiB request-estimate limits
  - StubTokenProvider test helper for the graph tests (reused by plan 03-04)
affects: [03-04, 03-05]

actuals:
  tokens: 9200
  tasks: 2
  commits: 2
plan_head_before: 9b623ab31197c0071374da5f80a83e8f57f1987a
plan_head_after: 41fe019acb0cab7f84c44d3469c42d65db6c58b1

tech-stack:
  added: []
  patterns:
    - "all request building stays in GraphTransport; no builder class or strategy"
    - "validate reads file sizes and the body text only; send reads file content after the token-free body build"

key-files:
  created:
    - morimail/src/test/java/de/morisoft/morimail/graph/StubTokenProvider.java
    - morimail/src/test/java/de/morisoft/morimail/graph/GraphRequestTest.java
    - morimail/src/test/java/de/morisoft/morimail/graph/GraphValidateTest.java
  modified:
    - morimail/src/main/java/de/morisoft/morimail/graph/GraphTransport.java
    - morimail/src/main/java/de/morisoft/morimail/graph/GraphTransportProvider.java

key-decisions:
  - "The request body is built before the token is asked for, so an unreadable attachment causes no token request and no POST"
  - "The body size in the estimate is measured with the same ObjectMapper that send uses (writeValueAsBytes length minus 2), so the estimate bounds what is posted by construction"
  - "Option errors name the key and never the value; an unknown key lists the two known keys"

requirements-completed: [GRAPH-01, GRAPH-02, GRAPH-03, GRAPH-04, GRAPH-05, TEST-03]

duration: about 20 min
completed: 2026-11-22
status: complete
---

# Phase 3 Plan 03: A complete Graph sendMail request, with options checked and oversized mails rejected Summary

**The Graph transport now sends the full documented sendMail JSON (names, cc, bcc, reply-to, one body, base64 file attachments, saveToSentItems only when false), rejects bad options naming the key, and rejects mails over the 3 MiB or 4 MiB limits before any token request or byte is sent.**

## Performance

- **Duration:** about 20 min
- **Tasks:** 2 of 2
- **Files modified:** 5 (3 created, 2 modified)

## Accomplishments

- `GraphTransport.requestBody` builds `message` with `subject`, one `body` (HTML when non-blank, else Text), `toRecipients`, `ccRecipients`, `bccRecipients` and `replyTo` (arrays of `{"emailAddress": {"address", "name"}}`, `name` only for a non-empty display name, empty lists and a null reply-to omitted) and `attachments` as `#microsoft.graph.fileAttachment` with standard base64. No `from` key. `saveToSentItems` goes out as the JSON boolean `false` only when the option is false.
- A mail with both bodies logs one fixed INFO record per transport instance (`AtomicBoolean`); the text carries no address, subject or body text.
- `GraphTransportProvider.create` accepts exactly `baseUrl` (absolute http or https URL with a host) and `saveToSentItems` (Boolean); unknown keys, wrong types, bad URLs and a null `TokenProvider` throw `IllegalArgumentException` naming the key and never the value. The Javadoc states options, defaults and rejections; the release build with doclint is clean.
- `GraphTransport.validate` applies, in order, the per-file rule, the total rule (every file named in attach order) and the request estimate (`base64Size` of the attachments, the JSON-escaped body bytes and a 16 KiB allowance). Each limit is one constant. Boundaries are pinned at 3,145,727 / 3,145,728 bytes, two files of 1,572,864 bytes, 4,177,920 / 4,177,921 letters, 2,088,960 / 2,088,961 umlauts and quotes.
- `StubTokenProvider` records scopes, rejected tokens and call counts thread-safely, with `failFresh(MailException)` for plan 03-04.

## Task Commits

1. Task 1 (tracer): `9ad4f08` feat(03-03): send the full Graph sendMail request and check the graph options
2. Task 2: `41fe019` feat(03-03): reject oversized mails in the Graph validate step

## Verification

- `./mvnw -B -ntp verify`, `./mvnw -B -ntp -o verify` and `./mvnw -B -ntp -Prelease clean verify -Dgpg.skip -pl morimail -am`: all BUILD SUCCESS, no Javadoc warnings.
- `GraphRequestTest` 22 tests, `GraphValidateTest` 19 tests, `SendThroughGraphTest` 4 tests; none skipped.
- Hygiene greps (paths, non-zero GUIDs, non-example addresses, private keys) found no match.
- Tracer gate: the full verify and release build passed after the Task 1 commit's content was final, before the expansion task started.
- TDD note: both test classes were written before the code. `GraphRequestTest` was run red (13 failures, 1 error) before the implementation. `GraphValidateTest` was written first but not run before the implementation; its red state would have been a compile failure on the missing `base64Size`.

## Docs differences

The Microsoft pages (user-sendmail, outlook-large-attachments) could not be fetched in this session (no web fetch tool), so the 03-RESEARCH.md record was used and no new difference was found against it. One finding for plan 03-05's `docs/architecture.md`: Jackson writes a character outside the Basic Multilingual Plane (an emoji) in a JSON string as a `😀` escape pair (12 bytes), not as 4 raw UTF-8 bytes; other non-ASCII characters such as ü stay raw (2 bytes). The plan text assumed 4 bytes for an emoji. The estimate measures the body with the same mapper that send uses, so it counts the bytes actually posted and stays an upper bound; only the plan's stated emoji figure was wrong. Plan 03-05 should still confirm the 3 MiB and 4 MiB limits against the live pages.

## Deviations from Plan

None - plan executed as written. The one wrong assumption (emoji byte count above) changed a test assertion only; the code follows the plan's rule of counting the body as sent.

## Known Stubs

None.

## Threat Flags

None beyond the plan's threat model. T-03-13 (Jackson writes every JSON value, sender percent-encoded), T-03-16 (limits decided from file sizes before any read or send) and T-03-22 (fixed INFO text, file-name-only attachment errors, key-only option errors) are implemented and covered by tests.

## Self-Check: PASSED

- Created files exist: `StubTokenProvider.java`, `GraphRequestTest.java`, `GraphValidateTest.java` under `morimail/src/test/java/de/morisoft/morimail/graph/`.
- Commits `9ad4f08` and `41fe019` are on `main`; author and committer are Stephan Wald for both.
