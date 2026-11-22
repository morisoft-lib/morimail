# Phase 5: Inline Images in Core and Graph - Context

**Gathered:** 2026-11-22
**Status:** Ready for planning

<domain>
## Phase Boundary

The library learns inline images (spec §10.3). The builder gets `.inline(Path image, String contentId)`. `build()` checks `cid:` references in both directions, along with unique content IDs, image content types and the usual file checks. `recording` records the images. The Graph transport sends them as inline file attachments within the existing size limits. The owner-run `live` test sends one mail with one inline image. Requirements: INLN-01..06, GRAPH-10..12.

Not in this phase: the sample's `inline/` directory (Phase 6), docs and release readiness (Phase 7), and the HTML image helper `morimail-html` (new phase after Phase 5, see Deferred).

</domain>

<decisions>
## Implementation Decisions

### Data model (carried forward, not re-asked)
- **D-01:** An inline image is an `Attachment` with `contentId != null`, kept in the same `MailMessage.attachments` list (spec §5). No new record, no second list. `RecordingTransport` already records `MailMessage` and so holds inline images with their content IDs. INLN-06 needs at most a test and Javadoc, plus a helper only if a test truly needs one.
- **D-02:** No `validate` code for "a transport that cannot send inline images", because both in-repo transports handle them (STATE.md, Rule 0).
- **D-03:** The v0.1.0 rejection of a set `contentId` in `Mail.Builder.build()` (`Mail.java`, around line 412) is replaced by the checks below.

### What counts as a `cid:` reference
- **D-04:** Any `cid:` anywhere in the **rendered** HTML counts: `src="cid:x"`, CSS `url(cid:x)`, `background=`. A simple regex, no HTML parsing. A literal "cid:" in body text also counts, which is accepted.
  - **Amended 2026-11-22 (owner-approved in plan review):** two narrowings apply. A `cid:` directly preceded by a letter or digit is not a reference (lookbehind `(?<![\p{L}\p{N}])`, so prose like "acid:" does not count). A `cid:` with an empty ID (e.g. `href="cid:"`) is skipped. Every other literal `cid:`, in markup or body text, still counts.
- **D-05:** The prefix `cid:` matches in any case (`CID:`, `Cid:`). The ID after it is compared to `contentId` exactly and case-sensitively. No URL-decoding.
- **D-06:** A reference's ID runs until `"`, `'`, whitespace, `)`, `>`, or the end of input.
- **D-07:** Only the HTML body is scanned. A mail with inline images and no HTML body fails, because the images are unreferenced (INLN-03).

### Builder API and content ID rules
- **D-08:** Public API: `.inline(Path image, String contentId)` only, as in the spec. `.attach(Attachment)` with a non-null `contentId` is also an inline image and gets the same checks. That route covers a custom file name or content type, so there is no `.inline(path, id, contentType)` overload. — **Reversibility:** one-way — a published public method on `Mail.Builder` in 0.2.0 on Maven Central
- **D-09:** Content ID rules: reject blank, CR/LF, whitespace, and `"` `'` `(` `)` `<` `>`. These are the characters that end a reference (D-06) plus angle brackets, so any accepted ID can be matched. No other character rules.
- **D-10:** The image type check passes when the resolved content type starts with `image/` (case-insensitive). Resolution works as for regular attachments: the built-in table (png, jpg, jpeg, gif), then `Files.probeContentType`, then `application/octet-stream`, which fails the check. A caller-supplied type via `attach(Attachment)` is checked the same way.
- **D-11:** Inline images get the same exists/readable/filename checks as regular attachments (INLN-05).

### Error messages
- **D-12:** All inline problems are `InvalidMailException`s thrown from `build()`. `.inline()` rejects only `null` arguments, with `NullPointerException` (`Objects.requireNonNull`), as `.attach()` does.
- **D-13:** `build()` reports the **first** problem only, as all other `build()` checks do. No collecting.
- **D-14:** Messages name the content ID, for example "no inline image for cid:logo", "inline image logo is not referenced", "duplicate inline content ID logo". The template author chooses the content ID, as with placeholder names, so it is not recipient data. Messages still never echo addresses or data values.

### Size limits (Graph `validate`)
- **D-15:** Keep today's limits unchanged. Inline images count in the same loop as regular attachments: each file under 3 MiB, all files together under 3 MiB (spec §6.2), and the request estimate (base64 plus body plus allowance) at or under 4 MiB. Roadmap success criterion 4 and GRAPH-11 say "together ... 4 MiB". That wording mixes up the 3 MiB total with the 4 MiB request estimate. The roadmap and REQUIREMENTS.md wording was corrected on 2026-11-22. Do not change the code.
- **D-16:** Size-limit messages stay the same for inline images and regular attachments ("attachment logo.png has N bytes ..."). No new message variants.
- **D-17:** Rejection still happens in `validate`, before any token request or HTTP call (existing behaviour).

### Claude's Discretion
- The Graph JSON property names for inline (`isInline`, `contentId`, possibly `contentLocation`), and whether `contentId` takes angle brackets. Take them from the current Microsoft `fileAttachment` and `user: sendMail` pages before coding, and record any difference from the spec in `docs/architecture.md`. Regular attachments must not be marked inline. Whether to send `isInline: false` explicitly for them follows the docs.
- The live test: one new method in `LiveGraphTest` with a tiny PNG generated in the test (no metadata, nothing checked in), referenced as `cid:` in the HTML. Same environment-variable configuration as the existing methods. Claude never runs it.
- The exact regex, the exact message wording within D-14, and the order of checks in `build()`.
- Test layout (`MailBuilderTest`, `GraphRequestTest`, `GraphValidateTest`, `RecordingTransportTest`, `SendThroughRecordingTest`) and tiny test images generated in the build.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Spec and planning
- `docs/spec.md` §5, §5.1: records, builder and `build()` checks (`contentId` semantics)
- `docs/spec.md` §6.2: attachment limits (3 MiB per file and in total)
- `docs/spec.md` §10.3: inline images (binding scope for this milestone)
- `docs/spec.md` §11 / `CLAUDE.md`: Rule 0, no secrets, no real mail, read the Microsoft docs first
- `.planning/ROADMAP.md`: Phase 5 success criteria (criterion 4 to be corrected, D-15) and phase gates
- `.planning/REQUIREMENTS.md`: INLN-01..06, GRAPH-10..12 (GRAPH-11 wording to be corrected, D-15)
- `docs/architecture.md`: where deviations from the spec and the Graph inline property names are recorded

### External (read before coding against Graph)
- Microsoft Graph `fileAttachment` resource page: inline property names (unverified in the spec)
- Microsoft Graph `user: sendMail` page: inline attachment example in the JSON body

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `Mail.Builder.build()` (`morimail/src/main/java/de/morisoft/morimail/Mail.java`, about lines 327-450): the attachment loop already resolves the file name and content type and checks exists/readable. Inline images reuse it, and the `contentId` rejection (about line 412) becomes the new checks.
- `Mail.Builder.contentTypeOf` and the built-in extension table: image types come from here.
- `MailAddress.hasLineBreak`: reuse for the content ID line-break check.
- `GraphTransport.validate` (`morimail/src/main/java/de/morisoft/morimail/graph/GraphTransport.java`, about lines 85-125): it iterates `m.attachments()`, so inline images are counted with no change beyond what D-15 says.
- `GraphTransport` body builder (about lines 153-170): this is where `fileAttachment` nodes are built. Add the inline properties when `contentId != null`.
- `RecordingTransport` (`morimail/src/test/java/de/morisoft/morimail/test/RecordingTransport.java`): records `MailMessage` as is.
- `LiveGraphTest` (`morimail/src/test/java/de/morisoft/morimail/LiveGraphTest.java`): environment-variable setup and the `live` tag. Add one method.

### Established Patterns
- Every content check happens in `build()` and throws `InvalidMailException`. The builder methods only `requireNonNull`.
- Fail on the first problem, with messages that are fixed text plus the field or index.
- Public types state their thread safety in Javadoc, and doclint must stay clean (`PublicTypesDocumentThreadSafetyTest`).
- The core package imports only the JDK (`ImportRulesTest`).

### Integration Points
- `TemplateRenderer` output (rendered HTML) is the input to the `cid:` scan, after rendering and before `MailMessage` is built.
- Phase 6 (sample) builds on `.inline(...)` and `recording`. Phase 7 documents the Graph property names and the live result.
- The new `morimail-html` phase (Deferred) produces a `MailTemplate` plus `Attachment`s with `contentId` and relies on this phase's checks.

</code_context>

<specifics>
## Specific Ideas

- Target usage: `Mail.using(id)...template(t, data).inline(Path.of("logo.png"), "logo").build()` with `<img src="cid:logo">` in the HTML.

</specifics>

<deferred>
## Deferred Ideas

### HTML image helper `morimail-html`: now Phase 7 of v0.2.0, spec §10.4, HTML-01..06 (owner decision 2026-11-22)
The owner wants a helper that turns a mail source with ordinary image URLs into a body with `cid:` references plus a set of image files.
- **Packaging:** a new Maven sub-module `morimail-html` (its own published jar), package `de.morisoft.morimail.html`, with one final class (working name `HtmlImages`/`HtmlConverter`). This keeps the core jar's Jackson-only rule intact.
- **Input:** a `MailTemplate`, converted **once, before rendering**, not the rendered mail. This keeps recipient data out of the converter (no path or host injection through placeholders) and means one conversion per campaign. A `src` that contains a placeholder is left unchanged.
- **Output:** a rewritten `MailTemplate` plus a `List<Attachment>` with `contentId` set, fed into the normal builder. Phase 5's two-way checks apply.
- **Local images:** relative or `file:` paths, resolved only under a caller-given base directory.
- **Remote `http(s)://` images:** opt-in. Download into a temp directory (`Attachment` is `Path`-based), with timeout, size cap and image-type check.
- **Open for that phase's discussion:** a parser dependency (for example jsoup) needs the owner's explicit approval (Rule 0); the error categories for download failures; how content IDs are generated and how the same URL used twice is handled; and who cleans up the temp files.
- **Done 2026-11-22:** spec §10.4, ROADMAP.md Phase 7 (docs and release moved to Phase 8) and REQUIREMENTS.md HTML-01..06, DOCS-07 are in place. The release process (`docs/release.md`, `-pl` scoping, D-20) must cover a second published artifact. This replaces the REQUIREMENTS.md Out of Scope line "Generated content IDs or automatic `cid:` rewriting in the library" for the helper module only. The core still does no rewriting.

</deferred>

---

*Phase: 05-inline-images-in-core-and-graph*
*Context gathered: 2026-11-22*
