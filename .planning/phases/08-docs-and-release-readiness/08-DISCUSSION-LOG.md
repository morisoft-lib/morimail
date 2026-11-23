# Phase 8: Docs and Release Readiness - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-11-23
**Phase:** 08-docs-and-release-readiness
**Areas discussed:** README layout + version, How far to edit spec.md, Sample docs: --help + README, release.md for two jars

The owner's live inline result (05-UAT test 1, pass 2026-11-23) was already on record, so it was not asked about.

---

## README layout + version

| Question | Options | Selected |
|----------|---------|----------|
| Inline example placement | New section after Quick start / Fold into Quick start | New section after Quick start |
| HtmlImages | Own section with its own coordinates / Under the inline section | Own section with its own coordinates |
| Coordinates version before release | 0.2.0 for both artifacts, with a note / Keep 0.1.0, add 0.2.0 later | 0.2.0 with a note |
| Repeat the limits | Yes, 2-3 sentences / Only link to architecture.md | Yes, 2-3 sentences |

## How far to edit spec.md

| Question | Options | Selected |
|----------|---------|----------|
| §5/§5.1 contentId wording | Edit in place, drop the deviation row / Add an "up to 0.1.0" note | Edit in place, drop the row |
| Where the Graph inline names go | New bullets in §6.2 / A new §6.4 | New bullets in §6.2 |
| §10.3/§10.4/§12 | Mark as done, keep the text / Leave alone | Mark as done |
| Live wording | Confirmed 2026-11-23, clients named / Name the desktop client | Confirmed, clients named as reported |

## Sample docs: --help + README

| Question | Options | Selected |
|----------|---------|----------|
| --help | picocli footer listing the campaign files / Only the --dry-run option text | picocli footer |
| README | New "Serial-mail sample" section / One paragraph | New section |
| Test the help text | Yes, one assertion / No test | Yes |

## release.md for two jars

| Question | Options | Selected |
|----------|---------|----------|
| Shape | Version-neutral with VERSION placeholder / 0.2.0 literals | Version-neutral |
| Deploy -pl | -pl morimail,morimail-html -am / Full reactor with deploy.skip | -pl morimail,morimail-html -am |
| Rehearsal | Yes, local skipUpload with a throwaway key / No, list derived on paper | Yes, rehearse |
| 0.2.0 pre-check | No, keep generic / Yes, a short checklist | No |

## Claude's Discretion

- Prose and section order inside the new README sections
- Scratch copy or a reverted version change for the rehearsal

## Deferred Ideas

None.
