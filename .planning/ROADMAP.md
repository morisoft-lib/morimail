# Roadmap: morimail

## Overview

morimail goes from an almost empty repository to a 0.1.0 release candidate in four vertical slices. Phase 1 delivers a working library core. A developer builds a templated mail and sends it through a `Mailer` account to the `recording` transport, on a build that is CI-checked and release-shaped from the first commit. Phase 2 puts the first real consumer on those contracts: the serial-mail CLI, still running against `recording`, so contract mistakes surface before any network code exists. Phase 3 adds the Entra token client and the Graph transport as a built-in, then runs the sample end to end against WireMock. It ends at a hard stop for the owner's live test. Phase 4 starts from the owner's live-test findings and finishes the setup guide, architecture page, README and release rehearsal.

## Phases

**Phase Numbering:**
- Integer phases (1, 2, 3, 4): Planned milestone work
- Decimal phases (for example 3.1): Urgent insertions (marked with INSERTED), such as a larger error-mapping correction after the live test

Decimal phases appear between their surrounding integers in numeric order.

- [x] **Phase 1: Library Core** - Templated mail built, validated and sent through a `Mailer` account to the `recording` transport, on a CI-checked, release-shaped build (completed 2026-11-21)
- [x] **Phase 2: Serial-Mail Sample** - The `serial-mail` CLI dry-runs and sends a CSV campaign against `recording`, with pre-flight, journal, resume, rate limit and exit codes (completed 2026-11-22)
- [ ] **Phase 3: Graph and Entra** - Entra client-credentials tokens and the built-in Graph transport, wired through the registry and run end to end against WireMock; ends at the owner's live-test STOP
- [ ] **Phase 4: Docs and Release Readiness** - Live-test findings applied, then the setup guide, architecture page, README quick start and a release rehearsal the owner can publish from

> **HARD STOP between Phase 3 and Phase 4 (spec §10.2, milestone 4).** When Phase 3 is verified and committed, report to the owner and stop. The owner runs the `live` test and feeds sanitised real responses back. Phase 4 does not start before that report. See the checkpoint block under Phase 3.

## Phase Gates

These apply to every phase. They come from the binding spec (§10.2, §11, §13.4).

- A phase is done only when `./mvnw verify` is green with no network access and the work is committed before the next phase starts. Author and committer of every commit is Stephan Wald <stephan@morisoft.de>.
  - Gate note (Phase 1, owner decision D-18 in `phases/01-library-core/01-CONTEXT.md`): "no network access" is proven in Phase 1 by a warm-cache `./mvnw -o verify` plus the source check that no source uses `java.net.` networking. The network-disabled proof (for example a CI job without network) is REPO-08 in Phase 3.
- Javadoc stays doclint-clean (CI's `mvn -Prelease verify -Dgpg.skip -pl morimail -am` stays green; the release build is scoped to the published modules per D-20 in `phases/01-library-core/01-CONTEXT.md`), and every new public type states its thread safety.
- No file, fixture or commit contains a secret, tenant ID, real address or internal host. Use only `example.com`/`example.org` and zero GUIDs.
- Never send real mail, publish, deploy or tag. The owner runs the live test and the release.
- The order is fixed. The sample runs against `recording` (Phase 2) before any Entra or Graph code exists (Phase 3).

## Phase Details

### Phase 1: Library Core
**Goal:** As a Java developer, I want to build a templated mail and send it through a Mailer account, so that the core contracts are proven before any real transport.
**Mode:** mvp
**Depends on**: Nothing (first phase)
**Requirements**: REPO-01, REPO-02, REPO-03, REPO-04, REPO-05, REPO-06, REPO-07, REPO-09, REPO-10, REPO-11, REPO-12, MODEL-01, MODEL-02, MODEL-03, MODEL-04, MODEL-05, TMPL-01, TMPL-02, TMPL-03, TMPL-04, CORE-01, CORE-02, CORE-03, CORE-04, CORE-05, CORE-06, CORE-07, ERR-01, ERR-02
**Success Criteria** (what must be TRUE):
  1. `./mvnw verify` passes offline on Java 17 (D-18: proven by a warm-cache `./mvnw -o verify` plus the `java.net.` source check; `-o` is not network isolation, and the network-off proof is REPO-08 in Phase 3). The CI workflow runs `mvn verify` and `mvn -Prelease verify -Dgpg.skip -pl morimail -am` (D-20: scoped to the published modules) green on push and pull request, with doclint-clean Javadoc that states thread safety on every public type.
  2. A developer can build a mail with `Mail.using(id)...template(t, data).attach(p).build()`. HTML values are escaped, `$$!name$$` inserts raw, line breaks in a subject value become a space, and `$` or `\` in data is inserted literally.
  3. `build()` raises `InvalidMailException`, naming the problem, for any of: a missing placeholder field; no recipient, subject or body; an unreadable attachment; a set `contentId`; CR/LF in an address, display name or subject; an address without exactly one `@`.
  4. A developer can build `MailAccount.builder(id).transport("recording")`, and `Mailer.of(account).send(mail)` records the message with `defaultFrom` applied. Scripted failures surface as transient, permanent, sender-rejected or auth-required. An unknown account raises `PermanentMailException`. An unknown transport ID fails and lists the available IDs. A duplicate external ID fails when the registry loads.
  5. The repository is safe to publish from its first commit. `LICENSE`, the `README.md` stub (pre-1.0, coordinates, accepted-not-delivered note), `SECURITY.md`, `CLAUDE.md` and `docs/spec.md` are in place. No file holds a secret or real address, and the import-rule test fails the build if the core imports anything but the JDK.
**Plans:** 7/7 plans complete (wave 1: 01-01; wave 2: 01-02, 01-03; wave 3: 01-04; wave 4: 01-05, 01-06; wave 5: 01-07 gap closure)

Plans:
**Wave 1**
- [x] 01-01-PLAN.md — Walking skeleton, part 1: reactor, wrapper, release profile and CI, green before any Java source

**Wave 2** *(blocked on Wave 1 completion)*
- [x] 01-02-PLAN.md — Publish-safe repository: README stub, SECURITY.md, root CLAUDE.md, spec moved to docs/spec.md, deviations in docs/architecture.md, hygiene gates
- [x] 01-03-PLAN.md — Walking skeleton, part 2: core records, plain `Mail.Builder`, checked error tree, SPI interfaces (`MailTransport`, `TransportProvider`, `TokenProvider`)

**Wave 3** *(blocked on Wave 2 completion)*
- [x] 01-04-PLAN.md — Walking skeleton, part 3: registry, `MailAccount`, `Mailer`, `recording` test-jar fixture, sample consumer test (end-to-end gate `SendThroughRecordingTest`), import-rule and thread-safety guards

**Wave 4** *(blocked on Wave 3 completion)*
- [x] 01-05-PLAN.md — Templated mail with attachments: MailTemplate, TemplateRenderer, full builder surface and build() validation
- [x] 01-06-PLAN.md — Failure categories and configuration errors: scripted failures on `recording`, registry duplicate rules, Mailer/MailAccount contracts, concurrency

**Wave 5** *(gap closure, blocked on Wave 4 completion)*
- [x] 01-07-PLAN.md — Gap G-01-1: document the `MailMessage` compact constructor parameters so JDK 17 doclint passes in the CI release build (comment-only; confirmed by the owner's Temurin 17 CI run)
**UI hint**: no

Scope notes:
- `BuiltInTransports` starts as an empty list. Its single Graph line is added in Phase 3.
- The import-rule test covers all three rules now. The `graph` and `entra` rules are trivially met until those packages exist in Phase 3.
- The `release` profile exists from this phase because CI runs it. Bundle-content inspection is Phase 4 (DOCS-04).
- `TokenProvider.freshAccessToken` is frozen here (CORE-02) because the sample and Graph both code against it.

### Phase 2: Serial-Mail Sample
**Goal:** As a campaign sender, I want to dry-run and send a CSV serial mail with a journal, so that a rerun never mails a recipient twice.
**Mode:** mvp
**Depends on**: Phase 1
**Requirements**: CLI-01, CLI-02, CLI-03, CLI-04, CLI-05, CLI-06, CLI-07, CLI-08, CLI-09, CLI-10, CLI-11, CLI-12, CLI-13
**Success Criteria** (what must be TRUE):
  1. `serial-mail send --dry-run` on the shipped example campaign writes 5 rendered mails to `out/` (`.html`, `.txt`, `.subject.txt`, sanitised file names) and lists the attachments. It needs no secret env vars and touches neither the network nor the journal. The campaign has three placeholders, a PDF and a PNG each under 100 KB, and five `example.org` rows.
  2. A broken campaign is rejected before the first send, with all problems reported together and exit code 2. Problems include a placeholder with no CSV column, an invalid email, a `;`-delimited header, and a first row that fails `mailer.validate`.
  3. Run against `recording`, each row is sent once, with one progress line per recipient and one forced journal line per attempt. Rerunning the same campaign sends nothing and counts every row as skipped.
  4. A transient failure waits `retryAfter` (capped) or 30 s and is retried up to 3 times, then recorded, and the run continues. A sender-rejected or auth-required failure stops the run at once with exit code 3. The run ends by printing accepted, skipped and failed counts, and exits 0 only if nothing failed.
  5. Sends are spaced by `--rate` and skipped rows don't wait. `--limit`, `--campaign-id` and `--journal` behave as specified. `account.properties` and the campaign files are read as UTF-8, secrets come from env vars only, and `./mvnw verify` stays green offline.
**Plans:** 4/4 plans complete (wave 1: 02-01; wave 2: 02-02, 02-03, 02-04)

Plans:
**Wave 1**
- [x] 02-01-PLAN.md — Send a campaign once through `recording` with a forced journal (a rerun sends nothing), dry-run into rendered files (row numbers padded to at least two digits), and scrub the phase planning files

**Wave 2** *(blocked on Wave 1 completion; 02-02, 02-03 and 02-04 share no file)*
- [x] 02-02-PLAN.md — Pre-flight reports every problem of a broken campaign at once (placeholders vs columns, `;` header, duplicates, CSV edge cases, credential form, no echoed values), exit 2
- [x] 02-03-PLAN.md — Transient retry with capped waits, stop with exit 3 on sender/auth errors, `--rate`/`--limit`/`--campaign-id`/`--journal` and argument checks, damaged-line warnings and stop on a failed journal write
- [x] 02-04-PLAN.md — The shipped example campaign (three placeholders, five `example.org` rows, PDF and PNG without metadata) and the runnable jar that dry-runs it on a fresh clone
**UI hint**: no

Scope notes:
- This phase parses `account.properties` and builds a `Mailer` through the CLI's seam (CLI-12). Building the real Entra `TokenProvider` and `graph` account from it is finished in Phase 3 (CLI-14).
- No Entra or Graph code is written in this phase (spec §10.2 ordering).

### Phase 3: Graph and Entra
**Goal:** As a Java developer, I want to send mail through Microsoft Graph as an Entra app, so that each failure says whether to retry, drop the mail or fix the account.
**Mode:** mvp
**Depends on**: Phase 2
**Requirements**: ENTRA-01, ENTRA-02, ENTRA-03, ENTRA-04, ENTRA-05, ENTRA-06, ENTRA-07, ENTRA-08, ENTRA-09, GRAPH-01, GRAPH-02, GRAPH-03, GRAPH-04, GRAPH-05, GRAPH-06, GRAPH-07, GRAPH-08, GRAPH-09, TEST-01, TEST-02, TEST-03, CLI-14, REPO-08
**Success Criteria** (what must be TRUE):
  1. `EntraClientCredentials` gets a token from a WireMock token endpoint in three ways: with a secret, with a key and certificate, and with the checked-in test PKCS12. The certificate assertion decodes to the specified PS256 header (with `x5t#S256`) and claims, and its signature verifies independently with the certificate's public key.
  2. The token is reused until 5 minutes before expiry and fetched again after that. Concurrent callers cause one token request, and `freshAccessToken` drops only a matching token. A 4xx response maps to auth-required with `error` and `error_description`; 5xx, 429, network and timeout errors map to transient. No secret, assertion or token is ever logged.
  3. A `graph` account built through the registry, with no `ServiceLoader` entry, sends the expected `POST /users/{from}/sendMail` to WireMock. The request has a percent-encoded from, a bearer token, recipients, an HTML-or-text body and base64 file attachments, and carries `saveToSentItems` only when it is false. A 202 returns normally. Attachments at or above 3 MB (per file or in total) and unknown options are rejected.
  4. Every row of the error table maps to its documented exception. The message carries Graph's `error.code`, message and `request-id`, never tokens or bodies. A 401 triggers exactly one retry, carrying a fresh token, before it is mapped.
  5. `serial-mail send` runs the example campaign end to end against WireMock for both the token endpoint and Graph, and `./mvnw verify` passes offline. A `live`-tagged test is skipped by default and is ready for the owner to run with `-Plive` and environment variables.
**Plans:** 5/5 plans executed (wave 1: 03-01; wave 2: 03-02, 03-03; wave 3: 03-04; wave 4: 03-05)

Plans:
**Wave 1**
- [x] 03-01-PLAN.md — Tracer: a secret-authenticated Entra app sends one text mail through a registry-built `graph` account to WireMock (202), with `graph` registered as the built-in, timeouts and no redirects from the first HTTP call

**Wave 2** *(blocked on Wave 1 completion; 03-02 and 03-03 share no file)*
- [x] 03-02-PLAN.md — Entra: certificate flow from the checked-in throwaway PKCS12 with a PS256 assertion that verifies independently, cache until 5 min before expiry with single flight and compare-and-drop, full token error mapping, no secret in any log or message
- [x] 03-03-PLAN.md — Graph request: full sendMail JSON with attachments and `saveToSentItems`, option rules, 3 MiB and 4 MiB limits in `validate`

**Wave 3** *(blocked on Wave 2 completion; 03-04 changes GraphTransport.java after 03-03)*
- [x] 03-04-PLAN.md — Graph errors: the single 401 retry with a fresh token, every error-table row and the status-class rule with Retry-After, messages with code and request-id only, cleaned and free of tokens and bodies

**Wave 4** *(blocked on Wave 3 completion)*
- [x] 03-05-PLAN.md — Sample: `serial-mail send` end to end against WireMock (once, rerun sends nothing, 403 stops with exit 3), D-14 pre-flight naming keys only, the owner's `live` test, the network-free CI step, and the phase's deviations in `docs/architecture.md`; then the STOP
**UI hint**: no

Scope notes:
- Entra (ENTRA-*, TEST-01, TEST-02) and Graph (GRAPH-*, TEST-03) depend only on Phase 1 contracts, not on each other, so they can be planned as parallel waves. The wiring step (the Graph line in `BuiltInTransports`, the registry test for the built-in `graph`, `AccountFile` building the real account, CLI-14 E2E, the `live` test) comes last.
- Research flag: before coding, read the current Microsoft pages (sendMail, certificate credentials, client-credentials flow). PS256 acceptance and the §6.3 table can only be proven by the live test, so do not code a speculative RS256 fallback.

**STOP checkpoint (hard stop after this phase, spec §10.2 milestone 4):** When Phase 3 is verified and committed, report to the owner and stop. Claude does not run the live test and sends no real mail. The owner runs the `live` test with `-Plive` and feeds back sanitised results on the following:
- whether Entra accepts the PS256 certificate assertion (and any clock-skew issue)
- the real Graph responses behind each §6.3 row (403 vs 404 vs `MailboxNotEnabledForRESTAPI`, a 401 after a fresh token)
- the attachment and request-size boundary: a library send at about 2.9 MB (the live test's opt-in probe); about 3.5 MB only if the owner wants it, as a hand-made request outside the library, because `validate` rejects it before sending (deferred in `phases/03-graph-and-entra/03-CONTEXT.md`)
- the RBAC for Applications scope (a mailbox outside the scope is refused)
- whether the `from` display name works, and whether a shared mailbox works
- the Exchange per-mailbox limits behind the `--rate 20` default
- the open §12 decisions: the `LICENSE` holder, the `SECURITY.md` contact, the `Co-Authored-By` trailer

Phase 4 starts only after this report. If the corrections to code are substantial, insert them as Phase 3.1 (`/gsd-phase --insert`) before Phase 4.

### Phase 4: Docs and Release Readiness
**Goal:** As the owner, I want the setup guide, final docs and a release rehearsal shaped by the live test, so that I can set up my tenant and publish 0.1.0.
**Mode:** mvp
**Depends on**: Phase 3 and the owner's live-test report (STOP checkpoint)
**Requirements**: DOCS-01, DOCS-02, DOCS-03, DOCS-04
**Success Criteria** (what must be TRUE):
  1. The owner's sanitised live-test findings are applied. The Graph error mapping, attachment boundary and default rate in code, `docs/spec.md` §6.3 and `docs/architecture.md` all match the real responses, and every error-table row is still tested.
  2. The owner can follow `docs/entra-setup.md` from the app registration and certificate, through Exchange RBAC for Applications (union trap, 30 min to 2 h delay, Enterprise-application IDs, Organization Management), to a dry run and a `--limit 1` send. The owner then sends the example campaign to test addresses, and a second run sends nothing.
  3. `docs/architecture.md` summarises the contracts on one page and records every deviation: the `freshAccessToken` default, `baseUrl`, unchecked registry exceptions, JUnit 6, the `changeit` keystore, the reactor-only test-jar, and any differences from the Microsoft docs.
  4. `README.md` explains what morimail is and what it is not (Exchange limits; not a bulk mailer). It covers the pre-1.0 status, the coordinates, a quick start, the at-least-once and accepted-not-delivered semantics, and the test keystore.
  5. `mvn -Prelease verify -Dgpg.skip -pl morimail -am` (D-20: scoped to the published modules) builds the sources and Javadoc jars without warnings. A `-DskipUpload=true` rehearsal bundle contains only `morimail-parent` and `morimail` (no `-tests.jar`, no sample), and the owner's release steps are listed.
**Plans**: TBD
**UI hint**: no

Scope notes:
- Criterion 1 is the checkpoint feedback loop. It has no requirement of its own and corrects GRAPH-06 and the related numbers delivered in Phase 3.
- Research flag: the Central `excludeArtifacts` behaviour for the test-jar classifier is unverified. The rehearsal bundle settles it.

## Coverage

69 of 69 v1 requirements mapped, each to exactly one phase:

| Phase | Requirements | Count |
|-------|--------------|-------|
| 1. Library Core | REPO-01..07, REPO-09..12, MODEL-01..05, TMPL-01..04, CORE-01..07, ERR-01..02 | 29 |
| 2. Serial-Mail Sample | CLI-01..13 | 13 |
| 3. Graph and Entra | ENTRA-01..09, GRAPH-01..09, TEST-01..03, CLI-14, REPO-08 | 23 |
| 4. Docs and Release Readiness | DOCS-01..04 | 4 |

## Progress

**Execution Order:**
Phases execute in numeric order: 1 → 2 → 3 → STOP (owner live test) → 4

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. Library Core | 7/7 | Complete    | 2026-11-21 |
| 2. Serial-Mail Sample | 4/4 | Complete    | 2026-11-22 |
| 3. Graph and Entra | 5/5 | In Progress | - |
| 4. Docs and Release Readiness | 0/0 | Not started | - |
