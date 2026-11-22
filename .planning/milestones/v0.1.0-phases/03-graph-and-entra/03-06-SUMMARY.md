---
phase: 03-graph-and-entra
plan: 06
subsystem: graph
tags: [graph, error-mapping, privacy, ci, junit, logging, gap-closure]

requires:
  - phase: 03-graph-and-entra
    provides: Graph error mapping (03-04), sample end-to-end test (03-05), Entra token error tests (03-02)
provides:
  - Graph failure messages with HTTP status, cleaned error.code and request-id only (no Graph free text)
  - GraphErrorMappingTest case with a plain and a percent-encoded address in Graph's text
  - GraphEndToEndTest proof that the stderr stop line and the journal errorMessage hold no Graph text
  - Thread-safe root-logger capture in EntraTokenErrorsTest
affects: [phase-04, verify-work-03, secure-phase-03]

actuals:
  tokens: 5000
  tasks: 3
  commits: 3
plan_head_before: c4b7ec9d8cadcfa663e7d4e1a49d2870ada6492d
plan_head_after: b388ae9c6411cfa4162d3ce0f437ad62c36f8358

tech-stack:
  added: []
  patterns:
    - "server free text never enters an exception message; only fixed-shape server fields (status, code, request-id) do"
    - "test handlers on the root logger collect into CopyOnWriteArrayList because other threads publish"

key-files:
  created: []
  modified:
    - morimail/src/main/java/de/morisoft/morimail/graph/GraphTransport.java
    - morimail/src/test/java/de/morisoft/morimail/graph/GraphErrorMappingTest.java
    - morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/GraphEndToEndTest.java
    - morimail/src/test/java/de/morisoft/morimail/entra/EntraTokenErrorsTest.java
    - docs/architecture.md
    - .planning/REQUIREMENTS.md
    - .planning/ROADMAP.md

key-decisions:
  - "03-06: Graph failure messages carry HTTP status, code and request-id only; Graph's error.message is dropped (G-03-2)"
  - "03-06: root-logger capture in EntraTokenErrorsTest uses CopyOnWriteArrayList; named-logger captures in the Graph tests stay plain lists (G-03-4)"

requirements-completed: [GRAPH-08, TEST-03, ENTRA-09, TEST-02, REPO-07, REPO-08]

coverage:
  - id: D1
    description: "A Graph failure message holds the HTTP status, cleaned error.code and request-id and no Graph free text"
    requirement: GRAPH-08
    verification:
      - kind: unit
        ref: "morimail/src/test/java/de/morisoft/morimail/graph/GraphErrorMappingTest.java#graphsOwnMessageNeverReachesTheExceptionMessage"
        status: pass
    human_judgment: false
  - id: D2
    description: "The sample's stderr stop line and journal errorMessage hold no Graph text after a 403"
    requirement: GRAPH-08
    verification:
      - kind: e2e
        ref: "morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/GraphEndToEndTest.java#aRejectedMailboxStopsTheRunWithExitThree"
        status: pass
    human_judgment: false
  - id: D3
    description: "The failure message change is recorded in docs/architecture.md, GRAPH-08 and Phase 3 criterion 4"
    requirement: GRAPH-08
    verification: []
    human_judgment: true
    rationale: "Documentation wording; a reader checks it, no test asserts it"
  - id: D4
    description: "EntraTokenErrorsTest root-logger capture no longer races with logging threads"
    requirement: ENTRA-09
    verification:
      - kind: unit
        ref: "morimail/src/test/java/de/morisoft/morimail/entra/EntraTokenErrorsTest.java#nothingSecretReachesALogOrAMessage"
        status: pass
    human_judgment: true
    rationale: "The race does not reproduce on JDK 21; only the owner's Temurin 17 CI run confirms the fix (G-03-4)"

duration: 8min
completed: 2026-11-22
status: complete
---

# Phase 3 Plan 06: Graph text out of failure messages and a race-free log test Summary

**Graph failure messages now carry only the HTTP status, `error.code` and `request-id` (Graph's free-text `error.message` is dropped), and the root-logger capture in `EntraTokenErrorsTest` uses `CopyOnWriteArrayList`.**

## Performance

- **Duration:** 8 min
- **Started:** 2026-11-22T12:07:00Z
- **Completed:** 2026-11-22T12:15:00Z
- **Tasks:** 3
- **Files modified:** 7

## Accomplishments

- G-03-2: `GraphTransport.failure` no longer reads `error.message`. A 404 `ErrorInvalidUser` whose text names the sender gives `Graph sendMail failed: HTTP 404, code ErrorInvalidUser, request-id <zero GUID>` and a `SenderRejectedException`. A text pattern that replaces addresses was already in place (review fix WR-02) and still let an address through in the live test, so the text is dropped, not scrubbed harder. `clean()` stays for the two remaining server fields.
- Eight existing `GraphErrorMappingTest` cases now expect the three-field layout. The control-character, 300-character, surrogate-pair and address rules stay covered by passing the same dirty text through `error.code`. A new parameterized test pins a plain and a percent-encoded address in Graph's text (2 cases).
- `GraphEndToEndTest.aRejectedMailboxStopsTheRunWithExitThree` now uses a Graph text that names the sender, still exits 3, and asserts that neither the stderr stop line nor the journal `errorMessage` holds "newsletter" or "Access to".
- `docs/architecture.md` has a `§6.3 (failure message)` row below the error-table row, and its Contracts bullet no longer says the service text is quoted for Graph. GRAPH-08 and Phase 3 success criterion 4 are reworded with the G-03-2 citation.
- G-03-4: `seen` and `loggers` in `EntraTokenErrorsTest.nothingSecretReachesALogOrAMessage` are `CopyOnWriteArrayList`s. Reads walk a snapshot, so a record published by another thread during the loop, or after `removeHandler`, cannot throw `ConcurrentModificationException`. Assertions, `Level.ALL` and the `finally` cleanup are unchanged.

## Task Commits

1. **Task 1: Graph text out of failure messages (G-03-2)** - `e648406` (fix)
2. **Task 2: record the narrower message in docs, GRAPH-08 and the roadmap** - `b2d99cc` (docs)
3. **Task 3: thread-safe root-logger capture (G-03-4)** - `b388ae9` (test)

## Checks on JDK 21

| Command | Result |
|---------|--------|
| `./mvnw -B -ntp verify` | BUILD SUCCESS (library 306 tests, sample 137, 0 failures, 0 skipped) |
| `./mvnw -B -ntp -Prelease clean verify -Dgpg.skip -pl morimail -am` | BUILD SUCCESS (306 tests, Javadoc clean) |
| `./mvnw -B -ntp -o verify` | BUILD SUCCESS (306 and 137 tests; local stand-in for the CI `unshare --net` step) |

Plan-level checks: no address outside example.com, example.org and the maintainer's in the changed files, no absolute home path, and no change to any POM, `ci.yml` or `docs/spec.md`. Static gates passed: no code line of `GraphTransport` reads `error.get("message")`, `EntraTokenErrorsTest` is the only test that attaches to the root logger (`getLogger("")`), and the test is not disabled or weakened.

## Why the Graph tests' handlers were left alone (G-03-4 missing item 2)

`GraphErrorMappingTest` and `GraphRequestTest` attach their handler to the named `de.morisoft.morimail.graph.GraphTransport` logger. Only `GraphTransport` writes to it, from the thread that calls `send`, and both tests call `send` on the test thread. Surefire runs one class at a time. Their plain lists are never shared between threads, so they are unchanged (Rule 0).

## Decisions Made

- 03-06: Graph failure messages carry HTTP status, code and request-id only; Graph's `error.message` is dropped (G-03-2). It replaces the 03-04 decision that included the cleaned `error.message`.
- 03-06: `CopyOnWriteArrayList` for the root-logger capture; moving the assertions after the `finally` block would not cover a record still being published after `removeHandler`.

## Deviations from Plan

None - plan executed exactly as written, with one note: the plan's commit-trailer text named no model; the run's attribution line (`Claude Sonnet 5.5`) was used, which matches the plan's trailer-shape gate.

## Issues Encountered

None.

## Known Stubs

None.

## Threat Flags

None. No new network endpoint, auth path, file access or schema change.

## Pending owner action

1. Push, then confirm on Temurin 17 that all three CI steps are green, including the `unshare --net` step that never ran before (G-03-4 missing item 3). The race does not reproduce on JDK 21 (ten pinned runs at planning time passed at HEAD), so only that run closes G-03-4. If step 1 then fails on a content assertion instead of the exception, report the logger name, never the record text.
2. Optional: re-run the live main test with a sender that has no Exchange mailbox and confirm the printed line has no address (G-03-2). `LiveGraphTest` needed no edit.
3. Re-run verify-work for phase 03 so UAT tests 2 and 4 are recorded again. T-03-19 in `03-SECURITY.md` assumed address-free messages; a secure-phase re-run can confirm it.

## Next Phase Readiness

Gaps G-03-2 and G-03-4 are fixed locally. Phase 3 stays at the owner STOP until the Temurin 17 CI run is green and the verify-work re-run records UAT tests 2 and 4.

## Self-Check: PASSED

- Files modified exist: GraphTransport.java, GraphErrorMappingTest.java, GraphEndToEndTest.java, EntraTokenErrorsTest.java, docs/architecture.md, REQUIREMENTS.md, ROADMAP.md.
- Commits `e648406`, `b2d99cc`, `b388ae9` are on `main`, authored and committed as Stephan Wald with the Co-Authored-By trailer, and not pushed.
