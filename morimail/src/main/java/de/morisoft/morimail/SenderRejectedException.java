package de.morisoft.morimail;

/**
 * The sending identity is not allowed to send: this mailbox is not usable by this account. Set
 * this mail aside; other mails of the account may still work.
 *
 * <p>Immutable apart from {@link Throwable} state; safe to pass between threads once thrown.
 */
public class SenderRejectedException extends PermanentMailException {

    /**
     * Creates the exception with a message.
     *
     * @param message the detail message
     */
    public SenderRejectedException(String message) {
        super(message);
    }

    /**
     * Creates the exception with a message and a cause.
     *
     * @param message the detail message
     * @param cause the cause
     */
    public SenderRejectedException(String message, Throwable cause) {
        super(message, cause);
    }
}
