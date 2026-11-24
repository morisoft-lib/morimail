---
phase: quick-261124-my2
plan: 01
subsystem: morimail-sample-serial
tags: [sample, account-file, preflight, docs]
requires: []
provides:
  - "hint in the pre-flight line when secretEnv or certificate.passwordEnv holds a value that is not a variable name"
  - "dev-account-secret.properties (client-secret development account), dev-account.properties without a toggle"
affects: [README.md, .env.sample, docs/spec.md, docs/architecture.md]
tech-stack:
  added: []
  patterns: ["one VARIABLE_NAME rule in AccountFile, shared by the reference pattern and the hint"]
key-files:
  created:
    - morimail-sample-serial/dev-account-secret.properties
  modified:
    - morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/AccountFile.java
    - morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/SerialMail.java
    - morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/GraphEndToEndTest.java
    - morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/AccountFileTest.java
    - morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/ExampleCampaignTest.java
    - morimail-sample-serial/dev-account.properties
    - README.md
    - .env.sample
    - docs/spec.md
    - docs/architecture.md
decisions:
  - "A value that matches the NAME rule gets no hint, since it may be a real variable that is just unset"
metrics:
  tasks: 3
  files: 11
status: complete
actuals:
  tasks: 3
  commits: 3
plan_head_before: 3f291bfe650f97afee8ba37d3a699a0dbccbada0
plan_head_after: b49dc7af852cf9450baef12057d4540d74247fac
---

# Phase quick-261124-my2 Plan 01: Dev account files without toggles, plus a secretEnv hint Summary

A secret pasted into `secretEnv` or `certificate.passwordEnv` now gets a fixed hint in the pre-flight line (the value is never printed), and the development account is two committed files, one per credential, with no line to edit.

## What changed

- D-02: `SerialMail.unsetVariable` builds the message from the key and fixed text. It appends `<key> names an environment variable, it does not hold the secret itself` (or `password`) only when the configured value does not match `AccountFile.VARIABLE_NAME`. The value is only matched, never concatenated. A name-like value keeps the old line exactly. `AccountFile.read` still does not look up these variables, so a dry run needs none of them.
- D-01: `dev-account.properties` is the certificate variant (no secret line, header names the other file). New `dev-account-secret.properties` has the same four shared lines plus `secretEnv=MORIMAIL_DEV_SECRET`. `ExampleCampaignTest` covers it with three tests that mirror the certificate file's tests (read, dry run with nothing set gives three reference lines, dry run with the development variables exits 0).
- D-03: README (both `--account` commands), `.env.sample`, spec section 8.1 and `docs/architecture.md` (one extended row, one new row) name both files. The spec section 8.1 code block and `example-campaign/account.properties` are unchanged.

## Tests

- New: `secretValueInSecretEnvGetsAHint`, `passwordValueInPasswordEnvGetsAHint`, and three `devSecretAccount*` tests.
- Adjusted: the one expected line in `AccountFileTest.realRunStopHoldsNoValueFromTheFile`.
- Name-like tests (`missingSecretIsAPreflightProblem`, `missingCertificatePasswordIsAPreflightProblem`, `SendRunTest.realRunWithoutTheSecretStopsBeforeSending`, `dryRunNeedsNoEnvironmentVariable`) pass unchanged.
- `./mvnw -B -ntp -o verify` was green before each of the three commits (176 tests in the sample module).

## Commits

- 534235b feat: hint when secretEnv or certificate.passwordEnv holds a value, not a variable name
- 4cc853a feat: dev-account-secret.properties for the client secret, no toggle line
- b49dc7a docs: two dev account files instead of an edit, and the secretEnv hint

## Deviations from Plan

The tests and the implementation of Task 1 were written one after the other and the RED run was skipped. Both hint tests and the adjusted expectation were confirmed present and passing in the surefire report after the implementation. Otherwise the plan was executed as written.

## Known Stubs

None.

## Threat Flags

None. The public-repo scan of all added lines finds no address other than example.com/example.org and no GUID other than the zero GUID. Every commit is authored and committed as Stephan Wald <stephan@morisoft.de>. Nothing under `morimail/` or `morimail-html/` changed.

## Self-Check: PASSED

Created file `morimail-sample-serial/dev-account-secret.properties` exists, and commits 534235b, 4cc853a and b49dc7a are in the history of main.
