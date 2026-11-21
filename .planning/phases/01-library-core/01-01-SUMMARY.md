---
phase: 01-library-core
plan: 01
subsystem: infra
tags: [maven, maven-wrapper, github-actions, release-profile, jackson, junit]

requires: []
provides:
  - Maven reactor morimail-parent, morimail, morimail-sample-serial (0.1.0-SNAPSHOT, release 17) building green in default, release and offline mode
  - Script-only Maven wrapper pinned to Maven 3.10.1, mode 100755, LF/CRLF rules in .gitattributes
  - release profile (sources, Javadoc with doclint all and failOnWarnings, gpg sign in verify, Central publishing with autoPublish false)
  - Library jar with Automatic-Module-Name de.morisoft.morimail and a reactor-only test-jar (attach false under release)
  - CI workflow on Java 17 with read-only permissions and SHA-pinned actions
affects: [01-02, 01-03, 01-04, 01-05, 01-06, phase-02, phase-03, phase-04]

actuals:
  tokens: 14000
  tasks: 2
  commits: 2

plan_head_before: 9441094fd54950a9d0f6a226ca394a8af959061d
plan_head_after: 59f14d1cbc952c73da102d32d4602b1e9ae0f7e1
commits: 2

tech-stack:
  added: [jackson-databind 2.23.0 (via jackson-bom), junit-jupiter 6.1.3 (test), assertj-core 3.27.7 (test), Maven wrapper 3.10.1]
  patterns:
    - every version declared once in the parent (properties, dependencyManagement, pluginManagement)
    - release builds scoped to the published modules with -pl morimail -am
    - module name on the default-jar execution only

key-files:
  created:
    - pom.xml
    - morimail/pom.xml
    - morimail-sample-serial/pom.xml
    - mvnw
    - mvnw.cmd
    - .mvn/wrapper/maven-wrapper.properties
    - .gitattributes
    - .github/workflows/ci.yml
  modified:
    - .gitignore

key-decisions:
  - "Release builds run with -pl morimail -am (owner decision D-20): an unattached test-jar cannot be resolved by a packaging full-reactor build"
  - "wiremock and picocli are version-managed in the parent but declared by no module until Phase 3 and Phase 2"
  - "The sample POM declares no test-jar dependency yet; plan 01-04 adds it with the fixture"
  - "JUnit Jupiter 6.1.3 is used instead of the spec's JUnit 5 (owner decision D-02c); plan 01-02 records the deviation"

requirements-completed: [REPO-01, REPO-02, REPO-03, REPO-04, REPO-05, REPO-07, REPO-09, REPO-11]

duration: 20min
completed: 2026-11-21
status: complete
---

# Phase 1 Plan 01: Walking skeleton, part 1 Summary

**Release-shaped Maven reactor (parent, library, sample) on a script-only Maven 3.10.1 wrapper, green in default, release and offline mode, with a SHA-pinned Java 17 CI workflow.**

## Performance

- **Duration:** about 20 min in this session; Task 1 files were written and verified by the previous executor
- **Tasks:** 2
- **Files:** 9 (8 created, 1 modified)

## Accomplishments

- Parent POM `de.morisoft:morimail-parent` with the Central metadata (name, description, URL, MIT license, developer, SCM over HTTPS), Jackson and JUnit BOM imports, every plugin pinned in `pluginManagement`, and the `release` profile.
- Library module `de.morisoft:morimail`: `jackson-databind` at compile scope, JUnit Jupiter and AssertJ at test scope, `Automatic-Module-Name: de.morisoft.morimail` on the main jar only, a `test-jar` execution that the release profile sets to `attach false`.
- Sample module with the five publish-skip properties and the library as compile dependency.
- Wrapper (`only-script`, Maven 3.10.1), `.gitattributes` and `.gitignore` rules, including `.review-diagnostics/`.
- `.github/workflows/ci.yml`: push and pull_request, `contents: read`, checkout v7.0.1 and setup-java v6.3.0 pinned to commit SHAs, Java 17 temurin with the Maven cache, and the three wrapper commands.

## Task Commits

1. **Task 1: reactor, wrapper, ignore rules** - `de4e8b8` (feat)
2. **Task 2: CI workflow** - `59f14d1` (chore)

## Verification

- `./mvnw -B -ntp verify`: BUILD SUCCESS, no missing-plugin-version warnings.
- `./mvnw -B -ntp -Prelease clean verify -Dgpg.skip -pl morimail -am`: BUILD SUCCESS; `morimail/target/morimail-0.1.0-SNAPSHOT-tests.jar` still built; `Automatic-Module-Name` present once in the main jar manifest and absent from the test-jar.
- `./mvnw -B -ntp -o verify`: BUILD SUCCESS (warmed-cache resolution check only, per D-18; it is not network isolation).
- Reactor order `morimail-parent,morimail,morimail-sample-serial`; runtime dependencies are exactly jackson-databind 2.23.0, jackson-annotations 2.23, jackson-core 2.23.0.
- `mvnw` committed with mode 100755; the email hygiene grep finds no address outside the maintainer identity; `.review-diagnostics/` is ignored; every commit after the phase base has the maintainer identity; `git tag --list` is empty.
- Spec sha256 of the root `spec.md` matches the pinned value.

## Version re-check

No version was changed from the plan. The previous executor re-checked Central and found no newer stable release for the listed plugins and dependencies; the owner pins (Jackson 2.23.x, JUnit 6.1.3, Maven 3.10.1) are untouched. The action SHAs match the GitHub API values for checkout v7.0.1 and setup-java v6.3.0 (confirmed through the unauthenticated public API because `gh` is not logged in here).

## Deviations from Plan

### Process notes

**1. Commits on `main`.** The protected-branch commit guard stopped the first attempt. The owner chose "allow-main", and the orchestrator set `git.allow_default_branch_commits: true` in `.planning/config.json`. That config file was not committed by this plan.

**2. `gh api` not usable.** Task 2 asked to confirm the action SHAs with `gh api`; `gh` has no login in this workspace, so the same two values were read from the public GitHub API with `curl`. Both match the plan.

Otherwise: none - plan executed as written.

## Known Stubs

None. No Java source exists yet; the "JAR will be empty" warnings for the library and sample are expected and accepted.

## Assumptions carried

- The CI workflow has not run; the owner's first push is the YAML parse check and the proof on Java 17 (`gh run list --workflow ci.yml --limit 1`).
- `./mvnw -o` assumes the wrapper distribution and all plugins are already cached.

## Issues Encountered

None beyond the protected-branch guard described above.

## Self-Check: PASSED
