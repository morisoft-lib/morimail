---
phase: 05-inline-images-in-core-and-graph
plan: 01
subsystem: core-mail-and-graph-transport
tags: [inline-images, cid, graph, fileAttachment, mail-builder, jdk-only]

requires:
  - phase: 01-core
    provides: "Attachment record with the contentId field (spec section 5), Mail.Builder, MailMessage"
  - phase: 03-graph
    provides: "GraphTransport.requestBody and validate with the v0.1.0 size limits"
provides:
  - "Mail.Builder.inline(Path, String) and attach(Attachment) with a content ID as an inline image"
  - "build() checks INLN-01..05 with fixed messages, two-way cid: scan of the rendered HTML body"
  - "Graph fileAttachment node with isInline true and contentId for inline images"
  - "recording keeps inline images with their content ID (INLN-06)"
affects: [05-02 graph size limits and live test, 06 sample inline folder, 07 morimail-html, 08 docs]

actuals:
  tokens: 5700
  tasks: 3
  commits: 2

tech-stack:
  added: []
  patterns:
    - "One Attachment record and one list; a non-null contentId marks an inline image"
    - "Reference scan with one private Pattern constant and a two-way check after the attachment loop"

key-files:
  created: []
  modified:
    - morimail/src/main/java/de/morisoft/morimail/Mail.java
    - morimail/src/main/java/de/morisoft/morimail/Attachment.java
    - morimail/src/main/java/de/morisoft/morimail/graph/GraphTransport.java
    - morimail/src/test/java/de/morisoft/morimail/MailBuilderTest.java
    - morimail/src/test/java/de/morisoft/morimail/SendThroughGraphTest.java
    - morimail/src/test/java/de/morisoft/morimail/SendThroughRecordingTest.java

key-decisions:
  - "The reference pattern (?i)(?<![\\p{L}\\p{N}])cid:([^\"'\\s)>]*) implements D-04 as amended on 2026-11-22 (owner-approved in plan review); an empty ID is skipped"
  - "An ID read from the rendered HTML is cut at 64 code points plus ... in the message no inline image for cid:X; messages that name a caller-given content ID show it whole"
  - "D-06 applied literally: < does not end a reference, so cid:logo</p> reads the ID logo</p and fails"
  - "Graph gets a bare contentId (no angle brackets), no contentLocation, and no isInline false on regular attachments"
  - "No validate code for transports without inline support (D-02); RecordingTransport and GraphTransport.validate unchanged"

patterns-established:
  - "Check order in the attachment loop: file, file name, content type, then content ID shape, image type, duplicate; after the loop missing image, then unreferenced image"
  - "Messages never echo a rejected content ID shape; the three shape messages are fixed text"

requirements-completed: [INLN-01, INLN-02, INLN-03, INLN-04, INLN-05, INLN-06]

coverage:
  - id: D1
    description: "Mail.Builder.inline(Path, String) adds an inline image that keeps its content ID, file name and resolved type through build()"
    requirement: INLN-01
    verification:
      - kind: unit
        ref: "MailBuilderTest#inlineImageKeepsItsContentIdFilenameAndType"
        status: pass
    human_judgment: false
  - id: D2
    description: "build() rejects bad content IDs, non-image types, duplicate IDs, a cid: reference without an inline image and an unreferenced inline image, each with its fixed message"
    requirement: INLN-05
    verification:
      - kind: unit
        ref: "MailBuilderTest#blankContentIdIsRejected, contentIdWithALineBreakIsRejected, contentIdWithAReferenceEndingCharacterIsRejected, referenceWithoutInlineImageFails, unreferencedInlineImageFails, duplicateContentIdFails, nonImageInlineFileFails, longReferenceIsCutInTheMessage"
        status: pass
    human_judgment: false
  - id: D3
    description: "recording keeps the inline image with its content ID next to a regular attachment"
    requirement: INLN-06
    verification:
      - kind: unit
        ref: "SendThroughRecordingTest#inlineImageIsRecordedWithItsContentIdApartFromTheRegularAttachment"
        status: pass
    human_judgment: false
  - id: D4
    description: "Graph sendMail body marks the inline image isInline true with contentId and leaves the regular attachment unmarked, tracer through Mailer, registry, Entra stub and WireMock"
    requirement: GRAPH-10
    verification:
      - kind: integration
        ref: "SendThroughGraphTest#inlineImageArrivesAsAnInlineFileAttachmentNextToARegularOne"
        status: pass
    human_judgment: false
  - id: D5
    description: "Exchange shows a bare contentId as the target of cid:<contentId> in real mail clients"
    requirement: GRAPH-10
    verification: []
    human_judgment: true
    rationale: "Only the owner live run (Plan 05-02, GRAPH-12) can confirm what a real mailbox renders"

duration: 6min
completed: 2026-11-23
status: complete
plan_head_before: 308ed6e8e6722be17c16f5292c842eb3962a178f
plan_head_after: ea77e48d924e906c1f180e72c0af9d52d7d491db
commits: 2
---

# Phase 5 Plan 01: Inline images in the core and on the Graph wire Summary

**`Mail.Builder.inline(Path, String)` with build()-time content-ID, image-type, duplicate and two-way `cid:` checks, and Graph `fileAttachment` nodes carrying `isInline` and `contentId`, proven through Mailer, Entra stub and WireMock**

## Performance

- **Duration:** 6 min of coding (tracer and checks), then the owner checkpoint
- **Started:** 2026-11-22T23:02:32Z
- **Completed:** 2026-11-23
- **Tasks:** 3 (tracer, checks, owner CI checkpoint)
- **Files modified:** 6

## Accomplishments

- A templated HTML mail with `<img src="cid:logo">`, one inline image and one regular attachment goes from `Mail.using(...)` through `Mailer`, the registry, the Entra token stub and `GraphTransport` to WireMock. The inline node carries `isInline: true` and `contentId: logo`; the regular node carries neither key.
- `build()` enforces INLN-01..05 with fixed messages, with or without inline images, and a mail without inline images and without `cid:` text builds and sends exactly as in v0.1.0 (`GraphRequestTest.fullMailArrivesAsTheDocumentedJson` and `TemplatedSendTest` unchanged and green).
- `recording` keeps inline images with their content ID and `RecordingTransport` is unchanged (INLN-06).
- No dependency, plugin or POM change; JDK-only imports (`ImportRulesTest` green).

Microsoft pages fetched on 2026-11-22: fileAttachment, attachment, attachmentItem, user: sendMail

Page results, all matching 05-RESEARCH.md: fileAttachment lists `isInline` and `contentId` and says `contentLocation` is not supported; attachment describes `isInline`; attachmentItem describes `contentId` as the CID that `<img src="cid:...">` refers to; user: sendMail has no inline example. The pages were fetched with curl because WebFetch was unavailable. A first attempt used wrong URLs and got 404s; the refetch with the plan's exact URLs returned 200 for all four. No difference from the research, so nothing to add to `docs/architecture.md` beyond what Plan 05-02 writes.

Temurin 17 CI (mid-phase, 05-01 Task 3): green on ea77e48 (verify, scoped release build, offline build)

The owner pushed `81ff531..ea77e48` to origin/main. GitHub Actions run 45122553386 (workflow CI, head SHA ea77e48d924e906c1f180e72c0af9d52d7d491db) concluded success on 2026-11-23, with `./mvnw -B -ntp verify`, `./mvnw -B -ntp -Prelease clean verify -Dgpg.skip -pl morimail -am` and the network-isolated `./mvnw -B -ntp -o verify` all green. Read-only checks in this run: `git merge-base --is-ancestor ea77e48 origin/main` exits 0 and `gh run list --workflow ci.yml --commit <origin/main>` prints `completed success`. This is the first proof of the Javadoc and the content type table on Java 17; Plan 05-02 ends with the second one.

## Task Commits

1. **Task 1: End-to-end one inline image through Graph (tracer)** - `ab06bad` (feat)
2. **Task 2: Inline-image and cid: checks in build(), Javadoc, recording proof** - `ea77e48` (feat)
3. **Task 3: Owner pushes and confirms Temurin 17 CI** - no commit (checkpoint:human-action, owner replied "green")

**Plan metadata:** the docs commit that holds this SUMMARY, STATE.md, ROADMAP.md and REQUIREMENTS.md.

The tracer was verified end to end automatically before the expansion task started. Plan 05-01 makes Task 2 a single feat commit, so the TDD RED step has no separate test commit; see TDD note below.

## Files Created/Modified

- `morimail/src/main/java/de/morisoft/morimail/Mail.java` - `inline(Path, String)`, `CID_REFERENCE`, `contentIdProblem`, `checkReferences`, `resolveAttachments(String html)`, the v0.1.0 content-ID rejection removed, Javadoc of `build()` and `attach(Attachment)`
- `morimail/src/main/java/de/morisoft/morimail/Attachment.java` - Javadoc: a non-null content ID marks an inline image
- `morimail/src/main/java/de/morisoft/morimail/graph/GraphTransport.java` - `isInline` and `contentId` on the `fileAttachment` node of an inline image (5 lines)
- `morimail/src/test/java/de/morisoft/morimail/MailBuilderTest.java` - the INLN-01..05 and D-04..D-14 cases, replaces `rejectsContentId`
- `morimail/src/test/java/de/morisoft/morimail/SendThroughGraphTest.java` - the tracer test
- `morimail/src/test/java/de/morisoft/morimail/SendThroughRecordingTest.java` - the INLN-06 test

## Decisions Made

- The reference pattern implements D-04 as amended on 2026-11-22 (owner-approved in plan review): `cid:` in any case, not directly after a letter or digit, ID ends at a double quote, apostrophe, whitespace, `)` or `>`; an empty ID (`href="cid:"`) and words such as `acid:` are no reference.
- Graph receives the bare content ID, no `contentLocation`, and no `isInline: false` on regular attachments, which keeps the v0.1.0 request JSON unchanged for mails without inline images.
- Commit trailers read `Co-Authored-By: Claude Sonnet 5.5`; the plan's commit gate accepts that shape.

## Owner review

1. **64-code-point cut.** The ID of a `cid:` reference read from the rendered HTML can come from recipient data, so `no inline image for cid:X` shows X cut at 64 code points followed by `...`. Messages that name a caller-given content ID (unreferenced, duplicate, not an image) show it whole.
2. **Literal D-06 consequence.** `<` does not end a reference, so a text such as `cid:logo</p>` reads the ID `logo</p` and fails with `no inline image for cid:logo</p`. Accepted content IDs can never contain `<` (D-09), so no accepted ID is cut short. A reference in markup always ends at a quote, whitespace, `)` or `>` first.
3. **Bare `contentId` to Graph.** The JSON value equals the builder's content ID with no angle brackets. Research (A1, A3) says Exchange shows it as the target of `cid:<contentId>`, but only the owner live run in Plan 05-02 (GRAPH-12) can confirm it.

## TDD note

Before the Task 2 implementation, 23 `MailBuilderTest` cases failed (RED). `SendThroughRecordingTest` passed beforehand because INLN-06 needs no code. The plan makes Task 2 one feat commit, so there is no separate test commit.

## Deviations from Plan

None - plan executed exactly as written.

One bookkeeping choice at close-out: `requirements mark-complete` was run for INLN-01..06 only. GRAPH-10 is listed in this plan's `requirements`, but its documentation part belongs to Plan 05-02 (which also lists GRAPH-10), so it stays pending until that plan closes.

## Issues Encountered

- The first Microsoft fetch used wrong URLs and returned 404; refetching with the exact URLs of the plan returned 200 for all four pages (no code had been written before this).

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Plan 05-02 can start: the Temurin 17 round-trip is green. It adds the Graph size limits for inline images (D-15 keeps `validate` unchanged in this plan), the owner live test (GRAPH-12) and the Graph notes in `docs/architecture.md`, and copies the Microsoft fetch date above.
- Open until the live run: whether Exchange renders the bare `contentId` as the `cid:` target.

## Known Stubs

None.

## Threat Flags

None - the content-ID shape check (T-05-01) and the 64-code-point cut (T-05-02) are the planned mitigations; no new network endpoint, auth path or file access pattern beyond the planned inline-image path.

## Self-Check: PASSED

- Commits `ab06bad` and `ea77e48` are ancestors of HEAD and of origin/main.
- All six modified files exist; 2 commits measured with `git rev-list --count 308ed6e8e6722be17c16f5292c842eb3962a178f..HEAD`.

---
*Phase: 05-inline-images-in-core-and-graph*
*Completed: 2026-11-23*
