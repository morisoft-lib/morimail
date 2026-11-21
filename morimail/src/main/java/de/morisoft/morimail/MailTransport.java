package de.morisoft.morimail;

/**
 * Sends one message through one service for one account.
 *
 * <p>Implementations must be safe for concurrent use by several threads.
 */
public interface MailTransport {

    /**
     * Checks the limits of this transport for the message without sending it.
     *
     * @param message the message, with a sender already applied
     * @throws InvalidMailException if the transport cannot send the message as it is
     */
    void validate(MailMessage message) throws InvalidMailException;

    /**
     * Hands one message to the service.
     *
     * <p>Returning normally means the service accepted the message, not that it was delivered.
     * A send that ended in a timeout may still have been accepted, so retrying after a timeout can
     * deliver the same mail twice: delivery is at-least-once.
     *
     * <p>A failure is reported as a {@link TransientMailException} (retry later), a
     * {@link PermanentMailException} (do not retry this mail) or an {@link AuthRequiredException}
     * (the credentials of the account are unusable).
     *
     * <p>An attachment file that cannot be read at send time, for example because it was deleted
     * after {@link Mail.Builder#build()}, is reported as an {@link InvalidMailException} that names
     * the file and never carries its content.
     *
     * @param message the message, with a sender already applied
     * @throws MailException if the service did not accept the message
     */
    void send(MailMessage message) throws MailException;
}
