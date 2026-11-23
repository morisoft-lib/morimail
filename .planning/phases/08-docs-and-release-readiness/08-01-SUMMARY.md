---
phase: 08-docs-and-release-readiness
plan: 01
subsystem: docs
tags: [readme, spec, architecture, picocli, inline-images, morimail-html]
status: complete

requires:
  - phase: 05-inline-images-in-core-and-graph
    provides: inline image checks in build(), Graph property names, owner live pass of 2026-11-23
  - phase: 06-inline-images-in-the-serial-mail-sample
    provides: the sample's inline/ directory and its dry-run copy
  - phase: 07-html-image-helper-module
    provides: HtmlImages.embed, toDataUris and the helper decisions
provides:
  - README sections Inline images, morimail-html and Serial-mail sample, both Java snippets compiled as written
  - docs/spec.md 5, 5.1, 6.2, 8.1, 8.3, 10.3, 10.4 and 12 describing 0.2.0
  - docs/architecture.md with the confirmed live status and the no-parser clause
  - send usage text naming the campaign files, inline/ and cid:NAME, pinned by SendCommandHelpTest
affects: [08-02 release.md and the scoped release build]

actuals:
  tokens: 3860
  tasks: 3
  commits: 3
plan_head_before: f246725
plan_head_after: cccfdf28849eb7ca6e1cac20696bb729fe85128d

tech-stack:
  added: []
  patterns: ["README Java blocks are complete source files that compile as written (gated with javac --release 17)"]

key-files:
  created:
    - morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/SendCommandHelpTest.java
  modified:
    - README.md
    - docs/spec.md
    - docs/architecture.md
    - morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/SendCommand.java

key-decisions:
  - "08-01: README inline, morimail-html and Serial-mail sample sections with compiled snippets; spec 5/5.1/6.2/8.1/8.3/10.3/10.4/12 and docs/architecture.md describe 0.2.0 with the live result of 2026-11-23; send usage names inline/ and cid:NAME, pinned by SendCommandHelpTest; no new option"

requirements-completed: [DOCS-05, DOCS-07]

duration: 3 min
completed: 2026-11-23
---

# Phase 8 Plan 01: Inline images, `morimail-html` and the sample's `inline/` directory, documented Summary

**README, spec, architecture page and `send` usage text now describe inline images, the `morimail-html` helper and the sample's `inline/` directory for 0.2.0, with both README Java snippets compiled as written.**

## Accomplishments

- D-04: the README Coordinates block shows 0.2.0 and the line below says 0.2.0 is not on Maven Central yet and 0.1.0 is the current release. The Quick start is byte-identical.
- D-01, D-02: "## Inline images" follows the Quick start with a complete source file (`InlineImage`), the `build()` checks (exact `cid:` match, case included, every image referenced, an HTML body needed, unique and valid content IDs, `image/` types) and the limits (3 MiB, 4 MiB) with a link to docs/architecture.md.
- D-03: "## morimail-html" has the 0.2.0 dependency block, a complete source file (`EmbedImages`) with both `embed` overloads and `toDataUris`, and the work-directory opt-in with the trust note.
- D-05, D-06, D-08: spec 5, 5.1 and 6.2 describe the section 10.3 checks, `"isInline": true`, the bare `contentId`, no `contentLocation`, no `isInline` key on regular attachments and the live sentence. docs/architecture.md has the same live sentence and the stale deviation row is gone (66 lines).
- D-07: the 10.3 and 10.4 headings say "implemented in 0.2.0". The two morimail-html rows of section 12 record the decided outcomes. The architecture decisions row starts with "No HTML parser: a JDK regular expression over `<img src>`." and says "Spec §12 left these".
- D-09, D-11: the `send` footer lists the campaign files and the `inline/` content-ID rule, and `--dry-run` says it copies each inline image once into `inline/` under `--out` and rewrites `cid:NAME` to `inline/NAME` plus the extension in the dry-run output only, while sent mails keep `cid:`. `SendCommandHelpTest.sendUsageNamesTheCampaignFilesAndTheInlineRules` was RED before the annotation change and GREEN after. SendCommand still has 8 options and no help option.
- D-10: "## Serial-mail sample" holds the layout, the `inline/` rules, the try-it block moved verbatim and the `out/inline/` paragraph. Spec 8.1 lists `inline/`, 8.3 describes `out/inline/` and that real mails keep `cid:`.

## Task Commits

1. Task 1 (tracer): `5d50c94` docs(08-01): document inline images in README, spec and architecture
2. Task 2: `65eb170` docs(08-01): document morimail-html in README, spec and architecture
3. Task 3: `cccfdf2` docs(08-01): describe the inline/ directory in send help and the README

Nothing was pushed, tagged or deployed.

## Gate Results

| Gate | Result |
|------|--------|
| Task 1 docs gate, Inline images snippet gate, commit gate | pass (exit 0) |
| Task 2 docs gate, both snippet gates, commit gate | pass (exit 0) |
| Task 3 test gate (RED at 400357d state, GREEN after) | pass |
| Task 3 source gate (hunks 42-47 and 66 only, 8 options, no help option, only `${DEFAULT-VALUE}`, gated phrases) | pass |
| Task 3 README and spec gate | pass |
| `./mvnw -B -ntp -o verify` | BUILD SUCCESS (sample module 164 tests) |
| Hygiene gate (addresses, GUIDs, home paths) | pass |
| Scope guard (nothing under morimail/, morimail-html/, POMs, .github/) | pass |
| Task 3 commit gate | pass |

## Deviations from Plan

None - plan executed exactly as written.

Notes on execution, not deviations: the Co-Authored-By trailer names the model of the executing run (`Claude Sonnet 5.5`), as the run's attribution reminder supplied it. The dispatch prompt named another model. The commit gates accept any `Claude <name> <...@anthropic.com>` trailer.

## Owner review

1. `send --help` is not a declared option. It prints "Missing required options: '--account=FILE', '--campaign=DIR'" and then the full usage on stderr, with exit code 2, because the sample has no help option (D-09 forbids a new option). A real `--help` would be a new option.
2. Spec 8.1 and 8.3 got lines that D-05 to D-07 did not list (the `inline/` line and the dry-run paragraph), following D-05's intent that the spec describes the code.
3. The Coordinates block shows 0.2.0 before 0.2.0 is on Central (D-04). The sentence below it says so, and release.md step 8 (plan 08-02) names that sentence.
4. Spec 10.3, the Sample bullet, changed per D-07 as amended on 2026-11-23:
   - Before: `- Sample: an optional `inline/` directory in the campaign; the file name without extension is the content ID. Dry-run writes the images next to the rendered HTML and rewrites `cid:` references to relative paths in the dry-run output only.`
   - After: `- Sample: an optional `inline/` directory in the campaign; the file name without extension is the content ID. Dry-run copies each inline image once into `inline/` under `--out` and rewrites `cid:` references to relative paths in the dry-run output only.`

## Known Stubs

None.

## Threat Flags

None. The README states that `http(s)` and `data:` sources are only fetched with a caller-supplied work directory and only for trusted templates (T-08-02).

## Next Phase Readiness

Ready for 08-02 (docs/release.md and the scoped release build).

## Self-Check: PASSED

- README.md, docs/spec.md, docs/architecture.md, SendCommand.java and SendCommandHelpTest.java exist.
- Commits 5d50c94, 65eb170 and cccfdf2 are ancestors of HEAD.
