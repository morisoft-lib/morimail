package de.morisoft.morimail;

/**
 * The mail itself is wrong, for example no recipient or a malformed address. The message names
 * the field and does not repeat the rejected value. Fix the mail; a retry will not help.
 *
 * <p>Immutable apart from {@link Throwable} state; safe to pass between threads once thrown.
 */
public class InvalidMailException extends PermanentMailException {

    /**
     * Creates the exception with a message.
     *
     * @param message the detail message
     */
    public InvalidMailException(String message) {
        super(message);
    }

    /**
     * Creates the exception with a message and a cause.
     *
     * @param message the detail message
     * @param cause the cause
     */
    public InvalidMailException(String message, Throwable cause) {
        super(message, cause);
    }
}
