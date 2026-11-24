---
phase: 09-v0-2-0-tech-debt-cleanup
plan: 01
subsystem: core
tags: [cid-references, inline-images, content-types, log-hygiene, architecture-docs]
status: complete

requires:
  - phase: 05-inline-images-in-core-and-graph
    provides: Mail.Builder.CID_REFERENCE, checkReferences and CONTENT_TYPES (review items WR-01, WR-02, IN-01, IN-03)
  - phase: 06-inline-images-in-the-serial-mail-sample
    provides: Campaign.CID_REFERENCE copy and its agreement test
  - phase: 07-html-image-helper-module
    provides: HtmlImages.CID_REFERENCE and CONTENT_TYPES copies and their agreement tests
provides:
  - a cid- id that ends at "<" in the core, the sample and the helper (three byte-equal pattern lines)
  - build() message that shows an HTML-read cid id cleaned (control, line separator, paragraph separator and format characters shown as ?) and cut at 64 code points
  - webp, bmp and svg in both content-type tables
  - docs/architecture.md section 10.3 row describing the scan as built
affects: [09-02, 09-03, milestone archive]

actuals:
  tokens: 5433
  tasks: 3
  commits: 3
plan_head_before: 8ee52be023e6c1d2f1484d8523fcf49f3e9b110c
plan_head_after: fa714ace2de645508230811a4f489a74c0ac4186

tech-stack:
  added: []
  patterns: ["a private static helper next to contentIdProblem and contentTypeOf, no utility class"]

key-files:
  created: []
  modified:
    - morimail/src/main/java/de/morisoft/morimail/Mail.java
    - morimail/src/test/java/de/morisoft/morimail/MailBuilderTest.java
    - morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/Campaign.java
    - morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/CampaignTest.java
    - morimail-html/src/main/java/de/morisoft/morimail/html/HtmlImages.java
    - morimail-html/src/test/java/de/morisoft/morimail/html/HtmlImagesTest.java
    - docs/architecture.md

key-decisions:
  - "09-01: a cid: id ends at < in all three pattern copies; the whole-HTML scan stays (no parser), prose such as ref=cid:123 still counts as an accepted trade-off"
  - "09-01: shownId in Mail.Builder replaces ISO control, LINE_SEPARATOR, PARAGRAPH_SEPARATOR and FORMAT code points with ? before the 64-code-point cut; content ids stay case-sensitive and the unquoted /> rule is documented, not changed"

requirements-completed: []

duration: about 20 min
completed: 2026-11-24
---

# Phase 9 Plan 01: core cid: rules and the three pattern copies Summary

**A `cid:` id now ends at `<` in the core, the serial mail sample and the HTML image helper, the id echoed by `build()` is cleaned of control, separator and format characters, and webp, bmp and svg resolve from both content-type tables.**

## Accomplishments

- D-04, D-05: `<p>cid:logo</p>` refers to `logo` in `Mail.Builder`, `Campaign` and `HtmlImages`. The three `Pattern.compile` lines are byte-identical (id class `[^\"'\\s)<>]*`). Both `cidPatternAgreesWithTheCore` corpora gained the entry and build it through `Mail.build()`; `referenceIsFoundInAnyAttributeStyleOrCase` has an eighth value.
- D-01, D-03: `Mail.Builder.shownId(String)` replaces every ISO control, line separator, paragraph separator and format code point with `?`, then cuts at 64 code points plus `...`. A recipient value with ESC, NEL, U+2028 and U+202E reached through a template placeholder now gives exactly `no inline image for cid:a?b?c?d?e`. `longReferenceIsCutAtWholeCodePoints` pins the cut for supplementary characters.
- D-06: `unquotedSrcBeforeASlashKeepsTheSlashInTheId` pins that `<img src=cid:logo/>` reads `logo/`; the `build()` Javadoc and the architecture row say so and that quoting avoids it.
- D-07: the Mail.java and HtmlImages.java pattern comments state the rule; `grep -rlE '\bD-[0-9]+' morimail/src/main` lists nothing; the architecture row cites no decision number.
- D-08: webp, bmp and svg are in `Mail.Builder.CONTENT_TYPES` and `HtmlImages.CONTENT_TYPES`; the `attach(Path)` Javadoc lists fifteen extensions. `resolvesTheAddedImageTypesOnEveryPlatform` (4 rows) and three new pairs in `localTypeAgreesWithTheCore` pin the types.
- D-09: the `inline()` Javadoc and the architecture row say ids are case-sensitive, that the live test of 2026-11-23 did not cover ids differing only in case, and that a careful author avoids them.

## Task Commits

| Task | Commit | Subject | Tests after (core, helper, sample) |
|------|--------|---------|------------------------------------|
| 1 (tracer) | 4e4e4df | fix(09-01): end a cid: id at < in the core, the sample and the helper | 366, 71, 164 |
| 2 | 3ff8fd4 | fix(09-01): show control characters of an echoed cid: id as ? | 370, 71, 164 |
| 3 | fa714ac | fix(09-01): resolve webp, bmp and svg everywhere and document the cid: rules | 374, 71, 164 |

After each commit `./mvnw -B -ntp -o verify` and `./mvnw -B -ntp -Prelease verify -Dgpg.skip -pl morimail,morimail-html -am` were green, the release build with zero `[WARNING]` lines. Tracer gate: the agreement tests failed on the new corpus entry before the pattern edit (red) and passed after (green); verified end to end before expanding.

## Checks recorded for the plan

- **D-02 (only HTML-read ids are cleaned):** checked, no change needed outside the core. The sample passes the core message on unchanged (`SendCommand` prints `e.getMessage()`); `Campaign.checkInline` names ids from `body.html` as written, which is a campaign file and never recipient data; `HtmlImages` uses its pattern copy only to fill the `taken` set and never prints an id. Builder-supplied content ids and content types are echoed as before.
- **D-08 inline() Javadoc:** it resolves the type "as for `attach(Path)`", so its type text needed no change; only the case sentence was added.
- **ModuleRulesTest constraint on core comments:** `ModuleRulesTest.morimailDoesNotReferenceTheHelper` fails if any file under `morimail/src` contains the helper's artifact id or package name. The Mail.java comment therefore names the copy as `HtmlImages.CID_REFERENCE in the HTML image helper`.

## Deviations from Plan

None - plan executed exactly as written.

Environment note (not a code deviation): in this shell `grep` is a function wrapping ugrep, whose `grep -vq` on empty input exits 0. That made the plan's commit-gate address check report a false "bad address" on the first run. I ran the gates with `/usr/bin/grep` (`unset -f grep`), and all gates passed. Also, two of the four Unicode escapes written into `MailBuilderTest` (U+2028 and U+202E) were turned into raw characters by the editor; I rewrote them as `\u` escapes before the test ran for the first time, and the file contains no raw invisible characters.

## Known Stubs

None.

## Threat Flags

None. T-09-01 (log forging through the echoed id) is mitigated by `shownId` and proven by `controlCharactersInAReferenceAreNotEchoed`; T-09-03 (copy drift) by the single distinct pattern line and both agreement corpora; T-09-04 by the commit gates (no address outside example.com and example.org, no home path, no long hex string in the added lines).

## Pending owner actions

- Push when ready so Temurin 17 CI runs on this phase. The regex, `Character` and Javadoc changes use JDK 8+ APIs, but only JDK 21 is installed here.

## Next Phase Readiness

Ready for 09-02. Nothing was pushed, tagged or published.

## Self-Check: PASSED

- Files modified exist: Mail.java, MailBuilderTest.java, Campaign.java, CampaignTest.java, HtmlImages.java, HtmlImagesTest.java, docs/architecture.md (all committed).
- Commits 4e4e4df, 3ff8fd4 and fa714ac are ancestors of HEAD; `git rev-list --count 8ee52be..HEAD` is 3.
- All task acceptance gates passed; author and committer are Stephan Wald <stephan@morisoft.de> on all three commits.
