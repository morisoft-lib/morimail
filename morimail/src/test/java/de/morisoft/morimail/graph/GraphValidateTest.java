package de.morisoft.morimail.graph;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.findAll;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import de.morisoft.morimail.InvalidMailException;
import de.morisoft.morimail.Mail;
import de.morisoft.morimail.MailAccount;
import de.morisoft.morimail.MailMessage;
import de.morisoft.morimail.MailTransport;
import de.morisoft.morimail.Mailer;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@WireMockTest
class GraphValidateTest {

    private static final String LIMIT_PER_FILE = "Graph takes attachments below 3 MiB (3145728 bytes)";

    @TempDir
    Path dir;

    private final StubTokenProvider stub = new StubTokenProvider("t1", "t2");

    private MailTransport transport(WireMockRuntimeInfo wm) {
        return new GraphTransportProvider()
                .create(Map.of("baseUrl", wm.getHttpBaseUrl() + "/v1.0"), stub);
    }

    private Path sparse(String name, long size) throws IOException {
        Path file = dir.resolve(name);
        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "rw")) {
            raf.setLength(size);
        }
        return file;
    }

    private static Mail.Builder base() {
        return Mail.using("main").from("newsletter@example.com").to("anna@example.org").subject("Hello");
    }

    private static MailMessage withText(String text) throws InvalidMailException {
        return base().text(text).build().message();
    }

    private MailMessage withFiles(String text, Path... files) throws InvalidMailException {
        Mail.Builder builder = base().text(text);
        for (Path file : files) {
            builder.attach(file);
        }
        return builder.build().message();
    }

    private MailMessage withInline(Path image, String contentId, Path... files) throws InvalidMailException {
        Mail.Builder builder = base().html("<img src=\"cid:" + contentId + "\">").inline(image, contentId);
        for (Path file : files) {
            builder.attach(file);
        }
        return builder.build().message();
    }

    @ParameterizedTest
    @CsvSource({"0,0", "1,4", "2,4", "3,4", "4,8", "3145727,4194304", "3145728,4194304"})
    void base64SizeIsExactInLongArithmetic(long size, long expected) {
        assertThat(GraphTransport.base64Size(size)).isEqualTo(expected);
    }

    @Test
    void singleFileOfThreeMiBIsReportedByThePerFileRule(WireMockRuntimeInfo wm) throws Exception {
        MailMessage message = withFiles("see attachment", sparse("big.bin", 3_145_728));

        assertThatThrownBy(() -> transport(wm).validate(message))
                .isExactlyInstanceOf(InvalidMailException.class)
                .hasMessage("attachment big.bin has 3145728 bytes; " + LIMIT_PER_FILE);
    }

    @Test
    void fileJustBelowThreeMiBFailsTheEstimateNotTheFileRules(WireMockRuntimeInfo wm) throws Exception {
        MailMessage message = withFiles("see attachment", sparse("big.bin", 3_145_727));

        assertThatThrownBy(() -> transport(wm).validate(message))
                .isExactlyInstanceOf(InvalidMailException.class)
                .hasMessageContaining("estimated request size")
                .hasMessageContaining("4194304")
                .message().doesNotContain("big.bin");
    }

    @Test
    void twoFilesOfHalfTheLimitFailTheTotalRuleNamingBoth(WireMockRuntimeInfo wm) throws Exception {
        MailMessage message = withFiles("see attachment",
                sparse("a.bin", 1_572_864), sparse("b.bin", 1_572_864));

        assertThatThrownBy(() -> transport(wm).validate(message))
                .isExactlyInstanceOf(InvalidMailException.class)
                .hasMessage("attachments a.bin, b.bin have 3145728 bytes together; Graph takes below 3 MiB (3145728 bytes) in total");
    }

    @Test
    void filesJustBelowTheTotalFailTheEstimate(WireMockRuntimeInfo wm) throws Exception {
        MailMessage message = withFiles("see attachment",
                sparse("a.bin", 1_572_863), sparse("b.bin", 1_572_864));

        assertThatThrownBy(() -> transport(wm).validate(message))
                .isExactlyInstanceOf(InvalidMailException.class)
                .hasMessageContaining("estimated request size");
    }

    @Test
    void fileOfAboutTwoPointNineMegabytesWithShortTextPasses(WireMockRuntimeInfo wm) throws Exception {
        MailMessage message = withFiles("see attachment", sparse("ok.bin", 2_900_000));

        assertThatCode(() -> transport(wm).validate(message)).doesNotThrowAnyException();
    }

    @Test
    void inlineImageOfThreeMiBIsReportedByThePerFileRule(WireMockRuntimeInfo wm) throws Exception {
        MailMessage message = withInline(sparse("big.png", 3_145_728), "big");

        assertThatThrownBy(() -> transport(wm).validate(message))
                .isExactlyInstanceOf(InvalidMailException.class)
                .hasMessage("attachment big.png has 3145728 bytes; " + LIMIT_PER_FILE);
    }

    @Test
    void inlineImageJustBelowThreeMiBFailsTheEstimateNotTheFileRules(WireMockRuntimeInfo wm) throws Exception {
        MailMessage message = withInline(sparse("big.png", 3_145_727), "big");

        assertThatThrownBy(() -> transport(wm).validate(message))
                .isExactlyInstanceOf(InvalidMailException.class)
                .hasMessageContaining("estimated request size")
                .hasMessageContaining("4194304")
                .message().doesNotContain("big.png");
    }

    @Test
    void inlineImageOfAboutTwoPointNineMegabytesPasses(WireMockRuntimeInfo wm) throws Exception {
        MailMessage message = withInline(sparse("big.png", 2_900_000), "big");

        assertThatCode(() -> transport(wm).validate(message)).doesNotThrowAnyException();
    }

    @Test
    void inlineImageAndRegularFileShareTheThreeMiBTotal(WireMockRuntimeInfo wm) throws Exception {
        MailMessage message = withInline(sparse("logo.png", 1_572_864), "logo", sparse("b.bin", 1_572_864));

        assertThatThrownBy(() -> transport(wm).validate(message))
                .isExactlyInstanceOf(InvalidMailException.class)
                .hasMessage("attachments logo.png, b.bin have 3145728 bytes together; Graph takes below 3 MiB (3145728 bytes) in total");
    }

    @Test
    void unreadableFileIsNamed(WireMockRuntimeInfo wm) throws Exception {
        Path gone = sparse("gone.bin", 10);
        MailMessage message = withFiles("see attachment", gone);
        Files.delete(gone);

        assertThatThrownBy(() -> transport(wm).validate(message))
                .isExactlyInstanceOf(InvalidMailException.class)
                .hasMessage("attachment gone.bin cannot be read");
    }

    @Test
    void asciiTextBodyIsAcceptedUpToTheEstimateLimit(WireMockRuntimeInfo wm) throws Exception {
        MailTransport transport = transport(wm);
        MailMessage atLimit = withText("a".repeat(4_177_920));
        MailMessage over = withText("a".repeat(4_177_921));

        assertThatCode(() -> transport.validate(atLimit)).doesNotThrowAnyException();
        assertThatThrownBy(() -> transport.validate(over))
                .isExactlyInstanceOf(InvalidMailException.class)
                .hasMessageContaining("estimated request size 4194305 bytes")
                .hasMessageContaining("4194304");
    }

    @Test
    void umlautsCountTwoBytesEach(WireMockRuntimeInfo wm) throws Exception {
        MailTransport transport = transport(wm);
        MailMessage atLimit = withText("ü".repeat(2_088_960));
        MailMessage over = withText("ü".repeat(2_088_961));

        assertThatCode(() -> transport.validate(atLimit)).doesNotThrowAnyException();
        assertThatThrownBy(() -> transport.validate(over))
                .isExactlyInstanceOf(InvalidMailException.class)
                .hasMessageContaining("estimated request size");
    }

    @Test
    void quotesCountAsEscapedAndTheSentRequestStaysWithinTheLimit(WireMockRuntimeInfo wm) throws Exception {
        stubFor(post(urlMatching("/v1\\.0/users/.*/sendMail")).willReturn(aResponse().withStatus(202)));
        MailTransport transport = transport(wm);
        MailMessage atLimit = withText("\"".repeat(2_088_960));
        MailMessage over = withText("\"".repeat(2_088_961));

        assertThatCode(() -> transport.validate(atLimit)).doesNotThrowAnyException();
        transport.send(atLimit);
        List<LoggedRequest> sent = findAll(postRequestedFor(urlMatching(".*")));
        assertThat(sent).hasSize(1);
        assertThat(sent.get(0).getBody().length).isLessThanOrEqualTo(4_194_304);
        assertThatThrownBy(() -> transport.validate(over))
                .isExactlyInstanceOf(InvalidMailException.class)
                .hasMessageContaining("estimated request size");
    }

    @Test
    void htmlIsCountedWhenItIsTheBodyThatIsSent(WireMockRuntimeInfo wm) throws Exception {
        MailMessage message = base().html("<p>" + "a".repeat(4_177_920) + "</p>").text("short").build().message();

        assertThatThrownBy(() -> transport(wm).validate(message))
                .isExactlyInstanceOf(InvalidMailException.class)
                .hasMessageContaining("estimated request size");
    }

    @Test
    void mailerRejectsAnOversizedMailBeforeAnyTokenOrRequest(WireMockRuntimeInfo wm) throws Exception {
        MailAccount account = MailAccount.builder("main").transport("graph").auth(stub)
                .option("baseUrl", wm.getHttpBaseUrl() + "/v1.0").defaultFrom("newsletter@example.com").build();
        Mail mail = base().text("see attachment").attach(sparse("big.bin", 3_145_728)).build();

        assertThatThrownBy(() -> Mailer.of(account).send(mail))
                .isInstanceOf(InvalidMailException.class)
                .hasMessageContaining("big.bin");

        assertThat(stub.accessCalls()).isZero();
        assertThat(findAll(postRequestedFor(urlMatching(".*")))).isEmpty();
    }

    @Test
    void mailerRejectsAnOversizedInlineImageBeforeAnyTokenOrRequest(WireMockRuntimeInfo wm) throws Exception {
        MailAccount account = MailAccount.builder("main").transport("graph").auth(stub)
                .option("baseUrl", wm.getHttpBaseUrl() + "/v1.0").defaultFrom("newsletter@example.com").build();
        Mail mail = base().html("<img src=\"cid:big\">").inline(sparse("big.png", 3_145_728), "big").build();

        assertThatThrownBy(() -> Mailer.of(account).send(mail))
                .isInstanceOf(InvalidMailException.class)
                .hasMessageContaining("big.png");

        assertThat(stub.accessCalls()).isZero();
        assertThat(findAll(postRequestedFor(urlMatching(".*")))).isEmpty();
    }

    @Test
    void eightThreadsSendThroughOneTransport(WireMockRuntimeInfo wm) throws Exception {
        stubFor(post(urlMatching("/v1\\.0/users/.*/sendMail")).willReturn(aResponse().withStatus(202)));
        MailTransport transport = transport(wm);
        MailMessage message = withText("small");
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<?>> results = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                results.add(pool.submit(() -> {
                    transport.validate(message);
                    transport.send(message);
                    return null;
                }));
            }
            for (Future<?> result : results) {
                result.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(findAll(postRequestedFor(urlMatching(".*")))).hasSize(8);
    }
}
