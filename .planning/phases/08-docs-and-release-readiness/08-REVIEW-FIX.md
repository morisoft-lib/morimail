---
phase: 08-docs-and-release-readiness
fixed_at: 2026-11-23T20:50:00Z
review_path: /home/coder/morimail/.planning/phases/08-docs-and-release-readiness/08-REVIEW.md
iteration: 1
findings_in_scope: 6
fixed: 6
skipped: 0
status: all_fixed
---

# Phase 8: Code Review Fix Report

**Fixed at:** 2026-11-23
**Source review:** /home/coder/morimail/.planning/phases/08-docs-and-release-readiness/08-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope: 6
- Fixed: 6
- Skipped: 0

**Verification:** `./mvnw -B -ntp -o verify` ran green in the isolated review-fix worktree after the code and test changes (IN-04, IN-05): BUILD SUCCESS for all four modules, 164 tests in `morimail-sample-serial`, 0 failures. The worktree was fast-forwarded into `main` and then removed, so this run is not reproducible from the worktree after teardown. The doc-only fixes (WR-01, IN-01, IN-02, IN-03) have no build gate beyond re-reading the edited text. No release command from `docs/release.md` was run.

## Fixed Issues

### WR-01: release.md presents one-time first-release setup as part of every release

**Files modified:** `docs/release.md`
**Commit:** eb14bec
**Applied fix:** Renamed "Before the first release" to "Before every release". Added a "One-time setup (steps 1 to 3)" heading ahead of step 1 with a note: if 0.1.0 was already released, check the namespace, check that `gpg --list-secret-keys` shows the key that signed 0.1.0, check the token, then continue at step 4. It also says not to create a second signing key. The first bullet under "Before every release" (enable GitHub private vulnerability reporting) is itself a one-time item and was left as is.

### IN-01: Step 4 says "Steps 5 to 8 use both variables", which is not true

**Files modified:** `docs/release.md`
**Commit:** 9ff5b6e
**Applied fix:** Step 4 now says steps 5 to 8 use `VERSION` and step 8 also uses `NEXT`. The one `$VERSION` in step 5 became `${VERSION}`.

### IN-02: README tells readers to depend on a version that is not on Maven Central

**Files modified:** `docs/release.md`
**Commit:** 51fe88b
**Applied fix:** Rewrote step 8.3. It now tells the owner to replace the "not on Maven Central yet" sentence under Coordinates, then check that both dependency blocks show `${VERSION}`. The step notes that for 0.2.0 they already do and that for a later release they must be changed. This differs slightly from the suggested fix, which said the blocks already show `${VERSION}`. That is true only for 0.2.0, and the doc is meant for later releases too. README.md was not changed, and the optional caveat under the `morimail-html` block was not added.

### IN-03: spec section 10.3 still says "without extension" and "not verified"

**Files modified:** `docs/spec.md`
**Commit:** 90162d7
**Applied fix:** The Graph transport bullet now points to section 6.2 and `docs/architecture.md` for the property names. The sample bullet now says "without its last extension".

### IN-04: SendCommandHelpTest cannot detect a missing dry-run description

**Files modified:** `morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/SendCommandHelpTest.java`
**Commit:** e425dd9
**Applied fix:** The test collapses whitespace in the usage text, because picocli wraps long lines. It asserts `copies each inline image once into inline/ under --out` and `sent mails keep cid:` in addition to the file names and `cid:NAME`. It dropped the weak standalone `inline/` token and the planning IDs (D-09, D-11) from the Javadoc. It kept the `doesNotContain("%")` guard.

### IN-05: Dry-run rewrite writes the file name into an HTML attribute without escaping quotes

**Files modified:** `morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/SendCommand.java`
**Commit:** 750e899
**Applied fix:** Added `.replace("'", "&#39;")` after the existing `&amp;` replacement of the `src` value, with no new helper. No new test was added, to keep within Rule 0.

---

_Fixed: 2026-11-23_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
