package de.morisoft.morimail.sample;

import static de.morisoft.morimail.sample.SampleFixtures.ACCOUNT;
import static de.morisoft.morimail.sample.SampleFixtures.THREE_ROWS;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.morisoft.morimail.MailAccount;
import de.morisoft.morimail.Mailer;
import de.morisoft.morimail.test.RecordingTransport;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

class PacingAndOptionsTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String FOUR_ROWS = String.join("\n",
            "email,firstName,city",
            "anna@example.org,Anna,Berlin",
            "ben@example.org,Ben,Hamburg",
            "clara@example.org,Clara,Köln",
            "dora@example.org,Dora,Bonn") + "\n";

    @TempDir
    Path root;

    private Path account() throws IOException {
        return SampleFixtures.writeAccount(root, ACCOUNT);
    }

    /** Writes accepted journal lines directly, never through Journal. */
    private static void writeAccepted(Path file, String idPrefix, String... emails) throws IOException {
        StringBuilder lines = new StringBuilder();
        for (String email : emails) {
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("key", idPrefix + ":" + email);
            line.put("email", email);
            line.put("timestamp", "2026-01-01T00:00:00Z");
            line.put("outcome", "accepted");
            lines.append(MAPPER.writeValueAsString(line)).append('\n');
        }
        Files.writeString(file, lines.toString());
    }

    @ParameterizedTest
    @CsvSource({"20,3000", "7,8572", "3,20000", "1,60000", "60000,1", "120000,1"})
    void spacingIsRoundedUp(int rate, long millis) {
        assertThat(SendCommand.spacing(rate)).isEqualTo(Duration.ofMillis(millis));
    }

    @Test
    void skippedRowsNeitherWaitNorCountAsAnAttempt() throws IOException {
        Path campaign = SampleFixtures.writeCampaign(root, FOUR_ROWS);
        writeAccepted(campaign.resolve("journal.jsonl"), "campaign",
                "anna@example.org", "clara@example.org");
        MailAccount recording = SampleFixtures.recordingAccount();
        List<Duration> waits = new ArrayList<>();

        SampleFixtures.Run run = SampleFixtures.run(a -> Mailer.of(recording), waits,
                "send", "--account", account().toString(), "--campaign", campaign.toString());

        assertThat(run.exit()).isZero();
        assertThat(run.outLines()).containsExactly(
                "[1/4] anna@example.org skipped (journal)",
                "[2/4] ben@example.org accepted",
                "[3/4] clara@example.org skipped (journal)",
                "[4/4] dora@example.org accepted",
                "accepted 2, skipped 2, failed 0");
        assertThat(waits).containsExactly(Duration.ofSeconds(3));
    }

    @Test
    void rateSetsTheSpacingBetweenAttempts() throws IOException {
        Path campaign = SampleFixtures.writeCampaign(root, THREE_ROWS);
        MailAccount recording = SampleFixtures.recordingAccount();
        List<Duration> waits = new ArrayList<>();

        SampleFixtures.run(a -> Mailer.of(recording), waits, "send", "--account",
                account().toString(), "--campaign", campaign.toString(), "--rate", "7");

        assertThat(waits).containsExactly(Duration.ofMillis(8572), Duration.ofMillis(8572));
    }

    @Test
    void limitProcessesTheFirstRowsOnly() throws IOException {
        Path campaign = SampleFixtures.writeCampaign(root, THREE_ROWS);
        MailAccount recording = SampleFixtures.recordingAccount();

        SampleFixtures.Run run = SampleFixtures.run(a -> Mailer.of(recording), new ArrayList<>(),
                "send", "--account", account().toString(), "--campaign", campaign.toString(),
                "--limit", "2");

        assertThat(run.exit()).isZero();
        assertThat(run.outLines()).containsExactly(
                "[1/2] anna@example.org accepted",
                "[2/2] ben@example.org accepted",
                "accepted 2, skipped 0, failed 0");
        assertThat(((RecordingTransport) recording.transport()).attempts()).isEqualTo(2);
    }

    @Test
    void limitCountsSkippedRows() throws IOException {
        Path campaign = SampleFixtures.writeCampaign(root, THREE_ROWS);
        writeAccepted(campaign.resolve("journal.jsonl"), "campaign", "anna@example.org");
        MailAccount recording = SampleFixtures.recordingAccount();

        SampleFixtures.Run run = SampleFixtures.run(a -> Mailer.of(recording), new ArrayList<>(),
                "send", "--account", account().toString(), "--campaign", campaign.toString(),
                "--limit", "2");

        assertThat(run.outLines()).containsExactly(
                "[1/2] anna@example.org skipped (journal)",
                "[2/2] ben@example.org accepted",
                "accepted 1, skipped 1, failed 0");
    }

    @Test
    void limitLargerThanTheRowCountProcessesEveryRow() throws IOException {
        Path campaign = SampleFixtures.writeCampaign(root, THREE_ROWS);
        MailAccount recording = SampleFixtures.recordingAccount();

        SampleFixtures.Run run = SampleFixtures.run(a -> Mailer.of(recording), new ArrayList<>(),
                "send", "--account", account().toString(), "--campaign", campaign.toString(),
                "--limit", "10");

        assertThat(run.outLines().get(0)).isEqualTo("[1/3] anna@example.org accepted");
        assertThat(run.outLines().get(2)).isEqualTo("[3/3] clara@example.org accepted");
    }

    @Test
    void dryRunLimitWritesOnlyTheFirstRows() throws IOException {
        Path campaign = SampleFixtures.writeCampaign(root, THREE_ROWS);
        Path out = root.resolve("out");

        SampleFixtures.Run run = SampleFixtures.run(a -> {
            throw new AssertionError("a dry run needs no mailer");
        }, new ArrayList<>(), "send", "--account", account().toString(), "--campaign",
                campaign.toString(), "--dry-run", "--out", out.toString(), "--limit", "2");

        assertThat(run.exit()).isZero();
        assertThat(run.outLines()).contains("dry run: 2 mails written to " + out);
        try (Stream<Path> files = Files.list(out)) {
            assertThat(files.count()).isEqualTo(6);
        }
    }

    @Test
    void preflightChecksTheRowsAfterTheLimitToo() throws IOException {
        String csv = String.join("\n", "email,firstName,city",
                "anna@example.org,Anna,Berlin",
                "ben@example.org,Ben,Hamburg",
                "not-an-address,Clara,Köln") + "\n";
        Path campaign = SampleFixtures.writeCampaign(root, csv);
        MailAccount recording = SampleFixtures.recordingAccount();

        SampleFixtures.Run run = SampleFixtures.run(a -> Mailer.of(recording), new ArrayList<>(),
                "send", "--account", account().toString(), "--campaign", campaign.toString(),
                "--limit", "1");

        assertThat(run.exit()).isEqualTo(2);
        assertThat(run.errLines()).anyMatch(l -> l.startsWith("recipients.csv line 4: "));
        assertThat(((RecordingTransport) recording.transport()).attempts()).isZero();
    }

    @Test
    void campaignIdPrefixesEveryKeyAndOtherCampaignsAreIgnored() throws IOException {
        Path campaign = SampleFixtures.writeCampaign(root, THREE_ROWS);
        writeAccepted(campaign.resolve("journal.jsonl"), "other",
                "anna@example.org", "ben@example.org", "clara@example.org");
        MailAccount recording = SampleFixtures.recordingAccount();

        SampleFixtures.Run run = SampleFixtures.run(a -> Mailer.of(recording), new ArrayList<>(),
                "send", "--account", account().toString(), "--campaign", campaign.toString(),
                "--campaign-id", "nov-2026");

        assertThat(run.exit()).isZero();
        assertThat(run.outLines()).containsExactly(
                "[1/3] anna@example.org accepted",
                "[2/3] ben@example.org accepted",
                "[3/3] clara@example.org accepted",
                "accepted 3, skipped 0, failed 0");
        assertThat(run.err()).isEmpty();
        List<JsonNode> journal = SampleFixtures.journalLines(campaign.resolve("journal.jsonl"));
        assertThat(journal.subList(3, journal.size()))
                .extracting(l -> l.get("key").asText())
                .containsExactly("nov-2026:anna@example.org", "nov-2026:ben@example.org",
                        "nov-2026:clara@example.org");
    }

    @Test
    void journalOptionMovesTheJournal() throws IOException {
        Path campaign = SampleFixtures.writeCampaign(root, THREE_ROWS);
        Path custom = root.resolve("j").resolve("custom.jsonl");
        Files.createDirectories(custom.getParent());
        MailAccount recording = SampleFixtures.recordingAccount();

        SampleFixtures.Run run = SampleFixtures.run(a -> Mailer.of(recording), new ArrayList<>(),
                "send", "--account", account().toString(), "--campaign", campaign.toString(),
                "--journal", custom.toString());

        assertThat(run.exit()).isZero();
        assertThat(SampleFixtures.journalLines(custom)).hasSize(3);
        assertThat(campaign.resolve("journal.jsonl")).doesNotExist();
    }

    static Stream<Arguments> badArguments() {
        return Stream.of(
                Arguments.of("--rate", "0", "--rate must be at least 1"),
                Arguments.of("--rate", "-5", "--rate must be at least 1"),
                Arguments.of("--limit", "0", "--limit must be at least 1"),
                Arguments.of("--campaign-id", "   ", "--campaign-id must not be blank"));
    }

    @ParameterizedTest
    @MethodSource("badArguments")
    void badArgumentsArePreflightProblems(String option, String value, String message)
            throws IOException {
        Path campaign = SampleFixtures.writeCampaign(root, THREE_ROWS);
        MailAccount recording = SampleFixtures.recordingAccount();

        SampleFixtures.Run run = SampleFixtures.run(a -> Mailer.of(recording), new ArrayList<>(),
                "send", "--account", account().toString(), "--campaign", campaign.toString(),
                option, value);

        assertThat(run.exit()).isEqualTo(2);
        assertThat(run.errLines()).containsExactly(message,
                "Pre-flight failed with 1 problem(s); nothing was sent.");
        assertThat(run.out()).isEmpty();
        assertThat(((RecordingTransport) recording.transport()).attempts()).isZero();
        assertThat(campaign.resolve("journal.jsonl")).doesNotExist();
    }

    @Test
    void argumentProblemsComeFirstAndAreReportedWithCampaignProblems() throws IOException {
        String csv = "email,firstName\nanna@example.org,Anna\n";
        Path campaign = SampleFixtures.writeCampaign(root, csv);
        MailAccount recording = SampleFixtures.recordingAccount();

        SampleFixtures.Run run = SampleFixtures.run(a -> Mailer.of(recording), new ArrayList<>(),
                "send", "--account", account().toString(), "--campaign", campaign.toString(),
                "--rate", "0");

        assertThat(run.exit()).isEqualTo(2);
        assertThat(run.errLines().get(0)).isEqualTo("--rate must be at least 1");
        assertThat(run.errLines()).anyMatch(l -> l.contains("city"));
        assertThat(run.errLines().get(run.errLines().size() - 1))
                .startsWith("Pre-flight failed with 2 problem(s)");
    }

    @Test
    void campaignWithoutADirectoryNameNeedsACampaignId() throws IOException {
        MailAccount recording = SampleFixtures.recordingAccount();

        SampleFixtures.Run run = SampleFixtures.run(a -> Mailer.of(recording), new ArrayList<>(),
                "send", "--account", account().toString(), "--campaign", "/");

        assertThat(run.exit()).isEqualTo(2);
        assertThat(run.errLines()).contains("--campaign has no directory name; set --campaign-id");
    }

    @Test
    void rateThatIsNoNumberIsAUsageError() throws IOException {
        Path campaign = SampleFixtures.writeCampaign(root, THREE_ROWS);
        MailAccount recording = SampleFixtures.recordingAccount();

        SampleFixtures.Run run = SampleFixtures.run(a -> Mailer.of(recording), new ArrayList<>(),
                "send", "--account", account().toString(), "--campaign", campaign.toString(),
                "--rate", "abc");

        assertThat(run.exit()).isEqualTo(2);
        assertThat(run.err()).contains("Invalid value for option '--rate'");
    }
}
