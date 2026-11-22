---
phase: 03-graph-and-entra
reviewed: 2026-11-22T17:00:00Z
depth: standard
files_reviewed: 5
files_reviewed_list:
  - docs/architecture.md
  - morimail/src/main/java/de/morisoft/morimail/entra/EntraClientCredentials.java
  - morimail/src/main/java/de/morisoft/morimail/graph/GraphTransport.java
  - morimail/src/test/java/de/morisoft/morimail/entra/EntraTokenErrorsTest.java
  - morimail/src/test/java/de/morisoft/morimail/graph/GraphErrorMappingTest.java
findings:
  critical: 0
  warning: 1
  info: 2
  total: 3
  necessity: 0
status: issues_found
---

# Phase 3: Code Review Report (incremental re-review)

**Reviewed:** 2026-11-22T17:00:00Z
**Depth:** standard
**Files Reviewed:** 5
**Status:** issues_found

## Summary

This is an incremental re-review of the diff `962ef2b..HEAD`. It covers the fixes for the previous WR-01 (cut `clean()` input before the address regex), IN-01 (architecture.md states address cleaning as best effort) and IN-02 (request-id cleaning tests).

- **WR-01 fix.** It removes the quadratic cost. I measured the unbounded pattern on 100,000 `x` characters at about 12 s on this machine. With a 1000-character cut the cost is bounded.
- **IN-01 fix.** The doc wording is accurate and consistent with the code.
- **IN-02 tests.** They are correct. `x@example.org` becomes `<address>` in both the header and `innerError` paths.

The new cut is placed before whitespace collapsing, though. That order creates a small leak that the old order did not have (WR-01 below). Two minor test and maintainability notes follow.

## Warnings

### WR-01: Cutting at 1000 raw characters before whitespace collapse can leave a partial mail address in the message

**File:** `morimail/src/main/java/de/morisoft/morimail/graph/GraphTransport.java:316-317` and `morimail/src/main/java/de/morisoft/morimail/entra/EntraClientCredentials.java:383-385`
**Issue:** The 1000-character cut runs on the raw text, before whitespace runs collapse. The final output limit is 300 characters, measured after collapsing. When the service text starts with a long run of whitespace or control characters, the cut can land in the middle of an address. The address's `@` is then lost, so the address pattern does not match. The local-part prefix survives in the output.

Reproduction: input `" ".repeat(995) + "alice@example.org"`. The cut gives 995 spaces plus `alice`. Collapsing gives ` alice`. No `@` is present, so nothing is replaced. The message contains `alice`.

The same cut can also leave a lone high surrogate at the end of a text that is shorter than 300 characters after collapsing. The existing surrogate guard only runs for texts longer than 300.

The input is service-controlled and the case is contrived. Still, this regresses against the claim "anything that looks like a mail address is replaced". Collapsing before cutting keeps the cut harmless. Collapsing is a linear regex, so the fix does not bring the quadratic cost back. After collapsing, any text that is 1000 characters or longer is hidden by the 300-character output cut, so a split address cannot show.

**Fix:** Collapse first, cut second, address pattern last.

```java
// GraphTransport.clean (the loop above already maps whitespace and controls to ' ')
String collapsed = sb.toString().replaceAll(" {2,}", " ");
String text = (collapsed.length() > 1000 ? collapsed.substring(0, 1000) : collapsed)
        .replaceAll("[^\\s'\"<>]+@[^\\s'\"<>]+", "<address>").trim();

// EntraClientCredentials.clean
String collapsed = spaced.toString().replaceAll("\\s+", " ");
String text = (collapsed.length() > 1000 ? collapsed.substring(0, 1000) : collapsed)
        .replaceAll("[^\\s'\"<>]+@[^\\s'\"<>]+", "<address>").trim();
```

Add a test with leading whitespace followed by an address that straddles the cut. For example, 995 spaces plus `alice@example.org` as `error.code` should give `code <address>` or `-`, and must not give `alice`.

## Info

### IN-01: The "quickly" regression tests have a thin time margin

**File:** `morimail/src/test/java/de/morisoft/morimail/entra/EntraTokenErrorsTest.java:211-219` and `morimail/src/test/java/de/morisoft/morimail/graph/GraphErrorMappingTest.java:361-366`
**Issue:** Both tests use `@Timeout(10)` with a 100,000-character input. I measured the un-cut regex on that input at about 12 s here. On a faster CI machine the regression could finish in under 10 s, so the tests would pass without the fix. JUnit's `@Timeout` also cannot interrupt a running regex. It fails the test only after the call returns, so raising the input size would slow a failing run instead of making the guard stronger.
**Fix:** Either accept this as a coarse guard, or make the check structural. For example, make `clean` package-private in Entra too, as Graph already does, and unit-test it with a size-based assertion. Raising the input to 200,000 characters would give about 4x the cost, roughly 48 s on this machine, and a clearer margin, but it makes a failing run slow.

### IN-02: The magic number 1000 is duplicated in two files, and Graph repeats 300 as a literal

**File:** `morimail/src/main/java/de/morisoft/morimail/graph/GraphTransport.java:316` and `morimail/src/main/java/de/morisoft/morimail/entra/EntraClientCredentials.java:383`
**Issue:** The pre-cut limit 1000 appears as a bare literal in both `clean()` methods. Entra names its output limit `MAX_ERROR_TEXT`, while Graph uses a literal `300`. Changing a limit in one place can leave the two copies out of step.
**Fix:** Add a named constant per file (for example `MAX_CLEAN_INPUT = 1000`). Rule 0 argues against a shared helper class that would couple `graph` and `entra`, since the architecture requires that they do not import each other.

---

_Reviewed: 2026-11-22T17:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
