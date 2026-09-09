package gg.lightpractice.kit.rule;

import gg.lightpractice.arena.Arena;
import gg.lightpractice.arena.ArenaBounds;
import gg.lightpractice.match.Match;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;

/**
 * Ring out rules: no damage at all, and leaving the platform ends the match instantly.
 *
 * <pre>
 * sumo:
 *   out-of-bounds: true
 *   y-threshold: 60
 *   border-warning: true
 * </pre>
 */
public final class SumoRule implements KitRule {

    private boolean outOfBounds = true;
    private double yThreshold = Double.NaN;
    private boolean borderWarning;

    @Override
    public String id() {
        return "sumo";
    }

    @Override
    public void load(KitRuleOptions options) {
        KitRuleOptions values = options == null ? KitRuleOptions.disabled() : options;
        this.outOfBounds = values.bool("out-of-bounds", true);
        this.yThreshold = values.decimal("y-threshold", Double.NaN);
        this.borderWarning = values.bool("border-warning", true);
    }

    @Override
    public boolean allowDamage(Match match, Player victim, EntityDamageEvent.DamageCause cause) {
        // the only thing that can kill a sumo player is leaving the platform
        return false;
    }

    @Override
    public double modifyDamage(Match match, Player victim, Player attacker, double damage,
                               EntityDamageEvent.DamageCause cause) {
        return 0.0D;
    }

    @Override
    public boolean allowFallDamage() {
        return false;
    }

    @Override
    public boolean instantLoss(Match match, Player player, EntityDamageEvent.DamageCause cause, Location location) {
        if (cause == EntityDamageEvent.DamageCause.VOID) {
            return true;
        }
        Location where = location == null ? (player == null ? null : player.getLocation()) : location;
        if (where == null) {
            return false;
        }
        if (!Double.isNaN(yThreshold) && where.getY() < yThreshold) {
            return true;
        }
        if (outOfBounds && match != null) {
            Arena arena = match.arena();
            ArenaBounds bounds = arena == null ? null : arena.bounds();
            return bounds != null && bounds.valid() && !bounds.contains(where);
        }
        return false;
    }

    public boolean borderWarning() {
        return borderWarning;
    }

    @Override
    public String describe(KitRuleOptions options) {
        return "Knock your opponent off the platform to win.";
    }
}
