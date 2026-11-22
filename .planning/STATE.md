---
gsd_state_version: "1.0"
current_phase: 04
status: completed
stopped_at: Phase 04 complete — all phases complete
last_updated: "2026-11-22T21:08:32.719Z"
last_activity: 2026-11-22
last_activity_desc: Phase 04 complete
state_head: 7069423bf9134647cf317e38b266fe2f7e542087
progress:
  total_phases: 4
  completed_phases: 4
  total_plans: 22
  completed_plans: 22
  percent: 100
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-11-22)

**Core value:** A consumer can send one templated mail with attachments through Microsoft Graph as an Entra app, using only the JDK and Jackson, and get back a clear error category (transient / permanent / auth-required) that tells them what to do next.
**Current focus:** Phase 04 — Docs and Release Readiness

## Current Position

Phase: 04
Plan: Not started
Status: All phases complete
Last activity: 2026-11-22 — Phase 04 complete

Progress: [██████████] 100%

## Performance Metrics

**Velocity:**
- Total plans completed: 22
- Average duration: -
- Total execution time: 0.0 hours

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| 01 | 7 | - | - |
| 02 | 4 | - | - |
| 03 | 6 | - | - |
| 04 | 5 | - | - |

**Recent Trend:**
- Last 5 plans: -
- Trend: -

*Updated after each plan completion*
**Per-Plan Metrics:**

| Plan | Duration | Tasks | Files |
|------|----------|-------|-------|
| Phase 01 P01 | 20min | 2 tasks | 9 files |
| Phase 01 P02 | 5min | 2 tasks | 8 files |
| Phase 01 P03 | 2 min | 2 tasks | 14 files |
| Phase 01 P04 | 12min | 2 tasks | 12 files |
| Phase 01 P05 | 25 min | 3 tasks | 7 files |
| Phase 01 P06 | 5 min | 3 tasks | 10 files |
| Phase 01 P07 | 2 min | 1 tasks | 1 files |
| Phase 02 P01 | 15 min | 3 tasks | 10 files |
| Phase 02 P02 | 12 min | 3 tasks | 5 files |
| Phase 02 P03 | 5 min | 3 tasks | 5 files |
| Phase 02 P04 | 3 min | 2 tasks | 10 files |
| Phase 03 P01 | 25min | 2 tasks | 8 files |
| Phase 03 P02 | 25min | 3 tasks | 6 files |
| Phase 03 P03 | 20 min | 2 tasks | 5 files |
| Phase 03 P04 | 25min | 3 tasks | 2 files |
| Phase 03 P05 | 45min | 3 tasks | 8 files |
| Phase 03 P06 | 8 min | 3 tasks | 7 files |
| Phase 04 P01 | 12 min | 3 tasks | 7 files |
| Phase 04 P02 | 3 min | 1 tasks | 3 files |
| Phase 04 P03 | 6 min | 1 tasks | 1 files |
| Phase 04 P04 | 12 min | 2 tasks | 1 files |
| Phase 04 P05 | 5min | 1 tasks | 1 files |

## Accumulated Context

### Decisions

Decisions are logged in PROJECT.md Key Decisions table.
Recent decisions affecting current work:

- [Roadmap]: Spec milestones 1 and 2 merged into Phase 1 as one vertical slice (build a templated mail, send it via `Mailer` to `recording`). Coarse granularity, 4 phases.
- [Roadmap]: The `release` profile and CI's `-Prelease verify -Dgpg.skip` start in Phase 1. The bundle rehearsal is Phase 4.
- [Roadmap]: Hard STOP after Phase 3 for the owner's live test. Phase 4 starts from its findings; a large code correction becomes a decimal phase inserted after Phase 3.
- [Init]: Jackson 2.23.x via `jackson-bom`, JUnit 6.x, `changeit` test keystore, reactor-only test-jar, Graph `baseUrl` option, comma-only CSV, at-least-once documented, and a `freshAccessToken` default on `TokenProvider`.
- [Phase 01]: Release builds use -pl morimail -am (D-20): an unattached test-jar is unresolvable in a packaging full-reactor build
- [Phase 01]: wiremock and picocli are version-managed in the parent but declared by no module until Phase 3 / Phase 2
- [Phase 01]: Spec copied byte for byte to docs/spec.md and root copy removed after the committed blob hash was verified; deviations (freshAccessToken, Map<String, ?>, unchecked config errors, JUnit 6, reactor-only test-jar) recorded in docs/architecture.md
- [Phase 01]: Mail.Builder.build() reports one first problem naming field and index; MailAddress.invalidReason returns fixed reason texts that never contain the address
- [Phase 01]: Exceptions are documented as not immutable (Throwable has mutable methods); state fixed at construction and safe to pass between threads once thrown
- [Phase 01]: Mailer.validate is declared throws PermanentMailException; no-sender and transport-validate failures are the subclass InvalidMailException
- [Phase 01]: ImportRulesTest scans comment-stripped source text of core depth-1 files; java.* only, javax rejected, report forms imports/references/mentions
- [Phase 01]: template(t, data) copies the data map with new HashMap at call time (Map.copyOf rejects present null values)
- [Phase 01]: Attachment checks and content-type resolution happen in build(), so repeated build() calls re-check the files
- [Phase 01]: Null Path or Attachment passed to attach throws NullPointerException at call time; a null Attachment.file is reported by build()
- [Phase 01]: RecordingTransport checks failFor, then the failNext queue, then records; registry wraps ServiceConfigurationError with a fixed message and keeps the error as cause
- [Phase 01]: 01-07: G-01-1 fixed by adding the nine @param tags to the MailMessage compact constructor Javadoc (not by dropping the comment); doclint all and failOnWarnings stay; Temurin 17 CI run after the owner pushes closes the gap
- [Phase 01]: G-01-1 closed by Temurin 17 CI run 44912211934 (all three steps green, no Javadoc warning); UAT complete, SECURITY.md threats_open 0, phase complete 2026-11-21
- [Phase 02]: A header-only recipients.csv yields no Campaign, so the command never indexes an empty row list
- [Phase 02]: Jackson stays transitive through morimail; the sample POM gains only picocli
- [Phase 02]: A broken header (semicolon, no email column, repeated column) still runs the placeholder check but not the row analysis
- [Phase 02]: Duplicate addresses compare with toLowerCase(Locale.ROOT); blank addresses are left to build()
- [Phase 02]: 02-03: the row behind an exit-3 stop counts as failed and has a failed journal line; later rows are not counted
- [Phase 02]: 02-03: a damaged journal line is warned about and its row sent again (at-least-once); never a pre-flight stop
- [Phase 02]: 02-03: pre-flight checks every CSV row even with --limit
- [Phase 02]: Sample runnable jar uses a jar manifest classpath over target/lib (copy-dependencies, runtime scope); no shade plugin
- [Phase 02]: Example attachments are generated once outside the repository; only the committed bytes are the source of truth
- [Phase 02]: UAT complete (rendered pages, PDF, PNG checked), SECURITY.md threats_open 0, verification re-run after code-review fixes passed; phase complete 2026-11-22
- [Phase 03]: 03-01: tracer gaps stay as missing branches; unusable 200 token response raises AuthRequiredException until 03-02 fixes the rule
- [Phase 03]: Unusable 2xx Entra token response (no access_token or non-positive expires_in) is AuthRequiredException
- [Phase 03]: expires_in is clamped to one year so a hostile value cannot overflow the expiry arithmetic
- [Phase 03]: 03-03: Graph request body is built before the token is requested, so an unreadable attachment costs no token request and no POST
- [Phase 03]: 03-03: Request-size estimate measures the body with the same ObjectMapper send uses, so it bounds the posted bytes by construction; an emoji counts 12 bytes (JSON escape pair), not 4
- [Phase 03]: The graph transport retries a 401 once with a fresh token and makes no other retry; a second 401 is SenderRejectedException only when error.code contains mailbox
- [Phase 03]: Graph failure messages carry only HTTP status, cleaned error.code, request-id and cleaned error.message; the raw body, request body and tokens never enter a message
- [Phase 03]: 03-05: No new account.properties key and no defaultFrom on the sample account; key-store path and load failures are fixed texts naming keys only
- [Phase 03]: 03-05: CI network-free step uses unshare --net with setpriv back to the runner user; not rehearsable in this workspace
- [Phase 03]: 03-06: Graph failure messages carry HTTP status, code and request-id only; Graph's error.message is dropped (G-03-2)
- [Phase 04]: 04-01: server text is cut back to a word boundary before the address pattern runs; a single word over 1000 characters gives '-' (WR-01)
- [Phase 04]: 04-01: the error mapping, the 3 MiB and 4 MiB constants and the sample --rate default are unchanged; live results matched them (D-01)
- [Phase 04]: 04-02: the guide's scope filter example uses Alias, because the recipient-filter page says to use EmailAddresses and not PrimarySmtpAddress
- [Phase 04]: 04-02: every open item lives in the closing section Not yet verified live; the steps carry only the two allowed caveat lines (D-04, D-06)
- [Phase 04]: 04-03: README quick start has three catches, one per category (D-08), with sender-rejected named in the permanent catch; checked once with javac, no mirror test (D-09)
- [Phase 04]: 04-04: the rehearsal bundle at 0.1.0 holds only morimail-parent and morimail; excludeArtifacts is not needed
- [Phase 04]: 04-05: docs/release.md step 5 warms up gpg-agent in a real terminal before each -B deploy; no POM change (G-04-3)

### Pending Todos

None yet.

### Blockers/Concerns

- [Phase 1]: GitHub private vulnerability reporting is disabled on the repository (`enabled: false`), although SECURITY.md routes reports there (T-01-05, non-blocking). The owner enables it in Settings → Code security.
- [Phase 3]: Live test done: PS256 accepted, 403/404 rows confirmed, size limits are binary MiB. Still unverified live: `from` display name, shared mailbox as sender, out-of-scope RBAC, `MailboxNotEnabledForRESTAPI`, mailbox 401 (WireMock-covered only).
- [Phase 3]: Review WR-01 and IN-01 fixed in 04-01 (word-boundary cut plus one straddle test per class).
- [Checkpoint]: Owner confirmed `LICENSE` holder, `SECURITY.md` contact, `Co-Authored-By` trailer and the 20/min rate (Phase 3 UAT). The `saveToSentItems` default in the sample is still unconfirmed.
- [Phase 02]: The journal has no exclusive lock, so two concurrent runs on one campaign can double-send (review IN-06, left out under Rule 0); `attachments/` skips only dot-files, so `Thumbs.db`/`desktop.ini` are still attached.
- [Phase 4]: The Central `excludeArtifacts` behaviour for the test-jar classifier is unverified. Confirm it with the `-DskipUpload=true` rehearsal.

## Deferred Items

Items acknowledged and deferred at milestone close, most recent first:

| Category | Item | Status | Deferred At | Milestone |
|----------|------|--------|-------------|-----------|
| *(none)* | | | | |

## Session Continuity

Last session: 2026-11-22T20:45:45.154Z
Stopped at: Phase 04 complete — all phases complete
Resume file: None
