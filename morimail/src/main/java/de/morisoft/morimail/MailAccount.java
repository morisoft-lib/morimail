package de.morisoft.morimail;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * One configured sending account: an id, the transport built for it and an optional default
 * sender.
 *
 * <p>A {@code MailAccount} is immutable and safe for concurrent use; the {@link Builder} is not
 * thread-safe. Whether the transport is safe for concurrent use is stated by its own type.
 */
public final class MailAccount {

    private final String id;
    private final MailTransport transport;
    private final MailAddress defaultFrom;

    private MailAccount(String id, MailTransport transport, MailAddress defaultFrom) {
        this.id = id;
        this.transport = transport;
        this.defaultFrom = defaultFrom;
    }

    /**
     * Starts a builder for an account.
     *
     * @param id the account id that mails name in {@link Mail#accountId()}
     * @return a new builder
     */
    public static Builder builder(String id) {
        return new Builder(id);
    }

    /**
     * Returns the account id.
     *
     * @return the id
     */
    public String id() {
        return id;
    }

    /**
     * Returns the transport built for this account. The same instance is returned every time, so
     * a test can read what a recording transport has accepted.
     *
     * @return the transport
     */
    public MailTransport transport() {
        return transport;
    }

    /**
     * Returns the sender used when a mail has none.
     *
     * @return the default sender, or empty if the account has none
     */
    public Optional<MailAddress> defaultFrom() {
        return Optional.ofNullable(defaultFrom);
    }

    /**
     * Collects the settings of an account and builds it in {@link #build()}.
     *
     * <p>A builder is not thread-safe: use one builder from one thread.
     */
    public static final class Builder {

        private final String id;
        private String transportId;
        private TokenProvider auth;
        private final Map<String, Object> options = new LinkedHashMap<>();
        private String defaultFrom;

        private Builder(String id) {
            this.id = id;
        }

        /**
         * Selects the transport by id, for example the id of a built-in transport.
         *
         * @param transportId the transport id
         * @return this builder
         */
        public Builder transport(String transportId) {
            this.transportId = transportId;
            return this;
        }

        /**
         * Sets the source of access tokens handed to the transport.
         *
         * @param auth the token provider, or {@code null} for none
         * @return this builder
         */
        public Builder auth(TokenProvider auth) {
            this.auth = auth;
            return this;
        }

        /**
         * Sets one transport option. The core does not interpret options. A {@code null} key or
         * value makes {@link #build()} throw {@link NullPointerException}.
         *
         * @param key the option name
         * @param value the option value
         * @return this builder
         */
        public Builder option(String key, Object value) {
            options.put(key, value);
            return this;
        }

        /**
         * Sets the sender used for mails that have none.
         *
         * @param email the sender address
         * @return this builder
         */
        public Builder defaultFrom(String email) {
            this.defaultFrom = email;
            return this;
        }

        /**
         * Checks the settings, creates the transport and builds the account.
         *
         * @return the account
         * @throws IllegalArgumentException if the account id or the transport id is missing, the
         *     transport id is unknown, or the default sender is not a valid address
         * @throws IllegalStateException if the transport registry cannot be loaded: a provider on
         *     the classpath has no id, two providers share an id, or the service loader fails
         * @throws NullPointerException if an option key or value is {@code null}
         */
        public MailAccount build() {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("account id is missing");
            }
            if (transportId == null || transportId.isBlank()) {
                throw new IllegalArgumentException("transport is missing for account '" + id + "'");
            }
            MailAddress from = null;
            if (defaultFrom != null) {
                from = MailAddress.of(defaultFrom);
                String reason = MailAddress.invalidReason(from);
                if (reason != null) {
                    throw new IllegalArgumentException(
                            "defaultFrom of account '" + id + "': " + reason);
                }
            }
            TransportProvider provider = TransportRegistry.load().find(transportId);
            MailTransport transport = provider.create(Map.copyOf(options), auth);
            return new MailAccount(id, transport, from);
        }
    }
}
