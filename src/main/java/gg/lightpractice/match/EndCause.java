package gg.lightpractice.match;

import java.util.Locale;

/** Why a match stopped. Whether the result counts towards statistics depends on the cause. */
public enum EndCause {

    /** Every opposing participant was eliminated. */
    KNOCKOUT,
    /** A point or hit based rule decided the winner, such as boxing. */
    POINTS,
    /** The configured time limit passed. */
    TIMEOUT,
    /** A participant left or disconnected. */
    FORFEIT,
    /** A participant broke the rules of the mode, such as leaving a sumo ring illegally. */
    DISQUALIFIED,
    /** A participant surrendered through a command. */
    SURRENDER,
    /** The server shut down or the plugin was disabled mid match. */
    SHUTDOWN,
    /** The match was dropped by an administrator or by the event system. */
    ABANDONED;

    /**
     * Whether the result changes ratings, records and streaks.
     *
     * <p>Leaving or surrendering counts on purpose so a match cannot be dodged by disconnecting. Only a
     * shutdown or an administrative drop is ignored.</p>
     */
    public boolean counted() {
        return this != SHUTDOWN && this != ABANDONED;
    }

    public String messageKey() {
        return "match.end-causes." + name().toLowerCase(Locale.ROOT);
    }

    public static EndCause parse(String value) {
        if (value == null) {
            return null;
        }
        String key = value.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        for (EndCause cause : values()) {
            if (cause.name().equals(key)) {
                return cause;
            }
        }
        if ("QUIT".equals(key) || "LEAVE".equals(key) || "DISCONNECT".equals(key)) {
            return FORFEIT;
        }
        if ("TIME".equals(key) || "LIMIT".equals(key)) {
            return TIMEOUT;
        }
        return null;
    }
}
