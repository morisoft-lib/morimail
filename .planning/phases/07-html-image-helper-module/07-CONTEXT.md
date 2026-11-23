# Phase 7: HTML Image Helper Module - Context

**Gathered:** 2026-11-23
**Status:** Ready for planning

<domain>
## Phase Boundary

A new reactor module `morimail-html` (artifact `de.morisoft:morimail-html`, package `de.morisoft.morimail.html`) with one final, stateless class `HtmlImages`. It works on a `MailTemplate` **before** rendering, once per template:

- `embed` rewrites `<img src>` values to `cid:` references and returns the matching inline `Attachment`s (HTML-01..06). Local sources resolve under a caller-given base directory. `http(s)` sources and `data:` sources are converted only when the caller passes a work directory.
- `toDataUris` is folded in on the owner's request of 2026-11-23 (HTML-07, spec §10.4 "Storable form"). It turns image sources into `data:image/...;base64,` URIs, so the HTML alone carries its images and can be stored as one string. Later, `embed(stored, baseDir, workDir)` turns that stored HTML back into the sendable form.

```
author time:   template + image files ──toDataUris──► stored HTML (images inside) ──► consumer's store
send time:     store ──► stored HTML ──embed(.., workDir)──► cid: template + images in workDir ──► Mail builder ──► Mailer
```

The `morimail` library (POM, jar, public API) is unchanged. The serial-mail sample does not use the helper in this phase. README and spec §6 docs are Phase 8.

</domain>

<decisions>
## Implementation Decisions

### Carried forward (not re-asked)
- Phase 5 D-04..D-06: the `cid:` reference grammar is `(?i)(?<![\p{L}\p{N}])cid:([^"'\s)>]*)`, and an empty ID is skipped. D-09: content-ID rules (not blank, no whitespace, CR/LF, `"` `'` `(` `)` `<` `>`). D-10: an image means the resolved content type starts with `image/` (case-insensitive).
- Spec §12 default stays: no HTML parser, only a JDK regular expression over `<img ... src>`. No new dependency.
- Messages name the `src` as written and never contain recipient data. Remote URLs are logged at DEBUG only (spec §11). Never log or echo a base64 payload.
- v0.1.0 D-20 (not this file's D-20): release builds are scoped with `-pl`. CI's release line must add `morimail-html` (see the research note under Module wiring).

### Module wiring and dependency direction (owner: "keep the dependency loose")
- **D-01:** The dependency goes one way only: `morimail-html` → `morimail`. `morimail` has no reference to `morimail-html` in its POM, code, `META-INF/services` or tests. Its jar content and runtime dependency tree (Jackson only) are unchanged. No `ServiceLoader` entry and no new extension point (spec §10.4). — **Reversibility:** one-way — both POMs are published to Maven Central in 0.2.0
- **D-02:** `morimail-html` has **no runtime dependency of its own** beyond `morimail`. It uses the JDK `HttpClient` for downloads and `java.util.Base64` for `data:`. It declares `morimail` with **`provided` scope**, so its published POM forces neither a `morimail` version nor Jackson on a consumer. A consumer of the helper always depends on `morimail` directly anyway, to build the mail. Test-scope dependencies (JUnit, AssertJ, WireMock, the `morimail` test-jar for `recording`) are as in the sample. — **Reversibility:** costly — changing the scope later changes what consumers get transitively
  - **Amended 2026-11-23 (owner decision in UAT, gap G-07-2):** `morimail-html` declares `morimail` with compile scope (the Maven default, no scope element) instead of `provided`. Maven's nearest-wins rule still lets a consumer's own `morimail` dependency win. So `provided` gained nothing for a consumer who declares `morimail`, and it left the helper's POM without a link to a core version with inline-image support: `morimail` 0.1.0 rejects every image the helper returns in `build()`. Nothing is published yet, so the change costs no consumer anything. The rest of D-02 stands: the helper has no runtime dependency of its own beyond `morimail`, and Jackson reaches it only through `morimail`.
- **D-03:** A verification step proves D-01/D-02. For example, `dependency:tree` of `morimail` shows no `morimail-html`, `morimail-html`'s runtime tree shows only `morimail` (provided), and `morimail`'s jar file list is unchanged.
  - **Amended 2026-11-23 (gap G-07-2):** the proof now reads as follows. `dependency:tree` of `morimail` shows no `morimail-html`. The only non-test direct dependency of `morimail-html` is `morimail` (compile). Its runtime classpath (`dependency:list -DincludeScope=runtime`) is exactly `morimail` plus `jackson-databind`, `jackson-core` and `jackson-annotations` through `morimail`. The `morimail` jar is unchanged.
- **Research note:** v0.1.0 learned that a full-reactor release build cannot resolve the unattached test-jar (`morimail/pom.xml` `attach=false`, parent POM line ~182). `morimail-html`'s tests need `recording` from that test-jar (HTML-06). The researcher/planner must prove that `./mvnw -Prelease clean verify -Dgpg.skip -pl morimail,morimail-html -am` works, or find the simplest way to make it work, before writing the plan around it. Do not publish the test-jar.

### Public API
- **D-04:** API (names fixed):
  ```java
  public final class HtmlImages {
    public record Result(MailTemplate template, List<Attachment> images) {}
    public static Result embed(MailTemplate t, Path baseDir) throws InvalidMailException;
    public static Result embed(MailTemplate t, Path baseDir, Path workDir)
        throws InvalidMailException, TransientMailException;
    public static MailTemplate toDataUris(MailTemplate t, Path baseDir) throws InvalidMailException;
    public static MailTemplate toDataUris(MailTemplate t, Path baseDir, Path workDir)
        throws InvalidMailException, TransientMailException;
  }
  ```
  Downloads (and `data:` decoding) happen only through the overloads that take a work directory. There is no nullable parameter. The work directory is the spec's "download directory": the caller owns it and cleans it up. `null` arguments → `NullPointerException` (`Objects.requireNonNull`). Javadoc states that the class is stateless and safe for concurrent use. The exact `toDataUris` overload set is the planner's call if a simpler shape covers HTML-07, but its name stays. — **Reversibility:** one-way — public API on Maven Central in 0.2.0
- **D-05:** The consumer passes `result.template()` to `.template(...)` and each image through `.attach(...)`. The Phase 5 checks in `build()` then apply as usual.
- **D-06:** A template with `html == null`, or HTML with nothing to rewrite, is not an error. `embed` returns the same template and `List.of()`; `toDataUris` returns the same template.
- **D-07:** The base dir is not checked up front. A missing or unusable base dir surfaces when the first local `src` is resolved, as `InvalidMailException` naming that `src`. A template with only `cid:`, remote or unknown sources works with any base dir.

### Which `src` values count
- **D-08:** The regex matches `src` on `<img>` elements in any case (`<IMG`, `SRC=`), with the value in double quotes, single quotes or unquoted. The rewrite keeps the original quoting. `srcset` and every other attribute are untouched.
- **D-09:** Classification of a `src` value, after decoding `&amp;` → `&` (the only character reference decoded):
  - contains `$$` → unchanged (spec §10.4)
  - `cid:` → unchanged
  - `data:` → unchanged by `embed(t, baseDir)` and by `toDataUris`. Converted by `embed(t, baseDir, workDir)` (D-23).
  - `http:` / `https:` → unchanged unless a work dir is given (D-14)
  - `file:` or no scheme → local (D-10)
  - any other scheme → unchanged. A scheme is two or more characters by URI syntax, so a Windows drive path like `C:\img\logo.png` counts as an unknown scheme and stays unchanged.
- **D-10:** A local `src` is parsed as a URI reference. It is percent-decoded (`my%20logo.png` → `my logo.png`), and `?query` and `#fragment` are dropped. A value that is not a valid URI (`my logo.png`) is used as written. `file:` goes through `Path.of(URI)`. Relative values resolve against the base dir. Absolute values (`/img/logo.png`, `file:///...`) are taken as file system paths.
- **D-11:** Inside-the-base-dir check: normalize, then compare the `toRealPath()` of the file and of the base dir. The file must lie inside the base dir, so a symlink that escapes it fails. Outside, missing or unreadable → `InvalidMailException` naming the `src` as written.

### Local content type
- **D-12:** The helper resolves the type the way the core does. It uses a copy of the core's 4-entry table (png, jpg, jpeg, gif), then `Files.probeContentType`, and fails if the result does not start with `image/`. It sets the resolved type on the `Attachment`. A test pins the copy: for sample files, the helper's resolved type equals what core `build()` resolves (same pattern as Phase 6's `PLACEHOLDER` pin). Do not widen the core API (D-01).

### Remote downloads (work dir given)
- **D-13:** Failure category, split by cause:
  - network error, timeout, HTTP 5xx or 429 → `TransientMailException` (a retry can help)
  - HTTP 4xx (other than 429), a missing or non-`image/` `Content-Type`, or a body over the cap → `InvalidMailException` (the template is wrong)

  Messages name the `src` as written and, for HTTP failures, the status only.
- **D-14:** Fixed constants, no options: connect timeout 10 s, request timeout 30 s, size cap 3 MiB per image (the Graph per-file limit). Stop reading as soon as the cap is passed. `HttpClient.Redirect.NORMAL` (follows redirects, never from https to http). One shared static `HttpClient` is fine, since it is thread-safe.
- **D-15:** The content type comes from the response `Content-Type` header only, with parameters stripped. It must start with `image/`, and it becomes the `Attachment`'s content type. There is no fallback to the URL's extension.
- **D-16:** A downloaded file is saved in the work dir under a unique name (for example `Files.createTempFile(workDir, ...)`), so two URLs with the same last segment never collide.

### Content IDs and file names
- **D-17:** The ID base is the file name without its last extension (local: the file's name; remote: the URL path's last segment, percent-decoded; `data:`: none, see D-19). The base is reduced to `[A-Za-z0-9._-]`, with **each** other character replaced by one `-` (no collapsing or trimming): `my logo.png` → `my-logo`, `Logo ü.png` → `Logo--`. If nothing is left (`.png`), the base is `image`.
- **D-18:** On a collision, the first image in document order keeps the bare base, and later ones get `-2`, `-3`, ..., incrementing until the ID is free. IDs that the template already uses as `cid:` references (Phase 5 grammar, scanned before rewriting) count as taken, so a hand-written `cid:logo` and `<img src="logo.png">` give `logo-2` for the file.
- **D-19:** Same source → one image and one ID. "Same" means the identical `src` string after `&amp;` decoding. For local files it also means the same real path, so `logo.png` and `./logo.png` collapse to one image. For `data:` it means identical `data:` strings. A `data:` image's ID base is `image`.
- **D-20 (Attachment filename):** The recipient sees the source's last segment: the local file's own name, or the URL's last path segment, decoded. When that segment is empty, or for a `data:` image, the name is `<contentId>.<ext>`, with the extension taken from the content type (`image/png` → `png`; an unknown subtype → the subtype as written, reduced as in D-17). The temp-file name in the work dir is never shown.

### Storable form (HTML-07, folded in 2026-11-23)
- **D-21:** The stored form is plain HTML with `data:<type>;base64,<payload>` image sources. It is a standard format with no new container: subject and text stay separate strings in the consumer's store, as today. A JSON pack format was considered and rejected (new format to keep stable, would need Jackson in `morimail-html`).
- **D-22:** `toDataUris` applies the same classification (D-09), path rules (D-10/D-11), type rules (D-12/D-15) and download rules (D-13/D-14) as `embed`. It replaces each convertible `src` with `data:<resolved type>;base64,<bytes>` and leaves everything else unchanged. Subject and text are untouched. It produces no `Attachment`s, and `cid:` sources stay as they are. Any 3 MiB cap applies per image to the raw bytes.
- **D-23:** `embed(t, baseDir, workDir)` converts `data:` sources: only `data:image/...;base64,...` counts. It decodes the bytes into a unique file in the work dir and creates an inline `Attachment` (ID per D-17..D-19, filename per D-20). A `data:` source whose type is not `image/`, which is not base64, whose payload is malformed or which decodes to more than 3 MiB → `InvalidMailException`. That message never echoes the payload: it names the source by position (for example "data: image source #2") and the reason. `embed(t, baseDir)` without a work dir leaves `data:` unchanged, as spec §10.4 originally said.
- **D-24:** The round trip is the acceptance test for HTML-07. `toDataUris` → (store as string) → `embed(.., workDir)` → render for two data records → send to `recording`. Both mails carry the inline images and pass `build()`, and the image bytes equal the originals.

### Claude's Discretion
- The exact `<img ... src>` regex, the order of the steps, and private helper methods inside `HtmlImages`. Prefer one class plus the nested `Result` record (Rule 0).
- Whether `toDataUris` gets the 2-arg/3-arg overload pair or a simpler shape, if it still covers HTML-07 (D-04).
- Exact message wording within the rules above.
- Test layout (for example `HtmlImagesTest`, `HtmlImagesDownloadTest` with `@WireMockTest`, `HtmlImagesDataUriTest`, `EmbedThroughRecordingTest`). Tiny test images are generated in tests, with no metadata and nothing checked in.
- Javadoc wording, including the `package-info.java`.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Spec (binding; spec wins)
- `docs/spec.md` §10.4: the HTML image helper, including the "Storable form" bullet added 2026-11-23
- `docs/spec.md` §10.3: inline images, content-ID and `cid:` rules that the helper's output must pass
- `docs/spec.md` §11: working rules (Rule 0, logging, no secrets, Javadoc thread safety)
- `docs/spec.md` §12: open decisions (HTML parser: none; failed-download category: decided here, D-13)
- `CLAUDE.md`: Rule 0 and the repository rules

### Planning
- `.planning/REQUIREMENTS.md`: HTML-01..HTML-07
- `.planning/ROADMAP.md` Phase 7: success criteria 1–7 and the phase gates
- `.planning/phases/05-inline-images-in-core-and-graph/05-CONTEXT.md`: D-04..D-14 (cid grammar, content-ID rules, image type rule)
- `.planning/phases/06-inline-images-in-the-serial-mail-sample/06-CONTEXT.md`: precedent for copying a core pattern and pinning it by test
- `docs/architecture.md`: package rules. Phase 8 documents the helper.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `de.morisoft.morimail.MailTemplate`: record `(subject, html, text)`. The helper returns a new instance with only `html` changed.
- `de.morisoft.morimail.Attachment`: record `(filename, contentType, file, contentId)`. It holds a `Path`, not bytes, which is why decoded or downloaded images go into the work dir.
- `InvalidMailException` / `TransientMailException`: public constructors `(String)` and `(String, Throwable)`.
- `Mail.java` ~line 51 `CONTENT_TYPES` and ~line 535 `contentTypeOf`: the table and probe order that D-12 copies. The cid pattern is in `Mail.java` as well (Phase 5).
- The `RecordingTransport` from the `morimail` test-jar (id `recording`) is used for HTML-06/HTML-07 end-to-end tests, consumed like `morimail-sample-serial/pom.xml` does (`<type>test-jar</type><scope>test</scope>`).

### Established Patterns
- The sample module's POM shows how a sibling module depends on `morimail` and its test-jar. `morimail-html` declares `morimail` the same way, with compile scope (D-02 as amended 2026-11-23).
- Phase 6 pinned a copied core pattern with a byte-equal test. D-12 uses the same idea for the content-type table.
- Graph transport timeouts and `HttpClient` usage: align the constants and style with D-14.

### Integration Points
- Parent `pom.xml`: `<module>morimail-html</module>`, plus dependencyManagement and the release-profile wiring (sources, javadoc, gpg, central).
- `.github/workflows/*.yml`: the release line becomes `-pl morimail,morimail-html -am` (see the research note).
- `morimail` itself: **no change** (D-01).

</code_context>

<specifics>
## Specific Ideas

- The owner wants a template that can be stored with its images as one large string and turned into the sendable form later. The data-URI HTML is that string. The `cid:` template plus files in the work dir is temporary and is built once per template per send run.
- The owner was explicit: `morimail` must never inherit `morimail-html`'s dependency graph (D-01..D-03).

</specifics>

<deferred>
## Deferred Ideas

- Serial-mail sample using `HtmlImages` (for example an `images/` directory instead of `inline/`): new capability, not in v0.2.0 unless the owner asks.
- A JSON pack format with subject, text and images in one value: rejected for now (D-21). Revisit if one value really has to hold subject and text as well.
- Rewriting `srcset`, CSS `url(...)` or `background=`: not asked by spec §10.4.

</deferred>

---

*Phase: 07-html-image-helper-module*
*Context gathered: 2026-11-23*
