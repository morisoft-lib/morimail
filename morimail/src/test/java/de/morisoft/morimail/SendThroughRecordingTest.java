package de.morisoft.morimail;

import static org.assertj.core.api.Assertions.assertThat;

import de.morisoft.morimail.test.RecordingTransport;
import org.junit.jupiter.api.Test;

class SendThroughRecordingTest {

    @Test
    void plainMailIsRecordedWithTheDefaultSenderApplied() throws MailException {
        MailAccount account = MailAccount.builder("main")
                .transport("recording")
                .defaultFrom("newsletter@example.com")
                .build();
        Mailer mailer = Mailer.of(account);
        Mail mail = Mail.using("main")
                .to("reader@example.org")
                .subject("Hello")
                .text("Plain text body")
                .build();
        RecordingTransport transport = (RecordingTransport) account.transport();

        mailer.validate(mail);
        assertThat(transport.sent()).isEmpty();

        mailer.send(mail);

        assertThat(transport.sent()).hasSize(1);
        MailMessage sent = transport.sent().get(0);
        assertThat(sent.from().email()).isEqualTo("newsletter@example.com");
        assertThat(sent.to()).containsExactly(MailAddress.of("reader@example.org"));
        assertThat(sent.subject()).isEqualTo("Hello");
        assertThat(sent.text()).isEqualTo("Plain text body");
        assertThat(sent.html()).isNull();
        assertThat(sent.cc()).isEmpty();
        assertThat(sent.bcc()).isEmpty();
        assertThat(sent.attachments()).isEmpty();
    }
}
