# morimail

## What This Is

morimail is a small open-source Java 17 library (`de.morisoft:morimail`) that sends templated mail through pluggable transports, one mail at a time. The MVP ships one built-in transport, Microsoft Graph, authenticated as an Entra app (client credentials with a certificate or client secret, no user sign-in). The same repository ships a command-line sample (`morimail-sample-serial`) that sends a serial mail: one template, one CSV of recipients, shared attachments, one mail per recipient, with a send journal so a rerun never sends twice.

The full, binding specification is `spec.md` (moved to `docs/spec.md` in the first phase). Where this document and the spec disagree, the spec wins.

## Core Value

A consumer can send one templated mail with attachments through Microsoft Graph as an Entra app, using only the JDK and Jackson, and get back a clear error category (transient / permanent / auth-required) that tells them what to do next.

## Requirements

### Validated

(None yet — ship to validate)

### Active

- [ ] Mail model as records: account ID, from, to, cc, bcc, reply-to, subject, HTML body, optional text body, attachments (`MailAddress`, `Attachment` with `contentId` prepared for inline images, `MailMessage`, `Mail`)
- [ ] Builder `Mail.using(accountId)...build()` with transport-independent validation (recipient, subject, body, readable attachments, no `contentId` in MVP)
- [ ] `TemplateRenderer` with `$$name$$` placeholders (HTML-escaped in HTML, `$$!name$$` raw, subject line-break guard, missing field = `InvalidMailException`)
- [ ] Accounts built in code (`MailAccount`: id, transport, auth, options, defaultFrom) and a `Mailer` holding accounts by ID, with `validate` and `send`
- [ ] Transport SPI (`MailTransport`, `TransportProvider`, `TokenProvider`) with a registry: built-in explicit list plus `ServiceLoader`; external duplicates of a built-in ID fail; unknown ID fails at account build with the list of available IDs
- [ ] Error hierarchy: `MailException` → `TransientMailException` (retryAfter), `PermanentMailException` → `InvalidMailException`, `SenderRejectedException`; `AuthRequiredException`
- [ ] `RecordingTransport` in library test sources, published as a test-jar, registered via `ServiceLoader` as `recording`
- [ ] Entra client-credentials token client on the JDK: secret and certificate (PS256 JWT assertion with `x5t#S256`), PKCS12 convenience loader, in-memory cache with 5-minute margin, single-flight, invalidation for 401 handling, error mapping
- [ ] Graph transport: `POST /users/{from}/sendMail`, JSON body, file attachments base64, `saveToSentItems` option, 3 MB per-file and total limit in `validate`, error mapping table, one retry after 401 with a fresh token
- [ ] Serial-mail CLI (picocli): account.properties, campaign dir, pre-flight check (exit 2), journal + resume, rate limit, transient retry ×3, stop on sender/auth errors (exit 3), dry-run output, counts and exit codes
- [ ] Example campaign with three placeholders, two small generated attachments (PDF, PNG), five `example.org` rows
- [ ] Repo hygiene: parent POM with Central metadata, Maven wrapper, `LICENSE`, `README.md`, `SECURITY.md`, CI workflow (`mvn verify`, Java 17), `CLAUDE.md` with spec §11 rules
- [ ] Docs: `docs/entra-setup.md` (admin guide, RBAC for Applications recommended), `docs/architecture.md`, README quick start
- [ ] `release` Maven profile (sources, Javadoc, GPG, Central publishing plugin); `mvn -Prelease verify -Dgpg.skip` passes without warnings
- [ ] `mvn verify` passes with no network access; live test tagged `live`, enabled with `-Plive`, run by the owner only

### Out of Scope

- SMTP, SES, Gmail transports — later milestones; contracts must accept them without change
- Delegated (user) OAuth, token store, interactive consent — later milestones
- Inline images (CID) — milestone 6, the first after the MVP; `Attachment.contentId` is prepared
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

- Greenfield. The repository holds only `LICENSE` (MIT, Stephan Wald), `.gitignore` and `spec.md`.
- Repository: https://github.com/morisoft-lib/morimail (origin remote corrected from the old StephanWald URL on 2026-11-21). Public from the first commit; git history is permanent.
- Published artifacts: `de.morisoft:morimail-parent` and `de.morisoft:morimail`. The sample and the test-jar are not published. Versions `0.1.0-SNAPSHOT` → first release `0.1.0`.
- Microsoft docs to read before coding against an endpoint: Graph `user: sendMail`, large attachments, Entra client-credentials flow, certificate credentials (PS256, `x5t#S256`, not the older RS256/SHA-1 copy), app-only access, Exchange RBAC for Applications.
- Spec §6.3's error table is a starting point. Verify it against real responses after the Graph phase; the owner runs the live test.
- The default rate of 20 mails per minute is a guess. Check Exchange Online per-mailbox limits after the Graph phase.
- The `.gitignore` ignores `*.jar`, which would exclude `.mvn/wrapper/maven-wrapper.jar`. Handle this when adding the Maven wrapper.

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
| No MSAL4J, no Graph SDK, no JWT library | REST calls plus JDK crypto are enough; keeps the jar to Jackson only | — Pending |
| Sample CLI built before the Graph transport | Exercises the contracts against `RecordingTransport` first, so contract mistakes surface early | — Pending |
| HTML only when both bodies are given on Graph (log once at INFO) | JSON sendMail carries one body; MIME later | — Pending |
| Attachments limited to under 3 MB per file and in total | Upload session needs draft flow and `Mail.ReadWrite` | — Pending |
| Recommend Exchange RBAC for Applications over tenant-wide Entra consent | Scopes sending to chosen mailboxes; the Entra grant must be removed or it overrides | — Pending |
| Planning docs (`.planning/`) committed to the public repo | Owner's choice at initialization | — Pending |
| Roadmap phases derived by the roadmapper rather than mirrored 1:1 from spec milestones | Owner's choice. The spec's order constraints (sample before Graph, stop after Graph/Entra) still apply | — Pending |
| `LICENSE` holder Stephan Wald, `SECURITY.md` contact stephan@morisoft.de | Spec defaults; confirm at the Graph checkpoint | — Pending |
| Keep Claude Code's `Co-Authored-By` trailer | Spec §12 default; the owner may switch it off | — Pending |

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
*Last updated: 2026-11-21 after initialization*
