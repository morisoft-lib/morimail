package de.morisoft.morimail.sample;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.MalformedInputException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The values of an {@code account.properties} file. A blank value counts as absent (null). A value
 * may hold {@code ${NAME}} references, each replaced by the environment variable NAME.
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

    /** The NAME rule of spec section 8.1: a letter or underscore, then letters, digits and underscores. */
    static final String VARIABLE_NAME = "[A-Za-z_][A-Za-z0-9_]*";

    private static final Pattern REFERENCE = Pattern.compile("\\$\\{(" + VARIABLE_NAME + ")\\}");

    /**
     * Reads the file and adds one message to {@code problems} for every defect. A message names
     * the file, the key and for a reference the variable, never a value. Every {@code ${NAME}} in a
     * value is replaced by {@code env.apply(NAME)}, taken literally. Returns null when the file
     * cannot be read at all or a reference cannot be expanded; otherwise returns the record even
     * when keys are missing.
     */
    static AccountFile read(Path file, Function<String, String> env, List<String> problems) {
        Path fileName = file.getFileName();
        String name = fileName == null ? file.toString() : fileName.toString();
        if (!Files.isRegularFile(file)) {
            problems.add(name + ": file not found");
            return null;
        }
        Properties properties = new Properties();
        try {
            String text = Files.readString(file);
            if (text.startsWith("\uFEFF")) {
                text = text.substring(1);
            }
            properties.load(new StringReader(text));
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
        int before = problems.size();
        AccountFile account = new AccountFile(
                value(properties, "account.id", env, name, problems),
                value(properties, "tenantId", env, name, problems),
                value(properties, "clientId", env, name, problems),
                value(properties, "from", env, name, problems),
                value(properties, "certificate.path", env, name, problems),
                value(properties, "certificate.passwordEnv", env, name, problems),
                value(properties, "secretEnv", env, name, problems));
        boolean unresolved = problems.size() > before;
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
        return unresolved ? null : account;
    }

    private static String value(Properties properties, String key, Function<String, String> env,
            String name, List<String> problems) {
        String value = properties.getProperty(key);
        if (value == null) {
            return null;
        }
        value = REFERENCE.matcher(value).replaceAll(match -> {
            String replacement = env.apply(match.group(1));
            if (replacement != null && !replacement.isBlank()) {
                return Matcher.quoteReplacement(replacement);
            }
            problems.add(name + ": key '" + key + "' uses " + match.group()
                    + ", which is not set or is blank");
            return Matcher.quoteReplacement(match.group());
        });
        value = value.trim();
        return value.isEmpty() ? null : value;
    }

    private static void require(String name, String key, String value, List<String> problems) {
        if (value == null) {
            problems.add(name + ": missing key '" + key + "'");
        }
    }
}
