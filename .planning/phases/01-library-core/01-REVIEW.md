---
phase: 01-library-core
reviewed: 2026-11-21T13:30:00Z
depth: standard
files_reviewed: 46
files_reviewed_list:
  - .gitattributes
  - .github/workflows/ci.yml
  - .gitignore
  - .mvn/wrapper/maven-wrapper.properties
  - pom.xml
  - morimail/pom.xml
  - morimail-sample-serial/pom.xml
  - morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/RecordingTestJarSmokeTest.java
  - morimail/src/main/java/de/morisoft/morimail/Attachment.java
  - morimail/src/main/java/de/morisoft/morimail/AuthRequiredException.java
  - morimail/src/main/java/de/morisoft/morimail/BuiltInTransports.java
  - morimail/src/main/java/de/morisoft/morimail/InvalidMailException.java
  - morimail/src/main/java/de/morisoft/morimail/MailAccount.java
  - morimail/src/main/java/de/morisoft/morimail/MailAddress.java
  - morimail/src/main/java/de/morisoft/morimail/Mailer.java
  - morimail/src/main/java/de/morisoft/morimail/MailException.java
  - morimail/src/main/java/de/morisoft/morimail/Mail.java
  - morimail/src/main/java/de/morisoft/morimail/MailMessage.java
  - morimail/src/main/java/de/morisoft/morimail/MailTemplate.java
  - morimail/src/main/java/de/morisoft/morimail/MailTransport.java
  - morimail/src/main/java/de/morisoft/morimail/PermanentMailException.java
  - morimail/src/main/java/de/morisoft/morimail/SenderRejectedException.java
  - morimail/src/main/java/de/morisoft/morimail/TemplateRenderer.java
  - morimail/src/main/java/de/morisoft/morimail/TokenProvider.java
  - morimail/src/main/java/de/morisoft/morimail/TransientMailException.java
  - morimail/src/main/java/de/morisoft/morimail/TransportProvider.java
  - morimail/src/main/java/de/morisoft/morimail/TransportRegistry.java
  - morimail/src/test/java/de/morisoft/morimail/ErrorHierarchyTest.java
  - morimail/src/test/java/de/morisoft/morimail/ImportRulesTest.java
  - morimail/src/test/java/de/morisoft/morimail/MailAccountTest.java
  - morimail/src/test/java/de/morisoft/morimail/MailBuilderTest.java
  - morimail/src/test/java/de/morisoft/morimail/MailerConcurrencyTest.java
  - morimail/src/test/java/de/morisoft/morimail/MailerTest.java
  - morimail/src/test/java/de/morisoft/morimail/MailModelTest.java
  - morimail/src/test/java/de/morisoft/morimail/PublicTypesDocumentThreadSafetyTest.java
  - morimail/src/test/java/de/morisoft/morimail/RecordingTransportTest.java
  - morimail/src/test/java/de/morisoft/morimail/SendThroughRecordingTest.java
  - morimail/src/test/java/de/morisoft/morimail/TemplatedSendTest.java
  - morimail/src/test/java/de/morisoft/morimail/TemplateRendererTest.java
  - morimail/src/test/java/de/morisoft/morimail/test/RecordingTransport.java
  - morimail/src/test/java/de/morisoft/morimail/test/RecordingTransportProvider.java
  - morimail/src/test/java/de/morisoft/morimail/TokenProviderTest.java
  - morimail/src/test/java/de/morisoft/morimail/TransportRegistryTest.java
  - morimail/src/test/resources/META-INF/services/de.morisoft.morimail.TransportProvider
  - mvnw
  - mvnw.cmd
  - README.md
  - SECURITY.md
  - CLAUDE.md
  - docs/architecture.md
findings:
  critical: 0
  warning: 5
  info: 7
  total: 12
  necessity: 0
status: issues_found
---

# Phase 1: Code Review Report

**Reviewed:** 2026-11-21T13:30:00Z
**Depth:** standard
**Files Reviewed:** 46
**Status:** issues_found

## Summary

I reviewed the core library (model records, `Mail.Builder`, `TemplateRenderer`, `MailAccount`, `Mailer`, `TransportRegistry`, the exception tree), the test fixtures (`RecordingTransport` and its provider), all tests, the POMs, the CI workflow, the wrapper and the public docs. The code was judged against `docs/spec.md`. I ran `./mvnw -B -ntp -o verify` and `-Prelease clean verify -Dgpg.skip -pl morimail -am`. Both pass (129 tests in the library, 1 in the sample, no Javadoc warnings).

I found no secrets, real addresses or tenant IDs in tracked files. Nothing is logged. The renderer treats `$`, `\` and re-scan cases correctly. The registry rejects duplicate IDs as the spec requires. The import-rule and Javadoc-presence tests really do guard what they claim to. The `freshAccessToken` default on `TokenProvider` deviates from spec 7.3, but `docs/architecture.md` records it as an owner decision, so I do not count it.

The issues are about weak input validation in the core and a few robustness gaps, plus CI and hygiene items. None of them is a crash or a security hole in the current Phase 1 state. Several (address and attachment-name validation) become real header-injection risks as soon as a MIME-building transport (SMTP, SES) is added. The core is the right place to close them, because the spec says the core carries the checks every transport needs.

## Warnings

### WR-01: Address validation accepts characters that break MIME headers (recipient injection)

**File:** `morimail/src/main/java/de/morisoft/morimail/MailAddress.java:44-60`
**Issue:** `invalidReason` only requires exactly one `@` with text on both sides, and no CR/LF. Each of these passes:
- `a,b@example.org`: in a MIME `To:` header this is two recipients, `a` (local) and `b@example.org`.
- `x <y@example.org`, `a b@example.org`, `a;b@example.org`, `"@example.org`: whitespace, angle brackets, quotes and semicolons.
- NUL and other control characters.

The same applies to the display name, which only gets a CR/LF check. A name such as `Smith, John` or one containing `"` or `<>` is not safe in a header unless the transport quotes it. Graph takes JSON and tolerates this, but the spec says `build()` "checks what every transport needs", and the spec's later SMTP/SES transports build MIME. The sample's pre-flight also needs a "syntactically valid email" check (spec 8.3), which this does not provide, so a typo in a CSV passes pre-flight and fails per row at the server.
**Fix:** Reject whitespace, ASCII control characters, and `,;<>()[]\":` in the email part, and keep the "no text echoed" rule:
```java
for (int i = 0; i < email.length(); i++) {
    char c = email.charAt(i);
    if (c <= ' ' || c == 0x7f || ",;<>()[]\\\":".indexOf(c) >= 0) {
        return "email contains a character that is not allowed";
    }
}
```
Add test cases for `a,b@example.org` and `a b@example.org`. Document that a display name is quoted or encoded by the transport.

### WR-02: Explicit `Attachment` filename and content type are not validated

**File:** `morimail/src/main/java/de/morisoft/morimail/Mail.java:410-418`
**Issue:** `resolveAttachments` takes `attachment.filename()` and `attachment.contentType()` as given when they are non-null. An empty or blank filename, a name containing CR/LF or path separators (`../x`), and a content type containing CR/LF or no `/` all reach the transport unchanged. For a MIME transport this is header injection through `Content-Disposition` or `Content-Type`. For Graph it is a malformed `name`/`contentType` and a 400 per mail. The recipient address and the subject get line-break checks, so these two fields are an inconsistent gap.
**Fix:** In `resolveAttachments`, after resolving the two values:
```java
if (filename.isBlank() || MailAddress.hasLineBreak(filename)) {
    throw new InvalidMailException(field + "filename is blank or contains a line break");
}
if (MailAddress.hasLineBreak(contentType) || contentType.indexOf('/') < 1) {
    throw new InvalidMailException(field + "contentType is not a media type");
}
```
Add tests for both.

### WR-03: `Mailer.send` and `Mailer.validate` throw a raw `NullPointerException` for a directly constructed `Mail` with a null account ID

**File:** `morimail/src/main/java/de/morisoft/morimail/Mailer.java:66`
**Issue:** `accounts` is a `Map.copyOf(...)` immutable map, and `get(null)` on it throws `NullPointerException` (I confirmed this for empty and non-empty maps on this JDK). `Mail` is a public record and its Javadoc says direct construction does not validate, and the spec wants consumers to deserialize and rebuild `Mail` for queues. A `Mail` with a null `accountId` (or a null `message`) therefore escapes the documented `PermanentMailException` for an unknown account as an unchecked exception. A queue worker that catches `MailException` to decide whether to park the mail would crash on it instead.
**Fix:**
```java
String id = mail.accountId();
MailAccount account = id == null ? null : accounts.get(id);
```
Treat a null `message` as `InvalidMailException("message is missing")`. Add a test with `new Mail(null, ...)`.

### WR-04: CI job has no `timeout-minutes`

**File:** `.github/workflows/ci.yml:7-8`
**Issue:** The `build` job runs three Maven invocations, the first of which downloads Maven and all dependencies, and has no job-level timeout. A hung download or a hung test (the concurrency test uses a 30 s future timeout, but nothing bounds Maven itself) holds a runner for the 6 hour default. This is a public repo, where pushes and PRs from anyone trigger it.
**Fix:**
```yaml
jobs:
  build:
    runs-on: ubuntu-latest
    timeout-minutes: 15
```
Optionally restrict `push:` to `branches: [main]` so a PR branch is not built twice (once for `push`, once for `pull_request`).

### WR-05: `fileDeletedAfterBuildStillSends` pins a contract that only the fixture satisfies

**File:** `morimail/src/test/java/de/morisoft/morimail/MailBuilderTest.java:486-497`
**Issue:** The test asserts that a mail whose attachment file was deleted after `build()` still sends. It passes only because `RecordingTransport` never reads the file. A real transport (Graph, which base64-encodes the file) must read it and will fail. `MailTransport.send`'s contract does not say which exception that produces (`IOException` is not a `MailException`). A future reader would take the test name as a library guarantee. The contract gap is the real defect: a file that disappears between `build()` and `send()` has no defined error category, so a queue consumer cannot tell whether to retry.
**Fix:** Either rename the test to say it documents the recording fixture ("recordingTransportDoesNotReadAttachmentFiles"), or drop it. Add one sentence to `MailTransport.send` Javadoc and `docs/architecture.md`: an attachment file that cannot be read at send time is reported as `InvalidMailException` (or `PermanentMailException`) with the file name and never the content. That gives Phase 3 a rule to implement.

## Info

### IN-01: Initial commit carries a different author identity

**File:** git history, commit `8c9b082` ("Initial commit")
**Issue:** The first commit is authored `Stephan Wald <swald@basis.cloud>` (committer GitHub). The project rules say all commits use `stephan@morisoft.de`, and an employer-domain address is in the permanent public history. It is the owner's own address and was presumably created in the GitHub UI, so this may be intended. It cannot be fixed without a history rewrite, which is only cheap before the first release or push.
**Fix:** Decide before publishing. Either accept it, or rewrite the root commit author (`git rebase --root` with `--exec 'git commit --amend --reset-author --no-edit'`) while the repo has no other consumers.

### IN-02: `TransientMailException` constructor overloads are ambiguous with a literal `null`, and there is no cause+delay form

**File:** `morimail/src/main/java/de/morisoft/morimail/TransientMailException.java:316-330`
**Issue:** `new TransientMailException("m", null)` does not compile because `(String, Throwable)` and `(String, Duration)` both match. The test works around it with `(Duration) null` (`ErrorHierarchyTest.java:134`). The Graph transport in Phase 3 wants to attach a network `IOException` cause. A 503 with `Retry-After` has no cause, so this is fine today. Nothing validates a negative `Duration`, and a negative `retryAfter` would make the sample's wait loop spin or throw in `Thread.sleep`.
**Fix:** Reject a negative duration in the constructor (`retryAfter != null && retryAfter.isNegative()` becomes `IllegalArgumentException`, or clamp to zero). Keep the overloads, since this is the simplest shape.

### IN-03: Exception and fixture Javadoc carries a copy-pasted thread-safety paragraph

**File:** `morimail/src/main/java/de/morisoft/morimail/AuthRequiredException.java:5-6` (and `InvalidMailException`, `MailException`, `PermanentMailException`, `SenderRejectedException`, `TransientMailException`)
**Issue:** Every exception repeats the sentence "The message, the cause and any value given to the constructor are fixed at construction...", on a single line well over 100 columns, while the rest of the codebase wraps at 100. "any value given to the constructor" is meaningless for classes with no extra value. The paragraph exists mainly to satisfy `PublicTypesDocumentThreadSafetyTest` (which matches the word "thread"). `MailTransport.java:19-20` has the same unwrapped long line.
**Fix:** Put the explanation once on `MailException` and wrap it. Subclasses can carry one short line ("Immutable apart from `Throwable` state; safe to pass between threads once thrown.").

### IN-04: `MailMessage` and `Attachment` default `toString()` print full bodies, recipients and paths

**File:** `morimail/src/main/java/de/morisoft/morimail/MailMessage.java:27`
**Issue:** The record `toString()` includes `html`, `text`, every address and every attachment path. The project rule is "never log full bodies; recipients at DEBUG only". The library itself never logs, but a consumer's `log.info("sent {}", mail)` or an exception message built from a `Mail` would break the rule without noticing. Test failure output (AssertJ) also dumps whole messages. This is a judgment call under Rule 0 ("build less"), so I rate it Info.
**Fix:** Optional: override `toString()` on `MailMessage` to print only the subject, counts and the account, or document in the class Javadoc that `toString()` includes content.

### IN-05: Subject line-break handling is inconsistent between literal and rendered subjects

**File:** `morimail/src/main/java/de/morisoft/morimail/TemplateRenderer.java:176,227` and `Mail.java:356`
**Issue:** The renderer replaces every `\R` (CR, LF, NEL U+0085, U+2028, U+2029, VT, FF) in a subject value with a space. `build()` only rejects CR/LF in the final subject, and `acceptsLineSeparatorInLiteralSubject` explicitly accepts U+2028. So the same text is sanitized when it comes from template data and kept when it is typed in directly. This is harmless for header safety (only CR/LF matter), but it is a surprise.
**Fix:** Pick one rule and state it in `MailTemplate` and `Mail.Builder.subject` Javadoc, for example "only CR and LF are treated as line breaks in both paths", and use `[\r\n]+` in `LINE_BREAK`. Note that the spec says "line breaks ... replaced by a space", so `\R` is defensible. In that case, say so in the `subject()` Javadoc.

### IN-06: Provider failures other than `ServiceConfigurationError` are not wrapped, and a null transport is not rejected

**File:** `morimail/src/main/java/de/morisoft/morimail/TransportRegistry.java:436` and `MailAccount.java:153-155`
**Issue:** A provider whose `id()` throws a `RuntimeException` leaks that exception unwrapped from `MailAccount.build()`. `MailAccount.Builder.build` Javadoc lists only `IllegalArgumentException`, `IllegalStateException` and `NullPointerException`. A provider whose `create(...)` returns `null` produces an account with a null transport, which fails later with an `NullPointerException` inside `Mailer.prepare` rather than at account build time. The spec says configuration errors surface "when the account is built".
**Fix:** `Objects.requireNonNull`-style check after `provider.create(...)` with `IllegalStateException("Transport provider X returned no transport")`. Optionally wrap `RuntimeException` from `id()` the same way as `ServiceConfigurationError`. Document that `create` may throw `IllegalArgumentException` for bad options (the Graph transport will need this for option validation).

### IN-07: Maven wrapper downloads the distribution without a checksum

**File:** `.mvn/wrapper/maven-wrapper.properties:1-3`
**Issue:** The properties file has no `distributionSha256Sum`. The `mvnw` script supports it (`mvnw:111-242`). With the checksum absent, CI and every contributor run whatever archive that URL serves. The URL is HTTPS from Maven Central, so the practical risk is low, but the repo is published to Central and CI has the signing profile available later.
**Fix:** Add `distributionSha256Sum=<sha256 of apache-maven-3.10.1-bin.zip>`, taken from the `.sha256` file next to the artifact on Central (verify it against apache.org once, by hand).

---

_Reviewed: 2026-11-21T13:30:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
