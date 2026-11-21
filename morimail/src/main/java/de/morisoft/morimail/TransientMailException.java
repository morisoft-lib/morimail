package de.morisoft.morimail;

import java.time.Duration;
import java.util.Optional;

/**
 * The send failed for now and may succeed later: retry, possibly after the delay from
 * {@link #retryAfter()}.
 *
 * <p>The message, the cause and any value given to the constructor are fixed at construction, and an instance may be passed to another thread once it has been thrown; like every {@link Throwable} it is not immutable (for example {@code setStackTrace} and {@code addSuppressed} change it).
 */
public class TransientMailException extends MailException {

    /** The delay the service asked for, or {@code null} when it gave none. */
    private final Duration retryAfter;

    /**
     * Creates the exception with a message and no retry delay.
     *
     * @param message the detail message
     */
    public TransientMailException(String message) {
        super(message);
        this.retryAfter = null;
    }

    /**
     * Creates the exception with a message, a cause and no retry delay.
     *
     * @param message the detail message
     * @param cause the cause
     */
    public TransientMailException(String message, Throwable cause) {
        super(message, cause);
        this.retryAfter = null;
    }

    /**
     * Creates the exception with a message and the delay after which a retry is sensible.
     *
     * @param message the detail message
     * @param retryAfter the delay the service asked for, or {@code null} for none
     * @throws IllegalArgumentException if the delay is negative
     */
    public TransientMailException(String message, Duration retryAfter) {
        super(message);
        if (retryAfter != null && retryAfter.isNegative()) {
            throw new IllegalArgumentException("retryAfter must not be negative");
        }
        this.retryAfter = retryAfter;
    }

    /**
     * Returns the delay after which a retry is sensible.
     *
     * @return the delay, or an empty value when the service gave none
     */
    public Optional<Duration> retryAfter() {
        return Optional.ofNullable(retryAfter);
    }
}
