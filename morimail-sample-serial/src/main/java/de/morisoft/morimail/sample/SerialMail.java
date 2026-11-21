package de.morisoft.morimail.sample;

import de.morisoft.morimail.Mailer;
import java.time.Duration;
import java.util.function.Consumer;
import java.util.function.Function;
import picocli.CommandLine;
import picocli.CommandLine.Command;

/**
 * The {@code serial-mail} command line: sends one templated mail per CSV row through morimail.
 *
 * <p>Not thread-safe: build one command line per run and do not share it between threads.
 */
@Command(name = "serial-mail",
        description = "Sends one templated mail per CSV row through morimail.")
public final class SerialMail {

    static final String GRAPH_NOT_AVAILABLE =
            "the graph transport is not available yet; only --dry-run works in this version";

    private SerialMail() {
    }

    /**
     * Runs the command line and exits with its exit code: 0 nothing failed, 1 at least one mail
     * failed or the journal could not be written, 2 a pre-flight or argument problem.
     *
     * @param args the command-line arguments
     */
    public static void main(String[] args) {
        System.exit(commandLine(SerialMail::realMailer, SerialMail::sleep).execute(args));
    }

    /**
     * Builds the command tree. {@code main} and the tests build the same tree; the tests pass a
     * Mailer over the recording transport and a recording sleep.
     */
    static CommandLine commandLine(Function<AccountFile, Mailer> mailerFactory,
            Consumer<Duration> sleep) {
        return new CommandLine(new SerialMail()).addSubcommand(new SendCommand(mailerFactory, sleep));
    }

    /**
     * Builds the Mailer of a real run. This is the one method the Graph and Entra phase replaces.
     * Any message it produces must name keys and environment-variable names only, never a value
     * read from the file or the environment.
     */
    static Mailer realMailer(AccountFile account) {
        throw new IllegalStateException(GRAPH_NOT_AVAILABLE);
    }

    private static void sleep(Duration wait) {
        try {
            Thread.sleep(wait.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting", e);
        }
    }
}
