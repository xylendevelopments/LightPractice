package gg.lightpractice.arena;

import java.util.Locale;

/**
 * Arena flavours.
 *
 * <p>{@code STANDARD} arenas live in a shared arena world and are reset from a schematic between
 * matches. {@code STANDALONE} arenas own their world (or a far away region of it) and may be configured
 * to host several matches at once, which is how duplicate arenas are supported without cloning data.</p>
 */
public enum ArenaType {

    STANDARD,
    STANDALONE;

    public boolean standalone() {
        return this == STANDALONE;
    }

    public static ArenaType parse(String value) {
        if (value == null) {
            return null;
        }
        String key = value.trim().toUpperCase(Locale.ROOT);
        if ("SOLO".equals(key) || "SHARED".equals(key) || "NORMAL".equals(key)) {
            return STANDARD;
        }
        if ("OWN".equals(key) || "DEDICATED".equals(key) || "DUPLICATE".equals(key) || "DUPLICATABLE".equals(key)) {
            return STANDALONE;
        }
        try {
            return valueOf(key);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
