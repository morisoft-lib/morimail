package de.morisoft.morimail.sample;

import static de.morisoft.morimail.sample.SampleFixtures.ACCOUNT;
import static de.morisoft.morimail.sample.SampleFixtures.INLINE_HTML;
import static de.morisoft.morimail.sample.SampleFixtures.THREE_ROWS;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import de.morisoft.morimail.Attachment;
import de.morisoft.morimail.MailAccount;
import de.morisoft.morimail.MailMessage;
import de.morisoft.morimail.Mailer;
import de.morisoft.morimail.PermanentMailException;
import de.morisoft.morimail.test.RecordingTransport;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SendRunTest {

    @TempDir
    Path root;

    @Test
    void sendsEveryRowOnceAndJournalsIt() throws IOException {
        Path account = SampleFixtures.writeAccount(root, ACCOUNT);
        Path campaign = SampleFixtures.writeCampaign(root, THREE_ROWS);
        MailAccount recording = SampleFixtures.recordingAccount();
        List<Duration> waits = new ArrayList<>();

        SampleFixtures.Run run = SampleFixtures.run(a -> Mailer.of(recording), waits,
                "send", "--account", account.toString(), "--campaign", campaign.toString());

        assertThat(run.exit()).isZero();
        assertThat(run.outLines()).containsExactly(
                "[1/3] anna@example.org accepted",
                "[2/3] ben@example.org accepted",
                "[3/3] clara@example.org accepted",
                "accepted 3, skipped 0, failed 0");
        assertThat(run.err()).isEmpty();
        List<MailMessage> sent = ((RecordingTransport) recording.transport()).sent();
        assertThat(sent).hasSize(3);
        String[] addresses = {"anna@example.org", "ben@example.org", "clara@example.org"};
        String[] subjects = {"Hello Anna", "Hello Ben", "Hello Clara"};
        for (int i = 0; i < 3; i++) {
            assertThat(sent.get(i).from().email()).isEqualTo("newsletter@example.com");
            assertThat(sent.get(i).to().get(0).email()).isEqualTo(addresses[i]);
            assertThat(sent.get(i).subject()).isEqualTo(subjects[i]);
        }
        assertThat(waits).containsExactly(Duration.ofSeconds(3), Duration.ofSeconds(3));
        List<JsonNode> journal = SampleFixtures.journalLines(campaign.resolve("journal.jsonl"));
        assertThat(journal).hasSize(3);
        for (int i = 0; i < 3; i++) {
            JsonNode line = journal.get(i);
            assertThat(line.get("key").asText()).isEqualTo("campaign:" + addresses[i]);
            assertThat(line.get("email").asText()).isEqualTo(addresses[i]);
            assertThat(line.get("outcome").asText()).isEqualTo("accepted");
            assertThat(Instant.parse(line.get("timestamp").asText())).isNotNull();
            assertThat(line.has("errorClass")).isFalse();
        }
    }

    @Test
    void sentMailsCarryTheInlineImageAndKeepTheCidReference() throws IOException {
        Path account = SampleFixtures.writeAccount(root, ACCOUNT);
        Path campaign = SampleFixtures.writeCampaign(root, THREE_ROWS);
        Files.writeString(campaign.resolve("body.html"), INLINE_HTML);
        SampleFixtures.writeInline(campaign, "logo.png");
        MailAccount recording = SampleFixtures.recordingAccount();

        SampleFixtures.Run run = SampleFixtures.run(a -> Mailer.of(recording), new ArrayList<>(),
                "send", "--account", account.toString(), "--campaign", campaign.toString());

        assertThat(run.exit()).isZero();
        List<MailMessage> sent = ((RecordingTransport) recording.transport()).sent();
        assertThat(sent).hasSize(3);
        String[] names = {"Anna", "Ben", "Clara"};
        String[] cities = {"Berlin", "Hamburg", "Köln"};
        for (int i = 0; i < 3; i++) {
            assertThat(sent.get(i).html())
                    .isEqualTo("<p><img src=\"cid:logo\"> Hi " + names[i] + " from " + cities[i] + "</p>");
            assertThat(sent.get(i).attachments()).hasSize(2);
            assertThat(sent.get(i).attachments().get(0).filename()).isEqualTo("info.txt");
            assertThat(sent.get(i).attachments().get(0).contentId()).isNull();
            assertThat(sent.get(i).attachments().get(1).filename()).isEqualTo("logo.png");
            assertThat(sent.get(i).attachments().get(1).contentType()).isEqualTo("image/png");
            assertThat(sent.get(i).attachments().get(1).contentId()).isEqualTo("logo");
        }
        assertThat(SampleFixtures.journalLines(campaign.resolve("journal.jsonl")))
                .hasSize(3)
                .allSatisfy(line -> assertThat(line.get("outcome").asText()).isEqualTo("accepted"));
    }

    @Test
    void inlineImagesFollowTheAttachmentsInFileNameOrder() throws IOException {
        Path account = SampleFixtures.writeAccount(root, ACCOUNT);
        Path campaign = SampleFixtures.writeCampaign(root, THREE_ROWS);
        Files.writeString(campaign.resolve("body.html"),
                "<p><img src=\"cid:a\"><img src=\"cid:B\"> Hi $$firstName$$ from $$city$$</p>");
        Path inline = SampleFixtures.writeInline(campaign, "B.png", "a.gif", ".DS_Store");
        Files.createDirectories(inline.resolve("sub"));
        MailAccount recording = SampleFixtures.recordingAccount();

        SampleFixtures.Run run = SampleFixtures.run(a -> Mailer.of(recording), new ArrayList<>(),
                "send", "--account", account.toString(), "--campaign", campaign.toString());

        assertThat(run.exit()).isZero();
        MailMessage first = ((RecordingTransport) recording.transport()).sent().get(0);
        assertThat(first.attachments()).extracting(Attachment::filename)
                .containsExactly("info.txt", "B.png", "a.gif");
        assertThat(first.attachments()).extracting(Attachment::contentId)
                .containsExactly(null, "B", "a");
        assertThat(first.html()).contains("cid:a").contains("cid:B");
    }

    @Test
    void rerunSkipsAcceptedRowsAndResendsFailedOnes() throws IOException {
        Path account = SampleFixtures.writeAccount(root, ACCOUNT);
        Path campaign = SampleFixtures.writeCampaign(root, THREE_ROWS);
        MailAccount first = SampleFixtures.recordingAccount();
        ((RecordingTransport) first.transport())
                .failFor("ben@example.org", new PermanentMailException("rejected by test"));
        String[] args = {"send", "--account", account.toString(), "--campaign", campaign.toString()};

        SampleFixtures.Run firstRun = SampleFixtures.run(a -> Mailer.of(first), new ArrayList<>(), args);

        assertThat(firstRun.exit()).isEqualTo(1);
        assertThat(firstRun.outLines()).contains(
                "[2/3] ben@example.org failed: PermanentMailException: rejected by test",
                "accepted 2, skipped 0, failed 1");
        JsonNode ben = SampleFixtures.journalLines(campaign.resolve("journal.jsonl")).get(1);
        assertThat(ben.get("outcome").asText()).isEqualTo("failed");
        assertThat(ben.get("errorClass").asText()).isEqualTo("PermanentMailException");
        assertThat(ben.get("errorMessage").asText()).isEqualTo("rejected by test");

        MailAccount second = SampleFixtures.recordingAccount();
        List<Duration> waits = new ArrayList<>();
        SampleFixtures.Run secondRun = SampleFixtures.run(a -> Mailer.of(second), waits, args);

        assertThat(secondRun.exit()).isZero();
        assertThat(secondRun.outLines()).containsExactly(
                "[1/3] anna@example.org skipped (journal)",
                "[2/3] ben@example.org accepted",
                "[3/3] clara@example.org skipped (journal)",
                "accepted 1, skipped 2, failed 0");
        assertThat(((RecordingTransport) second.transport()).attempts()).isEqualTo(1);
        assertThat(waits).isEmpty();
    }

    @Test
    void rerunOfACompleteCampaignSendsNothing() throws IOException {
        Path account = SampleFixtures.writeAccount(root, ACCOUNT);
        Path campaign = SampleFixtures.writeCampaign(root, THREE_ROWS);
        String[] args = {"send", "--account", account.toString(), "--campaign", campaign.toString()};
        MailAccount first = SampleFixtures.recordingAccount();
        SampleFixtures.run(a -> Mailer.of(first), new ArrayList<>(), args);

        MailAccount second = SampleFixtures.recordingAccount();
        List<Duration> waits = new ArrayList<>();
        SampleFixtures.Run run = SampleFixtures.run(a -> Mailer.of(second), waits, args);

        assertThat(run.exit()).isZero();
        assertThat(run.outLines()).containsExactly(
                "[1/3] anna@example.org skipped (journal)",
                "[2/3] ben@example.org skipped (journal)",
                "[3/3] clara@example.org skipped (journal)",
                "accepted 0, skipped 3, failed 0");
        assertThat(((RecordingTransport) second.transport()).attempts()).isZero();
        assertThat(waits).isEmpty();
        assertThat(SampleFixtures.journalLines(campaign.resolve("journal.jsonl"))).hasSize(3);
    }

    @Test
    void realRunWithoutTheSecretStopsBeforeSending() throws IOException {
        Path account = SampleFixtures.writeAccount(root, ACCOUNT);
        Path campaign = SampleFixtures.writeCampaign(root, THREE_ROWS);

        SampleFixtures.Run run = SampleFixtures.run(
                a -> SerialMail.mailer(a, name -> null, SerialMail.AUTHORITY, SerialMail.GRAPH_BASE_URL),
                new ArrayList<>(),
                "send", "--account", account.toString(), "--campaign", campaign.toString());

        assertThat(run.exit()).isEqualTo(2);
        assertThat(run.errLines()).containsExactly(
                "account: secretEnv: the environment variable it names is not set or is empty",
                "Pre-flight failed with 1 problem(s); nothing was sent.");
        assertThat(campaign.resolve("journal.jsonl")).doesNotExist();
    }

    @Test
    void missingCommandOrOptionsExitTwo() {
        SampleFixtures.Run none = SampleFixtures.run(a -> null, new ArrayList<>());
        SampleFixtures.Run noOptions = SampleFixtures.run(a -> null, new ArrayList<>(), "send");
        SampleFixtures.Run unknown = SampleFixtures.run(a -> null, new ArrayList<>(), "send", "--bogus");

        assertThat(none.exit()).isEqualTo(2);
        assertThat(none.err()).contains("Missing required subcommand");
        assertThat(noOptions.exit()).isEqualTo(2);
        assertThat(noOptions.err()).contains("Missing required option");
        assertThat(unknown.exit()).isEqualTo(2);
    }

    @Test
    void campaignIdDefaultsToTheNormalisedDirectoryName() throws IOException {
        Path account = SampleFixtures.writeAccount(root, ACCOUNT);
        Path campaign = SampleFixtures.writeCampaign(root, THREE_ROWS);
        MailAccount recording = SampleFixtures.recordingAccount();

        SampleFixtures.Run run = SampleFixtures.run(a -> Mailer.of(recording), new ArrayList<>(),
                "send", "--account", account.toString(),
                "--campaign", campaign.resolve(".").toString());

        assertThat(run.exit()).isZero();
        for (JsonNode line : SampleFixtures.journalLines(campaign.resolve("journal.jsonl"))) {
            assertThat(line.get("key").asText()).startsWith("campaign:");
        }
        assertThat(Files.readAllLines(campaign.resolve("journal.jsonl"))).hasSize(3);
    }
}
