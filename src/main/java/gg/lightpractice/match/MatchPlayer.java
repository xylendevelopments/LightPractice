package gg.lightpractice.match;

import gg.lightpractice.model.MatchOutcome;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * One participant of a match and everything counted about them: kills, deaths, damage, combos and the
 * rating movement once the result is known.
 *
 * <p>The Bukkit player reference is resolved on demand so a participant that disconnects keeps its
 * statistics and can be restored on reconnect.</p>
 */
public final class MatchPlayer {

    private final UUID uuid;
    private final String name;
    private final long joinedAt = System.currentTimeMillis();
    private MatchTeam team;
    private int kills;
    private int deaths;
    private long damage;
    private int currentCombo;
    private int longestCombo;
    private long lastHitAt;
    private MatchOutcome outcome;
    private boolean eliminated;
    private int eloBefore;
    private int eloAfter;
    private int respawns;
    private UUID lastAttacker;
    private Location respawnPoint;

    public MatchPlayer(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name == null ? "unknown" : name;
    }

    public UUID uuid() {
        return uuid;
    }

    public String name() {
        return name;
    }

    /** Current Bukkit player, {@code null} while the participant is offline. */
    public Player player() {
        if (uuid == null) {
            return null;
        }
        Player player = Bukkit.getPlayer(uuid);
        return player != null && player.isOnline() ? player : null;
    }

    public boolean isOnline() {
        return player() != null;
    }

    public MatchTeam team() {
        return team;
    }

    public void team(MatchTeam team) {
        this.team = team;
    }

    public int kills() {
        return kills;
    }

    public void addKill(int amount) {
        this.kills = Math.max(0, kills + amount);
    }

    public int deaths() {
        return deaths;
    }

    public void addDeath(int amount) {
        this.deaths = Math.max(0, deaths + amount);
    }

    public long damage() {
        return damage;
    }

    public void addDamage(double amount) {
        if (amount > 0.0D) {
            this.damage += (long) amount;
        }
    }

    public int currentCombo() {
        return currentCombo;
    }

    public int longestCombo() {
        return longestCombo;
    }

    /** Registers a landed hit inside the combo window, extending or restarting the chain. */
    public void registerHit(long comboWindowMillis) {
        long now = System.currentTimeMillis();
        if (currentCombo > 0 && now - lastHitAt > Math.max(0L, comboWindowMillis)) {
            currentCombo = 0;
        }
        currentCombo++;
        lastHitAt = now;
        if (currentCombo > longestCombo) {
            longestCombo = currentCombo;
        }
    }

    public void resetCombo() {
        currentCombo = 0;
    }

    public long lastHitAt() {
        return lastHitAt;
    }

    public MatchOutcome outcome() {
        return outcome;
    }

    public void outcome(MatchOutcome outcome) {
        this.outcome = outcome;
    }

    public boolean eliminated() {
        return eliminated;
    }

    public void eliminated(boolean eliminated) {
        this.eliminated = eliminated;
    }

    public int eloBefore() {
        return eloBefore;
    }

    public void eloBefore(int eloBefore) {
        this.eloBefore = eloBefore;
    }

    public int eloAfter() {
        return eloAfter;
    }

    public void eloAfter(int eloAfter) {
        this.eloAfter = eloAfter;
    }

    public int respawns() {
        return respawns;
    }

    public void addRespawn() {
        respawns++;
    }

    public UUID lastAttacker() {
        return lastAttacker;
    }

    public void lastAttacker(UUID lastAttacker) {
        this.lastAttacker = lastAttacker;
    }

    public Location respawnPoint() {
        return respawnPoint;
    }

    public void respawnPoint(Location respawnPoint) {
        this.respawnPoint = respawnPoint;
    }

    public long joinedAt() {
        return joinedAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof MatchPlayer)) {
            return false;
        }
        MatchPlayer participant = (MatchPlayer) other;
        return uuid == null ? participant.uuid == null : uuid.equals(participant.uuid);
    }

    @Override
    public int hashCode() {
        return uuid == null ? name.hashCode() : uuid.hashCode();
    }

    @Override
    public String toString() {
        return "MatchPlayer{" + name + ", team=" + (team == null ? "none" : team.name())
                + ", kills=" + kills + ", deaths=" + deaths + '}';
    }
}
