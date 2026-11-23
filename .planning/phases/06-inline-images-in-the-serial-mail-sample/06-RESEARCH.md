# Phase 6: Inline Images in the Serial-Mail Sample - Research

**Researched:** 2026-11-23
**Domain:** Plain Java 17 code in `morimail-sample-serial` (campaign reading, pre-flight, dry-run output) on top of the Phase 5 core API. No new library, no new dependency.
**Confidence:** HIGH (everything below was read in this repo or run in this session; the few `[ASSUMED]` items are listed in the Assumptions Log)

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

#### Carried forward (not re-asked)
- Phase 5 D-04..D-06: the `cid:` grammar is `(?i)(?<![\p{L}\p{N}])cid:([^"'\s)>]*)`, the ID is matched case-sensitively, and an empty ID is skipped. Only the HTML body counts (D-07).
- Phase 5 D-09: a content ID must not be blank and must not contain whitespace, CR/LF, `"` `'` `(` `)` `<` `>`. D-10: an image means the resolved content type starts with `image/`.
- Phase 5 D-13: `build()` reports only the first problem.
- Phase 2 D-02: dry-run runs pre-flight, `build()` included, without a `Mailer`. D-13: exit 2 for pre-flight. D-15: dry-run creates `out/`, overwrites same-named files and leaves other files alone. The same `build()` message across rows is reported once (`SendCommand`'s `invalid` map).

#### Pre-flight source
- **D-01:** The inline checks run as a **template scan in `Campaign.read`**, before any mail is built. The scan reads the `body.html` template and the `inline/` file list and adds **one problem per defect**:
  - each template `cid:` with no image
  - each image that no template `cid:` references
  - each pair of files with the same base name
  - each non-image file
  - each file whose name gives an invalid content ID (D-02)

  Mails are built (`.inline(file, id)` per image) only when the scan finds nothing, so `build()` stays a backstop. Its first-problem message must not duplicate the scan's findings in the report. One case only `build()` can catch is a `cid:` that appears only through a placeholder value, after rendering. It is reported through the existing per-row aggregation.
- **D-02:** When a file name gives a content ID that the Phase 5 rules reject (for example `my logo.png` -> `my logo`), pre-flight reports a problem that names the file. The sample never sanitizes or invents IDs. The ID is exactly the file name without its last extension.
- **D-03:** "Without extension" means **strip the last extension**: `logo.v2.png` -> `logo.v2`. A name with no dot keeps its full name (and usually then fails the non-image check). `logo.png` and `logo.jpg` both give `logo`, so they are reported as a base-name clash.
- Messages follow the existing pre-flight style: name the file (`inline/<name>`) or the `cid:` ID. Content IDs and file names come from the template author, not recipient data (Phase 5 D-14). Never print addresses or data values.

#### Dry-run layout
- **D-04:** Dry-run copies each inline image **once into `out/inline/`** and rewrites `cid:<id>` in the dry-run HTML to `inline/<file name>`. This mirrors the campaign layout and can never collide with a rendered mail file. Reruns overwrite, as D-15 of Phase 2 does. This is the accepted reading of CLI-17's "next to each rendered HTML file": the images sit in a subdirectory beside the HTML files, and the relative paths resolve from each HTML file.
- **D-05:** The rewritten `src` is the **percent-encoded** relative path (JDK URI encoding, no dependency), so any name that passes the content-ID rules opens in a browser.
- **D-06:** The rewrite touches only the files dry-run writes. The `Mail`/`MailMessage` objects keep `cid:`, and a real run sends `cid:` unchanged (CLI-17). The rewrite uses the same `cid:` grammar as the scan.

#### OS junk in `inline/`
- **D-07:** `inline/` skips **dot-files only**, the same rule as `attachments/`. A `Thumbs.db` or `desktop.ini` fails pre-flight as a non-image file, with a message naming it, and the author deletes it. No hard-coded list of special names (Rule 0). This resolves the STATE.md Phase 02 concern for `inline/`. `attachments/` is unchanged.
- Like `attachments/`, only regular files count (subdirectories are ignored). Files are sorted by name.

#### Example campaign
- **D-08:** Keep `attachments/` with a PDF and a PNG (spec section 8.1 still holds). **Add a new tiny `inline/logo.png`** (a few hundred bytes, generated once, no metadata, checked in, no generator code in the build). **Rename `attachments/logo.png` to `attachments/sample.png`** so a reader does not confuse the two. Update `body.html` text and the tests that name it (`ExampleCampaignTest`, `GraphEndToEndTest`).
- **D-09:** `body.html` shows the logo **at the top of the body** with a plain `<p><img src="cid:logo" alt="morimail logo" width=".." height=".."></p>` above "Hello $$firstName$$", plus one sentence saying that the logo is inline and the PNG and PDF are attachments. `body.txt` stays unchanged.
- The dry run of the example campaign gives five rendered mails, each showing `inline/logo.png` (CLI-18, success criterion 4).

### Claude's Discretion
- How the dry run lists inline images on stdout, for example an `inline:` line next to the existing `attachments:` line with file name, content type, size and `cid:` ID.
- How the sample gets the resolved content type for the non-image check without its resolution disagreeing with the core's. Options include reusing what `build()` resolves, or a copied table kept in sync like `Campaign.PLACEHOLDER`. Do not widen the core's public API for this unless the planner finds no simpler way, and then flag it.
- Whether the `cid:` pattern is copied into the sample (as `PLACEHOLDER` is, with a comment that it must match the core) or reached some other way, within the rule that the core does not rewrite HTML (spec section 10.3).
- Where the `inline/` list lives (likely a new `inlineImages` component on the `Campaign` record), the exact message wording, and the test layout (`CampaignTest`, `PreflightTest`, `DryRunTest`, `ExampleCampaignTest`, `SendRunTest` with `recording`).
- Tiny test images generated in tests (no metadata, nothing extra checked in beyond the example logo).

### Deferred Ideas (OUT OF SCOPE)
- Skipping `Thumbs.db`/`desktop.ini` in `attachments/` (STATE.md Phase 02 concern): not changed. The owner chose one dot-file rule for both directories.
- Sample docs for `inline/` (`send --help`, README sample section): Phase 8 (DOCS-05).
- Also not in this phase (CONTEXT domain): the `morimail-html` helper (Phase 7) and per-recipient inline images (REQUIREMENTS.md out of scope).
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| CLI-15 | Optional `inline/` directory; each file becomes an inline image in every mail, ID = file name without extension | `Campaign.read` lists `inline/` like `attachments/`; `SendCommand` adds `builder.inline(file, id)` after the attachments; core `Mail.Builder.inline(Path,String)` already exists (Patterns 1, 3) |
| CLI-16 | Pre-flight reports the four inline problems with the other findings, exit 2, nothing sent | Template scan in `Campaign.read` (Pattern 2); `Campaign.read` returns null when the scan found a problem so `build()` does not duplicate it; existing exit-2 path prints and returns (Pitfall 1) |
| CLI-17 | Dry-run writes images beside the HTML and rewrites `cid:` in the dry-run output only | `out/inline/` copy + `Matcher.replaceAll(Function)` rewrite of the written HTML only (Pattern 4); sent mails untouched (Pitfall 4) |
| CLI-18 | Example campaign has a small generated `inline/logo.png` referenced from the HTML, dry-run shows it | 166-byte generated PNG recipe (Code Examples), `body.html` edit, rename of `attachments/logo.png`, test updates (Example Campaign Changes) |
</phase_requirements>

## Summary

The core already does the hard work. Phase 5 added `Mail.Builder.inline(Path, String)` and a `build()` that enforces content-ID shape, image type, duplicate IDs and the two-way `cid:` check against the rendered HTML. The sample has to do four small things: (1) list `inline/` in `Campaign.read` and add a template-level scan so each defect is reported once with the other pre-flight findings, (2) call `.inline(file, id)` per image when it builds mails, (3) in dry-run copy the images once into `out/inline/` and rewrite `cid:<id>` to a percent-encoded `inline/<name>` in the written HTML only, (4) ship a tiny `inline/logo.png` and update the example campaign and the tests that name the old `attachments/logo.png`.

Two design points need a firm choice, and this research makes them. First, the non-image and invalid-ID checks in the scan should **reuse the core by building a throwaway one-image probe mail per file** instead of copying the core's content-type table. This was run in this session: the core's resolution is host-dependent for extensions outside its table (`a.webp` resolved to `image/webp` here through `Files.probeContentType`), so a copied table would disagree with the core on such hosts. The probe also reuses the content-ID rules and the file-readable checks, so nothing is copied but the `cid:` pattern. Second, when the scan finds a problem `Campaign.read` returns `null` (the existing "campaign cannot be used" signal), so `SendCommand` does not build mails and `build()` cannot repeat the scan's findings.

**Primary recommendation:** Put the `inline/` listing, the probe check and the template scan in `Campaign.read` (return null on any inline finding), add one `Campaign.CID_REFERENCE` copy of the core pattern, add `inlineImages` as a `List<Path>` plus a static `Campaign.contentId(Path)`, call `.inline(...)` after the `.attach(...)` loop, and do the dry-run copy and rewrite in `writeDryRun` using the `Attachment`s of the built mail (filtered on `contentId() != null`).

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Inline image attachment, content-ID and `cid:` checks, Graph wire format | Core library + Graph transport (Phase 5, done) | - | The sample only calls `Mail.Builder.inline`; no check is re-implemented |
| Listing `inline/`, base-name clash, template `cid:` cross-check, problem messages | Sample: `Campaign.read` | Core `build()` (probe and backstop) | Pre-flight is the sample's job; the core stays free of campaign layout and does not rewrite HTML (spec 10.3) |
| Building one mail per row with inline images | Sample: `SendCommand.call` | Core `Mail.Builder` | Same place that already adds attachments |
| Dry-run image copy and `cid:` -> relative path rewrite | Sample: `SendCommand.writeDryRun` | - | Rewrite is dry-run output only; `Mail`/`MailMessage` keep `cid:` |
| Example campaign fixture (`inline/logo.png`, `body.html`) | Sample resources (`example-campaign/`) | Tests | Checked in, generated once, no generator in the build |

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| JDK `java.nio.file`, `java.util.regex`, `java.net.URI` | JDK 17 API (`--release 17`) | Listing, copying, `cid:` scan and rewrite, percent-encoding | Everything this phase needs is in the JDK; Rule 0 forbids anything else |
| `de.morisoft:morimail` (this repo, 0.2.0-SNAPSHOT) | reactor | `Mail.Builder.inline`, `Attachment` (with `contentId`) | The Phase 5 API is what the sample consumes `[VERIFIED: morimail/src/main/java/de/morisoft/morimail/Mail.java:343-348]` |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| picocli 4.7.7, JUnit 6.1.3, AssertJ 3.27.7, WireMock 3.13.2 | already in the sample POM | CLI and tests | No change; existing tests use them. No POM change needed `[VERIFIED: baseline `mvnw -o -pl morimail-sample-serial -am test` green, 137 tests, 2026-11-23]` |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Throwaway probe mail through the core for type and ID checks | Copy the core's `CONTENT_TYPES` image entries into the sample | Copy is 3 lines but diverges as soon as the core table grows, and the core falls back to the host's `Files.probeContentType`, so the copy needs that fallback too. Probe cannot disagree. |
| `Campaign.read` returns null on inline findings | Keep building rows and filter duplicate `build()` messages | Filtering means matching message text. Null return is the existing "cannot be used" signal and is what D-01 asks for ("mails are built only when the scan finds nothing"). |
| `List<Path> inlineImages` + `Campaign.contentId(Path)` | New `InlineImage(Path, String)` record | A record is one more type that only forwards two values; the ID is a pure function of the path (Rule 0). |

**Installation:** none. No package is added, so there is no Package Legitimacy Audit to run.

## Package Legitimacy Audit

No external packages are installed in this phase. Not applicable. **Packages removed:** none. **Packages flagged:** none.

## Architecture Patterns

### System Architecture Diagram

```
campaign dir
 ├─ body.html ──────────────┐
 ├─ inline/<files> ──┐      │
 ├─ attachments/…    │      │
 └─ recipients.csv   │      │
                     v      v
        Campaign.read(dir, problems)
          1. list inline/ (regular files, no dot-files, sorted)
          2. per file: id = name minus last extension
             probe-build one mail via the core  --> invalid id / not an image / unreadable
          3. group by id                        --> base-name clash
          4. scan body.html with CID_REFERENCE  --> cid: with no image / image not referenced
          5. any inline finding? --> return null  (problems already in the list)
                     |
                     v
        SendCommand.call: pre-flight loop (only when campaign != null)
          per row: Mail.using(..).template(..).attach(a)*.inline(img, id)*.build()
          InvalidMailException --> `invalid` map (one report per distinct message)
                     |
        problems empty? ---- no ----> stderr + "Pre-flight failed ..." + exit 2 (no out/, no journal)
                     | yes
          --dry-run ?
          |                         |
          v yes                     v no
   writeDryRun                 send(): Mailer.send(mail)  (html keeps cid:, recording/Graph get inline parts)
     out/inline/<name>  (copy once, REPLACE_EXISTING)
     out/NN-<email>.html  = message.html() with cid:<id> -> inline/<encoded name>
     stdout: attachments: ... / inline: ...
```

### Recommended Project Structure

No new files in `src/main`. Changes only in existing classes:

```
morimail-sample-serial/
├── example-campaign/
│   ├── inline/logo.png               # NEW, 166-byte generated PNG
│   ├── attachments/sample.png        # RENAMED from logo.png (git mv)
│   └── body.html                     # logo at top + one sentence
└── src/
    ├── main/java/de/morisoft/morimail/sample/
    │   ├── Campaign.java             # inlineImages component, CID_REFERENCE, scan, contentId()
    │   └── SendCommand.java          # .inline() in the build loop, dry-run copy + rewrite + listing
    └── test/java/de/morisoft/morimail/sample/
        ├── CampaignTest.java         # listing, ids, each scan finding
        ├── PreflightTest.java        # inline findings in one report, exit 2, nothing written
        ├── DryRunTest.java           # out/inline/, rewrite, listing, no-inline unchanged
        ├── ExampleCampaignTest.java  # five mails show inline/logo.png; sample.png rename; logo pins
        ├── SendRunTest.java          # recording keeps cid: and the inline attachment
        ├── GraphEndToEndTest.java    # sample.png rename, inline node on the wire
        └── SampleFixtures.java       # small helper to write inline files
```

### Pattern 1: List `inline/` like `attachments/`
**What:** Same listing code as `attachments/` (regular files, skip names starting with `.`, sort by `getFileName().toString()`), `inline/ cannot be listed` on `IOException`. Do the listing before the early `return null` that fires when `subject`, `html` or `csv` is null, as `attachments/` does, so file-level inline findings are reported even when another file is missing.
**Existing code to mirror:** `[VERIFIED: morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/Campaign.java:79-91]`
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
Extract one private `listFiles(Path dir, String label, List<String> problems)` used for both directories (removes duplication instead of copy-pasting 12 lines). Sorting is `String` natural order (`B.txt` before `a.txt`), so clash and report order is deterministic.

### Pattern 2: Scan in `Campaign.read` (D-01, D-02, D-03)
**What:** After the listing, compute `id = contentId(file)` for each file and add problems; scan `html` only when it is not null.
```java
/** The content ID of an inline image: its file name without the last extension (D-03). */
static String contentId(Path file) {
    String name = file.getFileName().toString();
    int dot = name.lastIndexOf('.');
    return dot > 0 ? name.substring(0, dot) : name;   // dot-files never get here
}

/** The cid: grammar of the core's Mail.Builder, copied so scan and dry-run rewrite agree with build(). */
static final Pattern CID_REFERENCE =
        Pattern.compile("(?i)(?<![\\p{L}\\p{N}])cid:([^\"'\\s)>]*)");
```
Checks, in this report order (file-level first, then template-level):
1. **Per file, probe through the core** (invalid ID, not an image, unreadable):
```java
// the core resolves content type and ID rules; the probe cannot disagree with build()
try {
    Mail.using("probe").from("probe@example.org").to("probe@example.org").subject("probe")
            .html("<img src=\"cid:" + id + "\">").inline(file, id).build();
} catch (InvalidMailException e) {
    problems.add("inline/" + name + ": " + e.getMessage());   // optionally strip "attachments[0]: "
}
```
2. **Base-name clash:** `Map<String, String> firstFileById` in file order; for every later file with a taken ID add `inline/<later>: same content ID as inline/<first> (cid:<id>)`. One problem per extra file (3 files with one ID give 2 problems).
3. **Template cross-check** (only if `html != null`): `Set<String> referenced` from `CID_REFERENCE` (skip empty group 1, keep first-appearance order). For each referenced ID not in the set of all file IDs add `body.html: cid:<id> has no image in inline/`. For each file whose ID is not referenced add `inline/<name>: no cid:<id> reference in body.html`. Use **all** file IDs (valid or not) for the cross-check so a bad file does not also produce a "no image" cascade.
4. At the end of `read`, `if (inlineProblems > 0) return null;` where `inlineProblems` is the count of problems added by steps 1-3 (compare `problems.size()` before and after the inline block, as `SendCommand` already does for the account file).

**Probe messages verified in this session** (core classes from `morimail/target/classes`, JDK 21):
```
my logo.png  -> attachments[0]: contentId contains whitespace, a quote, a parenthesis or an angle bracket
report.pdf   -> attachments[0]: inline image report has content type application/pdf, which is not an image type
noext        -> attachments[0]: inline image noext has content type application/octet-stream, which is not an image type
missing.png  -> attachments[0]: file missing.png not found
logo.v2.png  -> OK (image/png)
a.webp       -> OK (image/webp)   <- host-dependent: resolved by Files.probeContentType, not by the core's table
```
Strip the `attachments[0]: ` prefix with one `replaceFirst("^attachments\\[0\\]: ", "")` if the wording matters; tests then assert `contains(...)` on the stable tail.

### Pattern 3: Building the mails
In the existing loop `[VERIFIED: SendCommand.java:165-168]`:
```java
for (Path attachment : campaign.attachments()) {
    builder.attach(attachment);
}
mails.add(builder.build());
```
add, between the loop and `build()`:
```java
for (Path image : campaign.inlineImages()) {
    builder.inline(image, Campaign.contentId(image));
}
```
Attachments first, inline last, so the order of regular attachments in v0.1.0 mails does not change. For the example campaign the Graph `attachments` array then reads `info.pdf, sample.png, logo.png` (see Example Campaign Changes).

### Pattern 4: Dry-run copy, rewrite and listing (`writeDryRun`)
`writeDryRun` takes the attachment list from `mails.get(0).message().attachments()` `[VERIFIED: SendCommand.java:251]`, which after this phase **also contains inline images**. Split it by `contentId() == null`:
- `attachments:` line: regular ones only, otherwise `attachments: none` (so a campaign with only inline images prints `attachments: none`).
- `inline:` line **only when at least one inline image exists**, so a campaign without `inline/` prints exactly the v0.1.0 lines (existing `DryRunTest` asserts `containsExactly` on stdout). Suggested format, parallel to the attachments line: `inline: logo.png (image/png, 166 bytes, cid:logo)`.
- Copy once: `Files.createDirectories(outDir.resolve("inline"))` (only when there are inline images), then `Files.copy(attachment.file(), outDir.resolve("inline").resolve(attachment.filename()), StandardCopyOption.REPLACE_EXISTING)`. The file name comes from a regular file listed in `inline/`, so it has no separator and cannot leave `out/inline/`.
- Rewrite per mail, before `Files.writeString(..html)`:
```java
private static String relativeUrl(String fileName) throws URISyntaxException {
    // JDK multi-argument URI constructor percent-encodes; & stays raw, so make it HTML-safe
    return new URI(null, null, "inline/" + fileName, null).toASCIIString().replace("&", "&amp;");
}

static String rewriteCid(String html, Map<String, String> fileNameById) throws URISyntaxException {
    ... Campaign.CID_REFERENCE.matcher(html).replaceAll(m -> {
        String name = fileNameById.get(m.group(1));
        return name == null ? Matcher.quoteReplacement(m.group())          // empty id or unknown: leave as is
                : Matcher.quoteReplacement(relativeUrl(name));
    });
}
```
(`replaceAll(Function)` cannot throw a checked exception; compute the URL map up front instead, `Map<String,String> urlById`, and keep the lambda free of `URISyntaxException`.) `Mail`/`MailMessage` are never changed: the rewritten string goes only to `Files.writeString`.

### Anti-Patterns to Avoid
- **Rewriting `message.html()` or building a second `Mail`** for the dry run: the sent mail must keep `cid:` (CLI-17). Rewrite the string that is written to disk, nothing else.
- **A copied content-type table** in the sample: it will drift from the core and from `Files.probeContentType` (see Alternatives).
- **A new `InlineImage` record, a rewrite helper class, an HTML parser**: one method in `SendCommand` and a regex are enough (Rule 0).
- **Silently skipping `Thumbs.db`/`desktop.ini`**: D-07 says they fail pre-flight with a message naming them.
- **Printing recipient values** in inline messages: only file names and IDs from the template author (Phase 5 D-14).

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Content-ID rules, image-type resolution, readable-file checks | A sample copy of these rules and of the extension table | A one-file probe `Mail.Builder...build()` | Single source of truth; host-dependent fallback (`Files.probeContentType`) is already in the core |
| Percent-encoding of the relative URL | Manual escaping | `new URI(null, null, "inline/" + name, null).toASCIIString()` | JDK quotes `%`, `#`, `?`, `[`, `]`, `\`, space and non-ASCII (UTF-8) |
| Regex replacement with literal values | String concatenation into `replaceAll("...")` | `Matcher.replaceAll(Function)` + `Matcher.quoteReplacement` | `$` and `\` in a name stay literal (run in this session with `a$1\x`) |
| Per-row duplicate problem reports | New dedupe code | Existing `invalid` map in `SendCommand` | Already reports one message once with the affected rows |
| Image generation in the build | Generator code or a plugin | One throwaway Python run, file checked in | Same rule as v0.1.0 CLI-13 and D-08 |

**Key insight:** the core enforces the rules that matter at send time. The sample's only job is to report the same defects earlier, once each, with file and ID names, so the check code that is not a one-liner (`cid:` pattern, the dry-run rewrite) is the only logic that exists twice.

## Runtime State Inventory

Not a rename/refactor/migration phase in the data sense, but one file is renamed (`attachments/logo.png` -> `attachments/sample.png`).

| Category | Items Found | Action Required |
|----------|-------------|------------------|
| Stored data | None. The journal keys are `campaign:<email>`, not file names `[VERIFIED: SendRunTest.java journal assertion `"campaign:" + addresses[i]`]` | none |
| Live service config | None | none |
| OS-registered state | None | none |
| Secrets/env vars | None | none |
| Build artifacts | `morimail-sample-serial/target/` may hold stale copies; example-campaign is read from the source tree by tests (`Path.of("example-campaign")`), not packaged | none (`mvn verify` rebuilds) |

Files that name the old PNG: `ExampleCampaignTest` (line 77 `logo.png (image/png, `, line 123 `resolve("logo.png")`), `GraphEndToEndTest` (line 168 `containsExactly("info.pdf", "logo.png")`) `[VERIFIED: grep this session]`. Outside `.planning/`, no README or doc names `logo.png` `[VERIFIED: grep over *.md/*.java/*.xml/*.html/*.yml, 2026-11-23]`. `.planning/milestones/...` mentions are history, leave them.

## Common Pitfalls

### Pitfall 1: Duplicate reports when `build()` re-finds a scan finding
**What goes wrong:** If the scan reports `body.html: cid:logo has no image` and `SendCommand` still builds rows, `build()` throws `no inline image for cid:logo`, and the same defect appears twice (D-01 forbids this).
**Why it happens:** `Campaign.read` returns a campaign even when problems were added (it does so today for a missing placeholder column, and `PreflightTest` relies on that).
**How to avoid:** return `null` from `Campaign.read` when the inline block added any problem. `SendCommand` already guards on `campaign != null` `[VERIFIED: SendCommand.java:158]`.
**Consequence to state in the plan:** row-level findings of `build()` (for example an invalid address) show up only after the inline findings are fixed. The same happens today when the header is broken. D-01 accepts it.
**Warning signs:** a `PreflightTest` that expects N problems and gets N+1 with a `no inline image for cid:` line.

### Pitfall 2: `cid:` with an empty ID in the rewrite
**What goes wrong:** `href="cid:"` (empty ID) matches the pattern with `group(1) == ""`; a naive rewrite turns it into `inline/.png` or similar.
**Evidence:** run in this session, `cid:` at the end of a string was replaced by `inline/.png` when the lambda did not guard the empty ID.
**How to avoid:** the rewrite looks the ID up in the map and leaves `m.group()` untouched when there is no entry. The core skips empty IDs the same way `[VERIFIED: Mail.java:513-517 `if (!matcher.group(1).isEmpty()) { referenced.add(matcher.group(1)); }`]`.

### Pitfall 3: `&` in a file name inside an HTML attribute
**What goes wrong:** `URI.toASCIIString()` leaves `&` raw (verified: `a&b.png -> inline/a&b.png`), so `<img src="inline/a&b.png">` has a bare ampersand.
**How to avoid:** `.replace("&", "&amp;")` after encoding. Note that such an ID can only be referenced from the template as `cid:a&b` (IDs are compared exactly and never decoded `[CITED: Mail.java Javadoc of build(), lines 359-363]`), which is legal; it is an edge case, but the one-line fix is free.

### Pitfall 4: Dry-run `attachments:` line now includes inline images
**What goes wrong:** `mails.get(0).message().attachments()` returns regular **and** inline attachments (`Attachment.contentId() != null` marks inline `[VERIFIED: Attachment.java record doc]`). Left unchanged, `logo.png` appears in the `attachments:` line and `DryRunTest`/`ExampleCampaignTest` string checks break.
**How to avoid:** filter on `contentId() == null` for `attachments:`, on `!= null` for `inline:`.

### Pitfall 5: Scan on the template can disagree with the rendered HTML at placeholder boundaries
**What goes wrong:** The scan reads the template, the core checks the rendered HTML. Example: template `$$firstName$$cid:logo`: the template scan sees `cid:` after `$` (a reference), after rendering `Annacid:logo` the lookbehind blocks it (preceded by a letter) so there is no reference. Also a `cid:` can appear only through a placeholder value.
**How to avoid:** nothing extra. The `build()` backstop reports these through the existing per-row aggregation (D-01), never as a silent wrong mail. Do not try to render in the scan.

### Pitfall 6: Existing example-campaign tests pin the old PNG
**What goes wrong:** After the rename `attachmentsAreSmallAndCarryNoMetadata` (reads `attachments/logo.png`) fails, the `startsWith("attachments: info.pdf ...").contains("logo.png (image/png, ")` assertion fails, and `GraphEndToEndTest` expects `["info.pdf","logo.png"]`.
**How to avoid:** update the three places in the same task as the file move; see Example Campaign Changes for the new expected values.

### Pitfall 7: Dry-run on a campaign with inline images creates `out/inline/` even with `--limit`
`--limit` only limits rows; the copy happens once regardless. Copy before the row loop. Not a defect, just state it in the test (`out/inline/logo.png` exists with `--limit 1`).

## Code Examples

### Generating the example logo (one-off, outside the repo)
Recipe verified in this session (`/tmp/.../scratchpad/genpng.py`, not in the repo): a 96x48 RGB PNG, zlib level 9, chunks IHDR, IDAT, IEND only, **166 bytes**, `ImageIO.read` gives 96x48. Same shape of generator as the v0.1.0 one (02-04 SUMMARY: "throwaway Python standard-library script outside the repository").
```python
import zlib, struct, sys
W, H = 96, 48
BG, FG, ACC = (255,255,255), (30,90,160), (240,160,30)
rows = bytearray()
for y in range(H):
    rows.append(0)                                   # filter type 0
    for x in range(W):
        if 4 <= x < W-4 and 4 <= y < H-4:
            px = ACC if (x-4)//12 % 2 == 1 and 12 <= y < 36 else FG
        else:
            px = BG
        rows += bytes(px)
def chunk(t, d):
    c = struct.pack(">I", len(d)) + t + d
    return c + struct.pack(">I", zlib.crc32(t + d) & 0xffffffff)
png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", W, H, 8, 2, 0, 0, 0)) \
    + chunk(b"IDAT", zlib.compress(bytes(rows), 9)) + chunk(b"IEND", b"")
open(sys.argv[1], "wb").write(png)
```
Run once with `python3 -I gen.py morimail-sample-serial/example-campaign/inline/logo.png`, keep the script out of the repo. The planner may change the picture; only the constraints matter: a few hundred bytes, no metadata chunk, `width`/`height` in the `<img>` equal the PNG size (96 x 48).

### Example `body.html` top
```html
<body>
<p><img src="cid:logo" alt="morimail logo" width="96" height="48"></p>
<p>Hello $$firstName$$,</p>
...
<p>The logo above is an inline image; the PDF and the PNG are attachments.</p>
```
`cid:logo` followed by `"` is read as ID `logo` by the pattern (ends at a double quote). `body.txt` unchanged, keep the existing sentence about the PNG consistent (D-09 only changes `body.html`; decide whether the old sentence "The attached PDF has the details; the PNG is a small sample image." stays, it is still true after the rename).

### Test helpers
Tests do not need real images: the core never decodes content, it resolves the type from the extension. `Files.writeString(inline.resolve("logo.png"), "png")` is enough for all non-example tests, so there is no image generation in tests (Rule 0). Add to `SampleFixtures` one tiny helper, for example `writeInline(Path campaignDir, String name)`, and have inline tests overwrite `body.html` with `<p><img src="cid:logo"></p>` (the default `SampleFixtures.HTML` has no `cid:`).

### Dry-run assertions that prove CLI-17
```java
// every rendered mail points at the copied image, none keeps cid:
for (String html : renderedHtmlFiles) {
    assertThat(html).contains("src=\"inline/logo.png\"").doesNotContain("cid:");
}
assertThat(Files.readAllBytes(out.resolve("inline/logo.png")))
        .isEqualTo(Files.readAllBytes(campaign.resolve("inline/logo.png")));
// same campaign sent through recording keeps cid: and carries the inline attachment
assertThat(sent.get(0).html()).contains("src=\"cid:logo\"");
assertThat(sent.get(0).attachments()).anySatisfy(a -> {
    assertThat(a.contentId()).isEqualTo("logo");
    assertThat(a.contentType()).isEqualTo("image/png");
});
```
Note: `RecordingTransport.sent()` returns `MailMessage`s whose `attachments()` carry `contentId` `[VERIFIED: RecordingTransport.java imports MailMessage; INLN-06 complete in REQUIREMENTS.md]`.

## Example Campaign Changes (CLI-18, D-08, D-09)

| File | Change |
|------|--------|
| `example-campaign/inline/logo.png` | new, generated once (recipe above) |
| `example-campaign/attachments/logo.png` | `git mv` to `attachments/sample.png` (240x120, 60959 bytes, IHDR/IDAT/IEND, unchanged content) |
| `example-campaign/body.html` | logo paragraph at the top, one sentence on inline vs attachments |
| `ExampleCampaignTest.dryRunWritesFiveRenderedMails` | `fileNames(out)` now also contains the directory `inline` -> expect 16 entries (15 files + `inline`); stdout line 0 `attachments: info.pdf (application/pdf, ` ... `sample.png (image/png, `; add the `inline:` line check; each of the five `.html` contains `src="inline/logo.png"` and no `cid:`; `out/inline/logo.png` equals the source |
| `ExampleCampaignTest.attachmentsAreSmallAndCarryNoMetadata` | `resolve("sample.png")` (line 123); add a second check for `inline/logo.png`: smaller than e.g. 1024 bytes, `ImageIO.read` gives the width/height used in `<img>`, chunk types exactly IHDR, IDAT, IEND (reuse `pngChunkTypes`) |
| `GraphEndToEndTest` (line 168) | names become `info.pdf, sample.png, logo.png` (attachments first, inline last); add: the `logo.png` node has `isInline` true and `contentId` `logo`, the other two nodes carry neither `[CITED: 05-01-SUMMARY.md: "inline node carries isInline: true and contentId: logo; the regular node carries neither key"]` |
| `ExampleCampaignTest.usesThreePlaceholdersAndFiveExampleRows` | no change; it reads `body.html` for placeholders and still sees exactly `city`, `firstName`, `topic` |

`Files.list(out)` in `fileNames(out)` includes the `inline` directory, so the `hasSize(15)` assertion and the `containsExactlyInAnyOrderElementsOf(expected)` assertion need `+ "inline"` or a filter on regular files. The `DryRunTest` tests of campaigns without `inline/` are unchanged because `out/inline/` is created only when images exist.

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| v0.1.0 core rejects any set `contentId` | Core accepts inline images with ID/type/duplicate/reference checks | Phase 5 (2026-11-23) | The sample can use `.inline(file, id)` directly |
| Dry run wrote only `.html/.txt/.subject.txt` | Dry run also writes `out/inline/` and rewrites `cid:` | this phase | Browser shows the images; sent mails unaffected |

**Deprecated/outdated:** none.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Stdout format `inline: logo.png (image/png, 166 bytes, cid:logo)` and the wording of the new problem messages are Claude's discretion (CONTEXT) and nobody depends on them yet | Pattern 2, Pattern 4 | Low; owner may prefer other wording, tests pin whatever the plan fixes |
| A2 | Returning `null` from `Campaign.read` on inline findings (so row-level `build()` findings appear only in a later run) is acceptable; derived from D-01 "mails are built only when the scan finds nothing" | Pitfall 1 | Low-medium; if the owner wants address problems in the same report, the plan needs a flag instead of null and a way to build without `cid:` errors |
| A3 | The probe mail uses `probe@example.org` and account id `probe`; the core never contacts anything on `build()` | Pattern 2 | Low; `build()` is pure validation (read in `Mail.java:373-410`) |
| A4 | JDK 17 behaves like the JDK 21 used here for `URI.toASCIIString`, `Matcher.replaceAll(Function)` (Java 9+) and `Files.probeContentType`; CI on Temurin 17 is the proof | Pattern 4 | Low; all APIs are old, but not run on 17 in this session |

## Open Questions

1. **Wording of problem messages and the `inline:` stdout line**
   - What we know: style is `inline/<name>: ...` or `body.html: cid:<id> ...`, no data values.
   - What's unclear: exact text (CONTEXT leaves it to the planner).
   - Recommendation: use the wording in Pattern 2 / Pattern 4 and pin it in tests with `contains` on stable fragments.
2. **Whether to strip the `attachments[0]: ` prefix of the probe message**
   - Recommendation: strip it with one `replaceFirst`; if the core changes the prefix the sample test that asserts the full message fails loudly, which is the intended drift alarm.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| JDK | build, tests | yes | OpenJDK 21.0.13.1 (`--release 17`) | CI runs Temurin 17 |
| Maven wrapper offline | `./mvnw -o -B -ntp -pl morimail-sample-serial -am test` | yes | green, 137 tests, 14 s | - |
| python3 | one-off generation of `inline/logo.png` (not part of the build) | yes | `/usr/bin/python3` | any tool that writes a PNG with only IHDR, IDAT, IEND |
| `file` command | - | no | - | not needed; use `ImageIO` and the chunk walk in the test |

No missing dependency blocks the phase.

## Validation Architecture

Skipped: `workflow.nyquist_validation` is `false` in `.planning/config.json`. For the planner's convenience, the test layout is in Recommended Project Structure; quick run `./mvnw -o -B -ntp -pl morimail-sample-serial -am test` (about 15 s), full gate `./mvnw -o -B -ntp verify`.

## Security Domain

`security_enforcement` is `true`, ASVS level 1 (`.planning/config.json`).

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no | unchanged |
| V3 Session Management | no | - |
| V4 Access Control | no | - |
| V5 Input Validation | yes | Campaign files are author input: content-ID shape via the core's rules (probe), `cid:` grammar copied from the core, exact-match IDs, `&` escaped in the rewritten attribute |
| V6 Cryptography | no | - |
| V12 Files | yes | Dry-run writes only to `out/inline/<file name>` where the name is the last path element of a regular file listed in `inline/`; `Files.copy` with `REPLACE_EXISTING`, no path built from template or CSV data |

### Known Threat Patterns

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Path traversal through a name written into `out/` | Tampering | Only listed regular files' last path elements are used; per-mail rendered files keep `safeName(email)` (unchanged) |
| Recipient data echoed into messages or logs | Information disclosure | Inline messages carry only file names and template IDs; the core cuts an ID read from rendered HTML at 64 code points `[CITED: 05-01-SUMMARY.md, T-05-02]` and the sample passes it on unchanged through the `invalid` map |
| Markup injection through a file name in the dry-run HTML | Tampering | Content-ID rules exclude quotes, angle brackets, whitespace; `&` is escaped; `%`/`#`/`?` are percent-encoded by `URI` |
| Real mail sent during development | - | Tests use `recording` and WireMock only; addresses are `example.org` (project rule) |

## Sources

### Primary (HIGH confidence, read or run this session)
- `morimail/src/main/java/de/morisoft/morimail/Mail.java` lines 20-110 (pattern, content-type table), 300-554 (`attach`, `inline`, `build`, `resolveAttachments`, `contentIdProblem`, `checkReferences`, `contentTypeOf`)
- `morimail/src/main/java/de/morisoft/morimail/Attachment.java` (record with `contentId`)
- `morimail-sample-serial/src/main/java/.../Campaign.java`, `SendCommand.java` (full files)
- Test sources: `ExampleCampaignTest`, `DryRunTest`, `PreflightTest` (head), `SampleFixtures`, `SendRunTest` (head), `CampaignTest` (attachment tests), `GraphEndToEndTest` (lines 60-190)
- `.planning/phases/05-inline-images-in-core-and-graph/05-01-SUMMARY.md`, `06-CONTEXT.md`, `REQUIREMENTS.md`, `docs/spec.md` sections 8.2, 8.3, 10.3
- Executed probes in the scratchpad: URI encoding table (`UriProbe.java`), `replaceAll(Function)` with `$`/`\` and the empty-ID case (`Repl.java`), core probe messages (`Probe.java`), PNG generator (`genpng.py`, 166 bytes, `ImageIO` 96x48)
- Baseline `./mvnw -o -B -ntp -pl morimail-sample-serial -am test`: BUILD SUCCESS, 137 tests

### Secondary / Tertiary
- None. No web research was needed: the phase uses only the JDK and the repo's own Phase 5 API, so no Microsoft page applies (the Graph wire format is Phase 5's and is already documented in `docs/architecture.md` by Plan 05-02).

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH, no new dependency; JDK APIs run here
- Architecture: HIGH, all integration points read in source
- Pitfalls: HIGH, the empty-ID, `&` and attachment-list pitfalls were reproduced; Pitfall 5 is reasoned from the pattern and the renderer, not run end to end

**Research date:** 2026-11-23
**Valid until:** 30 days (stable; only changes if Phase 5 core code changes)
