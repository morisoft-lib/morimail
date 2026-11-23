---
phase: 08-docs-and-release-readiness
reviewed: 2026-11-23T00:00:00Z
depth: standard
files_reviewed: 6
files_reviewed_list:
  - README.md
  - docs/architecture.md
  - docs/release.md
  - docs/spec.md
  - morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/SendCommand.java
  - morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/SendCommandHelpTest.java
findings:
  critical: 0
  warning: 1
  info: 5
  total: 6
  necessity: 0
status: issues_found
---

# Phase 8: Code Review Report

**Reviewed:** 2026-11-23
**Depth:** standard
**Files Reviewed:** 6
**Status:** issues_found

## Summary

The diff since f246725 is almost all documentation plus picocli help text. I checked it against the code.

**README Java examples.** All three examples match the real public API:
- `EntraClientCredentials.withCertificate(String, String, Path, char[])`, `MailAccount.builder(..).transport/auth/defaultFrom/build`, `Mailer.of`, `Mail.using(..).to/template/attach/inline/build`, `Mail.Builder.attach(Attachment)` with a content ID, `HtmlImages.embed` (2 and 3 args), `HtmlImages.toDataUris`, `HtmlImages.Result.template()/images()` and `TransientMailException.retryAfter()` exist with those signatures.
- The `throws MailException` clauses cover every checked exception the examples can raise (`InvalidMailException`, `TransientMailException`).
- The documented limits (3 MiB per attachment and in total, 4 MiB estimated request) match `GraphTransport` (`MAX_ATTACHMENT_BYTES`, `MAX_REQUEST_BYTES`).
- The documented `build()` rules match `Mail.Builder`: exact ID comparison, an inline image without an HTML body fails, and the content ID character rules.

**Documented behaviour against the code.**
- `SendCommand.writeDryRun` creates `out/inline/`, copies each inline image once, and rewrites `cid:NAME` to `inline/<file name>` in the written HTML only. The mails keep `cid:`, so the help text, README, spec and architecture statements are true.
- The pre-flight list in README and spec (a `cid:` without an image, an unreferenced image, two files with the same base name, a non-image file, dot-files skipped) matches `Campaign.checkInline` and `listFiles`.
- The helper decisions in `docs/architecture.md` (10 s connect timeout, 30 s budget, reader thread, https-to-http redirect refused, 3 MiB cap, compile scope in the helper POM) match `HtmlImages` and `morimail-html/pom.xml`.

**Picocli text.** I ran `send --help` from the built jar. The description and footers contain no `%` and no `${...}` other than the existing, valid `${DEFAULT-VALUE}`. Output wraps cleanly, and `SendCommandHelpTest` passes.

**docs/release.md.**
- The `sed` patterns match exactly one `<version>` line in each of the four POMs (`pom.xml` line 165 holds `0.12.0`, which cannot match).
- `-pl morimail,morimail-html -am` matches the POM comment and CI.
- The 54 entries are nine base files times six, which is consistent. The 08-02 summary records a rehearsal with a throwaway key, upload skipped and `-o` never used.
- The settings.xml block uses the placeholders `TOKEN_USERNAME` and `TOKEN_PASSWORD`.
- Nothing instructs Claude to publish, deploy or tag. The opening paragraph says the owner runs every step.
- I found no secrets, tenant IDs or real addresses in the six files. The only non-example address is the allowed maintainer identity.

No Critical findings. No necessity findings (no guard or option in the diff protects against a state that cannot occur).

## Warnings

### WR-01: release.md presents one-time first-release setup as part of every release

**File:** `docs/release.md:5-52`
**Issue:** The doc is now "version-neutral" and is meant to be reused for 0.2.0 and later releases. It still has a section titled "Before the first release". Steps 1 (verify the namespace), 2 (create and publish a GPG key) and 3 (generate a Portal token) are written as sequential steps of every release. Nothing says they are one-time. 0.1.0 is already published, so the namespace is verified and a signing key exists. An owner following the doc literally for 0.2.0 could generate a second key. The doc itself later warns about the several-secret-keys case, where the warm-up and the deploy must name the same `<KEYID>`. The signing key for 0.2.0 would then differ from the one that signed 0.1.0 on Central, and that breaks key continuity for consumers who verify signatures.
**Fix:** Mark the one-time steps and tell the owner to reuse the existing key. For example, rename the heading to "One-time setup (skip if 0.1.0 was already released)" and put a line under steps 1 to 3:
```
Steps 1 to 3 are one-time. If you already released 0.1.0, check that the namespace shows as verified, that
`gpg --list-secret-keys` shows the key that signed 0.1.0, and that the token in settings.xml is still valid,
then continue at step 4.
```
Rename "Before the first release" to "Before every release".

## Info

### IN-01: Step 4 says "Steps 5 to 8 use both variables", which is not true

**File:** `docs/release.md:63`
**Issue:** Only step 8 uses `NEXT`. Steps 5 and 7 use `VERSION` only, and step 6 uses neither. Step 5 also mixes `${VERSION}` and `$VERSION` (line 119). The wording sends the owner looking for `NEXT` in steps where it is not used.
**Fix:** Write "Steps 5 to 8 use `VERSION`; step 8 also uses `NEXT`. Set them again in every new shell." Use `${VERSION}` everywhere.

### IN-02: README tells readers to depend on a version that is not on Maven Central

**File:** `README.md:17-21` and `README.md:113`
**Issue:** The `morimail` and `morimail-html` dependency blocks show `0.2.0`. The sentence after the first block says 0.2.0 is not published yet. The `morimail-html` block at line 113 carries no such caveat. A reader who copies that block gets a resolution failure until the release. This is deliberate, and release.md step 8.3 describes how to fix the text later. The step 8.3 wording is confusing, though: after step 4 the dependency blocks already show `${VERSION}`, so "where they show another version" will normally match nothing, and the sentence under Coordinates is the only text that needs changing.
**Fix:** Say in step 8.3: "Replace the 'not on Maven Central yet' sentence under Coordinates with one that names `${VERSION}` as the current release. The dependency blocks already show `${VERSION}`." Optionally repeat the caveat under the `morimail-html` block until the release.

### IN-03: spec section 10.3 still says "without extension" and "not verified"

**File:** `docs/spec.md:398-399` (compare `docs/spec.md:289`)
**Issue:** The `docs/spec.md` text is edited in this diff to say 0.2.0 implements section 10.3, but two bullets are stale or inconsistent:
- Line 399 still says "the file name without extension is the content ID". The campaign tree at line 289 and the code (`Campaign.contentId`) use the last extension, so `logo.v2.png` becomes `logo.v2`. A reader of 10.3 alone would read it differently.
- Line 398 still says to read the `fileAttachment` documentation first because the property names "were not verified for this spec". Section 6.2 and `docs/architecture.md` now record that they were verified and confirmed live.

The project says the spec is binding, so these should not contradict the implementation.
**Fix:** Change line 399 to "the file name without its last extension". Change line 398 to "...property names are recorded in section 6.2 and docs/architecture.md".

### IN-04: SendCommandHelpTest cannot detect a missing dry-run description

**File:** `morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/SendCommandHelpTest.java:15-17`
**Issue:** Three of the four `contains` tokens are weak. `inline/` and `attachments/` appear in the footer text on their own, and `inline/` would also be present if the `--dry-run` text lost its sentence about copying to `inline/` under `--out`. Only `cid:NAME` really pins the new text, and the `--dry-run` rewrite sentence (`inline/NAME`, "sent mails keep cid:") is not asserted at all. The `doesNotContain("%")` guard is useful, but it also fails on any future legitimate percent sign. It tests the whole usage text, not only the new text, and a failure would not point at the cause. The Javadoc cites planning IDs (D-09, D-11) that mean nothing to a reader of the public repository.
**Fix:** Assert the load-bearing phrases, for example `"copies each inline image once into inline/ under --out"` and `"sent mails keep cid:"`. Wording changes in the help text then show up as test failures, and the planning-ID references can be dropped from the Javadoc.

### IN-05: Dry-run rewrite writes the file name into an HTML attribute without escaping quotes

**File:** `morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/SendCommand.java:303-308`
**Issue:** The `src` value is built with `new URI(null, null, "inline/" + filename, null).toASCIIString().replace("&", "&amp;")`. The `URI` constructor leaves `'` unescaped (it is a legal URI mark). The pre-flight rejects quotes in the content ID, which is the file name minus its last extension, but not in the extension itself. A file named `a.p'g` has the valid ID `a`, and the dry run would write `src='inline/a.p'g'` into the rendered HTML. This only affects the preview file the author opens in a browser, and the real send is unaffected, so it is low impact.
**Fix:** Also replace `'` with `&#39;` (or `%27`) in the value, or state in the usage text that file names with quotes are unsupported. It is acceptable to leave as is under Rule 0.

---

_Reviewed: 2026-11-23_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
