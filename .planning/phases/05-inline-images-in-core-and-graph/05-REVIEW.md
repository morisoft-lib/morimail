---
phase: 05-inline-images-in-core-and-graph
reviewed: 2026-11-23T00:00:00Z
depth: standard
files_reviewed: 9
files_reviewed_list:
  - docs/architecture.md
  - morimail/src/main/java/de/morisoft/morimail/Attachment.java
  - morimail/src/main/java/de/morisoft/morimail/Mail.java
  - morimail/src/main/java/de/morisoft/morimail/graph/GraphTransport.java
  - morimail/src/test/java/de/morisoft/morimail/LiveGraphTest.java
  - morimail/src/test/java/de/morisoft/morimail/MailBuilderTest.java
  - morimail/src/test/java/de/morisoft/morimail/SendThroughGraphTest.java
  - morimail/src/test/java/de/morisoft/morimail/SendThroughRecordingTest.java
  - morimail/src/test/java/de/morisoft/morimail/graph/GraphValidateTest.java
findings:
  critical: 0
  warning: 2
  info: 3
  total: 5
  necessity: 0
status: issues_found
---

# Phase 5: Code Review Report

**Reviewed:** 2026-11-23
**Depth:** standard
**Files Reviewed:** 9
**Status:** issues_found

## Summary

I reviewed the phase 05 diff (`6c9edce..HEAD`) for inline images in `Mail.Builder` and `GraphTransport`, and the tests that go with it. I also read `docs/spec.md` §10.3.

The core logic is sound. I traced the reference regex against `contentIdProblem`. Every character that ends a reference id (`"`, `'`, ASCII whitespace, `)`, `>`) is rejected in a content id, so every accepted id can be found again. The Java `\s` set is a subset of `Character.isWhitespace`, so the two checks do not drift apart in a harmful way. The cut at 64 code points is surrogate-safe. Duplicate detection, the `image/` check, check ordering and idempotent `build()` are correct. The Graph payload carries `isInline` and `contentId` only for inline images, and the size checks count inline images like any other attachment. The tests assert exact messages and the exact Graph JSON.

No critical issues. The two warnings concern recipient-derived text in exception messages and false positives of the `cid:` scan outside attributes. Both stem from decisions the phase documents, so I flag the risk, not the intent.

The necessity category was assessed (the rubric was read). I found no guards against states that cannot occur. The `html != null` guard in `checkReferences` is reachable for text-only mails.

## Warnings

### WR-01: Id read from rendered HTML (possibly recipient data) goes into the exception message without control-character cleaning

**File:** `morimail/src/main/java/de/morisoft/morimail/Mail.java:518-525` (regex at `:48-49`)
**Issue:** `checkReferences` scans the rendered HTML, which includes template data (`TemplateRenderer.render(template, data)`). It puts up to 64 code points of the matched id into `InvalidMailException` as `"no inline image for cid:" + shown`. The regex stops only at `"`, `'`, ASCII whitespace, `)` and `>`. A recipient value such as `cid:` followed by `\u0085`, `U+2028`, `\u001b` or another control character is therefore echoed into the message. In a serial run that message normally ends up in logs or a journal. That allows log-line forging (NEL, U+2028) and terminal escapes. The Javadoc and `docs/architecture.md` say the message "never repeats the rejected value", yet it repeats recipient-controlled text. Other messages in `Mail` echo only builder-supplied values. The same class of echo exists for the content type at `Mail.java:474-475`, which is builder-supplied and therefore low risk.
**Fix:** Do not echo the id raw. Either replace any code point that is not printable (`Character.isISOControl`, `Character.getType(cp) == Character.LINE_SEPARATOR`, `PARAGRAPH_SEPARATOR`, `FORMAT`) with `?` before cutting, or drop the id from the message and name only the count and position. For example:
```java
String shown = id.codePoints().limit(64)
        .map(cp -> Character.isISOControl(cp) || Character.getType(cp) == Character.LINE_SEPARATOR
                || Character.getType(cp) == Character.PARAGRAPH_SEPARATOR ? '?' : cp)
        .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
        .toString();
```

### WR-02: The `cid:` scan covers the whole rendered HTML, so prose, URLs and unquoted markup can fail a mail that has no inline image

**File:** `morimail/src/main/java/de/morisoft/morimail/Mail.java:48-49, 511-517`
**Issue:** Every `cid:` not preceded by a letter or digit is a reference. This applies to the whole body, including text nodes, comments, `<script>`/`<style>` and URLs. After this phase, any existing template or any recipient value containing such text makes `build()` throw `InvalidMailException`, even when no inline image is used. Examples are `?utm_cid:12`, `ref=cid:123`, `ref/cid:5`, `x-cid:7`, `_cid:`, and `<!-- cid:logo -->`. The lookbehind excludes only letters and digits, not `_ - = / ?`.

The end-of-id rule also mis-reads valid markup, which gives a confusing failure instead of a match:
- Unquoted self-closing: `<img src=cid:logo/>` reads the id as `logo/`, so the message is `no inline image for cid:logo/`.
- Reference in text directly before a tag: `<p>cid:logo</p>` reads `logo</p`. The regex does not stop at `<`, although `contentIdProblem` rejects `<` and `(` in an id. The architecture note says the two rules are symmetric, but they are not: `(` and `<` are rejected in an id yet do not end a reference.

The owner amended D-04 on 2026-11-22 and the behaviour is documented, so this is not a defect in intent. The risk is that the false positives hit mails that worked before the phase.
**Fix:** Consider two changes, or record the trade-off in the architecture note:
1. Restrict matching to attribute values and `url(...)` by requiring a preceding `=`, `(`, quote or `:`+space context, for example `(?<=[=("'\s])cid:` or by stripping comments first.
2. At least add `<` to the id terminator set (`[^"'\s)<>]`) and strip one trailing `/` when the match ends at `>`, so `src=cid:logo/>` and `>cid:logo</p>` resolve.

## Info

### IN-01: Content type for inline images depends on the platform for formats outside the built-in map

**File:** `morimail/src/main/java/de/morisoft/morimail/Mail.java:62-75, 535-552`
**Issue:** `CONTENT_TYPES` maps only `png`, `jpg`, `jpeg` and `gif` to `image/*`. For `.svg`, `.webp`, `.bmp`, `.ico` or `.tif`, `contentTypeOf` falls back to `Files.probeContentType`, which varies with the OS and its `mime.types`. `inline(Path, String)` for `logo.webp` can therefore build on one machine and fail with "not an image type" (`application/octet-stream`) on another. The Javadoc says "best effort", so this is documented. The `inline()` Javadoc only points to `attach(Attachment)` as the workaround.
**Fix:** Add `webp`, `bmp` and `svg` (`image/svg+xml`) to the map, or name the dependency on the platform in the `inline()` Javadoc.

### IN-02: `docs/spec.md` still states the MVP rule, and the source comments cite internal decision IDs

**File:** `docs/spec.md:124-125, 177`; `morimail/src/main/java/de/morisoft/morimail/Mail.java:48`; `morimail/src/main/java/de/morisoft/morimail/graph/GraphTransport.java:167`
**Issue:**
- `CLAUDE.md` says the spec wins over other documents. The spec lines 124-125 and 177 still say `build()` rejects a non-null `contentId`, which now contradicts both the code and §10.3. `docs/architecture.md` records the difference and says the spec text is updated "with the release docs", so a reader of the spec alone gets wrong information until then.
- The comment on `CID_REFERENCE` cites "D-04 as amended 2026-11-22, D-05, D-06". Those IDs live in planning artifacts and mean nothing to a reader of the public source. Both this comment and the one in `GraphTransport` are single very long lines.
**Fix:** Update spec lines 124-125 and 177 to point at §10.3, and replace the decision IDs with the rule they stand for.

### IN-03: Case-sensitive content ID uniqueness and the live status of the Graph properties

**File:** `morimail/src/main/java/de/morisoft/morimail/Mail.java:477-480`; `docs/architecture.md` (inline-image row)
**Issue:** `inlineIds` compares exactly, so `Logo` and `logo` are two distinct inline images. The HTML scan also compares exactly. Exchange and mail clients may match Content-IDs case-insensitively, and this repository does not show either way. The Graph properties `isInline` and `contentId` follow the resource pages and are "not yet confirmed" live, as the architecture note says. Nothing here is a defect yet; the owner's run of `LiveGraphTest.sendsOneMailWithAnInlineImage` decides it, and it should also show whether two ids that differ only in case cause trouble.
**Fix:** After the live run, either record that case-differing ids are safe or reject ids that collide ignoring case (`toLowerCase(Locale.ROOT)` in the uniqueness set).

---

_Reviewed: 2026-11-23_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
