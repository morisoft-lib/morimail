package de.morisoft.morimail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.Serializable;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class MailModelTest {

    private static Mail.Builder valid() {
        return Mail.using("main").subject("Hello").text("Body");
    }

    @Test
    void noModelRecordIsSerializable() {
        for (Class<?> type : List.of(MailAddress.class, Attachment.class, MailMessage.class,
                Mail.class, MailTemplate.class)) {
            assertThat(Serializable.class.isAssignableFrom(type)).as(type.getSimpleName())
                    .isFalse();
        }
    }

    @Test
    void mailMessageListsAreUnmodifiableCopies() {
        List<MailAddress> to = new ArrayList<>(List.of(MailAddress.of("a@example.org")));
        List<Attachment> attachments = new ArrayList<>();
        MailMessage message = new MailMessage(null, to, List.of(), List.of(), null, "S", null,
                "T", attachments);

        to.add(MailAddress.of("b@example.org"));
        attachments.add(new Attachment("x.pdf", "application/pdf", Path.of("x.pdf"), null));

        assertThat(message.to()).containsExactly(MailAddress.of("a@example.org"));
        assertThat(message.attachments()).isEmpty();
        assertThatThrownBy(() -> message.to().add(MailAddress.of("c@example.org")))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> message.cc().add(MailAddress.of("c@example.org")))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> message.bcc().add(MailAddress.of("c@example.org")))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> message.attachments().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void neverSetListsAreEmptyNotNull() throws Exception {
        MailMessage message = valid().to("a@example.org").build().message();

        assertThat(message.cc()).isNotNull().isEmpty();
        assertThat(message.bcc()).isNotNull().isEmpty();
        assertThat(message.attachments()).isNotNull().isEmpty();
    }

    @Test
    void directMailMessageWithNullListThrowsNullPointerException() {
        List<MailAddress> none = List.of();
        List<Attachment> noFiles = List.of();

        assertThatThrownBy(() -> new MailMessage(null, null, none, none, null, "S", null, "T",
                noFiles)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new MailMessage(null, none, null, none, null, "S", null, "T",
                noFiles)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new MailMessage(null, none, none, null, null, "S", null, "T",
                noFiles)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new MailMessage(null, none, none, none, null, "S", null, "T",
                null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void builtMailIsUnaffectedByLaterBuilderCallsAndArrayChanges() throws Exception {
        String[] recipients = {"a@example.org"};
        Mail.Builder builder = valid().to(recipients);

        recipients[0] = "changed@example.org";
        Mail mail = builder.build();
        builder.to("later@example.org").cc("cc@example.org").subject("Changed");

        assertThat(mail.message().to()).containsExactly(MailAddress.of("a@example.org"));
        assertThat(mail.message().cc()).isEmpty();
        assertThat(mail.message().subject()).isEqualTo("Hello");
    }

    @Test
    void mailAddressKeepsValuesExactlyAsGiven() throws Exception {
        MailAddress address = MailAddress.of(" A@Example.org ", "  N  ");

        Mail mail = valid().to(address).build();

        assertThat(mail.message().to().get(0).email()).isEqualTo(" A@Example.org ");
        assertThat(mail.message().to().get(0).displayName()).isEqualTo("  N  ");
    }
}
