package gg.lightpractice.util;

/**
 * Debug channels. Every subsystem logs through a category so operators can enable exactly what they
 * need instead of turning on a global firehose.
 */
public enum DebugCategory {

    /** Enables every other category. */
    ALL,
    /** Loading, saving and repairing configuration files. */
    CONFIG,
    DATABASE,
    PROFILE,
    PLAYER,
    MATCH,
    MATCHMAKING,
    QUEUE,
    DUEL,
    ARENA,
    SCHEMATIC,
    KIT,
    PARTY,
    TOURNAMENT,
    EVENT,
    BOT,
    COSMETIC,
    SPECTATOR,
    SCOREBOARD,
    TAB,
    GUI,
    COMMAND,
    INTEGRATION,
    COMBAT,
    REWARD,
    LEADERBOARD,
    STATISTICS,
    API,
    SHUTDOWN;

    /** Parses a category name, returning {@code null} when it is unknown. */
    public static DebugCategory parse(String name) {
        if (name == null) {
            return null;
        }
        try {
            return valueOf(name.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
