package gg.lightpractice.kit.rule;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import gg.lightpractice.match.Match;

/**
 * Governs golden apple usage: whether they work, how long the cooldown is and whether the enchanted
 * variant counts.
 *
 * <pre>
 * golden-apples:
 *   cooldown: 3000
 *   enchanted: false
 *   heal: 4.0
 * </pre>
 */
public final class GoldenAppleRule implements KitRule {

    private long cooldownMillis;
    private boolean enchanted;
    private double heal = 4.0D;
    private boolean enabled = true;

    @Override
    public String id() {
        return "golden-apples";
    }

    @Override
    public void load(KitRuleOptions options) {
        KitRuleOptions values = options == null ? KitRuleOptions.disabled() : options;
        this.enabled = values.bool("enabled", true);
        this.cooldownMillis = Math.max(0L, values.integer("cooldown", 0));
        this.enchanted = values.bool("enchanted", false);
        this.heal = Math.max(0.0D, values.decimal("heal", 4.0D));
    }

    @Override
    public boolean allowGoldenApples() {
        return enabled;
    }

    @Override
    public boolean canUseItem(Match match, Player player, org.bukkit.inventory.ItemStack item) {
        if (item == null || !isGoldenApple(item.getType())) {
            return true;
        }
        if (!enabled) {
            return false;
        }
        // enchanted golden apples carry data value 1 in 1.8
        return item.getDurability() == 0 || enchanted;
    }

    @Override
    public long goldenAppleCooldownMillis() {
        return enabled ? cooldownMillis : 0L;
    }

    public boolean enchanted() {
        return enchanted;
    }

    public double heal() {
        return heal;
    }

    public static boolean isGoldenApple(Material material) {
        return material == Material.GOLDEN_APPLE;
    }

    @Override
    public String describe(KitRuleOptions options) {
        if (!enabled) {
            return "Golden apples cannot be eaten.";
        }
        return "Golden apples heal " + heal + " hearts"
                + (cooldownMillis > 0 ? " every " + (cooldownMillis / 1000L) + "s" : "") + ".";
    }
}
