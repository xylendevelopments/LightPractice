package gg.lightpractice.kit.rule;

import gg.lightpractice.match.Match;
import gg.lightpractice.match.MatchTeam;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Grants a number of lives and respawns players at their team spawn.
 *
 * <pre>
 * respawn:
 *   lives: 3
 *   delay-ticks: 40
 *   reapply-kit: true
 * </pre>
 */
public final class RespawnRule implements KitRule {

    private final Map<UUID, Map<UUID, Integer>> lives = new ConcurrentHashMap<UUID, Map<UUID, Integer>>();
    private int configuredLives = 3;
    private int delayTicks = 40;
    private boolean reapplyKit = true;

    @Override
    public String id() {
        return "respawn";
    }

    @Override
    public void load(KitRuleOptions options) {
        KitRuleOptions values = options == null ? KitRuleOptions.disabled() : options;
        this.configuredLives = values.integer("lives", 3);
        this.delayTicks = Math.max(0, values.integer("delay-ticks", 40));
        this.reapplyKit = values.bool("reapply-kit", true);
    }

    @Override
    public boolean allowRespawn() {
        return configuredLives != 0;
    }

    @Override
    public int lives() {
        return configuredLives;
    }

    @Override
    public void onPlayerEnter(Match match, Player player) {
        if (match == null || player == null) {
            return;
        }
        Map<UUID, Integer> remaining = lives.get(match.id());
        if (remaining == null) {
            remaining = new ConcurrentHashMap<UUID, Integer>();
            lives.put(match.id(), remaining);
        }
        remaining.put(player.getUniqueId(), configuredLives < 0 ? -1 : configuredLives);
    }

    @Override
    public boolean canRespawn(Match match, Player player) {
        if (!allowRespawn() || match == null || player == null) {
            return false;
        }
        Map<UUID, Integer> remaining = lives.get(match.id());
        if (remaining == null) {
            return configuredLives < 0;
        }
        Integer left = remaining.get(player.getUniqueId());
        return left == null ? configuredLives < 0 : left != 0;
    }

    @Override
    public void onDeath(Match match, Player player) {
        if (match == null || player == null) {
            return;
        }
        Map<UUID, Integer> remaining = lives.get(match.id());
        if (remaining == null) {
            return;
        }
        Integer left = remaining.get(player.getUniqueId());
        if (left == null || left < 0) {
            return;
        }
        remaining.put(player.getUniqueId(), Math.max(0, left - 1));
    }

    @Override
    public Location respawnLocation(Match match, Player player) {
        if (match == null || player == null) {
            return null;
        }
        MatchTeam team = match.teamOf(player);
        if (team == null) {
            return null;
        }
        Location spawn = team.spawn();
        return spawn == null ? null : spawn.clone().add(0.5D, 0.0D, 0.5D);
    }

    @Override
    public void onMatchEnd(Match match) {
        if (match != null) {
            lives.remove(match.id());
        }
    }

    public int remaining(Match match, Player player) {
        if (match == null || player == null) {
            return 0;
        }
        Map<UUID, Integer> remaining = lives.get(match.id());
        if (remaining == null) {
            return configuredLives;
        }
        Integer left = remaining.get(player.getUniqueId());
        return left == null ? configuredLives : left;
    }

    public int delayTicks() {
        return delayTicks;
    }

    public boolean reapplyKit() {
        return reapplyKit;
    }

    @Override
    public String describe(KitRuleOptions options) {
        return configuredLives < 0 ? "Unlimited respawns." : configuredLives + " lives per player.";
    }
}
