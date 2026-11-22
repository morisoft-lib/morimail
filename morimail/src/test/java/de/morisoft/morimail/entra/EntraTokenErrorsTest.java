package de.morisoft.morimail.entra;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.findAll;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.removeStub;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import de.morisoft.morimail.AuthRequiredException;
import de.morisoft.morimail.MailException;
import de.morisoft.morimail.TransientMailException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

@WireMockTest
class EntraTokenErrorsTest {

    private static final String ZERO_GUID = "00000000-0000-0000-0000-000000000000";
    private static final String SCOPE = "https://graph.microsoft.com/.default";
    private static final String TOKEN_PATH = "/" + ZERO_GUID + "/oauth2/v2.0/token";
    private static final Path KEYSTORE = Path.of("src/test/resources/test-keystore-throwaway.p12");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    private static final String OK = "{\"access_token\":\"good-token\",\"token_type\":\"Bearer\",\"expires_in\":3599}";

    private static EntraClientCredentials secret(WireMockRuntimeInfo wm) {
        return secret(wm, "made-up-secret", Duration.ofSeconds(5));
    }

    private static EntraClientCredentials secret(WireMockRuntimeInfo wm, String secret, Duration timeout) {
        return EntraClientCredentials.withSecret(ZERO_GUID, ZERO_GUID, secret)
                .authority(URI.create(wm.getHttpBaseUrl()))
                .forTest(CLOCK, timeout);
    }

    private static StubMapping stubStatus(int status, String body) {
        return stubFor(post(urlEqualTo(TOKEN_PATH)).willReturn(aResponse().withStatus(status).withBody(body)));
    }

    private static Path copyKeyStore(Path dir) throws IOException {
        return Files.copy(KEYSTORE, dir.resolve("copy.p12"));
    }

    @Test
    void invalidClientIsAuthRequiredWithTheCleanedError(WireMockRuntimeInfo wm) {
        stubStatus(400, "{\"error\":\"invalid_client\",\"error_description\":\"AADSTS7000215: Invalid client "
                + "secret provided.\\r\\nTrace ID: 00000000-0000-0000-0000-000000000000\\r\\n"
                + "Correlation ID: 00000000-0000-0000-0000-000000000000\"}");

        assertThatThrownBy(() -> secret(wm).accessToken(SCOPE))
                .isExactlyInstanceOf(AuthRequiredException.class)
                .hasMessageContaining("HTTP 400")
                .hasMessageContaining("error invalid_client")
                .hasMessageContaining("AADSTS7000215")
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("\r").doesNotContain("\n"));
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403, 404})
    void otherClientErrorsAreAuthRequired(int status, WireMockRuntimeInfo wm) {
        stubStatus(status, "<html>no json</html>");

        assertThatThrownBy(() -> secret(wm).accessToken(SCOPE))
                .isExactlyInstanceOf(AuthRequiredException.class)
                .hasMessageContaining("HTTP " + status)
                .hasMessageContaining("error -")
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("html"));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', nullValues = "-", value = {
        "429|7|PT7S",
        "429|Thu, 01 Jan 2026 00:02:00 GMT|-",
        "429|soon|-",
        "503|30|PT30S",
        "500|-|-"})
    void transientStatusesCarryRetryAfter(int status, String header, String expected, WireMockRuntimeInfo wm) {
        stubFor(post(urlEqualTo(TOKEN_PATH)).willReturn(header == null
                ? aResponse().withStatus(status)
                : aResponse().withStatus(status).withHeader("Retry-After", header)));

        assertThatThrownBy(() -> secret(wm).accessToken(SCOPE))
                .isExactlyInstanceOf(TransientMailException.class)
                .hasMessageContaining("HTTP " + status)
                .satisfies(e -> assertThat(((TransientMailException) e).retryAfter())
                        .isEqualTo(expected == null ? java.util.Optional.empty()
                                : java.util.Optional.of(Duration.parse(expected))));
    }

    @Test
    void redirectIsNotFollowed(WireMockRuntimeInfo wm) {
        stubFor(post(urlEqualTo(TOKEN_PATH)).willReturn(aResponse().withStatus(302)
                .withHeader("Location", wm.getHttpBaseUrl() + "/elsewhere")));
        stubFor(get(urlEqualTo("/elsewhere")).willReturn(okJson(OK)));
        stubFor(post(urlEqualTo("/elsewhere")).willReturn(okJson(OK)));

        assertThatThrownBy(() -> secret(wm).accessToken(SCOPE))
                .isExactlyInstanceOf(AuthRequiredException.class)
                .hasMessageContaining("HTTP 302");
        assertThat(findAll(getRequestedFor(urlEqualTo("/elsewhere")))).isEmpty();
        assertThat(findAll(postRequestedFor(urlEqualTo("/elsewhere")))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "not json",
        "{\"token_type\":\"Bearer\",\"expires_in\":3599}",
        "{\"access_token\":\"x\",\"token_type\":\"Bearer\"}",
        "{\"access_token\":\"x\",\"expires_in\":0}",
        "{\"access_token\":\"x\",\"expires_in\":\"3599\"}"})
    void anUnusableSuccessIsAuthRequired(String body, WireMockRuntimeInfo wm) {
        stubStatus(200, body);

        assertThatThrownBy(() -> secret(wm).accessToken(SCOPE))
                .isExactlyInstanceOf(AuthRequiredException.class)
                .hasMessageContaining("not usable");
    }

    @ParameterizedTest
    @ValueSource(strings = {"secret token", "secret\\r\\nInjected: x", "secret\\u00e4"})
    void anAccessTokenThatIsNoHeaderValueIsAuthRequiredAndNeverQuoted(String token, WireMockRuntimeInfo wm) {
        stubStatus(200, "{\"access_token\":\"" + token + "\",\"expires_in\":3599}");

        assertThatThrownBy(() -> secret(wm).accessToken(SCOPE))
                .isExactlyInstanceOf(AuthRequiredException.class)
                .hasMessageContaining("not a valid header value")
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("secret"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://login.example.com", "ftp://localhost", "https:///x", "login.example.com"})
    void anAuthorityThatIsNeitherHttpsNorLoopbackHttpIsRejected(String authority) {
        assertThatThrownBy(() -> EntraClientCredentials.withSecret(ZERO_GUID, ZERO_GUID, "made-up-secret")
                .authority(URI.create(authority)))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("authority must be an absolute https URI (http only for localhost)");
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://login.example.com/", "http://localhost:8080", "http://127.0.0.1:8080",
        "http://[::1]:8080"})
    void httpsAndLoopbackHttpAuthoritiesAreAccepted(String authority) {
        assertThat(EntraClientCredentials.withSecret(ZERO_GUID, ZERO_GUID, "made-up-secret")
                .authority(URI.create(authority))).isNotNull();
    }

    @Test
    void aMailAddressInTheDescriptionIsReplaced(WireMockRuntimeInfo wm) {
        stubStatus(400, "{\"error\":\"invalid_request\",\"error_description\":\"No user x@example.org here\"}");

        assertThatThrownBy(() -> secret(wm).accessToken(SCOPE))
                .isExactlyInstanceOf(AuthRequiredException.class)
                .hasMessageContaining("description: No user <address> here")
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("example.org"));
    }

    @Test
    void aLongDescriptionIsCutToThreeHundredCharacters(WireMockRuntimeInfo wm) {
        stubStatus(400, "{\"error\":\"invalid_request\",\"error_description\":\"" + "x".repeat(1000) + "\"}");

        assertThatThrownBy(() -> secret(wm).accessToken(SCOPE))
                .isExactlyInstanceOf(AuthRequiredException.class)
                .satisfies(e -> {
                    String message = e.getMessage();
                    assertThat(message).contains("description: " + "x".repeat(300) + "...");
                    assertThat(message).doesNotContain("x".repeat(301));
                });
    }

    // A single word longer than the cut has no word boundary, so none of it is kept (review WR-01).
    @Test
    @Timeout(10)
    void aVeryLongDescriptionIsCutAndCleanedQuickly(WireMockRuntimeInfo wm) {
        stubStatus(400, "{\"error\":\"invalid_request\",\"error_description\":\"" + "x".repeat(100_000) + "\"}");

        assertThatThrownBy(() -> secret(wm).accessToken(SCOPE))
                .isExactlyInstanceOf(AuthRequiredException.class)
                .satisfies(e -> assertThat(e.getMessage()).endsWith("description: -"));
    }

    @Test
    void anAddressAfterALongWhitespaceRunLeavesNoPartOfItInTheMessage(WireMockRuntimeInfo wm) {
        stubStatus(400, "{\"error\":\"invalid_request\",\"error_description\":\"" + " ".repeat(995)
                + "alice@example.org\"}");

        assertThatThrownBy(() -> secret(wm).accessToken(SCOPE))
                .isExactlyInstanceOf(AuthRequiredException.class)
                .satisfies(e -> assertThat(e.getMessage()).endsWith("description: <address>"));
    }

    // The first address ends at character 991 and the second starts at 993, so the cut at 1000 falls inside it (review WR-01).
    @Test
    void anAddressStraddlingTheCutLeavesNoPartOfItInTheMessage(WireMockRuntimeInfo wm) {
        stubStatus(400, "{\"error\":\"invalid_request\",\"error_description\":\"" + "x".repeat(980) + "@example.org "
                + "alice".repeat(5) + "@example.org\"}");

        assertThatThrownBy(() -> secret(wm).accessToken(SCOPE))
                .isExactlyInstanceOf(AuthRequiredException.class)
                .satisfies(e -> assertThat(e.getMessage()).endsWith("description: <address>").doesNotContain("alice"));
    }

    @Test
    void aConnectionResetIsTransientWithTheIoCause(WireMockRuntimeInfo wm) {
        stubFor(post(urlEqualTo(TOKEN_PATH)).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        assertThatThrownBy(() -> secret(wm).accessToken(SCOPE))
                .isExactlyInstanceOf(TransientMailException.class)
                .hasCauseInstanceOf(IOException.class)
                .satisfies(e -> assertThat(e.getMessage())
                        .isEqualTo("Entra token request failed: no response (" + e.getCause().getClass().getSimpleName() + ")"));
    }

    @Test
    void aSlowResponseTimesOutAndTheLockIsReleased(WireMockRuntimeInfo wm) throws MailException {
        StubMapping slow = stubFor(post(urlEqualTo(TOKEN_PATH)).willReturn(okJson(OK).withFixedDelay(2000)));
        EntraClientCredentials credentials = secret(wm, "made-up-secret", Duration.ofMillis(500));

        assertThatThrownBy(() -> credentials.accessToken(SCOPE))
                .isExactlyInstanceOf(TransientMailException.class)
                .hasCauseInstanceOf(HttpTimeoutException.class);

        removeStub(slow);
        stubStatus(200, OK);
        assertThat(credentials.accessToken(SCOPE)).isEqualTo("good-token");
    }

    @Test
    void anInterruptedThreadGetsATransientErrorAndKeepsItsFlag(WireMockRuntimeInfo wm) throws MailException {
        stubStatus(200, OK);
        EntraClientCredentials credentials = secret(wm);

        Thread.currentThread().interrupt();
        try {
            assertThatThrownBy(() -> credentials.accessToken(SCOPE))
                    .isExactlyInstanceOf(TransientMailException.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }

        assertThat(credentials.accessToken(SCOPE)).isEqualTo("good-token");
        assertThat(findAll(postRequestedFor(urlEqualTo(TOKEN_PATH)))).hasSize(1);
    }

    @Test
    void failuresAreNeitherCachedNorShared(WireMockRuntimeInfo wm) throws Exception {
        stubFor(post(urlEqualTo(TOKEN_PATH)).willReturn(aResponse().withStatus(503).withFixedDelay(100)));
        EntraClientCredentials credentials = secret(wm);
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            CountDownLatch go = new CountDownLatch(1);
            List<Future<Throwable>> futures = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                futures.add(pool.submit(() -> {
                    go.await();
                    try {
                        credentials.accessToken(SCOPE);
                        return null;
                    } catch (MailException e) {
                        return e;
                    }
                }));
            }
            go.countDown();
            List<Throwable> failures = new ArrayList<>();
            for (Future<Throwable> future : futures) {
                failures.add(future.get(20, TimeUnit.SECONDS));
            }
            assertThat(failures).hasSize(4).allSatisfy(e -> assertThat(e).isExactlyInstanceOf(TransientMailException.class));
            assertThat(failures).doesNotHaveDuplicates();
        } finally {
            pool.shutdownNow();
        }
        assertThat(findAll(postRequestedFor(urlEqualTo(TOKEN_PATH)))).hasSize(4);
    }

    @Test
    void aMissingKeyStoreNamesTheFile() {
        Path missing = Path.of("no-such-dir", "missing.p12");

        assertThatThrownBy(() -> EntraClientCredentials.withCertificate(ZERO_GUID, ZERO_GUID, missing,
                "changeit".toCharArray()))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing.p12")
                .hasCauseInstanceOf(NoSuchFileException.class);
    }

    @Test
    void aWrongPasswordNeverAppearsInTheMessage() {
        assertThatThrownBy(() -> EntraClientCredentials.withCertificate(ZERO_GUID, ZERO_GUID, KEYSTORE,
                "Sentinel-pw-7f3a".toCharArray()))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("test-keystore-throwaway.p12")
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("Sentinel-pw-7f3a"));
    }

    @Test
    void twoKeyEntriesAreRejected(@TempDir Path dir) throws Exception {
        KeyStore store = KeyStore.getInstance("PKCS12");
        char[] password = "changeit".toCharArray();
        try (var in = Files.newInputStream(KEYSTORE)) {
            store.load(in, password);
        }
        store.setKeyEntry("second", store.getKey("test", password), password, store.getCertificateChain("test"));
        Path two = dir.resolve("two.p12");
        try (var out = Files.newOutputStream(two)) {
            store.store(out, password);
        }

        assertThatThrownBy(() -> EntraClientCredentials.withCertificate(ZERO_GUID, ZERO_GUID, two, password))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("found 2");
    }

    @Test
    void aStoreWithOnlyATrustedCertificateIsRejected(@TempDir Path dir) throws Exception {
        KeyStore source = KeyStore.getInstance("PKCS12");
        char[] password = "changeit".toCharArray();
        try (var in = Files.newInputStream(KEYSTORE)) {
            source.load(in, password);
        }
        KeyStore store = KeyStore.getInstance("PKCS12");
        store.load(null, null);
        store.setCertificateEntry("trusted", source.getCertificate("test"));
        Path none = dir.resolve("none.p12");
        try (var out = Files.newOutputStream(none)) {
            store.store(out, password);
        }

        assertThatThrownBy(() -> EntraClientCredentials.withCertificate(ZERO_GUID, ZERO_GUID, none, password))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("found 0");
    }

    @Test
    void aNonRsaKeyIsRejected() throws Exception {
        KeyStore store = KeyStore.getInstance("PKCS12");
        try (var in = Files.newInputStream(KEYSTORE)) {
            store.load(in, "changeit".toCharArray());
        }
        X509Certificate cert = (X509Certificate) store.getCertificate("test");
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(256);
        KeyPair ec = generator.generateKeyPair();
        PrivateKey key = ec.getPrivate();

        assertThatThrownBy(() -> EntraClientCredentials.withCertificate(ZERO_GUID, ZERO_GUID, key, cert))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("RSA");
    }

    @Test
    void theFileIsReadOnlyOnceAndLoadingIsConcurrent(@TempDir Path dir, WireMockRuntimeInfo wm) throws Exception {
        stubStatus(200, OK);
        Path copy = copyKeyStore(dir);
        char[] password = "changeit".toCharArray();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<EntraClientCredentials> loaded = new ArrayList<>();
        try {
            CountDownLatch go = new CountDownLatch(1);
            List<Future<EntraClientCredentials>> futures = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                futures.add(pool.submit(() -> {
                    go.await();
                    return EntraClientCredentials.withCertificate(ZERO_GUID, ZERO_GUID, copy, password);
                }));
            }
            go.countDown();
            for (Future<EntraClientCredentials> future : futures) {
                loaded.add(future.get(20, TimeUnit.SECONDS));
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(password).isEqualTo("changeit".toCharArray());
        Files.delete(copy);

        for (EntraClientCredentials credentials : loaded) {
            assertThat(credentials.authority(URI.create(wm.getHttpBaseUrl())).forTest(CLOCK, Duration.ofSeconds(5))
                    .accessToken(SCOPE)).isEqualTo("good-token");
        }
    }

    @Test
    void nothingSecretReachesALogOrAMessage(WireMockRuntimeInfo wm) throws Exception {
        // HttpClient and WireMock threads publish to the root logger while this thread reads, so both lists must be thread-safe (UAT G-03-4).
        List<String> seen = new CopyOnWriteArrayList<>();
        List<String> loggers = new CopyOnWriteArrayList<>();
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                loggers.add(String.valueOf(record.getLoggerName()));
                StringBuilder text = new StringBuilder(String.valueOf(record.getMessage()));
                if (record.getParameters() != null) {
                    for (Object parameter : record.getParameters()) {
                        text.append(' ').append(parameter);
                    }
                }
                if (record.getThrown() != null) {
                    text.append(' ').append(record.getThrown().getMessage());
                }
                seen.add(text.toString());
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        Logger root = Logger.getLogger("");
        Level before = root.getLevel();
        root.setLevel(Level.ALL);
        root.addHandler(handler);
        try {
            String secret = "Sentinel-secret-7f3a";
            String token = "Sentinel-token-7f3a";
            String tokenBody = "{\"access_token\":\"" + token + "\",\"expires_in\":3599}";
            stubStatus(200, tokenBody);
            secret(wm, secret, Duration.ofSeconds(5)).accessToken(SCOPE);
            EntraClientCredentials certificate = EntraClientCredentials
                    .withCertificate(ZERO_GUID, ZERO_GUID, KEYSTORE, "changeit".toCharArray())
                    .authority(URI.create(wm.getHttpBaseUrl())).forTest(CLOCK, Duration.ofSeconds(5));
            certificate.accessToken(SCOPE);
            String assertion = EntraClientCredentialsTest
                    .form(findAll(postRequestedFor(urlEqualTo(TOKEN_PATH))).get(1)).get("client_assertion");

            List<String> messages = new ArrayList<>();
            stubStatus(400, "{\"error\":\"invalid_client\",\"error_description\":\"bad\"}");
            try {
                secret(wm, secret, Duration.ofSeconds(5)).accessToken(SCOPE);
            } catch (MailException e) {
                messages.add(e.getMessage());
            }
            stubStatus(503, "");
            try {
                certificate.freshAccessToken(SCOPE, token);
            } catch (MailException e) {
                messages.add(e.getMessage());
            }

            assertThat(messages).hasSize(2);
            for (String text : seen) {
                assertThat(text).doesNotContain(secret).doesNotContain(token).doesNotContain(assertion);
            }
            for (String text : messages) {
                assertThat(text).doesNotContain(secret).doesNotContain(token).doesNotContain(assertion);
            }
            assertThat(loggers).noneMatch(name -> name.startsWith("de.morisoft.morimail.entra"));
        } finally {
            root.removeHandler(handler);
            root.setLevel(before);
        }
    }
}
