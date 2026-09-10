package gg.lightpractice.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Full record of a finished match as stored in MongoDB. One document per match holds every
 * participant, which keeps history queries to a single indexed lookup instead of joining data that
 * lives on separate profiles.
 */
public final class MatchRecord {

    private final String matchId;
    private final long timestamp;
    private final String kitId;
    private final String kitName;
    private final String arenaName;
    private final String matchType;
    private final String endCause;
    private final boolean ranked;
    private final long durationMillis;
    private final String winningTeam;
    private final List<PlayerRecord> players;
    private final List<String> spectators;

    public MatchRecord(String matchId, long timestamp, String kitId, String kitName, String arenaName,
                       String matchType, String endCause, boolean ranked, long durationMillis,
                       String winningTeam, List<PlayerRecord> players, List<String> spectators) {
        this.matchId = matchId;
        this.timestamp = timestamp;
        this.kitId = kitId;
        this.kitName = kitName;
        this.arenaName = arenaName;
        this.matchType = matchType;
        this.endCause = endCause;
        this.ranked = ranked;
        this.durationMillis = Math.max(0L, durationMillis);
        this.winningTeam = winningTeam;
        this.players = players == null ? Collections.<PlayerRecord>emptyList()
                : Collections.unmodifiableList(new ArrayList<PlayerRecord>(players));
        this.spectators = spectators == null ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<String>(spectators));
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

    public String endCause() {
        return endCause;
    }

    public boolean ranked() {
        return ranked;
    }

    public long durationMillis() {
        return durationMillis;
    }

    public String winningTeam() {
        return winningTeam;
    }

    public List<PlayerRecord> players() {
        return players;
    }

    public List<String> spectators() {
        return spectators;
    }

    public List<UUID> participantIds() {
        List<UUID> ids = new ArrayList<UUID>(players.size());
        for (PlayerRecord record : players) {
            ids.add(record.uuid());
        }
        return ids;
    }

    public PlayerRecord record(UUID uuid) {
        if (uuid == null) {
            return null;
        }
        for (PlayerRecord record : players) {
            if (uuid.equals(record.uuid())) {
                return record;
            }
        }
        return null;
    }

    /** Per participant results inside a stored match. */
    public static final class PlayerRecord {

        private final UUID uuid;
        private final String name;
        private final String team;
        private final MatchOutcome outcome;
        private final int eloBefore;
        private final int eloAfter;
        private final int kills;
        private final int deaths;
        private final long damageDealt;
        private final int longestCombo;

        public PlayerRecord(UUID uuid, String name, String team, MatchOutcome outcome, int eloBefore,
                            int eloAfter, int kills, int deaths, long damageDealt, int longestCombo) {
            this.uuid = uuid;
            this.name = name;
            this.team = team;
            this.outcome = outcome == null ? MatchOutcome.LOSS : outcome;
            this.eloBefore = eloBefore;
            this.eloAfter = eloAfter;
            this.kills = Math.max(0, kills);
            this.deaths = Math.max(0, deaths);
            this.damageDealt = Math.max(0L, damageDealt);
            this.longestCombo = Math.max(0, longestCombo);
        }

        public UUID uuid() {
            return uuid;
        }

        public String name() {
            return name;
        }

        public String team() {
            return team;
        }

        public MatchOutcome outcome() {
            return outcome;
        }

        public int eloBefore() {
            return eloBefore;
        }

        public int eloAfter() {
            return eloAfter;
        }

        public int kills() {
            return kills;
        }

        public int deaths() {
            return deaths;
        }

        public long damageDealt() {
            return damageDealt;
        }

        public int longestCombo() {
            return longestCombo;
        }
    }
}
