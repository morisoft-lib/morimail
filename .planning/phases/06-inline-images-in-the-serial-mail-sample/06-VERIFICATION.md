---
phase: 06-inline-images-in-the-serial-mail-sample
verified: 2026-11-23T08:40:00Z
status: human_needed
score: 4/4 must-haves verified
covered_files:
  - ".planning/phases/06-inline-images-in-the-serial-mail-sample/06-01-PLAN.md"
  - ".planning/phases/06-inline-images-in-the-serial-mail-sample/06-01-SUMMARY.md"
  - ".planning/phases/06-inline-images-in-the-serial-mail-sample/06-02-PLAN.md"
  - ".planning/phases/06-inline-images-in-the-serial-mail-sample/06-02-SUMMARY.md"
  - "morimail-sample-serial/example-campaign/body.html"
  - "morimail-sample-serial/example-campaign/inline/logo.png"
  - "morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/Campaign.java"
  - "morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/SendCommand.java"
  - "morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/DryRunTest.java"
  - "morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/ExampleCampaignTest.java"
  - "morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/PreflightTest.java"
  - "morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/SendRunTest.java"
covered_digest: "v4:sha256:35866635ef4629fbecda69248cfa540ed985f83961a5bdcdb39d2414bce89768"
behavior_unverified: 0
overrides_applied: 0
human_verification:
  - test: "Push the 06-02 commits (05bae95, 933649b, 6ab7df9, 4a7fd5c) and wait for the GitHub Actions run on Temurin 17"
    expected: "All steps of the run are green. 06-01 (2633e3a) is already green (run 45135972918); the 06-02 commits have not run on JDK 17 yet."
    why_human: "Only JDK 21 is installed locally, and the project gate (v0.1.0 lesson 1) requires a Temurin 17 round trip. Pushing is the owner's action."
  - test: "Run the example campaign with --dry-run and open out/01-anna@example.org.html in a browser"
    expected: "The 96x48 morimail logo is displayed (not a broken-image icon) in each of the five rendered mails."
    why_human: "Whether a browser resolves the percent-encoded relative path inline/logo.png and shows the image is visual. Tests check bytes and the src text only."
---

# Phase 6: Inline Images in the Serial-Mail Sample Verification Report

**Phase Goal:** A campaign author can put images into an optional `inline/` directory. The dry run shows them in the rendered mails, and every sent mail carries them inline.
**Verified:** 2026-11-23
**Status:** human_needed
**Re-verification:** No, initial verification

All four roadmap success criteria hold in the code and in a hands-on run. Two owner checks remain, so the status is `human_needed`: the Temurin 17 CI run of the unpushed 06-02 commits, and a browser look at the dry-run logo. Both are named in the plans. No gaps.

## Goal Achievement

### Observable Truths

| #   | Truth (roadmap success criterion) | Status | Evidence |
| --- | --------------------------------- | ------ | -------- |
| 1 | With `inline/`, every mail goes out with each file as an inline image, content ID = file name without extension; a campaign without `inline/` behaves as in v0.1.0 | VERIFIED | `SendCommand.call()` calls `builder.inline(image, Campaign.contentId(image))` for every `campaign.inlineImages()` after the regular attachments. `Campaign.contentId` strips the last extension, and `listFiles` reads `inline/` (dot-files skipped, sorted). `SendRunTest.sentMailsCarryTheInlineImageAndKeepTheCidReference` and `inlineImagesFollowTheAttachmentsInFileNameOrder` run against `recording`. A missing `inline/` gives an empty list, and the v0.1.0 tests still pass. `./mvnw -o verify -pl morimail-sample-serial -am` is green (run by me). |
| 2 | Pre-flight reports inline problems (cid with no image, unreferenced image, same base name, non-image file) in the same report as the other findings, exit 2, nothing sent, no journal | VERIFIED | `Campaign.checkInline` produces all four, and `Campaign.read` keeps collecting other findings before returning null. I ran a copy of the example with `notes.txt`, `logo.jpg`, `extra.png` and `cid:missing`. It printed 7 problems in one report: same content ID, non-image type, `cid:missing` has no image, two unreferenced images, and the unrelated account finding. It exited 2 with "nothing was sent", wrote no `out/` and no journal. `PreflightTest.inlineProblemsAreReportedWithTheOtherFindings` covers this. |
| 3 | Dry-run writes the images next to the rendered HTML and rewrites `cid:` to relative paths in the dry-run output only; sent mails keep `cid:` | VERIFIED | `writeDryRun` copies each image once to `out/inline/` and rewrites only the string written to the `.html` file, using `Campaign.CID_REFERENCE` with `Matcher.quoteReplacement` and a percent-encoded URI. The `Mail` objects keep `cid:`, and `SendRunTest` asserts that. My dry run wrote `out/inline/logo.png` and `<img src="inline/logo.png" ...>` in the HTML, with 0 `cid:` left in the five files. Browser display is an owner check (see below). |
| 4 | A dry run of the shipped example produces five rendered mails, each showing the small generated `inline/logo.png` that the HTML template references | VERIFIED (browser view pending) | `example-campaign/inline/logo.png` exists. It is 166 bytes and a valid PNG with chunks IHDR, IDAT and IEND only (no metadata). `body.html` references `cid:logo`. My dry run of the example printed `inline: logo.png (image/png, 166 bytes, cid:logo)`, `[1/5]` to `[5/5]` rendered and `dry run: 5 mails written`, and exited 0. `ExampleCampaignTest` pins the size, the dimensions and the chunk list. |

**Score:** 4/4 truths verified, 0 behavior-unverified.

### Required Artifacts

| Artifact | Expected | Status | Details |
| -------- | -------- | ------ | ------- |
| `morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/Campaign.java` | `inline/` listing, `contentId`, `checkInline`, `CID_REFERENCE` | VERIFIED | Substantive (417 lines) and wired: `read()` calls `checkInline`, and `SendCommand` uses `contentId` and `CID_REFERENCE`. |
| `morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/SendCommand.java` | inline wiring on build, dry-run copy and rewrite, `inline:` line | VERIFIED | `builder.inline(...)` in the mail loop. `writeDryRun` does the split, copy and rewrite. |
| `morimail-sample-serial/example-campaign/inline/logo.png` | tiny generated logo | VERIFIED | 166 bytes, valid PNG, no metadata. |
| `morimail-sample-serial/example-campaign/body.html` | `<img src="cid:logo">` | VERIFIED | Present with width and height 96x48. |
| Tests (`DryRunTest`, `SendRunTest`, `PreflightTest`, `ExampleCampaignTest`, `SampleFixtures`) | behavioural coverage | VERIFIED | All pass in my module run. |

### Key Link Verification

| From | To | Via | Status | Details |
| ---- | -- | --- | ------ | ------- |
| `Campaign.read` | `checkInline` | called after `listFiles(inline)` | WIRED | Findings join the shared `problems` list. |
| `SendCommand.call` | core `Mail.Builder.inline` | `campaign.inlineImages()` loop | WIRED | Regular attachments are added first. |
| `SendCommand.writeDryRun` | `Campaign.CID_REFERENCE` | `matcher.replaceAll` on `message.html()` | WIRED | Only the written HTML changes. |
| `Campaign.checkInline` | core `build()` rules | probe mails | WIRED | Type and content-ID rules come from the core. |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
| -------- | ------------- | ------ | ------------------ | ------ |
| `SendCommand` dry-run HTML | `srcById` | built from the real `Mail` attachments, taken from `inline/` files | Yes | FLOWING |
| `SendCommand` mails | `campaign.inlineImages()` | `Files.list(inline/)` | Yes | FLOWING |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
| -------- | ------- | ------ | ------ |
| Module build and tests offline | `./mvnw -B -ntp -o -q verify -pl morimail-sample-serial -am` | exit 0, no failures | PASS |
| Example dry run | `java -jar morimail-sample-serial-0.2.0-SNAPSHOT.jar send --campaign <copy of example> --dry-run` | 5 mails, `out/inline/logo.png`, `src="inline/logo.png"`, 0 `cid:` left | PASS |
| Four problem kinds in one report | same jar on a broken copy of the example | 7 problems on stderr, exit 2, no `out/`, no journal | PASS |

### Probe Execution

Step 7c: SKIPPED. The phase declares no probe scripts.

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
| ----------- | ----------- | ----------- | ------ | -------- |
| CLI-15 | 06-01, 06-02 | Optional `inline/` directory, one inline image per file, content ID = base name | SATISFIED | `listFiles`, `contentId`, `builder.inline`, `SendRunTest` |
| CLI-16 | 06-02 | Pre-flight inline problems, same report, exit 2, nothing sent | SATISFIED | `Campaign.checkInline`, manual run, `PreflightTest` |
| CLI-17 | 06-01 | Dry-run copies the images and rewrites `cid:` to relative paths in the dry-run output only | SATISFIED | `writeDryRun`, `DryRunTest`, manual run |
| CLI-18 | 06-02 | Example campaign has a generated `inline/logo.png` referenced from its HTML, and the dry run shows it | SATISFIED (browser view pending) | logo file, `body.html`, `ExampleCampaignTest`, manual run |

All four IDs are declared in the plans' frontmatter and listed as Phase 6 in REQUIREMENTS.md (marked complete). No orphaned requirements.

### Anti-Patterns Found

None blocking. There are no `TBD`, `FIXME`, `XXX` or `TODO` markers in the sample's main or test sources. No stubs, and all data paths are real.

Code review (`06-REVIEW.md`): 0 critical, 1 warning, 4 info, all recorded as `open` in `06-REVIEW-DISPOSITION.md`.

| Finding | Severity | Impact |
| ------- | -------- | ------ |
| WR-01: `DryRunTest.inlineSrcIsPercentEncodedAndHtmlSafe` creates `ü.png`, which depends on the host file-name encoding | Warning | Test-only. It can fail under a POSIX locale or on macOS. It passed on the Temurin 17 CI run of 06-01, which already contained it. Triage still needed. |
| IN-01 to IN-04 (copied core regex, probe builds, long lines, a template placeholder inside a `cid:` reference) | Info | Maintainability notes. No goal impact. |

### Human Verification Required

#### 1. Temurin 17 CI run of the 06-02 commits

**Test:** Push `05bae95`, `933649b`, `6ab7df9` and `4a7fd5c` (the branch is 6 commits ahead of origin) and wait for the Actions run.
**Expected:** All steps green.
**Why human:** Only JDK 21 is available locally, and pushing is the owner's action. Phase 6 is not complete until this run is green.

#### 2. Browser view of the dry-run logo

**Test:** Run the example campaign with `--dry-run` and open `out/01-anna@example.org.html` (and the other four) in a browser.
**Expected:** The 96x48 logo shows, not a broken-image icon.
**Why human:** Visual check. Tests assert the bytes and the `src` text only.

### Gaps Summary

No gaps. The code delivers the goal: images in `inline/` go out inline in every mail, the dry run shows them by relative path while sent mails keep `cid:`, and pre-flight reports all four inline problems in the shared report with exit 2. The status is `human_needed` only because of the two owner checks above. WR-01 should be triaged (fix or defer) before the phase is closed.

---

_Verified: 2026-11-23_
_Verifier: Claude (gsd-verifier)_
