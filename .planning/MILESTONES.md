# Milestones

## v0.2.0 Inline Images (Shipped: 2026-11-24)

**Phases completed:** 5 phases, 14 plans, 19 tasks

**Key accomplishments:**
- `Mail.Builder.inline(Path, String)` with build()-time content-ID, image-type, duplicate and two-way `cid:` checks, and Graph `fileAttachment` nodes carrying `isInline` and `contentId`, proven through Mailer, Entra stub and WireMock
- Five tests that put inline images under the unchanged 3 MiB / 4 MiB Graph rules before any token request, the owner-run `LiveGraphTest.sendsOneMailWithAnInlineImage` with a run-time PNG, and the Graph inline property names, live status and three spec deviations in `docs/architecture.md`
- An optional `inline/` directory in a campaign now becomes inline images in every sent mail (content ID = file name without its last extension), and the dry run copies them once to `out/inline/` with percent-encoded, HTML-safe relative `src` paths while sent mails keep `cid:`.
- Pre-flight now reports every inline defect (a `cid:` with no image, an image with no `cid:`, a taken content ID, a non-image file, a file name that gives an invalid content ID) as its own line of the one report with exit 2 and nothing written, and the shipped example campaign shows a generated 96 x 48 `inline/logo.png` in all five dry-run mails and on the Graph wire.
- New `morimail-html` module whose `HtmlImages.embed(template, baseDir)` turns local `<img src>` values into `cid:` references plus core-valid inline images, with `morimail` in compile scope since 07-04 (provided-scope only at 07-01), a scoped release build and CI line for both published modules, and a green Temurin 17 round-trip.
- `HtmlImages.embed(template, baseDir, workDir)` downloads `http(s)` images into the caller's work directory with one 30 s budget for headers and body, a hard 3 MiB cap (3,145,728 bytes refused, as Graph refuses them), an `image/` content type check and transient/invalid categories by cause, all pinned offline against WireMock.
- `HtmlImages.toDataUris` stores a template as one plain HTML string with `data:TYPE;base64,PAYLOAD` images, `embed` with a work directory decodes it back into inline images with the original bytes (strict base64, 3 MiB cap checked before and after decoding, failures named by position and never by payload), and the phase gate proves the `morimail` jar byte-identical to the one built from f9b2f57.
- `morimail-html` now declares `morimail` with Maven's default compile scope, so the helper's published POM names the core version it is built against; docs, Javadoc and 07-CONTEXT.md agree, and the `morimail` jar is byte-identical to the one from f9b2f57.
- README, spec, architecture page and `send` usage text now describe inline images, the `morimail-html` helper and the sample's `inline/` directory for 0.2.0, with both README Java snippets compiled as written.
- `docs/release.md` is now one version-neutral procedure for the parent POM, `morimail` and `morimail-html`. Its step 4 and the skipUpload check, run as written with a throwaway key, built exactly the 54-entry bundle it lists, and nothing was uploaded.
- docs/release.md step 5 now chains both deploy commands behind `! grep -- -SNAPSHOT pom.xml morimail/pom.xml morimail-html/pom.xml &&`, and its SNAPSHOT note states the plugin's real behaviour (snapshots repository, only `-DskipUpload=true` stops the upload).
- A `cid:` id now ends at `<` in the core, the serial mail sample and the HTML image helper, the id echoed by `build()` is cleaned of control, separator and format characters, and webp, bmp and svg resolve from both content-type tables.
- A serial run now stops in pre-flight, exit 2, when any row's mail is refused by the transport's validate (for Graph: a body estimate above 4 MiB or an attachment of 3 MiB or more), and a limit that every row breaks is one line ending `(all N rows)`.
- HtmlImages now documents that white space around a src value is ignored and no longer claims the HTML rule for strip(); the 05 and 07 review dispositions are settled (open 0), the 07-01 and 08-02 summaries parse again, and both builds are green on the combined tree.

**Release:** `0.2.0` released and tagged `v0.2.0` by the owner (release commit 2c4ea80). Phase 9 work postdates the tag and ships with 0.3.0.
**Git range:** v0.1.0..9c79893 plus the quick-261124-d10 docs, 242 commits, 2026-11-22 → 2026-11-24.
**Milestone audit:** tech_debt (23/23 requirements, 17/17 integration seams, 4/4 flows). Open debt: 09 WR-01 (pre-flight prints one line per distinct 4 MiB size message, noise only); see `milestones/v0.2.0-MILESTONE-AUDIT.md`.
**Closeout type:** override_closeout. All five phase VERIFICATION.md reports (phases 5-9) passed, but the verifier marks them stale: later phases and quick tasks edited files they cover. The owner chose to close anyway on 2026-11-24.
**Known verification overrides:** 5 newly acknowledged (stale reports for phases 5-9), 0 carried forward. The open-artifact audit was clear.

---

## v0.1.0 MVP (Shipped: 2026-11-22)

**Phases completed:** 4 phases, 22 plans, 42 tasks

**Key accomplishments:**
- Release-shaped Maven reactor (parent, library, sample) on a script-only Maven 3.10.1 wrapper, green in default, release and offline mode, with a SHA-pinned Java 17 CI workflow.
- Public-facing docs (README, SECURITY.md), the binding spec moved to docs/spec.md, spec section 11 as root CLAUDE.md, five spec deviations recorded, and every whole-tree hygiene gate green.
- Plain-text `Mail.using(..).to(..).subject(..).text(..).build()` with one first-problem `InvalidMailException`, the full checked error tree, and the documented `MailTransport` / `TransportProvider` / `TokenProvider` SPI, all doclint-clean under the release profile.
- A plain-text mail now goes from `Mail.using(..)` through `Mailer.of(account).send(mail)` into the `recording` transport with the account's `defaultFrom` applied, in the library and in the sample module via the reactor test-jar, with the import-rule and thread-safety Javadoc guards enforced by the build.
- `Mail.using(id).to(..).template(t, data).attach(p).build()` now renders subject, html and text from a `Map<String, String>` row with HTML escaping, resolves attachment metadata, and `build()` reports the first unsendable problem as one `InvalidMailException` that never echoes a value.
- RecordingTransport can script transient, permanent, sender-rejected, auth-required and per-recipient failures that surface from Mailer with their type, and TransportRegistry now fails loading on duplicate, blank or broken providers.
- Nine @param tags added to the MailMessage compact constructor Javadoc so JDK 17 doclint (all, failOnWarnings) no longer reports "no @param"; comment-only change, three CI commands green on JDK 21, Temurin 17 confirmation handed to the owner
- picocli `serial-mail send` that reads a CSV campaign, sends one mail per row through an injected Mailer with a forced JSON-lines journal (rerun skips accepted rows), and dry-runs the same campaign into sanitised, zero-padded rendered files.
- Pre-flight now reports placeholders without a CSV column, semicolon headers, repeated columns, duplicate addresses and bad account credentials together in one fixed order, exit 2, nothing sent, no journal, and never prints a value from account.properties.
- The `send` run loop now retries transient failures (four attempts, waits capped at 5 min and never below the `--rate` spacing), stops with exit 3 on sender-rejected or auth-required failures, rejects bad `--rate`, `--limit` and campaign ids in pre-flight, and keeps a journal that warns about damaged lines, repairs a torn last line and stops the run when a write fails.
- Runnable `morimail-sample-serial` jar (manifest classpath over `target/lib`) that dry-runs the shipped example campaign, five example.org rows with three placeholders, a PDF and a PNG, into five rendered mails with no secret set.
- A secret-authenticated Entra app gets a token from a WireMock endpoint and sends one text mail through a registry-built `graph` account, with graph resolved from the built-in list.
- EntraClientCredentials signs a PS256 client assertion from a key and certificate or a PKCS12 file, caches the token with single flight and compare-and-drop, and maps every token failure to transient or auth-required without a secret in any message.
- The Graph transport now sends the full documented sendMail JSON (names, cc, bcc, reply-to, one body, base64 file attachments, saveToSentItems only when false), rejects bad options naming the key, and rejects mails over the 3 MiB or 4 MiB limits before any token request or byte is sent.
- GraphTransport retries a 401 once with `freshAccessToken`, maps every status to the documented exception with Retry-After, and builds failure messages from the status, error code, request-id and cleaned Graph message only.
- `serial-mail send` now builds an Entra token provider and a registry-built graph account from account.properties and the environment, delivers the example campaign once through WireMock, and stops in pre-flight or with exit 3 as the plan requires.
- Graph failure messages now carry only the HTTP status, `error.code` and `request-id` (Graph's free-text `error.message` is dropped), and the root-logger capture in `EntraTokenErrorsTest` uses `CopyOnWriteArrayList`.
- Server text in Graph and Entra failure messages is cut at a word boundary before addresses are replaced, so no part of an address survives, and spec §6.3, §8.3 and docs/architecture.md now match the live test of 2026-11-22.
- docs/entra-setup.md takes the owner from an empty app registration to a campaign whose second run prints `accepted 0, skipped <rows>, failed 0`, with the Exchange cmdlets copied from Microsoft's RBAC page re-read on 2026-11-22 and every open item kept in a closing section.
- README.md now says what morimail is and is not (not a bulk mailer, the Exchange limits, under 3 MiB per file and in total), and its quick start has three catches, one per error category, and compiled clean with `javac --release 17 -Xlint:all -Werror` against the packaged jar.

**Closeout type:** override_closeout. All four phase VERIFICATION.md reports passed, but the verifier marks them stale: files they cover changed afterwards (later phases edited README, docs and POMs; then the 0.2.0-SNAPSHOT bump and fix 378e0bb). The owner chose to close anyway on 2026-11-22.
**Known verification overrides:** 4 newly acknowledged (stale reports for phases 1-4), 0 carried forward. Phase 1 also carries the owner-accepted override D-02b (root commit authored by GitHub).
**Audit:** tech_debt. 69/69 requirements, 14/14 integration connections, offline `./mvnw -o verify` green. 11 tech-debt items, none blocking; see milestones/v0.1.0-MILESTONE-AUDIT.md.
**Git range:** 8c9b082 (initial commit) → 378e0bb; release tag `v0.1.0` set by the owner on the CI-green release commit.

---
