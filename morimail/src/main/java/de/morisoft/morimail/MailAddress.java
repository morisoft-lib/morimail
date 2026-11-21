package de.morisoft.morimail;

/**
 * One mail address with an optional display name.
 *
 * <p>This record is immutable and safe to share between threads. The values are kept exactly as
 * given: no trimming, case folding or other normalization. {@link Mail.Builder#build()} checks the
 * address before a transport sees it.
 *
 * @param email the address, for example {@code reader@example.org}
 * @param displayName the name shown next to the address, or {@code null} for none
 */
public record MailAddress(String email, String displayName) {

    /**
     * Creates an address without a display name.
     *
     * @param email the address
     * @return an address whose display name is {@code null}
     */
    public static MailAddress of(String email) {
        return new MailAddress(email, null);
    }

    /**
     * Creates an address with a display name.
     *
     * @param email the address
     * @param displayName the display name, or {@code null} for none
     * @return the address
     */
    public static MailAddress of(String email, String displayName) {
        return new MailAddress(email, displayName);
    }

    /**
     * Checks an address and says what is wrong with it. The reason never contains the address.
     * A line break is a carriage return or a line feed.
     *
     * @param address the address to check, may be {@code null}
     * @return {@code null} when the address is valid, otherwise the reason text
     */
    static String invalidReason(MailAddress address) {
        if (address == null || address.email() == null || address.email().isEmpty()) {
            return "email is missing";
        }
        String email = address.email();
        int at = email.indexOf('@');
        if (at < 1 || at != email.lastIndexOf('@') || at == email.length() - 1) {
            return "email must contain exactly one '@' with text before and after it";
        }
        if (hasLineBreak(email)) {
            return "email contains a line break";
        }
        if (address.displayName() != null && hasLineBreak(address.displayName())) {
            return "display name contains a line break";
        }
        return null;
    }

    static boolean hasLineBreak(String value) {
        return value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0;
    }
}
