package de.morisoft.morimail.sample;

import static de.morisoft.morimail.sample.SampleFixtures.ACCOUNT;
import static de.morisoft.morimail.sample.SampleFixtures.INLINE_HTML;
import static de.morisoft.morimail.sample.SampleFixtures.THREE_ROWS;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DryRunTest {

    @TempDir
    Path root;

    private final AtomicInteger factoryCalls = new AtomicInteger();
    private final List<Duration> waits = new ArrayList<>();

    private SampleFixtures.Run dryRun(Path campaign, Path out) throws IOException {
        Path account = SampleFixtures.writeAccount(root, ACCOUNT);
        return SampleFixtures.run(a -> {
            factoryCalls.incrementAndGet();
            throw new AssertionError("a dry run must not build a Mailer");
        }, waits, "send", "--dry-run", "--account", account.toString(),
                "--campaign", campaign.toString(), "--out", out.toString());
    }

    private static String name(String prefix, String email, String suffix) {
        return prefix + "-" + email + suffix;
    }

    private static List<String> fileNames(Path dir) throws IOException {
        try (Stream<Path> files = Files.list(dir)) {
            return files.map(file -> file.getFileName().toString()).sorted().toList();
        }
    }

    @Test
    void writesRenderedFilesAndListsAttachments() throws IOException {
        Path campaign = SampleFixtures.writeCampaign(root, THREE_ROWS);
        Path out = root.resolve("out");

        SampleFixtures.Run run = dryRun(campaign, out);

        assertThat(run.exit()).isZero();
        assertThat(run.err()).isEmpty();
        assertThat(run.outLines()).containsExactly(
                "attachments: info.txt (text/plain, 18 bytes)",
                "[1/3] anna@example.org rendered",
                "[2/3] ben@example.org rendered",
                "[3/3] clara@example.org rendered",
                "dry run: 3 mails written to " + out);
        assertThat(fileNames(out)).containsExactly(
                name("01", "anna@example.org", ".html"),
                name("01", "anna@example.org", ".subject.txt"),
                name("01", "anna@example.org", ".txt"),
                name("02", "ben@example.org", ".html"),
                name("02", "ben@example.org", ".subject.txt"),
                name("02", "ben@example.org", ".txt"),
                name("03", "clara@example.org", ".html"),
                name("03", "clara@example.org", ".subject.txt"),
                name("03", "clara@example.org", ".txt"));
        assertThat(Files.readString(out.resolve(name("01", "anna@example.org", ".html"))))
                .isEqualTo("<p>Hi Anna from Berlin</p>");
        assertThat(Files.readString(out.resolve(name("01", "anna@example.org", ".subject.txt"))))
                .isEqualTo("Hello Anna");
        assertThat(Files.readString(out.resolve(name("01", "anna@example.org", ".txt"))))
                .isEqualTo("Hi Anna");
        assertThat(Files.readString(out.resolve(name("03", "clara@example.org", ".html"))))
                .isEqualTo("<p>Hi Clara from Köln</p>");
        assertThat(factoryCalls).hasValue(0);
        assertThat(campaign.resolve("journal.jsonl")).doesNotExist();
        assertThat(waits).isEmpty();
    }

    @Test
    void inlineImageIsCopiedOnceAndTheHtmlPointsAtIt() throws IOException {
        Path campaign = SampleFixtures.writeCampaign(root, THREE_ROWS);
        Files.writeString(campaign.resolve("body.html"), INLINE_HTML);
        SampleFixtures.writeInline(campaign, "logo.png");
        Path out = root.resolve("out");

        SampleFixtures.Run run = dryRun(campaign, out);

        assertThat(run.exit()).isZero();
        assertThat(run.err()).isEmpty();
        assertThat(run.outLines()).containsExactly(
                "attachments: info.txt (text/plain, 18 bytes)",
                "inline: logo.png (image/png, 3 bytes, cid:logo)",
                "[1/3] anna@example.org rendered",
                "[2/3] ben@example.org rendered",
                "[3/3] clara@example.org rendered",
                "dry run: 3 mails written to " + out);
        assertThat(fileNames(out.resolve("inline"))).containsExactly("logo.png");
        assertThat(Files.readString(out.resolve("inline").resolve("logo.png"))).isEqualTo("png");
        assertThat(Files.readString(out.resolve(name("01", "anna@example.org", ".html"))))
                .isEqualTo("<p><img src=\"inline/logo.png\"> Hi Anna from Berlin</p>");
        assertThat(Files.readString(out.resolve(name("03", "clara@example.org", ".html"))))
                .isEqualTo("<p><img src=\"inline/logo.png\"> Hi Clara from Köln</p>");
        for (String file : fileNames(out)) {
            if (file.endsWith(".html")) {
                assertThat(Files.readString(out.resolve(file))).doesNotContain("cid:");
            }
        }
        assertThat(factoryCalls).hasValue(0);
        assertThat(campaign.resolve("journal.jsonl")).doesNotExist();
    }

    @Test
    void writesNoTextFileWithoutBodyTxt() throws IOException {
        Path campaign = SampleFixtures.writeCampaign(root, THREE_ROWS);
        Files.delete(campaign.resolve("body.txt"));
        Path out = root.resolve("out");

        SampleFixtures.Run run = dryRun(campaign, out);

        assertThat(run.exit()).isZero();
        assertThat(fileNames(out)).hasSize(6).noneMatch(file -> file.endsWith(".txt") && !file.endsWith(".subject.txt"));
        assertThat(fileNames(out)).contains(
                name("01", "anna@example.org", ".html"),
                name("01", "anna@example.org", ".subject.txt"));
    }

    @Test
    void listsNoAttachmentsWhenTheDirectoryIsMissing() throws IOException {
        Path campaign = SampleFixtures.writeCampaign(root, THREE_ROWS);
        Files.delete(campaign.resolve("attachments").resolve("info.txt"));
        Files.delete(campaign.resolve("attachments"));

        SampleFixtures.Run run = dryRun(campaign, root.resolve("out"));

        assertThat(run.exit()).isZero();
        assertThat(run.outLines().get(0)).isEqualTo("attachments: none");
    }

    @Test
    void overwritesSameNamedFilesAndKeepsOthers() throws IOException {
        Path campaign = SampleFixtures.writeCampaign(root, THREE_ROWS);
        Path out = Files.createDirectories(root.resolve("out"));
        Files.writeString(out.resolve("keep.me"), "keep");
        Path old = out.resolve(name("01", "anna@example.org", ".html"));
        Files.writeString(old, "old content");

        SampleFixtures.Run run = dryRun(campaign, out);

        assertThat(run.exit()).isZero();
        assertThat(Files.readString(out.resolve("keep.me"))).isEqualTo("keep");
        assertThat(Files.readString(old)).isEqualTo("<p>Hi Anna from Berlin</p>");
    }

    @Test
    void sanitisesFileNamesAndStaysInsideOut() throws IOException {
        String csv = String.join("\n",
                "email,firstName,city",
                "../x@example.org,X,Berlin",
                "a+b@example.org,AB,Berlin",
                "jürgen@example.org,Jürgen,Berlin") + "\n";
        Path campaign = SampleFixtures.writeCampaign(root, csv);
        Path out = root.resolve("out");

        SampleFixtures.Run run = dryRun(campaign, out);

        assertThat(run.exit()).isZero();
        assertThat(fileNames(out)).contains(
                name("01", ".._x@example.org", ".html"),
                name("02", "a_b@example.org", ".html"),
                name("03", "j_rgen@example.org", ".html"));
        try (Stream<Path> walk = Files.walk(root)) {
            List<Path> outside = walk
                    .filter(Files::isRegularFile)
                    .filter(file -> !file.startsWith(campaign) && !file.startsWith(out)
                            && !file.equals(root.resolve("account.properties")))
                    .collect(Collectors.toList());
            assertThat(outside).isEmpty();
        }
    }

    @Test
    void padsRowNumbersToAtLeastTwoDigits() throws IOException {
        assertThat(htmlFileNames(3)).containsExactly(
                name("01", "r001@example.org", ".html"),
                name("02", "r002@example.org", ".html"),
                name("03", "r003@example.org", ".html"));
        List<String> twelve = htmlFileNames(12);
        assertThat(twelve.get(0)).startsWith("01-");
        assertThat(twelve.get(11)).startsWith("12-");
        List<String> hundred = htmlFileNames(100);
        assertThat(hundred.get(0)).startsWith("001-");
        assertThat(hundred.get(99)).startsWith("100-");
    }

    private List<String> htmlFileNames(int rows) throws IOException {
        StringBuilder csv = new StringBuilder("email,firstName,city\n");
        for (int i = 1; i <= rows; i++) {
            csv.append(String.format("r%03d@example.org,First%d,City%d%n", i, i, i));
        }
        Path dir = Files.createDirectories(root.resolve("rows" + rows));
        Path campaign = SampleFixtures.writeCampaign(dir, csv.toString());
        Path out = dir.resolve("out");
        SampleFixtures.Run run = dryRun(campaign, out);
        assertThat(run.exit()).isZero();
        return fileNames(out).stream().filter(file -> file.endsWith(".html")).toList();
    }

    @Test
    void reportsPreflightProblemsAndWritesNothing() throws IOException {
        String csv = String.join("\n",
                "email,firstName,city",
                "anna@example.org,Anna,Berlin",
                "not-an-address,Ben,Hamburg") + "\n";
        Path campaign = SampleFixtures.writeCampaign(root, csv);
        Path out = root.resolve("out");

        SampleFixtures.Run run = dryRun(campaign, out);

        assertThat(run.exit()).isEqualTo(2);
        assertThat(run.errLines().get(0)).startsWith("recipients.csv line 3:");
        assertThat(run.errLines().get(run.errLines().size() - 1))
                .isEqualTo("Pre-flight failed with 1 problem(s); nothing was sent.");
        assertThat(out).doesNotExist();
        assertThat(factoryCalls).hasValue(0);
    }
}
