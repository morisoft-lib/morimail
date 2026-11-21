package de.morisoft.morimail;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class TokenProviderTest {

    @Test
    void lambdaIsATokenProvider() throws MailException {
        TokenProvider provider = scope -> "tok";

        assertThat(provider.accessToken("s")).isEqualTo("tok");
    }

    @Test
    void freshAccessTokenDelegatesToAccessTokenExactlyOnce() throws MailException {
        AtomicInteger calls = new AtomicInteger();
        TokenProvider provider = scope -> {
            calls.incrementAndGet();
            return "tok-" + scope;
        };

        String fresh = provider.freshAccessToken("s", "old");

        assertThat(fresh).isEqualTo("tok-s");
        assertThat(calls.get()).isEqualTo(1);
    }
}
