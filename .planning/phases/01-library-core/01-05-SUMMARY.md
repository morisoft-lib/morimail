---
phase: 01-library-core
plan: 05
subsystem: core
tags: [templates, builder, html-escaping, attachments, content-type, validation]

requires:
  - phase: 01-library-core/03
    provides: Mail, MailAddress, Attachment, MailMessage records, InvalidMailException, fixed-order build() skeleton
  - phase: 01-library-core/04
    provides: MailAccount, Mailer, RecordingTransport fixture used to read the rendered message back
provides:
  - MailTemplate record and package-private TemplateRenderer with one Pattern and a manual find loop
  - Full Mail.Builder surface - to/cc/bcc (String... and MailAddress...), from/replyTo (String and MailAddress), html, template(MailTemplate, Map<String, ?>), attach(Path), attach(Path, String), attach(Attachment)
  - build() in the final fixed order with attachment checks and content-type resolution (twelve-entry map, then Files.probeContentType, then application/octet-stream)
  - TemplatedSendTest, TemplateRendererTest, extended MailBuilderTest (44 methods) and MailModelTest
affects: [01-06, phase-02, phase-03]

actuals:
  tokens: 13500
  tasks: 3
  commits: 4

plan_head_before: 8f858d53ba6aba7c1fafe4c940ad447b25bc332f
plan_head_after: 6236a2c151a348d1e13009921bec2030c1b1c459
commits: 4

tech-stack:
  added: []
  patterns:
    - one precompiled placeholder Pattern and a manual Matcher.find() loop; no data value ever reaches a regex replacement API
    - build() writes only local variables, so a second build() on the same builder returns an equal Mail
    - exception messages name part, placeholder, field and index, never an address, subject or data value

key-files:
  created:
    - morimail/src/main/java/de/morisoft/morimail/MailTemplate.java
    - morimail/src/main/java/de/morisoft/morimail/TemplateRenderer.java
    - morimail/src/test/java/de/morisoft/morimail/TemplatedSendTest.java
    - morimail/src/test/java/de/morisoft/morimail/TemplateRendererTest.java
    - morimail/src/test/java/de/morisoft/morimail/MailModelTest.java
  modified:
    - morimail/src/main/java/de/morisoft/morimail/Mail.java
    - morimail/src/test/java/de/morisoft/morimail/MailBuilderTest.java

key-decisions:
  - "template(t, data) copies the map with new HashMap at call time, because Map.copyOf rejects the present null values that must render as the text null"
  - "Attachment checks and content-type resolution live in build(), not in attach(...), so attach on a missing path fails with the builder's own message and build() can be repeated"
  - "A null Path or Attachment argument to attach throws NullPointerException at call time; an Attachment record whose file is null is reported by build() as 'attachments[i]: file is missing'"
  - "The two builder append helpers (String[] and MailAddress[]) are shared by to, cc and bcc; a null MailAddress element is stored and reported by build() as '<field>[i]: address is missing'"

patterns-established:
  - "RED commit with compiling signature stubs: the new overloads exist as UnsupportedOperationException stubs so the tests fail on behaviour, not on compilation"
  - "Parameterized test per assumption-guarded input, so each platform-dependent skip is reported separately"

requirements-completed: [MODEL-01, MODEL-02, MODEL-03, MODEL-04, MODEL-05, TMPL-01, TMPL-02, TMPL-03, TMPL-04]

coverage:
  - id: D1
    description: "A templated mail built from a Map<String, String> row with one attachment is recorded with rendered subject, escaped html, text and resolved attachment metadata"
    requirement: "TMPL-01"
    verification:
      - kind: unit
        ref: "morimail/src/test/java/de/morisoft/morimail/TemplatedSendTest.java#templatedMailWithOneAttachmentIsRecordedRendered"
        status: pass
    human_judgment: false
  - id: D2
    description: "Renderer grammar, HTML escaping, raw form, literal $ and backslash, no re-scan, subject line breaks, missing-field errors"
    requirement: "TMPL-02"
    verification:
      - kind: unit
        ref: "morimail/src/test/java/de/morisoft/morimail/TemplateRendererTest.java (15 methods, 30 executions)"
        status: pass
    human_judgment: false
  - id: D3
    description: "Every address form, attach form, content-type rule and build() rejection in the fixed check order"
    requirement: "MODEL-03"
    verification:
      - kind: unit
        ref: "morimail/src/test/java/de/morisoft/morimail/MailBuilderTest.java (44 methods) and MailModelTest.java (6 methods)"
        status: pass
    human_judgment: false

duration: about 25 min
completed: 2026-11-21
status: complete
---

# Phase 1 Plan 05: Templated mail with attachments, and the full builder Summary

**`Mail.using(id).to(..).template(t, data).attach(p).build()` now renders subject, html and text from a `Map<String, String>` row with HTML escaping, resolves attachment metadata, and `build()` reports the first unsendable problem as one `InvalidMailException` that never echoes a value.**

## Performance

- **Duration:** about 25 min
- **Tasks:** 3 (one tracer, two TDD)
- **Files:** 5 created, 2 modified (7 files, 1124 insertions, 17 deletions)
- **Commits:** 4, measured from the ledger (`plan_head_before` to `plan_head_after`)

## Accomplishments

- `TemplateRenderer` is a package-private final class with one `Pattern` (`\$\$(!?)([A-Za-z0-9_.-]+)\$\$`) and a manual find loop. Html values are escaped for exactly `& < > " '`, `$$!name$$` inserts raw, subject values turn every `\R` into one space, text values are inserted unchanged. A present `null` value renders as the text `null`; only an absent key is "missing". `appendReplacement` is not used anywhere.
- `Mail.Builder` offers `to`, `cc`, `bcc` with `String...` and `MailAddress...` (append, call order, duplicates kept), `from` and `replyTo` with either form (last call wins), `html`, `template`, and the three attach forms. There is no two-string address overload.
- `build()` checks in the fixed order: accountId, template-versus-explicit conflict, rendering, recipients across to/cc/bcc, subject, body, addresses (from, to, cc, bcc, replyTo), subject line break, attachments in order. It writes only locals; the builder and its conflict tracking are untouched, so repeated builds are equal.
- Attachment diagnostics use `Path.getFileName()` only; a path without a file name (file-system root) gives "path has no file name" instead of a `NullPointerException`.
- Content type: twelve-entry extension map matched with `Locale.ROOT`, then `Files.probeContentType`, then `application/octet-stream`. The `attach(Path)` Javadoc says that only the twelve mapped extensions are deterministic across systems and that other extensions are best effort.

## TDD record

- **Task 1 (tracer):** committed as `feat` `1ab9dd3`; no RED commit (tracer type, not `tdd="true"`).
- **Task 2:** the renderer was already complete from the tracer, so the 15 new test methods passed on first run (unexpected green, documented, not a test-first RED). To show the tests are not vacuous, a mutation probe (apostrophe escape disabled, line-break pattern changed to `\n`) made 8 of the 30 executions fail; the file was restored with `git checkout -- <file>`. Commit `0a1c891`.
- **Task 3 RED** `a1c54b6`: tests plus compiling signature stubs for the nine missing builder overloads (`UnsupportedOperationException`). `gsd check tdd-red-evidence` returned `RED_EVIDENCE_OK`, `target_test_failed`: 13 of the 45 builder tests failed, target `addressFormsAppendInCallOrder` failed on the stub, not on compilation or a fixture. Semantic assessment: the target executed and failed because the overload had no behaviour yet. The RED commit does contain a production file (the stubs) so that the tests compile; the commit therefore builds but its test run is red.
- **Task 3 GREEN** `6236a2c`: stubs replaced by the real overloads and the final check order; all 51 tests in the two classes pass. No REFACTOR commit was needed.

## Task Commits

1. **Task 1: templated mail with one attachment end to end** - `1ab9dd3` (feat)
2. **Task 2: renderer grammar and escaping edge cases** - `0a1c891` (test)
3. **Task 3 RED: builder surface and rejection tests** - `a1c54b6` (test)
4. **Task 3 GREEN: every address and attach form** - `6236a2c` (feat)

## Skipped tests

None. The two assumption-guarded tests, `rejectsUnreadableAttachmentFile` and the two executions of `unknownOrMissingExtensionFallsBackToOctetStream` (`data.unknownext`, `noextension`), ran and passed on this machine (the file really became unreadable, and `Files.probeContentType` returned `null` for both names). `grep -c '<skipped'` on the `MailBuilderTest` Surefire report is 0. On a platform where either assumption fails the report lists the skip with its message.

## Decisions Made

See `key-decisions` above. Deliberate scope choices: no injection hook for `Files.probeContentType` (Rule 0, D-11), so the octet-stream test stays assumption-guarded.

## Deviations from Plan

None - plan executed as written, with these notes:

- Verification commands were run with `-o` (offline) in place of the plan's online forms; the plan's own final verification asks for offline mode to pass as well. The build is green in every mode run: `./mvnw -B -ntp -o verify`, `./mvnw -B -ntp -o -Prelease clean verify -Dgpg.skip -pl morimail -am` (no WARN lines), and a final default `verify`.
- The two OOXML content-type strings sit on one source line each (instead of being split with `+`) so that the Task 1 grep acceptance count (>= 4) holds.
- The email-domain gate regex over `morimail/` exits 1 (no foreign addresses); `git log --author` check for foreign identities prints an empty list.

## Issues Encountered

None.

## Known Stubs

None. The RED stubs were replaced in the GREEN commit; `grep UnsupportedOperationException` finds nothing in `Mail.java`.

## Threat Flags

None. No new network, auth or file-access surface beyond the attachment file checks the plan's threat model already covers (T-01-11 to T-01-15).

## Next Phase Readiness

Plan 01-06 can proceed: `Mail.Builder` methods it uses are unchanged, and the files it touches (`Mailer`, `TransportRegistry`, `MailAccount`, `RecordingTransport`) were not modified here.

## Self-Check: PASSED

- Created files exist on disk: MailTemplate, TemplateRenderer, TemplatedSendTest, TemplateRendererTest, MailModelTest.
- Commits `1ab9dd3`, `0a1c891`, `a1c54b6`, `6236a2c` are ancestors of HEAD and carry the maintainer identity and the Co-Authored-By trailer.
- All task acceptance greps pass; the three build commands end in BUILD SUCCESS.
