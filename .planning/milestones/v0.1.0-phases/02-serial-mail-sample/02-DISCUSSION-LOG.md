# Phase 2: Serial-Mail Sample - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md. This log preserves the alternatives considered.

**Date:** 2026-11-21
**Phase:** 02-serial-mail-sample
**Areas discussed:** Real send before Graph, Journal & rerun rules, Retry & wait timing, Output & exit codes

---

## Real send before Graph

| Option | Description | Selected |
|--------|-------------|----------|
| Clear error, exit 2 | Parse account file; real Mailer factory fails clearly until Phase 3; tests inject Mailer | ✓ |
| Hidden transport= key | Optional `transport=recording` in account.properties | |

| Option | Description | Selected |
|--------|-------------|----------|
| Pre-flight minus validate | Dry-run builds no Mailer, skips mailer.validate, builds every mail | ✓ |
| Build Mailer without credentials | Dry-run builds account with an unused TokenProvider and runs validate | |

| Option | Description | Selected |
|--------|-------------|----------|
| Always, env vars on real runs only | Account file checked in dry-run too; env vars needed only for real sends | ✓ |
| Real runs only | Dry-run ignores account.properties | |

| Option | Description | Selected |
|--------|-------------|----------|
| Set from explicitly | CLI sets from(account.from) on every mail | ✓ |
| Rely on defaultFrom | Real run uses defaultFrom; dry-run has no sender | |

---

## Journal & rerun rules

| Option | Description | Selected |
|--------|-------------|----------|
| Yes, retry failed rows | Only accepted rows are skipped (spec §8.3.4) | ✓ |
| Skip failed too | Any journal key is skipped | |

| Option | Description | Selected |
|--------|-------------|----------|
| Pre-flight error, exit 2 | Duplicate addresses reported with both line numbers | ✓ |
| Second row skipped | First row wins, second skipped via journal key | |

| Option | Description | Selected |
|--------|-------------|----------|
| One line per attempt | `transient` per retried attempt, then `accepted`/`failed` | ✓ |
| One line per row | Final result only | |

| Option | Description | Selected |
|--------|-------------|----------|
| Key holds it, mixing allowed | Foreign campaign lines simply don't match | ✓ |
| Reject foreign lines | Different campaign ID is a pre-flight error | |

---

## Retry & wait timing

| Option | Description | Selected |
|--------|-------------|----------|
| 5 minutes | Cap on retryAfter, code constant | ✓ |
| 60 seconds | Tighter cap | |
| You decide | Claude picks | |

| Option | Description | Selected |
|--------|-------------|----------|
| Sleep function in constructor | `Consumer<Duration>`-style, default Thread.sleep | ✓ |
| Clock + sleeper | Also inject java.time.Clock | |

| Option | Description | Selected |
|--------|-------------|----------|
| Every send attempt keeps spacing | Retry wait = max(retry wait, rate spacing) | ✓ |
| Spacing per row only | Retries wait only retryAfter/30 s | |

| Option | Description | Selected |
|--------|-------------|----------|
| 4 attempts (1 + 3 retries) | Literal spec reading | ✓ |
| 3 attempts total | One first try, two retries | |

---

## Output & exit codes

| Option | Description | Selected |
|--------|-------------|----------|
| 1 = rows failed or journal error | Codes 0/1/2/3 only | ✓ |
| Own code 4 for journal error | Distinct code for journal failure | |

| Option | Description | Selected |
|--------|-------------|----------|
| Counter, address, result | `[3/5] anna@example.org accepted`; errors to stderr | ✓ |
| Without address | Row number and result only | |

| Option | Description | Selected |
|--------|-------------|----------|
| Overwrite files | Create out/ if needed, overwrite same names | ✓ |
| Fail if not empty | Pre-flight error on non-empty out/ | |

| Option | Description | Selected |
|--------|-------------|----------|
| Allow-list, rest to _ | Keep `[A-Za-z0-9._@-]`, zero-padded n | ✓ |
| You decide | Claude picks | |

---

## Claude's Discretion

- How the CLI is run (runnable jar + `lib/`), how the example PDF/PNG are generated and checked in, `.txt` only when `body.txt` exists, class split, journal timestamp format and damaged-line handling, `--limit` counting, pre-flight message wording.

## Deferred Ideas

None.
