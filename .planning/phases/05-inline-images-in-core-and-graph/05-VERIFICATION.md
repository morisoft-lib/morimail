---
phase: 05-inline-images-in-core-and-graph
verified: 2026-11-23T06:30:00Z
status: human_needed
score: 5/6 must-haves verified
covered_files:
  - ".planning/phases/05-inline-images-in-core-and-graph/05-01-PLAN.md"
  - ".planning/phases/05-inline-images-in-core-and-graph/05-01-SUMMARY.md"
  - ".planning/phases/05-inline-images-in-core-and-graph/05-02-PLAN.md"
  - ".planning/phases/05-inline-images-in-core-and-graph/05-02-SUMMARY.md"
  - "docs/architecture.md"
  - "morimail/src/main/java/de/morisoft/morimail/Attachment.java"
  - "morimail/src/main/java/de/morisoft/morimail/Mail.java"
  - "morimail/src/main/java/de/morisoft/morimail/graph/GraphTransport.java"
  - "morimail/src/test/java/de/morisoft/morimail/LiveGraphTest.java"
  - "morimail/src/test/java/de/morisoft/morimail/MailBuilderTest.java"
  - "morimail/src/test/java/de/morisoft/morimail/SendThroughGraphTest.java"
  - "morimail/src/test/java/de/morisoft/morimail/SendThroughRecordingTest.java"
  - "morimail/src/test/java/de/morisoft/morimail/graph/GraphValidateTest.java"
covered_digest: "v4:sha256:619326f097fdcc00ed49cb2a8e489b9031b7bb6e4355728a7a708717469fff97"
behavior_unverified: 0
overrides_applied: 0
human_verification:
  - test: "Owner runs the live inline-image test: ./mvnw -Plive test -pl morimail -Dtest=LiveGraphTest#sendsOneMailWithAnInlineImage (with the MORIMAIL_LIVE_* variables), then opens the mail in Outlook on the web and one desktop client"
    expected: "Output line 'live inline: flow certificate|secret, accepted'; the generated blue 64x64 square shows inside the mail body under the first line, not as a separate attachment"
    why_human: "Only the owner can send real mail through the tenant. Whether Exchange treats the bare contentId (no angle brackets) as the target of cid:logo is a non-inferable external behaviour (05-RESEARCH A1/A3). Claude must not run -Plive."
  - test: "Owner pushes the 05-02 commits (97266fe, 32ffa47 and the docs commits) and reads the GitHub Actions run on Temurin 17: verify, scoped release build (-Prelease clean verify -Dgpg.skip -pl morimail -am), offline build"
    expected: "All three steps green. The first Temurin 17 run (05-01, run 45122553386 on ea77e48) was green; this second run covers the new size tests and the compiled live test on Java 17."
    why_human: "Claude never pushes; local main is ahead of origin/main. Roadmap Phase Gates and 05-02 require this second and final Temurin 17 run."
---

# Phase 5: Inline Images in Core and Graph Verification Report

**Phase Goal:** A Java developer can build a templated HTML mail with `cid:` images and send it through the Graph transport, with each image attached inline under its content ID.
**Verified:** 2026-11-23
**Status:** human_needed
**Re-verification:** No, initial verification

All code-level and offline-testable parts of the goal are achieved and wired. No gaps were found. The phase cannot be called fully complete until two owner-run items are done: the live render check (GRAPH-12) and the second Temurin 17 CI run.

## Goal Achievement

### Observable Truths (ROADMAP success criteria, plus one non-inferable truth)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | `Mail.using(id)...template(t,data).inline(Path,"logo").build()` works with `cid:logo` in the HTML. Via `Mailer` to `recording`, the message shows the inline image with content ID, file name and content type apart from regular attachments. Blank or line-break content ID is rejected. The v0.1.0 "reject any contentId" rule is replaced. | VERIFIED | `Mail.java:343-348` `inline(Path,String)` adds `new Attachment(null,null,image,contentId)`; `Mail.java:470-484` `contentIdProblem` rejects blank / line break / whitespace, quote, paren, angle bracket; the old rejection is gone (no remaining rejection of non-null contentId; `resolved.add(new Attachment(filename, contentType, file, contentId))` at :485 keeps it). Tests (all in surefire reports, run green): `MailBuilderTest#inlineImageKeepsItsContentIdFilenameAndType`, `blankContentIdIsRejected`, `contentIdWithALineBreakIsRejected`, `SendThroughRecordingTest#inlineImageIsRecordedWithItsContentIdApartFromTheRegularAttachment`. `RecordingTransport` unchanged (not in diff). |
| 2 | `build()` raises one `InvalidMailException` naming the problem for: `cid:` reference without inline image; inline image not referenced (also in text-only mail); duplicate content ID; missing/unreadable inline file or non-image type. | VERIFIED | `Mail.java:508-532` `checkReferences` (missing image, then unreferenced; `html != null` guard covers text-only), `:477-483` image-type and duplicate checks, `:450-458` file checks shared with regular attachments. Tests: `referenceWithoutInlineImageFails`, `unreferencedInlineImageFails`, `inlineImageInATextOnlyMailIsUnreferenced`, `cidInTheTextBodyIsNoReference`, `duplicateContentIdFails`, `nonImageInlineFileFails[1..2]`, `missingInlineFileFails`, `unreadableInlineFileFails`, `missingImageIsReportedBeforeAnUnreferencedOne`, `longReferenceIsCutInTheMessage`, `bareCidAndWordsEndingInCidAreNoReference`. |
| 3 | Against WireMock, Graph sends each inline image as a base64 fileAttachment marked inline with its content ID; property names from the current Microsoft page, differences noted in `docs/architecture.md`; regular attachments in the same mail are not marked inline. | VERIFIED | `GraphTransport.java:167-171` puts `isInline: true` and `contentId` only when `attachment.contentId() != null`. Tracer `SendThroughGraphTest#inlineImageArrivesAsAnInlineFileAttachmentNextToARegularOne` (template + inline + regular through Mailer, registry, token stub, WireMock) asserts the whole ordered `attachments` array: logo node with `isInline`/`contentId`, agenda node without either key. `docs/architecture.md:52` records the property names, the missing sendMail example, no `contentLocation`, and "Live status: not yet confirmed"; `:40-42` hold the three deviation rows. 05-01-SUMMARY records "Microsoft pages fetched on 2026-11-22". |
| 4 | `validate` rejects an inline image of 3 MiB or more, inline + regular together at 3 MiB, or an estimated request over 4 MiB (v0.1.0 limits, unchanged); message names the file or limit; rejection precedes any token request/HTTP call. | VERIFIED | `GraphTransport.validate` untouched in the phase diff beyond the 5 lines in `requestBody`; it loops over all `m.attachments()`. Tests in `GraphValidateTest`: `inlineImageOfThreeMiBIsReportedByThePerFileRule`, `inlineImageJustBelowThreeMiBFailsTheEstimateNotTheFileRules`, `inlineImageOfAboutTwoPointNineMegabytesPasses`, `inlineImageAndRegularFileShareTheThreeMiBTotal`, `mailerRejectsAnOversizedInlineImageBeforeAnyTokenOrRequest` (zero token calls and zero WireMock requests). All present in the surefire report and green. |
| 5 | The `live` test (tag `live`, `-Plive`, env-configured) sends one mail with one inline image. Default offline `./mvnw verify` excludes it and stays green. Claude does not run it. | VERIFIED (artifact); the run itself is a human item | `LiveGraphTest.java:50` `@Tag("live")`; `:152-167` `sendsOneMailWithAnInlineImage` builds a run-time PNG (`tinyPng()` via ImageIO, no tracked image), `.inline(image, "logo")` with `cid:logo` in the HTML, sends via `mailer()`. `./mvnw -B -ntp -o verify` BUILD SUCCESS on this machine (137 sample tests plus the library module) with no `LiveGraphTest` report in `morimail/target/surefire-reports`. Not run by the verifier. |
| 6 | Exchange shows a bare `contentId` (no angle brackets) as the target of `cid:<contentId>`, so the image renders inline in a real mail client. | UNCERTAIN (human) | Non-inferable external behaviour (05-RESEARCH A1/A3; plan truth tagged `verification: backstop`). No test can exercise it offline. Routed to Human Verification item 1. |

**Score:** 5/6 truths verified (0 present-but-behavior-unverified; truth 6 is non-inferable and awaits the owner).

### Plan-level must-haves spot-checked

| Check | Result |
|-------|--------|
| Content-ID shape messages are fixed text and do not echo the rejected value (prohibition, test tier) | VERIFIED: `Mail.java:474` uses fixed `reason` strings; covered by the `MailBuilderTest` shape tests. |
| `build()` does not rewrite HTML or generate IDs (prohibition, test tier) | VERIFIED: `html` goes unchanged into `MailMessage` (`Mail.java:407-409`); `resolveAttachments` only reads it. |
| No dependency, plugin, interface, new record or inline overload taking a content type; POMs unchanged (prohibition, test tier) | VERIFIED: `git diff 308ed6e..HEAD --stat -- '*pom.xml'` is empty; only one `inline(Path,String)` overload; no new type; `ImportRulesTest` green. |
| Mail without inline images and without `cid:` builds/sends as in v0.1.0 | VERIFIED: `GraphRequestTest.fullMailArrivesAsTheDocumentedJson` and `TemplatedSendTest` unchanged and green in the full offline build; `isInline`/`contentId` are put only inside the `contentId != null` branch. |
| Caller-supplied `attach(new Attachment(..., contentId))` is an inline image with the same checks | VERIFIED: `attachWithContentIdIsAnInlineImage` test; same loop in `resolveAttachments`. |
| Case-insensitive `image/` check | VERIFIED: `regionMatches(true, 0, "image/", 0, 6)` (`Mail.java:476`). |
| Two commits per plan authored/committed as Stephan Wald; nothing pushed by Claude | VERIFIED: `git log` for `308ed6e..HEAD` shows author and committer `Stephan Wald <stephan@morisoft.de>` on all 8 commits. |
| No secrets or tracked images added by the phase | VERIFIED: the only tracked image, `morimail-sample-serial/example-campaign/attachments/logo.png`, predates the phase (not in the phase diff). |

### Key Link Verification

| From | To | Via | Status |
|------|----|-----|--------|
| `Mail.java` resolved Attachment | `GraphTransport.requestBody` | `attachment.contentId()` kept on resolved record (`Mail.java:485`) and read at `GraphTransport.java:168` | WIRED |
| `Mail.build()` | `TemplateRenderer` output | `resolveAttachments(html)` is called with the rendered `html` local (`Mail.java:407`) | WIRED |
| `SendThroughRecordingTest` | `RecordingTransport` | `sent().get(0).attachments()` holds the built list including inline entries | WIRED |

### Data-Flow Trace (Level 4)

| Artifact | Data | Source | Real data | Status |
|----------|------|--------|-----------|--------|
| `GraphTransport` fileAttachment node | `contentBytes`, `isInline`, `contentId` | file read from the resolved `Attachment.file()`, content ID from the builder; asserted byte-exact in the tracer test through WireMock | Yes | FLOWING |
| `checkReferences` | referenced IDs | rendered HTML (post template rendering) | Yes | FLOWING |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Full offline build including all phase tests | `./mvnw -B -ntp -o verify` | BUILD SUCCESS (library and sample modules) | PASS |
| Live test excluded by default | check `morimail/target/surefire-reports` | no `LiveGraphTest` report | PASS |
| Phase tests present in the reports | grep surefire XML | all named tests above found (`MailBuilderTest`, `SendThroughGraphTest`, `SendThroughRecordingTest`, `GraphValidateTest`) | PASS |

Probe Execution: no probes declared or implied by this phase. Not applicable.

### Requirements Coverage

Both plans declare IDs in frontmatter (05-01: INLN-01..06, GRAPH-10; 05-02: GRAPH-10, GRAPH-11, GRAPH-12). REQUIREMENTS.md maps all nine to Phase 5. No orphaned IDs.

| Requirement | Source Plan | Status | Evidence |
|-------------|-------------|--------|----------|
| INLN-01 | 05-01 | SATISFIED | `inline(Path,String)`; blank / line-break / shape checks; null args throw NPE |
| INLN-02 | 05-01 | SATISFIED | `checkReferences` missing-image path; tests above |
| INLN-03 | 05-01 | SATISFIED | unreferenced image, including text-only mail |
| INLN-04 | 05-01 | SATISFIED | `duplicate inline content ID`, `duplicateContentIdFails` |
| INLN-05 | 05-01 | SATISFIED | image type check plus shared file checks |
| INLN-06 | 05-01 | SATISFIED | `inlineImageIsRecordedWithItsContentIdApartFromTheRegularAttachment`; `RecordingTransport` unchanged |
| GRAPH-10 | 05-01, 05-02 | SATISFIED (code and docs); live confirmation pending | `GraphTransport.java:167-171`, tracer test, `docs/architecture.md:52` |
| GRAPH-11 | 05-02 | SATISFIED | five `GraphValidateTest` inline cases |
| GRAPH-12 | 05-02 | SATISFIED as worded ("so the owner can confirm"); the confirmation itself is owner-pending | test exists, tagged `live`, excluded by default. REQUIREMENTS.md already shows it Complete; that reflects delivery of the test, not the live result. |

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| (phase files) | - | TBD / FIXME / XXX markers | none found | - |
| `Mail.java` | 46, 48 | comment cites internal decision IDs ("D-04 as amended...") | Info | public-source readability only (code review IN-02) |

No stubs, hollow props or static returns found in the touched production code.

### Code review findings (05-REVIEW.md, all open per 05-REVIEW-DISPOSITION.md, advisory)

These do not fail any must-have; the behaviour is the documented, owner-approved D-04/D-06 design. They are recorded here for the owner:

- WR-01: the ID read from rendered HTML (up to 64 code points) is echoed into the exception message without control-character cleaning (log-forging risk for recipient-derived data). The phase prohibition as written (fixed texts for shape messages, 64-code-point cut for HTML-read IDs) is met, so this is a hardening suggestion, not a violated truth.
- WR-02: the `cid:` scan reads the whole rendered HTML, so a recipient value or existing template containing text such as `ref=cid:123` can fail a mail that has no inline image. Documented in the `build()` Javadoc and `docs/architecture.md` as the 0.2.0 behaviour change.
- IN-01 (platform-dependent image types outside png/jpg/jpeg/gif), IN-02 (`docs/spec.md` §5/§5.1 still states the MVP rule; Phase 8 owns the spec update), IN-03 (case-sensitive ID uniqueness, to be judged after the live run).

### Human Verification Required

#### 1. Live inline-image render (GRAPH-12, truth 6)

**Test:** Set the `MORIMAIL_LIVE_*` variables from the `LiveGraphTest` Javadoc and run `./mvnw -Plive test -pl morimail -Dtest=LiveGraphTest#sendsOneMailWithAnInlineImage`. Open the mail in Outlook on the web and one desktop client.
**Expected:** `live inline: flow certificate|secret, accepted`; the blue 64x64 square shows inside the mail body, not as a separate attachment.
**Why human:** Only the owner can send real mail through the tenant, and Exchange's handling of a bare `contentId` cannot be checked offline. A needed correction (for example angle brackets) becomes an inserted Phase 5.1. Phases 6 and 7 do not wait; Phase 8 needs the result.

#### 2. Second and final Temurin 17 CI run

**Test:** Push the 05-02 commits and read the GitHub Actions run on Temurin 17.
**Expected:** verify, scoped release build and offline build all green. The 05-01 run (45122553386 on ea77e48) was green already; this run covers the new size tests and the compiled live test.
**Why human:** Claude never pushes; local main is ahead of origin/main.

### Gaps Summary

No gaps. Every roadmap success criterion is implemented, wired and covered by a passing test, and the offline build is green on this machine. The status is `human_needed` solely because of the two owner-run items above, which the phase documents as required before it is verified-complete.

---

_Verified: 2026-11-23_
_Verifier: Claude (gsd-verifier)_
