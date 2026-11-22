package de.morisoft.morimail;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.findAll;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import de.morisoft.morimail.entra.EntraClientCredentials;
import de.morisoft.morimail.graph.GraphTransportProvider;
import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

@WireMockTest
class SendThroughGraphTest {

    private static final String ZERO_GUID = "00000000-0000-0000-0000-000000000000";
    private static final String SECRET = "tracer+secret&=%";
    private static final String TOKEN_PATH = "/" + ZERO_GUID + "/oauth2/v2.0/token";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static void stubToken() {
        stubFor(post(urlEqualTo(TOKEN_PATH))
                .willReturn(okJson("{\"access_token\":\"tracer-token\",\"token_type\":\"Bearer\",\"expires_in\":3599}")));
    }

    private static EntraClientCredentials credentials(WireMockRuntimeInfo wm) {
        return EntraClientCredentials.withSecret(ZERO_GUID, ZERO_GUID, SECRET)
                .authority(URI.create(wm.getHttpBaseUrl()));
    }

    private static MailMessage textMail(String from) throws InvalidMailException {
        return Mail.using("main").from(from).to("reader@example.org").subject("Hello")
                .text("Plain text body").build().message();
    }

    @Test
    void textMailIsAcceptedThroughGraph(WireMockRuntimeInfo wm) throws IOException, MailException {
        stubToken();
        stubFor(post(urlEqualTo("/v1.0/users/newsletter%40example.com/sendMail"))
                .withHeader("Authorization", equalTo("Bearer tracer-token"))
                .willReturn(aResponse().withStatus(202)));
        MailTransport transport = new GraphTransportProvider()
                .create(Map.of("baseUrl", wm.getHttpBaseUrl() + "/v1.0"), credentials(wm));

        transport.send(textMail("newsletter@example.com"));

        verify(exactly(1), postRequestedFor(urlEqualTo(TOKEN_PATH)));
        LoggedRequest tokenRequest = findAll(postRequestedFor(urlEqualTo(TOKEN_PATH))).get(0);
        assertThat(tokenRequest.getHeader("Content-Type")).startsWith("application/x-www-form-urlencoded");
        Map<String, String> form = new LinkedHashMap<>();
        for (String pair : tokenRequest.getBodyAsString().split("&")) {
            int eq = pair.indexOf('=');
            form.put(URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8),
                    URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
        }
        assertThat(form).containsOnly(
                Map.entry("client_id", ZERO_GUID),
                Map.entry("scope", "https://graph.microsoft.com/.default"),
                Map.entry("client_secret", SECRET),
                Map.entry("grant_type", "client_credentials"));

        List<LoggedRequest> sends = findAll(postRequestedFor(
                urlEqualTo("/v1.0/users/newsletter%40example.com/sendMail")));
        assertThat(sends).hasSize(1);
        assertThat(sends.get(0).getHeader("Content-Type")).startsWith("application/json");
        JsonNode message = MAPPER.readTree(sends.get(0).getBody()).get("message");
        assertThat(message.get("subject").asText()).isEqualTo("Hello");
        assertThat(message.get("body").get("contentType").asText()).isEqualTo("Text");
        assertThat(message.get("body").get("content").asText()).isEqualTo("Plain text body");
        assertThat(message.get("toRecipients").get(0).get("emailAddress").get("address").asText())
                .isEqualTo("reader@example.org");
    }

    @Test
    void encodedSenderAndTrailingSlash(WireMockRuntimeInfo wm) throws MailException {
        stubToken();
        stubFor(post(urlEqualTo("/v1.0/users/news%2Btag%40example.com/sendMail"))
                .willReturn(aResponse().withStatus(202)));
        MailTransport transport = new GraphTransportProvider()
                .create(Map.of("baseUrl", wm.getHttpBaseUrl() + "/v1.0/"), credentials(wm));

        transport.send(textMail("news+tag@example.com"));

        List<LoggedRequest> sends = findAll(postRequestedFor(
                urlEqualTo("/v1.0/users/news%2Btag%40example.com/sendMail")));
        assertThat(sends).hasSize(1);
        assertThat(sends.get(0).getUrl()).doesNotContain("%25");
    }

    @Test
    void forbiddenMailboxIsSenderRejected(WireMockRuntimeInfo wm) {
        stubToken();
        stubFor(post(urlEqualTo("/v1.0/users/newsletter%40example.com/sendMail"))
                .willReturn(aResponse().withStatus(403)));
        MailTransport transport = new GraphTransportProvider()
                .create(Map.of("baseUrl", wm.getHttpBaseUrl() + "/v1.0"), credentials(wm));

        assertThatThrownBy(() -> transport.send(textMail("newsletter@example.com")))
                .isExactlyInstanceOf(SenderRejectedException.class)
                .hasMessageContaining("HTTP 403");
    }
}
