package de.morisoft.morimail.test;

import de.morisoft.morimail.InvalidMailException;
import de.morisoft.morimail.MailException;
import de.morisoft.morimail.MailMessage;
import de.morisoft.morimail.MailTransport;
import de.morisoft.morimail.TokenProvider;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

/**
 * A transport that keeps accepted messages in memory instead of sending them. It is a test
 * fixture, shipped in the test-jar of the library.
 *
 * <p>This class is thread-safe: all mutable state is guarded by one lock, the instance itself,
 * and no I/O happens inside the lock. The registry builds one instance per account. Only messages
 * the transport accepted are kept in {@link #sent()}; {@link #attempts()} counts every call of
 * {@link #send(MailMessage)}, failed ones included.
 *
 * <p>A test scripts failures with {@link #failNext(MailException...)}: each queued failure is
 * thrown by one later send, in the order given.
 */
public final class RecordingTransport implements MailTransport {

    private final Map<String, Object> options;
    private final TokenProvider auth;
    private final List<MailMessage> sent = new ArrayList<>();
    private final Deque<MailException> failures = new ArrayDeque<>();
    private int attempts;

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
    public synchronized void send(MailMessage message) throws MailException {
        attempts++;
        MailException failure = failures.poll();
        if (failure != null) {
            throw failure;
        }
        sent.add(message);
    }

    /**
     * Queues failures. Each later send consumes and throws one of them, in the order given; once
     * the queue is empty, sends are accepted again.
     *
     * @param failures the failures to throw, first in line first
     */
    public synchronized void failNext(MailException... failures) {
        this.failures.addAll(List.of(failures));
    }

    /**
     * Fails sends to one recipient (stub, implemented in the GREEN step).
     *
     * @param email the recipient address
     * @param failure the failure
     */
    public synchronized void failFor(String email, MailException failure) {
        // Not implemented yet.
    }

    /**
     * Fails validation (stub, implemented in the GREEN step).
     *
     * @param failure the failure, or {@code null} to clear it
     */
    public synchronized void failValidation(InvalidMailException failure) {
        // Not implemented yet.
    }

    /**
     * Returns how many times {@link #send(MailMessage)} was called, failed calls included.
     *
     * @return the number of send calls
     */
    public synchronized int attempts() {
        return attempts;
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
