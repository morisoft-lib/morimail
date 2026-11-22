package de.morisoft.morimail;

import static org.assertj.core.api.Assertions.assertThat;

import de.morisoft.morimail.test.RecordingTransport;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SendThroughRecordingTest {

    @TempDir
    Path tempDir;

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

    @Test
    void inlineImageIsRecordedWithItsContentIdApartFromTheRegularAttachment()
            throws IOException, MailException {
        MailAccount account = MailAccount.builder("main")
                .transport("recording")
                .defaultFrom("newsletter@example.com")
                .build();
        Path logo = tempDir.resolve("logo.png");
        Path agenda = tempDir.resolve("agenda.pdf");
        Files.write(logo, new byte[] {(byte) 0x89, 'P', 'N', 'G'});
        Files.write(agenda, new byte[] {'%', 'P', 'D', 'F'});
        Mail mail = Mail.using("main")
                .to("reader@example.org")
                .template(new MailTemplate("Hello $$name$$",
                        "<p>Hi $$name$$</p><img src=\"cid:logo\">", "Hi $$name$$"),
                        Map.of("name", "Anna"))
                .attach(agenda)
                .inline(logo, "logo")
                .build();

        Mailer.of(account).send(mail);

        RecordingTransport transport = (RecordingTransport) account.transport();
        assertThat(transport.sent()).hasSize(1);
        MailMessage sent = transport.sent().get(0);
        assertThat(sent.html()).isEqualTo("<p>Hi Anna</p><img src=\"cid:logo\">");
        List<Attachment> attachments = sent.attachments();
        assertThat(attachments).hasSize(2);
        assertThat(attachments).filteredOn(a -> a.contentId() != null).hasSize(1);
        Attachment inline = attachments.stream().filter(a -> a.contentId() != null).findFirst().orElseThrow();
        assertThat(inline.contentId()).isEqualTo("logo");
        assertThat(inline.filename()).isEqualTo("logo.png");
        assertThat(inline.contentType()).isEqualTo("image/png");
        Attachment regular = attachments.stream().filter(a -> a.contentId() == null).findFirst().orElseThrow();
        assertThat(regular.filename()).isEqualTo("agenda.pdf");
    }
}
