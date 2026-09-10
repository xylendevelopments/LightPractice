package gg.lightpractice.model;

import java.util.UUID;

/** One cached row of a leaderboard. */
public final class LeaderboardEntry {

    private final UUID uuid;
    private final String name;
    private final long value;
    private final int position;

    public LeaderboardEntry(UUID uuid, String name, long value, int position) {
        this.uuid = uuid;
        this.name = name;
        this.value = value;
        this.position = position;
    }

    public UUID uuid() {
        return uuid;
    }

    public String name() {
        return name;
    }

    public long value() {
        return value;
    }

    public int position() {
        return position;
    }

    @Override
    public String toString() {
        return position + ". " + name + " (" + value + ")";
    }
}
