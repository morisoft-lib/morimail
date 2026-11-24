---
status: complete
phase: 07-html-image-helper-module
source: [07-VERIFICATION.md]
started: 2026-11-23T15:02:23Z
updated: 2026-11-23T17:41:59.564Z
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

### 3. Push the 07-04 commits (and the review-record commits up to HEAD) and let GitHub Actions run on Temurin 17
expected: All three CI steps are green on Temurin 17: `./mvnw -B -ntp verify`, `./mvnw -B -ntp -Prelease clean verify -Dgpg.skip -pl morimail,morimail-html -am`, and the offline `unshare --net` build. origin/main is at 1e5e891; HEAD is ahead by the 07-04 commits, among them the compile-scope change b333689.
result: pass

### 4. Rerun /gsd-verify-work 7 so the UAT file reconciles G-07-2 (07-UAT.md still shows status diagnosed, gap failed)
expected: G-07-2 is marked closed; the remaining owner-review items (A1-A7, P1-P10 in the 07-01 to 07-03 SUMMARYs) are accepted or corrected, apart from the D-02 scope point the owner has already decided.
result: pass

### 5. Push the unpushed commits (HEAD is 21 ahead of origin/main at 1e5e891) and let GitHub Actions run on Temurin 17
expected: All three CI steps are green on Temurin 17: `./mvnw -B -ntp verify`, `./mvnw -B -ntp -Prelease clean verify -Dgpg.skip -pl morimail,morimail-html -am`, and the offline `unshare --net` build. The commits to cover include the compile-scope change b333689 and the code-review fixes 4e49cd3, 66b5264, 457d469, 2a14312.
result: pass

### 6. Rerun /gsd-verify-work 7 so the UAT file reconciles G-07-2 (07-UAT.md still shows status diagnosed, gap failed, tests 3 and 4 pending)
expected: G-07-2 is marked closed; the remaining owner-review items (A1-A7, P1-P10 in the 07-01 to 07-03 SUMMARYs) are accepted or corrected, apart from the D-02 scope point the owner has already decided. Also decide the two open review findings (07-REVIEW-DISPOSITION.md): the undocumented `strip()` trimming (IN-01) and the untested Error-rethrow branch (IN-02).
result: pass

## Summary

total: 6
passed: 5
issues: 1
pending: 0
skipped: 0
blocked: 0

## Gaps

- gap_id: G-07-2
  truth: "Owner accepts the flagged assumptions and planner choices of 07-01 to 07-03, including the `provided` scope of `morimail` in the helper POM (D-02)"
  status: resolved
  resolved_by: 07-04-PLAN.md
  resolved_at: 2026-11-23
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
