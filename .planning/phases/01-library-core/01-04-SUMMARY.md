---
phase: 01-library-core
plan: 04
subsystem: core
tags: [serviceloader, test-jar, walking-skeleton, import-rules, javadoc-guard]

requires:
  - phase: 01-library-core/03
    provides: Mail, MailMessage, MailAddress, MailTransport, TransportProvider, TokenProvider, error tree
provides:
  - MailAccount with Builder resolving the transport through TransportRegistry
  - Mailer.of, validate and send with a shared prepare step (default sender, transport validate)
  - Package-private TransportRegistry (built-in list plus ServiceLoader) and BuiltInTransports
  - RecordingTransport and RecordingTransportProvider (id recording) in the library test-jar
  - Sample module test-jar dependency and RecordingTestJarSmokeTest
  - ImportRulesTest and PublicTypesDocumentThreadSafetyTest guards, proven by mutation probes
affects: [01-05, 01-06, phase-02, phase-03]

actuals:
  tokens: 9000
  tasks: 2
  commits: 2

plan_head_before: d0a85109542cf649d0ba20b12a7c7503af511f47
plan_head_after: b880a8176fcd3a394ed7a3ff65da6d2583f12af8
commits: 2

tech-stack:
  added: []
  patterns:
    - registry built fresh on every MailAccount.build(), no static state
    - one lock (the instance monitor) guarding all RecordingTransport state
    - source-text guard tests that report every violation in one sorted assertion

key-files:
  created:
    - morimail/src/main/java/de/morisoft/morimail/MailAccount.java
    - morimail/src/main/java/de/morisoft/morimail/Mailer.java
    - morimail/src/main/java/de/morisoft/morimail/TransportRegistry.java
    - morimail/src/main/java/de/morisoft/morimail/BuiltInTransports.java
    - morimail/src/test/java/de/morisoft/morimail/test/RecordingTransport.java
    - morimail/src/test/java/de/morisoft/morimail/test/RecordingTransportProvider.java
    - morimail/src/test/resources/META-INF/services/de.morisoft.morimail.TransportProvider
    - morimail/src/test/java/de/morisoft/morimail/SendThroughRecordingTest.java
    - morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/RecordingTestJarSmokeTest.java
    - morimail/src/test/java/de/morisoft/morimail/ImportRulesTest.java
    - morimail/src/test/java/de/morisoft/morimail/PublicTypesDocumentThreadSafetyTest.java
  modified:
    - morimail-sample-serial/pom.xml

key-decisions:
  - "Mailer.validate is declared throws PermanentMailException; the no-sender and transport-validate failures are the subclass InvalidMailException, so one signature covers both"
  - "Mailer.of keeps accounts in Map.copyOf; unknown-account and duplicate-id messages list ids sorted, never mail content"
  - "ImportRulesTest strips comments and scans source text only; its report lines are 'imports X', 'references X' and 'mentions pkg' so the probes can grep them"

patterns-established:
  - "Guard tests resolve the module directory from the basedir system property and fail on a vacuous (empty) file list"
  - "Mutation probes delete the old Surefire report, plant a compiling violation, and require a fresh report naming the probe"

requirements-completed: [REPO-09, REPO-10, REPO-11, REPO-12]

duration: 12min
completed: 2026-11-21
status: complete
---

# Phase 1 Plan 04: Walking skeleton, part 3 Summary

**A plain-text mail now goes from `Mail.using(..)` through `Mailer.of(account).send(mail)` into the `recording` transport with the account's `defaultFrom` applied, in the library and in the sample module via the reactor test-jar, with the import-rule and thread-safety Javadoc guards enforced by the build.**

## Performance

- **Duration:** about 12 min
- **Tasks:** 2
- **Files:** 11 created, 1 modified

## Accomplishments

- `TransportRegistry.load()` combines `BuiltInTransports.providers()` (empty in Phase 1) with `ServiceLoader.load(TransportProvider.class)` and builds a fresh registry per call; `find` throws `IllegalArgumentException` listing the available ids (or `(none)`).
- `MailAccount.Builder.build()` rejects a missing account id, a missing or unknown transport id and an invalid `defaultFrom` with `IllegalArgumentException`; messages carry ids and the fixed reason text only. `transport()` is public (D-11).
- `Mailer` shares one `prepare` step between `validate` and `send`: account lookup, default sender, transport `validate`. `send` calls the transport once, with no retry and no de-duplication.
- `RecordingTransport` keeps accepted messages under one lock, registered as `recording` through `META-INF/services` in the test-jar. The sample's `RecordingTestJarSmokeTest` finds it through `ServiceLoader` from the reactor-attached `-tests.jar`.
- `ImportRulesTest` (REPO-12) rejects non-`java.*` imports (javax included), non-`java.*` qualified names without import, graph/entra mentions in the wrong place and graph/entra cross references. `PublicTypesDocumentThreadSafetyTest` (REPO-10) is a documentation-presence check over 19 public types.

## Task Commits

1. **Task 1: registry, account, Mailer, recording fixture, sample consumer** - `3c25712` (feat)
2. **Task 2: import-rule and thread-safety Javadoc guards** - `b880a81` (test)

Both commits carry the maintainer identity as author and committer and the Claude Code `Co-Authored-By` trailer (spec 12 default).

## Verification

- `./mvnw -B -ntp clean verify` with `~/.m2/repository/de/morisoft` absent: BUILD SUCCESS; the fresh `RecordingTestJarSmokeTest` report shows 1 test, 0 failures, so the sample resolved the attached test-jar from the reactor.
- `./mvnw -B -ntp -Prelease clean verify -Dgpg.skip -pl morimail -am`: BUILD SUCCESS, no doclint warning; `morimail/target/morimail-0.1.0-SNAPSHOT-tests.jar` exists and holds the services entry (attach false). No de.morisoft artifact appeared in the local Maven repository.
- `./mvnw -B -ntp -o clean verify`: BUILD SUCCESS offline.
- Import probe (Jackson import, javax.naming import, fully qualified Jackson field): exit non-zero, fresh `ImportRulesTest` report with `Failures: 1` naming all three violations. Thread-doc probe: fresh report naming `ThreadDocProbe.java: ThreadDocProbe Javadoc states no thread safety`. Probe sources were removed by the EXIT trap; a clean rebuild leaves no probe class in `morimail/target` or the main jar. The import probe was rerun after the final report-helper refactor of the test and still passed.
- Acceptance greps pass: 17 core sources, no graph/entra/sendmail mention in the core, no `java.net.` in any source, no static initializer in `TransportRegistry`, no non-example address in `morimail` or `morimail-sample-serial`.
- Every commit after 8c9b082 has the maintainer identity as author and committer; `git tag --list` is empty.

## Deviations from Plan

None - plan executed exactly as written. Two notes, neither a deviation:

- Task 1 step 0: the main checkout had no root `spec.md` (plan 01-02 already removed it) and tracks `docs/spec.md` with the pinned sha256, so the cleanup was a no-op.
- The plan's acceptance grep expects each violation form to appear as a literal starting with a quote (`"imports `); the test builds the report text through a small `report(...)` helper so those literals exist.

## Known Stubs

None. `BuiltInTransports.providers()` returning an empty list is the intended Phase 1 state; Phase 3 adds the Graph provider line.

## Threat Flags

None. T-01-22 (core dependency allowlist) is mitigated and proven by the mutation probe; T-01-10 messages name only account ids, transport ids and fixed reason texts. T-01-09 stays accepted until plan 01-06 adds the duplicate-id rule.

## Assumptions carried

- `Mailer` does not re-run the `Mail.Builder` checks on a `Mail` built directly from the records; a `Mailer` with zero accounts rejects every send as an unknown account.
- Requirements CORE-01, CORE-03, CORE-04, CORE-05 and CORE-07 are also declared by plan 01-06 (failure scripting, duplicate-id rules, concurrency tests), so they stay open here.

## Issues Encountered

None.

## Self-Check: PASSED

All 12 files exist on disk; commits `3c25712` and `b880a81` are on main.
