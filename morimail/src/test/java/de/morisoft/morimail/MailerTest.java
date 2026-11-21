package de.morisoft.morimail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.morisoft.morimail.test.RecordingTransport;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MailerTest {

    private static MailAccount account(String id, String defaultFrom) {
        MailAccount.Builder builder = MailAccount.builder(id).transport("recording");
        if (defaultFrom != null) {
            builder.defaultFrom(defaultFrom);
        }
        return builder.build();
    }

    private static Mail mail(String accountId) throws InvalidMailException {
        return Mail.using(accountId)
                .to("reader@example.org")
                .subject("Hello")
                .text("Body")
                .build();
    }

    @Test
    void transientTwiceThenAccepted() throws MailException {
        MailAccount account = account("main", "newsletter@example.com");
        RecordingTransport recording = (RecordingTransport) account.transport();
        Mailer mailer = Mailer.of(account);
        Mail mail = mail("main");
        recording.failNext(new TransientMailException("busy", Duration.ofSeconds(7)),
                new TransientMailException("busy again"));

        assertThatThrownBy(() -> mailer.send(mail))
                .isInstanceOfSatisfying(TransientMailException.class, e ->
                        assertThat(e.retryAfter()).isEqualTo(Optional.of(Duration.ofSeconds(7))));
        assertThatThrownBy(() -> mailer.send(mail))
                .isInstanceOfSatisfying(TransientMailException.class, e ->
                        assertThat(e.retryAfter()).isEmpty());
        mailer.send(mail);

        assertThat(recording.attempts()).isEqualTo(3);
        assertThat(recording.sent()).hasSize(1);
    }

    @Test
    void appliesDefaultFromWhenMailHasNoFrom() throws MailException {
        MailAccount account = account("main", "newsletter@example.com");
        RecordingTransport recording = (RecordingTransport) account.transport();

        Mailer.of(account).send(mail("main"));

        assertThat(recording.sent()).hasSize(1);
        assertThat(recording.sent().get(0).from().email()).isEqualTo("newsletter@example.com");
    }

    @Test
    void explicitFromWinsOverDefaultFrom() throws MailException {
        MailAccount account = account("main", "newsletter@example.com");
        RecordingTransport recording = (RecordingTransport) account.transport();
        Mail mail = Mail.using("main")
                .from("boss@example.com")
                .to("reader@example.org")
                .subject("Hello")
                .text("Body")
                .build();

        Mailer.of(account).send(mail);

        assertThat(recording.sent().get(0).from().email()).isEqualTo("boss@example.com");
    }

    @Test
    void unknownAccountRaisesPermanentMailExceptionNamingId() throws MailException {
        MailAccount account = account("main", "newsletter@example.com");
        RecordingTransport recording = (RecordingTransport) account.transport();
        Mailer mailer = Mailer.of(account);
        Mail mail = mail("elsewhere");

        assertThatThrownBy(() -> mailer.send(mail))
                .isExactlyInstanceOf(PermanentMailException.class)
                .hasMessageContaining("elsewhere");
        assertThatThrownBy(() -> mailer.validate(mail))
                .isExactlyInstanceOf(PermanentMailException.class)
                .hasMessageContaining("elsewhere");
        assertThat(recording.attempts()).isZero();
    }

    @Test
    void missingSenderRaisesInvalidMailExceptionNamingAccount() throws MailException {
        MailAccount account = account("main", null);
        RecordingTransport recording = (RecordingTransport) account.transport();
        Mailer mailer = Mailer.of(account);
        Mail mail = mail("main");

        assertThatThrownBy(() -> mailer.send(mail))
                .isExactlyInstanceOf(InvalidMailException.class)
                .hasMessageContaining("main");
        assertThatThrownBy(() -> mailer.validate(mail))
                .isExactlyInstanceOf(InvalidMailException.class)
                .hasMessageContaining("main");
        assertThat(recording.attempts()).isZero();
    }

    @Test
    void duplicateAccountIdsAreRejected() {
        MailAccount first = account("main", "a@example.com");
        MailAccount second = account("main", "b@example.com");

        assertThatThrownBy(() -> Mailer.of(first, second))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("main");
    }

    @Test
    void scriptedPermanentFailureSurfacesWithItsType() throws MailException {
        assertScriptedFailureSurfaces(new PermanentMailException("rejected for good"));
    }

    @Test
    void scriptedInvalidMailFailureSurfacesWithItsType() throws MailException {
        assertScriptedFailureSurfaces(new InvalidMailException("bad mail"));
    }

    @Test
    void scriptedSenderRejectedFailureSurfacesWithItsType() throws MailException {
        assertScriptedFailureSurfaces(new SenderRejectedException("sender not allowed"));
    }

    @Test
    void scriptedAuthRequiredFailureSurfacesWithItsType() throws MailException {
        assertScriptedFailureSurfaces(new AuthRequiredException("sign in again"));
    }

    private static void assertScriptedFailureSurfaces(MailException failure)
            throws MailException {
        MailAccount account = account("main", "newsletter@example.com");
        RecordingTransport recording = (RecordingTransport) account.transport();
        Mailer mailer = Mailer.of(account);
        Mail mail = mail("main");
        mailer.send(mail);
        int sentBefore = recording.sent().size();
        int attemptsBefore = recording.attempts();
        recording.failNext(failure);

        assertThatThrownBy(() -> mailer.send(mail))
                .isExactlyInstanceOf(failure.getClass())
                .hasMessage(failure.getMessage());

        assertThat(recording.sent()).hasSize(sentBefore);
        assertThat(recording.attempts()).isEqualTo(attemptsBefore + 1);
    }

    @Test
    void sameMailSentTwiceIsRecordedTwice() throws MailException {
        MailAccount account = account("main", "newsletter@example.com");
        RecordingTransport recording = (RecordingTransport) account.transport();
        Mailer mailer = Mailer.of(account);
        Mail mail = mail("main");

        mailer.send(mail);
        mailer.send(mail);

        assertThat(recording.sent()).hasSize(2);
        assertThat(recording.attempts()).isEqualTo(2);
    }
}
