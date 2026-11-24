# Phase 5: Inline Images in Core and Graph - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md. This log preserves the alternatives considered.

**Date:** 2026-11-22
**Phase:** 05-inline-images-in-core-and-graph
**Areas discussed:** What counts as a cid: ref, Size limits, Builder API and content ID rules, Error messages, plus the HTML image helper (raised by the owner)

---

## What counts as a cid: ref

| Option | Description | Selected |
|--------|-------------|----------|
| Any cid: in the HTML | Regex anywhere: src, CSS url(), background= | ✓ |
| Only src="cid:..." | Closer to spec wording, misses CSS | |

| Option | Description | Selected |
|--------|-------------|----------|
| Prefix any case, ID exact | cid:/CID: both count, ID case-sensitive, no URL-decoding | ✓ |
| Everything exact | Lowercase cid: only | |
| ID case-insensitive too | cid:LOGO matches logo | |

| Option | Description | Selected |
|--------|-------------|----------|
| End at quote, space, ) or > | Covers src="cid:x", src=cid:x, url(cid:x) | ✓ |
| Restrict IDs to [A-Za-z0-9._@-] | Character rule on content IDs | |

## Size limits

| Option | Description | Selected |
|--------|-------------|----------|
| Keep today's limits | 3 MiB per file, 3 MiB total, 4 MiB request estimate; fix the roadmap wording | ✓ |
| Raise total to 4 MiB | Changes v0.1.0 behaviour and spec §6.2 | |

| Option | Description | Selected |
|--------|-------------|----------|
| Same message text | No inline-specific variants | ✓ |
| Say "inline image" | One extra branch | |

## Builder API and content ID rules

| Option | Description | Selected |
|--------|-------------|----------|
| .inline(path, id) + attach(Attachment) | No extra overload | ✓ |
| Also .inline(path, id, contentType) | One more public method | |

| Option | Description | Selected |
|--------|-------------|----------|
| Not blank, no line break, no cid-ending char | Plus angle brackets | ✓ |
| Only blank and line breaks | Exactly the requirement wording | |

| Option | Description | Selected |
|--------|-------------|----------|
| Any image/* type | Prefix check | ✓ |
| Only png, jpeg, gif | Fixed allow-list | |

## Error messages

| Option | Description | Selected |
|--------|-------------|----------|
| Name the content ID | e.g. "no inline image for cid:logo" | ✓ |
| Index only | Fixed text with attachments[i] | |

| Option | Description | Selected |
|--------|-------------|----------|
| First problem only | Same as other build() checks | ✓ |
| All inline problems at once | New pattern | |

| Option | Description | Selected |
|--------|-------------|----------|
| In build(), InvalidMailException | .inline() only requireNonNull | ✓ |
| Immediately in .inline() | IllegalArgumentException | |

## HTML image helper (raised by the owner)

The owner asked whether an HTML parser that turns file:// and http(s):// image URLs into cid: references was planned. Answer: no. The spec doesn't ask for it, and REQUIREMENTS.md lists automatic cid: rewriting as out of scope under Rule 0. Risks discussed: placeholder injection (internal hosts or local files), network I/O in build(), repeated downloads per mail, and the lack of an HTML parser in the JDK. The owner wants the convenience. Proposal: a helper that converts the template once, before rendering.

| Option | Description | Selected |
|--------|-------------|----------|
| Package in main jar, local only | JDK regex, no new artifact | |
| Sub-module morimail-html | Own jar, may use jsoup and http(s) | ✓ |

| Option | Description | Selected |
|--------|-------------|----------|
| Local files only | Remote stays remote | |
| http(s) opt-in | Download to temp, timeout, size cap | ✓ |

| Option | Description | Selected |
|--------|-------------|----------|
| New phase in v0.2.0 after Phase 5 | Spec §10 subsection first | ✓ |
| Next milestone | Deferred idea only | |

## Claude's Discretion

- Graph inline property names and the angle-bracket form (from the Microsoft docs)
- Shape of the live test (one method, PNG generated in the test)
- Regex details, message wording, check order, test layout

## Deferred Ideas

- `morimail-html` sub-module: new v0.2.0 phase after Phase 5 (details in CONTEXT.md)
