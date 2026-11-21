package de.morisoft.morimail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class TemplateRendererTest {

    private static String html(String template, Map<String, ?> data) throws InvalidMailException {
        return TemplateRenderer.render(new MailTemplate("s", template, null), data).html();
    }

    private static String subject(String template, Map<String, ?> data)
            throws InvalidMailException {
        return TemplateRenderer.render(new MailTemplate(template, "h", null), data).subject();
    }

    @Test
    void adjacentPlaceholdersBothRender() throws Exception {
        assertThat(html("$$a$$$$b$$", Map.of("a", "A", "b", "B"))).isEqualTo("AB");
    }

    @Test
    void extraDollarBeforePlaceholderStaysLiteral() throws Exception {
        assertThat(html("$$$name$$", Map.of("name", "x"))).isEqualTo("$x");
    }

    @Test
    void unmatchedTrailingDelimiterStaysText() throws Exception {
        assertThat(html("$$a$$b$$", Map.of("a", "A"))).isEqualTo("Ab$$");
    }

    @ParameterizedTest
    @ValueSource(strings = {"$$$$", "$$ $$", "$$a b$$", "$$!$$", "lone $$", "$$ä$$"})
    void nonPlaceholderSequencesStayText(String template) throws Exception {
        assertThat(html(template, Map.of("a", "A", "b", "B"))).isEqualTo(template);
    }

    @Test
    void htmlValuesEscapeExactlyTheFiveCharacters() throws Exception {
        String value = "<a href=\"x\" title='y'>R&D</a>";

        assertThat(html("$$v$$", Map.of("v", value)))
                .isEqualTo("&lt;a href=&quot;x&quot; title=&#39;y&#39;&gt;R&amp;D&lt;/a&gt;");
        assertThat(html("$$v$$", Map.of("v", "Köln"))).isEqualTo("Köln");
    }

    @ParameterizedTest
    @ValueSource(strings = {"$1", "\\", "C:\\temp", "$5 Fund"})
    void specialCharacterValuesAreInsertedLiterally(String value) throws Exception {
        assertThat(html("[$$v$$]", Map.of("v", value))).isEqualTo("[" + value + "]");
    }

    @Test
    void rawPlaceholderInsertsValueUnescaped() throws Exception {
        assertThat(html("$$!name$$", Map.of("name", "<b>x</b>"))).isEqualTo("<b>x</b>");
    }

    @Test
    void insertedValuesAreNeverRescanned() throws Exception {
        assertThat(html("$$nested$$", Map.of("nested", "$$a$$", "a", "A"))).isEqualTo("$$a$$");
    }

    @Test
    void presentNullValueRendersAsNullText() throws Exception {
        Map<String, String> data = new HashMap<>();
        data.put("nul", null);

        assertThat(html("$$nul$$", data)).isEqualTo("null");
    }

    @Test
    void emptyValueInsertsNothing() throws Exception {
        assertThat(html("[$$e$$]", Map.of("e", ""))).isEqualTo("[]");
    }

    @Test
    void missingHtmlFieldNamesPartAndPlaceholder() {
        assertThatThrownBy(() -> html("price $$20$$", Map.of()))
                .isInstanceOf(InvalidMailException.class)
                .hasMessageContaining("$$20$$")
                .hasMessageContaining("html");
    }

    @Test
    void missingSubjectFieldNeverEchoesOtherValues() {
        assertThatThrownBy(() -> subject("Hi $$absent$$", Map.of("other", "SentinelValue123")))
                .isInstanceOf(InvalidMailException.class)
                .hasMessageContaining("subject")
                .hasMessageContaining("$$absent$$")
                .message().doesNotContain("SentinelValue123");
    }

    static Stream<Arguments> subjectLineBreaks() {
        return Stream.of(
                Arguments.of("Hi $$multi$$ end", "x\r\ny\nz", "Hi x y z end"),
                Arguments.of("Hi $$!multi$$ end", "x\r\ny\nz", "Hi x y z end"),
                Arguments.of("[$$multi$$]", "\n\n", "[  ]"),
                Arguments.of("[$$multi$$]", "\r\n", "[ ]"),
                Arguments.of("[$$multi$$]", "\r", "[ ]"),
                Arguments.of("[$$multi$$]", "\u0085", "[ ]"),
                Arguments.of("[$$multi$$]", "\u2028", "[ ]"),
                Arguments.of("[$$multi$$]", "\u2029", "[ ]"));
    }

    @ParameterizedTest
    @MethodSource("subjectLineBreaks")
    void subjectLineBreaksBecomeSpaces(String template, String value, String expected)
            throws Exception {
        assertThat(subject(template, Map.of("multi", value))).isEqualTo(expected);
    }

    @Test
    void textValuesAreInsertedUnchanged() throws Exception {
        String value = "a\r\nb <&> $1";
        MailTemplate rendered = TemplateRenderer.render(
                new MailTemplate("s", "h", "$$t$$"), Map.of("t", value));

        assertThat(rendered.text()).isEqualTo(value);
    }

    @Test
    void templatePartShapesArePreserved() throws Exception {
        MailTemplate empty = TemplateRenderer.render(new MailTemplate("", "", null), Map.of());
        MailTemplate plain = TemplateRenderer.render(
                new MailTemplate("Subject", "<p>x</p>", "text"), Map.of());

        assertThat(empty.subject()).isEmpty();
        assertThat(empty.html()).isEmpty();
        assertThat(empty.text()).isNull();
        assertThat(plain).isEqualTo(new MailTemplate("Subject", "<p>x</p>", "text"));
    }
}
