package de.morisoft.morimail.test;

import de.morisoft.morimail.InvalidMailException;
import de.morisoft.morimail.MailAddress;
import de.morisoft.morimail.MailException;
import de.morisoft.morimail.MailMessage;
import de.morisoft.morimail.MailTransport;
import de.morisoft.morimail.TokenProvider;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
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
 * <p>A test scripts failures, and {@link #send(MailMessage)} checks them in this order:
 * <ol>
 *   <li>{@link #failFor(String, MailException)}: every send that addresses the recipient in to,
 *       cc or bcc fails; the entry stays and does not touch the queue;
 *   <li>{@link #failNext(MailException...)}: each queued failure is thrown by one send, first in
 *       line first;
 *   <li>otherwise the message is recorded.
 * </ol>
 * {@link #failValidation(InvalidMailException)} makes {@link #validate(MailMessage)} throw, which
 * {@code Mailer} calls before it sends, so a failed validation is not an attempt.
 */
public final class RecordingTransport implements MailTransport {

    private final Map<String, Object> options;
    private final TokenProvider auth;
    private final List<MailMessage> sent = new ArrayList<>();
    private final Deque<MailException> failures = new ArrayDeque<>();
    private final Map<String, MailException> recipientFailures = new HashMap<>();
    private InvalidMailException validationFailure;
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
    public synchronized void validate(MailMessage message) throws InvalidMailException {
        if (validationFailure != null) {
            throw validationFailure;
        }
    }

    @Override
    public synchronized void send(MailMessage message) throws MailException {
        attempts++;
        for (List<MailAddress> recipients : List.of(message.to(), message.cc(), message.bcc())) {
            for (MailAddress recipient : recipients) {
                MailException perRecipient =
                        recipientFailures.get(recipient.email().toLowerCase(Locale.ROOT));
                if (perRecipient != null) {
                    throw perRecipient;
                }
            }
        }
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
     * Fails every send that addresses {@code email} in to, cc or bcc. The address is matched
     * case-insensitively. The failure stays until it is replaced by another call for the same
     * address, and it wins over {@link #failNext(MailException...)} without consuming the queue.
     *
     * @param email the recipient address
     * @param failure the failure to throw
     */
    public synchronized void failFor(String email, MailException failure) {
        recipientFailures.put(email.toLowerCase(Locale.ROOT), failure);
    }

    /**
     * Makes {@link #validate(MailMessage)} throw the failure until this method is called with
     * {@code null}.
     *
     * @param failure the failure to throw, or {@code null} to clear it
     */
    public synchronized void failValidation(InvalidMailException failure) {
        this.validationFailure = failure;
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
