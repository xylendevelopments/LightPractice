package gg.lightpractice.kit.rule;

import gg.lightpractice.match.Match;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Ender pearl behaviour: cooldown, teleport damage and whether pearls may be thrown at all.
 *
 * <pre>
 * pearls:
 *   cooldown: 10000
 *   damage: 2.5
 *   enabled: true
 * </pre>
 */
public final class PearlRule implements KitRule {

    private long cooldownMillis = 10000L;
    private double damage;
    private boolean enabled = true;

    @Override
    public String id() {
        return "pearls";
    }

    @Override
    public void load(KitRuleOptions options) {
        KitRuleOptions values = options == null ? KitRuleOptions.disabled() : options;
        this.enabled = values.bool("enabled", true);
        this.cooldownMillis = Math.max(0L, values.integer("cooldown", 10000));
        this.damage = Math.max(0.0D, values.decimal("damage", 0.0D));
    }

    @Override
    public boolean allowEnderPearls() {
        return enabled;
    }

    @Override
    public boolean canUseItem(Match match, Player player, ItemStack item) {
        return item == null || item.getType() != Material.ENDER_PEARL || enabled;
    }

    @Override
    public long pearlCooldownMillis() {
        return enabled ? cooldownMillis : 0L;
    }

    @Override
    public double pearlDamage() {
        return enabled ? damage : 0.0D;
    }

    @Override
    public String describe(KitRuleOptions options) {
        if (!enabled) {
            return "Ender pearls are disabled.";
        }
        return "Ender pearls available" + (cooldownMillis > 0 ? " with a " + (cooldownMillis / 1000L) + "s cooldown" : "") + ".";
    }
}
