package gg.lightpractice.kit.rule;

import gg.lightpractice.match.Match;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * First to land a number of hits wins; damage is ignored entirely.
 *
 * <pre>
 * boxing:
 *   hits: 100
 * </pre>
 *
 * <p>Hit counters are tracked per match, because a single rule instance is shared by every match
 * running the kit.</p>
 */
public final class BoxingRule implements KitRule {

    private final Map<UUID, Map<UUID, Integer>> hits = new ConcurrentHashMap<UUID, Map<UUID, Integer>>();
    private int requiredHits = 100;

    @Override
    public String id() {
        return "boxing";
    }

    @Override
    public void load(KitRuleOptions options) {
        KitRuleOptions values = options == null ? KitRuleOptions.disabled() : options;
        this.requiredHits = Math.max(1, values.integer("hits", 100));
    }

    @Override
    public boolean allowDamage(Match match, Player victim, EntityDamageEvent.DamageCause cause) {
        return false;
    }

    @Override
    public double modifyDamage(Match match, Player victim, Player attacker, double damage,
                               EntityDamageEvent.DamageCause cause) {
        return 0.0D;
    }

    @Override
    public void onPlayerHit(Match match, Player attacker, Player victim) {
        if (match == null || attacker == null || victim == null) {
            return;
        }
        Map<UUID, Integer> counts = hits.get(match.id());
        if (counts == null) {
            counts = new ConcurrentHashMap<UUID, Integer>();
            hits.put(match.id(), counts);
        }
        Integer previous = counts.get(attacker.getUniqueId());
        int total = (previous == null ? 0 : previous) + 1;
        counts.put(attacker.getUniqueId(), total);
        if (total >= requiredHits) {
            match.endByScore(attacker, "boxing");
        }
    }

    @Override
    public void onMatchEnd(Match match) {
        if (match != null) {
            hits.remove(match.id());
        }
    }

    public int hitsOf(Match match, Player player) {
        if (match == null || player == null) {
            return 0;
        }
        Map<UUID, Integer> counts = hits.get(match.id());
        if (counts == null) {
            return 0;
        }
        Integer value = counts.get(player.getUniqueId());
        return value == null ? 0 : value;
    }

    public int requiredHits() {
        return requiredHits;
    }

    public Map<UUID, Integer> snapshot(Match match) {
        Map<UUID, Integer> counts = match == null ? null : hits.get(match.id());
        return counts == null ? new HashMap<UUID, Integer>() : new HashMap<UUID, Integer>(counts);
    }

    @Override
    public String describe(KitRuleOptions options) {
        return "First player to land " + requiredHits + " hits wins.";
    }
}
