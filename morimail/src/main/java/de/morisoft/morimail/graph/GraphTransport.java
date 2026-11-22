package de.morisoft.morimail.graph;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.morisoft.morimail.AuthRequiredException;
import de.morisoft.morimail.InvalidMailException;
import de.morisoft.morimail.MailAddress;
import de.morisoft.morimail.MailException;
import de.morisoft.morimail.MailMessage;
import de.morisoft.morimail.MailTransport;
import de.morisoft.morimail.PermanentMailException;
import de.morisoft.morimail.SenderRejectedException;
import de.morisoft.morimail.TokenProvider;
import de.morisoft.morimail.TransientMailException;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Sends one message through the Graph {@code sendMail} call. Immutable after construction and
 * safe for concurrent use by several threads; the one {@link HttpClient} is shared by all of them.
 */
final class GraphTransport implements MailTransport {

    static final String SCOPE = "https://graph.microsoft.com/.default";
    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String baseUrl;
    private final boolean saveToSentItems;
    private final TokenProvider auth;
    private final Duration requestTimeout;
    private final HttpClient http;

    GraphTransport(String baseUrl, boolean saveToSentItems, TokenProvider auth, Duration requestTimeout) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.saveToSentItems = saveToSentItems;
        this.auth = auth;
        this.requestTimeout = requestTimeout;
        this.http = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    URI sendMailUri(String from) {
        return URI.create(baseUrl + "/users/" + URLEncoder.encode(from, StandardCharsets.UTF_8) + "/sendMail");
    }

    @Override
    public void validate(MailMessage message) throws InvalidMailException {
        // The size limits arrive with the full request body.
    }

    @Override
    public void send(MailMessage m) throws MailException {
        String token = auth.accessToken(SCOPE);
        ObjectNode message = MAPPER.createObjectNode();
        message.put("subject", m.subject());
        boolean html = m.html() != null && !m.html().isBlank();
        ObjectNode body = message.putObject("body");
        body.put("contentType", html ? "HTML" : "Text");
        body.put("content", html ? m.html() : m.text());
        ArrayNode to = message.putArray("toRecipients");
        for (MailAddress address : m.to()) {
            to.addObject().putObject("emailAddress").put("address", address.email());
        }
        ObjectNode root = MAPPER.createObjectNode();
        root.set("message", message);

        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(sendMailUri(m.from().email()))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .timeout(requestTimeout)
                    .POST(BodyPublishers.ofByteArray(MAPPER.writeValueAsBytes(root)))
                    .build();
        } catch (IOException e) {
            throw new PermanentMailException("Graph sendMail failed: the request body cannot be built", e);
        }
        HttpResponse<byte[]> response;
        try {
            response = http.send(request, BodyHandlers.ofByteArray());
        } catch (IOException e) {
            throw new TransientMailException("Graph sendMail failed: no response ("
                    + e.getClass().getSimpleName() + ")", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TransientMailException("Graph sendMail failed: interrupted", e);
        }
        int status = response.statusCode();
        if (status >= 200 && status <= 299) {
            return;
        }
        String failure = "Graph sendMail failed: HTTP " + status;
        if (status == 403 || status == 404) {
            throw new SenderRejectedException(failure);
        }
        if (status == 429 || status >= 500 && status <= 599) {
            throw new TransientMailException(failure);
        }
        if (status == 401) {
            throw new AuthRequiredException(failure);
        }
        throw new PermanentMailException(failure);
    }
}
