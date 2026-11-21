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
 * Fails the build when the core breaks the dependency rules of the spec. It checks the source
 * text of the files directly in the core package, after comments are removed; it does not look
 * at bytecode, reflection or string contents.
 *
 * <ul>
 *   <li>The core imports only {@code java.*}, and names no other qualified type either. Only
 *       {@code BuiltInTransports} may use types of the graph package.
 *   <li>No other core file mentions the graph or the entra package.
 *   <li>The graph and entra packages do not reference each other.
 * </ul>
 */
class ImportRulesTest {

    private static final String CORE = "src/main/java/de/morisoft/morimail";
    private static final String GRAPH_PACKAGE = "de.morisoft.morimail.graph";
    private static final String ENTRA_PACKAGE = "de.morisoft.morimail.entra";
    private static final String BUILT_IN = "BuiltInTransports.java";

    private static final Pattern BLOCK_COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
    private static final Pattern LINE_COMMENT = Pattern.compile("//[^\\n]*");
    private static final Pattern IMPORT_LINE =
            Pattern.compile("^\\s*import\\s+(?:static\\s+)?([\\w.]+(?:\\.\\*)?)\\s*;", Pattern.MULTILINE);
    private static final Pattern IMPORT_OR_PACKAGE_LINE =
            Pattern.compile("^\\s*(?:import|package)\\s[^;]*;", Pattern.MULTILINE);
    private static final Pattern QUALIFIED_NAME =
            Pattern.compile("(?<![\\w.])[a-z]\\w*\\.[a-z]\\w*(?:\\.[a-z]\\w*)*\\.[A-Z]\\w*");
    private static final Pattern CORE_TYPE = Pattern.compile("de\\.morisoft\\.morimail\\.[A-Z]\\w*");

    private final Path module = Path.of(System.getProperty("basedir", ".")).toAbsolutePath().normalize();

    @Test
    void coreImportsOnlyTheJdkAndKeepsTransportPackagesApart() throws IOException {
        List<Path> coreFiles = javaFiles(module.resolve(CORE), 1);
        assertThat(coreFiles).as("core files").isNotEmpty();
        assertThat(coreFiles).anyMatch(file -> file.getFileName().toString().equals("Mailer.java"));

        List<String> violations = new ArrayList<>();
        for (Path file : coreFiles) {
            boolean builtIn = file.getFileName().toString().equals(BUILT_IN);
            String code = withoutComments(file);
            checkImports(file, code, builtIn, violations);
            checkQualifiedNames(file, code, builtIn, violations);
            checkMentions(file, code, GRAPH_PACKAGE, !builtIn, violations);
            checkMentions(file, code, ENTRA_PACKAGE, true, violations);
        }
        for (Path file : javaFiles(module.resolve(CORE).resolve("graph"), Integer.MAX_VALUE)) {
            checkMentions(file, withoutComments(file), ENTRA_PACKAGE, true, violations);
        }
        for (Path file : javaFiles(module.resolve(CORE).resolve("entra"), Integer.MAX_VALUE)) {
            checkMentions(file, withoutComments(file), GRAPH_PACKAGE, true, violations);
        }

        Collections.sort(violations);
        assertThat(violations).isEmpty();
    }

    private void checkImports(Path file, String code, boolean builtIn, List<String> violations) {
        Matcher matcher = IMPORT_LINE.matcher(code);
        while (matcher.find()) {
            String name = matcher.group(1);
            boolean allowed = name.startsWith("java.")
                    || (builtIn && name.startsWith(GRAPH_PACKAGE + "."));
            if (!allowed) {
                report(violations, file, "imports " + name);
            }
        }
    }

    private void checkQualifiedNames(Path file, String code, boolean builtIn, List<String> violations) {
        String body = IMPORT_OR_PACKAGE_LINE.matcher(code).replaceAll("");
        Matcher matcher = QUALIFIED_NAME.matcher(body);
        while (matcher.find()) {
            String name = matcher.group();
            boolean allowed = name.startsWith("java.")
                    || CORE_TYPE.matcher(name).matches()
                    || (builtIn && name.startsWith(GRAPH_PACKAGE + "."));
            if (!allowed) {
                report(violations, file, "references " + name);
            }
        }
    }

    private void checkMentions(Path file, String code, String packageName, boolean forbidden,
            List<String> violations) {
        if (forbidden && Pattern.compile(Pattern.quote(packageName) + "\\b").matcher(code).find()) {
            report(violations, file, "mentions " + packageName);
        }
    }

    private void report(List<String> violations, Path file, String problem) {
        violations.add(relative(file) + ": " + problem);
    }

    private List<Path> javaFiles(Path directory, int depth) throws IOException {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (Stream<Path> files = Files.walk(directory, depth)) {
            return files.filter(path -> path.getFileName().toString().endsWith(".java")).sorted().toList();
        }
    }

    private String withoutComments(Path file) throws IOException {
        String text = Files.readString(file, StandardCharsets.UTF_8);
        text = BLOCK_COMMENT.matcher(text).replaceAll(" ");
        return LINE_COMMENT.matcher(text).replaceAll("");
    }

    private String relative(Path file) {
        return module.relativize(file.toAbsolutePath().normalize()).toString().replace('\\', '/');
    }
}
