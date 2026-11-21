package de.morisoft.morimail;

import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.TreeMap;

/**
 * Finds a {@link TransportProvider} by id among the built-in providers and the providers that
 * jars on the classpath register through {@link ServiceLoader}.
 *
 * <p>A registry is built fresh by {@link #load()} and never changes afterwards, so it is safe for
 * concurrent use. There is no static state. This class is package-private.
 */
final class TransportRegistry {

    private final Map<String, TransportProvider> providers = new TreeMap<>();

    private TransportRegistry() {
    }

    /**
     * Builds a new registry from the built-in list and the service loader.
     *
     * @return a new registry
     */
    static TransportRegistry load() {
        return of(BuiltInTransports.providers(), ServiceLoader.load(TransportProvider.class));
    }

    /**
     * Builds a registry from the given providers. The built-in ones are inserted first.
     *
     * @param builtIn the built-in providers
     * @param external the providers found through the service loader
     * @return a new registry
     */
    static TransportRegistry of(List<TransportProvider> builtIn,
            Iterable<TransportProvider> external) {
        TransportRegistry registry = new TransportRegistry();
        for (TransportProvider provider : builtIn) {
            registry.providers.put(provider.id(), provider);
        }
        for (TransportProvider provider : external) {
            registry.providers.put(provider.id(), provider);
        }
        return registry;
    }

    /**
     * Returns the provider with the given id.
     *
     * @param id the transport id
     * @return the provider
     * @throws IllegalArgumentException if no provider has this id; the message lists the available
     *     ids
     */
    TransportProvider find(String id) {
        TransportProvider provider = providers.get(id);
        if (provider == null) {
            String available = providers.isEmpty() ? "(none)" : String.join(", ", providers.keySet());
            throw new IllegalArgumentException(
                    "Unknown transport '" + id + "'. Available: " + available);
        }
        return provider;
    }
}
