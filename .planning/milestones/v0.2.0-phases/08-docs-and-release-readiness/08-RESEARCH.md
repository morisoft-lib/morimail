# Phase 8: Docs and Release Readiness - Research

**Researched:** 2026-11-23
**Domain:** Markdown docs (README, spec, architecture, release guide), one picocli annotation text change plus one test, Maven release-bundle rehearsal
**Confidence:** HIGH (every fact below was read from the repo or produced by a build run in this session; nothing depends on outside sources)

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**README**
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

**docs/spec.md**
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

**docs/architecture.md**
- **D-08:** Replace "Live status: not yet confirmed..." in the inline Microsoft-docs bullet (line 53) with: "Confirmed live on 2026-11-23: the image showed inline, not as a separate attachment, in Outlook on the web and one desktop client (`LiveGraphTest.sendsOneMailWithAnInlineImage`)." Use the same wording in spec §6.2.

**Sample docs**
- **D-09:** Add a picocli `footer` on `send` that lists the campaign directory files: `subject.txt`, `body.html`, `body.txt`, `recipients.csv`, `attachments/`, and optional `inline/` (the file name without extension is the content ID, referenced as `cid:NAME`). The `--dry-run` option description gains: it writes inline images next to each mail and rewrites `cid:` to relative paths in the dry-run output only. These are annotation texts only, with no new option.
- **D-10:** Add a new README section "Serial-mail sample". The existing try-it block moves into it. It gains the campaign directory layout with `inline/` and its rules, and one sentence that dry-run shows the logo through relative paths while a real send keeps `cid:`. It points to `send --help` for the options. The `saveToSentItems` sentence stays.
- **D-11:** Add one small test that renders the `send` usage text and asserts it contains `inline/` and `cid:`.

**docs/release.md**
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

### Deferred Ideas (OUT OF SCOPE)
None. The discussion stayed within phase scope.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| DOCS-05 | README inline-image example with `.inline(...)` and `cid:`; `docs/architecture.md` and spec §6 record the Graph inline property names and the live status; sample docs describe `inline/` | Edit map in "Exact Edit Map" (README, spec §6.2, architecture line 53, SendCommand footer); property names verified in `GraphTransport.java:168-170`; live result verified in 05-UAT.md test 1 |
| DOCS-06 | `./mvnw verify` passes offline and the scoped release build passes without warnings at `0.2.0-SNAPSHOT`; ready for the owner to release 0.2.0 from `docs/release.md` | "Build Results": both builds run this session, exit 0, zero `WARN` lines in the release builds. Rehearsal bundle verified: 54 entries |
| DOCS-07 | README `HtmlImages` example (local, remote opt-in) and `morimail-html` coordinates; `docs/release.md` covers the second published jar | Verified API signatures in "HtmlImages API"; snippets compile; release.md edit map |
</phase_requirements>

## Summary

This is a documentation phase with one tiny code change (picocli annotation text in `SendCommand`) and one test. Nothing in the library, the Graph transport or `morimail-html` changes. The two success-criteria builds are already green on the current tree, so the build work is to keep them green after the edits and to record the rehearsal. I ran the offline verify, the scoped release build (with and without `clean`, offline and online) and the full `-Prelease clean deploy -DskipUpload=true -pl morimail,morimail-html -am` rehearsal in a scratch copy at `0.2.0` with a throwaway key. All passed, and the bundle holds exactly the 54 entries that D-14 expects, with all 9 signatures verifying.

Three things in the CONTEXT decisions need the planner's attention because the code differs from the wording: (1) `send --help` is not a real help option, it works only because required options are missing, goes to stderr and exits 2 (see Pitfall 1 and Open Question 1); (2) dry-run writes the images once into `out/inline/` and rewrites `src` to `inline/NAME`, it does not write them "next to each mail" (D-09 wording); (3) `docs/release.md` has several 0.1.0-only statements beyond steps 4, 5, 7, 8 that must also change (line 8, the "Tests run" line, the 30-entry list).

**Primary recommendation:** Do the work as three small plans: (A) README + spec + architecture text edits, (B) SendCommand annotation text + one usage test, (C) release.md rewrite from the rehearsal evidence below, then re-run the two gate builds. Compile every README Java snippet against the built classes before committing (the snippets in "Code Examples" already compile).

## Architectural Responsibility Map

Not applicable in the usual sense: no new capability is built. The documented capabilities already have owners:

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Inline image content-ID and `cid:` checks | Core `Mail.Builder.build()` | - | Documented, not changed |
| Graph inline wire format (`isInline`, `contentId`) | `graph` transport | - | `GraphTransport.java:168-170` |
| `src` to `cid:` rewriting | `morimail-html` `HtmlImages` | - | Outside the core (spec §10.3 last bullet) |
| `inline/` campaign directory, dry-run rewrite | Sample `Campaign` / `SendCommand` | - | Documented through picocli text and README |

## Standard Stack

No new libraries. Nothing is installed in this phase.

### Core (existing, versions from the repo POMs)
| Item | Version | Purpose |
|------|---------|---------|
| Maven wrapper | 3.10.1 (pinned in repo) | All builds use `./mvnw` |
| picocli | 4.7.7 (`pom.xml` property `picocli.version`) | `@Command(footer=...)`, `@Option(description=...)` text only |
| maven-gpg-plugin | 3.2.8 | Signing in the rehearsal |
| central-publishing-maven-plugin | 0.12.0 | Builds `central-bundle.zip`; `-DskipUpload=true` stops before upload |
| JUnit Jupiter 6.1.3 + AssertJ 3.27.7 | per BOM | The one new usage-text test |

**Installation:** none.

## Package Legitimacy Audit

No external package is added or changed in this phase. Packages removed due to SLOP verdict: none. Packages flagged SUS: none. The slopcheck/legitimacy gate does not apply.

## Architecture Patterns

### Edit flow (no runtime data flow)
```
CONTEXT decisions D-01..D-15
   |-- README.md ---------------- Coordinates, Inline images, morimail-html, Serial-mail sample
   |-- docs/spec.md ------------- §5, §5.1, §6.2, §10.3/§10.4 titles, §12 rows (+ optional §8)
   |-- docs/architecture.md ----- line 53 live status, delete row 40, add regex note to row 43
   |-- SendCommand.java --------- footer + --dry-run text  --> SendCommandHelpTest (usage text)
   |-- docs/release.md ---------- version-neutral steps, -pl morimail,morimail-html -am, 54-entry list
   v
Gate: ./mvnw -B -ntp -o verify  and  ./mvnw -B -ntp -Prelease verify -Dgpg.skip -pl morimail,morimail-html -am
```

### Recommended plan split
1. **Docs text** (README, spec, architecture): pure Markdown, no build impact.
2. **Sample help text**: edit `SendCommand` annotations, add `SendCommandHelpTest` in `morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/`.
3. **release.md** from the rehearsal evidence in this document; re-run the rehearsal only if the planner wants a fresh proof after the other edits (the edits do not touch anything that goes into the bundle except the sample, which is not bundled).
4. **Gate builds** last.

### Anti-Patterns to Avoid
- **New option for help:** do not add `mixinStandardHelpOptions` silently; D-09 says "no new option" (see Open Question 1).
- **Committing a release version:** the rehearsal changes versions in a scratch copy only. The working tree stays at `0.2.0-SNAPSHOT`.
- **README promising a result of 0.2.0 that is not published:** the Coordinates sentence from D-04 must say 0.1.0 is the current release.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Checking README snippets compile | A doc-test framework | `javac --release 17 -cp morimail/target/classes:morimail-html/target/classes:<jackson jars>` once in the scratchpad | Rule 0; one manual compile is the proof (done below) |
| Version bump in the release guide | `versions-maven-plugin` | `sed` over four POMs | Plugin is not pinned in the repo; verified 4 changed lines |
| Bundle checks | A script | `unzip -Z1` plus `gpg --verify` per `.asc` | Same as the v0.1.0 rehearsal |

## Build Results (run this session, JDK Temurin 21.0.13.1 with `--release 17`)

| Command | Result |
|---------|--------|
| `./mvnw -B -ntp -o verify` | `BUILD SUCCESS`, exit 0, 24 s. Tests: morimail 366, morimail-html 71, sample 163, 0 failures. One `[WARNING] Overwriting .../morimail-sample-serial/target/lib/morimail-0.2.0-SNAPSHOT.jar` appeared only because `target/` was not cleaned; the dependency plugin copies the jar again. It is not part of the release gate. [VERIFIED: run log] |
| `./mvnw -B -ntp -Prelease clean verify -Dgpg.skip -pl morimail,morimail-html -am` | `BUILD SUCCESS`, exit 0, 14.7 s. `grep -c WARN` of the log: 0. [VERIFIED: run log] |
| `./mvnw -B -ntp -o -Prelease verify -Dgpg.skip -pl morimail,morimail-html -am` (the exact line from success criterion 5, offline, no clean) | exit 0, `grep -c WARN`: 0. Produces `morimail-0.2.0-SNAPSHOT{,-sources,-javadoc,-tests}.jar` and `morimail-html-0.2.0-SNAPSHOT{,-sources,-javadoc}.jar`. [VERIFIED: run log] |
| Rehearsal: scratch copy at `0.2.0`, `./mvnw -B -ntp -Prelease clean deploy -DskipUpload=true -pl morimail,morimail-html -am`, throwaway key (RSA 2048, no passphrase, `throwaway@example.com`, 1 day) | `BUILD SUCCESS`, exit 0, 15 s. Bundle: `target/central-publishing/central-bundle.zip`. [VERIFIED: run log] |

Rehearsal specifics the planner needs for release.md:

- `unzip -Z1` count: **54**. Each of the 9 base files comes with `.asc`, `.md5`, `.sha1`, `.sha256` and `.sha512`, so 6 entries per base file, 9 x 6 = 54. [VERIFIED: `wc -l` = 54; `.asc` count = 9]
- The nine base files, verbatim from the listing:
  - `morimail-parent-0.2.0.pom`
  - `morimail-0.2.0.pom`, `morimail-0.2.0.jar`, `morimail-0.2.0-sources.jar`, `morimail-0.2.0-javadoc.jar`
  - `morimail-html-0.2.0.pom`, `morimail-html-0.2.0.jar`, `morimail-html-0.2.0-sources.jar`, `morimail-html-0.2.0-javadoc.jar`
- Paths inside the zip: `de/morisoft/morimail/0.2.0/...`, `de/morisoft/morimail-html/0.2.0/...`, `de/morisoft/morimail-parent/0.2.0/...`. `grep -c "tests.jar\|sample"` over the entries: 0. [VERIFIED]
- All 9 `.asc` verified `Good signature` with `gpg --verify` against the throwaway key. [VERIFIED]
- The sed `s/0\.2\.0-SNAPSHOT/0.2.0/` over `pom.xml morimail/pom.xml morimail-html/pom.xml morimail-sample-serial/pom.xml` changed exactly four lines (afterwards `grep -rn 0\.2\.0 --include=pom.xml` found only the four `<version>` lines at line 10 of each file). [VERIFIED]
- During `deploy`, `morimail-html`'s tests do not run: its `release` profile sets `default-testResources`, `default-testCompile`, `default-test` to phase `none` (`morimail-html/pom.xml`). Only `morimail`'s 366 tests ran. release.md's sentence "Tests run." must say that the helper's tests run in step 4's plain `./mvnw -B -ntp verify`, not in the deploy. [VERIFIED: rehearsal log shows surefire only under `@ morimail`]
- Three `[WARNING] Hard link not supported, falling back to copy ... Invalid cross-device link` lines appeared in the rehearsal log (one per module). Cause: my scratch copy lives on a different filesystem from `~/.m2`. This is an environment effect of the scratch location, not a repo problem, and the scoped release `verify` run in the real tree has no warnings. Do not put them in release.md. [VERIFIED: log; cause is an inference from the message text]
- gpg detail: the `GNUPGHOME` must be a **short path**. My first attempt in the long scratchpad path failed with `gpg: agent_genkey failed: No agent running` (Unix socket path length limit). I used `/home/coder/.gnupg-rh` and removed it plus the agent afterwards. [VERIFIED: failing output seen]
- Cleanup done by me: removed `~/.m2/repository/de/morisoft/morimail/0.2.0`, `.../morimail-html`, `.../morimail-parent/0.2.0`, the throwaway `GNUPGHOME` and killed its agent. Working tree is clean apart from the two pre-existing untracked files (`07-PATTERNS.md`, `.planning/state.json`).
- **Left over, not mine:** `~/.m2/repository/de/morisoft/{morimail,morimail-parent}/0.1.0` with `.asc` files (from the v0.1.0 rehearsal). D-14 says to clean `~/.m2/repository/de/morisoft`; I removed only what this session created. The planner decides whether a final cleanup task removes the whole directory. `maven-metadata-local.xml` files remain in those two directories.

If the planner wants the rehearsal re-run after the doc edits (it is cheap, 15 s plus key creation), the recipe is: copy the tree without `.git`, `.planning`, `target` and `*.log` into the scratchpad; `sed` the four POMs; `GNUPGHOME=<short path> gpg --batch --gen-key` with `%no-protection`; run the deploy command with `-B -ntp` and **without** `-o`; `unzip -Z1`; `gpg --verify` each `.asc`; clean up.

## Current State of Each Target File (read this session)

### README.md (106 lines)
- Lines 12-21: `## Coordinates` with one `morimail` `0.1.0` block and "0.1.0 is the first release, published on Maven Central."
- Lines 23-72: Quick start (unchanged by D-01). It already imports `de.morisoft.morimail.*` and uses `.attach(Path.of("info.pdf"))`.
- Lines 74-81: the sample try-it block (a text paragraph, then a `bash` block with `./mvnw -B -ntp -DskipTests package -pl morimail-sample-serial -am` and `java -jar morimail-sample-serial/target/morimail-sample-serial-*.jar send ... --dry-run --out out`), then "This writes the rendered mails to `out/` and needs no secrets..." including the `saveToSentItems` sentence. D-10 moves this into a new "Serial-mail sample" section.
- Line 87: "Attachments must stay under 3 MiB (3 x 1024 x 1024 bytes), per file and in total." Inline images count toward it; D-02 says so in the new section.
- `out/` and `target/` are in `.gitignore` (lines 27-28), so the try-it block leaves no tracked files. [VERIFIED: `.gitignore`]
- The try-it command was run for real here against the `0.2.0-SNAPSHOT` jar: exit 0, stdout:
  ```
  attachments: info.pdf (application/pdf, 602 bytes), sample.png (image/png, 60959 bytes)
  inline: logo.png (image/png, 166 bytes, cid:logo)
  [1/5] anna@example.org rendered
  ...
  dry run: 5 mails written to <out>
  ```
  `out/` holds `NN-<email>.html`, `.subject.txt`, `.txt` and one `out/inline/logo.png`; the rendered HTML contains `<img src="inline/logo.png" alt="morimail logo" width="96" height="48">`. [VERIFIED: ran it]
- The `-*.jar` glob in the try-it command matches several jars if `target/` holds stale versions (this checkout has `-0.1.0-SNAPSHOT.jar`, `-0.1.0.jar` and `-0.2.0-SNAPSHOT.jar`, so `java -jar a b c` would misbehave here). On a fresh clone there is one jar. Not worth changing under Rule 0; just do not paste local-checkout output into the README.

### docs/spec.md (520 lines) - exact edit sites
| Site | Line | Current text (verbatim) | Change |
|------|------|-------------------------|--------|
| §5 `Attachment` comment | 124-125 | `// contentId null = regular attachment. Non-null = inline image (milestone 6);` / `// in the MVP, build() rejects a non-null contentId.` | Say a non-null `contentId` is an inline image and `build()` applies the §10.3 checks |
| §5.1 build checks | 177 | `...attachment files exist and are readable, no `contentId` set.` | Replace "no `contentId` set" with the §10.3 checks (content ID valid, image type, unique IDs, `cid:` both ways) |
| §6.2 Attachments | 217-222 | five bullets, last: "The upload-session route is a later milestone..." | Add the D-06 bullets (inline property names, no `isInline` on regular, same limits, live status, pointer to architecture.md) |
| §10.3 heading | 385 | `### 10.3 Next after the MVP: milestone 6, inline images` | Retitle as implemented in 0.2.0, keep text |
| §10.4 heading | 395 | `### 10.4 Milestone 6, continued: HTML image helper (`morimail-html`)` | Same |
| §12 rows | 448-449 | `HTML parser for `morimail-html` (10.4) | None: a JDK regular expression over `<img src>`. A parser such as jsoup needs the owner's explicit approval` and `Failed download in `morimail-html` (10.4) | Decided in the phase that builds it, together with the size cap, the timeouts and the redirect rule` | Fill outcomes per D-07 with a pointer to architecture.md row |
| §12 intro | 439 | `Each has a default so work is not blocked. Raise them at the milestone-4 report.` | Optional: leave |

Other stale spots the decisions do not list (planner discretion, flag in Open Question 3): spec line 40 ("Inline images (CID). Scheduled as milestone 6... The `Attachment` record is prepared for them." in "Out of scope for the MVP", historically true, can stay); §8.1 campaign layout (lines 275-282) has no `inline/`; §8.3 dry-run sentence (line ~327) says nothing of `out/inline/`; §5.1 usage example has no `.inline`.

Spec §13.2 line 468 already names `morimail-html` from 0.2.0. Spec §4 layout (line 95-96) already lists `morimail-html/`. No change needed there.

### docs/architecture.md (67 lines)
- Line 53 (the "Inline images (read on 2026-11-22)" bullet) ends with: `Live status: not yet confirmed, the owner runs `LiveGraphTest.sendsOneMailWithAnInlineImage`.` Replace that sentence with the D-08 text. The rest of the bullet already names `"isInline": true`, `"contentId"` (bare, no angle brackets), no `contentLocation`, and no `isInline` on regular attachments. [VERIFIED: file]
- Line 40: the table row `| §5 and §5.1 (contentId in build()) | ... |` is the row D-05 removes. It is one table line.
- Line 43: the `§10.4 and §12 (morimail-html decisions)` row already states the download decisions (transient on network error, timeout, 5xx, 429; invalid otherwise; 3 MiB; 10 s connect; one 30 s budget; no https to http redirect; body read on a thread of its own, which matches `HtmlImages.java:550-553`). It does **not** mention "no HTML parser, a JDK regex". Because D-07 points the spec's HTML-parser row at architecture.md, add a short clause to this row (for example "No HTML parser: a JDK regular expression over `<img src>`") so the pointer has a target.
- Line 38 already mentions `LiveGraphTest` has "an inline-image test", fine.

### docs/release.md (139 lines) - everything that is 0.1.0-specific
| Line | Current | Needed |
|------|---------|--------|
| 8 | `...release build with `-Prelease verify -Dgpg.skip -pl morimail -am`.` | `-pl morimail,morimail-html -am` |
| 54-64 (step 4) | "Change the three POM versions from `0.1.0-SNAPSHOT` to `0.1.0`", `sed ... pom.xml morimail/pom.xml morimail-sample-serial/pom.xml`, "exactly three changed lines", commit "release 0.1.0" | `VERSION=0.2.0`; four POMs; four lines; commit message with `$VERSION` |
| 82 | `-pl morimail -am` (skipUpload check) | `-pl morimail,morimail-html -am` |
| 85-93 | "exactly 30 entries. These are the five base files" + five names + "bundle seen in the rehearsal on 2026-11-22" | 54 entries, nine base files (list above), rehearsal date 2026-11-23 |
| 97 | "At a SNAPSHOT version no zip is made, so run it after step 4." | keep (still true; the rehearsal needed the release version) |
| 100 | "The check installs the 0.1.0 artifacts under `~/.m2/repository/de/morisoft`." | version-neutral |
| 112 | `-pl morimail -am` (real deploy) | `-pl morimail,morimail-html -am` |
| 115 | "Tests run." | Say the `morimail` tests run; the `morimail-html` tests do not run in a release-profile deploy (its profile skips them), they ran in step 4's plain verify |
| 127-133 (step 7) | `v0.1.0`, `--grep='^release 0\.1\.0$'`, `"morimail 0.1.0"` | use `$VERSION` (dots in the grep regex as `\.`; with a variable use `"^release ${VERSION//./\\.}$"` or accept unescaped dots) |
| 135-139 (step 8) | "Set the three versions... `0.2.0-SNAPSHOT`... Update the README sentences that say 0.1.0 is not released" | `NEXT`; four POMs; name the D-04 README sentence ("inline images and `morimail-html` arrive in 0.2.0, and 0.1.0 is the current release") and the Coordinates version as the things to update |

Step 8's sed after a release is `s/$VERSION/$NEXT/` over the same four POMs; there is no other occurrence of the version in those POMs (shown by the grep above), so it changes four lines. Note the first POM in the sed list is `pom.xml` (the parent).

Because the release version in the sed pattern is `0.2.0-SNAPSHOT`, write the step as `sed -i "s/${VERSION}-SNAPSHOT/${VERSION}/" ...`. Dots in `${VERSION}` match any character in sed; this is harmless here (the four-line `git diff` check catches any surprise). [ASSUMED: harmlessness for other version strings; it is verified for 0.2.0]

### Sample `send` help (picocli 4.7.7)
Current `send` usage (exact, from the built jar) ends with the two existing footer lines (the in-flight sentence and the exit codes). Options: `--account`, `--campaign`, `--campaign-id`, `--dry-run` ("Write the rendered mails to --out and send nothing."), `--journal`, `--limit`, `--out`, `--rate`. `SendCommand` is annotated with `@Command(name = "send", description = ..., footer = {"A mail that is in flight ...", "Exit codes: ..."})` at `SendCommand.java:38-45`; `--dry-run` is at lines ~69-71.
- Footer is an array: add the campaign-directory lines as further array entries (or one entry before the existing two). Each element prints as its own wrapped paragraph.
- The file layout the footer must name matches `morimail-sample-serial/example-campaign/`: `subject.txt`, `body.html`, `body.txt`, `recipients.csv`, `attachments/`, `inline/`, plus `account.properties` (the account file, passed with `--account`, not part of the campaign directory in the spec; the example campaign keeps it inside the directory).
- Real inline behaviour (read from `SendCommand.java:275-300` and `Campaign.java`): files in `inline/` become inline images of every mail, content ID = file name without its last extension, dot-files skipped, pre-flight reports problems (a `cid:` with no image, an image with no `cid:`, duplicate base names, non-image files) and exits 2. Dry-run copies each inline image **once** into `<out>/inline/<file name>`, rewrites `cid:ID` to `inline/<file name>` (URI-encoded, `&` as `&amp;`) in the written HTML only, and prints a line `inline: logo.png (image/png, 166 bytes, cid:logo)`. A real send keeps `cid:`. [VERIFIED: source read plus the dry-run executed here]
  - So the D-09 phrase "writes inline images next to each mail" should read "writes them to `inline/` in the output directory" to match behaviour. (Spec §10.3 and CLI-17 say "next to the rendered HTML"; the code uses the `inline/` subdirectory. Prefer the precise wording in help and README.)

### HtmlImages API (verified by reading the source; Javadoc is in `HtmlImages.java`)
- `public static Result embed(MailTemplate template, Path baseDir) throws InvalidMailException`
- `public static Result embed(MailTemplate template, Path baseDir, Path workDir) throws InvalidMailException, TransientMailException`
- `public static MailTemplate toDataUris(MailTemplate template, Path baseDir) throws InvalidMailException`
- `public static MailTemplate toDataUris(MailTemplate template, Path baseDir, Path workDir) throws InvalidMailException, TransientMailException`
- `public record Result(MailTemplate template, List<Attachment> images)`
- `Mail.Builder` has `attach(Attachment)` (so the result images go in through it), `inline(Path, String)` and `template(MailTemplate, Map<String, ?>)`.
- Both exceptions are checked (`MailException` subtypes), so the README snippet should declare `throws MailException`.
- Behaviour to state accurately in README (all from the class Javadoc): local sources must lie inside `baseDir`; `$$` placeholders, `cid:`, unknown schemes unchanged; `data:` and `http(s)` unchanged unless a work directory is passed; images of 3 MiB or more are refused; the file-type check uses the file name and platform detection, not the content; the author of the template is trusted (a URL can reach any host the machine can reach); `toDataUris` never writes the work directory.

### morimail-html coordinates
`de.morisoft:morimail-html`, version follows the parent (`0.2.0-SNAPSHOT` now, `0.2.0` at release). It declares `morimail` in **compile** scope (owner decision G-07-2), so a consumer that declares only `morimail-html` gets `morimail` and Jackson through it. JDK 17, no other runtime dependency. `Automatic-Module-Name: de.morisoft.morimail.html` is in the manifest (verified in the rehearsal jar). [VERIFIED: `morimail-html/pom.xml`, jar manifest]

### Graph inline property names (for spec §6.2)
`GraphTransport.java:168-170` writes, for an attachment with a non-null content ID: `"isInline": true` and `"contentId": <bare id>`; a regular attachment gets neither key. `@odata.type` is `#microsoft.graph.fileAttachment`, plus `name`, `contentType`, `contentBytes` (base64). No `contentLocation`. [VERIFIED: GraphTransport.java:160-171, read this session]

Quoted from `GraphTransport.java`:
```java
node.put("@odata.type", "#microsoft.graph.fileAttachment");
...
if (attachment.contentId() != null) {
    node.put("isInline", true);
    node.put("contentId", attachment.contentId());
}
```

### Live result (for D-06/D-08)
05-UAT.md test 1: `result: pass`. The generated blue 64x64 square showed inside the body, not as a separate attachment, in Outlook on the web and one desktop client; dated 2026-11-23 in CONTEXT and STATE. [VERIFIED: `05-UAT.md` lines 15-17]. No question to the owner is needed: the roadmap's "ask whether to record not yet confirmed" branch does not apply.

## Code Examples (all compile against the current build)

The two snippets below were compiled with `javac --release 17` against `morimail/target/classes`, `morimail-html/target/classes` and the Jackson jars (result: no errors). Use them as the README bodies, trimmed as D-01 and D-03 ask. All addresses are `example.org`; no GUID is needed in these two.

### Inline image (README "Inline images")
```java
MailTemplate template = new MailTemplate(
    "Hello $$firstName$$",
    "<p><img src=\"cid:logo\" alt=\"Logo\"></p><p>Hello $$firstName$$,</p>",
    "Hello $$firstName$$,");

Mail mail = Mail.using("entra-main")
    .to("anna@example.org")
    .template(template, Map.of("firstName", "Anna"))
    .inline(Path.of("logo.png"), "logo")
    .build();
```
D-01 wants only the `<img src="cid:logo">` template and the `.inline(Path.of("logo.png"), "logo")` line to carry the point; the lines around them make the block copyable. Behaviours to state (D-02), each backed by `Mail.Builder` Javadoc (`Mail.java:328-343`): content ID not blank, no whitespace, quote, parenthesis or angle bracket; `image/` content type; unique content IDs; every `cid:` has an image and every image is referenced, including a mail with inline images and no HTML body.

### `morimail-html` (README "morimail-html")
```java
HtmlImages.Result result = HtmlImages.embed(template, Path.of("campaign"));   // img src="images/logo.png"
Mail.Builder builder = Mail.using("entra-main")
    .to("anna@example.org")
    .template(result.template(), Map.of("firstName", "Anna"));
for (Attachment image : result.images()) {
    builder.attach(image);
}
Mail mail = builder.build();

// opt-in for http(s) and data: sources, downloads go into the work directory
HtmlImages.Result withRemote = HtmlImages.embed(template, Path.of("campaign"), Path.of("work"));
// storable form: the HTML alone carries its images
MailTemplate storable = HtmlImages.toDataUris(template, Path.of("campaign"));
```
Dependency block for the section:
```xml
<dependency>
  <groupId>de.morisoft</groupId>
  <artifactId>morimail-html</artifactId>
  <version>0.2.0</version>
</dependency>
```

## Common Pitfalls

### Pitfall 1: `send --help` is not a help option
**What goes wrong:** D-09/D-10 and the roadmap say "`send --help`", but `SendCommand` and `SerialMail` declare no `mixinStandardHelpOptions` or `usageHelp` option. Running `send --help` prints the full usage text (the missing-required-option error path) to **stderr** and exits **2**. Running `--help` on the root command prints `Unknown option: '--help'` followed by the root usage, also exit 2. With `--account a --campaign b --help` the first line is `Unknown option: '--help'` and then the usage. [VERIFIED: ran all three against the built jar]
**Why it matters:** The README sentence "points to `send --help` for the options" works for a reader (they see the options), but it is accidental and exits 2. DOCS-05 criterion 3 says the docs are in `send --help`.
**How to avoid:** See Open Question 1. The new D-11 test must not depend on `--help`; call `SerialMail.commandLine(...).getSubcommands().get("send").getUsageMessage()` (picocli API) and assert `contains("inline/")` and `contains("cid:")`. [ASSUMED: that exact picocli call chain; `getUsageMessage()` was used on a plain `CommandLine` in my probe and works. `getSubcommands()` is standard picocli, not run here.]

### Pitfall 2: `%` in picocli help text breaks formatting
**What goes wrong:** picocli formats footer and description text with `String.format`. A single `%` prints `[picocli WARN] Could not format '50% done' ... Please ensure to escape '%' characters with another '%'` and the text comes out unformatted. A `${name}` that picocli does not know prints `null`. [VERIFIED: probe with picocli 4.7.7 jar in the scratchpad, output seen]
**How to avoid:** Write no `%` and no `${...}` in the new help text (the existing text uses `${DEFAULT-VALUE}` on purpose). Do not write `$$firstName$$`-style examples with a `$` followed by `{`. `cid:NAME` and `inline/` are safe.

### Pitfall 3: D-09 wording does not match the dry-run behaviour
**What goes wrong:** "writes inline images next to each mail" is wrong: images go once into `<out>/inline/`. See "Sample `send` help" above. Use the precise wording in the `--dry-run` description and the README.

### Pitfall 4: release.md has more 0.1.0-only text than steps 4, 5, 7, 8
**What goes wrong:** CONTEXT names steps 4, 5, 7 and 8; the table above shows lines 8, 100 and 115 as well. A missed line leaves the owner a guide that says `-pl morimail -am` once, which would silently build a bundle without the helper.
**How to avoid:** After the edit, `grep -n "pl morimail -am\|0\.1\.0\|three\|30 entries" docs/release.md` must return only deliberate hits (the step 5 explanation of the v0.1.0 history, if any is kept). Better: remove all of them.

### Pitfall 5: the rehearsal's pass depends on not using `-o`
**What goes wrong:** With `-o` the central plugin skips `publish`, so no bundle is made (already noted at release.md line 98). Also a SNAPSHOT version makes no zip. The rehearsal must use the release version in a scratch copy.

### Pitfall 6: morimail-html tests do not run in the release-profile build
**What goes wrong:** `-Prelease ... -pl morimail,morimail-html -am` runs 366 `morimail` tests and none of the 71 helper tests. The helper tests run only in the plain `./mvnw -B -ntp verify`. Both gate commands are therefore required, and release.md step 4 must keep the plain verify before the commit. [VERIFIED: `morimail-html/pom.xml` release profile; release-build log shows one surefire run]

### Pitfall 7: README statements about versions and releases
D-04 puts `0.2.0` in the Coordinates block while 0.1.0 is the current release; the added sentence prevents a reader from using a version that is not on Central yet. Keep the existing "0.1.0 is the first release, published on Maven Central" fact in the same sentence group so it stays true until the owner releases. Step 8 of the guide updates it.

### Pitfall 8: public-repo rules for the new docs
Examples use `example.org`/`example.com` and zero GUIDs only. The new snippets above use `anna@example.org`. The release guide's maintainer identity `Stephan Wald <stephan@morisoft.de>` is allowed where it already appears. Never put the rehearsal's throwaway key id into a file (key id `28E8AC57...` was local to this session, deleted; not needed in any doc).

## State of the Art

| Old Approach | Current Approach | Impact |
|--------------|------------------|--------|
| Spec says `build()` rejects `contentId` | `build()` accepts and checks it (spec §10.3, implemented in Phase 5) | D-05 aligns spec §5/§5.1 |
| Release bundle: 30 entries, 5 base files | 54 entries, 9 base files with `morimail-html` | D-13/D-14 |
| Scoped release `-pl morimail -am` | `-pl morimail,morimail-html -am` | CI already uses it; release.md must follow |

## Runtime State Inventory

Not a rename/refactor phase, so the full inventory does not apply. The one relevant runtime item: `~/.m2/repository/de/morisoft/{morimail,morimail-parent}/0.1.0` exist from the earlier rehearsal (stored data on this machine, not in git). Action: planner chooses whether the final cleanup removes `~/.m2/repository/de/morisoft` entirely (D-14 wording) or leaves 0.1.0. No other category (service config, OS registrations, env vars, build artifacts in git) is affected: nothing is renamed.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `SerialMail.commandLine(...).getSubcommands().get("send").getUsageMessage()` returns the full usage text for the D-11 test (only `getUsageMessage()` on a plain `CommandLine` was run here) | Pitfall 1 | Test needs a small adjustment; low risk, the planner's executor runs it |
| A2 | The unescaped dots in `sed "s/${VERSION}-SNAPSHOT/${VERSION}/"` cannot hit another string in the four POMs for versions other than 0.2.0 | release.md edit table | The four-line `git diff` check in the guide catches it |
| A3 | The three "Hard link not supported" warnings are caused by the scratch copy's different filesystem, not by the repo | Build Results | Only affects whether the owner sees them; the real-tree release build has zero warnings |

## Open Questions (RESOLVED)

1. **`send --help` (decision needed or default accepted)**
   - What we know: `send --help` prints the usage text on stderr with exit 2 because `--help` is unknown and required options are missing; there is no help option (verified). D-09 says "annotation texts only, with no new option".
   - What's unclear: whether the owner wants a real help option (`mixinStandardHelpOptions = true` would add `-h/--help` and `-V/--version`, a new option and a version string that needs `versionProvider`) or accepts the current behaviour.
   - Recommendation: Rule 0, keep the CONTEXT decision (no new option). Write "run `send --help` for the options" in the README, because that is true for a human reader. Record the exit-code 2 and stderr quirk in the plan summary so the owner can decide later. Do not add the option without asking.
   - RESOLVED: recommendation taken (08-01-PLAN.md FA-1). No help option is added (D-09, Rule 0), and the 08-01 source gate fails on a help mixin. The README points to `send --help` as it is, and 08-01-SUMMARY.md lists the stderr output with exit code 2 under "Owner review" (08-01 Task 3 steps 3 and 7).

2. **Rehearsal re-run after the doc edits?**
   - The edits touch nothing that enters the bundle, and the evidence above is from the current tree. Recommendation: do not re-run unless the plan edits a POM; do re-run the two gate builds.
   - RESOLVED: re-run at execution, against this recommendation (08-02-PLAN.md FA-6). D-14 locks "Claude re-runs the rehearsal", the evidence above lives only in a session scratchpad, and the re-run also proves that the step 4 sed and the skipUpload line work as written in the new release.md (08-02 Task 1 rehearsal gate). The two gate builds are re-run as well (08-02 Task 2).

3. **Unlisted spec spots (§8.1 layout, §8.3 dry-run sentence)**
   - They describe the sample without `inline/`. D-05 says "edit in place so the spec describes the code" but lists only §5, §5.1, §6.2, §10.3/§10.4, §12. Recommendation: add one line to §8.1 (`inline/` directory with the content-ID rule) and one sentence to §8.3 (dry-run writes `out/inline/` and rewrites `src`), each short; this follows the intent of D-05/D-10. If the planner prefers to stay strictly inside D-05..D-07, leave them and note the gap.
   - RESOLVED: recommendation taken (08-01-PLAN.md FA-3). 08-01 Task 3 step 4 adds one `inline/` line to §8.1 and one paragraph to §8.3, the 08-01 spec hunk gates allow no other line of section 8, and 08-01-SUMMARY.md lists both under "Owner review".

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| JDK | all builds | yes | Temurin 21.0.13.1 (builds with `--release 17`; JDK 17 itself is not installed, CI on Temurin 17 is the proof) | - |
| `./mvnw` (Maven 3.10.1) | builds | yes | wrapper, cached | - |
| gpg | rehearsal | yes | 2.4.8 | - |
| unzip | bundle listing | yes | present | - |
| Network | deploy rehearsal, `mvnw` | works; dependencies are cached so `-o` verify also passes | - | - |

No missing dependencies. `docker`, `jshell` and other tools are not needed.

## Validation Architecture

`workflow.nyquist_validation` is `false` in `.planning/config.json`, so no Nyquist test map is required. Practical gates for the planner, all runnable here:

| Check | Command |
|-------|---------|
| Full build offline | `./mvnw -B -ntp -o verify` |
| Scoped release build, no warnings | `./mvnw -B -ntp -Prelease clean verify -Dgpg.skip -pl morimail,morimail-html -am` then `grep -c WARN <log>` equals 0 |
| New usage test | `./mvnw -B -ntp -o test -pl morimail-sample-serial -am -Dtest=SendCommandHelpTest -Dsurefire.failIfNoSpecifiedTests=false` (the sample needs the `morimail` test-jar from the reactor, so keep `-am`) |
| README snippets compile | `javac --release 17 -cp morimail/target/classes:morimail-html/target/classes:<jackson jars> <file>` (as done in the scratchpad) |
| No 0.1.0 leftovers in release.md | `grep -n "pl morimail -am\|0\.1\.0\|three POM\|30 entries" docs/release.md` |
| Public-repo rule | `git diff` contains no real address, tenant GUID (only zero GUIDs), host name or key id |

Wave 0 gaps: none. The only new file is `SendCommandHelpTest.java` (existing test infrastructure covers it; `SampleFixtures` is not needed for a usage-text test).

## Security Domain

`security_enforcement` is enabled (absent means enabled). The phase changes docs, annotation text and one test, so almost no ASVS category applies.

| ASVS Category | Applies | Control |
|---------------|---------|---------|
| V2/V3/V4 Auth, Session, Access | no | no runtime change |
| V5 Input validation | no | no code reading input changes |
| V6 Cryptography | no | no change; the release signing uses the owner's key, rehearsal used a throwaway key |
| V10 / data in docs (secrets in a public repo) | yes | examples use `example.org`/`example.com` and zero GUIDs only (CLAUDE.md rules) |

| Threat | STRIDE | Mitigation |
|--------|--------|------------|
| A real address, tenant ID or token in README/spec/release.md | Information disclosure | Review `git diff`; use only `example.*` and zero GUIDs; the Portal token placeholders in release.md stay `TOKEN_USERNAME`/`TOKEN_PASSWORD` |
| README snippet teaches a risky pattern (remote images from untrusted templates) | Tampering / SSRF | State in the README line that remote download is opt-in and that the template author is trusted (the class Javadoc says: "a template URL, also after a redirect, can reach any host this machine can reach"); never pass recipient data into `HtmlImages` |
| Release guide leads to a partial bundle (helper missing) | Tampering with release integrity | All `-pl` strings include `morimail-html`; guide lists the 54 expected entries so the owner can detect a wrong bundle |
| Throwaway key or signed bundle left on the machine | Information disclosure | Removed the key dir and the 0.2.0 local-repo artifacts; the scratch rehearsal copy lives in the scratchpad (`.../scratchpad/rehearsal`, not in the repo) |

## Sources

### Primary (HIGH confidence, read or run this session)
- `/home/coder/morimail/.planning/phases/08-docs-and-release-readiness/08-CONTEXT.md`, `REQUIREMENTS.md`, `STATE.md`, `ROADMAP.md` (Phase 8 block)
- `README.md`, `docs/spec.md`, `docs/architecture.md`, `docs/release.md` (read in full or the relevant ranges)
- `morimail/src/main/java/de/morisoft/morimail/graph/GraphTransport.java` (lines 150-180), `.../Mail.java` (builder Javadoc, signatures)
- `morimail-html/src/main/java/de/morisoft/morimail/html/HtmlImages.java` (class Javadoc, signatures, lines 40-170, 236-320)
- `morimail-html/pom.xml`, `pom.xml` (release profile), `morimail-sample-serial/pom.xml`, `.github/workflows/ci.yml`
- `morimail-sample-serial/src/main/java/.../SendCommand.java`, `SerialMail.java`, `Campaign.java`; `SampleFixtures.java`; `example-campaign/`
- `.planning/phases/05-inline-images-in-core-and-graph/05-UAT.md` (test 1)
- Build and rehearsal logs in `/tmp/claude-1000/-home-coder-morimail/d32c6d2f-c51a-49c5-8473-cae383e39687/scratchpad/` (`verify.log`, `release.log`, `release2.log`, `rehearsal.log`, `entries.txt`)
- picocli 4.7.7 behaviour probes in `.../scratchpad/pc/T.java`

### Secondary / Tertiary
None. No web source was needed; no claim depends on one.

## Metadata

**Confidence breakdown:**
- Build and bundle facts: HIGH, produced by runs this session.
- File edit map and API names: HIGH, read from the files.
- picocli test call chain (A1): MEDIUM, standard API but not run for `getSubcommands()`.
- JDK 17 runtime behaviour: not tested here (JDK 21 only); the repo relies on Temurin 17 CI, per the roadmap gates. After the edits, the owner pushes and checks CI.

**Research date:** 2026-11-23
**Valid until:** until the repo changes (docs and builds are tied to this commit, 1480da2)
