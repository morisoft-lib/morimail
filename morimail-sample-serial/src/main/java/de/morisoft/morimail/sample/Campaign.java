package de.morisoft.morimail.sample;

import de.morisoft.morimail.MailTemplate;
import java.io.IOException;
import java.nio.charset.MalformedInputException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The files of a campaign directory: the template, the recipient rows and the attachments.
 *
 * <p>Immutable and safe to share between threads.
 *
 * @param template subject, HTML body and optional text body
 * @param rows one row per recipient, in file order
 * @param attachments the regular files of {@code attachments/}, sorted by file name
 */
record Campaign(MailTemplate template, List<Row> rows, List<Path> attachments) {

    /** The placeholder pattern of the core's template renderer, copied so both agree on every name. */
    static final Pattern PLACEHOLDER = Pattern.compile("\\$\\$(!?)([A-Za-z0-9_.-]+)\\$\\$");

    /**
     * One recipient row.
     *
     * @param line the physical line of {@code recipients.csv} on which the row starts
     * @param fields the cells by header name, in header order
     */
    record Row(int line, Map<String, String> fields) {
        String email() {
            return fields.get("email");
        }
    }

    /**
     * One CSV record as the reader found it.
     *
     * @param line the physical line on which the record starts
     * @param cells the cells
     */
    record CsvRecord(int line, List<String> cells) {
    }

    /**
     * Reads the campaign directory. Adds a message to {@code problems} for every defect and
     * returns null when the campaign cannot be used.
     */
    static Campaign read(Path dir, List<String> problems) {
        if (!Files.isDirectory(dir)) {
            problems.add(dir + ": campaign directory not found");
            return null;
        }
        String subject = readText(dir.resolve("subject.txt"), problems);
        if (subject != null) {
            int end = subject.length();
            while (end > 0 && (subject.charAt(end - 1) == '\n' || subject.charAt(end - 1) == '\r')) {
                end--;
            }
            subject = subject.substring(0, end);
        }
        String html = readText(dir.resolve("body.html"), problems);
        Path textFile = dir.resolve("body.txt");
        String text = Files.exists(textFile) ? readText(textFile, problems) : null;
        String csv = readText(dir.resolve("recipients.csv"), problems);
        List<Path> attachments = new ArrayList<>();
        Path attachmentDir = dir.resolve("attachments");
        if (Files.isDirectory(attachmentDir)) {
            try (Stream<Path> files = Files.list(attachmentDir)) {
                // Hidden files such as .DS_Store are editor and OS leftovers, never attachments.
                files.filter(Files::isRegularFile)
                        .filter(file -> !file.getFileName().toString().startsWith("."))
                        .forEach(attachments::add);
            } catch (IOException e) {
                problems.add("attachments/ cannot be listed");
            }
            attachments.sort(Comparator.comparing(file -> file.getFileName().toString()));
        }
        if (subject == null || html == null || csv == null) {
            return null;
        }
        List<CsvRecord> records = parseCsv(csv, problems);
        if (records.isEmpty()) {
            problems.add("recipients.csv: the header row is missing");
            return null;
        }
        List<String> header = records.get(0).cells();
        boolean headerBroken = false;
        for (String cell : header) {
            if (cell.contains(";")) {
                problems.add("recipients.csv: the header looks ';'-delimited; "
                        + "this reader accepts commas only");
                headerBroken = true;
                break;
            }
        }
        if (!header.contains("email")) {
            problems.add("recipients.csv: no column named 'email' in the header");
            headerBroken = true;
        }
        Set<String> seen = new HashSet<>();
        Set<String> repeated = new LinkedHashSet<>();
        for (String cell : header) {
            if (!seen.add(cell)) {
                repeated.add(cell);
            }
        }
        for (String name : repeated) {
            problems.add("recipients.csv: column '" + name + "' appears more than once in the header");
            headerBroken = true;
        }
        Set<String> missing = new LinkedHashSet<>();
        missing.addAll(missingColumns("subject.txt", subject, header, problems));
        missing.addAll(missingColumns("body.html", html, header, problems));
        missing.addAll(missingColumns("body.txt", text, header, problems));
        if (headerBroken) {
            return null;
        }
        List<Row> rows = new ArrayList<>();
        for (CsvRecord record : records.subList(1, records.size())) {
            List<String> cells = record.cells();
            if (cells.size() != header.size()) {
                problems.add("recipients.csv line " + record.line() + ": " + cells.size()
                        + " fields, but the header has " + header.size());
                continue;
            }
            Map<String, String> fields = new LinkedHashMap<>();
            for (String name : missing) {
                fields.put(name, "");
            }
            for (int i = 0; i < header.size(); i++) {
                fields.put(header.get(i), cells.get(i));
            }
            rows.add(new Row(record.line(), Collections.unmodifiableMap(fields)));
        }
        Map<String, Integer> firstSeen = new HashMap<>();
        for (Row row : rows) {
            String email = row.email();
            if (email.isBlank()) {
                continue;
            }
            Integer first = firstSeen.putIfAbsent(email.toLowerCase(Locale.ROOT), row.line());
            if (first != null) {
                problems.add("recipients.csv line " + row.line()
                        + ": duplicate address, first seen in line " + first);
            }
        }
        if (records.size() == 1) {
            problems.add("recipients.csv: no recipient rows");
            return null;
        }
        return new Campaign(new MailTemplate(subject, html, text), List.copyOf(rows),
                List.copyOf(attachments));
    }

    /**
     * The placeholder names of a template text in order of first appearance, {@code $$name$$} and
     * {@code $$!name$$} alike.
     */
    static Set<String> placeholders(String text) {
        Set<String> names = new LinkedHashSet<>();
        if (text != null) {
            Matcher matcher = PLACEHOLDER.matcher(text);
            while (matcher.find()) {
                names.add(matcher.group(2));
            }
        }
        return names;
    }

    /** Adds one problem for every placeholder of {@code text} that is no header cell; returns those names. */
    private static Set<String> missingColumns(String file, String text, List<String> header,
            List<String> problems) {
        Set<String> missing = new LinkedHashSet<>();
        for (String name : placeholders(text)) {
            if (!header.contains(name)) {
                problems.add(file + ": placeholder $$" + name + "$$ has no column in recipients.csv");
                missing.add(name);
            }
        }
        return missing;
    }

    /**
     * Reads a file as strict UTF-8 and removes one leading byte order mark. Adds a message to
     * {@code problems} and returns null when the file is missing or cannot be read.
     */
    static String readText(Path file, List<String> problems) {
        Path fileName = file.getFileName();
        String name = fileName == null ? file.toString() : fileName.toString();
        if (!Files.isRegularFile(file)) {
            problems.add(name + " is missing");
            return null;
        }
        String text;
        try {
            text = Files.readString(file);
        } catch (MalformedInputException e) {
            problems.add(name + " is not valid UTF-8");
            return null;
        } catch (IOException e) {
            problems.add(name + " cannot be read");
            return null;
        }
        return text.startsWith("﻿") ? text.substring(1) : text;
    }

    /**
     * Splits CSV text into records. Commas separate fields; a field may be quoted, with a doubled
     * quote for a literal quote, and then may hold commas and line breaks. A record ends at LF,
     * CRLF or a lone CR outside quotes. Every record carries the physical line it starts on.
     */
    static List<CsvRecord> parseCsv(String text, List<String> problems) {
        List<CsvRecord> records = new ArrayList<>();
        List<String> cells = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        int line = 1;
        int recordLine = 1;
        boolean inQuotes = false;
        boolean fieldStart = true;
        boolean recordHasContent = false;
        int length = text.length();
        for (int i = 0; i < length; i++) {
            char c = text.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < length && text.charAt(i + 1) == '"') {
                        cell.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    if (c == '\n') {
                        line++;
                    } else if (c == '\r') {
                        if (i + 1 < length && text.charAt(i + 1) == '\n') {
                            cell.append('\r');
                            i++;
                            c = '\n';
                        }
                        line++;
                    }
                    cell.append(c);
                }
                continue;
            }
            if (c == ',') {
                cells.add(cell.toString());
                cell.setLength(0);
                fieldStart = true;
                recordHasContent = true;
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < length && text.charAt(i + 1) == '\n') {
                    i++;
                }
                if (recordHasContent || cell.length() > 0 || !fieldStart) {
                    cells.add(cell.toString());
                    records.add(new CsvRecord(recordLine, List.copyOf(cells)));
                }
                cells = new ArrayList<>();
                cell.setLength(0);
                fieldStart = true;
                recordHasContent = false;
                line++;
                recordLine = line;
            } else if (c == '"' && fieldStart) {
                inQuotes = true;
                fieldStart = false;
                recordHasContent = true;
            } else {
                cell.append(c);
                fieldStart = false;
                recordHasContent = true;
            }
        }
        if (inQuotes) {
            problems.add("recipients.csv line " + recordLine + ": a quoted field is not closed");
        } else if (recordHasContent || cell.length() > 0 || !fieldStart) {
            cells.add(cell.toString());
            records.add(new CsvRecord(recordLine, List.copyOf(cells)));
        }
        return records;
    }
}
