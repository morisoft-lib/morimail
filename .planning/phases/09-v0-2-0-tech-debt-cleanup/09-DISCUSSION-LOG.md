# Phase 9: v0.2.0 tech debt cleanup - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-11-24
**Phase:** 09-v0-2-0-tech-debt-cleanup
**Mode:** `--auto`. Claude picked the recommended option in every area, and the owner was not asked.
**Areas discussed:** Core WR-01, Core WR-02, Core IN-01, Core IN-03, Sample pre-flight, Helper IN-01, Helper IN-02, Planning artifacts

---

## Core WR-01: control characters in an echoed cid: ID

| Option | Description | Selected |
|--------|-------------|----------|
| Replace non-printable code points with `?` | Message stays useful and recipient text can no longer forge log lines | ✓ |
| Drop the ID from the message | Safest, but the message no longer says which reference is wrong | |

**Choice:** [auto] Replace with `?` (recommended by the reviewer)

## Core WR-02: whole-HTML cid: scan

| Option | Description | Selected |
|--------|-------------|----------|
| Keep the scan, add `<` to the terminators, document the trade-off | Only loosens the check and matches content ID rule D-09 | ✓ |
| Match only in attribute or `url()` context | Reverses owner decision D-04 | |
| Document only | No code change, and `<p>cid:x</p>` still fails in a confusing way | |

**Choice:** [auto] Add `<`, keep D-04. Do not strip a trailing `/`, because it is a legal ID character; document it instead.

## Core IN-01: content type of image formats outside the table

| Option | Description | Selected |
|--------|-------------|----------|
| Add webp, bmp, svg to CONTENT_TYPES | The same type on every platform | ✓ |
| Javadoc only | Already partly documented, and the platform dependence stays | |

**Choice:** [auto] Add the three entries, and keep the helper table in step.

## Core IN-03: case-sensitive content IDs

| Option | Description | Selected |
|--------|-------------|----------|
| Document, no behaviour change | Keeps owner decision D-05 and does not break 0.2.0 mails | ✓ |
| Reject IDs that differ only in case | Tightens a released check (sample and helper would follow), with no evidence of a problem | |

**Choice:** [auto] Document only.

## Sample: pre-flight validates only the first mail

| Option | Description | Selected |
|--------|-------------|----------|
| Validate every mail, group messages by line | Reuses `invalidMail`, and validate is local and cheap | ✓ |
| Validate only the largest mail | Clever, but a different limit could fail on another row | |

**Choice:** [auto] Validate every mail.

## Helper IN-01: strip()

| Option | Description | Selected |
|--------|-------------|----------|
| Javadoc sentence, keep strip() | The owner accepted the behaviour in 07-UAT | ✓ |
| Trim only U+0000..U+0020 | Exact browser rule, but a behaviour change nobody asked for | |

**Choice:** [auto] Document only.

## Helper IN-02: untested Error rethrow

| Option | Description | Selected |
|--------|-------------|----------|
| Accept the gap and record it | The reviewer says to add no seam for this (Rule 0) | ✓ |
| Add a package-private test seam | Goes against Rule 0 | |

**Choice:** [auto] Accept it. A test is allowed only if an existing path reaches the reader without a new seam.

## Planning artifacts

**Choice:** [auto] Fix the 07-01-SUMMARY YAML and its "provided scope" text. Mark the 08-02-SUMMARY Central line as superseded by the 0.2.0 release. Update the 05 and 07 review dispositions.

## Claude's Discretion

- How to split the plans, the exact wording, and the test names.

## Deferred Ideas

- Attribute-context-only `cid:` matching, case-insensitive ID uniqueness, and the owner's git housekeeping.
