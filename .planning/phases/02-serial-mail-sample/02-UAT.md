---
status: complete
phase: 02-serial-mail-sample
source: [02-VERIFICATION.md]
started: 2026-11-21T18:35:00Z
updated: 2026-11-22T06:48:12.796Z
---

## Current Test

[testing complete]

## Tests

### 1. Rendered example pages and attachments open correctly in real viewers
expected: Run the jar dry-run with --out in a new mktemp -d directory outside the repo, open the five .html files in a browser, and open example-campaign/attachments/info.pdf in a real PDF viewer and logo.png in an image viewer. Each page shows the greeting with the recipient's name, city and topic (clara's topic reads 'Tips, tricks & shortcuts' as an ampersand). The PDF opens as one page with the text 'morimail example attachment'. The PNG shows a colour gradient. Neither viewer shows an author, creator or producer.
result: pass

## Summary

total: 1
passed: 1
issues: 0
pending: 0
skipped: 0
blocked: 0

## Gaps
