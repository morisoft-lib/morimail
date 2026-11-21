package de.morisoft.morimail;

/**
 * The credentials or permissions of the account are unusable, so every mail of the account will
 * fail. Fix the credentials or permissions before sending again.
 *
 * <p>Immutable apart from {@link Throwable} state; safe to pass between threads once thrown.
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
