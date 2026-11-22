package de.morisoft.morimail.sample;

import static de.morisoft.morimail.sample.SampleFixtures.ACCOUNT;
import static de.morisoft.morimail.sample.SampleFixtures.THREE_ROWS;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import de.morisoft.morimail.AuthRequiredException;
import de.morisoft.morimail.InvalidMailException;
import de.morisoft.morimail.MailAccount;
import de.morisoft.morimail.MailException;
import de.morisoft.morimail.Mailer;
import de.morisoft.morimail.PermanentMailException;
import de.morisoft.morimail.SenderRejectedException;
import de.morisoft.morimail.TransientMailException;
import de.morisoft.morimail.test.RecordingTransport;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class FailureHandlingTest {

    @TempDir
    Path root;

    private String[] args(Path account, Path campaign) {
        return new String[] {"send", "--account", account.toString(), "--campaign", campaign.toString()};
    }

    @Test
    void transientFailuresAreRetriedThenAccepted() throws IOException {
        Path account = SampleFixtures.writeAccount(root, ACCOUNT);
        Path campaign = SampleFixtures.writeCampaign(root, THREE_ROWS);
        MailAccount recording = SampleFixtures.recordingAccount();
        RecordingTransport rec = (RecordingTransport) recording.transport();
        rec.failNext(new TransientMailException("busy", Duration.ofSeconds(7)),
                new TransientMailException("busy again"));
        List<Duration> waits = new ArrayList<>();

        SampleFixtures.Run run = SampleFixtures.run(a -> Mailer.of(recording), waits,
                args(account, campaign));

        assertThat(run.exit()).isZero();
        assertThat(run.outLines()).containsExactly(
                "[1/3] anna@example.org transient, retry 1/3 in 7s",
                "[1/3] anna@example.org transient, retry 2/3 in 30s",
                "[1/3] anna@example.org accepted",
                "[2/3] ben@example.org accepted",
                "[3/3] clara@example.org accepted",
                "accepted 3, skipped 0, failed 0");
        assertThat(waits).containsExactly(Duration.ofSeconds(7), Duration.ofSeconds(30),
                Duration.ofSeconds(3), Duration.ofSeconds(3));
        List<JsonNode> journal = SampleFixtures.journalLines(campaign.resolve("journal.jsonl"));
        assertThat(journal).extracting(l -> l.get("outcome").asText())
                .containsExactly("transient", "transient", "accepted", "accepted", "accepted");
        assertThat(journal.get(0).get("errorClass").asText()).isEqualTo("TransientMailException");
        assertThat(journal.get(0).get("errorMessage").asText()).isEqualTo("busy");
        assertThat(journal.get(1).get("errorClass").asText()).isEqualTo("TransientMailException");
        assertThat(journal.get(1).get("errorMessage").asText()).isEqualTo("busy again");
        assertThat(rec.attempts()).isEqualTo(5);
    }

    static List<Arguments> retryWaits() {
        return List.of(
                Arguments.of(Duration.ofMinutes(10), Duration.ofMinutes(5), 300),
                Arguments.of(Duration.ofMinutes(5), Duration.ofMinutes(5), 300),
                Arguments.of(Duration.ofSeconds(299), Duration.ofSeconds(299), 299),
                Arguments.of(Duration.ofSeconds(2), Duration.ofSeconds(3), 3),
                Arguments.of(Duration.ZERO, Duration.ofSeconds(3), 3));
    }

    @ParameterizedTest
    @MethodSource("retryWaits")
    void retryWaitIsCappedAndNeverShorterThanTheSpacing(Duration retryAfter, Duration expected,
            int printedSeconds) throws IOException {
        Path account = SampleFixtures.writeAccount(root, ACCOUNT);
        Path campaign = SampleFixtures.writeCampaign(root, THREE_ROWS);
        MailAccount recording = SampleFixtures.recordingAccount();
        ((RecordingTransport) recording.transport())
                .failNext(new TransientMailException("busy", retryAfter));
        List<Duration> waits = new ArrayList<>();

        SampleFixtures.Run run = SampleFixtures.run(a -> Mailer.of(recording), waits,
                args(account, campaign));

        assertThat(run.exit()).isZero();
        assertThat(waits.get(0)).isEqualTo(expected);
        assertThat(run.outLines().get(0))
                .isEqualTo("[1/3] anna@example.org transient, retry 1/3 in " + printedSeconds + "s");
    }

    @Test
    void fourTransientAttemptsEndAsFailedAndTheRunGoesOn() throws IOException {
        Path account = SampleFixtures.writeAccount(root, ACCOUNT);
        Path campaign = SampleFixtures.writeCampaign(root, THREE_ROWS);
        MailAccount recording = SampleFixtures.recordingAccount();
        RecordingTransport rec = (RecordingTransport) recording.transport();
        rec.failNext(new TransientMailException("busy"), new TransientMailException("busy"),
                new TransientMailException("busy"), new TransientMailException("busy"));
        List<Duration> waits = new ArrayList<>();

        SampleFixtures.Run run = SampleFixtures.run(a -> Mailer.of(recording), waits,
                args(account, campaign));

        assertThat(run.exit()).isEqualTo(1);
        assertThat(run.outLines()).containsExactly(
                "[1/3] anna@example.org transient, retry 1/3 in 30s",
                "[1/3] anna@example.org transient, retry 2/3 in 30s",
                "[1/3] anna@example.org transient, retry 3/3 in 30s",
                "[1/3] anna@example.org failed: TransientMailException: busy",
                "[2/3] ben@example.org accepted",
                "[3/3] clara@example.org accepted",
                "accepted 2, skipped 0, failed 1");
        List<JsonNode> journal = SampleFixtures.journalLines(campaign.resolve("journal.jsonl"));
        assertThat(journal.subList(0, 4)).extracting(l -> l.get("outcome").asText())
                .containsExactly("transient", "transient", "transient", "failed");
        assertThat(journal.subList(0, 4)).extracting(l -> l.get("email").asText())
                .containsOnly("anna@example.org");
        assertThat(journal).hasSize(6);
        assertThat(rec.attempts()).isEqualTo(6);
        assertThat(waits).containsExactly(Duration.ofSeconds(30), Duration.ofSeconds(30),
                Duration.ofSeconds(30), Duration.ofSeconds(3), Duration.ofSeconds(3));
    }

    static List<MailException> permanentFailures() {
        return List.of(new PermanentMailException("no such recipient"),
                new InvalidMailException("too large"));
    }

    @ParameterizedTest
    @MethodSource("permanentFailures")
    void permanentFailuresAreRecordedAndTheRunGoesOn(MailException failure) throws IOException {
        Path account = SampleFixtures.writeAccount(root, ACCOUNT);
        Path campaign = SampleFixtures.writeCampaign(root, THREE_ROWS);
        MailAccount recording = SampleFixtures.recordingAccount();
        ((RecordingTransport) recording.transport()).failFor("ben@example.org", failure);

        SampleFixtures.Run run = SampleFixtures.run(a -> Mailer.of(recording), new ArrayList<>(),
                args(account, campaign));

        assertThat(run.exit()).isEqualTo(1);
        assertThat(run.outLines()).containsExactly(
                "[1/3] anna@example.org accepted",
                "[2/3] ben@example.org failed: " + failure.getClass().getSimpleName() + ": "
                        + failure.getMessage(),
                "[3/3] clara@example.org accepted",
                "accepted 2, skipped 0, failed 1");
    }

    static List<MailException> stoppingFailures() {
        return List.of(new SenderRejectedException("mailbox not allowed"),
                new AuthRequiredException("consent missing"));
    }

    @ParameterizedTest
    @MethodSource("stoppingFailures")
    void senderRejectedOrAuthRequiredStopsTheRun(MailException failure) throws IOException {
        Path account = SampleFixtures.writeAccount(root, ACCOUNT);
        Path campaign = SampleFixtures.writeCampaign(root, THREE_ROWS);
        MailAccount recording = SampleFixtures.recordingAccount();
        RecordingTransport rec = (RecordingTransport) recording.transport();
        rec.failFor("ben@example.org", failure);
        String name = failure.getClass().getSimpleName();

        SampleFixtures.Run run = SampleFixtures.run(a -> Mailer.of(recording), new ArrayList<>(),
                args(account, campaign));

        assertThat(run.exit()).isEqualTo(3);
        assertThat(run.outLines()).containsExactly(
                "[1/3] anna@example.org accepted",
                "[2/3] ben@example.org failed: " + name + ": " + failure.getMessage(),
                "accepted 1, skipped 0, failed 1");
        assertThat(run.errLines()).contains("Stopped: " + name + ": " + failure.getMessage());
        assertThat(rec.attempts()).isEqualTo(2);
        List<JsonNode> journal = SampleFixtures.journalLines(campaign.resolve("journal.jsonl"));
        assertThat(journal).extracting(l -> l.get("email").asText() + " " + l.get("outcome").asText())
                .containsExactly("anna@example.org accepted", "ben@example.org failed");
    }

    @Test
    void authRequiredDuringRetriesStopsAtOnce() throws IOException {
        Path account = SampleFixtures.writeAccount(root, ACCOUNT);
        Path campaign = SampleFixtures.writeCampaign(root, THREE_ROWS);
        MailAccount recording = SampleFixtures.recordingAccount();
        RecordingTransport rec = (RecordingTransport) recording.transport();
        rec.failNext(new TransientMailException("busy"), new AuthRequiredException("token rejected"));

        SampleFixtures.Run run = SampleFixtures.run(a -> Mailer.of(recording), new ArrayList<>(),
                args(account, campaign));

        assertThat(run.exit()).isEqualTo(3);
        assertThat(run.outLines()).containsExactly(
                "[1/3] anna@example.org transient, retry 1/3 in 30s",
                "[1/3] anna@example.org failed: AuthRequiredException: token rejected",
                "accepted 0, skipped 0, failed 1");
        assertThat(rec.attempts()).isEqualTo(2);
    }

    @Test
    void aStoppedRunResumesWithTheFailedRow() throws IOException {
        Path account = SampleFixtures.writeAccount(root, ACCOUNT);
        Path campaign = SampleFixtures.writeCampaign(root, THREE_ROWS);
        MailAccount first = SampleFixtures.recordingAccount();
        ((RecordingTransport) first.transport())
                .failFor("ben@example.org", new AuthRequiredException("consent missing"));
        String[] args = args(account, campaign);
        assertThat(SampleFixtures.run(a -> Mailer.of(first), new ArrayList<>(), args).exit())
                .isEqualTo(3);

        MailAccount second = SampleFixtures.recordingAccount();
        SampleFixtures.Run run = SampleFixtures.run(a -> Mailer.of(second), new ArrayList<>(), args);

        assertThat(run.exit()).isZero();
        assertThat(run.outLines()).containsExactly(
                "[1/3] anna@example.org skipped (journal)",
                "[2/3] ben@example.org accepted",
                "[3/3] clara@example.org accepted",
                "accepted 2, skipped 1, failed 0");
    }

    @Test
    void anUnexpectedRuntimeExceptionStopsTheRunWithCountsAndExitOne() throws IOException {
        Path account = SampleFixtures.writeAccount(root, ACCOUNT);
        Path campaign = SampleFixtures.writeCampaign(root, THREE_ROWS);
        MailAccount recording = SampleFixtures.recordingAccount();
        // The sleep before the second mail fails, as an interrupted wait does.
        List<Duration> waits = new ArrayList<>() {
            @Override
            public boolean add(Duration wait) {
                throw new IllegalStateException("Interrupted while waiting");
            }
        };

        SampleFixtures.Run run = SampleFixtures.run(a -> Mailer.of(recording), waits,
                args(account, campaign));

        assertThat(run.exit()).isEqualTo(1);
        assertThat(run.outLines()).containsExactly(
                "[1/3] anna@example.org accepted",
                "accepted 1, skipped 0, failed 0");
        assertThat(run.errLines()).containsExactly("Stopped: unexpected IllegalStateException: "
                + "Interrupted while waiting. The last mail may have been sent; "
                + "a rerun sends it again.");
        assertThat(SampleFixtures.journalLines(campaign.resolve("journal.jsonl"))).hasSize(1);
    }
}
