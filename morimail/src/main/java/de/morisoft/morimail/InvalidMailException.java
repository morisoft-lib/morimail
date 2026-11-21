package de.morisoft.morimail;

/**
 * The mail itself is wrong, for example no recipient or a malformed address. The message names the field and does not repeat the rejected value. Fix the mail; a retry will not help.
 *
 * <p>The message, the cause and any value given to the constructor are fixed at construction, and an instance may be passed to another thread once it has been thrown; like every {@link Throwable} it is not immutable (for example {@code setStackTrace} and {@code addSuppressed} change it).
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
