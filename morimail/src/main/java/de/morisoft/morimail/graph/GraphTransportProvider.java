package de.morisoft.morimail.graph;

import de.morisoft.morimail.MailTransport;
import de.morisoft.morimail.TokenProvider;
import de.morisoft.morimail.TransportProvider;
import java.net.URI;
import java.util.Map;

/**
 * Provides the {@code graph} transport, which sends mail through the Microsoft Graph
 * {@code sendMail} call. It is one of the built-in transports.
 *
 * <p>The account accepts exactly two options:
 *
 * <ul>
 *   <li>{@code baseUrl}, a {@link String} that is an absolute {@code https} URL with a host;
 *       {@code http} is accepted only for {@code localhost}, {@code 127.0.0.1} and {@code [::1]},
 *       so a bearer token never travels in cleartext over a network. It sets the Graph endpoint
 *       and defaults to {@link #DEFAULT_BASE_URL}.
 *   <li>{@code saveToSentItems}, a {@link Boolean} that defaults to {@code true}. With
 *       {@code false} the mail is not kept in the Sent Items folder of the sender.
 * </ul>
 *
 * <p>{@link #create(Map, TokenProvider)} throws {@link IllegalArgumentException} for any other
 * option key, for a value of the wrong type, for a {@code baseUrl} that is not an absolute https URL
 * (or an http URL for localhost), and for a missing token provider. The message names the option key and never the
 * value. The token provider of the account supplies the bearer tokens.
 *
 * <p>This class is stateless and thread-safe.
 */
public final class GraphTransportProvider implements TransportProvider {

    /** The Graph v1.0 endpoint of the public cloud, used when the option {@code baseUrl} is absent. */
    public static final String DEFAULT_BASE_URL = "https://graph.microsoft.com/v1.0";

    /** Creates the provider. */
    public GraphTransportProvider() {
    }

    /**
     * Returns the transport id of this provider.
     *
     * @return the id {@code graph}
     */
    @Override
    public String id() {
        return "graph";
    }

    /**
     * Creates a Graph transport for one account.
     *
     * @param options the account options; {@code baseUrl} (a {@link String}) overrides
     *     {@link #DEFAULT_BASE_URL} and {@code saveToSentItems} (a {@link Boolean}, default
     *     {@code true}) says whether Graph keeps a copy in Sent Items
     * @param auth the source of access tokens for Graph
     * @return a new transport
     * @throws IllegalArgumentException if an option key is unknown, a value has the wrong type,
     *     {@code baseUrl} is not an absolute https URL (or an http URL for localhost), or {@code auth} is {@code null}
     */
    @Override
    public MailTransport create(Map<String, Object> options, TokenProvider auth) {
        String baseUrl = DEFAULT_BASE_URL;
        boolean saveToSentItems = true;
        for (Map.Entry<String, Object> option : options.entrySet()) {
            switch (option.getKey()) {
                case "baseUrl" -> baseUrl = checkedBaseUrl(option.getValue());
                case "saveToSentItems" -> {
                    if (!(option.getValue() instanceof Boolean flag)) {
                        throw new IllegalArgumentException("option saveToSentItems must be a Boolean");
                    }
                    saveToSentItems = flag;
                }
                default -> throw new IllegalArgumentException("unknown option '" + option.getKey()
                        + "' for the graph transport; known options: baseUrl, saveToSentItems");
            }
        }
        if (auth == null) {
            throw new IllegalArgumentException("the graph transport needs a TokenProvider (auth)");
        }
        return new GraphTransport(baseUrl, saveToSentItems, auth, GraphTransport.REQUEST_TIMEOUT);
    }

    private static String checkedBaseUrl(Object value) {
        String problem = "option baseUrl must be an absolute https URL (http only for localhost)";
        if (!(value instanceof String text)) {
            throw new IllegalArgumentException(problem);
        }
        URI uri;
        try {
            uri = URI.create(text);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(problem);
        }
        if (!secureOrLoopback(uri)) {
            throw new IllegalArgumentException(problem);
        }
        return text;
    }

    /** True for https, and for http only when the host is this machine (a test server). */
    private static boolean secureOrLoopback(URI uri) {
        String host = uri.getHost();
        if (host == null) {
            return false;
        }
        if ("https".equalsIgnoreCase(uri.getScheme())) {
            return true;
        }
        return "http".equalsIgnoreCase(uri.getScheme())
                && (host.equalsIgnoreCase("localhost") || host.equals("127.0.0.1") || host.equals("[::1]"));
    }
}
