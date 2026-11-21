package de.morisoft.morimail;

/**
 * A mail template: subject, HTML body and an optional plain text body, each with placeholders.
 *
 * <p>A placeholder is written {@code $$name$$}, where the name is one or more of the ASCII
 * characters {@code A-Z a-z 0-9 _ . -}. {@link Mail.Builder#template(MailTemplate, java.util.Map)}
 * replaces every placeholder with the value of the same key in the data map when the mail is
 * built. The rules per part:
 *
 * <ul>
 *   <li>In the HTML part a value is HTML-escaped: {@code &}, {@code <}, {@code >}, {@code "} and
 *       the apostrophe become character references. {@code $$!name$$} inserts the value
 *       unchanged. Use it only for values you trust: escaping does not make a value safe inside
 *       an attribute such as {@code href} or {@code style}, or inside a {@code script} element.
 *   <li>In the subject every line break inside a value becomes one space. A line break is any
 *       Unicode line break, not only carriage return and line feed.
 *   <li>In the text part a value is inserted unchanged.
 * </ul>
 *
 * <p>A placeholder whose name is not a key of the data map makes {@code build()} fail. A value is
 * inserted exactly as it is, and it is never scanned for further placeholders.
 *
 * <p>This record is immutable and safe to share between threads.
 *
 * @param subject the subject line, may contain placeholders
 * @param html the HTML body, may contain placeholders
 * @param text the plain text body, may contain placeholders, or {@code null} for none
 */
public record MailTemplate(String subject, String html, String text) {
}
