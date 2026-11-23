---
phase: 07-html-image-helper-module
plan: 03
subsystem: html-image-helper
tags: [morimail-html, data-uri, base64, storable-form, module-rules, phase-gate, java17]

requires:
  - phase: 07-html-image-helper-module
    provides: HtmlImages.embed (local and remote), the rewrite loop, Fetched, writeToWorkDir, newId, fileName (Plans 07-01, 07-02)
  - phase: 05-inline-images-core-graph
    provides: build() content-ID and image-type checks; GraphTransport 3 MiB limit (size >= MAX_ATTACHMENT_BYTES)
provides:
  - HtmlImages.toDataUris(MailTemplate, Path) and toDataUris(MailTemplate, Path, Path), the storable form (plain HTML with data:TYPE;base64,PAYLOAD sources)
  - embed(MailTemplate, Path, Path) decodes data:image/...;base64, sources into inline images in the work directory
  - ModuleRulesTest, the import, dependency-direction and thread-safety rules over the finished morimail-html module
  - the phase gate run: three CI commands green on JDK 21, morimail jar byte-identical to the one built from f9b2f57, dependency trees as required
affects: [08 docs and release readiness]

actuals:
  tokens: 12000
  tasks: 2
  commits: 2
plan_head_before: 6ee6760c2fe0646dd938ad6222b243dd68df1260
plan_head_after: 8f7381afbe4bcf6383c8fd095eae8690ae2d2f3d
commits: 2

tech-stack:
  added: []
  patterns:
    - "One rewrite walk with a dataUris mode: the same classification, local resolution and download serve cid: and data: output"
    - "A failure label (img src \"SRC\": or data: image source #N: ) is passed to writeToWorkDir, so one helper serves both message forms"
    - "Strict Base64.getDecoder() after a pre-decode length check; the lenient MIME decoder is never used"

key-files:
  created:
    - morimail-html/src/test/java/de/morisoft/morimail/html/HtmlImagesDataUriTest.java
    - morimail-html/src/test/java/de/morisoft/morimail/html/ModuleRulesTest.java
  modified:
    - morimail-html/src/main/java/de/morisoft/morimail/html/HtmlImages.java

key-decisions:
  - "A2: toDataUris refuses a local file of exactly 3 MiB (3,145,728 bytes) and a decoded data: image of 3,145,728 bytes, the same >= comparison as GraphTransport.validate; 3,145,727 passes"
  - "A4: in toDataUris(template, baseDir, workDir) the work directory is only the opt-in for downloads and is never written"
  - "A6: no cleanup after a failed decode; files written for earlier data: sources stay in the work directory"
  - "P10: a decoded image is named ID.SUBTYPE (image/jpeg gives image-2.jpeg, image/svg+xml gives image-3.svg-xml), no reverse type table"

patterns-established:
  - "Failure message form data: image source #N: REASON with REASON one of is not an image type, is not base64, payload is not valid base64, is 3 MiB or larger, work directory is not usable; never any part of the payload"

requirements-completed: [HTML-07, HTML-01, HTML-06]

coverage:
  - id: D1
    description: "toDataUris turns a template's local images into data: URIs (quoting kept, subject and text unchanged); embed with a work directory turns that stored HTML back into cid: references and inline images with the original bytes, and both rendered mails pass build() through recording, with no image file on disk"
    requirement: HTML-07
    verification:
      - kind: integration
        ref: "morimail-html/src/test/java/de/morisoft/morimail/html/HtmlImagesDataUriTest.java#storedHtmlRoundTripsToTwoMailsWithTheOriginalBytes"
        status: pass
    human_judgment: false
  - id: D2
    description: "data: parsing and failures: only data:image/...;base64 is decoded, parameters ignored, any case accepted, wrapped payloads accepted, non-image, non-base64 and malformed sources fail naming the position (#N) and a fixed reason, never the payload; without a work directory data: stays unchanged"
    requirement: HTML-07
    verification:
      - kind: unit
        ref: "HtmlImagesDataUriTest#onlyBase64ImageDataIsDecoded, wrappedPayloadIsAccepted, parametersAreIgnoredAndTheTypeIsKept, dataSourcesStayUnlessAWorkDirectoryIsGiven"
        status: pass
    human_judgment: false
  - id: D3
    description: "The 3 MiB boundary before and after decoding (3,145,727 passes, exactly 3,145,728 refused, 4,194,305 characters refused before decoding) for data: sources, local files in toDataUris and downloads"
    requirement: HTML-07
    verification:
      - kind: unit
        ref: "HtmlImagesDataUriTest#decodedCapIsThreeMebibytes, toDataUrisCapsLocalFiles, toDataUrisDownloadsWithAWorkDirectoryAndWritesNothingThere"
        status: pass
    human_judgment: false
  - id: D4
    description: "IDs and file names of decoded images (identical values give one image, different ones image and image-2, names from the type), toDataUris for local and remote images without touching the work directory, the same instance when nothing converts, concurrent embeds into one work directory, and no cleanup after a failure"
    requirement: HTML-07
    verification:
      - kind: unit
        ref: "HtmlImagesDataUriTest#identicalDataSourcesGiveOneImage, dataImageNamesComeFromTheType, toDataUrisConvertsLocalImagesOnly, toDataUrisFailsLikeEmbedForLocalSources, toDataUrisKeepsTheTemplateWhenNothingIsConverted, concurrentRoundTripsUseSeparateFiles, failedDecodeKeepsEarlierFiles, nullArgumentsThrowNullPointerException"
        status: pass
    human_judgment: false
  - id: D5
    description: "Module rules on the finished module: imports only java.* and core types, no Jackson, graph or entra in comment-free sources; morimail names the helper nowhere; the public types are exactly HtmlImages and Result and each Javadoc states thread safety or immutability"
    requirement: HTML-01
    verification:
      - kind: unit
        ref: "morimail-html/src/test/java/de/morisoft/morimail/html/ModuleRulesTest.java#mainImportsOnlyTheJdkAndTheCore, morimailDoesNotReferenceTheHelper, publicTypesStateThreadSafety"
        status: pass
    human_judgment: false
  - id: D6
    description: "morimail jar unchanged (same file list and SHA-256 as the jar built from f9b2f57) and the dependency trees: morimail does not name morimail-html, morimail-html has morimail as provided and no compile or runtime dependency"
    requirement: HTML-01
    verification:
      - kind: other
        ref: "phase gate: jar gate against f9b2f57 and dependency-tree gate of 07-03-PLAN.md Task 2"
        status: pass
    human_judgment: false
  - id: D7
    description: "Final Temurin 17 run of the 07-02 and 07-03 commits on GitHub Actions, and the owner's review of assumptions A1 to A7 and planner choices P1 to P10"
    verification: []
    human_judgment: true
    rationale: "Claude never pushes, so only the owner can trigger and read the Java 17 run; the assumptions shape public behaviour of a jar that reaches Maven Central and only the owner can accept them"

duration: 5 min
completed: 2026-11-23
status: complete
---

# Phase 7 Plan 03: The storable data: form and the phase gate Summary

**`HtmlImages.toDataUris` stores a template as one plain HTML string with `data:TYPE;base64,PAYLOAD` images, `embed` with a work directory decodes it back into inline images with the original bytes (strict base64, 3 MiB cap checked before and after decoding, failures named by position and never by payload), and the phase gate proves the `morimail` jar byte-identical to the one built from f9b2f57.**

## Performance

- **Duration:** 5 min
- **Started:** 2026-11-23T14:46:59Z
- **Completed:** 2026-11-23T14:52:00Z
- **Tasks:** 2 (tracer, edge tests and gate)
- **Files modified:** 3 (2 created, 1 changed)

## Accomplishments

- `toDataUris(template, baseDir)` converts every local image to `data:TYPE;base64,PAYLOAD` with the local rules and messages of `embed`, keeps quoting, subject and text, leaves `cid:`, `data:`, `http(s):`, `$$` and unknown schemes unchanged, and returns the same instance when nothing converts. `toDataUris(template, baseDir, workDir)` also downloads `http(s)` images with the 07-02 rules and never writes the work directory.
- `embed(template, baseDir, workDir)` decodes `data:image/...;base64,` sources into unique `morimail-*` files and inline images: the first is `image`, repeats of an identical value share it, different ones get `image-2`, and the file name is `ID.SUBTYPE`. The header is split on `;`, parameters in between are ignored, `DATA:IMAGE/PNG;BASE64,` is accepted, and ASCII white space in the payload is removed before the strict decoder runs.
- The round trip of D-24 is pinned through `recording`: stored HTML, embedded with a base directory that does not exist, gives two mails for Anna and Ben that pass `build()` and carry both images with the original bytes.
- The 3 MiB cap holds in every form: 3,145,727 bytes pass and exactly 3,145,728 are refused for decoded `data:` images, local files in `toDataUris` and downloads; 4,194,305 `A` characters are refused before decoding (a mutation removing that check made `decodedCapIsThreeMebibytes` fail, then the source was restored).
- `ModuleRulesTest` guards the finished module: imports are `java.*` or core types only (Jackson is on the compile classpath through the provided scope, so this is the real guard), `morimail` names the helper nowhere, and the two public types state their thread safety.
- Phase gate on JDK 21, CI order: `./mvnw -B -ntp verify`, `./mvnw -B -ntp -Prelease clean verify -Dgpg.skip -pl morimail,morimail-html -am` (all three jars, no Javadoc warning) and `./mvnw -B -ntp -o verify` all exit 0. Dependency trees as required. The `morimail` jar has the same 39-entry file list and the same SHA-256 (`07874795ed816c194cc40b9634cc2719994f8a821275b3efac2e3ca3757006c0`) as the jar built from f9b2f57. `git diff --name-only f9b2f57 -- morimail/` prints nothing. The module holds exactly its POM, `HtmlImages.java` and five test classes. No address outside `example.com` and `example.org` appears in the diff, and every 07 code commit carries Stephan Wald <stephan@morisoft.de> as author and committer.

## Task Commits

1. **Task 1: Tracer, toDataUris and data: decoding with the D-24 round trip** - `0a44f3f` (feat)
2. **Task 2: Pin the data: and toDataUris edges and the module rules** - `8f7381a` (test)

**Plan metadata:** recorded in the `docs(07-03)` commit that adds this file and updates STATE.md, ROADMAP.md and REQUIREMENTS.md.

## Files Created/Modified

- `morimail-html/src/main/java/de/morisoft/morimail/html/HtmlImages.java` - both `toDataUris` overloads, `rewrite` with a `dataUris` mode and a `data:` branch, `decodeData`, `dataUri`, `readSmallFile`, Javadoc ("Storable form" paragraph, `data:` text on both `embed` overloads), `writeToWorkDir` taking a full message label
- `morimail-html/src/test/java/de/morisoft/morimail/html/HtmlImagesDataUriTest.java` - 16 test methods: the round trip and the `data:` and `toDataUris` edges
- `morimail-html/src/test/java/de/morisoft/morimail/html/ModuleRulesTest.java` - 3 test methods: import rule, dependency direction (D-01), thread-safety Javadoc

## Decisions Made

- `cidByKey` became `replacementByKey` (it holds either `cid:ID` or a `data:` value), as the plan said.
- `writeToWorkDir(label, ...)` now takes the message prefix instead of the raw `src`, so `img src "SRC": work directory is not usable` is unchanged and a `data:` source gets `data: image source #N: work directory is not usable`. Private only, no behaviour change for 07-02.
- The local read in `toDataUris` is a small private `readSmallFile(raw, file)` (size check, then read) so the 3 MiB refusal comes before any bytes are read.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None. All 19 new tests passed on the first run against the Task 1 source, so `8f7381a` holds only test files.

## Owner review

Flagged assumptions and planner choices this plan applied (reply only where one is wrong). The Owner review sections of 07-01-SUMMARY.md and 07-02-SUMMARY.md hold the other assumptions (A1 to A7) and choices (P1 to P10); they are still open for the owner to confirm.

- A2: the 3 MiB cap also applies to local files in `toDataUris` and to decoded `data:` images, read the way `GraphTransport.validate` reads it: 3,145,727 bytes pass, exactly 3,145,728 are refused (`img src "big.png": image is 3 MiB or larger` and `data: image source #N: is 3 MiB or larger`). So every stored form can be embedded again and neither form admits an image that Graph refuses.
- A4: `toDataUris` has a 2-argument and a 3-argument overload; in the 3-argument one the work directory is only the opt-in for downloads and is never written, created in or cleaned (`toDataUrisDownloadsWithAWorkDirectoryAndWritesNothingThere`).
- A6 (Rule 0): no cleanup after a failure; files decoded for earlier `data:` sources stay in the work directory, which the caller owns (`failedDecodeKeepsEarlierFiles`).
- Position rule: N in `data: image source #N` counts every `data:` `img src` value of the template (after the `$$` rule) in document order from 1, repeats of an identical value included.
- P10: the file name of a decoded image is `ID.SUBTYPE` with the subtype reduced as in D-17 (`image/jpeg` gives `image-2.jpeg`, `image/svg+xml` gives `image-3.svg-xml`); no reverse type table. The type keeps its case (`IMAGE/PNG` gives the type `IMAGE/PNG`).
- Round-trip naming consequence (research Pitfall 7): the stored form carries no file names, so after `toDataUris` and `embed` the IDs are `image`, `image-2`, ... and the names `image.png`, `image-2.gif`. Phase 8 documents it.
- Pre-decode length check: a whitespace-free payload longer than 4,194,304 characters is refused before decoding; an exact check after decoding refuses 3,145,728 bytes or more.
- Jar gate result: `morimail/target/morimail-0.2.0-SNAPSHOT.jar` has the same 39 entries and the same SHA-256 as the jar built from f9b2f57 (`07874795ed816c194cc40b9634cc2719994f8a821275b3efac2e3ca3757006c0`), so HTML-01 and D-03 hold.

## Open items for the owner

- **Final Temurin 17 CI run of the 07-02 and 07-03 commits: pending owner.** The owner pushes `main` and reads the GitHub Actions run on Temurin 17: all three steps (`./mvnw -B -ntp verify`, the scoped release build, `./mvnw -B -ntp -o verify`) must be green. Until then Phase 7 is not verified-complete. The run also confirms on Java 17 the JDK 21 observations behind the download deadline, `java.net.URI`, `Path.of(URI)` and `probeContentType` (research A7). Claude pushed nothing.
- Read the Owner review sections of 07-01, 07-02 and 07-03 and confirm or correct A1 to A7 and P1 to P10; a change becomes a fix commit or a decimal phase before Phase 8 documents the helper.

## User Setup Required

None - no external service configuration required.

## Known Stubs

None.

## Threat Flags

None. The surface this plan adds is the plan's own T-07-13 to T-07-17, each mitigated and covered by a test (`decodedCapIsThreeMebibytes`, `onlyBase64ImageDataIsDecoded`, `toDataUrisDownloadsWithAWorkDirectoryAndWritesNothingThere`, `failedDecodeKeepsEarlierFiles`).

## Next Phase Readiness

- Phase 7 has its three plans done on JDK 21. Remaining before the phase counts as verified: the owner's Temurin 17 run and the Owner review.
- Phase 8 (docs and release readiness) documents `embed`, `toDataUris` and the round-trip naming; the owner releases after Phase 8.

---
*Phase: 07-html-image-helper-module*
*Completed: 2026-11-23*

## Self-Check: PASSED

- FOUND: morimail-html/src/main/java/de/morisoft/morimail/html/HtmlImages.java, morimail-html/src/test/java/de/morisoft/morimail/html/HtmlImagesDataUriTest.java, morimail-html/src/test/java/de/morisoft/morimail/html/ModuleRulesTest.java
- FOUND: commits 0a44f3f and 8f7381a (ancestors of HEAD); 2 commits since plan_head_before
- `git diff --name-only f9b2f57 -- morimail/` prints nothing; jar gate, dependency-tree gate, hygiene gate and both commit gates exit 0
