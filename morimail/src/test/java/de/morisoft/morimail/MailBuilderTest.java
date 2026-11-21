package de.morisoft.morimail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class MailBuilderTest {

    private static Mail.Builder valid() {
        return Mail.using("main").to("reader@example.org").subject("Hello").text("Plain text body");
    }

    @Test
    void buildsPlainTextMail() throws Exception {
        Mail mail = valid().build();

        assertThat(mail.accountId()).isEqualTo("main");
        MailMessage message = mail.message();
        assertThat(message.to()).containsExactly(MailAddress.of("reader@example.org"));
        assertThat(message.subject()).isEqualTo("Hello");
        assertThat(message.text()).isEqualTo("Plain text body");
        assertThat(message.from()).isNull();
        assertThat(message.replyTo()).isNull();
        assertThat(message.html()).isNull();
        assertThat(message.cc()).isEmpty();
        assertThat(message.bcc()).isEmpty();
        assertThat(message.attachments()).isEmpty();
        assertThatThrownBy(() -> message.cc().add(MailAddress.of("x@example.org")))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> message.bcc().add(MailAddress.of("x@example.org")))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> message.to().add(MailAddress.of("x@example.org")))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> message.attachments().add(null))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void toAppendsInCallOrder() throws Exception {
        Mail mail = Mail.using("main")
                .to("first@example.org", "second@example.org")
                .to("third@example.org")
                .subject("Hello").text("Body").build();

        assertThat(mail.message().to()).extracting(MailAddress::email)
                .containsExactly("first@example.org", "second@example.org", "third@example.org");
    }

    @Test
    void fromLastCallWins() throws Exception {
        Mail mail = valid().from("one@example.org").from("two@example.org").build();

        assertThat(mail.message().from()).isEqualTo(MailAddress.of("two@example.org"));
    }

    @Test
    void mailAddressOfEmailHasNullDisplayName() {
        MailAddress address = MailAddress.of("reader@example.org");

        assertThat(address.email()).isEqualTo("reader@example.org");
        assertThat(address.displayName()).isNull();
        assertThat(MailAddress.of("reader@example.org", "Reader").displayName()).isEqualTo("Reader");
    }

    @Test
    void rejectsBlankAccountId() {
        for (String id : new String[] {null, "", "   "}) {
            assertThatThrownBy(() -> Mail.using(id).to("reader@example.org").subject("Hello")
                    .text("Body").build())
                    .isInstanceOf(InvalidMailException.class)
                    .hasMessageContaining("accountId");
        }
    }

    @Test
    void rejectsMissingRecipient() {
        assertThatThrownBy(() -> Mail.using("main").subject("Hello").text("Body").build())
                .isInstanceOf(InvalidMailException.class)
                .hasMessageContaining("recipient");
    }

    @Test
    void rejectsBlankSubject() {
        for (String subject : new String[] {null, "", "   "}) {
            assertThatThrownBy(() -> Mail.using("main").to("reader@example.org").subject(subject)
                    .text("Body").build())
                    .isInstanceOf(InvalidMailException.class)
                    .hasMessageContaining("subject");
        }
    }

    @Test
    void rejectsBlankText() {
        for (String text : new String[] {null, "", "   "}) {
            assertThatThrownBy(() -> Mail.using("main").to("reader@example.org").subject("Hello")
                    .text(text).build())
                    .isInstanceOf(InvalidMailException.class)
                    .hasMessageContaining("body");
        }
    }

    @Test
    void rejectsMalformedToAddress() {
        for (String bad : List.of("@example.org", "a@", "a@@example.org", "a@b@example.org")) {
            assertThatThrownBy(() -> Mail.using("main").to("reader@example.org", bad)
                    .subject("Hello").text("Body").build())
                    .isInstanceOf(InvalidMailException.class)
                    .hasMessageContaining("to[1]")
                    .hasMessageContaining("'@'")
                    .message().doesNotContain(bad);
        }
        assertThatThrownBy(() -> Mail.using("main").to((String) null)
                .subject("Hello").text("Body").build())
                .isInstanceOf(InvalidMailException.class)
                .hasMessageContaining("to[0]");
    }

    @Test
    void rejectsInvalidFrom() {
        assertThatThrownBy(() -> valid().from("a@b@example.org").build())
                .isInstanceOf(InvalidMailException.class)
                .hasMessageContaining("from")
                .message().doesNotContain("a@b@example.org");
    }

    @Test
    void rejectsLineBreakInToEmail() {
        for (String bad : List.of("read\ner@example.org", "read\rer@example.org")) {
            assertThatThrownBy(() -> Mail.using("main").to(bad).subject("Hello").text("Body")
                    .build())
                    .isInstanceOf(InvalidMailException.class)
                    .hasMessageContaining("to[0]")
                    .hasMessageContaining("line break")
                    .message().doesNotContain(bad);
        }
    }

    @Test
    void invalidReasonReportsLineBreakInDisplayName() {
        assertThat(MailAddress.invalidReason(MailAddress.of("reader@example.org", "Line\nBreak")))
                .isEqualTo("display name contains a line break");
        assertThat(MailAddress.invalidReason(MailAddress.of("reader@example.org", "Reader")))
                .isNull();
    }

    @Test
    void rejectsLineBreakInSubject() {
        for (String subject : List.of("Secret\nSubject", "Secret\rSubject")) {
            assertThatThrownBy(() -> valid().subject(subject).build())
                    .isInstanceOf(InvalidMailException.class)
                    .hasMessageContaining("subject")
                    .hasMessageContaining("line break")
                    .message().doesNotContain("Secret");
        }
    }

    @Test
    void reportsFirstProblemOnly() {
        assertThatThrownBy(() -> Mail.using("main").subject("  ").text("Body").build())
                .isInstanceOf(InvalidMailException.class)
                .hasMessageContaining("recipient")
                .message().doesNotContain("subject");
    }
}
