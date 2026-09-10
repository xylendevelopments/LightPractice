package gg.lightpractice.scoreboard;

import java.util.Locale;

/**
 * Situation a scoreboard is rendered for.
 *
 * <p>Each context has its own line list in {@code scoreboard.yml}, so a queued player sees their position
 * while a fighting player sees the kit, the clock and their opponents.</p>
 */
public enum BoardContext {

    /** Standing in a lobby with nothing running. */
    LOBBY,
    /** Waiting in a queue. */
    QUEUE,
    /** Inside a live match. */
    MATCH,
    /** Watching a match as a spectator. */
    SPECTATING;

    public String configKey() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static BoardContext parse(String value) {
        if (value == null) {
            return null;
        }
        String key = value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        for (BoardContext context : values()) {
            if (context.name().equals(key)) {
                return context;
            }
        }
        return null;
    }
}
