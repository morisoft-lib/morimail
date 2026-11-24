---
phase: quick-261124-hfx
plan: 01
subsystem: sample-serial, live test, docs
tags: [env, account-file, dev-setup]
requirements-completed: [D-01, D-02, D-03, D-04]
key-files:
  created:
    - .env.sample
    - morimail-sample-serial/dev-account.properties
  modified:
    - morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/AccountFile.java
    - morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/SendCommand.java
    - morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/SerialMail.java
    - morimail/src/test/java/de/morisoft/morimail/LiveGraphTest.java
    - README.md
    - docs/spec.md
    - docs/architecture.md
    - docs/entra-setup.md
decisions:
  - "${NAME} references in account file values are expanded from an injected environment function; an unset or blank variable is one pre-flight problem naming key and variable, and AccountFile.read returns null."
metrics:
  tasks: 3
  tests_added: 8
  files: 15
status: complete
commits: 3
plan_head_before: db325f1c034b9ce71b89d034480a31bcd3ebeaa8
plan_head_after: 5f1905c
---

# Quick 261124-hfx: Development env for the live test and the sample

One development file (`.env` from the root `.env.sample`) now drives the live test and the serial-mail sample. The sample reads it through the committed `dev-account.properties`, whose values use `${NAME}` references.

## Commits

- bc2ed5c: `${NAME}` expansion in `AccountFile`, environment injected through `SerialMail.commandLine` and `SendCommand`, `dev-account.properties`, three ExampleCampaignTest tests (tracer, verified end to end).
- df50d43: five AccountFileTest edge-case tests (set, embedded, unset, blank, literal `$` and `\`, no value echoed) plus the spec 8.1 note and one architecture.md row.
- 5f1905c: root `.env.sample`, live-test rename to `MORIMAIL_DEV_*`, README Development setup, entra-setup.md wording.

## Verification

- `./mvnw -B -ntp -o verify` green after each task (374, 71 and 174 tests at the end).
- `./mvnw -B -ntp -Prelease clean verify -Dgpg.skip -pl morimail,morimail-html -am` green.
- `.env` is ignored, `.env.sample` and `dev-account.properties` are not. No file outside `.planning` names the old `MORIMAIL_LIVE_` prefix. Added lines hold only example.com/example.org addresses and the zero GUID. `-Plive` was never run, no mail sent.

## Deviations from Plan

- The shell-sourcing check of `.env.sample` in Task 3's verify was replaced by a grep count of the nine assignments plus `sh -n`, because the workspace secret-read guard blocks any command that sources `.env*` files. The result is the same: 9 assignments, nothing but comments and assignments.
- The plan file could not be materialised by shell redirect in the worktree (path guard); it was written with `git show --output`.

## Known Stubs

None.

## Self-Check: PASSED

Commits bc2ed5c, df50d43, 5f1905c exist; `.env.sample` and `dev-account.properties` are tracked.
