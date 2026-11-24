# Phase 6: Inline Images in the Serial-Mail Sample - Pattern Map

**Mapped:** 2026-11-23
**Files analyzed:** 12 (2 main classes, 6 test classes/helper, 3 example-campaign files, 1 rename)
**Analogs found:** 12 / 12 (all analogs are in-file or sibling files; all git-tracked)

All paths are under `/home/coder/morimail/morimail-sample-serial/` unless noted. Main package: `src/main/java/de/morisoft/morimail/sample/`.

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `Campaign.java` (modify: `inlineImages`, `CID_REFERENCE`, `contentId()`, scan, `listFiles`) | model/reader | file-I/O + transform | same file, `attachments/` block lines 79-91, `PLACEHOLDER` line 35 | exact |
| `SendCommand.java` (modify: build loop) | controller/command | batch | same file, attach loop lines ~165-168 | exact |
| `SendCommand.java` (modify: `writeDryRun` copy, rewrite, listing) | command | file-I/O | same file, `writeDryRun` lines ~246-281 | exact |
| `example-campaign/inline/logo.png` (new) | fixture | file | `example-campaign/attachments/logo.png` | role-match |
| `example-campaign/attachments/sample.png` (git mv) | fixture | file | itself | exact |
| `example-campaign/body.html` (modify) | fixture/template | transform | itself | exact |
| `CampaignTest.java`, `PreflightTest.java`, `DryRunTest.java` (modify) | test | request-response | existing attachment tests in same files | exact |
| `ExampleCampaignTest.java` (modify lines ~77, ~123) | test | file-I/O | itself | exact |
| `GraphEndToEndTest.java` (modify line ~168) | test | request-response | itself | exact |
| `SendRunTest.java` (modify) | test | request-response | itself, `recording` transport | exact |
| `SampleFixtures.java` (add `writeInline`) | test helper | file-I/O | existing helpers in same file | exact |

## Pattern Assignments

### `Campaign.java` (reader, file-I/O + transform)

**Analog:** itself.

**Listing pattern** (lines 79-91): extract into one private `listFiles(Path dir, String label, List<String> problems)` and use for both directories.
```java
List<Path> attachments = new ArrayList<>();
Path attachmentDir = dir.resolve("attachments");
if (Files.isDirectory(attachmentDir)) {
    try (Stream<Path> files = Files.list(attachmentDir)) {
        // Hidden files such as .DS_Store are editor and OS leftovers, never attachments.
        files.filter(Files::isRegularFile)
                .filter(file -> !file.getFileName().toString().startsWith("."))
                .forEach(attachments::add);
    } catch (IOException e) {
        problems.add("attachments/ cannot be listed");
    }
    attachments.sort(Comparator.comparing(file -> file.getFileName().toString()));
}
```
Place the `inline/` listing and scan before the early `return null` at line 92 (`if (subject == null || html == null || csv == null)`), as `attachments/` is. Compare `problems.size()` before/after the inline block (same idiom as `SendCommand` lines 150-152) and `return null` if it grew (so `build()` cannot repeat scan findings, D-01).

**Copied-core-pattern precedent** (line 34-35): copy `CID_REFERENCE` the same way, with a comment that it must match the core's `Mail.java` pattern.
```java
/** The placeholder pattern of the core's template renderer, copied so both agree on every name. */
static final Pattern PLACEHOLDER = Pattern.compile("\\$\\$(!?)([A-Za-z0-9_.-]+)\\$\\$");
```
New:
```java
static final Pattern CID_REFERENCE = Pattern.compile("(?i)(?<![\\p{L}\\p{N}])cid:([^\"'\\s)>]*)");
static String contentId(Path file) {            // last extension stripped (D-03)
    String name = file.getFileName().toString();
    int dot = name.lastIndexOf('.');
    return dot > 0 ? name.substring(0, dot) : name;
}
```
Record header (line 32) gains a component: `record Campaign(MailTemplate template, List<Row> rows, List<Path> attachments, List<Path> inlineImages)`. Update the Javadoc `@param` (lines 28-30) and every `new Campaign(...)` call site (grep in main and tests).

**Probe through the core** (RESEARCH Pattern 2): per file, `Mail.using("probe")...html("<img src=\"cid:"+id+"\">").inline(file,id).build()`, catch `InvalidMailException`, add `"inline/" + name + ": " + e.getMessage().replaceFirst("^attachments\\[0\\]: ", "")`. Needs imports `de.morisoft.morimail.Mail`, `de.morisoft.morimail.InvalidMailException` (check where `SendCommand` imports them from).

**Problem message style** (lines 97, 104, 111, 122): `"<file>: <what>"`, no data values. New: `inline/<name>: ...`, `body.html: cid:<id> has no image in inline/`, `inline/<name>: no cid:<id> reference in body.html`, `inline/<name>: same content ID as inline/<first> (cid:<id>)`.

---

### `SendCommand.java` build loop (batch, request-response)

**Analog:** itself, pre-flight loop (lines ~159-177).
```java
Mail.Builder builder = Mail.using(account.id())
        .from(account.from())
        .to(row.email())
        .template(campaign.template(), row.fields());
for (Path attachment : campaign.attachments()) {
    builder.attach(attachment);
}
mails.add(builder.build());
```
Add between the attach loop and `build()` (attachments first, inline last):
```java
for (Path image : campaign.inlineImages()) {
    builder.inline(image, Campaign.contentId(image));
}
```
The `invalid` map (lines 156-157, 175-177) already reports a repeated `build()` message once; keep unchanged.

---

### `SendCommand.writeDryRun` (file-I/O)

**Analog:** itself (lines ~246-281).

**Listing pattern to split** (lines ~251-261): `mails.get(0).message().attachments()` will now include inline images. Filter `contentId() == null` for the `attachments:` line (keep `attachments: none` when empty) and `!= null` for a new `inline: logo.png (image/png, N bytes, cid:logo)` line printed only when inline images exist, so no-inline campaigns print the v0.1.0 lines exactly.
```java
List<Attachment> attachments = mails.get(0).message().attachments();
if (attachments.isEmpty()) { out.println("attachments: none"); } else { ... 
    entries.add(attachment.filename() + " (" + attachment.contentType() + ", "
            + Files.size(attachment.file()) + " bytes)");
```

**Write pattern** (lines ~262-270): rewrite only the string passed to the file, never the `MailMessage`.
```java
Files.writeString(outDir.resolve(base + ".html"), message.html());   // wrap with cid rewrite
```
Before the row loop (once, even with `--limit`): if inline images exist, `Files.createDirectories(outDir.resolve("inline"))` and `Files.copy(a.file(), outDir.resolve("inline").resolve(a.filename()), StandardCopyOption.REPLACE_EXISTING)`. Rewrite using `Campaign.CID_REFERENCE.matcher(html).replaceAll(m -> ...)` with a prebuilt `Map<String,String> urlById` (URL = `new URI(null, null, "inline/" + name, null).toASCIIString().replace("&", "&amp;")`, computed up front so the lambda throws no checked exception), `Matcher.quoteReplacement`, and leave `m.group()` untouched when the ID is empty or unknown.

**Error handling:** keep the existing `catch (IOException e)` with `err.println("Dry run stopped: ...")` and `return 1`. Catch/convert `URISyntaxException` into the same path, or wrap in the up-front map building.

---

### Example campaign (fixtures)

- `git mv example-campaign/attachments/logo.png example-campaign/attachments/sample.png`.
- New `example-campaign/inline/logo.png`: 96x48, ~166 bytes, chunks IHDR/IDAT/IEND only. Generator recipe is in RESEARCH "Code Examples"; run once with `python3 -I`, script kept outside the repo.
- `body.html`: add `<p><img src="cid:logo" alt="morimail logo" width="96" height="48"></p>` above "Hello $$firstName$$" plus one sentence (logo inline; PDF and PNG attachments). `body.txt` unchanged.

---

### Tests

**Analog for all:** the existing tests in the same file. Read each file's attachment tests (grep `attachments`) and mirror their structure.
- `SampleFixtures.java`: add `writeInline(Path campaignDir, String name)` writing `Files.writeString(inline.resolve(name), "png")`. The core never decodes content, only resolves the type from the extension, so no real images in tests. Inline tests overwrite `body.html` with `<p><img src="cid:logo"></p>`.
- `CampaignTest`: listing (sorted, dot-files skipped, subdirs ignored), `contentId` (`logo.v2.png` -> `logo.v2`, no dot keeps the name), each scan finding, null return on inline findings.
- `PreflightTest`: inline findings in the same report as others, exit 2, no `out/`, no journal.
- `DryRunTest`: `out/inline/<file>` copied, `src="inline/logo.png"` and no `cid:` in written HTML, `inline:` stdout line, campaign without `inline/` unchanged (existing `containsExactly` on stdout), `--limit 1` still copies. Percent-encoding and `&amp;` case.
- `SendRunTest` (`recording` transport): `sent.get(0).html()` contains `src="cid:logo"`; `attachments()` has `contentId()=="logo"`, `contentType()=="image/png"`.
- `ExampleCampaignTest`: line ~77 expects `sample.png (image/png, `, add `inline:` check; line ~123 `resolve("sample.png")`; `fileNames(out)` now includes the `inline` directory (16 entries, or filter to regular files); add logo checks (size < 1024, `ImageIO` 96x48, chunk types via `pngChunkTypes`).
- `GraphEndToEndTest` line ~168: `containsExactly("info.pdf", "sample.png", "logo.png")`; assert the `logo.png` node has `isInline` true and `contentId` `logo`, the others neither.

## Shared Patterns

### Pre-flight aggregation
**Source:** `Campaign.java` (problems list), `SendCommand.java` lines ~150-177. All defects go into one `List<String> problems`, printed to stderr, exit 2, no sending, no journal. Inline messages name only files and template IDs, never recipient values (Phase 5 D-14).

### Copy-and-comment of core patterns
**Source:** `Campaign.PLACEHOLDER` (line 34-35). Apply to `CID_REFERENCE`. Single source of truth for type and ID rules is the core, reached through the probe mail, not a copied table.

### Rule 0
No new class, record, interface, or dependency. `List<Path> inlineImages` plus the static `Campaign.contentId(Path)` instead of an `InlineImage` record.

## No Analog Found

None. The percent-encoding and the `cid:` rewrite are new logic with no repo analog; use the JDK recipes in RESEARCH Pattern 4. The example PNG generator has no in-repo analog (v0.1.0 used a throwaway script outside the repo, per 02-04 SUMMARY).

## Metadata

**Analog search scope:** `morimail-sample-serial/src/main`, `morimail-sample-serial/example-campaign`, RESEARCH-cited test files.
**Files read directly:** `Campaign.java` (1-140), `SendCommand.java` (150-185, 240-290), CONTEXT, RESEARCH. Test file excerpts are taken from RESEARCH citations; the planner should read each test file before editing.
**Tracked-source gate:** `git ls-files` confirms the example-campaign files are tracked; no mirror paths used.
**Pattern extraction date:** 2026-11-23
