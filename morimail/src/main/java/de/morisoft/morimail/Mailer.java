package de.morisoft.morimail;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;

/**
 * Sends mail through a set of accounts, one mail at a time.
 *
 * <p>A {@code Mailer} is immutable and safe for concurrent use. {@link #send(Mail)} calls the
 * transport once: there is no retry and no de-duplication, so the caller decides what to do after
 * a failure.
 */
public final class Mailer {

    private final Map<String, MailAccount> accounts;

    private Mailer(Map<String, MailAccount> accounts) {
        this.accounts = accounts;
    }

    /**
     * Creates a mailer for the given accounts. Zero accounts is allowed; every send is then
     * rejected as an unknown account.
     *
     * @param accounts the accounts
     * @return a new mailer
     * @throws IllegalArgumentException if two accounts have the same id
     */
    public static Mailer of(MailAccount... accounts) {
        Map<String, MailAccount> byId = new HashMap<>();
        for (MailAccount account : accounts) {
            if (byId.put(account.id(), account) != null) {
                throw new IllegalArgumentException("Duplicate account id '" + account.id() + "'");
            }
        }
        return new Mailer(Map.copyOf(byId));
    }

    /**
     * Runs the checks of {@link #send(Mail)} without sending: the account lookup, the default
     * sender and the validation of the transport.
     *
     * @param mail the mail
     * @throws PermanentMailException if the account is unknown, or, as the subclass
     *     {@link InvalidMailException}, if the mail has no sender or the transport rejects it
     */
    public void validate(Mail mail) throws PermanentMailException {
        prepare(mail);
    }

    /**
     * Sends the mail once through the account the mail names.
     *
     * <p>Returning normally means the service accepted the mail, not that it was delivered.
     *
     * @param mail the mail
     * @throws MailException if the account is unknown, the mail is invalid or the transport fails
     */
    public void send(Mail mail) throws MailException {
        Prepared prepared = prepare(mail);
        prepared.account().transport().send(prepared.message());
    }

    private Prepared prepare(Mail mail) throws PermanentMailException {
        MailAccount account = accounts.get(mail.accountId());
        if (account == null) {
            String known = accounts.isEmpty() ? "(none)" : String.join(", ", new TreeSet<>(accounts.keySet()));
            throw new PermanentMailException(
                    "Unknown account '" + mail.accountId() + "'. Known accounts: " + known);
        }
        MailMessage message = mail.message();
        if (message.from() == null) {
            MailAddress from = account.defaultFrom().orElseThrow(() -> new InvalidMailException(
                    "No sender: set from() on the mail or defaultFrom() on account '"
                            + account.id() + "'"));
            message = new MailMessage(from, message.to(), message.cc(), message.bcc(),
                    message.replyTo(), message.subject(), message.html(), message.text(),
                    message.attachments());
        }
        account.transport().validate(message);
        return new Prepared(account, message);
    }

    private record Prepared(MailAccount account, MailMessage message) {
    }
}
