package de.morisoft.morimail.html;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.findAll;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
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

/** Remote images: every test talks to WireMock on localhost or to a closed local port. */
@WireMockTest
class HtmlImagesDownloadTest {

    @TempDir
    Path tempDir;

    private static void stubImage(String path, byte[] body) {
        stubFor(get(urlPathEqualTo(path)).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "image/png")
                .withBody(body)));
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
    void downloadedImageIsSentInlineFromTheWorkDirectory(WireMockRuntimeInfo wm) throws Exception {
        byte[] logo = {(byte) 0x89, 'P', 'N', 'G', '7'};
        stubImage("/img/logo.png", logo);
        Path base = Files.createDirectories(tempDir.resolve("site"));
        Files.write(base.resolve("banner.gif"), new byte[] {'G', 'I', 'F', '8', '2'});
        Path work = Files.createDirectories(tempDir.resolve("work"));
        String url = wm.getHttpBaseUrl() + "/img/logo.png";
        MailTemplate template = new MailTemplate("Hello $$name$$",
                "<p>Hi $$name$$</p><img src=\"" + url + "\"><img src=\"banner.gif\">", null);

        HtmlImages.Result result = HtmlImages.embed(template, base, work);

        assertThat(result.template().html())
                .isEqualTo("<p>Hi $$name$$</p><img src=\"cid:logo\"><img src=\"cid:banner\">");
        assertThat(result.images()).hasSize(2);
        Attachment logoImage = result.images().get(0);
        assertThat(logoImage.filename()).isEqualTo("logo.png");
        assertThat(logoImage.contentType()).isEqualTo("image/png");
        assertThat(logoImage.contentId()).isEqualTo("logo");
        assertThat(logoImage.file().getParent()).isEqualTo(work);
        assertThat(logoImage.file().getFileName().toString()).startsWith("morimail-");
        assertThat(Files.readAllBytes(logoImage.file())).isEqualTo(logo);
        Attachment banner = result.images().get(1);
        assertThat(banner.filename()).isEqualTo("banner.gif");
        assertThat(banner.contentId()).isEqualTo("banner");

        MailAccount account = account();
        sendTo(account, result, "anna@example.org", "Anna");
        sendTo(account, result, "ben@example.org", "Ben");
        List<MailMessage> sent = ((RecordingTransport) account.transport()).sent();
        assertThat(sent).hasSize(2);
        assertThat(sent.get(0).html())
                .isEqualTo("<p>Hi Anna</p><img src=\"cid:logo\"><img src=\"cid:banner\">");
        assertThat(sent.get(1).html())
                .isEqualTo("<p>Hi Ben</p><img src=\"cid:logo\"><img src=\"cid:banner\">");
        assertThat(sent.get(0).attachments()).isEqualTo(result.images());
        assertThat(sent.get(1).attachments()).isEqualTo(result.images());
        assertThat(findAll(getRequestedFor(urlPathEqualTo("/img/logo.png")))).hasSize(1);
    }

    @Test
    void withoutAWorkDirectoryNothingIsDownloaded(WireMockRuntimeInfo wm) throws Exception {
        Path base = Files.createDirectories(tempDir.resolve("site"));
        Path work = Files.createDirectories(tempDir.resolve("work"));
        String url = wm.getHttpBaseUrl() + "/img/logo.png";

        MailTemplate plain = new MailTemplate("S",
                "<img src=\"" + url + "\"><img src=\"https://example.com/a.png\">", null);
        assertThat(HtmlImages.embed(plain, base).template()).isSameAs(plain);
        assertThat(findAll(anyRequestedFor(anyUrl()))).isEmpty();

        MailTemplate unchanged = new MailTemplate("S",
                "<img src=\"ftp://example.com/a.png\"><img src=\"cid:a\">"
                        + "<img src=\"" + wm.getHttpBaseUrl() + "/img/$$name$$.png\">", null);
        HtmlImages.Result result = HtmlImages.embed(unchanged, base, work);
        assertThat(result.template()).isSameAs(unchanged);
        assertThat(result.images()).isEmpty();
        assertThat(findAll(anyRequestedFor(anyUrl()))).isEmpty();
        try (var files = Files.list(work)) {
            assertThat(files).isEmpty();
        }
    }
}
