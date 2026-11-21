package de.morisoft.morimail.sample;

import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.WRITE;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.morisoft.morimail.MailException;
import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * An append-only JSON-lines journal of send attempts. Every line is forced to the device before
 * the next mail is sent. A mail that is in flight when the process dies has no line, so the next
 * run sends it again (at-least-once).
 *
 * <p>Not thread-safe: use one instance per run.
 */
final class Journal implements Closeable {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final FileChannel channel;
    private final Set<String> accepted;

    private Journal(FileChannel channel, Set<String> accepted) {
        this.channel = channel;
        this.accepted = accepted;
    }

    /** Returns the journal key of a recipient: the campaign id, a colon and the lower-cased address. */
    static String key(String campaignId, String email) {
        return campaignId + ":" + email.toLowerCase(Locale.ROOT);
    }

    /**
     * Reads the keys that were accepted in an earlier run and opens the file for appending. A line
     * that cannot be read is an error, never a silent "accepted".
     */
    static Journal open(Path file) throws IOException {
        Set<String> accepted = new HashSet<>();
        if (Files.isRegularFile(file)) {
            String content = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            String[] lines = content.split("\n", -1);
            for (int i = 0; i < lines.length; i++) {
                if (lines[i].isBlank()) {
                    continue;
                }
                JsonNode node;
                try {
                    node = MAPPER.readTree(lines[i]);
                } catch (IOException e) {
                    throw new IOException(file.getFileName() + " line " + (i + 1) + " cannot be read");
                }
                if (node == null || !node.isObject()) {
                    throw new IOException(file.getFileName() + " line " + (i + 1) + " cannot be read");
                }
                JsonNode outcome = node.get("outcome");
                JsonNode key = node.get("key");
                if (outcome != null && outcome.isTextual() && outcome.asText().equals("accepted")
                        && key != null && key.isTextual()) {
                    accepted.add(key.asText());
                }
            }
        }
        return new Journal(FileChannel.open(file, CREATE, WRITE, APPEND), accepted);
    }

    /** Tells whether an earlier attempt for this key was accepted. */
    boolean isAccepted(String key) {
        return accepted.contains(key);
    }

    /**
     * Appends one line and forces it to the device.
     *
     * @param outcome {@code accepted} or {@code failed}
     * @param error the failure, or null
     */
    void append(String key, String email, String outcome, MailException error) throws IOException {
        Map<String, Object> line = new LinkedHashMap<>();
        line.put("key", key);
        line.put("email", email);
        line.put("timestamp", Instant.now().toString());
        line.put("outcome", outcome);
        if (error != null) {
            line.put("errorClass", error.getClass().getSimpleName());
            if (error.getMessage() != null) {
                line.put("errorMessage", error.getMessage());
            }
        }
        ByteBuffer bytes = ByteBuffer.wrap(
                (MAPPER.writeValueAsString(line) + "\n").getBytes(StandardCharsets.UTF_8));
        while (bytes.hasRemaining()) {
            channel.write(bytes);
        }
        channel.force(true);
        if (outcome.equals("accepted")) {
            accepted.add(key);
        }
    }

    @Override
    public void close() throws IOException {
        channel.close();
    }
}
