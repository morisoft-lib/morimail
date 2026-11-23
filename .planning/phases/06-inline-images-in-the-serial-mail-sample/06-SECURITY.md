---
phase: "06"
slug: "inline-images-in-the-serial-mail-sample"
status: verified
# threats_open = count of OPEN threats at or above workflow.security_block_on severity (the blocking gate)
threats_open: 0
asvs_level: 1
created: "2026-11-23"
---

# Phase 06 — Security

> Per-phase security contract: threat register, accepted risks, and audit trail.

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| campaign directory to the sample | File names in `inline/` and the `body.html` template come from the campaign author | file names, template text (author trust) |
| recipient data to rendered HTML and pre-flight messages | CSV values are rendered (HTML-escaped by the core) into the HTML the dry-run rewrite reads; they reach the report only through the core's backstop message | recipient data (may be personal) |
| sample to local files | The dry run writes copies and HTML files under `--out`, which the author opens in a browser | image copies, rendered HTML |
| repository to the public | The checked-in example logo and fixtures are public from the first commit | example logo, test fixtures |

---

## Threat Register

| Threat ID | Category | Component | Severity | Disposition | Mitigation | Status |
|-----------|----------|-----------|----------|-------------|------------|--------|
| T-06-01 | Tampering | `writeDryRun` copy into `out/inline/` | low | mitigate | Target is `outDir.resolve("inline").resolve(image.filename())` (`SendCommand.java:290-292`); the files come from `Campaign.listFiles` (`Campaign.java:275`), regular files listed directly in `inline/`, so the name holds no separator and is never `..`; mail files keep `safeName` (`SendCommand.java:246`); test `DryRunTest.sanitisesFileNamesAndStaysInsideOut` | closed |
| T-06-02 | Tampering | rewritten `src` in the dry-run HTML | low | mitigate | `new URI(null, null, "inline/" + filename, null).toASCIIString().replace("&", "&amp;")` (`SendCommand.java:295-296`); core content-ID rules reject quotes, angle brackets and whitespace; test `DryRunTest.inlineSrcIsPercentEncodedAndHtmlSafe` | closed |
| T-06-03 | Tampering | sent mail HTML | low | mitigate | Rewrite applies only to the string passed to `Files.writeString` (`SendCommand.java:308`); `Mail`/`MailMessage` unchanged; tests `SendRunTest.sentMailsCarryTheInlineImageAndKeepTheCidReference`, `SendRunTest.inlineImagesFollowTheAttachmentsInFileNameOrder` | closed |
| T-06-04 | Information disclosure | the `inline:` stdout line | low | accept | Prints author-controlled file names, types, sizes and content IDs only, like the existing `attachments:` line | closed |
| T-06-05 | Information disclosure | `checkInline` messages | low | mitigate | Messages name only `inline/<file>`, `body.html` and template IDs (`Campaign.java:196`, `247`, `253`); data-derived IDs only via the core backstop, cut at 64 code points (T-05-02); tests pin each message | closed |
| T-06-06 | Information disclosure | `example-campaign/inline/logo.png` | low | mitigate | 166 bytes, 96x48 RGB, generated from scratch; test `ExampleCampaignTest.inlineLogoIsTinyAndMatchesTheImgSize` | closed |
| T-06-07 | Spoofing | probe mails and tests | low | mitigate | Probes use `probe@example.org` and are only built (`Campaign.java:201`, `212`); no address outside `example.org`/`example.com` in the sample sources or example campaign | closed |
| T-06-08 | Denial of service | two probes per inline file | low | accept | `build()` checks metadata and content type without reading the image; the directory is the author's own | closed |
| T-06-SC | Tampering | Maven dependencies and plugins | low | accept | No dependency, plugin or POM change: `git diff 0ab315d HEAD` on `morimail/` and the three POMs shows only a one-line comment in `Mail.java` (ed29b34, review fix IN-01) | closed |

*Status: open · closed · open — below high threshold (non-blocking)*
*Severity: critical > high > medium > low — only open threats at or above workflow.security_block_on count toward threats_open*
*Disposition: mitigate (implementation required) · accept (documented risk) · transfer (third-party)*

---

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|-------------|------|
| AR-06-01 | T-06-04 | The `inline:` line shows only author-controlled metadata, never an address or data value | plan 06-01 threat model | 2026-11-23 |
| AR-06-02 | T-06-08 | Probes read metadata only; the campaign directory belongs to the author running the sample | plan 06-02 threat model | 2026-11-23 |
| AR-06-03 | T-06-SC | No dependency or plugin change in this phase | plans 06-01, 06-02 threat models | 2026-11-23 |

*Accepted risks do not resurface in future audit runs.*

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-11-23 | 9 | 9 | 0 | /gsd-secure-phase (L1 grep check, plan-time register) |

---

## Sign-Off

- [x] All threats have a disposition (mitigate / accept / transfer)
- [x] Accepted risks documented in Accepted Risks Log
- [x] `threats_open: 0` confirmed
- [x] `status: verified` set in frontmatter

**Approval:** verified 2026-11-23
