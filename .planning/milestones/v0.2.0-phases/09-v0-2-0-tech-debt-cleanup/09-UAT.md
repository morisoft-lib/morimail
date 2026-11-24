---
status: complete
phase: 09-v0-2-0-tech-debt-cleanup
source: [09-01-SUMMARY.md, 09-02-SUMMARY.md, 09-03-SUMMARY.md]
started: 2026-11-24T07:26:23Z
updated: 2026-11-24T09:15:39.877Z
---

## Current Test

[testing complete]

## Tests

### 1. A cid id ends at <
expected: Run from the repo root: `java -jar morimail-sample-serial/target/morimail-sample-serial-0.3.0-SNAPSHOT.jar send --account /tmp/claude-1000/-home-coder-morimail/044f9eca-fad2-4734-b855-21d613230f79/scratchpad/uat09/account.properties --campaign /tmp/claude-1000/-home-coder-morimail/044f9eca-fad2-4734-b855-21d613230f79/scratchpad/uat09/cid-end --dry-run --out /tmp/claude-1000/-home-coder-morimail/044f9eca-fad2-4734-b855-21d613230f79/scratchpad/uat09/out-cid-end; echo "exit $?"`. body.html has <p>cid:logo</p> next to <img src="cid:logo">. Pre-flight passes (no "no inline image for cid:logo</p" problem), the run ends with exit 0, and out-cid-end holds the rendered mail plus inline/logo.png.
result: pass

### 2. SVG works as an inline image
expected: `java -jar morimail-sample-serial/target/morimail-sample-serial-0.3.0-SNAPSHOT.jar send --account /tmp/claude-1000/-home-coder-morimail/044f9eca-fad2-4734-b855-21d613230f79/scratchpad/uat09/account.properties --campaign /tmp/claude-1000/-home-coder-morimail/044f9eca-fad2-4734-b855-21d613230f79/scratchpad/uat09/types --dry-run --out /tmp/claude-1000/-home-coder-morimail/044f9eca-fad2-4734-b855-21d613230f79/scratchpad/uat09/out-types; echo "exit $?"`. The campaign has inline/badge.svg and inline/logo.png. Pre-flight accepts the SVG as an image (no "not an image" problem), exit 0, out-types/inline/ holds badge.svg and logo.png, and opening the rendered HTML in a browser shows the logo and a teal circle.
result: pass

### 3. Control characters in an echoed cid id show as ?
expected: `java -jar morimail-sample-serial/target/morimail-sample-serial-0.3.0-SNAPSHOT.jar send --account /tmp/claude-1000/-home-coder-morimail/044f9eca-fad2-4734-b855-21d613230f79/scratchpad/uat09/account.properties --campaign /tmp/claude-1000/-home-coder-morimail/044f9eca-fad2-4734-b855-21d613230f79/scratchpad/uat09/control --dry-run --out /tmp/claude-1000/-home-coder-morimail/044f9eca-fad2-4734-b855-21d613230f79/scratchpad/uat09/out-control; echo "exit $?"`. body.html has `<p>$$note$$</p>`, and the row's note value is `cid:a` + ESC + `b` + NEL + `c` + U+2028 + `d` + U+202E + `e`, so the reference reaches build() only through recipient data. Pre-flight fails with exit 2, and one problem line contains exactly `no inline image for cid:a?b?c?d?e`. No raw control character reaches the terminal: no color change, no line break and no reversed text in that line.
result: pass

### 4. A later row that Graph would reject stops pre-flight
expected: `UAT_DUMMY_SECRET=dummy java -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=1 -jar morimail-sample-serial/target/morimail-sample-serial-0.3.0-SNAPSHOT.jar send --account /tmp/claude-1000/-home-coder-morimail/044f9eca-fad2-4734-b855-21d613230f79/scratchpad/uat09/account.properties --campaign /tmp/claude-1000/-home-coder-morimail/044f9eca-fad2-4734-b855-21d613230f79/scratchpad/uat09/late-row; echo "exit $?"`. This is a real send with a dummy account; the proxy blocks any network call. Rows 1 and 2 are fine, but row 3 (line 4) has a 4.2 million character city. Exit 2, nothing on stdout, and exactly two stderr lines: `recipients.csv line 4: mailer.validate rejected the mail: estimated request size ... bytes is above the limit of 4 MiB (4194304 bytes)` and `Pre-flight failed with 1 problem(s); nothing was sent.` No journal.jsonl appears in late-row/.
result: pass

### 5. A limit that every row breaks is one line
expected: `UAT_DUMMY_SECRET=dummy java -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=1 -jar morimail-sample-serial/target/morimail-sample-serial-0.3.0-SNAPSHOT.jar send --account /tmp/claude-1000/-home-coder-morimail/044f9eca-fad2-4734-b855-21d613230f79/scratchpad/uat09/account.properties --campaign /tmp/claude-1000/-home-coder-morimail/044f9eca-fad2-4734-b855-21d613230f79/scratchpad/uat09/all-rows; echo "exit $?"`. Every one of the 3 rows shares attachments/big.bin (exactly 3 MiB). Exit 2 and exactly two stderr lines: `mailer.validate rejected the mail: attachment big.bin has 3145728 bytes; Graph takes attachments below 3 MiB (3145728 bytes) (all 3 rows)` and `Pre-flight failed with 1 problem(s); nothing was sent.`
result: pass

## Summary

total: 5
passed: 5
issues: 0
pending: 0
skipped: 0
blocked: 0

## Gaps

[none yet]
