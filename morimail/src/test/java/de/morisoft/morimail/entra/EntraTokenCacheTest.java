package de.morisoft.morimail.entra;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.findAll;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import de.morisoft.morimail.MailException;
import de.morisoft.morimail.TransientMailException;
import java.net.URI;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

@WireMockTest
class EntraTokenCacheTest {

    private static final String ZERO_GUID = "00000000-0000-0000-0000-000000000000";
    private static final String SCOPE = "https://graph.microsoft.com/.default";
    private static final String TOKEN_PATH = "/" + ZERO_GUID + "/oauth2/v2.0/token";
    private static final Path KEYSTORE = Path.of("src/test/resources/test-keystore-throwaway.p12");
    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** A clock the test moves by hand. */
    private static final class MutableClock extends Clock {
        private volatile Instant now = START;

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private static String body(String token, String expiresIn) {
        return "{\"access_token\":\"" + token + "\",\"token_type\":\"Bearer\",\"expires_in\":" + expiresIn + "}";
    }

    private static void stubToken(String token, String expiresIn) {
        stubFor(post(urlEqualTo(TOKEN_PATH)).willReturn(okJson(body(token, expiresIn))));
    }

    private static EntraClientCredentials secret(WireMockRuntimeInfo wm, Clock clock) {
        return EntraClientCredentials.withSecret(ZERO_GUID, ZERO_GUID, "made-up-secret")
                .authority(URI.create(wm.getHttpBaseUrl()))
                .forTest(clock, Duration.ofSeconds(5));
    }

    private static int requests() {
        return findAll(postRequestedFor(urlEqualTo(TOKEN_PATH))).size();
    }

    /** Answers t1 on the first request and t2 on every later one. */
    private static void stubTwoTokens(int delayMillis) {
        stubFor(post(urlEqualTo(TOKEN_PATH)).inScenario("tokens").whenScenarioStateIs(Scenario.STARTED)
                .willSetStateTo("second")
                .willReturn(okJson(body("t1", "3599")).withFixedDelay(delayMillis)));
        stubFor(post(urlEqualTo(TOKEN_PATH)).inScenario("tokens").whenScenarioStateIs("second")
                .willReturn(okJson(body("t2", "3599")).withFixedDelay(delayMillis)));
    }

    private static <T> List<T> runTogether(int threads, Callable<T> call) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch ready = new CountDownLatch(threads);
            CountDownLatch go = new CountDownLatch(1);
            List<Future<T>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return call.call();
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(20, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void tokenIsReusedWithinItsLifetime(WireMockRuntimeInfo wm) throws MailException {
        stubToken("cached-token", "3599");
        EntraClientCredentials credentials = secret(wm, new MutableClock());

        assertThat(credentials.accessToken(SCOPE)).isEqualTo("cached-token");
        assertThat(credentials.accessToken(SCOPE)).isEqualTo("cached-token");

        assertThat(requests()).isEqualTo(1);
    }

    @Test
    void tokenExpiresFiveMinutesBeforeTheEnd(WireMockRuntimeInfo wm) throws MailException {
        stubToken("cached-token", "3600");
        MutableClock clock = new MutableClock();
        EntraClientCredentials credentials = secret(wm, clock);

        credentials.accessToken(SCOPE);
        clock.advance(Duration.ofSeconds(3299));
        credentials.accessToken(SCOPE);
        assertThat(requests()).isEqualTo(1);

        clock.advance(Duration.ofSeconds(1));
        credentials.accessToken(SCOPE);
        assertThat(requests()).isEqualTo(2);
    }

    @Test
    void shortLivedTokenIsNeverReused(WireMockRuntimeInfo wm) throws MailException {
        stubToken("short", "300");
        EntraClientCredentials credentials = secret(wm, new MutableClock());

        credentials.accessToken(SCOPE);
        credentials.accessToken(SCOPE);
        credentials.accessToken(SCOPE);

        assertThat(requests()).isEqualTo(3);
    }

    @Test
    void tokenOneSecondAboveTheMarginIsReusedForOneSecond(WireMockRuntimeInfo wm) throws MailException {
        stubToken("edge", "301");
        MutableClock clock = new MutableClock();
        EntraClientCredentials credentials = secret(wm, clock);

        credentials.accessToken(SCOPE);
        credentials.accessToken(SCOPE);
        assertThat(requests()).isEqualTo(1);

        clock.advance(Duration.ofSeconds(1));
        credentials.accessToken(SCOPE);
        assertThat(requests()).isEqualTo(2);
    }

    @Test
    void expiresInAsStringIsCached(WireMockRuntimeInfo wm) throws MailException {
        stubToken("string-ttl", "\"3599\"");
        EntraClientCredentials credentials = secret(wm, new MutableClock());

        credentials.accessToken(SCOPE);
        credentials.accessToken(SCOPE);

        assertThat(requests()).isEqualTo(1);
    }

    @Test
    void eightThreadsOnAnEmptyCacheMakeOneRequest(WireMockRuntimeInfo wm) throws Exception {
        stubFor(post(urlEqualTo(TOKEN_PATH)).willReturn(okJson(body("shared", "3599")).withFixedDelay(300)));
        EntraClientCredentials credentials = secret(wm, new MutableClock());

        List<String> tokens = runTogether(8, () -> credentials.accessToken(SCOPE));

        assertThat(tokens).hasSize(8).containsOnly("shared");
        assertThat(requests()).isEqualTo(1);
    }

    @Test
    void freshAccessTokenDropsTheRejectedToken(WireMockRuntimeInfo wm) throws MailException {
        stubTwoTokens(0);
        EntraClientCredentials credentials = secret(wm, new MutableClock());

        assertThat(credentials.accessToken(SCOPE)).isEqualTo("t1");
        assertThat(credentials.freshAccessToken(SCOPE, "t1")).isEqualTo("t2");
        assertThat(credentials.accessToken(SCOPE)).isEqualTo("t2");

        assertThat(requests()).isEqualTo(2);
    }

    @Test
    void freshAccessTokenKeepsAnotherToken(WireMockRuntimeInfo wm) throws MailException {
        stubToken("t1", "3599");
        EntraClientCredentials credentials = secret(wm, new MutableClock());
        credentials.accessToken(SCOPE);

        assertThat(credentials.freshAccessToken(SCOPE, "old-token")).isEqualTo("t1");

        assertThat(requests()).isEqualTo(1);
    }

    @Test
    void twoThreadsRejectingTheSameTokenCauseOneNewRequest(WireMockRuntimeInfo wm) throws Exception {
        stubTwoTokens(300);
        EntraClientCredentials credentials = secret(wm, new MutableClock());
        assertThat(credentials.accessToken(SCOPE)).isEqualTo("t1");

        List<String> tokens = runTogether(2, () -> credentials.freshAccessToken(SCOPE, "t1"));

        assertThat(tokens).containsOnly("t2");
        assertThat(requests()).isEqualTo(2);
    }

    @Test
    void aFailedRequestIsNotCached(WireMockRuntimeInfo wm) throws MailException {
        stubFor(post(urlEqualTo(TOKEN_PATH)).inScenario("flaky").whenScenarioStateIs(Scenario.STARTED)
                .willSetStateTo("up").willReturn(aResponse().withStatus(503)));
        stubFor(post(urlEqualTo(TOKEN_PATH)).inScenario("flaky").whenScenarioStateIs("up")
                .willReturn(okJson(body("recovered", "3599"))));
        EntraClientCredentials credentials = secret(wm, new MutableClock());

        assertThatThrownBy(() -> credentials.accessToken(SCOPE)).isInstanceOf(TransientMailException.class);
        assertThat(credentials.accessToken(SCOPE)).isEqualTo("recovered");

        assertThat(requests()).isEqualTo(2);
    }

    @Test
    void anotherScopeFetchesAndReplaces(WireMockRuntimeInfo wm) throws Exception {
        stubToken("scoped", "3599");
        EntraClientCredentials credentials = secret(wm, new MutableClock());

        credentials.accessToken("scope-a");
        credentials.accessToken("scope-b");
        assertThat(requests()).isEqualTo(2);
        List<LoggedRequest> sent = findAll(postRequestedFor(urlEqualTo(TOKEN_PATH)));
        assertThat(EntraClientCredentialsTest.form(sent.get(1)).get("scope")).isEqualTo("scope-b");

        credentials.accessToken("scope-b");
        assertThat(requests()).isEqualTo(2);
    }

    @Test
    void certificateFlowBuildsANewAssertionForEveryRequest(WireMockRuntimeInfo wm) throws Exception {
        stubToken("cert-token", "3599");
        EntraClientCredentials credentials = EntraClientCredentials
                .withCertificate(ZERO_GUID, ZERO_GUID, KEYSTORE, "changeit".toCharArray())
                .authority(URI.create(wm.getHttpBaseUrl()))
                .forTest(new MutableClock(), Duration.ofSeconds(5));

        String first = credentials.accessToken(SCOPE);
        credentials.freshAccessToken(SCOPE, first);

        List<LoggedRequest> sent = findAll(postRequestedFor(urlEqualTo(TOKEN_PATH)));
        assertThat(sent).hasSize(2);
        List<String> jtis = new ArrayList<>();
        for (LoggedRequest request : sent) {
            String assertion = EntraClientCredentialsTest.form(request).get("client_assertion");
            jtis.add(MAPPER.readTree(Base64.getUrlDecoder().decode(assertion.split("\\.")[1]))
                    .get("jti").asText());
        }
        assertThat(jtis.get(0)).isNotEqualTo(jtis.get(1));
    }
}
