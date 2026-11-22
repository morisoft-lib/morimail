package de.morisoft.morimail.sample;

import static de.morisoft.morimail.sample.SampleFixtures.ACCOUNT;
import static de.morisoft.morimail.sample.SampleFixtures.THREE_ROWS;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import de.morisoft.morimail.MailAccount;
import de.morisoft.morimail.Mailer;
import de.morisoft.morimail.TransientMailException;
import de.morisoft.morimail.test.RecordingTransport;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JournalTest {

    private static final String ANNA_ACCEPTED =
            "{\"key\":\"c:anna@example.org\",\"email\":\"anna@example.org\","
                    + "\"timestamp\":\"2026-01-01T00:00:00Z\",\"outcome\":\"accepted\"}";
    private static final String BEN_ACCEPTED = ANNA_ACCEPTED.replace("anna", "ben");
    private static final String BEN_START = "{\"key\":\"c:ben@example.org\",\"email\":\"ben@exa";

    @TempDir
    Path root;

    private Path journalFile() {
        return root.resolve("journal.jsonl");
    }

    private Journal open(List<String> warnings) throws IOException {
        return Journal.open(journalFile(), warnings);
    }

    @Test
    void onlyAcceptedLinesOfTheSameKeyCount() throws IOException {
        String transientBen = "{\"key\":\"c:ben@example.org\",\"outcome\":\"transient\"}";
        String failedClara = "{\"key\":\"c:clara@example.org\",\"outcome\":\"failed\"}";
        String otherDora = "{\"key\":\"other:dora@example.org\",\"outcome\":\"accepted\"}";
        Files.writeString(journalFile(),
                String.join("\n", ANNA_ACCEPTED, transientBen, failedClara, otherDora) + "\n");

        try (Journal journal = open(new ArrayList<>())) {
            assertThat(journal.isAccepted("c:anna@example.org")).isTrue();
            assertThat(journal.isAccepted("c:ben@example.org")).isFalse();
            assertThat(journal.isAccepted("c:clara@example.org")).isFalse();
            assertThat(journal.isAccepted("c:dora@example.org")).isFalse();
            assertThat(journal.isAccepted("other:dora@example.org")).isTrue();
        }
    }

    @Test
    void keyLowercasesTheAddressWithRootLocale() {
        assertThat(Journal.key("c", "Anna.Ü@Example.ORG")).isEqualTo("c:anna.ü@example.org");
        assertThat(Journal.key("c", "ben@example.org")).isEqualTo(Journal.key("c", "BEN@example.org"));
    }

    @Test
    void acceptedLineAnywhereInTheFileCounts() throws IOException {
        String failed = "{\"key\":\"c:anna@example.org\",\"outcome\":\"failed\"}";
        Files.writeString(journalFile(), String.join("\n", failed, ANNA_ACCEPTED, failed) + "\n");

        try (Journal journal = open(new ArrayList<>())) {
            assertThat(journal.isAccepted("c:anna@example.org")).isTrue();
        }
    }

    @Test
    void tornLastLineIsWarnedAboutAndTheNextRecordStartsOnItsOwnLine() throws IOException {
        Files.writeString(journalFile(), ANNA_ACCEPTED + "\n" + BEN_START);
        List<String> warnings = new ArrayList<>();

        try (Journal journal = open(warnings)) {
            assertThat(warnings).containsExactly("journal.jsonl line 2 is damaged and was ignored");
            assertThat(journal.isAccepted("c:anna@example.org")).isTrue();
            assertThat(journal.isAccepted("c:ben@example.org")).isFalse();
            journal.append("c:clara@example.org", "clara@example.org", "accepted", null);
        }

        List<String> lines = Files.readAllLines(journalFile());
        assertThat(lines).hasSize(3);
        assertThat(lines.get(0)).isEqualTo(ANNA_ACCEPTED);
        assertThat(lines.get(1)).isEqualTo(BEN_START);
        assertThat(lines.get(2)).contains("c:clara@example.org");
    }

    @Test
    void aLineThatIsNoJsonObjectIsWarnedAboutAndIgnored() throws IOException {
        Files.writeString(journalFile(), String.join("\n", ANNA_ACCEPTED, "not json", BEN_ACCEPTED) + "\n");
        List<String> warnings = new ArrayList<>();

        try (Journal journal = open(warnings)) {
            assertThat(warnings).containsExactly("journal.jsonl line 2 is damaged and was ignored");
            assertThat(journal.isAccepted("c:anna@example.org")).isTrue();
            assertThat(journal.isAccepted("c:ben@example.org")).isTrue();
        }
    }

    @Test
    void aJsonValueThatIsNotAnObjectIsDamagedToo() throws IOException {
        Files.writeString(journalFile(), "[1,2]\n" + ANNA_ACCEPTED + "\n");
        List<String> warnings = new ArrayList<>();

        try (Journal journal = open(warnings)) {
            assertThat(warnings).containsExactly("journal.jsonl line 1 is damaged and was ignored");
            assertThat(journal.isAccepted("c:anna@example.org")).isTrue();
        }
    }

    @Test
    void aTornMultibyteCharacterGivesAWarningNotAnException() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bytes.write((ANNA_ACCEPTED + "\n").getBytes(StandardCharsets.UTF_8));
        bytes.write(("{\"key\":\"c:j").getBytes(StandardCharsets.UTF_8));
        bytes.write(0xC3);
        Files.write(journalFile(), bytes.toByteArray());
        List<String> warnings = new ArrayList<>();

        try (Journal journal = open(warnings)) {
            assertThat(warnings).hasSize(1);
            assertThat(journal.isAccepted("c:anna@example.org")).isTrue();
        }
    }

    @Test
    void blankLinesAreSilentAndSkipNothing() throws IOException {
        Files.writeString(journalFile(), "\n\n" + ANNA_ACCEPTED + "\n\n");
        List<String> warnings = new ArrayList<>();

        try (Journal journal = open(warnings)) {
            assertThat(warnings).isEmpty();
            assertThat(journal.isAccepted("c:anna@example.org")).isTrue();
        }
        assertThat(Files.readString(journalFile())).isEqualTo("\n\n" + ANNA_ACCEPTED + "\n\n");
    }

    @Test
    void aMissingFileIsCreatedWithoutAWarning() throws IOException {
        List<String> warnings = new ArrayList<>();

        try (Journal journal = open(warnings)) {
            assertThat(warnings).isEmpty();
            assertThat(journal.isAccepted("c:anna@example.org")).isFalse();
        }
        assertThat(journalFile()).exists();
        assertThat(Files.size(journalFile())).isZero();
    }

    @Test
    void anEmptyFileStaysEmptyUntilTheFirstAppend() throws IOException {
        Files.write(journalFile(), new byte[0]);
        List<String> warnings = new ArrayList<>();

        try (Journal journal = open(warnings)) {
            assertThat(warnings).isEmpty();
            assertThat(Files.size(journalFile())).isZero();
            journal.append("c:anna@example.org", "anna@example.org", "accepted", null);
        }
        assertThat(Files.readAllLines(journalFile())).hasSize(1);
    }

    @Test
    void aFileEndingWithALineFeedGetsNoRepair() throws IOException {
        Files.writeString(journalFile(), ANNA_ACCEPTED + "\n");

        try (Journal journal = open(new ArrayList<>())) {
            journal.append("c:ben@example.org", "ben@example.org", "accepted", null);
        }

        assertThat(Files.readAllLines(journalFile())).hasSize(2);
    }

    @Test
    void appendWritesOneJsonLinePerAttemptWithTheFieldsInOrder() throws IOException {
        TransientMailException error = new TransientMailException("line one\nline \"two\"");

        try (Journal journal = open(new ArrayList<>())) {
            journal.append("c:anna@example.org", "anna@example.org", "transient", error);
            journal.append("c:anna@example.org", "anna@example.org", "accepted", null);
        }

        List<JsonNode> lines = SampleFixtures.journalLines(journalFile());
        assertThat(Files.readAllLines(journalFile())).hasSize(2);
        assertThat(fieldNames(lines.get(0))).containsExactly(
                "key", "email", "timestamp", "outcome", "errorClass", "errorMessage");
        assertThat(lines.get(0).get("errorMessage").asText()).isEqualTo("line one\nline \"two\"");
        assertThat(lines.get(0).get("errorClass").asText()).isEqualTo("TransientMailException");
        assertThat(Instant.parse(lines.get(0).get("timestamp").asText())).isNotNull();
        assertThat(fieldNames(lines.get(1))).containsExactly("key", "email", "timestamp", "outcome");
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        Iterator<String> it = node.fieldNames();
        it.forEachRemaining(names::add);
        return names;
    }

    @Test
    void linesAreUtf8() throws IOException {
        try (Journal journal = open(new ArrayList<>())) {
            journal.append("c:jürgen@example.org", "jürgen@example.org", "accepted", null);
        }

        assertThat(new String(Files.readAllBytes(journalFile()), StandardCharsets.UTF_8))
                .contains("jürgen");
    }

    @Test
    void aFailedJournalWriteStopsTheRun() throws IOException {
        Path devFull = Path.of("/dev/full");
        Assumptions.assumeTrue(Files.exists(devFull));
        Path account = SampleFixtures.writeAccount(root, ACCOUNT);
        Path campaign = SampleFixtures.writeCampaign(root, THREE_ROWS);
        MailAccount recording = SampleFixtures.recordingAccount();
        RecordingTransport rec = (RecordingTransport) recording.transport();

        SampleFixtures.Run run = SampleFixtures.run(a -> Mailer.of(recording), new ArrayList<>(),
                "send", "--account", account.toString(), "--campaign", campaign.toString(),
                "--journal", devFull.toString());

        assertThat(run.exit()).isEqualTo(1);
        assertThat(run.errLines()).anyMatch(l -> l.startsWith("Stopped: cannot write the journal /dev/full"));
        assertThat(run.errLines()).anyMatch(l -> l.endsWith(
                "The last mail may have been sent; a rerun sends it again."));
        assertThat(rec.attempts()).isEqualTo(1);
        assertThat(run.outLines().get(run.outLines().size() - 1))
                .isEqualTo("accepted 0, skipped 0, failed 0");
    }

    @Test
    void aJournalThatCannotBeOpenedIsAPreflightProblem() throws IOException {
        Path account = SampleFixtures.writeAccount(root, ACCOUNT);
        Path campaign = SampleFixtures.writeCampaign(root, THREE_ROWS);
        Path directory = Files.createDirectories(root.resolve("a-directory"));
        MailAccount recording = SampleFixtures.recordingAccount();

        SampleFixtures.Run run = SampleFixtures.run(a -> Mailer.of(recording), new ArrayList<>(),
                "send", "--account", account.toString(), "--campaign", campaign.toString(),
                "--journal", directory.toString());

        assertThat(run.exit()).isEqualTo(2);
        assertThat(run.errLines().get(0)).startsWith("journal " + directory + " cannot be opened: ");
        assertThat(run.errLines().get(run.errLines().size() - 1))
                .isEqualTo("Pre-flight failed with 1 problem(s); nothing was sent.");
        assertThat(((RecordingTransport) recording.transport()).attempts()).isZero();
    }

    @Test
    void aDamagedLineIsWarnedAboutAndTheRowIsSentAgain() throws IOException {
        Path account = SampleFixtures.writeAccount(root, ACCOUNT);
        Path campaign = SampleFixtures.writeCampaign(root, THREE_ROWS);
        Path journal = campaign.resolve("journal.jsonl");
        Files.writeString(journal,
                ANNA_ACCEPTED.replace("c:anna", "campaign:anna").substring(0, 60));
        MailAccount recording = SampleFixtures.recordingAccount();

        SampleFixtures.Run run = SampleFixtures.run(a -> Mailer.of(recording), new ArrayList<>(),
                "send", "--account", account.toString(), "--campaign", campaign.toString());

        assertThat(run.exit()).isZero();
        assertThat(run.errLines())
                .contains("warning: journal.jsonl line 1 is damaged and was ignored");
        assertThat(run.outLines()).containsExactly(
                "[1/3] anna@example.org accepted",
                "[2/3] ben@example.org accepted",
                "[3/3] clara@example.org accepted",
                "accepted 3, skipped 0, failed 0");
        assertThat(Files.readAllLines(journal)).hasSize(4);
    }

    @Test
    void theHelpTextStatesTheAtLeastOnceRule() {
        SampleFixtures.Run run = SampleFixtures.run(a -> {
            throw new AssertionError("no mailer");
        }, new ArrayList<>(), "send");

        assertThat(run.exit()).isEqualTo(2);
        // picocli wraps the footer at the terminal width, so compare with line breaks folded
        assertThat(run.err().replaceAll("\\s+", " ")).contains("the next run sends it again");
    }

    @Test
    void theHelpTextListsTheExitCodes() {
        SampleFixtures.Run run = SampleFixtures.run(a -> {
            throw new AssertionError("no mailer");
        }, new ArrayList<>(), "send");

        assertThat(run.err().replaceAll("\\s+", " ")).contains("3 the run was stopped by a rejected "
                + "sender or required authentication");
    }
}
