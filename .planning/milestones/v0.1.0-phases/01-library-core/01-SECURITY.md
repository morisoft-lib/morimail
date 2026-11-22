---
phase: "01"
slug: "library-core"
status: verified
# threats_open = count of OPEN threats at or above workflow.security_block_on severity (the blocking gate)
threats_open: 0
asvs_level: 1
created: "2026-11-21"
---

# Phase 01 — Security

> Per-phase security contract: threat register, accepted risks, and audit trail.

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| GitHub event -> CI workflow | Pushes and pull requests run the workflow with the repository token | Source, read-only token |
| Maven Central -> build | Plugins and dependencies are downloaded and executed at build time | Third-party code |
| working tree -> public repository | Everything committed becomes permanent public history | All tracked files |
| vulnerability reporter -> maintainer | Reports must reach the maintainer without being public | Vulnerability details (sensitive) |
| consumer code -> Mail.Builder | Addresses, display names, subject and attachment paths enter the core and later reach transport headers | Untrusted strings |
| campaign data -> TemplateRenderer | Data values are untrusted text inserted into HTML and subject | Untrusted strings |
| classpath -> TransportRegistry | Any jar with a services entry can register a TransportProvider that receives the account's TokenProvider | Code, auth objects |
| consumer threads -> Mailer | One Mailer instance is shared by concurrent senders | Mail objects |
| source comment -> published Javadoc jar | Javadoc text ends up in the published `-javadoc.jar` | Documentation text |

---

## Threat Register

| Threat ID | Category | Component | Severity | Disposition | Mitigation | Status |
|-----------|----------|-----------|----------|-------------|------------|--------|
| T-01-01 | Tampering | .github/workflows/ci.yml | high | mitigate | Actions pinned to commit SHAs (checkout 3d3c42e, setup-java d1374f8); `permissions: contents: read`; only push and pull_request triggers; no secret, deploy, publish or tag step | closed |
| T-01-02 | Tampering | pom.xml pluginManagement / dependencyManagement | medium | mitigate | All versions pinned once in the parent with Jackson and JUnit BOMs; no SNAPSHOT/LATEST/range; offline `-o verify` green on Temurin 17 (CI run 44912211934) | closed |
| T-01-03 | Information disclosure | tracked files | high | mitigate | No tracked key/keystore file, no PEM private-key header; only `example.org`/`example.com` and the maintainer address in tracked non-planning files | closed |
| T-01-04 | Spoofing | git commits | medium | mitigate | 66 commits authored and committed as the maintainer; root commit 8c9b082 is the documented exception D-02b, accepted by the owner (UAT test 2) | closed |
| T-01-05 | Information disclosure | vulnerability reports | medium | mitigate | SECURITY.md routes reports to GitHub private vulnerability reporting with an email fallback and asks for no public issue. The repository feature is **not enabled** (`gh api .../private-vulnerability-reporting` → `enabled: false`); the owner must enable it | open — below high threshold (non-blocking) |
| T-01-06 | Tampering | docs/spec.md move | low | mitigate | docs/spec.md present, no root spec.md left | closed |
| T-01-07 | Tampering | Mail.Builder from, to, subject | high | mitigate | `MailAddress.invalidReason` rejects `\r`/`\n`; `Mail.java:359` rejects a subject line break; pinned by MailBuilderTest | closed |
| T-01-08 | Information disclosure | InvalidMailException messages | medium | mitigate | MailBuilderTest asserts `message().doesNotContain(bad)` | closed |
| T-01-09 | Elevation of privilege | TransportRegistry ServiceLoader discovery | medium | accept | Superseded by T-01-16 (duplicate-id rule); classpath contents are the consumer's trust decision — see Accepted Risks | closed |
| T-01-10 | Information disclosure | Mailer / MailAccount messages | medium | mitigate | Messages carry ids only; MailAccountTest asserts `hasMessageNotContaining` the rejected defaultFrom | closed |
| T-01-11 | Tampering | TemplateRenderer html part | high | mitigate | `TemplateRenderer.java:82-86` escapes `& < > " '`; `$$!name$$` documented as trusted-only | closed |
| T-01-12 | Tampering | Mail.Builder addresses and subject | high | mitigate | `invalidReason` applied to every address field (`Mail.java:369, 383`); subject line breaks rejected / template values flattened to a space | closed |
| T-01-13 | Tampering | TemplateRenderer value insertion | medium | mitigate | Matcher used only to find placeholders; no `appendReplacement`; values appended literally and not re-scanned; 30 TemplateRendererTest tests | closed |
| T-01-14 | Information disclosure | InvalidMailException messages | medium | mitigate | Attachments named by `Path.getFileName()` only (`Mail.java:399`); tests assert value absent | closed |
| T-01-15 | Tampering | recipient handling | medium | mitigate | Only varargs `to/cc/bcc(String...)` and `(MailAddress...)` overloads; no two-string overload | closed |
| T-01-16 | Spoofing | TransportRegistry.of | high | mitigate | `putIfAbsent` + IllegalStateException on duplicate id, built-in flagged (`TransportRegistry.java:73-78`); TransportRegistryTest covers built-in and external duplicates | closed |
| T-01-17 | Denial of service | ServiceLoader iteration | low | mitigate | ServiceConfigurationError caught and rethrown as IllegalStateException with cause (`TransportRegistry.java:58-62`) | closed |
| T-01-18 | Information disclosure | account / registry messages | low | mitigate | Messages name ids and provider class names only; MailAccountTest asserts absence of the rejected value | closed |
| T-01-19 | Tampering | Mailer.send | medium | mitigate | One transport send per call; MailerTest asserts `attempts()` counts | closed |
| T-01-20 | Tampering | shared Mailer / RecordingTransport state | low | mitigate | Immutable Mailer/MailAccount; RecordingTransport synchronized; MailerConcurrencyTest 8 threads × 400 sends | closed |
| T-01-21 | Information disclosure | .review-diagnostics/ | medium | mitigate | `.gitignore:36` `.review-diagnostics/` (verified with `git check-ignore`) | closed |
| T-01-22 | Tampering | core dependency allowlist | medium | mitigate | ImportRulesTest allows only `java.` names in the core | closed |
| T-01-23 | Information disclosure | registry wrapper message | low | mitigate | Fixed wrapper text, provider error kept as cause only | closed |
| T-01-24 | Tampering | release profile Javadoc gate | medium | mitigate | `doclint all` and `failOnWarnings true` unchanged (`pom.xml:200-201`); fix 5109854 touches MailMessage.java only | closed |
| T-01-25 | Information disclosure | MailMessage Javadoc text | low | mitigate | The nine @param lines match the record header; no address or host | closed |
| T-01-26 | Repudiation | fix commit and CI evidence | low | mitigate | 5109854 authored/committed by the maintainer; no tags; Temurin 17 run 44912211934 green | closed |
| T-01-SC | Tampering | Maven Central plugins and dependencies | high | mitigate | Library runtime dependency is jackson-databind only (test: junit-jupiter, assertj-core); all from the spec allowlist and pinned in the parent | closed |

*Status: open · closed · open — below high threshold (non-blocking)*
*Severity: critical > high > medium > low — only open threats at or above workflow.security_block_on count toward threats_open*
*Disposition: mitigate (implementation required) · accept (documented risk) · transfer (third-party)*

---

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|-------------|------|
| AR-01-01 | T-01-09 | ServiceLoader instantiates every provider on the classpath before its id is read; which jars are on the classpath is the consumer's trust decision. The duplicate-id rule (T-01-16) gives configuration integrity, not a sandbox | plan 01-04 / 01-06 threat model | 2026-11-21 |

*Accepted risks do not resurface in future audit runs.*

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-11-21 | 27 | 26 | 1 (T-01-05, medium, non-blocking) | /gsd-secure-phase 01 (orchestrator, L1 grep-depth) |

---

## Sign-Off

- [x] All threats have a disposition (mitigate / accept / transfer)
- [x] Accepted risks documented in Accepted Risks Log
- [x] `threats_open: 0` confirmed
- [x] `status: verified` set in frontmatter

**Approval:** verified 2026-11-21

**Owner action (non-blocking):** enable GitHub private vulnerability reporting (repository Settings → Code security → Private vulnerability reporting), so the route SECURITY.md promises works. Until then reports can only use the email fallback.
