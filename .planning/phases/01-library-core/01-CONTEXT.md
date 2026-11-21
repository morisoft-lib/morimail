# Phase 1: Library Core - Context

**Gathered:** 2026-11-21
**Status:** Ready for planning

<domain>
## Phase Boundary

A Java developer builds a templated mail (`Mail.using(id)...template(t, data).attach(p).build()`), and `Mailer.of(account).send(mail)` sends it through a `MailAccount` resolved by the registry to the test-only `recording` transport. All of this runs on a CI-checked, release-shaped Maven reactor (parent + library + empty-ish sample module, wrapper, `release` profile, doclint-clean Javadoc), and the repository is publish-safe from its first commit.

Requirements: REPO-01..07, REPO-09..12, MODEL-01..05, TMPL-01..04, CORE-01..07, ERR-01..02 (see `.planning/REQUIREMENTS.md`). Not in this phase: Graph, Entra, the sample CLI logic, the test keystore, the live test (REPO-08 is Phase 3), and bundle inspection (Phase 4). `BuiltInTransports` starts as an empty list.

</domain>

<decisions>
## Implementation Decisions

### Mail builder input shapes
- **D-01:** `to`/`cc`/`bcc`/`replyTo`/`from` each have exactly two forms: bare `String` email and `MailAddress`. There is **no** `(String email, String displayName)` overload; a display name is given only through `MailAddress`. `MailAddress` (record `MailAddress(String email, String displayName)`) gains static factories `MailAddress.of(String email)` and `MailAddress.of(String email, String displayName)` for inline use in the builder chain. — **Reversibility:** one-way — after the 0.1.0 Central release, removing a public overload breaks consumers (adding the two-string form later would not). *(Revised 2026-11-21 during plan-phase: research reproduced that `to("a@example.org", "b@example.org")` silently bound to `(email, displayName)`; the owner chose to drop that overload instead of guarding it.)*
- **D-02:** `to`/`cc`/`bcc` take varargs for both forms (`to(String...)`, `to(MailAddress...)`). Repeated calls append to the list.
- **D-02a:** `template(MailTemplate t, Map<String, ?> data)` takes `Map<String, ?>`, not the spec's `Map<String, Object>`, so typed maps such as a CSV row `Map<String,String>` compile. Recorded as a deliberate spec deviation in `docs/architecture.md`. — **Reversibility:** one-way (public signature). *(Owner decision 2026-11-21.)*
- **D-02b:** The pre-existing first commit `8c9b082` (different author address) is left as is; no history rewrite. Hygiene checks treat it as the one known exception, and the owner decides before publishing. *(Owner decision 2026-11-21.)*
- **D-02c:** The test engine is JUnit Jupiter 6.1.3 via `junit-bom`, not the spec's "JUnit 5" (§3); same `org.junit.jupiter` API, Java 17 baseline. Recorded as a deliberate spec deviation in `docs/architecture.md`. — **Reversibility:** reversible (test scope only). *(Owner decision 2026-11-21.)*
- **D-03:** `from()` and `replyTo()` are single-valued, and a second call replaces the first (last call wins).
- **D-04:** `template(t, data)` and an explicit `subject()`/`html()`/`text()` cannot be combined. `build()` throws `InvalidMailException` if both are set. There are no precedence rules.
- **D-05:** `build()` reports the **first** problem it finds, as one `InvalidMailException` that names it. It does not collect all problems (the sample's pre-flight aggregates across rows itself).
- **D-06:** Template rendering happens in `build()`, so a missing placeholder field surfaces there as `InvalidMailException` (success criterion 3, TMPL-04).

### Attachment content type and name
- **D-07:** Content type is resolved in this order: (1) a small built-in extension map (about 10 entries: pdf, png, jpg/jpeg, gif, txt, html/htm, csv, zip, docx, xlsx), (2) `Files.probeContentType`, (3) `application/octet-stream` (MODEL-05). The resolved type is therefore the same on every OS and CI image.
- **D-08:** The extension match is case-insensitive using `Locale.ROOT` (`Report.PDF` → `application/pdf`).
- **D-09:** The filename defaults to `path.getFileName().toString()`.
- **D-10:** The builder offers three attach forms: `attach(Path)`, `attach(Path, String contentType)` and `attach(Attachment)` (full record for a custom name and type; `contentId` must still be null in the MVP). — **Reversibility:** one-way — these are public API once released.

### RecordingTransport test hooks
- **D-11:** `MailAccount` has a public `transport()` accessor (it holds id, transport and defaultFrom per spec §5). A test reaches the registry-created instance with `(RecordingTransport) account.transport()`. There are no statics and no test-only hooks in the core. — **Reversibility:** costly — public accessor on a core type.
- **D-12:** Failures are scripted two ways: `failFor(email, exception)` fails any send that addresses that recipient, and `failNext(exception...)` is a queue consumed by the next sends in order, for sequences like "transient twice, then accept". Per-recipient matching ignores case (`Locale.ROOT`).
- **D-13:** `validate` is scriptable through `failValidation(InvalidMailException)`, so tests can prove that `Mailer.validate`/`send` run the transport's `validate` (CORE-05) and the Phase 2 pre-flight path.
- **D-14:** `RecordingTransport` stores accepted messages in memory per instance. It must be thread-safe (CORE-06), and its Javadoc says so even though it lives in test sources.

### Repository docs
- **D-15:** A new **root** `CLAUDE.md` holds the spec §11 rules verbatim (Rule 0 + further rules). The existing `.claude/CLAUDE.md` stays as is (GSD-managed). Both are loaded.
- **D-16:** `SECURITY.md` names GitHub private vulnerability reporting as the primary channel (the owner enables it in the repo settings) and `stephan@morisoft.de` as the fallback.
- **D-17:** The `README.md` stub in Phase 1 is minimal: what morimail is, a pre-1.0/API-may-change notice, Maven coordinates, the accepted-not-delivered and at-least-once note (ERR-02), and "quick start coming". The test-keystore sentence is added in Phase 3 when the keystore exists. The full quick start is Phase 4.

### Plan-check decisions
- **D-18:** In Phase 1, "`./mvnw verify` passes offline" is proven by a warm-cache `./mvnw -B -ntp -o verify` (Maven resolves nothing remotely) plus the source check that no test or main source uses `java.net.` networking (no `java.net.` reference under `morimail/src` or `morimail-sample-serial/src`, no HTTP or WireMock dependency declared). `-o` is not claimed as network isolation. The real network-disabled proof is REPO-08 in Phase 3 (for example a CI job without network). — **Reversibility:** reversible (a verification scope, no public API). *(Owner decision 2026-11-21, plan-checker iteration 1, warning W3; owner = maintainer.)*
- **D-19:** The owner accepts the larger coarse plans and does not want them split: 01-03 lists 14 files, 01-04 lists 12 files with a 10-file Task 1, 01-06 lists 11 files, and 01-05 and 01-06 have an estimated context ratio of 0.74 to 0.82 (uncalibrated, zero calibration samples). Each affected plan's sizing note cites this decision. — **Reversibility:** reversible (plan shape only). *(Owner decision 2026-11-21, plan-checker iteration 1, warnings W1 and W2; owner = maintainer.)*
- **D-20:** Every release build is scoped to the published modules: `./mvnw -B -ntp -Prelease clean verify -Dgpg.skip -pl morimail -am` (parent and library). Evidence: with the test-jar `attach=false` (REPO-09), a full-reactor `-Prelease` build fails with "Could not find artifact ...:jar:tests" on Maven 3.10.1 and 3.9.16. It fails with and without `clean`, and even when the sample skips its tests, because the library is packaged earlier in the same build and Maven then no longer falls back to `target/test-classes`. Scoping passed. This is from the planning probe of 2026-11-21: a minimal three-module reactor, offline, nothing installed. Consequence: the CI release step and the later Phase 4 rehearsal and owner release commands carry `-pl morimail -am`; the sample's use of the test-jar is proven in the default build; `attach=false` and the sample's skip properties stay. — **Reversibility:** reversible (a build command scope, no public API). *(Owner decision 2026-11-21, plan-checker iteration 1, warning W4, Alternative A; owner = maintainer.)*

### Claude's Discretion
- Exact method names on `RecordingTransport` beyond the shape above (e.g. `sent()` returning an immutable snapshot), and whether failed attempts are also recorded.
- Message wording of `InvalidMailException` and registry errors, as long as each names the field/placeholder/ID involved.
- Handling of duplicate account IDs in `Mailer.of(...)`: rejecting them with `IllegalArgumentException` is the expected simple choice.
- Logging (via `System.Logger`) levels and content, within the rules (never secrets or bodies; recipients at DEBUG only).
- Package placement of the registry and `BuiltInTransports`, within the import rules.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Binding specification
- `spec.md` (moved to `docs/spec.md` in this phase) — the binding spec. For Phase 1: §2 (scope, registry requirements), §3 (technology), §4 (layout, import rules), §5, 5.1, 5.2, 5.3 (core types, usage, errors, renderer), §10.1 (tests, RecordingTransport), §11 (working rules, copied verbatim into root `CLAUDE.md`), §12 (open-decision defaults), §13.1, 13.3, 13.4 (repo files, POM/build, identity).

### Planning
- `.planning/REQUIREMENTS.md` — REPO/MODEL/TMPL/CORE/ERR requirement text, with the **[decided]** owner decisions.
- `.planning/ROADMAP.md` — Phase 1 goal, success criteria and scope notes.
- `.planning/PROJECT.md` — Key Decisions table.
- `.planning/research/STACK.md` — verified versions and recipes (parent POM, release profile, test-jar `attach=false`, wrapper, Surefire tags).
- `.planning/research/ARCHITECTURE.md`, `.planning/research/PITFALLS.md` — structure and known traps.
- `.claude/CLAUDE.md` — project constraints and the stack table (GSD-managed).

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- None. The repository holds only `LICENSE`, `.gitignore`, `spec.md` (untracked) and the planning docs. This phase creates the first code.

### Established Patterns
- None yet. Phase 1 sets them: records for the model, final classes with static factories and builders, a checked exception tree, `System.Logger`.

### Integration Points
- `BuiltInTransports` (an empty list now) gets its Graph line in Phase 3.
- `TokenProvider.freshAccessToken` default (CORE-02) is frozen here for Phase 2 and Phase 3.
- The test-jar (`RecordingTransport` + its `META-INF/services` entry) is consumed by `morimail-sample-serial` tests in Phase 2.
- Commits must override the workspace `GIT_*` env vars to use `Stephan Wald <stephan@morisoft.de>` (see memory note).

</code_context>

<specifics>
## Specific Ideas

- The extension map's purpose is determinism across OS/CI, not completeness. Keep it short.
- `failFor` + `failNext` were chosen with the Phase 2 sample tests in mind ("row 3 fails", "transient twice then accepted", "stop on auth error").

</specifics>

<deferred>
## Deferred Ideas

None. The discussion stayed within the phase scope.

</deferred>

---

*Phase: 01-library-core*
*Context gathered: 2026-11-21*
