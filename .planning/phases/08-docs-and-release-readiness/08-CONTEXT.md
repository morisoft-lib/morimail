# Phase 8: Docs and Release Readiness - Context

**Gathered:** 2026-11-23
**Status:** Ready for planning

<domain>
## Phase Boundary

Documentation and release readiness for 0.2.0 (DOCS-05, DOCS-06, DOCS-07). This phase covers:
- README examples for inline images and `morimail-html`
- the Graph inline property names and the owner's live result in spec §6 and `docs/architecture.md`
- sample docs for `inline/` (`send --help` and the README)
- `docs/release.md` covering the second published jar
- the scoped release build `./mvnw -Prelease verify -Dgpg.skip -pl morimail,morimail-html -am` green without warnings at `0.2.0-SNAPSHOT`

No library, Graph or helper behaviour changes. The only code change is picocli annotation text in the sample, plus one test for it. The owner releases, deploys and tags. Claude never does.

**Live inline result (already on record, not re-asked):** the owner ran `LiveGraphTest.sendsOneMailWithAnInlineImage` on 2026-11-23 and it passed. The image showed inline, not as a separate attachment, in Outlook on the web and one desktop client, with the bare `contentId` (05-UAT.md test 1).

</domain>

<decisions>
## Implementation Decisions

### README
- **D-01:** Add a new "Inline images" section after Quick start. It is a short snippet that shows only the template `<img src="cid:logo">` and the `.inline(Path.of("logo.png"), "logo")` builder line. The Quick start stays unchanged.
- **D-02:** The inline section has 2-3 sentences on what `build()` checks:
  - every `cid:` matches an image and every image is referenced
  - content IDs are unique
  - only image types are accepted

  It also says that images count toward the existing 3 MiB attachment limit and the 4 MiB request estimate. Details stay in `docs/architecture.md`, with a link.
- **D-03:** Add its own "morimail-html" section with:
  - the `de.morisoft:morimail-html` dependency block
  - an `HtmlImages.embed(template, baseDir)` snippet for local images, with the result passed to `.template(result.template(), data)` and each image passed through `.attach(...)`
  - one line showing the work-directory overload `embed(template, baseDir, workDir)` as the opt-in for `http(s)` and `data:` sources
  - one sentence on `toDataUris` (the storable form)
- **D-04:** The Coordinates block shows `morimail` 0.2.0 (and the helper section shows `morimail-html` 0.2.0), with one sentence: inline images and `morimail-html` arrive in 0.2.0, and 0.1.0 is the current release. This avoids a README edit after the deploy. release.md step 8 still covers updating that sentence after publishing.

### docs/spec.md
- **D-05:** Edit in place so the spec describes the code:
  - §5 (the `Attachment` record comment, "in the MVP, build() rejects a non-null contentId") and §5.1 (the `build()` checks list, "no `contentId` set") are rewritten to describe the §10.3 checks.
  - The `docs/architecture.md` deviation row "§5 and §5.1 (contentId in build())" is removed, because it only existed while the spec was stale.
- **D-06:** Add new bullets in §6.2 Attachments:
  - inline images are file attachments with `"isInline": true` and the bare `contentId` (no angle brackets), and no `contentLocation`
  - regular attachments carry no `isInline` key
  - inline images count toward the same limits
  - live status: confirmed 2026-11-23
  - a pointer to `docs/architecture.md` for the Microsoft sources
- **D-07:** Retitle §10.3 and §10.4 to say they are implemented in 0.2.0, and keep their text. In §12, fill the rows "HTML parser for `morimail-html`" (outcome: none, a JDK regex) and "Failed download" (outcome: transient for network error, timeout, 5xx and 429; invalid otherwise; 3 MiB cap, 10 s connect, 30 s budget, no https→http redirect) with a pointer to `docs/architecture.md`.

### docs/architecture.md
- **D-08:** Replace "Live status: not yet confirmed..." in the inline Microsoft-docs bullet (line 53) with: "Confirmed live on 2026-11-23: the image showed inline, not as a separate attachment, in Outlook on the web and one desktop client (`LiveGraphTest.sendsOneMailWithAnInlineImage`)." Use the same wording in spec §6.2.

### Sample docs
- **D-09:** Add a picocli `footer` on `send` that lists the campaign directory files: `subject.txt`, `body.html`, `body.txt`, `recipients.csv`, `attachments/`, and optional `inline/` (the file name without extension is the content ID, referenced as `cid:NAME`). The `--dry-run` option description gains: it copies each inline image once into `inline/` under `--out` and rewrites `cid:NAME` to the relative path `inline/NAME` in the dry-run output only. (Amended 2026-11-23 by the owner during plan check: the earlier wording "next to each mail" did not match the code, which copies once.) These are annotation texts only, with no new option.
- **D-10:** Add a new README section "Serial-mail sample". The existing try-it block moves into it. It gains the campaign directory layout with `inline/` and its rules, and one sentence that dry-run shows the logo through relative paths while a real send keeps `cid:`. It points to `send --help` for the options. The `saveToSentItems` sentence stays.
- **D-11:** Add one small test that renders the `send` usage text and asserts it contains `inline/` and `cid:`.

### docs/release.md
- **D-12:** Make the steps version-neutral with shell variables (`VERSION=0.2.0`, `NEXT=0.3.0-SNAPSHOT`):
  - sed covers all four POMs (parent, `morimail`, `morimail-html`, `morimail-sample-serial`), and `git diff` expects four changed lines
  - the commit message, the `git log --grep` and the tag commands use `$VERSION`
  - "Before the first release" stays as a one-time section
- **D-13:** Change both deploy commands (the skipUpload check and the real deploy) to `-pl morimail,morimail-html -am`, matching the CI release line. The sample (`maven.deploy.skip`) and the test-jar (`attach=false`) stay out of the bundle.
- **D-14:** Claude re-runs the `-Prelease clean deploy -DskipUpload=true -pl morimail,morimail-html -am` rehearsal locally, as in v0.1.0:
  - use a throwaway GPG key in a temporary `GNUPGHOME`
  - set the release version only temporarily and never commit it (revert or use a scratch copy)
  - upload nothing, never use `-o`

  The verified `unzip -Z1` entry list goes into release.md. Expected: 9 base files, each with `.asc` and four checksums, so 54 entries:
  - `morimail-parent` pom
  - `morimail` pom, jar, sources and javadoc
  - `morimail-html` pom, jar, sources and javadoc

  Clean up `~/.m2/repository/de/morisoft` and the temporary key afterwards. Nothing is published, deployed or tagged.
- **D-15:** release.md stays generic: no 0.2.0-specific doc pre-check list.

### Claude's Discretion
- Exact prose, snippet variable names and section order inside the new README sections, within Rule 0 and the repo-wide example rules (`example.com`/`example.org`, zero GUIDs).
- Whether the rehearsal uses a scratch copy of the repo or a temporary version change that is reverted. Either way it is never committed.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Requirements and roadmap
- `.planning/ROADMAP.md` — Phase 8 goal and its five success criteria; Phase Gates (offline verify, scoped release build, Temurin 17 CI round trip, identity, no secrets)
- `.planning/REQUIREMENTS.md` — DOCS-05, DOCS-06, DOCS-07

### Spec (to be edited)
- `docs/spec.md` §5 (`Attachment` comment near line 123), §5.1 (build checks near line 177), §6.2 Attachments, §10.3, §10.4, §12 open decisions, §13.2 published artifacts (morimail-html from 0.2.0, same version)

### Docs (to be edited)
- `docs/architecture.md` — the inline Microsoft-docs bullet (live status), the deviation rows for §5/§5.1 (to be removed) and §10.4/§12 (morimail-html decisions, the source for spec §12 outcomes)
- `docs/release.md` — steps 4, 5, 7 and 8 (version, deploy, bundle list, tag, bump)
- `README.md` — Coordinates, Quick start, sample try-it block

### Live result
- `.planning/phases/05-inline-images-in-core-and-graph/05-UAT.md` — test 1, owner live inline pass 2026-11-23

### Prior phase decisions
- `.planning/phases/07-html-image-helper-module/07-CONTEXT.md` — helper API and the scoped release line
- `.planning/phases/06-inline-images-in-the-serial-mail-sample/` — the `inline/` rules (content ID from the file name, dot-files skipped, dry-run rewrite)

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `morimail-html/src/main/java/de/morisoft/morimail/html/HtmlImages.java` — `embed(template, baseDir)`, `embed(template, baseDir, workDir)`, `toDataUris(template, baseDir)`, `toDataUris(template, baseDir, workDir)`; the README snippet must match these signatures
- `morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/SendCommand.java` — the `@Command(name = "send")` and `@Option` annotations, where the footer (D-09) and the `--dry-run` text go
- `morimail-sample-serial/example-campaign/` — the real layout (`subject.txt`, `body.html`, `body.txt`, `recipients.csv`, `attachments/`, `inline/`, `account.properties`) to list in the help footer and the README

### Established Patterns
- CI (`.github/workflows/`) already runs `./mvnw -B -ntp -Prelease clean verify -Dgpg.skip -pl morimail,morimail-html -am`. D-13 copies this `-pl` set into release.md.
- `morimail/pom.xml` release profile: the test-jar has `attach=false`. `morimail-sample-serial/pom.xml`: `maven.deploy.skip=true`.
- v0.1.0 release.md was rehearsed with a throwaway key on 2026-11-22. Follow the same approach for D-14.
- Javadoc and doclint are untouched unless public types change. They don't in this phase, but the scoped release build must still be green without warnings.

### Integration Points
- Sample tests in `morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/` (for example `PacingAndOptionsTest.java`) are where the usage-text assertion (D-11) fits.

</code_context>

<specifics>
## Specific Ideas

- Live wording (D-08): "Confirmed live on 2026-11-23: the image showed inline, not as a separate attachment, in Outlook on the web and one desktop client (`LiveGraphTest.sendsOneMailWithAnInlineImage`)."
- Bundle: 54 entries expected. The rehearsal output is the authority, not this count.

</specifics>

<deferred>
## Deferred Ideas

None. The discussion stayed within phase scope.

</deferred>

---

*Phase: 08-docs-and-release-readiness*
*Context gathered: 2026-11-23*
