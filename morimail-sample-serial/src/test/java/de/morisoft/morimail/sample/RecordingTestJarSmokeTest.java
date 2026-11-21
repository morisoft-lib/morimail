package de.morisoft.morimail.sample;

import static org.assertj.core.api.Assertions.assertThat;

import de.morisoft.morimail.Mail;
import de.morisoft.morimail.MailAccount;
import de.morisoft.morimail.MailException;
import de.morisoft.morimail.Mailer;
import de.morisoft.morimail.test.RecordingTransport;
import org.junit.jupiter.api.Test;

/** Proves the library test-jar and its services entry reach the sample module. */
class RecordingTestJarSmokeTest {

    @Test
    void recordingTransportComesFromTheTestJar() throws MailException {
        MailAccount account = MailAccount.builder("main")
                .transport("recording")
                .defaultFrom("newsletter@example.com")
                .build();
        Mail mail = Mail.using("main")
                .to("reader@example.org")
                .subject("Hello")
                .text("Plain text body")
                .build();

        Mailer.of(account).send(mail);

        assertThat(account.transport()).isInstanceOf(RecordingTransport.class);
        assertThat(((RecordingTransport) account.transport()).sent()).hasSize(1);
    }
}
