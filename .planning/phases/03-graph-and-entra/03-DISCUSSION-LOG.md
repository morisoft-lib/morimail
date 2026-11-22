# Phase 3: Graph and Entra - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md. This log keeps the alternatives that were considered.

**Date:** 2026-11-22
**Phase:** 03-graph-and-entra
**Areas discussed:** Entra public API shape, Graph edge mapping, Sample E2E wiring, Live test design

---

## Entra public API shape

| Question | Options | Selected |
|---|---|---|
| Authority override | Copy method `authority(URI)` / Overloads (6 factories) / Builder | Copy method |
| Timeouts | Fixed constants / Settable | Fixed constants |
| P12 password type | `char[]` / `String` | `char[]` |
| P12 with several keys | Exactly one key / First key entry | Exactly one key |
| P12 load errors | Unchecked `IllegalArgumentException` at setup / `AuthRequiredException` | Unchecked |

## Graph edge mapping

| Question | Options | Selected |
|---|---|---|
| Unlisted statuses | By class (2xx ok, 5xx transient, else permanent) / Strict table | By class |
| 401 "points at mailbox" | `error.code` contains Mailbox / Code or message | `error.code` match |
| 3 MB | 3 × 1024² / 3,000,000 | 3 × 1024² |
| Request max | 4 MB constant vs estimate / Let research decide | 4 MB constant |
| Retry-After | Seconds + HTTP-date / Seconds only | Seconds + HTTP-date |

## Sample E2E wiring

| Question | Options | Selected |
|---|---|---|
| E2E seam | Package-private `mailer(account, env, authority, baseUrl)` / Optional properties keys | Package-private overload |
| E2E credential | Certificate / Both / Secret | Certificate |
| E2E checks | 5 sendMail + 1 token; Rerun sends nothing; 403 stops with exit 3 (multi) | All three |
| Missing env var / bad P12 | Pre-flight exit 2 / Exit 3 | Pre-flight exit 2 |

## Live test design

| Question | Options | Selected |
|---|---|---|
| Module | Library / Sample | Library |
| Scope | One mail + optional probes / Exactly one mail | One mail + optional probes |
| Env naming | `MORIMAIL_LIVE_*` / Reuse account.properties | `MORIMAIL_LIVE_*` |
| Both creds set | Cert wins / Both, two sends | Cert wins |

## Claude's Discretion

Class split, INFO-once scoping, single-flight mechanism, exact timeout values, keystore file name, REPO-08 proof method, message wording.

## Deferred Ideas

- Settable timeouts and authority/baseUrl keys in account.properties.
- A live probe above 3 MB (validate rejects it before sending).
