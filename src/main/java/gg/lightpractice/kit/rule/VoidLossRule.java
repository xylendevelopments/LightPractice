package gg.lightpractice.kit.rule;

import gg.lightpractice.arena.Arena;
import gg.lightpractice.arena.ArenaBounds;
import gg.lightpractice.match.Match;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;

/**
 * Treats falling into the void or below a height threshold as an instant elimination.
 *
 * <pre>
 * void-loss:
 *   y-threshold: 0
 *   outside-bounds: true
 * </pre>
 */
public final class VoidLossRule implements KitRule {

    private double yThreshold = 0.0D;
    private boolean outsideBounds;
    private boolean enabled = true;

    @Override
    public String id() {
        return "void-loss";
    }

    @Override
    public void load(KitRuleOptions options) {
        KitRuleOptions values = options == null ? KitRuleOptions.disabled() : options;
        this.enabled = values.bool("enabled", true);
        this.yThreshold = values.decimal("y-threshold", 0.0D);
        this.outsideBounds = values.bool("outside-bounds", true);
    }

    @Override
    public boolean allowFallDamage() {
        // falling should eliminate rather than chip away health
        return !enabled;
    }

    @Override
    public boolean instantLoss(Match match, Player player, EntityDamageEvent.DamageCause cause, Location location) {
        if (!enabled) {
            return false;
        }
        if (cause == EntityDamageEvent.DamageCause.VOID) {
            return true;
        }
        Location where = location == null ? (player == null ? null : player.getLocation()) : location;
        if (where == null) {
            return false;
        }
        if (where.getY() < yThreshold) {
            return true;
        }
        if (outsideBounds && match != null) {
            Arena arena = match.arena();
            ArenaBounds bounds = arena == null ? null : arena.bounds();
            return bounds != null && bounds.valid() && !bounds.contains(where);
        }
        return false;
    }

    @Override
    public String describe(KitRuleOptions options) {
        return "Falling into the void eliminates you instantly.";
    }
}
