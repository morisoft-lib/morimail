---
status: diagnosed
phase: 07-html-image-helper-module
source: [07-VERIFICATION.md]
started: 2026-11-23T15:02:23Z
updated: 2026-11-23T16:15:20.076Z
---

## Current Test

[testing complete]

## Tests

### 1. Push the Phase 7 commits (07-02 and 07-03, up to HEAD) and let GitHub Actions run on Temurin 17
expected: All three CI steps are green on Temurin 17: `./mvnw -B -ntp verify`, `./mvnw -B -ntp -Prelease clean verify -Dgpg.skip -pl morimail,morimail-html -am`, and the offline `unshare --net` build. Only commit cce3625 (end of 07-01 Task 2) has run there so far.
result: pass

### 2. Read the owner-review items in the 07-01, 07-02 and 07-03 SUMMARYs (assumptions A1-A7, planner choices P1-P10) and accept or correct them
expected: Owner accepts or corrects, for example the `provided` scope of `morimail` in the helper POM (D-02), the 3 MiB `>=` refusal rule (A2), HTTP 408 mapped to InvalidMailException, and the `image-3.svg-xml` file name for `image/svg+xml` (P10)
result: issue
reported: "D-02 should be compile scope"
severity: major

## Summary

total: 2
passed: 1
issues: 1
pending: 0
skipped: 0
blocked: 0

## Gaps

- gap_id: G-07-2
  truth: "Owner accepts the flagged assumptions and planner choices of 07-01 to 07-03, including the `provided` scope of `morimail` in the helper POM (D-02)"
  status: failed
  reason: "User reported: D-02 should be compile scope"
  severity: major
  test: 2
  root_cause: "D-02 (07-CONTEXT.md) chose `provided` scope for `morimail` in morimail-html/pom.xml. Nearest-wins already lets a consumer's direct morimail dependency override a compile-scoped transitive one, so provided buys nothing for consumers who declare morimail, but it leaves no version link: morimail-html 0.2.0 with morimail 0.1.0 compiles and runs (Attachment.contentId exists since 0.1.0) while 0.1.0 lacks the Phase 5 inline-image checks and sending, so images break silently. Owner decided compile scope."
  artifacts:
    - path: "morimail-html/pom.xml"
      issue: "morimail declared with <scope>provided</scope> (line 22)"
    - path: ".planning/phases/07-html-image-helper-module/07-CONTEXT.md"
      issue: "D-02 and D-03 state provided scope"
    - path: "docs/architecture.md"
      issue: "morimail-html decisions record (IN-05) may state provided scope"
  missing:
    - "Declare morimail in morimail-html/pom.xml with compile scope (drop <scope>provided</scope>)"
    - "Update every doc, Javadoc and planning text that states provided scope for the helper (D-02/D-03 wording, docs/architecture.md, README if any) to compile scope with the nearest-wins rationale"
    - "Keep the D-03 checks green: morimail's dependency tree has no morimail-html; morimail-html's runtime tree is morimail (compile) plus Jackson via morimail; morimail's jar file list unchanged"
  debug_session: ""
