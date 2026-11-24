package de.morisoft.morimail.sample;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.morisoft.morimail.MailAccount;
import de.morisoft.morimail.Mailer;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import picocli.CommandLine;

/** Campaign files, an account file, a recording account and output capture for the sample tests. */
final class SampleFixtures {

    static final String SUBJECT = "Hello $$firstName$$";
    static final String HTML = "<p>Hi $$firstName$$ from $$city$$</p>";
    static final String TEXT = "Hi $$firstName$$";
    static final String INLINE_HTML = "<p><img src=\"cid:logo\"> Hi $$firstName$$ from $$city$$</p>";

    static final String THREE_ROWS = String.join("\n",
            "email,firstName,city",
            "anna@example.org,Anna,Berlin",
            "ben@example.org,Ben,Hamburg",
            "clara@example.org,Clara,Köln") + "\n";

    static final String ACCOUNT = String.join("\n",
            "account.id=main",
            "tenantId=00000000-0000-0000-0000-000000000000",
            "clientId=00000000-0000-0000-0000-000000000000",
            "from=newsletter@example.com",
            "secretEnv=MAIL_CLIENT_SECRET") + "\n";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private SampleFixtures() {
    }

    /** The outcome of one command line run. */
    record Run(int exit, String out, String err) {
        List<String> outLines() {
            return out.lines().toList();
        }

        List<String> errLines() {
            return err.lines().toList();
        }
    }

    /** Writes root/campaign with the template files, the given CSV and one attachment. */
    static Path writeCampaign(Path root, String csv) throws IOException {
        Path dir = root.resolve("campaign");
        Files.createDirectories(dir.resolve("attachments"));
        Files.writeString(dir.resolve("subject.txt"), SUBJECT + "\n");
        Files.writeString(dir.resolve("body.html"), HTML);
        Files.writeString(dir.resolve("body.txt"), TEXT);
        Files.writeString(dir.resolve("recipients.csv"), csv);
        Files.writeString(dir.resolve("attachments").resolve("info.txt"), "example attachment");
        return dir;
    }

    /**
     * Writes the named files into campaign/inline and returns that directory. The core resolves the
     * type from the extension and never decodes the content, so three bytes are enough.
     */
    static Path writeInline(Path campaign, String... names) throws IOException {
        Path dir = Files.createDirectories(campaign.resolve("inline"));
        for (String name : names) {
            Files.writeString(dir.resolve(name), "png");
        }
        return dir;
    }

    /** Writes root/account.properties. */
    static Path writeAccount(Path root, String content) throws IOException {
        Path file = root.resolve("account.properties");
        Files.writeString(file, content);
        return file;
    }

    /** An account over the recording transport, without a default sender. */
    static MailAccount recordingAccount() {
        return MailAccount.builder("main").transport("recording").build();
    }

    /** Runs the same command tree as main, with the given factory and a recording sleep. */
    static Run run(Function<AccountFile, Mailer> factory, List<Duration> waits, String... args) {
        return run(name -> null, factory, waits, args);
    }

    /** The same with an injected environment. */
    static Run run(Function<String, String> env, Function<AccountFile, Mailer> factory,
            List<Duration> waits, String... args) {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        CommandLine commandLine = SerialMail.commandLine(factory, waits::add, env);
        commandLine.setOut(new PrintWriter(out, true));
        commandLine.setErr(new PrintWriter(err, true));
        int exit = commandLine.execute(args);
        return new Run(exit, out.toString(), err.toString());
    }

    /** Every non-blank line of a journal file, parsed. */
    static List<JsonNode> journalLines(Path file) throws IOException {
        List<JsonNode> lines = new ArrayList<>();
        for (String line : Files.readAllLines(file)) {
            if (!line.isBlank()) {
                lines.add(MAPPER.readTree(line));
            }
        }
        return lines;
    }
}
