package de.morisoft.morimail.sample;

import static de.morisoft.morimail.sample.SampleFixtures.ACCOUNT;
import static de.morisoft.morimail.sample.SampleFixtures.THREE_ROWS;
import static org.assertj.core.api.Assertions.assertThat;

import de.morisoft.morimail.Mailer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AccountFileTest {

    private static final String SENTINEL = "Sentinel-7f3a";
    private static final String ZERO_GUID = "00000000-0000-0000-0000-000000000000";
    private static final String REQUIRED = String.join("\n",
            "account.id=main",
            "tenantId=" + ZERO_GUID,
            "clientId=" + ZERO_GUID,
            "from=newsletter@example.com") + "\n";
    private static final String CERTIFICATE =
            "certificate.path=certs/test.p12\ncertificate.passwordEnv=MAIL_CERT_PASSWORD\n";

    @TempDir
    Path root;

    private final List<String> problems = new ArrayList<>();

    private AccountFile read(String content) throws IOException {
        return AccountFile.read(SampleFixtures.writeAccount(root, content), name -> null, problems);
    }

    private AccountFile read(String content, Map<String, String> env) throws IOException {
        return AccountFile.read(SampleFixtures.writeAccount(root, content), env::get, problems);
    }

    @Test
    void certificateFormIsEnough() throws IOException {
        AccountFile account = read(REQUIRED + CERTIFICATE);

        assertThat(problems).isEmpty();
        assertThat(account.certificatePath()).isEqualTo("certs/test.p12");
        assertThat(account.certificatePasswordEnv()).isEqualTo("MAIL_CERT_PASSWORD");
        assertThat(account.secretEnv()).isNull();
    }

    @Test
    void secretFormIsEnough() throws IOException {
        AccountFile account = read(ACCOUNT);

        assertThat(problems).isEmpty();
        assertThat(account.secretEnv()).isEqualTo("MAIL_CLIENT_SECRET");
    }

    @Test
    void bothFormsAreAProblem() throws IOException {
        read(REQUIRED + CERTIFICATE + "secretEnv=MAIL_CLIENT_SECRET\n");
        read(REQUIRED + "certificate.path=certs/test.p12\nsecretEnv=MAIL_CLIENT_SECRET\n");

        String both = "account.properties: use either certificate.path with "
                + "certificate.passwordEnv, or secretEnv, not both";
        assertThat(problems).containsExactly(both, both);
    }

    @Test
    void noCredentialIsAProblem() throws IOException {
        read(REQUIRED);

        assertThat(problems).containsExactly("account.properties: no credential: "
                + "set certificate.path and certificate.passwordEnv, or secretEnv");
    }

    @Test
    void oneCertificateKeyAloneIsAProblem() throws IOException {
        read(REQUIRED + "certificate.path=certs/test.p12\n");
        read(REQUIRED + "certificate.passwordEnv=MAIL_CERT_PASSWORD\n");

        String together = "account.properties: certificate.path and certificate.passwordEnv "
                + "must be set together";
        assertThat(problems).containsExactly(together, together);
    }

    @ParameterizedTest
    @ValueSource(strings = {"account.id", "tenantId", "clientId", "from"})
    void missingRequiredKeyIsAProblem(String key) throws IOException {
        read(ACCOUNT.lines().filter(line -> !line.startsWith(key + "=")).reduce("",
                (all, line) -> all + line + "\n"));

        assertThat(problems).containsExactly("account.properties: missing key '" + key + "'");
    }

    @ParameterizedTest
    @ValueSource(strings = {"account.id", "tenantId", "clientId", "from"})
    void blankRequiredKeyCountsAsMissing(String key) throws IOException {
        read(ACCOUNT.lines().map(line -> line.startsWith(key + "=") ? key + "=   " : line)
                .reduce("", (all, line) -> all + line + "\n"));

        assertThat(problems).containsExactly("account.properties: missing key '" + key + "'");
    }

    @Test
    void valuesLoseSurroundingSpacesAndKeepNonAsciiCharacters() throws IOException {
        AccountFile account = read(ACCOUNT.replace("from=newsletter@example.com",
                "from=newsletter@example.com   ").replace("account.id=main", "account.id=Grüße"));

        assertThat(problems).isEmpty();
        assertThat(account.from()).isEqualTo("newsletter@example.com");
        assertThat(account.id()).isEqualTo("Grüße");
    }

    @Test
    void fileThatIsNotUtf8IsAProblem() throws IOException {
        Path file = root.resolve("account.properties");
        Files.write(file, new byte[] {'a', 'c', 'c', 'o', 'u', 'n', 't', '.', 'i', 'd', '=', 'G', 'r',
                (byte) 0xFC, 'e', '\n'});

        assertThat(AccountFile.read(file, name -> null, problems)).isNull();

        assertThat(problems).containsExactly("account.properties is not valid UTF-8");
    }

    @Test
    void leadingByteOrderMarkIsIgnored() throws IOException {
        AccountFile account = read("\uFEFF" + ACCOUNT);

        assertThat(problems).isEmpty();
        assertThat(account.id()).isEqualTo("main");
    }

    @Test
    void malformedUnicodeEscapeIsAProblem() throws IOException {
        String backslash = String.valueOf('\\');

        assertThat(read("account.id=" + backslash + "uzz\n")).isNull();

        assertThat(problems).containsExactly(
                "account.properties: contains a malformed unicode escape");
    }

    @Test
    void missingFileIsAProblem() {
        assertThat(AccountFile.read(root.resolve("account.properties"), name -> null, problems)).isNull();

        assertThat(problems).containsExactly("account.properties: file not found");
    }

    @Test
    void problemsNeverEchoAValueFromTheFile() throws IOException {
        String content = String.join("\n",
                "account.id=main",
                "tenantId=" + SENTINEL,
                "clientId=" + ZERO_GUID,
                "from=" + SENTINEL,
                "certificate.path=" + SENTINEL,
                "secretEnv=" + SENTINEL) + "\n";
        Path account = SampleFixtures.writeAccount(root, content);
        Path campaign = SampleFixtures.writeCampaign(root, THREE_ROWS);

        AccountFile.read(account, name -> null, problems);
        SampleFixtures.Run run = SampleFixtures.run(a -> Mailer.of(SampleFixtures.recordingAccount()),
                new ArrayList<>(), "send", "--account", account.toString(), "--campaign",
                campaign.toString());

        assertThat(problems).isNotEmpty().noneMatch(problem -> problem.contains(SENTINEL));
        assertThat(run.exit()).isEqualTo(2);
        assertThat(run.err()).isNotEmpty().doesNotContain(SENTINEL);
        assertThat(run.out()).doesNotContain(SENTINEL);
    }

    @Test
    void realRunStopHoldsNoValueFromTheFile() throws IOException {
        String content = REQUIRED.replace(ZERO_GUID, SENTINEL) + "secretEnv=" + SENTINEL + "\n";
        Path account = SampleFixtures.writeAccount(root, content);
        Path campaign = SampleFixtures.writeCampaign(root, THREE_ROWS);

        SampleFixtures.Run run = SampleFixtures.run(SerialMail::realMailer, new ArrayList<>(),
                "send", "--account", account.toString(), "--campaign", campaign.toString());

        assertThat(run.exit()).isEqualTo(2);
        assertThat(run.errLines()).contains(
                "account: secretEnv: the environment variable it names is not set or is empty; "
                        + "secretEnv names an environment variable, it does not hold the secret itself");
        assertThat(run.err()).doesNotContain(SENTINEL);
        assertThat(run.out()).doesNotContain(SENTINEL);
    }

    @Test
    void dryRunNeedsNoEnvironmentVariable() throws IOException {
        Path account = SampleFixtures.writeAccount(root,
                REQUIRED + "secretEnv=MORIMAIL_TEST_UNSET_VARIABLE\n");
        Path campaign = SampleFixtures.writeCampaign(root, THREE_ROWS);
        AtomicInteger factoryCalls = new AtomicInteger();

        SampleFixtures.Run dry = SampleFixtures.run(a -> {
            factoryCalls.incrementAndGet();
            return Mailer.of(SampleFixtures.recordingAccount());
        }, new ArrayList<>(), "send", "--account", account.toString(), "--campaign",
                campaign.toString(), "--dry-run", "--out", root.resolve("out").toString());
        SampleFixtures.Run real = SampleFixtures.run(SerialMail::realMailer, new ArrayList<>(),
                "send", "--account", account.toString(), "--campaign", campaign.toString());

        assertThat(dry.exit()).isZero();
        assertThat(factoryCalls).hasValue(0);
        assertThat(real.exit()).isEqualTo(2);
        assertThat(real.errLines()).containsExactly(
                "account: secretEnv: the environment variable it names is not set or is empty",
                "Pre-flight failed with 1 problem(s); nothing was sent.");
    }

    @Test
    void dryRunStillReportsAnAccountProblem() throws IOException {
        Path account = SampleFixtures.writeAccount(root,
                ACCOUNT.replace("clientId=" + ZERO_GUID + "\n", ""));
        Path campaign = SampleFixtures.writeCampaign(root, THREE_ROWS);

        SampleFixtures.Run run = SampleFixtures.run(a -> Mailer.of(SampleFixtures.recordingAccount()),
                new ArrayList<>(), "send", "--account", account.toString(), "--campaign",
                campaign.toString(), "--dry-run", "--out", root.resolve("out").toString());

        assertThat(run.exit()).isEqualTo(2);
        assertThat(run.errLines()).containsExactly(
                "account.properties: missing key 'clientId'",
                "Pre-flight failed with 1 problem(s); nothing was sent.");
    }

    @Test
    void referenceIsReplacedByTheEnvironmentValue() throws IOException {
        String content = "account.id=main\n"
                + "tenantId=${MORIMAIL_TEST_TENANT_ID}\n"
                + "clientId=" + ZERO_GUID + "\n"
                + "from=newsletter@example.com\n"
                + "certificate.path=${MORIMAIL_TEST_DIR}/test.p12\n"
                + "certificate.passwordEnv=MAIL_CERT_PASSWORD\n";

        AccountFile account = read(content,
                Map.of("MORIMAIL_TEST_TENANT_ID", ZERO_GUID, "MORIMAIL_TEST_DIR", "/path/certs"));

        assertThat(problems).isEmpty();
        assertThat(account.tenantId()).isEqualTo(ZERO_GUID);
        assertThat(account.certificatePath()).isEqualTo("/path/certs/test.p12");
        assertThat(account.certificatePasswordEnv()).isEqualTo("MAIL_CERT_PASSWORD");
    }

    @Test
    void unsetReferenceIsAProblemThatNamesTheKey() throws IOException {
        AccountFile account = read(REQUIRED.replace("tenantId=" + ZERO_GUID,
                "tenantId=${MORIMAIL_TEST_UNSET_VARIABLE}") + CERTIFICATE, Map.of());

        assertThat(account).isNull();
        assertThat(problems).containsExactly("account.properties: key 'tenantId' uses "
                + "${MORIMAIL_TEST_UNSET_VARIABLE}, which is not set or is blank");
    }

    @Test
    void blankReferenceIsAProblemThatNamesTheKey() throws IOException {
        AccountFile account = read(REQUIRED.replace("from=newsletter@example.com",
                "from=${MORIMAIL_TEST_BLANK}") + CERTIFICATE, Map.of("MORIMAIL_TEST_BLANK", "   "));

        assertThat(account).isNull();
        assertThat(problems).containsExactly("account.properties: key 'from' uses "
                + "${MORIMAIL_TEST_BLANK}, which is not set or is blank");
    }

    @Test
    void environmentValueIsInsertedLiterally() throws IOException {
        String content = "account.id=$main-${1X}-${not a reference}\n"
                + "tenantId=" + ZERO_GUID + "\n"
                + "clientId=" + ZERO_GUID + "\n"
                + "from=newsletter@example.com\n"
                + "certificate.path=${MORIMAIL_TEST_DIR}/test.p12\n"
                + "certificate.passwordEnv=MAIL_CERT_PASSWORD\n";

        AccountFile account = read(content, Map.of("MORIMAIL_TEST_DIR", "C:\\certs$1"));

        assertThat(problems).isEmpty();
        assertThat(account.certificatePath()).isEqualTo("C:\\certs$1/test.p12");
        assertThat(account.id()).isEqualTo("$main-${1X}-${not a reference}");
    }

    @Test
    void referenceProblemsNeverEchoAValue() throws IOException {
        String content = "account.id=main\n"
                + "tenantId=${MORIMAIL_TEST_SET}\n"
                + "clientId=${MORIMAIL_TEST_UNSET}\n"
                + "from=${MORIMAIL_TEST_BLANK}\n"
                + "secretEnv=MAIL_CLIENT_SECRET\n";
        Map<String, String> env = Map.of("MORIMAIL_TEST_SET", SENTINEL, "MORIMAIL_TEST_BLANK", "  ");
        Path account = SampleFixtures.writeAccount(root, content);
        Path campaign = SampleFixtures.writeCampaign(root, THREE_ROWS);

        AccountFile.read(account, env::get, problems);
        SampleFixtures.Run run = SampleFixtures.run(env::get, a -> {
            throw new AssertionError("a dry run must not build a Mailer");
        }, new ArrayList<>(), "send", "--dry-run", "--account", account.toString(), "--campaign",
                campaign.toString(), "--out", root.resolve("out").toString());

        assertThat(problems).hasSize(2).noneMatch(problem -> problem.contains(SENTINEL));
        assertThat(problems.get(0)).contains("'clientId'");
        assertThat(problems.get(1)).contains("'from'");
        assertThat(run.exit()).isEqualTo(2);
        assertThat(run.err()).isNotEmpty().doesNotContain(SENTINEL);
        assertThat(run.out()).doesNotContain(SENTINEL);
    }
}
