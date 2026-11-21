package de.morisoft.morimail;

import java.util.ArrayList;
import java.util.List;

/**
 * One mail for one account: the account id plus the message to send.
 *
 * <p>This record is immutable and safe to share between threads. Direct construction does not
 * validate anything; {@link Builder#build()} is the validating path.
 *
 * @param accountId the id of the account that sends the mail
 * @param message the message
 */
public record Mail(String accountId, MailMessage message) {

    /**
     * Starts a builder for a mail that is sent through the given account.
     *
     * @param accountId the id of the sending account
     * @return a new builder
     */
    public static Builder using(String accountId) {
        return new Builder(accountId);
    }

    /**
     * Collects the parts of a mail and checks them in {@link #build()}.
     *
     * <p>A builder is not thread-safe: use one builder from one thread. The mail it returns is
     * immutable.
     */
    public static final class Builder {

        private final String accountId;
        private final List<MailAddress> to = new ArrayList<>();
        private MailAddress from;
        private String subject;
        private String text;

        private Builder(String accountId) {
            this.accountId = accountId;
        }

        /**
         * Appends recipients, in the order given. Repeated calls append further recipients.
         *
         * @param emails the recipient addresses
         * @return this builder
         */
        public Builder to(String... emails) {
            for (String email : emails) {
                to.add(MailAddress.of(email));
            }
            return this;
        }

        /**
         * Sets the sender. A later call replaces an earlier one.
         *
         * @param email the sender address
         * @return this builder
         */
        public Builder from(String email) {
            this.from = MailAddress.of(email);
            return this;
        }

        /**
         * Sets the subject.
         *
         * @param subject the subject line
         * @return this builder
         */
        public Builder subject(String subject) {
            this.subject = subject;
            return this;
        }

        /**
         * Sets the plain text body.
         *
         * @param text the body text
         * @return this builder
         */
        public Builder text(String text) {
            this.text = text;
            return this;
        }

        /**
         * Checks the parts and builds the mail. Only the first problem found is reported, in this
         * order: account id, recipients, subject, body, addresses, line break in the subject.
         * The message names the field and never repeats the rejected value.
         *
         * @return the mail
         * @throws InvalidMailException if the mail cannot be sent as it is
         */
        public Mail build() throws InvalidMailException {
            if (accountId == null || accountId.isBlank()) {
                throw new InvalidMailException("accountId is missing");
            }
            if (to.isEmpty()) {
                throw new InvalidMailException("at least one recipient (to, cc or bcc) is required");
            }
            if (subject == null || subject.isBlank()) {
                throw new InvalidMailException("subject is missing");
            }
            if (text == null || text.isBlank()) {
                throw new InvalidMailException("html or text body is missing");
            }
            if (from != null) {
                String reason = MailAddress.invalidReason(from);
                if (reason != null) {
                    throw new InvalidMailException("from: " + reason);
                }
            }
            for (int i = 0; i < to.size(); i++) {
                String reason = MailAddress.invalidReason(to.get(i));
                if (reason != null) {
                    throw new InvalidMailException("to[" + i + "]: " + reason);
                }
            }
            if (MailAddress.hasLineBreak(subject)) {
                throw new InvalidMailException("subject contains a line break");
            }
            return new Mail(accountId, new MailMessage(from, to, List.of(), List.of(), null,
                    subject, null, text, List.of()));
        }
    }
}
