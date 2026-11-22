package de.morisoft.morimail;

import de.morisoft.morimail.graph.GraphTransportProvider;
import java.util.List;

/**
 * The transports that ship inside the library. This is the one place in the core that may name a
 * transport package.
 *
 * <p>This class holds no state and is safe for concurrent use. It is package-private: use
 * {@code TransportRegistry} to look a provider up.
 */
final class BuiltInTransports {

    private BuiltInTransports() {
    }

    /**
     * Returns the built-in providers: the Microsoft Graph transport, id {@code graph}.
     *
     * @return an unmodifiable list of providers
     */
    static List<TransportProvider> providers() {
        return List.of(new GraphTransportProvider());
    }
}
