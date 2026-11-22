package de.morisoft.morimail.graph;

import de.morisoft.morimail.MailException;
import de.morisoft.morimail.TokenProvider;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/** Token provider double for the graph tests: fixed tokens, recorded calls, thread-safe. */
final class StubTokenProvider implements TokenProvider {

    private final String token;
    private final String freshToken;
    private final AtomicInteger accessCalls = new AtomicInteger();
    private final AtomicInteger freshCalls = new AtomicInteger();
    private final List<String> scopes = new CopyOnWriteArrayList<>();
    private final List<String> rejectedTokens = new CopyOnWriteArrayList<>();
    private volatile MailException freshFailure;

    StubTokenProvider(String token, String freshToken) {
        this.token = token;
        this.freshToken = freshToken;
    }

    @Override
    public String accessToken(String scope) {
        accessCalls.incrementAndGet();
        scopes.add(scope);
        return token;
    }

    @Override
    public String freshAccessToken(String scope, String rejectedToken) throws MailException {
        freshCalls.incrementAndGet();
        scopes.add(scope);
        rejectedTokens.add(rejectedToken);
        MailException failure = freshFailure;
        if (failure != null) {
            throw failure;
        }
        return freshToken;
    }

    /** Makes every later {@code freshAccessToken} call throw this exception. */
    void failFresh(MailException failure) {
        this.freshFailure = failure;
    }

    int accessCalls() {
        return accessCalls.get();
    }

    int freshCalls() {
        return freshCalls.get();
    }

    List<String> scopes() {
        return scopes;
    }

    List<String> rejectedTokens() {
        return rejectedTokens;
    }
}
