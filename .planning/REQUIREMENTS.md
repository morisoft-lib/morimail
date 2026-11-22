# Requirements: morimail

**Defined:** 2026-11-22
**Milestone:** v0.2.0 Inline Images (spec §10.3)
**Core Value:** A consumer can send one templated mail with attachments through Microsoft Graph as an Entra app, using only the JDK and Jackson, and get back a clear error category (transient / permanent / auth-required) that tells them what to do next.

## v0.2.0 Requirements

Requirements for this milestone. Each maps to one roadmap phase. IDs continue the v0.1.0 categories (`milestones/v0.1.0-REQUIREMENTS.md`); `INLN-01`..`04` replace the four deferred placeholders of the same IDs.

### Inline images in the core

- [ ] **INLN-01**: Consumer can add an inline image with `Mail.using(..)...inline(Path image, String contentId)` and refer to it from the HTML as `<img src="cid:<contentId>">`; a blank content ID or one with a line break is rejected.
- [ ] **INLN-02**: `build()` fails with `InvalidMailException` when the rendered HTML has a `cid:` reference with no matching inline image.
- [ ] **INLN-03**: `build()` fails with `InvalidMailException` when an inline image is not referenced by any `cid:` in the rendered HTML, including a mail with inline images and no HTML body.
- [ ] **INLN-04**: `build()` fails with `InvalidMailException` when two inline images in one mail share a content ID.
- [ ] **INLN-05**: `build()` fails with `InvalidMailException` when an inline image's resolved content type is not an image type; inline images get the same file checks (exists, readable) and content-type resolution as regular attachments.
- [ ] **INLN-06**: `RecordingTransport` records inline images with their content ID, so consumers and the sample can assert on them in tests.

### Graph transport

- [ ] **GRAPH-10**: The Graph transport sends each inline image as a file attachment marked inline with its content ID, using the property names from the current Microsoft `fileAttachment` documentation (read before coding; differences from the spec noted in `docs/architecture.md`).
- [ ] **GRAPH-11**: Inline images count toward the existing limits in `validate` (each file under 3 MiB, all attachments together under 4 MiB), rejected before any token request.
- [ ] **GRAPH-12**: The owner-run `live` test sends a mail with one inline image, so the owner can confirm it renders inline (not as a separate attachment).

### Serial-mail sample

- [ ] **CLI-15**: A campaign may have an optional `inline/` directory; each file in it becomes an inline image in every mail, with the file name without extension as its content ID.
- [ ] **CLI-16**: Pre-flight reports inline problems (a template `cid:` with no image, an image no template references, two files with the same base name, a non-image file) together with the other pre-flight findings, exit 2, nothing sent.
- [ ] **CLI-17**: Dry-run writes the inline images next to each rendered HTML file and rewrites `cid:` references to relative paths in the dry-run output only; sent mails keep `cid:`.
- [ ] **CLI-18**: The shipped example campaign has a small generated `inline/logo.png` referenced from its HTML template, and its dry-run shows the image.

### Docs and release readiness

- [ ] **DOCS-05**: README shows an inline-image example with `.inline(...)` and `cid:`; `docs/architecture.md` and spec §6 record the Graph inline property names and the live-test status; the sample docs describe `inline/`.
- [ ] **DOCS-06**: `./mvnw verify` passes offline and `./mvnw -Prelease verify -Dgpg.skip -pl morimail -am` passes without warnings at `0.2.0-SNAPSHOT`, ready for the owner to release 0.2.0 from `docs/release.md`.

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
| Generated content IDs or automatic `cid:` rewriting in the library | Not in the spec; the template author names the ID (Rule 0) |
| Rewriting `cid:` in sent mail | Spec §10.3: rewrite in dry-run output only |
| Per-recipient inline images in the sample | Same as attachments: shared per campaign |
| Releasing, tagging or the live run | Done by the owner |

## Traceability

Which phases cover which requirements. Updated during roadmap creation.

| Requirement | Phase | Status |
|-------------|-------|--------|

**Coverage:**
- v0.2.0 requirements: 15 total
- Mapped to phases: 0
- Unmapped: 15 ⚠️

---
*Requirements defined: 2026-11-22*
*Last updated: 2026-11-22 after initial definition*
