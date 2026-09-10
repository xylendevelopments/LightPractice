package gg.lightpractice.config;

import java.util.ArrayList;
import java.util.List;

/**
 * Replacement token for configurable messages. Tokens are written as {@code {name}} in
 * {@code messages.yml} and substituted here, which keeps every user facing string configurable
 * without a template engine.
 */
public final class Placeholder {

    private final String key;
    private final String value;

    private Placeholder(String key, String value) {
        this.key = key;
        this.value = value;
    }

    public static Placeholder of(String key, Object value) {
        return new Placeholder(key, value == null ? "" : String.valueOf(value));
    }

    public String key() {
        return key;
    }

    public String value() {
        return value;
    }

    public static String apply(String text, Placeholder... placeholders) {
        if (text == null || text.isEmpty() || placeholders == null || placeholders.length == 0) {
            return text;
        }
        String result = text;
        for (Placeholder placeholder : placeholders) {
            if (placeholder == null) {
                continue;
            }
            result = result.replace("{" + placeholder.key() + "}", placeholder.value());
        }
        return result;
    }

    public static String apply(String text, List<Placeholder> placeholders) {
        if (text == null || text.isEmpty() || placeholders == null || placeholders.isEmpty()) {
            return text;
        }
        return apply(text, placeholders.toArray(new Placeholder[0]));
    }

    public static List<String> apply(List<String> lines, Placeholder... placeholders) {
        List<String> result = new ArrayList<String>();
        if (lines == null) {
            return result;
        }
        for (String line : lines) {
            result.add(apply(line, placeholders));
        }
        return result;
    }

    /** Merges two placeholder groups, the second one winning on duplicate keys. */
    public static Placeholder[] merge(Placeholder[] base, Placeholder... extra) {
        if (extra == null || extra.length == 0) {
            return base == null ? new Placeholder[0] : base;
        }
        if (base == null || base.length == 0) {
            return extra;
        }
        Placeholder[] merged = new Placeholder[base.length + extra.length];
        System.arraycopy(base, 0, merged, 0, base.length);
        System.arraycopy(extra, 0, merged, base.length, extra.length);
        return merged;
    }
}
