---
phase: "08"
slug: "docs-and-release-readiness"
status: verified
# threats_open = count of OPEN threats at or above workflow.security_block_on severity (the blocking gate)
threats_open: 0
asvs_level: 1
created: "2026-11-23"
---

# Phase 08 — Security

> Per-phase security contract: threat register, accepted risks, and audit trail.

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| repository to the public | README, spec, architecture page, release.md, help text, tests and commits are public, and git history is permanent | example addresses, placeholder ids and paths |
| README reader to their own code | Readers copy the snippets and act on the stated rules (for example the `HtmlImages` work directory) | code snippets, usage rules |
| this workspace / the owner's terminal to Maven Central | A deploy uploads to Central: to the snapshots repository at a SNAPSHOT version, to the Portal at a release version | signed release bundle, Portal credentials (owner only) |
| the owner's release to every consumer | A published bundle is permanent and is what consumers resolve | released jars, POMs, signatures |
| a plan to the owner's working tree | The owner releases from this workspace; `target/` and `~/.m2/repository/de/morisoft` are theirs | build output, local artifacts |

---

## Threat Register

| Threat ID | Category | Component | Severity | Disposition | Mitigation | Status |
|-----------|----------|-----------|----------|-------------|------------|--------|
| T-08-01 | Information disclosure | README.md, docs/spec.md, docs/architecture.md, SendCommand.java, SendCommandHelpTest.java | medium | mitigate | Only `example.org`/`example.com` addresses and the maintainer identity appear; no non-zero GUID, no home path (grep 2026-11-23) | closed |
| T-08-02 | Tampering (SSRF through template URLs) | README morimail-html section | medium | mitigate | README.md:151 states that remote and `data:` sources are fetched only with a caller-owned work directory, only for trusted templates, and that a `$$` src stays unchanged | closed |
| T-08-03 | Tampering (misleading contract) | README Java snippets and build() rules | low | mitigate | 08-01 snippet gates compiled both snippets against the built classes; rules taken from the `Mail.Builder.inline` Javadoc | closed |
| T-08-04 | Elevation of privilege | rehearsal deploy (central-publishing-maven-plugin) | high | mitigate | 08-02-SUMMARY: the only Claude deploy carried `-DskipUpload=true` with an isolated dummy-credential settings file; plugin skipUpload log line recorded; no tag or push by Claude (confirmed by owner in 08-UAT test 5) | closed |
| T-08-05 | Information disclosure | throwaway key, gpg-agent, signed rehearsal artifacts | medium | mitigate | 08-02-SUMMARY: key only in a temporary GNUPGHOME removed by exit trap together with the temp tree and local de/morisoft artifacts; no hex string of 16+ characters in phase doc diffs (grep 2026-11-23) | closed |
| T-08-06 | Tampering (release integrity) | docs/release.md procedure | medium | mitigate | release.md pins `-pl morimail,morimail-html -am`, the anchored four-POM sed lines (72, 165) and the tag line (155); 08-02 rehearsal built the listed 54-entry bundle with 9 good signatures | closed |
| T-08-07 | Tampering | the four POMs (release version committed by mistake) | medium | mitigate | Rehearsal ran only on a `git archive` copy; no Claude phase-08 commit touches a POM; the version bump to 0.3.0-SNAPSHOT is the owner's commit 8df5f6a | closed |
| T-08-08 | Tampering (release integrity) | docs/release.md step 5 deploy commands | high | mitigate | release.md:101 and :136 chain both deploys behind `grep -- -SNAPSHOT pom.xml morimail/pom.xml morimail-html/pom.xml; [ $? -eq 1 ] &&` (form tightened by review fix WR-01, da6bc67). Re-tested 2026-11-23 on POM copies: SNAPSHOT stops, release version runs, missing POM (grep error) stops | closed |
| T-08-09 | Denial of service / Tampering | owner's working tree `target/` and `~/.m2/repository/de/morisoft` | medium | mitigate | 08-03-SUMMARY: Maven ran only in a git-archive temp copy removed afterwards; commits 2ad672c, da6bc67, 088d37d touch docs/release.md only | closed |
| T-08-10 | Information disclosure | release.md, SUMMARY and commit messages | medium | mitigate | 08-03 commit gate rejected addresses, long hex strings and home paths; grep of release.md 2026-11-23 finds none | closed |
| T-08-SC | Tampering | Maven dependencies and plugins | low | accept | No package or plugin added or upgraded in phase 08 | closed |

*Status: open · closed · open — below high threshold (non-blocking)*
*Severity: critical > high > medium > low — only open threats at or above workflow.security_block_on count toward threats_open*
*Disposition: mitigate (implementation required) · accept (documented risk) · transfer (third-party)*

---

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|-------------|------|
| AR-08-01 | T-08-SC | No dependency or plugin was added or upgraded; the pinned versions were already accepted in earlier phases | plan 08-01/08-02/08-03 threat model | 2026-11-23 |

*Accepted risks do not resurface in future audit runs.*

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-11-23 | 11 | 11 | 0 | /gsd-secure-phase 8 (orchestrator, ASVS L1 grep depth) |

---

## Sign-Off

- [x] All threats have a disposition (mitigate / accept / transfer)
- [x] Accepted risks documented in Accepted Risks Log
- [x] `threats_open: 0` confirmed
- [x] `status: verified` set in frontmatter

**Approval:** verified 2026-11-23
