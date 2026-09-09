package gg.lightpractice.match;

import java.util.Locale;

/** Shape of a match: how many sides fight and whether ratings are involved. */
public enum MatchType {

    /** One versus one, the standard queue and duel format. */
    SOLO,
    /** Two or more teams of equal size. */
    TEAMS,
    /** Every participant fights alone. */
    FFA,
    /** Started from a party, team based or free for all. */
    PARTY,
    /** Player against a practice bot. */
    BOT,
    /** A round inside a tournament bracket. */
    TOURNAMENT,
    /** A round inside a running event. */
    EVENT;

    public boolean isTeamBased() {
        return this == TEAMS || this == PARTY;
    }

    public boolean everyoneAlone() {
        return this == FFA;
    }

    /** Only queue and duel style matches move ratings. */
    public boolean rankedCapable() {
        return this == SOLO || this == TEAMS;
    }

    /** Whether results of this type are written to statistics. */
    public boolean counted() {
        return this != EVENT;
    }

    public boolean allowsSpectators() {
        return true;
    }

    public String messageKey() {
        return "match.types." + name().toLowerCase(Locale.ROOT);
    }

    public static MatchType parse(String value) {
        if (value == null) {
            return null;
        }
        String key = value.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        for (MatchType type : values()) {
            if (type.name().equals(key)) {
                return type;
            }
        }
        if ("1V1".equals(key) || "DUEL".equals(key) || "SOLO".equals(key)) {
            return SOLO;
        }
        if ("TEAM".equals(key)) {
            return TEAMS;
        }
        if ("FREE_FOR_ALL".equals(key)) {
            return FFA;
        }
        if ("NPC".equals(key)) {
            return BOT;
        }
        return null;
    }
}
