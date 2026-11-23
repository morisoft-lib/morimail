---
phase: "07"
slug: "html-image-helper-module"
status: verified
# threats_open = count of OPEN threats at or above workflow.security_block_on severity (the blocking gate)
threats_open: 0
asvs_level: 1
created: "2026-11-23"
---

# Phase 07 — Security

> Per-phase security contract: threat register, accepted risks, and audit trail.

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| template author to the helper | The HTML template and its `img src` values come from the template author; recipient data never reaches the helper (template time, `$$` sources unchanged) | template text, image sources (author trust) |
| helper to the local file system | A local src opens a file; only files inside the caller's base directory may become attachments | local image files |
| helper to remote servers | With the work-directory opt-in, template URLs are fetched, possibly after redirects | URLs, response status, headers, body |
| remote content to the mail and the work directory | Downloaded bytes and the Content-Type become a file in the work directory and an Attachment | image bytes, media type |
| consumer's store to the helper | Stored HTML comes back with `data:` sources that are decoded into the work directory | base64 payloads |
| helper to the stored HTML | `toDataUris` writes types and payloads into attribute values of HTML that is stored and rendered later | `data:` URIs |
| morimail-html to morimail / published POM to consumers | The helper depends on the core, never the reverse; its POM brings `morimail` and Jackson onto the consumer's classpath | dependency graph |

---

## Threat Register

| Threat ID | Category | Component | Severity | Disposition | Mitigation | Status |
|-----------|----------|-----------|----------|-------------|------------|--------|
| T-07-01 | Information disclosure | `resolveLocal` path traversal | high | mitigate | `toRealPath()` of base and file, element-wise `Path.startsWith`, regular readable file only (`HtmlImages.java:611`, `644-652`); test `HtmlImagesTest.sourcesOutsideTheBaseDirectoryFail` | closed |
| T-07-02 | Information disclosure | non-image file renamed `.png` in the base directory | low | accept | The base directory is the boundary; type check by name and platform detection like the core | closed |
| T-07-03 | Elevation of privilege | dependency direction | low | mitigate | Core never references the helper; tests `ModuleRulesTest.morimailDoesNotReferenceTheHelper`, `ModuleRulesTest.mainImportsOnlyTheJdkAndTheCore`; core tree and jar gates (07-04) | closed |
| T-07-04 | Denial of service | `IMG_SRC` on hostile or huge templates | medium | mitigate | Possessive quantifiers, `<` excluded from attribute names (`HtmlImages.java:121-124`); test `HtmlImagesTest.hostileMarkupStaysFast` | closed |
| T-07-05 | Information disclosure | release build publishing the test-jar | low | mitigate | `<attach>false</attach>` kept in `morimail/pom.xml:79` | closed |
| T-07-06 | Information disclosure | failure messages (local sources) | low | mitigate | Fixed texts naming the src as written; no file content, no recipient data | closed |
| T-07-07 | Information disclosure | `download` SSRF | medium | mitigate | Downloads only with the work-directory opt-in; `$$` sources never fetched; template author trusted (class Javadoc); test `HtmlImagesDownloadTest.withoutAWorkDirectoryNothingIsDownloaded` | closed |
| T-07-08 | Denial of service | `download` huge, endless or slow bodies and headers | medium | mitigate | `readNBytes((int) MAX_IMAGE_BYTES)` with `>=` refusal (`HtmlImages.java:578-590`), 10 s connect and 30 s request budget (`91-103`); tests `sizeCapIsThreeMebibytes`, `slowHeadersTimeOut`, `stalledBodyTimesOut`. Residual: JDK re-arms the header timeout per redirect hop (owner review) | closed |
| T-07-09 | Tampering | Content-Type into the Attachment type | medium | mitigate | `IMAGE_TYPE` full match (`HtmlImages.java:130`); test `HtmlImagesDownloadTest.contentTypeMustBeAnImage` | closed |
| T-07-10 | Tampering | decoded URL segment as file name | low | mitigate | `fileName` falls back to `ID.ext` for blank names, control characters or separators (`HtmlImages.java:687-691`); test `fileNamesComeFromTheLastUrlSegment` | closed |
| T-07-11 | Tampering | redirect downgrade https to http | low | mitigate | `HttpClient.Redirect.NORMAL` (`HtmlImages.java:104`) never follows https to http | closed |
| T-07-12 | Information disclosure | logs and messages for downloads | low | mitigate | One DEBUG record with scheme, host, port and path only (`HtmlImages.java:511-513`); status only, never bodies; tests `downloadIsLoggedAtDebugOnly`, `statusDecidesTheCategory` | closed |
| T-07-13 | Denial of service | `decodeData` huge payload | medium | mitigate | Length check before decoding and `>=` check after (`HtmlImages.java:456-466`); strict decoder; test `HtmlImagesDataUriTest.decodedCapIsThreeMebibytes` | closed |
| T-07-14 | Tampering | `data:` media type into the Attachment | medium | mitigate | `IMAGE_TYPE` full match, `base64` required; test `HtmlImagesDataUriTest.onlyBase64ImageDataIsDecoded` | closed |
| T-07-15 | Information disclosure | failure messages for `data:` sources | low | mitigate | Messages name position and fixed reason, never the payload (`HtmlImages.java:440`); test asserts no payload text | closed |
| T-07-16 | Tampering | `toDataUris` output in stored HTML | low | mitigate | Replacement is `data:` + matched `IMAGE_TYPE` + `;base64,` + base64 alphabet (`HtmlImages.java:437`), so it cannot close the attribute or add `$$` | closed |
| T-07-17 | Tampering | decoded files in the work directory | low | mitigate | `Files.createTempFile` unique names (`HtmlImages.java:599`); nothing overwritten or deleted; tests `failedDecodeKeepsEarlierFiles`, `toDataUrisDownloadsWithAWorkDirectoryAndWritesNothingThere` | closed |
| T-07-18 | Tampering | transitive `morimail` and Jackson for consumers | low | mitigate | Helper names only `morimail` at `${project.version}`, no version, optional or exclusions elements; runtime-classpath gate pins the exact set (07-04) | closed |
| T-07-SC | Tampering | Maven dependencies and plugins | low | accept | No new package or plugin in any plan; all versions pinned in the parent POM | closed |

*Status: open · closed · open — below high threshold (non-blocking)*
*Severity: critical > high > medium > low — only open threats at or above workflow.security_block_on count toward threats_open*
*Disposition: mitigate (implementation required) · accept (documented risk) · transfer (third-party)*

---

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|-------------|------|
| AR-07-01 | T-07-02 | The base directory is the boundary; a renamed file inside it belongs to the template author | plan 07-01 threat model | 2026-11-23 |
| AR-07-02 | T-07-SC | No dependency or plugin added in this phase | plans 07-01 to 07-04 threat models | 2026-11-23 |

*Accepted risks do not resurface in future audit runs.*

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-11-23 | 19 | 19 | 0 | /gsd-secure-phase (L1 grep check, plan-time register) |

---

## Sign-Off

- [x] All threats have a disposition (mitigate / accept / transfer)
- [x] Accepted risks documented in Accepted Risks Log
- [x] `threats_open: 0` confirmed
- [x] `status: verified` set in frontmatter

**Approval:** verified 2026-11-23
