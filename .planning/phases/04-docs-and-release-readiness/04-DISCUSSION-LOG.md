# Phase 4: Docs and Release Readiness - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-11-22
**Phase:** 04-docs-and-release-readiness
**Areas discussed:** Live-findings write-back, Setup guide shape, README quick start, Release steps & rehearsal

---

## Live-findings write-back

| Option | Description | Selected |
|--------|-------------|----------|
| Edit table + status col | Keep rows, add live-status column, replace forum sentence with 404 finding | ✓ |
| Minimal sentence edit | Only replace the forum-answer sentence | |
| Leave spec untouched | Record findings in architecture.md only | |

| Option | Description | Selected |
|--------|-------------|----------|
| Fix WR-01 here | Replace before cut / token-boundary cut, one test per class | ✓ |
| Defer past 0.1.0 | Note as known limitation | |

| Option | Description | Selected |
|--------|-------------|----------|
| Keep Graph default (Sent Items) | No code change, document it | ✓ |
| Sample sets false | Small code change | |

## Setup guide shape

| Option | Description | Selected |
|--------|-------------|----------|
| B recommended, A as tested | Spec order; state A is live-verified, B not yet | ✓ |
| A first, B as hardening | Deviates from spec §9 | |

| Option | Description | Selected |
|--------|-------------|----------|
| openssl | Cross-platform | ✓ |
| keytool | JDK-only | |
| PowerShell New-SelfSignedCertificate | Windows only | |

| Option | Description | Selected |
|--------|-------------|----------|
| Short "Not yet verified" list | One section at the end | ✓ |
| Inline caveats | Per-step notes | |

| Option | Description | Selected |
|--------|-------------|----------|
| English | Matches repo | ✓ |
| German | | |

## README quick start

| Option | Description | Selected |
|--------|-------------|----------|
| Library snippet + CLI pointer | One Java block with catches, short dry-run pointer, link to setup guide | ✓ |
| Library snippet only | Separate sample README | |
| Both, full detail | Duplicates spec §8 | |

| Option | Description | Selected |
|--------|-------------|----------|
| No, hand-checked | Rule 0 | ✓ |
| Mirror in a test | | |

## Release steps & rehearsal

| Option | Description | Selected |
|--------|-------------|----------|
| docs/release.md | Short checklist, no secrets | ✓ |
| Section in architecture.md | | |
| Only in phase report | | |

| Option | Description | Selected |
|--------|-------------|----------|
| Fix in POM, then stop and ask | Documented fix first, ask before structural change | ✓ |
| Just ask first | | |

| Option | Description | Selected |
|--------|-------------|----------|
| Throwaway key in scratch | Verifies .asc files, key deleted | ✓ |
| gpg.skip only | | |

## Claude's Discretion

- Layout of the architecture.md contract summary; wording/section order in README and entra-setup.md; WR-01 fix technique.

## Deferred Ideas

- Live verification of shared mailbox, display name, out-of-scope RBAC, mailbox 401, MailboxNotEnabledForRESTAPI.
- Sample `saveToSentItems=false` option.
- Phase 3 deferrals (timeouts, sovereign clouds, >3 MB probe/upload sessions); IN-06 journal lock; Thumbs.db/desktop.ini in attachments.
