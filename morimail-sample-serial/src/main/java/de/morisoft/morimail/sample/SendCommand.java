package de.morisoft.morimail.sample;

import de.morisoft.morimail.InvalidMailException;
import de.morisoft.morimail.Mail;
import de.morisoft.morimail.MailException;
import de.morisoft.morimail.Mailer;
import de.morisoft.morimail.PermanentMailException;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;
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
        description = "Sends one mail per row of recipients.csv, or renders them with --dry-run.")
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

    @Option(names = "--limit", description = "Process only the first N recipients.")
    Integer limit;

    @Option(names = "--rate", defaultValue = "20",
            description = "At most this many mails per minute. Default: ${DEFAULT-VALUE}.")
    int rate;

    @Option(names = "--journal", paramLabel = "FILE",
            description = "The journal file. Default: journal.jsonl in the campaign directory.")
    Path journalFile;

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

    @Override
    public Integer call() {
        PrintWriter out = spec.commandLine().getOut();
        PrintWriter err = spec.commandLine().getErr();
        List<String> problems = new ArrayList<>();

        String id = campaignId != null
                ? campaignId
                : campaignDir.toAbsolutePath().normalize().getFileName().toString();

        int before = problems.size();
        AccountFile account = AccountFile.read(accountFile, problems);
        boolean accountOk = account != null && problems.size() == before;
        Campaign campaign = Campaign.read(campaignDir, problems);

        List<Mail> mails = new ArrayList<>();
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
                    mails.add(builder.build());
                } catch (InvalidMailException e) {
                    mails.add(null);
                    problems.add("recipients.csv line " + row.line() + ": " + e.getMessage());
                }
            }
        }

        Mailer mailer = null;
        if (accountOk) {
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

        Journal journal = null;
        Path journalPath = journalFile != null ? journalFile : campaignDir.resolve("journal.jsonl");
        if (problems.isEmpty()) {
            try {
                journal = Journal.open(journalPath);
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

        try (Journal run = journal) {
            return send(campaign, mails, mailer, run, journalPath, id, out, err);
        } catch (IOException e) {
            err.println("Stopped: cannot close the journal " + journalPath + ": " + e.getMessage());
            err.flush();
            return 1;
        }
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
                if (sentAny) {
                    sleep.accept(spacing);
                }
                sentAny = true;
                try {
                    mailer.send(mails.get(i));
                    journal.append(key, email, "accepted", null);
                    accepted++;
                    out.println(prefix + " accepted");
                } catch (MailException e) {
                    journal.append(key, email, "failed", e);
                    failed++;
                    out.println(prefix + " failed: " + e.getClass().getSimpleName() + ": "
                            + e.getMessage());
                }
            }
        } catch (IOException e) {
            err.println("Stopped: cannot write the journal " + journalPath + ": " + e.getMessage()
                    + ". The last mail may have been sent; a rerun sends it again.");
            exit = 1;
        }
        out.println("accepted " + accepted + ", skipped " + skipped + ", failed " + failed);
        out.flush();
        err.flush();
        return exit != 0 || failed > 0 ? 1 : 0;
    }
}
