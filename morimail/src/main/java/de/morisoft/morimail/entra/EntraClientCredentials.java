package de.morisoft.morimail.entra;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.morisoft.morimail.AuthRequiredException;
import de.morisoft.morimail.MailException;
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
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;

/**
 * Gets access tokens for an Entra app with the client credentials flow. Create an instance with
 * {@link #withSecret(String, String, String)}. The token endpoint is
 * {@code {authority}/{tenant}/oauth2/v2.0/token}; the authority is the public cloud unless
 * {@link #authority(URI)} returns a copy that uses another one.
 *
 * <p>Instances are immutable and safe for concurrent use by several threads.
 */
public final class EntraClientCredentials implements TokenProvider {

    private static final URI PUBLIC_CLOUD = URI.create("https://login.microsoftonline.com");
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String tenantId;
    private final String clientId;
    private final String secret;
    private final PrivateKey privateKey;
    private final X509Certificate certificate;
    private final URI authority;
    private final Clock clock;
    private final Duration requestTimeout;
    private final HttpClient http;

    private EntraClientCredentials(String tenantId, String clientId, String secret, PrivateKey privateKey,
            X509Certificate certificate, URI authority, Clock clock, Duration requestTimeout) {
        this.tenantId = tenantId;
        this.clientId = clientId;
        this.secret = secret;
        this.privateKey = privateKey;
        this.certificate = certificate;
        this.authority = authority;
        this.clock = clock;
        this.requestTimeout = requestTimeout;
        this.http = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    /**
     * Creates credentials that authenticate with a client secret.
     *
     * @param tenantId the directory (tenant) id
     * @param clientId the application (client) id
     * @param secret the client secret
     * @return new credentials for the public cloud
     * @throws NullPointerException if an argument is {@code null}
     * @throws IllegalArgumentException if an argument is blank
     */
    public static EntraClientCredentials withSecret(String tenantId, String clientId, String secret) {
        requireText(tenantId, "tenantId");
        requireText(clientId, "clientId");
        requireText(secret, "secret");
        return new EntraClientCredentials(tenantId, clientId, secret, null, null, PUBLIC_CLOUD,
                Clock.systemUTC(), REQUEST_TIMEOUT);
    }

    /**
     * Returns a copy of these credentials that gets tokens from another authority, for example a
     * sovereign cloud or a test server.
     *
     * @param authority an absolute http or https URI with a host; one trailing slash is dropped
     * @return a new instance with the same credentials and the given authority
     * @throws NullPointerException if the authority is {@code null}
     * @throws IllegalArgumentException if the authority is not an absolute http or https URI
     */
    public EntraClientCredentials authority(URI authority) {
        Objects.requireNonNull(authority, "authority");
        String scheme = authority.getScheme();
        boolean web = "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
        if (!web || authority.getHost() == null) {
            throw new IllegalArgumentException("authority must be an absolute http or https URI");
        }
        String text = authority.toString();
        if (text.endsWith("/")) {
            text = text.substring(0, text.length() - 1);
        }
        return new EntraClientCredentials(tenantId, clientId, secret, privateKey, certificate,
                URI.create(text), clock, requestTimeout);
    }

    URI tokenUri() {
        return URI.create(authority + "/" + URLEncoder.encode(tenantId, StandardCharsets.UTF_8)
                + "/oauth2/v2.0/token");
    }

    /**
     * Requests an access token for the scope from the token endpoint.
     *
     * @param scope the scope the token is for
     * @return the access token
     * @throws TransientMailException if the endpoint cannot be reached or answers 429 or 5xx
     * @throws AuthRequiredException if the endpoint rejects the credentials
     */
    @Override
    public String accessToken(String scope) throws MailException {
        String form = form("client_id", clientId, "scope", scope, "client_secret", secret,
                "grant_type", "client_credentials");
        HttpRequest request = HttpRequest.newBuilder(tokenUri())
                .header("Content-Type", "application/x-www-form-urlencoded")
                .timeout(requestTimeout)
                .POST(BodyPublishers.ofString(form))
                .build();
        HttpResponse<byte[]> response;
        try {
            response = http.send(request, BodyHandlers.ofByteArray());
        } catch (IOException e) {
            throw new TransientMailException("Entra token request failed: no response ("
                    + e.getClass().getSimpleName() + ")", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TransientMailException("Entra token request failed: interrupted", e);
        }
        int status = response.statusCode();
        JsonNode json = parse(response.body());
        if (status >= 200 && status <= 299) {
            String token = text(json, "access_token");
            if (token == null) {
                throw new AuthRequiredException("Entra token request failed: HTTP " + status
                        + ", the response has no access_token");
            }
            return token;
        }
        String failure = "Entra token request failed: HTTP " + status;
        if (status == 429 || status >= 500) {
            throw new TransientMailException(failure);
        }
        throw new AuthRequiredException(failure + ", error " + orDash(text(json, "error"))
                + ", description: " + orDash(text(json, "error_description")));
    }

    private static String form(String... namesAndValues) {
        StringBuilder form = new StringBuilder();
        for (int i = 0; i < namesAndValues.length; i += 2) {
            if (i > 0) {
                form.append('&');
            }
            form.append(URLEncoder.encode(namesAndValues[i], StandardCharsets.UTF_8))
                    .append('=')
                    .append(URLEncoder.encode(namesAndValues[i + 1], StandardCharsets.UTF_8));
        }
        return form.toString();
    }

    private static JsonNode parse(byte[] body) {
        try {
            return MAPPER.readTree(body);
        } catch (IOException e) {
            return null;
        }
    }

    private static String text(JsonNode json, String field) {
        if (json == null || !json.hasNonNull(field) || !json.get(field).isTextual()) {
            return null;
        }
        String value = json.get(field).asText();
        return value.isEmpty() ? null : value;
    }

    private static String orDash(String value) {
        return value == null ? "-" : value;
    }

    private static void requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
