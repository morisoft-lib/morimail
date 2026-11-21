package de.morisoft.morimail;

/**
 * The credentials or permissions of the account are unusable, so every mail of the account will fail. Fix the credentials or permissions before sending again.
 *
 * <p>The message, the cause and any value given to the constructor are fixed at construction, and an instance may be passed to another thread once it has been thrown; like every {@link Throwable} it is not immutable (for example {@code setStackTrace} and {@code addSuppressed} change it).
 */
public class AuthRequiredException extends MailException {

    /**
     * Creates the exception with a message.
     *
     * @param message the detail message
     */
    public AuthRequiredException(String message) {
        super(message);
    }

    /**
     * Creates the exception with a message and a cause.
     *
     * @param message the detail message
     * @param cause the cause
     */
    public AuthRequiredException(String message, Throwable cause) {
        super(message, cause);
    }
}
