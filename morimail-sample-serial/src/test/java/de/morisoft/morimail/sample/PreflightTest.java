package de.morisoft.morimail.sample;

import static de.morisoft.morimail.sample.SampleFixtures.ACCOUNT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.morisoft.morimail.InvalidMailException;
import de.morisoft.morimail.Mail;
import de.morisoft.morimail.MailAccount;
import de.morisoft.morimail.MailTemplate;
import de.morisoft.morimail.Mailer;
import de.morisoft.morimail.test.RecordingTransport;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PreflightTest {

    private static final String BROKEN_CSV = String.join("\n",
            "email,firstName",
            "anna@example.org,Anna",
            "not-an-address,Ben",
            "ANNA@example.org,Anna") + "\n";

    private static final String PLACEHOLDER_CITY =
            "body.html: placeholder $$city$$ has no column in recipients.csv";
    private static final String DUPLICATE =
            "recipients.csv line 4: duplicate address, first seen in line 2";
    private static final String INVALID_ADDRESS = "recipients.csv line 3: to[0]: "
            + "email must contain exactly one '@' with text before and after it";

    @TempDir
    Path root;

    @Test
    void brokenCampaignReportsEveryProblemTogether() throws IOException {
        Path account = SampleFixtures.writeAccount(root, ACCOUNT);
        Path campaign = SampleFixtures.writeCampaign(root, BROKEN_CSV);
        MailAccount recording = SampleFixtures.recordingAccount();
        RecordingTransport transport = (RecordingTransport) recording.transport();
        transport.failValidation(new InvalidMailException("too large for the test"));

        SampleFixtures.Run run = SampleFixtures.run(a -> Mailer.of(recording), new ArrayList<>(),
                "send", "--account", account.toString(), "--campaign", campaign.toString());

        assertThat(run.exit()).isEqualTo(2);
        assertThat(run.out()).isEmpty();
        assertThat(run.errLines()).containsExactly(
                PLACEHOLDER_CITY,
                DUPLICATE,
                INVALID_ADDRESS,
                "mailer.validate rejected the mail: too large for the test (recipients.csv lines 2, 4)",
                "Pre-flight failed with 4 problem(s); nothing was sent.");
        assertThat(transport.attempts()).isZero();
        assertThat(campaign.resolve("journal.jsonl")).doesNotExist();
    }

    // The Graph account's validate runs locally. A send by mistake would call the token provider
    // and then localhost port 1, so no real host is reached.
    private static MailAccount graphAccount(AtomicInteger tokenCalls) {
        return MailAccount.builder("main").transport("graph")
                .option("baseUrl", "http://localhost:1/v1.0")
                .auth(scope -> {
                    tokenCalls.incrementAndGet();
                    return "token";
                })
                .build();
    }

    @Test
    void laterRowThatGraphRejectsStopsPreflight() throws IOException {
        Path account = SampleFixtures.writeAccount(root, ACCOUNT);
        Path campaign = SampleFixtures.writeCampaign(root, "email,firstName,city\n"
                + "anna@example.org,Anna,Berlin\n"
                + "ben@example.org,Ben,Hamburg\n"
                + "clara@example.org,Clara," + "x".repeat(4_200_000) + "\n");
        AtomicInteger tokenCalls = new AtomicInteger();
        MailAccount graph = graphAccount(tokenCalls);

        SampleFixtures.Run run = SampleFixtures.run(a -> Mailer.of(graph), new ArrayList<>(),
                "send", "--account", account.toString(), "--campaign", campaign.toString());

        assertThat(run.exit()).isEqualTo(2);
        assertThat(run.out()).isEmpty();
        assertThat(run.errLines()).hasSize(2);
        assertThat(run.errLines().get(0))
                .startsWith("recipients.csv line 4: mailer.validate rejected the mail: estimated request size ")
                .endsWith(" bytes is above the limit of 4 MiB (4194304 bytes)");
        assertThat(run.errLines().get(1))
                .isEqualTo("Pre-flight failed with 1 problem(s); nothing was sent.");
        assertThat(tokenCalls).hasValue(0);
        assertThat(campaign.resolve("journal.jsonl")).doesNotExist();
    }

    @Test
    void semicolonHeaderIsReportedWithEveryOtherProblem() throws IOException {
        Path account = SampleFixtures.writeAccount(root, ACCOUNT.replace(
                "tenantId=00000000-0000-0000-0000-000000000000\n", ""));
        Path campaign = SampleFixtures.writeCampaign(root,
                "email;firstName;city\nanna@example.org;Anna;Berlin\n");
        MailAccount recording = SampleFixtures.recordingAccount();
        AtomicInteger factoryCalls = new AtomicInteger();

        SampleFixtures.Run run = SampleFixtures.run(a -> {
            factoryCalls.incrementAndGet();
            return Mailer.of(recording);
        }, new ArrayList<>(), "send", "--account", account.toString(), "--campaign",
                campaign.toString());

        assertThat(run.exit()).isEqualTo(2);
        assertThat(run.out()).isEmpty();
        assertThat(run.errLines()).containsExactly(
                "account.properties: missing key 'tenantId'",
                "recipients.csv: the header looks ';'-delimited; this reader accepts commas only",
                "recipients.csv: no column named 'email' in the header",
                "subject.txt: placeholder $$firstName$$ has no column in recipients.csv",
                "body.html: placeholder $$firstName$$ has no column in recipients.csv",
                PLACEHOLDER_CITY,
                "body.txt: placeholder $$firstName$$ has no column in recipients.csv",
                "Pre-flight failed with 7 problem(s); nothing was sent.");
        assertThat(factoryCalls).hasValue(0);
        assertThat(((RecordingTransport) recording.transport()).attempts()).isZero();
        assertThat(campaign.resolve("journal.jsonl")).doesNotExist();
    }

    @Test
    void dryRunReportsTheSameProblemsWithoutTheMailer() throws IOException {
        Path account = SampleFixtures.writeAccount(root, ACCOUNT);
        Path campaign = SampleFixtures.writeCampaign(root, BROKEN_CSV);
        Path out = root.resolve("out");
        AtomicInteger factoryCalls = new AtomicInteger();

        SampleFixtures.Run run = SampleFixtures.run(a -> {
            factoryCalls.incrementAndGet();
            return Mailer.of(SampleFixtures.recordingAccount());
        }, new ArrayList<>(), "send", "--account", account.toString(), "--campaign",
                campaign.toString(), "--dry-run", "--out", out.toString());

        assertThat(run.exit()).isEqualTo(2);
        assertThat(run.errLines()).containsExactly(
                PLACEHOLDER_CITY,
                DUPLICATE,
                INVALID_ADDRESS,
                "Pre-flight failed with 3 problem(s); nothing was sent.");
        assertThat(factoryCalls).hasValue(0);
        assertThat(out).doesNotExist();
    }

    @Test
    void accountAndCampaignProblemsAreReportedTogether() throws IOException {
        Path account = SampleFixtures.writeAccount(root, ACCOUNT.replace(
                "tenantId=00000000-0000-0000-0000-000000000000\n", ""));
        Path campaign = SampleFixtures.writeCampaign(root,
                "email,firstName\nanna@example.org,Anna\n");
        AtomicInteger factoryCalls = new AtomicInteger();

        SampleFixtures.Run run = SampleFixtures.run(a -> {
            factoryCalls.incrementAndGet();
            return Mailer.of(SampleFixtures.recordingAccount());
        }, new ArrayList<Duration>(), "send", "--account", account.toString(), "--campaign",
                campaign.toString());

        assertThat(run.exit()).isEqualTo(2);
        assertThat(run.errLines()).containsExactly(
                "account.properties: missing key 'tenantId'",
                PLACEHOLDER_CITY,
                "Pre-flight failed with 2 problem(s); nothing was sent.");
        assertThat(factoryCalls).hasValue(0);
    }

    @Test
    void placeholderMatchIsCaseSensitive() throws IOException {
        Path account = SampleFixtures.writeAccount(root, ACCOUNT);
        Path campaign = SampleFixtures.writeCampaign(root,
                "email,firstname,city\nanna@example.org,Anna,Berlin\n");

        SampleFixtures.Run run = SampleFixtures.run(a -> Mailer.of(SampleFixtures.recordingAccount()),
                new ArrayList<>(), "send", "--account", account.toString(), "--campaign",
                campaign.toString(), "--dry-run", "--out", root.resolve("out").toString());

        assertThat(run.exit()).isEqualTo(2);
        assertThat(run.errLines()).containsExactly(
                "subject.txt: placeholder $$firstName$$ has no column in recipients.csv",
                "body.html: placeholder $$firstName$$ has no column in recipients.csv",
                "body.txt: placeholder $$firstName$$ has no column in recipients.csv",
                "Pre-flight failed with 3 problem(s); nothing was sent.");
    }

    @Test
    void rawPlaceholderIsCheckedToo() throws IOException {
        Path account = SampleFixtures.writeAccount(root, ACCOUNT);
        Path campaign = SampleFixtures.writeCampaign(root, SampleFixtures.THREE_ROWS);
        Files.writeString(campaign.resolve("body.html"), "<p>$$!banner$$ $$firstName$$ $$city$$</p>");

        SampleFixtures.Run run = SampleFixtures.run(a -> Mailer.of(SampleFixtures.recordingAccount()),
                new ArrayList<>(), "send", "--account", account.toString(), "--campaign",
                campaign.toString(), "--dry-run", "--out", root.resolve("out").toString());

        assertThat(run.exit()).isEqualTo(2);
        assertThat(run.errLines()).containsExactly(
                "body.html: placeholder $$!banner$$ has no column in recipients.csv",
                "Pre-flight failed with 1 problem(s); nothing was sent.");
    }

    @Test
    void headerOnlyCsvIsOneProblem() throws IOException {
        Path account = SampleFixtures.writeAccount(root, ACCOUNT);
        Path campaign = SampleFixtures.writeCampaign(root, "email,firstName,city\n");

        SampleFixtures.Run run = SampleFixtures.run(a -> Mailer.of(SampleFixtures.recordingAccount()),
                new ArrayList<>(), "send", "--account", account.toString(), "--campaign",
                campaign.toString());

        assertThat(run.exit()).isEqualTo(2);
        assertThat(run.errLines()).containsExactly(
                "recipients.csv: no recipient rows",
                "Pre-flight failed with 1 problem(s); nothing was sent.");
    }

    @Test
    void extractedNamesSatisfyTheCoreRenderer() throws InvalidMailException {
        String text = "A $$a$$ $$!b$$ $$c.d-e_f$$ $$ä$$ $$x y$$ $$";

        assertThat(Campaign.placeholders(text).keySet()).containsExactly("a", "b", "c.d-e_f");

        MailTemplate template = new MailTemplate("subject", text, null);
        Map<String, String> data = new LinkedHashMap<>();
        data.put("a", "1");
        data.put("b", "2");
        data.put("c.d-e_f", "3");
        Mail.using("main").from("newsletter@example.com").to("anna@example.org")
                .template(template, data).build();

        data.remove("b");
        assertThatThrownBy(() -> Mail.using("main").from("newsletter@example.com")
                .to("anna@example.org").template(template, data).build())
                .isInstanceOf(InvalidMailException.class);
    }

    @Test
    void aDefectOfEveryMailIsReportedOnce() throws IOException {
        Path account = SampleFixtures.writeAccount(root, ACCOUNT);
        Path campaign = SampleFixtures.writeCampaign(root, SampleFixtures.THREE_ROWS);
        Files.writeString(campaign.resolve("subject.txt"), "Hello\nthere");

        SampleFixtures.Run run = SampleFixtures.run(a -> Mailer.of(SampleFixtures.recordingAccount()),
                new ArrayList<>(), "send", "--account", account.toString(), "--campaign",
                campaign.toString());

        assertThat(run.exit()).isEqualTo(2);
        assertThat(run.errLines()).containsExactly(
                "subject contains a line break (all 3 rows)",
                "Pre-flight failed with 1 problem(s); nothing was sent.");
    }

    @Test
    void inlineProblemsAreReportedWithTheOtherFindings() throws IOException {
        Path account = SampleFixtures.writeAccount(root, ACCOUNT.replace(
                "tenantId=00000000-0000-0000-0000-000000000000\n", ""));
        Path campaign = SampleFixtures.writeCampaign(root, SampleFixtures.THREE_ROWS);
        Files.writeString(campaign.resolve("body.html"), "<p><img src=\"cid:logo\">"
                + "<img src=\"cid:banner\"> Hi $$firstName$$ from $$city$$ in $$country$$</p>");
        SampleFixtures.writeInline(campaign, "extra.gif", "logo.jpg", "logo.png", "my logo.png",
                "notes.txt");
        MailAccount recording = SampleFixtures.recordingAccount();

        SampleFixtures.Run run = SampleFixtures.run(a -> Mailer.of(recording), new ArrayList<>(),
                "send", "--account", account.toString(), "--campaign", campaign.toString());

        assertThat(run.exit()).isEqualTo(2);
        assertThat(run.out()).isEmpty();
        assertThat(run.errLines()).containsExactly(
                "account.properties: missing key 'tenantId'",
                "inline/logo.png: same content ID as inline/logo.jpg (cid:logo)",
                "inline/my logo.png: contentId contains whitespace, a quote, a parenthesis "
                        + "or an angle bracket",
                "inline/notes.txt: inline image notes has content type text/plain, "
                        + "which is not an image type",
                "body.html: cid:banner has no image in inline/",
                "inline/extra.gif: no cid:extra reference in body.html",
                "body.html: placeholder $$country$$ has no column in recipients.csv",
                "Pre-flight failed with 7 problem(s); nothing was sent.");
        assertThat(((RecordingTransport) recording.transport()).attempts()).isZero();
        assertThat(campaign.resolve("journal.jsonl")).doesNotExist();
    }

    @Test
    void rowChecksWaitUntilTheInlineProblemsAreFixed() throws IOException {
        Path account = SampleFixtures.writeAccount(root, ACCOUNT);
        Path campaign = SampleFixtures.writeCampaign(root,
                "email,firstName,city\nanna@example.org,Anna,Berlin\nnot-an-address,Ben,Hamburg\n");
        Path inline = SampleFixtures.writeInline(campaign, "extra.gif");
        Path out = root.resolve("out");

        SampleFixtures.Run first = SampleFixtures.run(a -> Mailer.of(SampleFixtures.recordingAccount()),
                new ArrayList<>(), "send", "--account", account.toString(), "--campaign",
                campaign.toString(), "--dry-run", "--out", out.toString());

        assertThat(first.exit()).isEqualTo(2);
        assertThat(first.errLines()).containsExactly(
                "inline/extra.gif: no cid:extra reference in body.html",
                "Pre-flight failed with 1 problem(s); nothing was sent.");
        assertThat(out).doesNotExist();

        Files.delete(inline.resolve("extra.gif"));
        SampleFixtures.Run second = SampleFixtures.run(a -> Mailer.of(SampleFixtures.recordingAccount()),
                new ArrayList<>(), "send", "--account", account.toString(), "--campaign",
                campaign.toString(), "--dry-run", "--out", out.toString());

        assertThat(second.exit()).isEqualTo(2);
        assertThat(second.errLines()).containsExactly(
                INVALID_ADDRESS,
                "Pre-flight failed with 1 problem(s); nothing was sent.");
        assertThat(out).doesNotExist();
    }

    @Test
    void cidThatOnlyARecipientValueAddsIsReportedByBuild() throws IOException {
        Path account = SampleFixtures.writeAccount(root, ACCOUNT);
        Path campaign = SampleFixtures.writeCampaign(root, "email,firstName,city,note\n"
                + "anna@example.org,Anna,Berlin,\nben@example.org,Ben,Hamburg,see cid:x\n");
        Files.writeString(campaign.resolve("body.html"),
                "<p>Hi $$firstName$$ from $$city$$ $$note$$ </p>");

        SampleFixtures.Run run = SampleFixtures.run(a -> Mailer.of(SampleFixtures.recordingAccount()),
                new ArrayList<>(), "send", "--account", account.toString(), "--campaign",
                campaign.toString(), "--dry-run", "--out", root.resolve("out").toString());

        assertThat(run.exit()).isEqualTo(2);
        assertThat(run.errLines()).containsExactly(
                "recipients.csv line 3: no inline image for cid:x",
                "Pre-flight failed with 1 problem(s); nothing was sent.");
    }

    @Test
    void theSameDefectInSomeRowsIsReportedOnceWithItsLines() throws IOException {
        Path account = SampleFixtures.writeAccount(root, ACCOUNT);
        String csv = String.join("\n", "email,firstName,city",
                "anna@example.org,Anna,Berlin",
                "not-an-address,Ben,Hamburg",
                "ben@example.org,Ben,Hamburg",
                "also-bad,Clara,Köln") + "\n";
        Path campaign = SampleFixtures.writeCampaign(root, csv);

        SampleFixtures.Run run = SampleFixtures.run(a -> Mailer.of(SampleFixtures.recordingAccount()),
                new ArrayList<>(), "send", "--account", account.toString(), "--campaign",
                campaign.toString());

        assertThat(run.exit()).isEqualTo(2);
        assertThat(run.errLines()).containsExactly(
                "to[0]: email must contain exactly one '@' with text before and after it "
                        + "(recipients.csv lines 3, 5)",
                "Pre-flight failed with 1 problem(s); nothing was sent.");
    }
}
