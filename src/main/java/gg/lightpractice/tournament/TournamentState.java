package gg.lightpractice.tournament;

import java.util.Locale;

/** Lifecycle of a tournament, from gathering entries to a finished or cancelled bracket. */
public enum TournamentState {

    /** Entries are open, the bracket has not been built. */
    GATHERING,
    /** Entries closed, the start countdown is running. */
    STARTING,
    /** Rounds are being played. */
    RUNNING,
    /** A winner was decided. */
    FINISHED,
    /** The tournament was stopped before a winner existed. */
    CANCELLED;

    /** True while the tournament still occupies the single active slot. */
    public boolean live() {
        return this == GATHERING || this == STARTING || this == RUNNING;
    }

    public boolean joinable() {
        return this == GATHERING;
    }

    public boolean left() {
        return this == STARTING || this == RUNNING;
    }

    public String configKey() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static TournamentState parse(String value) {
        if (value == null) {
            return null;
        }
        String key = value.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        for (TournamentState state : values()) {
            if (state.name().equals(key)) {
                return state;
            }
        }
        if ("OPEN".equals(key) || "WAITING".equals(key)) {
            return GATHERING;
        }
        if ("ACTIVE".equals(key) || "PLAYING".equals(key)) {
            return RUNNING;
        }
        if ("ENDED".equals(key) || "COMPLETE".equals(key)) {
            return FINISHED;
        }
        if ("STOPPED".equals(key)) {
            return CANCELLED;
        }
        return null;
    }
}
