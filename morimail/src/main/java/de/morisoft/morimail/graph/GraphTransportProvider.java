package de.morisoft.morimail.graph;

import de.morisoft.morimail.MailTransport;
import de.morisoft.morimail.TokenProvider;
import de.morisoft.morimail.TransportProvider;
import java.util.Map;

/**
 * Provides the {@code graph} transport, which sends mail through the Microsoft Graph
 * {@code sendMail} call. It is one of the built-in transports.
 *
 * <p>The account option {@code baseUrl} sets the Graph endpoint; it defaults to
 * {@link #DEFAULT_BASE_URL}. The token provider of the account supplies the bearer tokens.
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
     * @param options the account options; {@code baseUrl} overrides {@link #DEFAULT_BASE_URL}
     * @param auth the source of access tokens for Graph
     * @return a new transport
     */
    @Override
    public MailTransport create(Map<String, Object> options, TokenProvider auth) {
        Object baseUrl = options.get("baseUrl");
        String url = baseUrl instanceof String text ? text : DEFAULT_BASE_URL;
        return new GraphTransport(url, true, auth, GraphTransport.REQUEST_TIMEOUT);
    }
}
