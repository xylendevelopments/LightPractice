package gg.lightpractice.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Persisted tournament snapshot. Written while a tournament runs so an unfinished bracket can be
 * reported and cleaned up after a restart instead of silently disappearing.
 */
public final class TournamentRecord {

    private final String id;
    private final String kitId;
    private final UUID host;
    private final String hostName;
    private final String state;
    private final long startedAt;
    private final long updatedAt;
    private final int size;
    private final List<UUID> participants;
    private final UUID winner;

    public TournamentRecord(String id, String kitId, UUID host, String hostName, String state, long startedAt,
                            long updatedAt, int size, List<UUID> participants, UUID winner) {
        this.id = id;
        this.kitId = kitId;
        this.host = host;
        this.hostName = hostName;
        this.state = state;
        this.startedAt = startedAt;
        this.updatedAt = updatedAt;
        this.size = size;
        this.participants = participants == null ? Collections.<UUID>emptyList()
                : Collections.unmodifiableList(new ArrayList<UUID>(participants));
        this.winner = winner;
    }

    public String id() {
        return id;
    }

    public String kitId() {
        return kitId;
    }

    public UUID host() {
        return host;
    }

    public String hostName() {
        return hostName;
    }

    public String state() {
        return state;
    }

    public long startedAt() {
        return startedAt;
    }

    public long updatedAt() {
        return updatedAt;
    }

    public int size() {
        return size;
    }

    public List<UUID> participants() {
        return participants;
    }

    public UUID winner() {
        return winner;
    }

    public boolean finished() {
        return "FINISHED".equals(state) || "CANCELLED".equals(state);
    }
}
