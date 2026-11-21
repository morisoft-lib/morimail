package de.morisoft.morimail.sample;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.MalformedInputException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

/**
 * The values of an {@code account.properties} file. A blank value counts as absent (null).
 *
 * <p>Immutable and safe to share between threads.
 *
 * @param id the account id, key {@code account.id}
 * @param tenantId key {@code tenantId}
 * @param clientId key {@code clientId}
 * @param from the sender address, key {@code from}
 * @param certificatePath key {@code certificate.path}, or null
 * @param certificatePasswordEnv key {@code certificate.passwordEnv}, the name of an environment
 *     variable, or null
 * @param secretEnv key {@code secretEnv}, the name of an environment variable, or null
 */
record AccountFile(String id, String tenantId, String clientId, String from,
        String certificatePath, String certificatePasswordEnv, String secretEnv) {

    /**
     * Reads the file and adds one message to {@code problems} for every defect. A message names
     * the file and the key, never a value. Returns null when the file cannot be read at all;
     * otherwise returns the record even when keys are missing.
     */
    static AccountFile read(Path file, List<String> problems) {
        Path fileName = file.getFileName();
        String name = fileName == null ? file.toString() : fileName.toString();
        if (!Files.isRegularFile(file)) {
            problems.add(name + ": file not found");
            return null;
        }
        Properties properties = new Properties();
        try (BufferedReader reader = Files.newBufferedReader(file)) {
            properties.load(reader);
        } catch (MalformedInputException e) {
            problems.add(name + " is not valid UTF-8");
            return null;
        } catch (IOException e) {
            problems.add(name + ": cannot be read");
            return null;
        } catch (IllegalArgumentException e) {
            problems.add(name + ": contains a malformed unicode escape");
            return null;
        }
        AccountFile account = new AccountFile(
                value(properties, "account.id"),
                value(properties, "tenantId"),
                value(properties, "clientId"),
                value(properties, "from"),
                value(properties, "certificate.path"),
                value(properties, "certificate.passwordEnv"),
                value(properties, "secretEnv"));
        require(name, "account.id", account.id, problems);
        require(name, "tenantId", account.tenantId, problems);
        require(name, "clientId", account.clientId, problems);
        require(name, "from", account.from, problems);
        boolean hasPath = account.certificatePath != null;
        boolean hasPasswordEnv = account.certificatePasswordEnv != null;
        boolean hasSecret = account.secretEnv != null;
        if (hasSecret && (hasPath || hasPasswordEnv)) {
            problems.add(name + ": use either certificate.path with certificate.passwordEnv, "
                    + "or secretEnv, not both");
        } else if (!hasSecret && !hasPath && !hasPasswordEnv) {
            problems.add(name + ": no credential: set certificate.path and "
                    + "certificate.passwordEnv, or secretEnv");
        } else if (!hasSecret && hasPath != hasPasswordEnv) {
            problems.add(name + ": certificate.path and certificate.passwordEnv must be set together");
        }
        return account;
    }

    private static String value(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null) {
            return null;
        }
        value = value.trim();
        return value.isEmpty() ? null : value;
    }

    private static void require(String name, String key, String value, List<String> problems) {
        if (value == null) {
            problems.add(name + ": missing key '" + key + "'");
        }
    }
}
