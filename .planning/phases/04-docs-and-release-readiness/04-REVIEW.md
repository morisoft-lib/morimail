---
phase: 04-docs-and-release-readiness
reviewed: 2026-11-22T00:00:00Z
depth: standard
files_reviewed: 10
files_reviewed_list:
  - .gitignore
  - README.md
  - docs/architecture.md
  - docs/entra-setup.md
  - docs/release.md
  - docs/spec.md
  - morimail/src/main/java/de/morisoft/morimail/entra/EntraClientCredentials.java
  - morimail/src/main/java/de/morisoft/morimail/graph/GraphTransport.java
  - morimail/src/test/java/de/morisoft/morimail/entra/EntraTokenErrorsTest.java
  - morimail/src/test/java/de/morisoft/morimail/graph/GraphErrorMappingTest.java
findings:
  critical: 0
  warning: 3
  info: 4
  total: 7
  necessity: 0
status: issues_found
---

# Phase 4: Code Review Report

**Reviewed:** 2026-11-22
**Depth:** standard
**Files Reviewed:** 10
**Status:** issues_found

## Summary

The phase changed two `clean()` methods (the word-boundary cut), their tests, the Javadoc of `GraphTransport.validate`, `.gitignore`, and the user-facing docs (README quick start, `entra-setup.md`, `release.md`, `architecture.md`, and spec §6.3/§8.3).

The code change is correct. I traced the cut: text over 1000 characters is cut back to the last ASCII space, so only whole words reach the address regex. An address contains no whitespace, so it cannot be split. If a text has no usable space, the result is `-`. The new straddle tests fail against the old code, because the old code left `alicea...` behind, so they test something real. I found no security defect in the diff. No secrets, real addresses or non-zero GUIDs appear in the docs. The `.gitignore` negation for the throwaway keystore works (`git check-ignore` reports nothing for it, and no tracked file is now ignored).

I checked these claims against the source and they hold:

- The README quick-start API calls (`MailAccount.builder`, `Mailer.of`, `MailTemplate` with 3 arguments, `Mail.using...attach`, the catch hierarchy).
- The sample flags, defaults and `account.properties` keys.
- The exit codes.
- The `dry run:` and `accepted N, skipped N, failed N` output lines.
- The registry exceptions.
- The `freshAccessToken` default method.
- `ImportRulesTest`.
- The "exactly three changed lines" claim in the release steps.

The defects are in the documents: contradictory or overclaiming statements that a reader can act on wrongly.

## Warnings

### WR-01: README states as fact a limit that architecture.md says is unconfirmed for Graph

**File:** `README.md:86` (the "What morimail is not" list); also `docs/spec.md` §8.3 and `docs/entra-setup.md:140`
**Issue:** The README says morimail "is bound by Exchange Online's documented per-mailbox limits: 30 messages per minute and 10,000 recipients per day". `docs/architecture.md` (last bullet of "Differences from the Microsoft documentation") records that the Microsoft page gives 30 messages per minute as a limit for SMTP client submission and "does not say whether Graph `sendMail` counts against it". The same page says the limit is not confirmed for this transport. The spec §8.3 text ("stays below the Exchange Online limit of 30 messages per minute per mailbox") has the same overclaim. The `--rate 20` default is justified by a limit that may not apply. A reader who trusts the README will believe 30 per minute is a hard Graph limit, or that rates under 30 are safe. Neither follows from the source.
**Fix:** Say what is documented and what is not, in the README and in spec §8.3:
```
Exchange Online documents a limit of 30 messages per minute per mailbox for SMTP client
submission and 10,000 recipients per day. Microsoft does not say whether Graph sendMail counts
against the per-minute limit. The sample's default of 20 mails per minute stays below it either way.
```

### WR-02: entra-setup step 2 tells the reader to delete the key, then needs it

**File:** `docs/entra-setup.md:51-61`
**Issue:** The guide is "follow the steps in order". Line 51 says "Then delete `app.key`." Line 55 then gives a fallback (`openssl pkcs12 -export -legacy -inkey app.key ...`) that needs `app.key`, and line 61 says "delete it only after you know that `app.p12` loads". The two instructions contradict each other. A reader who follows the order has deleted the only copy of the key when the fallback is needed. The key was generated with `-nodes`, so it is unencrypted, and the reader would have to start again, including re-uploading the certificate. A second problem: `-legacy` exists only on OpenSSL 3 and needs the legacy provider to be installed. OpenSSL 1.1 and LibreSSL (the macOS default) reject the flag. On those, the default output is already the old format, so the fallback is not needed there. The guide does not say so.
**Fix:** Move the delete instruction after the load check. For example, put the sentence after the fallback: "Check that the file loads (step 9b, or `keytool -list -storetype PKCS12 -keystore app.p12`). Only then delete `app.key`, for example with `shred -u app.key`." Add one sentence that `-legacy` is for OpenSSL 3 only.

### WR-03: release.md asks for CI on the release commit, but the commit is made after CI could run and before the irreversible step

**File:** `docs/release.md:6-7`, steps 4, 6 and 7
**Issue:** "Before the first release" says CI must be green "for the commit you release". Step 4 creates the release commit ("release 0.1.0") locally. The commit is first pushed in step 7 (`git push origin main v0.1.0`), after step 6, where the owner presses Publish. Publishing to Central is permanent (the guide says so itself). So CI never runs on the released commit before the irreversible step. A version bump that breaks the build, for example a stale `0.1.0-SNAPSHOT` reference, would show up only after publishing. The local `./mvnw verify` in step 4.3 is the only guard. It does not run the Java 17 / offline CI jobs.
**Fix:** Push the release commit to `main` or a branch after step 4 and wait for green CI before step 5, then tag after publishing:
```
3. Run ./mvnw -B -ntp verify, commit "release 0.1.0" and push it.
4. Wait until CI is green on that commit. Only then go on to the deploy.
```

## Info

### IN-01: Review and plan identifiers leak into public source and docs

**File:** `morimail/src/main/java/de/morisoft/morimail/graph/GraphTransport.java:321-323`, `EntraClientCredentials.java:384-386`, both test files (comment above each straddle test), `docs/architecture.md` (Contracts, "Server text in messages", and the rows citing `D-01`, `G-03-2`, `UAT`)
**Issue:** Comments and published docs refer to "review WR-01", "G-03-2", "D-06" and "Phase 4 D-01". These ids point into `.planning/` artifacts that an outside reader does not know. A source comment like "(review WR-01)" does not say what the finding was. The comments around it do explain the reason, so the id adds nothing.
**Fix:** Drop the ids from source comments and from `architecture.md`. Keep the plain reason that is already written next to them.

### IN-02: The two `clean()` methods sanitise differently, though both documents say the same thing

**File:** `morimail/src/main/java/de/morisoft/morimail/entra/EntraClientCredentials.java:382` vs `GraphTransport.java:319`
**Issue:** Graph turns `Character.isISOControl(c) || Character.isWhitespace(c)` into a space. Entra turns only `isISOControl` into a space and then collapses ASCII `\s+`. U+2028 and U+2029 (line and paragraph separator) are not ISO control characters, so they pass through the Entra path unchanged into `AuthRequiredException` messages, the sample's stop line and the journal. Some log and JSON viewers treat them as line breaks. `architecture.md` says "control characters become spaces" for both. The duplication is intentional (`graph` and `entra` must not import each other), but the two copies should agree.
**Fix:** Use the same test in `entra`: `Character.isISOControl(c) || Character.isWhitespace(c) ? ' ' : c`.

### IN-03: Test names and coverage no longer match the new behaviour

**File:** `morimail/src/test/java/de/morisoft/morimail/entra/EntraTokenErrorsTest.java:211-221`, `GraphErrorMappingTest.java:358-366`
**Issue:** `aVeryLongDescriptionIsCutAndCleanedQuickly` and `aVeryLongCodeIsCutAndCleanedQuickly` now assert `-`, so the text is dropped, not cut. The names say the opposite. No test covers the path where a text over 1000 characters contains spaces and the kept words are still longer than 300 characters, so the cut at a word boundary followed by the 300-character cut with `...` is untested. The one test with that shape (the straddle test) ends in `<address>`.
**Fix:** Rename the two tests, for example `aSingleWordOverTheCutIsDropped...`. Add one test with about 1500 characters of `"word "` repeats that expects 300 characters plus `...`.

### IN-04: `.gitignore` covers the key formats in the guide but not the common neighbours

**File:** `.gitignore:35-39`
**Issue:** The guide has the reader create `app.key`, `app.cer` and `app.p12` outside the repository, and the new rules guard those names. Other formats a reader could produce for the same job are still not ignored: `*.jks`, `*.keystore`, `*.pkcs12`, `*.crt`, `*.p8`, `*.csr`. The repository is public and its history is permanent, so one accidental `git add .` of such a file cannot be undone.
**Fix:** Add `*.jks`, `*.keystore`, `*.pkcs12`, `*.crt`, `*.p8`, `*.csr` and `.env` below `*.cer`. This does not need new rules for the throwaway keystore, whose negation already covers it.

---

_Reviewed: 2026-11-22_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
