package de.morisoft.morimail;

import java.util.List;

/**
 * The message a transport sends: sender, recipients, subject, bodies and attachments.
 *
 * <p>This record is immutable and safe to share between threads; the lists are unmodifiable
 * copies. Direct construction does not validate anything, and a {@code null} list throws
 * {@link NullPointerException}. {@link Mail.Builder#build()} is the validating path.
 *
 * @param from the sender, or {@code null} to use the account's default sender
 * @param to the primary recipients
 * @param cc the copy recipients
 * @param bcc the blind copy recipients
 * @param replyTo the reply address, or {@code null} for none
 * @param subject the subject line
 * @param html the HTML body, or {@code null} for none
 * @param text the plain text body, or {@code null} for none
 * @param attachments the attached files
 */
public record MailMessage(MailAddress from, List<MailAddress> to, List<MailAddress> cc,
        List<MailAddress> bcc, MailAddress replyTo, String subject, String html, String text,
        List<Attachment> attachments) {

    /**
     * Copies the four lists so the message cannot change after construction.
     *
     * @throws NullPointerException if one of the four lists is {@code null}
     */
    public MailMessage {
        to = List.copyOf(to);
        cc = List.copyOf(cc);
        bcc = List.copyOf(bcc);
        attachments = List.copyOf(attachments);
    }
}
