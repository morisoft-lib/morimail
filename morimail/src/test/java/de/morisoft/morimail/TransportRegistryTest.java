package de.morisoft.morimail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.morisoft.morimail.test.RecordingTransportProvider;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.ServiceConfigurationError;
import org.junit.jupiter.api.Test;

class TransportRegistryTest {

    private static final String WRAPPER = "Cannot load a transport provider through ServiceLoader (see the cause)";

    private abstract static class StubProvider implements TransportProvider {
        private final String id;

        StubProvider(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public MailTransport create(Map<String, Object> options, TokenProvider auth) {
            throw new UnsupportedOperationException("not used in these tests");
        }
    }

    private static final class FirstProvider extends StubProvider {
        FirstProvider(String id) {
            super(id);
        }
    }

    private static final class SecondProvider extends StubProvider {
        SecondProvider(String id) {
            super(id);
        }
    }

    private static List<TransportProvider> none() {
        return List.of();
    }

    @Test
    void duplicateBuiltInAndExternalIdIsRejected() {
        TransportProvider builtIn = new FirstProvider("x");
        TransportProvider external = new SecondProvider("x");

        assertThatThrownBy(() -> TransportRegistry.of(List.of(builtIn), List.of(external)))
                .isExactlyInstanceOf(IllegalStateException.class)
                .hasMessageContaining("'x'")
                .hasMessageContaining(FirstProvider.class.getName())
                .hasMessageContaining(SecondProvider.class.getName())
                .hasMessageContaining("(built-in)");
    }

    @Test
    void duplicateExternalIdsAreRejected() {
        TransportProvider first = new FirstProvider("x");
        TransportProvider second = new SecondProvider("x");

        assertThatThrownBy(() -> TransportRegistry.of(none(), List.of(first, second)))
                .isExactlyInstanceOf(IllegalStateException.class)
                .hasMessageContaining("'x'")
                .hasMessageContaining(FirstProvider.class.getName())
                .hasMessageContaining(SecondProvider.class.getName())
                .hasMessageNotContaining("(built-in)");
    }

    @Test
    void nullOrBlankProviderIdIsRejected() {
        assertThatThrownBy(() -> TransportRegistry.of(none(), List.of(new FirstProvider(null))))
                .isExactlyInstanceOf(IllegalStateException.class)
                .hasMessageContaining(FirstProvider.class.getName());
        assertThatThrownBy(() -> TransportRegistry.of(none(), List.of(new SecondProvider("  "))))
                .isExactlyInstanceOf(IllegalStateException.class)
                .hasMessageContaining(SecondProvider.class.getName());
        assertThatThrownBy(() -> TransportRegistry.of(List.of(new FirstProvider("")), none()))
                .isExactlyInstanceOf(IllegalStateException.class)
                .hasMessageContaining(FirstProvider.class.getName());
    }

    @Test
    void serviceConfigurationErrorFromIteratorIsWrapped() {
        ServiceConfigurationError error = new ServiceConfigurationError("iterator broke");
        Iterable<TransportProvider> external = () -> {
            throw error;
        };

        assertThatThrownBy(() -> TransportRegistry.of(none(), external))
                .isExactlyInstanceOf(IllegalStateException.class)
                .hasMessage(WRAPPER)
                .hasCause(error);
    }

    @Test
    void serviceConfigurationErrorFromHasNextIsWrapped() {
        ServiceConfigurationError error = new ServiceConfigurationError("hasNext broke");
        Iterable<TransportProvider> external = () -> new Iterator<>() {
            private int calls;

            @Override
            public boolean hasNext() {
                if (calls >= 1) {
                    throw error;
                }
                return true;
            }

            @Override
            public TransportProvider next() {
                calls++;
                return new FirstProvider("one");
            }
        };

        assertThatThrownBy(() -> TransportRegistry.of(none(), external))
                .isExactlyInstanceOf(IllegalStateException.class)
                .hasMessage(WRAPPER)
                .hasCause(error);
    }

    @Test
    void serviceConfigurationErrorFromNextIsWrapped() {
        ServiceConfigurationError error = new ServiceConfigurationError("next broke");
        Iterable<TransportProvider> external = () -> new Iterator<>() {
            private int calls;

            @Override
            public boolean hasNext() {
                return true;
            }

            @Override
            public TransportProvider next() {
                if (calls++ >= 1) {
                    throw error;
                }
                return new FirstProvider("one");
            }
        };

        assertThatThrownBy(() -> TransportRegistry.of(none(), external))
                .isExactlyInstanceOf(IllegalStateException.class)
                .hasMessage(WRAPPER)
                .hasCause(error);
    }

    @Test
    void wrapperMessageOmitsProviderGeneratedText() {
        ServiceConfigurationError error = new ServiceConfigurationError("provider detail 42");
        Iterable<TransportProvider> external = () -> {
            throw error;
        };

        assertThatThrownBy(() -> TransportRegistry.of(none(), external))
                .isExactlyInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining("provider detail 42")
                .hasMessageNotContaining("detail");
        assertThat(error).hasMessage("provider detail 42");
    }

    @Test
    void idsAreCaseSensitive() {
        TransportProvider lower = new FirstProvider("recording");
        TransportProvider upper = new SecondProvider("Recording");

        assertThatCode(() -> TransportRegistry.of(List.of(lower), List.of(upper)))
                .doesNotThrowAnyException();
        TransportRegistry registry = TransportRegistry.of(List.of(lower), List.of(upper));
        assertThat(registry.find("recording")).isSameAs(lower);
        assertThat(registry.find("Recording")).isSameAs(upper);
    }

    @Test
    void emptyRegistryReportsAvailableNone() {
        TransportRegistry registry = TransportRegistry.of(none(), none());

        assertThatThrownBy(() -> registry.find("x"))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'x'")
                .hasMessageEndingWith("Available: (none)");
    }

    @Test
    void unknownIdListsAvailableIdsSorted() {
        TransportRegistry registry = TransportRegistry.of(List.of(new FirstProvider("b")),
                List.of(new SecondProvider("a")));

        assertThatThrownBy(() -> registry.find("x"))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Available: a, b");
    }

    @Test
    void loadFindsRecordingThroughServiceLoader() {
        assertThat(TransportRegistry.load().find("recording"))
                .isInstanceOf(RecordingTransportProvider.class);
    }
}
