package de.morisoft.morimail.graph;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.findAll;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import de.morisoft.morimail.AuthRequiredException;
import de.morisoft.morimail.InvalidMailException;
import de.morisoft.morimail.Mail;
import de.morisoft.morimail.MailException;
import de.morisoft.morimail.MailMessage;
import de.morisoft.morimail.MailTransport;
import de.morisoft.morimail.SenderRejectedException;
import de.morisoft.morimail.TokenProvider;
import de.morisoft.morimail.TransientMailException;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
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

    @Test
    void aFailureMessageHasStatusCodeRequestIdAndMessageInThatOrder(WireMockRuntimeInfo wm) throws Exception {
        stubFor(post(urlEqualTo(SEND_PATH)).willReturn(aResponse().withStatus(403)
                .withHeader("request-id", ZERO_ID)
                .withBody(errorBody("ErrorAccessDenied", "Access is denied."))));

        assertThatThrownBy(() -> transport(wm).send(mail()))
                .isInstanceOf(SenderRejectedException.class)
                .hasMessage("Graph sendMail failed: HTTP 403, code ErrorAccessDenied, request-id "
                        + ZERO_ID + ", message: Access is denied.");
    }
}
