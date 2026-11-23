package de.morisoft.morimail.html;

import de.morisoft.morimail.Attachment;
import de.morisoft.morimail.InvalidMailException;
import de.morisoft.morimail.MailTemplate;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
 * with the scheme {@code cid:}, {@code data:}, {@code http:} or {@code https:}, and a value with
 * any other scheme stay exactly as written. A value without a scheme, and a {@code file:} URI, is
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
     * file in place. A template whose HTML is {@code null} or has nothing to rewrite comes back as
     * the same instance with an empty list.
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
        return rewrite(template, baseDir);
    }

    private static Result rewrite(MailTemplate template, Path baseDir) throws InvalidMailException {
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
        Map<String, String> cidByKey = new HashMap<>();
        List<Attachment> images = new ArrayList<>();
        StringBuilder out = new StringBuilder(html.length());
        int last = 0;
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
            // Plans 07-02 and 07-03 add http(s) and data: when a work directory is given.
            if (scheme != null && !scheme.equals("file")) {
                continue;
            }
            LocalFile local = resolveLocal(raw, value, scheme != null, baseDir);
            Path file = local.file();
            String key = file.toString();
            if (!cidByKey.containsKey(key)) {
                String name = local.name();
                String type = contentTypeOf(file, name);
                if (type == null || !IMAGE_TYPE.matcher(type).matches()) {
                    throw invalid(raw, "is not an image file (content type "
                            + (type == null ? "unknown" : type) + ")");
                }
                String id = newId(name, taken);
                images.add(new Attachment(fileName(name, id, type), type, file, id));
                cidByKey.put(key, "cid:" + id);
            }
            out.append(html, last, m.start(g)).append(cidByKey.get(key));
            last = m.end(g);
            replaced = true;
        }
        if (!replaced) {
            return new Result(template, List.of());
        }
        out.append(html, last, html.length());
        return new Result(new MailTemplate(template.subject(), out.toString(), template.text()), images);
    }

    // A resolved local source: the real path is the attachment's file and the dedupe key (D-11, D-19); the name is the last segment as written (D-17, D-20, P2).
    private record LocalFile(Path file, String name) {
    }

    private static InvalidMailException invalid(String raw, String reason) {
        return new InvalidMailException("img src \"" + raw + "\": " + reason);
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
