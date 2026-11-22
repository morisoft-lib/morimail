# morimail

morimail is a small Java 17 library that sends templated mail through pluggable transports, one mail at a time. The built-in transport is Microsoft Graph, authenticated as an Entra app with a certificate or a client secret. The repository also holds a command-line sample, `serial-mail`. It sends one template to a CSV of recipients and keeps a journal, so a rerun skips every mail already accepted. The only runtime dependency of the library is Jackson.

Setup is in [docs/entra-setup.md](docs/entra-setup.md). The contracts and the deviations from the Microsoft documentation are in [docs/architecture.md](docs/architecture.md).

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

## Quick start

```java
import de.morisoft.morimail.*;
import de.morisoft.morimail.entra.EntraClientCredentials;
import java.nio.file.Path;
import java.util.Map;

public class QuickStart {
    public static void main(String[] args) throws MailException {
        var auth = EntraClientCredentials.withCertificate(
            "00000000-0000-0000-0000-000000000000", // directory (tenant) id
            "00000000-0000-0000-0000-000000000000", // application (client) id
            Path.of("/path/app.p12"),
            System.getenv("MAIL_CERT_PASSWORD").toCharArray());

        MailAccount account = MailAccount.builder("entra-main")
            .transport("graph")
            .auth(auth)
            .defaultFrom("newsletter@example.com")
            .build();
        Mailer mailer = Mailer.of(account);

        MailTemplate template = new MailTemplate(
            "Hello $$firstName$$", "<p>Hello $$firstName$$,</p>", "Hello $$firstName$$,");

        // build() throws InvalidMailException, naming what is wrong
        Mail mail = Mail.using("entra-main")
            .to("anna@example.org")
            .template(template, Map.of("firstName", "Anna"))
            .attach(Path.of("info.pdf"))
            .build();

        try {
            mailer.send(mail); // a normal return means accepted, not delivered
        } catch (TransientMailException e) {
            // Not sent this time. Try again later, after e.retryAfter() if present.
            System.err.println("try again later: " + e.getMessage());
        } catch (PermanentMailException e) {
            // This mail will never be accepted as it is. Drop it or fix it.
            // A SenderRejectedException (caught here) means the sending mailbox is
            // missing or not granted: fix the setup before sending more.
            System.err.println("rejected: " + e.getMessage());
        } catch (AuthRequiredException e) {
            // The app's credentials or consent need fixing. Stop until they are fixed.
            System.err.println("authentication needed: " + e.getMessage());
        }
    }
}
```

You can try the sample without any account. It renders the example campaign to files and sends nothing:

```bash
./mvnw -B -ntp -DskipTests package -pl morimail-sample-serial -am
java -jar morimail-sample-serial/target/morimail-sample-serial-*.jar send --account morimail-sample-serial/example-campaign/account.properties --campaign morimail-sample-serial/example-campaign --dry-run --out out
```

This writes the rendered mails to `out/` and needs no secrets. For a real send, follow [docs/entra-setup.md](docs/entra-setup.md). The sample sends no `saveToSentItems`, so Graph's default applies and every campaign mail also lands in the sender's Sent Items.

## What morimail is not

- Not a bulk mailer or campaign tool. It sends one mail per call, with no batching, queues, scheduling or retries; those belong to the consumer.
- It is bound by Exchange Online's documented per-mailbox limits: 30 messages per minute and 10,000 recipients per day (Microsoft's Exchange Online limits page, read 2026-11-22). The sample's default of 20 mails per minute stays below the first. Tenants also have a limit for external recipients, and a sender on an onmicrosoft.com address reaches at most 100 external recipients per 24 hours. For bulk mail to external recipients, Microsoft points to Azure Communication Services.
- Not for large attachments. Attachments must stay under 3 MiB (3 x 1024 x 1024 bytes), per file and in total. Larger files need Graph upload sessions, which morimail does not do.
- One body per mail on Graph. When both HTML and text are given, only the HTML is sent.

## Delivery semantics

A normal return from send means the service accepted the mail, not that it was delivered.

A send that ended in a timeout may still have been accepted, so retrying after a timeout can deliver the same mail twice: delivery is at-least-once.

## Tests

The file `morimail/src/test/resources/test-keystore-throwaway.p12` is a throwaway PKCS12 key store for the tests only (password `changeit`, certificate `CN=morimail-test-throwaway`): it protects nothing and is not a leaked credential.

## License

MIT, see [LICENSE](LICENSE).

## Security

To report a vulnerability, see [SECURITY.md](SECURITY.md).
