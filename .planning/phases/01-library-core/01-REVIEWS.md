---
phase: 1
reviewers: [codex]
reviewed_at: 2026-11-21T10:32:34Z
plans_reviewed: [01-01-PLAN.md, 01-02-PLAN.md, 01-03-PLAN.md, 01-04-PLAN.md, 01-05-PLAN.md, 01-06-PLAN.md]
models:
  codex: "gpt-6-astra (reasoning=high)"
model_sources:
  codex: "banner"
---

# Cross-AI Plan Review — Phase 1

<!-- gsd:plan-revision-conflicts:begin -->
## Plan-Revision Conflicts
<!-- gsd:plan-revision-conflicts:end -->

## Codex Review

> [reviewed-without-source-citations] This reviewer declared source-grounded evidence but cited no file:line source evidence, so it reviewed the pasted plan text only — down-weight its verdict in the Consensus Summary.

**Verification limitation:** I could not read the checkout: the filesystem tool failed with `bwrap: No permissions to create a new namespace`. Fetching the public repository also failed. Consequently, this is a **provisional review of the supplied plans**, not a source-verified review. Repository-dependent findings remain open questions; I cannot provide trustworthy `file:line` citations.

References below identify tasks in the supplied files under `.planning/phases/01-library-core/`.

## 01-01

**Summary:** The build-first sequence is sensible and addresses packaging problems before application code depends on them. The main weaknesses are overclaimed verification guarantees and an upgrade instruction that conflicts with locked dependency decisions.

**Strengths**

- `01-01-PLAN.md`, Task 1, steps 2–3: applying the module name only to `default-jar`, and explicitly skipping sample publishing/signing/documentation artifacts, addresses distinct packaging risks.
- Task 2, steps 1–2: Java 17 CI, commit-pinned actions, read-only permissions, and both normal and release builds provide useful early integration coverage.
- Task 1, step 3: deferring the sample’s test-jar dependency until the fixture exists accounts for the reported empty-reactor failure.

**Concerns / open questions**

- **MEDIUM — Offline verification is presented as network isolation.** Task 1 and the prohibitions associate `-o verify` with tests opening no connections. Maven’s offline flag controls Maven operation; it does not sandbox test code or prevent an HTTP client from accessing the network. The command establishes a narrower guarantee than the plan claims. [Maven CLI reference](https://maven.apache.org/ref/3.9.11/maven-embedder/cli.html)
- **MEDIUM — Version refresh can override owner decisions.** Task 1, step 1 says to adopt newer stable versions, while the supplied requirements constrain Jackson to 2.23.x and D-02c fixes JUnit 6.1.3. An unrestricted “latest stable” refresh could violate those decisions.
- **MEDIUM — Artifact resolution remains unverified here.** The supplied probe used Maven 3.9.16, whereas execution uses 3.10.1. The eventual sample fixture test is the necessary confirmation of release-mode reactor resolution; the earlier probe should not be treated as final proof.

**Suggestions**

- Describe `-o verify` as a warmed-cache build check. Use an actual network-disabled execution environment when certifying the stronger requirement.
- Restrict version updates to explicitly permitted lines and preserve exact owner pins.
- Run the fixture gate with the checked-in wrapper and without previously installed project artifacts.

**Risk assessment: MEDIUM.** The structure is sound, but several acceptance claims need tighter definitions.

## 01-02

**Summary:** The documentation scope is appropriate. The spec migration and hygiene checks need revision before autonomous execution because their mechanisms do not fully support their stated guarantees.

**Strengths**

- `01-02-PLAN.md`, Task 2, step 1: verifying the committed spec blob before removing its source is a useful preservation check.
- Task 1, step 2: private reporting with an email fallback avoids depending entirely on a repository setting.
- Task 2, step 3: recording deviations alongside the spec makes later implementation decisions easier to audit.

**Concerns / open questions**

- **HIGH — The spec move crosses worktree boundaries.** Task 2 explicitly permits deleting the untracked source in the main checkout after committing its replacement in an isolated worktree. Meanwhile, 01-03 may read that same main-checkout source. Before integration, the main checkout can lose its binding spec without receiving `docs/spec.md`. This contradicts the claimed isolation.
- **HIGH — The email allowlist accepts disallowed suffixes.** Task 1’s expression ends allowed domains with `\b`. A word boundary exists between `org` and `.`, so `reader@example.org.evil.test` escapes detection. The maintainer exception similarly permits `stephan@morisoft.de.evil.test`. I reproduced these outcomes using the pasted expression in JavaScript; the actual Git/PCRE gate remains unrun.
- **MEDIUM — Hygiene coverage is overstated.** Four scans for emails, GUIDs, home paths, and PEM headers do not establish absence of arbitrary secrets, internal hosts, customer names, binary secrets, or historical disclosures. Task 1 explicitly leaves old content in history.
- **MEDIUM — Identity checks can pass on command failure.** The verification uses `test -z "$(git log ...)"`. A failed `git log` that produces no stdout can satisfy that outer test.

**Suggestions**

- Copy the spec into the task worktree, then remove the main-checkout source only during integration after verifying its replacement there.
- Check complete extracted addresses and exact domain endings; add suffix-lookalike negative controls.
- Scope hygiene claims to what each scan actually detects and separately record historical exceptions.
- Check `git log`’s exit status before evaluating its output.

**Risk assessment: HIGH.** The migration can disrupt another executor, and the central publication-safety gate has a concrete false-negative mechanism.

## 01-03

**Summary:** The core contracts are small and coherent. The checked errors, defensive copies, and deferred builder validation fit the project. Documentation and rejection-test instructions contain inaccuracies that should be corrected before they become public contracts.

**Strengths**

- `01-03-PLAN.md`, Task 1, steps 3–4: copying message lists while keeping validation in `build()` separates storage from the validating construction path.
- Task 1, step 6: typed null arguments anticipate the later overload additions and reduce integration failures.
- Task 2, step 5: a default `freshAccessToken` method preserves functional-interface use while providing the required invalidation seam.

**Concerns / open questions**

- **MEDIUM — Exceptions are incorrectly described as immutable.** Task 1, step 1 and Task 2, step 6 require that description. These classes inherit mutable `Throwable` operations, including `setStackTrace`, `initCause`, and `addSuppressed`. Immutable custom fields do not make the exception object immutable. [Java 17 `Throwable`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Throwable.html)
- **MEDIUM — Rejection assertions are impossible for some inputs.** Task 1, step 6 requires every error message not to contain the rejected value, while explicitly testing empty and null inputs. Every string contains the empty string; null needs separate assertion handling.
- **MEDIUM — Binding-spec access becomes optional.** The context declares the copied interface block authoritative when the spec is unreadable. That weakens the project’s explicit rule that the spec wins, especially during the concurrent move in 01-02.

**Suggestions**

- Document exception mutability accurately rather than changing `Throwable` behavior.
- Use nonempty sentinel values for disclosure assertions; test null and blank inputs through type and field-name assertions.
- Require a readable, hash-verified spec before implementation starts.

**Risk assessment: MEDIUM.** The implementation design is straightforward, but the public documentation and test instructions need correction.

## 01-04

**Summary:** This is a valuable end-to-end integration step. Exercising the sample through the registry and reactor tests real boundaries. The architecture and mutation guards, however, currently promise more than their described mechanisms enforce.

**Strengths**

- `01-04-PLAN.md`, Task 1, steps 6–8: sending from both library and consumer-module tests exercises provider discovery and test-fixture availability.
- Task 1, step 4: one shared preparation path reduces divergence between `validate` and `send`.
- Task 2, step 1: checking for a nonempty source set and `Mailer.java` avoids a vacuous architecture-test pass.

**Concerns / open questions**

- **HIGH — The import guard does not establish “JDK only.”** Task 2 allows every `javax.*` import, although that namespace also contains external APIs. Its fully qualified reference checks cover only Graph and Entra, so a core declaration using `com.fasterxml.jackson.databind.ObjectMapper` without an import would evade the described checks.
- **MEDIUM — Mutation probes accept unrelated failures.** Task 2 discards all Maven output and considers any nonzero exit successful. Compilation errors, dependency failures, or test-launch failures can therefore masquerade as a successful guard test.
- **MEDIUM — Probe cleanup covers source files only.** The commands delete probe `.java` files and then run an incremental build. The plan does not demonstrate that generated probe classes are absent from subsequent jars.
- **LOW — Thread-safety scanning checks wording, not truth.** Matching `thread|immutable` cannot validate a concurrency guarantee, as the exception wording in 01-03 demonstrates.

**Suggestions**

- Add negative controls for external fully qualified references and non-JDK `javax` names; make the guard’s documented scope match its implementation.
- Inspect Surefire results for the expected test failure and probe filename.
- Use cleanup traps and a clean rebuild after mutation probes.
- Keep the Javadoc scan as a documentation-presence check, supported by review and behavioral tests.

**Risk assessment: MEDIUM.** The integration path is well chosen; the verification machinery needs hardening.

## 01-05

**Summary:** The template and builder behavior is unusually well specified. Literal insertion, escaping, validation order, and typed CSV maps receive useful coverage. Remaining gaps concern repeatable builds, attachment error paths, and the claimed portability of content-type resolution.

**Strengths**

- `01-05-PLAN.md`, Task 1, step 2: manual append logic prevents replacement-string interpretation and recursive expansion of inserted values.
- Task 1, step 4: the end-to-end test uses `Map<String,String>` and checks the recorded rendered message and attachment metadata.
- Task 3, steps 1–3: append order, duplicate preservation, explicit/template conflicts, and all address fields receive behavioral coverage.

**Concerns / open questions**

- **MEDIUM — Content-type determinism is only partial.** The built-in extension map is deterministic, but its fallback delegates to installed file-type detectors. Unknown extensions can therefore vary across systems. The assumption-guarded tests can also skip fallback verification. [Java 17 `Files.probeContentType`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html#probeContentType(java.nio.file.Path))
- **MEDIUM — Template builder reuse is underspecified.** Task 1 stores a template/data pair and renders during `build()`, but does not pin repeated-build behavior. An implementation that replaces template fields with rendered output could reuse stale values or accidentally affect conflict tracking.
- **MEDIUM — Attachment diagnostic edges need coverage.** Task 1 formats errors using `Path.getFileName()`. Filesystem roots have no filename, so error formatting needs to avoid throwing an incidental null exception when rejecting a directory. Null `Attachment` inputs also lack an explicit policy.
- **LOW — Test-count floors are weak coverage evidence.** Task 3 allows parameterized invocations and skipped tests to count toward 41. That number cannot establish that all 41 intended behaviors were exercised.

**Suggestions**

- State that mapped extensions are deterministic and platform probing is best effort.
- Add repeated-build tests and define whether data-map changes before a later build are observed.
- Add a root-directory attachment test and document null argument behavior.
- Prefer a behavior checklist and explicit skipped-test reporting over numeric test floors.

**Risk assessment: MEDIUM.** Core rendering risks are well covered; several boundary behaviors remain to be settled.

## 01-06

**Summary:** The failure fixture and contract tests directly support the next phase. Error propagation and caller-controlled retries are clear. Concurrency validation and ServiceLoader claims should be more precise.

**Strengths**

- `01-06-PLAN.md`, Task 1, steps 1–3: the transient/transient/accepted sequence checks retry metadata, attempt counts, and accepted-message recording together.
- Task 2, steps 1–4: explicit failure precedence and validation failures support meaningful CLI tests without production test hooks.
- Task 3, step 1: wrapping external iteration is the correct place to handle lazily discovered provider failures. [Java 17 `ServiceLoader`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ServiceLoader.html)

**Concerns / open questions**

- **MEDIUM — Concurrency assertions check totals, not identities.** Task 3, step 4 verifies 400 attempts and records. Duplicate records combined with missing records could preserve both totals. Executor shutdown on failure is also unspecified.
- **MEDIUM — Broken-provider coverage is narrower than the actual iteration path.** The behavior block tests an `Iterable` whose `iterator()` throws. Lazy discovery failures can arise in `hasNext()` or `next()` after earlier providers have loaded.
- **MEDIUM — Registry diagnostic guarantees conflict.** Task 3, step 1 copies `ServiceConfigurationError.getMessage()` into the wrapper message while also promising that messages contain only IDs and class names. Arbitrary provider-generated error text is not constrained that way.
- **LOW — Duplicate detection is overstated as a hostile-jar defense.** Threat T-01-16 implies it prevents takeover by hostile classpath code. Providers may be instantiated before their IDs are checked; collision rejection prevents registry replacement, not arbitrary provider execution.

**Suggestions**

- Assert the exact set of 400 distinct message identifiers and shut down the executor in `finally`.
- Test failures from `iterator()`, `hasNext()`, and `next()`.
- Define a bounded wrapper diagnostic policy and distinguish it from the preserved cause.
- Describe duplicate detection as configuration integrity, with classpath contents trusted.

**Risk assessment: MEDIUM.** The intended contracts are well covered, but several tests and security statements overreach.

**Overall assessment:** All **6/6 plans** are covered. The proposed implementation remains small and broadly matches the phase goals. I would revise the plans before execution, prioritizing the spec-move isolation problem, email allowlist, architecture guard, and mutation-test validation. Source verification is still outstanding; the reported probes, actual repository state, and binding spec could not be independently checked.

---

## Consensus Summary

There was one reviewer. Claude was skipped for independence because this review ran inside Claude Code, and no other CLIs or local servers were detected. Codex's sandbox could not read the checkout (`bwrap: No permissions to create a new namespace`), so its output is marked `[reviewed-without-source-citations]` and should be down-weighted. To make up for this, the orchestrator checked the two HIGH findings against the plan text:

- **CONFIRMED: the 01-02 email allowlist lets lookalike domains through.** The regex was run with `grep -P` against `01-02-PLAN.md:151`. `reader@example.org.evil.test` and `stephan@morisoft.de.evil.test` are **not flagged**, while `a@corp.test` is flagged. `\b` matches between `org`/`de` and the next `.`, so the negative lookahead rejects the whole address. Fix: anchor the allowed domain endings with `(?![A-Z0-9.-])` instead of `\b`, and add lookalike negative controls.
- **CONFIRMED (with nuance): the 01-04 core import guard accepts all of `javax.*` and checks fully qualified names only for graph/entra** (`01-04-PLAN.md:232`). Writing `com.fasterxml.jackson...` fully qualified without an import would pass Rule 1. The mutation probes at `01-04-PLAN.md:241,243` send all output to `/dev/null` and accept any nonzero exit. This is moderate in practice because the Jackson probe compiles: Jackson is on the library classpath.
- **PARTLY MITIGATED: the 01-02 spec move across worktrees.** The plan states this coupling on purpose (`01-02-PLAN.md:17`): 01-03 reads `spec.md` "only as optional background", and the plan removes the main-checkout copy (`:30`). The risk Codex describes is still real: the main checkout loses `spec.md` before the worktree merge brings in `docs/spec.md`. Consider deleting the root copy only when the worktree is integrated.

### Agreed Strengths
(One reviewer, so there is no cross-reviewer agreement.) Codex rated the plans as small and coherent, and as broadly matching the phase goals. It singled out the build-first ordering in 01-01, the literal template insertion and escaping in 01-05, and the retry/failure fixture in 01-06.

### Agreed Concerns
(Single reviewer; ordered by priority.)
1. 01-02 email-allowlist false negatives (HIGH, confirmed).
2. 01-02 spec-move timing across worktrees (HIGH per Codex, partly mitigated).
3. 01-04 import guard scope and mutation probes that accept any failure (HIGH/MEDIUM, confirmed).
4. Wording that overclaims: `-o verify` described as network isolation (01-01), exceptions described as "immutable" (01-03), a thread-safety scan that checks wording rather than truth (01-04).
5. Test-design gaps: empty-string "message must not contain the value" assertions (01-03), repeated `build()` behaviour and root-path attachments (01-05), the 400-record concurrency test checking totals instead of identities, and ServiceLoader failures in `hasNext()`/`next()` (01-06).
6. 01-01 "adopt newer stable versions" could override the Jackson 2.23.x and JUnit 6.1.3 pins.

### Divergent Views
None (single reviewer). For true cross-AI consensus, rerun once Codex can read the repo, or add a second reviewer CLI.
