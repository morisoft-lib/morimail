# Project Retrospective

*A living document updated after each milestone. Lessons feed forward into future planning.*

## Milestone: v0.1.0 — MVP

**Shipped:** 2026-11-22
**Phases:** 4 | **Plans:** 22 | **Tasks:** 42 | **Commits:** 255 (2026-11-21 → 2026-11-22)

### What Was Built
- The library core:
  - templated `Mail` builder with strict validation and HTML escaping
  - checked error tree (transient / permanent / sender-rejected / auth-required)
  - transport registry with a built-in list plus `ServiceLoader`
  - `Mailer` and `MailAccount`
- The `serial-mail` CLI sample: pre-flight checks, forced journal with resume, rate limit, retry and exit codes, and a dry run.
- An Entra client-credentials token client on the JDK only: secret, PS256 certificate assertion and PKCS12. Tokens are cached with single flight.
- The built-in Graph `sendMail` transport, with size limits in `validate`, the full error mapping and a single 401 retry.
- Docs: setup guide, architecture page, README quick start and release guide. The owner released 0.1.0 to Maven Central.

### What Worked
- The sample ran against `recording` before any network code existed. The whole send loop was proven with no library change.
- The hard stop for the owner's live test after Phase 3. The live results confirmed PS256, the 403/404 rows and the binary MiB limits. They also caught that Graph's free text names the sender (G-03-2), so it no longer goes into messages.
- The release profile and `-Prelease verify` ran in CI from Phase 1, so doclint strictness surfaced early (G-01-1) and not at release time.
- Hygiene gates and reviews caught leaks before push: a maintainer GPG key id in a debug note was removed by rewriting unpushed history.

### What Was Inefficient
- Only JDK 21 was installed locally. JDK 17 behaviour (doclint on record compact constructors, logger capture) could only be confirmed by the owner's CI runs, and each check needed a push.
- Later phases and post-release commits edited files that earlier VERIFICATION reports cover. All four reports went stale, so the close needed an override.
- Several rounds of review fixes per phase. All four phases have a REVIEW-FIX file, and the `clean()` address cut took two passes (WR-01).

### Patterns Established
- Failure messages hold fixed texts plus codes and ids only. They never echo a value, an address, a token or a body.
- Verify claims against Microsoft docs at implementation time, and record differences in `docs/architecture.md`.
- Release builds are scoped with `-pl morimail -am`, and the test-jar is reactor-only.

### Key Lessons
1. When the dev machine lacks the target JDK, plan an early CI round-trip for each phase that touches Javadoc or runtime-sensitive code.
2. Re-run verification (or accept staleness explicitly) after cross-phase doc and POM edits, before the milestone close.
3. Treat any server-provided text as potentially personal data from the first design, not after a live test.

### Cost Observations
- Model mix: not tracked
- Sessions: not tracked
- Notable: 22 plans over about 2 days. Most plans took 2–25 minutes; the end-to-end wiring plan (03-05) was the longest at 45 minutes.

---

## Cross-Milestone Trends

### Process Evolution

| Milestone | Sessions | Phases | Key Change |
|-----------|----------|--------|------------|
| v0.1.0 | — | 4 | Vertical slices with a hard stop for the owner's live test |

### Cumulative Quality

| Milestone | Tests | Coverage | Zero-Dep Additions |
|-----------|-------|----------|-------------------|
| v0.1.0 | all green offline (`./mvnw -o verify`) | not measured | Jackson is the only runtime dependency |

### Top Lessons (Verified Across Milestones)

1. (Needs a second milestone to cross-validate.)
