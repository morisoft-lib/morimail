---
phase: 03-graph-and-entra
fixed_at: 2026-11-22T16:30:00Z
review_path: /home/coder/morimail/.planning/phases/03-graph-and-entra/03-REVIEW.md
iteration: 1
findings_in_scope: 3
fixed: 2
skipped: 1
status: partial
---

# Phase 3: Code Review Fix Report

**Fixed at:** 2026-11-22
**Source review:** /home/coder/morimail/.planning/phases/03-graph-and-entra/03-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope: 3
- Fixed: 2
- Skipped: 1

**Verification:** `./mvnw -B -ntp -o verify` ran green (137 sample tests plus the library suite) in the main checkout on `main` (no worktree) before each commit.

## Fixed Issues

### WR-01: Cutting at 1000 raw characters before whitespace collapse can leave a partial mail address in the message

**Files modified:** `morimail/src/main/java/de/morisoft/morimail/graph/GraphTransport.java`, `morimail/src/main/java/de/morisoft/morimail/entra/EntraClientCredentials.java`, `morimail/src/test/java/de/morisoft/morimail/graph/GraphErrorMappingTest.java`, `morimail/src/test/java/de/morisoft/morimail/entra/EntraTokenErrorsTest.java`
**Commit:** f9588ec
**Status:** fixed: requires human verification (logic/order change)
**Applied fix:** In both `clean()` methods the order is now collapse whitespace, cut at 1000, replace addresses, trim. Added one test per class with 995 spaces followed by `alice@example.org`. Both expect the address to become `<address>` with no `alice` left in the message.

### IN-02: The magic number 1000 is duplicated in two files, and Graph repeats 300 as a literal

**Files modified:** `morimail/src/main/java/de/morisoft/morimail/graph/GraphTransport.java`, `morimail/src/main/java/de/morisoft/morimail/entra/EntraClientCredentials.java`
**Commit:** c7c0ba5
**Applied fix:** Added a private `MAX_CLEAN_INPUT = 1000` constant to each class, and a `MAX_ERROR_TEXT = 300` constant to `GraphTransport` (Entra already had it). No shared helper, so `graph` and `entra` stay independent.

## Skipped Issues

### IN-01: The "quickly" regression tests have a thin time margin

**File:** `morimail/src/test/java/de/morisoft/morimail/entra/EntraTokenErrorsTest.java:211-219` and `morimail/src/test/java/de/morisoft/morimail/graph/GraphErrorMappingTest.java:361-366`
**Reason:** Accepted as a coarse guard, no change. The review offers this as an option. The structural alternative would widen `EntraClientCredentials.clean` visibility for tests, and a size-based assertion cannot detect quadratic cost anyway. A larger input would only slow a failing run. Rule 0 favours leaving the tests as they are.
**Original issue:** Both tests use `@Timeout(10)` with a 100,000-character input, and the un-cut regex took about 12 s on the reviewer's machine, so a faster CI machine could pass without the fix.

---

_Fixed: 2026-11-22_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
