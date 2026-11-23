---
phase: quick-261123-ubm
plan: 01
subsystem: docs
tags: [release, docs]
requires: []
provides:
  - docs/release.md with IN-01, IN-02 and IN-03 corrected
affects: [docs/release.md]
key-files:
  modified: [docs/release.md]
decisions: []
metrics:
  duration: 5min
  completed: 2026-11-23
status: complete
actuals:
  tokens: 600
  tasks: 1
  commits: 1
plan_head_before: 4922caaa85d5ea9e92c4b5ca58da3c6dcb02e9b6
plan_head_after: 9f15ca1c444ac905ed351f42e0bff91849c57257
---

# Quick 261123-ubm: Fix IN-01 to IN-03 in docs/release.md Summary

Three prose lines of the release procedure corrected: the step 4.1 variable note, the step 8.1 macOS sed note, and the step 8.3 README Coordinates instruction.

## What changed

- IN-03 (step 4.1): the note now says steps 7 and 8 use `VERSION`, step 8 also uses `NEXT`, and step 5 needs neither (it names `${VERSION}` only in the bundle listing).
- IN-02 (step 8.1): added "On macOS use `sed -i ''`." as in step 4.2. The sed command is unchanged.
- IN-01 (step 8.3): the owner is told to rewrite the README Coordinates text so it names `${VERSION}` and says what that release adds. The check of both dependency blocks stays.

Commit 9f15ca1: 3 insertions, 3 deletions, docs/release.md only. No fenced command, heading or other file changed.

## Verification

- Four content gates passed (old wordings gone, new wordings present, macOS note on lines 69 and 162 only, numstat 3/3).
- `./mvnw -B -ntp -o verify`: BUILD SUCCESS across all four modules.
- Commit gate: ok (scope, maintainer identity, trailer, no address or home path, not pushed).

## Deviations from Plan

None - plan executed exactly as written.

## Self-Check: PASSED
