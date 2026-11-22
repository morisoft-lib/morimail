---
phase: 01-library-core
fixed_at: 2026-11-21T13:50:00Z
review_path: .planning/phases/01-library-core/01-REVIEW.md
iteration: 1
findings_in_scope: 12
fixed: 11
skipped: 1
status: partial
---

# Phase 1: Code Review Fix Report

**Fixed at:** 2026-11-21T13:50:00Z
**Source review:** .planning/phases/01-library-core/01-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope: 12
- Fixed: 11
- Skipped: 1

**Verification:** each fix was checked with `./mvnw -B -ntp -o verify` in an isolated worktree, which was then fast-forwarded into `main`. After the merge the same command ran again in the main checkout: BUILD SUCCESS, 134 tests in the library and 1 in the sample. `./mvnw -B -ntp -o -Prelease clean verify -Dgpg.skip -pl morimail -am` also passed (Javadoc and sources jars build).

## Fixed Issues

### WR-01: Address validation accepts characters that break MIME headers (recipient injection)

**Files modified:** `morimail/src/main/java/de/morisoft/morimail/MailAddress.java`, `morimail/src/test/java/de/morisoft/morimail/MailBuilderTest.java`, `morimail/src/test/java/de/morisoft/morimail/MailModelTest.java`
**Commit:** 4badb84
**Status:** fixed: requires human verification (validation rule)
**Applied fix:** `invalidReason` now rejects whitespace, ASCII control characters and `, ; < > ( ) [ ] \ "` in the email part, with a reason that does not echo the address. The display name is still checked for line breaks only; the class Javadoc says a MIME transport quotes or encodes it. New test `rejectsCharactersThatBreakAHeader`. `MailModelTest.mailAddressKeepsValuesExactlyAsGiven` used an email with surrounding spaces to prove "no trimming"; it now uses `A@Example.org` (the case is still preserved) because surrounding spaces are no longer a valid address. A CSV value with stray spaces now fails `build()`, so the sample must trim before building.

### WR-02: Explicit `Attachment` filename and content type are not validated

**Files modified:** `morimail/src/main/java/de/morisoft/morimail/Mail.java`, `morimail/src/test/java/de/morisoft/morimail/MailBuilderTest.java`
**Commit:** 2cbf016
**Status:** fixed: requires human verification (validation rule)
**Applied fix:** `resolveAttachments` throws `InvalidMailException` for a blank filename or one with CR/LF, and for a content type with CR/LF or without a `/` after at least one character. Path separators in the filename are not rejected: the review's fix text covers line breaks and blank names only. Two new tests.

### WR-03: `Mailer.send` and `Mailer.validate` throw a raw `NullPointerException` for a directly constructed `Mail` with a null account ID

**Files modified:** `morimail/src/main/java/de/morisoft/morimail/Mailer.java`, `morimail/src/test/java/de/morisoft/morimail/MailerTest.java`
**Commit:** 5a4b88a
**Status:** fixed: requires human verification (null handling)
**Applied fix:** A null account id is looked up as unknown (`PermanentMailException`). A null message throws `InvalidMailException("message is missing")`. New test with `new Mail(null, null)` and `new Mail("main", null)`.

### WR-04: CI job has no `timeout-minutes`

**Files modified:** `.github/workflows/ci.yml`
**Commit:** 4a5a21b
**Applied fix:** Added `timeout-minutes: 15` to the `build` job. The optional `push:` branch restriction was left alone (not asked for by the spec).

### WR-05: `fileDeletedAfterBuildStillSends` pins a contract that only the fixture satisfies

**Files modified:** `morimail/src/main/java/de/morisoft/morimail/MailTransport.java`, `docs/architecture.md`, `morimail/src/test/java/de/morisoft/morimail/MailBuilderTest.java`
**Commit:** 72a4244
**Applied fix:** Renamed the test to `recordingTransportDoesNotReadAttachmentFiles`. `MailTransport.send` Javadoc and a new "Contracts" section in `docs/architecture.md` say that an attachment file unreadable at send time is reported as `InvalidMailException` that names the file and never carries its content.

### IN-02: `TransientMailException` constructor overloads are ambiguous with a literal `null`, and there is no cause+delay form

**Files modified:** `morimail/src/main/java/de/morisoft/morimail/TransientMailException.java`, `morimail/src/test/java/de/morisoft/morimail/ErrorHierarchyTest.java`
**Commit:** 17d59c2
**Applied fix:** The `(String, Duration)` constructor throws `IllegalArgumentException` for a negative duration. The overloads are unchanged. New test.

### IN-03: Exception and fixture Javadoc carries a copy-pasted thread-safety paragraph

**Files modified:** `AuthRequiredException.java`, `InvalidMailException.java`, `MailException.java`, `PermanentMailException.java`, `SenderRejectedException.java`, `TransientMailException.java`, `MailTransport.java` (all under `morimail/src/main/java/de/morisoft/morimail/`)
**Commit:** b0c0893
**Applied fix:** The full explanation now sits once on `MailException`, wrapped at 100 columns. Subclasses carry one short sentence. The over-long first lines of these files and the at-least-once line in `MailTransport` were wrapped. The "thread" wording that `PublicTypesDocumentThreadSafetyTest` checks is kept.

### IN-04: `MailMessage` and `Attachment` default `toString()` print full bodies, recipients and paths

**Files modified:** `morimail/src/main/java/de/morisoft/morimail/MailMessage.java`
**Commit:** 3987910
**Applied fix:** Took the review's documentation option, not a `toString()` override (Rule 0): the class Javadoc now says `toString()` includes addresses, bodies and attachment paths, so a message should not be logged as a whole.

### IN-05: Subject line-break handling is inconsistent between literal and rendered subjects

**Files modified:** `morimail/src/main/java/de/morisoft/morimail/Mail.java`, `morimail/src/main/java/de/morisoft/morimail/MailTemplate.java`
**Commit:** bb76d5a
**Applied fix:** Kept the behaviour, which matches the spec ("line breaks ... replaced by a space"), and documented it: `Mail.Builder.subject` rejects only CR and LF, while a subject value from a template has every Unicode line break replaced by a space.

### IN-06: Provider failures other than `ServiceConfigurationError` are not wrapped, and a null transport is not rejected

**Files modified:** `morimail/src/main/java/de/morisoft/morimail/MailAccount.java`, `morimail/src/main/java/de/morisoft/morimail/TransportProvider.java`
**Commit:** a6653fa
**Applied fix:** `MailAccount.Builder.build()` throws `IllegalStateException` naming the provider class when `create` returns `null`. `TransportProvider.create` and `build()` Javadoc now say `create` must not return `null` and may throw `IllegalArgumentException` for bad options. The optional wrapping of a `RuntimeException` from `id()` was not done (Rule 0; the review marked it optional). No new test: a null-returning provider would need a second service entry in the shared test-jar, which is more machinery than the check is worth.

### IN-07: Maven wrapper downloads the distribution without a checksum

**Files modified:** `.mvn/wrapper/maven-wrapper.properties`
**Commit:** 6e08f81
**Applied fix:** Added `distributionSha256Sum=9c1ef9174ef250a5009ed0dec97cbe8699acf7479add4e62bf7b0ea791c0adb5`. Central publishes only a SHA-512 for the zip. I downloaded the zip, confirmed that its SHA-512 equals the value on both Central and archive.apache.org, and took the SHA-256 from that file. A fresh wrapper download into an empty Maven user home (`MAVEN_USER_HOME`) passed the check.

## Skipped Issues

### IN-01: Initial commit carries a different author identity

**File:** git history, commit `8c9b082`
**Reason:** Fixing it needs a history rewrite (`git rebase --root`), which is not allowed here. This is the owner's decision before the first publish; no file change was made.
**Original issue:** The root commit is authored `Stephan Wald <swald@basis.cloud>`, not the `stephan@morisoft.de` identity the project rules require.

---

_Fixed: 2026-11-21T13:50:00Z_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
