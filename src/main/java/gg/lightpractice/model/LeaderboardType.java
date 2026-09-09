package gg.lightpractice.model;

import java.util.Locale;

/**
 * Leaderboards that can be queried. Each type knows the MongoDB field it sorts on so the repository
 * can build an indexed query instead of loading profiles into memory.
 */
public enum LeaderboardType {

    WINS("statistics.wins"),
    LOSSES("statistics.losses"),
    KILLS("statistics.kills"),
    DEATHS("statistics.deaths"),
    WINSTREAK("statistics.winstreak"),
    BEST_WINSTREAK("statistics.best_winstreak"),
    GAMES_PLAYED("statistics.games_played"),
    EXPERIENCE("experience"),
    COINS("coins"),
    LEVEL("level"),
    /** Rating is stored per kit, the field is completed by {@link #field(String)}. */
    ELO("elo");

    private final String field;

    LeaderboardType(String field) {
        this.field = field;
    }

    public String field() {
        return field;
    }

    /** Full document field for this type, kit aware for ratings. */
    public String field(String kitId) {
        if (this == ELO) {
            return "elo." + (kitId == null ? "global" : kitId);
        }
        return field;
    }

    public boolean isKitSpecific() {
        return this == ELO;
    }

    public String messageKey() {
        return "leaderboard.types." + name().toLowerCase(Locale.ROOT);
    }

    public static LeaderboardType parse(String value) {
        if (value == null) {
            return null;
        }
        String key = value.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        if ("RATING".equals(key) || "RANKED".equals(key)) {
            return ELO;
        }
        if ("STREAK".equals(key)) {
            return WINSTREAK;
        }
        if ("XP".equals(key)) {
            return EXPERIENCE;
        }
        if ("GAMES".equals(key)) {
            return GAMES_PLAYED;
        }
        try {
            return valueOf(key);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
