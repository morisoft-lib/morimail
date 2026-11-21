---
phase: 01-library-core
plan: 06
subsystem: core
tags: [recording-transport, error-categories, transport-registry, service-loader, concurrency, tdd]

requires:
  - phase: 01-library-core/03
    provides: Mail builder, exception tree, TokenProvider, MailTransport and TransportProvider contracts
  - phase: 01-library-core/04
    provides: MailAccount, Mailer, TransportRegistry, RecordingTransport fixture and its provider
provides:
  - RecordingTransport scripting surface - failNext (FIFO queue), failFor (persistent per recipient, Locale.ROOT, wins over the queue without consuming it), failValidation (cleared with null), attempts()
  - TransportRegistry rules - duplicate id, null or blank id, ServiceConfigurationError from iterator(), hasNext() or next() wrapped with a fixed message
  - Seven test classes pinning the Mailer, MailAccount, error tree, TokenProvider and registry contracts, plus an 8 thread / 400 send concurrency test
affects: [phase-02, phase-03]

actuals:
  tokens: 9200
  tasks: 3
  commits: 6

plan_head_before: 3d735c8816531e0c4cb4d0f4d71c878ee94722d9
plan_head_after: 6fdebcf811c6f0e6941366a866d333bdf9fdd1af
commits: 6

tech-stack:
  added: []
  patterns:
    - scripted failures live on the test fixture, set on the registry-created instance via (RecordingTransport) account.transport()
    - registry duplicate and broken-provider cases are fed through TransportRegistry.of(builtIn, external) with nested stub providers, never through a second services file
    - registry wrapper messages are fixed text; provider-generated diagnostics stay in the cause

key-files:
  created:
    - morimail/src/test/java/de/morisoft/morimail/MailerTest.java
    - morimail/src/test/java/de/morisoft/morimail/ErrorHierarchyTest.java
    - morimail/src/test/java/de/morisoft/morimail/RecordingTransportTest.java
    - morimail/src/test/java/de/morisoft/morimail/TokenProviderTest.java
    - morimail/src/test/java/de/morisoft/morimail/TransportRegistryTest.java
    - morimail/src/test/java/de/morisoft/morimail/MailAccountTest.java
    - morimail/src/test/java/de/morisoft/morimail/MailerConcurrencyTest.java
  modified:
    - morimail/src/test/java/de/morisoft/morimail/test/RecordingTransport.java
    - morimail/src/main/java/de/morisoft/morimail/TransportRegistry.java
    - morimail/src/main/java/de/morisoft/morimail/MailAccount.java

key-decisions:
  - "RecordingTransport checks in the order failFor, then failNext queue, then record; validate and send are synchronized on the instance and no I/O happens inside the lock"
  - "TransportRegistry marks a collision as (built-in) by checking whether the already registered provider is in the built-in list, so no extra state is kept"
  - "The registry catches ServiceConfigurationError around the whole external for-each loop; its wrapper message is fixed and never copies provider text"
  - "Mailer and MailAccount needed no defect fix: every contract test passed against the 01-04 code"

requirements-completed: [CORE-01, CORE-02, CORE-03, CORE-04, CORE-05, CORE-06, CORE-07, ERR-01]

coverage:
  - id: D1
    description: "A queued transient failure surfaces from Mailer.send with its retryAfter, the next one without, and the third send is recorded (attempts 3, sent 1)"
    requirement: "CORE-07"
    verification:
      - kind: unit
        ref: "morimail/src/test/java/de/morisoft/morimail/MailerTest.java#transientTwiceThenAccepted"
        status: pass
    human_judgment: false
  - id: D2
    description: "Permanent, invalid-mail, sender-rejected and auth-required failures surface with their own type, a failed send records nothing and counts one attempt"
    requirement: "ERR-01"
    verification:
      - kind: unit
        ref: "morimail/src/test/java/de/morisoft/morimail/MailerTest.java#scriptedPermanentFailureSurfacesWithItsType"
        status: pass
      - kind: unit
        ref: "morimail/src/test/java/de/morisoft/morimail/ErrorHierarchyTest.java#mailExceptionIsCheckedNotRuntime"
        status: pass
    human_judgment: false
  - id: D3
    description: "failFor and failValidation scripting with the documented precedence"
    requirement: "CORE-07"
    verification:
      - kind: unit
        ref: "morimail/src/test/java/de/morisoft/morimail/RecordingTransportTest.java#failForWinsOverFailNextWithoutConsumingTheQueue"
        status: pass
      - kind: unit
        ref: "morimail/src/test/java/de/morisoft/morimail/RecordingTransportTest.java#failValidationFailsValidateAndSendWithoutAnAttempt"
        status: pass
    human_judgment: false
  - id: D4
    description: "Duplicate ids, blank ids and ServiceConfigurationError from iterator(), hasNext() and next() fail registry loading with IllegalStateException and a fixed wrapper message"
    requirement: "CORE-03"
    verification:
      - kind: unit
        ref: "morimail/src/test/java/de/morisoft/morimail/TransportRegistryTest.java#serviceConfigurationErrorFromNextIsWrapped"
        status: pass
      - kind: unit
        ref: "morimail/src/test/java/de/morisoft/morimail/TransportRegistryTest.java#duplicateBuiltInAndExternalIdIsRejected"
        status: pass
    human_judgment: false
  - id: D5
    description: "Account configuration errors name only ids; options and auth pass through unchanged"
    requirement: "CORE-04"
    verification:
      - kind: unit
        ref: "morimail/src/test/java/de/morisoft/morimail/MailAccountTest.java#invalidDefaultFromIsRejectedWithoutEchoingTheValue"
        status: pass
    human_judgment: false
  - id: D6
    description: "One Mailer shared by 8 threads sending 50 mails each records exactly the 400 expected subjects"
    requirement: "CORE-06"
    verification:
      - kind: unit
        ref: "morimail/src/test/java/de/morisoft/morimail/MailerConcurrencyTest.java#eightThreadsSendFourHundredMailsThroughOneMailer"
        status: pass
    human_judgment: false

duration: 5min
completed: 2026-11-21
status: complete
---

# Phase 1 Plan 06: Failure categories, scripted failures and configuration errors Summary

**RecordingTransport can script transient, permanent, sender-rejected, auth-required and per-recipient failures that surface from Mailer with their type, and TransportRegistry now fails loading on duplicate, blank or broken providers.**

## Performance

- **Duration:** 5 min (2026-11-21T13:17Z to 13:21Z)
- **Tasks:** 3
- **Files:** 10 (7 created, 3 modified)
- **Verification:** `./mvnw -B -ntp -o verify` and `./mvnw -B -ntp -o -Prelease clean verify -Dgpg.skip -pl morimail -am` both BUILD SUCCESS, 129 library tests, 0 failures, 0 skipped

## Accomplishments

- Tracer: a queued `TransientMailException` with `retryAfter` 7 s, then one without, then an accepted send, all through `Mailer.send` on the registry-created recording instance (attempts 3, sent 1).
- `failFor` matches to, cc and bcc case-insensitively (`Locale.ROOT`), wins over `failNext` and leaves the queue untouched; `failValidation` makes `Mailer.validate` and `Mailer.send` throw before any attempt is counted.
- `TransportRegistry.of` rejects an id reused by a later provider (naming the id and both classes, with `(built-in)` when the earlier one is built in), a null or blank id, and wraps `ServiceConfigurationError` from `iterator()`, `hasNext()` or `next()` in `IllegalStateException` with the fixed text "Cannot load a transport provider through ServiceLoader (see the cause)" and the error as cause.
- Mailer, MailAccount, TokenProvider and the six exceptions are pinned by tests; the concurrency test compares all 400 recorded subjects, so a duplicate cannot hide a loss, and shuts the executor down in `finally`.

## TDD Gate Compliance

Three RED to GREEN cycles, no REFACTOR commits (nothing to clean up).

| Cycle | RED commit | RED evidence | GREEN commit |
|-------|-----------|--------------|--------------|
| 1 (tracer) | e5847df | `MailerTest.transientTwiceThenAccepted` failed with "Expecting code to raise a throwable" against the `failNext` stub; the five `ErrorHierarchyTest` methods pass because they pin the existing tree | fd360c9 |
| 2 | 186f954 | `failForMatchesRecipientCaseInsensitivelyInToCcAndBcc`, `failForWinsOverFailNextWithoutConsumingTheQueue` and `failValidationFailsValidateAndSendWithoutAnAttempt` failed on assertions against `failFor` and `failValidation` stubs | 1e93aff |
| 3 | 84c324e | Seven `TransportRegistryTest` methods failed on assertions (duplicate, blank, three ServiceConfigurationError cases, wrapper text) | 6fdebcf |

Semantic assessment: each RED run compiled and executed the target tests, which failed on the planned assertion. The remaining tests of each RED commit (Mailer contract, TokenProvider, MailAccount, concurrency, error tree) passed immediately because they characterize behavior plans 01-03 and 01-04 already built; they are regression pins, not new behavior. Stubs of `failNext`, `attempts`, `failFor` and `failValidation` in the RED commits keep the tests compiling, so the failure is an assertion failure and not a build failure. `workflow.tdd_mode` is not enabled, so the `gsd_run check tdd-red-evidence` classifier was not run.

## Task Commits

1. Task 1 (tracer): `e5847df` test, `fd360c9` feat
2. Task 2: `186f954` test, `1e93aff` feat
3. Task 3: `84c324e` test, `6fdebcf` feat

## Deviations from Plan

None - plan executed exactly as written. No test exposed a defect in `Mailer.java`, so it is unchanged. `MailAccount.java` changed only in the `build()` Javadoc (`@throws IllegalStateException`), as step 1 of Task 3 asked.

The tracer gate (end-of-phase mode, automated-only verify) was satisfied by running the full task verify block after the GREEN change: it passed, so expansion went ahead.

## Authentication Gates

None.

## Known Stubs

None. The `failNext`, `attempts`, `failFor` and `failValidation` stubs existed only in RED commits and are fully implemented.

## Threat Flags

None. No new network endpoint, auth path or file access; the registry changes implement T-01-16, T-01-17 and T-01-23 from the plan's threat model.

## Verification Results

- Surefire checklists for ErrorHierarchyTest (5), MailerTest (11), RecordingTransportTest (7), TokenProviderTest (2), TransportRegistryTest (11) and MailAccountTest (7): every named method ran, none skipped.
- Acceptance greps: TransportRegistry rule words 13 (>= 4), "see the cause" 1, `+ e.getMessage()` 0, concurrency test words 6 and 3, services files 1 (<= 1).
- Email gate (`git grep` for non-example addresses under `morimail`) returned 1 (no match). Author and committer checks since 8c9b082 are empty.
- Last commit trailer check passed.

## Next Phase Readiness

Phase 1 plans are all executed. Phase 2 (sample) can script failures on `recording` through the test-jar (`failNext`, `failFor`, `failValidation`, `attempts`). Phase 3 will add `graph` to the built-in list; the unknown-id test uses `contains(...)`, so it keeps passing.

## Self-Check: PASSED

- Created test files all present on disk (seven test classes).
- Commits e5847df, fd360c9, 186f954, 1e93aff, 84c324e and 6fdebcf are ancestors of HEAD.
