---
phase: 02-serial-mail-sample
reviewed: 2026-11-21T18:15:00Z
depth: standard
files_reviewed: 23
files_reviewed_list:
  - .gitignore
  - morimail-sample-serial/pom.xml
  - morimail-sample-serial/example-campaign/account.properties
  - morimail-sample-serial/example-campaign/body.html
  - morimail-sample-serial/example-campaign/body.txt
  - morimail-sample-serial/example-campaign/recipients.csv
  - morimail-sample-serial/example-campaign/subject.txt
  - morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/AccountFile.java
  - morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/Campaign.java
  - morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/Journal.java
  - morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/SendCommand.java
  - morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/SerialMail.java
  - morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/AccountFileTest.java
  - morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/CampaignTest.java
  - morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/DryRunTest.java
  - morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/ExampleCampaignTest.java
  - morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/FailureHandlingTest.java
  - morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/JournalTest.java
  - morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/PacingAndOptionsTest.java
  - morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/PreflightTest.java
  - morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/SampleFixtures.java
  - morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/SendRunTest.java
findings:
  critical: 0
  warning: 3
  info: 6
  total: 9
  necessity: 0
status: issues_found
---

# Phase 02: Code Review Report

**Reviewed:** 2026-11-21T18:15:00Z
**Depth:** standard
**Files Reviewed:** 23 (the list above has 22 entries because the config counted 23 files; every file named in the scope was read)
**Status:** issues_found

## Summary

I reviewed the serial-mail sample: its account reader, campaign and CSV reader, journal, send command, entry point, example campaign, POM and tests. I cross-checked them against `docs/spec.md` section 8 and the core types they call (`Mail`, `Mailer`, `MailException`, `TransientMailException`, `TemplateRenderer`).

The send loop matches spec 8.3 as I traced it:
- Retries are capped at 4 attempts, with the wait capped at 5 minutes and never shorter than the spacing.
- Exit code 3 on `SenderRejectedException` or `AuthRequiredException` is correct.
- The journal is forced to disk per line, and a torn last line is handled.
- Duplicate addresses are rejected in pre-flight, so a duplicate can never be silently skipped as "accepted".
- The CSV parser handles quotes, CRLF, lone CR and line numbers correctly.
- Dry-run file names cannot traverse out of `--out`, because `safeName` strips separators and an index prefix is added.
- I found no secrets, real addresses or tenant IDs. The example account uses zero GUIDs and `example.com`/`example.org`.

No blockers. The three warnings are real user-facing defects: a BOM-induced false "missing key" on Windows, junk files attached to every mail, and misattributed or flooded pre-flight messages. The Info items are robustness and maintainability.

Necessity: `necessity-review.md` was read. I found no necessity findings. The guards I checked each defend a reachable state.

## Warnings

### WR-01: `account.properties` with a UTF-8 BOM reports a missing `account.id` key

**File:** `morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/AccountFile.java:41-42`
**Issue:** `Campaign.readText` strips a leading BOM (`Campaign.java:309`), but `AccountFile.read` does not. `Files.newBufferedReader` decodes UTF-8 without removing the BOM, and `Properties.load` then keys the first line as `"[BOM]account.id"` (an invisible BOM character before the name). The user sees `account.properties: missing key 'account.id'` although the key is visibly in the file. Notepad and other Windows editors add a BOM, and the example file itself warns about backslash paths, so Windows users are an expected audience. The campaign files and the account file are handled inconsistently.
**Fix:** Strip the BOM before parsing, for example read the file through the same helper `Campaign.readText` uses and load from a `StringReader`:
```java
String text = Files.readString(file);              // strict UTF-8, MalformedInputException as now
if (text.startsWith("\uFEFF")) text = text.substring(1);
properties.load(new java.io.StringReader(text));
```
Add a test with a BOM-prefixed account file.

### WR-02: Every regular file in `attachments/` is attached, including hidden and OS junk files

**File:** `morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/Campaign.java:176-177`
**Issue:** `files.filter(Files::isRegularFile)` accepts `.DS_Store`, `Thumbs.db`, `desktop.ini`, editor temp files (`~$report.docx`, `*.swp`) and any other stray file, and the sample attaches all of them to every recipient's mail. The dry-run lists the attachments, but nothing in pre-flight stops the real send. Symlinks are followed silently, so a link in the directory can pull in a file from elsewhere. A wrongly attached private file goes to the whole list and cannot be recalled.
**Fix:** Skip dot-files at minimum, and ideally report each skipped or unexpected file in the dry-run output:
```java
files.filter(Files::isRegularFile)
     .filter(f -> !f.getFileName().toString().startsWith("."))
     .forEach(attachments::add);
```

### WR-03: Campaign-wide mail defects produce one misattributed problem per recipient

**File:** `morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/SendCommand.java:266-280`
**Issue:** Mails are built per row and each `InvalidMailException` is reported as `"recipients.csv line N: " + e.getMessage()`. Several failure causes do not depend on the row:
- an invalid `from` in `account.properties`;
- an attachment that is unreadable or has a bad content type;
- a subject that renders blank or contains a line break (a multi-line `subject.txt`).

For these, a 5,000-row CSV yields 5,000 identical stderr lines, and each one wrongly blames `recipients.csv`. The spec asks for all problems reported at once, but a flood hides the real list. The "N problem(s)" count is also inflated.
**Fix:** Deduplicate on the message text without the line prefix. Report row-independent causes once, without the `recipients.csv line` prefix. For example, keep a `Map<String, List<Integer>>` from exception message to rows, and print `"<message> (rows 2, 3, 4 ...)"`. At minimum, suppress repeats of an identical message after the first few.

## Info

### IN-01: Exit codes are documented incompletely

**File:** `morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/SerialMail.java:486-488`
**Issue:** The `main` Javadoc lists exit codes 0, 1 and 2 only. `SendCommand.send` also returns 3 when `SenderRejectedException` or `AuthRequiredException` stops the run (spec 8.3 point 9). The command's `footer` does not mention it either, so `--help` never shows the code a script would branch on.
**Fix:** Add "3 the run was stopped by a rejected sender or required authentication" to the Javadoc and to the `@Command` footer or description.

### IN-02: An unexpected `RuntimeException` ends the run with a stack trace, no counts and no exit code from the table

**File:** `morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/SendCommand.java:412-447`
**Issue:** Only `MailException` and `IOException` are handled around `mailer.send`. A `RuntimeException` from a transport or token provider, or the `IllegalStateException` thrown on an interrupted sleep (`SerialMail.java:518`), escapes to picocli. Picocli prints a stack trace and exits 1, with no "accepted/skipped/failed" line. The journal stays consistent because the try-with-resources closes it, but the operator loses the summary and cannot tell whether the last mail was sent.
**Fix:** Catch `RuntimeException` around the loop in `send`, print `Stopped: unexpected <class>: <message>. The last mail may have been sent; a rerun sends it again.`, print the counts and return 1. This reuses the existing IOException branch.

### IN-03: `describe` prints "null" for exceptions without a message

**File:** `morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/SendCommand.java:226-228`
**Issue:** `e.getClass().getSimpleName() + ": " + e.getMessage()` prints `TransientMailException: null` when the message is null. `Journal.append` guards against a null message, but the console output does not.
**Fix:** Omit the colon and message when `getMessage()` is null.

### IN-04: Placeholder regex is copied three times and the `!` is lost in the missing-column message

**File:** `morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/Campaign.java:129`, `:281`
**Issue:** `PLACEHOLDER` copies the private pattern of the core's `TemplateRenderer`, and `ExampleCampaignTest.java:29` copies it a third time. The comment says it is copied "so both agree". `PreflightTest.extractedNamesSatisfyTheCoreRenderer` catches drift only for the sample inputs it lists. Separately, the message for `$$!banner$$` prints `$$banner$$`, so the user searches for a placeholder that is not in the file.
**Fix:** Reuse `Campaign.PLACEHOLDER` in `ExampleCampaignTest`. In `placeholders`, keep the `!` marker so the message can show the placeholder as written. Alternatively, have the core expose the placeholder names, if that is acceptable under Rule 0.

### IN-05: Literal invisible U+FEFF character in source

**File:** `morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/Campaign.java:309`
**Issue:** `text.startsWith(...)` contains a raw, invisible byte-order-mark character (bytes EF BB BF in the source). A reader or an editor that normalizes the file will not see it, and the line looks like a bug.
**Fix:** Write the character as the escape `\uFEFF` in the string literal.

### IN-06: Journal has no exclusive lock, and `jackson-databind` is used without being declared

**File:** `morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/Journal.java:85`; `morimail-sample-serial/pom.xml:23-45`
**Issue:**
- `FileChannel.open(file, CREATE, WRITE, APPEND)` takes no lock. Two runs started against the same campaign, such as a cron overlap or a double-click, each read the same accepted set and both send every pending mail. The journal exists to prevent double sends. `tryLock()` on the channel would turn this into a pre-flight problem for a few lines. This is a robustness gap rather than a spec violation, so weigh it against Rule 0.
- `Journal` imports `com.fasterxml.jackson.databind` directly (and the test fixtures do too), but the sample POM declares no Jackson dependency and relies on the transitive one from `morimail`. The build works today. If the library ever drops Jackson from its compile scope, the sample breaks with no hint from the POM.

**Fix:** Take `channel.tryLock()` and report "journal is in use by another run" as a problem. Declare `jackson-databind` in the sample POM (the BOM already manages its version).

---

_Reviewed: 2026-11-21T18:15:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
