---
phase: 04-docs-and-release-readiness
plan: 03
subsystem: docs
tags: [readme, quick-start, exchange-limits, dry-run]
status: complete

requires:
  - phase: 04-docs-and-release-readiness
    provides: 04-02 docs/entra-setup.md, which the README links for a real send
provides:
  - README.md with what morimail is and is not, a quick start that compiled against the packaged jar, the sample dry run and the setup link
affects: [04-04]

actuals:
  tokens: 1300
  tasks: 1
  commits: 1
plan_head_before: 3c45164ea94d77bea00a449bfaa5de06c7c1329f
plan_head_after: eb0e7a7a11b52b41e87a6571b182a1c15960259c

tech-stack:
  added: []
  patterns:
    - "the README snippet is compile-checked once with javac against the packaged jar in the scratchpad, with no mirror test in the repository (D-09)"

key-files:
  created: []
  modified:
    - README.md

key-decisions:
  - "04-03: README quick start has three catches, one per category (D-08), with sender-rejected named in the permanent catch; checked once with javac, no mirror test (D-09)"

requirements-completed: [DOCS-03]

coverage:
  - id: D1
    description: "README.md has the eight sections in order, says what morimail is not (not a bulk mailer, 30 per minute and 10,000 recipients per day, 100 external recipients for onmicrosoft.com, sample default 20, under 3 MiB), and keeps the status, coordinates, delivery and keystore text verbatim"
    requirement: DOCS-03
    verification:
      - kind: command
        ref: "plan verify gate 1 (headings, required strings, stub gone, kept sentences verbatim)"
        status: pass
    human_judgment: false
  - id: D2
    description: "The quick start compiles against the real API"
    requirement: DOCS-03
    verification:
      - kind: command
        ref: "javac --release 17 -Xlint:all -Werror -cp morimail-sample-serial/target/lib/* on the first java block of README.md: exit 0, QuickStart.class produced"
        status: pass
    human_judgment: false
  - id: D3
    description: "The block reads the certificate password from the environment and has exactly three catches in the order transient, permanent, auth-required, with SenderRejectedException named inside the permanent catch"
    requirement: DOCS-03
    verification:
      - kind: command
        ref: "plan verify gate 3 (required calls, no extra catch, catch order)"
        status: pass
    human_judgment: false
  - id: D4
    description: "The README dry-run command works as written and creates no journal"
    requirement: DOCS-03
    verification:
      - kind: command
        ref: "README command with --out in the scratchpad and no secret variables: exit 0, dry run: 5 mails written to, no journal.jsonl in the example campaign"
        status: pass
    human_judgment: false
  - id: D5
    description: "The README reads as a clear, honest first page for a developer"
    requirement: DOCS-03
    verification: []
    human_judgment: true
    rationale: "Wording and tone are a reader's judgment; structure and facts were checked by script"

duration: 6min
completed: 2026-11-22
---

# Phase 4 Plan 03: README quick start Summary

**README.md now says what morimail is and is not (not a bulk mailer, the Exchange limits, under 3 MiB per file and in total), and its quick start has three catches, one per error category, and compiled clean with `javac --release 17 -Xlint:all -Werror` against the packaged jar.**

## Accomplishments

- The intro drops the "in development" note, names the `serial-mail` sample and its journal, and links `docs/entra-setup.md` and `docs/architecture.md`.
- The eight `##` sections are in the planned order. Status, Coordinates, Delivery semantics, Tests, License and Security keep their text verbatim (the three sentence checks pass).
- The quick start is one `QuickStart` class: `EntraClientCredentials.withCertificate` with zero GUIDs and the password read from `MAIL_CERT_PASSWORD`, a `MailAccount` for transport `graph`, `Mailer.of(account)`, a `MailTemplate`, `Mail.using(...).template(...).attach(...).build()` and `mailer.send(mail)`. It has three catches in the order `TransientMailException` (retry later, after `e.retryAfter()` if present), `PermanentMailException` (drop or fix it; a `SenderRejectedException` means fix the sending mailbox or its grant) and `AuthRequiredException` (stop until credentials or consent are fixed).
- After the block come the two dry-run commands (no secrets, output in `out/`), the setup link for a real send, and the D-03 sentence that the sample sends no `saveToSentItems`, so every campaign mail also lands in Sent Items.
- `## What morimail is not` has four bullets: not a bulk mailer, the Exchange limits (30 messages per minute, 10,000 recipients per day, 100 external recipients per 24 hours for onmicrosoft.com, sample default 20 per minute, Azure Communication Services for bulk), the 3 MiB (3 x 1024 x 1024 bytes) attachment limit per file and in total, and one body per mail on Graph.

## Gate results

- Compile gate: `package -DskipTests` then `javac --release 17 -Xlint:all -Werror` on the extracted block, in the scratchpad. Exit 0, `QuickStart.class` produced. No class or test was added to the repository (D-09).
- README gate, block gate (required calls, no extra catch, catch order 33 < 36 < 38 < 41), dry-run gate (exit 0, `dry run: 5 mails written to`, no journal): all pass.
- Hygiene: no address other than example ones, no GUID other than the zero GUID, no absolute home path. No 04-03 commit adds a module file or touches a POM, the CI file or a source file.
- `./mvnw -B -ntp verify`: BUILD SUCCESS (137 sample tests, library tests green), run before the commit.
- Commit gate: the README commit holds only `README.md`, is authored and committed as the maintainer with the Co-Authored-By trailer, and is not on origin/main.
- No real mail was sent and no live test was run. Only `--dry-run` was used.

## Task Commits

1. Task 1: `eb0e7a7` docs(04-03): write the README quick start and its limits

## Deviations from Plan

None - plan executed exactly as written. One small addition: each catch body prints one line to `System.err` so a copied block does something visible; the plan allowed comment-only catches, and the block still compiles clean under `-Xlint:all -Werror`.

## Known Stubs

None.

## Threat Flags

None. The plan adds documentation only.

## Next Phase Readiness

Ready for 04-04. The README links `docs/entra-setup.md` and its quick start matches the current API.

## Self-Check: PASSED

- README.md exists and carries the edits.
- Commit eb0e7a7 is an ancestor of HEAD, authored and committed as the maintainer with the Co-Authored-By trailer.
