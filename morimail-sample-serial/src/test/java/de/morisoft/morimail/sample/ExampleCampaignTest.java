package de.morisoft.morimail.sample;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Dry-runs the shipped example campaign and checks its fixtures. Surefire runs in the module directory. */
class ExampleCampaignTest {

    private static final Path CAMPAIGN = Path.of("example-campaign");
    private static final Path ACCOUNT = CAMPAIGN.resolve("account.properties");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\$(!?)([A-Za-z0-9_.-]+)\\$\\$");
    private static final List<String> NAMES = List.of("anna", "ben", "clara", "david", "emma");

    @TempDir
    Path root;

    private static String name(int n, String person, String suffix) {
        return "0" + n + "-" + person + "@example.org" + suffix;
    }

    private static List<String> fileNames(Path dir) throws IOException {
        try (Stream<Path> files = Files.list(dir)) {
            return files.map(file -> file.getFileName().toString()).sorted().toList();
        }
    }

    @Test
    void dryRunWritesFiveRenderedMails() throws IOException {
        Path out = root.resolve("out");
        AtomicInteger factoryCalls = new AtomicInteger();
        List<Duration> waits = new ArrayList<>();

        SampleFixtures.Run run = SampleFixtures.run(a -> {
            factoryCalls.incrementAndGet();
            throw new AssertionError("a dry run must not build a Mailer");
        }, waits, "send", "--dry-run", "--account", ACCOUNT.toString(),
                "--campaign", CAMPAIGN.toString(), "--out", out.toString());

        assertThat(run.exit()).as(run.err()).isZero();
        assertThat(factoryCalls).hasValue(0);
        assertThat(Files.exists(CAMPAIGN.resolve("journal.jsonl"))).isFalse();

        List<String> expected = new ArrayList<>();
        for (int i = 0; i < NAMES.size(); i++) {
            expected.add(name(i + 1, NAMES.get(i), ".html"));
            expected.add(name(i + 1, NAMES.get(i), ".subject.txt"));
            expected.add(name(i + 1, NAMES.get(i), ".txt"));
        }
        assertThat(fileNames(out)).containsExactlyInAnyOrderElementsOf(expected);
        assertThat(fileNames(out)).hasSize(15);

        String clara = Files.readString(out.resolve(name(3, "clara", ".html")));
        assertThat(clara).contains("Tips, tricks &amp; shortcuts").contains("Köln");
        assertThat(Files.readString(out.resolve(name(1, "anna", ".subject.txt"))).strip())
                .isEqualTo("Welcome to the November update, Anna");
        assertThat(run.outLines().get(run.outLines().size() - 1))
                .isEqualTo("dry run: 5 mails written to " + out);
    }

    @Test
    void usesThreePlaceholdersAndFiveExampleRows() throws IOException {
        Set<String> names = new TreeSet<>();
        for (String file : List.of("subject.txt", "body.html", "body.txt")) {
            Matcher matcher = PLACEHOLDER.matcher(Files.readString(CAMPAIGN.resolve(file), StandardCharsets.UTF_8));
            while (matcher.find()) {
                names.add(matcher.group(2));
            }
        }
        assertThat(names).containsExactly("city", "firstName", "topic");

        List<String> problems = new ArrayList<>();
        Campaign campaign = Campaign.read(CAMPAIGN, problems);

        assertThat(problems).isEmpty();
        assertThat(campaign.rows()).extracting(Campaign.Row::email).containsExactly(
                "anna@example.org", "ben@example.org", "clara@example.org",
                "david@example.org", "emma@example.org");
        for (Campaign.Row row : campaign.rows()) {
            assertThat(row.fields().keySet()).containsExactly("email", "firstName", "city", "topic");
            assertThat(row.fields().values()).allSatisfy(value -> assertThat(value).isNotBlank());
        }
    }

    @Test
    void accountFileHoldsOnlyExampleValues() {
        List<String> problems = new ArrayList<>();

        AccountFile account = AccountFile.read(ACCOUNT, problems);

        assertThat(problems).isEmpty();
        assertThat(account.id()).isEqualTo("entra-main");
        assertThat(account.tenantId()).isEqualTo("00000000-0000-0000-0000-000000000000");
        assertThat(account.clientId()).isEqualTo("00000000-0000-0000-0000-000000000000");
        assertThat(account.from()).endsWith("@example.com");
        assertThat(account.certificatePath()).isEqualTo("/path/app.p12");
        assertThat(account.certificatePasswordEnv()).isEqualTo("MAIL_CERT_PASSWORD");
        assertThat(account.secretEnv()).isNull();
    }
}
