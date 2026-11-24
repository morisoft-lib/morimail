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

## Milestone: v0.2.0 — Inline Images

**Shipped:** 2026-11-24 (owner released 0.2.0 on 2026-11-23; Phase 9 ships with 0.3.0)
**Phases:** 5 | **Plans:** 14 | **Tasks:** 19 | **Commits:** 242 (2026-11-22 → 2026-11-24)

### What Was Built
- Inline images in the core: `.inline(path, contentId)`, with two-way `cid:` checks, content-ID rules and an image-type check in `build()`, recorded by `recording`.
- Graph sends them as inline file attachments (`isInline` plus a bare `contentId`) within the existing 3 MiB / 4 MiB limits; confirmed live by the owner.
- The sample's optional `inline/` campaign directory: pre-flight checks, dry run with relative image paths, and a generated logo in the example campaign.
- New published module `morimail-html`: `HtmlImages.embed` (local, opt-in `http(s)` and `data:` sources) and `toDataUris` for a storable form.
- Docs for all of it, a version-neutral `docs/release.md` for three published artifacts with a SNAPSHOT guard, then a tech-debt phase (cid: ends at `<`, pre-flight validates every row).

### What Worked
- The owner's live inline test after Phase 5 did not block Phases 6 and 7, which ran against `recording`. The live result confirmed the bare `contentId` shape the docs allowed.
- Copying the `cid:` pattern into the sample and the helper, each pinned equal to the core's by a test, kept the rule in one place without a new dependency between modules.
- Early Temurin 17 CI round-trips per phase (the v0.1.0 lesson) caught nothing new, but removed the JDK 17 doubt each time.
- Re-running the milestone audit after each cleanup gave a clear "nothing blocks archiving" signal.

### What Was Inefficient
- The helper module's dependency scope was decided twice: `provided` in planning, reversed to compile scope in UAT (G-07-2).
- The release doc let a SNAPSHOT deploy reach Central's snapshots repository (the 401 in Phase 8 UAT, G-08-2); the guard came only after the owner hit it.
- A whole tech-debt phase (9) plus four quick tasks after the docs phase, mostly for review infos. Verification reports went stale again, so the close needed an override as in v0.1.0.

### Patterns Established
- A rule shared by several modules is copied, not shared through a new API, and each copy is pinned by an agreement test.
- Opt-in for anything that touches the network in a helper (`http(s)` downloads only with a work directory, a 30 s budget and a 3 MiB cap).
- Release docs chain deploy commands behind a guard instead of relying on the reader.

### Key Lessons
1. Settle a new module's dependency scope (and what its published POM says) during discussion, with the consumer's view, not in UAT.
2. Rehearse the release doc's deploy step with the real version shape (SNAPSHOT and release) before the owner runs it.
3. Collect review infos into one triage pass per milestone instead of quick task by quick task; staleness of earlier verifications is then expected and recorded once.

### Cost Observations
- Model mix: not tracked
- Sessions: not tracked
- Notable: 14 plans over about 2 days; plans took 3–22 minutes, with the inline-directory tracer (06-01) the longest.

---

## Cross-Milestone Trends

### Process Evolution

| Milestone | Sessions | Phases | Key Change |
|-----------|----------|--------|------------|
| v0.1.0 | — | 4 | Vertical slices with a hard stop for the owner's live test |
| v0.2.0 | — | 5 | Non-blocking owner live checkpoint; a dedicated tech-debt phase before the close |

### Cumulative Quality

| Milestone | Tests | Coverage | Zero-Dep Additions |
|-----------|-------|----------|-------------------|
| v0.1.0 | 312 `@Test` methods, all green offline (`./mvnw -o verify`) | not measured | Jackson is the only runtime dependency |
| v0.2.0 | 427 `@Test` methods, all green offline; Temurin 17 CI green | not measured | No new dependency; `morimail-html` depends only on `morimail` |

### Top Lessons (Verified Across Milestones)

1. Plan an early CI round-trip on the target JDK when the dev machine lacks it (v0.1.0, held in v0.2.0).
2. Cross-phase edits make earlier VERIFICATION reports stale; expect it and record the override at the close (v0.1.0 and v0.2.0).
3. A live check by the owner right after the first transport slice settles the wire format early (PS256 in v0.1.0, inline `contentId` in v0.2.0).
