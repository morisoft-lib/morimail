package de.morisoft.morimail.sample;

import static org.assertj.core.api.Assertions.assertThat;

import de.morisoft.morimail.Mail;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CampaignTest {

    @TempDir
    Path root;

    private final List<String> problems = new ArrayList<>();

    /** Writes a campaign without placeholders, so only the CSV decides what is reported. */
    private Path campaign(String csv) throws IOException {
        Path dir = root.resolve("campaign");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("subject.txt"), "Hello\n");
        Files.writeString(dir.resolve("body.html"), "<p>Hi</p>");
        Files.writeString(dir.resolve("recipients.csv"), csv);
        return dir;
    }

    private static List<String> cells(List<Campaign.CsvRecord> records, int index) {
        return records.get(index).cells();
    }

    private static List<Integer> lines(List<Campaign.CsvRecord> records) {
        return records.stream().map(Campaign.CsvRecord::line).toList();
    }

    @Test
    void quotedCommaStaysInTheField() {
        List<Campaign.CsvRecord> records = Campaign.parseCsv("\"x,y\",z\n", problems);

        assertThat(records).hasSize(1);
        assertThat(cells(records, 0)).containsExactly("x,y", "z");
        assertThat(problems).isEmpty();
    }

    @Test
    void doubledQuoteIsOneQuote() {
        List<Campaign.CsvRecord> records = Campaign.parseCsv("\"say \"\"hi\"\"\"\n", problems);

        assertThat(cells(records, 0)).containsExactly("say \"hi\"");
    }

    @Test
    void quotedLineBreakKeepsLineNumbers() {
        List<Campaign.CsvRecord> records =
                Campaign.parseCsv("h\n\"a\r\nb\",c\nnext\n", problems);

        assertThat(lines(records)).containsExactly(1, 2, 4);
        assertThat(cells(records, 1)).containsExactly("a\r\nb", "c");
    }

    @Test
    void everyLineEndingEndsARecord() {
        List<Campaign.CsvRecord> records = Campaign.parseCsv("a\nb\r\nc\rd", problems);

        assertThat(records).hasSize(4);
        assertThat(lines(records)).containsExactly(1, 2, 3, 4);
        assertThat(cells(records, 3)).containsExactly("d");
    }

    @Test
    void emptyLinesAreSkippedButCounted() {
        List<Campaign.CsvRecord> records = Campaign.parseCsv("a\n\nb", problems);

        assertThat(lines(records)).containsExactly(1, 3);
    }

    @Test
    void finalLineEndingAddsNoRecord() {
        assertThat(Campaign.parseCsv("a\nb\n", problems)).hasSize(2);
        assertThat(Campaign.parseCsv("a\nb\r\n", problems)).hasSize(2);
        assertThat(cells(Campaign.parseCsv("a\nb", problems), 1)).containsExactly("b");
    }

    @Test
    void unterminatedQuoteNamesItsStartLine() {
        List<Campaign.CsvRecord> records = Campaign.parseCsv("a\n\"open", problems);

        assertThat(problems).containsExactly("recipients.csv line 2: a quoted field is not closed");
        assertThat(records).hasSize(1);
    }

    @Test
    void quoteInsideUnquotedFieldIsLiteral() {
        assertThat(cells(Campaign.parseCsv("a\"b\n", problems), 0)).containsExactly("a\"b");
    }

    @Test
    void textAfterClosingQuoteIsAppended() {
        assertThat(cells(Campaign.parseCsv("\"ab\"c\n", problems), 0)).containsExactly("abc");
    }

    @Test
    void bomBeforeHeaderIsRemoved() throws IOException {
        Path dir = campaign("\uFEFFemail,city\nanna@example.org,Berlin\n");

        Campaign campaign = Campaign.read(dir, problems);

        assertThat(problems).isEmpty();
        assertThat(campaign.rows().get(0).email()).isEqualTo("anna@example.org");
        assertThat(campaign.rows().get(0).fields()).containsOnlyKeys("email", "city");
    }

    @Test
    void umlautsSurvive() throws IOException {
        Path dir = campaign("email,city\nanna@example.org,Köln\n");

        Campaign campaign = Campaign.read(dir, problems);

        assertThat(campaign.rows().get(0).fields().get("city")).isEqualTo("Köln");
    }

    @Test
    void duplicateHeaderColumnIsAProblem() throws IOException {
        Path dir = campaign("email,city,city\nanna@example.org,Berlin,Köln\n");

        Campaign campaign = Campaign.read(dir, problems);

        assertThat(campaign).isNull();
        assertThat(problems).containsExactly(
                "recipients.csv: column 'city' appears more than once in the header");
    }

    @Test
    void headerNamesAreMatchedExactly() throws IOException {
        Path upper = campaign("Email,firstName\nanna@example.org,Anna\n");
        assertThat(Campaign.read(upper, problems)).isNull();
        Path spaced = campaign(" email,firstName\nanna@example.org,Anna\n");
        assertThat(Campaign.read(spaced, problems)).isNull();

        assertThat(problems).containsExactly(
                "recipients.csv: no column named 'email' in the header",
                "recipients.csv: no column named 'email' in the header");
    }

    @Test
    void wrongFieldCountNamesTheLine() throws IOException {
        Path dir = campaign("email,a,b\nanna@example.org,1,2\nben@example.org,1\n"
                + "clara@example.org,1,2\n");

        Campaign campaign = Campaign.read(dir, problems);

        assertThat(problems).containsExactly("recipients.csv line 3: 2 fields, but the header has 3");
        assertThat(campaign.rows()).extracting(Campaign.Row::email)
                .containsExactly("anna@example.org", "clara@example.org");
    }

    @Test
    void addressCellIsNotTrimmed() throws IOException {
        Path dir = campaign("email\nanna@example.org \n");

        Campaign campaign = Campaign.read(dir, problems);

        assertThat(campaign.rows().get(0).email()).isEqualTo("anna@example.org ");
    }

    @Test
    void invalidUtf8IsAProblem() throws IOException {
        Path dir = campaign("email\n");
        Files.write(dir.resolve("recipients.csv"),
                new byte[] {'e', 'm', 'a', 'i', 'l', '\n', 'a', (byte) 0xE4, '\n'});

        assertThat(Campaign.read(dir, problems)).isNull();

        assertThat(problems).containsExactly("recipients.csv is not valid UTF-8");
    }

    @Test
    void subjectTrailingLineBreaksAreTrimmed() throws IOException {
        Path dir = campaign("email\nanna@example.org\n");
        Files.writeString(dir.resolve("subject.txt"), "Hi\r\n\r\n");

        assertThat(Campaign.read(dir, problems).template().subject()).isEqualTo("Hi");
    }

    @Test
    void subjectBomIsRemoved() throws IOException {
        Path dir = campaign("email\nanna@example.org\n");
        Files.write(dir.resolve("subject.txt"), "\uFEFFHi\n".getBytes(StandardCharsets.UTF_8));

        assertThat(Campaign.read(dir, problems).template().subject()).isEqualTo("Hi");
    }

    @Test
    void bodyTxtIsOptional() throws IOException {
        Path dir = campaign("email\nanna@example.org\n");

        assertThat(Campaign.read(dir, problems).template().text()).isNull();
        assertThat(problems).isEmpty();
    }

    @Test
    void bodyHtmlIsRequired() throws IOException {
        Path dir = campaign("email\nanna@example.org\n");
        Files.delete(dir.resolve("body.html"));

        assertThat(Campaign.read(dir, problems)).isNull();

        assertThat(problems).containsExactly("body.html is missing");
    }

    @Test
    void attachmentsAreSortedAndDirectoriesSkipped() throws IOException {
        Path dir = campaign("email\nanna@example.org\n");
        Path attachments = Files.createDirectories(dir.resolve("attachments"));
        Files.writeString(attachments.resolve("a.txt"), "a");
        Files.writeString(attachments.resolve("B.txt"), "b");
        Files.writeString(attachments.resolve("c.txt"), "c");
        Files.createDirectory(attachments.resolve("d"));

        Campaign campaign = Campaign.read(dir, problems);

        assertThat(campaign.attachments()).extracting(file -> file.getFileName().toString())
                .containsExactly("B.txt", "a.txt", "c.txt");
    }

    @Test
    void hiddenFilesAreNotAttached() throws IOException {
        Path dir = campaign("email\nanna@example.org\n");
        Path attachments = Files.createDirectories(dir.resolve("attachments"));
        Files.writeString(attachments.resolve("a.txt"), "a");
        Files.writeString(attachments.resolve(".DS_Store"), "junk");

        Campaign campaign = Campaign.read(dir, problems);

        assertThat(campaign.attachments()).extracting(file -> file.getFileName().toString())
                .containsExactly("a.txt");
    }

    @Test
    void missingAttachmentsDirectoryMeansNone() throws IOException {
        Path dir = campaign("email\nanna@example.org\n");

        assertThat(Campaign.read(dir, problems).attachments()).isEmpty();
    }

    private static final String NOT_AN_IMAGE = ", which is not an image type";
    private static final String BAD_ID =
            "contentId contains whitespace, a quote, a parenthesis or an angle bracket";

    /** A campaign whose body.html is the given text and whose inline/ holds the named files. */
    private Path campaignWithInline(String html, String... names) throws IOException {
        Path dir = campaign("email\nanna@example.org\n");
        Files.writeString(dir.resolve("body.html"), html);
        if (names.length > 0) {
            SampleFixtures.writeInline(dir, names);
        }
        return dir;
    }

    @Test
    void inlineImagesAreSortedAndHiddenFilesAndDirectoriesSkipped() throws IOException {
        Path dir = campaignWithInline("<img src=\"cid:b\"><img src=\"cid:A\">",
                "b.png", "A.gif", ".DS_Store");
        Files.createDirectory(dir.resolve("inline").resolve("d"));

        Campaign campaign = Campaign.read(dir, problems);

        assertThat(problems).isEmpty();
        assertThat(campaign.inlineImages()).extracting(file -> file.getFileName().toString())
                .containsExactly("A.gif", "b.png");
    }

    @Test
    void missingInlineDirectoryMeansNone() throws IOException {
        Path dir = campaign("email\nanna@example.org\n");

        Campaign campaign = Campaign.read(dir, problems);

        assertThat(campaign.inlineImages()).isEmpty();
        assertThat(problems).isEmpty();
    }

    @Test
    void contentIdIsTheFileNameWithoutTheLastExtension() {
        assertThat(Campaign.contentId(Path.of("logo.png"))).isEqualTo("logo");
        assertThat(Campaign.contentId(Path.of("logo.v2.png"))).isEqualTo("logo.v2");
        assertThat(Campaign.contentId(Path.of("archive.tar.gz"))).isEqualTo("archive.tar");
        assertThat(Campaign.contentId(Path.of("noext"))).isEqualTo("noext");
    }

    @Test
    void cidWithoutAnImageIsAProblem() throws IOException {
        Path dir = campaignWithInline("<p><img src=\"cid:logo\"></p>");

        assertThat(Campaign.read(dir, problems)).isNull();
        assertThat(problems).containsExactly("body.html: cid:logo has no image in inline/");

        problems.clear();
        Files.createDirectory(dir.resolve("inline"));

        assertThat(Campaign.read(dir, problems)).isNull();
        assertThat(problems).containsExactly("body.html: cid:logo has no image in inline/");
    }

    @Test
    void imageWithoutACidIsAProblem() throws IOException {
        Path dir = campaignWithInline("<p>Hi</p>", "logo.png");

        assertThat(Campaign.read(dir, problems)).isNull();
        assertThat(problems).containsExactly("inline/logo.png: no cid:logo reference in body.html");

        problems.clear();
        Files.writeString(dir.resolve("body.html"), "");

        assertThat(Campaign.read(dir, problems)).isNull();
        assertThat(problems).containsExactly("inline/logo.png: no cid:logo reference in body.html");
    }

    @Test
    void sameBaseNameIsOneProblemPerExtraFile() throws IOException {
        Path dir = campaignWithInline("<img src=\"cid:logo\"><img src=\"cid:logo.v2\">",
                "logo.gif", "logo.jpg", "logo.png", "logo.v2.png");

        assertThat(Campaign.read(dir, problems)).isNull();

        assertThat(problems).containsExactly(
                "inline/logo.jpg: same content ID as inline/logo.gif (cid:logo)",
                "inline/logo.png: same content ID as inline/logo.gif (cid:logo)");
    }

    @Test
    void nonImageFilesAreProblemsAndNeedNoReference() throws IOException {
        Path dir = campaignWithInline("<p>Hi</p>", "Thumbs.db", "desktop.ini", "notes.txt");

        assertThat(Campaign.read(dir, problems)).isNull();

        assertThat(problems).hasSize(3);
        assertThat(problems.get(0)).startsWith("inline/Thumbs.db: inline image Thumbs has content type ")
                .endsWith(NOT_AN_IMAGE);
        assertThat(problems.get(1)).startsWith("inline/desktop.ini: inline image desktop has content type ")
                .endsWith(NOT_AN_IMAGE);
        assertThat(problems.get(2)).isEqualTo("inline/notes.txt: inline image notes has content type "
                + "text/plain" + NOT_AN_IMAGE);
    }

    @Test
    void fileNameGivingAnInvalidContentIdIsAProblem() throws IOException {
        Path dir = campaignWithInline("<p>Hi</p>", "logo (1).png", "my logo.png");

        assertThat(Campaign.read(dir, problems)).isNull();

        assertThat(problems).containsExactly(
                "inline/logo (1).png: " + BAD_ID,
                "inline/my logo.png: " + BAD_ID);
        assertThat(dir.resolve("inline").resolve("logo (1).png")).exists();
        assertThat(dir.resolve("inline").resolve("my logo.png")).exists();
    }

    @Test
    void everyDefectOfAFileIsItsOwnProblem() throws IOException {
        Path dir = campaignWithInline("<p>Hi</p>", "my logo.png", "my logo.txt");

        assertThat(Campaign.read(dir, problems)).isNull();

        assertThat(problems).containsExactly(
                "inline/my logo.png: " + BAD_ID,
                "inline/my logo.txt: " + BAD_ID,
                "inline/my logo.txt: inline image my logo has content type text/plain" + NOT_AN_IMAGE,
                "inline/my logo.txt: same content ID as inline/my logo.png (cid:my logo)");

        problems.clear();
        Files.delete(dir.resolve("inline").resolve("my logo.png"));
        Files.delete(dir.resolve("inline").resolve("my logo.txt"));
        SampleFixtures.writeInline(dir, "my notes.txt");

        assertThat(Campaign.read(dir, problems)).isNull();

        assertThat(problems).containsExactly(
                "inline/my notes.txt: " + BAD_ID,
                "inline/my notes.txt: inline image my notes has content type text/plain" + NOT_AN_IMAGE);
    }

    @Test
    void inlineFilesAreCheckedEvenWhenBodyHtmlIsMissing() throws IOException {
        Path dir = campaignWithInline("<p>Hi</p>", "notes.txt", "logo.png");
        Files.delete(dir.resolve("body.html"));

        assertThat(Campaign.read(dir, problems)).isNull();

        assertThat(problems).containsExactly(
                "body.html is missing",
                "inline/notes.txt: inline image notes has content type text/plain" + NOT_AN_IMAGE);
    }

    @Test
    void cidScanFollowsTheCoreGrammar() throws IOException {
        Path dir = campaignWithInline(
                "<p>Folic acid: 5 mg</p><a href=\"cid:\">x</a><img src='CID:logo'>", "logo.png");
        Files.writeString(dir.resolve("body.txt"), "see cid:other");

        Campaign campaign = Campaign.read(dir, problems);

        assertThat(problems).isEmpty();
        assertThat(campaign).isNotNull();
    }

    @Test
    void cidIdIsCaseSensitive() throws IOException {
        Path dir = campaignWithInline("<img src=\"cid:Logo\">", "logo.png");

        assertThat(Campaign.read(dir, problems)).isNull();

        assertThat(problems).containsExactly(
                "body.html: cid:Logo has no image in inline/",
                "inline/logo.png: no cid:logo reference in body.html");
    }

    @Test
    void cidPatternAgreesWithTheCore() throws Exception {
        for (String name : List.of("logo.png", "a.png", "b.png")) {
            Files.writeString(root.resolve(name), "png");
        }
        List<Map.Entry<String, Set<String>>> cases = List.of(
                Map.entry("<img src=\"cid:logo\">", Set.of("logo")),
                Map.entry("<img src='CID:logo'>", Set.of("logo")),
                Map.entry("<div style=\"background:url(cid:logo)\">", Set.of("logo")),
                Map.entry("<td background=cid:logo>", Set.of("logo")),
                Map.entry("<p>Folic acid: 5 mg, xcid:logo</p>", Set.<String>of()),
                Map.entry("<a href=\"cid:\">x</a>", Set.<String>of()),
                Map.entry("<img src=\"cid:a\"><img src=\"cid:b\">", Set.of("a", "b")),
                Map.entry("<img src=\"cid:logo\"> and cid:logo again", Set.of("logo")),
                Map.entry("<p>(cid:a)</p>", Set.of("a")),
                Map.entry("<p>cid:a\"b</p>", Set.of("a")),
                Map.entry("<p>1cid:a and \u00e9cid:b</p>", Set.<String>of()),
                Map.entry("<p>cid:logo</p>", Set.of("logo")));

        for (Map.Entry<String, Set<String>> entry : cases) {
            Set<String> found = new HashSet<>();
            Matcher matcher = Campaign.CID_REFERENCE.matcher(entry.getKey());
            while (matcher.find()) {
                if (!matcher.group(1).isEmpty()) {
                    found.add(matcher.group(1));
                }
            }
            assertThat(found).as(entry.getKey()).isEqualTo(entry.getValue());

            Mail.Builder builder = Mail.using("main").to("anna@example.org").subject("s")
                    .html(entry.getKey());
            for (String id : entry.getValue()) {
                builder.inline(root.resolve(id + ".png"), id);
            }
            builder.build();
        }
    }
}
