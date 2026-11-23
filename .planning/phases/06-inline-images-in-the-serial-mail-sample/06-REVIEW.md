---
phase: 06-inline-images-in-the-serial-mail-sample
reviewed: 2026-11-23T08:30:00Z
depth: standard
files_reviewed: 10
files_reviewed_list:
  - morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/Campaign.java
  - morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/SendCommand.java
  - morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/CampaignTest.java
  - morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/DryRunTest.java
  - morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/ExampleCampaignTest.java
  - morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/GraphEndToEndTest.java
  - morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/PreflightTest.java
  - morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/SampleFixtures.java
  - morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/SendRunTest.java
  - morimail-sample-serial/example-campaign/body.html
findings:
  critical: 0
  warning: 1
  info: 4
  total: 5
  necessity: 0
status: issues_found
---

# Phase 6: Code Review Report

**Reviewed:** 2026-11-23
**Depth:** standard
**Files Reviewed:** 10
**Status:** issues_found

## Summary

I reviewed the phase 6 diff (`git diff 9655d19`) for the sample module: the `inline/` directory handling in `Campaign`, the inline wiring and dry-run rewrite in `SendCommand`, the example campaign, and the tests. I checked each behaviour against the core's `Mail.Builder` (`resolveAttachments`, `contentIdProblem`, `checkReferences`, `CID_REFERENCE`). I also ran `./mvnw -o -pl morimail-sample-serial -am test` and it passes.

I found no correctness or security defects in the production code. The checks that could plausibly go wrong all hold:

- The copied `CID_REFERENCE` is byte-identical to the core's.
- Every character a content ID may contain is accepted by that pattern, so an accepted ID is always captured whole.
- The dry-run replacement goes through `Matcher.quoteReplacement`, so `$` and `\` in a file name cannot corrupt the output.
- The `URI` multi-argument constructor always quotes `%`, and `&` is escaped afterwards.
- Dry-run copies use only `image.filename()`, so there is no traversal. Symlinks in `inline/` are the campaign author's own input and are no worse than the existing `attachments/` behaviour.
- The `inlineBroken` size comparison is correct.
- Attachment order is unchanged for v0.1.0 campaigns.

The one real weakness is a test that depends on the host's file-name encoding. The remaining items are maintainability notes.

I read no necessity reference and report no necessity findings. I found no guard that protects against a state that cannot occur. The `getOrDefault` fallback in the dry-run rewrite is reachable for an empty `cid:` reference, and a test covers it.

## Warnings

### WR-01: Test creates non-ASCII file names, so it fails on hosts with a non-UTF-8 file-name encoding

**File:** `morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/DryRunTest.java:131-134`
**Issue:** `inlineSrcIsPercentEncodedAndHtmlSafe` writes the inline file `ü.png` through `SampleFixtures.writeInline`. It then asserts exact output: `cid:ü` in the stdout line, `inline/%C3%BC.png` in the HTML, and the sort order `50%.png, a&b.png, x#1.png, ü.png`. The file-name round trip depends on `sun.jnu.encoding`, which follows the locale. Under the POSIX/C locale, which is common in minimal CI containers, the JDK cannot represent `ü` in a path. `Files.writeString` then throws `InvalidPathException` or creates a mangled name, and the test fails for a reason unrelated to the code under test. On macOS, HFS+ normalises names to NFD. The listed ID is then `u` plus a combining diaeresis and no longer equals the NFC `cid:ü` in the body, so the pre-flight reports a missing image. The existing suite only puts non-ASCII text in file contents (`Köln`), never in file names, so this is a new host dependency. CLAUDE.md requires `mvn verify` to pass offline on every host that builds the project, and CI runs on Java 17.
**Fix:** Keep the percent-encoding coverage but skip it when the platform cannot hold the name. For example:

```java
@Test
void inlineSrcIsPercentEncodedAndHtmlSafe() throws IOException {
    org.junit.jupiter.api.Assumptions.assumeTrue(
            java.nio.charset.Charset.forName(System.getProperty("sun.jnu.encoding"))
                    .newEncoder().canEncode('ü'),
            "file names cannot hold non-ASCII characters on this host");
    ...
```

Alternatively, move the `ü` case into its own test with that assumption and keep the `&`, `%` and `#` cases unconditional.

## Info

### IN-01: Core logic is copied into the sample and kept in step only by a test and a comment

**File:** `morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/Campaign.java:47-48, 216-220, 255-257`
**Issue:** Three pieces of core behaviour are duplicated here:
- the `CID_REFERENCE` regex;
- the image-type rule, including its exact wording, "inline image X has content type Y, which is not an image type";
- the `^attachments\[0\]: ` prefix that `withoutField` strips from the core's messages.

If the core changes any of them, the pre-flight scan and `build()` drift apart silently. A campaign then passes pre-flight and fails per row, or the reverse. `CampaignTest.cidPatternAgreesWithTheCore` guards the regex with eight cases, and the exact-string assertions guard the wording, so the drift would be caught in CI, but only for the cases listed. The same `PLACEHOLDER` precedent already exists in this file, so the choice is consistent with the project.
**Fix:** Leave it for now, because exposing the core's pattern would widen the public API and conflicts with Rule 0. If the core ever gains a public "check one inline image" helper, replace the probes with it. At minimum, add a comment in `Mail.java` next to its private `CID_REFERENCE` saying that the sample copies it.

### IN-02: Two throw-away `Mail.build()` probes per image, with a forced `image/png`, are an indirect way to reuse the core's checks

**File:** `morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/Campaign.java:195-221`
**Issue:** `checkInline` builds a fake mail twice per file. The first probe resolves the type. The second forces `image/png` and a synthetic HTML body so that only the content-ID rule can fire. The image rule is then re-implemented by hand, and the "type is non-null" branch hides the second probe when the first fails. The result is correct, and the comments explain it, but it is the most intricate code in the phase for a sample, and Rule 0 prefers a short direct method. It also depends on `Mail.using("probe")...build()` not needing a `from`, an account or a transport. That is true today only because `build()` does not require them.
**Fix:** Not urgent. If this code is touched again, consider a single probe per file with the real type and a matching `cid:` body, and map its one `InvalidMailException` to one problem line. This would lose the "two defects, two lines" behaviour that tests D-01 pin, so only do it if that rule is dropped.

### IN-03: Over-long lines and a magic number

**File:** `morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/Campaign.java:38, 215-216`; `morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/SendCommand.java:173, 255`
**Issue:** The rest of both files wraps at about 100 columns. `Campaign.java:38` is the record header at 102 columns. `SendCommand.java:173` is a single-line comment of about 125 columns, and line 255 is a Javadoc line over 100. `regionMatches(true, 0, "image/", 0, 6)` hard-codes the prefix length, which can drift if the literal changes.
**Fix:** Wrap the long lines. Use a constant such as `private static final String IMAGE_PREFIX = "image/";` and `IMAGE_PREFIX.length()`.

### IN-04: Pre-flight scans the unrendered template for `cid:` references, so a placeholder inside a reference is reported as a missing image

**File:** `morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/Campaign.java:233-251`
**Issue:** `CID_REFERENCE` runs on the raw `body.html`, while `build()` runs it on each rendered body. A template like `<img src="cid:$$logo$$">` is reported as `body.html: cid:$$logo$$ has no image in inline/`, and the matching file as "no cid:logo reference". The core would accept the same template if every row's value were `logo`. Conversely, a reference that only a recipient value adds, such as a note column containing "see cid:x", passes this scan and fails at build, per row. `PreflightTest.cidThatOnlyARecipientValueAddsIsReportedByBuild` covers that case, and the message is correct there. The first direction has no test or note. The core's rule that every inline image must be referenced in every mail means a per-row choice of image cannot work anyway, so the practical impact is a constant-valued column used as an image ID.
**Fix:** State in the sample's documentation, or in a one-line comment on `checkInline`, that the scan reads the template as written and that `cid:` references cannot be built from placeholders.

---

_Reviewed: 2026-11-23_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
