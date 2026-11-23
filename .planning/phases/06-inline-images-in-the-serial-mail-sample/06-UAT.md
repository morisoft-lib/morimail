---
status: complete
phase: 06-inline-images-in-the-serial-mail-sample
source: [06-VERIFICATION.md]
started: 2026-11-23T08:24:30Z
updated: 2026-11-23T09:51:58.606Z
---

## Current Test

[testing complete]

## Tests

### 1. Push the 06-02 commits and wait for the GitHub Actions run on Temurin 17
expected: All steps of the run are green (verify, scoped release build, offline build). 06-01 (2633e3a) is already green (run 45135972918).
result: pass

### 2. Run the example campaign with --dry-run and open out/01-anna@example.org.html in a browser
expected: The 96x48 morimail logo is displayed (not a broken-image icon) in each of the five rendered mails.
result: pass

## Summary

total: 2
passed: 2
issues: 0
pending: 0
skipped: 0
blocked: 0

## Gaps
