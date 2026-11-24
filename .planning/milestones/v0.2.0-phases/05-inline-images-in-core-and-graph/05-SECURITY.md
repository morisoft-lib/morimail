---
phase: "05"
slug: "inline-images-in-core-and-graph"
status: verified
# threats_open = count of OPEN threats at or above workflow.security_block_on severity (the blocking gate)
threats_open: 0
asvs_level: 1
created: "2026-11-23"
---

# Phase 05 — Security

> Per-phase security contract: threat register, accepted risks, and audit trail.

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| caller code to `Mail.Builder` | Content ID and image path come from the consumer's code, not from recipients | content ID, file path (caller trust) |
| template data to rendered HTML | Recipient data is rendered into the HTML that the `cid:` scan reads | recipient data (may be personal) |
| core to Graph JSON | Content ID and base64 image leave the process in the `sendMail` body | content ID, image bytes |
| live test to owner's console and tenant | Owner-run test talks to a real tenant and prints to the console | exception class and message only |
| repository to the public | Every file and commit is public | test code, fixtures, docs |

---

## Threat Register

| Threat ID | Category | Component | Severity | Disposition | Mitigation | Status |
|-----------|----------|-----------|----------|-------------|------------|--------|
| T-05-01 | Tampering | `Mail.Builder.build()` content ID check | medium | mitigate | `Mail.contentIdProblem` (`Mail.java:491`) rejects blank, line breaks, any `Character.isWhitespace`, `"` `'` `(` `)` `<` `>`; tests `contentIdWithALineBreakIsRejected`, `contentIdWithAReferenceEndingCharacterIsRejected` in `MailBuilderTest` | closed |
| T-05-02 | Information disclosure | `checkReferences` failure message | low | mitigate | `Mail.checkReferences` (`Mail.java:521`) cuts the HTML-derived ID at 64 code points; shape messages never echo the value; test `longReferenceIsCutInTheMessage` | closed |
| T-05-03 | Tampering | template data adding `cid:` references | low | accept | Unknown ID fails `build()` for that mail only and never reads a file; renderer escapes HTML values | closed |
| T-05-04 | Information disclosure | inline image path | low | accept | Path comes from caller code, same trust as `attach(Path)`; base-directory rule belongs to `morimail-html` (Phase 7) | closed |
| T-05-05 | Denial of service | `GraphTransport.validate` with inline images | low | mitigate | 3 MiB per-file / 3 MiB total / 4 MiB estimate rules count inline images; `GraphValidateTest.mailerRejectsAnOversizedInlineImageBeforeAnyTokenOrRequest` asserts zero token calls and zero requests | closed |
| T-05-06 | Information disclosure | `LiveGraphTest.sendsOneMailWithAnInlineImage` output | low | mitigate | Prints only flow and `describe(e)` = exception simple name + message (`LiveGraphTest.java:121`); no address, token or body | closed |
| T-05-07 | Information disclosure | test image and fixtures in public repo | low | mitigate | PNG generated at run time in `@TempDir`; `git ls-files morimail` lists no image; test addresses use example.com / example.org only | closed |
| T-05-SC | Tampering | Maven dependencies and plugins | low | accept | No POM change since 7562a6d (`git diff 7562a6d HEAD` on the three POMs is empty); `ImageIO`/`BufferedImage` are JDK test-only | closed |

*Status: open · closed · open — below high threshold (non-blocking)*
*Severity: critical > high > medium > low — only open threats at or above workflow.security_block_on count toward threats_open*
*Disposition: mitigate (implementation required) · accept (documented risk) · transfer (third-party)*

---

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|-------------|------|
| AR-05-01 | T-05-03 | Data-added `cid:` references can only fail `build()` for one mail; no file read, no HTML rewrite | plan 05-01 threat model | 2026-11-23 |
| AR-05-02 | T-05-04 | Image path has caller-code trust, like `attach(Path)`; template-derived paths are Phase 7 scope | plan 05-01 threat model | 2026-11-23 |
| AR-05-03 | T-05-SC | No dependency or plugin change in this phase | plans 05-01, 05-02 threat models | 2026-11-23 |

*Accepted risks do not resurface in future audit runs.*

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-11-23 | 8 | 8 | 0 | /gsd-secure-phase (L1 grep-depth, auditor skipped by short-circuit rule) |

---

## Sign-Off

- [x] All threats have a disposition (mitigate / accept / transfer)
- [x] Accepted risks documented in Accepted Risks Log
- [x] `threats_open: 0` confirmed
- [x] `status: verified` set in frontmatter

**Approval:** verified 2026-11-23
