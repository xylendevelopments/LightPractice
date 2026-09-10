package gg.lightpractice.kit.rule;

import gg.lightpractice.match.Match;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Governs potion usage, including whether drinking or splashing is permitted.
 *
 * <pre>
 * potions:
 *   drinking: true
 *   splashing: true
 *   deny-types: [REGEN]
 * </pre>
 */
public final class PotionRule implements KitRule {

    private boolean enabled = true;
    private boolean drinking = true;
    private boolean splashing = true;
    private java.util.Set<String> deniedTypes = new java.util.LinkedHashSet<String>();

    @Override
    public String id() {
        return "potions";
    }

    @Override
    public void load(KitRuleOptions options) {
        KitRuleOptions values = options == null ? KitRuleOptions.disabled() : options;
        this.enabled = values.bool("enabled", true);
        this.drinking = values.bool("drinking", true);
        this.splashing = values.bool("splashing", true);
        this.deniedTypes = new java.util.LinkedHashSet<String>(values.lowerCaseList("deny-types"));
    }

    @Override
    public boolean allowPotions() {
        return enabled;
    }

    @Override
    public boolean allowDrinkingPotions() {
        return enabled && drinking;
    }

    @Override
    public boolean allowSplashPotions() {
        return enabled && splashing;
    }

    @Override
    public boolean canUseItem(Match match, Player player, ItemStack item) {
        if (item == null || !enabled || !isPotion(item)) {
            return true;
        }
        return deniedTypes.isEmpty() || !denies(item);
    }

    /** True when the potion type of the item is on the deny list. */
    public boolean denies(ItemStack item) {
        if (item == null || deniedTypes.isEmpty()) {
            return false;
        }
        try {
            org.bukkit.potion.Potion potion = org.bukkit.potion.Potion.fromItemStack(item);
            if (potion == null || potion.getType() == null) {
                return false;
            }
            return deniedTypes.contains(potion.getType().name().toLowerCase(java.util.Locale.ROOT));
        } catch (Throwable throwable) {
            return false;
        }
    }

    public boolean isPotion(ItemStack item) {
        return item != null && item.getType() == Material.POTION;
    }

    @Override
    public String describe(KitRuleOptions options) {
        if (!enabled) {
            return "Potions are disabled.";
        }
        return "Potions allowed (drinking: " + drinking + ", splashing: " + splashing + ").";
    }
}
