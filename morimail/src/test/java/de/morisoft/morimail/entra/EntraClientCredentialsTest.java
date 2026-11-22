package de.morisoft.morimail.entra;

import static com.github.tomakehurst.wiremock.client.WireMock.findAll;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.io.InputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.cert.X509Certificate;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PSSParameterSpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@WireMockTest
class EntraClientCredentialsTest {

    private static final String ZERO_GUID = "00000000-0000-0000-0000-000000000000";
    private static final String SCOPE = "https://graph.microsoft.com/.default";
    private static final Path KEYSTORE = Path.of("src/test/resources/test-keystore-throwaway.p12");
    private static final String TOKEN_PATH = "/" + ZERO_GUID + "/oauth2/v2.0/token";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static PrivateKey key;
    private static X509Certificate cert;

    @BeforeAll
    static void loadKeyStore() throws Exception {
        KeyStore store = KeyStore.getInstance("PKCS12");
        try (InputStream in = Files.newInputStream(KEYSTORE)) {
            store.load(in, "changeit".toCharArray());
        }
        key = (PrivateKey) store.getKey("test", "changeit".toCharArray());
        cert = (X509Certificate) store.getCertificate("test");
    }

    private static void stubToken() {
        stubFor(post(urlEqualTo(TOKEN_PATH))
                .willReturn(okJson("{\"access_token\":\"cert-token\",\"token_type\":\"Bearer\",\"expires_in\":3599}")));
    }

    private static EntraClientCredentials fromFile(WireMockRuntimeInfo wm) {
        return EntraClientCredentials.withCertificate(ZERO_GUID, ZERO_GUID, KEYSTORE, "changeit".toCharArray())
                .authority(URI.create(wm.getHttpBaseUrl()))
                .forTest(CLOCK, Duration.ofSeconds(5));
    }

    static Map<String, String> form(LoggedRequest request) {
        Map<String, String> form = new LinkedHashMap<>();
        for (String pair : request.getBodyAsString().split("&")) {
            int eq = pair.indexOf('=');
            form.put(URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8),
                    URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
        }
        return form;
    }

    private static List<String> assertions() {
        List<String> found = new ArrayList<>();
        for (LoggedRequest request : findAll(postRequestedFor(urlEqualTo(TOKEN_PATH)))) {
            found.add(form(request).get("client_assertion"));
        }
        return found;
    }

    private static boolean verifies(String signingInput, String signature) throws Exception {
        Signature verifier = Signature.getInstance("RSASSA-PSS");
        verifier.setParameter(new PSSParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, 32, 1));
        verifier.initVerify(cert.getPublicKey());
        verifier.update(signingInput.getBytes(StandardCharsets.US_ASCII));
        return verifier.verify(Base64.getUrlDecoder().decode(signature));
    }

    @Test
    void certificateFlowFromPkcs12GetsAToken(WireMockRuntimeInfo wm) throws Exception {
        stubToken();

        assertThat(fromFile(wm).accessToken(SCOPE)).isEqualTo("cert-token");

        List<LoggedRequest> requests = findAll(postRequestedFor(urlEqualTo(TOKEN_PATH)));
        assertThat(requests).hasSize(1);
        Map<String, String> form = form(requests.get(0));
        assertThat(form).containsOnlyKeys("client_id", "scope", "client_assertion_type",
                "client_assertion", "grant_type");
        assertThat(form.get("client_assertion_type"))
                .isEqualTo("urn:ietf:params:oauth:client-assertion-type:jwt-bearer");
        assertThat(form.get("grant_type")).isEqualTo("client_credentials");
        assertThat(form.get("client_id")).isEqualTo(ZERO_GUID);
        assertThat(form.get("scope")).isEqualTo(SCOPE);
    }

    @Test
    void assertionHeaderClaimsAndSignature(WireMockRuntimeInfo wm) throws Exception {
        stubToken();
        fromFile(wm).accessToken(SCOPE);

        String assertion = assertions().get(0);
        assertThat(assertion).doesNotContain("=");
        String[] parts = assertion.split("\\.");
        assertThat(parts).hasSize(3);
        JsonNode header = MAPPER.readTree(Base64.getUrlDecoder().decode(parts[0]));
        JsonNode claims = MAPPER.readTree(Base64.getUrlDecoder().decode(parts[1]));

        String thumbprint = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(MessageDigest.getInstance("SHA-256").digest(cert.getEncoded()));
        assertThat(header.size()).isEqualTo(3);
        assertThat(header.get("alg").asText()).isEqualTo("PS256");
        assertThat(header.get("typ").asText()).isEqualTo("JWT");
        assertThat(header.get("x5t#S256").asText()).isEqualTo(thumbprint);

        assertThat(claims.size()).isEqualTo(7);
        assertThat(claims.get("aud").asText()).isEqualTo(wm.getHttpBaseUrl() + TOKEN_PATH);
        assertThat(claims.get("iss").asText()).isEqualTo(ZERO_GUID);
        assertThat(claims.get("sub").asText()).isEqualTo(ZERO_GUID);
        UUID.fromString(claims.get("jti").asText());
        assertThat(claims.get("nbf").asLong()).isEqualTo(1767225600L);
        assertThat(claims.get("iat").asLong()).isEqualTo(1767225600L);
        assertThat(claims.get("exp").asLong()).isEqualTo(1767226200L);

        String signingInput = parts[0] + "." + parts[1];
        assertThat(verifies(signingInput, parts[2])).isTrue();
        String changed = (signingInput.charAt(0) == 'e' ? "f" : "e") + signingInput.substring(1);
        assertThat(verifies(changed, parts[2])).isFalse();
    }

    @Test
    void keyAndCertificateFactoryGetsAToken(WireMockRuntimeInfo wm) throws Exception {
        stubToken();
        EntraClientCredentials credentials = EntraClientCredentials.withCertificate(ZERO_GUID, ZERO_GUID, key, cert)
                .authority(URI.create(wm.getHttpBaseUrl()));

        assertThat(credentials.accessToken(SCOPE)).isEqualTo("cert-token");
    }

    @Test
    void twoInstancesSendDifferentAssertions(WireMockRuntimeInfo wm) throws Exception {
        stubToken();
        fromFile(wm).accessToken(SCOPE);
        fromFile(wm).accessToken(SCOPE);

        List<String> assertions = assertions();
        assertThat(assertions).hasSize(2);
        List<String> jtis = new ArrayList<>();
        for (String assertion : assertions) {
            String[] parts = assertion.split("\\.");
            jtis.add(MAPPER.readTree(Base64.getUrlDecoder().decode(parts[1])).get("jti").asText());
            assertThat(verifies(parts[0] + "." + parts[1], parts[2])).isTrue();
        }
        assertThat(jtis.get(0)).isNotEqualTo(jtis.get(1));
    }
}
