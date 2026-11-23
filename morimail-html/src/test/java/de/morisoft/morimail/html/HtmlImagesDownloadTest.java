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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import de.morisoft.morimail.Attachment;
import de.morisoft.morimail.InvalidMailException;
import de.morisoft.morimail.Mail;
import de.morisoft.morimail.MailAccount;
import de.morisoft.morimail.MailMessage;
import de.morisoft.morimail.MailTemplate;
import de.morisoft.morimail.Mailer;
import de.morisoft.morimail.TransientMailException;
import de.morisoft.morimail.test.RecordingTransport;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Remote images: every test talks to WireMock on localhost or to a closed local port. */
@WireMockTest
class HtmlImagesDownloadTest {

    @TempDir
    Path tempDir;

    private Path base;
    private Path work;

    @BeforeEach
    void directories() throws Exception {
        base = Files.createDirectories(tempDir.resolve("site"));
        work = Files.createDirectories(tempDir.resolve("work"));
    }

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

    /** A template whose HTML holds one img element per source, each in double quotes. */
    private static MailTemplate htmlOf(String... sources) {
        StringBuilder html = new StringBuilder();
        for (String source : sources) {
            html.append("<img src=\"").append(source).append("\">");
        }
        return new MailTemplate("S", html.toString(), null);
    }

    private static String message(String src, String reason) {
        return "img src \"" + src + "\": " + reason;
    }

    private static byte[] filled(int length, char fill) {
        byte[] bytes = new byte[length];
        Arrays.fill(bytes, (byte) fill);
        return bytes;
    }

    private static int requests(String path) {
        return findAll(getRequestedFor(urlPathEqualTo(path))).size();
    }

    @Test
    void downloadedImageIsSentInlineFromTheWorkDirectory(WireMockRuntimeInfo wm) throws Exception {
        byte[] logo = {(byte) 0x89, 'P', 'N', 'G', '7'};
        stubImage("/img/logo.png", logo);
        Files.write(base.resolve("banner.gif"), new byte[] {'G', 'I', 'F', '8', '2'});
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
        assertThat(requests("/img/logo.png")).isEqualTo(1);
    }

    @Test
    void withoutAWorkDirectoryNothingIsDownloaded(WireMockRuntimeInfo wm) throws Exception {
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
        try (Stream<Path> files = Files.list(work)) {
            assertThat(files).isEmpty();
        }
    }

    static Stream<Arguments> statuses() {
        return Stream.of(
                Arguments.of(404, InvalidMailException.class),
                Arguments.of(410, InvalidMailException.class),
                Arguments.of(400, InvalidMailException.class),
                Arguments.of(403, InvalidMailException.class),
                Arguments.of(204, InvalidMailException.class),
                Arguments.of(304, InvalidMailException.class),
                Arguments.of(408, InvalidMailException.class),
                Arguments.of(500, TransientMailException.class),
                Arguments.of(502, TransientMailException.class),
                Arguments.of(503, TransientMailException.class),
                Arguments.of(429, TransientMailException.class));
    }

    @ParameterizedTest
    @MethodSource("statuses")
    void statusDecidesTheCategory(int status, Class<? extends Exception> expected, WireMockRuntimeInfo wm) {
        stubFor(get(urlPathEqualTo("/img/s.png")).willReturn(aResponse()
                .withStatus(status)
                .withHeader("Content-Type", "image/png")
                .withBody("secret-body")));
        String src = wm.getHttpBaseUrl() + "/img/s.png";

        Throwable thrown = catchThrowable(() -> HtmlImages.embed(htmlOf(src), base, work));

        assertThat(thrown).isExactlyInstanceOf(expected)
                .hasMessage(message(src, "download failed with HTTP " + status));
        assertThat(thrown.getMessage()).doesNotContain("secret-body");
    }

    @Test
    void redirectIsFollowed(WireMockRuntimeInfo wm) throws Exception {
        byte[] real = {'r', 'e', 'a', 'l', '1'};
        stubFor(get(urlPathEqualTo("/img/old.png")).willReturn(aResponse()
                .withStatus(302)
                .withHeader("Location", wm.getHttpBaseUrl() + "/img/real.png")));
        stubImage("/img/real.png", real);

        HtmlImages.Result result = HtmlImages.embed(htmlOf(wm.getHttpBaseUrl() + "/img/old.png"), base, work);

        assertThat(result.images()).hasSize(1);
        Attachment image = result.images().get(0);
        assertThat(image.filename()).isEqualTo("old.png");
        assertThat(image.contentId()).isEqualTo("old");
        assertThat(Files.readAllBytes(image.file())).isEqualTo(real);
        assertThat(requests("/img/old.png")).isEqualTo(1);
        assertThat(requests("/img/real.png")).isEqualTo(1);
    }

    @Test
    void contentTypeMustBeAnImage(WireMockRuntimeInfo wm) throws Exception {
        byte[] bytes = {1, 2, 3};
        stubFor(get(urlPathEqualTo("/p.png")).willReturn(aResponse()
                .withHeader("Content-Type", "image/png; charset=binary").withBody(bytes)));
        HtmlImages.Result parameters = HtmlImages.embed(htmlOf(wm.getHttpBaseUrl() + "/p.png"), base, work);
        assertThat(parameters.images().get(0).contentType()).isEqualTo("image/png");

        stubFor(get(urlPathEqualTo("/html")).willReturn(aResponse()
                .withHeader("Content-Type", "text/html").withBody(bytes)));
        stubFor(get(urlPathEqualTo("/none")).willReturn(aResponse().withBody(bytes)));
        stubFor(get(urlPathEqualTo("/quote")).willReturn(aResponse()
                .withHeader("Content-Type", "image/x\"y").withBody(bytes)));
        for (String path : new String[] {"/html", "/none", "/quote"}) {
            String src = wm.getHttpBaseUrl() + path;
            Throwable thrown = catchThrowable(() -> HtmlImages.embed(htmlOf(src), base, work));
            assertThat(thrown).isExactlyInstanceOf(InvalidMailException.class)
                    .hasMessage(message(src, "response has no image content type"));
            assertThat(thrown.getMessage()).doesNotContain("text/html").doesNotContain("image/x");
        }
    }

    /**
     * Whether the stub answers a client like the helper's without a Content-Length. Measured while
     * writing: over HTTP/1.1 the same stub sends Transfer-Encoding: chunked, and the default client
     * negotiates HTTP/2 with WireMock, where the body is a stream of frames with no length header.
     */
    private static boolean hasNoContentLength(String url) throws Exception {
        HttpResponse<InputStream> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(url)).build(), HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream body = response.body()) {
            return response.headers().firstValue("Content-Length").isEmpty();
        }
    }

    @Test
    void sizeCapIsThreeMebibytes(WireMockRuntimeInfo wm) throws Exception {
        // GraphTransport.validate refuses 3,145,728 bytes, so the helper must refuse them too.
        stubImage("/img/under.png", filled(3_145_727, 'u'));
        stubImage("/img/exact.png", filled(3_145_728, 'e'));
        stubFor(get(urlPathEqualTo("/img/chunked.png")).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "image/png")
                .withBody(filled(3_145_728, 'c'))
                .withChunkedDribbleDelay(2, 50)));

        HtmlImages.Result accepted = HtmlImages.embed(htmlOf(wm.getHttpBaseUrl() + "/img/under.png"), base, work);
        assertThat(Files.size(accepted.images().get(0).file())).isEqualTo(3_145_727L);

        String exact = wm.getHttpBaseUrl() + "/img/exact.png";
        assertThat(catchThrowable(() -> HtmlImages.embed(htmlOf(exact), base, work)))
                .isExactlyInstanceOf(InvalidMailException.class)
                .hasMessage(message(exact, "image is 3 MiB or larger"));

        String chunked = wm.getHttpBaseUrl() + "/img/chunked.png";
        assertThat(hasNoContentLength(chunked)).as("the dribble delay sends no Content-Length").isTrue();
        assertThat(catchThrowable(() -> HtmlImages.embed(htmlOf(chunked), base, work)))
                .isExactlyInstanceOf(InvalidMailException.class)
                .hasMessage(message(chunked, "image is 3 MiB or larger"));
    }

    @Test
    @Timeout(20)
    void slowHeadersTimeOut(WireMockRuntimeInfo wm) {
        stubFor(get(urlPathEqualTo("/img/slow.png")).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "image/png")
                .withBody("x")
                .withFixedDelay(3000)));
        String src = wm.getHttpBaseUrl() + "/img/slow.png";

        long start = System.nanoTime();
        Throwable thrown = catchThrowable(
                () -> HtmlImages.embed(htmlOf(src), base, work, Duration.ofSeconds(1)));
        long millis = (System.nanoTime() - start) / 1_000_000;

        assertThat(thrown).isExactlyInstanceOf(TransientMailException.class)
                .hasMessage(message(src, "download timed out"));
        assertThat(millis).isLessThan(2900);
    }

    private static boolean readerRunning() {
        // The reader is a thread named by HtmlImages, so the test needs no private method name.
        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            if (thread.getName().equals("morimail-html-download") && thread.isAlive()) {
                return true;
            }
        }
        return false;
    }

    @Test
    @Timeout(20)
    void stalledBodyTimesOut(WireMockRuntimeInfo wm) throws Exception {
        // One 2 s budget ends the call at about 2.0 s (measured 2,002 ms). A fresh 2 s deadline after
        // the headers takes at least 3.2 s (measured 3,211 ms), and no body deadline lets the call
        // succeed after about 6 s, so the test fails for both. The reader was gone within 21 ms after
        // close() in the planning experiment.
        stubFor(get(urlPathEqualTo("/img/stall.png")).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "image/png")
                .withBody(filled(100, 's'))
                .withChunkedDribbleDelay(5, 6000)));
        String src = wm.getHttpBaseUrl() + "/img/stall.png";
        AtomicReference<Throwable> error = new AtomicReference<>();
        AtomicLong elapsed = new AtomicLong();
        Thread worker = new Thread(() -> {
            long start = System.nanoTime();
            try {
                HtmlImages.embed(htmlOf(src), base, work, Duration.ofSeconds(2));
            } catch (Throwable t) {
                error.set(t);
            }
            elapsed.set((System.nanoTime() - start) / 1_000_000);
        });
        worker.start();

        boolean sawReader = false;
        while (worker.isAlive() && !sawReader) {
            sawReader = readerRunning();
            if (!sawReader) {
                Thread.sleep(20);
            }
        }
        worker.join(5000);

        assertThat(sawReader).as("a reader thread was seen while the body stalled").isTrue();
        assertThat(worker.isAlive()).isFalse();
        assertThat(error.get()).isExactlyInstanceOf(TransientMailException.class)
                .hasMessage(message(src, "download timed out"));
        assertThat(elapsed.get()).isLessThan(3000);
        long grace = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        while (readerRunning() && System.nanoTime() < grace) {
            Thread.sleep(20);
        }
        assertThat(readerRunning()).as("no reader outlives the call").isFalse();
    }

    @Test
    void networkFailuresAreTransient(WireMockRuntimeInfo wm) throws Exception {
        stubFor(get(urlPathEqualTo("/reset")).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
        stubFor(get(urlPathEqualTo("/empty")).willReturn(aResponse().withFault(Fault.EMPTY_RESPONSE)));
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        for (String src : new String[] {wm.getHttpBaseUrl() + "/reset", wm.getHttpBaseUrl() + "/empty",
                "http://127.0.0.1:" + closedPort + "/x.png"}) {
            Throwable thrown = catchThrowable(() -> HtmlImages.embed(htmlOf(src), base, work));
            assertThat(thrown).isExactlyInstanceOf(TransientMailException.class)
                    .hasMessage(message(src, "download failed"));
        }
    }

    @Test
    @Timeout(20)
    void interruptedDownloadIsTransientAndKeepsTheFlag(WireMockRuntimeInfo wm) throws Exception {
        stubFor(get(urlPathEqualTo("/img/wait.png")).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "image/png")
                .withBody("x")
                .withFixedDelay(5000)));
        String src = wm.getHttpBaseUrl() + "/img/wait.png";
        AtomicReference<Throwable> error = new AtomicReference<>();
        AtomicReference<Boolean> flag = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try {
                HtmlImages.embed(htmlOf(src), base, work);
            } catch (Throwable t) {
                error.set(t);
            }
            flag.set(Thread.currentThread().isInterrupted());
        });
        worker.start();
        // Interrupt once the request has reached the server, not after a fixed sleep.
        long arrival = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (wm.getWireMock().getServeEvents().isEmpty() && System.nanoTime() < arrival) {
            Thread.sleep(20);
        }
        worker.interrupt();
        worker.join(3000);

        assertThat(worker.isAlive()).isFalse();
        assertThat(error.get()).isExactlyInstanceOf(TransientMailException.class)
                .hasMessage(message(src, "download was interrupted"));
        assertThat(flag.get()).isTrue();
    }

    @Test
    void fileNamesComeFromTheLastUrlSegment(WireMockRuntimeInfo wm) throws Exception {
        stubFor(get(anyUrl()).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "image/png")
                .withBody(new byte[] {1, 2, 3})));
        String[][] cases = {
            {"/img/My%20Logo.png?x=1", "My Logo.png", "My-Logo"},
            {"/", "image.png", "image"},
            // A control character in the decoded segment falls back to ID.ext.
            {"/a%0Ab.png", "a-b.png", "a-b"},
            // One "-" per code point, not per UTF-16 unit.
            {"/%F0%9F%98%80.png", "😀.png", "-"},
        };
        for (String[] c : cases) {
            String src = wm.getHttpBaseUrl() + c[0];
            HtmlImages.Result result = HtmlImages.embed(htmlOf(src), base, work);
            assertThat(result.images()).as(c[0]).hasSize(1);
            Attachment image = result.images().get(0);
            assertThat(image.filename()).as(c[0]).isEqualTo(c[1]);
            assertThat(image.contentId()).as(c[0]).isEqualTo(c[2]);
            assertThat(result.template().html()).isEqualTo("<img src=\"cid:" + c[2] + "\">");
            assertThat(image.filename()).doesNotStartWith("morimail-");
            assertThat(image.file().getFileName().toString()).startsWith("morimail-");
        }
    }

    @Test
    void sameUrlIsDownloadedOnce(WireMockRuntimeInfo wm) throws Exception {
        stubImage("/img/logo.png", new byte[] {1, 2, 3});
        String url = wm.getHttpBaseUrl() + "/img/logo.png";
        MailTemplate template = new MailTemplate("S",
                "<img src=\"" + url + "?a=1&amp;b=2\"><img src=\"" + url + "?a=1&b=2\">", null);

        HtmlImages.Result result = HtmlImages.embed(template, base, work);

        assertThat(result.template().html()).isEqualTo("<img src=\"cid:logo\"><img src=\"cid:logo\">");
        assertThat(result.images()).hasSize(1);
        assertThat(requests("/img/logo.png")).isEqualTo(1);
    }

    @Test
    void sameLastSegmentGivesTwoFiles(WireMockRuntimeInfo wm) throws Exception {
        stubImage("/a/logo.png", new byte[] {'A', 'A', 'A'});
        stubImage("/b/logo.png", new byte[] {'B', 'B', 'B'});

        HtmlImages.Result result = HtmlImages.embed(
                htmlOf(wm.getHttpBaseUrl() + "/a/logo.png", wm.getHttpBaseUrl() + "/b/logo.png"), base, work);

        assertThat(result.template().html()).isEqualTo("<img src=\"cid:logo\"><img src=\"cid:logo-2\">");
        List<Attachment> images = result.images();
        assertThat(images).extracting(Attachment::contentId).containsExactly("logo", "logo-2");
        assertThat(images).extracting(Attachment::filename).containsExactly("logo.png", "logo.png");
        assertThat(images.get(0).file()).isNotEqualTo(images.get(1).file());
        assertThat(Files.readAllBytes(images.get(0).file())).isEqualTo(new byte[] {'A', 'A', 'A'});
        assertThat(Files.readAllBytes(images.get(1).file())).isEqualTo(new byte[] {'B', 'B', 'B'});
    }

    @Test
    void remoteAndLocalIdsShareOneNamespace(WireMockRuntimeInfo wm) throws Exception {
        Files.write(base.resolve("logo.png"), new byte[] {'L', 'O', 'C', 'A', 'L'});
        stubImage("/x/logo.png", new byte[] {'R', 'E', 'M'});

        HtmlImages.Result result = HtmlImages.embed(
                htmlOf("logo.png", wm.getHttpBaseUrl() + "/x/logo.png"), base, work);

        assertThat(result.template().html()).isEqualTo("<img src=\"cid:logo\"><img src=\"cid:logo-2\">");
        assertThat(result.images()).extracting(Attachment::contentId).containsExactly("logo", "logo-2");
    }

    @Test
    void unusableWorkDirectoryFails(WireMockRuntimeInfo wm) {
        stubImage("/img/logo.png", new byte[] {1, 2, 3});
        String src = wm.getHttpBaseUrl() + "/img/logo.png";

        assertThat(catchThrowable(() -> HtmlImages.embed(htmlOf(src), base, tempDir.resolve("missing"))))
                .isExactlyInstanceOf(InvalidMailException.class)
                .hasMessage(message(src, "work directory is not usable"));
    }

    @Test
    void invalidUrlFails(WireMockRuntimeInfo wm) {
        for (String src : new String[] {wm.getHttpBaseUrl() + "/a b.png", "http:x.png"}) {
            assertThat(catchThrowable(() -> HtmlImages.embed(htmlOf(src), base, work)))
                    .isExactlyInstanceOf(InvalidMailException.class)
                    .hasMessage(message(src, "is not a valid URL"));
        }
        assertThat(findAll(anyRequestedFor(anyUrl()))).isEmpty();
    }

    @Test
    void concurrentDownloadsUseSeparateFiles(WireMockRuntimeInfo wm) throws Exception {
        byte[] bytes = {'C', 'O', 'N', 'C', '1'};
        stubImage("/img/logo.png", bytes);
        MailTemplate template = htmlOf(wm.getHttpBaseUrl() + "/img/logo.png");
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Future<HtmlImages.Result>> futures = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                futures.add(pool.submit(() -> HtmlImages.embed(template, base, work)));
            }
            Set<Path> files = new HashSet<>();
            String html = null;
            for (Future<HtmlImages.Result> future : futures) {
                HtmlImages.Result result = future.get(20, TimeUnit.SECONDS);
                if (html == null) {
                    html = result.template().html();
                }
                assertThat(result.template().html()).isEqualTo(html);
                assertThat(result.images()).hasSize(1);
                Path file = result.images().get(0).file();
                assertThat(Files.readAllBytes(file)).isEqualTo(bytes);
                files.add(file);
            }
            assertThat(html).isEqualTo("<img src=\"cid:logo\">");
            assertThat(files).hasSize(4);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void failedCallKeepsEarlierFiles(WireMockRuntimeInfo wm) throws Exception {
        Path keep = Files.writeString(work.resolve("keep.txt"), "mine");
        byte[] ok = {'O', 'K', '1'};
        stubImage("/img/ok.png", ok);
        stubFor(get(urlPathEqualTo("/img/missing.png")).willReturn(aResponse().withStatus(404)));
        String missing = wm.getHttpBaseUrl() + "/img/missing.png";

        Throwable thrown = catchThrowable(() -> HtmlImages.embed(
                htmlOf(wm.getHttpBaseUrl() + "/img/ok.png", missing), base, work));

        assertThat(thrown).isExactlyInstanceOf(InvalidMailException.class)
                .hasMessage(message(missing, "download failed with HTTP 404"));
        assertThat(Files.readString(keep)).isEqualTo("mine");
        try (Stream<Path> files = Files.list(work)) {
            List<Path> written = files.filter(f -> f.getFileName().toString().startsWith("morimail-")).toList();
            assertThat(written).hasSize(1);
            assertThat(Files.readAllBytes(written.get(0))).isEqualTo(ok);
        }
        try (Stream<Path> files = Files.list(work)) {
            assertThat(files.count()).isEqualTo(2);
        }
    }

    @Test
    void downloadIsLoggedAtDebugOnly(WireMockRuntimeInfo wm) throws Exception {
        stubImage("/img/logo.png", new byte[] {1, 2, 3});
        String src = "http://user:secret-pass@localhost:" + wm.getHttpPort()
                + "/img/logo.png?sig=secret-token#top";
        List<LogRecord> records = new ArrayList<>();
        Logger logger = Logger.getLogger("de.morisoft.morimail.html.HtmlImages");
        Level before = logger.getLevel();
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                records.add(record);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        handler.setLevel(Level.ALL);
        logger.setLevel(Level.ALL);
        logger.addHandler(handler);
        try {
            HtmlImages.embed(htmlOf(src), base, work);
        } finally {
            logger.removeHandler(handler);
            logger.setLevel(before);
        }

        assertThat(records).hasSize(1);
        assertThat(records.get(0).getLevel()).isEqualTo(Level.FINE);
        SimpleFormatter formatter = new SimpleFormatter();
        assertThat(formatter.formatMessage(records.get(0)))
                .isEqualTo("Downloading image http://localhost:" + wm.getHttpPort() + "/img/logo.png");
        assertThat(records).allSatisfy(r -> assertThat(formatter.formatMessage(r))
                .doesNotContain("secret-pass", "secret-token", "sig=", "#top"));
    }

    @Test
    void nullArgumentsThrowNullPointerException() {
        MailTemplate template = htmlOf("a.png");
        assertThatThrownBy(() -> HtmlImages.embed(null, base, work)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> HtmlImages.embed(template, null, work)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> HtmlImages.embed(template, base, null)).isInstanceOf(NullPointerException.class);
    }
}
