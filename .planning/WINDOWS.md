---
schema_version: 1
open_count: 1
waived_count: 0
fixed_count: 0
total_count: 1
last_updated: 2026-11-23T08:17:58.192Z
---

# Broken Windows Ledger

> Cross-phase defect register. With `workflow.windows_enforce` enabled, `/gsd-ship` blocks while `open_count > 0`.
> Waive with `gsd-tools windows waive <id> "<reason>"` (reason required).
> Mark fixed with `gsd-tools windows fixed <id>`.

| id | phase | kind | file | line | description | status | reason | recorded_at | resolved_at |
|----|-------|------|------|------|-------------|--------|--------|-------------|-------------|
| 1 | 06 | unrun-verify | .planning/phases/06-inline-images-in-the-serial-mail-sample/06-02-SUMMARY.md |  | Final Temurin 17 CI run of the pushed 06-02 commits (owner), all three steps; and the browser look of the example dry-run HTML | open |  | 2026-11-23T08:17:58.192Z |  |

````json
[
  {
    "id": 1,
    "kind": "unrun-verify",
    "phase": "06",
    "file": ".planning/phases/06-inline-images-in-the-serial-mail-sample/06-02-SUMMARY.md",
    "line": null,
    "description": "Final Temurin 17 CI run of the pushed 06-02 commits (owner), all three steps; and the browser look of the example dry-run HTML",
    "status": "open",
    "reason": "",
    "recorded_at": "2026-11-23T08:17:58.192Z",
    "resolved_at": null,
    "milestone": "v0.2.0"
  }
]
````
