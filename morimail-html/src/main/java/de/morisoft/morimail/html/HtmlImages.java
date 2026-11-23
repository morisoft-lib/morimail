package de.morisoft.morimail.html;

import de.morisoft.morimail.Attachment;
import de.morisoft.morimail.InvalidMailException;
import de.morisoft.morimail.MailTemplate;
import de.morisoft.morimail.TransientMailException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns the image URLs of a mail template into {@code cid:} references plus inline images.
 *
 * <p>{@link #embed(MailTemplate, Path)} rewrites the {@code src} attribute of every {@code <img>}
 * element in the HTML part of a template. Call it once per template, before the template is
 * rendered, so recipient data never reaches it. The subject and the text part are never touched.
 *
 * <p>Which {@code src} values change: a value that contains {@code $$} (a placeholder), a value
 * with the scheme {@code cid:}, a value with the scheme {@code data:} or {@code http:} or
 * {@code https:} unless a work directory is given (see below), and a value with any other scheme
 * stay exactly as written. A value without a scheme, and a {@code file:} URI, is
 * a local image: the file must lie inside the base directory, must be readable and must be an
 * image, otherwise the call fails with an {@link InvalidMailException} that names the {@code src}
 * as written. The only character reference that is decoded is {@code &amp;}. A percent-encoded
 * value is decoded once, and a query and a fragment are dropped.
 *
 * <p>The content ID of an image is the file name as written in the {@code src}, without its last
 * extension, with every character outside {@code A-Z a-z 0-9 . _ -} replaced by one {@code -}
 * (an empty result becomes {@code image}). A second image with the same ID gets {@code -2},
 * {@code -3} and so on, and an ID that the template already uses as a {@code cid:} reference
 * counts as taken. The same file, however it is written, gives one image and one ID: the first
 * name in document order wins. The file name shown to the recipient is that same name.
 *
 * <p>Remote images are opt-in: only {@link #embed(MailTemplate, Path, Path)} makes network
 * requests, and only for {@code http:} and {@code https:} sources without {@code $$}. Each image is
 * downloaded into the work directory under a new unique name and becomes an inline image. A
 * download has a connect timeout, one time budget for the response and its body, a size limit below
 * 3 MiB and an {@code image/} content type. A network error, a timeout, an HTTP 5xx status or 429
 * fails with a {@link TransientMailException}, because a retry can help; every other failure is an
 * {@link InvalidMailException}, because the template is wrong. The author of a template is
 * trusted: a template URL, also after a redirect, can reach any host this machine can reach.
 *
 * <p>Storable form: {@link #toDataUris(MailTemplate, Path)} turns every local image into a
 * {@code data:TYPE;base64,PAYLOAD} source, so the HTML alone carries its images and can be stored
 * as one string next to the subject and the text. Later, {@link #embed(MailTemplate, Path, Path)}
 * decodes the {@code data:image/...;base64,} sources back into inline images in a work directory.
 * The stored form carries no file names, so after the round trip the content IDs are
 * {@code image}, {@code image-2} and so on.
 *
 * <p>How to use the result: pass {@link Result#template()} to
 * {@code Mail.Builder.template(MailTemplate, Map)} and each image of {@link Result#images()} to
 * {@code Mail.Builder.attach(Attachment)}. {@code build()} then applies the checks for inline
 * images of the core as usual.
 *
 * <p>An {@code <img>} element inside an HTML comment (for example an Outlook conditional
 * comment) is rewritten like any other, and a source that fails there fails the call. The check
 * that a file is an image uses the file name and the platform's type detection, not the file
 * content.
 *
 * <p>This class is stateless and thread-safe, so it is safe for concurrent use.
 */
public final class HtmlImages {

    /** How long a connection may take to open; the same value as in the Graph transport. */
    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

    /** The budget of one download: the response headers and the body together. */
    static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    /** An image of this many bytes or more is refused, as the Graph transport refuses it. */
    static final long MAX_IMAGE_BYTES = 3L * 1024 * 1024;

    // NORMAL follows redirects except from https to http (D-14); the client is thread-safe and shared (P6).
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private static final System.Logger LOG = System.getLogger(HtmlImages.class.getName());

    // Copy of Mail.Builder.CID_REFERENCE (Phase 5 D-04 to D-06); keep both equal.
    static final Pattern CID_REFERENCE =
            Pattern.compile("(?i)(?<![\\p{L}\\p{N}])cid:([^\"'\\s)>]*)");

    // One attribute of a tag: a name and an optional value, possessive so a hostile tag cannot backtrack.
    private static final String ATTRIBUTE =
            "[^\\s=/>\"'<]++(?:\\s*+=\\s*+(?:\"[^\"]*+\"|'[^']*+'|[^\\s>]*+))?+";

    // An img tag up to the value of its first src attribute; groups 1, 2, 3 are the double-quoted,
    // single-quoted and unquoted value. The tag name is not followed by a name character, and "<" is
    // not allowed in an attribute name, so an unclosed tag cannot swallow the rest of the document.
    private static final Pattern IMG_SRC = Pattern.compile(
            "<img(?=[\\s/])(?:[\\s/]++(?!src[\\s=/>])" + ATTRIBUTE + ")*+"
                    + "[\\s/]++src\\s*+=\\s*+(?:\"([^\"]*+)\"|'([^']*+)'|([^\\s\"'>]++))",
            Pattern.CASE_INSENSITIVE);

    // One or more characters (A1): a drive letter such as C: is an unknown scheme, so the value stays unchanged as D-09 states.
    private static final Pattern SCHEME = Pattern.compile("^([A-Za-z][A-Za-z0-9+.-]*):");

    // An image type for the attachment: the image/ prefix plus a subtype of letters, digits and . + - only (P1).
    private static final Pattern IMAGE_TYPE = Pattern.compile("(?i)image/[a-z0-9][a-z0-9.+-]*");

    // The image entries of Mail.Builder.CONTENT_TYPES (D-12); HtmlImagesTest pins the result against build().
    private static final Map<String, String> CONTENT_TYPES = Map.ofEntries(
            Map.entry("png", "image/png"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("gif", "image/gif"));

    private HtmlImages() {
        // Static methods only, no instances.
    }

    /**
     * The converted template and its inline images.
     *
     * <p>This record is immutable and safe to share between threads. Every image has its content ID
     * set, and the images are in document order of first use.
     *
     * @param template the template with every convertible image source replaced by a {@code cid:}
     *     reference
     * @param images the inline images, one per distinct image file
     */
    public record Result(MailTemplate template, List<Attachment> images) {

        /**
         * Copies the list so the result cannot change after construction.
         *
         * @param template the converted template
         * @param images the inline images
         * @throws NullPointerException if an argument or an image is {@code null}
         */
        public Result {
            Objects.requireNonNull(template, "template");
            images = List.copyOf(images);
        }
    }

    /**
     * Rewrites the local image sources of a template to {@code cid:} references.
     *
     * <p>The method reads no file content and writes nothing: each image attachment points at the
     * file in place. A {@code data:} source stays unchanged. A template whose HTML is {@code null}
     * or has nothing to rewrite comes back as the same instance with an empty list.
     *
     * @param template the template to convert
     * @param baseDir the directory that local sources resolve against; it is checked only when a
     *     local source is resolved
     * @return the converted template and the inline images
     * @throws InvalidMailException if a local source is outside the base directory, missing,
     *     unreadable, not an image or not a valid path, or if the base directory is not usable
     * @throws NullPointerException if an argument is {@code null}
     */
    public static Result embed(MailTemplate template, Path baseDir) throws InvalidMailException {
        Objects.requireNonNull(template, "template");
        Objects.requireNonNull(baseDir, "baseDir");
        try {
            return rewrite(template, baseDir, null, REQUEST_TIMEOUT, false);
        } catch (TransientMailException e) {
            throw new AssertionError("no download without a work directory", e);
        }
    }

    /**
     * Rewrites the local and the remote image sources of a template to {@code cid:} references.
     *
     * <p>Like {@link #embed(MailTemplate, Path)}, and in addition the {@code http:} and
     * {@code https:} sources (without {@code $$}) are downloaded into {@code workDir}, each under a
     * new unique name, and become inline images. The caller owns {@code workDir} and cleans it up,
     * also after a failure: files written before a later source failed stay there, and no file the
     * call did not create is deleted, overwritten or renamed.
     *
     * <p>A download waits at most 10 seconds for the connection. The response headers and the body
     * together get one budget of 30 seconds, counted from the request; a redirect is followed (but
     * never from {@code https:} to {@code http:}), and each followed redirect may again wait up to
     * 30 seconds for its own headers. An image must be below 3 MiB, as the Graph transport requires,
     * and the {@code Content-Type} header must name an {@code image/} type; the file name extension
     * of the URL is not used. The image ID and the file name shown to the recipient come from the
     * last segment of the URL path.
     *
     * <p>Exception messages name the {@code src} as written, a query included, and for an HTTP
     * failure the status only, never a response body. The one DEBUG log record per download
     * ({@code java.util.logging} level FINE) shows only scheme, host, port and path.
     *
     * <p>A {@code data:image/...;base64,} source (the stored form of
     * {@link #toDataUris(MailTemplate, Path)}) is decoded into a new unique file in {@code workDir}
     * and becomes an inline image, with the same type check and the same 3 MiB cap. The ID is
     * {@code image}, {@code image-2} and so on, and the file name is the ID plus the subtype of the
     * type. A failure names the source by position and reason only, for example
     * {@code data: image source #2: payload is not valid base64}, and never any part of the
     * payload. Parameters between the type and {@code base64} are ignored, and white space in the
     * payload is removed before decoding.
     *
     * <p>The author of a template is trusted: a URL in the template, also after a redirect, can reach
     * any host this machine can reach. Recipient data never reaches this method, because a
     * {@code src} with {@code $$} is never fetched.
     *
     * @param template the template to convert
     * @param baseDir the directory that local sources resolve against; it is checked only when a
     *     local source is resolved
     * @param workDir the existing directory that receives the downloaded and decoded images
     * @return the converted template and the inline images
     * @throws InvalidMailException if a local source is outside the base directory, missing,
     *     unreadable, not an image or not a valid path, if a remote source is not a valid URL, the
     *     server answers with an HTTP 4xx status (other than 429) or another unsuccessful status, the
     *     response is not an image or is 3 MiB or larger, if a {@code data:} image source is not
     *     base64, not an image type, not valid or 3 MiB or larger, or if the work directory is not
     *     usable
     * @throws TransientMailException if a download fails with a network error, times out, is
     *     interrupted, or the server answers with an HTTP 5xx status or 429; a retry can help
     * @throws NullPointerException if an argument is {@code null}
     */
    public static Result embed(MailTemplate template, Path baseDir, Path workDir)
            throws InvalidMailException, TransientMailException {
        Objects.requireNonNull(template, "template");
        Objects.requireNonNull(baseDir, "baseDir");
        Objects.requireNonNull(workDir, "workDir");
        return embed(template, baseDir, workDir, REQUEST_TIMEOUT);
    }

    // Test seam: the timeout tests use seconds instead of REQUEST_TIMEOUT (P9).
    static Result embed(MailTemplate template, Path baseDir, Path workDir, Duration requestTimeout)
            throws InvalidMailException, TransientMailException {
        return rewrite(template, baseDir, workDir, requestTimeout, false);
    }

    /**
     * Rewrites the local image sources of a template to {@code data:} URIs, the storable form.
     *
     * <p>Every local image becomes {@code data:TYPE;base64,PAYLOAD}, so the HTML alone carries its
     * images and can be stored as one string next to the subject and the text. Later,
     * {@link #embed(MailTemplate, Path, Path)} turns it back into the sendable form. The local rules
     * and messages are those of {@link #embed(MailTemplate, Path)}, and each image must be under
     * 3 MiB, as Graph requires, so every stored form can be embedded again. A {@code cid:},
     * {@code data:}, {@code http:}, {@code https:}, {@code $$} or unknown-scheme source stays
     * unchanged, and so do the subject and the text. A template whose HTML is {@code null} or has
     * nothing to convert comes back as the same instance.
     *
     * <p>The stored form carries no file names, so after the round trip the content IDs are
     * {@code image}, {@code image-2} and so on.
     *
     * <p>This method is stateless and safe for concurrent use.
     *
     * @param template the template to convert
     * @param baseDir the directory that local sources resolve against; it is checked only when a
     *     local source is resolved
     * @return the template with every local image source replaced by a {@code data:} URI
     * @throws InvalidMailException if a local source is outside the base directory, missing,
     *     unreadable, not an image, not a valid path or 3 MiB or larger, or if the base directory
     *     is not usable
     * @throws NullPointerException if an argument is {@code null}
     */
    public static MailTemplate toDataUris(MailTemplate template, Path baseDir) throws InvalidMailException {
        Objects.requireNonNull(template, "template");
        Objects.requireNonNull(baseDir, "baseDir");
        try {
            return rewrite(template, baseDir, null, REQUEST_TIMEOUT, true).template();
        } catch (TransientMailException e) {
            throw new AssertionError("no download without a work directory", e);
        }
    }

    /**
     * Rewrites the local and the remote image sources of a template to {@code data:} URIs.
     *
     * <p>Like {@link #toDataUris(MailTemplate, Path)}, and in addition the {@code http:} and
     * {@code https:} sources (without {@code $$}) are downloaded with the limits and the failure
     * categories of {@link #embed(MailTemplate, Path, Path)} and become {@code data:} URIs.
     * {@code workDir} is only the opt-in for downloads: this method never writes, creates or deletes
     * a file there.
     *
     * @param template the template to convert
     * @param baseDir the directory that local sources resolve against; it is checked only when a
     *     local source is resolved
     * @param workDir the opt-in for downloads; it is not written
     * @return the template with every local and remote image source replaced by a {@code data:} URI
     * @throws InvalidMailException if a local source is outside the base directory, missing,
     *     unreadable, not an image, not a valid path or 3 MiB or larger, if a remote source is not a
     *     valid URL, the server answers with an HTTP 4xx status (other than 429) or another
     *     unsuccessful status, or the response is not an image or is 3 MiB or larger
     * @throws TransientMailException if a download fails with a network error, times out, is
     *     interrupted, or the server answers with an HTTP 5xx status or 429; a retry can help
     * @throws NullPointerException if an argument is {@code null}
     */
    public static MailTemplate toDataUris(MailTemplate template, Path baseDir, Path workDir)
            throws InvalidMailException, TransientMailException {
        Objects.requireNonNull(template, "template");
        Objects.requireNonNull(baseDir, "baseDir");
        Objects.requireNonNull(workDir, "workDir");
        return rewrite(template, baseDir, workDir, REQUEST_TIMEOUT, true).template();
    }

    // A null workDir means "no downloads" and no data: decoding. With dataUris the values become
    // data: URIs and no image is created; otherwise they become cid: references plus images.
    private static Result rewrite(MailTemplate template, Path baseDir, Path workDir, Duration requestTimeout,
            boolean dataUris) throws InvalidMailException, TransientMailException {
        String html = template.html();
        if (html == null) {
            return new Result(template, List.of());
        }
        Set<String> taken = new HashSet<>();
        Matcher references = CID_REFERENCE.matcher(html);
        while (references.find()) {
            if (!references.group(1).isEmpty()) {
                taken.add(references.group(1));
            }
        }
        Map<String, String> replacementByKey = new HashMap<>();
        List<Attachment> images = new ArrayList<>();
        StringBuilder out = new StringBuilder(html.length());
        int last = 0;
        int dataPosition = 0;
        boolean replaced = false;
        Matcher m = IMG_SRC.matcher(html);
        while (m.find()) {
            int g = m.start(1) >= 0 ? 1 : m.start(2) >= 0 ? 2 : 3;
            String raw = m.group(g);
            String value = raw.replace("&amp;", "&");
            if (value.contains("$$")) {
                continue;
            }
            Matcher s = SCHEME.matcher(value);
            String scheme = s.find() ? s.group(1).toLowerCase(Locale.ROOT) : null;
            // http(s) is remote only with a work directory (spec 10.4 opt-in); data: is decoded only by embed with one.
            boolean remote = workDir != null && ("http".equals(scheme) || "https".equals(scheme));
            boolean data = !dataUris && workDir != null && "data".equals(scheme);
            if (scheme != null && !scheme.equals("file") && !remote && !data) {
                continue;
            }
            String key;
            if (data) {
                // Every data: value counts, repeats included (the position names the source in a message).
                dataPosition++;
                key = value;
                if (!replacementByKey.containsKey(key)) {
                    String label = "data: image source #" + dataPosition + ": ";
                    Fetched decoded = decodeData(value, label);
                    String id = newId("", taken);
                    String type = decoded.contentType();
                    Path file = writeToWorkDir(label, workDir, decoded.bytes());
                    images.add(new Attachment(fileName("", id, type), type, file, id));
                    replacementByKey.put(key, "cid:" + id);
                }
            } else if (remote) {
                // The source after &amp; decoding is the key: one source, one download (D-19).
                key = value;
                if (!replacementByKey.containsKey(key)) {
                    URI uri = parseUrl(raw, value);
                    Fetched fetched = download(raw, uri, requestTimeout);
                    String type = fetched.contentType();
                    if (dataUris) {
                        replacementByKey.put(key, dataUri(type, fetched.bytes()));
                    } else {
                        String path = uri.getPath();
                        String segment = path == null ? "" : path.substring(path.lastIndexOf('/') + 1);
                        String id = newId(segment, taken);
                        Path file = writeToWorkDir("img src \"" + raw + "\": ", workDir, fetched.bytes());
                        images.add(new Attachment(fileName(segment, id, type), type, file, id));
                        replacementByKey.put(key, "cid:" + id);
                    }
                }
            } else {
                LocalFile local = resolveLocal(raw, value, scheme != null, baseDir);
                Path file = local.file();
                key = file.toString();
                if (!replacementByKey.containsKey(key)) {
                    String name = local.name();
                    String type = contentTypeOf(file, name);
                    if (type == null || !IMAGE_TYPE.matcher(type).matches()) {
                        throw invalid(raw, "is not an image file (content type "
                                + (type == null ? "unknown" : type) + ")");
                    }
                    if (dataUris) {
                        replacementByKey.put(key, dataUri(type, readSmallFile(raw, file)));
                    } else {
                        String id = newId(name, taken);
                        images.add(new Attachment(fileName(name, id, type), type, file, id));
                        replacementByKey.put(key, "cid:" + id);
                    }
                }
            }
            out.append(html, last, m.start(g)).append(replacementByKey.get(key));
            last = m.end(g);
            replaced = true;
        }
        if (!replaced) {
            return new Result(template, List.of());
        }
        out.append(html, last, html.length());
        return new Result(new MailTemplate(template.subject(), out.toString(), template.text()), images);
    }

    // The comparison GraphTransport.validate uses: a file of 3 MiB or more is refused, so a stored form can always be embedded again (A2).
    private static byte[] readSmallFile(String raw, Path file) throws InvalidMailException {
        try {
            if (Files.size(file) >= MAX_IMAGE_BYTES) {
                throw invalid(raw, "image is 3 MiB or larger");
            }
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw invalid(raw, "is not a readable file");
        }
    }

    private static String dataUri(String type, byte[] bytes) {
        return "data:" + type + ";base64," + Base64.getEncoder().encodeToString(bytes);
    }

    // Parses data:TYPE[;parameter]*;base64,PAYLOAD. A message names the source by position and the reason, never the payload (D-23).
    private static Fetched decodeData(String value, String label) throws InvalidMailException {
        int comma = value.indexOf(',');
        if (comma < 0) {
            throw new InvalidMailException(label + "is not base64");
        }
        String[] header = value.substring(5, comma).split(";", -1);
        String type = header[0].trim();
        if (!IMAGE_TYPE.matcher(type).matches()) {
            throw new InvalidMailException(label + "is not an image type");
        }
        if (header.length < 2 || !"base64".equalsIgnoreCase(header[header.length - 1].trim())) {
            throw new InvalidMailException(label + "is not base64");
        }
        String payload = value.substring(comma + 1).replaceAll("[ \\t\\r\\n\\f]", "");
        // The base64 length of 3 MiB: a longer payload cannot decode to less, so it is refused before decoding.
        if (payload.length() > 4 * (MAX_IMAGE_BYTES / 3)) {
            throw new InvalidMailException(label + "is 3 MiB or larger");
        }
        byte[] bytes;
        try {
            // The strict decoder: the MIME decoder would skip illegal characters.
            bytes = Base64.getDecoder().decode(payload);
        } catch (IllegalArgumentException e) {
            throw new InvalidMailException(label + "payload is not valid base64");
        }
        if (bytes.length >= MAX_IMAGE_BYTES) {
            throw new InvalidMailException(label + "is 3 MiB or larger");
        }
        return new Fetched(bytes, type);
    }

    // A resolved local source: the real path is the attachment's file and the dedupe key (D-11, D-19); the name is the last segment as written (D-17, D-20, P2).
    private record LocalFile(Path file, String name) {
    }

    // A downloaded or decoded image.
    private record Fetched(byte[] bytes, String contentType) {
    }

    private static InvalidMailException invalid(String raw, String reason) {
        return new InvalidMailException("img src \"" + raw + "\": " + reason);
    }

    private static TransientMailException transientFailure(String raw, String reason, Throwable cause) {
        return new TransientMailException("img src \"" + raw + "\": " + reason, cause);
    }

    private static URI parseUrl(String raw, String value) throws InvalidMailException {
        URI uri;
        try {
            uri = new URI(value);
        } catch (URISyntaxException e) {
            throw invalid(raw, "is not a valid URL");
        }
        if (uri.getHost() == null) {
            throw invalid(raw, "is not a valid URL");
        }
        return uri;
    }

    private static Fetched download(String raw, URI uri, Duration timeout)
            throws InvalidMailException, TransientMailException {
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(uri).timeout(timeout).GET().build();
        } catch (IllegalArgumentException e) {
            throw invalid(raw, "is not a valid URL");
        }
        // Log scheme, host, port and path only: userinfo, query and fragment can hold credentials
        // (signed or token URLs), which must never reach a log (P7).
        String shown = uri.getScheme() + "://" + uri.getHost()
                + (uri.getPort() < 0 ? "" : ":" + uri.getPort()) + uri.getRawPath();
        LOG.log(System.Logger.Level.DEBUG, "Downloading image {0}", shown);
        // One budget for the whole download (D-14, P9): the headers wait at most the request
        // timeout, the body only for what is left of it.
        long deadline = System.nanoTime() + timeout.toNanos();
        HttpResponse<InputStream> response;
        try {
            response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (HttpTimeoutException e) {
            throw transientFailure(raw, "download timed out", e);
        } catch (IOException e) {
            throw transientFailure(raw, "download failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw transientFailure(raw, "download was interrupted", e);
        }
        try (InputStream body = response.body()) {
            int status = response.statusCode();
            if (status >= 500 || status == 429) {
                throw new TransientMailException(
                        "img src \"" + raw + "\": download failed with HTTP " + status);
            }
            if (status < 200 || status > 299 || status == 204) {
                throw invalid(raw, "download failed with HTTP " + status);
            }
            String type = response.headers().firstValue("Content-Type").orElse("");
            int semicolon = type.indexOf(';');
            type = (semicolon >= 0 ? type.substring(0, semicolon) : type).trim();
            if (!IMAGE_TYPE.matcher(type).matches()) {
                throw invalid(raw, "response has no image content type");
            }
            // HttpRequest.timeout bounds only the wait for the response headers (research Pattern 3),
            // so the body is read within what is left of the same budget. Every exit from this try
            // block closes the stream, and close() ends a blocked read (research Stall3).
            long left = deadline - System.nanoTime();
            if (left <= 0) {
                throw transientFailure(raw, "download timed out", null);
            }
            // A thread of its own, not the common pool, so a blocked read never waits behind or starves
            // other work of the host application (WR-01).
            CompletableFuture<byte[]> read = new CompletableFuture<>();
            Thread reader = new Thread(() -> {
                try {
                    read.complete(readAtMost(body));
                } catch (Throwable t) {
                    read.completeExceptionally(t);
                }
            }, "morimail-html-download");
            reader.setDaemon(true);
            reader.start();
            byte[] bytes;
            try {
                bytes = read.get(left, TimeUnit.NANOSECONDS);
            } catch (TimeoutException e) {
                throw transientFailure(raw, "download timed out", e);
            } catch (ExecutionException e) {
                throw transientFailure(raw, "download failed", e.getCause());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw transientFailure(raw, "download was interrupted", e);
            }
            // The comparison GraphTransport.validate uses: Graph takes files below 3 MiB (A2).
            if (bytes.length >= MAX_IMAGE_BYTES) {
                throw invalid(raw, "image is 3 MiB or larger");
            }
            return new Fetched(bytes, type);
        } catch (IOException e) {
            throw transientFailure(raw, "download failed", e);
        }
    }

    // Reading the full cap is enough: a result of MAX_IMAGE_BYTES bytes already means 3 MiB or more (A2).
    private static byte[] readAtMost(InputStream body) {
        try {
            return body.readNBytes((int) MAX_IMAGE_BYTES);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // The label starts the message: img src "SRC": or data: image source #N: .
    private static Path writeToWorkDir(String label, Path workDir, byte[] bytes) throws InvalidMailException {
        try {
            Path file = Files.createTempFile(workDir, "morimail-", null);
            Files.write(file, bytes);
            return file;
        } catch (IOException e) {
            throw new InvalidMailException(label + "work directory is not usable", e);
        }
    }

    private static LocalFile resolveLocal(String raw, String value, boolean fileScheme, Path baseDir)
            throws InvalidMailException {
        Path base;
        try {
            base = baseDir.toRealPath();
        } catch (IOException e) {
            throw invalid(raw, "base directory not found");
        }
        Path written;
        try {
            Path candidate;
            if (fileScheme) {
                URI uri = new URI(value);
                if (uri.getPath() == null) {
                    throw invalid(raw, "is not a valid path");
                }
                // A clean URI drops query and fragment but keeps an authority, so Path.of rejects file://host/...
                candidate = Path.of(new URI("file", uri.getAuthority(), uri.getPath(), null, null));
            } else {
                String path = value;
                try {
                    String decoded = new URI(value).getPath();
                    if (decoded != null) {
                        path = decoded;
                    }
                } catch (URISyntaxException e) {
                    // Not a URI (for example a space): used as written (D-10).
                }
                Path p = Path.of(path);
                candidate = p.isAbsolute() ? p : base.resolve(p);
            }
            written = candidate.normalize();
        } catch (URISyntaxException | IllegalArgumentException | FileSystemNotFoundException e) {
            throw invalid(raw, "is not a valid path");
        }
        Path real;
        try {
            real = written.toRealPath();
        } catch (IOException e) {
            throw invalid(raw, "file not found");
        }
        if (!real.startsWith(base)) {
            throw invalid(raw, "is outside the base directory");
        }
        Path name = written.getFileName();
        if (name == null || !Files.isRegularFile(real) || !Files.isReadable(real)) {
            throw invalid(raw, "is not a readable file");
        }
        return new LocalFile(real, name.toString());
    }

    private static String contentTypeOf(Path file, String name) {
        int dot = name.lastIndexOf('.');
        if (dot >= 0) {
            String known = CONTENT_TYPES.get(name.substring(dot + 1).toLowerCase(Locale.ROOT));
            if (known != null) {
                return known;
            }
        }
        try {
            return Files.probeContentType(file);
        } catch (IOException e) {
            return null;
        }
    }

    private static String newId(String name, Set<String> taken) {
        int dot = name.lastIndexOf('.');
        String base = (dot >= 0 ? name.substring(0, dot) : name).replaceAll("[^A-Za-z0-9._-]", "-");
        if (base.isEmpty()) {
            base = "image";
        }
        String id = base;
        int n = 2;
        while (!taken.add(id)) {
            id = base + "-" + n++;
        }
        return id;
    }

    private static String fileName(String segment, String id, String type) {
        boolean usable = !segment.isBlank() && segment.indexOf('/') < 0 && segment.indexOf('\\') < 0
                && segment.chars().noneMatch(Character::isISOControl);
        if (usable) {
            return segment;
        }
        return id + "." + type.substring(type.indexOf('/') + 1).replaceAll("[^A-Za-z0-9._-]", "-");
    }
}
