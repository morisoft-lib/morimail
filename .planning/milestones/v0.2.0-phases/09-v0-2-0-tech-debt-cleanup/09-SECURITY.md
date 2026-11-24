---
phase: "09"
slug: "v0-2-0-tech-debt-cleanup"
status: verified
# threats_open = count of OPEN threats at or above workflow.security_block_on severity (the blocking gate)
threats_open: 0
asvs_level: 1
created: "2026-11-24"
---

# Phase 09 — Security

> Per-phase security contract: threat register, accepted risks, and audit trail.

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| recipient data to the rendered HTML | TemplateRenderer escapes only `& < > " '`, so control and format characters reach the `cid:` scan | recipient-controlled text |
| build() message to logs, journals and terminals | InvalidMailException messages are printed by the sample and logged by consumers | up to 64 code points of HTML-read text |
| campaign files and recipient data to the pre-flight report | row values decide the body size; the report goes to stderr | file names, byte counts, limits |
| the sample to Microsoft Graph | a send uses a token and an HTTP call; tests must never reach a real host | token, mail content |
| planning records to the owner and the milestone audit | disposition and summary files decide whether the milestone closes | review dispositions |
| frontmatter to gsd-tools | summaries and dispositions are parsed by tools | YAML frontmatter |
| this workspace to the public repository | sources, tests, .planning files and commits are public and permanent | everything committed |

---

## Threat Register

| Threat ID | Category | Component | Severity | Disposition | Mitigation | Status |
|-----------|----------|-----------|----------|-------------|------------|--------|
| T-09-01 | Tampering (log forging, terminal escapes) | Mail.Builder.checkReferences message `no inline image for cid:<id>` | high | mitigate | `shownId` in `morimail/src/main/java/de/morisoft/morimail/Mail.java` replaces ISO control, LINE_SEPARATOR, PARAGRAPH_SEPARATOR and FORMAT code points with `?` within the 64-code-point cut; `MailBuilderTest.controlCharactersInAReferenceAreNotEchoed` sends ESC, NEL, U+2028 and U+202E through the template path and asserts the message is `cid:a?b?c?d?e` | closed |
| T-09-02 | Information disclosure | the same message echoes up to 64 code points of HTML-read text | low | accept | see Accepted Risks AR-09-01 | closed |
| T-09-03 | Tampering (check bypass by drift) | the three CID_REFERENCE copies | medium | mitigate | `Mail.java`, `Campaign.java` and `HtmlImages.java` hold one distinct pattern line `(?i)(?<![\p{L}\p{N}])cid:([^"'\s)<>]*)`; `cidPatternAgreesWithTheCore` exists in both `CampaignTest` and `HtmlImagesTest` | closed |
| T-09-04 | Information disclosure | new tests, comments and the architecture row | low | mitigate | Added lines since 58ad024 hold no address outside example.com and example.org except the maintainer identity and the co-author trailer | closed |
| T-09-05 | Denial of service (half-sent campaign) | SendCommand pre-flight | medium | mitigate | `SendCommand` loops `mailer.validate` over every non-null built mail before the journal opens; `PreflightTest.laterRowThatGraphRejectsStopsPreflight` asserts exit 2, line 4 reported, no journal | closed |
| T-09-06 | Information disclosure | validate messages copied into pre-flight lines | low | accept | see Accepted Risks AR-09-02 | closed |
| T-09-07 | Spoofing / Tampering (a test sends real mail) | PreflightTest Graph account | high | mitigate | `PreflightTest.graphAccount` uses `baseUrl http://localhost:1/v1.0` and a counting token provider; both Graph tests assert `tokenCalls` is 0 and no journal exists | closed |
| T-09-08 | Information disclosure | new test fixtures | low | mitigate | PreflightTest fixtures use anna, ben and clara at example.org; no other address in added lines | closed |
| T-09-09 | Repudiation (a record claims a fix that is not there) | 05-REVIEW-DISPOSITION.md and 07-REVIEW-DISPOSITION.md | medium | mitigate | Code facts behind each `fixed` row hold: `shownId` present, one `<`-class pattern line in three files, `svg` in the core and helper tables, no decision-ID comment in `morimail/src/main`; 05 IN-02 scopes itself to the core and says the helper and sample comments stay; 07 IN-02 recorded `skipped` | closed |
| T-09-10 | Tampering (tool input) | 07-01-SUMMARY.md and 08-02-SUMMARY.md frontmatter | low | mitigate | `summary-extract` parses all eleven summaries of phases 05 to 08 | closed |
| T-09-11 | Information disclosure | records, Javadoc and commit text | low | mitigate | No address outside the allowed set in added lines; the hex strings of 16+ characters in added lines are git commit SHAs and a GSD `covered_digest` in planning frontmatter, not secrets; see advisory ADV-09-01 for one home path | closed |
| T-09-12 | Spoofing (wrong author identity in public history) | all phase commits | medium | mitigate | All 19 commits in `58ad024..HEAD` have author and committer `Stephan Wald <stephan@morisoft.de>` | closed |
| T-09-SC | Tampering | Maven dependencies and plugins | low | accept | see Accepted Risks AR-09-03 | closed |

*Status: open · closed · open — below high threshold (non-blocking)*
*Severity: critical > high > medium > low — only open threats at or above workflow.security_block_on count toward threats_open*
*Disposition: mitigate (implementation required) · accept (documented risk) · transfer (third-party)*

---

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|-------------|------|
| AR-09-01 | T-09-02 | Owner decision D-01 keeps the id in the message because it is the useful part; it is cut to 64 code points and cleaned, and the library forms no address or secret from it | owner (D-01, plan time) | 2026-11-24 |
| AR-09-02 | T-09-06 | GraphTransport.validate messages hold attachment file names from the campaign, byte counts and limits, never addresses or body text; only the number of rows reported changed | owner (09-02 plan) | 2026-11-24 |
| AR-09-03 | T-09-SC | No package, plugin or version is added or changed in phase 9; the build uses the pinned wrapper and the cached repository | owner (09-01..09-03 plans) | 2026-11-24 |

*Accepted risks do not resurface in future audit runs.*

---

## Advisories (non-blocking, outside the register)

| ID | Severity | Finding | Note |
|----|----------|---------|------|
| ADV-09-01 | low | `09-RESEARCH.md` (planning commit 8ee52be) contains a local tool path under `/home/coder/.claude-shared/...` | The planning commit is not covered by the task commit gates. The path is the generic workspace user and holds no personal data, secret or host name. Nine earlier `.planning` files carry the same kind of path, so this is pre-existing. The owner can replace these with `~/...` before the next release if wanted. |

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-11-24 | 13 | 13 | 0 | /gsd-secure-phase (orchestrator, L1 grep-depth; auditor skipped per short-circuit rule) |

---

## Sign-Off

- [x] All threats have a disposition (mitigate / accept / transfer)
- [x] Accepted risks documented in Accepted Risks Log
- [x] `threats_open: 0` confirmed
- [x] `status: verified` set in frontmatter

**Approval:** verified 2026-11-24
