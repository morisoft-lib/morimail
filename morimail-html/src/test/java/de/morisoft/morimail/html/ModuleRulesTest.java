package de.morisoft.morimail.html;

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
 * Fails the build when the helper module breaks the rules of the phase. Like the core's own rule
 * tests it checks source text after comments are removed; it does not look at bytecode or string
 * contents.
 *
 * <ul>
 *   <li>The helper imports only {@code java.*} and core types. Jackson is on its compile classpath
 *       (provided scope brings it in), so this is what keeps it out.
 *   <li>The core names the helper nowhere: not in its POM and not under its {@code src}.
 *   <li>The two public types of the helper state their thread safety.
 * </ul>
 */
class ModuleRulesTest {

    private static final Pattern BLOCK_COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
    private static final Pattern LINE_COMMENT = Pattern.compile("//[^\\n]*");
    private static final Pattern IMPORT_LINE =
            Pattern.compile("^\\s*import\\s+(?:static\\s+)?([\\w.]+(?:\\.\\*)?)\\s*;", Pattern.MULTILINE);
    private static final Pattern CORE_TYPE = Pattern.compile("de\\.morisoft\\.morimail\\.[A-Z]\\w*");
    private static final Pattern PUBLIC_TYPE = Pattern.compile(
            "^[ \\t]*public\\s+(?:(?:static|final|abstract|sealed|non-sealed|strictfp)\\s+)*"
                    + "(?:class|interface|record|enum)\\s+(\\w+)",
            Pattern.MULTILINE);
    private static final Pattern STATEMENT = Pattern.compile("thread|immutable", Pattern.CASE_INSENSITIVE);

    private final Path module = Path.of(System.getProperty("basedir", ".")).toAbsolutePath().normalize();

    @Test
    void mainImportsOnlyTheJdkAndTheCore() throws IOException {
        List<Path> files = javaFiles(module.resolve("src/main/java"));
        assertThat(files).as("helper files").isNotEmpty();
        assertThat(files).anyMatch(file -> file.getFileName().toString().equals("HtmlImages.java"));

        List<String> violations = new ArrayList<>();
        for (Path file : files) {
            String code = withoutComments(file);
            Matcher imports = IMPORT_LINE.matcher(code);
            while (imports.find()) {
                String name = imports.group(1);
                if (!name.startsWith("java.") && !CORE_TYPE.matcher(name).matches()) {
                    violations.add(relative(file) + ": imports " + name);
                }
            }
            for (String forbidden : List.of("com.fasterxml", "de.morisoft.morimail.graph",
                    "de.morisoft.morimail.entra")) {
                if (code.contains(forbidden)) {
                    violations.add(relative(file) + ": mentions " + forbidden);
                }
            }
        }
        Collections.sort(violations);
        assertThat(violations).isEmpty();
    }

    @Test
    void morimailDoesNotReferenceTheHelper() throws IOException {
        Path core = module.resolve("../morimail").normalize();
        assertThat(core.resolve("src/main/java")).isDirectory();

        List<Path> files = new ArrayList<>();
        files.add(core.resolve("pom.xml"));
        try (Stream<Path> walk = Files.walk(core.resolve("src"))) {
            files.addAll(walk.filter(Files::isRegularFile).sorted().toList());
        }
        List<String> violations = new ArrayList<>();
        for (Path file : files) {
            // ISO-8859-1 reads every byte, so the binary test keystore does not fail the decoding.
            String text = new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1);
            if (text.contains("morimail-html") || text.contains("morimail.html")) {
                violations.add(module.relativize(file.toAbsolutePath().normalize()).toString().replace('\\', '/'));
            }
        }
        assertThat(violations).isEmpty();
    }

    @Test
    void publicTypesStateThreadSafety() throws IOException {
        List<String> found = new ArrayList<>();
        List<String> violations = new ArrayList<>();
        for (Path file : javaFiles(module.resolve("src/main/java"))) {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            Matcher matcher = PUBLIC_TYPE.matcher(text);
            while (matcher.find()) {
                String type = matcher.group(1);
                found.add(type);
                String javadoc = javadocBefore(text, matcher.start());
                if (javadoc == null) {
                    violations.add(type + " has no Javadoc");
                } else if (!STATEMENT.matcher(javadoc).find()) {
                    violations.add(type + " Javadoc states no thread safety");
                }
            }
        }
        assertThat(found).containsExactlyInAnyOrder("HtmlImages", "Result");
        assertThat(violations).isEmpty();
    }

    /** Returns the Javadoc block directly before the declaration, or null if there is none. */
    private static String javadocBefore(String text, int declarationStart) {
        String before = text.substring(0, declarationStart).stripTrailing();
        // Skip annotation lines between the comment and the type.
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

    private static String withoutComments(Path file) throws IOException {
        String text = Files.readString(file, StandardCharsets.UTF_8);
        text = BLOCK_COMMENT.matcher(text).replaceAll(" ");
        return LINE_COMMENT.matcher(text).replaceAll("");
    }

    private String relative(Path file) {
        return module.relativize(file.toAbsolutePath().normalize()).toString().replace('\\', '/');
    }
}
