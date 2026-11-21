package de.morisoft.morimail;

import java.util.Map;

/**
 * Creates the transport for one account; found in the built-in list or through the service
 * loader for external jars.
 *
 * <p>Implementations must be safe for concurrent use by several threads.
 */
public interface TransportProvider {

    /**
     * Returns the id the account configuration uses to select this transport.
     *
     * @return the transport id
     */
    String id();

    /**
     * Creates a new transport for one account.
     *
     * @param options the account options as an unmodifiable map; the core does not interpret them
     * @param auth the source of access tokens, or {@code null} when the transport needs none
     * @return a new transport
     */
    MailTransport create(Map<String, Object> options, TokenProvider auth);
}
