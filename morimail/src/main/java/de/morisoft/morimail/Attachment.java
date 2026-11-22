package de.morisoft.morimail;

import java.nio.file.Path;

/**
 * A file attached to a mail.
 *
 * <p>This record is immutable and safe to share between threads; it holds a path, not the file
 * content. {@code contentId} is {@code null} for a regular attachment; a non-null value
 * marks an inline image, which the HTML body refers to as {@code cid:} followed by the content
 * id, and which {@link Mail.Builder#build()} checks.
 *
 * @param filename the name shown to the recipient
 * @param contentType the media type, for example {@code application/pdf}
 * @param file the file to attach
 * @param contentId the content id of an inline image, or {@code null} for a regular attachment
 */
public record Attachment(String filename, String contentType, Path file, String contentId) {
}
