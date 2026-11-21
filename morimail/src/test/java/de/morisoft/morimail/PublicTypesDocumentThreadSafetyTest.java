package de.morisoft.morimail;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * A documentation-presence check: every public type in the library sources and in the shipped
 * fixture package {@code de.morisoft.morimail.test} must have a Javadoc comment that says
 * something about thread safety (the words "thread" or "immutable").
 *
 * <p>The test cannot tell whether a statement is true. That rests on the behavioural concurrency
 * tests and on review.
 */
class PublicTypesDocumentThreadSafetyTest {

    private static final Pattern PUBLIC_TYPE = Pattern.compile(
            "^[ \\t]*public\\s+(?:(?:static|final|abstract|sealed|non-sealed|strictfp)\\s+)*"
                    + "(?:class|interface|record|enum)\\s+(\\w+)",
            Pattern.MULTILINE);
    private static final Pattern STATEMENT = Pattern.compile("thread|immutable", Pattern.CASE_INSENSITIVE);

    private final Path module = Path.of(System.getProperty("basedir", ".")).toAbsolutePath().normalize();

    @Test
    void everyPublicTypeStatesThreadSafety() throws IOException {
        List<Path> files = new ArrayList<>();
        files.addAll(javaFiles(module.resolve("src/main/java")));
        files.addAll(javaFiles(module.resolve("src/test/java/de/morisoft/morimail/test")));

        List<String> violations = new ArrayList<>();
        List<String> found = new ArrayList<>();
        for (Path file : files) {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            Matcher matcher = PUBLIC_TYPE.matcher(text);
            while (matcher.find()) {
                String type = matcher.group(1);
                found.add(type);
                String javadoc = javadocBefore(text, matcher.start());
                String path = module.relativize(file).toString().replace('\\', '/');
                if (javadoc == null) {
                    violations.add(path + ": " + type + " has no Javadoc");
                } else if (!STATEMENT.matcher(javadoc).find()) {
                    violations.add(path + ": " + type + " Javadoc states no thread safety");
                }
            }
        }

        assertThat(found).hasSizeGreaterThanOrEqualTo(18).contains("Mailer", "RecordingTransport");
        Collections.sort(violations);
        assertThat(violations).isEmpty();
    }

    /** Returns the Javadoc block directly before the declaration, or null if there is none. */
    private static String javadocBefore(String text, int declarationStart) {
        String before = text.substring(0, declarationStart).stripTrailing();
        // Skip annotation lines such as @FunctionalInterface between the comment and the type.
        while (true) {
            int lineStart = before.lastIndexOf('\n') + 1;
            if (!before.substring(lineStart).stripLeading().startsWith("@")) {
                break;
            }
            before = before.substring(0, lineStart).stripTrailing();
        }
        if (!before.endsWith("*/")) {
            return null;
        }
        int end = before.length() - 2;
        int start = before.lastIndexOf("/**", end);
        if (start < 0 || before.indexOf("*/", start) != end) {
            return null;
        }
        return before.substring(start, end);
    }

    private static List<Path> javaFiles(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (Stream<Path> files = Files.walk(directory)) {
            return files.filter(path -> path.getFileName().toString().endsWith(".java")).sorted().toList();
        }
    }
}
