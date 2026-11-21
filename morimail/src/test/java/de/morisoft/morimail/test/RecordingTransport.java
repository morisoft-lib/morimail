package de.morisoft.morimail.test;

import de.morisoft.morimail.InvalidMailException;
import de.morisoft.morimail.MailException;
import de.morisoft.morimail.MailMessage;
import de.morisoft.morimail.MailTransport;
import de.morisoft.morimail.TokenProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A transport that keeps accepted messages in memory instead of sending them. It is a test
 * fixture, shipped in the test-jar of the library.
 *
 * <p>This class is thread-safe: all mutable state is guarded by one lock, the instance itself.
 * The registry builds one instance per account. Only messages the transport accepted are kept.
 */
public final class RecordingTransport implements MailTransport {

    private final Map<String, Object> options;
    private final TokenProvider auth;
    private final List<MailMessage> sent = new ArrayList<>();

    /**
     * Creates a transport.
     *
     * @param options the account options, as given to the provider
     * @param auth the token provider of the account, or {@code null}
     */
    public RecordingTransport(Map<String, Object> options, TokenProvider auth) {
        this.options = options;
        this.auth = auth;
    }

    @Override
    public void validate(MailMessage message) throws InvalidMailException {
        // Every message is accepted.
    }

    @Override
    public void send(MailMessage message) {
        synchronized (this) {
            sent.add(message);
        }
    }

    /**
     * Queues failures for the next sends (stub, implemented in the GREEN step).
     *
     * @param failures the failures
     */
    public synchronized void failNext(MailException... failures) {
        // Not implemented yet.
    }

    /**
     * Returns the number of send calls (stub, implemented in the GREEN step).
     *
     * @return the count
     */
    public synchronized int attempts() {
        return 0;
    }

    /**
     * Returns the accepted messages in the order they arrived.
     *
     * @return an unmodifiable snapshot
     */
    public synchronized List<MailMessage> sent() {
        return List.copyOf(sent);
    }

    /**
     * Returns the options the account was built with.
     *
     * @return the options
     */
    public Map<String, Object> options() {
        return options;
    }

    /**
     * Returns the token provider the account was built with.
     *
     * @return the token provider, or {@code null}
     */
    public TokenProvider auth() {
        return auth;
    }
}
