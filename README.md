# morimail

morimail is a small Java 17 library that sends templated mail through pluggable transports, one mail at a time. The first built-in transport is Microsoft Graph, authenticated as an Entra app (in development). The repository also holds a command-line sample for serial mail. The only runtime dependency of the library is Jackson.

## Status

morimail is pre-1.0. The API may change between minor versions. It is not yet released to Maven Central.

## Coordinates

```xml
<dependency>
  <groupId>de.morisoft</groupId>
  <artifactId>morimail</artifactId>
  <version>0.1.0</version>
</dependency>
```

0.1.0 is the first planned release and is not published yet.

## Delivery semantics

A normal return from send means the service accepted the mail, not that it was delivered.

A send that ended in a timeout may still have been accepted, so retrying after a timeout can deliver the same mail twice: delivery is at-least-once.

## Quick start

A quick start comes with the first release.

## Tests

The file `morimail/src/test/resources/test-keystore-throwaway.p12` is a throwaway PKCS12 key store for the tests only (password `changeit`, certificate `CN=morimail-test-throwaway`): it protects nothing and is not a leaked credential.

## License

MIT, see [LICENSE](LICENSE).

## Security

To report a vulnerability, see [SECURITY.md](SECURITY.md).
