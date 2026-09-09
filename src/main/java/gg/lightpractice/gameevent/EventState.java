package gg.lightpractice.gameevent;

import java.util.Locale;

/** Lifecycle of a hosted event session. */
public enum EventState {

    /** Entries are open. */
    GATHERING,
    /** Entries closed, the start countdown runs. */
    STARTING,
    /** The event is being played. */
    RUNNING,
    /** A winner was decided. */
    FINISHED,
    /** The event was stopped early. */
    CANCELLED;

    public boolean live() {
        return this == GATHERING || this == STARTING || this == RUNNING;
    }

    public boolean joinable() {
        return this == GATHERING;
    }

    public String configKey() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static EventState parse(String value) {
        if (value == null) {
            return null;
        }
        String key = value.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        for (EventState state : values()) {
            if (state.name().equals(key)) {
                return state;
            }
        }
        if ("OPEN".equals(key)) {
            return GATHERING;
        }
        if ("ACTIVE".equals(key) || "PLAYING".equals(key)) {
            return RUNNING;
        }
        if ("ENDED".equals(key)) {
            return FINISHED;
        }
        if ("STOPPED".equals(key)) {
            return CANCELLED;
        }
        return null;
    }
}
