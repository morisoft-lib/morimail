---
phase: 05-inline-images-in-core-and-graph
plan: 02
subsystem: graph-transport-limits-live-test-docs
tags: [inline-images, graph, size-limits, live-test, architecture-docs]

requires:
  - phase: 05-inline-images-in-core-and-graph
    provides: "Plan 05-01: Mail.Builder.inline, build() cid: checks, Graph isInline and contentId"
provides:
  - "Tests that pin inline images into the unchanged Graph 3 MiB per-file, 3 MiB total and 4 MiB estimate rules, before any token request"
  - "LiveGraphTest.sendsOneMailWithAnInlineImage with a PNG generated at run time, for the owner to run"
  - "docs/architecture.md: Graph inline property names and sources, live status, three deviation rows, extended Attachments contract"
affects: [06 sample inline folder, 07 morimail-html, 08 docs and spec update]

actuals:
  tokens: 2600
  tasks: 2
  commits: 2

tech-stack:
  added: []
  patterns:
    - "Size tests use sparse files named *.png in a TempDir plus a one-line HTML body that references the content ID"
    - "Live PNG is generated with ImageIO in test code, so no image file is tracked"

key-files:
  created: []
  modified:
    - morimail/src/test/java/de/morisoft/morimail/graph/GraphValidateTest.java
    - morimail/src/test/java/de/morisoft/morimail/LiveGraphTest.java
    - docs/architecture.md

key-decisions:
  - "No production change: GraphTransport.validate and base64Size stay as they are; the tests prove inline images count (D-15, D-16, D-17)"
  - "docs/spec.md stays untouched; the deviation rows in docs/architecture.md say that spec sections 5 and 5.1 still describe the MVP (Phase 8 updates them)"
  - "The inline Microsoft-docs bullet is dated 2026-11-22, the fetch date recorded in 05-01-SUMMARY.md, and gives the live status as not yet confirmed"

patterns-established:
  - "Live test method for one feature: copy the main method, label its output line live <feature>, reuse the existing variables"

requirements-completed: [GRAPH-10, GRAPH-11, GRAPH-12]

coverage:
  - id: D1
    description: "An inline image of exactly 3 MiB fails the per-file rule naming the file; 3145727 bytes fail the estimate; 2,900,000 bytes pass"
    requirement: GRAPH-11
    verification:
      - kind: unit
        ref: "GraphValidateTest#inlineImageOfThreeMiBIsReportedByThePerFileRule, inlineImageJustBelowThreeMiBFailsTheEstimateNotTheFileRules, inlineImageOfAboutTwoPointNineMegabytesPasses"
        status: pass
    human_judgment: false
  - id: D2
    description: "An inline image and a regular attachment share the 3 MiB total and are listed in builder order"
    requirement: GRAPH-11
    verification:
      - kind: unit
        ref: "GraphValidateTest#inlineImageAndRegularFileShareTheThreeMiBTotal"
        status: pass
    human_judgment: false
  - id: D3
    description: "Mailer.send of an oversized inline mail throws InvalidMailException with zero token calls and zero HTTP requests"
    requirement: GRAPH-11
    verification:
      - kind: unit
        ref: "GraphValidateTest#mailerRejectsAnOversizedInlineImageBeforeAnyTokenOrRequest"
        status: pass
    human_judgment: false
  - id: D4
    description: "LiveGraphTest.sendsOneMailWithAnInlineImage exists, compiles, is tagged live and is excluded from the default build"
    requirement: GRAPH-12
    verification:
      - kind: command
        ref: "./mvnw -B -ntp verify, then no LiveGraphTest report in morimail/target/surefire-reports"
        status: pass
    human_judgment: false
  - id: D5
    description: "A real mailbox shows the generated image inside the mail body, not as a separate attachment, in Outlook on the web and one desktop client"
    requirement: GRAPH-12
    verification: []
    human_judgment: true
    rationale: "Only the owner can send real mail through the tenant and look at the result in mail clients"
  - id: D6
    description: "docs/architecture.md records isInline and the bare contentId, the missing sendMail example, no contentLocation, the unconfirmed live status and the three deviation rows"
    requirement: GRAPH-10
    verification:
      - kind: command
        ref: "static grep gate of Task 2 (STATIC_OK)"
        status: pass
    human_judgment: false

duration: 15min
completed: 2026-11-23
status: complete
plan_head_before: cb78c4346d98641ab8904386786af71ce73c8c38
plan_head_after: 32ffa4749b2e5b8d9877a3b994d346aae9b7668e
commits: 2
---

# Phase 5 Plan 02: Inline images in the Graph size limits, the owner's live test and the Graph notes Summary

**Five tests that put inline images under the unchanged 3 MiB / 4 MiB Graph rules before any token request, the owner-run `LiveGraphTest.sendsOneMailWithAnInlineImage` with a run-time PNG, and the Graph inline property names, live status and three spec deviations in `docs/architecture.md`**

## Performance

- **Duration:** about 15 min
- **Tasks:** 2
- **Files modified:** 3 (two test files, one doc), no production code

## Accomplishments

- GRAPH-11: `GraphTransport.validate` already loops over every attachment, so the tests only pin the behavior. A 3,145,728-byte inline `big.png` gives `attachment big.png has 3145728 bytes; Graph takes attachments below 3 MiB (3145728 bytes)`. A 3,145,727-byte one fails the 4 MiB estimate without naming the file, and 2,900,000 bytes pass. `logo.png` (inline) plus `b.bin`, each 1,572,864 bytes, fail the total rule naming `logo.png, b.bin` in builder order. `Mailer.send` of the oversized inline mail throws `InvalidMailException` naming `big.png` with zero access-token calls and zero WireMock requests.
- GRAPH-12: `LiveGraphTest.sendsOneMailWithAnInlineImage` generates a 64 x 64 PNG with `ImageIO`, sends one mail whose HTML shows `cid:logo`, prints one `live inline: flow ..., accepted` or error line and reads no new variable. The class stays tagged `live`. It was compiled and never run; the default build produced no LiveGraphTest report.
- GRAPH-10 docs: `docs/architecture.md` has the `Inline images (read on 2026-11-22)` bullet, the three deviation rows (`§5 and §5.1`, `§10.3 (cid: references)`, `§10.3 (transport without inline images)`), the reworded `§10.1` live-test row and the extended Attachments contract bullet.

Microsoft pages fetched on 2026-11-22: copied from 05-01-SUMMARY.md as the date of the bullet; this plan fetched nothing new.

## Task Commits

1. **Task 1: Inline images count toward the Graph size limits** - `97266fe` (test)
2. **Task 2: Live inline-image test and Graph inline notes** - `32ffa47` (test)

**Plan metadata:** the docs commit that holds this SUMMARY, STATE.md, ROADMAP.md and REQUIREMENTS.md.

## Files Created/Modified

- `morimail/src/test/java/de/morisoft/morimail/graph/GraphValidateTest.java` - helper `withInline`, five inline-image tests
- `morimail/src/test/java/de/morisoft/morimail/LiveGraphTest.java` - `tinyPng()`, `sendsOneMailWithAnInlineImage`, Javadoc paragraph, three imports
- `docs/architecture.md` - Attachments contract sentence, three deviation rows, §10.1 row wording, Microsoft inline bullet

## Decisions Made

- No production change; `GraphTransport.java` is untouched (D-15, D-16).
- `docs/spec.md`, README and every other file are untouched; the deviation rows say the spec text still describes the MVP.
- The Microsoft bullet carries the 05-01 fetch date 2026-11-22 and says live status is not yet confirmed. 05-01 recorded no different page reading, so the plan's wording stands.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None.

## Gates on JDK 21

- Task 1 test gate: `GraphValidateTest` green with the five new and all existing limit tests present in the report; `./mvnw -B -ntp verify` BUILD SUCCESS (137 tests in the last module summary).
- Task 2: static gate OK, `./mvnw -B -ntp verify` BUILD SUCCESS with no LiveGraphTest report, `./mvnw -B -ntp -Prelease clean verify -Dgpg.skip -pl morimail -am` BUILD SUCCESS with no warning line, `./mvnw -B -ntp -o verify` BUILD SUCCESS.
- Both commit gates OK: only their own files, author and committer Stephan Wald, attribution line present. Nothing pushed.

## Known Stubs

None.

## Threat Flags

None - tests and documentation only. T-05-05 to T-05-07 mitigations are in place: rejection before any token request is tested, the live test prints only the flow and the exception class and message, and `git ls-files` lists no image under `morimail/`.

## Self-Check: PASSED

- `97266fe` and `32ffa47` are ancestors of HEAD; `git rev-list --count cb78c4346d98641ab8904386786af71ce73c8c38..HEAD` gave 2 before this SUMMARY commit.
- The three modified files exist.

## Owner checkpoint

1. **Live run (GRAPH-12).** Set the `MORIMAIL_LIVE_*` variables from the `LiveGraphTest` Javadoc and run `./mvnw -Plive test -pl morimail -Dtest=LiveGraphTest#sendsOneMailWithAnInlineImage`. Expect `live inline: flow certificate, accepted` (or `flow secret`). Open the mail in Outlook on the web and in one desktop client: the blue 64 x 64 square must show inside the mail body under the first line, not as a separate attachment. A Graph correction (for example brackets around the content ID) becomes an inserted Phase 5.1. Phases 6 and 7 do not wait for the live run; Phase 8 needs its result.
2. **Second and final Temurin 17 check.** Push the 05-02 commits and read their GitHub Actions run on Temurin 17, all three steps (`./mvnw -B -ntp verify`, `./mvnw -B -ntp -Prelease clean verify -Dgpg.skip -pl morimail -am`, `./mvnw -B -ntp -o verify`). The first check, at the 05-01 Task 3 checkpoint, covered the Javadoc and the content type table; this one covers the new size tests and the compiled live test on Java 17.

Phase 5 is not verified-complete until the second and final Temurin 17 CI run, on the pushed 05-02 commits, is green (verify, scoped release build, offline build); the first, on the 05-01 commits, was green at the 05-01 checkpoint. Claude never pushes.

3. **Owner-review items from 05-01-SUMMARY.md.** (a) The 64-code-point cut of an ID read from the HTML in the `no inline image for cid:X` message. (b) The literal D-06 consequence: `<` does not end a reference, so `cid:logo</p>` reads the ID `logo</p` and fails. (c) The bare `contentId` sent to Graph, unconfirmed until the live run.

---
*Phase: 05-inline-images-in-core-and-graph*
*Completed: 2026-11-23*
