package de.morisoft.morimail;

/**
 * Supplies access tokens for a transport.
 *
 * <p>Implementations must be safe for concurrent use by several threads.
 */
@FunctionalInterface
public interface TokenProvider {

    /**
     * Returns an access token for the scope, from a cache if the implementation has one.
     *
     * @param scope the scope the token is for
     * @return the access token
     * @throws MailException if no token can be obtained
     */
    String accessToken(String scope) throws MailException;

    /**
     * Returns a token after the service rejected {@code rejectedToken}. The default asks
     * {@link #accessToken(String)} again. A caching implementation overrides it to drop the
     * cached token when it equals {@code rejectedToken}.
     *
     * @param scope the scope the token is for
     * @param rejectedToken the token the service rejected
     * @return an access token that is not the rejected one when the implementation can tell
     * @throws MailException if no token can be obtained
     */
    default String freshAccessToken(String scope, String rejectedToken) throws MailException {
        return accessToken(scope);
    }
}
