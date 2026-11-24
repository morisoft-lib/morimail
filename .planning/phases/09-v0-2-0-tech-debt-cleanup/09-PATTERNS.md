# Phase 9: v0.2.0 tech debt cleanup - Pattern Map

**Mapped:** 2026-11-24
**Files analyzed:** 12 edited, 0 created
**Analogs found:** 12 / 12 (all edits sit next to an in-file analog)

All paths are git-tracked. Line numbers are for HEAD `58ad024`. Edit locations are in 09-RESEARCH.md "Edit Map". This file gives only the code to copy.

## File Classification

| Modified file | Role | Data flow | Analog (same file or sibling) | Match |
|---|---|---|---|---|
| `morimail/src/main/java/de/morisoft/morimail/Mail.java` | model/builder | transform | own `checkReferences` (509-533), private static helpers `contentIdProblem`, `contentTypeOf` | exact |
| `morimail/src/test/java/de/morisoft/morimail/MailBuilderTest.java` | test | request-response | `longReferenceIsCutInTheMessage` (~526) | exact |
| `morimail-sample-serial/.../sample/Campaign.java` | utility | transform | own `CID_REFERENCE` (48) | exact |
| `morimail-sample-serial/.../sample/SendCommand.java` | controller (CLI) | batch | own `invalid` map plus `invalidMail` (122, 172-214) | exact |
| `morimail-sample-serial/src/test/.../PreflightTest.java` | test | batch | `brokenCampaignReportsEveryProblemTogether` (44) | exact |
| `morimail-sample-serial/src/test/.../CampaignTest.java` | test | transform | `cidPatternAgreesWithTheCore` (427-455) | exact |
| `morimail-html/.../html/HtmlImages.java` | utility | transform | own `CID_REFERENCE` (111), `CONTENT_TYPES` (132-137) | exact |
| `morimail-html/src/test/.../HtmlImagesTest.java` | test | transform | `cidPatternAgreesWithTheCore` (268-298), `localTypeAgreesWithTheCore` (235) | exact |
| `docs/architecture.md` (40), `docs/spec.md` (321) | docs | n/a | existing rows | exact |
| `.planning/phases/{05,07,08}-*` dispositions and summaries | planning | n/a | `07-REVIEW-DISPOSITION.md` for the `open:` count rule | role-match |

## Pattern Assignments

### Core WR-01 test: `MailBuilderTest.java` (analog `longReferenceIsCutInTheMessage`, lines 526-531)

Copy this shape (`valid()` helper, `InvalidMailException`, exact `hasMessage`):
```java
@Test
void longReferenceIsCutInTheMessage() {
    String id = "a".repeat(100);

    assertThatThrownBy(() -> valid().html("<img src=\"cid:" + id + "\">").build())
            .isInstanceOf(InvalidMailException.class)
            .hasMessage("no inline image for cid:" + "a".repeat(64) + "...");
}
```
New test goes right after it. Expect `"no inline image for cid:a?b?c?d"` for `"<p>cid:a\u001bb\u0085c\u2028d</p>"` (`\s` is ASCII only, so these three flow into the ID). Also assert the message contains none of the three characters. Keep the existing test green (code point cut at 64, then `...`).

The `/>` test (D-06, Pitfall 4) goes here too, not in the corpus. Use the same style as `missingImageIsReportedBeforeAnUnreferencedOne` (just above):
```java
Path png = file("logo.png");
assertThatThrownBy(() -> valid().html("<img src=\"cid:a\">").inline(png, "b").build())
        .isInstanceOf(InvalidMailException.class)
        .hasMessage("no inline image for cid:a");
```
For D-06 use `html("<img src=cid:logo/>").inline(png, "logo")`, expecting `no inline image for cid:logo/`.

The D-08 content-type test goes beside `resolvesContentTypeCaseInsensitively` (~676), using the file(name) helper (line 24). Use `@ParameterizedTest @CsvSource({"logo.webp,image/webp", ...})`.

### Core `Mail.java` cleaning (lines 522-524)

The current code is the following. Replace the `shown` computation with the private static helper from RESEARCH Code Example A.
```java
String shown = id.codePointCount(0, id.length()) <= 64 ? id : id.substring(0, id.offsetByCodePoints(0, 64)) + "...";
throw new InvalidMailException("no inline image for cid:" + shown);
```
Pattern change (line 48-49): `Pattern.compile("(?i)(?<![\\p{L}\\p{N}])cid:([^\"'\\s)<>]*)");`. The same string goes in `Campaign.java:48` and `HtmlImages.java:111`.

### Both `cidPatternAgreesWithTheCore` tests (corpus plus agreement loop)

The sample is `CampaignTest.java:433-455` and the helper is `HtmlImagesTest.java:273-298`. Both share this structure:
```java
List<Map.Entry<String, Set<String>>> cases = List.of(
        Map.entry("<td background=cid:logo>", Set.of("logo")),
        Map.entry("<a href=\"cid:\">x</a>", Set.<String>of()), ...);
for (Map.Entry<String, Set<String>> entry : cases) {
    Set<String> found = new HashSet<>();
    Matcher matcher = Campaign.CID_REFERENCE.matcher(entry.getKey());   // HtmlImages.CID_REFERENCE in the helper
    while (matcher.find()) { if (!matcher.group(1).isEmpty()) found.add(matcher.group(1)); }
    assertThat(found).as(entry.getKey()).isEqualTo(entry.getValue());
    Mail.Builder builder = Mail.using("main").to("anna@example.org").subject("s").html(entry.getKey());
    for (String id : entry.getValue()) { builder.inline(root.resolve(id + ".png"), id); }  // base.resolve in the helper
    builder.build();
}
```
Add `Map.entry("<p>cid:logo</p>", Set.of("logo"))` to both lists. It fails before the pattern change, so write it first. `logo.png` already exists in both fixtures.

### Helper content types: `HtmlImagesTest.localTypeAgreesWithTheCore` (line 235)

It uses `Map.of("logo.png", ..., "LOGO.PNG", ...)` with 5 pairs and checks helper type equals core type equals expected. Add the three new pairs (8 total, under the 10-pair `Map.of` limit). The `HtmlImages.java` table is `Map.ofEntries(png, jpg, jpeg, gif)` and takes `Map.entry("webp", "image/webp")`, `bmp`, and `svg` -> `image/svg+xml`.

### Sample pre-flight: `SendCommand.java`

Group `validate` failures the same way as `invalid` (lines 172-198). The grouping idiom and the reporting call to copy:
```java
invalid.computeIfAbsent(e.getMessage(), m -> new ArrayList<>()).add(row.line());
...
for (Map.Entry<String, List<Integer>> entry : invalid.entrySet()) {
    problems.add(invalidMail(entry.getKey(), entry.getValue(), campaign.rows().size()));
}
```
Replace the block at lines 208-214 (`mails.get(0)`, "rejected the first mail") with the loop in RESEARCH Code Example C. It iterates `mails`, skips `null`, catches `PermanentMailException`, keys by `"mailer.validate rejected the mail: " + e.getMessage()`, and reports after the `invalid` entries. `invalidMail` output forms are one line `recipients.csv line N: msg`, `msg (all N rows)`, and `msg (recipients.csv lines a, b ...)`.

### Pre-flight tests: `PreflightTest.java`

The existing test to update (lines 44-65) uses the `recording` transport with a global `failValidation`:
```java
Path account = SampleFixtures.writeAccount(root, ACCOUNT);
Path campaign = SampleFixtures.writeCampaign(root, BROKEN_CSV);
MailAccount recording = SampleFixtures.recordingAccount();
RecordingTransport transport = (RecordingTransport) recording.transport();
transport.failValidation(new InvalidMailException("too large for the test"));
SampleFixtures.Run run = SampleFixtures.run(a -> Mailer.of(recording), new ArrayList<>(),
        "send", "--account", account.toString(), "--campaign", campaign.toString());
assertThat(run.exit()).isEqualTo(2);
assertThat(run.out()).isEmpty();
assertThat(run.errLines()).containsExactly(PLACEHOLDER_CITY, DUPLICATE, INVALID_ADDRESS,
        "recipients.csv line 2: mailer.validate rejected the first mail: too large for the test",   // CHANGE
        "Pre-flight failed with 4 problem(s); nothing was sent.");
assertThat(transport.attempts()).isZero();
assertThat(campaign.resolve("journal.jsonl")).doesNotExist();
```
The changed line becomes `"mailer.validate rejected the mail: too large for the test (recipients.csv lines 2, 4)"`. Update it in the same commit as the `SendCommand` change.

For the new "later row fails only" test, `recording` cannot do it. The account setup, from `SampleFixtures.recordingAccount()` (line 87), is `MailAccount.builder("main").transport("recording").build()`. Mirror it for Graph:
```java
MailAccount graph = MailAccount.builder("main").transport("graph").auth(scope -> "token").build();
SampleFixtures.run(a -> Mailer.of(graph), new ArrayList<>(), "send", "--account", ..., "--campaign", ...)
```
`TokenProvider` is a functional interface, so a lambda stub is enough. `GraphValidateTest` uses a `StubTokenProvider("t1","t2")` (same package as the Graph transport, not visible from the sample module), so use the lambda. `GraphTransport.validate` is local only. The CSV's third row gets a huge `city` cell (the template has `$$city$$`), so only row 4 of the file fails. Assert that the `errLines()` line starts with `recipients.csv line 4: mailer.validate rejected the mail: ` and does not compare the full text, because the size is in the message.

For the grouping test, use a sparse shared attachment, copied from `GraphValidateTest.java:55-60`:
```java
private Path sparse(String name, long size) throws IOException {
    Path file = dir.resolve(name);
    try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "rw")) { raf.setLength(size); }
    return file;
}
```
Put it in `<campaign>/attachments/` at 3 MiB (3145728 bytes) and expect one line ending `(all N rows)`. Write the campaign with `SampleFixtures.writeCampaign(root, csv)` (line 56; it creates `campaign/attachments`, `body.html`, `recipients.csv`).

## Shared Patterns

- **Three `CID_REFERENCE` copies change in one commit:** `Mail.java:48`, `Campaign.java:48`, `HtmlImages.java:111`. Both corpus tests catch drift.
- **Fixed-text messages:** `InvalidMailException` for build problems and `PermanentMailException` from `mailer.validate`. Only the cleaned core `cid:` echo may carry HTML-read text.
- **Javadoc:** write `{@code <}`, never a bare `<`. Run `./mvnw -B -ntp -Prelease verify -Dgpg.skip -pl morimail,morimail-html -am`.
- **YAML frontmatter edits:** never add `: ` inside a plain scalar (`07-01-SUMMARY.md` line 11 `cid: pattern`; `08-02-SUMMARY.md` line 10). Check with `gsd-tools query summary-extract <repo-relative path>`.
- **Disposition `open:` count:** every row that is not `fixed` counts (the `07-REVIEW-DISPOSITION.md` shape).
- **Commit identity:** export all four `GIT_*` variables as Stephan Wald and `stephan@morisoft.de`.

## No Analog Found

None. Nothing is created. The pre-flight "later row only" test needs the real `graph` transport because `RecordingTransport.failValidation` is global (no new seam, Rule 0).

## Metadata

**Analog search scope:** `morimail`, `morimail-sample-serial`, `morimail-html` main and test sources, `docs/`, `.planning/phases/{05,07,08}`
**Pattern extraction date:** 2026-11-24
