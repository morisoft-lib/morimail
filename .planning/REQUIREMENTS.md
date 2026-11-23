# Requirements: morimail

**Defined:** 2026-11-22
**Milestone:** v0.2.0 Inline Images (spec §10.3, §10.4)
**Core Value:** A consumer can send one templated mail with attachments through Microsoft Graph as an Entra app, using only the JDK and Jackson, and get back a clear error category (transient / permanent / auth-required) that tells them what to do next.

## v0.2.0 Requirements

Requirements for this milestone. Each maps to one roadmap phase. IDs continue the v0.1.0 categories (`milestones/v0.1.0-REQUIREMENTS.md`); `INLN-01`..`04` replace the four deferred placeholders of the same IDs.

### Inline images in the core

- [x] **INLN-01**: Consumer can add an inline image with `Mail.using(..)...inline(Path image, String contentId)` and refer to it from the HTML as `<img src="cid:<contentId>">`; a blank content ID or one with a line break is rejected.
- [x] **INLN-02**: `build()` fails with `InvalidMailException` when the rendered HTML has a `cid:` reference with no matching inline image.
- [x] **INLN-03**: `build()` fails with `InvalidMailException` when an inline image is not referenced by any `cid:` in the rendered HTML, including a mail with inline images and no HTML body.
- [x] **INLN-04**: `build()` fails with `InvalidMailException` when two inline images in one mail share a content ID.
- [x] **INLN-05**: `build()` fails with `InvalidMailException` when an inline image's resolved content type is not an image type; inline images get the same file checks (exists, readable) and content-type resolution as regular attachments.
- [x] **INLN-06**: `RecordingTransport` records inline images with their content ID, so consumers and the sample can assert on them in tests.

### Graph transport

- [x] **GRAPH-10**: The Graph transport sends each inline image as a file attachment marked inline with its content ID, using the property names from the current Microsoft `fileAttachment` documentation (read before coding; differences from the spec noted in `docs/architecture.md`).
- [x] **GRAPH-11**: Inline images count toward the existing limits in `validate` (each file under 3 MiB, all attachments together under 3 MiB, estimated request at most 4 MiB), rejected before any token request.
- [x] **GRAPH-12**: The owner-run `live` test sends a mail with one inline image, so the owner can confirm it renders inline (not as a separate attachment).

### Serial-mail sample

- [x] **CLI-15**: A campaign may have an optional `inline/` directory; each file in it becomes an inline image in every mail, with the file name without extension as its content ID.
- [x] **CLI-16**: Pre-flight reports inline problems (a template `cid:` with no image, an image no template references, two files with the same base name, a non-image file) together with the other pre-flight findings, exit 2, nothing sent.
- [x] **CLI-17**: Dry-run writes the inline images next to each rendered HTML file and rewrites `cid:` references to relative paths in the dry-run output only; sent mails keep `cid:`.
- [x] **CLI-18**: The shipped example campaign has a small generated `inline/logo.png` referenced from its HTML template, and its dry-run shows the image.

### HTML image helper (`morimail-html`, spec §10.4)

- [x] **HTML-01**: A new module `morimail-html` (artifact `de.morisoft:morimail-html`, package `de.morisoft.morimail.html`) depends on `morimail`, has no other runtime dependency unless the owner approves one, and is in the release profile; the `morimail` jar is unchanged.
- [x] **HTML-02**: `HtmlImages` converts a `MailTemplate` (before rendering) into a template whose `<img src>` values are `cid:` references plus the matching inline `Attachment`s; `cid:`, `data:`, unknown schemes, `src` values with a `$$` placeholder, the subject and the text stay unchanged.
- [x] **HTML-03**: Local sources (relative path or `file:`) resolve only inside a caller-given base directory; a path outside it, a missing or unreadable file, or a non-image file fails with `InvalidMailException` naming the `src` as written.
- [x] **HTML-04**: Generated content IDs come from the file name without extension, pass the Phase 5 content-ID rules, get a numeric suffix on collision; the same source used twice yields one image.
- [x] **HTML-05**: `http(s)` sources are downloaded only when the caller passes a download directory (opt-in), with a timeout, a size cap and an image content-type check; tested offline against WireMock.
- [x] **HTML-06**: A converted template, rendered for several data records and sent to `recording`, passes `build()` and carries the inline images in every mail.
- [x] **HTML-07**: A storable form: `HtmlImages.toDataUris` turns local (and, with a work directory, remote) image sources into `data:image/...;base64,` URIs so the HTML alone carries its images; `embed` with a work directory turns `data:` image sources back into `cid:` references plus inline `Attachment`s (decoded into the work directory), with the same image-type check and 3 MiB cap (spec §10.4 "Storable form", owner request 2026-11-23).

### Docs and release readiness

- [x] **DOCS-05**: README shows an inline-image example with `.inline(...)` and `cid:`; `docs/architecture.md` and spec §6 record the Graph inline property names and the live-test status; the sample docs describe `inline/`.
- [ ] **DOCS-06**: `./mvnw verify` passes offline and `./mvnw -Prelease verify -Dgpg.skip -pl morimail,morimail-html -am` passes without warnings at `0.2.0-SNAPSHOT`, ready for the owner to release 0.2.0 from `docs/release.md`.
- [ ] **DOCS-07**: README shows an `HtmlImages` example (local images, remote opt-in) and the `morimail-html` coordinates; `docs/release.md` covers the second published jar.

## Future Requirements

Deferred. Tracked but not in this roadmap.

### Tech debt (v0.1.0 audit)

- Quoted local part not redacted; Entra messages can name app and tenant IDs; `isHeaderSafe` duplicates Graph's `requireToken`; `SerialMail` duplicates the Graph base URL; `TransportRegistry.load()` on every account build; settable timeouts and authority/`baseUrl` keys in `account.properties`. Owner's choice: not in v0.2.0.

### Later transports and features (spec §2)

- SMTP, SES, Gmail transports; delegated OAuth; upload session for attachments of 3 MiB or more; HTML and text as alternative parts (MIME).

## Out of Scope

| Feature | Reason |
|---------|--------|
| Inline images of 3 MiB or more | Same upload-session constraint as regular attachments (spec §6.2) |
| Non-image inline parts | Spec §10.3: only image content types |
| Generated content IDs or automatic `cid:` rewriting in the core library | The core stays as spec §10.3 says; the helper module `morimail-html` (spec §10.4, HTML-01..06) does this outside the core |
| Rewriting `cid:` in sent mail | Spec §10.3: rewrite in dry-run output only |
| Per-recipient inline images in the sample | Same as attachments: shared per campaign |
| Releasing, tagging or the live run | Done by the owner |

## Traceability

Which phases cover which requirements. Updated during roadmap creation.

| Requirement | Phase | Status |
|-------------|-------|--------|
| INLN-01 | Phase 5 | Complete |
| INLN-02 | Phase 5 | Complete |
| INLN-03 | Phase 5 | Complete |
| INLN-04 | Phase 5 | Complete |
| INLN-05 | Phase 5 | Complete |
| INLN-06 | Phase 5 | Complete |
| GRAPH-10 | Phase 5 | Complete |
| GRAPH-11 | Phase 5 | Complete |
| GRAPH-12 | Phase 5 | Complete |
| CLI-15 | Phase 6 | Complete |
| CLI-16 | Phase 6 | Complete |
| CLI-17 | Phase 6 | Complete |
| CLI-18 | Phase 6 | Complete |
| HTML-01 | Phase 7 | Complete |
| HTML-02 | Phase 7 | Complete |
| HTML-03 | Phase 7 | Complete |
| HTML-04 | Phase 7 | Complete |
| HTML-05 | Phase 7 | Complete |
| HTML-06 | Phase 7 | Complete |
| HTML-07 | Phase 7 | Complete |
| DOCS-05 | Phase 8 | Complete |
| DOCS-06 | Phase 8 | Pending |
| DOCS-07 | Phase 8 | Pending |

**Coverage:**
- v0.2.0 requirements: 23 total
- Mapped to phases: 23 (Phase 5: 9, Phase 6: 4, Phase 7: 7, Phase 8: 3)
- Unmapped: 0 ✓

---
*Requirements defined: 2026-11-22*
*Last updated: 2026-11-22 after adding Phase 7 (HTML image helper, spec §10.4)*
