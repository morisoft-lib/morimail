package de.morisoft.morimail.graph;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.findAll;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import de.morisoft.morimail.AuthRequiredException;
import de.morisoft.morimail.InvalidMailException;
import de.morisoft.morimail.Mail;
import de.morisoft.morimail.MailException;
import de.morisoft.morimail.MailMessage;
import de.morisoft.morimail.MailTransport;
import de.morisoft.morimail.PermanentMailException;
import de.morisoft.morimail.SenderRejectedException;
import de.morisoft.morimail.TokenProvider;
import de.morisoft.morimail.TransientMailException;
import java.io.IOException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Stream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@WireMockTest
class GraphErrorMappingTest {

    private static final String SEND_PATH = "/v1.0/users/newsletter%40example.com/sendMail";
    private static final String ZERO_ID = "00000000-0000-0000-0000-000000000000";

    private final StubTokenProvider stub = new StubTokenProvider("t1", "t2");

    private static MailTransport transport(WireMockRuntimeInfo wm, TokenProvider auth) {
        return new GraphTransportProvider().create(Map.of("baseUrl", wm.getHttpBaseUrl() + "/v1.0"), auth);
    }

    private MailTransport transport(WireMockRuntimeInfo wm) {
        return transport(wm, stub);
    }

    private static MailMessage mail() throws InvalidMailException {
        return Mail.using("main").from("newsletter@example.com").to("reader@example.org")
                .subject("Hello").text("Plain").build().message();
    }

    private static String errorBody(String code, String message) {
        return "{\"error\":{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}}";
    }

    /** Answers a POST that carries the given bearer token. */
    private static void stubBearer(String bearer, int status, String body) {
        stubFor(post(urlEqualTo(SEND_PATH)).withHeader("Authorization", equalTo("Bearer " + bearer))
                .willReturn(aResponse().withStatus(status).withBody(body)));
    }

    private static List<LoggedRequest> sends() {
        return findAll(postRequestedFor(urlEqualTo(SEND_PATH)));
    }

    @Test
    void a401IsRetriedOnceWithTheFreshTokenAndTheSameBody(WireMockRuntimeInfo wm) throws Exception {
        stubBearer("t1", 401, errorBody("InvalidAuthenticationToken", "Expired"));
        stubBearer("t2", 202, "");

        transport(wm).send(mail());

        List<LoggedRequest> sent = sends();
        assertThat(sent).hasSize(2);
        assertThat(sent.get(0).getHeader("Authorization")).isEqualTo("Bearer t1");
        assertThat(sent.get(1).getHeader("Authorization")).isEqualTo("Bearer t2");
        assertThat(sent.get(1).getBody()).isEqualTo(sent.get(0).getBody());
        assertThat(stub.freshCalls()).isEqualTo(1);
        assertThat(stub.rejectedTokens()).containsExactly("t1");
        assertThat(stub.scopes()).containsOnly("https://graph.microsoft.com/.default");
    }

    @ParameterizedTest
    @MethodSource("secondUnauthorized")
    void aSecond401IsMappedByTheMailboxHint(String code, Class<? extends MailException> expected,
            WireMockRuntimeInfo wm) throws Exception {
        stubBearer("t1", 401, errorBody(code, "No"));
        stubBearer("t2", 401, errorBody(code, "No"));

        assertThatThrownBy(() -> transport(wm).send(mail())).isInstanceOf(expected);

        assertThat(sends()).hasSize(2);
    }

    static Stream<Arguments> secondUnauthorized() {
        return Stream.of(
                Arguments.of("InvalidAuthenticationToken", AuthRequiredException.class),
                Arguments.of("MailboxNotEnabledForRESTAPI", SenderRejectedException.class),
                Arguments.of("mailboxnotenabledforrestapi", SenderRejectedException.class));
    }

    @ParameterizedTest
    @MethodSource("answerAfter401")
    void theAnswerAfterTheRetryIsMappedLikeAnyOther(int status, Class<? extends MailException> expected,
            WireMockRuntimeInfo wm) throws Exception {
        stubBearer("t1", 401, errorBody("InvalidAuthenticationToken", "Expired"));
        stubBearer("t2", status, "");

        assertThatThrownBy(() -> transport(wm).send(mail())).isInstanceOf(expected);

        assertThat(sends()).hasSize(2);
    }

    static Stream<Arguments> answerAfter401() {
        return Stream.of(
                Arguments.of(403, SenderRejectedException.class),
                Arguments.of(503, TransientMailException.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"MailboxNotEnabledForRESTAPI", "ErrorMailboxNotFound", "MAILBOX"})
    void mailboxHintIsTrueWhenTheCodeNamesAMailbox(String code) {
        assertThat(GraphTransport.mailboxHint(code)).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"InvalidAuthenticationToken"})
    void mailboxHintIsFalseOtherwise(String code) {
        assertThat(GraphTransport.mailboxHint(code)).isFalse();
    }

    @Test
    void aFailingFreshTokenPropagatesAndSendsNothingMore(WireMockRuntimeInfo wm) throws Exception {
        stubBearer("t1", 401, errorBody("InvalidAuthenticationToken", "Expired"));
        stub.failFresh(new AuthRequiredException("the secret was refused"));

        assertThatThrownBy(() -> transport(wm).send(mail()))
                .isInstanceOf(AuthRequiredException.class)
                .hasMessage("the secret was refused");

        assertThat(sends()).hasSize(1);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void aProviderWithoutATokenSendsNothing(String token, WireMockRuntimeInfo wm) throws Exception {
        stubBearer("t1", 202, "");
        TokenProvider none = scope -> token;

        assertThatThrownBy(() -> transport(wm, none).send(mail()))
                .isInstanceOf(AuthRequiredException.class)
                .hasMessage("the token provider returned no access token");

        assertThat(sends()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"secret token", "secret\r\nInjected: x", "secret\u00e4"})
    void aTokenThatIsNoHeaderValueSendsNothingAndIsNeverQuoted(String token, WireMockRuntimeInfo wm)
            throws Exception {
        stubFor(post(urlEqualTo(SEND_PATH)).willReturn(aResponse().withStatus(202)));
        TokenProvider bad = scope -> token;

        assertThatThrownBy(() -> transport(wm, bad).send(mail()))
                .isExactlyInstanceOf(AuthRequiredException.class)
                .hasMessage("the token provider returned an access token that is not a valid header value");

        assertThat(sends()).isEmpty();
    }

    @Test
    void aFailureMessageHasStatusCodeAndRequestIdInThatOrder(WireMockRuntimeInfo wm) throws Exception {
        stubFor(post(urlEqualTo(SEND_PATH)).willReturn(aResponse().withStatus(403)
                .withHeader("request-id", ZERO_ID)
                .withBody(errorBody("ErrorAccessDenied", "Access is denied."))));

        assertThatThrownBy(() -> transport(wm).send(mail()))
                .isInstanceOf(SenderRejectedException.class)
                .hasMessage("Graph sendMail failed: HTTP 403, code ErrorAccessDenied, request-id " + ZERO_ID);
    }

    // Task 2: the status table, Retry-After, redirects, no hidden repeat.

    @ParameterizedTest
    @MethodSource("statusTable")
    void everyStatusMapsToItsExceptionAndCostsOneRequest(int status, Class<? extends MailException> expected,
            WireMockRuntimeInfo wm) throws Exception {
        stubFor(post(urlEqualTo(SEND_PATH)).willReturn(aResponse().withStatus(status)));

        if (expected == null) {
            transport(wm).send(mail());
        } else {
            assertThatThrownBy(() -> transport(wm).send(mail())).isInstanceOf(expected)
                    .hasMessageStartingWith("Graph sendMail failed: HTTP " + status + ",");
        }

        assertThat(sends()).hasSize(1);
        assertThat(stub.freshCalls()).isZero();
    }

    static Stream<Arguments> statusTable() {
        Stream.Builder<Arguments> rows = Stream.builder();
        for (int status : new int[] {200, 202, 204}) {
            rows.add(Arguments.of(status, null));
        }
        for (int status : new int[] {400, 405, 409, 413, 415, 422, 428, 430, 499}) {
            rows.add(Arguments.of(status, PermanentMailException.class));
        }
        for (int status : new int[] {403, 404}) {
            rows.add(Arguments.of(status, SenderRejectedException.class));
        }
        for (int status : new int[] {429, 500, 502, 503, 504}) {
            rows.add(Arguments.of(status, TransientMailException.class));
        }
        return rows.build();
    }

    @ParameterizedTest
    @MethodSource("retryAfterValues")
    void retryAfterIsReadAsSecondsOnly(int status, String header, String expected,
            WireMockRuntimeInfo wm) throws Exception {
        ResponseDefinitionBuilder response = aResponse().withStatus(status);
        if (header != null) {
            response.withHeader("Retry-After", header);
        }
        stubFor(post(urlEqualTo(SEND_PATH)).willReturn(response));

        Throwable thrown = catchThrowable(() -> transport(wm).send(mail()));

        assertThat(thrown).isInstanceOf(TransientMailException.class);
        Optional<Duration> delay = ((TransientMailException) thrown).retryAfter();
        switch (expected) {
            case "none" -> assertThat(delay).isEmpty();
            default -> assertThat(delay).contains(Duration.parse(expected));
        }
    }

    static Stream<Arguments> retryAfterValues() {
        return Stream.of(
                Arguments.of(429, "7", "PT7S"),
                Arguments.of(429, "Thu, 01 Jan 2099 00:00:00 GMT", "none"),
                Arguments.of(429, "soon", "none"),
                Arguments.of(503, "30", "PT30S"),
                Arguments.of(504, null, "none"));
    }

    @ParameterizedTest
    @ValueSource(ints = {300, 302})
    void aRedirectIsPermanentAndItsTargetIsNeverRequested(int status, WireMockRuntimeInfo wm) throws Exception {
        stubFor(post(urlEqualTo(SEND_PATH)).willReturn(aResponse().withStatus(status)
                .withHeader("Location", wm.getHttpBaseUrl() + "/v1.0/elsewhere")));
        stubFor(post(urlEqualTo("/v1.0/elsewhere")).willReturn(aResponse().withStatus(202)));

        assertThatThrownBy(() -> transport(wm).send(mail())).isInstanceOf(PermanentMailException.class);

        assertThat(sends()).hasSize(1);
        assertThat(findAll(postRequestedFor(urlEqualTo("/v1.0/elsewhere")))).isEmpty();
    }

    @Test
    void sendingAgainAfterATransientFailureMakesOneNewRequest(WireMockRuntimeInfo wm) throws Exception {
        stubFor(post(urlEqualTo(SEND_PATH)).willReturn(aResponse().withStatus(503)));
        MailTransport transport = transport(wm);
        assertThatThrownBy(() -> transport.send(mail())).isInstanceOf(TransientMailException.class);
        assertThat(sends()).hasSize(1);

        assertThatThrownBy(() -> transport.send(mail())).isInstanceOf(TransientMailException.class);

        assertThat(sends()).hasSize(2);
    }

    // Task 3: clean and private messages, network errors, timeouts, parallel sends.

    private static void stubError(int status, String requestIdHeader, String body) {
        ResponseDefinitionBuilder response = aResponse().withStatus(status).withBody(body);
        if (requestIdHeader != null) {
            response.withHeader("request-id", requestIdHeader);
        }
        stubFor(post(urlEqualTo(SEND_PATH)).willReturn(response));
    }

    private String messageOfFailedSend(WireMockRuntimeInfo wm) {
        Throwable thrown = catchThrowable(() -> transport(wm).send(mail()));
        assertThat(thrown).isInstanceOf(MailException.class);
        return thrown.getMessage();
    }

    @ParameterizedTest
    @ValueSource(strings = {"innerError", "innererror"})
    void theRequestIdComesFromTheInnerErrorWhenTheHeaderIsMissing(String field, WireMockRuntimeInfo wm) {
        stubError(500, null, "{\"error\":{\"code\":\"C\",\"message\":\"m\",\"" + field
                + "\":{\"request-id\":\"rid-inner\"}}}");

        assertThat(messageOfFailedSend(wm))
                .isEqualTo("Graph sendMail failed: HTTP 500, code C, request-id rid-inner");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "<html>Sentinel-body-7f3a</html>", "{}", "{\"error\":{}}", "{\"error\":\"text\"}"})
    void aBodyWithoutErrorFieldsGivesDashesAndNeverShowsTheBody(String body, WireMockRuntimeInfo wm) {
        stubError(500, null, body);

        assertThat(messageOfFailedSend(wm))
                .isEqualTo("Graph sendMail failed: HTTP 500, code -, request-id -");
    }

    @Test
    void controlCharactersBecomeSingleSpaces(WireMockRuntimeInfo wm) {
        stubError(400, null, errorBody("line one\\r\\nline\\ttwo\\u0007", "m"));

        assertThat(messageOfFailedSend(wm))
                .isEqualTo("Graph sendMail failed: HTTP 400, code line one line two, request-id -");
    }

    @Test
    void aMailAddressInTheRequestIdIsReplaced(WireMockRuntimeInfo wm) {
        stubError(500, "x@example.org", errorBody("C", "m"));

        assertThat(messageOfFailedSend(wm))
                .isEqualTo("Graph sendMail failed: HTTP 500, code C, request-id <address>");
    }

    @Test
    void aMailAddressInTheInnerRequestIdIsReplaced(WireMockRuntimeInfo wm) {
        stubError(500, null, "{\"error\":{\"code\":\"C\",\"message\":\"m\",\"innerError\":"
                + "{\"request-id\":\"x@example.org\"}}}");

        assertThat(messageOfFailedSend(wm))
                .isEqualTo("Graph sendMail failed: HTTP 500, code C, request-id <address>");
    }

    @Test
    @Timeout(10)
    void aVeryLongCodeIsCutAndCleanedQuickly(WireMockRuntimeInfo wm) {
        stubError(400, null, errorBody("a".repeat(100_000), "m"));

        assertThat(messageOfFailedSend(wm))
                .isEqualTo("Graph sendMail failed: HTTP 400, code " + "a".repeat(300) + "..., request-id -");
    }

    @Test
    void anAddressAfterALongWhitespaceRunLeavesNoPartOfItInTheMessage(WireMockRuntimeInfo wm) {
        stubError(400, null, errorBody(" ".repeat(995) + "alice@example.org", "m"));

        assertThat(messageOfFailedSend(wm))
                .isEqualTo("Graph sendMail failed: HTTP 400, code <address>, request-id -");
    }

    @Test
    void aMailAddressInTheCodeIsReplaced(WireMockRuntimeInfo wm) {
        stubError(404, null, errorBody("'x@example.org' <y@example.org>", "m"));

        assertThat(messageOfFailedSend(wm))
                .isEqualTo("Graph sendMail failed: HTTP 404, code '<address>' <<address>>, request-id -");
    }

    // Graph's text can name the sender (live 404 ErrorInvalidUser, UAT G-03-2), so it never enters the message.
    @ParameterizedTest
    @ValueSource(strings = {"The requested user 'newsletter@example.com' is invalid.",
        "The requested user 'newsletter%40example.com' is invalid."})
    void graphsOwnMessageNeverReachesTheExceptionMessage(String text, WireMockRuntimeInfo wm) {
        stubError(404, ZERO_ID, errorBody("ErrorInvalidUser", text));

        assertThatThrownBy(() -> transport(wm).send(mail()))
                .isInstanceOf(SenderRejectedException.class)
                .hasMessage("Graph sendMail failed: HTTP 404, code ErrorInvalidUser, request-id " + ZERO_ID)
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("newsletter"));
    }

    @Test
    void aLongCodeIsCutAt300CharactersPlusDots(WireMockRuntimeInfo wm) {
        stubError(400, null, errorBody("a".repeat(1000), "m"));

        assertThat(messageOfFailedSend(wm))
                .isEqualTo("Graph sendMail failed: HTTP 400, code " + "a".repeat(300) + "..., request-id -");
    }

    @Test
    void aCutNeverSplitsASurrogatePair(WireMockRuntimeInfo wm) {
        // Characters 300 and 301 (1-based) are the two halves of one emoji.
        stubError(400, null, errorBody("a".repeat(299) + "\\ud83d\\ude00" + "b".repeat(100), "m"));

        assertThat(messageOfFailedSend(wm))
                .isEqualTo("Graph sendMail failed: HTTP 400, code " + "a".repeat(299) + "..., request-id -");
    }

    @Test
    void noTokenSubjectBodyOrBearerReachesAMessageOrALogRecord(WireMockRuntimeInfo wm) throws Exception {
        StubTokenProvider secret = new StubTokenProvider("Sentinel-token-7f3a", "Sentinel-token-7f3a-fresh");
        MailMessage message = Mail.using("main").from("newsletter@example.com").to("reader@example.org")
                .subject("Sentinel-subject-7f3a").text("Plain").build().message();
        List<LogRecord> records = new ArrayList<>();
        Logger logger = Logger.getLogger("de.morisoft.morimail.graph.GraphTransport");
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
        List<String> messages = new ArrayList<>();
        MailMessage bothBodies = Mail.using("main").from("newsletter@example.com").to("reader@example.org")
                .subject("Sentinel-subject-7f3a").text("Sentinel-text-7f3a").html("<p>Sentinel-html-7f3a</p>")
                .build().message();
        try {
            // The one log call of the transport fires for a mail with both bodies.
            stubError(500, ZERO_ID, errorBody("ErrorX", "gone"));
            catchThrowable(() -> transport(wm, secret).send(bothBodies));
            for (int status : new int[] {401, 403, 429, 500, 400}) {
                for (String body : new String[] {"<html>Sentinel-body-7f3a</html>", errorBody("ErrorX", "gone")}) {
                    stubError(status, ZERO_ID, body);
                    messages.add(catchThrowable(() -> transport(wm, secret).send(message)).getMessage());
                }
            }
        } finally {
            logger.removeHandler(handler);
            logger.setLevel(before);
        }

        assertThat(messages).hasSize(10).allSatisfy(m -> assertThat(m)
                .doesNotContain("Sentinel-body-7f3a", "Sentinel-token-7f3a", "Sentinel-subject-7f3a", "Bearer"));
        assertThat(records).isNotEmpty().allSatisfy(r -> assertThat(r.getMessage())
                .doesNotContain("Sentinel", "Bearer", "example.org", "example.com"));
    }

    @Test
    void aResetConnectionIsTransientWithTheCause(WireMockRuntimeInfo wm) {
        stubFor(post(urlEqualTo(SEND_PATH)).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        Throwable thrown = catchThrowable(() -> transport(wm).send(mail()));

        assertThat(thrown).isInstanceOf(TransientMailException.class).hasCauseInstanceOf(IOException.class);
        assertThat(thrown.getMessage()).isEqualTo("Graph sendMail failed: no response ("
                + thrown.getCause().getClass().getSimpleName() + ")");
    }

    @Test
    void aSlowAnswerIsATransientTimeout(WireMockRuntimeInfo wm) {
        stubFor(post(urlEqualTo(SEND_PATH)).willReturn(aResponse().withStatus(202).withFixedDelay(2000)));
        GraphTransport quick = new GraphTransport(wm.getHttpBaseUrl() + "/v1.0", true, stub,
                Duration.ofMillis(500));

        assertThatThrownBy(() -> quick.send(mail()))
                .isInstanceOf(TransientMailException.class)
                .hasCauseInstanceOf(HttpTimeoutException.class);
    }

    @Test
    void theRequestAfterA401HasTheSameTimeout(WireMockRuntimeInfo wm) {
        stubBearer("t1", 401, errorBody("InvalidAuthenticationToken", "Expired"));
        stubFor(post(urlEqualTo(SEND_PATH)).withHeader("Authorization", equalTo("Bearer t2"))
                .willReturn(aResponse().withStatus(202).withFixedDelay(2000)));
        GraphTransport quick = new GraphTransport(wm.getHttpBaseUrl() + "/v1.0", true, stub,
                Duration.ofMillis(500));

        assertThatThrownBy(() -> quick.send(mail()))
                .isInstanceOf(TransientMailException.class)
                .hasCauseInstanceOf(HttpTimeoutException.class);
        assertThat(sends()).hasSize(2);
    }

    private static MailMessage mailTo(String recipient) throws InvalidMailException {
        return Mail.using("main").from("newsletter@example.com").to(recipient)
                .subject("Hello").text("Plain").build().message();
    }

    /** Runs the calls on separate threads that start together; a hang fails the test after 20 seconds. */
    private static List<Throwable> inParallel(ThrowingCallable... calls) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(calls.length);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Throwable>> futures = new ArrayList<>();
            for (ThrowingCallable call : calls) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return catchThrowable(call);
                }));
            }
            start.countDown();
            List<Throwable> results = new ArrayList<>();
            for (Future<Throwable> future : futures) {
                results.add(future.get(20, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void parallelSendsGetTheirOwnMapping(WireMockRuntimeInfo wm) throws Exception {
        stubFor(post(urlEqualTo(SEND_PATH)).withRequestBody(containing("rejected@example.org"))
                .willReturn(aResponse().withStatus(403)));
        stubFor(post(urlEqualTo(SEND_PATH)).withRequestBody(containing("fine@example.org"))
                .willReturn(aResponse().withStatus(202)));
        MailTransport transport = transport(wm);

        List<Throwable> results = inParallel(
                () -> transport.send(mailTo("rejected@example.org")),
                () -> transport.send(mailTo("fine@example.org")));

        assertThat(results.get(0)).isInstanceOf(SenderRejectedException.class);
        assertThat(results.get(1)).isNull();
    }

    @Test
    void parallelFailuresCarryTheirOwnCodeAndRequestId(WireMockRuntimeInfo wm) throws Exception {
        stubFor(post(urlEqualTo(SEND_PATH)).withRequestBody(containing("a@example.org"))
                .willReturn(aResponse().withStatus(400).withHeader("request-id", "rid-a")
                        .withBody(errorBody("CodeA", "first"))));
        stubFor(post(urlEqualTo(SEND_PATH)).withRequestBody(containing("b@example.org"))
                .willReturn(aResponse().withStatus(400).withHeader("request-id", "rid-b")
                        .withBody(errorBody("CodeB", "second"))));
        MailTransport transport = transport(wm);

        List<Throwable> results = inParallel(
                () -> transport.send(mailTo("a@example.org")),
                () -> transport.send(mailTo("b@example.org")));

        assertThat(results.get(0)).hasMessage(
                "Graph sendMail failed: HTTP 400, code CodeA, request-id rid-a");
        assertThat(results.get(1)).hasMessage(
                "Graph sendMail failed: HTTP 400, code CodeB, request-id rid-b");
    }

    @Test
    void twoThreadsRejectedWithTheSameTokenEachRetryOnce(WireMockRuntimeInfo wm) throws Exception {
        stubBearer("t1", 401, errorBody("InvalidAuthenticationToken", "Expired"));
        stubBearer("t2", 202, "");
        MailTransport transport = transport(wm);

        List<Throwable> results = inParallel(
                () -> transport.send(mailTo("a@example.org")),
                () -> transport.send(mailTo("b@example.org")));

        assertThat(results).containsOnlyNulls();
        assertThat(sends()).hasSize(4);
        assertThat(sends().stream().filter(r -> "Bearer t1".equals(r.getHeader("Authorization"))).count())
                .isEqualTo(2);
        assertThat(sends().stream().filter(r -> "Bearer t2".equals(r.getHeader("Authorization"))).count())
                .isEqualTo(2);
        assertThat(stub.freshCalls()).isEqualTo(2);
    }
}
