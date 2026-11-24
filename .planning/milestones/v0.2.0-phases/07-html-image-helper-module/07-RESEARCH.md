# Phase 7: HTML Image Helper Module - Research

**Researched:** 2026-11-23
**Domain:** Java 17, JDK-only HTML `<img src>` rewriting, JDK `HttpClient` downloads, Maven multi-module release wiring
**Confidence:** HIGH (everything below the stack level was exercised in a scratch copy of the repo or a JDK 21 experiment; JDK 17 runtime was not testable here, CI on Temurin 17 is the proof)

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

Carried forward (not re-asked):
- Phase 5 D-04..D-06: the `cid:` reference grammar is `(?i)(?<![\p{L}\p{N}])cid:([^"'\s)>]*)`, and an empty ID is skipped. D-09: content-ID rules (not blank, no whitespace, CR/LF, `"` `'` `(` `)` `<` `>`). D-10: an image means the resolved content type starts with `image/` (case-insensitive).
- Spec §12 default stays: no HTML parser, only a JDK regular expression over `<img ... src>`. No new dependency.
- Messages name the `src` as written and never contain recipient data. Remote URLs are logged at DEBUG only (spec §11). Never log or echo a base64 payload.
- v0.1.0 D-20 (not this file's D-20): release builds are scoped with `-pl`. CI's release line must add `morimail-html` (see the research note under Module wiring).

Module wiring and dependency direction (owner: "keep the dependency loose"):
- **D-01:** The dependency goes one way only: `morimail-html` -> `morimail`. `morimail` has no reference to `morimail-html` in its POM, code, `META-INF/services` or tests. Its jar content and runtime dependency tree (Jackson only) are unchanged. No `ServiceLoader` entry and no new extension point (spec §10.4). Reversibility: one-way, both POMs are published to Maven Central in 0.2.0.
- **D-02:** `morimail-html` has **no runtime dependency of its own** beyond `morimail`. It uses the JDK `HttpClient` for downloads and `java.util.Base64` for `data:`. It declares `morimail` with **`provided` scope**, so its published POM forces neither a `morimail` version nor Jackson on a consumer. A consumer of the helper always depends on `morimail` directly anyway, to build the mail. Test-scope dependencies (JUnit, AssertJ, WireMock, the `morimail` test-jar for `recording`) are as in the sample. Reversibility: costly.
- **D-03:** A verification step proves D-01/D-02. For example, `dependency:tree` of `morimail` shows no `morimail-html`, `morimail-html`'s runtime tree shows only `morimail` (provided), and `morimail`'s jar file list is unchanged.
- **Research note:** v0.1.0 learned that a full-reactor release build cannot resolve the unattached test-jar (`morimail/pom.xml` `attach=false`, parent POM line ~182). `morimail-html`'s tests need `recording` from that test-jar (HTML-06). The researcher/planner must prove that `./mvnw -Prelease clean verify -Dgpg.skip -pl morimail,morimail-html -am` works, or find the simplest way to make it work, before writing the plan around it. Do not publish the test-jar.

Public API:
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
  Downloads (and `data:` decoding) happen only through the overloads that take a work directory. There is no nullable parameter. The work directory is the spec's "download directory": the caller owns it and cleans it up. `null` arguments -> `NullPointerException` (`Objects.requireNonNull`). Javadoc states that the class is stateless and safe for concurrent use. The exact `toDataUris` overload set is the planner's call if a simpler shape covers HTML-07, but its name stays. Reversibility: one-way.
- **D-05:** The consumer passes `result.template()` to `.template(...)` and each image through `.attach(...)`. The Phase 5 checks in `build()` then apply as usual.
- **D-06:** A template with `html == null`, or HTML with nothing to rewrite, is not an error. `embed` returns the same template and `List.of()`; `toDataUris` returns the same template.
- **D-07:** The base dir is not checked up front. A missing or unusable base dir surfaces when the first local `src` is resolved, as `InvalidMailException` naming that `src`. A template with only `cid:`, remote or unknown sources works with any base dir.

Which `src` values count:
- **D-08:** The regex matches `src` on `<img>` elements in any case (`<IMG`, `SRC=`), with the value in double quotes, single quotes or unquoted. The rewrite keeps the original quoting. `srcset` and every other attribute are untouched.
- **D-09:** Classification of a `src` value, after decoding `&amp;` -> `&` (the only character reference decoded):
  - contains `$$` -> unchanged (spec §10.4)
  - `cid:` -> unchanged
  - `data:` -> unchanged by `embed(t, baseDir)` and by `toDataUris`. Converted by `embed(t, baseDir, workDir)` (D-23).
  - `http:` / `https:` -> unchanged unless a work dir is given (D-14)
  - `file:` or no scheme -> local (D-10)
  - any other scheme -> unchanged. A scheme is two or more characters by URI syntax, so a Windows drive path like `C:\img\logo.png` counts as an unknown scheme and stays unchanged.
- **D-10:** A local `src` is parsed as a URI reference. It is percent-decoded (`my%20logo.png` -> `my logo.png`), and `?query` and `#fragment` are dropped. A value that is not a valid URI (`my logo.png`) is used as written. `file:` goes through `Path.of(URI)`. Relative values resolve against the base dir. Absolute values (`/img/logo.png`, `file:///...`) are taken as file system paths.
- **D-11:** Inside-the-base-dir check: normalize, then compare the `toRealPath()` of the file and of the base dir. The file must lie inside the base dir, so a symlink that escapes it fails. Outside, missing or unreadable -> `InvalidMailException` naming the `src` as written.

Local content type:
- **D-12:** The helper resolves the type the way the core does. It uses a copy of the core's 4-entry table (png, jpg, jpeg, gif), then `Files.probeContentType`, and fails if the result does not start with `image/`. It sets the resolved type on the `Attachment`. A test pins the copy: for sample files, the helper's resolved type equals what core `build()` resolves (same pattern as Phase 6's `PLACEHOLDER` pin). Do not widen the core API (D-01).

Remote downloads (work dir given):
- **D-13:** Failure category, split by cause: network error, timeout, HTTP 5xx or 429 -> `TransientMailException` (a retry can help); HTTP 4xx (other than 429), a missing or non-`image/` `Content-Type`, or a body over the cap -> `InvalidMailException` (the template is wrong). Messages name the `src` as written and, for HTTP failures, the status only.
- **D-14:** Fixed constants, no options: connect timeout 10 s, request timeout 30 s, size cap 3 MiB per image (the Graph per-file limit). Stop reading as soon as the cap is passed. `HttpClient.Redirect.NORMAL` (follows redirects, never from https to http). One shared static `HttpClient` is fine, since it is thread-safe.
- **D-15:** The content type comes from the response `Content-Type` header only, with parameters stripped. It must start with `image/`, and it becomes the `Attachment`'s content type. There is no fallback to the URL's extension.
- **D-16:** A downloaded file is saved in the work dir under a unique name (for example `Files.createTempFile(workDir, ...)`), so two URLs with the same last segment never collide.

Content IDs and file names:
- **D-17:** The ID base is the file name without its last extension (local: the file's name; remote: the URL path's last segment, percent-decoded; `data:`: none, see D-19). The base is reduced to `[A-Za-z0-9._-]`, with **each** other character replaced by one `-` (no collapsing or trimming): `my logo.png` -> `my-logo`, `Logo ü.png` -> `Logo--`. If nothing is left (`.png`), the base is `image`.
- **D-18:** On a collision, the first image in document order keeps the bare base, and later ones get `-2`, `-3`, ..., incrementing until the ID is free. IDs that the template already uses as `cid:` references (Phase 5 grammar, scanned before rewriting) count as taken, so a hand-written `cid:logo` and `<img src="logo.png">` give `logo-2` for the file.
- **D-19:** Same source -> one image and one ID. "Same" means the identical `src` string after `&amp;` decoding. For local files it also means the same real path, so `logo.png` and `./logo.png` collapse to one image. For `data:` it means identical `data:` strings. A `data:` image's ID base is `image`.
- **D-20 (Attachment filename):** The recipient sees the source's last segment: the local file's own name, or the URL's last path segment, decoded. When that segment is empty, or for a `data:` image, the name is `<contentId>.<ext>`, with the extension taken from the content type (`image/png` -> `png`; an unknown subtype -> the subtype as written, reduced as in D-17). The temp-file name in the work dir is never shown.

Storable form (HTML-07, folded in 2026-11-23):
- **D-21:** The stored form is plain HTML with `data:<type>;base64,<payload>` image sources. It is a standard format with no new container: subject and text stay separate strings in the consumer's store, as today. A JSON pack format was considered and rejected.
- **D-22:** `toDataUris` applies the same classification (D-09), path rules (D-10/D-11), type rules (D-12/D-15) and download rules (D-13/D-14) as `embed`. It replaces each convertible `src` with `data:<resolved type>;base64,<bytes>` and leaves everything else unchanged. Subject and text are untouched. It produces no `Attachment`s, and `cid:` sources stay as they are. Any 3 MiB cap applies per image to the raw bytes.
- **D-23:** `embed(t, baseDir, workDir)` converts `data:` sources: only `data:image/...;base64,...` counts. It decodes the bytes into a unique file in the work dir and creates an inline `Attachment` (ID per D-17..D-19, filename per D-20). A `data:` source whose type is not `image/`, which is not base64, whose payload is malformed or which decodes to more than 3 MiB -> `InvalidMailException`. That message never echoes the payload: it names the source by position (for example "data: image source #2") and the reason. `embed(t, baseDir)` without a work dir leaves `data:` unchanged, as spec §10.4 originally said.
- **D-24:** The round trip is the acceptance test for HTML-07. `toDataUris` -> (store as string) -> `embed(.., workDir)` -> render for two data records -> send to `recording`. Both mails carry the inline images and pass `build()`, and the image bytes equal the originals.

### Claude's Discretion
- The exact `<img ... src>` regex, the order of the steps, and private helper methods inside `HtmlImages`. Prefer one class plus the nested `Result` record (Rule 0).
- Whether `toDataUris` gets the 2-arg/3-arg overload pair or a simpler shape, if it still covers HTML-07 (D-04).
- Exact message wording within the rules above.
- Test layout (for example `HtmlImagesTest`, `HtmlImagesDownloadTest` with `@WireMockTest`, `HtmlImagesDataUriTest`, `EmbedThroughRecordingTest`). Tiny test images are generated in tests, with no metadata and nothing checked in.
- Javadoc wording, including the `package-info.java`.

### Deferred Ideas (OUT OF SCOPE)
- Serial-mail sample using `HtmlImages` (for example an `images/` directory instead of `inline/`): new capability, not in v0.2.0 unless the owner asks.
- A JSON pack format with subject, text and images in one value: rejected for now (D-21).
- Rewriting `srcset`, CSS `url(...)` or `background=`: not asked by spec §10.4.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| HTML-01 | New module `morimail-html`, depends on `morimail`, no other runtime dependency, in the release profile, `morimail` jar unchanged | "Module wiring" (POM skeleton, release-build proof, `provided` tree verified); jar SHA check |
| HTML-02 | `embed` converts `<img src>` to `cid:` plus inline `Attachment`s; `cid:`, `data:`, unknown schemes, `$$` src, subject, text unchanged | Regex prototype, classification order, offset-based rewrite loop |
| HTML-03 | Local sources resolve only inside base dir; outside/missing/unreadable/non-image -> `InvalidMailException` naming the `src` | URI handling findings, `toRealPath` containment, content-type rule |
| HTML-04 | IDs from file name, rule-conformant, numeric suffix, same source -> one image | ID derivation, taken-set from the core's cid grammar, dedupe keys |
| HTML-05 | http(s) opt-in download with timeout, size cap, image content-type check, WireMock offline | Download pattern with a real body deadline (verified experiment), WireMock test notes |
| HTML-06 | Converted template rendered for several records and sent to `recording` passes `build()` | Test-jar release-build solution, `recording` usage |
| HTML-07 | `toDataUris` / `embed` with work dir for `data:` | `data:` parsing rules, round-trip test shape, named-loss consequence |
</phase_requirements>

## Summary

The phase is a small, self-contained JDK-only class (`HtmlImages`) plus a new Maven module. Nothing needs a new dependency, so there is no package-legitimacy exposure: every library in the new POM (JUnit, AssertJ, WireMock, the `morimail` jar and test-jar) is already pinned in the parent POM and used by the sample module. The risky parts are not the helper logic but three build and runtime traps found in this session: (1) the release build `-pl morimail,morimail-html -am` fails as soon as `morimail-html` declares the unattached `morimail` test-jar (reproduced, exact error below), (2) the JDK `HttpRequest.timeout` does not bound the response body, so a stalling server would hang a download forever (reproduced), and (3) a naive HTML-tag regex either overflows the Java regex stack or goes quadratic on pathological input (prototype tested, a safe pattern is given).

The release-build problem has a tested fix: in `morimail-html`'s own `release` profile, unbind `default-testResources`, `default-testCompile` and `default-test` (`<phase>none</phase>`). Maven then never resolves the test scope, the build succeeds and produces the main, sources and Javadoc jars (Javadoc with `failOnWarnings`), and the plain `./mvnw verify` still runs every `morimail-html` test against the attached test-jar. The cost is that the scoped release build no longer re-runs `morimail-html` tests, which CI's preceding `./mvnw verify` already ran. The fallback, if the owner dislikes POM profile tricks, is a ~25-line test-only `TransportProvider` inside `morimail-html` (no test-jar dependency at all).

**Primary recommendation:** One final class `HtmlImages` using a single attribute-token regex over `<img>` tags, a rewrite loop that splices only the `src` value span (never `appendReplacement`), in-memory byte[] fetching (3 MiB cap) for remote and `data:` sources written to unique files in the work dir, a body-read deadline around the download, `provided` scope for `morimail`, and the unbound-test-phases profile for the release build.

## Architectural Responsibility Map

This is a library, not a multi-tier app. Tiers here are build-time and send-time responsibilities.

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Rewrite `<img src>` to `cid:` and produce `Attachment`s | `morimail-html` (author/template time, once per template) | none | Spec §10.4: before rendering, so recipient data never reaches it |
| Check `cid:` references vs inline images, types, unique IDs | `morimail` core `Mail.Builder.build()` | none | Phase 5, unchanged; the helper's output must pass it |
| Fetch bytes (local file, HTTP, `data:`) | `morimail-html` | JDK (`java.nio.file`, `java.net.http`, `java.util.Base64`) | No dependency allowed beyond the JDK |
| Own the work directory lifecycle | The caller (consumer) | none | D-04: caller owns and cleans the directory |
| Size limits at send time | Graph transport `validate` | helper cap (3 MiB per remote/`data:` image) | Helper cap is an early, local guard; Graph still enforces total and request estimate |
| Release packaging of `morimail-html` | Parent POM `release` profile | `morimail-html/pom.xml` release profile | Inherited sources/Javadoc/gpg/central wiring; module profile only fixes test resolution |

## Standard Stack

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| JDK `java.util.regex`, `java.nio.file`, `java.net.http.HttpClient`, `java.util.Base64`, `System.Logger` | JDK 17 API (`--release 17`) | The whole helper | CONTEXT D-02/spec §12: no HTML parser, no dependency |
| `de.morisoft:morimail` | `${project.version}` (0.2.0-SNAPSHOT), scope `provided` | `MailTemplate`, `Attachment`, `InvalidMailException`, `TransientMailException` | D-02 |

### Supporting (all `test` scope, already managed in the parent POM)

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `org.junit.jupiter:junit-jupiter` | 6.1.3 (junit-bom) | Tests | All tests [VERIFIED: /home/coder/morimail/pom.xml `junit.version`] |
| `org.assertj:assertj-core` | 3.27.7 | Assertions | All tests |
| `org.wiremock:wiremock` | 3.13.2 | Fake image server for download tests | `@WireMockTest` download/redirect/cap/timeout tests |
| `de.morisoft:morimail` `type=test-jar` | `${project.version}` | `recording` transport (HTML-06, D-24) | End-to-end tests, declared `<type>test-jar</type><scope>test</scope>` as in the sample |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Test-phase unbinding in the release profile | A local test `TransportProvider` registered under a `META-INF/services` file in `morimail-html` | No POM trick, release build keeps running html tests, but duplicates ~25 lines of fixture and diverges from the literal "`recording` from the test-jar" in CONTEXT. Use if the owner prefers it. |
| Hand-rolled regex | jsoup | Forbidden without owner approval (spec §12). Do not add. |

**Installation:** nothing to install. New module POM only (skeleton in "Module wiring").

**Version verification:** no new package is introduced. The test dependency versions above were read from the parent POM this session and a `dependency:tree` of the scratch `morimail-html` module resolved `junit-jupiter 6.1.3`, `assertj-core 3.27.7`, `wiremock 3.13.2` offline [VERIFIED: scratch build, `./mvnw -o dependency:tree -pl morimail-html`].

## Package Legitimacy Audit

No external package is added by this phase. All test dependencies are already declared by `morimail` and `morimail-sample-serial` and managed in the parent POM; the `morimail` dependency is this repository's own module. `gsd-tools package-legitimacy check` was therefore not needed.

| Package | Registry | Age | Downloads | Source Repo | Verdict | Disposition |
|---------|----------|-----|-----------|-------------|---------|-------------|
| (none new) | - | - | - | - | - | - |

**Packages removed due to [SLOP] verdict:** none
**Packages flagged as suspicious [SUS]:** none

## Architecture Patterns

### System Architecture Diagram

```
 TEMPLATE (MailTemplate: subject, html, text)            baseDir        workDir (optional, caller-owned)
        |                                                   |                |
        v                                                   |                |
 html == null / no <img>? ----yes----> return template unchanged (+ List.of())
        | no
        v
 scan html for cid: references (core grammar) --> "taken IDs" set
        |
        v
 IMG_SRC regex: for each <img ... src=VALUE> (value span only)
        |
        v
 decode &amp; -> classify VALUE
   contains "$$" ............................ unchanged
   cid: ..................................... unchanged
   other scheme (incl. C:\...) .............. unchanged
   data: ..... workDir? --no--> unchanged
                         \-yes-> parse data:image/..;base64 -> bytes (<= 3 MiB) --+
   http(s): ... workDir? --no--> unchanged                                        |
                         \-yes-> HttpClient GET (redirects NORMAL, 10s/30s,       |
                                  body read with deadline, cap 3 MiB,             |
                                  status + image/ Content-Type check) -> bytes ---+
   file: / no scheme -> URI parse, strip ?#, percent-decode, resolve under baseDir |
                        toRealPath containment, readable, image type -> Path ------+
        |                                                                          |
        v                                                                          v
 dedupe key (decoded src | real path | data string) -> existing ID, or new ID (D-17/D-18)
        |
        +--- embed:  local -> Attachment(file in place); remote/data -> bytes written to unique
        |            file in workDir -> Attachment(filename, type, file, contentId); VALUE := "cid:<id>"
        |
        +--- toDataUris: bytes (local read, remote download) -> VALUE := "data:<type>;base64,<payload>"
        v
 splice into new HTML -> new MailTemplate(subject, newHtml, text)
        |
        v
 Result(template, images)  --->  consumer: Mail.using(..).template(result.template(), data)
                                          .attach(image) for each image  --> build() (Phase 5 checks) --> Mailer
```

### Recommended Project Structure

```
morimail-html/
├── pom.xml                                   # parent morimail-parent, provided morimail, test deps, release profile
└── src/
    ├── main/java/de/morisoft/morimail/html/
    │   ├── HtmlImages.java                   # the one class (+ nested record Result)
    │   └── package-info.java                 # optional; the core has none, doclint passes without it
    └── test/java/de/morisoft/morimail/html/
        ├── HtmlImagesTest.java               # local files, classification, IDs, dedupe, errors
        ├── HtmlImagesDownloadTest.java       # @WireMockTest: status mapping, redirect, cap, content type, stall
        ├── HtmlImagesDataUriTest.java        # data: decode, malformed, toDataUris, round trip bytes
        ├── EmbedThroughRecordingTest.java    # HTML-06 and D-24 via Mailer + recording
        └── ModuleRulesTest.java              # imports only java.* + de.morisoft.morimail.*; morimail has no ref to morimail-html
```

Package `de.morisoft.morimail.html` does not collide with the core package `de.morisoft.morimail` (different package name, no split package) [VERIFIED: core source tree lists only `de.morisoft.morimail`, `.graph`, `.entra` plus test package `.test`]. Give the jar `Automatic-Module-Name: de.morisoft.morimail.html`, copying the `default-jar` execution of `morimail/pom.xml` (read this session, lines in the `maven-jar-plugin` block).

### Module wiring (tested in a scratch copy of the repo)

Parent POM: add `<module>morimail-html</module>` after `<module>morimail</module>`. No dependencyManagement change is needed (`morimail` jar and test-jar entries already exist). The release profile's source/Javadoc/gpg/central plugins are inherited, so the module is automatically "in the release profile".

`morimail-html/pom.xml` skeleton (this exact shape built green):

```xml
<parent> de.morisoft:morimail-parent:0.2.0-SNAPSHOT </parent>
<artifactId>morimail-html</artifactId>
<name>morimail-html</name>
<description>... one sentence ...</description>   <!-- Central needs name + description; url/license/scm/developers inherit from the parent -->
<dependencies>
  <dependency> de.morisoft:morimail  <scope>provided</scope> </dependency>
  <dependency> de.morisoft:morimail  <type>test-jar</type> <scope>test</scope> </dependency>
  <dependency> org.junit.jupiter:junit-jupiter  <scope>test</scope> </dependency>
  <dependency> org.assertj:assertj-core         <scope>test</scope> </dependency>
  <dependency> org.wiremock:wiremock            <scope>test</scope> </dependency>
</dependencies>
<!-- maven-jar-plugin default-jar: Automatic-Module-Name de.morisoft.morimail.html -->
<profiles>
  <profile>
    <id>release</id>
    <build><plugins>
      <!-- The unattached morimail test-jar cannot be resolved in a packaging reactor, so the
           scoped release build must not touch the test scope. Tests run in the plain verify. -->
      maven-resources-plugin: execution default-testResources  <phase>none</phase>
      maven-compiler-plugin:  execution default-testCompile    <phase>none</phase>
      maven-surefire-plugin:  execution default-test           <phase>none</phase>
    </plugins></build>
  </profile>
</profiles>
```

Results [VERIFIED: scratch copy at the researcher's scratchpad, Maven wrapper 3.10.1, JDK 21, `--release 17`]:

| Build | Outcome |
|-------|---------|
| `./mvnw -o -Prelease clean verify -Dgpg.skip -pl morimail,morimail-html -am` with the test-jar dependency and **no** unbinding | **FAILS**: `Could not resolve dependencies for project de.morisoft:morimail-html:jar:0.2.0-SNAPSHOT ... dependency: de.morisoft:morimail:jar:tests:0.2.0-SNAPSHOT (test) Could not find artifact de.morisoft:morimail:jar:tests:0.2.0-SNAPSHOT` |
| same, with the three executions unbound in the module's `release` profile | BUILD SUCCESS; `morimail-html-0.2.0-SNAPSHOT.jar`, `-sources.jar`, `-javadoc.jar` built; Javadoc runs with `doclint all` and `failOnWarnings` (a stub with a missing `@param` failed it, which proves it is active) |
| `./mvnw -o clean verify` (full reactor, default profile) | BUILD SUCCESS, `morimail-html` tests run against the attached test-jar, sample still green |
| `-Prelease clean deploy -DskipUpload=true -Dgpg.skip` offline | The Central `publish` goal is skipped offline (`requires online mode`); bundle contents of two modules could not be inspected here. Owner/Phase 8 concern. |

Wiring consequences to include in the plan:
- `.github/workflows/ci.yml` line `./mvnw -B -ntp -Prelease clean verify -Dgpg.skip -pl morimail -am` becomes `-pl morimail,morimail-html -am` (and its comment). Also the comment above the `release` profile in the parent POM (`-pl morimail -am`), `docs/architecture.md` §10.1 row (`-pl morimail -am`) and `docs/release.md` ("CI must be green ... -pl morimail -am") mention the old line. DOCS-06/DOCS-07 in Phase 8 own `docs/release.md`; the CI line, the parent POM comment and the architecture row belong to this phase because they change with the module.
- `dependency:tree` of the module shows `morimail:provided` with `jackson-databind:provided` below it, i.e. a consumer of `morimail-html` inherits nothing at compile or runtime from the published POM [VERIFIED: scratch `dependency:tree`]. Because `provided` puts Jackson on the helper's compile classpath, an accidental `com.fasterxml` import compiles silently. Guard it with a test (see Pitfalls, `ModuleRulesTest`).
- The `morimail` jar is reproducible: `project.build.outputTimestamp` is set in the parent, and two builds gave the identical SHA-256 (`0787...006c0`) [VERIFIED: scratch, `sha256sum` before and after `clean package`]. D-03 can therefore be checked as "SHA-256 of `morimail/target/morimail-0.2.0-SNAPSHOT.jar` equals that of a build at the phase's starting commit" plus `unzip -l`, plus `git diff --stat` showing no `morimail/` path changed.
- Known harmless oddity: the published POM of `morimail-html` lists a `test`-scope `morimail:tests` dependency that does not exist on Central. Test scope is never transitive and consumers do not resolve it.

### Pattern 1: tag regex that is safe on large and hostile input

**What:** One compiled `Pattern` that walks the attributes of an `<img>` tag token by token and captures the `src` value in one of three groups (double-quoted, single-quoted, unquoted). Possessive quantifiers prevent backtracking; attribute names exclude `<` so an unclosed tag cannot run to the end of the document for every later `<img`.
**When to use:** The only regex in the module (D-08).
**Example** (prototype tested in this session, JDK 21):

```java
// Source: researcher prototype, run against the cases listed below
private static final String ATTRIBUTE =
        "[^\\s=/>\"'<]++(?:\\s*+=\\s*+(?:\"[^\"]*+\"|'[^']*+'|[^\\s>]*+))?+";
private static final Pattern IMG_SRC = Pattern.compile(
        "<img(?=[\\s/])(?:[\\s/]++(?!src[\\s=/>])" + ATTRIBUTE + ")*+"
                + "[\\s/]++src\\s*+=\\s*+(?:\"([^\"]*+)\"|'([^']*+)'|([^\\s\"'>]++))",
        Pattern.CASE_INSENSITIVE);
```

Tested cases and results [VERIFIED: prototype run]:

| Input | Captured `src` |
|-------|----------------|
| `<img src="a.png">`, `<IMG SRC='b.png'>`, `<img src=c.png>` | `a.png`, `b.png`, `c.png` |
| `<img alt="x src='y'" src="z.png">` | `z.png` (a `src=` inside another attribute's quoted value is ignored) |
| `<img data-src="no.png" src="yes.png">`; `<img data-src="no.png">` | `yes.png`; no match |
| `<imgx src="no">` | no match |
| `<img\n  class=a\n  src = "sp.png" />`; `<img/src="slash.png">` | `sp.png`; `slash.png` |
| `<img alt="a > b" src="gt.png">` | `gt.png` |
| `<picture><source src="s.png"><img src="p.png"></picture>` | `p.png` only |
| `<img SRC="u" src="second">` | `u` (first `src` wins) |
| `<img srcset="x 2x" src="ss.png">` | `ss.png` (`srcset` untouched) |
| `<img src="">` | empty string (group 1 participates; decide behaviour, see Open Questions) |
| 8 MB `data:` URI inside the quotes | found in 60 ms, no `StackOverflowError` |
| tag with 200,000 attributes | found in 48 ms |
| 20,000 repeated unclosed `<img ` | 4 ms (19.6 s, i.e. quadratic, before `<` was excluded from attribute names) |

Group selection and splice (never `Matcher.appendReplacement`, whose replacement string treats `$` and `\` specially):

```java
Matcher m = IMG_SRC.matcher(html);
StringBuilder out = new StringBuilder(html.length());
int last = 0;
while (m.find()) {
    int g = m.start(1) >= 0 ? 1 : m.start(2) >= 0 ? 2 : 3;   // keeps the author's quoting
    String replacement = convert(m.group(g));                  // null = leave the original text
    if (replacement != null) {
        out.append(html, last, m.start(g)).append(replacement);
        last = m.end(g);
    }
}
out.append(html, last, html.length());
```

Notes: matches inside HTML comments and Outlook conditional comments (`<!--[if mso]> <img src="x.png"> <![endif]-->`) are rewritten too. That is desirable here, because the core's own `cid:` scan also sees them, so the pairing stays consistent. A failing source inside a comment still fails the whole call; say so in Javadoc.

### Pattern 2: classify a `src` by string, not by `URI` parsing

D-09 needs "is there a scheme". `new URI(...)` throws on a backslash or a space, so it cannot classify `C:\img\logo.png` or `my logo.png` [VERIFIED: experiment, `URISyntaxException Illegal character in opaque part` / `in path`]. Classify with a regex on the decoded string first, then parse only local values:

```java
private static final Pattern SCHEME = Pattern.compile("^([A-Za-z][A-Za-z0-9+.-]*):");   // see Open Question 1
// order: contains("$$") -> unchanged; SCHEME absent -> local; scheme lower-cased:
//   "cid" unchanged, "data" -> D-23/unchanged, "http"/"https" -> D-14/unchanged, "file" -> local, other -> unchanged
```

Local value handling, results of the experiment [VERIFIED: JDK 21 `java.net.URI` / `Path.of(URI)` run]:

| Value | `new URI` | Consequence for the code |
|-------|-----------|--------------------------|
| `my%20logo.png` | path `my logo.png` | percent-decode via `URI.getPath()` |
| `my logo.png`, `a\b.png` | `URISyntaxException` | use the value as written (D-10) |
| `logo.png?v=2#x` | path `logo.png` | `getPath()` drops query and fragment |
| `%2e%2e/secret.png` | path `../secret.png` | decoded first, so containment must run after decoding (it does, via `toRealPath`) |
| `file:///tmp/x.png` | `Path.of(uri)` works | ok |
| `file:///tmp/x.png?v=1` | `Path.of(uri)` throws `IllegalArgumentException: URI has a query component` | build a clean URI from `scheme` + `getPath()` before `Path.of` |
| `file:logo.png` (opaque) | `Path.of(uri)` throws `URI is not hierarchical`; `getPath()` is `null` | catch, `InvalidMailException` naming the `src` |
| `file://host/x.png` | `Path.of(uri)` throws `URI has an authority component`; `getPath()` is `/x.png` | do not drop the authority silently: keep it in the clean URI so `Path.of` rejects it |
| `Path.of("a\0b")` | `InvalidPathException` | catch (`RuntimeException` family) and map to `InvalidMailException` |

Catch `URISyntaxException | IllegalArgumentException | InvalidPathException | FileSystemNotFoundException | IOException` around local resolution and always throw `InvalidMailException` with the `src` as written.

Containment (D-11), minimal form:

```java
Path base = baseDir.toRealPath();               // IOException -> InvalidMailException naming the src (D-07)
Path file = candidate.toRealPath();             // NoSuchFileException -> InvalidMailException
if (!file.startsWith(base) || !Files.isRegularFile(file) || !Files.isReadable(file)) { fail }
```

`toRealPath()` follows symlinks, so a link that escapes the base directory fails. `Path.startsWith` compares path elements, not string prefixes, so `/base-evil` is not inside `/base`.

### Pattern 3: remote download with a body deadline

`HttpRequest.Builder.timeout` is documented as "If the response is not received within the specified timeout then an `HttpTimeoutException` is thrown" [CITED: docs.oracle.com/en/java/javase/17/docs/api/java.net.http/java/net/http/HttpRequest.Builder.html] and the page does not say it covers the body. An experiment with a server that sends headers plus 3 bytes and then stalls showed that with `BodyHandlers.ofInputStream()` **and** with `BodyHandlers.ofByteArray()` the 2 s request timeout did **not** fire; the read blocked past 40 s [VERIFIED: experiment, `timeout 40 java Stall2.java` exited 124 with no output]. The shape below bounded the same stall at 2.06 s and `close()` unblocked the reader with `IOException: closed` [VERIFIED: experiment `Stall3.java`]:

```java
// Source: researcher experiment (Stall3), adapted
HttpResponse<InputStream> response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
try (InputStream body = response.body()) {
    // 1. status mapping (D-13), 2. Content-Type check (D-15), 3. Content-Length > cap -> Invalid
    CompletableFuture<byte[]> read = CompletableFuture.supplyAsync(() -> readAtMost(body, CAP + 1));
    byte[] bytes;
    try {
        bytes = read.get(REQUEST_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
    } catch (TimeoutException e) {
        body.close();                                   // unblocks the reader thread
        throw new TransientMailException("download of " + src + " timed out");
    } catch (ExecutionException e) { /* IOException cause -> Transient */ }
    if (bytes.length > CAP) { /* Invalid: body over the cap */ }
}
```

`readAtMost` is `InputStream.readNBytes(CAP + 1)`: it stops at the cap plus one byte, so a chunked response without `Content-Length` is capped too. (Resolved boundary, Open Question 2: the plans read `readNBytes(CAP)` and refuse `bytes.length >= CAP`, the comparison Graph `validate` uses; the shape is otherwise unchanged.) An `InterruptedException` from `send` or `get` must call `Thread.currentThread().interrupt()` and throw `TransientMailException`.

Status mapping per D-13, plus the gap D-13 does not name: 2xx continues; 5xx and 429 and any `IOException` (connect refused, unknown host, `HttpTimeoutException`, reset) -> `TransientMailException`; every other status, **including 3xx that were not followed** (an https-to-http redirect, a redirect without `Location`, 304) and 1xx/204 -> `InvalidMailException` with the status only. Redirect policy: `HttpClient.Redirect.NORMAL` is documented as "Always redirect, except from HTTPS URLs to HTTP URLs." [CITED: docs.oracle.com/en/java/javase/17/docs/api/java.net.http/java/net/http/HttpClient.Redirect.html]. The Graph transport sets `.connectTimeout(CONNECT_TIMEOUT)` and `.followRedirects(HttpClient.Redirect.NEVER)` with `Duration.ofSeconds(10)` / `Duration.ofSeconds(30)` constants [VERIFIED: morimail/src/main/java/de/morisoft/morimail/graph/GraphTransport.java:44-45,71-74]; reuse the same constants and style, only the redirect policy differs (D-14).

Static client: `private static final HttpClient HTTP = ...` is fine. A JDK 17 `HttpClient` starts its selector thread when created, so a lazy holder class (3 lines) avoids a thread for consumers who only embed local images. Planner's call; either is acceptable under Rule 0.

Test seam for timing: the 30 s request timeout makes the stall test unusable. Follow the Graph precedent (`GraphTransport(..., Duration requestTimeout)`, package-private) and give the download method a package-private variant that takes the request timeout (and optionally the cap), so one WireMock test can use 1 s. WireMock `withFixedDelay` covers "slow headers"; for a body stall use `withChunkedDribbleDelay` or `Fault`-style responses, whichever WireMock 3.13.2 offers (check at implementation time). A 3 MiB + 1 byte body is cheap for WireMock; test both with and without `Content-Length` (chunked).

### Pattern 4: IDs, names and `data:` handling

Content IDs (D-17/D-18):
- Base: file name without its last extension (`name.substring(0, name.lastIndexOf('.'))` when the dot index is greater than 0 or at least 0 per D-17's `.png` -> empty -> `image`; a name with no dot keeps the whole name). Reduce with `replaceAll("[^A-Za-z0-9._-]", "-")`. Java regex works per code point, so an emoji becomes one `-`, not two [VERIFIED: experiment output `Logo ü` + emoji gave `Logo---a-` for `"Logo ü a😀"`, i.e. ü, the space and the emoji each give one `-`]. D-17's example `Logo ü.png` -> `Logo--` matches.
- Output characters are all inside the core's accepted set (no whitespace, quotes, parentheses, angle brackets) [VERIFIED: Mail.java `contentIdProblem`, lines 492-507, read this session: `Character.isWhitespace(c) || c == '"' || c == '\'' || c == '(' || c == ')' || c == '<' || c == '>'`], so no extra validation is needed beyond a non-empty base.
- The core compares IDs exactly (case-sensitive) [CITED: docs/architecture.md, §10.3 row: "is compared exactly and is never decoded"]. Use exact comparison for the taken set too.
- Taken set: run the **core's** pattern over the original HTML before rewriting and add every non-empty group 1. Copy the pattern verbatim (the sample did the same): `Pattern.compile("(?i)(?<![\\p{L}\\p{N}])cid:([^\"'\\s)>]*)")` [VERIFIED: Mail.java:48-49]. Pin it by behaviour, the way `CampaignTest.cidPatternAgreesWithTheCore` does (run a set of HTML snippets through the helper's pattern and through `Mail.using(..).html(..).inline(..).build()`, compare). The core field is private, so do not use reflection in the test.
- Dedupe key per image: decoded `src` string (remote, `data:`); real path (local). `logo.png` and `./logo.png` collapse through the real path.

Attachment filename (D-20): the local file's own name, or the decoded last URL segment. The decoded segment can contain `\r`, `\n`, other control characters or a `/` (from `%0D%0A`, `%2F`), and core `build()` rejects a filename with a line break. Use the segment only if it is non-blank and has no control characters or path separators; otherwise fall back to `<contentId>.<ext>` (extension from the content type subtype reduced as in D-17, with the 4-type table for png/jpeg/gif).

Local type resolution (D-12). The core's order, read this session: lower-cased extension of the attachment **filename** in `CONTENT_TYPES`, then `Files.probeContentType(file)`, then `application/octet-stream` [VERIFIED: Mail.java:535-553]. The table's image entries, quoted verbatim [VERIFIED: Mail.java:53-56]: `Map.entry("png", "image/png")`, `Map.entry("jpg", "image/jpeg")`, `Map.entry("jpeg", "image/jpeg")`, `Map.entry("gif", "image/gif")`. The helper copies these four, then probes, and treats `null` or a non-`image/` result as "not an image". Because the helper sets `contentType` on the `Attachment`, the core skips its own resolution (`attachment.contentType() != null ? attachment.contentType() : contentTypeOf(...)`, Mail.java:462-463), so the pin test must build a probe attachment with `contentType == null` and compare. Keep the pin test to `png`, `jpg`, `jpeg`, `gif` (table, machine independent) plus one non-image (`.txt`, `.xyz`). `Files.probeContentType` results are machine dependent: on this JDK 21 box `.webp` gave `image/webp`, `.svg` gave `image/svg+xml`, `.bmp` gave `image/bmp` [VERIFIED: experiment], which a CI runner may not reproduce. Do not assert on those.

`data:` parsing (D-23):
- Header = text up to the first `,`; split on `;`; first part is the media type (case-insensitive `image/` prefix required), last part must be `base64` (case-insensitive); a missing comma or non-base64 form -> `InvalidMailException`.
- Strip ASCII whitespace from the payload, then use `Base64.getDecoder()` (strict). The basic decoder rejects a newline: `Illegal base64 character a` [VERIFIED: experiment]; hand-wrapped payloads are common, hence the strip. `getMimeDecoder()` would silently skip illegal characters; do not use it.
- Check `payloadLength / 4 * 3` against the cap **before** decoding, so an 80 MB string is not decoded just to be refused.
- The message names the source by position (`data: image source #2`) and the reason; never the payload.
- Position counter: count `data:` image sources in document order among those the call considers (planner's choice, pin it in a test).

Work-dir files: `Files.createTempFile(workDir, "morimail-", null)` gives a unique, owner-only file; the `Attachment` carries its own `filename` and `contentType`, so the temp name and extension never reach the recipient. A missing or unwritable work dir surfaces as `NoSuchFileException`/`IOException` on first write: map to `InvalidMailException` ("work directory is not usable"), without echoing full paths (the core's messages show file names only). If a call fails after earlier images were written, best-effort delete the files this call created (a few lines); the caller still owns the directory.

`toDataUris`: the bytes of a local file are read with a size check first (`Files.size`), remote bytes come from the same download method (bytes in memory, 3 MiB cap), so the work directory is never written by `toDataUris`; the 3-arg overload's `Path workDir` only acts as the opt-in for downloads (see Open Question 4). Build the replacement `data:<type>;base64,<payload>` with `Base64.getEncoder()` (no line breaks). `data:` sources stay unchanged in every `toDataUris` overload (D-09).

Round-trip consequence to pin in tests (D-17/D-19/D-20): the stored form carries no file names, so after `toDataUris` -> `embed(.., workDir)` the content IDs are `image`, `image-2`, ... and the attachment filenames `image.png`, `image-2.png`, not the original names. Two identical `data:` strings collapse to one image.

### Anti-Patterns to Avoid

- **`Matcher.appendReplacement` / `replaceAll` with the new text:** `$` and `\` in the replacement are special. Use offset splicing (Pattern 1).
- **Per-character alternation in the tag regex** such as `(?:[^>"']|"[^"]*"|'[^']*')*`: Java's regex recurses per iteration of a group, so a long tag overflows the stack. Keep loops per attribute and use possessive single-class loops, as in the prototype.
- **Letting `URI` decide the scheme:** it throws on backslash and space (Pattern 2).
- **`Path.of(URI)` on the raw `file:` URI:** it rejects a query component (Pattern 2).
- **Trusting `HttpRequest.timeout` for the body:** it does not bound it (Pattern 3).
- **Reading local images in `embed`:** the attachment points at the file in place; reading it would only waste memory. Only `toDataUris`, remote and `data:` sources need bytes.
- **Adding any interface, option, builder or constant-holder class:** Rule 0. One class, one nested record, private static helpers.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| HTML parsing | A tokenizer or tree | The single prototype regex (Pattern 1) | Spec §12 and D-08 forbid a parser; the regex handles quoting, case, `data-src`, `srcset`, huge values |
| URL percent-decoding | Manual `%XX` loop | `URI.getPath()` | Correct for UTF-8, leaves `+` alone |
| Base64 | Own codec | `java.util.Base64` basic encoder/decoder | Strict decode, no line breaks on encode |
| HTTP, redirects, TLS | Sockets, manual redirect loop | JDK `HttpClient` with `Redirect.NORMAL` | The https-to-http downgrade rule is built in |
| Unique temp file names | Counters or hashes | `Files.createTempFile(workDir, ...)` | Atomic, collision-free |
| Media type table | A big extension map | The 4 image entries copied from the core, then `Files.probeContentType` | Matches `build()` by D-12 and a pin test |
| Content-ID grammar | A new `cid:` scanner | The core's pattern, copied verbatim | One definition of "referenced" |

**Key insight:** the module is a thin adapter between three byte sources and one rewrite step; every hard piece (decoding, redirects, unique files, path normalization) is already in the JDK.

## Common Pitfalls

### Pitfall 1: Scoped release build cannot resolve the unattached test-jar
**What goes wrong:** `-Prelease ... -pl morimail,morimail-html -am` fails at `morimail-html` dependency resolution (exact error quoted above).
**Why it happens:** `morimail/pom.xml` sets `attach=false` in the release profile, and a packaging reactor only sees attached artifacts [CITED: docs/architecture.md §10.1 row].
**How to avoid:** the unbound-test-executions release profile in `morimail-html/pom.xml` (tested), or the local-fixture fallback. Do not set `attach` back to true (the Central plugin would stage the test-jar; its `excludeArtifacts` works by artifactId only [CITED: project STACK notes in .claude/CLAUDE.md]).
**Warning signs:** `Could not find artifact de.morisoft:morimail:jar:tests:0.2.0-SNAPSHOT` in the release step only; plain `verify` is green.

### Pitfall 2: A stalled server hangs the download
**What goes wrong:** headers arrive, the body never finishes, `send` has returned, and `readNBytes` blocks forever.
**Why it happens:** request timeout stops at the response headers (experiment in Pattern 3).
**How to avoid:** the future-with-deadline plus `close()` pattern; test it with a 1 s package-private timeout.
**Warning signs:** a CI job that hits its 15 minute timeout.

### Pitfall 3: Accidental Jackson (or other `provided`) import
**What goes wrong:** `morimail-html` compiles against `jackson-databind` because `provided` `morimail` brings it onto the compile classpath, and consumers without Jackson (they always have it through `morimail`, but the intent of D-02 is "no own dependency") would get a surprise later.
**How to avoid:** `ModuleRulesTest` in the style of the core's `ImportRulesTest` (reads source text via `System.getProperty("basedir", ".")`): every import in `morimail-html` main sources is `java.*` or `de.morisoft.morimail.*`; and `../morimail/pom.xml`, `../morimail/src/main` and `../morimail/src/test` contain no `morimail-html`. (Reading a sibling module's files from a test works the same way the core test reads its own `basedir`.)

### Pitfall 4: `Path.of(URI)` and `URI` strictness
See Pattern 2 table: query on `file:`, opaque `file:logo.png`, authority `file://host/...`, NUL in a path, backslash and space. Each must end in `InvalidMailException` (or the "use as written" rule), never in an unchecked exception.

### Pitfall 5: Single-letter "scheme" and the D-09 sentence
`C:\img\logo.png` is not a URI. With a scheme regex that needs two or more characters it has no scheme, is therefore local, and fails (missing file or outside base dir). With `[A-Za-z][A-Za-z0-9+.-]*:` (one or more) it has the scheme `C`, is unknown, and stays unchanged, which is the outcome D-09 states. The D-09 wording ("two or more characters") contradicts its own outcome. See Open Question 1.

### Pitfall 6: Content-type resolution differs between machines
`Files.probeContentType` depends on the OS MIME database (Pattern 4). Pin tests on the four table types and an unmistakably non-image file only.

### Pitfall 7: Stored form loses names
After the round trip every image has the `image`-based ID and name. Not a bug (D-17/D-19/D-20); do not write a test that expects `logo`. Document it in Phase 8.

### Pitfall 8: Everything runs on JDK 21 locally
Only JDK 21 is installed here; CI is Temurin 17. The code uses no API after 17 (`--release 17` enforces it), but HttpClient and `probeContentType` behaviour was only observed on 21. Plan an early push to CI for this phase (roadmap lesson from v0.1.0), and prefer assertions that do not depend on exact JDK message text.

### Pitfall 9: Doclint with `failOnWarnings`
The release profile builds Javadoc with `doclint all` and `failOnWarnings` for the new module. The record `Result` needs `@param template` and `@param images`; every public method needs `@param`, `@return`, `@throws`; the class Javadoc must state thread safety (also asserted in the core by `PublicTypesDocumentThreadSafetyTest`, whose pattern can be copied for the html module). The default constructor of a utility class: declare `private HtmlImages() {}` so no undocumented public constructor appears.

## Code Examples

### Accepted check on the scanning side (copy of the core grammar)
```java
// Source: morimail/src/main/java/de/morisoft/morimail/Mail.java:47-49 (read this session); keep equal, pin by test
private static final Pattern CID_REFERENCE =
        Pattern.compile("(?i)(?<![\\p{L}\\p{N}])cid:([^\"'\\s)>]*)");
```

### End to end (HTML-06 / D-24 test shape)
```java
// Source: pattern of morimail SendThroughRecordingTest (read this session), adapted
MailAccount account = MailAccount.builder("main").transport("recording")
        .defaultFrom("newsletter@example.com").build();
Mailer mailer = Mailer.of(account);
HtmlImages.Result result = HtmlImages.embed(template, imagesDir);        // or toDataUris -> store -> embed(.., workDir)
for (Map<String, ?> data : List.of(dataA, dataB)) {
    Mail.Builder builder = Mail.using("main").to("reader@example.org").template(result.template(), data);
    result.images().forEach(builder::attach);
    mailer.send(builder.build());
}
RecordingTransport transport = (RecordingTransport) account.transport();
// transport.sent() has 2 MailMessage; each .attachments() holds the inline images with contentId set
```
`Mail.Builder` signatures used: `template(MailTemplate, Map<String, ?>)` (Mail.java:267) and `attach(Attachment)` (Mail.java:321) [VERIFIED: Mail.java signature lines]. `MailMessage` is `record MailMessage(MailAddress from, List<MailAddress> to, ..., String html, String text, List<Attachment> attachments)` [VERIFIED: MailMessage.java:25-27]. `RecordingTransport.sent()` returns `List<MailMessage>`.

Test images: generate tiny byte arrays in the test (`@TempDir`), distinct content per image so the byte-equality check in D-24 is meaningful; the core and sample tests use 3-4 byte fake PNGs written to a `.png` name, which is enough because the helper looks at names and headers, not pixels.

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| JUnit 5 (spec text) | JUnit Jupiter 6.1.3 | owner decision, recorded in docs/architecture.md | Same `org.junit.jupiter` API; the new module copies the sample's dependency block |
| `BodySubscribers.limiting` | not available on `--release 17` (added in JDK 21) | n/a | Do not use it; read with `readNBytes(CAP + 1)` |

**Deprecated/outdated:** none relevant.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Treating a one-letter scheme (`C:`) as an unknown scheme (unchanged) is what the owner wants, despite D-09's "two or more characters" sentence | Pattern 2, Pitfall 5, Open Question 1 | `C:\img\x.png` would fail with `InvalidMailException` instead of being left alone |
| A2 | `toDataUris` also applies the 3 MiB cap to **local** files (D-22 says "Any 3 MiB cap applies per image to the raw bytes"), so the stored form can always be embedded again | Pattern 4 | A 5 MiB local image would convert to a huge string that `embed` later refuses |
| A3 | Exactly 3 MiB (3,145,728 bytes) is accepted and 3 MiB + 1 is refused, following D-13/D-23 "over/more than"; Graph `validate` itself refuses `size >= 3 MiB` | Pattern 3 | A 3 MiB image passes the helper and then fails Graph validation, a one-byte boundary. Not adopted: see Open Question 2 (the helper refuses 3 MiB or more) |
| A4 | Non-2xx statuses that D-13 does not name (3xx not followed, 1xx, 204, 408) map to `InvalidMailException` | Pattern 3 | 408 arguably wants a retry; any of them reclassified is a one-line change |
| A5 | `<img src="">` (empty) is classified local and fails like a missing file | Pattern 1 table | Could instead be left unchanged; either way a deliberate choice for the planner |
| A6 | In `toDataUris(t, baseDir, workDir)` the work directory is only the download opt-in and is not written | Pattern 4 | If the owner expects temp files there, downloads would go to disk first (no functional gain) |
| A7 | JDK 17 behaves like the JDK 21 used for every experiment (HttpClient body behaviour, `URI`, `Path.of(URI)`, `probeContentType`) | Pitfall 8 | Fixed by CI on Temurin 17 |
| A8 | WireMock 3.13.2 offers `withFixedDelay` and `withChunkedDribbleDelay` (or an equivalent) for slow headers and a stalled body | Pattern 3 test notes | Fall back to a `ServerSocket` stub like the experiment (about 15 lines) |

## Open Questions (RESOLVED)

Each question is resolved with the answer Plans 07-01 to 07-03 adopt. The plan IDs (A1 to A7) differ from the Assumptions Log numbering above, so each answer names its plan ID. The owner's confirmation is a separate Owner review item: the SUMMARY of each plan lists these IDs, and Plan 07-03 Task 2 asks the owner to confirm or correct them.

1. **One-letter scheme (D-09).** RESOLVED as plan A1 (Plan 07-01).
   - What we know: the stated outcome is "`C:\img\logo.png` stays unchanged"; the stated reason ("a scheme is two or more characters") would make it local and failing.
   - Recommendation: scheme regex `^[A-Za-z][A-Za-z0-9+.-]*:` (one or more characters), which yields the stated outcome, and record the choice in the plan as A1 for the owner to confirm.
   - Answer adopted: the scheme pattern needs one or more characters, so `C:\img\logo.png` has the unknown scheme `c` and stays unchanged, the outcome D-09 states.

2. **Cap on local files in `toDataUris` and the exact boundary (A2, A3).** RESOLVED as plan A2 (Plans 07-02 and 07-03), with a different boundary from the recommendation.
   - Recommendation: cap applies to every image `toDataUris` reads, boundary "more than 3 MiB fails", pin both with tests at the cap and cap + 1.
   - Answer adopted: the cap applies to every image `toDataUris` reads (local files and downloads), to every download in `embed` and to every decoded `data:` source. The boundary follows D-14 ("the Graph per-file limit") and `GraphTransport.validate`, which refuses `size >= MAX_ATTACHMENT_BYTES` (GraphTransport.java line 105, GRAPH-11): an image of 3 MiB (3,145,728 bytes) or more fails, and 3,145,727 bytes pass. Tests pin 3,145,727 (passes) and 3,145,728 (fails) in each of the three modes. This replaces the Assumptions Log claim A3 and the `CAP + 1` read in Pattern 3: the plans read at most `MAX_IMAGE_BYTES` bytes and refuse `length >= MAX_IMAGE_BYTES`. The pre-decode check for `data:` refuses a payload longer than `4 * (MAX_IMAGE_BYTES / 3)` = 4,194,304 characters, which is the base64 length of both 3 MiB - 1 byte and 3 MiB.

3. **Statuses outside D-13 (A4).** RESOLVED as plan A3 (Plan 07-02). Recommendation: Invalid, status only in the message.
   - Answer adopted: an unfollowed 3xx, 1xx, 204 and 408 give `InvalidMailException` with the status only; any 2xx except 204 continues.

4. **Shape of `toDataUris` (A6).** RESOLVED as plan A4 (Plan 07-03). Recommendation: keep the 2-arg/3-arg pair from D-04; the 3-arg overload takes `workDir` as the opt-in and documents that it is not written to. Alternatives (a boolean) conflict with "no nullable parameter / no flag" spirit.
   - Answer adopted: the 2-argument and 3-argument pair of D-04; in the 3-argument overload the work directory is only the opt-in for downloads and is never written.

5. **Release-build approach.** RESOLVED as plan A7 (Plan 07-01). Recommendation: unbound test executions in the module's release profile (tested). Needs the owner's nod only if the owner dislikes a profile that skips tests; the fallback is the local test provider (Alternatives table).
   - Answer adopted: `morimail-html`'s `release` profile sets `default-testResources`, `default-testCompile` and `default-test` to phase `none`; only the scoped line `./mvnw -B -ntp -Prelease clean verify -Dgpg.skip -pl morimail,morimail-html -am` is supported, and the plain `verify` in the same CI job runs the module's tests.

6. **Work dir cleanup on a failed call.** RESOLVED as plan A6 (Plans 07-02 and 07-03). Recommendation: best-effort delete of this call's files; skip it if the planner judges it beyond Rule 0.
   - Answer adopted: no cleanup (Rule 0, the skip this question allows). Files one call wrote before a later source failed stay in the work directory, which the caller owns and cleans up (D-04); the Javadoc of the work-directory overloads says so, and no file the call did not create is deleted, overwritten or renamed.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| JDK | build, tests | yes | Temurin 21.0.13.1 (compiles with `--release 17`) | CI runs Temurin 17 |
| Maven wrapper | build | yes | 3.10.1 in `~/.m2/wrapper`, offline `-o` builds work | none needed |
| Maven plugins and test dependencies for the new module | offline build | yes (all already cached from `morimail` and the sample) | see POM | none |
| `curl`/network | tests | not needed (WireMock on loopback) | n/a | n/a |
| Central publish (online) | releasing | not testable here (the `publish` goal skips offline) | n/a | Owner/Phase 8 |

**Missing dependencies with no fallback:** none.
**Side effect to know about:** while testing, an `install` and a `deploy -DskipUpload` run in the scratch copy wrote `0.2.0-SNAPSHOT` entries into `~/.m2/repository/de/morisoft/`; the researcher deleted them again (`morimail`, `morimail-parent`, `morimail-html` snapshot directories and their metadata lines). The `0.1.0` entries were left alone.

## Security Domain

`security_enforcement` is enabled (ASVS level 1, block on high). The Validation Architecture section is omitted because `workflow.nyquist_validation` is `false` in `.planning/config.json`.

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no | no credentials handled |
| V3 Session Management | no | n/a |
| V4 Access Control | yes (file system) | base-directory containment via `toRealPath()` + `startsWith` (D-11), symlink escape fails |
| V5 Input Validation | yes | classification before use, URI/path parse errors mapped to `InvalidMailException`, strict base64, content-type allow-list `image/`, size cap, no payload in messages |
| V6 Cryptography | no | none used |
| V12 Files and Resources | yes | unique temp files in the caller's work dir, caps and timeouts on remote content |
| V13 API / Communication | yes (outbound HTTP) | opt-in downloads only, `HttpClient` with TLS defaults, no https-to-http redirect |

### Known Threat Patterns for this stack

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Path traversal in a template `src` (`../../etc/passwd`, `%2e%2e/`, absolute path, symlink) | Information disclosure | Decode, resolve, `toRealPath`, `startsWith(realBase)`, regular readable file, image type check |
| Local file disclosure through a non-image file renamed `.png` | Information disclosure | Known limit: the check is by name/type table, not magic bytes (same as the core). State it in Javadoc; the base-dir rule is the real boundary |
| SSRF / internal host reachable through a template URL | Information disclosure | Opt-in only (work dir overload), template authored by the trusted owner, recipient data never reaches the helper (spec §10.4); redirects follow `NORMAL` and could land on an internal host: state in Javadoc that the template author is trusted |
| Oversized or endless remote body, slow-loris | Denial of service | 3 MiB cap with `readNBytes(CAP + 1)`, `Content-Length` pre-check, body deadline (Pattern 3), connect timeout |
| Zip-bomb-like base64 `data:` string | Denial of service | Check `payload length` against the cap before decoding |
| Regex denial of service on a hostile template | Denial of service | Possessive quantifiers, `<` excluded from attribute names (prototype timings above) |
| Leaking payload, recipient data or full URLs in messages and logs | Information disclosure | Messages name the `src` as written (or the `data:` position), HTTP failures give the status only, remote URLs at DEBUG only via `System.Logger`, never log bytes |
| Header or filename injection through a decoded URL segment | Tampering | Fall back to `<contentId>.<ext>` when the decoded name has control characters or separators |

## Project Constraints (from CLAUDE.md)

Directives from `./CLAUDE.md` and `.claude/CLAUDE.md` that bind this phase:
- **Rule 0:** simplest code, no new interface/SPI/factory/strategy/abstract class, no options or flags the spec does not ask for, no frameworks or reflection, one clear class over many forwarding ones, ask before adding any dependency. Reflection is not used in this research's recommendations (pins are behavioural).
- Library dependencies: Jackson only in the core jar; `morimail-html` adds none. `System.Logger` for logging; JDK `HttpClient`; no `slf4j`.
- Public types get Javadoc stating thread safety (`HtmlImages` stateless, safe for concurrent use; `Result` immutable). Javadoc must build cleanly (release profile uses `failOnWarnings`).
- Never log tokens, secrets, assertions or full mail bodies; recipients at DEBUG only; remote URLs at DEBUG only (CONTEXT).
- Public repository: no secrets, tenant IDs, real addresses, internal hosts or customer names in code, fixtures or messages; use `example.com` / `example.org`.
- Commit as `Stephan Wald <stephan@morisoft.de>`; never publish, deploy or tag; never send real mail.
- `mvn verify` must pass offline; finish and commit each unit of work with `mvn verify` green.
- Before coding against any Graph or token endpoint read the Microsoft docs: **not triggered** by this phase (no Graph or token call; downloads are plain HTTP GETs of image URLs).
- Maven 3.10.1 through the wrapper; never Maven 4; pin plugin versions in `pluginManagement` (all already pinned, nothing new is needed).
- GSD workflow: edits go through the GSD execution commands.

## Sources

### Primary (HIGH confidence)
- Repository files read this session: `pom.xml`, `morimail/pom.xml`, `morimail-sample-serial/pom.xml`, `.github/workflows/ci.yml`, `docs/spec.md` §10.3/10.4/12, `docs/architecture.md`, `morimail/src/main/java/de/morisoft/morimail/Mail.java` (lines 47-56, 425-570), `Attachment.java`, `MailTemplate.java`, `InvalidMailException.java`, `TransientMailException.java`, `MailMessage.java`, `graph/GraphTransport.java` (lines 44-52, 60-80, 98-115), `test/.../RecordingTransport.java` and its provider, `SendThroughRecordingTest.java`, `ImportRulesTest.java`, `PublicTypesDocumentThreadSafetyTest.java`, sample `CampaignTest.cidPatternAgreesWithTheCore`.
- Scratch-copy builds (Maven wrapper 3.10.1, JDK 21, offline): failing release build, passing release build with unbound test executions, full default `verify`, `dependency:tree`, reproducible jar SHA.
- JDK experiments: regex prototype and stress cases, `URI`/`Path.of(URI)` behaviour table, body-stall experiments (`Stall`, `Stall2`, `Stall3`), `Base64` strictness, regex code-point replacement.
- docs.oracle.com `HttpClient.Redirect` and `HttpRequest.Builder` (JDK 17 Javadoc), fetched this session.

### Secondary (MEDIUM confidence)
- `.claude/CLAUDE.md` stack notes (versions, Central plugin `excludeArtifacts` by artifactId only, WireMock/JUnit 6 compatibility), written by earlier research phases of this project.

### Tertiary (LOW confidence)
- WireMock 3.13.2 dribble/fixed-delay API names (A8): not exercised this session.

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH, nothing new; versions read from the parent POM and a resolved dependency tree.
- Architecture: HIGH, build wiring and release behaviour reproduced; regex and URI behaviour measured.
- Pitfalls: HIGH for the three reproduced traps (release resolution, body timeout, regex), MEDIUM for JDK 17 parity.

**Research date:** 2026-11-23
**Valid until:** 2026-12-23 (stable; re-check only if the parent POM plugin versions or the core `Mail.Builder` cid/type code change before planning)
