package gg.lightpractice.bot;

import java.util.Locale;

/** Lifecycle of a spawned practice bot. */
public enum BotState {

    /** The body is being created, the match is not live yet. */
    SPAWNING,
    /** Fighting. */
    ACTIVE,
    /** Health reached zero, the body is about to be removed. */
    DEAD,
    /** Removed from the world and from every registry. */
    REMOVED;

    public boolean alive() {
        return this == SPAWNING || this == ACTIVE;
    }

    public String configKey() {
        return name().toLowerCase(Locale.ROOT);
    }
}
