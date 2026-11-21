package de.morisoft.morimail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.morisoft.morimail.test.RecordingTransport;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RecordingTransportTest {

    private static MailMessage message(List<String> to, List<String> cc, List<String> bcc) {
        return new MailMessage(MailAddress.of("newsletter@example.com"), addresses(to),
                addresses(cc), addresses(bcc), null, "Hello", null, "Body", List.of());
    }

    private static MailMessage messageTo(String email) {
        return message(List.of(email), List.of(), List.of());
    }

    private static List<MailAddress> addresses(List<String> emails) {
        return emails.stream().map(MailAddress::of).toList();
    }

    private static RecordingTransport transport() {
        MailAccount account = MailAccount.builder("main")
                .transport("recording")
                .option("k", "v")
                .build();
        return (RecordingTransport) account.transport();
    }

    @Test
    void failForMatchesRecipientCaseInsensitivelyInToCcAndBcc() throws MailException {
        RecordingTransport transport = transport();
        PermanentMailException failure = new PermanentMailException("blocked");
        transport.failFor("Reader@Example.org", failure);

        MailMessage inTo = messageTo("reader@example.org");
        MailMessage inCc = message(List.of("other@example.org"), List.of("READER@example.org"),
                List.of());
        MailMessage inBcc = message(List.of("other@example.org"), List.of(),
                List.of("reader@EXAMPLE.org"));

        assertThatThrownBy(() -> transport.send(inTo)).isSameAs(failure);
        assertThatThrownBy(() -> transport.send(inCc)).isSameAs(failure);
        assertThatThrownBy(() -> transport.send(inBcc)).isSameAs(failure);
        assertThat(transport.attempts()).isEqualTo(3);
        assertThat(transport.sent()).isEmpty();
    }

    @Test
    void failForLeavesOtherRecipientsAlone() throws MailException {
        RecordingTransport transport = transport();
        transport.failFor("reader@example.org", new PermanentMailException("blocked"));

        transport.send(messageTo("other@example.org"));

        assertThat(transport.sent()).hasSize(1);
        assertThat(transport.attempts()).isEqualTo(1);
    }

    @Test
    void failForWinsOverFailNextWithoutConsumingTheQueue() throws MailException {
        RecordingTransport transport = transport();
        PermanentMailException perRecipient = new PermanentMailException("blocked");
        TransientMailException queued = new TransientMailException("busy");
        transport.failFor("reader@example.org", perRecipient);
        transport.failNext(queued);

        assertThatThrownBy(() -> transport.send(messageTo("reader@example.org")))
                .isSameAs(perRecipient);
        assertThatThrownBy(() -> transport.send(messageTo("other@example.org")))
                .isSameAs(queued);
        transport.send(messageTo("other@example.org"));

        assertThat(transport.attempts()).isEqualTo(3);
        assertThat(transport.sent()).hasSize(1);
    }

    @Test
    void failValidationFailsValidateAndSendWithoutAnAttempt() throws MailException {
        MailAccount account = MailAccount.builder("main")
                .transport("recording")
                .defaultFrom("newsletter@example.com")
                .build();
        RecordingTransport transport = (RecordingTransport) account.transport();
        Mailer mailer = Mailer.of(account);
        Mail mail = Mail.using("main").to("reader@example.org").subject("Hello").text("Body")
                .build();
        InvalidMailException failure = new InvalidMailException("too big");
        transport.failValidation(failure);

        assertThatThrownBy(() -> mailer.validate(mail)).isSameAs(failure);
        assertThatThrownBy(() -> mailer.send(mail)).isSameAs(failure);

        assertThat(transport.attempts()).isZero();
        assertThat(transport.sent()).isEmpty();
    }

    @Test
    void failValidationNullClearsTheFailure() throws MailException {
        MailAccount account = MailAccount.builder("main")
                .transport("recording")
                .defaultFrom("newsletter@example.com")
                .build();
        RecordingTransport transport = (RecordingTransport) account.transport();
        Mailer mailer = Mailer.of(account);
        Mail mail = Mail.using("main").to("reader@example.org").subject("Hello").text("Body")
                .build();
        transport.failValidation(new InvalidMailException("too big"));
        transport.failValidation(null);

        mailer.validate(mail);
        mailer.send(mail);

        assertThat(transport.sent()).hasSize(1);
    }

    @Test
    void sentIsAnUnmodifiableSnapshot() throws MailException {
        RecordingTransport transport = transport();
        transport.send(messageTo("one@example.org"));
        List<MailMessage> snapshot = transport.sent();

        transport.send(messageTo("two@example.org"));

        assertThat(snapshot).hasSize(1);
        assertThat(transport.sent()).hasSize(2);
        assertThatThrownBy(() -> snapshot.add(messageTo("three@example.org")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void optionsAndAuthArePassedThroughFromTheProvider() {
        TokenProvider auth = scope -> "tok";
        MailAccount account = MailAccount.builder("main")
                .transport("recording")
                .option("k", "v")
                .auth(auth)
                .build();
        RecordingTransport transport = (RecordingTransport) account.transport();

        assertThat(transport.options()).isEqualTo(Map.of("k", "v"));
        assertThat(transport.auth()).isSameAs(auth);
    }
}
