# Phase 6: Inline Images in the Serial-Mail Sample - Context

**Gathered:** 2026-11-23
**Status:** Ready for planning

<domain>
## Phase Boundary

The serial-mail sample (`morimail-sample-serial`) learns an optional `inline/` campaign directory (spec §10.3, sample line). Every file in it becomes an inline image in every mail. Its content ID is the file name without the extension. Pre-flight reports every inline problem in the same report as the other findings (exit 2, nothing sent, no journal). The dry run copies the images into its output and rewrites `cid:` to relative paths in the dry-run HTML only. The example campaign gets a small generated inline logo. A campaign without `inline/` behaves exactly as in v0.1.0. Requirements: CLI-15..18.

Not in this phase: the `morimail-html` helper (Phase 7), sample docs, `send --help` text and README (Phase 8, DOCS-05), and per-recipient inline images (out of scope, REQUIREMENTS.md).

</domain>

<decisions>
## Implementation Decisions

### Carried forward (not re-asked)
- Phase 5 D-04..D-06: the `cid:` grammar is `(?i)(?<![\p{L}\p{N}])cid:([^"'\s)>]*)`, the ID is matched case-sensitively, and an empty ID is skipped. Only the HTML body counts (D-07).
- Phase 5 D-09: a content ID must not be blank and must not contain whitespace, CR/LF, `"` `'` `(` `)` `<` `>`. D-10: an image means the resolved content type starts with `image/`.
- Phase 5 D-13: `build()` reports only the first problem.
- Phase 2 D-02: dry-run runs pre-flight, `build()` included, without a `Mailer`. D-13: exit 2 for pre-flight. D-15: dry-run creates `out/`, overwrites same-named files and leaves other files alone. The same `build()` message across rows is reported once (`SendCommand`'s `invalid` map).

### Pre-flight source
- **D-01:** The inline checks run as a **template scan in `Campaign.read`**, before any mail is built. The scan reads the `body.html` template and the `inline/` file list and adds **one problem per defect**:
  - each template `cid:` with no image
  - each image that no template `cid:` references
  - each pair of files with the same base name
  - each non-image file
  - each file whose name gives an invalid content ID (D-02)
  
  Mails are built (`.inline(file, id)` per image) only when the scan finds nothing, so `build()` stays a backstop. Its first-problem message must not duplicate the scan's findings in the report. One case only `build()` can catch is a `cid:` that appears only through a placeholder value, after rendering. It is reported through the existing per-row aggregation.
- **D-02:** When a file name gives a content ID that the Phase 5 rules reject (for example `my logo.png` → `my logo`), pre-flight reports a problem that names the file. The sample never sanitizes or invents IDs. The ID is exactly the file name without its last extension.
- **D-03:** "Without extension" means **strip the last extension**: `logo.v2.png` → `logo.v2`. A name with no dot keeps its full name (and usually then fails the non-image check). `logo.png` and `logo.jpg` both give `logo`, so they are reported as a base-name clash.
- Messages follow the existing pre-flight style: name the file (`inline/<name>`) or the `cid:` ID. Content IDs and file names come from the template author, not recipient data (Phase 5 D-14). Never print addresses or data values.

### Dry-run layout
- **D-04:** Dry-run copies each inline image **once into `out/inline/`** and rewrites `cid:<id>` in the dry-run HTML to `inline/<file name>`. This mirrors the campaign layout and can never collide with a rendered mail file. Reruns overwrite, as D-15 of Phase 2 does. This is the accepted reading of CLI-17's "next to each rendered HTML file": the images sit in a subdirectory beside the HTML files, and the relative paths resolve from each HTML file.
- **D-05:** The rewritten `src` is the **percent-encoded** relative path (JDK URI encoding, no dependency), so any name that passes the content-ID rules opens in a browser.
- **D-06:** The rewrite touches only the files dry-run writes. The `Mail`/`MailMessage` objects keep `cid:`, and a real run sends `cid:` unchanged (CLI-17). The rewrite uses the same `cid:` grammar as the scan.

### OS junk in `inline/`
- **D-07:** `inline/` skips **dot-files only**, the same rule as `attachments/`. A `Thumbs.db` or `desktop.ini` fails pre-flight as a non-image file, with a message naming it, and the author deletes it. No hard-coded list of special names (Rule 0). This resolves the STATE.md Phase 02 concern for `inline/`. `attachments/` is unchanged.
- Like `attachments/`, only regular files count (subdirectories are ignored). Files are sorted by name.

### Example campaign
- **D-08:** Keep `attachments/` with a PDF and a PNG (spec §8.1 still holds). **Add a new tiny `inline/logo.png`** (a few hundred bytes, generated once, no metadata, checked in, no generator code in the build). **Rename `attachments/logo.png` to `attachments/sample.png`** so a reader does not confuse the two. Update `body.html` text and the tests that name it (`ExampleCampaignTest`, `GraphEndToEndTest`).
- **D-09:** `body.html` shows the logo **at the top of the body** with a plain `<p><img src="cid:logo" alt="morimail logo" width=".." height=".."></p>` above "Hello $$firstName$$", plus one sentence saying that the logo is inline and the PNG and PDF are attachments. `body.txt` stays unchanged.
- The dry run of the example campaign gives five rendered mails, each showing `inline/logo.png` (CLI-18, success criterion 4).

### Claude's Discretion
- How the dry run lists inline images on stdout, for example an `inline:` line next to the existing `attachments:` line with file name, content type, size and `cid:` ID.
- How the sample gets the resolved content type for the non-image check without its resolution disagreeing with the core's. Options include reusing what `build()` resolves, or a copied table kept in sync like `Campaign.PLACEHOLDER`. Do not widen the core's public API for this unless the planner finds no simpler way, and then flag it.
- Whether the `cid:` pattern is copied into the sample (as `PLACEHOLDER` is, with a comment that it must match the core) or reached some other way, within the rule that the core does not rewrite HTML (spec §10.3).
- Where the `inline/` list lives (likely a new `inlineImages` component on the `Campaign` record), the exact message wording, and the test layout (`CampaignTest`, `PreflightTest`, `DryRunTest`, `ExampleCampaignTest`, `SendRunTest` with `recording`).
- Tiny test images generated in tests (no metadata, nothing extra checked in beyond the example logo).

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Spec and planning
- `docs/spec.md` §8 (8.1-8.3): campaign layout, command, pre-flight and dry-run behaviour of the sample
- `docs/spec.md` §10.3: inline images, including the sample line (`inline/`, ID = file name without extension, dry-run rewrite only)
- `docs/spec.md` §11 / `CLAUDE.md`: Rule 0, no secrets, no real mail, `example.org` addresses only
- `.planning/ROADMAP.md`: Phase 6 success criteria and the phase gates
- `.planning/REQUIREMENTS.md`: CLI-15..18, Out of Scope (per-recipient inline images, rewriting `cid:` in sent mail)
- `.planning/phases/05-inline-images-in-core-and-graph/05-CONTEXT.md`: D-04..D-14 (cid grammar, content-ID rules, image check, messages)
- `.planning/milestones/v0.1.0-phases/02-serial-mail-sample/02-CONTEXT.md`: D-02, D-13..D-16 (pre-flight in dry-run, exit codes, output files)
- `.planning/STATE.md`: Blockers/Concerns, Phase 02 note on `Thumbs.db` (resolved by D-07)

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `Campaign.read` (`morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/Campaign.java`): it already lists `attachments/` (regular files, no dot-files, sorted) and collects problems. `inline/` follows the same pattern, and the template scan goes here.
- `Campaign.PLACEHOLDER`: precedent for copying a core pattern into the sample so both agree.
- `SendCommand` pre-flight loop (`SendCommand.java` about lines 155-177): builds one mail per row and adds `.attach(...)` per attachment. Add `.inline(file, id)` here. The `invalid` map reports the same `build()` message once.
- `SendCommand.writeDryRun` (about lines 246-281): writes `out/<n>-<safe email>.html/.subject.txt/.txt` and lists attachments. The image copy, the `cid:` rewrite of the written HTML and the inline listing go here.
- Core `Mail.Builder.inline(Path, String)` and the `CID_REFERENCE` pattern (`morimail/src/main/java/de/morisoft/morimail/Mail.java` about lines 46-48, 343).
- `RecordingTransport` (test-jar): records inline images with content ID, so tests can assert on them for sent mails.

### Established Patterns
- Pre-flight collects all problems into one list, prints them to stderr and exits 2.
- Problem messages name the file, line or column and never print secrets or data values.
- Few classes, no forwarding layers (Rule 0). The sample is package-private code.

### Integration Points
- `Campaign` record gains the inline file list (with IDs). `SendCommand` uses it for building mails and for dry-run.
- Example campaign files under `morimail-sample-serial/example-campaign/`. Tests that name `attachments/logo.png`: `ExampleCampaignTest` (lines ~77, ~123) and `GraphEndToEndTest` (line ~168).

</code_context>

<specifics>
## Specific Ideas

- Dry-run output for the example: `out/01-<addr>.html` … `out/05-<addr>.html` with `<img src="inline/logo.png" ...>`, and `out/inline/logo.png` once.
- Example body: logo at the top, then "Hello $$firstName$$", then a sentence that the logo is inline and the PNG and PDF are attachments.

</specifics>

<deferred>
## Deferred Ideas

- Skipping `Thumbs.db`/`desktop.ini` in `attachments/` (STATE.md Phase 02 concern): not changed. The owner chose one dot-file rule for both directories.
- Sample docs for `inline/` (`send --help`, README sample section): Phase 8 (DOCS-05).

</deferred>

---

*Phase: 06-inline-images-in-the-serial-mail-sample*
*Context gathered: 2026-11-23*
