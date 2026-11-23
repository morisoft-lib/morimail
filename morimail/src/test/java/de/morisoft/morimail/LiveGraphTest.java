package de.morisoft.morimail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import de.morisoft.morimail.entra.EntraClientCredentials;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Sends one real mail through Microsoft Graph as an Entra app. The owner runs it with
 * {@code ./mvnw -Plive test -pl morimail}; the default build excludes it, so it never runs in
 * {@code verify} or in CI.
 *
 * <p>Environment variables:
 *
 * <ul>
 *   <li>{@code MORIMAIL_LIVE_TENANT_ID}, {@code MORIMAIL_LIVE_CLIENT_ID}: the Entra tenant and app
 *       (required).
 *   <li>{@code MORIMAIL_LIVE_FROM}: the mailbox the app may send from; {@code MORIMAIL_LIVE_TO}: the
 *       recipient (both required).
 *   <li>{@code MORIMAIL_LIVE_CERT_PATH} with {@code MORIMAIL_LIVE_CERT_PASSWORD}: a PKCS12 file and
 *       its password; or {@code MORIMAIL_LIVE_SECRET}: a client secret. One credential is required;
 *       the certificate is used when both are set.
 *   <li>{@code MORIMAIL_LIVE_OUT_OF_SCOPE_FROM} (optional): a mailbox the app must not send from;
 *       enables the out-of-scope probe, which expects a rejected sender.
 *   <li>{@code MORIMAIL_LIVE_BIG_ATTACHMENT} (optional): the path of a file of about 2.9 MB;
 *       enables the big-attachment probe.
 * </ul>
 *
 * <p>The inline-image test sends one mail whose HTML body shows a generated 64 x 64 PNG through
 * {@code cid:logo}. The owner checks in a mail client that the image is shown inside the mail and
 * not as a separate attachment. It needs no further variable.
 *
 * <p>With a required variable missing the class fails before any network call and names every
 * missing variable. Each test prints one line with the flow, the outcome and the exception class
 * and message, and never a token, a secret, a body or an address. Not shared between threads.
 */
@Tag("live")
class LiveGraphTest {

    private static String tenantId;
    private static String clientId;
    private static String from;
    private static String to;
    private static String certPath;
    private static String certPassword;
    private static String secret;
    private static String outOfScopeFrom;
    private static String bigAttachment;

    @TempDir
    Path tmp;

    private static String env(String name) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? null : value;
    }

    @BeforeAll
    static void readEnvironment() {
        tenantId = env("MORIMAIL_LIVE_TENANT_ID");
        clientId = env("MORIMAIL_LIVE_CLIENT_ID");
        from = env("MORIMAIL_LIVE_FROM");
        to = env("MORIMAIL_LIVE_TO");
        certPath = env("MORIMAIL_LIVE_CERT_PATH");
        certPassword = env("MORIMAIL_LIVE_CERT_PASSWORD");
        secret = env("MORIMAIL_LIVE_SECRET");
        outOfScopeFrom = env("MORIMAIL_LIVE_OUT_OF_SCOPE_FROM");
        bigAttachment = env("MORIMAIL_LIVE_BIG_ATTACHMENT");

        List<String> missing = new ArrayList<>();
        if (tenantId == null) {
            missing.add("MORIMAIL_LIVE_TENANT_ID");
        }
        if (clientId == null) {
            missing.add("MORIMAIL_LIVE_CLIENT_ID");
        }
        if (from == null) {
            missing.add("MORIMAIL_LIVE_FROM");
        }
        if (to == null) {
            missing.add("MORIMAIL_LIVE_TO");
        }
        if ((certPath == null || certPassword == null) && secret == null) {
            missing.add("MORIMAIL_LIVE_CERT_PATH and MORIMAIL_LIVE_CERT_PASSWORD, or MORIMAIL_LIVE_SECRET");
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "Live test: missing environment variables: " + String.join(", ", missing));
        }
    }

    private static boolean useCertificate() {
        return certPath != null && certPassword != null;
    }

    private static String flow() {
        return useCertificate() ? "certificate" : "secret";
    }

    private static Mailer mailer() {
        EntraClientCredentials credentials = useCertificate()
                ? EntraClientCredentials.withCertificate(tenantId, clientId, Path.of(certPath),
                        certPassword.toCharArray())
                : EntraClientCredentials.withSecret(tenantId, clientId, secret);
        return Mailer.of(MailAccount.builder("live").transport("graph").auth(credentials).build());
    }

    private static String describe(Exception e) {
        return e.getClass().getSimpleName() + ": " + e.getMessage();
    }

    @Test
    void sendsOneMailWithASmallAttachment() throws IOException, MailException {
        Path attachment = tmp.resolve("note.txt");
        Files.writeString(attachment, "A small attachment from the morimail live test.\n");
        Mail mail = Mail.using("live").from(from).to(to).subject("morimail live test")
                .text("This mail was sent by the morimail live test.").attach(attachment).build();
        try {
            mailer().send(mail);
        } catch (MailException | RuntimeException e) {
            System.out.println("live main: flow " + flow() + ", " + describe(e));
            throw e;
        }
        System.out.println("live main: flow " + flow() + ", accepted");
    }

    private static byte[] tinyPng() throws IOException {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 64; y++) {
            for (int x = 0; x < 64; x++) {
                image.setRGB(x, y, 0x2060c0);
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    @Test
    void sendsOneMailWithAnInlineImage() throws IOException, MailException {
        Path image = tmp.resolve("logo.png");
        Files.write(image, tinyPng());
        Mail mail = Mail.using("live").from(from).to(to).subject("morimail live test, inline image")
                .html("<p>The blue square below should show inside this mail, not as a separate attachment.</p>"
                        + "<p><img src=\"cid:logo\" alt=\"morimail test image\"></p>")
                .inline(image, "logo").build();
        try {
            mailer().send(mail);
        } catch (MailException | RuntimeException e) {
            System.out.println("live inline: flow " + flow() + ", " + describe(e));
            throw e;
        }
        System.out.println("live inline: flow " + flow() + ", accepted");
    }

    @Test
    void outOfScopeMailboxIsRejected() throws IOException, InvalidMailException {
        Assumptions.assumeTrue(outOfScopeFrom != null, "MORIMAIL_LIVE_OUT_OF_SCOPE_FROM is not set");
        Mail mail = Mail.using("live").from(outOfScopeFrom).to(to).subject("morimail live test")
                .text("This mail must be rejected.").build();
        Mailer mailer = mailer();
        try {
            mailer.send(mail);
        } catch (MailException e) {
            System.out.println("live out-of-scope: " + describe(e));
            assertThat(e).isInstanceOf(SenderRejectedException.class);
            return;
        }
        System.out.println("live out-of-scope: accepted");
        fail("the out-of-scope mailbox was accepted");
    }

    @Test
    void bigAttachmentIsAccepted() throws IOException, MailException {
        Assumptions.assumeTrue(bigAttachment != null, "MORIMAIL_LIVE_BIG_ATTACHMENT is not set");
        Path file = Path.of(bigAttachment);
        long size = Files.size(file);
        Mail mail = Mail.using("live").from(from).to(to).subject("morimail live test, big attachment")
                .text("This mail carries a big attachment.").attach(file).build();
        try {
            mailer().send(mail);
        } catch (MailException | RuntimeException e) {
            System.out.println("live big-attachment: " + size + " bytes, " + describe(e));
            throw e;
        }
        System.out.println("live big-attachment: " + size + " bytes, accepted");
    }
}
