# Phase 5: Inline Images in Core and Graph - Pattern Map

**Mapped:** 2026-11-22
**Files analyzed:** 9 (all modifications; no new production files)
**Analogs found:** 9 / 9 (all are the files themselves or sibling files; all verified git-tracked)

## File Classification

| Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---------------|------|-----------|----------------|---------------|
| `morimail/src/main/java/de/morisoft/morimail/Mail.java` | builder/validation | transform + file-I/O checks | itself (`resolveAttachments`, `attach(...)`) | exact |
| `morimail/src/main/java/de/morisoft/morimail/Attachment.java` | model (record) | n/a, Javadoc only | itself | exact |
| `morimail/src/main/java/de/morisoft/morimail/graph/GraphTransport.java` | transport | request-response | itself (`requestBody`) | exact |
| `morimail/src/test/java/de/morisoft/morimail/MailBuilderTest.java` | test | request-response | itself (`rejectsContentId`, `file()`, `valid()`) | exact |
| `morimail/src/test/java/de/morisoft/morimail/SendThroughRecordingTest.java` | test | request-response | itself | exact |
| `morimail/src/test/java/de/morisoft/morimail/graph/GraphRequestTest.java` | test | request-response (WireMock) | itself (`fullMailArrivesAsTheDocumentedJson`) | exact |
| `morimail/src/test/java/de/morisoft/morimail/graph/GraphValidateTest.java` | test | request-response | itself (`sparse`, `withFiles`) | exact |
| `morimail/src/test/java/de/morisoft/morimail/LiveGraphTest.java` | test (live, owner-run) | request-response | itself (`sendsOneMailWithASmallAttachment`) | exact |
| `docs/architecture.md` | doc | n/a | existing "Differences from the Microsoft documentation" section | role-match |

`RecordingTransport` and `GraphTransport.validate` need NO code change (they store `MailMessage` as is, and iterate `m.attachments()`).

## Pattern Assignments

### `Mail.java` (builder, transform)

**Analog:** the same file. Existing code to modify.

**Imports** (lines 3-11): `IOException, Files, Path, ArrayList, HashMap, List, Locale, Map, Objects`. Add `java.util.HashSet/LinkedHashSet, Set` and `java.util.regex.Matcher, Pattern` (JDK only; `ImportRulesTest` keeps core JDK-only).

**Builder method pattern** (lines 279-282, 311-314): builder methods only `requireNonNull` and add to `attachments`:
```java
public Builder attach(Path file) {
    ...
    attachments.add(new Attachment(null, null, file, null));
public Builder attach(Attachment attachment) {
    Objects.requireNonNull(attachment, "attachment");
    attachments.add(attachment);
```
New `inline(Path image, String contentId)`: `requireNonNull` both, `attachments.add(new Attachment(null, null, image, contentId))`, return `this`. Javadoc with thread-safety note and `@throws NullPointerException`; update `attach(Attachment)` Javadoc (lines ~304-309) and `build()` Javadoc (~320) that still say a non-null contentId is rejected.

**build() call site** (line 361): `List<Attachment> resolved = resolveAttachments();` becomes `resolveAttachments(html)`. `html` is the local set from the rendered template (lines 340-342), may be null.

**Core loop to extend** (lines 390-428): field-prefixed first-problem-only messages:
```java
String field = "attachments[" + i + "]: ";
...
if (!Files.isReadable(file)) { throw new InvalidMailException(field + fileName + " is not readable"); }
if (attachment.contentId() != null) {                       // lines 412-415: REPLACE this block
    throw new InvalidMailException(field
            + "contentId is not supported (inline images are a later milestone)");
}
String filename = attachment.filename() != null ? attachment.filename() : fileName.toString();
String contentType = attachment.contentType() != null
        ? attachment.contentType() : contentTypeOf(file, filename);
if (filename.isBlank() || MailAddress.hasLineBreak(filename)) {
    throw new InvalidMailException(field + "filename is blank or contains a line break");
}
if (MailAddress.hasLineBreak(contentType) || contentType.indexOf('/') < 1) {
    throw new InvalidMailException(field + "contentType is not a media type");
}
resolved.add(new Attachment(filename, contentType, file, null));   // line 427: pass contentId through (Pitfall 1)
```
Put the inline checks AFTER the contentType checks (type is resolved): content ID shape (blank, `MailAddress.hasLineBreak`, `Character.isWhitespace`, `"` `'` `(` `)` `<` `>`; do not echo the value), `contentType.regionMatches(true, 0, "image/", 0, 6)`, `Set.add` duplicate check (use `LinkedHashSet`). Then, after the loop, scan `html` with a `CID_REFERENCE` pattern constant (`(?i)(?<![\p{L}\p{N}])cid:([^"'\s)>]*)`, skip empty IDs, case-sensitive compare) and throw "no inline image for cid:X" (truncate echoed X to 64 chars) before "inline image X is not referenced". Scan always, also with zero inline images.

**Helpers to reuse:** `MailAddress.hasLineBreak(String)` (package-private, `MailAddress.java:69-71`); `contentTypeOf` (lines 432-450, built-in `CONTENT_TYPES` table at lines 42-56, then `probeContentType`, then `application/octet-stream`, which fails the image check; no change).

---

### `GraphTransport.java` (transport, request-response)

**Analog:** itself, `requestBody` lines 153-168. `validate` (about lines 85-125) loops over `m.attachments()` and stays unchanged.
```java
ObjectNode node = attachments.addObject();
node.put("@odata.type", "#microsoft.graph.fileAttachment");
node.put("name", attachment.filename());
node.put("contentType", attachment.contentType());
node.put("contentBytes", Base64.getEncoder().encodeToString(content));
```
Add directly after (bare ID, no `contentLocation`, no `isInline:false` for regular ones):
```java
if (attachment.contentId() != null) {
    node.put("isInline", true);
    node.put("contentId", attachment.contentId());
}
```

---

### `MailBuilderTest.java` (test)

**Analog:** itself. Helpers: `file(String name)` (lines 23-27, writes 3 bytes into `tempDir`) and `valid()` (line 33: `Mail.using("main").to("reader@example.org").subject("Hello").text("Plain text body")`). Replace `rejectsContentId` (lines ~388-397; asserts message contains `attachments[0]` and `contentId`). Assertion style: `assertThatThrownBy(() -> ...).isInstanceOf(InvalidMailException.class).hasMessageContaining(...)`. Use only png/jpg/jpeg/gif for accepted images and `.pdf` for the not-an-image case (Pitfall 5). Case list is in 05-RESEARCH.md "Builder test cases".

### `SendThroughRecordingTest.java` (test)

**Analog:** itself (lines 9-39): `MailAccount.builder("main").transport("recording").defaultFrom(...)`, `Mailer.of(account)`, `(RecordingTransport) account.transport()`, `mailer.send(mail)`, assert on `transport.sent().get(0)`. Add one test: HTML `<img src="cid:logo">`, `.attach(pdf).inline(png, "logo")`, assert two attachments, inline one has `contentId`, `filename`, `contentType "image/png"`. Needs `@TempDir`.

### `GraphRequestTest.java` (test, WireMock)

**Analog:** `fullMailArrivesAsTheDocumentedJson` (lines ~110-148): builds message, `transport(wm).send(message)`, builds expected `ObjectNode` tree, `assertThat(bodyOf(request)).isEqualTo(expected)`; helpers `stubSend()` (line 66), `fileAttachment(name, type, bytes)` (line 96), `sends()`, `file(name, bytes)`. New test: one inline and one regular attachment; expected inline node = `fileAttachment(...)` plus `put("isInline", true).put("contentId", "logo")`; regular node has no `isInline`. Leave the existing full-mail test untouched.

### `GraphValidateTest.java` (test)

**Analog:** itself (lines 40-75): `@TempDir Path dir`, `StubTokenProvider stub`, `transport(wm)`, `sparse(name, size)` (RandomAccessFile.setLength), `base()`, `withFiles(text, files...)`, constant `LIMIT_PER_FILE`. Add a helper like `withFiles` that builds `.html("<img src=\"cid:big\">").inline(file, "big")` (file must be `big.png`, Pitfall 8). Cases: inline 3 MiB rejected with existing message; inline plus regular over 3 MiB total; estimate over 4 MiB; no token request (assert `stub` scopes empty / no WireMock request).

### `LiveGraphTest.java` (live test)

**Analog:** `sendsOneMailWithASmallAttachment` (line 118) plus class Javadoc (lines 18-40). Uses `tmp`, `from`, `to`, `mailer()`, `flow()`, `describe(e)`, `System.out.println("live ...: flow " + flow() + ...)`. Add `sendsOneMailWithAnInlineImage` and `tinyPng()` as sketched in RESEARCH "Live test method" (ImageIO 64x64, no metadata). Update class Javadoc. Keep `@Tag("live")`. Imports to add: `BufferedImage`, `ByteArrayOutputStream`, `ImageIO`.

### `docs/architecture.md` (doc)

Add under the existing "Differences from the Microsoft documentation" section: sendMail page has no inline example; properties come from fileAttachment/attachmentItem pages; bare `contentId`; no `contentLocation`; regular attachments carry no `isInline`; live status unconfirmed. Add a deviation row for spec §5/§5.1 (build() no longer rejects contentId). Do not edit `docs/spec.md` (Phase 8).

## Shared Patterns

### First-problem-only InvalidMailException from build()
**Source:** `Mail.java` lines 345-390. **Apply to:** all new inline checks. Fixed text plus field or content ID; never echo addresses or data values; do not echo an invalid content ID value.

### Javadoc and doclint
**Source:** `PublicTypesDocumentThreadSafetyTest`. **Apply to:** `inline(...)`, updated `attach(Attachment)`, `build()`, `Attachment` Javadoc. Verify with `./mvnw -Prelease verify -Dgpg.skip -pl morimail -am`.

### Core imports only JDK
**Source:** `ImportRulesTest`. **Apply to:** `Mail.java` additions (regex, collections only). `graph` must not import `entra`.

### Test fixtures
`example.com` / `example.org` addresses only; images generated in the build or tiny dummy bytes; no checked-in binaries.

## No Analog Found

None. The `cid:` regex scan is new logic, but it sits inside the existing `resolveAttachments` pattern, and RESEARCH.md has a tested regex.

## Metadata

**Analog search scope:** `morimail/src/main`, `morimail/src/test`, with `git ls-files` confirming the tracked Java files (`Mail.java` verified tracked; all others listed by `git ls-files`).
**Files scanned:** about 10 read in part.
**Pattern extraction date:** 2026-11-22
