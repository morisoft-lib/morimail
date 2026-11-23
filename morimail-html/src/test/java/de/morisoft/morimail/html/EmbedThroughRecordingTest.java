package de.morisoft.morimail.html;

import static org.assertj.core.api.Assertions.assertThat;

import de.morisoft.morimail.Attachment;
import de.morisoft.morimail.Mail;
import de.morisoft.morimail.MailAccount;
import de.morisoft.morimail.MailMessage;
import de.morisoft.morimail.MailTemplate;
import de.morisoft.morimail.Mailer;
import de.morisoft.morimail.test.RecordingTransport;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EmbedThroughRecordingTest {

    @TempDir
    Path tempDir;

    private static MailAccount account() {
        return MailAccount.builder("main")
                .transport("recording")
                .defaultFrom("newsletter@example.com")
                .build();
    }

    private static void sendTo(MailAccount account, HtmlImages.Result result, String address,
            String name) throws Exception {
        Mail.Builder builder = Mail.using("main")
                .to(address)
                .template(result.template(), Map.of("name", name));
        result.images().forEach(builder::attach);
        Mailer.of(account).send(builder.build());
    }

    @Test
    void templateConvertedOnceIsSentToTwoRecipientsWithItsImages() throws Exception {
        Path base = Files.createDirectories(tempDir.resolve("site"));
        Files.write(base.resolve("logo.png"), new byte[] {(byte) 0x89, 'P', 'N', 'G', '1'});
        Files.createDirectories(base.resolve("img"));
        Files.write(base.resolve("img/banner.gif"), new byte[] {'G', 'I', 'F', '8', '2'});
        MailTemplate template = new MailTemplate("Hello $$name$$",
                "<p>Hi $$name$$</p><img src=\"logo.png\" alt=\"logo\"><img src='img/banner.gif'>",
                "Hi $$name$$");

        HtmlImages.Result result = HtmlImages.embed(template, base);

        assertThat(result.template().html())
                .isEqualTo("<p>Hi $$name$$</p><img src=\"cid:logo\" alt=\"logo\"><img src='cid:banner'>");
        assertThat(result.template().subject()).isEqualTo("Hello $$name$$");
        assertThat(result.template().text()).isEqualTo("Hi $$name$$");
        assertThat(result.images()).containsExactly(
                new Attachment("logo.png", "image/png", base.resolve("logo.png").toRealPath(), "logo"),
                new Attachment("banner.gif", "image/gif", base.resolve("img/banner.gif").toRealPath(),
                        "banner"));

        MailAccount account = account();
        sendTo(account, result, "anna@example.org", "Anna");
        sendTo(account, result, "ben@example.org", "Ben");

        List<MailMessage> sent = ((RecordingTransport) account.transport()).sent();
        assertThat(sent).hasSize(2);
        assertThat(sent.get(0).html())
                .isEqualTo("<p>Hi Anna</p><img src=\"cid:logo\" alt=\"logo\"><img src='cid:banner'>");
        assertThat(sent.get(1).html())
                .isEqualTo("<p>Hi Ben</p><img src=\"cid:logo\" alt=\"logo\"><img src='cid:banner'>");
        assertThat(sent.get(0).attachments()).isEqualTo(result.images());
        assertThat(sent.get(1).attachments()).isEqualTo(result.images());
    }

    @Test
    void templateWithoutImagesIsSentUnchanged() throws Exception {
        Path base = Files.createDirectories(tempDir.resolve("site"));
        MailTemplate template = new MailTemplate("Hello $$name$$", "<p>Hi $$name$$</p>", null);

        HtmlImages.Result result = HtmlImages.embed(template, base);

        assertThat(result.template()).isSameAs(template);
        assertThat(result.images()).isEmpty();
        MailAccount account = account();
        sendTo(account, result, "anna@example.org", "Anna");
        sendTo(account, result, "ben@example.org", "Ben");
        List<MailMessage> sent = ((RecordingTransport) account.transport()).sent();
        assertThat(sent).hasSize(2);
        assertThat(sent).allSatisfy(message -> assertThat(message.attachments()).isEmpty());
        assertThat(sent.get(1).html()).isEqualTo("<p>Hi Ben</p>");
    }
}
