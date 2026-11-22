# morimail

## What This Is

morimail is a small open-source Java 17 library (`de.morisoft:morimail`) that sends templated mail through pluggable transports, one mail at a time. The MVP ships one built-in transport, Microsoft Graph, authenticated as an Entra app (client credentials with a certificate or client secret, no user sign-in). The same repository ships a command-line sample (`morimail-sample-serial`) that sends a serial mail: one template, one CSV of recipients, shared attachments, one mail per recipient, with a send journal so a rerun never sends twice.

The full, binding specification is `spec.md` (moved to `docs/spec.md` in the first phase). Where this document and the spec disagree, the spec wins.

## Core Value

A consumer can send one templated mail with attachments through Microsoft Graph as an Entra app, using only the JDK and Jackson, and get back a clear error category (transient / permanent / auth-required) that tells them what to do next.

## Current Milestone: v0.2.0 Inline Images

**Goal:** A consumer can embed images in a templated HTML mail with `cid:` references and send it through Graph with the images shown inline (spec §10.3).

**Target features:**
- Builder `.inline(Path image, String contentId)`; `build()` checks every `cid:` reference against the inline attachments both ways, unique content IDs, image content types only
- Graph transport sends inline images as file attachments marked inline (property names verified against the current `fileAttachment` docs); inline images count toward the existing 3 MiB / 4 MiB limits
- Sample: optional `inline/` campaign directory (file name without extension = content ID); dry-run writes the images next to the rendered HTML and rewrites `cid:` to relative paths in the dry-run output only
- Docs updated and release-ready (`-Prelease verify -Dgpg.skip` green); the owner runs the live inline test, the deploy and the tag

Out of this milestone: the v0.1.0 tech-debt items (owner's choice, 2026-11-22).

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

All of the above shipped in v0.1.0 (released 2026-11-22, `de.morisoft:morimail:0.1.0` on Maven Central).

### Active

- [ ] Inline images (CID) per spec §10.3: builder `.inline(...)`, two-way `cid:` checks in `build()`, Graph inline file attachments within the existing size limits, sample `inline/` directory and dry-run rewrite, docs — v0.2.0 (REQ-IDs in REQUIREMENTS.md)
- [ ] HTML image helper per spec §10.4: new module `morimail-html` with `HtmlImages` (template `<img src>` to `cid:` plus inline images; local under a base directory, `http(s)` opt-in) — v0.2.0 Phase 7 (HTML-01..06)

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

- Shipped v0.1.0 (2026-11-22): about 3,350 lines of main Java and 10,600 lines in total with tests, in the library and the sample. Java 17, Maven 3.10.1 wrapper, Jackson 2.23.0 only at runtime, picocli in the sample, JUnit 6, AssertJ and WireMock for tests.
- Repository: https://github.com/morisoft-lib/morimail. Public from the first commit; git history is permanent. The `v0.1.0` tag was set by the owner on the CI-green release commit; `main` is now at `0.2.0-SNAPSHOT`.
- Published artifacts: `de.morisoft:morimail-parent` and `de.morisoft:morimail`. The sample and the test-jar are not published. The release build only works scoped (`-Prelease ... -pl morimail -am`, D-20).
- Microsoft docs to read before coding against an endpoint: Graph `user: sendMail` and `fileAttachment` (inline properties are unverified), Entra client-credentials flow, certificate credentials (PS256, `x5t#S256`), Exchange RBAC for Applications.
- The live test confirmed PS256, the 403/404 error rows and the binary MiB limits. The spec §6.3 table has a live-status column.
- Known tech debt (v0.1.0 audit): a quoted local part is not redacted; `isHeaderSafe` duplicates Graph's `requireToken`; Entra messages can name app and tenant IDs; settable timeouts and authority/`baseUrl` keys in `account.properties` are deferred; `SerialMail` duplicates the Graph base URL constant; `TransportRegistry.load()` runs on every account build.

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
*Last updated: 2026-11-22 after starting milestone v0.2.0*
