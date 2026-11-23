# Roadmap: morimail

## Milestones

- ✅ **v0.1.0 MVP**: Phases 1-4, shipped 2026-11-22. Full details are in [milestones/v0.1.0-ROADMAP.md](milestones/v0.1.0-ROADMAP.md).
- 🚧 **v0.2.0 Inline Images**: Phases 5-8, in progress (spec §10.3, §10.4).

## Overview

v0.2.0 adds inline images (spec §10.3) and the HTML image helper (spec §10.4) in four slices. Phase 5 does the library work. The builder gets `.inline(path, contentId)`. `build()` checks the `cid:` references in both directions, and `recording` records the images. The Graph transport sends them as inline file attachments within the existing size limits. Phase 5 also ships the owner's `live` test with one inline image. Phase 6 adds the `inline/` campaign directory to the serial-mail sample, with pre-flight checks and the dry-run rewrite. It runs against `recording` and does not wait for the owner's live run. Phase 7 adds the helper module `morimail-html`: it turns ordinary image URLs in a template into `cid:` references plus inline images, once per template and before rendering. Phase 8 records the Graph property names and the owner's live result in the docs. It also confirms the release build for both published jars, so the owner can release 0.2.0.

## Phases

**Phase Numbering:**
- Integer phases (5, 6, 7, 8): Planned milestone work, continuing from v0.1.0
- Decimal phases (for example 5.1): Urgent insertions (marked with INSERTED), such as a Graph correction after the owner's live inline test

Decimal phases appear between their surrounding integers in numeric order.

<details>
<summary>✅ v0.1.0 MVP (Phases 1-4), shipped 2026-11-22</summary>

- [x] Phase 1: Library Core (7/7 plans), completed 2026-11-21
- [x] Phase 2: Serial-Mail Sample (4/4 plans), completed 2026-11-22
- [x] Phase 3: Graph and Entra (6/6 plans), completed 2026-11-22
- [x] Phase 4: Docs and Release Readiness (5/5 plans), completed 2026-11-22

</details>

### 🚧 v0.2.0 Inline Images (In Progress)

**Milestone Goal:** A consumer can embed images in a templated HTML mail with `cid:` references and send it through Graph with the images shown inline (spec §10.3).

- [x] **Phase 5: Inline Images in Core and Graph** - `.inline(path, contentId)` with two-way `cid:` checks in `build()`, recorded by `recording`, and sent by Graph as inline file attachments within the 3 MiB / 4 MiB limits. Ends with the owner's live-test checkpoint. (completed 2026-11-23)
- [x] **Phase 6: Inline Images in the Serial-Mail Sample** - Optional `inline/` campaign directory, pre-flight inline checks, dry-run with images and relative paths, example campaign with a logo (completed 2026-11-23)
- [ ] **Phase 7: HTML Image Helper Module** - New module `morimail-html` with `HtmlImages`: rewrites `<img src>` (local under a base directory, `http(s)` opt-in) in a template to `cid:` and returns the inline images
- [ ] **Phase 8: Docs and Release Readiness** - README inline and helper examples, Graph inline property names and live status in spec §6 and `docs/architecture.md`, sample docs for `inline/`, release build green for `morimail` and `morimail-html` at `0.2.0-SNAPSHOT`

> **Owner checkpoint after Phase 5 (live inline test).** Once Phase 5 is verified and committed, report to the owner. Claude does not run the live test and sends no real mail. The owner runs the `live` test with `-Plive` and checks that the image shows inline in a mail client and not as a separate attachment. Phases 6 and 7 do not wait for this. Phase 8 needs the result. If the owner has not run the test by then, Phase 8 asks the owner whether to record it as "not yet confirmed live". A Graph correction found by the live run becomes an inserted Phase 5.1.

## Phase Gates

These apply to every phase. They come from the binding spec (§10.2, §11, §13.4) and carry over from v0.1.0.

- A phase is done only when `./mvnw verify` is green with no network access and the work is committed. Author and committer of every commit is Stephan Wald <stephan@morisoft.de>. Workspace `GIT_*` variables override local config, so export the identity before each commit.
- Javadoc stays doclint-clean, and every new or changed public type states its thread safety. CI's `./mvnw -Prelease verify -Dgpg.skip -pl morimail -am` stays green (D-20: release builds are scoped, and the test-jar stays reactor-only). Only JDK 21 is installed locally, so plan an early CI round-trip on Temurin 17 for each phase that touches Javadoc or runtime-sensitive code (v0.1.0 lesson 1).
- No file, fixture, test image or commit contains a secret, tenant ID, real address or internal host. Use only `example.com`/`example.org` and zero GUIDs. Generate test images in the build or keep them tiny, with no embedded metadata.
- Never send real mail, publish, deploy or tag. The owner runs the live test and the release.
- Rule 0: only the two extension points, no new dependency (ask first), no option or hook spec §10.3 does not ask for. Failure messages hold fixed texts. They never echo data values or addresses.
- Before coding against Graph, read the current Microsoft `fileAttachment` and `user: sendMail` pages. Where they differ from the spec, follow the docs and note the difference in `docs/architecture.md`.

## Phase Details

### Phase 5: Inline Images in Core and Graph
**Goal**: A Java developer can build a templated HTML mail with `cid:` images and send it through the Graph transport, with each image attached inline under its content ID.
**Depends on**: Phase 4 (v0.1.0 shipped)
**Requirements**: INLN-01, INLN-02, INLN-03, INLN-04, INLN-05, INLN-06, GRAPH-10, GRAPH-11, GRAPH-12
**Success Criteria** (what must be TRUE):
  1. A developer can build `Mail.using(id)...template(t, data).inline(Path.of("logo.png"), "logo").build()` with `<img src="cid:logo">` in the HTML. Sent through `Mailer` to `recording`, the recorded message shows the inline image with its content ID, file name and content type, apart from the regular attachments. A blank content ID or one with a line break is rejected. The v0.1.0 rule that rejects any set `contentId` is replaced by these checks.
  2. `build()` raises one `InvalidMailException` that names the problem for each of these cases:
     - a `cid:` reference in the rendered HTML with no matching inline image
     - an inline image that no `cid:` references, also when the mail has no HTML body
     - two inline images with the same content ID
     - an inline file that is missing or unreadable, or whose resolved content type is not an image type
  3. Against WireMock, the Graph transport sends each inline image as a base64 file attachment marked inline with its content ID. The property names come from the current Microsoft `fileAttachment` page, and any difference from the spec is noted in `docs/architecture.md`. Regular attachments in the same mail are not marked inline.
  4. `validate` rejects a mail when an inline image is 3 MiB or more, when inline images and regular attachments together reach 3 MiB, or when the estimated request exceeds 4 MiB (the v0.1.0 limits, unchanged; 05-CONTEXT D-15). The message names the file or the limit, and the rejection comes before any token request or HTTP call.
  5. The `live` test (tag `live`, run with `-Plive`, configured through environment variables) sends one mail with one inline image. The default offline `./mvnw verify` excludes it and stays green. Claude does not run it.
**Plans:** 2/2 plans complete

Plans:
**Wave 1**
- [x] 05-01-PLAN.md — Tracer: one inline image end to end through Graph, then the `build()` checks (INLN-01..06, GRAPH-10 code)

**Wave 2** *(blocked on Wave 1 completion)*
- [x] 05-02-PLAN.md — Inline images in the Graph size limits, the owner's live test, the Graph notes in `docs/architecture.md`, phase gate (GRAPH-10 docs, GRAPH-11, GRAPH-12)

**UI hint**: no

### Phase 6: Inline Images in the Serial-Mail Sample
**Goal**: A campaign author can put images into an optional `inline/` directory. The dry run shows them in the rendered mails, and every sent mail carries them inline.
**Depends on**: Phase 5 (not the owner's live inline test)
**Requirements**: CLI-15, CLI-16, CLI-17, CLI-18
**Success Criteria** (what must be TRUE):
  1. With an `inline/` directory, every mail goes out (checked with `recording`) with each file there as an inline image. Its content ID is the file name without the extension. A campaign without `inline/` behaves exactly as in v0.1.0.
  2. Pre-flight reports each inline problem in the same report as the other findings, exits 2, sends nothing and writes no journal. The problems are:
     - a template `cid:` with no image
     - an image that no template references
     - two files with the same base name
     - a non-image file
  3. Dry-run writes the inline images next to the rendered HTML files, and the HTML points at them by relative path, so a browser shows the images. The `cid:` rewrite happens only in the dry-run output. Mails sent in a real run keep their `cid:` references.
  4. A dry run of the shipped example campaign produces five rendered mails. Each one shows the small generated `inline/logo.png` that the HTML template references.
**Plans:** 2/2 plans complete

Plans:
**Wave 1**
- [x] 06-01-PLAN.md — Tracer: inline/ images in every mail and in the dry run (out/inline/ copies, percent-encoded relative paths, sent mails keep cid:), then the dry-run and send edges (CLI-15, CLI-17)

**Wave 2** *(blocked on Wave 1 completion)*
- [x] 06-02-PLAN.md — Tracer: inline problems in the pre-flight report, then the example campaign's generated inline logo and the phase gate (CLI-15, CLI-16, CLI-18)

**UI hint**: no

### Phase 7: HTML Image Helper Module
**Goal**: A template author can write ordinary image URLs in an HTML template, and one call to `HtmlImages` turns them into `cid:` references plus inline images that pass the Phase 5 checks.
**Depends on**: Phase 5 (not the owner's live inline test, not Phase 6)
**Requirements**: HTML-01, HTML-02, HTML-03, HTML-04, HTML-05, HTML-06
**Success Criteria** (what must be TRUE):
  1. The reactor has a new module `morimail-html` (artifact `de.morisoft:morimail-html`, package `de.morisoft.morimail.html`). It depends on `morimail`, uses no other runtime dependency unless the owner approved one, and is part of the release profile. The `morimail` jar is unchanged.
  2. `HtmlImages` takes a `MailTemplate` and a base directory. It returns a template whose `<img src>` values for local files are `cid:` references, plus one `Attachment` with `contentId` per distinct image. `cid:`, `data:`, unknown schemes and any `src` with a `$$` placeholder stay unchanged. Subject and text are untouched.
  3. A local `src` that resolves outside the base directory, or a missing, unreadable or non-image file, fails with `InvalidMailException` naming the `src` as written.
  4. Generated content IDs follow the file name without extension, pass the Phase 5 content-ID rules, get a numeric suffix on collision, and the same source used twice yields one image.
  5. `http(s)` sources stay unchanged by default. When the caller passes a download directory, they are downloaded there (tested against WireMock, offline) with a timeout, a size cap and an image content-type check, and a failure maps to the category decided in this phase's discussion.
  6. End to end: a template converted once, rendered for two data records and sent through `Mailer` to `recording`, gives two mails that each carry the inline images and pass `build()`.
**Plans**: TBD
**UI hint**: no

### Phase 8: Docs and Release Readiness
**Goal**: A developer reading the README and docs can use inline images, and the build is ready for the owner to release 0.2.0.
**Depends on**: Phases 6 and 7, and the owner's live inline result from the Phase 5 checkpoint (or the owner's explicit choice to record it as not yet confirmed)
**Requirements**: DOCS-05, DOCS-06, DOCS-07
**Success Criteria** (what must be TRUE):
  1. The README shows a copyable inline-image example that uses `.inline(...)` and `<img src="cid:...">`.
  2. Spec §6 and `docs/architecture.md` name the Graph inline property names in use and give the live-test status for inline images, as the owner reported it.
  3. The sample's documentation (`send --help` and the README's sample section) describes the `inline/` directory, the content-ID rule and the dry-run rewrite.
  4. The README shows a copyable `HtmlImages` example (local images, and the opt-in for remote ones) and the `morimail-html` coordinates.
  5. `./mvnw verify` passes offline, and the scoped release build (`./mvnw -Prelease verify -Dgpg.skip -pl morimail,morimail-html -am`) passes without warnings at `0.2.0-SNAPSHOT`. `docs/release.md` covers the second published jar, so the owner can release 0.2.0 by following it.
**Plans**: TBD
**UI hint**: no

## Coverage

| Phase | Requirements | Count |
|-------|--------------|-------|
| 5. Inline Images in Core and Graph | INLN-01..06, GRAPH-10..12 | 9 |
| 6. Inline Images in the Serial-Mail Sample | CLI-15..18 | 4 |
| 7. HTML Image Helper Module | HTML-01..06 | 6 |
| 8. Docs and Release Readiness | DOCS-05..07 | 3 |

Mapped: 22/22 v0.2.0 requirements. No orphans, no duplicates.

Spec §10.3 also says "a transport that cannot send inline images rejects them in `validate`". No requirement covers this line. Both transports in this repository handle inline images (`graph` sends them, `recording` records them), so no code is needed under Rule 0. It is a rule for external transports.

## Progress

**Execution Order:**
Phases execute in numeric order: 5 → (owner live checkpoint, non-blocking for 6 and 7) → 6 → 7 → 8

| Phase | Milestone | Plans Complete | Status | Completed |
|-------|-----------|----------------|--------|-----------|
| 1. Library Core | v0.1.0 | 7/7 | Complete | 2026-11-21 |
| 2. Serial-Mail Sample | v0.1.0 | 4/4 | Complete | 2026-11-22 |
| 3. Graph and Entra | v0.1.0 | 6/6 | Complete | 2026-11-22 |
| 4. Docs and Release Readiness | v0.1.0 | 5/5 | Complete | 2026-11-22 |
| 5. Inline Images in Core and Graph | v0.2.0 | 2/2 | Complete    | 2026-11-23 |
| 6. Inline Images in the Serial-Mail Sample | v0.2.0 | 2/2 | Complete    | 2026-11-23 |
| 7. HTML Image Helper Module | v0.2.0 | 0/TBD | Not started | - |
| 8. Docs and Release Readiness | v0.2.0 | 0/TBD | Not started | - |
