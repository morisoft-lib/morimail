# Phase 7: HTML Image Helper Module - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-11-23
**Phase:** 07-html-image-helper-module
**Areas discussed:** Failed downloads, Which src values count, Content ID generation, API shape and content type, Storable form (raised by owner), Module dependency direction (raised by owner)

---

## Failed downloads

| Question | Options | Selected |
|---|---|---|
| Exception category | Split by cause / Always Invalid / Always Transient | Split by cause (network, timeout, 5xx, 429 → Transient; 4xx, non-image, over cap → Invalid) |
| Size cap | Fixed 3 MiB / Caller-given / Fixed 1 MiB | Fixed 3 MiB |
| Timeouts | Fixed 10 s / 30 s / Caller-given Duration | Fixed 10 s connect, 30 s request |
| Redirects | Redirect.NORMAL / Redirect.NEVER | NORMAL |
| Remote content type | Header only / Header else file name | Header only |

## Which src values count

| Question | Options | Selected |
|---|---|---|
| Attribute forms | Quoted and unquoted / Quoted only | Quoted and unquoted, original quoting kept, srcset untouched |
| Local path | Decode as URI reference / Literal path | Decode as URI reference |
| Absolute forms | Resolve then inside-check / Leading '/' = base root / Reject | Resolve then inside-check |
| Symlinks | Real-path check / Normalized only | Real-path check |
| Windows drive | Unknown scheme, unchanged / One letter = drive | Unknown scheme, unchanged |
| Entities | Decode &amp; only / None | Decode &amp; only |

## Content ID generation

| Question | Options | Selected |
|---|---|---|
| Reduce characters | Replace D-09 chars with '-' / Keep only [A-Za-z0-9._-] / Drop | Keep only [A-Za-z0-9._-] (others → '-') |
| Fallback | 'image' / Fail | 'image' |
| Suffix | logo, logo-2 / logo-1, logo-2 | logo, logo-2, logo-3 |
| Remote ID and same source | URL last segment + unique temp file / Save under own name | URL last segment + unique temp file |
| Existing cid: IDs | Avoid / Ignore | Avoid |
| Recipient filename | Source last segment / ID plus extension | Source last segment |
| Collapse runs | No / Yes | No |

## API shape and content type

| Question | Options | Selected |
|---|---|---|
| Signature | Two overloads `embed` / One nullable / Named `convert` | Two overloads of `embed` |
| Local type | Copy table, pin by test / Probe through build() / Leave null | Copy table, pin by test |
| Nothing to do | Return as is / Fail on null HTML | Return as is, empty list |
| Base dir check | Only when needed / Always up front | Only when needed |

## Storable form (raised by owner)

**Owner question:** "Is the template serializable, so it can be stored as a large string (the mail HTML plus the images as b64)?"
**Answer given:** Not as designed. `Attachment` holds a path, and `data:` stayed unchanged per spec §10.4.
**Owner's choice:** in this milestone. After asking "what's better?", the owner accepted the recommendation: HTML with `data:` URIs as the stored form (rejected alternative: a JSON pack), folded into Phase 7 (rejected alternative: Phase 7.1). The owner confirmed the two-form model: the stored form keeps the images, and the send form is built from it at send time.
**Follow-up edits:** spec §10.4 "Storable form" bullet, HTML-07 in REQUIREMENTS.md, roadmap success criterion 7.

## Module dependency direction (raised by owner)

**Owner:** "Make sure that the dependency between the modules remains loose, so we don't forcefully inherit morimail-html's dependency graph in morimail."
**Captured as:** D-01..D-03. The dependency is one-way, `morimail-html` has no runtime dependencies of its own, and it declares `morimail` with `provided` scope. A verification step proves it.

## Claude's Discretion

- Regex details, the method decomposition inside `HtmlImages`, message wording, test layout, Javadoc wording, and possibly a simpler overload set for `toDataUris`.

## Deferred Ideas

- Serial sample using `HtmlImages`
- JSON pack format
- Rewriting srcset, CSS url() or background=
