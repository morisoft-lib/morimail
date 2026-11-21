package de.morisoft.morimail;

import java.util.List;
import java.util.Map;
import java.util.ServiceConfigurationError;
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
     * Builds a registry from the given providers. The built-in ones are registered first. Ids are
     * compared exactly, so {@code recording} and {@code Recording} are different ids.
     *
     * <p>An external provider that reuses the id of a built-in or of an earlier external provider
     * is an error, never an override: a transport id must always select the same provider. This is
     * configuration integrity, not a sandbox, because the service loader has already created
     * every provider when its id is read.
     *
     * @param builtIn the built-in providers
     * @param external the providers found through the service loader
     * @return a new registry
     * @throws IllegalStateException if a provider has no id, an id is registered twice, or the
     *     service loader fails; the messages name ids and provider classes only, and the service
     *     loader's own error is kept as the cause
     */
    static TransportRegistry of(List<TransportProvider> builtIn,
            Iterable<TransportProvider> external) {
        TransportRegistry registry = new TransportRegistry();
        for (TransportProvider provider : builtIn) {
            registry.register(provider, builtIn);
        }
        try {
            for (TransportProvider provider : external) {
                registry.register(provider, builtIn);
            }
        } catch (ServiceConfigurationError e) {
            // Lazy discovery can fail in iterator(), hasNext() or next(). The wrapper text is
            // fixed: the error's own message is provider-generated and stays in the cause.
            throw new IllegalStateException(
                    "Cannot load a transport provider through ServiceLoader (see the cause)", e);
        }
        return registry;
    }

    private void register(TransportProvider provider, List<TransportProvider> builtIn) {
        String id = provider.id();
        if (id == null || id.isBlank()) {
            throw new IllegalStateException(
                    "Transport provider " + provider.getClass().getName() + " has no id");
        }
        TransportProvider existing = providers.putIfAbsent(id, provider);
        if (existing != null) {
            throw new IllegalStateException("Transport id '" + id + "' from "
                    + provider.getClass().getName() + " is already registered by "
                    + existing.getClass().getName() + (builtIn.contains(existing) ? " (built-in)" : ""));
        }
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
