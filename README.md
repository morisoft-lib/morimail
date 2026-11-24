# morimail

morimail is a small Java 17 library that sends templated mail through pluggable transports, one mail at a time. The built-in transport is Microsoft Graph, authenticated as an Entra app with a certificate or a client secret. The repository also holds a command-line sample, `serial-mail`. It sends one template to a CSV of recipients and keeps a journal, so a rerun skips every mail already accepted. The only runtime dependency of the library is Jackson.

Setup is in [docs/entra-setup.md](docs/entra-setup.md). The contracts and the deviations from the Microsoft documentation are in [docs/architecture.md](docs/architecture.md).

## Status

morimail is pre-1.0. The API may change between minor versions.

## Coordinates

```xml
<dependency>
  <groupId>de.morisoft</groupId>
  <artifactId>morimail</artifactId>
  <version>0.2.0</version>
</dependency>
```

0.2.0 is the current release on Maven Central. It adds inline images and the `morimail-html` helper.

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

## Inline images

An inline image is shown inside the HTML body instead of arriving as a separate attachment. Add it with `.inline(file, contentId)` and refer to it in the HTML as `cid:` plus the content ID. The `mailer` below is the one from the Quick start.

```java
import de.morisoft.morimail.*;
import java.nio.file.Path;
import java.util.Map;

public class InlineImage {
    public static void send(Mailer mailer) throws MailException {
        MailTemplate template = new MailTemplate(
            "Hello $$firstName$$",
            "<p><img src=\"cid:logo\" alt=\"Logo\"></p><p>Hello $$firstName$$,</p>",
            "Hello $$firstName$$,");

        Mail mail = Mail.using("entra-main")
            .to("anna@example.org")
            .template(template, Map.of("firstName", "Anna"))
            .inline(Path.of("logo.png"), "logo")
            .build();

        mailer.send(mail);
    }
}
```

`build()` checks the images. Every `cid:` reference in the rendered HTML must match the content ID of an inline image exactly, case included, and every inline image must be referenced, so an inline image in a mail without an HTML body fails. Content IDs must be unique in a mail, not blank and free of whitespace, quotes, parentheses and angle brackets, and an inline image needs an `image/` content type.

Inline images count toward the Graph transport's attachment limits: under 3 MiB (3 x 1024 x 1024 bytes) per file and for all attachments together, and at most 4 MiB for the estimated request. Details are in [docs/architecture.md](docs/architecture.md).

## morimail-html

The helper module turns ordinary image sources in an HTML template, such as `<img src="images/logo.png">`, into `cid:` references plus inline images. Convert the template once, before rendering, and use the result for every mail. The helper brings `morimail` with it and adds no other dependency.

```xml
<dependency>
  <groupId>de.morisoft</groupId>
  <artifactId>morimail-html</artifactId>
  <version>0.2.0</version>
</dependency>
```

Local sources resolve against a base directory and must lie inside it.

```java
import de.morisoft.morimail.*;
import de.morisoft.morimail.html.HtmlImages;
import java.nio.file.Path;
import java.util.Map;

public class EmbedImages {
    public static void send(Mailer mailer) throws MailException {
        MailTemplate template = new MailTemplate(
            "Hello $$firstName$$",
            "<p><img src=\"images/logo.png\" alt=\"Logo\"></p><p>Hello $$firstName$$,</p>",
            "Hello $$firstName$$,");

        HtmlImages.Result result = HtmlImages.embed(template, Path.of("campaign"));
        Mail.Builder builder = Mail.using("entra-main")
            .to("anna@example.org")
            .template(result.template(), Map.of("firstName", "Anna"));
        for (Attachment image : result.images()) {
            builder.attach(image);
        }
        Mail mail = builder.build();
        mailer.send(mail);

        // With a work directory, http(s) and data: sources are converted too.
        HtmlImages.Result withRemote = HtmlImages.embed(template, Path.of("campaign"), Path.of("work"));

        // The storable form: HTML whose images are data: URIs.
        MailTemplate storable = HtmlImages.toDataUris(template, Path.of("campaign"));
    }
}
```

`http(s)` and `data:` sources stay unchanged unless a work directory is passed, an existing directory that the caller owns and cleans up. With it the helper downloads remote images into it, with a timeout, a 3 MiB cap and an image type check, and decodes `data:` images there. A template URL can reach any host this machine can reach, so pass a work directory only for templates you trust. Recipient data never reaches the helper, because a `src` with a `$$` placeholder stays unchanged.

`toDataUris` gives the storable form, HTML whose images are `data:` URIs so the template alone carries them, and `embed` with a work directory turns it back into inline images.

## Serial-mail sample

The sample `serial-mail` reads a campaign directory and sends one mail per row of `recipients.csv`.

```text
campaign/
  subject.txt      subject template
  body.html        HTML template
  body.txt         text template (optional)
  recipients.csv   header row with an email column; every column is a placeholder
  attachments/     optional; every file is attached to every mail
  inline/          optional; every file is an inline image of every mail
```

The content ID of a file in `inline/` is its name without the last extension, so `body.html` refers to `logo.png` as `cid:logo`, and the match is exact. Pre-flight checks the campaign before the first mail and stops with exit code 2, sending nothing, when a `cid:` has no image in `inline/`, an image is not referenced, two files share a base name or a file is not an image. It accepts names such as `50%.png` and `a&b.png`, but a mail client may decode the `%` or `&` in the `cid:` reference and then not find the image, so name inline files with ASCII letters, digits, `-`, `_` and `.` only. Dot-files such as `.DS_Store` are skipped. Run `send --help` for every option.

You can try the sample without any account. It renders the example campaign to files and sends nothing:

```bash
./mvnw -B -ntp -DskipTests package -pl morimail-sample-serial -am
java -jar morimail-sample-serial/target/morimail-sample-serial-*.jar send --account morimail-sample-serial/example-campaign/account.properties --campaign morimail-sample-serial/example-campaign --dry-run --out out
```

This writes the rendered mails to `out/` and needs no secrets. For a real send, follow [docs/entra-setup.md](docs/entra-setup.md). The sample sends no `saveToSentItems`, so Graph's default applies and every campaign mail also lands in the sender's Sent Items.

The example campaign has `inline/logo.png`. The dry run copies it once to `out/inline/` and the written HTML points at it by relative path, so a browser shows the logo, while a real send keeps the `cid:` reference.

## What morimail is not

- Not a bulk mailer or campaign tool. It sends one mail per call, with no batching, queues, scheduling or retries; those belong to the consumer.
- Exchange Online documents a limit of 30 messages per minute per mailbox for SMTP client submission, and 10,000 recipients per day (Microsoft's Exchange Online limits page, read 2026-11-22). Microsoft does not say whether Graph `sendMail` counts against the per-minute limit. The sample's default of 20 mails per minute stays below it either way. Tenants also have a limit for external recipients, and a sender on an onmicrosoft.com address reaches at most 100 external recipients per 24 hours. For bulk mail to external recipients, Microsoft points to Azure Communication Services.
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
