package de.morisoft.morimail.graph;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.morisoft.morimail.Attachment;
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
import java.nio.file.Files;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Sends one message through the Graph {@code sendMail} call. The configuration is fixed after
 * construction and the only mutable state is one {@link AtomicBoolean}; one instance with its one
 * {@link HttpClient} is safe for concurrent use by several threads.
 */
final class GraphTransport implements MailTransport {

    static final String SCOPE = "https://graph.microsoft.com/.default";
    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final System.Logger LOG = System.getLogger(GraphTransport.class.getName());

    private final String baseUrl;
    private final boolean saveToSentItems;
    private final TokenProvider auth;
    private final Duration requestTimeout;
    private final HttpClient http;
    private final AtomicBoolean bothBodiesLogged = new AtomicBoolean();

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

    private byte[] requestBody(MailMessage m) throws MailException {
        boolean html = m.html() != null && !m.html().isBlank();
        boolean text = m.text() != null && !m.text().isBlank();
        if (html && text && bothBodiesLogged.compareAndSet(false, true)) {
            LOG.log(System.Logger.Level.INFO,
                    "a mail has both an HTML and a text body; Graph sends only the HTML body (logged once per transport)");
        }
        ObjectNode message = MAPPER.createObjectNode();
        message.put("subject", m.subject());
        message.putObject("body").put("contentType", html ? "HTML" : "Text")
                .put("content", html ? m.html() : m.text());
        addRecipients(message, "toRecipients", m.to());
        addRecipients(message, "ccRecipients", m.cc());
        addRecipients(message, "bccRecipients", m.bcc());
        if (m.replyTo() != null) {
            addRecipients(message, "replyTo", List.of(m.replyTo()));
        }
        if (!m.attachments().isEmpty()) {
            ArrayNode attachments = message.putArray("attachments");
            for (Attachment attachment : m.attachments()) {
                byte[] content;
                try {
                    content = Files.readAllBytes(attachment.file());
                } catch (IOException e) {
                    throw new InvalidMailException("attachment " + attachment.filename() + " cannot be read", e);
                }
                ObjectNode node = attachments.addObject();
                node.put("@odata.type", "#microsoft.graph.fileAttachment");
                node.put("name", attachment.filename());
                node.put("contentType", attachment.contentType());
                node.put("contentBytes", Base64.getEncoder().encodeToString(content));
            }
        }
        ObjectNode root = MAPPER.createObjectNode();
        root.set("message", message);
        if (!saveToSentItems) {
            root.put("saveToSentItems", false);
        }
        try {
            return MAPPER.writeValueAsBytes(root);
        } catch (IOException e) {
            throw new PermanentMailException("Graph sendMail failed: the request body cannot be built", e);
        }
    }

    private static void addRecipients(ObjectNode message, String key, List<MailAddress> addresses) {
        if (addresses.isEmpty()) {
            return;
        }
        ArrayNode array = message.putArray(key);
        for (MailAddress address : addresses) {
            ObjectNode email = array.addObject().putObject("emailAddress");
            email.put("address", address.email());
            if (address.displayName() != null && !address.displayName().isEmpty()) {
                email.put("name", address.displayName());
            }
        }
    }

    @Override
    public void send(MailMessage m) throws MailException {
        byte[] json = requestBody(m);
        String token = auth.accessToken(SCOPE);
        HttpRequest request = HttpRequest.newBuilder(sendMailUri(m.from().email()))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .timeout(requestTimeout)
                .POST(BodyPublishers.ofByteArray(json))
                .build();
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
