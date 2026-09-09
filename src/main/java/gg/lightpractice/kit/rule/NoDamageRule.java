package gg.lightpractice.kit.rule;

import gg.lightpractice.match.Match;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Disables damage entirely, except for the causes listed as allowed.
 *
 * <pre>
 * no-damage:
 *   allowed-causes: [BLOCK_EXPLOSION, VOID]
 * </pre>
 *
 * <p>Used by kits where the only way to lose is a fall, an explosion or leaving the arena.</p>
 */
public final class NoDamageRule implements KitRule {

    private final Set<String> allowedCauses = new LinkedHashSet<String>();
    private boolean enabled = true;

    @Override
    public String id() {
        return "no-damage";
    }

    @Override
    public void load(KitRuleOptions options) {
        KitRuleOptions values = options == null ? KitRuleOptions.disabled() : options;
        this.enabled = values.bool("enabled", true);
        this.allowedCauses.clear();
        for (String cause : values.list("allowed-causes")) {
            allowedCauses.add(cause.trim().toUpperCase(Locale.ROOT));
        }
    }

    @Override
    public boolean allowDamage(Match match, Player victim, EntityDamageEvent.DamageCause cause) {
        if (!enabled || cause == null) {
            return true;
        }
        return allowedCauses.contains(cause.name());
    }

    @Override
    public double modifyDamage(Match match, Player victim, Player attacker, double damage,
                               EntityDamageEvent.DamageCause cause) {
        return allowDamage(match, victim, cause) ? damage : 0.0D;
    }

    public boolean allows(EntityDamageEvent.DamageCause cause) {
        return !enabled || cause == null || allowedCauses.contains(cause.name());
    }

    @Override
    public String describe(KitRuleOptions options) {
        if (!enabled) {
            return "Damage rules disabled.";
        }
        return allowedCauses.isEmpty() ? "All damage is disabled." : "Damage is disabled except " + allowedCauses + ".";
    }
}
