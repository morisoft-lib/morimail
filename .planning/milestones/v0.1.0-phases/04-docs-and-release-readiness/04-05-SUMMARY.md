---
phase: 04-docs-and-release-readiness
plan: 05
subsystem: release
tags: [release, gpg, gpg-agent, docs, gap-closure]
status: complete

requires:
  - phase: 04-docs-and-release-readiness
    provides: 04-04 docs/release.md and the 0.1.0 release commit 72e7f7f
provides:
  - docs/release.md step 5 with a gpg-agent warm-up before each -B deploy and a hint for reading a gpg exit code 2
affects: []

gap_ids: [G-04-3]

actuals:
  tokens: 1500
  tasks: 1
  commits: 1
plan_head_before: 1f7871d7b3b4fba42781a8879cab42fbbc4aae93
plan_head_after: c359ec179e3550fd65767917cd94055f1ae15072

tech-stack:
  added: []
  patterns: []

key-files:
  created: []
  modified:
    - docs/release.md

key-decisions:
  - "04-05: docs/release.md step 5 warms up gpg-agent in a real terminal before each -B deploy; no POM change (G-04-3)"

requirements-completed: [DOCS-04]
---

# Phase 4 Plan 05: gpg-agent warm-up and signing-failure hint in docs/release.md Summary

docs/release.md step 5 now warms up gpg-agent in a real terminal before each `-B` deploy, says the deploy signs from the agent cache and cannot prompt, and explains how to read a `maven-gpg-plugin` exit code 2.

## What changed in docs/release.md (G-04-3)

| G-04-3 missing item | Change |
|---|---|
| 1. Warm-up before each deploy, real terminal, 10-minute cache | Step 5 opens with a real-terminal note (not a `!` command in Claude Code, where `tty` prints `not a tty`), a two-line warm-up block (`export GPG_TTY=$(tty)`, `echo test \| gpg --clearsign > /dev/null`) and the 10-minute note. The same block is repeated right before the real deploy ("Warm up the agent again:", then "Then deploy:"). |
| 2. Replace the prompt promise | "Tests run, and gpg asks for the passphrase." became "Tests run. The deploy signs from the agent cache and cannot prompt, because of `-B`." |
| 3. Re-scope step 2 item 3 | `GPG_TTY` is for the warm-up and does not make the deploy prompt. "Never set `gpg.passphrase`" is kept. |
| 4. Read the failure | Last paragraph of step 5: exit code 2 carries no reason; the unprefixed `gpg:` line under `--- gpg:3.2.8:sign`, above the Reactor Summary, does; `No pinentry` means an empty cache, so warm up and rerun. |
| 5. Several secret keys | One sentence: `--local-user <KEYID>` on the warm-up, `-Dgpg.keyname=<KEYID>` on both deploy commands. Placeholders only. |

No heading was added, both deploy commands keep `-B` unchanged, step 4 is untouched, and no POM, source or workflow file changed.

## Verification

- 04-04 release.md gate (nine headings, commands, placeholders, bundle facts): exit 0.
- New-content gate (step 2 scoping, step 5 notes, two warm-ups directly above their blocks, in order warm-up, skipUpload check, warm-up, real deploy): exit 0.
- Removed-text gate (no prompt promise, no deploy-shell `GPG_TTY`, no env-var or loopback route): exit 0.
- Build-input gate (only docs/release.md and .planning differ from 72e7f7f; the three POMs at 0.1.0): exit 0, also re-run after the build.
- Hygiene gate (no token, key id, non-example address or home path): exit 0.
- `./mvnw -B -ntp verify`: BUILD SUCCESS (all three reactor modules).
- Commit gate (fix commit holds docs/release.md only, maintainer identity as author and committer, Co-Authored-By trailer, not on origin/main, no tag): exit 0.

## Deviations from Plan

None - plan executed exactly as written.

## Flagged assumption (open for the owner)

The Central GPG requirements page does not say which key algorithms the Portal accepts. Its examples use RSA 3072, and the owner's key is EdDSA. There is no evidence of a problem, so the doc does not change for it. If the Portal rejects the signatures, that happens at validation, before anything is public (`autoPublish` is false). Step 6 item 3 covers that case: drop the deployment, fix the cause, deploy again.

## Pending owner actions

1. In a real terminal in this repository, continue docs/release.md at step 5: warm-up, skipUpload check (30 entries), warm-up again, real deploy, then step 6.
2. Step 4 does not need to be redone. The fix commit changes only docs/release.md, so every build input still equals the release commit 72e7f7f, whose CI run is green.
3. Push the fix commit when convenient.
4. Re-run verify-work for phase 04 so 04-UAT.md test 3 is recorded.

The G-04-3 truth that the Portal validates the signed deployment is verified by the owner only (backstop). The executor ran no gpg command, deploy, push or tag.

## Known Stubs

None.

## Self-Check: PASSED

- docs/release.md: FOUND
- Commit c359ec1: FOUND (ancestor of HEAD)
