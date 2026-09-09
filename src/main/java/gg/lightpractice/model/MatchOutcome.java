package gg.lightpractice.model;

import java.util.Locale;

/** Result of a match from the point of view of a single participant. */
public enum MatchOutcome {

    WIN,
    LOSS,
    DRAW,
    FORFEIT;

    public boolean counted() {
        return this == WIN || this == LOSS || this == DRAW;
    }

    public static MatchOutcome parse(String value) {
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
