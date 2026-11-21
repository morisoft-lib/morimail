package de.morisoft.morimail.test;

import de.morisoft.morimail.MailTransport;
import de.morisoft.morimail.TokenProvider;
import de.morisoft.morimail.TransportProvider;
import java.util.Map;

/**
 * Provides the {@code recording} transport for tests. It is registered through the service
 * loader in the test-jar of the library.
 *
 * <p>This class is stateless and thread-safe.
 */
public final class RecordingTransportProvider implements TransportProvider {

    @Override
    public String id() {
        return "recording";
    }

    @Override
    public MailTransport create(Map<String, Object> options, TokenProvider auth) {
        return new RecordingTransport(options, auth);
    }
}
