# morimail

## What This Is

morimail is a small open-source Java 17 library (`de.morisoft:morimail`) that sends templated mail through pluggable transports, one mail at a time. The MVP ships one built-in transport, Microsoft Graph, authenticated as an Entra app (client credentials with a certificate or client secret, no user sign-in). Since 0.2.0 a mail can carry inline images referenced by `cid:`, and the optional helper module `morimail-html` (`de.morisoft:morimail-html`) turns ordinary `<img src>` URLs in a template into `cid:` references plus inline images. The same repository ships a command-line sample (`morimail-sample-serial`) that sends a serial mail: one template, one CSV of recipients, shared attachments, one mail per recipient, with a send journal so a rerun never sends twice. Its campaigns can add an optional `inline/` image directory.

The full, binding specification is `spec.md` (moved to `docs/spec.md` in the first phase). Where this document and the spec disagree, the spec wins.

## Core Value

A consumer can send one templated mail with attachments through Microsoft Graph as an Entra app, using only the JDK and Jackson, and get back a clear error category (transient / permanent / auth-required) that tells them what to do next.

## Current State

**Shipped:** v0.2.0 Inline Images (archived 2026-11-24; the owner released `0.2.0` and tagged `v0.2.0`). `main` is at `0.3.0-SNAPSHOT`, and the Phase 9 tech-debt fixes ship with the next release.

**Next milestone:** not defined yet. Start it with `/gsd-new-milestone`.

## Requirements

### Validated

- ✓ Mail model as records: account ID, from, to, cc, bcc, reply-to, subject, HTML body, optional text body, attachments (`MailAddress`, `Attachment` with `contentId` prepared for inline images, `MailMessage`, `Mail`) — Phase 1
- ✓ Builder `Mail.using(accountId)...build()` with transport-independent validation (recipient, subject, body, readable attachments, no `contentId` in MVP) — Phase 1
- ✓ `TemplateRenderer` with `$$name$$` placeholders (HTML-escaped in HTML, `$$!name$$` raw, subject line-break guard, missing field = `InvalidMailException`) — Phase 1
- ✓ Accounts built in code (`MailAccount`: id, transport, auth, options, defaultFrom) and a `Mailer` holding accounts by ID, with `validate` and `send` — Phase 1
- ✓ Transport SPI (`MailTransport`, `TransportProvider`, `TokenProvider`) with a registry: built-in explicit list plus `ServiceLoader`; external duplicates of a built-in ID fail; unknown ID fails at account build with the list of available IDs — Phase 1
- ✓ Error hierarchy: `MailException` → `TransientMailException` (retryAfter), `PermanentMailException` → `InvalidMailException`, `SenderRejectedException`; `AuthRequiredException` — Phase 1
- ✓ `RecordingTransport` in library test sources, built as a test-jar shared inside the reactor only (`attach=false` under `release`, never published), registered via `ServiceLoader` as `recording` — Phase 1
- ✓ Repo hygiene: parent POM with Central metadata, Maven wrapper, `LICENSE`, `README.md`, `SECURITY.md`, CI workflow (`mvn verify`, Java 17), `CLAUDE.md` with spec §11 rules — Phase 1
- ✓ `release` Maven profile (sources, Javadoc, GPG, Central publishing plugin); `mvn -Prelease verify -Dgpg.skip` passes without warnings — Phase 1
- ✓ Serial-mail CLI (picocli): account.properties, campaign dir, pre-flight check (exit 2), journal + resume, rate limit, transient retry ×3, stop on sender/auth errors (exit 3), dry-run output, counts and exit codes — Phase 2 (real sending waits for the Graph transport, Phase 3)
- ✓ Example campaign with three placeholders, two small generated attachments (PDF, PNG), five `example.org` rows — Phase 2
- ✓ Entra client-credentials token client on the JDK: secret and certificate (PS256 JWT assertion with `x5t#S256`, accepted by Entra live), PKCS12 convenience loader, in-memory cache with 5-minute margin, single-flight, invalidation for 401 handling, error mapping — Phase 3
- ✓ Graph transport: `POST /users/{from}/sendMail`, JSON body, file attachments base64, `saveToSentItems` option, size limits in `validate` (binary MiB, confirmed live), error mapping (403/404 rows confirmed live), one retry after 401 with a fresh token; failure messages carry status, code and request-id only — Phase 3
- ✓ `mvn verify` passes with no network access (CI `unshare --net` step, Temurin 17); live test tagged `live`, enabled with `-Plive`, run by the owner — Phase 3
- ✓ Docs: `docs/entra-setup.md` (admin guide, RBAC for Applications), `docs/architecture.md`, README quick start, `docs/release.md` (gpg-agent warm-up for `-B` deploys, tag the release commit) — Phase 4
- ✓ Release 0.1.0: signed deploy validated by the Central Portal (30-entry bundle), tag `v0.1.0` on the CI-green release commit — Phase 4 (run by the owner)

- ✓ Address scrub in `clean()` cuts at a word boundary, so an address straddling the 1000-character cut cannot leak its start (review WR-01) — v0.1.0 (Phase 4, 04-01)

- ✓ Inline images in core and Graph: `.inline(path, contentId)`, two-way `cid:` checks and image-type check in `build()`, recorded by `recording`, sent by Graph as inline file attachments (bare `contentId`) within the existing 3 MiB / 4 MiB limits; renders inline live (owner UAT) — v0.2.0 Phase 5 (INLN-01..06, GRAPH-10..12)

- ✓ Inline images in the serial-mail sample: optional `inline/` directory (file name without extension = content ID), pre-flight inline checks in one report, dry run copies the images to `out/inline/` and rewrites `cid:` only in the written HTML, sent mails keep `cid:`; example campaign ships a generated 166-byte `inline/logo.png` — v0.2.0 Phase 6 (CLI-15..18)

- ✓ HTML image helper per spec §10.4: module `morimail-html` (depends on `morimail` in compile scope) with `HtmlImages.embed` (local images under a base directory, `http(s)` downloads opt-in via a work directory, `data:` decoded) and `toDataUris`; 3 MiB per-image cap as in Graph; Temurin 17 CI green, owner accepted A1-A7 and P1-P10 (Phase 7 UAT) — v0.2.0 Phase 7 (HTML-01..06)

- ✓ Docs and release readiness: README inline-image, `morimail-html` and sample sections with compiled snippets; spec and `docs/architecture.md` describe 0.2.0 with the live result; `docs/release.md` is one version-neutral procedure for parent, `morimail` and `morimail-html`, with both deploy commands guarded against SNAPSHOT versions; the owner released 0.2.0 (tag `v0.2.0`) — v0.2.0 Phase 8 (DOCS-05..07)

- ✓ v0.2.0 tech-debt cleanup: `<` ends a `cid:` id in the core, the sample and the helper; the echoed id is cleaned of control, separator and format characters; webp, bmp and svg resolve in both type tables; sample pre-flight validates every row's mail through the transport, not only the first — v0.2.0 Phase 9 (ships with 0.3.0)

Everything above this line except the Phase 5 to 9 entries shipped in v0.1.0 (released 2026-11-22, `de.morisoft:morimail:0.1.0` on Maven Central).

### Active

None. All 23 v0.2.0 requirements are validated; the next milestone defines new ones.

### Out of Scope

- SMTP, SES, Gmail transports — later milestones; contracts must accept them without change
- Delegated (user) OAuth, token store, interactive consent — later milestones
- Attachments of 3 MB or more (upload session) — needs a draft-then-send flow and `Mail.ReadWrite`
- HTML and text as two alternative parts on Graph — needs the MIME format; MVP sends HTML only
- Queue integration, idempotency keys, expiry — the consumer wraps the mail in its own message
- Loading accounts from config files, secret references, vault adapters — consumer's concern
- Local check of allowed from-addresses — Exchange enforces it server-side
- Missing-placeholder policies other than "fail"
- Per-recipient attachments in the sample CLI; any batch or "shared attachments" concept in the library
- S/MIME, DKIM, custom internet headers, List-Unsubscribe
- Spring Boot starter
- Contribution guide, code of conduct, issue templates, badges — until there are contributors

## Context

- Shipped v0.1.0 (2026-11-22) and v0.2.0 (2026-11-24). At the v0.2.0 close: about 4,400 lines of main Java and 14,500 lines in total with tests, across the library, `morimail-html` and the sample. Java 17, Maven 3.10.1 wrapper, Jackson 2.23.0 only at runtime, picocli in the sample, JUnit 6, AssertJ and WireMock for tests.
- Repository: https://github.com/morisoft-lib/morimail. Public from the first commit; git history is permanent. The owner set the `v0.1.0` and `v0.2.0` tags on the CI-green release commits; `main` is now at `0.3.0-SNAPSHOT`.
- Published artifacts: `de.morisoft:morimail-parent`, `de.morisoft:morimail` and (from 0.2.0) `de.morisoft:morimail-html`. The sample and the test-jar are not published. The release build only works scoped (`-Prelease ... -pl morimail,morimail-html -am`).
- Microsoft docs to read before coding against an endpoint: Graph `user: sendMail` and `fileAttachment` (inline `isInline` + bare `contentId` confirmed live in Phase 5), Entra client-credentials flow, certificate credentials (PS256, `x5t#S256`), Exchange RBAC for Applications.
- The live test confirmed PS256, the 403/404 error rows and the binary MiB limits. The spec §6.3 table has a live-status column.
- Known tech debt (v0.1.0 audit): a quoted local part is not redacted; `isHeaderSafe` duplicates Graph's `requireToken`; Entra messages can name app and tenant IDs; settable timeouts and authority/`baseUrl` keys in `account.properties` are deferred; `SerialMail` duplicates the Graph base URL constant; `TransportRegistry.load()` runs on every account build.
- Known tech debt (v0.2.0 audit): pre-flight prints one line per distinct 4 MiB request-size message, because the message carries the per-row estimate (09 WR-01, noise only); content IDs are compared case-sensitively in the core (05 IN-03, deferred until a live test shows a need); decomposed file names in `inline/` on macOS (06 IN-03, deferred).

## Constraints

- **Tech stack**: Java 17 and Maven (parent + library + sample module, wrapper checked in). Library runtime dependency is Jackson only; `System.Logger` for logging; `java.net.http.HttpClient`; JDK crypto for the JWT. picocli for the sample only. Tests: JUnit 5, AssertJ, WireMock. Use the latest stable versions, checked on Maven Central.
- **Simplicity (Rule 0)**: Build the simplest code that meets the spec. Only two extension points (`TransportProvider`, `TokenProvider`). No frameworks, DI, reflection, or extra interfaces/patterns. Ask before adding any dependency. When in doubt, build less.
- **Architecture**: The core package imports only the JDK (its one outside reference is the built-in list). `graph` and `entra` don't import each other. No Graph assumptions in the core. Transport options are opaque to the core. Limits are checked in the transport's `validate`.
- **Security / public repo**: No secrets, tenant IDs, real addresses, internal hosts or customer names in any file, fixture or commit. Use `example.com`/`example.org` and zero GUIDs. The only exceptions are the throwaway test PKCS12 (password `changeit`, since keytool rejects `test`) and the maintainer identity. Never log tokens, secrets, assertions or full bodies; recipients at DEBUG only.
- **Identity**: All commits authored and committed as `Stephan Wald <stephan@morisoft.de>`, set in local git config (done 2026-11-21). Global config untouched.
- **Operations**: Never send real mail, never publish/deploy/tag. The owner does releases and live tests.
- **Concurrency/docs**: Public types get Javadoc stating thread safety. `Mailer`, transports and token providers are safe for concurrent use. Javadoc must build cleanly.
- **Build**: `mvn verify` must pass offline. Finish and commit each unit of work with `mvn verify` green.
- **Checkpoint**: Stop after the Graph transport + Entra client work (spec milestone 4) and report, so the owner can run the live test and correct the error mapping.

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| No MSAL4J, no Graph SDK, no JWT library | REST calls plus JDK crypto are enough; keeps the jar to Jackson only | ✓ Good — PS256 assertion accepted by Entra live (Phase 3) |
| Sample CLI built before the Graph transport | Exercises the contracts against `RecordingTransport` first, so contract mistakes surface early | ✓ Good — Phase 2 drove the whole send loop through `Mailer` and `recording` with no library change |
| Sample delivery is at-least-once; a damaged journal line resends its row | A mail in flight at a crash has no journal line; a duplicate beats a silently skipped recipient | ✓ Documented in `send --help` (Phase 2) |
| Hand-written comma-only CSV reader, runnable jar via manifest classpath over `target/lib` | Rule 0: no CSV library, no shade plugin | ✓ Good (Phase 2) |
| HTML only when both bodies are given on Graph (log once at INFO) | JSON sendMail carries one body; MIME later | ✓ Good — shipped in v0.1.0; revisit with a MIME transport |
| Attachments limited to under 3 MiB per file and 4 MiB in total | Upload session needs draft flow and `Mail.ReadWrite` | ✓ Good — binary MiB limits confirmed live (Phase 4) |
| Recommend Exchange RBAC for Applications over tenant-wide Entra consent | Scopes sending to chosen mailboxes; the Entra grant must be removed or it overrides | ✓ Good — documented in `docs/entra-setup.md` |
| Planning docs (`.planning/`) committed to the public repo | Owner's choice at initialization | ✓ Good — hygiene scans kept them free of secrets; one GPG key id was caught and removed before push |
| Roadmap phases derived by the roadmapper rather than mirrored 1:1 from spec milestones | Owner's choice. The spec's order constraints (sample before Graph, stop after Graph/Entra) still apply | ✓ Good — four vertical slices, the hard stop held |
| Release build scoped to published modules (`-pl morimail -am`, D-20), test-jar reactor-only | The unattached test-jar cannot resolve in a full-reactor release build | ⚠️ Revisit — works, but easy to trip over |
| Release 0.1.0 run by the owner from `docs/release.md` | Claude never publishes or tags | ✓ Good — Central Portal validated the 30-entry bundle |
| `LICENSE` holder Stephan Wald, `SECURITY.md` contact stephan@morisoft.de | Spec defaults; confirm at the Graph checkpoint | ✓ Confirmed by the owner (Phase 3 UAT) |
| Keep Claude Code's `Co-Authored-By` trailer | Spec §12 default; the owner may switch it off | ✓ Confirmed by the owner (Phase 3 UAT) |
| Graph failure messages drop Graph's free-text `error.message` | It can name the sender address (404 ErrorInvalidUser seen live) | ✓ Good — G-03-2, confirmed live (Phase 3) |
| `Retry-After` read as seconds only | Matches Graph throttling docs; reverses D-10 after review IN-05 | ✓ Accepted by the owner (Phase 3) |
| Sample default rate 20 mails per minute | A guess pending Exchange limits | ✓ Accepted by the owner (Phase 3) |
| Graph inline images sent with a bare `contentId` (no angle brackets, no `contentLocation`) | Smallest body the current `fileAttachment` docs allow | ✓ Good — renders inline live (Phase 5 UAT) |
| Sample inline scan probes each `inline/` file through the core's `build()` instead of copying its type table; the `cid:` pattern is copied into `Campaign` and pinned equal to the core's | One source of truth for the rules; the sample stays small (Rule 0) | ✓ Good — dry-run logo shown in all five mails (Phase 6 UAT) |
| `morimail-html` declares `morimail` in compile scope, not `provided` (reverses D-02) | Nearest-wins lets a consumer's direct `morimail` override it anyway; compile scope keeps the version link, so a pre-0.2.0 core without inline-image support is not silently used | ✓ Owner decision (Phase 7 UAT, G-07-2) |
| Release deploys in `docs/release.md` are chained behind `grep -- -SNAPSHOT ...; [ $? -eq 1 ] &&` | A SNAPSHOT deploy uploads to Central's snapshots repository (the 401 in Phase 8 UAT); the guard also stops on a grep error (review WR-01) | ✓ Good — G-08-2 closed, re-verified (Phase 8) |
| `<` ends a `cid:` id, with the same pattern copied into the sample and the helper and pinned equal by tests | `<p>cid:logo</p>` must refer to `logo`; one rule in three places, each guarded by a `cidPatternAgreesWithTheCore` test | ✓ Good (Phase 9) |
| Sample pre-flight runs the transport's `validate` on every row's mail and groups messages | A later row with a larger body could pass pre-flight and fail at send time | ✓ Good — proven with the real Graph transport (Phase 9); output noise is WR-01 |
| Helper content IDs compared case-insensitively, core IDs case-sensitively | The helper derives IDs from file names (`Logo.png` then `logo.png` gives `logo-2`); the core keeps the 0.2.0 behaviour | — Pending — revisit after a live test (05 IN-03) |

## Evolution

This document evolves at phase transitions and milestone boundaries.

**After each phase transition** (via `/gsd-transition`):
1. Requirements invalidated? → Move to Out of Scope with reason
2. Requirements validated? → Move to Validated with phase reference
3. New requirements emerged? → Add to Active
4. Decisions to log? → Add to Key Decisions
5. "What This Is" still accurate? → Update if drifted

**After each milestone** (via `/gsd-complete-milestone`):
1. Full review of all sections
2. Core Value check — still the right priority?
3. Audit Out of Scope — reasons still valid?
4. Update Context with current state

---
*Last updated: 2026-11-24 after v0.2.0 milestone*
