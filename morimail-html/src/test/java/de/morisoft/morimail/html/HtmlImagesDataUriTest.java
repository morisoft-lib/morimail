package de.morisoft.morimail.html;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.findAll;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import de.morisoft.morimail.Attachment;
import de.morisoft.morimail.InvalidMailException;
import de.morisoft.morimail.Mail;
import de.morisoft.morimail.MailAccount;
import de.morisoft.morimail.MailMessage;
import de.morisoft.morimail.MailTemplate;
import de.morisoft.morimail.Mailer;
import de.morisoft.morimail.test.RecordingTransport;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The storable data: form: toDataUris, the decoding of data: sources in embed, and their edges. */
@WireMockTest
class HtmlImagesDataUriTest {

    private static final byte[] A = {(byte) 0x89, 'P', 'N', 'G', '1', '2', '3'};
    private static final byte[] B = {'G', 'I', 'F', '8', '9', 'a', '4'};

    @TempDir
    Path tempDir;

    private Path base;
    private Path work;

    @BeforeEach
    void directories() throws Exception {
        base = Files.createDirectories(tempDir.resolve("site"));
        work = Files.createDirectories(tempDir.resolve("work"));
    }

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

    private static String data(String type, byte[] bytes) {
        return "data:" + type + ";base64," + b64(bytes);
    }

    /** A template whose HTML holds one img element per source, each in double quotes. */
    private static MailTemplate htmlOf(String... sources) {
        StringBuilder html = new StringBuilder();
        for (String source : sources) {
            html.append("<img src=\"").append(source).append("\">");
        }
        return new MailTemplate("S", html.toString(), null);
    }

    private static List<String> names(Path dir) throws Exception {
        try (Stream<Path> files = Files.list(dir)) {
            return files.map(file -> file.getFileName().toString()).sorted().toList();
        }
    }

    /** Embeds one data: source and asserts the failure message, which never holds the payload. */
    private void assertDataFails(String src, String reason, String payloadText) {
        Throwable thrown = catchThrowable(() -> HtmlImages.embed(htmlOf(src), base, work));
        assertThat(thrown).isInstanceOf(InvalidMailException.class)
                .hasMessage("data: image source #1: " + reason);
        assertThat(thrown.getMessage()).doesNotContain(payloadText);
    }

    @Test
    void storedHtmlRoundTripsToTwoMailsWithTheOriginalBytes() throws Exception {
        Files.write(base.resolve("logo.png"), A);
        Files.createDirectories(base.resolve("img"));
        Files.write(base.resolve("img/banner.gif"), B);
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

    @Test
    void dataSourcesStayUnlessAWorkDirectoryIsGiven() throws Exception {
        MailTemplate template = htmlOf("data:image/png;base64,iVBORw0K");

        assertThat(HtmlImages.embed(template, base).template()).isSameAs(template);
        assertThat(HtmlImages.toDataUris(template, base)).isSameAs(template);
        assertThat(HtmlImages.toDataUris(template, base, work)).isSameAs(template);
        assertThat(names(work)).isEmpty();
    }

    @Test
    void onlyBase64ImageDataIsDecoded() throws Exception {
        assertDataFails("data:text/plain;base64,QQ==", "is not an image type", "QQ==");
        assertDataFails("data:image/png,abc", "is not base64", "abc");
        assertDataFails("data:image/png;base64", "is not base64", "image/png;base64,");
        assertDataFails("data:image/png;base64,@@@", "payload is not valid base64", "@@@");

        // The position counts every data: source of the template, a valid one before it included.
        MailTemplate second = htmlOf("data:image/png;base64,iVBORw0K", "data:image/png;base64,@@@");
        Throwable thrown = catchThrowable(() -> HtmlImages.embed(second, base, work));
        assertThat(thrown).isInstanceOf(InvalidMailException.class)
                .hasMessage("data: image source #2: payload is not valid base64");
        assertThat(thrown.getMessage()).doesNotContain("@@@");
    }

    @Test
    void wrappedPayloadIsAccepted() throws Exception {
        byte[] bytes = new byte[200];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) (i * 7 + 1);
        }
        String plain = b64(bytes);
        StringBuilder wrapped = new StringBuilder();
        for (int i = 0; i < plain.length(); i += 76) {
            wrapped.append(plain, i, Math.min(plain.length(), i + 76)).append(i == 0 ? " \t" : "").append("\r\n");
        }

        HtmlImages.Result result = HtmlImages.embed(htmlOf("data:image/png;base64," + wrapped), base, work);

        assertThat(result.images()).hasSize(1);
        assertThat(Files.readAllBytes(result.images().get(0).file())).isEqualTo(bytes);
    }

    @Test
    void parametersAreIgnoredAndTheTypeIsKept() throws Exception {
        HtmlImages.Result withParameter = HtmlImages.embed(
                htmlOf("data:image/png;name=x.png;base64,iVBORw0K"), base, work);
        HtmlImages.Result upperCase = HtmlImages.embed(htmlOf("DATA:IMAGE/PNG;BASE64,iVBORw0K"), base, work);

        assertThat(withParameter.images().get(0).contentType()).isEqualTo("image/png");
        assertThat(upperCase.images().get(0).contentType()).isEqualTo("IMAGE/PNG");
        assertThat(upperCase.template().html()).isEqualTo("<img src=\"cid:image\">");
    }

    @Test
    void decodedCapIsThreeMebibytes() throws Exception {
        String justUnder = b64(new byte[3_145_727]);
        assertThat(justUnder).hasSize(4_194_304).endsWith("=").doesNotEndWith("==");

        HtmlImages.Result result = HtmlImages.embed(htmlOf("data:image/png;base64," + justUnder), base, work);

        assertThat(Files.size(result.images().get(0).file())).isEqualTo(3_145_727);

        // The same length of 4,194,304 characters, no padding: passes the length check, refused after decoding.
        String exactly = b64(new byte[3_145_728]);
        assertThat(exactly).hasSize(4_194_304);
        assertDataFails("data:image/png;base64," + exactly, "is 3 MiB or larger", "AAAAAAAA");

        // The strict decoder rejects 4,194,305 characters as not valid base64, so this message
        // proves that the length check runs before decoding.
        assertDataFails("data:image/png;base64," + "A".repeat(4_194_305), "is 3 MiB or larger", "AAAAAAAA");
    }

    @Test
    void identicalDataSourcesGiveOneImage() throws Exception {
        String x = data("image/png", A);
        String y = data("image/png", B);

        HtmlImages.Result same = HtmlImages.embed(htmlOf(x, x), base, work);
        assertThat(same.template().html()).isEqualTo("<img src=\"cid:image\"><img src=\"cid:image\">");
        assertThat(same.images()).hasSize(1);

        HtmlImages.Result different = HtmlImages.embed(htmlOf(x, y), base, work);
        assertThat(different.template().html()).isEqualTo("<img src=\"cid:image\"><img src=\"cid:image-2\">");
        assertThat(different.images()).extracting(Attachment::contentId).containsExactly("image", "image-2");

        Files.write(base.resolve("image.png"), A);
        HtmlImages.Result mixed = HtmlImages.embed(htmlOf(x, "image.png"), base, work);
        assertThat(mixed.template().html()).isEqualTo("<img src=\"cid:image\"><img src=\"cid:image-2\">");
        assertThat(mixed.images()).extracting(Attachment::contentId).containsExactly("image", "image-2");
    }

    @Test
    void dataImageNamesComeFromTheType() throws Exception {
        HtmlImages.Result result = HtmlImages.embed(htmlOf(
                data("image/png", A), data("image/jpeg", A), data("image/svg+xml", A)), base, work);

        assertThat(result.images()).extracting(Attachment::filename)
                .containsExactly("image.png", "image-2.jpeg", "image-3.svg-xml");
        assertThat(result.images()).extracting(Attachment::contentType)
                .containsExactly("image/png", "image/jpeg", "image/svg+xml");
    }

    @Test
    void toDataUrisConvertsLocalImagesOnly(WireMockRuntimeInfo wm) throws Exception {
        Files.write(base.resolve("logo.png"), A);
        Files.createDirectories(base.resolve("img"));
        Files.write(base.resolve("img/r.png"), B);
        String remote = wm.getHttpBaseUrl() + "/r.png";
        MailTemplate template = new MailTemplate("Hello $$name$$",
                "<img src=\"logo.png\"><img src='logo.png'><img src=\"" + remote + "\">"
                        + "<img src=\"cid:a\"><img src=\"data:image/png;base64,iVBORw0K\">"
                        + "<img src=\"$$logo$$\"><img src=\"ftp://example.com/a.png\">",
                "Hi");

        MailTemplate stored = HtmlImages.toDataUris(template, base);

        String a = data("image/png", A);
        assertThat(stored.html()).isEqualTo("<img src=\"" + a + "\"><img src='" + a + "'><img src=\"" + remote
                + "\"><img src=\"cid:a\"><img src=\"data:image/png;base64,iVBORw0K\">"
                + "<img src=\"$$logo$$\"><img src=\"ftp://example.com/a.png\">");
        assertThat(stored.subject()).isEqualTo("Hello $$name$$");
        assertThat(stored.text()).isEqualTo("Hi");
        assertThat(findAll(anyRequestedFor(anyUrl()))).isEmpty();
    }

    @Test
    void toDataUrisFailsLikeEmbedForLocalSources() throws Exception {
        Files.write(tempDir.resolve("secret.png"), A);
        Files.write(base.resolve("notes.txt"), new byte[] {'h', 'i'});

        for (String src : List.of("../secret.png", "notes.txt", "missing.png")) {
            MailTemplate template = htmlOf(src);
            Throwable fromEmbed = catchThrowable(() -> HtmlImages.embed(template, base));
            Throwable fromStore = catchThrowable(() -> HtmlImages.toDataUris(template, base));

            assertThat(fromEmbed).isInstanceOf(InvalidMailException.class);
            assertThat(fromStore).isInstanceOf(InvalidMailException.class);
            assertThat(fromStore.getMessage()).isEqualTo(fromEmbed.getMessage()).startsWith("img src \"" + src + "\": ");
        }
    }

    @Test
    void toDataUrisKeepsTheTemplateWhenNothingIsConverted() throws Exception {
        MailTemplate noHtml = new MailTemplate("S", null, "text");
        MailTemplate unchanged = htmlOf("cid:a", "ftp://example.com/a.png", "$$logo$$",
                "data:image/png;base64,iVBORw0K");

        assertThat(HtmlImages.toDataUris(noHtml, base)).isSameAs(noHtml);
        assertThat(HtmlImages.toDataUris(noHtml, base, work)).isSameAs(noHtml);
        assertThat(HtmlImages.toDataUris(unchanged, base)).isSameAs(unchanged);
        assertThat(HtmlImages.toDataUris(unchanged, base, work)).isSameAs(unchanged);
    }

    @Test
    void toDataUrisDownloadsWithAWorkDirectoryAndWritesNothingThere(WireMockRuntimeInfo wm) throws Exception {
        byte[] c = {(byte) 0x89, 'P', 'N', 'G', 'C'};
        stubFor(get(urlPathEqualTo("/img/r.png")).willReturn(aResponse()
                .withStatus(200).withHeader("Content-Type", "image/png").withBody(c)));
        stubFor(get(urlPathEqualTo("/missing.png")).willReturn(aResponse().withStatus(404)));
        Files.writeString(work.resolve("keep.txt"), "keep");
        MailTemplate template = htmlOf(wm.getHttpBaseUrl() + "/img/r.png");

        MailTemplate stored = HtmlImages.toDataUris(template, base, work);

        assertThat(stored.html()).isEqualTo("<img src=\"" + data("image/png", c) + "\">");
        assertThat(names(work)).containsExactly("keep.txt");
        assertThat(Files.readString(work.resolve("keep.txt"))).isEqualTo("keep");

        Path second = Files.createDirectories(tempDir.resolve("second"));
        HtmlImages.Result result = HtmlImages.embed(stored, base, second);
        assertThat(result.images()).hasSize(1);
        assertThat(Files.readAllBytes(result.images().get(0).file())).isEqualTo(c);

        String missing = wm.getHttpBaseUrl() + "/missing.png";
        assertThatThrownBy(() -> HtmlImages.toDataUris(htmlOf(missing), base, work))
                .isInstanceOf(InvalidMailException.class)
                .hasMessage("img src \"" + missing + "\": download failed with HTTP 404");
        assertThat(names(work)).containsExactly("keep.txt");
    }

    @Test
    void toDataUrisCapsLocalFiles() throws Exception {
        byte[] ok = new byte[3_145_727];
        Files.write(base.resolve("ok.png"), ok);
        Files.write(base.resolve("big.png"), new byte[3_145_728]);

        MailTemplate stored = HtmlImages.toDataUris(htmlOf("ok.png"), base);

        assertThat(stored.html()).isEqualTo("<img src=\"" + data("image/png", ok) + "\">");
        assertThatThrownBy(() -> HtmlImages.toDataUris(htmlOf("big.png"), base))
                .isInstanceOf(InvalidMailException.class)
                .hasMessage("img src \"big.png\": image is 3 MiB or larger");
    }

    @Test
    void concurrentRoundTripsUseSeparateFiles() throws Exception {
        Files.write(base.resolve("logo.png"), A);
        Files.write(base.resolve("banner.gif"), B);
        MailTemplate stored = HtmlImages.toDataUris(htmlOf("logo.png", "banner.gif"), base);
        Path none = tempDir.resolve("no-images");
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Future<HtmlImages.Result>> futures = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return HtmlImages.embed(stored, none, work);
                }));
            }
            start.countDown();
            Set<Path> files = new HashSet<>();
            for (Future<HtmlImages.Result> future : futures) {
                HtmlImages.Result result = future.get();
                assertThat(result.template().html()).isEqualTo("<img src=\"cid:image\"><img src=\"cid:image-2\">");
                assertThat(Files.readAllBytes(result.images().get(0).file())).isEqualTo(A);
                assertThat(Files.readAllBytes(result.images().get(1).file())).isEqualTo(B);
                files.add(result.images().get(0).file());
                files.add(result.images().get(1).file());
            }
            assertThat(files).hasSize(8);
            assertThat(names(work)).hasSize(8);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void failedDecodeKeepsEarlierFiles() throws Exception {
        Files.writeString(work.resolve("keep.txt"), "keep");
        MailTemplate stored = htmlOf(data("image/png", A), "data:image/png;base64,@@@");

        assertThatThrownBy(() -> HtmlImages.embed(stored, base, work))
                .isInstanceOf(InvalidMailException.class)
                .hasMessage("data: image source #2: payload is not valid base64");

        List<String> left = names(work);
        assertThat(left).hasSize(2).contains("keep.txt");
        assertThat(Files.readString(work.resolve("keep.txt"))).isEqualTo("keep");
        String written = left.stream().filter(name -> !name.equals("keep.txt")).findFirst().orElseThrow();
        assertThat(written).startsWith("morimail-");
        assertThat(Files.readAllBytes(work.resolve(written))).isEqualTo(A);
    }

    @Test
    void nullArgumentsThrowNullPointerException() {
        MailTemplate template = htmlOf("logo.png");

        assertThatNullPointerException().isThrownBy(() -> HtmlImages.toDataUris(null, base));
        assertThatNullPointerException().isThrownBy(() -> HtmlImages.toDataUris(template, null));
        assertThatNullPointerException().isThrownBy(() -> HtmlImages.toDataUris(null, base, work));
        assertThatNullPointerException().isThrownBy(() -> HtmlImages.toDataUris(template, null, work));
        assertThatNullPointerException().isThrownBy(() -> HtmlImages.toDataUris(template, base, null));
    }
}
