package de.morisoft.morimail.graph;

import com.fasterxml.jackson.databind.JsonNode;
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
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
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

    /** Graph takes a file attachment below this size, alone and with all others added up. */
    static final long MAX_ATTACHMENT_BYTES = 3L * 1024 * 1024;
    /** The estimated request, attachments as base64 plus body plus allowance, may not exceed this. */
    static final long MAX_REQUEST_BYTES = 4L * 1024 * 1024;
    /** Room for subject, addresses and JSON syntax in the request estimate. */
    static final long JSON_ALLOWANCE = 16L * 1024;

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_ERROR_TEXT = 300;
    private static final int MAX_CLEAN_INPUT = 1000;
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

    static long base64Size(long size) {
        return (size + 2) / 3 * 4;
    }

    /**
     * Checks the attachment and request size limits. The request size is an estimate: it counts the
     * attachments as base64, the body and a fixed allowance, but not the subject, the recipients or
     * the attachment names, so a mail near the limit can still be refused by the service. The 3 MiB
     * and 4 MiB limits are readings of the Graph documentation; in the live test of 2026-11-22 attachments
     * of 2,900,000 and 3,100,000 bytes were accepted, which fits the binary reading, and the point where
     * the service refuses was not probed.
     */
    @Override
    public void validate(MailMessage m) throws InvalidMailException {
        long total = 0;
        long estimate = JSON_ALLOWANCE;
        List<String> names = new ArrayList<>();
        for (Attachment attachment : m.attachments()) {
            long size;
            try {
                size = Files.size(attachment.file());
            } catch (IOException e) {
                throw new InvalidMailException("attachment " + attachment.filename() + " cannot be read", e);
            }
            if (size >= MAX_ATTACHMENT_BYTES) {
                throw new InvalidMailException("attachment " + attachment.filename() + " has " + size
                        + " bytes; Graph takes attachments below 3 MiB (" + MAX_ATTACHMENT_BYTES + " bytes)");
            }
            total += size;
            estimate += base64Size(size);
            names.add(attachment.filename());
        }
        if (total >= MAX_ATTACHMENT_BYTES) {
            throw new InvalidMailException("attachments " + String.join(", ", names) + " have " + total
                    + " bytes together; Graph takes below 3 MiB (" + MAX_ATTACHMENT_BYTES + " bytes) in total");
        }
        String content = usesHtml(m) ? m.html() : m.text();
        if (content != null) {
            try {
                // The escaped UTF-8 JSON string without its two quotes: what send writes for the body.
                estimate += MAPPER.writeValueAsBytes(content).length - 2;
            } catch (IOException e) {
                throw new InvalidMailException("the mail body cannot be written as JSON", e);
            }
        }
        if (estimate > MAX_REQUEST_BYTES) {
            throw new InvalidMailException("estimated request size " + estimate
                    + " bytes is above the limit of 4 MiB (" + MAX_REQUEST_BYTES + " bytes)");
        }
    }

    private static boolean usesHtml(MailMessage m) {
        return m.html() != null && !m.html().isBlank();
    }

    private byte[] requestBody(MailMessage m) throws MailException {
        boolean html = usesHtml(m);
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
        URI uri = sendMailUri(m.from().email());
        String token = requireToken(auth.accessToken(SCOPE));
        HttpResponse<byte[]> response = post(uri, token, json);
        if (response.statusCode() == 401) {
            // The one retry the library makes (spec 6.3): the token may have expired or been revoked.
            token = requireToken(auth.freshAccessToken(SCOPE, token));
            response = post(uri, token, json);
        }
        int status = response.statusCode();
        if (status >= 200 && status <= 299) {
            return;
        }
        throw failure(response);
    }

    private static String requireToken(String token) throws AuthRequiredException {
        if (token == null || token.isEmpty()) {
            throw new AuthRequiredException("the token provider returned no access token");
        }
        // A control character or blank would make the JDK throw an exception that quotes the whole header.
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if (c <= ' ' || c > '~') {
                throw new AuthRequiredException(
                        "the token provider returned an access token that is not a valid header value");
            }
        }
        return token;
    }

    private HttpResponse<byte[]> post(URI uri, String token, byte[] body) throws TransientMailException {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .timeout(requestTimeout)
                .POST(BodyPublishers.ofByteArray(body))
                .build();
        try {
            return http.send(request, BodyHandlers.ofByteArray());
        } catch (IOException e) {
            throw new TransientMailException("Graph sendMail failed: no response ("
                    + e.getClass().getSimpleName() + ")", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TransientMailException("Graph sendMail failed: interrupted", e);
        }
    }

    /**
     * True when the Graph error code names a mailbox. A second 401 with such a code means the sender has
     * no usable mailbox, not that the credentials are wrong.
     */
    static boolean mailboxHint(String errorCode) {
        return errorCode != null && errorCode.toLowerCase(Locale.ROOT).contains("mailbox");
    }

    /**
     * Maps the answer of a failed call (a 401 reaches this only after the one retry) to its exception.
     * The message holds the HTTP status, the cleaned error code and the request-id only: never the raw
     * body, and never Graph's free-text error message, which can name the sender.
     */
    private MailException failure(HttpResponse<byte[]> r) {
        int status = r.statusCode();
        String code = null;
        String requestId = r.headers().firstValue("request-id").orElse(null);
        try {
            JsonNode error = MAPPER.readTree(r.body()).get("error");
            if (error != null && error.isObject()) {
                code = textOf(error.get("code"));
                if (requestId == null) {
                    requestId = textOf(innerRequestId(error.get("innerError")));
                }
                if (requestId == null) {
                    requestId = textOf(innerRequestId(error.get("innererror")));
                }
            }
        } catch (IOException | RuntimeException e) {
            // Not JSON, or not the expected shape: the fields stay empty.
        }
        String message = "Graph sendMail failed: HTTP " + status + ", code " + clean(code)
                + ", request-id " + clean(requestId);
        if (status == 401) {
            return mailboxHint(code) ? new SenderRejectedException(message) : new AuthRequiredException(message);
        }
        if (status == 403 || status == 404) {
            return new SenderRejectedException(message);
        }
        if (status == 429 || status >= 500 && status <= 599) {
            Duration retryAfter = retryAfter(r.headers());
            return new TransientMailException(message, retryAfter);
        }
        return new PermanentMailException(message);
    }

    /** The {@code Retry-After} header as a delay in seconds, or null when absent or not a number of seconds. */
    private static Duration retryAfter(HttpHeaders headers) {
        String value = headers.firstValue("Retry-After").map(String::trim).orElse("");
        return value.matches("[0-9]{1,9}") ? Duration.ofSeconds(Long.parseLong(value)) : null;
    }

    private static String textOf(JsonNode node) {
        return node != null && node.isTextual() ? node.asText() : null;
    }

    private static JsonNode innerRequestId(JsonNode inner) {
        return inner == null ? null : inner.get("request-id");
    }

    /**
     * Makes a text from the server safe for a message: control characters become spaces, whitespace runs
     * collapse, a text longer than 1000 characters is cut back to the last space before character 1000 so
     * that no part of a word is kept, anything that looks like a mail address becomes "&lt;address&gt;",
     * the result is cut at 300 characters (never inside a surrogate pair), and nothing becomes "-".
     */
    static String clean(String value) {
        if (value == null) {
            return "-";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            sb.append(Character.isISOControl(c) || Character.isWhitespace(c) ? ' ' : c);
        }
        // Collapse, then cut at a word boundary, then replace addresses: the address pattern is quadratic on a long
        // token without "@", so it only sees the cut text, and a cut inside a word could leave the start of an address
        // that the pattern no longer finds (review WR-01).
        String collapsed = sb.toString().replaceAll(" {2,}", " ");
        if (collapsed.length() > MAX_CLEAN_INPUT) {
            collapsed = collapsed.substring(0, MAX_CLEAN_INPUT);
            int lastSpace = collapsed.lastIndexOf(' ');
            collapsed = lastSpace > 0 ? collapsed.substring(0, lastSpace) : "";
        }
        String text = collapsed.replaceAll("[^\\s'\"<>]+@[^\\s'\"<>]+", "<address>").trim();
        if (text.isEmpty()) {
            return "-";
        }
        if (text.length() <= MAX_ERROR_TEXT) {
            return text;
        }
        int end = Character.isHighSurrogate(text.charAt(MAX_ERROR_TEXT - 1)) ? MAX_ERROR_TEXT - 1 : MAX_ERROR_TEXT;
        return text.substring(0, end) + "...";
    }
}
