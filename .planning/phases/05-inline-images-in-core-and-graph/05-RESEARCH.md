# Phase 5: Inline Images in Core and Graph - Research

**Researched:** 2026-11-22
**Domain:** Java 17 library internals (mail builder validation) plus the Microsoft Graph `sendMail` file-attachment JSON
**Confidence:** HIGH for the core and the Graph property names. MEDIUM for how Outlook clients treat the content ID, which only the owner's live run can confirm.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Data model (carried forward, not re-asked)**
- **D-01:** An inline image is an `Attachment` with `contentId != null`, kept in the same `MailMessage.attachments` list (spec §5). No new record, no second list. `RecordingTransport` already records `MailMessage` and so holds inline images with their content IDs. INLN-06 needs at most a test and Javadoc, plus a helper only if a test truly needs one.
- **D-02:** No `validate` code for "a transport that cannot send inline images", because both in-repo transports handle them (STATE.md, Rule 0).
- **D-03:** The v0.1.0 rejection of a set `contentId` in `Mail.Builder.build()` (`Mail.java`, around line 412) is replaced by the checks below.

**What counts as a `cid:` reference**
- **D-04:** Any `cid:` anywhere in the **rendered** HTML counts: `src="cid:x"`, CSS `url(cid:x)`, `background=`. A simple regex, no HTML parsing. A literal "cid:" in body text also counts, which is accepted.
- **D-05:** The prefix `cid:` matches in any case (`CID:`, `Cid:`). The ID after it is compared to `contentId` exactly and case-sensitively. No URL-decoding.
- **D-06:** A reference's ID runs until `"`, `'`, whitespace, `)`, `>`, or the end of input.
- **D-07:** Only the HTML body is scanned. A mail with inline images and no HTML body fails, because the images are unreferenced (INLN-03).

**Builder API and content ID rules**
- **D-08:** Public API: `.inline(Path image, String contentId)` only, as in the spec. `.attach(Attachment)` with a non-null `contentId` is also an inline image and gets the same checks. That route covers a custom file name or content type, so there is no `.inline(path, id, contentType)` overload. Reversibility: one-way, a published public method on `Mail.Builder` in 0.2.0 on Maven Central.
- **D-09:** Content ID rules: reject blank, CR/LF, whitespace, and `"` `'` `(` `)` `<` `>`. These are the characters that end a reference (D-06) plus angle brackets, so any accepted ID can be matched. No other character rules.
- **D-10:** The image type check passes when the resolved content type starts with `image/` (case-insensitive). Resolution works as for regular attachments: the built-in table (png, jpg, jpeg, gif), then `Files.probeContentType`, then `application/octet-stream`, which fails the check. A caller-supplied type via `attach(Attachment)` is checked the same way.
- **D-11:** Inline images get the same exists/readable/filename checks as regular attachments (INLN-05).

**Error messages**
- **D-12:** All inline problems are `InvalidMailException`s thrown from `build()`. `.inline()` rejects only `null` arguments, with `NullPointerException` (`Objects.requireNonNull`), as `.attach()` does.
- **D-13:** `build()` reports the **first** problem only, as all other `build()` checks do. No collecting.
- **D-14:** Messages name the content ID, for example "no inline image for cid:logo", "inline image logo is not referenced", "duplicate inline content ID logo". The template author chooses the content ID, as with placeholder names, so it is not recipient data. Messages still never echo addresses or data values.

**Size limits (Graph `validate`)**
- **D-15:** Keep today's limits unchanged. Inline images count in the same loop as regular attachments: each file under 3 MiB, all files together under 3 MiB (spec §6.2), and the request estimate (base64 plus body plus allowance) at or under 4 MiB. Roadmap success criterion 4 and GRAPH-11 say "together ... 4 MiB". That wording mixes up the 3 MiB total with the 4 MiB request estimate. The roadmap and REQUIREMENTS.md wording was corrected on 2026-11-22. Do not change the code.
- **D-16:** Size-limit messages stay the same for inline images and regular attachments ("attachment logo.png has N bytes ..."). No new message variants.
- **D-17:** Rejection still happens in `validate`, before any token request or HTTP call (existing behaviour).

### Claude's Discretion
- The Graph JSON property names for inline (`isInline`, `contentId`, possibly `contentLocation`), and whether `contentId` takes angle brackets. Take them from the current Microsoft `fileAttachment` and `user: sendMail` pages before coding, and record any difference from the spec in `docs/architecture.md`. Regular attachments must not be marked inline. Whether to send `isInline: false` explicitly for them follows the docs.
- The live test: one new method in `LiveGraphTest` with a tiny PNG generated in the test (no metadata, nothing checked in), referenced as `cid:` in the HTML. Same environment-variable configuration as the existing methods. Claude never runs it.
- The exact regex, the exact message wording within D-14, and the order of checks in `build()`.
- Test layout (`MailBuilderTest`, `GraphRequestTest`, `GraphValidateTest`, `RecordingTransportTest`, `SendThroughRecordingTest`) and tiny test images generated in the build.

### Deferred Ideas (OUT OF SCOPE)
- The HTML image helper `morimail-html` (spec §10.4, Phase 7 of v0.2.0): rewriting `<img src>` to `cid:`, remote downloads, a parser dependency. Not in this phase; the core does no rewriting and generates no content IDs.
- The sample's `inline/` directory (Phase 6) and docs/release readiness (Phase 8).
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| INLN-01 | `.inline(Path, String)`; blank or line-break content ID rejected | New builder method plus `checkContentId` in the attachment loop (Architecture Pattern 1) |
| INLN-02 | `cid:` reference with no inline image fails | Regex scan of the rendered `html` local in `build()` (Pattern 2) |
| INLN-03 | Unreferenced inline image fails, also with no HTML body | Same scan; `html == null` gives an empty reference set (Pattern 2) |
| INLN-04 | Duplicate content ID fails | `Set<String>` in the attachment loop (Pattern 1) |
| INLN-05 | Non-image content type fails; same file checks | `image/` prefix check after the existing type resolution (Pattern 1) |
| INLN-06 | `recording` records inline images with content ID | Already true: `RecordingTransport` stores the `MailMessage` unchanged. Test only (Code Examples) |
| GRAPH-10 | Graph sends inline file attachments with content ID | Add `isInline: true` and `contentId` to the `fileAttachment` node (Graph JSON section) |
| GRAPH-11 | Inline counts toward existing limits, before any token request | `validate` iterates `m.attachments()` already. Add tests only (Pattern 3) |
| GRAPH-12 | Owner-run live test with one inline image | One new `LiveGraphTest` method with an `ImageIO`-generated PNG (Code Examples) |
</phase_requirements>

## Project Constraints (from CLAUDE.md)

- **Rule 0:** simplest code. Two extension points only. No new interface, factory, strategy, SPI, reflection or dependency. No option or hook the spec does not ask for. If something elaborate seems needed, stop and ask.
- **Dependencies:** library jar stays Jackson only. No dependency is added in this phase.
- **Docs first:** read the current Microsoft page before coding against Graph. Note differences from the spec in `docs/architecture.md`. Done below.
- **Public repo:** no secrets, tenant IDs, real addresses, internal hosts or customer names in any file, fixture or commit. Use `example.com`, `example.org`. Generate test images in the build.
- **Never** send real mail, publish, deploy or tag. The live test is run by the owner.
- **Javadoc:** public types state thread safety, and doclint stays clean (`PublicTypesDocumentThreadSafetyTest`). `Mailer`, transports and token providers are safe for concurrent use.
- **Logging:** never log tokens, secrets, assertions or full bodies. Recipients at DEBUG only.
- **Commit identity:** `Stephan Wald <stephan@morisoft.de>`. Workspace `GIT_*` variables override local config, so export the identity before every commit (auto-memory note).
- **Build gate:** `./mvnw verify` green offline before commit. Release build `./mvnw -Prelease verify -Dgpg.skip -pl morimail -am` stays green.
- **Core imports only the JDK** (`ImportRulesTest`). `graph` and `entra` do not import each other.

## Summary

Phase 5 is a small change in three places. In `Mail.Builder` it adds `inline(Path, String)` and replaces the one `contentId` rejection with four checks (content ID shape, image type, unique IDs, `cid:` references in both directions). In `GraphTransport.requestBody` it adds two JSON properties when `contentId != null`. In the tests it adds cases, one live-test method and a doc note. `validate` needs no code change because it already counts every entry of `m.attachments()`, and `RecordingTransport` needs no code change because it stores the `MailMessage` as built. [VERIFIED: morimail/src/main/java/de/morisoft/morimail/graph/GraphTransport.java:98, `for (Attachment attachment : m.attachments()) {`]

The Graph property names are settled by the current Microsoft pages. A file attachment marked inline carries `"isInline": true` and `"contentId": "<id>"`, and the HTML refers to it as `<img src="cid:contentId">`. `contentLocation` is documented as unsupported and must not be sent. One finding differs from what CONTEXT.md assumed: the `user: sendMail` page has **no** inline example (its only attachment example, Example 3, is a plain text file). The inline property names come from the `fileAttachment`, `attachment` and `attachmentItem` resource pages instead. Record this in `docs/architecture.md`.

The only real design care is in `build()`: pass the rendered `html` into the attachment resolution, keep `contentId` on the resolved `Attachment` (today line 427 hard-codes `null`), and make the regex terminator set and the content ID rejection set agree so that every accepted ID can be matched.

**Primary recommendation:** Extend `resolveAttachments` to take the rendered `html`, validate inline entries inside the existing loop, run one case-insensitive `cid:` regex over the HTML afterwards, and in `GraphTransport` add `isInline: true` plus `contentId` for inline entries only (no `isInline: false` for regular ones). Write no other production code.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Inline image API (`inline(...)`) and all `cid:` / content ID / image-type checks | Core (`de.morisoft.morimail`, `Mail.Builder.build()`) | — | Spec §5.1: `build()` checks what every transport needs. Core must not assume Graph. |
| Recording inline images for tests | Core test-jar (`RecordingTransport`) | — | Stores `MailMessage` as is, so nothing to add (D-01). |
| `isInline` / `contentId` JSON properties | Graph transport (`graph` package) | — | Transport-specific wire format. Core stays free of Graph names. |
| Size limits including inline images | Graph transport `validate` | — | Limits are transport-specific and checked in `validate` (project constraint). |
| Live confirmation that the image renders inline | Owner, manual (`LiveGraphTest`, tag `live`) | — | Claude never sends real mail. |

## Standard Stack

No new libraries. Everything uses what is already on the classpath.

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| JDK `java.util.regex`, `java.nio.file` | JDK 17 API (built with `--release 17` on JDK 21) | `cid:` scan, file checks | Already used in the core. The core imports only the JDK. |
| jackson-databind | 2.23.0 (BOM) | Graph request JSON (`ObjectNode`) | Already used in `GraphTransport`. [VERIFIED: .claude/CLAUDE.md stack table] |

### Supporting (test scope, already present)
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| JUnit Jupiter | 6.1.3 | Tests | All new tests |
| AssertJ | 3.27.7 | Assertions | All new tests |
| WireMock `org.wiremock:wiremock` | 3.13.2 | Fake Graph endpoint in `GraphRequestTest` | Inline JSON test |
| `javax.imageio.ImageIO` (JDK, `java.desktop`) | JDK | Generate a tiny PNG in tests | Live test, optional builder tests |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `ImageIO` generated PNG | Hand-written 67-byte PNG literal | A literal has no `java.desktop` need but a 1x1 pixel is invisible to the owner who must see "renders inline". Use `ImageIO` with a 64x64 solid fill. |
| Regex scan | HTML parser (jsoup) | Locked out by D-04 and Rule 0 (no dependency). |

**Installation:** none.

## Package Legitimacy Audit

No external packages are installed in this phase. Not applicable.

**Packages removed due to [SLOP] verdict:** none
**Packages flagged as suspicious [SUS]:** none

## Architecture Patterns

### System Architecture Diagram

```
caller code
  Mail.using(id).template(t, data) / .html(..)
                .inline(Path, cid)            -> Attachment(null, null, path, cid)   [list shared with attach()]
                .attach(Attachment w/ cid)    -> same list
                .build()
                   |
                   v
   render template (TemplateRenderer)  -> local `html`
                   |
   existing checks: recipients, subject, body, addresses, subject line break
                   |
   resolveAttachments(html)
      for each attachment:
         file checks (exists, regular, readable)            [existing]
         resolve filename + contentType                     [existing]
         if contentId != null:
              contentId shape   -> InvalidMailException
              not image/*       -> InvalidMailException
              duplicate id      -> InvalidMailException
         keep contentId in the resolved Attachment          [was: null]
      scan html with CID regex -> Set<String> referenced
         referenced id not in inline ids -> "no inline image for cid:X"
         inline id not in referenced     -> "inline image X is not referenced"
                   |
                   v
   Mail(accountId, MailMessage(..., attachments))
                   |
        Mailer.send -> transport.validate(message)           [Graph: size loop, unchanged]
                   |                     |
          recording transport      Graph requestBody:
          stores MailMessage         fileAttachment node
                                     + isInline:true, contentId (only if contentId != null)
                                     -> POST /users/{from}/sendMail
```

### Recommended Project Structure

No new files in `src/main`. Changes:

```
morimail/src/main/java/de/morisoft/morimail/
├── Mail.java              # inline(), checks, regex, Javadoc of attach(Attachment) and build()
├── Attachment.java        # Javadoc only (no longer "rejects a non-null value")
└── graph/GraphTransport.java   # 4 lines in requestBody
morimail/src/test/java/de/morisoft/morimail/
├── MailBuilderTest.java        # replace rejectsContentId, add inline cases
├── SendThroughRecordingTest.java   # inline recorded next to a regular attachment
├── LiveGraphTest.java          # one new method
└── graph/GraphRequestTest.java, GraphValidateTest.java   # inline JSON, limits
docs/architecture.md       # differences from the Microsoft documentation, deviation row
```

### Pattern 1: Validate inline entries inside the existing attachment loop

**What:** `resolveAttachments` already walks every `Attachment`, checks the file, and resolves filename and content type. Add the inline checks after the type is resolved, and carry `contentId` into the resolved record. Currently:

[VERIFIED: morimail/src/main/java/de/morisoft/morimail/Mail.java:412-415]
```java
                if (attachment.contentId() != null) {
                    throw new InvalidMailException(field
                            + "contentId is not supported (inline images are a later milestone)");
                }
```
and [VERIFIED: Mail.java:427] `resolved.add(new Attachment(filename, contentType, file, null));`

**Recommended shape** (sketch, not final wording):
```java
// in Builder
public Builder inline(Path image, String contentId) {
    Objects.requireNonNull(image, "image");
    Objects.requireNonNull(contentId, "contentId");
    attachments.add(new Attachment(null, null, image, contentId));
    return this;
}

// in build():   List<Attachment> resolved = resolveAttachments(html);

private List<Attachment> resolveAttachments(String html) throws InvalidMailException {
    List<Attachment> resolved = new ArrayList<>(attachments.size());
    Set<String> inlineIds = new HashSet<>();            // import java.util.HashSet / Set
    for (int i = 0; i < attachments.size(); i++) {
        ... existing file / filename / contentType checks, minus the old contentId throw ...
        String contentId = attachment.contentId();
        if (contentId != null) {
            String reason = contentIdProblem(contentId);   // null when fine
            if (reason != null) {
                throw new InvalidMailException(field + "contentId " + reason);
            }
            if (!contentType.regionMatches(true, 0, "image/", 0, 6)) {
                throw new InvalidMailException(field + "inline image " + contentId + " is not an image (content type " + contentType + ")");
            }
            if (!inlineIds.add(contentId)) {
                throw new InvalidMailException("duplicate inline content ID " + contentId);
            }
        }
        resolved.add(new Attachment(filename, contentType, file, contentId));
    }
    checkReferences(html, inlineIds);
    return resolved;
}
```

**Notes the planner must carry into tasks:**
- Content ID shape message must not echo a CR/LF-bearing ID. For a blank or line-break ID say "contentId is blank" / "contentId contains a line break" without the value (the existing `subject` and `filename` checks do the same).
- Existing helper for the line break test: [VERIFIED: morimail/src/main/java/de/morisoft/morimail/MailAddress.java:69-71] `static boolean hasLineBreak(String value) { return value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0; }` (package-private, same package as `Mail`, reusable).
- The content type of a caller-supplied `Attachment` is already used as is ([VERIFIED: Mail.java:418-419] `String contentType = attachment.contentType() != null ? attachment.contentType() : contentTypeOf(file, filename);`), so D-10 needs no extra branch.
- Check order inside the loop keeps the existing field-prefixed messages (`attachments[i]: ...`), so existing tests for missing/unreadable files stay valid.

### Pattern 2: The `cid:` scan, and why the ID and regex rules must agree

**What:** one `Pattern` constant on `Mail.Builder`, run with `find()` over the rendered `html` (skip when `html == null`). Scan **always**, even when the mail has no inline images, because INLN-02 says a reference with no matching image fails.

**Recommended regex** (tested in this session on JDK 21, see output below):
```java
private static final Pattern CID_REFERENCE =
        Pattern.compile("(?i)(?<![\\p{L}\\p{N}])cid:([^\"'\\s)>]*)");
```
Test run on the sample string
`<img src="cid:logo"> <td background=cid:bg> url(CID:x) Folic acid: 5mg <img src='cid:a b'> &quot;cid:z&quot; <a href="cid:">`:

| Pattern | Captured IDs |
|---------|--------------|
| `(?i)cid:([^"'\s)>]*)` (no lookbehind) | `logo`, `bg`, `x`, *(empty, from "acid:")*, `a`, `z&quot;`, *(empty, from `href="cid:"`)* |
| with `(?<![\p{L}\p{N}])` lookbehind | `logo`, `bg`, `x`, `a`, `z&quot;`, *(empty, from `href="cid:"`)* |

[VERIFIED: ran `java T.java` in the scratchpad this session; output copied above]

**Findings the planner should act on:**
1. Plain "cid:" matches inside ordinary words such as "acid:" ("Folic acid: 5mg") and yields an empty ID. D-04 accepts that a literal "cid:" in body text counts, but an empty ID would produce the confusing message "no inline image for cid:" for harmless prose. **Recommendation: ignore empty IDs** (a bare `cid:` with nothing after it is not a reference to anything). The lookbehind is an optional extra that removes "acid:", "decid:" style false positives. The exact regex is Claude's discretion (CONTEXT), so both are allowed; recommend both, mention in the plan, keep the Javadoc honest. If the owner wants strict D-04 ("a literal cid: in text counts"), drop the lookbehind and keep only the empty-ID skip.
2. The terminator class of the regex (`"`, `'`, ASCII whitespace, `)`, `>`) and the D-09 rejection set must cover each other: **no accepted ID may contain a character from the terminator set**, or the scan would cut the ID short and report a false "not referenced". D-09 rejects `"` `'` `(` `)` `<` `>` and whitespace, which includes the whole terminator set. Use `Character.isWhitespace(c)` (a superset of regex `\s`) for the whitespace test, plus the six characters, plus line breaks (a subset of whitespace, but the message should say "line break" for INLN-01).
3. `&quot;cid:z&quot;` yields the ID `z&quot;`. No URL or entity decoding (D-05, D-04). Do nothing; mention in Javadoc that quotes must be literal.
4. The ID that appears in a "no inline image for cid:X" message comes from the **rendered** HTML, so a recipient data value rendered into the HTML (for example `see cid:abc` in a name field) could appear in the message. D-14 treats the ID as template-author text. To keep "messages never echo data values" credible, truncate the ID in that one message (for example to the first 64 characters) or accept the leak. This is a small decision for the plan; recommend a truncation of the echoed ID, it costs one line.

**Both-direction check:**
```java
Set<String> referenced = new LinkedHashSet<>();   // first-seen order gives a stable message
if (html != null) {
    Matcher m = CID_REFERENCE.matcher(html);
    while (m.find()) { if (!m.group(1).isEmpty()) referenced.add(m.group(1)); }
}
for (String id : referenced) if (!inlineIds.contains(id)) throw new InvalidMailException("no inline image for cid:" + id);
for (String id : inlineIds)  if (!referenced.contains(id)) throw new InvalidMailException("inline image " + id + " is not referenced");
```
`inlineIds` should be a `LinkedHashSet` too so the "first problem" is deterministic (insertion order). Case-sensitive comparison per D-05.

**Order of the two reference checks:** pick "missing image" before "unreferenced". Either is allowed; the order must be stable and tested.

### Pattern 3: Graph transport changes, and what does not change

**`requestBody`** [VERIFIED: GraphTransport.java:162-166]:
```java
                ObjectNode node = attachments.addObject();
                node.put("@odata.type", "#microsoft.graph.fileAttachment");
                node.put("name", attachment.filename());
                node.put("contentType", attachment.contentType());
                node.put("contentBytes", Base64.getEncoder().encodeToString(content));
```
Add directly after:
```java
                if (attachment.contentId() != null) {
                    node.put("isInline", true);
                    node.put("contentId", attachment.contentId());
                }
```
Regular attachments get no `isInline` key. This keeps `GraphRequestTest.fullMailArrivesAsTheDocumentedJson` untouched, because that test builds its expected tree with `assertThat(bodyOf(request)).isEqualTo(expected)` [VERIFIED: GraphRequestTest.java:144] and its helper `fileAttachment(...)` emits only `@odata.type`, `name`, `contentType`, `contentBytes`. Jackson `ObjectNode` equality ignores key order.

**`validate`:** no change. It already includes the loop quoted above and its messages ("attachment " + attachment.filename() + " has " ...). Inline images use `attachment.filename()`, which `build()` resolved from the file name, so the message names the file (success criterion 4: "names the file or the limit"). Add tests only (see Validation below).

**`send` / token ordering:** `Mailer.send` calls `validate` before `transport.send` (documented in `docs/spec.md` §5.1), so rejection precedes any token request. Keep the existing assertion style from `GraphValidateTest` (stub token provider records scopes; assert none).

### Anti-Patterns to Avoid
- **A second list or a new record for inline images.** D-01 forbids it. Filter with `contentId() != null` where a consumer needs only inline ones.
- **A `validate` rule for "transport cannot send inline".** D-02: no code.
- **Sending `isInline: false` for regular attachments.** Not needed: the `attachment` resource page documents `isInline` as "`true` if the attachment is an inline attachment; otherwise, `false`", and Microsoft's own sendMail attachment example omits it. Sending it would also change the existing request JSON test for no gain.
- **Sending `contentLocation`.** Documented as "Don't use this property as it isn't supported."
- **Wrapping the content ID in angle brackets.** The Microsoft description of `contentId` uses it bare (`<img src="cid:contentId">`). See Open Question 1.
- **Collecting several inline errors.** D-13: first problem only.
- **Adding `.inline(path, id, contentType)`.** D-08: no overload; `.attach(new Attachment(...))` covers it.

## Graph JSON: what Microsoft documents (read 2026-11-22)

| Property | What the page says | Source |
|----------|--------------------|--------|
| `@odata.type` | `"#microsoft.graph.fileAttachment"` required when creating a file attachment; required properties **name** and **contentBytes** | fileAttachment page [CITED: learn.microsoft.com/en-us/graph/api/resources/fileattachment] |
| `isInline` | "Set to `true` if the attachment is an inline attachment." (Boolean) | same |
| `contentId` | "The ID of the attachment in the Exchange store." (String) | same |
| `contentLocation` | "Don't use this property as it isn't supported." | same |
| `contentId` (usage) | "The CID or Content-Id of the attachment for referencing for the in-line attachments using the `<img src="cid:contentId">` tag in HTML messages. Optional." | attachmentItem page [CITED: learn.microsoft.com/en-us/graph/api/resources/attachmentitem] |
| `isInline` (base) | "`true` if the attachment is an inline attachment; otherwise, `false`." | attachment page [CITED: learn.microsoft.com/en-us/graph/api/resources/attachment] |
| `name` | "...doesn't need to be the actual file name" | fileAttachment page |
| size limit | "limit the size of the attachment to 3 MB" for add-attachment calls; 3-150 MB needs an upload session | attachment page |

[VERIFIED: fetched all four pages this session via WebFetch from learn.microsoft.com]

**The `user: sendMail` page** [CITED: learn.microsoft.com/en-us/graph/api/user-sendmail] (page updated 2026-06-19 per its own front matter) has five examples: plain text, custom headers, **Example 3: file attachment** (`"@odata.type": "#microsoft.graph.fileAttachment", "name": "attachment.txt", "contentType": "text/plain", "contentBytes": "SGVsbG8gV29ybGQh"`), MIME, and a flag. It has **no inline example**, and it states no `isInline`/`contentId` property. CONTEXT.md and the roadmap say the page has an inline example; it does not. Record in `docs/architecture.md` that the inline properties come from the resource pages and a community example, not from the sendMail page.

**Community corroboration** (not authoritative, hence `[ASSUMED]` for behaviour): a 2016 blog sample posts exactly `{"@odata.type": "#microsoft.graph.fileAttachment", "contentBytes": ..., "contentType": "image/png", "contentId": "thumbsUp", "name": "thumbs-up.png", "isInline": true}` to `sendMail` with `<img src='cid:thumbsUp'>` in an HTML body, and a Microsoft Q&A question (2025) uses the same property set with bare IDs like `image0`. [CITED: eliostruyf.com/sending-mails-embedded-images-via-microsoft-graph; learn.microsoft.com/en-us/answers/questions/2137105] The Q&A thread reports that one client (Windows Outlook desktop) did not show the image while web and Mac clients did, with no resolution posted. That is the reason the live test matters and why the content ID format question stays open.

**Entry for `docs/architecture.md`, "Differences from the Microsoft documentation"** (to be written in this phase):
- Inline properties: the `sendMail` page has no inline example. The library follows the `fileAttachment` and `attachmentItem` pages: `isInline: true` and `contentId` (bare, no angle brackets) on the file attachment; `contentLocation` is not sent because the page says it is unsupported; regular attachments carry no `isInline`. The fileAttachment page describes `contentId` as "the ID of the attachment in the Exchange store", while attachmentItem describes it as the CID for `<img src="cid:contentId">`; the library uses the second meaning. Live status: not yet confirmed (owner runs `LiveGraphTest`).
- A deviation row for spec §5 and §5.1 ("build() rejects a non-null contentId") replaced by the INLN checks.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Finding `cid:` references | An HTML parser or tokenizer | One `java.util.regex.Pattern` | D-04, Rule 0, no dependency |
| Base64 of the image | Custom encoder | `Base64.getEncoder()` already in `requestBody` | Existing code path, same as regular attachments |
| Content type of the image | New table or MIME sniffing | Existing `CONTENT_TYPES` + `Files.probeContentType` in `contentTypeOf` | D-10 |
| Test PNG | Checked-in binary | `ImageIO.write` of a `BufferedImage` in the test | CONTEXT discretion; no file in the repo |

**Key insight:** every piece except the regex and two JSON properties already exists. Resist abstractions; this is about 40 lines of production code.

## Runtime State Inventory

Not a rename/refactor/migration phase. Omitted.

## Common Pitfalls

### Pitfall 1: The resolved attachment loses its content ID
**What goes wrong:** `resolveAttachments` builds `new Attachment(filename, contentType, file, null)` at line 427. If only the old throw is removed, every inline image is stored as a regular attachment, the Graph request has no `isInline`, and the scan can never match.
**How to avoid:** pass `contentId` through, and assert it in a `MailBuilderTest` case (`build().message().attachments().get(0).contentId()`).
**Warning signs:** the recorded message shows `contentId() == null`.

### Pitfall 2: Accepted ID that the regex cannot read back
**What goes wrong:** an ID containing a regex terminator (`)`, `>`, a quote, whitespace) is accepted, then the scan truncates it and reports "not referenced". See Pattern 2, finding 2.
**How to avoid:** make the rejection set a superset of the terminator set; add a parameterized test over each rejected character and one over accepted characters (`logo`, `logo-1`, `a.b_c`, `x@y`, `ü`, `logo:1`).

### Pitfall 3: "cid:" inside prose or links with no ID
**What goes wrong:** "acid:" or `href="cid:"` produce an empty ID and a baffling error, or reject a legitimate mail.
**How to avoid:** skip empty IDs; optionally the lookbehind (Pattern 2).

### Pitfall 4: Regular attachment mails with `cid:` text now fail
**What goes wrong:** a v0.1.0 mail whose HTML contains the text `cid:...` but no inline images used to build, and now fails INLN-02. Accepted by the spec ("every cid: reference has a matching inline attachment"), but it is a visible behaviour change in 0.2.0.
**How to avoid:** say so in the `build()` Javadoc and add a test that pins it. No code.

### Pitfall 5: Content type for non-table image extensions is platform dependent
**What goes wrong:** `.svg`, `.webp`, `.bmp` resolve through `Files.probeContentType`, which varies by OS and JDK. A test using such a file passes locally and fails on CI (Temurin 17, Linux).
**How to avoid:** tests use only png, jpg, jpeg, gif (built-in table, same everywhere) for "accepted" cases. For the "not an image" case use `.pdf`, `.txt` or an unknown extension; both resolve to a non-`image/` type on every platform (`pdf` from the table; `.bin` is unknown, and `probeContentType` returning an `image/` type for it would be extraordinary). STATE.md already notes the local JDK is 21 only, so the CI Temurin 17 run is the check. [VERIFIED: Mail.java:42-56, the `CONTENT_TYPES` map has `png`, `jpg`, `jpeg`, `gif` as the only image entries]
**Also:** `Files.probeContentType` may return `null` or throw `IOException`; the existing helper already falls through to `application/octet-stream`, which fails the image check. Good: no new code.

### Pitfall 6: A message that echoes data
**What goes wrong:** the ID in "no inline image for cid:X" comes from rendered HTML (see Pattern 2, finding 4), and a content ID message must not carry a line break. IDs from the scan cannot contain whitespace, but IDs given to `.inline()` can (for the shape check message, do not echo the value).
**How to avoid:** do not echo the value in the blank/line-break/forbidden-character messages (say "contains a character that is not allowed"); truncate the echoed ID in scan messages.

### Pitfall 7: Javadoc drift (doclint)
**What goes wrong:** the `Attachment` and `attach(Attachment)` Javadoc still say "the first release rejects a non-null value" and "{@code build()} rejects ... a non-null content id". `build()` Javadoc lists the check order and must now mention inline checks. A broken `{@link}` fails `-Prelease` Javadoc.
**How to avoid:** update all three Javadoc blocks; add the `@throws NullPointerException` tag on `inline`; run `./mvnw -Prelease verify -Dgpg.skip -pl morimail -am` locally.
**Also stale in the docs, not code:** `docs/spec.md` lines 124-125 and 177 say `build()` rejects a non-null `contentId`; spec §10.3 is the binding replacement. See Open Question 3.

### Pitfall 8: Per-test file naming in `GraphValidateTest`
`GraphValidateTest.withFiles` builds mails with `.text(...)` and plain `attach`. An inline sparse file needs `.html("<img src=\"cid:big\">")` and `.inline(file, "big")`, and the file must have an image extension (`big.png`) or `build()` rejects it for type. [VERIFIED: GraphValidateTest.java:55-60 `withFiles` uses `base().text(text)` and `builder.attach(file)`]

## Code Examples

### Builder test cases (`MailBuilderTest`, existing helpers `valid()` and `file(name)`)
`rejectsContentId` [VERIFIED: MailBuilderTest.java:388-397] becomes obsolete and must be replaced:
```java
    @Test
    void rejectsContentId() throws Exception {
        Path file = file("logo.png");

        assertThatThrownBy(() -> valid()
                .attach(new Attachment("logo.png", null, file, "cid-1")).build())
                .isInstanceOf(InvalidMailException.class)
                .hasMessageContaining("attachments[0]")
                .hasMessageContaining("contentId");
    }
```
Cases to add (one `@Test` or `@ParameterizedTest` each, mapped to requirements):

| Case | Mail | Expect |
|------|------|--------|
| accepts inline referenced from HTML | `.html("<img src=\"cid:logo\">").inline(png, "logo")` | build ok; `attachments().get(0)` has `contentId "logo"`, `filename "logo.png"`, `contentType "image/png"` (INLN-01) |
| template route | `.template(new MailTemplate("S", "<img src=\"cid:logo\">$$name$$", null), data).inline(...)` | ok (spec example in success criterion 1) |
| blank / line break / forbidden char ID | `""`, `"  "`, `"a\nb"`, `"a b"`, `a"b`, `a(b`, `a>b` | `InvalidMailException` (INLN-01) |
| null args | `.inline(null, "x")`, `.inline(p, null)` | `NullPointerException` (D-12) |
| `cid:` with no image | HTML `<img src="cid:logo">`, no inline | message contains `no inline image for cid:logo` (INLN-02) |
| `cid:` variants | `CID:logo`, CSS `url(cid:logo)`, `background=cid:logo` all referenced; `cid:Logo` vs ID `logo` is a mismatch | (D-04, D-05) |
| unreferenced image | HTML without `cid:` | `inline image logo is not referenced` (INLN-03) |
| no HTML body | `.text("x").inline(png, "logo")` | same message (INLN-03) |
| duplicate | two `.inline(..., "logo")` | `duplicate inline content ID logo` (INLN-04) |
| not an image | `.inline(pdfFile, "doc")`, HTML refers to it | InvalidMailException naming the ID (INLN-05) |
| missing / unreadable inline | nonexistent path | `attachments[0]` ... `not found` (INLN-05, D-11) |
| `attach(Attachment)` route | `new Attachment("x.png", null, png, "logo")` and one with `contentType "image/PNG"` accepted; with `"application/pdf"` rejected | D-08, D-10 |
| regular attachment alongside | `.attach(pdf).inline(png, "logo")` | `attachments()` has two entries, pdf `contentId() == null` |
| `build()` repeatable | call twice | same result (existing Javadoc promise) |
| plain mail with `cid:` text | HTML with `cid:x`, no inline | fails (pins Pitfall 4) |

Test images: tiny files. Use `Files.write(path, new byte[]{1})` named `logo.png`; the checks use the extension, not the bytes (D-10), so no real PNG is needed in unit tests. The existing `file("logo.png")` helper in `MailBuilderTest` already does this.

### Recording and Graph tests
- `SendThroughRecordingTest`: build a mail with `attach(pdf)` and `inline(png, "logo")` and an HTML with `cid:logo`; `mailer.send`; assert `transport.sent().get(0).attachments()` has two entries, the inline one with `contentId "logo"`, `filename`, `contentType "image/png"`, and `filter(a -> a.contentId() != null)` finds exactly the inline one (INLN-06, success criterion 1). No new helper in `RecordingTransport` is needed.
- `GraphRequestTest`: stubbed endpoint (`stubSend()`), expected JSON tree built with `MAPPER.createObjectNode()`: the inline entry equals `fileAttachment(name, type, bytes)` plus `put("isInline", true)` and `put("contentId", "logo")`; the regular entry in the same request has neither key (`assertThat(node.has("isInline")).isFalse()`). Compare the whole tree like the existing full-mail test.
- `GraphValidateTest`, using the existing `sparse(name, size)` helper [VERIFIED: GraphValidateTest.java:54-59]:
  - inline `big.png` of 3,145,728 bytes: message `attachment big.png has 3145728 bytes; Graph takes attachments below 3 MiB (3145728 bytes)` (the existing `LIMIT_PER_FILE` constant).
  - inline 1,572,864 + regular 1,572,864 bytes: `attachments ... have 3145728 bytes together ...` naming both files.
  - estimate above 4 MiB with an inline image: reuse the existing pattern (a file just under 3 MiB plus body). 
  - rejected before any token request: assert `stub` was not called and WireMock saw no request, the way the existing validate tests do.

### Live test method (`LiveGraphTest`)
Follow the existing methods: same `mailer()`, `describe`, `flow()`, `System.out.println` style, no tokens/addresses printed. Sketch:
```java
    @Test
    void sendsOneMailWithAnInlineImage() throws IOException, MailException {
        Path image = tmp.resolve("logo.png");
        Files.write(image, tinyPng());
        Mail mail = Mail.using("live").from(from).to(to).subject("morimail live test, inline image")
                .html("<p>The image below should show inside the mail, not as a separate attachment.</p>"
                        + "<p><img src=\"cid:logo\" alt=\"morimail test image\"></p>")
                .inline(image, "logo").build();
        try {
            mailer().send(mail);
        } catch (MailException | RuntimeException e) {
            System.out.println("live inline: flow " + flow() + ", " + describe(e));
            throw e;
        }
        System.out.println("live inline: flow " + flow() + ", accepted");
    }

    private static byte[] tinyPng() throws IOException {
        BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < 64; x++) for (int y = 0; y < 64; y++) img.setRGB(x, y, 0x2060c0);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }
```
Verified in this session on JDK 21: this generator writes 221 bytes with exactly the chunks `IHDR IDAT IEND` (no text or time metadata). [VERIFIED: ran the same code in the scratchpad; output `221 bytes, chunks: IHDR IDAT IEND`] The sample's existing test also relies on the same chunk-list check [VERIFIED: ExampleCampaignTest.java:130 `assertThat(pngChunkTypes(Files.readAllBytes(pngFile))).containsExactly("IHDR", "IDAT", "IEND");`]. Update the class Javadoc ("sends one real mail", list of probe methods) to mention the new method; it needs no new environment variable. A visible blue square lets the owner see "inline, not attached" at a glance.

The test class stays tagged `@Tag("live")`; the default `excludedGroups` property excludes it [VERIFIED: pom.xml:59-61 `<excludedGroups>live</excludedGroups>` / `<groups></groups>`], so `./mvnw verify` stays offline and green.

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `build()` rejects any `contentId` (v0.1.0) | `build()` validates content IDs and `cid:` references in both directions | this phase | `Attachment.contentId` becomes live API surface |
| 2016 community sample for Graph inline | `fileAttachment`/`attachmentItem` pages document `isInline`, `contentId`; `contentLocation` unsupported | pages updated 2025-01 / 2025-12 | Property set unchanged since the old sample, only `contentLocation` is discouraged |

**Deprecated/outdated:** `contentLocation` (documented as unsupported).

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Graph/Exchange accepts a bare `contentId` and renders `cid:<contentId>` inline without the caller adding angle brackets | Graph JSON | The image shows as a separate attachment or is blank. Only the owner's live run answers it; if wrong it becomes an inserted Phase 5.1 (roadmap says so) |
| A2 | Omitting `isInline` for regular attachments is treated as `false` by the service | Pattern 3 | Low. Microsoft's own sendMail Example 3 omits it, and the current request tests already pass without it |
| A3 | Windows Outlook desktop renders `isInline` + bare `contentId` images from `sendMail` | Graph JSON | A Microsoft Q&A thread reports a client that did not. Document the live result in Phase 8; no code can fix it from here |
| A4 | `Files.probeContentType` returns a non-`image/` type (or null) for `.bin`/`.pdf`/`.txt` on every CI platform | Pitfall 5 | A "not an image" test could flake on an exotic platform. Use `.pdf` (built-in table) as the primary negative case to remove the dependency |
| A5 | Behaviour of `ImageIO` PNG output on Temurin 17 equals JDK 21 (chunks only `IHDR IDAT IEND`) | Live test | Only affects a "no metadata" claim in a test comment. CI round-trip on 17 shows it; the live test itself is not run in CI |
| A6 | Skipping empty `cid:` matches and adding a lookbehind is acceptable to the owner (a small narrowing of D-04's "a literal cid: in body text also counts") | Pattern 2 | Owner may prefer strict D-04. Cheap to flip, so ask in plan review |

## Open Questions

1. **Angle brackets on `contentId`?**
   - Known: Microsoft's description is `<img src="cid:contentId">` with the bare value, and the community samples use bare values.
   - Unclear: whether Exchange wraps it in `<...>` itself for the MIME `Content-ID` header. Only a real send shows it.
   - Recommendation: send bare. The live test (GRAPH-12) is the check; a correction becomes Phase 5.1.

2. **Empty-ID matches and the "acid:" lookbehind (A6).**
   - Recommendation: skip empty IDs; add the `(?<![\p{L}\p{N}])` lookbehind. Both are inside "the exact regex" discretion. Tell the owner in the plan.

3. **Stale spec text.** `docs/spec.md` §5 (lines 124-125) and §5.1 (line 177) still say `build()` rejects a non-null `contentId`; §10.3 says the opposite. The spec is binding and CONTEXT says Phase 8 records the Graph names in spec §6. Recommendation: leave the spec edit to Phase 8 (DOCS-05), record the deviation in `docs/architecture.md` now, and note it in the plan so the executor does not edit `docs/spec.md` unasked.

4. **Truncating the echoed ID** in "no inline image for cid:X" (Pattern 2, finding 4). Recommendation: cap at 64 characters; trivial and keeps the "no data echo" rule honest.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| JDK | build and tests | yes | OpenJDK 21.0.13.1 (builds `--release 17`) | none needed; Temurin 17 only in CI |
| Maven wrapper | `./mvnw` | yes | wrapper, offline repo present | none needed |
| Baseline test run | confidence | yes | `./mvnw -o -B -ntp -pl morimail test`: 316 tests, 0 failures, BUILD SUCCESS, 8.8 s | none |
| Network / real Graph / real mail | live test | not used | the owner runs `-Plive` | none; Claude never runs it |

**Missing dependencies with no fallback:** none.

## Validation Architecture

Skipped: `workflow.nyquist_validation` is `false` in `.planning/config.json`. [VERIFIED: .planning/config.json, `"nyquist_validation": false`] Tests are laid out in Code Examples above and run with `./mvnw -pl morimail test` (about 9 s) and `./mvnw verify`.

## Security Domain

`security_enforcement` is enabled (ASVS level 1, block on high). [VERIFIED: .planning/config.json]

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no (unchanged) | existing Entra client |
| V3 Session Management | no | none |
| V4 Access Control | no | none |
| V5 Input Validation | yes | content ID rejection rules (D-09), image type check (D-10), file checks (D-11), all in `build()` |
| V6 Cryptography | no | none |
| V12 Files | yes (light) | the inline file is read from a caller-given `Path`; same trust as `attach()`; no path derived from template data |

### Known Threat Patterns

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Header injection via content ID (CR/LF in `Content-ID`) | Tampering | Reject CR/LF and whitespace in `contentId` (D-09). Graph carries it as a JSON string, but a future SMTP/MIME transport would put it in a header, so the rule belongs in the core |
| Template data steering the `cid:` set (data containing `cid:`) | Tampering / Info disclosure | Scan sees only IDs; an unknown ID fails the build. Truncate the echoed ID (Open Question 4); never echo address or data values |
| Reading arbitrary local files via the inline path | Info disclosure | Path comes from caller code, not from template data in the core. The helper module (Phase 7) owns the base-directory rule |
| Oversized inline images exhausting the request | DoS | Existing 3 MiB / 4 MiB checks in `validate`, before any token request (D-15, D-17) |
| Logging image bytes or bodies | Info disclosure | No new logging; nothing in this phase logs content |

## Sources

### Primary (HIGH confidence)
- `docs/spec.md` §5, §5.1, §6.2, §10.3 and the existing code files listed under each `[VERIFIED: path:line]` tag (read this session).
- https://learn.microsoft.com/en-us/graph/api/resources/fileattachment (property table, `isInline`, `contentId`, `contentLocation`)
- https://learn.microsoft.com/en-us/graph/api/resources/attachment (`isInline` semantics, 3 MB limit and upload-session note)
- https://learn.microsoft.com/en-us/graph/api/resources/attachmentitem (`contentId` as the `cid:` reference)
- https://learn.microsoft.com/en-us/graph/api/user-sendmail (no inline example; Example 3 is a plain file attachment)
- Executed in the scratchpad: regex behaviour on JDK 21; `ImageIO` PNG chunk list; `./mvnw -o -pl morimail test` baseline (316 tests green).

### Secondary (MEDIUM confidence)
- https://eliostruyf.com/sending-mails-embedded-images-via-microsoft-graph (2016 sample, property set confirmed by the Microsoft resource pages)
- https://learn.microsoft.com/en-us/answers/questions/2137105/microsoft-graph-api-inline-image-is-not-visible-in (question only, no answer)

### Tertiary (LOW confidence)
- none beyond `[ASSUMED]` items A1-A6.

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH, no new dependencies.
- Architecture: HIGH, existing code read in full (`Mail.java`, `GraphTransport.java`, `Attachment.java`, test helpers).
- Graph property names: HIGH from three Microsoft resource pages. Rendering behaviour in real clients: MEDIUM, needs the owner's live run.
- Pitfalls: HIGH for the code-level ones (verified by reading and by running the regex), MEDIUM for platform content-type resolution (CI on Temurin 17 is the check).

**Research date:** 2026-11-22
**Valid until:** 2026-12-22 (Graph pages change rarely; the property set has been stable since 2016)
