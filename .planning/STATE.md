---
gsd_state_version: "1.0"
current_phase: 02
current_phase_name: Serial-Mail Sample
status: verifying
stopped_at: Completed 02-04-PLAN.md
last_updated: "2026-11-21T18:08:25.126Z"
last_activity: 2026-11-21
last_activity_desc: Phase 02 execution started
state_head: 09f14b6eba71ec900a12388d3cd1356fc679c1db
progress:
  total_phases: 4
  completed_phases: 1
  total_plans: 11
  completed_plans: 11
  percent: 25
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-11-21)

**Core value:** A consumer can send one templated mail with attachments through Microsoft Graph as an Entra app, using only the JDK and Jackson, and get back a clear error category (transient / permanent / auth-required) that tells them what to do next.
**Current focus:** Phase 02 — Serial-Mail Sample

## Current Position

Phase: 02 (Serial-Mail Sample) — EXECUTING
Plan: 4 of 4
Status: Phase complete — ready for verification
Last activity: 2026-11-21 — Phase 02 execution started

Progress: [███░░░░░░░] 25%

## Performance Metrics

**Velocity:**
- Total plans completed: 7
- Average duration: -
- Total execution time: 0.0 hours

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| 01 | 7 | - | - |

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

### Pending Todos

None yet.

### Blockers/Concerns

- [Phase 1]: GitHub private vulnerability reporting is disabled on the repository (`enabled: false`), although SECURITY.md routes reports there (T-01-05, non-blocking). The owner enables it in Settings → Code security.
- [Phase 3]: PS256 acceptance, the §6.3 error table, the request-size ceiling (about 4 MB, LOW confidence) and the `from` display name can only be proven by the owner's live test. Research flagged Phase 3 for deeper research.
- [Checkpoint]: The owner still has to confirm the `LICENSE` holder, the `SECURITY.md` contact, the `Co-Authored-By` trailer, the `saveToSentItems` default in the sample, and the 20/min rate default.
- [Phase 4]: The Central `excludeArtifacts` behaviour for the test-jar classifier is unverified. Confirm it with the `-DskipUpload=true` rehearsal.

## Deferred Items

Items acknowledged and deferred at milestone close, most recent first:

| Category | Item | Status | Deferred At | Milestone |
|----------|------|--------|-------------|-----------|
| *(none)* | | | | |

## Session Continuity

Last session: 2026-11-21T18:08:25.017Z
Stopped at: Completed 02-04-PLAN.md
Resume file: None
