package de.morisoft.morimail;

import static org.assertj.core.api.Assertions.assertThat;

import de.morisoft.morimail.test.RecordingTransport;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TemplatedSendTest {

    @TempDir
    Path tempDir;

    @Test
    void templatedMailWithOneAttachmentIsRecordedRendered() throws Exception {
        Path pdf = tempDir.resolve("agenda.pdf");
        Files.write(pdf, new byte[] {'%', 'P', 'D', 'F'});
        MailTemplate template = new MailTemplate(
                "Welcome, $$firstName$$",
                "<p>Hello $$firstName$$ from $$city$$</p>",
                "Hello $$firstName$$");
        Map<String, String> row = Map.of("firstName", "Anna & <Bob>", "city", "Köln");
        MailAccount account = MailAccount.builder("main")
                .transport("recording")
                .defaultFrom("newsletter@example.com")
                .build();
        Mail mail = Mail.using("main")
                .to("reader@example.org")
                .template(template, row)
                .attach(pdf)
                .build();

        Mailer.of(account).send(mail);

        MailMessage sent = ((RecordingTransport) account.transport()).sent().get(0);
        assertThat(sent.subject()).isEqualTo("Welcome, Anna & <Bob>");
        assertThat(sent.html()).isEqualTo("<p>Hello Anna &amp; &lt;Bob&gt; from Köln</p>");
        assertThat(sent.text()).isEqualTo("Hello Anna & <Bob>");
        assertThat(sent.attachments()).hasSize(1);
        Attachment attachment = sent.attachments().get(0);
        assertThat(attachment.filename()).isEqualTo("agenda.pdf");
        assertThat(attachment.contentType()).isEqualTo("application/pdf");
        assertThat(attachment.file()).isEqualTo(pdf);
        assertThat(attachment.contentId()).isNull();
    }
}
