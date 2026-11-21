# morimail architecture

This page summarises the contracts of morimail and the reasons for them. The full contract summary follows with the 0.1.0 documentation. Deviations from the spec are recorded here as they are made (spec §5: signatures are binding in intent).

## Deviations from the spec

| Spec | morimail | Reason |
|------|----------|--------|
| §7.3 (do not widen `TokenProvider`) | `TokenProvider` has a default method `freshAccessToken(String scope, String rejectedToken)` that delegates to `accessToken(scope)`. | The Graph transport can get a new token after a 401 without importing `entra`. Lambdas still work, and there is no new interface (CORE-02, owner decision). |
| §5.3 (data is `Map<String, Object>`) | `Mail.Builder.template(MailTemplate, Map<String, ?>)`. | Typed maps such as a CSV row `Map<String, String>` compile. Values still go through `String.valueOf` (D-02a, owner decision). |
| §2 and §5.1 (error types unspecified) | An unknown transport id raises `IllegalArgumentException` that lists the available ids. A duplicate or blank provider id and a broken provider raise `IllegalStateException`. Invalid account configuration raises `IllegalArgumentException`. | Configuration errors surface when the account or registry is built and are not mail errors. The checked `MailException` tree stays for sending. |
| §3 (JUnit 5) | JUnit Jupiter 6.1.3 through `junit-bom`. | It is the current stable line, with the same `org.junit.jupiter` API and a Java 17 baseline (D-02c, owner decision). |
| §10.1 (published as a test-jar) | The test-jar with `RecordingTransport` is built and shared inside the reactor only. The release profile sets `attach false` on it. Because an unattached test-jar is invisible to a packaging reactor, the release build runs on the published modules only (`-Prelease ... -pl morimail -am`). | The Central plugin would otherwise stage the test-jar, and spec §13.2 says the test-jar is not published (owner decision). |

## Contracts

- `Mail.Builder.build()` checks that attachment files exist and are readable, but a file can still disappear before the transport reads it. A transport reports an attachment it cannot read at send time as an `InvalidMailException` that names the file and never carries its content.
