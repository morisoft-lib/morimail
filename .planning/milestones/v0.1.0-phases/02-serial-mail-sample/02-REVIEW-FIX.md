---
phase: 02-serial-mail-sample
fixed_at: 2026-11-22T06:20:00Z
review_path: .planning/phases/02-serial-mail-sample/02-REVIEW.md
iteration: 1
findings_in_scope: 9
fixed: 9
skipped: 0
status: all_fixed
---

# Phase 02: Code Review Fix Report

**Fixed at:** 2026-11-22T06:20:00Z
**Source review:** .planning/phases/02-serial-mail-sample/02-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope: 9
- Fixed: 9 (IN-06 only in part, see below)
- Skipped: 0

**Verification:** after every fix, `./mvnw -B -ntp -o verify` ran green (library 134 tests, sample 128 tests at the end). The gates ran in the isolated worktree, not in the main checkout. The worktree was fast-forwarded into `main` and removed afterwards, so the final tree equals the verified tree.

## Fixed Issues

### WR-01: `account.properties` with a UTF-8 BOM reports a missing `account.id` key

**Files modified:** `morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/AccountFile.java`, `morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/AccountFileTest.java`
**Commit:** 6a959d3
**Applied fix:** The file is read with `Files.readString` (still strict UTF-8, so the "not valid UTF-8" problem is unchanged), one leading BOM is removed, and `Properties` loads from a `StringReader`. New test `leadingByteOrderMarkIsIgnored`.

### WR-02: Every regular file in `attachments/` is attached, including hidden and OS junk files

**Files modified:** `morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/Campaign.java`, `morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/CampaignTest.java`
**Commit:** de6c45f
**Applied fix:** Files whose name starts with `.` are skipped. New test `hiddenFilesAreNotAttached`. Note: spec section 8 says "every file here is attached"; this narrows that for dot-files only. `Thumbs.db`, `desktop.ini` and `~$` files are still attached, because a name list would be more than Rule 0 asks for. The dry-run still lists the attachments, so the owner can see them.

### WR-03: Campaign-wide mail defects produce one misattributed problem per recipient

**Status:** fixed: requires human verification (reporting logic and message format)
**Files modified:** `morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/SendCommand.java`, `morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/PreflightTest.java`
**Commit:** 230daaf
**Applied fix:** `InvalidMailException` messages are grouped by text. One row gives the old `recipients.csv line N: <message>`. A message hit by every row (more than one) is reported once as `<message> (all N rows)`. Otherwise it is reported once as `<message> (recipients.csv lines 3, 5)`, listing at most 5 lines. The "N problem(s)" count now counts distinct problems. New tests `aDefectOfEveryMailIsReportedOnce` and `theSameDefectInSomeRowsIsReportedOnceWithItsLines`.

### IN-01: Exit codes are documented incompletely

**Files modified:** `morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/SerialMail.java`, `morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/SendCommand.java`, `morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/JournalTest.java`
**Commit:** 3e71c50
**Applied fix:** Exit code 3 added to the `main` Javadoc, and an "Exit codes" line added to the `send` footer so `--help` shows all four codes. New test `theHelpTextListsTheExitCodes`.

### IN-02: An unexpected `RuntimeException` ends the run with a stack trace, no counts and no exit code from the table

**Files modified:** `morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/SendCommand.java`, `morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/FailureHandlingTest.java`
**Commit:** fe9ab86
**Applied fix:** The send loop now also catches `RuntimeException` next to the existing `IOException` branch: it prints `Stopped: unexpected <class>: <message>. The last mail may have been sent; a rerun sends it again.`, then the counts, and exits 1. New test uses a failing sleep to simulate an interrupted wait.

### IN-03: `describe` prints "null" for exceptions without a message

**Files modified:** `morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/SendCommand.java`, `morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/FailureHandlingTest.java`
**Commit:** a826f78
**Applied fix:** The colon and message are omitted when `getMessage()` is null. New test `aFailureWithoutMessageIsShownWithoutNull`.

### IN-04: Placeholder regex is copied three times and the `!` is lost in the missing-column message

**Files modified:** `morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/Campaign.java`, `morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/ExampleCampaignTest.java`, `morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/PreflightTest.java`
**Commit:** 404e194
**Applied fix:** `Campaign.placeholders` now maps each name to the placeholder as first written, so the message shows `$$!banner$$` as in the file. `ExampleCampaignTest` uses `Campaign.PLACEHOLDER` instead of its own copy. The copy of the core's private pattern in `Campaign` stays (the core does not expose it, and adding an API for it is beyond Rule 0); `extractedNamesSatisfyTheCoreRenderer` still guards drift. Test expectation for the raw placeholder updated.

### IN-05: Literal invisible U+FEFF character in source

**Files modified:** `morimail-sample-serial/src/main/java/de/morisoft/morimail/sample/Campaign.java`, `morimail-sample-serial/src/test/java/de/morisoft/morimail/sample/CampaignTest.java`
**Commit:** 56a2fe8
**Applied fix:** The raw BOM characters in `Campaign.readText` and in two `CampaignTest` literals are now written as the Java unicode escape for U+FEFF. No raw U+FEFF remains under `morimail-sample-serial/src`.

### IN-06: Journal has no exclusive lock, and `jackson-databind` is used without being declared

**Files modified:** `morimail-sample-serial/pom.xml`
**Commit:** 3981cf8
**Applied fix (partial):** `com.fasterxml.jackson.core:jackson-databind` is declared in the sample POM (version from the BOM already managed in the parent). `target/lib` contents are unchanged.
**Not applied:** the `FileChannel.tryLock()` on the journal. Reasoning: the reviewer calls it a robustness gap, not a spec violation. A lock needs new failure paths (a lock held by the same JVM throws `OverlappingFileLockException`; network drives and some platforms treat locks differently), and Rule 0 says to build less and stop at anything elaborate. If the owner wants it, it is a small separate change. Running two instances against one campaign remains possible and is documented nowhere yet.

---

_Fixed: 2026-11-22T06:20:00Z_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
