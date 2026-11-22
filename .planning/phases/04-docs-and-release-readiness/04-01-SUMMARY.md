---
phase: 04-docs-and-release-readiness
plan: 01
subsystem: docs
tags: [graph, entra, error-cleaning, spec, architecture, live-test]
status: complete

requires:
  - phase: 03-graph-and-entra
    provides: Graph transport, Entra client, the live test findings in 03-UAT.md, review findings WR-01 and IN-01
provides:
  - clean() in GraphTransport and EntraClientCredentials that cuts at a word boundary before the address pattern runs
  - one straddling-address test per class
  - spec §6.3 with a live-status column and the 404 finding, §8.3 with the documented limits and the bibliography entry Microsoft n.d.-g
  - docs/architecture.md with a one-page contract summary, two new deviation rows and live-result bullets
affects: [04-02, 04-03, 04-04]

actuals:
  tokens: 7000
  tasks: 3
  commits: 3
plan_head_before: ed97e364fbec1452cd50ed5a7da15c9092841f65
plan_head_after: 8a58a34c1c7a7f50e0af9275e801d17e88103efd

tech-stack:
  added: []
  patterns:
    - "server text is collapsed, cut at a word boundary, then scanned for addresses; the cut text only ever meets the quadratic address pattern"

key-files:
  created: []
  modified:
    - morimail/src/main/java/de/morisoft/morimail/graph/GraphTransport.java
    - morimail/src/main/java/de/morisoft/morimail/entra/EntraClientCredentials.java
    - morimail/src/test/java/de/morisoft/morimail/graph/GraphErrorMappingTest.java
    - morimail/src/test/java/de/morisoft/morimail/entra/EntraTokenErrorsTest.java
    - .planning/phases/03-graph-and-entra/03-REVIEW-DISPOSITION.md
    - docs/spec.md
    - docs/architecture.md

key-decisions:
  - "04-01: server text is cut back to a word boundary before the address pattern runs; a single word over 1000 characters gives '-' (WR-01)"
  - "04-01: the error mapping, the 3 MiB and 4 MiB constants and the sample --rate default are unchanged; the live results matched them (D-01)"

requirements-completed: [DOCS-02]

coverage:
  - id: D1
    description: "No part of an address that straddles the 1000-character cut reaches a Graph failure message"
    requirement: DOCS-02
    verification:
      - kind: unit
        ref: "morimail/src/test/java/de/morisoft/morimail/graph/GraphErrorMappingTest.java#anAddressStraddlingTheCutLeavesNoPartOfItInTheMessage"
        status: pass
    human_judgment: false
  - id: D2
    description: "No part of an address that straddles the cut reaches an Entra failure message"
    requirement: DOCS-02
    verification:
      - kind: unit
        ref: "morimail/src/test/java/de/morisoft/morimail/entra/EntraTokenErrorsTest.java#anAddressStraddlingTheCutLeavesNoPartOfItInTheMessage"
        status: pass
    human_judgment: false
  - id: D3
    description: "Spec §6.3 and §8.3 carry the live results of 2026-11-22 and architecture.md records both edits"
    requirement: DOCS-02
    verification: []
    human_judgment: true
    rationale: "Documentation wording; a reader checks it against 03-UAT.md, no test asserts it"
  - id: D4
    description: "docs/architecture.md opens with a one-page contract summary of 10 bullets and 459 words"
    requirement: DOCS-02
    verification: []
    human_judgment: true
    rationale: "Whether the page reads as a summary is a reader's judgment; structure was checked by script"

duration: 12min
completed: 2026-11-22
---

# Phase 4 Plan 01: Live findings applied Summary

**Server text in Graph and Entra failure messages is cut at a word boundary before addresses are replaced, so no part of an address survives, and spec §6.3, §8.3 and docs/architecture.md now match the live test of 2026-11-22.**

## Accomplishments

- WR-01 and IN-01 fixed in both `clean()` methods (written twice on purpose, `graph` and `entra` do not import each other). A text over 1000 characters is cut back to the last space before character 1000. A single word over 1000 characters gives `-`.
- One straddling-address test per class. The input is 980 `x`, `@example.org`, a space, then `alice` x5 and `@example.org`. Graph gives exactly `Graph sendMail failed: HTTP 400, code <address>, request-id -`, and Entra gives a message ending in `description: <address>`. Neither contains `alice`.
- Spec §6.3 has the third column `Live status (2026-11-22)`, and the forum-answer sentence is replaced by the 404 `ErrorInvalidUser` finding. §8.3 states 30 messages per minute and 10,000 recipients per day with the new bibliography entry Microsoft n.d.-g, and records the owner's confirmation of the default of 20. §11 is byte-identical to d488908.
- docs/architecture.md: a 10-bullet, 459-word `## Contracts` summary above the deviations table, with the old Contracts bullets folded in. Two new deviation rows (`§6.3 (live results)` below `§6.3 (failure message)`, `§8.3 (rate default)` last, 18 rows in total, the 16 earlier ones verbatim). The Microsoft-difference bullets carry the live results: PS256 accepted, 404 `ErrorInvalidUser` for a sender without a mailbox, attachments of 2,900,000 and 3,100,000 bytes accepted.
- The `GraphTransport.validate` Javadoc states the live result for the 3 MiB and 4 MiB limits.
- 03-REVIEW-DISPOSITION.md: WR-01 and IN-01 are `fixed`, `open: 0`.

## Red run (Task 1)

On the unchanged main code, exactly four tests failed after the test edits: `GraphErrorMappingTest.anAddressStraddlingTheCutLeavesNoPartOfItInTheMessage`, `GraphErrorMappingTest.aVeryLongCodeIsCutAndCleanedQuickly`, `EntraTokenErrorsTest.anAddressStraddlingTheCutLeavesNoPartOfItInTheMessage` and `EntraTokenErrorsTest.aVeryLongDescriptionIsCutAndCleanedQuickly`. The red state was not committed, because every commit keeps `verify` green.

Reproduction numbers from planning (2026-11-22): the old `clean()` gave `<address> aliceal` for the straddle input in both packages, and the new one gives `<address>`. The RESEARCH input (990 `x`, a space, the address) could not prove the fix, because the old code also passed it, so the input above shrinks the first word through the address replacement.

## Changed expectations

Only two existing expectations changed: `aVeryLongCodeIsCutAndCleanedQuickly` (Graph, 100,000 `a`) now expects `code -`, and `aVeryLongDescriptionIsCutAndCleanedQuickly` (Entra, 100,000 `x`) now expects `description: -`. A single word longer than the cut has no word boundary, so none of it is kept. Both keep `@Timeout(10)` and so still guard against the quadratic address pattern. The 1000-character boundary tests (`aLongCodeIsCutAt300CharactersPlusDots`, `aLongDescriptionIsCutToThreeHundredCharacters`) are unchanged and pass.

## Error mapping unchanged (D-01)

No mapping line in `GraphTransport` or `EntraClientCredentials` changed. `MAX_ATTACHMENT_BYTES` is still `3L * 1024 * 1024`, `MAX_REQUEST_BYTES` is still `4L * 1024 * 1024`, and `morimail-sample-serial` is untouched (`--rate` stays 20). Every error-table row test is still present.

## Task Commits

1. Task 1: `b389c5a` fix(04-01): cut server text at a word boundary before replacing addresses
2. Task 2: `26e8c46` docs(04-01): write the live test results into spec 6.3 and 8.3
3. Task 3: `8a58a34` docs(04-01): summarise the contracts on one page

## Deviations from Plan

None - plan executed exactly as written. One small adjustment: the Maven logs for the release build and the offline verify went to the session scratch directory instead of `task-release.log` in the repository root, so no log file could be left untracked.

## Verification

- `./mvnw -B -ntp verify`: BUILD SUCCESS (137 tests in the sample, all green; library tests green).
- `./mvnw -B -ntp -Prelease clean verify -Dgpg.skip -pl morimail -am`: BUILD SUCCESS, no `.java:<n>: warning:` line.
- `./mvnw -B -ntp -o verify`: BUILD SUCCESS.
- Hygiene: no address other than example.com, example.org and the maintainer's; no GUID other than the zero GUID in docs; no POM or CI change since d488908. Nothing pushed.

## Known Stubs

None.

## Threat Flags

None. No new network endpoint, auth path or file access was added.

## Next Phase Readiness

Ready for 04-02. The Microsoft-doc differences found while writing the setup guide follow there.

## Self-Check: PASSED

- docs/spec.md, docs/architecture.md, both main files, both test files and 03-REVIEW-DISPOSITION.md exist and carry the edits.
- Commits b389c5a, 26e8c46 and 8a58a34 are ancestors of HEAD, authored and committed as the maintainer with the Co-Authored-By trailer.
