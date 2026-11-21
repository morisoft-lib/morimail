package de.morisoft.morimail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.morisoft.morimail.test.RecordingTransport;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MailerTest {

    @Test
    void transientTwiceThenAccepted() throws MailException {
        MailAccount account = MailAccount.builder("main")
                .transport("recording")
                .defaultFrom("newsletter@example.com")
                .build();
        RecordingTransport recording = (RecordingTransport) account.transport();
        Mailer mailer = Mailer.of(account);
        Mail mail = Mail.using("main")
                .to("reader@example.org")
                .subject("Hello")
                .text("Body")
                .build();
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
}
