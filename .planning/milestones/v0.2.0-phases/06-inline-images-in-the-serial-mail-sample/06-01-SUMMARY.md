---
phase: 06-inline-images-in-the-serial-mail-sample
plan: 01
subsystem: sample
tags: [inline-images, cid, dry-run, picocli, java-net-uri, serial-mail]

requires:
  - phase: 05-inline-images-in-core-and-graph
    provides: "Mail.Builder.inline(Path, String), the cid: reference grammar and the build() inline checks, Graph inline attachments"
provides:
  - "Optional inline/ directory in a campaign: every regular file is an inline image of every mail, content ID = file name without its last extension"
  - "Dry run copies the images once into out/inline/ and points the written HTML at them by percent-encoded relative path; sent mails keep cid:"
  - "inline: stdout line of the dry run"
affects: [06-02 pre-flight inline scan, 06-02 example campaign logo, phase 8 docs for the sample]

actuals:
  tokens: 4755
  tasks: 3
  commits: 2
plan_head_before: 9655d197ff675473d10735b3304b215527cdebe3
plan_head_after: 2633e3a73a578661d9c79864eac26fb5b23c26f8

tech-stack:
  added: []
  patterns:
    - "Pattern copied from the core (Campaign.CID_REFERENCE) with a static byte-equality gate, because the core does not rewrite HTML"
    - "One shared listFiles for attachments/ and inline/ (regular files, dot-files skipped, sorted by file name)"
    - "Dry-run rewrite touches only the string written to the .html file; Mail and MailMessage keep cid:"

key-files:
  created: []
  modified:
    - morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/Campaign.java
    - morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/SendCommand.java
    - morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/SampleFixtures.java
    - morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/DryRunTest.java
    - morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/SendRunTest.java

key-decisions:
  - "Stdout: one inline: line right after the attachments: line, printed only when the mail has inline images; attachments: lists regular attachments only"
  - "Dry-run src value: new URI(null, null, \"inline/\" + fileName, null).toASCIIString() with & then written as &amp; (D-05)"
  - "The cid: pattern is copied into Campaign next to PLACEHOLDER; a static gate keeps it byte-equal to the core's"
  - "inlineImages is a List<Path> and the content ID a pure static function of the path; no new record, class or interface (Rule 0)"
  - "Attachments first, inline images after them, so v0.1.0 mails keep their attachment order"

patterns-established:
  - "Content ID is derived from the file name, never chosen (D-03): logo.png is cid:logo, logo.v2.png is cid:logo.v2, a name without a dot stays whole"
  - "Hidden-only or empty inline/ behaves exactly like no inline/"

requirements-completed: [CLI-15, CLI-17]

coverage:
  - id: D1
    description: "A campaign with inline/ sends every mail with each file as an inline image under the file name without its last extension; the sent HTML keeps cid:"
    requirement: CLI-15
    verification:
      - kind: unit
        ref: "morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/SendRunTest.java#sentMailsCarryTheInlineImageAndKeepTheCidReference"
        status: pass
      - kind: unit
        ref: "morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/SendRunTest.java#inlineImagesFollowTheAttachmentsInFileNameOrder"
        status: pass
    human_judgment: false
  - id: D2
    description: "The dry run copies each image once into out/inline/, prints an inline: line, and every written HTML file points at the copies by percent-encoded relative path"
    requirement: CLI-17
    verification:
      - kind: unit
        ref: "morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/DryRunTest.java#inlineImageIsCopiedOnceAndTheHtmlPointsAtIt"
        status: pass
      - kind: unit
        ref: "morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/DryRunTest.java#inlineSrcIsPercentEncodedAndHtmlSafe"
        status: pass
      - kind: unit
        ref: "morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/DryRunTest.java#inlineLineListsEveryImageInFileNameOrder"
        status: pass
    human_judgment: false
  - id: D3
    description: "Dry-run edges: any-case cid: prefix is rewritten and a bare cid: stays, --limit still copies once, a rerun overwrites the copies and keeps other files, a hidden-only inline/ changes nothing"
    requirement: CLI-17
    verification:
      - kind: unit
        ref: "morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/DryRunTest.java#bareCidStaysAndAnyCasePrefixIsRewritten"
        status: pass
      - kind: unit
        ref: "morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/DryRunTest.java#limitStillCopiesTheImagesOnce"
        status: pass
      - kind: unit
        ref: "morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/DryRunTest.java#rerunOverwritesTheCopiedImagesAndKeepsOtherFiles"
        status: pass
      - kind: unit
        ref: "morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/DryRunTest.java#inlineDirectoryWithOnlyHiddenFilesChangesNothing"
        status: pass
    human_judgment: false
  - id: D4
    description: "A campaign without inline/ behaves as in v0.1.0: every existing sample test passes unchanged"
    requirement: CLI-15
    verification:
      - kind: unit
        ref: "./mvnw -B -ntp verify (146 tests in the sample module, JDK 21)"
        status: pass
      - kind: unit
        ref: "morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/DryRunTest.java#writesRenderedFilesAndListsAttachments"
        status: pass
    human_judgment: false
  - id: D5
    description: "Early Temurin 17 round-trip: the pushed 06-01 commits build green on Temurin 17 in all three CI steps (verify, scoped release build, offline build)"
    verification:
      - kind: other
        ref: "GitHub Actions ci.yml run on 2633e3a: completed success (read-only gh run list check after the owner's push)"
        status: pass
    human_judgment: false
  - id: D6
    description: "Unicode normalization mismatch between a file name and the cid: in body.html (for example NFD after a macOS copy) is reported by pre-flight; the concurrent-change and two-runs edges keep the known journal limit"
    verification: []
    human_judgment: true
    rationale: "Plan marks both as backstop: the pre-flight report for the normalization case arrives with Plan 06-02, and the concurrency edges need real parallel runs that no test here asserts"

duration: 22min
completed: 2026-11-23
status: complete
---

# Phase 6 Plan 01: Inline images through the serial-mail sample (tracer first) Summary

**An optional `inline/` directory in a campaign now becomes inline images in every sent mail (content ID = file name without its last extension), and the dry run copies them once to `out/inline/` with percent-encoded, HTML-safe relative `src` paths while sent mails keep `cid:`.**

## Performance

- **Duration:** 22 min wall clock, including the wait for the owner's push and the Temurin 17 CI run (the two task commits are one minute apart)
- **Started:** 2026-11-23T07:48:44Z
- **Completed:** 2026-11-23T08:10:34Z
- **Tasks:** 3 (two commit tasks and the owner checkpoint)
- **Files modified:** 5

## Accomplishments

- `Campaign` reads `inline/` with the same listing rule as `attachments/` (one shared `listFiles`), exposes `inlineImages`, `contentId(Path)` and a copy of the core's `cid:` pattern (`CID_REFERENCE`, pinned byte-equal to the core's by the static gate).
- `SendCommand` adds every inline image with `builder.inline(image, Campaign.contentId(image))` after the regular attachments, so v0.1.0 mails keep their attachment order. The recording tracer shows three mails with `info.txt` (no content ID) then `logo.png` (`image/png`, content ID `logo`), and the HTML still reads `cid:logo`.
- The dry run copies the images once into `out/inline/` (before the row loop, so `--limit` does not matter), rewrites only the string written to the `.html` files, and prints `inline: ...` after the `attachments:` line.
- Seven edge tests pin encoding (`a&b.png`, `ü.png`, `50%.png`, `x#1.png`), any-case `CID:` and bare `cid:`, `--limit`, rerun overwrite, hidden-only `inline/`, and file-name order.
- The early Temurin 17 round-trip is green: the owner pushed `main` and the CI run for the pushed head passed all three steps.

Temurin 17 CI (mid-phase, 06-01 Task 3): green on 2633e3a (verify, scoped release build, offline build)

## Task Commits

Each task was committed atomically:

1. **Task 1: End-to-end inline/logo.png (tracer)** - `6f635cf` (feat)
2. **Task 2: Pin the dry-run and send edges of inline images** - `2633e3a` (test)
3. **Task 3: Owner pushes and confirms the Temurin 17 CI run** - no commit (checkpoint; the owner pushed `aa7a7da..2633e3a`)

**Plan metadata:** the docs commit that holds this SUMMARY, STATE.md and ROADMAP.md (docs: complete plan)

_Task 2 is a test-only commit: see "Task 2 needed no source fix" below._

## Files Created/Modified

- `morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/Campaign.java` - `inlineImages` component, `CID_REFERENCE`, `contentId(Path)`, shared `listFiles` for `attachments/` and `inline/`
- `morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/SendCommand.java` - `builder.inline` in the build loop; in `writeDryRun` the split of regular and inline attachments, the `inline:` line, the copy into `out/inline/` and the `cid:` rewrite of the written HTML
- `morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/SampleFixtures.java` - `INLINE_HTML` and `writeInline(Path, String...)`
- `morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/DryRunTest.java` - the dry-run tracer and six edge tests; `dryRun` helper takes extra arguments
- `morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/SendRunTest.java` - the recording tracer and the order test

## Owner review

1. **The `inline:` stdout line format.** One line right after `attachments:`, printed only when the mail has inline images, shaped like the attachments line: `inline: logo.png (image/png, 166 bytes, cid:logo)`, entries joined by `, `. The `attachments:` line lists regular attachments only and stays `attachments: none` when there are none. A campaign without `inline/` prints the same five stdout lines as in v0.1.0.
2. **Percent-encoding through `java.net.URI` with `&` written as `&amp;` (D-05).** The dry-run `src` is `new URI(null, null, "inline/" + fileName, null).toASCIIString()`, then `&` becomes `&amp;`, because `URI` leaves `&` raw and the value goes into an HTML attribute. `a&b.png`, `ü.png`, `50%.png` and `x#1.png` become `inline/a&amp;b.png`, `inline/%C3%BC.png`, `inline/50%25.png` and `inline/x%231.png`. Only the written `.html` files change; the sent mail keeps `cid:`.

## Decisions Made

- The `cid:` pattern is copied into `Campaign` (the core does not rewrite HTML, spec 10.3); a static gate compares the two `Pattern.compile` lines, so the dry-run rewrite reads exactly the references `build()` checked.
- `inlineImages` stays a `List<Path>` and the content ID a pure static function of the path: no new record, class or interface (Rule 0).
- No pre-flight inline check in this plan. Until Plan 06-02, inline problems (a bad content ID, an unsupported image type, a `cid:` reference with no image, a file name that does not match a `cid:` in `body.html`) are reported only by `build()`, through the existing per-row aggregation (one message per distinct text, exit 2).

## Deviations from Plan

None - plan executed exactly as written.

**Task 2 needed no source fix.** All seven new edge tests passed on the first run against the Task 1 code, so Task 2 had no failing RED phase and no GREEN change; the commit holds only `DryRunTest.java` and `SendRunTest.java`. The tests still pin the behaviour so a later change that breaks it fails.

**Total deviations:** 0
**Impact on plan:** None.

## Issues Encountered

None. `./mvnw -B -ntp verify` is green on JDK 21 (146 tests in the sample module), `ü.png` worked locally, and the CI run on Temurin 17 passed all three steps, so the runner-locale risk for the non-ASCII file name did not materialise.

## Authentication Gates

None. Task 3 was a planned human-action checkpoint (the owner pushes, because Claude never pushes), not an auth gate.

## User Setup Required

None - no external service configuration required.

## Known Stubs

None. No placeholder, hardcoded empty value or unwired data source in the changed files.

## Threat Flags

None. Nothing outside the plan's threat model: the copy target stays inside `out/inline/` (T-06-01), the `src` value is percent-encoded and `&amp;`-escaped (T-06-02), and sent mails are never rewritten (T-06-03).

## Next Phase Readiness

- Ready for 06-02: the pre-flight inline scan (CLI-16), the example campaign logo (CLI-18) and the phase gate. Its Task 1 precondition matches the anchored Temurin line above and re-checks that SHA (the last 06-01 commit is its ancestor, it is on `origin/main`, its `ci.yml` run is completed success).
- Open until 06-02: CLI-15 stays unchecked in REQUIREMENTS.md because Plan 06-02 also declares it (shared-ID gate).
- Core and POMs are unchanged against `0ab315d` (`git diff` prints nothing under `morimail/` or for the three POM files).

---
*Phase: 06-inline-images-in-the-serial-mail-sample*
*Completed: 2026-11-23*

## Self-Check: PASSED

- FOUND: `Campaign.java`, `SendCommand.java`, `SampleFixtures.java`, `DryRunTest.java`, `SendRunTest.java`
- FOUND: commits `6f635cf`, `2633e3a` (ancestors of HEAD and `origin/main`)
- Measured commits: 2 (`git rev-list --count 9655d19..HEAD`)
- Task 3 checks: `git merge-base --is-ancestor` exit 0; `gh run list --workflow ci.yml --commit 2633e3a...` printed `completed success`
