# Phase 6: Inline Images in the Serial-Mail Sample - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-11-23
**Phase:** 06-inline-images-in-the-serial-mail-sample
**Areas discussed:** Pre-flight source, Dry-run layout, OS junk in inline/, Example campaign logo

---

## Pre-flight source

| Option | Description | Selected |
|--------|-------------|----------|
| Template scan in Campaign | Scan body.html and the inline/ list, one problem per defect; build() stays backstop | ✓ |
| Rely on build() only | First problem only per rerun | |

**User's choice:** Template scan in Campaign

| Option | Description | Selected |
|--------|-------------|----------|
| Report a problem | File name giving an invalid content ID is a pre-flight problem naming the file | ✓ |
| Sanitize the ID | Replace bad characters with `_` | |

**User's choice:** Report a problem

| Option | Description | Selected |
|--------|-------------|----------|
| Strip the last extension | `logo.v2.png` → `logo.v2` | ✓ |
| Strip from the first dot | `logo.v2.png` → `logo` | |

**User's choice:** Strip the last extension

---

## Dry-run layout

| Option | Description | Selected |
|--------|-------------|----------|
| Once into out/inline/ | Mirrors campaign layout, no collisions | ✓ |
| Once into out/ flat | Beside the HTML files | |
| Once per mail | One directory per recipient | |

**User's choice:** Once into out/inline/

| Option | Description | Selected |
|--------|-------------|----------|
| Percent-encode the file name | JDK URI encoding | ✓ |
| Plain file name | Insert as is | |

**User's choice:** Percent-encode the file name

---

## OS junk in inline/

| Option | Description | Selected |
|--------|-------------|----------|
| Dot-files only, same as attachments/ | Thumbs.db fails pre-flight with a clear message | ✓ |
| Dot-files plus Thumbs.db and desktop.ini (both dirs) | Hard-coded list | |
| Dot-files plus the two names, inline/ only | Hard-coded list, inline/ only | |

**User's choice:** Dot-files only, same as attachments/

---

## Example campaign logo

| Option | Description | Selected |
|--------|-------------|----------|
| Keep both, add a new tiny inline/logo.png, rename attachment to sample.png | §8.1 holds, no confusion | ✓ |
| Keep both, no rename | Same name in two directories | |
| Move the PNG to inline/ | Breaks §8.1, 60 KB not small | |

**User's choice:** Keep both, add a new tiny inline/logo.png (rename attachment to sample.png)

| Option | Description | Selected |
|--------|-------------|----------|
| Top of the body, plain `<img>` | Above the greeting, plus one explanatory sentence | ✓ |
| You decide | | |

**User's choice:** Top of the body, plain `<img>`

---

## Claude's Discretion

- Stdout listing of inline images in dry-run
- How the non-image check gets a content type that agrees with the core
- Copying the `cid:` pattern into the sample vs another route
- Campaign record shape, message wording, test layout

## Deferred Ideas

- Skipping Thumbs.db/desktop.ini in attachments/ (not changed)
- Sample docs for inline/ (Phase 8)
