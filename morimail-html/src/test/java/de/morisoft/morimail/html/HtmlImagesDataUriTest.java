package de.morisoft.morimail.html;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import de.morisoft.morimail.Mail;
import de.morisoft.morimail.MailAccount;
import de.morisoft.morimail.MailMessage;
import de.morisoft.morimail.MailTemplate;
import de.morisoft.morimail.Mailer;
import de.morisoft.morimail.test.RecordingTransport;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The storable data: form: toDataUris, the decoding of data: sources in embed, and their edges. */
@WireMockTest
class HtmlImagesDataUriTest {

    private static final byte[] A = {(byte) 0x89, 'P', 'N', 'G', '1', '2', '3'};
    private static final byte[] B = {'G', 'I', 'F', '8', '9', 'a', '4'};

    @TempDir
    Path tempDir;

    private static String b64(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    private static MailAccount account() {
        return MailAccount.builder("main")
                .transport("recording")
                .defaultFrom("newsletter@example.com")
                .build();
    }

    private static void sendTo(MailAccount account, HtmlImages.Result result, String address, String name)
            throws Exception {
        Mail.Builder builder = Mail.using("main")
                .to(address)
                .template(result.template(), Map.of("name", name));
        result.images().forEach(builder::attach);
        Mailer.of(account).send(builder.build());
    }

    @Test
    void storedHtmlRoundTripsToTwoMailsWithTheOriginalBytes() throws Exception {
        Path base = Files.createDirectories(tempDir.resolve("site"));
        Files.write(base.resolve("logo.png"), A);
        Files.createDirectories(base.resolve("img"));
        Files.write(base.resolve("img/banner.gif"), B);
        Path work = Files.createDirectories(tempDir.resolve("work"));
        MailTemplate template = new MailTemplate("Hello $$name$$",
                "<p>Hi $$name$$</p><img src=\"logo.png\"><img src='img/banner.gif'>", "Hi $$name$$");

        MailTemplate stored = HtmlImages.toDataUris(template, base);

        assertThat(stored.html()).isEqualTo("<p>Hi $$name$$</p><img src=\"data:image/png;base64,"
                + b64(A) + "\"><img src='data:image/gif;base64," + b64(B) + "'>");
        assertThat(stored.subject()).isEqualTo("Hello $$name$$");
        assertThat(stored.text()).isEqualTo("Hi $$name$$");

        // The stored form needs no image files: the base directory below never exists (D-07).
        String storedHtml = stored.html();
        HtmlImages.Result result = HtmlImages.embed(
                new MailTemplate("Hello $$name$$", storedHtml, "Hi $$name$$"),
                tempDir.resolve("no-images"), work);

        assertThat(result.template().html())
                .isEqualTo("<p>Hi $$name$$</p><img src=\"cid:image\"><img src='cid:image-2'>");
        assertThat(result.images()).hasSize(2);
        assertThat(result.images().get(0).filename()).isEqualTo("image.png");
        assertThat(result.images().get(0).contentType()).isEqualTo("image/png");
        assertThat(result.images().get(0).contentId()).isEqualTo("image");
        assertThat(result.images().get(0).file().getParent()).isEqualTo(work);
        assertThat(Files.readAllBytes(result.images().get(0).file())).isEqualTo(A);
        assertThat(result.images().get(1).filename()).isEqualTo("image-2.gif");
        assertThat(result.images().get(1).contentType()).isEqualTo("image/gif");
        assertThat(result.images().get(1).contentId()).isEqualTo("image-2");
        assertThat(Files.readAllBytes(result.images().get(1).file())).isEqualTo(B);

        MailAccount account = account();
        sendTo(account, result, "anna@example.org", "Anna");
        sendTo(account, result, "ben@example.org", "Ben");
        List<MailMessage> sent = ((RecordingTransport) account.transport()).sent();
        assertThat(sent).hasSize(2);
        assertThat(sent.get(0).html())
                .isEqualTo("<p>Hi Anna</p><img src=\"cid:image\"><img src='cid:image-2'>");
        assertThat(sent.get(1).html())
                .isEqualTo("<p>Hi Ben</p><img src=\"cid:image\"><img src='cid:image-2'>");
        for (MailMessage message : sent) {
            assertThat(message.attachments()).hasSize(2);
            assertThat(Files.readAllBytes(message.attachments().get(0).file())).isEqualTo(A);
            assertThat(Files.readAllBytes(message.attachments().get(1).file())).isEqualTo(B);
        }
    }
}
