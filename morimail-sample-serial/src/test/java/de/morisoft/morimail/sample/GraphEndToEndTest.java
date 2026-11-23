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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

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
            assertThat(names).containsExactly("info.pdf", "sample.png", "logo.png");
            JsonNode logoNode = message.get("attachments").get(2);
            assertThat(logoNode.get("isInline").asBoolean()).isTrue();
            assertThat(logoNode.get("contentId").asText()).isEqualTo("logo");
            for (int i = 0; i < 2; i++) {
                assertThat(message.get("attachments").get(i).has("isInline")).isFalse();
                assertThat(message.get("attachments").get(i).has("contentId")).isFalse();
            }
            assertThat(message.get("body").get("content").asText())
                    .contains("src=\"cid:logo\"").doesNotContain("inline/logo.png");
            if (address.equals("anna@example.org")) {
                assertThat(message.get("attachments").get(0).get("contentBytes").asText())
                        .isEqualTo(Base64.getEncoder().encodeToString(info));
                assertThat(logoNode.get("contentBytes").asText()).isEqualTo(Base64.getEncoder()
                        .encodeToString(Files.readAllBytes(campaign.resolve("inline").resolve("logo.png"))));
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

    private SampleFixtures.Run runLimited(Path account, Path journal, Map<String, String> env, String... more) {
        List<String> args = new ArrayList<>(List.of("send", "--account", account.toString(),
                "--campaign", Path.of("example-campaign").toString(), "--journal", journal.toString()));
        args.addAll(List.of(more));
        return SampleFixtures.run(factory(env), new ArrayList<>(), args.toArray(new String[0]));
    }

    private void assertPreflight(SampleFixtures.Run run, Path journal, String problem, String... absent) {
        assertThat(run.exit()).isEqualTo(2);
        assertThat(run.errLines()).containsExactly(problem,
                "Pre-flight failed with 1 problem(s); nothing was sent.");
        for (String value : absent) {
            assertThat(run.err()).doesNotContain(value);
            assertThat(run.out()).doesNotContain(value);
        }
        assertThat(WireMock.getAllServeEvents()).isEmpty();
        assertThat(journal).doesNotExist();
    }

    @Test
    void secretFlowSendsAClientSecretAndNoAssertion() throws IOException {
        stubToken();
        stubSend();
        Path account = SampleFixtures.writeAccount(root, CERT_ACCOUNT + "secretEnv=MAIL_CLIENT_SECRET\n");

        SampleFixtures.Run run = runLimited(account, root.resolve("journal.jsonl"),
                Map.of("MAIL_CLIENT_SECRET", "e2e-secret"), "--limit", "1");

        assertThat(run.exit()).isZero();
        assertThat(tokenRequests()).hasSize(1);
        Map<String, String> tokenForm = form(tokenRequests().get(0));
        assertThat(tokenForm).containsEntry("client_secret", "e2e-secret")
                .doesNotContainKey("client_assertion");
        assertThat(sendRequests()).hasSize(1);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = "")
    void missingSecretIsAPreflightProblem(String value) throws IOException {
        Path account = SampleFixtures.writeAccount(root, CERT_ACCOUNT + "secretEnv=MAIL_CLIENT_SECRET\n");
        Map<String, String> env = new HashMap<>();
        env.put("MAIL_CLIENT_SECRET", value);

        SampleFixtures.Run run = runLimited(account, root.resolve("journal.jsonl"), env);

        assertPreflight(run, root.resolve("journal.jsonl"),
                "account: secretEnv: the environment variable it names is not set or is empty");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = "")
    void missingCertificatePasswordIsAPreflightProblem(String value) throws IOException {
        Path account = certificateAccount();
        Map<String, String> env = new HashMap<>();
        env.put("MAIL_CERT_PASSWORD", value);

        SampleFixtures.Run run = runLimited(account, root.resolve("journal.jsonl"), env);

        assertPreflight(run, root.resolve("journal.jsonl"),
                "account: certificate.passwordEnv: the environment variable it names is not set or is empty");
    }

    @Test
    void missingKeyStoreFileIsAPreflightProblemWithoutThePath() throws IOException {
        Path missing = root.resolve("keys").resolve("missing.p12");
        Path account = SampleFixtures.writeAccount(root, CERT_ACCOUNT
                + "certificate.path=" + missing.toString().replace('\\', '/') + "\n"
                + "certificate.passwordEnv=MAIL_CERT_PASSWORD\n");

        SampleFixtures.Run run = runLimited(account, root.resolve("journal.jsonl"),
                Map.of("MAIL_CERT_PASSWORD", "changeit"));

        assertPreflight(run, root.resolve("journal.jsonl"),
                "account: certificate.path: the file does not exist or is not a regular file",
                root.toString());
    }

    @Test
    void wrongKeyStorePasswordIsAPreflightProblemWithoutPasswordOrPath() throws IOException {
        Path account = certificateAccount();

        SampleFixtures.Run run = runLimited(account, root.resolve("journal.jsonl"),
                Map.of("MAIL_CERT_PASSWORD", "Sentinel-pw-7f3a"));

        assertPreflight(run, root.resolve("journal.jsonl"),
                "account: certificate.path: the PKCS12 key store cannot be loaded with the password "
                        + "from certificate.passwordEnv",
                "Sentinel-pw-7f3a", root.toString());
    }

    @Test
    void aRejectedMailboxStopsTheRunWithExitThree() throws IOException {
        stubToken();
        stubSend();
        stubFor(post(urlEqualTo(SEND_PATH))
                .withRequestBody(matchingJsonPath("$.message.toRecipients[0].emailAddress.address",
                        equalTo("clara@example.org")))
                .atPriority(1).willReturn(aResponse().withStatus(403)
                        .withHeader("request-id", ZERO_GUID)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":{\"code\":\"ErrorAccessDenied\",\"message\":\"Access to newsletter@example.com is denied.\"}}")));
        Path account = certificateAccount();
        Path journal = root.resolve("rejected.jsonl");

        SampleFixtures.Run run = runLimited(account, journal, Map.of("MAIL_CERT_PASSWORD", "changeit"));

        assertThat(run.exit()).isEqualTo(3);
        List<String> out = run.outLines();
        assertThat(out).hasSize(4);
        assertThat(out.get(0)).isEqualTo("[1/5] anna@example.org accepted");
        assertThat(out.get(1)).isEqualTo("[2/5] ben@example.org accepted");
        assertThat(out.get(2)).startsWith("[3/5] clara@example.org failed: SenderRejectedException: ");
        assertThat(out.get(3)).isEqualTo("accepted 2, skipped 0, failed 1");
        assertThat(run.errLines()).hasSize(1);
        assertThat(run.err()).startsWith("Stopped: SenderRejectedException: Graph sendMail failed: HTTP 403")
                .contains("ErrorAccessDenied").contains(ZERO_GUID)
                .doesNotContain("e2e-token").doesNotContain("Bearer")
                .doesNotContain("newsletter").doesNotContain("Access to");
        List<JsonNode> lines = SampleFixtures.journalLines(journal);
        assertThat(lines).hasSize(3);
        assertThat(lines.get(0).get("outcome").asText()).isEqualTo("accepted");
        assertThat(lines.get(1).get("outcome").asText()).isEqualTo("accepted");
        assertThat(lines.get(2).get("email").asText()).isEqualTo("clara@example.org");
        assertThat(lines.get(2).get("outcome").asText()).isEqualTo("failed");
        assertThat(lines.get(2).get("errorClass").asText()).isEqualTo("SenderRejectedException");
        assertThat(lines.get(2).get("errorMessage").asText()).startsWith("Graph sendMail failed: HTTP 403")
                .doesNotContain("newsletter").doesNotContain("Access to");
        assertThat(sendRequests()).hasSize(3);
        assertThat(sendRequests()).noneMatch(r -> r.getBodyAsString().contains("david@example.org")
                || r.getBodyAsString().contains("emma@example.org"));
    }
}
