# Phase 9: v0.2.0 tech debt cleanup - Context

**Gathered:** 2026-11-24
**Status:** Ready for planning
**Mode:** `--auto` (every choice below is the recommended default; see 09-DISCUSSION-LOG.md)

<domain>
## Phase Boundary

Close the open tech-debt items from `.planning/v0.2.0-MILESTONE-AUDIT.md` so the v0.2.0 milestone can be archived with no loose ends. Every item is closed either by a small code change with a test or by a recorded, documented decision. Nothing new is added to the API. Scope is fixed by ROADMAP.md Phase 9:

- Phase 5 (core): WR-01, WR-02, IN-01, IN-03
- Phase 6 (sample): pre-flight validates only the first mail (`SendCommand.java:210`)
- Phase 7 (helper): IN-01, IN-02, plus the `07-01-SUMMARY.md` frontmatter and stale "provides" text
- Phase 8 (docs): the stale "0.2.0 not on Central" text in `08-02-SUMMARY.md`

Out of scope (per ROADMAP): the `cid:` pattern-copies "no equality test" note (already covered by `cidPatternAgreesWithTheCore` in the sample and the helper), pushing main / running Temurin 17 CI, and the untracked `07-PATTERNS.md` and `state.json`. Those are git housekeeping for the owner.

</domain>

<decisions>
## Implementation Decisions

### Core: WR-01, control characters in an echoed `cid:` ID
- **D-01:** In `Mail.Builder.checkReferences`, replace every code point of the shown ID that is an ISO control character, or of type `LINE_SEPARATOR`, `PARAGRAPH_SEPARATOR` or `FORMAT`, with `?`, then cut at 64 code points as today. The message text stays `no inline image for cid:<shown>`. This keeps the message useful and stops log-line forging and terminal escapes from recipient data.
- **D-02:** Only IDs read from the rendered HTML need this, because they can come from recipient data. Builder-supplied values (content ID, content type) are left as they are (the review rates them low risk). The planner checks whether the sample (`Campaign`) or the helper prints an ID read from rendered HTML through its own message. If one does, it gets the same cleaning. If they only pass on the core message, nothing more is needed.
- **D-03:** Test: a rendered body with `cid:` followed by `\u001b`, `\u0085` and `\u2028` gives a message that contains none of them.

### Core: WR-02, the whole-HTML `cid:` scan
- **D-04:** Keep the owner's 2026-11-22 rule. Any `cid:` in the rendered HTML that does not follow a letter or digit counts, with no HTML parser (Phase 5 D-04 as amended). Restricting the match to attribute context is **not** done, because it would reverse an owner decision.
- **D-05:** Add `<` to the ID terminator set: `[^"'\s)<>]`. This makes `<p>cid:logo</p>` resolve to `logo`. It also makes the terminator set match the content ID rule (Phase 5 D-09 already rejects `<` in an ID). The change only loosens the check. No mail that passes today can fail, because an accepted content ID can never contain `<`. Change all three copies together: `Mail.java:48`, `Campaign.java:48` and `HtmlImages.java:111`. The existing `cidPatternAgreesWithTheCore` tests keep them equal. Add `>cid:logo</p` to their corpus.
- **D-06:** Do **not** strip a trailing `/` (`src=cid:logo/>`), because `/` is a legal content ID character. Document instead that an unquoted `src` directly followed by `/>` reads the `/` as part of the ID, and that quoting the value avoids this.
- **D-07:** Update the `docs/architecture.md` §10.3 row (line 40) for the new terminator, the `/` note and the accepted trade-off: prose such as `ref=cid:123` still counts as a reference. Replace the decision IDs in the `Mail.java:46` comment ("D-04 as amended 2026-11-22, D-05, D-06") and in `HtmlImages.java:110` ("Phase 5 D-04 to D-06") with the rule they stand for. Planning IDs mean nothing to readers of the public source.

### Core: IN-01, content type of image formats outside the table
- **D-08:** Add `webp` → `image/webp`, `bmp` → `image/bmp` and `svg` → `image/svg+xml` to `Mail.Builder.CONTENT_TYPES`, so these types resolve the same way on every platform. Update the `attach(Path)` Javadoc, which currently says "twelve extensions" and lists them, to the new count and list. Check the `inline()` Javadoc too. Check the helper's own `CONTENT_TYPES` in `HtmlImages.java`. If it lacks these three, add them there as well, so a helper-made inline image and a core-built one resolve to the same type.

### Core: IN-03, case-sensitive content IDs
- **D-09:** No behaviour change. Content IDs and references stay exact and case-sensitive (Phase 5 D-05, an owner decision). Rejecting IDs that differ only in case would tighten a check in the released 0.2.0 API, and nothing has shown a real problem. Record in `docs/architecture.md` that `Logo` and `logo` are two distinct inline images. Also record that the owner's 2026-11-23 live test did not cover case-differing IDs, and that an author who wants to be safe across clients should not use IDs that differ only in case. Mention it in the `inline()` Javadoc in one sentence.

### Sample: pre-flight validates only the first mail
- **D-10:** Pre-flight calls `mailer.validate` on **every** non-null built mail, not only `mails.get(0)`. Group identical messages by row line, the same way the `invalid` map does with `invalidMail(message, lines, rows)`, so a limit problem shared by 500 rows is one problem line and not 500. Message wording follows the existing pattern, for example "mailer.validate rejected the mail: ...", with the line list from `invalidMail`. `GraphTransport.validate` only does `Files.size` and a body-length estimate, with no token and no HTTP, so validating every row is cheap.
- **D-11:** Test with the `recording` transport or a fake mailer, whichever the existing `SendCommand` tests use. Use a campaign whose first row passes and a later row fails `validate`. Pre-flight must fail with exit code 2, name that row's line, and send nothing.
- **D-12:** Update `docs/spec.md` §10.x / the sample section if it says pre-flight validates "the first mail". The planner greps for it. The sample README gets the same check.

### Helper: IN-01, `strip()`
- **D-13:** Keep `strip()` (the owner accepted the behaviour in 07-UAT test 4). Close the finding with documentation only: add one sentence to the `HtmlImages` class Javadoc saying white space around a `src` value is ignored. Correct the code comment at `HtmlImages.java:348` so it no longer claims the HTML rule.

### Helper: IN-02, untested Error rethrow
- **D-14:** Accept the gap, as the reviewer suggests: "Do not add a seam only for this (Rule 0)". The reader thread and the static `HttpClient` cannot be made to throw an `Error` through WireMock without a new seam. Record the closure in `07-REVIEW-DISPOSITION.md` (IN-02 Source cell: closed in Phase 9, accepted without test, no seam per Rule 0). Add no test seam. If the researcher finds an existing package-private path that reaches `readAtMost` with a custom `InputStream`, without any new seam, a test may be added instead.

### Planning artifacts
- **D-15:** Fix the `07-01-SUMMARY.md` frontmatter so `gsd-tools query summary-extract` parses it. It currently fails with "not parseable YAML", probably an unquoted `: ` inside a list item such as the `provides:` entries. Correct the stale "morimail in provided scope" text to compile scope (changed by 07-04). Verify with `summary-extract`.
- **D-16:** In `08-02-SUMMARY.md` (lines 10 and 104), add that the owner has since released 0.2.0 (tag `v0.2.0`). Keep the historical statement and mark it as superseded, so the record stays true for its date.
- **D-17:** Update `05-REVIEW-DISPOSITION.md`: WR-01 fixed, WR-02 fixed (terminator) with the trade-off documented, IN-01 fixed, IN-02 fixed earlier by 08 IN-03 (spec 10.3 aligned; the planner confirms the decision-ID comments are gone after D-07), IN-03 deferred with a reason (D-09). Update the 07 disposition for IN-01 and IN-02 (D-13, D-14).

### Phase gates (carried forward, not re-decided)
- Every unit ends with `./mvnw verify` green offline and a commit as Stephan Wald <stephan@morisoft.de>, with the `GIT_*` identity exported. Javadoc stays doclint-clean. `-Prelease verify -Dgpg.skip -pl morimail,morimail-html -am` stays green.
- Failure messages hold fixed texts and never echo data values or addresses. D-01 is the one place that echoes HTML-read text, and it is now cleaned.
- No new dependency, option or hook (Rule 0).

### Claude's Discretion
- How to split the work into plans. A natural cut is one plan for the core and the three pattern copies (D-01 to D-09), one for the sample pre-flight (D-10 to D-12), and one for the helper docs and planning artifacts (D-13 to D-17). One plan is also fine if it stays small.
- Exact Javadoc and architecture wording, and the exact test names.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Scope and findings
- `.planning/v0.2.0-MILESTONE-AUDIT.md` — the tech-debt list this phase closes
- `.planning/ROADMAP.md` (Phase 9 section) — fixed scope and what is not in scope
- `.planning/phases/05-inline-images-in-core-and-graph/05-REVIEW.md` — WR-01, WR-02, IN-01, IN-03 with the proposed fixes
- `.planning/phases/05-inline-images-in-core-and-graph/05-REVIEW-DISPOSITION.md` — dispositions to update
- `.planning/phases/07-html-image-helper-module/07-REVIEW.md` — IN-01, IN-02
- `.planning/phases/07-html-image-helper-module/07-REVIEW-DISPOSITION.md` — owner deferral of IN-01/IN-02 in 07-UAT test 4

### Prior owner decisions that constrain this phase
- `.planning/phases/05-inline-images-in-core-and-graph/05-CONTEXT.md` — D-04 (whole-HTML scan, amended 2026-11-22), D-05 (exact, case-sensitive IDs), D-06 (terminators), D-09 (content ID characters), D-10 (image type resolution)
- `.planning/phases/06-inline-images-in-the-serial-mail-sample/06-CONTEXT.md` — sample pre-flight design
- `.planning/phases/07-html-image-helper-module/07-UAT.md` — test 4, owner accepted the helper IN-01/IN-02 behaviour

### Binding spec and docs
- `docs/spec.md` §10.3 (lines ~390-420), §11 (Rule 0), and the sample pre-flight section (~line 333)
- `docs/architecture.md` line 40 (the §10.3 `cid:` row) and line 52 (Graph inline notes, live result)
- `CLAUDE.md` — Rule 0 and the public-repo rules

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `Mail.Builder.checkReferences` (`morimail/src/main/java/de/morisoft/morimail/Mail.java:509-533`): already cuts the shown ID at 64 code points. D-01 adds the cleaning step there.
- `SendCommand` pre-flight (`morimail-sample-serial/.../SendCommand.java:~170-215`): the `invalid` map plus `invalidMail(message, lines, rows)` (line 122) already groups build failures by line. D-10 reuses it for `validate` failures.
- `cidPatternAgreesWithTheCore` tests in the sample and the helper: a corpus of inputs checked against `Mail.build()`. D-05 extends it.

### Established Patterns
- The three `CID_REFERENCE` copies must change together (`Mail.java:48`, `Campaign.java:48`, `HtmlImages.java:111`). The sample's dry-run rewrite (`SendCommand.java:316`) also uses the pattern.
- `CONTENT_TYPES` tables exist in both `Mail.Builder` and `HtmlImages`. Keep them consistent for image types.
- Messages are fixed texts. `InvalidMailException` for build problems, `PermanentMailException` from `mailer.validate`.

### Integration Points
- `GraphTransport.validate` (`morimail/src/main/java/de/morisoft/morimail/graph/GraphTransport.java:94`) is local-only (file sizes plus an estimate), so per-row validation in pre-flight is safe and cheap.

</code_context>

<specifics>
## Specific Ideas

- WR-01 cleaning snippet suggested by the reviewer (05-REVIEW.md WR-01 Fix). Add `FORMAT` to the types it replaces.
- New corpus entries: `<p>cid:logo</p>` resolves to `logo`. `src=cid:logo/>` stays documented as reading `logo/`.

</specifics>

<deferred>
## Deferred Ideas

- Restricting `cid:` matching to attribute and `url(...)` context (WR-02 option 1). It reverses owner decision D-04. Revisit only if the owner asks.
- Rejecting content IDs that collide ignoring case (IN-03). Revisit if a live test shows trouble with case-differing IDs.
- Git housekeeping (push main so Temurin 17 CI runs, and track or delete `07-PATTERNS.md` and `state.json`). This is the owner's job, per ROADMAP.

</deferred>

---

*Phase: 09-v0-2-0-tech-debt-cleanup*
*Context gathered: 2026-11-24*
