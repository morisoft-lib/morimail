package de.morisoft.morimail.sample;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.findAll;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import de.morisoft.morimail.Mailer;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** CLI-14: the sample sends the example campaign for real, with WireMock as token endpoint and Graph. */
@WireMockTest
class GraphEndToEndTest {

    private static final String ZERO_GUID = "00000000-0000-0000-0000-000000000000";
    private static final String TOKEN_PATH = "/" + ZERO_GUID + "/oauth2/v2.0/token";
    private static final String SEND_PATH = "/v1.0/users/newsletter%40example.com/sendMail";
    private static final String CERT_ACCOUNT = String.join("\n",
            "account.id=entra-main",
            "tenantId=" + ZERO_GUID,
            "clientId=" + ZERO_GUID,
            "from=newsletter@example.com") + "\n";
    private static final String[] ADDRESSES = {"anna@example.org", "ben@example.org",
            "clara@example.org", "david@example.org", "emma@example.org"};
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path root;

    private String baseUrl;
    private Path keyStore;

    @BeforeEach
    void setUp(WireMockRuntimeInfo wm) throws IOException {
        baseUrl = wm.getHttpBaseUrl();
        keyStore = root.resolve("keys").resolve("app.p12");
        Files.createDirectories(keyStore.getParent());
        try (InputStream in = GraphEndToEndTest.class.getResourceAsStream("/test-keystore-throwaway.p12")) {
            Files.copy(Objects.requireNonNull(in), keyStore);
        }
    }

    private Function<AccountFile, Mailer> factory(Map<String, String> env) {
        return a -> SerialMail.mailer(a, env::get, URI.create(baseUrl), baseUrl + "/v1.0");
    }

    private Path certificateAccount() throws IOException {
        return SampleFixtures.writeAccount(root, CERT_ACCOUNT
                + "certificate.path=" + keyStore.toString().replace('\\', '/') + "\n"
                + "certificate.passwordEnv=MAIL_CERT_PASSWORD\n");
    }

    private static void stubToken() {
        stubFor(post(urlEqualTo(TOKEN_PATH)).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"access_token\":\"e2e-token\",\"token_type\":\"Bearer\",\"expires_in\":3599}")));
    }

    private static void stubSend() {
        stubFor(post(urlEqualTo(SEND_PATH)).withHeader("Authorization", equalTo("Bearer e2e-token"))
                .atPriority(5).willReturn(aResponse().withStatus(202)));
    }

    private static Map<String, String> form(LoggedRequest request) {
        Map<String, String> form = new LinkedHashMap<>();
        for (String pair : request.getBodyAsString().split("&")) {
            int eq = pair.indexOf('=');
            form.put(URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8),
                    URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
        }
        return form;
    }

    private static List<LoggedRequest> sendRequests() {
        return findAll(postRequestedFor(urlEqualTo(SEND_PATH)));
    }

    private static List<LoggedRequest> tokenRequests() {
        return findAll(postRequestedFor(urlEqualTo(TOKEN_PATH)));
    }

    private static JsonNode json(LoggedRequest request) throws IOException {
        return MAPPER.readTree(request.getBodyAsString());
    }

    @Test
    void exampleCampaignIsSentOnceAndARerunSendsNothing() throws IOException {
        stubToken();
        stubSend();
        Path account = certificateAccount();
        Path journal = root.resolve("journal.jsonl");
        Path campaign = Path.of("example-campaign");
        String[] args = {"send", "--account", account.toString(), "--campaign", campaign.toString(),
                "--journal", journal.toString()};
        List<Duration> waits = new ArrayList<>();

        SampleFixtures.Run first = SampleFixtures.run(factory(Map.of("MAIL_CERT_PASSWORD", "changeit")),
                waits, args);

        assertThat(first.err()).isEmpty();
        assertThat(first.exit()).isZero();
        assertThat(first.outLines()).containsExactly(
                "[1/5] anna@example.org accepted",
                "[2/5] ben@example.org accepted",
                "[3/5] clara@example.org accepted",
                "[4/5] david@example.org accepted",
                "[5/5] emma@example.org accepted",
                "accepted 5, skipped 0, failed 0");
        assertThat(waits).containsExactly(Duration.ofSeconds(3), Duration.ofSeconds(3),
                Duration.ofSeconds(3), Duration.ofSeconds(3));
        List<LoggedRequest> tokens = tokenRequests();
        assertThat(tokens).hasSize(1);
        Map<String, String> tokenForm = form(tokens.get(0));
        assertThat(tokenForm).containsEntry("client_assertion_type",
                "urn:ietf:params:oauth:client-assertion-type:jwt-bearer");
        assertThat(tokenForm).containsKey("client_assertion").doesNotContainKey("client_secret");

        List<LoggedRequest> sends = sendRequests();
        assertThat(sends).hasSize(5);
        byte[] info = Files.readAllBytes(campaign.resolve("attachments").resolve("info.pdf"));
        for (String address : ADDRESSES) {
            List<JsonNode> bodies = new ArrayList<>();
            for (LoggedRequest send : sends) {
                JsonNode message = json(send).get("message");
                if (address.equals(message.get("toRecipients").get(0).get("emailAddress").get("address").asText())) {
                    bodies.add(message);
                }
            }
            assertThat(bodies).as(address).hasSize(1);
            JsonNode message = bodies.get(0);
            assertThat(message.get("body").get("contentType").asText()).isEqualTo("HTML");
            List<String> names = new ArrayList<>();
            for (JsonNode attachment : message.get("attachments")) {
                names.add(attachment.get("name").asText());
            }
            assertThat(names).containsExactly("info.pdf", "logo.png");
            if (address.equals("anna@example.org")) {
                assertThat(message.get("attachments").get(0).get("contentBytes").asText())
                        .isEqualTo(Base64.getEncoder().encodeToString(info));
            }
        }
        assertThat(SampleFixtures.journalLines(journal)).hasSize(5)
                .allMatch(line -> line.get("outcome").asText().equals("accepted"));

        WireMock.resetAllRequests();
        SampleFixtures.Run second = SampleFixtures.run(factory(Map.of("MAIL_CERT_PASSWORD", "changeit")),
                new ArrayList<>(), args);

        assertThat(second.exit()).isZero();
        assertThat(second.outLines()).containsExactly(
                "[1/5] anna@example.org skipped (journal)",
                "[2/5] ben@example.org skipped (journal)",
                "[3/5] clara@example.org skipped (journal)",
                "[4/5] david@example.org skipped (journal)",
                "[5/5] emma@example.org skipped (journal)",
                "accepted 0, skipped 5, failed 0");
        assertThat(WireMock.getAllServeEvents()).isEmpty();
        assertThat(SampleFixtures.journalLines(journal)).hasSize(5);
        assertThat(campaign.resolve("journal.jsonl")).doesNotExist();
    }
}
