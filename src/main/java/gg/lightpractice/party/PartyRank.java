package gg.lightpractice.party;

import java.util.Locale;

/** Role inside a party; only leaders may invite, kick, promote or start matches. */
public enum PartyRank {

    LEADER,
    MEMBER;

    public boolean isLeader() {
        return this == LEADER;
    }

    public boolean atLeast(PartyRank other) {
        if (other == null) {
            return true;
        }
        return ordinal() <= other.ordinal();
    }

    public String displayName() {
        return this == LEADER ? "Leader" : "Member";
    }

    public static PartyRank parse(String value) {
        if (value == null) {
            return MEMBER;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return MEMBER;
        }
    }
}
