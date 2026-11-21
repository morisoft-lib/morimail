package de.morisoft.morimail;

/**
 * Base class of every checked failure of this library. Catch one of the subclasses to decide what
 * to do next; this class alone does not say whether a retry can help.
 *
 * <p>The message and the cause are fixed at construction, and an instance may be passed to
 * another thread once it has been thrown. Like every {@link Throwable} it is not fully immutable:
 * {@code setStackTrace} and {@code addSuppressed} change it.
 */
public class MailException extends Exception {

    /**
     * Creates the exception with a message.
     *
     * @param message the detail message
     */
    public MailException(String message) {
        super(message);
    }

    /**
     * Creates the exception with a message and a cause.
     *
     * @param message the detail message
     * @param cause the cause
     */
    public MailException(String message, Throwable cause) {
        super(message, cause);
    }
}
