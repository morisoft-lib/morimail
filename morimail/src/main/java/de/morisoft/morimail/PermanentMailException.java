package de.morisoft.morimail;

/**
 * The send failed and will not succeed as it is: do not retry this mail unchanged.
 *
 * <p>The message, the cause and any value given to the constructor are fixed at construction, and an instance may be passed to another thread once it has been thrown; like every {@link Throwable} it is not immutable (for example {@code setStackTrace} and {@code addSuppressed} change it).
 */
public class PermanentMailException extends MailException {

    /**
     * Creates the exception with a message.
     *
     * @param message the detail message
     */
    public PermanentMailException(String message) {
        super(message);
    }

    /**
     * Creates the exception with a message and a cause.
     *
     * @param message the detail message
     * @param cause the cause
     */
    public PermanentMailException(String message, Throwable cause) {
        super(message, cause);
    }
}
