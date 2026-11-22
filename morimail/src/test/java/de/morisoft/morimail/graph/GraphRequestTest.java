package de.morisoft.morimail.graph;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.findAll;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import de.morisoft.morimail.InvalidMailException;
import de.morisoft.morimail.Mail;
import de.morisoft.morimail.MailAccount;
import de.morisoft.morimail.MailAddress;
import de.morisoft.morimail.MailMessage;
import de.morisoft.morimail.MailTransport;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

@WireMockTest
class GraphRequestTest {

    private static final String SEND_PATH = "/v1.0/users/newsletter%40example.com/sendMail";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path dir;

    private final StubTokenProvider stub = new StubTokenProvider("t1", "t2");

    private MailTransport transport(WireMockRuntimeInfo wm, Map<String, Object> extra) {
        Map<String, Object> options = new java.util.HashMap<>(extra);
        options.put("baseUrl", wm.getHttpBaseUrl() + "/v1.0");
        return new GraphTransportProvider().create(options, stub);
    }

    private MailTransport transport(WireMockRuntimeInfo wm) {
        return transport(wm, Map.of());
    }

    private static void stubSend() {
        stubFor(post(urlMatching("/v1\\.0/users/.*/sendMail")).willReturn(aResponse().withStatus(202)));
    }

    private static List<LoggedRequest> sends() {
        return findAll(postRequestedFor(urlEqualTo(SEND_PATH)));
    }

    private static JsonNode bodyOf(LoggedRequest request) throws IOException {
        return MAPPER.readTree(request.getBody());
    }

    private static Mail.Builder base() {
        return Mail.using("main").from("newsletter@example.com").to("anna@example.org").subject("Hello");
    }

    private static MailMessage textMail() throws InvalidMailException {
        return base().text("Plain").build().message();
    }

    private static ObjectNode recipient(String address, String name) {
        ObjectNode node = MAPPER.createObjectNode();
        ObjectNode email = node.putObject("emailAddress");
        email.put("address", address);
        if (name != null) {
            email.put("name", name);
        }
        return node;
    }

    private static ObjectNode fileAttachment(String name, String type, byte[] bytes) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("@odata.type", "#microsoft.graph.fileAttachment");
        node.put("name", name);
        node.put("contentType", type);
        node.put("contentBytes", Base64.getEncoder().encodeToString(bytes));
        return node;
    }

    private Path file(String name, byte[] bytes) throws IOException {
        Path file = dir.resolve(name);
        Files.write(file, bytes);
        return file;
    }

    @Test
    void fullMailArrivesAsTheDocumentedJson(WireMockRuntimeInfo wm) throws Exception {
        stubSend();
        byte[] pdf = {'%', 'P', 'D', 'F', 0, 1, 2, (byte) 0xff};
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 13, 10};
        MailMessage message = Mail.using("main")
                .from(MailAddress.of("newsletter@example.com", "News Team"))
                .to(MailAddress.of("anna@example.org", "Anna Ä"), MailAddress.of("ben@example.org"))
                .cc("carl@example.org")
                .bcc("dora@example.org")
                .replyTo(MailAddress.of("office@example.com", "Office"))
                .subject("Quote \"x\" \\ <y> ü 😀")
                .html("<p>Hi &amp; bye</p>")
                .attach(file("info.pdf", pdf))
                .attach(file("logo.png", png))
                .build().message();

        transport(wm).send(message);

        assertThat(sends()).hasSize(1);
        LoggedRequest request = sends().get(0);
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer t1");
        assertThat(request.getHeader("Content-Type")).startsWith("application/json");
        ObjectNode expected = MAPPER.createObjectNode();
        ObjectNode m = expected.putObject("message");
        m.put("subject", "Quote \"x\" \\ <y> ü 😀");
        m.putObject("body").put("contentType", "HTML").put("content", "<p>Hi &amp; bye</p>");
        m.putArray("toRecipients").add(recipient("anna@example.org", "Anna Ä"))
                .add(recipient("ben@example.org", null));
        m.putArray("ccRecipients").add(recipient("carl@example.org", null));
        m.putArray("bccRecipients").add(recipient("dora@example.org", null));
        m.putArray("replyTo").add(recipient("office@example.com", "Office"));
        m.putArray("attachments").add(fileAttachment("info.pdf", "application/pdf", pdf))
                .add(fileAttachment("logo.png", "image/png", png));
        assertThat(bodyOf(request)).isEqualTo(expected);
        assertThat(stub.scopes()).containsOnly("https://graph.microsoft.com/.default");
    }

    @Test
    void saveToSentItemsFalseIsSentAsJsonBoolean(WireMockRuntimeInfo wm) throws Exception {
        stubSend();

        transport(wm, Map.of("saveToSentItems", false)).send(textMail());

        JsonNode body = bodyOf(sends().get(0));
        assertThat(body.get("saveToSentItems").isBoolean()).isTrue();
        assertThat(body.get("saveToSentItems").booleanValue()).isFalse();
    }

    @Test
    void saveToSentItemsTrueAndAbsentSendNoKey(WireMockRuntimeInfo wm) throws Exception {
        stubSend();

        transport(wm, Map.of("saveToSentItems", true)).send(textMail());
        transport(wm).send(textMail());

        assertThat(sends()).hasSize(2);
        for (LoggedRequest request : sends()) {
            assertThat(bodyOf(request).has("saveToSentItems")).isFalse();
        }
    }

    @Test
    void bothBodiesLogOneInfoPerTransportAndSendTheHtml(WireMockRuntimeInfo wm) throws Exception {
        stubSend();
        MailMessage both = base().subject("Secret subject").html("<p>H</p>").text("T").build().message();
        List<LogRecord> records = new ArrayList<>();
        Logger logger = Logger.getLogger("de.morisoft.morimail.graph.GraphTransport");
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
        logger.addHandler(handler);
        try {
            MailTransport first = transport(wm);
            first.send(both);
            first.send(both);
            assertThat(infoRecords(records)).hasSize(1);
            transport(wm).send(both);
            assertThat(infoRecords(records)).hasSize(2);
            transport(wm).send(base().html("<p>only html</p>").build().message());
            transport(wm).send(base().text("only text").build().message());
            assertThat(infoRecords(records)).hasSize(2);
        } finally {
            logger.removeHandler(handler);
        }

        for (LogRecord record : infoRecords(records)) {
            assertThat(record.getMessage()).doesNotContain("example.org", "example.com", "Secret", "<p>");
        }
        assertThat(bodyOf(sends().get(0)).get("message").get("body").get("contentType").asText())
                .isEqualTo("HTML");
        assertThat(bodyOf(sends().get(0)).get("message").get("body").get("content").asText())
                .isEqualTo("<p>H</p>");
    }

    private static List<LogRecord> infoRecords(List<LogRecord> records) {
        return records.stream().filter(r -> r.getLevel().intValue() == Level.INFO.intValue()).toList();
    }

    @Test
    void bccOnlyMailSendsNoToRecipientsKey(WireMockRuntimeInfo wm) throws Exception {
        stubSend();
        MailMessage message = Mail.using("main").from("newsletter@example.com").bcc("dora@example.org")
                .subject("Hello").text("Plain").build().message();

        transport(wm).send(message);

        JsonNode m = bodyOf(sends().get(0)).get("message");
        assertThat(m.has("toRecipients")).isFalse();
        assertThat(m.get("bccRecipients")).hasSize(1);
    }

    @Test
    void mailWithoutOptionalPartsSendsNoKeysForThem(WireMockRuntimeInfo wm) throws Exception {
        stubSend();

        transport(wm).send(textMail());

        JsonNode m = bodyOf(sends().get(0)).get("message");
        assertThat(m.has("ccRecipients")).isFalse();
        assertThat(m.has("bccRecipients")).isFalse();
        assertThat(m.has("replyTo")).isFalse();
        assertThat(m.has("attachments")).isFalse();
        assertThat(m.get("toRecipients")).hasSize(1);
        assertThat(bodyOf(sends().get(0)).has("from")).isFalse();
        assertThat(m.has("from")).isFalse();
    }

    @Test
    void blankHtmlWithTextSendsATextBody(WireMockRuntimeInfo wm) throws Exception {
        stubSend();

        transport(wm).send(base().html("  ").text("Plain").build().message());

        JsonNode body = bodyOf(sends().get(0)).get("message").get("body");
        assertThat(body.get("contentType").asText()).isEqualTo("Text");
        assertThat(body.get("content").asText()).isEqualTo("Plain");
    }

    @Test
    void emptyDisplayNameSendsNoName(WireMockRuntimeInfo wm) throws Exception {
        stubSend();
        MailMessage message = Mail.using("main").from("newsletter@example.com")
                .to(MailAddress.of("anna@example.org", "")).subject("Hello").text("Plain").build().message();

        transport(wm).send(message);

        JsonNode address = bodyOf(sends().get(0)).get("message").get("toRecipients").get(0).get("emailAddress");
        assertThat(address.has("name")).isFalse();
        assertThat(address.get("address").asText()).isEqualTo("anna@example.org");
    }

    @Test
    void sameMessageSentTwiceGivesIdenticalBodies(WireMockRuntimeInfo wm) throws Exception {
        stubSend();
        MailMessage message = base().text("Plain").attach(file("a.txt", new byte[] {1, 2, 3})).build().message();
        MailTransport transport = transport(wm);

        transport.send(message);
        transport.send(message);

        assertThat(sends()).hasSize(2);
        assertThat(sends().get(0).getBody()).isEqualTo(sends().get(1).getBody());
    }

    @Test
    void attachmentDeletedAfterBuildFailsBeforeTokenAndRequest(WireMockRuntimeInfo wm) throws Exception {
        stubSend();
        Path gone = file("gone.pdf", new byte[] {1});
        MailMessage message = base().text("Plain").attach(gone).build().message();
        Files.delete(gone);

        assertThatThrownBy(() -> transport(wm).send(message))
                .isExactlyInstanceOf(InvalidMailException.class)
                .hasMessageContaining("gone.pdf");

        assertThat(stub.accessCalls()).isZero();
        assertThat(findAll(postRequestedFor(urlMatching(".*")))).isEmpty();
    }

    static Stream<Arguments> badOptions() {
        return Stream.of(
                Arguments.of("timeout", Map.of("timeout", 5), "5"),
                Arguments.of("saveToSentItems", Map.of("saveToSentItems", "false"), "false"),
                Arguments.of("baseUrl", Map.of("baseUrl", 42), "42"),
                Arguments.of("baseUrl", Map.of("baseUrl", "ftp://example.com"), "ftp://example.com"),
                Arguments.of("baseUrl", Map.of("baseUrl", "not a url"), "not a url"),
                Arguments.of("baseUrl", Map.of("baseUrl", "https:///v1.0"), "https:///v1.0"),
                Arguments.of("baseUrl", Map.of("baseUrl", "http://graph.example.com/v1.0"),
                        "graph.example.com"));
    }

    @ParameterizedTest
    @MethodSource("badOptions")
    void badOptionsNameTheKeyAndNeverTheValue(String key, Map<String, Object> options, String value) {
        assertThatThrownBy(() -> new GraphTransportProvider().create(options, stub))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(key)
                .message().doesNotContain(value);
    }

    @Test
    void missingTokenProviderIsRejected() {
        assertThatThrownBy(() -> new GraphTransportProvider().create(Map.of(), null))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TokenProvider");
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://localhost:8080/v1.0", "http://127.0.0.1:8080/v1.0",
        "http://[::1]:8080/v1.0", "https://graph.example.com/v1.0"})
    void httpsAndLoopbackHttpUrlsAreAccepted(String url) {
        assertThat(new GraphTransportProvider().create(Map.of("baseUrl", url), stub)).isNotNull();
    }

    @Test
    void unknownOptionFailsThroughTheAccountBuilder() {
        MailAccount.Builder builder = MailAccount.builder("main").transport("graph").auth(stub)
                .option("timeout", 5);

        assertThatThrownBy(builder::build)
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("timeout");
    }

    @Test
    void defaultBaseUrlIsTheGraphEndpoint() {
        GraphTransport transport = (GraphTransport) new GraphTransportProvider().create(Map.of(), stub);

        assertThat(transport.sendMailUri("news+tag@example.com").toString())
                .isEqualTo("https://graph.microsoft.com/v1.0/users/news%2Btag%40example.com/sendMail");
    }

    @Test
    void utf8BodyKeepsSpecialCharacters(WireMockRuntimeInfo wm) throws Exception {
        stubSend();

        String text = "line1\nline2 \"q\" \\ <b> ü 😀";

        transport(wm).send(base().text(text).build().message());

        assertThat(new String(sends().get(0).getBody(), StandardCharsets.UTF_8))
                .contains("ü");
        assertThat(bodyOf(sends().get(0)).get("message").get("body").get("content").asText()).isEqualTo(text);
    }
}
