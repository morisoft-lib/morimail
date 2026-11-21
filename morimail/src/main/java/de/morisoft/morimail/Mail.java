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
        private final List<Attachment> attachments = new ArrayList<>();
        private MailAddress from;
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

        // RED stubs for plan 01-05 task 3: the signatures compile, the behaviour is missing.
        public Builder to(MailAddress... addresses) { throw new UnsupportedOperationException(); }
        public Builder cc(String... emails) { throw new UnsupportedOperationException(); }
        public Builder cc(MailAddress... addresses) { throw new UnsupportedOperationException(); }
        public Builder bcc(String... emails) { throw new UnsupportedOperationException(); }
        public Builder bcc(MailAddress... addresses) { throw new UnsupportedOperationException(); }
        public Builder from(MailAddress address) { throw new UnsupportedOperationException(); }
        public Builder replyTo(String email) { throw new UnsupportedOperationException(); }
        public Builder replyTo(MailAddress address) { throw new UnsupportedOperationException(); }
        public Builder attach(Path file, String contentType) {
            throw new UnsupportedOperationException();
        }
        public Builder attach(Attachment attachment) { throw new UnsupportedOperationException(); }

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
            if (to.isEmpty()) {
                throw new InvalidMailException("at least one recipient (to, cc or bcc) is required");
            }
            if (subject == null || subject.isBlank()) {
                throw new InvalidMailException("subject is missing");
            }
            if ((html == null || html.isBlank()) && (text == null || text.isBlank())) {
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
            List<Attachment> resolved = resolveAttachments();
            return new Mail(accountId, new MailMessage(from, to, List.of(), List.of(), null,
                    subject, html, text, resolved));
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
