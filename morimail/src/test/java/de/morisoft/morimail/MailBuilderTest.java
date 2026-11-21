package de.morisoft.morimail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.morisoft.morimail.test.RecordingTransport;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MailBuilderTest {

    @TempDir
    Path tempDir;

    private Path file(String name) throws Exception {
        Path file = tempDir.resolve(name);
        Files.write(file, new byte[] {1, 2, 3});
        return file;
    }

    private static List<String> emails(List<MailAddress> addresses) {
        return addresses.stream().map(MailAddress::email).toList();
    }

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
    void rejectsCharactersThatBreakAHeader() {
        for (String bad : List.of("a,b@example.org", "a b@example.org", "a;b@example.org",
                "x<y@example.org", "\"@example.org", "a\0b@example.org", "a\tb@example.org")) {
            assertThatThrownBy(() -> Mail.using("main").to(bad).subject("Hello").text("Body")
                    .build())
                    .isInstanceOf(InvalidMailException.class)
                    .hasMessageContaining("to[0]")
                    .hasMessageContaining("not allowed")
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

    // ---- address forms (D-01, D-02, D-03) ----

    @Test
    void addressFormsAppendInCallOrder() throws Exception {
        Mail mail = Mail.using("main")
                .to("a@example.org").to(MailAddress.of("b@example.org", "B"),
                        MailAddress.of("c@example.org"))
                .cc("d@example.org", "e@example.org").cc(MailAddress.of("f@example.org"))
                .bcc("g@example.org").bcc(MailAddress.of("h@example.org"))
                .subject("Hello").text("Body").build();

        assertThat(emails(mail.message().to()))
                .containsExactly("a@example.org", "b@example.org", "c@example.org");
        assertThat(emails(mail.message().cc()))
                .containsExactly("d@example.org", "e@example.org", "f@example.org");
        assertThat(emails(mail.message().bcc())).containsExactly("g@example.org", "h@example.org");
    }

    @Test
    void fromAndReplyToLastCallWinsAcrossForms() throws Exception {
        Mail mail = valid()
                .from("one@example.org").from(MailAddress.of("two@example.org", "Two"))
                .replyTo(MailAddress.of("r1@example.org")).replyTo("r2@example.org")
                .build();
        Mail other = valid()
                .from(MailAddress.of("one@example.org")).from("two@example.org")
                .replyTo("r1@example.org").replyTo(MailAddress.of("r2@example.org", "R"))
                .build();

        assertThat(mail.message().from()).isEqualTo(MailAddress.of("two@example.org", "Two"));
        assertThat(mail.message().replyTo()).isEqualTo(MailAddress.of("r2@example.org"));
        assertThat(other.message().from()).isEqualTo(MailAddress.of("two@example.org"));
        assertThat(other.message().replyTo()).isEqualTo(MailAddress.of("r2@example.org", "R"));
    }

    @Test
    void displayNameArrivesThroughMailAddressOf() throws Exception {
        Mail mail = Mail.using("main").to(MailAddress.of("reader@example.org", "Reader Name"))
                .subject("Hello").text("Body").build();

        assertThat(mail.message().to().get(0).email()).isEqualTo("reader@example.org");
        assertThat(mail.message().to().get(0).displayName()).isEqualTo("Reader Name");
    }

    @Test
    void nonAsciiEmailAndDisplayNamePass() throws Exception {
        MailAddress address = MailAddress.of("jürgen@example.org", "Jürgen Müller");

        Mail mail = valid().to(address).build();

        assertThat(mail.message().to()).contains(address);
    }

    @Test
    void duplicateAndCaseVariantRecipientsAreKept() throws Exception {
        Mail twice = Mail.using("main").to("a@example.org", "a@example.org")
                .subject("Hello").text("Body").build();
        Mail cased = Mail.using("main").to("A@example.org", "a@example.org")
                .subject("Hello").text("Body").build();

        assertThat(emails(twice.message().to())).containsExactly("a@example.org", "a@example.org");
        assertThat(emails(cased.message().to())).containsExactly("A@example.org", "a@example.org");
    }

    // ---- rejections ----

    @Test
    void rejectsTemplateCombinedWithExplicitContent() {
        MailTemplate template = new MailTemplate("S", "<p>h</p>", "t");
        Map<String, String> data = Map.of();

        assertThatThrownBy(() -> valid().template(template, data).subject("x").build())
                .isInstanceOf(InvalidMailException.class)
                .hasMessageContaining("cannot be combined");
        assertThatThrownBy(() -> valid().html("<p>x</p>").template(template, data).build())
                .isInstanceOf(InvalidMailException.class)
                .hasMessageContaining("cannot be combined");
        assertThatThrownBy(() -> valid().template(template, data).text("x").build())
                .isInstanceOf(InvalidMailException.class)
                .hasMessageContaining("cannot be combined");
    }

    @Test
    void rejectsMissingPlaceholderField() {
        MailTemplate template = new MailTemplate("Hi $$name$$", "<p>x</p>", null);

        assertThatThrownBy(() -> Mail.using("main").to("reader@example.org")
                .template(template, Map.of("other", "SecretValue")).build())
                .isInstanceOf(InvalidMailException.class)
                .hasMessageContaining("$$name$$")
                .hasMessageContaining("subject")
                .message().doesNotContain("SecretValue");
    }

    @Test
    void rejectsEmptyRecipientArray() {
        assertThatThrownBy(() -> Mail.using("main").to(new String[0]).subject("Hello")
                .text("Body").build())
                .isInstanceOf(InvalidMailException.class)
                .hasMessageContaining("recipient");
    }

    @Test
    void rejectsWhenNoBodyIsNonBlank() {
        assertThatThrownBy(() -> Mail.using("main").to("reader@example.org").subject("Hello")
                .html("  ").text(" \t").build())
                .isInstanceOf(InvalidMailException.class)
                .hasMessageContaining("body");
        assertThatThrownBy(() -> Mail.using("main").to("reader@example.org").subject("Hello")
                .html(null).build())
                .isInstanceOf(InvalidMailException.class)
                .hasMessageContaining("body");
    }

    @Test
    void rejectsNullAddressElement() {
        assertThatThrownBy(() -> Mail.using("main").to((MailAddress) null).subject("Hello")
                .text("Body").build())
                .isInstanceOf(InvalidMailException.class)
                .hasMessageContaining("to[0]")
                .hasMessageContaining("missing");
        assertThatThrownBy(() -> valid().cc((String) null).build())
                .isInstanceOf(InvalidMailException.class)
                .hasMessageContaining("cc[0]")
                .hasMessageContaining("missing");
    }

    @Test
    void rejectsLineBreakInCcBccAndReplyToEmail() {
        assertThatThrownBy(() -> valid().cc("one\nline@example.org").build())
                .isInstanceOf(InvalidMailException.class)
                .hasMessageContaining("cc[0]").hasMessageContaining("line break")
                .message().doesNotContain("example.org");
        assertThatThrownBy(() -> valid().bcc("ok@example.org", "one\rline@example.org").build())
                .isInstanceOf(InvalidMailException.class)
                .hasMessageContaining("bcc[1]").hasMessageContaining("line break")
                .message().doesNotContain("example.org");
        assertThatThrownBy(() -> valid().replyTo("one\nline@example.org").build())
                .isInstanceOf(InvalidMailException.class)
                .hasMessageContaining("replyTo").hasMessageContaining("line break")
                .message().doesNotContain("example.org");
    }

    @Test
    void rejectsLineBreakInDisplayName() {
        assertThatThrownBy(() -> valid()
                .to(MailAddress.of("second@example.org", "Secret\nName")).build())
                .isInstanceOf(InvalidMailException.class)
                .hasMessageContaining("to[1]").hasMessageContaining("display name")
                .message().doesNotContain("Secret");
        assertThatThrownBy(() -> valid()
                .from(MailAddress.of("sender@example.org", "Secret\rName")).build())
                .isInstanceOf(InvalidMailException.class)
                .hasMessageContaining("from").hasMessageContaining("display name")
                .message().doesNotContain("Secret");
    }

    @Test
    void rejectsDirectoryAttachment() {
        assertThatThrownBy(() -> valid().attach(tempDir).build())
                .isInstanceOf(InvalidMailException.class)
                .hasMessageContaining("attachments[0]")
                .hasMessageContaining("not a regular file");
    }

    @Test
    void rejectsMissingAttachmentFile() {
        assertThatThrownBy(() -> valid().attach(tempDir.resolve("nope.pdf")).build())
                .isInstanceOf(InvalidMailException.class)
                .hasMessageContaining("attachments[0]")
                .hasMessageContaining("not found");
    }

    @Test
    void rejectsUnreadableAttachmentFile() throws Exception {
        Path file = file("secret.pdf");
        file.toFile().setReadable(false, false);
        Assumptions.assumeTrue(!Files.isReadable(file),
                "file stays readable (running as root or on a file system without permissions)");

        assertThatThrownBy(() -> valid().attach(file).build())
                .isInstanceOf(InvalidMailException.class)
                .hasMessageContaining("attachments[0]")
                .hasMessageContaining("not readable");
    }

    @Test
    void rejectsContentId() throws Exception {
        Path file = file("logo.png");

        assertThatThrownBy(() -> valid()
                .attach(new Attachment("logo.png", null, file, "cid-1")).build())
                .isInstanceOf(InvalidMailException.class)
                .hasMessageContaining("attachments[0]")
                .hasMessageContaining("contentId");
    }

    @Test
    void reportsCcProblemBeforeReplyTo() {
        assertThatThrownBy(() -> valid().cc("ok@example.org", "bad@@example.org")
                .replyTo("also@@bad.example.org").build())
                .isInstanceOf(InvalidMailException.class)
                .hasMessageContaining("cc[1]");
    }

    // ---- accepted ----

    @Test
    void acceptsHtmlOnlyBody() throws Exception {
        Mail mail = Mail.using("main").to("reader@example.org").subject("Hello")
                .html("<p>Hi</p>").build();

        assertThat(mail.message().html()).isEqualTo("<p>Hi</p>");
        assertThat(mail.message().text()).isNull();
    }

    @Test
    void acceptsTextOnlyBody() throws Exception {
        Mail mail = valid().build();

        assertThat(mail.message().text()).isEqualTo("Plain text body");
        assertThat(mail.message().html()).isNull();
    }

    @Test
    void acceptsZeroByteAttachment() throws Exception {
        Path empty = tempDir.resolve("empty.txt");
        Files.createFile(empty);

        Mail mail = valid().attach(empty).build();

        assertThat(mail.message().attachments()).hasSize(1);
    }

    @Test
    void acceptsNullOrEmptyDisplayName() throws Exception {
        Mail mail = valid().to(MailAddress.of("a@example.org", null),
                MailAddress.of("b@example.org", "")).build();

        assertThat(mail.message().to()).hasSize(3);
    }

    @Test
    void acceptsLineSeparatorInLiteralSubject() throws Exception {
        Mail mail = valid().subject("Line\u2028Separator").build();

        assertThat(mail.message().subject()).isEqualTo("Line\u2028Separator");
    }

    // ---- attachments ----

    @Test
    void resolvesContentTypeCaseInsensitively() throws Exception {
        Path pdf = file("Report.PDF");

        Attachment attachment = valid().attach(pdf).build().message().attachments().get(0);

        assertThat(attachment.contentType()).isEqualTo("application/pdf");
        assertThat(attachment.filename()).isEqualTo("Report.PDF");
    }

    @Test
    void keepsExplicitContentType() throws Exception {
        Path pdf = file("data.pdf");

        Attachment attachment = valid().attach(pdf, "text/x-custom").build()
                .message().attachments().get(0);

        assertThat(attachment.contentType()).isEqualTo("text/x-custom");
        assertThat(attachment.filename()).isEqualTo("data.pdf");
    }

    @Test
    void attachmentRecordKeepsFilenameAndResolvesType() throws Exception {
        Path file = file("plain.dat");

        Attachment attachment = valid()
                .attach(new Attachment("Agenda 2026.pdf", null, file, null)).build()
                .message().attachments().get(0);

        assertThat(attachment.filename()).isEqualTo("Agenda 2026.pdf");
        assertThat(attachment.contentType()).isEqualTo("application/pdf");
        assertThat(attachment.file()).isEqualTo(file);
    }

    @ParameterizedTest
    @ValueSource(strings = {"data.unknownext", "noextension"})
    void unknownOrMissingExtensionFallsBackToOctetStream(String name) throws Exception {
        Path file = file(name);
        Assumptions.assumeTrue(Files.probeContentType(file) == null,
                "this platform detects a content type for " + name);

        Attachment attachment = valid().attach(file).build().message().attachments().get(0);

        assertThat(attachment.contentType()).isEqualTo("application/octet-stream");
    }

    @Test
    void fileDeletedAfterBuildStillSends() throws Exception {
        Path file = file("agenda.pdf");
        MailAccount account = MailAccount.builder("main").transport("recording")
                .defaultFrom("newsletter@example.com").build();
        Mail mail = valid().attach(file).build();
        Files.delete(file);

        Mailer.of(account).send(mail);

        assertThat(((RecordingTransport) account.transport()).sent()).hasSize(1);
    }

    // ---- review additions ----

    @Test
    void repeatedBuildIsStableAndIgnoresLaterDataChanges() throws Exception {
        Map<String, String> data = new HashMap<>();
        data.put("n", "A");
        Mail.Builder builder = Mail.using("main").to("reader@example.org")
                .template(new MailTemplate("Hi $$n$$", "<p>$$n$$</p>", "t $$n$$"), data);

        Mail first = builder.build();
        data.put("n", "CHANGED");
        data.put("extra", "x");
        Mail second = builder.build();

        assertThat(second).isEqualTo(first);
        assertThat(second.message().subject()).isEqualTo("Hi A");
        builder.subject("x");
        assertThatThrownBy(builder::build)
                .isInstanceOf(InvalidMailException.class)
                .hasMessageContaining("cannot be combined");

        Path file = file("again.pdf");
        Mail.Builder withFile = valid().attach(file);
        withFile.build();
        Files.delete(file);
        assertThatThrownBy(withFile::build)
                .isInstanceOf(InvalidMailException.class)
                .hasMessageContaining("attachments[0]")
                .hasMessageContaining("not found");
    }

    @Test
    void rejectsPathWithoutFileName() {
        assertThatThrownBy(() -> valid().attach(tempDir.getRoot()).build())
                .isInstanceOf(InvalidMailException.class)
                .hasMessageContaining("attachments[0]")
                .hasMessageContaining("no file name");
    }

    @Test
    void attachNullArgumentsThrowNullPointerException() throws Exception {
        Path file = file("x.pdf");

        assertThatThrownBy(() -> valid().attach((Path) null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> valid().attach(file, (String) null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> valid().attach((Attachment) null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> valid().attach(new Attachment("x.pdf", null, null, null))
                .build())
                .isInstanceOf(InvalidMailException.class)
                .hasMessageContaining("attachments[0]: file is missing");
    }
}
