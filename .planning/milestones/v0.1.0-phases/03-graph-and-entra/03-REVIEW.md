---
phase: 03-graph-and-entra
reviewed: 2026-11-22T17:00:00Z
depth: standard
files_reviewed: 4
files_reviewed_list:
  - morimail/src/main/java/de/morisoft/morimail/entra/EntraClientCredentials.java
  - morimail/src/main/java/de/morisoft/morimail/graph/GraphTransport.java
  - morimail/src/test/java/de/morisoft/morimail/entra/EntraTokenErrorsTest.java
  - morimail/src/test/java/de/morisoft/morimail/graph/GraphErrorMappingTest.java
findings:
  critical: 0
  warning: 1
  info: 1
  total: 2
  necessity: 0
status: issues_found
---

# Phase 3: Code Review Report

**Reviewed:** 2026-11-22T17:00:00Z
**Depth:** standard
**Files Reviewed:** 4
**Status:** issues_found

## Summary

Incremental re-review of the two fixes applied since `fa9f943`: WR-01 (collapse whitespace before the cut, commit f9588ec) and IN-02 (named the length limits, commit c7c0ba5). I read both `clean()` methods in full and compared the diff for the two tests that were added.

IN-02 is correct. `MAX_ERROR_TEXT - 1` replaces the literal `299`, and the constants carry the same values as the literals they replaced. Neither class gained a shared helper, so `graph` and `entra` still do not import each other.

WR-01 fixes the case it targets. The collapse runs in linear time, so running it before the cut does not bring back the quadratic cost. The two new tests pin that case: 995 spaces followed by an address now give `<address>`. However, the cut at `MAX_CLEAN_INPUT` still happens before address replacement, so the same class of defect remains. A cut can split an address at the 1000-character boundary, and the head of that address is then left in the message. WR-01 only closed the whitespace-padding route to that boundary.

## Warnings

### WR-01: The 1000-character cut can still split a mail address and leave its start in the message

**File:** `morimail/src/main/java/de/morisoft/morimail/graph/GraphTransport.java:320-322` and `morimail/src/main/java/de/morisoft/morimail/entra/EntraClientCredentials.java:386-388`

**Issue:** The cut happens before the address pattern runs. If the boundary at `MAX_CLEAN_INPUT` falls inside an address, the cut text has no `@`, or no complete `local@domain`, and the pattern does not match. The leftover start of the address then stays in the text. The final 300-character cut hides this only when the leftover lies beyond position 300 after replacement. It does not when earlier text shrinks during replacement, or when the leftover is the head of a single token longer than 1000 characters.

I ran the Graph `clean()` logic on `"x".repeat(996) + "@y" + " " + "alice@example.org"` (the second address starts at position 999). The result was `<address> a`. The first token shrinks to `<address>` and the start of the second address survives as `a`. With a longer local part that begins earlier, the survivor is correspondingly longer. The input is contrived: Graph codes and Entra descriptions do not contain addresses in practice. The project rule is that no mail addresses appear in exception messages, and the 03-REVIEW-FIX note says the order change closed this problem. The tests only cover the whitespace-padding variant.

**Fix:** Do not leave a partial token at the cut. This stays a few lines and adds no abstraction:

```java
String collapsed = sb.toString().replaceAll(" {2,}", " ");
if (collapsed.length() > MAX_CLEAN_INPUT) {
    collapsed = collapsed.substring(0, MAX_CLEAN_INPUT);
    int lastSpace = collapsed.lastIndexOf(' ');
    collapsed = lastSpace > 0 ? collapsed.substring(0, lastSpace) : "";
}
String text = collapsed.replaceAll("[^\\s'\"<>]+@[^\\s'\"<>]+", "<address>").trim();
```

The same change applies in `EntraClientCredentials.clean` (`\\s+` collapse). Cutting at a token boundary also removes the chance of a lone high surrogate at the end, which would be possible if the text after replacement is 300 characters or fewer. Add one test per class where an address straddles character 1000, for example `"x".repeat(990) + " " + "alice".repeat(5) + "@example.org"` with the surrounding padding adjusted so the `@` falls after position 1000. It should assert that no `alice` remains.

## Info

### IN-01: The two new regression tests cover only the whitespace-padding route to the cut boundary

**File:** `morimail/src/test/java/de/morisoft/morimail/graph/GraphErrorMappingTest.java:367-373` and `morimail/src/test/java/de/morisoft/morimail/entra/EntraTokenErrorsTest.java:221-229`

**Issue:** Both tests use `" ".repeat(995)` to put an address at the old raw cut. They fail on the old code and pass on the new, so they are valid. They do not exercise a boundary inside a non-whitespace region (see WR-01), so they would not catch the remaining leak.

**Fix:** Add the straddling-address test described under WR-01 when that is fixed.

---

_Reviewed: 2026-11-22T17:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
