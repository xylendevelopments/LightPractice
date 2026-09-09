package gg.lightpractice.model;

import java.util.Locale;

/** Chat routing for the practice lobby, parties and live matches. */
public enum ChatChannel {

    PUBLIC,
    PARTY,
    MATCH;

    public String prefix() {
        switch (this) {
            case PARTY:
                return "(Party) ";
            case MATCH:
                return "(Match) ";
            default:
                return "";
        }
    }

    public String trigger() {
        switch (this) {
            case PARTY:
                return "@";
            case MATCH:
                return "!";
            default:
                return "";
        }
    }

    public static ChatChannel parse(String value) {
        if (value == null) {
            return null;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
