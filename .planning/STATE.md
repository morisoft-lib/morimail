---
gsd_state_version: "1.0"
milestone: v0.2.0
milestone_name: Inline Images
current_phase: 5
current_phase_name: Inline Images in Core and Graph
status: planning
stopped_at: Phase 5 context gathered
last_updated: "2026-11-22T21:59:24.170Z"
last_activity: "2026-11-22: Roadmap for v0.2.0 created (Phases 5-7, 15/15 requirements mapped)"
state_head: 27930dd42be3a17a0a354465262091888d8cbb4a
progress:
  total_phases: 4
  completed_phases: 0
  total_plans: 0
  completed_plans: 0
  percent: 0
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-11-22 after starting milestone v0.2.0)

**Core value:** A consumer can send one templated mail with attachments through Microsoft Graph as an Entra app, using only the JDK and Jackson, and get back a clear error category (transient / permanent / auth-required) that tells them what to do next.
**Current focus:** Phase 5: Inline Images in Core and Graph

## Current Position

Phase: 5 of 8 (Inline Images in Core and Graph), first of 4 phases in v0.2.0
Plan: Not started
Status: Ready to plan
Last activity: 2026-11-22: Roadmap for v0.2.0 created (Phases 5-7, 15/15 requirements mapped)

Progress (v0.2.0): [░░░░░░░░░░] 0%

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
v0.1.0 decisions are archived in milestones/v0.1.0-ROADMAP.md, the phase files in milestones/v0.1.0-phases/ and the PROJECT.md Key Decisions table. Ones that still affect future work:

- Release builds use `-pl morimail -am` (D-20); the test-jar is reactor-only and never published.
- Graph failure messages carry the HTTP status, code and request-id only.
- `Attachment.contentId` exists and `build()` rejects it until inline images are built (spec §10.3). Phase 5 replaces that rejection with the INLN-01..05 checks.

v0.2.0 roadmap (2026-11-22):

- Three phases: core and Graph together (Phase 5), then the sample (Phase 6), then docs and release readiness (Phase 7). Core checks come before Graph and the sample, and docs come last.
- Owner checkpoint after Phase 5 for the live inline test. It does not block Phase 6, which runs against `recording`. Phase 7 records the owner's result, or asks the owner whether to record "not yet confirmed live".
- Spec §10.3 says "a transport that cannot send inline images rejects them in `validate`". This needs no code: both in-repo transports handle inline images (Rule 0).

### Pending Todos

None yet.

### Blockers/Concerns

- [Phase 5]: The Graph `fileAttachment` inline property names are unverified (spec §10.3). Read the current Microsoft page before coding and note any difference in `docs/architecture.md`.
- [Phase 5]: Only JDK 21 is installed locally. Content-type resolution for images and the doclint result on the changed records need a Temurin 17 CI round-trip.
- [Phase 1]: GitHub private vulnerability reporting is disabled on the repository (`enabled: false`), although SECURITY.md routes reports there (T-01-05, non-blocking). The owner enables it in Settings → Code security.
- [Phase 3]: Live test done: PS256 accepted, 403/404 rows confirmed, size limits are binary MiB. Still unverified live: `from` display name, shared mailbox as sender, out-of-scope RBAC, `MailboxNotEnabledForRESTAPI`, mailbox 401 (WireMock-covered only).
- [Checkpoint]: Owner confirmed `LICENSE` holder, `SECURITY.md` contact, `Co-Authored-By` trailer and the 20/min rate (Phase 3 UAT). The `saveToSentItems` default in the sample is still unconfirmed.
- [Phase 02]: The journal has no exclusive lock, so two concurrent runs on one campaign can double-send (review IN-06, left out under Rule 0); `attachments/` skips only dot-files, so `Thumbs.db`/`desktop.ini` are still attached. Phase 6 should decide the same rule for `inline/`. Under the CLI-16 non-image check, `Thumbs.db` in `inline/` would fail pre-flight.

### Roadmap Evolution

- Phase 7 added: HTML Image Helper Module (morimail-html, spec §10.4), owner request 2026-11-22; former Phase 7 Docs and Release Readiness renumbered to 8

## Deferred Items

Items acknowledged and deferred at milestone close, most recent first:

| Category | Item | Status | Deferred At | Milestone |
|----------|------|--------|-------------|-----------|
| verification_gaps | Phases 1-4 VERIFICATION.md stale (covered files changed after verification) | passed, stale | 2026-11-22 | v0.1.0 |

## Session Continuity

Last session: 2026-11-22T21:56:41.893Z
Stopped at: Phase 5 context gathered
Resume file: .planning/phases/05-inline-images-in-core-and-graph/05-CONTEXT.md

## Operator Next Steps

- Review and approve the v0.2.0 roadmap (.planning/ROADMAP.md)
- Then plan the first phase with /gsd-plan-phase 5 (or /gsd-discuss-phase 5 first)
