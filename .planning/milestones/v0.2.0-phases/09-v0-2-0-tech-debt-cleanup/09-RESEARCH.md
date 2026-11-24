# Phase 9: v0.2.0 tech debt cleanup - Research

**Researched:** 2026-11-24
**Domain:** In-repo cleanup of a Java 17 library, a sample CLI and planning artifacts. No new technology, no new dependency.
**Confidence:** HIGH. Every line number, signature and quoted value below was read from the file this session. The proposed code snippets are `[ASSUMED]` until they compile (they use only JDK APIs).

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Core: WR-01, control characters in an echoed `cid:` ID**
- **D-01:** In `Mail.Builder.checkReferences`, replace every code point of the shown ID that is an ISO control character, or of type `LINE_SEPARATOR`, `PARAGRAPH_SEPARATOR` or `FORMAT`, with `?`, then cut at 64 code points as today. The message text stays `no inline image for cid:<shown>`. This keeps the message useful and stops log-line forging and terminal escapes from recipient data.
- **D-02:** Only IDs read from the rendered HTML need this, because they can come from recipient data. Builder-supplied values (content ID, content type) are left as they are (the review rates them low risk). The planner checks whether the sample (`Campaign`) or the helper prints an ID read from rendered HTML through its own message. If one does, it gets the same cleaning. If they only pass on the core message, nothing more is needed.
- **D-03:** Test: a rendered body with `cid:` followed by `\u001b`, `\u0085` and `\u2028` gives a message that contains none of them.

**Core: WR-02, the whole-HTML `cid:` scan**
- **D-04:** Keep the owner's 2026-11-22 rule. Any `cid:` in the rendered HTML that does not follow a letter or digit counts, with no HTML parser (Phase 5 D-04 as amended). Restricting the match to attribute context is **not** done, because it would reverse an owner decision.
- **D-05:** Add `<` to the ID terminator set: `[^"'\s)<>]`. This makes `<p>cid:logo</p>` resolve to `logo`. It also makes the terminator set match the content ID rule (Phase 5 D-09 already rejects `<` in an ID). The change only loosens the check. No mail that passes today can fail, because an accepted content ID can never contain `<`. Change all three copies together: `Mail.java:48`, `Campaign.java:48` and `HtmlImages.java:111`. The existing `cidPatternAgreesWithTheCore` tests keep them equal. Add `>cid:logo</p` to their corpus.
- **D-06:** Do **not** strip a trailing `/` (`src=cid:logo/>`), because `/` is a legal content ID character. Document instead that an unquoted `src` directly followed by `/>` reads the `/` as part of the ID, and that quoting the value avoids this.
- **D-07:** Update the `docs/architecture.md` §10.3 row (line 40) for the new terminator, the `/` note and the accepted trade-off: prose such as `ref=cid:123` still counts as a reference. Replace the decision IDs in the `Mail.java:46` comment ("D-04 as amended 2026-11-22, D-05, D-06") and in `HtmlImages.java:110` ("Phase 5 D-04 to D-06") with the rule they stand for. Planning IDs mean nothing to readers of the public source.

**Core: IN-01, content type of image formats outside the table**
- **D-08:** Add `webp` → `image/webp`, `bmp` → `image/bmp` and `svg` → `image/svg+xml` to `Mail.Builder.CONTENT_TYPES`, so these types resolve the same way on every platform. Update the `attach(Path)` Javadoc, which currently says "twelve extensions" and lists them, to the new count and list. Check the `inline()` Javadoc too. Check the helper's own `CONTENT_TYPES` in `HtmlImages.java`. If it lacks these three, add them there as well, so a helper-made inline image and a core-built one resolve to the same type.

**Core: IN-03, case-sensitive content IDs**
- **D-09:** No behaviour change. Content IDs and references stay exact and case-sensitive (Phase 5 D-05, an owner decision). Rejecting IDs that differ only in case would tighten a check in the released 0.2.0 API, and nothing has shown a real problem. Record in `docs/architecture.md` that `Logo` and `logo` are two distinct inline images. Also record that the owner's 2026-11-23 live test did not cover case-differing IDs, and that an author who wants to be safe across clients should not use IDs that differ only in case. Mention it in the `inline()` Javadoc in one sentence.

**Sample: pre-flight validates only the first mail**
- **D-10:** Pre-flight calls `mailer.validate` on **every** non-null built mail, not only `mails.get(0)`. Group identical messages by row line, the same way the `invalid` map does with `invalidMail(message, lines, rows)`, so a limit problem shared by 500 rows is one problem line and not 500. Message wording follows the existing pattern, for example "mailer.validate rejected the mail: ...", with the line list from `invalidMail`. `GraphTransport.validate` only does `Files.size` and a body-length estimate, with no token and no HTTP, so validating every row is cheap.
- **D-11:** Test with the `recording` transport or a fake mailer, whichever the existing `SendCommand` tests use. Use a campaign whose first row passes and a later row fails `validate`. Pre-flight must fail with exit code 2, name that row's line, and send nothing.
- **D-12:** Update `docs/spec.md` §10.x / the sample section if it says pre-flight validates "the first mail". The planner greps for it. The sample README gets the same check.

**Helper: IN-01, `strip()`**
- **D-13:** Keep `strip()` (the owner accepted the behaviour in 07-UAT test 4). Close the finding with documentation only: add one sentence to the `HtmlImages` class Javadoc saying white space around a `src` value is ignored. Correct the code comment at `HtmlImages.java:348` so it no longer claims the HTML rule.

**Helper: IN-02, untested Error rethrow**
- **D-14:** Accept the gap, as the reviewer suggests: "Do not add a seam only for this (Rule 0)". The reader thread and the static `HttpClient` cannot be made to throw an `Error` through WireMock without a new seam. Record the closure in `07-REVIEW-DISPOSITION.md` (IN-02 Source cell: closed in Phase 9, accepted without test, no seam per Rule 0). Add no test seam. If the researcher finds an existing package-private path that reaches `readAtMost` with a custom `InputStream`, without any new seam, a test may be added instead.

**Planning artifacts**
- **D-15:** Fix the `07-01-SUMMARY.md` frontmatter so `gsd-tools query summary-extract` parses it. It currently fails with "not parseable YAML", probably an unquoted `: ` inside a list item such as the `provides:` entries. Correct the stale "morimail in provided scope" text to compile scope (changed by 07-04). Verify with `summary-extract`.
- **D-16:** In `08-02-SUMMARY.md` (lines 10 and 104), add that the owner has since released 0.2.0 (tag `v0.2.0`). Keep the historical statement and mark it as superseded, so the record stays true for its date.
- **D-17:** Update `05-REVIEW-DISPOSITION.md`: WR-01 fixed, WR-02 fixed (terminator) with the trade-off documented, IN-01 fixed, IN-02 fixed earlier by 08 IN-03 (spec 10.3 aligned; the planner confirms the decision-ID comments are gone after D-07), IN-03 deferred with a reason (D-09). Update the 07 disposition for IN-01 and IN-02 (D-13, D-14).

**Phase gates (carried forward, not re-decided)**
- Every unit ends with `./mvnw verify` green offline and a commit as Stephan Wald <stephan@morisoft.de>, with the `GIT_*` identity exported. Javadoc stays doclint-clean. `-Prelease verify -Dgpg.skip -pl morimail,morimail-html -am` stays green.
- Failure messages hold fixed texts and never echo data values or addresses. D-01 is the one place that echoes HTML-read text, and it is now cleaned.
- No new dependency, option or hook (Rule 0).

### Claude's Discretion
- How to split the work into plans. A natural cut is one plan for the core and the three pattern copies (D-01 to D-09), one for the sample pre-flight (D-10 to D-12), and one for the helper docs and planning artifacts (D-13 to D-17). One plan is also fine if it stays small.
- Exact Javadoc and architecture wording, and the exact test names.

### Deferred Ideas (OUT OF SCOPE)
- Restricting `cid:` matching to attribute and `url(...)` context (WR-02 option 1). It reverses owner decision D-04. Revisit only if the owner asks.
- Rejecting content IDs that collide ignoring case (IN-03). Revisit if a live test shows trouble with case-differing IDs.
- Git housekeeping (push main so Temurin 17 CI runs, and track or delete `07-PATTERNS.md` and `state.json`). This is the owner's job, per ROADMAP.
</user_constraints>

<phase_requirements>
## Phase Requirements

None. No requirement IDs were assigned (REQUIREMENTS "TBD"). The CONTEXT decisions D-01..D-17 are the contract; the table below maps each to what the planner needs.

| Decision | Research support (section) |
|----------|---------------------------|
| D-01..D-03 | Edit Map 1, Code Examples A, Pitfall 1 |
| D-04..D-07 | Edit Map 1-3, 6; Pitfall 2, 3; Code Examples B |
| D-08 | Edit Map 1, 3, 4; Pitfall 5 |
| D-09 | Edit Map 1, 6 |
| D-10..D-12 | Edit Map 5; Pitfall 4; Code Examples C |
| D-13, D-14 | Edit Map 3; D-14 finding |
| D-15..D-17 | Planning Artifact Findings |
</phase_requirements>

## Summary

Phase 9 is a set of small edits in four places: `Mail.java` (core), `Campaign.java` and `SendCommand.java` (sample), `HtmlImages.java` (helper), plus `docs/architecture.md`, `docs/spec.md` and five planning files. No library, API or option is added. `./mvnw -B -ntp -o verify` is green at HEAD (BUILD SUCCESS; tests: morimail 366, morimail-html 71, morimail-sample-serial 164, total 601, 0 failures). The scoped release build `./mvnw -B -ntp -Prelease verify -Dgpg.skip -pl morimail,morimail-html -am` is green with 0 WARN lines. The single-test pattern `-q -o -pl morimail-sample-serial -am test -Dtest='PreflightTest' -Dsurefire.failIfNoSpecifiedTests=false` exits 0. [VERIFIED: run this session]

Four findings change how the planner should write tasks:
1. **An existing test breaks on purpose.** `PreflightTest.brokenCampaignReportsEveryProblemTogether` (line 44) expects `"recipients.csv line 2: mailer.validate rejected the first mail: too large for the test"`. `RecordingTransport.failValidation` fails every mail, so after D-10 rows 2 and 4 both fail (row 3 has a bad address and its mail is `null`) and the line becomes `mailer.validate rejected the mail: too large for the test (recipients.csv lines 2, 4)`. The problem count stays 4 and the order stays. That test must be updated in the same task as the `SendCommand` change.
2. **`RecordingTransport` cannot fail only a later row.** `failValidation` is global (it throws for every message). `MailAccount.Builder.transport(String)` takes only a transport id, so a test cannot pass its own transport instance. D-11 therefore needs the real `graph` transport (its `validate` is local) with an oversize body in one row, or an oversize shared attachment for the grouping case. No new seam is needed. See Code Examples C.
3. **D-14 is confirmed: no existing seam.** `readAtMost` is `private static` and its `InputStream` always comes from the private static `Http.CLIENT`. Accept the gap and record it.
4. **The YAML error in `07-01-SUMMARY.md` is line 11:** `provides: the Temurin 17 mid-phase CI round-trip pattern and the pinned cid: pattern copy technique` (the `cid: ` inside a plain scalar). Confirmed by removing the colon in a temporary copy: `summary-extract` then parsed the file. Do not add `: ` to any frontmatter value you edit (this also applies to `08-02-SUMMARY.md` line 10, which is a frontmatter `provides:` line).

**Primary recommendation:** Three plans as CONTEXT suggests (core + pattern copies + docs; sample pre-flight; helper docs + planning artifacts). Do the three `CID_REFERENCE` copies, the corpus additions in both `cidPatternAgreesWithTheCore` tests, and the `Mail.java` Javadoc/comment edits in one commit so the copies are never out of step.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| `cid:` reference scan and message cleaning | Core library (`Mail.Builder`) | Sample and helper hold copies | The core owns `build()` checks; copies exist so the sample and helper agree with `build()` |
| Content-type table for images | Core library | Helper (copy of the image rows) | The helper must give the same type as the core |
| Size-limit validation | Transport (`GraphTransport.validate`) | Sample pre-flight calls it through `Mailer.validate` | Spec: limits are checked in the transport's `validate`, not in the core or sample |
| Pre-flight grouping and report | Sample CLI (`SendCommand`) | — | Report text and exit code 2 are sample behaviour |
| Dispositions, summaries | `.planning/` files | — | Planning records only |

## Standard Stack

No new libraries. Nothing is installed.

| Item | Version | Source |
|------|---------|--------|
| Java | `--release 17`, JDK 21 locally (Temurin 21.0.13.1) | [VERIFIED: `java -version`] |
| JUnit Jupiter / AssertJ / WireMock / picocli / Jackson | as pinned in the parent POM and `.claude/CLAUDE.md` | [CITED: .claude/CLAUDE.md] |

**Package Legitimacy Audit:** not applicable, this phase installs no external package. Packages removed: none. Packages flagged SUS: none.

## Architecture Patterns

### Data flow (what changes where)

```
template HTML + row data
        |  TemplateRenderer.render (core)
        v
rendered HTML --> Mail.Builder.checkReferences --CID_REFERENCE--> IDs
        |                                              |
        |                       unknown ID --> InvalidMailException("no inline image for cid:" + cleaned(<=64 cp))   [D-01, D-05]
        v
SendCommand pre-flight: per row build() --> InvalidMailException --> invalid map (message -> lines)
                         per row mailer.validate --> PermanentMailException --> NEW map (message -> lines)   [D-10]
        both maps --> invalidMail(message, lines, rows) --> stderr, exit 2
```

### Edit Map (exact current locations, read this session)

**1. `morimail/src/main/java/de/morisoft/morimail/Mail.java`**

| Lines | Current content | Change |
|-------|-----------------|--------|
| 46-47 | Comment: `// A cid: reference in any case, not preceded by a letter or digit; its id ends at a quote, whitespace, ) or > (D-04 as amended 2026-11-22, D-05, D-06).` and `// The serial mail sample copies this pattern (Campaign.CID_REFERENCE); keep both equal.` | Rewrite without decision IDs; say `<` ends an ID too; name all three copies (sample `Campaign`, helper `HtmlImages`); say a trailing `/` of an unquoted `src=cid:x/>` is part of the ID |
| 48-49 | `private static final Pattern CID_REFERENCE =` / `Pattern.compile("(?i)(?<![\\p{L}\\p{N}])cid:([^\"'\\s)>]*)");` | Class becomes `[^\"'\\s)<>]*` |
| 51-65 | `CONTENT_TYPES = Map.ofEntries(` pdf, png, jpg, jpeg, gif, txt, html, htm, csv, zip, docx, xlsx (12 entries) | Add `Map.entry("webp", "image/webp")`, `Map.entry("bmp", "image/bmp")`, `Map.entry("svg", "image/svg+xml")` (15 entries; `Map.ofEntries` has no size limit) |
| 275-283 | `attach(Path)` Javadoc: `built-in table of` / `twelve extensions (pdf, png, jpg, jpeg, gif, txt, html, htm, csv, zip, docx, xlsx,` / `matched case-insensitively), ...` | "fifteen extensions" and add `webp, bmp, svg` to the list; re-wrap lines |
| 327-343 | `inline(Path, String)` Javadoc: "The file name and content type are resolved in {@link #build()} as for {@link #attach(Path)}." | Content-type text needs no change (it points to `attach`). Add one sentence for D-09: IDs are compared exactly, `Logo` and `logo` are two images, and an ID that differs only in case from another is best avoided because mail clients may match case-insensitively |
| 350-370 | `build()` Javadoc: `Its id ends at a quote, whitespace, {@code )} or {@code >}, is compared exactly and is` (line 361-362) and `an id read from the HTML body is cut at 64 code points` (line 367-368) | Add `{@code <}` to the terminators; say that control, line-separator, paragraph-separator and format characters in the shown id are replaced by `?`; add the `/>` note (D-06) |
| 509-533 | `checkReferences(String html, Set<String> inlineIds)`; lines 522-523: `String shown = id.codePointCount(0, id.length()) <= 64 ? id : id.substring(0, id.offsetByCodePoints(0, 64)) + "...";` then line 524 `throw new InvalidMailException("no inline image for cid:" + shown);` | Replace the `shown` computation (D-01). Message text prefix stays `no inline image for cid:` |

Note: line 529-530, the second message `"inline image " + id + " is not referenced in the HTML body"`, echoes a **builder-supplied** content ID (validated by `contentIdProblem`, which rejects blank, line breaks, whitespace, quotes, parentheses, `<` and `>`), so it is not recipient data. It is left alone per D-02. (Note `contentIdProblem` does not reject other control characters such as U+001B; it is builder-supplied, so the review rated it low risk. Leave it.)

**2. `morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/Campaign.java`**

| Lines | Content | Change |
|-------|---------|--------|
| 44-49 | Javadoc "The cid: reference pattern of the core's Mail.Builder, copied so the dry-run rewrite and the pre-flight scan agree with build(); keep both equal." and `static final Pattern CID_REFERENCE = Pattern.compile("(?i)(?<![\\p{L}\\p{N}])cid:([^\"'\\s)>]*)");` | Terminator class to `[^\"'\\s)<>]*` |
| 237-249 | `checkInline` scan of `html` (the **template** HTML as written, not rendered): `Matcher matcher = CID_REFERENCE.matcher(html);` line 239; message at line 247: `problems.add("body.html: cid:" + id + " has no image in inline/");` | No change for D-02: this ID comes from the campaign's own `body.html`, written by the owner of the campaign, never from recipient data (the javadoc at 184-189 says "The HTML is scanned as written, so a cid: reference cannot be built from a placeholder"). It is also not cut at 64. Record in the plan that it was checked and left alone. |

**3. `morimail-html/src/main/java/de/morisoft/morimail/html/HtmlImages.java`**

| Lines | Content | Change |
|-------|---------|--------|
| 38-50 | Class Javadoc paragraph "Which src values change: ..." ends `The only character reference that is decoded is {@code &amp;}. A percent-encoded value is decoded once, and a query and a fragment are dropped.` | Add one sentence (D-13): white space around a `src` value is ignored |
| 110-112 | `// Copy of Mail.Builder.CID_REFERENCE (Phase 5 D-04 to D-06); keep both equal.` / `static final Pattern CID_REFERENCE =` / `Pattern.compile("(?i)(?<![\\p{L}\\p{N}])cid:([^\"'\\s)>]*)");` | Replace the comment with the rule; terminator class to `[^\"'\\s)<>]*` |
| 132-137 | `// The image entries of Mail.Builder.CONTENT_TYPES (D-12); HtmlImagesTest pins the result against build().` and `Map.ofEntries(png, jpg, jpeg, gif)` (4 entries) | Add the same three entries (webp, bmp, svg). The comment still cites `D-12`; it is on a line being edited, so drop the ID there too |
| 331-337 | `rewrite`: `Matcher references = CID_REFERENCE.matcher(html);` fills `taken` | Used only to reserve IDs; **never printed** (checked: no `"cid` string in any message, `taken` is only used by `newId`). No cleaning needed (D-02). |
| 348-349 | `// Browsers and mail clients strip surrounding whitespace from a src; raw stays as written for messages and the span.` and `String value = raw.replace("&amp;", "&").strip();` | Rewrite the comment (D-13): `strip()` removes all Unicode white space, which is wider than the HTML rule (ASCII white space); accepted by the owner. Keep the code. |
| 658-670 | `contentTypeOf(Path, String)`: table lookup then `Files.probeContentType` | No change; picks up the new entries. `IMAGE_TYPE` (`(?i)image/[a-z0-9][a-z0-9.+-]*`, line 130) accepts `image/svg+xml`, `image/webp`, `image/bmp`. |

Other planning-ID comments remain in `HtmlImages.java` (for example `D-14`, `P6`, `A1`, `D-11, D-19`, `WR-01`). Only the two named in D-07 (plus the `D-12` on the line being edited) are in scope; do not sweep the rest (Rule 0, scope).

**4. `SendCommand` (`morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/SendCommand.java`)**

| Lines | Content |
|-------|---------|
| 122 | `private static String invalidMail(String message, List<Integer> lines, int rows)` : 1 line gives `recipients.csv line N: message`; all rows gives `message (all N rows)`; otherwise `message (recipients.csv lines a, b, c, ... K rows)` (first 5 lines shown) |
| 172-198 | Per-row build into `mails` (null for an `InvalidMailException`), grouping into `Map<String, List<Integer>> invalid` keyed by `e.getMessage()`; loop at 195-197 adds `invalidMail(entry.getKey(), entry.getValue(), campaign.rows().size())` to `problems` |
| 199-206 | `Mailer mailer = null; if (accountOk && !dryRun) { mailer = mailerFactory.apply(account); ... }` |
| 208-214 | The block to change: `if (mailer != null && !mails.isEmpty() && mails.get(0) != null) { try { mailer.validate(mails.get(0)); } catch (PermanentMailException e) { problems.add("recipients.csv line " + campaign.rows().get(0).line() + ": mailer.validate rejected the first mail: " + e.getMessage()); } }` |
| 289 | `for (Attachment attachment : mails.get(0).message().attachments())` in `writeDryRun` (uses the first mail only to list attachments; not a validation, leave) |
| 316 | `html = Campaign.CID_REFERENCE.matcher(html).replaceAll(m -> Matcher.quoteReplacement(srcById.getOrDefault(m.group(1), m.group())));` in the dry-run rewrite |

Dry-run effect of D-05: `<p>cid:logo</p>` now has group(1) `logo` and is rewritten to `<p>inline/logo.png</p>` (before: group `logo</p` found no entry and stayed unchanged). Only `srcById` hits are replaced, so everything else is untouched. No existing test depends on the old behaviour (see Pitfall 3).

**5. Docs**

| File:line | Content | Change |
|-----------|---------|--------|
| `docs/architecture.md:40` | §10.3 (cid: references) row. Quoted: `Its ID ends at a double quote, an apostrophe, whitespace, `)`, `>` or the end of the body, is compared exactly and is never decoded; an empty ID is ignored. The content ID rules reject every character that ends an ID, so each accepted ID can be found. A failure message shows an ID read from the HTML cut at 64 code points.` and the rationale `(owner decision of 2026-11-22, the D-04 amendment)` | Add `<` to the terminators; control/separator/format characters in the shown ID become `?`; state that `Logo` and `logo` are different images (D-09, with the "2026-11-23 live test did not cover case-differing IDs" note); `src=cid:logo/>` reads `logo/` (quote the value); `ref=cid:123` in prose still counts. Also replace "the D-04 amendment" by the rule (it is a planning ID in a public doc, same reason as D-07). |
| `docs/architecture.md:52` | Graph inline notes; ends `Confirmed live on 2026-11-23: ... (LiveGraphTest.sendsOneMailWithAnInlineImage).` | Needs no edit; the case-differing-IDs remark belongs on line 40 or as a sentence after line 52's live result. Planner choice. |
| `docs/spec.md:321` | §8.3 step 2: `Check everything before the first send: all placeholders in the template exist as CSV columns, every row has a syntactically valid email, and `mailer.validate` passes for the first row's mail. Report all problems at once and exit with code 2.` | The only place in the repo that says "first row's mail" for validation. Change to "for every row's mail" (D-12). The spec is binding, but D-12 is an explicit owner decision and the code and spec must agree. |
| `docs/spec.md:333` | Mentions Pre-flight (step 2) reports inline problems | No change |
| `README.md:169` | `Pre-flight checks the campaign before the first mail and stops with exit code 2, ...` | This means "before sending the first mail", not "validates the first mail". No change. The sample module has no README of its own (`ls morimail-sample-serial`: `example-campaign`, `pom.xml`, `src`, `target`). |
| `SendCommand.java` help text (lines 40-58) | Description, footer with exit codes | No sentence about validating the first mail. No change. |

`docs/spec.md` §10.3 (lines 392-402) states no terminator grammar, so D-05 needs no spec edit.

### Anti-Patterns to Avoid
- **Do not strip a trailing `/`** (D-06): `/` is a legal content-ID character (`contentIdProblem` rejects only blank, line breaks, whitespace, `"`, `'`, `(`, `)`, `<`, `>`).
- **Do not validate with a new transport hook or option.** `Mailer.validate(Mail)` already throws `PermanentMailException`; reuse it in a loop.
- **Do not add a `Pattern` shared constant across modules.** The copies are deliberate (the library jar must not depend on the sample, and the helper depends on the core only through its public API). Keep three copies.

## Don't Hand-Roll

| Problem | Don't build | Use instead | Why |
|---------|-------------|-------------|-----|
| Grouping many identical pre-flight failures | A new report class | `invalidMail(message, lines, rows)` plus a `Map<String, List<Integer>>` exactly like `invalid` | Already produces the single-line, "all N rows" and "lines a, b, ..." forms |
| Cleaning an ID | A regex over the message or a utility class | A few lines with `Character.isISOControl`, `Character.getType` and `appendCodePoint` inside `checkReferences` (or one private static helper in `Mail.Builder`) | Rule 0, and the 64-code-point cut must stay code-point based |
| Failing validation on a later row | A new test seam in `RecordingTransport` or a new `TransportProvider` | The real `graph` transport with a stub `TokenProvider` | `GraphTransport.validate` is local and already enforces 3 MiB and 4 MiB |

## Common Pitfalls

### Pitfall 1: Cleaning after the cut changes the semantic of "64"
**What goes wrong:** Cutting by `substring` before cleaning, or cleaning by UTF-16 `char`, splits surrogate pairs or counts differently from today.
**How to avoid:** Work on code points: `id.codePoints().limit(64)`, map each to `'?'` or itself, `appendCodePoint`; append `"..."` only when the ID has more than 64 code points. Keep the existing test `longReferenceIsCutInTheMessage` (`MailBuilderTest.java:526-531`, expects `"no inline image for cid:" + "a".repeat(64) + "..."`) green.
**Warning signs:** `\u2028` or `\u0085` still visible in the message; a test with a supplementary character fails.

Facts that matter for the D-03 test (HIGH, from `Mail.java:48-49` and Java `Pattern` semantics `[ASSUMED: JDK documentation, standard behaviour]`): without `UNICODE_CHARACTER_CLASS`, `\s` matches only ASCII white space `[ \t\n\x0B\f\r]`. So U+0085 (NEL), U+2028 and U+001B are **not** terminators and do flow into the ID. A body `"<p>cid:a\u001bb\u0085c\u2028d</p>"` (after D-05 the `<` ends it) yields one ID with all three characters. Expected message `no inline image for cid:a?b?c?d`. Assert both the exact message and that it contains none of the three characters.

### Pitfall 2: The three pattern copies drift
**What goes wrong:** One copy changed, the others not.
**How to avoid:** One commit for all three plus both corpus tests. The agreement tests build each corpus entry through the real `Mail.build()`, so a copy that is wider or narrower than the core fails (details below).

### Pitfall 3: Existing tests that might depend on `<` not ending an ID
Searched all tests and fixtures with the regex `cid:[A-Za-z0-9._-]*(<|<)` over `*.java`, `*.html`, `*.txt`, `*.md` outside `.planning`: **no match**. The example campaign body uses `cid:logo` followed by `"` only. There is no test that expects a message such as `cid:logo</p`. The planning-only mentions (`05-01-PLAN.md:122`, `05-01-SUMMARY.md:43`) describe the old D-06 behaviour as history; they stay as they are. [VERIFIED: grep this session]

### Pitfall 4: The corpus entry `src=cid:logo/>` cannot go into the agreement tests
The two `cidPatternAgreesWithTheCore` tests call `builder.inline(root.resolve(id + ".png"), id)` for each expected ID. For `logo/` that path is `logo/.png` and does not exist, so `build()` fails for the wrong reason. Test the D-06 `/` behaviour in `MailBuilderTest` instead: `valid().html("<img src=cid:logo/>").inline(png, "logo").build()` throws `no inline image for cid:logo/`, and `...inline(file, "logo/")` builds (a `/` is accepted in a content ID, `png` file named `logo.png`).

### Pitfall 5: The post-edit pre-flight message changes more than the word "first"
See Summary finding 1. The new text is built from `"mailer.validate rejected the mail: " + e.getMessage()` passed to `invalidMail`, so single-row failures read `recipients.csv line 7: mailer.validate rejected the mail: ...` (note the position of the colon text follows the same style as the build problems), and several rows read `mailer.validate rejected the mail: ... (recipients.csv lines 2, 4)`. Two groups with different messages (for example different oversize byte counts, because `GraphTransport` puts the computed size into the message) become two problem lines. That is acceptable: the message differs, so the rows differ.

### Pitfall 6: Frontmatter edits must not add `: `
In `07-01-SUMMARY.md`, remove or quote `cid: pattern` on line 11. In `08-02-SUMMARY.md` line 10 (frontmatter `provides:`) any added "superseded: ..." text must avoid `: ` or the line must be quoted. After each edit run `gsd-tools query summary-extract <file>` from the repo root (the tool refuses paths outside the project, `Path escapes allowed directory`).

### Pitfall 7: Commit identity
Workspace `GIT_AUTHOR_NAME`, `GIT_AUTHOR_EMAIL`, `GIT_COMMITTER_NAME` and `GIT_COMMITTER_EMAIL` are set in the environment (values not shown here) and override the repo-local config. Local config is `Stephan Wald` / `stephan@morisoft.de`. There is a `.git/hooks/pre-commit`. The executor must export all four variables as `Stephan Wald` / `stephan@morisoft.de` before every commit and check `git log -1 --format='%an <%ae> | %cn <%ce>'`. [VERIFIED: `env`, `git config`, `ls .git/hooks`]

## Code Examples

### A. WR-01 cleaning in `checkReferences` (`[ASSUMED]`, JDK API only, not compiled)
```java
// Replaces Mail.java:522-523. Inside the existing `for (String id : referenced)` loop.
private static String shown(String id) {
    StringBuilder out = new StringBuilder();
    id.codePoints().limit(64).forEach(cp -> {
        int type = Character.getType(cp);
        boolean unprintable = Character.isISOControl(cp) || type == Character.LINE_SEPARATOR
                || type == Character.PARAGRAPH_SEPARATOR || type == Character.FORMAT;
        out.appendCodePoint(unprintable ? '?' : cp);
    });
    return id.codePointCount(0, id.length()) > 64 ? out + "..." : out.toString();
}
// throw new InvalidMailException("no inline image for cid:" + shown(id));
```
Based on the reviewer's snippet in `05-REVIEW.md` WR-01 Fix, plus `FORMAT` as D-01 says. `Mail.Builder` already has private static helpers (`contentIdProblem`, `contentTypeOf`), so one more is in style.

Test (add to `MailBuilderTest`, near `longReferenceIsCutInTheMessage`, line 526):
```java
@Test
void controlCharactersInAReferenceAreNotEchoed() {
    assertThatThrownBy(() -> valid().html("<p>cid:a\u001bb\u0085c\u2028d</p>").build())
            .isInstanceOf(InvalidMailException.class)
            .hasMessage("no inline image for cid:a?b?c?d");
}
```
`valid()` is the existing helper in `MailBuilderTest` (used at lines 393, 441, 477). `\u0085` and `\u2028` are not Java line terminators, so they are safe inside a string literal. Optionally add a `FORMAT` character such as `\u200b` (zero-width space) for completeness.

### B. New corpus entries (both `cidPatternAgreesWithTheCore` tests)
Existing corpus is `List<Map.Entry<String, Set<String>>>` (HTML → expected IDs), each entry checked against the copy **and** built through the core.
- Sample: `morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/CampaignTest.java:426-455` (the method starts at line 427; corpus is lines 433-440; it writes `logo.png`, `a.png`, `b.png` into `root`; pattern under test `Campaign.CID_REFERENCE`).
- Helper: `morimail-html/src/test/java/de/morisoft/morimail/html/HtmlImagesTest.java:268-298` (corpus lines 273-283; `@BeforeEach` writes `logo.png` into `base`, the test adds `a.png`, `b.png`; pattern under test `HtmlImages.CID_REFERENCE`).
Add to both: `Map.entry("<p>cid:logo</p>", Set.of("logo"))`. CONTEXT says to add `>cid:logo</p`; the entry `"<p>cid:logo</p>"` contains exactly that. Before D-05 this entry fails in both tests (the copy returns `logo</p`, and `Mail.build()` throws), so write the test first to see it fail. The helper corpus already has `"<p>(cid:a)</p>"` and `"<p>cid:a\"b</p>"` that the sample corpus lacks; adding them to the sample is optional and not required by the decisions.

### C. Pre-flight loop (D-10) and its tests (D-11)
Replace `SendCommand.java:208-214` with a loop that mirrors the `invalid` map (assumed shape, `[ASSUMED]`):
```java
if (mailer != null) {
    Map<String, List<Integer>> rejected = new LinkedHashMap<>();
    for (int i = 0; i < mails.size(); i++) {
        if (mails.get(i) == null) {
            continue;
        }
        try {
            mailer.validate(mails.get(i));
        } catch (PermanentMailException e) {
            rejected.computeIfAbsent("mailer.validate rejected the mail: " + e.getMessage(),
                    m -> new ArrayList<>()).add(campaign.rows().get(i).line());
        }
    }
    for (Map.Entry<String, List<Integer>> entry : rejected.entrySet()) {
        problems.add(invalidMail(entry.getKey(), entry.getValue(), campaign.rows().size()));
    }
}
```
`mails` is index-aligned with `campaign.rows()` (one add per row, `null` on build failure), and `mailer != null` implies `campaign != null` (`mailer` is only created when `accountOk`, but `campaign` could still be `null`: `mails` is empty then, so the loop does nothing; keep the `mails.isEmpty()` safety by iterating `mails`, as above, not `campaign.rows()`). The `rejected` entries must come after the `invalid` entries, as the current code does.

`mailer.validate` runs for rows beyond `--limit` too. The existing build loop already does that, so this is consistent; do not special-case it.

**Update the existing test** `PreflightTest.brokenCampaignReportsEveryProblemTogether` (line 44) to expect, as the fourth line:
`"mailer.validate rejected the mail: too large for the test (recipients.csv lines 2, 4)"` (rows: 2 = anna, 3 = bad address with `null` mail, 4 = the duplicate address, whose mail still builds). Total stays `4 problem(s)`.

**New tests** (in `PreflightTest`). Two ways to make a **later** row fail only, no new seam:
1. *Later row, real Graph transport.* Account `MailAccount.builder("main").transport("graph").auth(scope -> "token").build()` (`TokenProvider` is a `@FunctionalInterface`; the Graph provider accepts `baseUrl`/`saveToSentItems` only and requires `auth`; no HTTP happens in `validate`). Campaign: `SampleFixtures.writeCampaign(root, csv)` with `body.html` containing `$$city$$` (the default `SampleFixtures.HTML` already does) and a CSV whose third row has a `city` cell of about 4.2 million characters (`GraphTransport`'s limit is `MAX_REQUEST_BYTES = 4L * 1024 * 1024` with a 16 KiB allowance, read from `GraphTransport.java:50-52`). Rows 1 and 2 pass, row 3 fails. Assert exit 2, that `errLines()` has a line starting `recipients.csv line 4: mailer.validate rejected the mail: estimated request size ` (the message contains the computed size, so do not compare the full text) and ends with `Pre-flight failed with 1 problem(s); nothing was sent.`, that no journal file exists, and that nothing was sent (the Graph account has no WireMock stub; an HTTP call would fail the test as a connection error, and the fixtures check `journal.jsonl` does not exist).
   The CSV reader has no size limit (checked: `Campaign.java` has no length cap).
2. *Grouping.* An attachment of 3 MiB or more is shared by every row (`attachments/` is shared), so every row fails with the identical message `attachment <name> has N bytes; Graph takes attachments below 3 MiB (3145728 bytes)`: assert one line ending `(all 3 rows)`. A sparse file works: `new RandomAccessFile(file.toFile(), "rw").setLength(3L * 1024 * 1024)` (the pattern `GraphValidateTest.sparse` uses at `GraphValidateTest.java:55-60`).
   The existing recording-transport test covers "same message for all non-null rows" (finding 1).

Alternative if the owner prefers `recording` only: it cannot express "later row only" without changing `RecordingTransport` (`failValidation(InvalidMailException)` is global), and `MailAccount` cannot take a transport instance. Not recommended.

### D. Content-type tests (D-08)
- Core: add a `@ParameterizedTest` `@CsvSource({"logo.webp,image/webp", "logo.bmp,image/bmp", "logo.svg,image/svg+xml"})` near `resolvesContentTypeCaseInsensitively` (`MailBuilderTest.java:676`) that attaches `file(name)` and asserts `attachments().get(0).contentType()` (the helper `file(String)` is at line 24). Add an upper-case case (`LOGO.WEBP`) if wanted; the lookup lowercases with `Locale.ROOT`.
- Helper: `HtmlImagesTest.localTypeAgreesWithTheCore` (line 235) builds `Map.of("logo.png", ..., "LOGO.PNG", ...)` with 5 pairs and checks helper type equals core type equals expected. `Map.of` takes up to 10 pairs, so adding three gives 8, which is fine. This is the D-08 "same type" proof. The test's `image(name, 3)` writes PNG-looking bytes; the type comes from the name only.
No existing test pins `.webp`, `.bmp` or `.svg` as unsupported (grep over `morimail*/src/test`: only `HtmlImagesDataUriTest` mentions `image/svg+xml`, with `data:` sources, which use the data URI type and not the table).

## Planning Artifact Findings

| Item | Finding |
|------|---------|
| D-15 YAML error | `.planning/phases/07-html-image-helper-module/07-01-SUMMARY.md` line 11 (inside `requires:`): `    provides: the Temurin 17 mid-phase CI round-trip pattern and the pinned cid: pattern copy technique`. Reproduced: `summary-extract` fails with `... is not parseable YAML ... an unquoted ": " inside a block-sequence item`; with `cid: pattern` changed to `cid pattern` in a temporary copy (deleted afterwards), it returns the JSON. Fix: quote the value or write `cid pattern` / `cid:pattern`. Line 9 has `provides: build() content-ID ... (Mail.Builder.CID_REFERENCE, CONTENT_TYPES, Attachment.contentId)` which parses. |
| D-15 stale text | "provided scope" occurs on lines 13 (frontmatter `provides:` item), 59 (D1 `description`), 119 (the one-liner, in `**...**`), 131 (accomplishments), 148 (key-files `morimail-html/pom.xml`: `provided` morimail). Current truth: `morimail-html/pom.xml` line 18 `<!-- compile scope (D-02, amended 2026-11-23): ... -->`, and `docs/architecture.md:42` says "`morimail` has compile scope in the helper's POM ... replacing the earlier `provided` scope". Note the one-liner on line 119 is what `summary-extract` returns as `one_liner`, so it feeds other tooling; change it to compile scope (07-04) or add "(since 07-04: compile scope)" per taste. Keep the history honest: it was provided at 07-01, changed by 07-04. |
| D-15 verify | `node /home/coder/.claude-shared/dot-claude/gsd-core/bin/gsd-tools.cjs query summary-extract .planning/phases/07-html-image-helper-module/07-01-SUMMARY.md` must print JSON (`one_liner`, `key_files`, `decisions` ...). The tool rejects files outside the repo root. All 10 other SUMMARY files in phases 05-08 already parse (checked: each printed `{`). |
| D-16 | `08-02-SUMMARY.md` line 10 (frontmatter, inside `requires`/`provides` list): `provides: 08-01 README sentence under Coordinates (0.2.0 not on Central yet, 0.1.0 current), named by release.md step 8`. Line 104 (item 3 in a list): `3. The Coordinates block shows 0.2.0 before 0.2.0 is on Central (D-04). Release.md step 8 names the sentence under Coordinates to update after publishing.` Facts for the note: tag `v0.2.0` exists (commit `2c4ea80 release 0.2.0`, Stephan Wald); `README.md:21` now reads `0.2.0 is the current release on Maven Central.` Line 17 (`affects: [owner release of 0.2.0]`) and line 125 (`3. Release 0.2.0 by following docs/release.md.`) are historical next steps and may stay, or get a short "done" note; D-16 names only lines 10 and 104. Line 10 is frontmatter: no `: ` in the added text (Pitfall 6), for example "(superseded, the owner released 0.2.0 as tag v0.2.0)". |
| D-17 05 dispositions | `05-REVIEW-DISPOSITION.md`: frontmatter `findings[].disposition` is `open` for all five (WR-01, WR-02, IN-01, IN-02, IN-03), frontmatter `open: 5`, `total: 5`, and a table `| Finding | Severity | Disposition | Source |` with `-` in each Source cell. Edit by hand (no tool command was found for this): set WR-01 fixed, WR-02 fixed, IN-01 fixed, IN-02 fixed, IN-03 deferred, give each a Source cell (for example the Phase 9 plan), and update the frontmatter `open:` count. The 07 file shows the counting rule: `07-REVIEW-DISPOSITION.md` has `open: 2` with 2 deferred and 6 fixed, so `open` counts every row that is not `fixed`. After the edit the 05 file has `open: 1` (IN-03 deferred). The file's own text says `deferred` is set by hand with the reason in the Source cell and is preserved on later gate runs. |
| D-17 IN-02 check | `05-REVIEW.md` IN-02 has two parts: spec 124-125 and 177 (still say `build()` rejects a non-null `contentId`), and decision-ID comments in `Mail.java:48` and `GraphTransport.java:167`. Checked now: `docs/spec.md:125` reads "its content ID, its image type and the cid: references of the HTML body." and `:177` lists the inline checks, so the spec part is aligned. The `GraphTransport.java` comment at line 167 is `// Inline image: property names from the fileAttachment and attachmentItem pages; ...` with no decision ID. `Mail.java:46` is the **only** remaining decision-ID comment in `morimail/src/main` (grep `D-[0-9][0-9]\b`), and D-07 removes it. After D-07 the claim "decision-ID comments are gone" is true for the core jar. |
| D-14 / 07 dispositions | `07-REVIEW-DISPOSITION.md`: IN-01 and IN-02 are `deferred` with Source `owner accepted as-is in 07-UAT test 4, 2026-11-23`. Update: IN-01 to `fixed` (docs plus comment, D-13); IN-02 stays with a recorded closure, either `skipped` or `deferred`, Source cell naming "closed in Phase 9, accepted without test, no seam per Rule 0". `open:` count becomes 0 or 1 by the counting rule above (planner decides the exact value for IN-02; use `skipped` if "open" should reach 0, since `skipped` is a valid disposition per the file's legend). |

## D-14 finding (plainly)

There is **no** existing package-private path that reaches the download reader with a custom `InputStream`. [VERIFIED: `HtmlImages.java` lines 252, 501-595]
- The only package-private overload is `static Result embed(MailTemplate, Path, Path, Duration)` (line 252), which takes a timeout, not a stream.
- `download(String, URI, Duration)` is `private static`; the stream is `response.body()` from `Http.CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream())` where `Http.CLIENT` is a private static holder.
- `readAtMost(InputStream)` is `private static` (line 588).
- WireMock can send faults, resets and delays (these give `IOException` or timeouts, which map to `TransientMailException`), but nothing that makes `readNBytes` throw an `Error`.
So accept the gap as D-14 says and record it. No test, no seam.

## State of the Art

| Old | Current | Impact |
|-----|---------|--------|
| `cid:` ID ended only at `"`, `'`, ASCII white space, `)`, `>` | Also ends at `<` | `<p>cid:logo</p>` resolves. Mail that built before still builds (an accepted content ID cannot contain `<`). A mail whose HTML has `cid:xyz</p` and an inline image `xyz</p` could not exist, since `<` is rejected in a content ID |
| 4 image types in tables | 7 (png, jpg, jpeg, gif, webp, bmp, svg) in both tables | Same type on every platform |

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | The Code Examples compile as written (JDK API only) | Code Examples A, C | Executor fixes a typo; low |
| A2 | `\s` in a Java `Pattern` without `UNICODE_CHARACTER_CLASS` is ASCII-only, so U+0085 and U+2028 are not terminators | Pitfall 1 | The D-03 test would show different IDs; the executor sees it at once when the test runs. Standard documented behaviour, but not run in isolation |
| A3 | A 4.2 million character CSV cell passes through `Campaign.read`, `TemplateRenderer` and `GraphTransport.validate` fast enough for a unit test and over the 4 MiB estimate | Code Examples C | If slow, use the sparse-attachment test for grouping and a smaller-but-over-limit body (the limit is 4,194,304 minus 16,384 bytes for JSON allowance, minus the escaping overhead); the planner/executor can tune the size |
| A4 | `open` in a disposition file counts every non-`fixed` row | Planning Artifact Findings | Only a frontmatter number; derived from the 07 file (`open: 2` with 2 deferred, 6 fixed) |
| A5 | Test counts per module (366 / 71 / 164) follow the reactor order morimail, morimail-html, morimail-sample-serial | Summary | Informational |

## Open Questions (RESOLVED)

1. **Exact `skipped` or `deferred` value for 07 IN-02.** What we know: D-14 asks for "closed in Phase 9, accepted without test" in the Source cell. Unclear: which of the four values to set (the file lists `open`, `fixed`, `skipped`, `deferred`). Recommendation: `skipped` for IN-02 (accepted by decision, no change made) and `fixed` for IN-01; the planner can pick `deferred` if the owner wants it to stay visible.
   RESOLVED: `skipped`, recorded in the 09-03-PLAN.md objective (closed by decision, no change, nothing planned later).
2. **`Campaign` echo of a template-written ID is not cut at 64 or cleaned.** What we know: it is the campaign owner's own `body.html`, never recipient data (`Campaign.java:247`). Recommendation: leave as is, per D-02, and say so in the plan.
   RESOLVED: left as is, stated in 09-01-PLAN.md Task 2 step 3.

## Environment Availability

| Dependency | Required by | Available | Version | Fallback |
|------------|-------------|-----------|---------|----------|
| JDK | build | yes | Temurin 21.0.13.1 (compiles `--release 17`) | none needed. Java 17 runtime only on CI, which cannot run here: push is the owner's job |
| `./mvnw` (Maven 3.10.1) | verify | yes | wrapper, offline repo `~/.m2/repository` populated | none needed |
| Node | `gsd-tools summary-extract` | yes | present (the tool ran) | none |
| Network | `-Prelease verify` ran online, `verify -o` runs offline | n/a | both green | none |

Missing dependencies: none.

## Validation Architecture

Skipped: `.planning/config.json` has `workflow.nyquist_validation: false`. Test commands the planner can use per task (all verified this session):
- Full: `./mvnw -B -ntp -o verify` (BUILD SUCCESS, about 21 s; 366 + 71 + 164 tests).
- Single class: `./mvnw -B -ntp -q -o -pl morimail-sample-serial -am test -Dtest='PreflightTest' -Dsurefire.failIfNoSpecifiedTests=false` (exit 0). Use `-pl morimail` or `-pl morimail-html` with `-am` and the class name for the other modules.
- Release gate: `./mvnw -B -ntp -Prelease verify -Dgpg.skip -pl morimail,morimail-html -am` (exit 0, 0 WARN lines; Javadoc strictness applies, so check `{@code <}` and similar in Javadoc edits: write `{@code <}` and not a bare `<`).

## Security Domain

`security_enforcement: true`, ASVS level 1, block on high (config.json).

| ASVS category | Applies | Control |
|---------------|---------|---------|
| V5 Input validation / output encoding (log injection) | yes, D-01 | Replace control, line/paragraph separator and format code points in recipient-derived text before it enters an exception message |
| V7 Error handling and logging | yes | Messages stay fixed text; no tokens, addresses or full bodies. D-10 puts the transport's validate message into the pre-flight line as today (`GraphTransport.validate` messages contain file names, byte counts and the limit, no addresses) |
| V2, V3, V4, V6 | no | Phase touches no authentication, session, access control or cryptography |

| Threat | STRIDE | Mitigation |
|--------|--------|------------|
| Recipient value with `cid:` + NEL, U+2028 or ESC forges a log line or terminal escape through the exception message | Tampering | D-01 cleaning, D-03 test |
| `GraphTransport.validate` message echoes attachment **file names** (not recipient data, campaign files) | Information disclosure | Unchanged, accepted today |

## Project Constraints (from CLAUDE.md)

- Rule 0: build less; no new interface, flag, option, hook or dependency; one clear class over several forwarding ones. Ask before adding any dependency.
- Public repo: no secrets, tenant IDs, real addresses, internal hosts or customer names in any file, fixture, commit message or issue text. Use `example.com`, `example.org`, zero GUIDs.
- Commit as `Stephan Wald <stephan@morisoft.de>` (export the four `GIT_*` variables, see Pitfall 7). Never publish, deploy or tag. Never send real mail.
- Public types get Javadoc that states thread safety; Javadoc must build cleanly (doclint). `Mail.Builder` is documented as not thread-safe and `Mail` immutable; do not weaken that.
- Do not log tokens, secrets, assertions or full mail bodies.
- `mvn verify` must pass offline; finish and commit each unit of work with `verify` green.
- Spec wins over CLAUDE.md-level docs; where the code differs from the spec the owner has decided (D-12), update the spec.
- GSD workflow enforcement: file edits happen through a GSD command (the executor of this phase).
- Project skills: none in this repo.

## Sources

### Primary (HIGH confidence, read or run this session)
- `morimail/src/main/java/de/morisoft/morimail/Mail.java` (lines 38-66, 200-375, 470-555)
- `morimail/src/main/java/de/morisoft/morimail/Mailer.java` (validate, prepare), `MailTransport.java`, `TokenProvider.java`, `MailAccount.java` (builder API)
- `morimail/src/main/java/de/morisoft/morimail/graph/GraphTransport.java` (validate, lines 80-125), `GraphTransportProvider.java`
- `morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/SendCommand.java` (lines 40-330), `Campaign.java` (lines 30-70, 150-300)
- `morimail-html/src/main/java/de/morisoft/morimail/html/HtmlImages.java` (lines 1-180, 315-420, 470-700)
- Tests: `PreflightTest`, `SampleFixtures`, `CampaignTest` (396-455), `HtmlImagesTest` (24-52, 232-330), `MailBuilderTest` (440-535, 660-745), `RecordingTransport`, `GraphEndToEndTest` (header), `GraphValidateTest` (header)
- `docs/spec.md` (315-333, 386-402), `docs/architecture.md` (36-46), `README.md` (lines 21, 101, 169)
- `.planning/` files: 09-CONTEXT, ROADMAP Phase 9, v0.2.0-MILESTONE-AUDIT, 05-REVIEW (WR-01, WR-02, IN-01, IN-03), 05 and 07 REVIEW-DISPOSITION, 07-01-SUMMARY, 08-02-SUMMARY, config.json
- Commands run: `./mvnw -B -ntp -o verify`, `./mvnw -B -ntp -Prelease verify -Dgpg.skip -pl morimail,morimail-html -am`, the single-test pattern, `gsd-tools query summary-extract` (before and after a temporary fix in a copy that was deleted; `git status` shows no stray file)

### Secondary / Tertiary
- None. No external documentation was needed; no Microsoft endpoint is touched in this phase (the CLAUDE.md rule on reading Graph documentation applies only when coding against Graph).

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH, nothing new.
- Architecture / edit map: HIGH, every line read this session at HEAD `58ad024`.
- Pitfalls: HIGH for 2-7; MEDIUM for A3 (the 4 MiB cell test size was not run).

**Research date:** 2026-11-24
**Valid until:** until any of the listed files change (line numbers are exact for HEAD `58ad024`); re-grep before editing if another plan lands first.
