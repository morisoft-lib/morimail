package de.morisoft.morimail.sample;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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
}
