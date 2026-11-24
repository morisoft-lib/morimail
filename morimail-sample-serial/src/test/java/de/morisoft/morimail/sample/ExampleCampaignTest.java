package de.morisoft.morimail.sample;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Dry-runs the shipped example campaign and checks its fixtures. Surefire runs in the module directory. */
class ExampleCampaignTest {

    private static final Path CAMPAIGN = Path.of("example-campaign");
    private static final Path ACCOUNT = CAMPAIGN.resolve("account.properties");
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
        expected.add("inline");
        assertThat(fileNames(out)).containsExactlyInAnyOrderElementsOf(expected);
        assertThat(fileNames(out)).hasSize(16);
        Path logo = CAMPAIGN.resolve("inline").resolve("logo.png");
        assertThat(fileNames(out.resolve("inline"))).containsExactly("logo.png");
        assertThat(Files.readAllBytes(out.resolve("inline").resolve("logo.png")))
                .isEqualTo(Files.readAllBytes(logo));

        String clara = Files.readString(out.resolve(name(3, "clara", ".html")));
        assertThat(clara).contains("Tips, tricks &amp; shortcuts").contains("Köln");
        assertThat(Files.readString(out.resolve(name(1, "anna", ".subject.txt"))).strip())
                .isEqualTo("Welcome to the November update, Anna");
        assertThat(run.outLines().get(run.outLines().size() - 1))
                .isEqualTo("dry run: 5 mails written to " + out);
        // pdf and png come from the core's built-in extension table, so this holds on every host
        assertThat(run.outLines().get(0))
                .startsWith("attachments: info.pdf (application/pdf, ")
                .contains("sample.png (image/png, ")
                .doesNotContain("logo.png");
        assertThat(run.outLines().get(1))
                .isEqualTo("inline: logo.png (image/png, " + Files.size(logo) + " bytes, cid:logo)");

        Pattern src = Pattern.compile("<img src=\"([^\"]*)\"");
        for (int i = 0; i < NAMES.size(); i++) {
            String html = Files.readString(out.resolve(name(i + 1, NAMES.get(i), ".html")));
            assertThat(html)
                    .contains("<p><img src=\"inline/logo.png\" alt=\"morimail logo\" width=\"96\" height=\"48\"></p>")
                    .doesNotContain("cid:");
            Matcher matcher = src.matcher(html);
            assertThat(matcher.find()).isTrue();
            BufferedImage shown = ImageIO.read(out.resolve(URI.create(matcher.group(1)).getPath()).toFile());
            assertThat(shown.getWidth()).isEqualTo(96);
            assertThat(shown.getHeight()).isEqualTo(48);
        }
    }

    @Test
    void usesThreePlaceholdersAndFiveExampleRows() throws IOException {
        Set<String> names = new TreeSet<>();
        for (String file : List.of("subject.txt", "body.html", "body.txt")) {
            Matcher matcher = Campaign.PLACEHOLDER.matcher(Files.readString(CAMPAIGN.resolve(file), StandardCharsets.UTF_8));
            while (matcher.find()) {
                names.add(matcher.group(2));
            }
        }
        assertThat(names).containsExactly("city", "firstName", "topic");

        List<String> problems = new ArrayList<>();
        Campaign campaign = Campaign.read(CAMPAIGN, problems);

        assertThat(problems).isEmpty();
        assertThat(campaign.inlineImages()).extracting(file -> file.getFileName().toString())
                .containsExactly("logo.png");
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

        AccountFile account = AccountFile.read(ACCOUNT, name -> null, problems);

        assertThat(problems).isEmpty();
        assertThat(account.id()).isEqualTo("entra-main");
        assertThat(account.tenantId()).isEqualTo("00000000-0000-0000-0000-000000000000");
        assertThat(account.clientId()).isEqualTo("00000000-0000-0000-0000-000000000000");
        assertThat(account.from()).endsWith("@example.com");
        assertThat(account.certificatePath()).isEqualTo("/path/app.p12");
        assertThat(account.certificatePasswordEnv()).isEqualTo("MAIL_CERT_PASSWORD");
        assertThat(account.secretEnv()).isNull();
    }

    private static final Path DEV_ACCOUNT = Path.of("dev-account.properties");
    private static final Path DEV_SECRET_ACCOUNT = Path.of("dev-account-secret.properties");
    private static final Map<String, String> DEV_ENV = Map.of(
            "MORIMAIL_DEV_TENANT_ID", "00000000-0000-0000-0000-000000000000",
            "MORIMAIL_DEV_CLIENT_ID", "00000000-0000-0000-0000-000000000000",
            "MORIMAIL_DEV_FROM", "sender@example.com",
            "MORIMAIL_DEV_CERT_PATH", "/path/to/test-app.p12");

    @Test
    void devAccountFileReadsTheDevelopmentVariables() {
        List<String> problems = new ArrayList<>();

        AccountFile account = AccountFile.read(DEV_ACCOUNT, DEV_ENV::get, problems);

        assertThat(problems).isEmpty();
        assertThat(account.id()).isEqualTo("entra-dev");
        assertThat(account.tenantId()).isEqualTo("00000000-0000-0000-0000-000000000000");
        assertThat(account.clientId()).isEqualTo("00000000-0000-0000-0000-000000000000");
        assertThat(account.from()).isEqualTo("sender@example.com");
        assertThat(account.certificatePath()).isEqualTo("/path/to/test-app.p12");
        assertThat(account.certificatePasswordEnv()).isEqualTo("MORIMAIL_DEV_CERT_PASSWORD");
        assertThat(account.secretEnv()).isNull();
    }

    @Test
    void devAccountDryRunNamesEveryUnsetVariable() {
        SampleFixtures.Run run = SampleFixtures.run(a -> {
            throw new AssertionError("a dry run must not build a Mailer");
        }, new ArrayList<>(), "send", "--dry-run", "--account", DEV_ACCOUNT.toString(),
                "--campaign", CAMPAIGN.toString(), "--out", root.resolve("out").toString());

        assertThat(run.exit()).isEqualTo(2);
        assertThat(run.errLines()).containsExactly(
                "dev-account.properties: key 'tenantId' uses ${MORIMAIL_DEV_TENANT_ID}, "
                        + "which is not set or is blank",
                "dev-account.properties: key 'clientId' uses ${MORIMAIL_DEV_CLIENT_ID}, "
                        + "which is not set or is blank",
                "dev-account.properties: key 'from' uses ${MORIMAIL_DEV_FROM}, "
                        + "which is not set or is blank",
                "dev-account.properties: key 'certificate.path' uses ${MORIMAIL_DEV_CERT_PATH}, "
                        + "which is not set or is blank",
                "Pre-flight failed with 4 problem(s); nothing was sent.");
    }

    @Test
    void devAccountDryRunUsesTheDevelopmentVariables() {
        AtomicInteger factoryCalls = new AtomicInteger();

        SampleFixtures.Run run = SampleFixtures.run(DEV_ENV::get, a -> {
            factoryCalls.incrementAndGet();
            throw new AssertionError("a dry run must not build a Mailer");
        }, new ArrayList<>(), "send", "--dry-run", "--account", DEV_ACCOUNT.toString(),
                "--campaign", CAMPAIGN.toString(), "--out", root.resolve("out").toString());

        assertThat(run.exit()).as(run.err()).isZero();
        assertThat(factoryCalls).hasValue(0);
    }

    @Test
    void devSecretAccountFileReadsTheDevelopmentVariables() {
        List<String> problems = new ArrayList<>();

        AccountFile account = AccountFile.read(DEV_SECRET_ACCOUNT, DEV_ENV::get, problems);

        assertThat(problems).isEmpty();
        assertThat(account.id()).isEqualTo("entra-dev");
        assertThat(account.tenantId()).isEqualTo("00000000-0000-0000-0000-000000000000");
        assertThat(account.clientId()).isEqualTo("00000000-0000-0000-0000-000000000000");
        assertThat(account.from()).isEqualTo("sender@example.com");
        assertThat(account.certificatePath()).isNull();
        assertThat(account.certificatePasswordEnv()).isNull();
        assertThat(account.secretEnv()).isEqualTo("MORIMAIL_DEV_SECRET");
    }

    @Test
    void devSecretAccountDryRunNamesEveryUnsetVariable() {
        SampleFixtures.Run run = SampleFixtures.run(a -> {
            throw new AssertionError("a dry run must not build a Mailer");
        }, new ArrayList<>(), "send", "--dry-run", "--account", DEV_SECRET_ACCOUNT.toString(),
                "--campaign", CAMPAIGN.toString(), "--out", root.resolve("out").toString());

        assertThat(run.exit()).isEqualTo(2);
        assertThat(run.errLines()).containsExactly(
                "dev-account-secret.properties: key 'tenantId' uses ${MORIMAIL_DEV_TENANT_ID}, "
                        + "which is not set or is blank",
                "dev-account-secret.properties: key 'clientId' uses ${MORIMAIL_DEV_CLIENT_ID}, "
                        + "which is not set or is blank",
                "dev-account-secret.properties: key 'from' uses ${MORIMAIL_DEV_FROM}, "
                        + "which is not set or is blank",
                "Pre-flight failed with 3 problem(s); nothing was sent.");
    }

    @Test
    void devSecretAccountDryRunUsesTheDevelopmentVariables() {
        AtomicInteger factoryCalls = new AtomicInteger();

        SampleFixtures.Run run = SampleFixtures.run(DEV_ENV::get, a -> {
            factoryCalls.incrementAndGet();
            throw new AssertionError("a dry run must not build a Mailer");
        }, new ArrayList<>(), "send", "--dry-run", "--account", DEV_SECRET_ACCOUNT.toString(),
                "--campaign", CAMPAIGN.toString(), "--out", root.resolve("out").toString());

        assertThat(run.exit()).as(run.err()).isZero();
        assertThat(factoryCalls).hasValue(0);
    }

    @Test
    void attachmentsAreSmallAndCarryNoMetadata() throws IOException {
        Path pdfFile = CAMPAIGN.resolve("attachments").resolve("info.pdf");
        Path pngFile = CAMPAIGN.resolve("attachments").resolve("sample.png");
        assertThat(CAMPAIGN.resolve("attachments").resolve("logo.png")).doesNotExist();
        assertThat(Files.size(pdfFile)).isLessThan(102400);
        assertThat(Files.size(pngFile)).isLessThan(102400);

        BufferedImage image = ImageIO.read(pngFile.toFile());
        assertThat(image.getWidth()).isEqualTo(240);
        assertThat(image.getHeight()).isEqualTo(120);
        assertThat(pngChunkTypes(Files.readAllBytes(pngFile))).containsExactly("IHDR", "IDAT", "IEND");

        String pdf = new String(Files.readAllBytes(pdfFile), StandardCharsets.ISO_8859_1);
        assertThat(pdf).startsWith("%PDF-1.4");
        assertThat(pdf).matches("(?s).*%%EOF\\n?");
        for (String key : List.of("Info", "Producer", "Creator", "Author", "CreationDate", "ModDate")) {
            assertThat(pdf).as("PDF key /" + key).doesNotContain("/" + key);
        }
        int xref = pdf.indexOf("\nxref\n") + 1;
        assertThat(xref).isPositive();
        Matcher startxref = Pattern.compile("startxref\\n(\\d+)\\n").matcher(pdf);
        assertThat(startxref.find()).isTrue();
        assertThat(Integer.parseInt(startxref.group(1))).isEqualTo(xref);
        Matcher entries = Pattern.compile("(\\d{10}) 00000 n \\n").matcher(pdf);
        int count = 0;
        while (entries.find()) {
            count++;
            int offset = Integer.parseInt(entries.group(1));
            assertThat(pdf.startsWith(count + " 0 obj", offset)).as("object " + count).isTrue();
        }
        assertThat(count).isEqualTo(5);
    }

    @Test
    void inlineLogoIsTinyAndMatchesTheImgSize() throws IOException {
        Path logo = CAMPAIGN.resolve("inline").resolve("logo.png");

        assertThat(Files.size(logo)).isLessThan(1024);
        BufferedImage image = ImageIO.read(logo.toFile());
        assertThat(image.getWidth()).isEqualTo(96);
        assertThat(image.getHeight()).isEqualTo(48);
        assertThat(pngChunkTypes(Files.readAllBytes(logo))).containsExactly("IHDR", "IDAT", "IEND");
        assertThat(Files.readString(CAMPAIGN.resolve("body.html")))
                .contains("<img src=\"cid:logo\" alt=\"morimail logo\" width=\"96\" height=\"48\">");
    }

    private static List<String> pngChunkTypes(byte[] png) {
        List<String> types = new ArrayList<>();
        int pos = 8;
        while (pos < png.length) {
            int length = ((png[pos] & 0xff) << 24) | ((png[pos + 1] & 0xff) << 16)
                    | ((png[pos + 2] & 0xff) << 8) | (png[pos + 3] & 0xff);
            types.add(new String(png, pos + 4, 4, StandardCharsets.US_ASCII));
            pos += 12 + length;
        }
        return types;
    }
}
