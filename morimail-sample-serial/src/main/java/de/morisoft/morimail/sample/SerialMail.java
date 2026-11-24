package de.morisoft.morimail.sample;

import de.morisoft.morimail.MailAccount;
import de.morisoft.morimail.Mailer;
import de.morisoft.morimail.entra.EntraClientCredentials;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
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

    static final URI AUTHORITY = URI.create("https://login.microsoftonline.com");
    static final String GRAPH_BASE_URL = "https://graph.microsoft.com/v1.0";

    private SerialMail() {
    }

    /**
     * Runs the command line and exits with its exit code: 0 nothing failed, 1 at least one mail
     * failed or the journal could not be written, 2 a pre-flight or argument problem, 3 the run was
     * stopped by a rejected sender or required authentication.
     *
     * @param args the command-line arguments
     */
    public static void main(String[] args) {
        System.exit(commandLine(SerialMail::realMailer, SerialMail::sleep, System::getenv).execute(args));
    }

    /**
     * Builds the command tree. {@code main} and the tests build the same tree; the tests pass a
     * Mailer over the recording transport, a recording sleep and an injected environment, which
     * {@code main} takes from the process.
     */
    static CommandLine commandLine(Function<AccountFile, Mailer> mailerFactory,
            Consumer<Duration> sleep, Function<String, String> env) {
        return new CommandLine(new SerialMail())
                .addSubcommand(new SendCommand(mailerFactory, sleep, env));
    }

    /**
     * Builds the Mailer of a real run from the account file and the process environment. Every
     * message it produces names keys only, never a value from the file or the environment.
     */
    static Mailer realMailer(AccountFile account) {
        return mailer(account, System::getenv, AUTHORITY, GRAPH_BASE_URL);
    }

    /**
     * Builds the Mailer for an account file: Entra client credentials (the secret or the
     * certificate named by the file, read from {@code env}) and a graph account built through the
     * registry. The tests pass a WireMock authority and base URL and an injected environment.
     */
    static Mailer mailer(AccountFile account, Function<String, String> env, URI authority,
            String graphBaseUrl) {
        EntraClientCredentials credentials;
        if (account.secretEnv() != null) {
            String secret = env.apply(account.secretEnv());
            if (secret == null || secret.isEmpty()) {
                throw new IllegalStateException(
                        "secretEnv: the environment variable it names is not set or is empty");
            }
            credentials = EntraClientCredentials.withSecret(account.tenantId(), account.clientId(), secret);
        } else {
            String password = env.apply(account.certificatePasswordEnv());
            if (password == null || password.isEmpty()) {
                throw new IllegalStateException(
                        "certificate.passwordEnv: the environment variable it names is not set or is empty");
            }
            credentials = certificateCredentials(account, password);
        }
        MailAccount mailAccount = MailAccount.builder(account.id())
                .transport("graph")
                .auth(credentials.authority(authority))
                .option("baseUrl", graphBaseUrl)
                .build();
        return Mailer.of(mailAccount);
    }

    /**
     * The library's key-store message names the file, which is a value from account.properties, so
     * every problem here is a fixed text.
     */
    private static EntraClientCredentials certificateCredentials(AccountFile account, String password) {
        Path path;
        try {
            path = Path.of(account.certificatePath());
        } catch (InvalidPathException e) {
            throw new IllegalStateException("certificate.path: not a valid path");
        }
        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException("certificate.path: the file does not exist or is not a regular file");
        }
        try {
            return EntraClientCredentials.withCertificate(account.tenantId(), account.clientId(), path,
                    password.toCharArray());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("certificate.path: the PKCS12 key store cannot be loaded "
                    + "with the password from certificate.passwordEnv", e);
        }
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
