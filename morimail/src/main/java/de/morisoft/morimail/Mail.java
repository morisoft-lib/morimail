package de.morisoft.morimail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

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

        private static final Map<String, String> CONTENT_TYPES = Map.ofEntries(
                Map.entry("pdf", "application/pdf"),
                Map.entry("png", "image/png"),
                Map.entry("jpg", "image/jpeg"),
                Map.entry("jpeg", "image/jpeg"),
                Map.entry("gif", "image/gif"),
                Map.entry("txt", "text/plain"),
                Map.entry("html", "text/html"),
                Map.entry("htm", "text/html"),
                Map.entry("csv", "text/csv"),
                Map.entry("zip", "application/zip"),
                Map.entry("docx",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
                Map.entry("xlsx",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));

        private final String accountId;
        private final List<MailAddress> to = new ArrayList<>();
        private final List<MailAddress> cc = new ArrayList<>();
        private final List<MailAddress> bcc = new ArrayList<>();
        private final List<Attachment> attachments = new ArrayList<>();
        private MailAddress from;
        private MailAddress replyTo;
        private String subject;
        private String html;
        private String text;
        private MailTemplate template;
        private Map<String, Object> data;
        private boolean explicitContent;

        private Builder(String accountId) {
            this.accountId = accountId;
        }

        /**
         * Appends primary recipients, in the order given. Repeated calls append further
         * recipients; nothing is merged or removed, so an address given twice is kept twice. Pass
         * one address per argument. To give a display name use
         * {@link #to(MailAddress...)} with {@link MailAddress#of(String, String)}.
         *
         * @param emails the recipient addresses
         * @return this builder
         */
        public Builder to(String... emails) {
            return append(to, emails);
        }

        /**
         * Appends primary recipients, in the order given. Repeated calls append further
         * recipients. Use {@link MailAddress#of(String, String)} for an address with a display
         * name.
         *
         * @param addresses the recipients
         * @return this builder
         */
        public Builder to(MailAddress... addresses) {
            return append(to, addresses);
        }

        /**
         * Appends copy recipients, in the order given. Repeated calls append further recipients.
         * Pass one address per argument; use {@link #cc(MailAddress...)} with
         * {@link MailAddress#of(String, String)} for a display name.
         *
         * @param emails the recipient addresses
         * @return this builder
         */
        public Builder cc(String... emails) {
            return append(cc, emails);
        }

        /**
         * Appends copy recipients, in the order given. Repeated calls append further recipients.
         *
         * @param addresses the recipients
         * @return this builder
         */
        public Builder cc(MailAddress... addresses) {
            return append(cc, addresses);
        }

        /**
         * Appends blind copy recipients, in the order given. Repeated calls append further
         * recipients. Pass one address per argument; use {@link #bcc(MailAddress...)} with
         * {@link MailAddress#of(String, String)} for a display name.
         *
         * @param emails the recipient addresses
         * @return this builder
         */
        public Builder bcc(String... emails) {
            return append(bcc, emails);
        }

        /**
         * Appends blind copy recipients, in the order given. Repeated calls append further
         * recipients.
         *
         * @param addresses the recipients
         * @return this builder
         */
        public Builder bcc(MailAddress... addresses) {
            return append(bcc, addresses);
        }

        private Builder append(List<MailAddress> target, String[] emails) {
            for (String email : emails) {
                target.add(MailAddress.of(email));
            }
            return this;
        }

        private Builder append(List<MailAddress> target, MailAddress[] addresses) {
            for (MailAddress address : addresses) {
                target.add(address);
            }
            return this;
        }

        /**
         * Sets the sender. A later call replaces an earlier one. Without a sender the account's
         * default sender is used.
         *
         * @param email the sender address
         * @return this builder
         */
        public Builder from(String email) {
            this.from = MailAddress.of(email);
            return this;
        }

        /**
         * Sets the sender, with an optional display name. A later call replaces an earlier one.
         *
         * @param address the sender
         * @return this builder
         */
        public Builder from(MailAddress address) {
            this.from = address;
            return this;
        }

        /**
         * Sets the reply address. A later call replaces an earlier one.
         *
         * @param email the reply address
         * @return this builder
         */
        public Builder replyTo(String email) {
            this.replyTo = MailAddress.of(email);
            return this;
        }

        /**
         * Sets the reply address, with an optional display name. A later call replaces an earlier
         * one.
         *
         * @param address the reply address
         * @return this builder
         */
        public Builder replyTo(MailAddress address) {
            this.replyTo = address;
            return this;
        }

        /**
         * Sets the subject. {@link #build()} rejects a carriage return or line feed in it. A
         * subject that comes from a template value is stricter: every Unicode line break
         * (including U+0085, U+2028 and U+2029) becomes a space.
         *
         * @param subject the subject line
         * @return this builder
         */
        public Builder subject(String subject) {
            this.subject = subject;
            this.explicitContent = true;
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
            this.explicitContent = true;
            return this;
        }

        /**
         * Sets the HTML body.
         *
         * @param html the HTML body
         * @return this builder
         */
        public Builder html(String html) {
            this.html = html;
            this.explicitContent = true;
            return this;
        }

        /**
         * Uses a template for subject and bodies. The placeholders are filled from {@code data}
         * in {@link #build()}. A template cannot be combined with {@link #subject(String)},
         * {@link #html(String)} or {@link #text(String)}; {@code build()} rejects the mix. A later
         * call replaces an earlier one.
         *
         * <p>The map is copied at call time, so a later change to the caller's map is not
         * observed. A key mapped to {@code null} renders as the text {@code null}.
         *
         * @param template the template
         * @param data the values by placeholder name, for example a row of a CSV file
         * @return this builder
         * @throws NullPointerException if an argument is {@code null}
         */
        public Builder template(MailTemplate template, Map<String, ?> data) {
            Objects.requireNonNull(template, "template");
            Objects.requireNonNull(data, "data");
            this.template = template;
            this.data = new HashMap<>(data);
            return this;
        }

        /**
         * Attaches a file. The file name shown to the recipient is the last element of the path,
         * and the content type is resolved in {@link #build()}: first from a built-in table of
         * twelve extensions (pdf, png, jpg, jpeg, gif, txt, html, htm, csv, zip, docx, xlsx,
         * matched case-insensitively), which gives the same type on every system. For any other
         * extension the type comes from {@link Files#probeContentType(Path)}, which depends on
         * the detectors installed on the platform and is best effort; the final fallback is
         * {@code application/octet-stream}. The file is checked in {@code build()}, not here.
         *
         * @param file the file to attach
         * @return this builder
         * @throws NullPointerException if {@code file} is {@code null}
         */
        public Builder attach(Path file) {
            Objects.requireNonNull(file, "file");
            attachments.add(new Attachment(null, null, file, null));
            return this;
        }

        /**
         * Attaches a file with an explicit content type. The file name shown to the recipient is
         * the last element of the path. The file is checked in {@link #build()}, not here.
         *
         * @param file the file to attach
         * @param contentType the media type, for example {@code application/pdf}
         * @return this builder
         * @throws NullPointerException if an argument is {@code null}
         */
        public Builder attach(Path file, String contentType) {
            Objects.requireNonNull(file, "file");
            Objects.requireNonNull(contentType, "contentType");
            attachments.add(new Attachment(null, contentType, file, null));
            return this;
        }

        /**
         * Attaches a file described by an {@link Attachment}, for a file name or content type of
         * your choice. A {@code null} file name or content type is filled in by {@link #build()}
         * as for {@link #attach(Path)}. {@code build()} rejects a {@code null} file and a
         * non-null content id.
         *
         * @param attachment the attachment
         * @return this builder
         * @throws NullPointerException if {@code attachment} is {@code null}
         */
        public Builder attach(Attachment attachment) {
            Objects.requireNonNull(attachment, "attachment");
            attachments.add(attachment);
            return this;
        }

        /**
         * Checks the parts and builds the mail. Only the first problem found is reported, in this
         * order: account id, template mixed with explicit content, template rendering,
         * recipients, subject, body, addresses, line break in the subject, attachments. The
         * message names the field and never repeats the rejected value. Calling this method does
         * not change the builder, so it can be called again.
         *
         * @return the mail
         * @throws InvalidMailException if the mail cannot be sent as it is
         */
        public Mail build() throws InvalidMailException {
            if (accountId == null || accountId.isBlank()) {
                throw new InvalidMailException("accountId is missing");
            }
            if (template != null && explicitContent) {
                throw new InvalidMailException(
                        "template(...) cannot be combined with subject(), html() or text()");
            }
            String subject = this.subject;
            String html = this.html;
            String text = this.text;
            if (template != null) {
                MailTemplate rendered = TemplateRenderer.render(template, data);
                subject = rendered.subject();
                html = rendered.html();
                text = rendered.text();
            }
            if (to.isEmpty() && cc.isEmpty() && bcc.isEmpty()) {
                throw new InvalidMailException("at least one recipient (to, cc or bcc) is required");
            }
            if (subject == null || subject.isBlank()) {
                throw new InvalidMailException("subject is missing");
            }
            if ((html == null || html.isBlank()) && (text == null || text.isBlank())) {
                throw new InvalidMailException("html or text body is missing");
            }
            checkAddress("from", from);
            checkAddresses("to", to);
            checkAddresses("cc", cc);
            checkAddresses("bcc", bcc);
            checkAddress("replyTo", replyTo);
            if (MailAddress.hasLineBreak(subject)) {
                throw new InvalidMailException("subject contains a line break");
            }
            List<Attachment> resolved = resolveAttachments();
            return new Mail(accountId, new MailMessage(from, to, cc, bcc, replyTo,
                    subject, html, text, resolved));
        }

        private static void checkAddress(String field, MailAddress address)
                throws InvalidMailException {
            if (address != null) {
                String reason = MailAddress.invalidReason(address);
                if (reason != null) {
                    throw new InvalidMailException(field + ": " + reason);
                }
            }
        }

        private static void checkAddresses(String field, List<MailAddress> addresses)
                throws InvalidMailException {
            for (int i = 0; i < addresses.size(); i++) {
                MailAddress address = addresses.get(i);
                if (address == null) {
                    throw new InvalidMailException(field + "[" + i + "]: address is missing");
                }
                String reason = MailAddress.invalidReason(address);
                if (reason != null) {
                    throw new InvalidMailException(field + "[" + i + "]: " + reason);
                }
            }
        }

        private List<Attachment> resolveAttachments() throws InvalidMailException {
            List<Attachment> resolved = new ArrayList<>(attachments.size());
            for (int i = 0; i < attachments.size(); i++) {
                Attachment attachment = attachments.get(i);
                String field = "attachments[" + i + "]: ";
                Path file = attachment.file();
                if (file == null) {
                    throw new InvalidMailException(field + "file is missing");
                }
                Path fileName = file.getFileName();
                if (fileName == null) {
                    throw new InvalidMailException(field + "path has no file name");
                }
                if (!Files.exists(file)) {
                    throw new InvalidMailException(field + "file " + fileName + " not found");
                }
                if (!Files.isRegularFile(file)) {
                    throw new InvalidMailException(field + fileName + " is not a regular file");
                }
                if (!Files.isReadable(file)) {
                    throw new InvalidMailException(field + fileName + " is not readable");
                }
                if (attachment.contentId() != null) {
                    throw new InvalidMailException(field
                            + "contentId is not supported (inline images are a later milestone)");
                }
                String filename = attachment.filename() != null
                        ? attachment.filename() : fileName.toString();
                String contentType = attachment.contentType() != null
                        ? attachment.contentType() : contentTypeOf(file, filename);
                if (filename.isBlank() || MailAddress.hasLineBreak(filename)) {
                    throw new InvalidMailException(
                            field + "filename is blank or contains a line break");
                }
                if (MailAddress.hasLineBreak(contentType) || contentType.indexOf('/') < 1) {
                    throw new InvalidMailException(field + "contentType is not a media type");
                }
                resolved.add(new Attachment(filename, contentType, file, null));
            }
            return resolved;
        }

        private static String contentTypeOf(Path file, String filename) {
            int dot = filename.lastIndexOf('.');
            if (dot >= 0) {
                String known = CONTENT_TYPES.get(
                        filename.substring(dot + 1).toLowerCase(Locale.ROOT));
                if (known != null) {
                    return known;
                }
            }
            try {
                String probed = Files.probeContentType(file);
                if (probed != null) {
                    return probed;
                }
            } catch (IOException e) {
                // Falls through to the default type.
            }
            return "application/octet-stream";
        }
    }
}
