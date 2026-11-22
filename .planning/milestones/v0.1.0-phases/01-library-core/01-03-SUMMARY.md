---
phase: 01-library-core
plan: 03
subsystem: core
tags: [java-records, builder, checked-exceptions, spi, javadoc, doclint]

requires:
  - phase: 01-library-core/01
    provides: Maven reactor, release profile with doclint all and failOnWarnings
provides:
  - MailAddress, Attachment, MailMessage and Mail records with defensive list copies
  - Mail.Builder with to(String...), from(String), subject, text and the fixed-order build() checks
  - Checked error tree MailException with Transient, Permanent, Invalid, SenderRejected and AuthRequired subclasses
  - MailTransport, TransportProvider and TokenProvider SPI (freshAccessToken default)
  - MailBuilderTest with 14 cases
affects: [01-04, 01-05, 01-06, phase-02, phase-03]

actuals:
  tokens: 7600
  tasks: 2
  commits: 2

plan_head_before: 8646053d34bf6108b35002224a65b585c8e1764d
plan_head_after: 7f62f4214b8bd37d51eb3d62872a64f36e20a165
commits: 2

tech-stack:
  added: []
  patterns:
    - records for the model, a static nested builder with a private constructor
    - one InvalidMailException per build(), naming field and index, never the value
    - every public type Javadoc states thread safety; exceptions are described as not immutable

key-files:
  created:
    - morimail/src/main/java/de/morisoft/morimail/MailAddress.java
    - morimail/src/main/java/de/morisoft/morimail/Attachment.java
    - morimail/src/main/java/de/morisoft/morimail/MailMessage.java
    - morimail/src/main/java/de/morisoft/morimail/Mail.java
    - morimail/src/main/java/de/morisoft/morimail/MailException.java
    - morimail/src/main/java/de/morisoft/morimail/PermanentMailException.java
    - morimail/src/main/java/de/morisoft/morimail/InvalidMailException.java
    - morimail/src/main/java/de/morisoft/morimail/TransientMailException.java
    - morimail/src/main/java/de/morisoft/morimail/SenderRejectedException.java
    - morimail/src/main/java/de/morisoft/morimail/AuthRequiredException.java
    - morimail/src/main/java/de/morisoft/morimail/MailTransport.java
    - morimail/src/main/java/de/morisoft/morimail/TransportProvider.java
    - morimail/src/main/java/de/morisoft/morimail/TokenProvider.java
    - morimail/src/test/java/de/morisoft/morimail/MailBuilderTest.java
  modified: []

key-decisions:
  - "MailAddress.invalidReason treats a null or empty email as 'email is missing' and checks the single '@' before the line break, so each reason text stays fixed and never contains the address"
  - "MailAddress.hasLineBreak is a package-private helper reused by Mail.Builder for the subject check, instead of a second copy"
  - "Mail.Builder is public static final with a private constructor; only Mail.using creates it"

requirements-completed: [MODEL-01, MODEL-04, CORE-01, CORE-02, ERR-01, ERR-02, REPO-09, REPO-10, REPO-11]

duration: 2min
completed: 2026-11-21
status: complete
---

# Phase 1 Plan 03: Core contracts Summary

**Plain-text `Mail.using(..).to(..).subject(..).text(..).build()` with one first-problem `InvalidMailException`, the full checked error tree, and the documented `MailTransport` / `TransportProvider` / `TokenProvider` SPI, all doclint-clean under the release profile.**

## Performance

- **Duration:** about 2 min of execution (files were short declarations fixed by spec section 5)
- **Tasks:** 2
- **Files:** 14 created

## Accomplishments

- `Mail.Builder.build()` checks in the fixed order account id, recipient, subject, body, from and each `to[i]`, subject line break, and throws the first failure only. Messages name the field and index (`to[1]: ...`) and never echo a value.
- The records are immutable; `MailMessage` copies its four lists with `List.copyOf`.
- Error tree per spec 5.2: `TransientMailException` carries a nullable `Duration` and exposes `Optional<Duration> retryAfter()`; no serialization version constant.
- `MailTransport.send` Javadoc carries the two ERR-02 sentences (accepted, not delivered; at-least-once). Every SPI interface states that implementations must be safe for concurrent use. `TokenProvider` is a `@FunctionalInterface` with the `freshAccessToken` default.
- All six exception classes carry the same accurate sentence: state fixed at construction, safe to pass between threads once thrown, "not immutable" like every `Throwable`.
- The release build now writes the sources and Javadoc jars.

## Task Commits

1. **Task 1: records, builder, base exceptions, MailBuilderTest** - `70e3d86` (feat)
2. **Task 2: SPI interfaces and remaining exceptions** - `7f62f42` (feat)

Both commits carry author and committer `Stephan Wald <stephan@morisoft.de>` and the Claude Code `Co-Authored-By` trailer (spec 12 default).

## Verification

- `./mvnw -B -ntp -q -o -pl morimail test -Dtest=MailBuilderTest`: green; the Surefire report contains all 14 named methods and no skipped case.
- `./mvnw -B -ntp -o verify`: BUILD SUCCESS after each task.
- `./mvnw -B -ntp -o -Prelease clean verify -Dgpg.skip -pl morimail -am`: BUILD SUCCESS, doclint clean; `morimail-0.1.0-SNAPSHOT-sources.jar` and `-javadoc.jar` exist in `morimail/target`.
- javap hierarchy check passes for all six exceptions; `TokenProvider.freshAccessToken` default and `TransientMailException.retryAfter()` are in the compiled classes.
- Acceptance greps of both tasks pass: 13 main sources, no two-string address overload, no untyped `null` in tests, no `javax` import, no concrete transport or service name in the core, no `java.net.` under `morimail/src`, six exception classes with the "not immutable" sentence, no `serialVersionUID`.
- No commit after 8c9b082 has another author or committer; `git tag --list` is empty; no address other than example.com/example.org in `morimail/src`.

## Deviations from Plan

None - plan executed exactly as written. The spec section 5 signatures and the plan's interface block agree; no difference to report. The spec precondition (docs/spec.md, sha256 ff96d3ce...e3b814) was met.

## Known Stubs

None.

## Threat Flags

None. T-01-07 (CR/LF in from, to, subject) and T-01-08 (no value echoed in messages) are mitigated and pinned by `MailBuilderTest`.

## Assumptions carried

- JDK 17's javadoc is assumed to be no stricter than JDK 21's on the constructs used; the local runs use JDK 21 and CI on Java 17 is the proof.
- The exception Javadoc sentences are long single lines; wrapping them is cosmetic and was left alone.

## Issues Encountered

None.

## Self-Check: PASSED

All 14 files exist on disk; commits `70e3d86` and `7f62f42` are on main.
