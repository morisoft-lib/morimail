package de.morisoft.morimail.sample;

import de.morisoft.morimail.Attachment;
import de.morisoft.morimail.AuthRequiredException;
import de.morisoft.morimail.InvalidMailException;
import de.morisoft.morimail.Mail;
import de.morisoft.morimail.MailException;
import de.morisoft.morimail.MailMessage;
import de.morisoft.morimail.Mailer;
import de.morisoft.morimail.PermanentMailException;
import de.morisoft.morimail.SenderRejectedException;
import de.morisoft.morimail.TransientMailException;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

/**
 * {@code serial-mail send}: checks the account file and the campaign, then sends one mail per
 * recipient row and journals every attempt.
 *
 * <p>Not thread-safe: one instance runs one command line.
 */
@Command(name = "send",
        description = "Sends one mail per row of recipients.csv, or renders them with --dry-run.",
        footer = {"A mail that is in flight when the run is stopped has no journal line, "
                + "so the next run sends it again.",
                "Exit codes: 0 nothing failed, 1 a mail failed or the journal could not be "
                + "written, 2 a pre-flight or argument problem, 3 the run was stopped by a "
                + "rejected sender or required authentication."})
final class SendCommand implements Callable<Integer> {

    @Spec
    CommandSpec spec;

    @Option(names = "--account", required = true, paramLabel = "FILE",
            description = "The account.properties file.")
    Path accountFile;

    @Option(names = "--campaign", required = true, paramLabel = "DIR",
            description = "The campaign directory.")
    Path campaignDir;

    @Option(names = "--campaign-id",
            description = "Prefix of the journal keys. Default: the name of the campaign directory.")
    String campaignId;

    @Option(names = "--dry-run",
            description = "Write the rendered mails to --out and send nothing.")
    boolean dryRun;

    @Option(names = "--out", defaultValue = "out", paramLabel = "DIR",
            description = "Where --dry-run writes the rendered mails. Default: ${DEFAULT-VALUE}.")
    Path outDir;

    @Option(names = "--limit", description = "Process only the first N recipients.")
    Integer limit;

    @Option(names = "--rate", defaultValue = "20",
            description = "At most this many mails per minute. Default: ${DEFAULT-VALUE}.")
    int rate;

    @Option(names = "--journal", paramLabel = "FILE",
            description = "The journal file. Default: journal.jsonl in the campaign directory.")
    Path journalFile;

    /** The wait before a retry when the transport gives no retryAfter. */
    static final Duration DEFAULT_RETRY_WAIT = Duration.ofSeconds(30);
    /** The longest wait before a retry, whatever the transport asks for. */
    static final Duration MAX_RETRY_WAIT = Duration.ofMinutes(5);
    /** One attempt plus three retries. */
    static final int MAX_ATTEMPTS = 4;

    private final Function<AccountFile, Mailer> mailerFactory;
    private final Consumer<Duration> sleep;

    SendCommand(Function<AccountFile, Mailer> mailerFactory, Consumer<Duration> sleep) {
        this.mailerFactory = mailerFactory;
        this.sleep = sleep;
    }

    /** The time between two send attempts for a rate in mails per minute, rounded up. */
    static Duration spacing(int rate) {
        return Duration.ofMillis((60_000L + rate - 1) / rate);
    }

    /** The display form of a failure: its simple class name, and a colon and its message if it has one. */
    private static String describe(MailException e) {
        return e.getMessage() == null ? e.getClass().getSimpleName()
                : e.getClass().getSimpleName() + ": " + e.getMessage();
    }

    /** One pre-flight problem for an {@code InvalidMailException} message and the rows it hit. */
    private static String invalidMail(String message, List<Integer> lines, int rows) {
        if (lines.size() == 1) {
            return "recipients.csv line " + lines.get(0) + ": " + message;
        }
        if (lines.size() == rows) {
            return message + " (all " + rows + " rows)";
        }
        List<String> shown = new ArrayList<>();
        for (Integer line : lines.subList(0, Math.min(lines.size(), 5))) {
            shown.add(String.valueOf(line));
        }
        return message + " (recipients.csv lines " + String.join(", ", shown)
                + (lines.size() > 5 ? ", ... " + lines.size() + " rows" : "") + ")";
    }

    private static String counts(int accepted, int skipped, int failed) {
        return "accepted " + accepted + ", skipped " + skipped + ", failed " + failed;
    }

    /** Whole seconds of a wait, rounded up, for display only. */
    static long seconds(Duration wait) {
        return (wait.toMillis() + 999) / 1000;
    }

    @Override
    public Integer call() {
        PrintWriter out = spec.commandLine().getOut();
        PrintWriter err = spec.commandLine().getErr();
        List<String> problems = new ArrayList<>();

        if (rate < 1) {
            problems.add("--rate must be at least 1");
        }
        if (limit != null && limit < 1) {
            problems.add("--limit must be at least 1");
        }
        Path dirName = campaignDir.toAbsolutePath().normalize().getFileName();
        if (campaignId != null && campaignId.isBlank()) {
            problems.add("--campaign-id must not be blank");
        } else if (campaignId == null && dirName == null) {
            problems.add("--campaign has no directory name; set --campaign-id");
        }
        String id = campaignId != null ? campaignId : dirName == null ? null : dirName.toString();

        int before = problems.size();
        AccountFile account = AccountFile.read(accountFile, problems);
        boolean accountOk = account != null && problems.size() == before;
        Campaign campaign = Campaign.read(campaignDir, problems);

        List<Mail> mails = new ArrayList<>();
        // The same defect (a bad sender, attachment or subject) hits every row: report it once.
        Map<String, List<Integer>> invalid = new LinkedHashMap<>();
        if (campaign != null && account != null && account.id() != null && account.from() != null) {
            for (Campaign.Row row : campaign.rows()) {
                try {
                    Mail.Builder builder = Mail.using(account.id())
                            .from(account.from())
                            .to(row.email())
                            .template(campaign.template(), row.fields());
                    for (Path attachment : campaign.attachments()) {
                        builder.attach(attachment);
                    }
                    // Regular attachments first, inline images after them, so v0.1.0 mails keep
                    // their attachment order.
                    for (Path image : campaign.inlineImages()) {
                        builder.inline(image, Campaign.contentId(image));
                    }
                    mails.add(builder.build());
                } catch (InvalidMailException e) {
                    mails.add(null);
                    invalid.computeIfAbsent(e.getMessage(), m -> new ArrayList<>()).add(row.line());
                }
            }
            for (Map.Entry<String, List<Integer>> entry : invalid.entrySet()) {
                problems.add(invalidMail(entry.getKey(), entry.getValue(), campaign.rows().size()));
            }
        }

        Mailer mailer = null;
        if (accountOk && !dryRun) {
            try {
                mailer = mailerFactory.apply(account);
            } catch (IllegalStateException | IllegalArgumentException e) {
                problems.add("account: " + e.getMessage());
            }
        }
        if (mailer != null && !mails.isEmpty() && mails.get(0) != null) {
            try {
                mailer.validate(mails.get(0));
            } catch (PermanentMailException e) {
                problems.add("recipients.csv line " + campaign.rows().get(0).line()
                        + ": mailer.validate rejected the first mail: " + e.getMessage());
            }
        }

        List<String> warnings = new ArrayList<>();
        Journal journal = null;
        Path journalPath = journalFile != null ? journalFile : campaignDir.resolve("journal.jsonl");
        if (problems.isEmpty() && !dryRun) {
            try {
                journal = Journal.open(journalPath, warnings);
            } catch (IOException e) {
                problems.add("journal " + journalPath + " cannot be opened: "
                        + e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }

        if (!problems.isEmpty()) {
            for (String problem : problems) {
                err.println(problem);
            }
            err.println("Pre-flight failed with " + problems.size() + " problem(s); nothing was sent.");
            err.flush();
            return 2;
        }

        for (String warning : warnings) {
            err.println("warning: " + warning);
        }
        err.flush();

        if (dryRun) {
            return writeDryRun(campaign, mails, out, err);
        }

        try (Journal run = journal) {
            return send(campaign, mails, mailer, run, journalPath, id, out, err);
        } catch (IOException e) {
            err.println("Stopped: cannot close the journal " + journalPath + ": " + e.getMessage());
            err.flush();
            return 1;
        }
    }

    /** Keeps ASCII letters, digits and . _ @ - of an address and turns every other code point into _. */
    static String safeName(String email) {
        StringBuilder name = new StringBuilder();
        email.codePoints().forEach(c -> {
            boolean keep = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                    || c == '.' || c == '_' || c == '@' || c == '-';
            name.append(keep ? (char) c : '_');
        });
        return name.toString();
    }

    /**
     * The stdout entries of attachments, joined by a comma; an inline image also shows its
     * content ID.
     */
    private static String describeAll(List<Attachment> attachments, boolean withContentId)
            throws IOException {
        List<String> entries = new ArrayList<>();
        for (Attachment attachment : attachments) {
            entries.add(attachment.filename() + " (" + attachment.contentType() + ", "
                    + Files.size(attachment.file()) + " bytes"
                    + (withContentId ? ", cid:" + attachment.contentId() : "") + ")");
        }
        return String.join(", ", entries);
    }

    private int writeDryRun(Campaign campaign, List<Mail> mails, PrintWriter out, PrintWriter err) {
        int total = limit == null ? campaign.rows().size() : Math.min(limit, campaign.rows().size());
        int width = Math.max(2, String.valueOf(total).length());
        try {
            Files.createDirectories(outDir);
            List<Attachment> regular = new ArrayList<>();
            List<Attachment> inline = new ArrayList<>();
            for (Attachment attachment : mails.get(0).message().attachments()) {
                (attachment.contentId() == null ? regular : inline).add(attachment);
            }
            if (regular.isEmpty()) {
                out.println("attachments: none");
            } else {
                out.println("attachments: " + describeAll(regular, false));
            }
            // The dry-run files point at the copies; the mails keep cid: (spec 10.3, CLI-17).
            Map<String, String> srcById = new HashMap<>();
            if (!inline.isEmpty()) {
                out.println("inline: " + describeAll(inline, true));
                Path inlineDir = Files.createDirectories(outDir.resolve("inline"));
                for (Attachment image : inline) {
                    Files.copy(image.file(), inlineDir.resolve(image.filename()),
                            StandardCopyOption.REPLACE_EXISTING);
                    srcById.put(image.contentId(),
                            new URI(null, null, "inline/" + image.filename(), null)
                                    .toASCIIString().replace("&", "&amp;"));
                }
            }
            for (int i = 0; i < total; i++) {
                String email = campaign.rows().get(i).email();
                MailMessage message = mails.get(i).message();
                String base = String.format("%0" + width + "d-%s", i + 1, safeName(email));
                String html = message.html();
                if (!srcById.isEmpty()) {
                    html = Campaign.CID_REFERENCE.matcher(html).replaceAll(
                            m -> Matcher.quoteReplacement(srcById.getOrDefault(m.group(1), m.group())));
                }
                Files.writeString(outDir.resolve(base + ".html"), html);
                Files.writeString(outDir.resolve(base + ".subject.txt"), message.subject());
                if (message.text() != null) {
                    Files.writeString(outDir.resolve(base + ".txt"), message.text());
                }
                out.println("[" + (i + 1) + "/" + total + "] " + email + " rendered");
            }
            out.println("dry run: " + total + " mails written to " + outDir);
        } catch (IOException | URISyntaxException e) {
            err.println("Dry run stopped: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            err.flush();
            return 1;
        }
        out.flush();
        return 0;
    }

    private int send(Campaign campaign, List<Mail> mails, Mailer mailer, Journal journal,
            Path journalPath, String id, PrintWriter out, PrintWriter err) {
        int total = limit == null ? campaign.rows().size() : Math.min(limit, campaign.rows().size());
        Duration spacing = spacing(rate);
        int accepted = 0;
        int skipped = 0;
        int failed = 0;
        boolean sentAny = false;
        int exit = 0;
        try {
            for (int i = 0; i < total; i++) {
                String email = campaign.rows().get(i).email();
                String key = Journal.key(id, email);
                String prefix = "[" + (i + 1) + "/" + total + "] " + email;
                if (journal.isAccepted(key)) {
                    skipped++;
                    out.println(prefix + " skipped (journal)");
                    continue;
                }
                Duration nextWait = spacing;
                for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
                    if (sentAny) {
                        sleep.accept(nextWait);
                    }
                    sentAny = true;
                    try {
                        mailer.send(mails.get(i));
                        journal.append(key, email, "accepted", null);
                        accepted++;
                        out.println(prefix + " accepted");
                        break;
                    } catch (SenderRejectedException | AuthRequiredException e) {
                        journal.append(key, email, "failed", e);
                        failed++;
                        out.println(prefix + " failed: " + describe(e));
                        err.println("Stopped: " + describe(e));
                        out.println(counts(accepted, skipped, failed));
                        out.flush();
                        err.flush();
                        return 3;
                    } catch (TransientMailException e) {
                        if (attempt == MAX_ATTEMPTS) {
                            journal.append(key, email, "failed", e);
                            failed++;
                            out.println(prefix + " failed: " + describe(e));
                            break;
                        }
                        Duration retry = e.retryAfter().orElse(DEFAULT_RETRY_WAIT);
                        if (retry.compareTo(MAX_RETRY_WAIT) > 0) {
                            retry = MAX_RETRY_WAIT;
                        }
                        nextWait = retry.compareTo(spacing) > 0 ? retry : spacing;
                        journal.append(key, email, "transient", e);
                        out.println(prefix + " transient, retry " + attempt + "/"
                                + (MAX_ATTEMPTS - 1) + " in " + seconds(nextWait) + "s");
                    } catch (MailException e) {
                        journal.append(key, email, "failed", e);
                        failed++;
                        out.println(prefix + " failed: " + describe(e));
                        break;
                    }
                }
            }
        } catch (IOException e) {
            err.println("Stopped: cannot write the journal " + journalPath + ": " + e.getMessage()
                    + ". The last mail may have been sent; a rerun sends it again.");
            exit = 1;
        } catch (RuntimeException e) {
            err.println("Stopped: unexpected " + e.getClass().getSimpleName()
                    + (e.getMessage() == null ? "" : ": " + e.getMessage())
                    + ". The last mail may have been sent; a rerun sends it again.");
            exit = 1;
        }
        out.println(counts(accepted, skipped, failed));
        out.flush();
        err.flush();
        return exit != 0 || failed > 0 ? 1 : 0;
    }
}
