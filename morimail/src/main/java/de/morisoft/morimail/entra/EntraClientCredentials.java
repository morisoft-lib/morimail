package de.morisoft.morimail.entra;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.morisoft.morimail.AuthRequiredException;
import de.morisoft.morimail.MailException;
import de.morisoft.morimail.TokenProvider;
import de.morisoft.morimail.TransientMailException;
import java.io.IOException;
import java.io.InputStream;
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
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PSSParameterSpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Gets access tokens for an Entra app with the client credentials flow. Create an instance with
 * {@link #withSecret(String, String, String)}, or with a certificate through
 * {@link #withCertificate(String, String, PrivateKey, X509Certificate)} or
 * {@link #withCertificate(String, String, Path, char[])}. The token endpoint is
 * {@code {authority}/{tenant}/oauth2/v2.0/token}; the authority is the public cloud unless
 * {@link #authority(URI)} returns a copy that uses another one.
 *
 * <p>The certificate flow signs a client assertion with PS256 (RSASSA-PSS with SHA-256). The
 * assertion header carries {@code x5t#S256}, the base64url SHA-256 thumbprint of the certificate,
 * and the assertion is valid for 10 minutes. A new assertion is built for every token request.
 * Only RSA keys are supported, and there is no PEM support: pass a key and a certificate, or a
 * PKCS12 file.
 *
 * <p>The configuration is fixed after construction; the token cache is guarded by a lock, so an
 * instance is safe for concurrent use by several threads. An instance makes one token request at a
 * time, reuses a token until five minutes before it expires, and never caches a failure.
 */
public final class EntraClientCredentials implements TokenProvider {

    private static final URI PUBLIC_CLOUD = URI.create("https://login.microsoftonline.com");
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration EXPIRY_MARGIN = Duration.ofMinutes(5);
    private static final int MAX_ERROR_TEXT = 300;
    private static final long MAX_EXPIRES_IN_SECONDS = 31_536_000L;
    private static final long ASSERTION_LIFETIME_SECONDS = 600;
    private static final String JWT_BEARER = "urn:ietf:params:oauth:client-assertion-type:jwt-bearer";
    private static final Base64.Encoder BASE64URL = Base64.getUrlEncoder().withoutPadding();
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
    private final ReentrantLock lock = new ReentrantLock();
    private volatile Token cached;

    private record Token(String scope, String value, Instant usableUntil) {
    }

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
     * Creates credentials that authenticate with a certificate: the token request carries a client
     * assertion signed with the private key (PS256).
     *
     * @param tenantId the directory (tenant) id
     * @param clientId the application (client) id
     * @param privateKey the RSA private key that belongs to the certificate
     * @param certificate the certificate registered for the app
     * @return new credentials for the public cloud
     * @throws NullPointerException if an argument is {@code null}
     * @throws IllegalArgumentException if an id is blank or the key is not an RSA key
     */
    public static EntraClientCredentials withCertificate(String tenantId, String clientId,
            PrivateKey privateKey, X509Certificate certificate) {
        requireText(tenantId, "tenantId");
        requireText(clientId, "clientId");
        Objects.requireNonNull(privateKey, "privateKey");
        Objects.requireNonNull(certificate, "certificate");
        if (!"RSA".equals(privateKey.getAlgorithm())) {
            throw new IllegalArgumentException("privateKey must be an RSA key (PS256)");
        }
        return new EntraClientCredentials(tenantId, clientId, null, privateKey, certificate, PUBLIC_CLOUD,
                Clock.systemUTC(), REQUEST_TIMEOUT);
    }

    /**
     * Creates credentials that authenticate with a certificate from a PKCS12 file. The file must
     * hold exactly one private key entry with an RSA key and its X.509 certificate. The file and
     * the password are read once, here; the instance keeps only the key and the certificate, and
     * the password array is neither stored nor modified.
     *
     * @param tenantId the directory (tenant) id
     * @param clientId the application (client) id
     * @param p12 the PKCS12 file
     * @param password the password of the file, also used for the key
     * @return new credentials for the public cloud
     * @throws NullPointerException if an argument is {@code null}
     * @throws IllegalArgumentException if an id is blank or the file cannot be used; the message
     *         names the file and never the password
     */
    public static EntraClientCredentials withCertificate(String tenantId, String clientId, Path p12,
            char[] password) {
        requireText(tenantId, "tenantId");
        requireText(clientId, "clientId");
        Objects.requireNonNull(p12, "p12");
        Objects.requireNonNull(password, "password");
        String where = "cannot load the PKCS12 key store " + p12 + ": ";
        KeyStore store;
        try (InputStream in = Files.newInputStream(p12)) {
            store = KeyStore.getInstance("PKCS12");
            store.load(in, password);
        } catch (IOException | GeneralSecurityException e) {
            throw new IllegalArgumentException(where + "the file cannot be read or the password is wrong", e);
        }
        try {
            List<String> keyAliases = new ArrayList<>();
            for (String alias : Collections.list(store.aliases())) {
                if (store.isKeyEntry(alias)) {
                    keyAliases.add(alias);
                }
            }
            if (keyAliases.size() != 1) {
                throw new IllegalArgumentException(where + "expected exactly one private key entry, found "
                        + keyAliases.size());
            }
            String alias = keyAliases.get(0);
            Key key = store.getKey(alias, password);
            if (!(key instanceof PrivateKey privateKey) || !"RSA".equals(key.getAlgorithm())) {
                throw new IllegalArgumentException(where + "the key entry is not an RSA private key");
            }
            Certificate certificate = store.getCertificate(alias);
            if (!(certificate instanceof X509Certificate x509)) {
                throw new IllegalArgumentException(where + "the key entry has no X.509 certificate");
            }
            return withCertificate(tenantId, clientId, privateKey, x509);
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException(where + "the key cannot be recovered", e);
        }
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

    /** Test seam: a copy with another clock and request timeout. Used by same-package tests only. */
    EntraClientCredentials forTest(Clock clock, Duration requestTimeout) {
        return new EntraClientCredentials(tenantId, clientId, secret, privateKey, certificate, authority,
                clock, requestTimeout);
    }

    URI tokenUri() {
        return URI.create(authority + "/" + URLEncoder.encode(tenantId, StandardCharsets.UTF_8)
                + "/oauth2/v2.0/token");
    }

    /**
     * Returns an access token for the scope. A cached token is reused until five minutes before it
     * expires; otherwise one request at a time goes to the token endpoint, and the other callers
     * wait for its result.
     *
     * @param scope the scope the token is for
     * @return the access token
     * @throws TransientMailException if the endpoint cannot be reached or answers 429 or 5xx
     * @throws AuthRequiredException if the endpoint rejects the credentials
     */
    @Override
    public String accessToken(String scope) throws MailException {
        Token token = cached;
        if (usable(token, scope)) {
            return token.value();
        }
        lock.lock();
        try {
            return cachedOrFetch(scope);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns a token after the service rejected {@code rejectedToken}. The cached token is dropped
     * only when it equals {@code rejectedToken}; with any other cached token that newer token is
     * returned without a request. Callers that reject the same token share one new request.
     *
     * @param scope the scope the token is for
     * @param rejectedToken the token the service rejected
     * @return a token for the scope
     * @throws TransientMailException if the endpoint cannot be reached or answers 429 or 5xx
     * @throws AuthRequiredException if the endpoint rejects the credentials
     */
    @Override
    public String freshAccessToken(String scope, String rejectedToken) throws MailException {
        lock.lock();
        try {
            Token token = cached;
            if (token != null && token.value().equals(rejectedToken)) {
                cached = null;
            }
            return cachedOrFetch(scope);
        } finally {
            lock.unlock();
        }
    }

    private String cachedOrFetch(String scope) throws MailException {
        Token token = cached;
        if (usable(token, scope)) {
            return token.value();
        }
        token = fetch(scope);
        cached = token;
        return token.value();
    }

    private boolean usable(Token token, String scope) {
        return token != null && token.scope().equals(scope) && clock.instant().isBefore(token.usableUntil());
    }

    private Token fetch(String scope) throws MailException {
        String form = privateKey == null
                ? form("client_id", clientId, "scope", scope, "client_secret", secret,
                        "grant_type", "client_credentials")
                : form("client_id", clientId, "scope", scope, "client_assertion_type", JWT_BEARER,
                        "client_assertion", assertion(tokenUri().toString()), "grant_type", "client_credentials");
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
            long expiresIn = expiresIn(json);
            if (token == null || expiresIn <= 0) {
                throw new AuthRequiredException("Entra token response is not usable: HTTP " + status
                        + " without access_token and expires_in");
            }
            if (!isHeaderSafe(token)) {
                throw new AuthRequiredException("Entra token response is not usable: HTTP " + status
                        + " with an access_token that is not a valid header value");
            }
            return new Token(scope, token, clock.instant().plusSeconds(expiresIn).minus(EXPIRY_MARGIN));
        }
        if (status == 429 || status >= 500 && status <= 599) {
            Duration retryAfter = retryAfter(response.headers());
            throw new TransientMailException("Entra token request failed: HTTP " + status, retryAfter);
        }
        throw new AuthRequiredException("Entra token request failed: HTTP " + status + ", error "
                + clean(text(json, "error")) + ", description: " + clean(text(json, "error_description")));
    }

    private Duration retryAfter(HttpHeaders headers) {
        String value = headers.firstValue("Retry-After").map(String::trim).orElse("");
        if (value.matches("\\d{1,9}")) {
            return Duration.ofSeconds(Long.parseLong(value));
        }
        try {
            Instant at = ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
            Duration wait = Duration.between(clock.instant(), at);
            return wait.isNegative() ? Duration.ZERO : wait;
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String clean(String value) {
        if (value == null) {
            return "-";
        }
        StringBuilder spaced = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            spaced.append(Character.isISOControl(c) ? ' ' : c);
        }
        String text = spaced.toString().replaceAll("\\s+", " ").trim();
        if (text.isEmpty()) {
            return "-";
        }
        if (text.length() > MAX_ERROR_TEXT) {
            int end = MAX_ERROR_TEXT;
            if (Character.isHighSurrogate(text.charAt(end - 1))) {
                end--;
            }
            return text.substring(0, end) + "...";
        }
        return text;
    }

    /** True when the token has only printable ASCII, so it can go into an Authorization header. */
    private static boolean isHeaderSafe(String token) {
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if (c <= ' ' || c > '~') {
                return false;
            }
        }
        return true;
    }

    private static long expiresIn(JsonNode json) {
        JsonNode node = json == null ? null : json.get("expires_in");
        if (node != null && (node.isNumber() || node.isTextual() && node.asText().matches("\\d{1,9}"))) {
            return Math.max(-1, Math.min(node.asLong(), MAX_EXPIRES_IN_SECONDS));
        }
        return 0;
    }

    private String assertion(String audience) throws AuthRequiredException {
        try {
            Map<String, Object> header = new LinkedHashMap<>();
            header.put("alg", "PS256");
            header.put("typ", "JWT");
            header.put("x5t#S256", BASE64URL.encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded())));
            long now = clock.instant().getEpochSecond();
            Map<String, Object> claims = new LinkedHashMap<>();
            claims.put("aud", audience);
            claims.put("exp", now + ASSERTION_LIFETIME_SECONDS);
            claims.put("iss", clientId);
            claims.put("jti", UUID.randomUUID().toString());
            claims.put("nbf", now);
            claims.put("sub", clientId);
            claims.put("iat", now);
            String signingInput = BASE64URL.encodeToString(MAPPER.writeValueAsBytes(header)) + "."
                    + BASE64URL.encodeToString(MAPPER.writeValueAsBytes(claims));
            Signature signature = Signature.getInstance("RSASSA-PSS");
            signature.setParameter(new PSSParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, 32, 1));
            signature.initSign(privateKey);
            signature.update(signingInput.getBytes(StandardCharsets.US_ASCII));
            return signingInput + "." + BASE64URL.encodeToString(signature.sign());
        } catch (GeneralSecurityException | IOException e) {
            throw new AuthRequiredException("cannot sign the client assertion", e);
        }
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

    private static void requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
