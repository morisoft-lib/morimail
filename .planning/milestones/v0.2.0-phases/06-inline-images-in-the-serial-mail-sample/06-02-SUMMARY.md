---
phase: 06-inline-images-in-the-serial-mail-sample
plan: 02
subsystem: sample
tags: [inline-images, cid, pre-flight, example-campaign, serial-mail, graph]

requires:
  - phase: 06-inline-images-in-the-serial-mail-sample
    provides: "Plan 06-01: inline/ listing, Campaign.contentId, Campaign.CID_REFERENCE, builder.inline in SendCommand, the dry-run copy and rewrite"
provides:
  - "Pre-flight inline scan (Campaign.checkInline): every inline defect is its own line of the one pre-flight report, exit 2, nothing sent, no out/, no journal"
  - "A generated 96 x 48 example-campaign/inline/logo.png that the example body.html shows at the top; attachments/logo.png renamed to attachments/sample.png"
affects: [phase 7 and 8 docs and README for the sample, phase verification of Phase 6]

actuals:
  tokens: 5434
  tasks: 2
  commits: 2
plan_head_before: 4e1267485b70374c7ec0932bb54a4f7e8ecd99ec
plan_head_after: 933649be080be3327e4745dcf13ccfb4291521f0

tech-stack:
  added: []
  patterns:
    - "Two probe mails per inline file through Mail.Builder.build(): the type build() resolves, and the content-ID rule under the fixed type image/png; the scan reuses the core's rules instead of copying its tables"
    - "Inline defects return a null Campaign, so no mail is built and no defect is reported twice (D-01)"

key-files:
  created:
    - morimail-sample-serial/example-campaign/inline/logo.png
  modified:
    - morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/Campaign.java
    - morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/CampaignTest.java
    - morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/PreflightTest.java
    - morimail-sample-serial/example-campaign/body.html
    - morimail-sample-serial/example-campaign/attachments/sample.png
    - morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/ExampleCampaignTest.java
    - morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/GraphEndToEndTest.java

key-decisions:
  - "Type and content-ID checks go through two probe mails and the core's build(), not a copied content-type table, so the scan agrees with build() on hosts where Files.probeContentType adds types"
  - "Every distinct defect of an inline file is its own pre-flight line; the same-ID check uses only the file name"
  - "An inline finding makes Campaign.read return null (D-01); row-level findings of build() and mailer.validate wait until the inline problems are fixed (A2, confirmed by the owner)"
  - "The shipped inline logo is a 166-byte PNG with only IHDR, IDAT and IEND, generated once outside the repository; no generator is checked in"

patterns-established:
  - "Scan messages name only inline/FILE, body.html and IDs read from the template, never a recipient address or CSV value"

requirements-completed: [CLI-15, CLI-16, CLI-18]

coverage:
  - id: D1
    description: "Pre-flight reports each inline problem (cid: with no image, image with no cid:, same base name, non-image file, file name giving an invalid content ID) once, each defect of a file on its own line, in the same report as the other findings, exit 2, nothing sent, no journal"
    requirement: CLI-16
    verification:
      - kind: unit
        ref: "morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/PreflightTest.java#inlineProblemsAreReportedWithTheOtherFindings"
        status: pass
      - kind: unit
        ref: "morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/CampaignTest.java#everyDefectOfAFileIsItsOwnProblem"
        status: pass
      - kind: unit
        ref: "morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/CampaignTest.java#sameBaseNameIsOneProblemPerExtraFile"
        status: pass
      - kind: unit
        ref: "morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/CampaignTest.java#nonImageFilesAreProblemsAndNeedNoReference"
        status: pass
    human_judgment: false
  - id: D2
    description: "The D-01 consequence and backstop: row-level checks wait until inline problems are fixed; a cid: that only a CSV value adds is reported per row by build()"
    requirement: CLI-16
    verification:
      - kind: unit
        ref: "morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/PreflightTest.java#rowChecksWaitUntilTheInlineProblemsAreFixed"
        status: pass
      - kind: unit
        ref: "morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/PreflightTest.java#cidThatOnlyARecipientValueAddsIsReportedByBuild"
        status: pass
    human_judgment: false
  - id: D3
    description: "Content ID rules and the cid: grammar: last extension stripped, exact case, core grammar, scan and build() agree on eight snippets"
    requirement: CLI-15
    verification:
      - kind: unit
        ref: "morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/CampaignTest.java#cidPatternAgreesWithTheCore"
        status: pass
      - kind: unit
        ref: "morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/CampaignTest.java#cidScanFollowsTheCoreGrammar"
        status: pass
      - kind: unit
        ref: "morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/CampaignTest.java#cidIdIsCaseSensitive"
        status: pass
      - kind: unit
        ref: "morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/CampaignTest.java#contentIdIsTheFileNameWithoutTheLastExtension"
        status: pass
    human_judgment: false
  - id: D4
    description: "The dry run of the example campaign writes five mails that each show inline/logo.png (96 x 48), prints the inline: line and writes out/inline/logo.png"
    requirement: CLI-18
    verification:
      - kind: unit
        ref: "morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/ExampleCampaignTest.java#dryRunWritesFiveRenderedMails"
        status: pass
      - kind: unit
        ref: "morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/ExampleCampaignTest.java#inlineLogoIsTinyAndMatchesTheImgSize"
        status: pass
    human_judgment: false
  - id: D5
    description: "On the Graph wire the example campaign carries info.pdf, sample.png, logo.png in this order; only logo.png has isInline true and contentId logo; every body keeps cid:logo"
    requirement: CLI-18
    verification:
      - kind: unit
        ref: "morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/GraphEndToEndTest.java#exampleCampaignIsSentOnceAndARerunSendsNothing"
        status: pass
    human_judgment: false
  - id: D6
    description: "How the rendered example mail looks in a browser (logo above Hello Anna, the inline-versus-attachments sentence)"
    verification: []
    human_judgment: true
    rationale: "The tests prove the src resolves to a readable 96 x 48 PNG; the look in a browser is a visual check for the owner"
  - id: D7
    description: "Final Temurin 17 CI run of the pushed 06-02 commits, all three steps"
    verification: []
    human_judgment: true
    rationale: "Only JDK 21 is installed locally and Claude never pushes; only the owner can push and read that run"

duration: 6min
completed: 2026-11-23
status: complete
---

# Phase 6 Plan 02: Inline problems in pre-flight, the example logo and the phase gate Summary

**Pre-flight now reports every inline defect (a `cid:` with no image, an image with no `cid:`, a taken content ID, a non-image file, a file name that gives an invalid content ID) as its own line of the one report with exit 2 and nothing written, and the shipped example campaign shows a generated 96 x 48 `inline/logo.png` in all five dry-run mails and on the Graph wire.**

## Performance

- **Duration:** about 6 min (2026-11-23T08:11Z to 08:17Z)
- **Tasks:** 2
- **Files modified:** 8 (one new image, one rename)

## Accomplishments

- `Campaign.checkInline` runs two probe mails per inline file through the core's `build()`: one for the type `build()` resolves, one for the content-ID rule under the fixed type `image/png`. The scan therefore uses the core's type resolution (including the host's `probeContentType` fallback) and content-ID rule, and only restates the one-line image rule of Phase 5 D-10 with the core's wording.
- `Campaign.read` returns null when the scan found anything, so no mail is built and nothing is reported twice. The scan runs before the CSV and placeholder checks, so its lines sit in the same report as the account, campaign and CSV findings. The tracer test shows seven problems in one run with exit 2, zero transport attempts and no journal.
- The example campaign has `inline/logo.png` (166 bytes, IHDR, IDAT and IEND only, generated once outside the repository), `attachments/logo.png` is renamed to `attachments/sample.png` with unchanged content, and `body.html` shows the logo above `Hello $$firstName$$`.
- The example dry run prints `attachments: info.pdf (...), sample.png (...)`, then `inline: logo.png (image/png, 166 bytes, cid:logo)`, writes 15 rendered files plus `out/inline/logo.png`, and each HTML file shows `inline/logo.png`, which opens as 96 x 48.
- On the Graph wire the example mails carry `info.pdf`, `sample.png`, `logo.png`; only the `logo.png` node has `isInline` true and `contentId` `logo`, and every body keeps `cid:logo`.

## Task Commits

1. **Task 1: Inline problems in pre-flight (tracer)** - `05bae95` (feat)
2. **Task 2: Example campaign inline logo and the phase gate** - `933649b` (feat)

**Plan metadata:** the docs commit that holds this SUMMARY, STATE.md, ROADMAP.md and REQUIREMENTS.md (docs: complete plan).

The Task 1 precondition checked SHA `2633e3a` (the pushed last 06-01 commit, on `origin/main`, its `ci.yml` run completed success, gh signed in) and exited 0.

## TDD note

The plan is `type: execute` with `tdd="true"` tasks and one `feat` commit per task, so there are no separate `test` commits. RED was run before any code in `Campaign.java`: 11 assertion failures (8 in `CampaignTest`, 3 in `PreflightTest`), each on the planned message list, no compile or fixture error. The other five new `CampaignTest` tests pin 06-01 behaviour and passed at once (sorted listing, missing directory, content-ID derivation, the grammar and the pattern agreement). GREEN: all of them pass after the scan.

## Files Created/Modified

- `morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/Campaign.java` - `checkInline`, `withoutField`, the `inlineBroken` null return, extended `read` Javadoc
- `morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/CampaignTest.java` - 13 inline tests
- `morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/PreflightTest.java` - the tracer, the pinned D-01 consequence and the `build()` backstop
- `morimail-sample-serial/example-campaign/inline/logo.png` - new, generated, 96 x 48, 166 bytes
- `morimail-sample-serial/example-campaign/attachments/sample.png` - renamed from `attachments/logo.png`, content unchanged
- `morimail-sample-serial/example-campaign/body.html` - logo paragraph and the merged sentence
- `morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/ExampleCampaignTest.java` - 16 entries in `out/`, the inline line, five shown logos, the logo fixture test, the rename
- `morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/GraphEndToEndTest.java` - the inline node on the wire

## Owner review

1. **Two probes per file.** The type probe builds the file as a regular attachment and reads the type `build()` resolved. The content-ID probe sends the file inline under its own content ID with the fixed type `image/png`, so only the content-ID rule is checked. A file that the core rejects as an attachment at all (not found, not readable, a line break in its name) is one line, the stripped core message. The D-10 image rule (a type that starts with `image/` in any case) is the only rule restated in the sample.
2. **The five message texts** (prefix `inline/NAME: ` for the file-level ones):
   - `<core content-ID or file message without attachments[0]: >`, for example `contentId contains whitespace, a quote, a parenthesis or an angle bracket`
   - `inline image ID has content type TYPE, which is not an image type`
   - `same content ID as inline/FIRST (cid:ID)`
   - `body.html: cid:ID has no image in inline/`
   - `inline/NAME: no cid:ID reference in body.html`
3. **The merged `body.html` sentence:** `The logo above is an inline image; the attached PDF has the details, and the attached PNG is a small sample image.` The logo paragraph is `<p><img src="cid:logo" alt="morimail logo" width="96" height="48"></p>`; `body.txt` is unchanged.

Report order (as planned): `inline/ cannot be listed`, then file by file in name order (content-ID problem, type problem, same-ID problem), then each `cid:` with no image in order of first appearance, then each image with no reference in name order.

## Assumptions

- A2 (D-01 consequence) was confirmed by the owner on 2026-11-23, so it is not open: with an inline problem and an invalid address in the CSV, the dry run reports only the inline problem, and the address problem shows after the inline problem is fixed. `PreflightTest.rowChecksWaitUntilTheInlineProblemsAreFixed` pins it.
- The scan reads the template as written: a `cid:` whose ID is a placeholder is reported as having no image in `inline/`. Per-recipient inline images are out of scope.
- CLI-18 had no derived edge category; it is covered by the Task 2 tests, and the reviewer decides whether an edge is missing.

## Decisions Made

See `key-decisions` above. No new record, class or interface was added (Rule 0); the core and the POMs are unchanged against `0ab315d`.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Test input rendered `cid:x</p` instead of `cid:x`**
- **Found during:** Task 1, first GREEN run of `PreflightTest.cidThatOnlyARecipientValueAddsIsReportedByBuild`
- **Issue:** The plan's body `<p>Hi $$firstName$$ from $$city$$ $$note$$</p>` renders the CSV value `see cid:x` directly before `</p>`. The core's pattern ends an ID only at a quote, whitespace, `)` or `>`, so the ID read was `x</p` and the core backstop message was `no inline image for cid:x</p`. The scan is correct (this is the core grammar); the fixture was not.
- **Fix:** One space before `</p>` in the test's `body.html`: `<p>Hi $$firstName$$ from $$city$$ $$note$$ </p>`. No production code changed.
- **Files modified:** `morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/PreflightTest.java`
- **Verification:** the test passes with the exact line `recipients.csv line 3: no inline image for cid:x`
- **Commit:** `05bae95`

**Total deviations:** 1 auto-fixed (1 test input). **Impact on plan:** none on the code; worth knowing for the docs phase that a `cid:` written directly before `<` reads the `<` as part of the ID.

## Issues Encountered

None beyond the deviation above. All three CI commands passed on JDK 21 in CI order (`./mvnw -B -ntp verify`, the scoped release build with an empty negative `warning` grep, `./mvnw -B -ntp -o verify`).

## Authentication Gates

None.

## User Setup Required

None - no external service configuration required.

## Known Stubs

None.

## Threat Flags

None. The scan messages name only `inline/FILE`, `body.html` and IDs read from the template (T-06-05); the logo has only IHDR, IDAT and IEND and is 166 bytes (T-06-06); the probes use `probe@example.org` and are only built (T-06-07); the hygiene gate found no address outside `example.org` and `example.com` in the sample diff since `0ab315d`.

## Next Phase Readiness

- Phase 6 has no further plan. Verification of Phase 6 waits for the owner checkpoint below.
- Open: final Temurin 17 CI run of the pushed 06-02 commits (owner), all three steps

## Owner checkpoint

1. Open a dry-run HTML file of the example campaign in a browser (for example `java -jar morimail-sample-serial/target/morimail-sample-serial-*.jar send --account morimail-sample-serial/example-campaign/account.properties --campaign morimail-sample-serial/example-campaign --dry-run --out out`, then `out/01-anna@example.org.html`): the small blue logo shows above "Hello Anna," and the page names the logo as inline and the PDF and PNG as attachments.
2. Push the 06-02 commits (`05bae95`, `933649b`, and the docs commit) and read the final Temurin 17 CI run (verify, scoped release build, offline build).

Phase 6 is not verified-complete until the final Temurin 17 CI run of the pushed 06-02 commits is green (verify, scoped release build, offline build). Claude never pushes.

---
*Phase: 06-inline-images-in-the-serial-mail-sample*
*Completed: 2026-11-23*

## Self-Check: PASSED

- FOUND: `Campaign.java`, `CampaignTest.java`, `PreflightTest.java`, `ExampleCampaignTest.java`, `GraphEndToEndTest.java`, `example-campaign/inline/logo.png`, `example-campaign/attachments/sample.png`, `example-campaign/body.html`
- FOUND: commits `05bae95`, `933649b`
- Measured commits: 2 (`git rev-list --count 4e12674..HEAD`)
- `attachments/logo.png` is no longer tracked; `body.txt`, `morimail/` and the three POMs have no diff against `0ab315d`
