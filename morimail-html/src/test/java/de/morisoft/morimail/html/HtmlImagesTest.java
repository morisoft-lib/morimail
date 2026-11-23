package de.morisoft.morimail.html;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import de.morisoft.morimail.Attachment;
import de.morisoft.morimail.InvalidMailException;
import de.morisoft.morimail.Mail;
import de.morisoft.morimail.MailTemplate;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

class HtmlImagesTest {

    @TempDir
    Path tempDir;

    Path base;

    @BeforeEach
    void createBaseDirectory() throws IOException {
        base = Files.createDirectories(tempDir.resolve("base"));
        image("logo.png", 1);
    }

    // A few bytes with a distinct last byte; nothing is checked in.
    private Path image(String relative, int marker) throws IOException {
        Path file = base.resolve(relative);
        Files.createDirectories(file.getParent());
        return Files.write(file, new byte[] {(byte) 0x89, 'P', 'N', 'G', (byte) marker});
    }

    private static MailTemplate html(String html) {
        return new MailTemplate("s", html, "t");
    }

    private String rewritten(String html) throws InvalidMailException {
        return HtmlImages.embed(html(html), base).template().html();
    }

    private HtmlImages.Result embed(String html) throws InvalidMailException {
        return HtmlImages.embed(html(html), base);
    }

    private String failure(String html) {
        try {
            HtmlImages.embed(html(html), base);
        } catch (InvalidMailException e) {
            return e.getMessage();
        }
        return fail("expected an InvalidMailException for " + html);
    }

    private static String img(String src) {
        return "<img src=\"" + src + "\">";
    }

    private static boolean symlink(Path link, Path target) {
        try {
            Files.createSymbolicLink(link, target);
            return true;
        } catch (UnsupportedOperationException | IOException | SecurityException e) {
            return false;
        }
    }

    private static List<String> ids(HtmlImages.Result result) {
        return result.images().stream().map(Attachment::contentId).toList();
    }

    @Test
    void nullHtmlAndNothingToRewriteReturnTheSameTemplate() throws InvalidMailException {
        MailTemplate withoutHtml = new MailTemplate("s", null, "t");
        MailTemplate empty = html("");
        MailTemplate unchanged = html(img("cid:a") + img("https://example.com/a.png"));

        for (MailTemplate template : List.of(withoutHtml, empty, unchanged)) {
            HtmlImages.Result result = HtmlImages.embed(template, base);

            assertThat(result.template()).isSameAs(template);
            assertThat(result.images()).isEmpty();
        }
    }

    @Test
    void unchangedSourcesStayByteForByte() throws InvalidMailException {
        List<String> unchanged = List.of("cid:logo", "data:image/png;base64,iVBOR",
                "https://example.com/a.png", "http://example.com/a.png", "ftp://example.com/a.png",
                "C:\\img\\logo.png", "$$logoUrl$$", "img/$$name$$.png");
        StringBuilder before = new StringBuilder();
        for (String src : unchanged) {
            before.append(img(src));
        }
        String expected = before + img("cid:logo-2");
        MailTemplate template = new MailTemplate("<img src=\"logo.png\">",
                before + img("logo.png"), "<img src=\"logo.png\">");

        HtmlImages.Result result = HtmlImages.embed(template, base);

        // The hand-written cid:logo is taken, so the file gets logo-2 (D-18); only the last tag changes.
        assertThat(result.template().html()).isEqualTo(expected);
        assertThat(ids(result)).containsExactly("logo-2");
        assertThat(result.template().subject()).isEqualTo("<img src=\"logo.png\">");
        assertThat(result.template().text()).isEqualTo("<img src=\"logo.png\">");
    }

    @Test
    void quotingAndCaseArePreserved() throws InvalidMailException {
        assertThat(rewritten("<IMG SRC='logo.png'>")).isEqualTo("<IMG SRC='cid:logo'>");
        assertThat(rewritten("<img src=logo.png>")).isEqualTo("<img src=cid:logo>");
        assertThat(rewritten("<img\n  class=a\n  src = \"logo.png\" />"))
                .isEqualTo("<img\n  class=a\n  src = \"cid:logo\" />");
    }

    @Test
    void onlyImgSrcIsRewritten() throws InvalidMailException {
        assertThat(rewritten("<img srcset=\"logo.png 2x\" data-src=\"logo.png\" src=\"logo.png\">"))
                .isEqualTo("<img srcset=\"logo.png 2x\" data-src=\"logo.png\" src=\"cid:logo\">");
        assertThat(rewritten("<picture><source src=\"logo.png\"><img src=\"logo.png\"></picture>"))
                .isEqualTo("<picture><source src=\"logo.png\"><img src=\"cid:logo\"></picture>");
        for (String untouched : List.of("<a href=\"logo.png\">x</a>", "<td background=\"logo.png\">",
                "<div style=\"background:url(logo.png)\">")) {
            HtmlImages.Result result = embed(untouched);
            assertThat(result.template().html()).isEqualTo(untouched);
            assertThat(result.images()).isEmpty();
        }
        assertThat(rewritten("<img alt=\"x src='y'\" src=\"logo.png\">"))
                .isEqualTo("<img alt=\"x src='y'\" src=\"cid:logo\">");
        assertThat(rewritten("<img SRC=\"logo.png\" src=\"other.png\">"))
                .isEqualTo("<img SRC=\"cid:logo\" src=\"other.png\">");

        HtmlImages.Result conditional =
                embed("<!--[if mso]><img src=\"logo.png\"><![endif]--><img src=\"logo.png\">");
        assertThat(conditional.template().html())
                .isEqualTo("<!--[if mso]><img src=\"cid:logo\"><![endif]--><img src=\"cid:logo\">");
        assertThat(conditional.images()).hasSize(1);
    }

    @Test
    void ampersandIsTheOnlyDecodedReference() throws IOException, InvalidMailException {
        image("a&b.png", 2);

        HtmlImages.Result result = embed(img("a&amp;b.png"));

        assertThat(result.template().html()).isEqualTo(img("cid:a-b"));
        assertThat(result.images().get(0).filename()).isEqualTo("a&b.png");
        assertThat(failure(img("a&#38;b.png")))
                .isEqualTo("img src \"a&#38;b.png\": file not found");
    }

    @Test
    void percentEncodingQueryAndFragment() throws IOException, InvalidMailException {
        image("my logo.png", 2);

        HtmlImages.Result spaces = embed(img("my%20logo.png") + img("my logo.png"));
        assertThat(spaces.template().html()).isEqualTo(img("cid:my-logo") + img("cid:my-logo"));
        assertThat(spaces.images()).hasSize(1);

        assertThat(rewritten(img("logo.png?v=2#x"))).isEqualTo(img("cid:logo"));
        assertThat(rewritten(img(base.resolve("logo.png").toString()))).isEqualTo(img("cid:logo"));
        assertThat(rewritten(img(base.resolve("logo.png").toUri() + "?v=1")))
                .isEqualTo(img("cid:logo"));
    }

    @Test
    void sourcesOutsideTheBaseDirectoryFail() throws IOException {
        Path secret = Files.write(tempDir.resolve("secret.png"), new byte[] {1, 2, 3});
        Files.createDirectories(tempDir.resolve("base-evil"));
        Files.write(tempDir.resolve("base-evil/x.png"), new byte[] {1, 2, 3});
        List<String> sources = new ArrayList<>(List.of("../secret.png", "%2e%2e/secret.png",
                secret.toString(), secret.toUri().toString(), "../base-evil/x.png"));
        if (symlink(base.resolve("escape.png"), secret)) {
            sources.add("escape.png");
        }

        for (String src : sources) {
            assertThat(failure(img(src)))
                    .as(src)
                    .isEqualTo("img src \"" + src + "\": is outside the base directory");
        }
    }

    @Test
    void missingUnreadableAndNonImageFilesFail() throws IOException {
        Files.createDirectories(base.resolve("img"));
        Files.write(base.resolve("notes.txt"), new byte[] {'h', 'i'});

        assertThat(failure(img("nope.png"))).isEqualTo("img src \"nope.png\": file not found");
        assertThat(failure(img("img"))).isEqualTo("img src \"img\": is not a readable file");
        assertThat(failure(img("notes.txt")))
                .startsWith("img src \"notes.txt\": is not an image file (content type ");
        assertThat(failure(img(""))).isEqualTo("img src \"\": is not a readable file");

        Path locked = Files.write(base.resolve("locked.png"), new byte[] {1});
        try {
            Files.setPosixFilePermissions(locked, PosixFilePermissions.fromString("---------"));
        } catch (UnsupportedOperationException e) {
            return;
        }
        // A run as root can still read the file; then there is nothing to assert.
        if (!Files.isReadable(locked)) {
            assertThat(failure(img("locked.png")))
                    .isEqualTo("img src \"locked.png\": is not a readable file");
        }
    }

    @Test
    void invalidLocalPathsFail() {
        for (String src : List.of("file:logo.png", "file://host/logo.png", "a\u0000b.png")) {
            assertThat(failure(img(src)))
                    .as(src)
                    .isEqualTo("img src \"" + src + "\": is not a valid path");
        }
    }

    @Test
    void missingBaseDirectoryFailsOnlyForLocalSources() throws InvalidMailException {
        Path missing = tempDir.resolve("missing");

        assertThatThrownBy(() -> HtmlImages.embed(html(img("logo.png")), missing))
                .isInstanceOf(InvalidMailException.class)
                .hasMessage("img src \"logo.png\": base directory not found");

        MailTemplate remote = html(img("cid:a") + img("https://example.com/a.png"));
        assertThat(HtmlImages.embed(remote, missing).template()).isSameAs(remote);
    }

    @Test
    void localTypeAgreesWithTheCore() throws Exception {
        Map<String, String> expected = Map.of("logo.png", "image/png", "photo.jpg", "image/jpeg",
                "photo.jpeg", "image/jpeg", "anim.gif", "image/gif", "LOGO.PNG", "image/png");
        for (Map.Entry<String, String> entry : expected.entrySet()) {
            Path file = image(entry.getKey(), 3);

            String helper = embed(img(entry.getKey())).images().get(0).contentType();
            String core = Mail.using("main").to("anna@example.org").subject("s").text("t")
                    .attach(new Attachment(null, null, file, null)).build()
                    .message().attachments().get(0).contentType();

            assertThat(helper).as(entry.getKey()).isEqualTo(core).isEqualTo(entry.getValue());
        }

        Path notes = Files.write(base.resolve("notes.txt"), new byte[] {'h', 'i'});
        String coreType = Mail.using("main").to("anna@example.org").subject("s").text("t")
                .attach(new Attachment(null, null, notes, null)).build()
                .message().attachments().get(0).contentType();
        assertThat(coreType).doesNotStartWith("image/");
        assertThat(failure(img("notes.txt"))).startsWith("img src \"notes.txt\": is not an image file");
    }

    @Test
    void cidPatternAgreesWithTheCore() throws Exception {
        image("a.png", 4);
        image("b.png", 5);
        List<Map.Entry<String, Set<String>>> cases = List.of(
                Map.entry("<img src=\"cid:logo\">", Set.of("logo")),
                Map.entry("<img src='CID:logo'>", Set.of("logo")),
                Map.entry("<div style=\"background:url(cid:logo)\">", Set.of("logo")),
                Map.entry("<td background=cid:logo>", Set.of("logo")),
                Map.entry("<p>Folic acid: 5 mg, xcid:logo</p>", Set.<String>of()),
                Map.entry("<a href=\"cid:\">x</a>", Set.<String>of()),
                Map.entry("<img src=\"cid:a\"><img src=\"cid:b\">", Set.of("a", "b")),
                Map.entry("<img src=\"cid:logo\"> and cid:logo again", Set.of("logo")),
                Map.entry("<p>(cid:a)</p>", Set.of("a")),
                Map.entry("<p>cid:a\"b</p>", Set.of("a")),
                Map.entry("<p>1cid:a and \u00e9cid:b</p>", Set.<String>of()));

        for (Map.Entry<String, Set<String>> entry : cases) {
            Set<String> found = new HashSet<>();
            Matcher matcher = HtmlImages.CID_REFERENCE.matcher(entry.getKey());
            while (matcher.find()) {
                if (!matcher.group(1).isEmpty()) {
                    found.add(matcher.group(1));
                }
            }
            assertThat(found).as(entry.getKey()).isEqualTo(entry.getValue());

            Mail.Builder builder = Mail.using("main").to("anna@example.org").subject("s")
                    .html(entry.getKey());
            for (String id : entry.getValue()) {
                builder.inline(base.resolve(id + ".png"), id);
            }
            builder.build();
        }
    }

    @Test
    void contentIdsFollowTheFileName() throws Exception {
        image("my logo.png", 2);
        image("Logo \u00fc.png", 3);
        image("x.tar.gif", 4);
        image(".png", 5);

        HtmlImages.Result result = embed(img("my logo.png") + img("Logo \u00fc.png")
                + img("x.tar.gif") + img(".png"));

        assertThat(ids(result)).containsExactly("my-logo", "Logo--", "x.tar", "image");
        assertThat(result.template().html()).isEqualTo(img("cid:my-logo") + img("cid:Logo--")
                + img("cid:x.tar") + img("cid:image"));
        Mail.Builder builder = Mail.using("main").to("anna@example.org").subject("s")
                .html(result.template().html());
        result.images().forEach(builder::attach);
        builder.build();
    }

    @Test
    void collisionsGetANumericSuffixInDocumentOrder() throws Exception {
        image("a/logo.png", 2);
        image("b/logo.png", 3);
        image("c/logo.gif", 4);

        HtmlImages.Result three = embed(img("a/logo.png") + img("b/logo.png") + img("c/logo.gif"));
        assertThat(ids(three)).containsExactly("logo", "logo-2", "logo-3");
        assertThat(three.template().html())
                .isEqualTo(img("cid:logo") + img("cid:logo-2") + img("cid:logo-3"));

        image("logo-2.png", 6);
        HtmlImages.Result handWritten = embed(img("cid:logo") + img("logo.png") + img("logo-2.png"));
        assertThat(handWritten.template().html())
                .isEqualTo(img("cid:logo") + img("cid:logo-2") + img("cid:logo-2-2"));
        assertThat(ids(handWritten)).containsExactly("logo-2", "logo-2-2");

        image("u/Logo.png", 7);
        image("l/logo.png", 8);
        HtmlImages.Result cases = embed(img("u/Logo.png") + img("l/logo.png"));
        assertThat(ids(cases)).containsExactly("Logo", "logo");
    }

    @Test
    void sameSourceGivesOneImage() throws Exception {
        Files.createDirectories(base.resolve("img"));
        image("b.gif", 2);
        image("a.png", 3);

        HtmlImages.Result same = embed(img("logo.png") + img("./logo.png") + img("img/../logo.png")
                + img("logo.png"));
        assertThat(ids(same)).containsExactly("logo");
        assertThat(same.template().html()).isEqualTo(img("cid:logo").repeat(4));

        HtmlImages.Result ordered = embed(img("b.gif") + img("a.png") + img("b.gif"));
        assertThat(ids(ordered)).containsExactly("b", "a");
        assertThat(ordered.template().html())
                .isEqualTo(img("cid:b") + img("cid:a") + img("cid:b"));
    }

    @Test
    void attachmentCarriesNameTypeAndRealPath() throws Exception {
        Files.createDirectories(base.resolve("img"));
        Path real = base.resolve("logo.png").toRealPath();

        HtmlImages.Result viaDots = embed(img("img/../logo.png"));
        assertThat(viaDots.images())
                .containsExactly(new Attachment("logo.png", "image/png", real, "logo"));

        assumeTrue(symlink(base.resolve("alias.png"), real), "no symbolic links on this file system");
        HtmlImages.Result aliasFirst = embed(img("alias.png") + img("logo.png"));
        assertThat(aliasFirst.images())
                .containsExactly(new Attachment("alias.png", "image/png", real, "alias"));
        assertThat(aliasFirst.template().html()).isEqualTo(img("cid:alias") + img("cid:alias"));

        HtmlImages.Result logoFirst = embed(img("logo.png") + img("alias.png"));
        assertThat(logoFirst.images())
                .containsExactly(new Attachment("logo.png", "image/png", real, "logo"));
        assertThat(logoFirst.template().html()).isEqualTo(img("cid:logo") + img("cid:logo"));
    }

    @Test
    void concurrentCallsGiveEqualResults() throws Exception {
        image("a/logo.png", 2);
        image("b/logo.png", 3);
        image("c.png", 4);
        image("d.gif", 5);
        MailTemplate template = html("<p>$$name$$</p>" + img("a/logo.png") + img("b/logo.png")
                + img("c.png") + img("d.gif") + img("logo.png") + img("a/logo.png"));
        HtmlImages.Result sequential = HtmlImages.embed(template, base);
        assertThat(sequential.images()).hasSize(5);

        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Future<HtmlImages.Result>> futures = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                futures.add(executor.submit(() -> HtmlImages.embed(template, base)));
            }
            for (Future<HtmlImages.Result> future : futures) {
                assertThat(future.get()).isEqualTo(sequential);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @Timeout(10)
    void hostileMarkupStaysFast() throws Exception {
        String unclosed = "<img ".repeat(20_000);
        assertThat(rewritten(unclosed + img("logo.png"))).isEqualTo(unclosed + img("cid:logo"));

        StringBuilder attributes = new StringBuilder("<img ");
        for (int i = 0; i < 200_000; i++) {
            attributes.append('a').append(i).append("=1 ");
        }
        assertThat(rewritten(attributes + "src=\"logo.png\">"))
                .isEqualTo(attributes + "src=\"cid:logo\">");

        MailTemplate huge = html(img("data:image/png;base64," + "A".repeat(8_000_000)));
        assertThat(HtmlImages.embed(huge, base).template()).isSameAs(huge);
    }

    @Test
    void nullArgumentsThrowNullPointerException() {
        MailTemplate template = html(img("logo.png"));

        assertThatThrownBy(() -> HtmlImages.embed(null, base)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> HtmlImages.embed(template, null))
                .isInstanceOf(NullPointerException.class);
    }
}
