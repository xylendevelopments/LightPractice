package gg.lightpractice.party;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

/** One member of a party, including offline members so a party survives a reconnect. */
public final class PartyMember {

    private final UUID uuid;
    private final long joinedAt = System.currentTimeMillis();
    private String name;
    private PartyRank rank;

    public PartyMember(UUID uuid, String name, PartyRank rank) {
        this.uuid = uuid;
        this.name = name == null ? "unknown" : name;
        this.rank = rank == null ? PartyRank.MEMBER : rank;
    }

    public UUID uuid() {
        return uuid;
    }

    public String name() {
        Player player = player();
        if (player != null) {
            name = player.getName();
        }
        return name;
    }

    public void name(String name) {
        if (name != null && !name.trim().isEmpty()) {
            this.name = name;
        }
    }

    public PartyRank rank() {
        return rank;
    }

    public void rank(PartyRank rank) {
        this.rank = rank == null ? PartyRank.MEMBER : rank;
    }

    public boolean isLeader() {
        return rank == PartyRank.LEADER;
    }

    public Player player() {
        return uuid == null ? null : Bukkit.getPlayer(uuid);
    }

    public boolean isOnline() {
        Player player = player();
        return player != null && player.isOnline();
    }

    public long joinedAt() {
        return joinedAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PartyMember)) {
            return false;
        }
        return uuid == null ? ((PartyMember) other).uuid == null : uuid.equals(((PartyMember) other).uuid);
    }

    @Override
    public int hashCode() {
        return uuid == null ? name.hashCode() : uuid.hashCode();
    }

    @Override
    public String toString() {
        return name + "(" + rank.name() + ')';
    }
}
