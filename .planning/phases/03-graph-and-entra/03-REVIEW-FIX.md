---
phase: 03-graph-and-entra
fixed_at: 2026-11-22T16:15:00Z
review_path: /home/coder/morimail/.planning/phases/03-graph-and-entra/03-REVIEW.md
iteration: 1
findings_in_scope: 3
fixed: 3
skipped: 0
status: all_fixed
---

# Phase 3: Code Review Fix Report

**Fixed at:** 2026-11-22
**Source review:** /home/coder/morimail/.planning/phases/03-graph-and-entra/03-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope: 3
- Fixed: 3
- Skipped: 0

**Verification:** `./mvnw -B -ntp -o verify` ran green (137 sample tests plus the library suite) in the isolated worktree before each commit. The worktree was fast-forwarded into `main` and removed afterwards.

## Fixed Issues

### WR-01: `clean()` address regex is quadratic on a long, address-free token

**Files modified:** `morimail/src/main/java/de/morisoft/morimail/graph/GraphTransport.java`, `morimail/src/main/java/de/morisoft/morimail/entra/EntraClientCredentials.java`, `morimail/src/test/java/de/morisoft/morimail/graph/GraphErrorMappingTest.java`, `morimail/src/test/java/de/morisoft/morimail/entra/EntraTokenErrorsTest.java`
**Commit:** 2f1e499
**Applied fix:** Both `clean()` methods (Graph and Entra use the same pattern) now cut the text to 1000 characters before the whitespace and address regexes run. The 300-character output cut is unchanged. Two tests send a 100000-character `error.code` and `error_description`, assert the 300-character plus `...` result, and carry `@Timeout(10)` so a regression to the quadratic cost fails the build.

### IN-01: The "no addresses in exception messages" claim is still a best-effort statement for `code` and `request-id`

**Files modified:** `docs/architecture.md`
**Commit:** f084394
**Applied fix:** The Contracts bullet no longer lists mail addresses among the things messages never carry. It now says the Graph fields are identifiers in practice, and that the address pattern is a best-effort safeguard, not a guarantee.

### IN-02: No test shows that an address-bearing `request-id` is cleaned

**Files modified:** `morimail/src/test/java/de/morisoft/morimail/graph/GraphErrorMappingTest.java`
**Commit:** 691c615
**Applied fix:** Added two tests, one with the `request-id` header and one with `innerError.request-id` set to `x@example.org`. Both assert the message ends with `request-id <address>`.

---

_Fixed: 2026-11-22_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
