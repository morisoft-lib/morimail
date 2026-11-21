package de.morisoft.morimail;

import java.nio.file.Path;

/**
 * A file attached to a mail.
 *
 * <p>This record is immutable and safe to share between threads; it holds a path, not the file
 * content. {@code contentId} is {@code null} for a regular attachment; the first release rejects a
 * non-null value in {@link Mail.Builder#build()}.
 *
 * @param filename the name shown to the recipient
 * @param contentType the media type, for example {@code application/pdf}
 * @param file the file to attach
 * @param contentId the content id of an inline image, or {@code null} for a regular attachment
 */
public record Attachment(String filename, String contentType, Path file, String contentId) {
}
