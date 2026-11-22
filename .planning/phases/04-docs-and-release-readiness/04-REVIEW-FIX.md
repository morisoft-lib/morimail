---
phase: 04-docs-and-release-readiness
fixed_at: 2026-11-22T20:00:00Z
review_path: /home/coder/morimail/.planning/phases/04-docs-and-release-readiness/04-REVIEW.md
iteration: 1
findings_in_scope: 7
fixed: 7
skipped: 0
status: all_fixed
---

# Phase 4: Code Review Fix Report

**Fixed at:** 2026-11-22
**Source review:** .planning/phases/04-docs-and-release-readiness/04-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope: 7
- Fixed: 7
- Skipped: 0

**Verification:** `./mvnw -B -ntp -o verify` ran in the isolated worktree (not the main checkout) after the last code change and after the last commit. BUILD SUCCESS, 137 sample tests and the full library suite green, including the two new tests.

## Fixed Issues

### WR-01: README states as fact a limit that architecture.md says is unconfirmed for Graph

**Files modified:** `README.md`, `docs/spec.md`, `docs/entra-setup.md`, `docs/architecture.md`
**Commit:** 29555d4
**Applied fix:** All four places now say the 30 messages per minute limit is documented for SMTP client submission, that Microsoft does not say whether Graph `sendMail` counts against it, and that `--rate 20` stays below it either way. Wording is the same in all four.

### WR-02: entra-setup step 2 tells the reader to delete the key, then needs it

**Files modified:** `docs/entra-setup.md`
**Commit:** f99d4cc
**Applied fix:** The early "delete `app.key`" sentence is now "keep it until `app.p12` loads". The delete instruction moved after the fallback and the `keytool -list` check, and one sentence says `-legacy` is OpenSSL 3 only.

### WR-03: release.md asks for CI on the release commit, but the commit is made after CI could run and before the irreversible step

**Files modified:** `docs/release.md`
**Commit:** ce7bf8b
**Applied fix:** Step 4.3 now also pushes the release commit and a new step 4.4 waits for green CI before the deploy. The tag step pushes only the tag (`git push origin v0.1.0`), because main is already pushed. Status: fixed, requires human verification (release procedure wording, owner should read it once).

### IN-01: Review and plan identifiers leak into public source and docs

**Files modified:** `docs/architecture.md`, `GraphTransport.java`, `EntraClientCredentials.java`, `EntraTokenErrorsTest.java`, `GraphErrorMappingTest.java`
**Commit:** e6707b5
**Applied fix:** Removed WR-01, G-03-2, G-03-4, D-xx, "Phase 4" and UAT references from comments and architecture.md. The plain-language reasons stay.

### IN-02: The two `clean()` methods sanitise differently, though both documents say the same thing

**Files modified:** `morimail/src/main/java/de/morisoft/morimail/entra/EntraClientCredentials.java`
**Commit:** 1fba771
**Applied fix:** Entra `clean()` now uses `Character.isISOControl(c) || Character.isWhitespace(c) ? ' ' : c`, as graph does. The rest is unchanged. Status: fixed, requires human verification (sanitising logic, no test added for U+2028).

### IN-03: Test names and coverage no longer match the new behaviour

**Files modified:** `EntraTokenErrorsTest.java`, `GraphErrorMappingTest.java`
**Commit:** 188707a
**Applied fix:** Both misnamed tests are now `aSingleWordOverTheCutIsDroppedQuickly`. Each class has a new test `aLongTextWithSpacesIsCutAtAWordBoundaryAndThenAtThreeHundredCharacters` that feeds 300 repeats of `"word "` and expects `"word "` x 60 followed by `...` (the 300-character cut ends on a space, as the code produces).

### IN-04: `.gitignore` covers the key formats in the guide but not the common neighbours

**Files modified:** `.gitignore`
**Commit:** 6792cdf
**Applied fix:** Added `*.crt`, `*.csr`, `*.jks`, `*.keystore`, `*.pkcs12`, `*.p8` and `.env` below `*.cer`. No tracked file matches, and the throwaway keystore is still not ignored.

## Notes

- Planning ids of the same kind remain outside the reviewed files: `morimail/src/test/java/de/morisoft/morimail/MailBuilderTest.java:202` ("D-01, D-02, D-03") and `pom.xml:82` ("Phase 3 ... Phase 2"). They were not part of IN-01 and were left alone.

---

_Fixed: 2026-11-22_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
