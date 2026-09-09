package gg.lightpractice.kit.rule;

import gg.lightpractice.match.Match;
import gg.lightpractice.util.Cooldowns;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * Places end crystals that explode with a configurable yield.
 *
 * <pre>
 * crystals:
 *   enabled: true
 *   trigger-item: EYE_OF_ENDER
 *   base-blocks: [OBSIDIAN, BEDROCK]
 *   cooldown: 3000
 *   block-damage: false
 *   damage-scale: 0.8
 * </pre>
 */
public final class CrystalRule implements KitRule {

    private final Cooldowns<UUID> cooldown = new Cooldowns<UUID>();
    private boolean enabled = true;
    private Material trigger = Material.EYE_OF_ENDER;
    private java.util.Set<Material> bases = new java.util.LinkedHashSet<Material>();
    private long cooldownMillis = 3000L;
    private boolean blockDamage;
    private double damageScale = 0.8D;

    @Override
    public String id() {
        return "crystals";
    }

    @Override
    public void load(KitRuleOptions options) {
        KitRuleOptions values = options == null ? KitRuleOptions.disabled() : options;
        this.enabled = values.bool("enabled", true);
        this.trigger = values.material("trigger-item", Material.EYE_OF_ENDER);
        this.bases = values.materials("base-blocks", java.util.Arrays.asList("OBSIDIAN", "BEDROCK"));
        this.cooldownMillis = Math.max(0L, values.integer("cooldown", 3000));
        this.blockDamage = values.bool("block-damage", false);
        this.damageScale = Math.max(0.0D, values.decimal("damage-scale", 0.8D));
    }

    @Override
    public boolean canUseItem(Match match, Player player, ItemStack item) {
        return enabled || item == null || item.getType() != trigger;
    }

    @Override
    public boolean handleItemUse(Match match, Player player, ItemStack item, Action action) {
        if (!enabled || player == null || item == null || item.getType() != trigger) {
            return false;
        }
        if (action != Action.RIGHT_CLICK_BLOCK) {
            return false;
        }
        Block target = player.getTargetBlock(bases, 5);
        if (target == null) {
            return false;
        }
        if (!bases.isEmpty() && !bases.contains(target.getType())) {
            return false;
        }
        if (!cooldown.ready(player.getUniqueId())) {
            return true;
        }
        if (match != null && !match.canModify(target.getLocation())) {
            return true;
        }
        Location above = target.getLocation().add(0.5D, 1.0D, 0.5D);
        EnderCrystal crystal = above.getWorld().spawn(above, EnderCrystal.class);
        crystal.setShowingBottom(false);
        cooldown.mark(player.getUniqueId(), cooldownMillis);
        return true;
    }

    @Override
    public boolean allowExplosionBlockDamage() {
        return enabled && blockDamage;
    }

    @Override
    public double explosionDamageScale() {
        return enabled ? damageScale : 1.0D;
    }

    public boolean enabled() {
        return enabled;
    }

    public long remaining(UUID uuid) {
        return Math.max(0L, cooldown.remaining(uuid));
    }

    @Override
    public String describe(KitRuleOptions options) {
        if (!enabled) {
            return "End crystals are disabled.";
        }
        return "Right click " + trigger.name() + " on " + bases + " to place an end crystal.";
    }
}
