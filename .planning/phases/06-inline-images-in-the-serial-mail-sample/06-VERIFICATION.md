---
phase: 06-inline-images-in-the-serial-mail-sample
verified: 2026-11-23T10:00:00Z
status: passed
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
  - "morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/SampleFixtures.java"
  - "morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/SendRunTest.java"
  - "morimail/src/main/java/de/morisoft/morimail/Mail.java"
covered_digest: "v4:sha256:7a51d51dcc915a95ab0b93a523368731d48a85c38f1727b8c37cd69e1a9639ad"
behavior_unverified: 0
overrides_applied: 0
re_verification:
  previous_status: human_needed
  previous_score: 4/4
  gaps_closed: []
  gaps_remaining: []
  regressions: []
---

# Phase 6: Inline Images in the Serial-Mail Sample Verification Report

**Phase Goal:** A campaign author can put images into an optional `inline/` directory. The dry run shows them in the rendered mails, and every sent mail carries them inline.
**Verified:** 2026-11-23
**Status:** passed
**Re-verification:** Yes. The earlier report (status `human_needed`) predates the review-fix commits a92b831, ed29b34, 0aea8d0 and 48bb97e. I re-read the changed code and re-ran the checks myself.

## Goal Achievement

### Observable Truths

| #   | Truth (roadmap success criterion) | Status | Evidence |
| --- | --------------------------------- | ------ | -------- |
| 1 | With `inline/`, every mail goes out with each file as an inline image (content ID = file name without extension); a campaign without `inline/` behaves as in v0.1.0 | VERIFIED | `SendCommand.call()` (line 175) loops `campaign.inlineImages()` after the regular attachments and calls `builder.inline(image, Campaign.contentId(image))`. `Campaign.contentId` strips the last extension (dot index > 0). `listFiles` returns an empty list when `inline/` is absent. `SendRunTest.sentMailsCarryTheInlineImageAndKeepTheCidReference` and `inlineImagesFollowTheAttachmentsInFileNameOrder` exist and pass. |
| 2 | Pre-flight reports the four inline problems in the shared report, exit 2, nothing sent, no journal | VERIFIED | `Campaign.checkInline` runs from `Campaign.read` and adds to the shared `problems` list. I ran the jar on a broken copy of the example (`notes.txt`, `logo.jpg`, `extra.png`, `cid:missing`). It reported `same content ID as inline/logo.jpg`, `inline image notes has content type text/plain, which is not an image type`, `body.html: cid:missing has no image in inline/` and `no cid:extra reference in body.html`. It printed "Pre-flight failed with 4 problem(s); nothing was sent.", exited 2, created no `out/` and no journal. `PreflightTest` has 13 tests. |
| 3 | Dry-run writes the images next to the rendered HTML and rewrites `cid:` to relative paths in the dry-run output only; sent mails keep `cid:` | VERIFIED | `writeDryRun` copies each image once to `out/inline/` and rewrites only the string written to the `.html` file. It uses `CID_REFERENCE`, `Matcher.quoteReplacement` and a percent-encoded `URI`. The `Mail` objects keep `cid:`; `SendRunTest` asserts it. My dry run of the example wrote `out/inline/logo.png`, `<img src="inline/logo.png" alt="morimail logo" width="96" height="48">`, and 0 `cid:` in all five HTML files. |
| 4 | A dry run of the shipped example produces five rendered mails, each showing the small generated `inline/logo.png` | VERIFIED | My dry run of a copy of `example-campaign` printed `inline: logo.png (image/png, 166 bytes, cid:logo)`, `[1/5]` to `[5/5]` rendered, and exited 0. All five HTML files carry the relative `src`. `ExampleCampaignTest` (5 tests) pins the logo. UAT test 2 (browser view of all five mails) passed. |

**Score:** 4/4 truths verified, 0 behavior-unverified.

### Required Artifacts

| Artifact | Expected | Status | Details |
| -------- | -------- | ------ | ------- |
| `morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/Campaign.java` | `inline/` listing, `contentId`, `checkInline`, `CID_REFERENCE`, `inlineImages` component | VERIFIED | Substantive and wired (`read` calls `checkInline`; `SendCommand` uses `contentId` and `CID_REFERENCE`). Mails are built only when the inline scan found nothing, so findings are not reported twice. |
| `morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/SendCommand.java` | build-loop wiring, dry-run copy and rewrite, `inline:` line | VERIFIED | Present as described. |
| `morimail-sample-serial/example-campaign/inline/logo.png` | small generated logo | VERIFIED | Exists, 166 bytes per the dry-run output. |
| `morimail-sample-serial/example-campaign/body.html` | `<img src="cid:logo">` | VERIFIED | Dry-run output shows the `cid:` was found and rewritten. |
| Sample tests (`DryRunTest` 14, `PreflightTest` 13, `ExampleCampaignTest` 5, `SendRunTest`, `SampleFixtures`) | behavioural coverage | VERIFIED | All pass in `./mvnw -B -ntp -o verify` (run by me: BUILD SUCCESS, sample module 163 tests, 0 failures). |

### Key Link Verification

| From | To | Via | Status | Details |
| ---- | -- | --- | ------ | ------- |
| `Campaign.read` | `checkInline` | call after `listFiles(inline)` | WIRED | Findings join the shared `problems` list. |
| `SendCommand.call` | core `Mail.Builder.inline` | `campaign.inlineImages()` loop | WIRED | Regular attachments are added first. |
| `SendCommand.writeDryRun` | `Campaign.CID_REFERENCE` | `matcher.replaceAll` on `message.html()` | WIRED | Only the written HTML changes. |
| `Campaign.checkInline` | core `build()` rules | probe mails | WIRED | Type and content-ID rules come from the core. |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
| -------- | ------------- | ------ | ------------------ | ------ |
| `SendCommand.writeDryRun` HTML | `srcById` | built from the real `Mail` attachments, taken from `inline/` files | Yes | FLOWING |
| `SendCommand.call` mails | `campaign.inlineImages()` | `Files.list(inline/)` | Yes | FLOWING |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
| -------- | ------- | ------ | ------ |
| Full offline build | `./mvnw -B -ntp -o verify` | BUILD SUCCESS, 0 failures | PASS |
| Example dry run | `java -jar morimail-sample-serial-0.2.0-SNAPSHOT.jar send --account ... --campaign <copy of example> --dry-run --out ...` | exit 0, 5 mails, `out/inline/logo.png`, `src="inline/logo.png"`, 0 `cid:` left | PASS |
| Four problem kinds in one report | same jar on a broken copy | 4 problems, exit 2, no `out/`, no journal | PASS |

### Probe Execution

SKIPPED. The phase declares no probe scripts.

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
| ----------- | ----------- | ----------- | ------ | -------- |
| CLI-15 | 06-01, 06-02 | Optional `inline/` directory, one inline image per file, content ID = base name | SATISFIED | `listFiles`, `contentId`, `builder.inline`, `SendRunTest` |
| CLI-16 | 06-02 | Pre-flight inline problems in the shared report, exit 2, nothing sent | SATISFIED | `Campaign.checkInline`, manual run, `PreflightTest` |
| CLI-17 | 06-01 | Dry-run copies the images and rewrites `cid:` in the dry-run output only | SATISFIED | `writeDryRun`, `DryRunTest`, manual run |
| CLI-18 | 06-02 | Example has a generated `inline/logo.png` referenced from its HTML; the dry run shows it | SATISFIED | logo file, `body.html`, `ExampleCampaignTest`, manual run, UAT test 2 |

PLAN frontmatter declares CLI-15 and CLI-17 (06-01) and CLI-15, CLI-16 and CLI-18 (06-02). REQUIREMENTS.md maps CLI-15 to CLI-18 to Phase 6, all marked complete. No orphaned requirements.

### Anti-Patterns Found

No `TBD`, `FIXME`, `XXX` or `TODO` markers in the sample sources or tests (grep). No stubs. `06-REVIEW.md` (re-review of the four files changed by the fixes): 0 critical, 0 warning, 3 info, no goal impact. `06-SECURITY.md`: `status: verified`, `threats_open: 0`.

### Human Verification

None outstanding. Both items from the earlier report are closed:

- Temurin 17 CI: `gh run list` shows run 45149609185 on head SHA 4ea76d8 with conclusion `success`. The commits after 4ea76d8 change only `.planning/` files (git diff), so the code under test equals the code that ran green.
- Browser view of the dry-run logo: recorded as `pass` in `06-UAT.md` (2 passed, 0 issues). My dry run confirms the HTML points at an existing `out/inline/logo.png`.

### Gaps Summary

No gaps. The code delivers the goal: images in `inline/` go out inline in every mail, the dry run shows them by relative path while sent mails keep `cid:`, and pre-flight reports the inline problems in the shared report with exit 2. The earlier warning WR-01 was fixed in a92b831 and the build is green.

---

_Verified: 2026-11-23_
_Verifier: Claude (gsd-verifier)_
