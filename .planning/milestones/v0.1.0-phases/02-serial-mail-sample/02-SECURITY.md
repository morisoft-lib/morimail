---
phase: "02"
slug: "serial-mail-sample"
status: verified
# threats_open = count of OPEN threats at or above workflow.security_block_on severity (the blocking gate)
threats_open: 0
asvs_level: 1
created: "2026-11-22"
---

# Phase 02 — Security

> Per-phase security contract: threat register, accepted risks, and audit trail.

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| campaign files to the CLI | recipients.csv, templates and attachments are operator-supplied and untrusted in shape | recipient addresses and field values (personal data) |
| account.properties to the CLI | operator-supplied; a secret may be pasted where an environment-variable name belongs | tenant, sender, credential references (sensitive if misused) |
| CLI to the console and CI logs | pre-flight, progress, stop and warning lines are printed | file names, keys, exception messages (no values) |
| CLI to the file system | dry-run writes below --out; the real run appends to the journal | rendered mails, journal records with addresses |
| CLI to the mail service (through the Mailer) | attempts can be throttled, rejected or lost | one mail per attempt |
| journal file across runs | written by one run, read by the next; may be torn or hand-edited | send outcomes per key |
| repository to the public | every committed file and git history are public and permanent | example files, two binaries, planning docs |

---

## Threat Register

| Threat ID | Category | Component | Severity | Disposition | Mitigation | Status |
|-----------|----------|-----------|----------|-------------|------------|--------|
| T-02-01 | Tampering | SendCommand dry-run file names | high | mitigate | `SendCommand.safeName` keeps only ASCII letters, digits and `._@-`, with an `<n>-` prefix; `DryRunTest.sanitisesFileNamesAndStaysInsideOut` | closed |
| T-02-02 | Information Disclosure | example-campaign files | medium | mitigate | Only `example.org`/`example.com` addresses (grep found none other); `ExampleCampaignTest.accountFileHoldsOnlyExampleValues` | closed |
| T-02-03 | Information Disclosure | info.pdf, logo.png | low | mitigate | PDF has no Info dictionary, PNG has only IHDR/IDAT/IEND (inspected 2026-11-22); `ExampleCampaignTest.attachmentsAreSmallAndCarryNoMetadata` | closed |
| T-02-04 | Information Disclosure | .gitignore negation | medium | mitigate | `git check-ignore`: other `account.properties` ignored (.gitignore:32), only the example path is negated | closed |
| T-02-05 | Information Disclosure | phase planning files | low | mitigate | No workstation paths or session ids in 02-RESEARCH.md / 02-PATTERNS.md (grep clean) | closed |
| T-02-06 | Information Disclosure | journal.jsonl, out/ | low | mitigate | `*.jsonl` (.gitignore:31) and `out/` (.gitignore:28) ignored, proven by `git check-ignore` | closed |
| T-02-07 | Information Disclosure | AccountFile.read, pre-flight output | high | mitigate | Problems name file and keys only; `AccountFileTest` sentinel-value assertions | closed |
| T-02-08 | Tampering | Campaign.parseCsv and read | medium | mitigate | Strict UTF-8 (`MalformedInputException` reported), `;`-header rule, duplicate-address problem, field-count check; `CampaignTest` | closed |
| T-02-09 | Tampering | HTML values from recipients.csv | low | accept | Core renderer escapes values (TMPL-02); dry-run output shows `&amp;` | closed |
| T-02-10 | Denial of Service | large or unterminated recipients.csv | low | accept | Unterminated quote reported with start line (`Campaign.java` "a quoted field is not closed") | closed |
| T-02-11 | Spoofing | from in account.properties | low | transfer | Send-as permission enforced by Exchange on the server (spec §2) | closed |
| T-02-12 | Repudiation | Journal.append | medium | mitigate | `channel.force(true)` per line; write failure stops the run, `JournalTest.aFailedJournalWriteStopsTheRun` (/dev/full) | closed |
| T-02-13 | Tampering | Journal.open | medium | mitigate | Non-object lines warned and never counted as accepted (`Journal.java` `isObject` check) | closed |
| T-02-14 | Denial of Service | SendCommand attempt loop | medium | mitigate | Spacing `ceil(60000/rate)`, `MAX_RETRY_WAIT` 5 min, `MAX_ATTEMPTS` 4, stop on `SenderRejectedException \| AuthRequiredException` | closed |
| T-02-15 | Information Disclosure | journal and console error text | low | accept | Only exception class and message written; journal git-ignored | closed |
| T-02-16 | Repudiation | mail in flight when the process is killed | low | accept | At-least-once is the owner's decision; stated in the `send` help footer | closed |
| T-02-SC | Tampering | Maven artifacts | high | mitigate | Sample POM declares only morimail, jackson-databind, picocli and test deps, no versions; `target/lib` holds exactly Jackson (3), picocli 4.7.7 and morimail | closed |

*Status: open · closed · open — below high threshold (non-blocking)*
*Severity: critical > high > medium > low — only open threats at or above workflow.security_block_on count toward threats_open*
*Disposition: mitigate (implementation required) · accept (documented risk) · transfer (third-party)*

---

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|-------------|------|
| AR-02-01 | T-02-09 | Values are escaped by the core renderer; the raw form is chosen by the template author, not a recipient | plan 02-02 (owner-approved plan) | 2026-11-21 |
| AR-02-02 | T-02-10 | The file is read once by the operator's own run; an unterminated quote is reported, not hung on | plan 02-02 (owner-approved plan) | 2026-11-21 |
| AR-02-03 | T-02-15 | Library messages never carry tokens or bodies; the journal stays git-ignored | plan 02-03 (owner-approved plan) | 2026-11-21 |
| AR-02-04 | T-02-16 | At-least-once delivery is the owner's recorded decision; the forced per-attempt journal narrows the window | plan 02-03 (owner-approved plan) | 2026-11-21 |

*Accepted risks do not resurface in future audit runs.*

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-11-22 | 17 | 17 | 0 | /gsd-secure-phase (orchestrator, ASVS L1 grep-depth; auditor skipped per short-circuit rule) |

---

## Sign-Off

- [x] All threats have a disposition (mitigate / accept / transfer)
- [x] Accepted risks documented in Accepted Risks Log
- [x] `threats_open: 0` confirmed
- [x] `status: verified` set in frontmatter

**Approval:** verified 2026-11-22
