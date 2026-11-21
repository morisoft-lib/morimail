package de.morisoft.morimail;

/**
 * The send failed and will not succeed as it is: do not retry this mail unchanged.
 *
 * <p>Immutable apart from {@link Throwable} state; safe to pass between threads once thrown.
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
