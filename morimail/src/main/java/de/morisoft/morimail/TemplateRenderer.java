package de.morisoft.morimail;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fills the placeholders of a {@link MailTemplate} from a data map. Stateless; safe for
 * concurrent use.
 *
 * <p>The loop appends text and values itself. No data value is ever passed to a regex
 * replacement method, so {@code $} and a backslash in a value stay literal.
 */
final class TemplateRenderer {

    private static final Pattern PLACEHOLDER =
            Pattern.compile("\\$\\$(!?)([A-Za-z0-9_.-]+)\\$\\$");
    private static final Pattern LINE_BREAK = Pattern.compile("\\R");

    private enum Part {
        SUBJECT("subject"), HTML("html"), TEXT("text");

        private final String label;

        Part(String label) {
            this.label = label;
        }
    }

    private TemplateRenderer() {
    }

    /**
     * Renders the three parts. A {@code null} part stays {@code null}.
     *
     * @param template the template
     * @param data the values by placeholder name
     * @return the rendered template
     * @throws InvalidMailException if a placeholder has no key in the data map
     */
    static MailTemplate render(MailTemplate template, Map<String, ?> data)
            throws InvalidMailException {
        String subject = render(Part.SUBJECT, template.subject(), data);
        String html = render(Part.HTML, template.html(), data);
        String text = render(Part.TEXT, template.text(), data);
        return new MailTemplate(subject, html, text);
    }

    private static String render(Part part, String source, Map<String, ?> data)
            throws InvalidMailException {
        if (source == null) {
            return null;
        }
        Matcher matcher = PLACEHOLDER.matcher(source);
        StringBuilder out = new StringBuilder(source.length() + 32);
        int last = 0;
        while (matcher.find()) {
            out.append(source, last, matcher.start());
            String name = matcher.group(2);
            if (!data.containsKey(name)) {
                throw new InvalidMailException("Missing data field '" + name
                        + "' for placeholder " + matcher.group() + " in the " + part.label
                        + " template");
            }
            String value = String.valueOf(data.get(name));
            boolean raw = !matcher.group(1).isEmpty();
            switch (part) {
                case HTML -> out.append(raw ? value : escapeHtml(value));
                case SUBJECT -> out.append(LINE_BREAK.matcher(value).replaceAll(" "));
                case TEXT -> out.append(value);
            }
            last = matcher.end();
        }
        return out.append(source, last, source.length()).toString();
    }

    private static String escapeHtml(String value) {
        StringBuilder out = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&#39;");
                default -> out.append(c);
            }
        }
        return out.toString();
    }
}
