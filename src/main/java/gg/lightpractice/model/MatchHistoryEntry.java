package gg.lightpractice.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable snapshot of a finished match kept on the profile (recent entries) and in MongoDB
 * (permanent history).
 */
public final class MatchHistoryEntry {

    private final String matchId;
    private final long timestamp;
    private final String kitId;
    private final String kitName;
    private final String arenaName;
    private final String matchType;
    private final long durationMillis;
    private final List<String> opponents;
    private final MatchOutcome outcome;
    private final boolean ranked;
    private final int eloBefore;
    private final int eloAfter;
    private final int kills;
    private final int deaths;

    public MatchHistoryEntry(String matchId, long timestamp, String kitId, String kitName, String arenaName,
                             String matchType, long durationMillis, List<String> opponents, MatchOutcome outcome,
                             boolean ranked, int eloBefore, int eloAfter, int kills, int deaths) {
        this.matchId = matchId;
        this.timestamp = timestamp;
        this.kitId = kitId;
        this.kitName = kitName;
        this.arenaName = arenaName;
        this.matchType = matchType;
        this.durationMillis = Math.max(0L, durationMillis);
        this.opponents = opponents == null ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<String>(opponents));
        this.outcome = outcome == null ? MatchOutcome.LOSS : outcome;
        this.ranked = ranked;
        this.eloBefore = eloBefore;
        this.eloAfter = eloAfter;
        this.kills = Math.max(0, kills);
        this.deaths = Math.max(0, deaths);
    }

    public String matchId() {
        return matchId;
    }

    public long timestamp() {
        return timestamp;
    }

    public String kitId() {
        return kitId;
    }

    public String kitName() {
        return kitName;
    }

    public String arenaName() {
        return arenaName;
    }

    public String matchType() {
        return matchType;
    }

    public long durationMillis() {
        return durationMillis;
    }

    public List<String> opponents() {
        return opponents;
    }

    public MatchOutcome outcome() {
        return outcome;
    }

    public boolean ranked() {
        return ranked;
    }

    public int eloBefore() {
        return eloBefore;
    }

    public int eloAfter() {
        return eloAfter;
    }

    public int eloDelta() {
        return eloAfter - eloBefore;
    }

    public int kills() {
        return kills;
    }

    public int deaths() {
        return deaths;
    }

    @Override
    public String toString() {
        return outcome + " " + kitName + " vs " + opponents;
    }
}
