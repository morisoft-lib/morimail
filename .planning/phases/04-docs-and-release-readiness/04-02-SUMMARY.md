---
phase: 04-docs-and-release-readiness
plan: 02
subsystem: docs
tags: [entra, exchange, rbac, setup-guide, gitignore, key-hygiene]
status: complete

requires:
  - phase: 04-docs-and-release-readiness
    provides: 04-01 live findings in spec 6.3 and 8.3 and the contracts page in docs/architecture.md
provides:
  - docs/entra-setup.md, the owner's guide from app registration to a second run that sends nothing
  - .gitignore rules for *.p12, *.key, *.pem and *.cer with the tracked test keystore exempt
  - four dated Microsoft-doc difference bullets in docs/architecture.md
affects: [04-03, 04-04]

actuals:
  tokens: 3900
  tasks: 1
  commits: 1
plan_head_before: c5ac4009d8772ad1806b5642b7da352cd137896a
plan_head_after: 7f07748d604cd75dfc54f65a765885803db3b3dd

tech-stack:
  added: []
  patterns:
    - "secrets reach the shell only through read -rs and an exported variable; openssl reads the password with -passout env:"

key-files:
  created:
    - docs/entra-setup.md
  modified:
    - .gitignore
    - docs/architecture.md

key-decisions:
  - "04-02: the guide's scope filter example uses Alias, because the recipient-filter page says to use EmailAddresses and not PrimarySmtpAddress"
  - "04-02: every open item lives in the closing section Not yet verified live; the steps carry only the two allowed caveat lines (D-04, D-06)"

requirements-completed: [DOCS-01, DOCS-02]

coverage:
  - id: D1
    description: "docs/entra-setup.md has the eleven spec 9 headings in order, the cmdlets from the re-read Microsoft page, the exact openssl and sample commands, and six closing items"
    requirement: DOCS-01
    verification:
      - kind: command
        ref: "plan verify gates 1 to 3 (headings, required strings, cmdlet parts, exact commands, six closing bullets, two caveat lines, no secret value)"
        status: pass
    human_judgment: false
  - id: D2
    description: "The guide's openssl recipe and dry-run command work as written"
    requirement: DOCS-01
    verification:
      - kind: command
        ref: "openssl req and pkcs12 in a scratch directory, keytool -list shows PrivateKeyEntry; dry run on a scratch copy of the example campaign prints dry run: 5 mails written to, writes five subject files, creates no journal"
        status: pass
    human_judgment: false
  - id: D3
    description: "Key files cannot be committed by accident and the tracked test keystore is unaffected"
    requirement: DOCS-01
    verification:
      - kind: command
        ref: "git check-ignore --no-index for app.p12, app.key, app.pem, app.cer; the test keystore is not ignored and still tracked"
        status: pass
    human_judgment: false
  - id: D4
    description: "docs/architecture.md records the four Microsoft-doc differences found while writing the guide"
    requirement: DOCS-02
    verification:
      - kind: command
        ref: "architecture gate: Exchange Administrator, Organization Management, Test-ServicePrincipalAuthorization, PrimarySmtpAddress, onmicrosoft.com, SMTP all present in the differences section"
        status: pass
    human_judgment: false
  - id: D5
    description: "A reader can follow the guide on a real tenant from registration to a second run that sends nothing"
    requirement: DOCS-01
    verification: []
    human_judgment: true
    rationale: "Needs a real tenant and real sends, which the executor never does; this is the owner's walk-through in 04-04"

duration: 3min
completed: 2026-11-22
---

# Phase 4 Plan 02: Setup guide Summary

**docs/entra-setup.md takes the owner from an empty app registration to a campaign whose second run prints `accepted 0, skipped <rows>, failed 0`, with the Exchange cmdlets copied from Microsoft's RBAC page re-read on 2026-11-22 and every open item kept in a closing section.**

## Accomplishments

- The guide has the eleven `##` sections of spec 9 in order, from "Before you start" to "Not yet verified live". Path A is stated as the path verified live on 2026-11-22, and path B as recommended but not yet verified live, in one sentence at the start of step 3.
- Step 2 gives the two openssl commands exactly as the plan fixes them, with the password read by `read -rs` and passed through `-passout env:MAIL_CERT_PASSWORD`. `-legacy` is a labelled fallback and the client secret is the quick alternative.
- Step 9 is a chain of single commands: build, dry run, `--limit 1` to the owner's own address, the full run on the same campaign copy, and the same command again. The guide explains the exit codes, resuming an interrupted run (at-least-once), and the journal.
- Key and secret hygiene: key files and the campaign copy live outside the repository, `app.key` is deleted once `app.p12` exists, no password or secret appears as a value, and `account.properties` holds only variable names. `.gitignore` now ignores `*.p12`, `*.key`, `*.pem` and `*.cer`, and exempts the tracked throwaway test keystore.
- docs/architecture.md has four new dated bullets under "Differences from the Microsoft documentation": the Exchange Administrator role besides Organization Management, `Test-ServicePrincipalAuthorization` excluding Entra grants, the `PrimarySmtpAddress` advice, and the limits page wording (30 per minute for SMTP client submission, the tenant external recipient limit, the onmicrosoft.com throttle).

## Microsoft pages read on 2026-11-22

- The RBAC for Applications page was fetched with a read-only GET (curl, because WebFetch is not offered to this agent; the precondition asked for a read-only GET of the page and the result is the same). Its cmdlets match the quotes in 04-RESEARCH.md: `New-ServicePrincipal -AppId -ObjectId -DisplayName`, `New-ManagementScope -Name -RecipientRestrictionFilter`, `New-ManagementRoleAssignment -App -Role -CustomResourceScope`, `Test-ServicePrincipalAuthorization -Identity -Resource`. The page itself names `Connect-ExchangeOnline` only in prose, so its syntax comes from the connect page cited in RESEARCH.
- One difference: the page shows "Last updated on 2026-08-21", while RESEARCH recorded 2026-10-27. The guide quotes the date the page shows. No cmdlet or requirement differs.
- The limits page (shown date 2026-11-11) and the recipient-filter page confirmed what RESEARCH quoted: 30 messages per minute worded for SMTP client submission, 10,000 recipients per day, the tenant external recipient rate limit, 100 external recipients per 24 hours with NDR 550 5.7.236 for onmicrosoft.com, and "Don't use the PrimarySmtpAddress property; use the EmailAddresses property instead".

## Gate results

- Guide, cmdlet, command, closing-section and hygiene gates: all pass (eleven headings in order, six closing bullets, two caveat lines before them, no secret value, no non-zero GUID, no address other than example ones, no absolute home path).
- Recipe run: the two openssl commands produced a `.p12` that `keytool -list` showed with a `PrivateKeyEntry`, on OpenSSL 3.5.5 and JDK 21. The scratch files were deleted. The `-legacy` fallback was not run; RESEARCH had run it.
- Dry run on a scratch copy of the example campaign, with no secret variables: exit 0, `dry run: 5 mails written to`, five `.subject.txt` files, no `journal.jsonl`.
- Ignore check: `app.p12`, `app.key`, `app.pem` and `app.cer` are ignored; the test keystore is tracked and not ignored.
- `./mvnw -B -ntp verify`: BUILD SUCCESS (137 sample tests, library tests green), run before the commit.
- Commit gate: the guide commit holds exactly `.gitignore`, `docs/architecture.md` and `docs/entra-setup.md`, authored and committed as the maintainer with the Co-Authored-By trailer, not pushed. No 04-02 commit touches a POM, the CI file or a source file.
- No real mail was sent and no live test was run. Only `--dry-run` was used.

## Task Commits

1. Task 1: `7f07748` docs(04-02): add the Entra and Exchange setup guide

## Deviations from Plan

None - plan executed exactly as written. One note: the RBAC page was read with curl, not WebFetch, which this agent does not have. The precondition's purpose (a read-only look at the current page before writing) is met.

## Flagged assumptions, open for the owner's walk-through

- A1: the Overview page of the app registration shows the Directory (tenant) ID next to the Application (client) ID. If the portal differs, the owner still finds both ids.
- A2: an Enterprise application exists once the app is registered. The guide tells the owner to create the service principal in Entra first if it is missing.
- A3: the scope filter `Alias -eq 'newsletter'` matches the sending mailbox. It has not been run on a real mailbox. The guide tells the admin to check `InScope` and adjust the filter if it is False.

## Known Stubs

None.

## Threat Flags

None. The plan adds documentation and ignore rules only.

## Next Phase Readiness

Ready for 04-03. The README can link `docs/entra-setup.md`. The owner's own walk-through of the guide with live sends is the human check in 04-04.

## Self-Check: PASSED

- docs/entra-setup.md, .gitignore and docs/architecture.md exist and carry the edits.
- Commit 7f07748 is an ancestor of HEAD, authored and committed as the maintainer with the Co-Authored-By trailer.
