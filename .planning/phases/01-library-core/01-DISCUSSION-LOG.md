# Phase 1: Library Core - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md. This log keeps the alternatives that were considered.

**Date:** 2026-11-21
**Phase:** 01-library-core
**Areas discussed:** Builder input shapes, Attachment content type, RecordingTransport hooks, CLAUDE.md / SECURITY.md

---

## Builder input shapes

| Option | Description | Selected |
|--------|-------------|----------|
| String + (String, String) | Bare email and email + display name | |
| Also MailAddress | Add a MailAddress overload too | ✓ |
| String only | No display names | |

| Option | Description | Selected |
|--------|-------------|----------|
| Error at build() | template and explicit subject/html/text are mutually exclusive | ✓ |
| Last call wins | Later call overwrites | |
| Explicit overrides template | Precedence rule | |

| Option | Description | Selected |
|--------|-------------|----------|
| Varargs + append | to(String...), repeated calls add | ✓ |
| Single + append | One per call | |
| You decide | | |

| Option | Description | Selected |
|--------|-------------|----------|
| First problem only | Simple, deterministic | ✓ |
| All problems in one message | Collected list | |

| Option | Description | Selected |
|--------|-------------|----------|
| Last call wins (from/replyTo) | Normal builder behaviour | ✓ |
| Error | Strict | |

---

## Attachment content type

| Option | Description | Selected |
|--------|-------------|----------|
| Probe, then octet-stream + attach(path, type) | No table | |
| Small extension map first | About 10 types, then probe, then octet-stream | ✓ |
| Probe only, no override | Smallest | |

| Option | Description | Selected |
|--------|-------------|----------|
| path.getFileName() | File name on disk | ✓ |
| attach(path, filename, contentType) | Extra overload | |

| Option | Description | Selected |
|--------|-------------|----------|
| attach(Attachment) | Full record | |
| attach(path, contentType) | Type only | |
| Both | | ✓ |

| Option | Description | Selected |
|--------|-------------|----------|
| Case-insensitive, Locale.ROOT | | ✓ |
| Exact lowercase only | | |

---

## RecordingTransport hooks

| Option | Description | Selected |
|--------|-------------|----------|
| MailAccount.transport() getter | Cast in tests, no statics | ✓ |
| Static registry in RecordingTransport | Global state | |
| Pass a handle via options | option("recorder", rec) | |

| Option | Description | Selected |
|--------|-------------|----------|
| Per-recipient + next-send queue | failFor + failNext | ✓ |
| Next-send queue only | | |
| Per-recipient only | | |

| Option | Description | Selected |
|--------|-------------|----------|
| Yes, failValidation(ex) | | ✓ |
| No, validate always passes | | |

---

## CLAUDE.md / SECURITY.md

| Option | Description | Selected |
|--------|-------------|----------|
| Root CLAUDE.md, §11 verbatim | .claude/CLAUDE.md stays GSD-managed | ✓ |
| Merge into .claude/CLAUDE.md | | |
| Root file, move GSD content there | | |

| Option | Description | Selected |
|--------|-------------|----------|
| GitHub advisories + email | Primary GitHub, fallback stephan@morisoft.de | ✓ |
| Email only | | |
| GitHub advisories only | | |

| Option | Description | Selected |
|--------|-------------|----------|
| Minimal stub | Full quick start in Phase 4 | ✓ |
| Stub + recording quick start | | |

---

## Claude's Discretion

- RecordingTransport method names and whether it records failed attempts.
- Exception message wording.
- Duplicate account IDs in Mailer.of (expected: IllegalArgumentException).
- Logging levels and content within the rules.
- Package placement of the registry and BuiltInTransports.

## Deferred Ideas

None.
